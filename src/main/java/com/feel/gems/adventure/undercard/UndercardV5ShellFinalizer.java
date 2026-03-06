package com.feel.gems.adventure.undercard;

import com.feel.gems.GemsMod;
import com.feel.gems.adventure.worldgen.site.GuardedNewChunkSiteAccess;
import com.feel.gems.adventure.worldgen.site.SiteAccess;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Completes the authored v5 shell only where persistent new-chunk custody permits. */
final class UndercardV5ShellFinalizer {
    enum Result { READY, WAIT, BLOCKED }

    private static final Identifier TEMPLATE = Identifier.fromNamespaceAndPath(
            GemsMod.MOD_ID, "undercard_ring_v5");
    private static final int MAX_SHELL_CELLS = 20_000;
    private static final int MAX_REPAIR_WRITES = 256;
    private static StructureTemplate cachedTemplate;
    private static List<ShellCell> cachedShell;

    private record ShellCell(BlockPos local, BlockState state) { }
    private record Write(BlockPos pos, BlockState state) { }

    private UndercardV5ShellFinalizer() { }

    static boolean isV5(StructureStart start) {
        return start.getPieces().size() == 1
                && start.getPieces().getFirst() instanceof PoolElementStructurePiece piece
                && piece.getElement() instanceof SinglePoolElement single
                && TEMPLATE.equals(single.getTemplateLocation());
    }

    static void clear() {
        cachedTemplate = null;
        cachedShell = null;
    }

    static Result finish(ServerLevel level, StructureStart start, Structure structure,
                         Set<ChunkPos> touched) {
        if (!isV5(start)) return blocked("wrong saved template");
        PoolElementStructurePiece piece = (PoolElementStructurePiece) start.getPieces().getFirst();
        BoundingBox box = piece.getBoundingBox();
        if (box.getXSpan() != 63 || box.getYSpan() != 48 || box.getZSpan() != 63)
            return blocked("wrong saved bounds " + box);
        GuardedNewChunkSiteAccess access = new GuardedNewChunkSiteAccess(level,
                Set.of(structure));
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                ChunkPos position = new ChunkPos(cx, cz);
                if (!access.canInspectChunk(position)) return Result.WAIT;
            }
        }
        // Compare the exact same, fully loaded outside ring across this
        // one server-thread mutation. A separate world with structures
        // disabled changes ore/foliage decoration and is not an exact control.
        for (int cx = (box.minX() - 2) >> 4; cx <= (box.maxX() + 2) >> 4; cx++) {
            for (int cz = (box.minZ() - 2) >> 4; cz <= (box.maxZ() + 2) >> 4; cz++) {
                if (!access.canInspectChunk(new ChunkPos(cx, cz))) return Result.WAIT;
            }
        }
        SiteAccess.Inspection protection = access.protectedVolume(box);
        if (protection == SiteAccess.Inspection.UNKNOWN) return Result.WAIT;
        if (protection != SiteAccess.Inspection.CLEAR)
            return blocked("other structure overlaps saved bounds");
        List<ShellCell> shell = shell(level);
        if (shell == null) return blocked("authored shell palette or mask invalid");
        List<Write> writes = new ArrayList<>();
        for (ShellCell cell : shell) {
            BlockPos rotated = StructureTemplate.transform(cell.local(), Mirror.NONE,
                    piece.getRotation(), BlockPos.ZERO);
            BlockPos position = piece.getPosition().offset(rotated);
            if (!box.isInside(position)) return blocked("transformed shell outside bounds at "
                    + position);
            BlockState current = level.getBlockState(position);
            if (current.equals(cell.state())) continue;
            // A different placed block could be a player edit or another mod.
            // Leave it and the gate sealed; only natural cave/feature material
            // seen after first-generation placement may be restored.
            if (!naturalInterference(current)) {
                trace("non-natural shell mismatch", position, current, cell.state());
                return blocked("non-natural shell mismatch");
            }
            ChunkPos chunk = new ChunkPos(position.getX() >> 4, position.getZ() >> 4);
            // An intact saved shell can be audited after restart without a
            // write. For a mismatch, the chunk must carry the watch installed
            // when it first became FULL, with no later setBlockState attempt
            // at this exact cell. That proof survives save/restart.
            if (!access.isNewChunk(chunk)
                    || !UndercardShellCustody.canRepair(level, position))
                return blocked("shell mismatch without clean generation custody at "
                        + position + " chunk=" + chunk);
            writes.add(new Write(position, cell.state()));
            if (writes.size() > MAX_REPAIR_WRITES) {
                trace("repair cap exceeded", position, current, cell.state());
                return blocked("repair cap exceeded");
            }
        }
        if (writes.isEmpty()) return Result.READY;
        // Prior proximity alone cannot prove an edit. The persistent cell
        // watch provides that proof; the live occupancy check below still
        // prevents a shell repair while a player stands inside the venue.
        // A write to one fresh chunk must never retrofit a site spanning an
        // old, unguarded chunk that the later gate audit would reject.
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                ChunkPos chunk = new ChunkPos(cx, cz);
                if (!access.isNewChunk(chunk))
                    return blocked("unstamped chunk in authored shell at " + chunk);
            }
        }
        // A delayed load cannot become a repair after someone could reach the
        // authored shell. The controller also remembers earlier approaches.
        for (var player : level.players()) {
            BlockPos pos = player.blockPosition();
            if (pos.getX() >= box.minX() - 8 && pos.getX() <= box.maxX() + 8
                    && pos.getY() >= box.minY() - 8 && pos.getY() <= box.maxY() + 8
                    && pos.getZ() >= box.minZ() - 8 && pos.getZ() <= box.maxZ() + 8)
                return Result.WAIT;
        }
        String outsideBefore = outsideHash(level, box);
        for (Write write : writes) {
            if (!UndercardShellCustody.setAuthoredBlock(level, write.pos(), write.state()))
                return blocked("world refused in-footprint write at " + write.pos());
            touched.add(new ChunkPos(write.pos().getX() >> 4,
                    write.pos().getZ() >> 4));
        }
        String outsideAfter = outsideHash(level, box);
        if (!outsideBefore.equals(outsideAfter))
            return blocked("outside ring changed from " + outsideBefore + " to "
                    + outsideAfter);
        if (Boolean.getBoolean("gems.undercardV5Acceptance")) {
            System.out.println("UNDERCARD_V5_OUTSIDE_SAME_WORLD box=" + box.minX() + ","
                    + box.minY() + "," + box.minZ() + "," + box.maxX() + ","
                    + box.maxY() + "," + box.maxZ() + " before=" + outsideBefore
                    + " after=" + outsideAfter);
        }
        GemsMod.LOGGER.info("Undercard v5 shell finalized at {}: {} shell cells",
                box.getCenter(), writes.size());
        return Result.READY;
    }

    private static String outsideHash(ServerLevel level, BoundingBox box) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int x = box.minX() - 2; x <= box.maxX() + 2; x++) {
                for (int z = box.minZ() - 2; z <= box.maxZ() + 2; z++) {
                    if (x >= box.minX() && x <= box.maxX()
                            && z >= box.minZ() && z <= box.maxZ()) continue;
                    for (int y = box.minY() - 4; y <= box.maxY() + 4; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        digest.update((x + "," + y + "," + z + ":"
                                + level.getBlockState(pos) + ":"
                                + level.getFluidState(pos) + "\n")
                                .getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void trace(String reason, BlockPos position, BlockState actual,
                              BlockState expected) {
        if (Boolean.getBoolean("gems.undercardV5Acceptance"))
            System.out.println("UNDERCARD_V5_SHELL_FINALIZER_BLOCKED reason=" + reason
                    + " pos=" + position + " actual=" + actual + " expected=" + expected);
    }

    private static Result blocked(String reason) {
        if (Boolean.getBoolean("gems.undercardV5Acceptance"))
            System.out.println("UNDERCARD_V5_SHELL_FINALIZER_BLOCKED reason=" + reason);
        return Result.BLOCKED;
    }

    private static boolean naturalInterference(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(Blocks.CAVE_AIR) || state.is(Blocks.AIR)
                || state.is(Blocks.LAVA) || state.is(Blocks.WATER)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.DIRT)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.BONE_BLOCK)
                || state.is(Blocks.COAL_ORE);
    }

    private static List<ShellCell> shell(ServerLevel level) {
        StructureTemplate template = level.getStructureTemplateManager().getOrCreate(TEMPLATE);
        if (template == cachedTemplate && cachedShell != null) return cachedShell;
        if (template.getSize().getX() != 63 || template.getSize().getY() != 48
                || template.getSize().getZ() != 63)
            return invalidShell("template size " + template.getSize());
        CompoundTag nbt = template.save(new CompoundTag());
        if (nbt.contains("palettes")) return invalidShell("multiple palettes");
        ListTag palette = nbt.getListOrEmpty("palette");
        ListTag blocks = nbt.getListOrEmpty("blocks");
        if (palette.isEmpty() || blocks.isEmpty())
            return invalidShell("empty palette=" + palette.size() + " blocks=" + blocks.size());
        BlockState[] states = new BlockState[palette.size()];
        for (int i = 0; i < palette.size(); i++) {
            states[i] = NbtUtils.readBlockState(level.registryAccess()
                    .lookupOrThrow(Registries.BLOCK), palette.getCompoundOrEmpty(i));
        }
        List<ShellCell> cells = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag entry = blocks.getCompoundOrEmpty(i);
            ListTag pos = entry.getListOrEmpty("pos");
            if (pos.size() != 3) return invalidShell("bad block position at " + i);
            int x = pos.getIntOr(0, -1), y = pos.getIntOr(1, -1),
                    z = pos.getIntOr(2, -1);
            int index = entry.getIntOr("state", -1);
            if (index < 0 || index >= states.length)
                return invalidShell("bad palette index " + index + " at " + i);
            BlockState state = states[index];
            if (!shellCell(x, y, z)) continue;
            if (state.is(UndercardBlocks.ROOT) || !state.isSolid()
                    || !state.getFluidState().isEmpty()) continue;
            // The two vertical basalt roof posts keep their Y axis under a
            // horizontal structure rotation. Other directional states need
            // an authored transform review before any automatic repair.
            if (!state.getProperties().isEmpty()
                    && !(state.is(Blocks.POLISHED_BASALT)
                    && state.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y)) {
                return invalidShell("unreviewed state " + state + " at " + x + "," + y
                        + "," + z);
            }
            cells.add(new ShellCell(new BlockPos(x, y, z), state));
            if (cells.size() > MAX_SHELL_CELLS)
                return invalidShell("shell exceeds cap " + cells.size());
        }
        if (cells.size() < 13_000)
            return invalidShell("shell under minimum " + cells.size());
        cachedTemplate = template;
        cachedShell = List.copyOf(cells);
        return cachedShell;
    }

    private static List<ShellCell> invalidShell(String reason) {
        if (Boolean.getBoolean("gems.undercardV5Acceptance"))
            System.out.println("UNDERCARD_V5_SHELL_TEMPLATE_INVALID reason=" + reason);
        return null;
    }

    private static boolean shellCell(int x, int y, int z) {
        if (x < 2 || x > 60 || z < 2 || z > 60) return false;
        // The 21 new buried-bank seam cells are part of the authored dry
        // shell. Keep the copper-edged three-high portal at x38,z47 open.
        if (x >= 38 && x <= 41 && z >= 46 && z <= 47 && y >= 4 && y <= 6
                && !(x == 38 && z == 47)) return true;
        if (y == 0 || y == 1 || y == 2 || y == 22) return true;
        return y >= 2 && y <= 23
                && (((x == 2 || x == 60) && z >= 14 && z <= 48)
                || ((z == 2 || z == 60) && x >= 14 && x <= 48));
    }

}
