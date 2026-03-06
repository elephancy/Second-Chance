package com.feel.gems.adventure.worldgen;

import com.feel.gems.adventure.DomainBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;

/** A pier-supported sandstone walk from natural ground to each elevated v10 Fam hall. */
final class FamV10ArrivalTerrace {
    private static final Set<Identifier> FACADES = Set.of(
            id("fam_forum_arcaded_court_v10"),
            id("fam_open_school_workshop_v10"),
            id("fam_story_hall_reading_garden_v10"));
    private static final Identifier REFERENCE = id("fam_east_reference_v1");
    private static final Identifier ARRIVAL_REFERENCE = id("fam_arrival_reference_v10");
    private static final int LENGTH = 24;
    private static final int HALF_WIDTH = 4;
    private static final int MAX_RISE = 18;
    private static final int CLEAR_ABOVE = 12;
    private static final int MAX_SHALLOW_ROOF_GAP = 8;
    private static final boolean DEBUG = Boolean.getBoolean("gems.famForumV10NaturalGate")
            || Boolean.getBoolean("gems.famForumV10PublicGate");
    private static final AtomicInteger DEBUG_COUNT = new AtomicInteger();

    private FamV10ArrivalTerrace() { }

    /** Finish a saved v10 causeway without loading its remote start chunk. */
    static boolean clearCanopyOverMarker(WorldGenRegion region, ChunkPos center,
            Structure trial) {
        if (trial == null || region.getChunk(center.x(), center.z())
                .getPersistedStatus().isOrAfter(ChunkStatus.FULL)) return false;
        var references = region.getChunk(center.x(), center.z()).getAllReferences();
        // The saved marker proves this accepted start placed its reserved
        // apron here. A remote foreign reference without an intersecting
        // piece must not hide the arrival behind newly grown tree canopy.
        if (references.get(trial) == null || references.get(trial).isEmpty()) return false;
        boolean changed = false;
        for (int x = center.getMinBlockX(); x < center.getMinBlockX() + 16; x++) {
            for (int z = center.getMinBlockZ(); z < center.getMinBlockZ() + 16; z++) {
                int top = region.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                for (int y = top - 1; y >= Math.max(region.getMinY(), top - 32); y--) {
                    BlockPos marker = new BlockPos(x, y, z);
                    // Both tiles are absent from the saved v10 templates;
                    // only the reserved exterior deck uses them here.
                    if (!arrivalTile(region.getBlockState(marker)))
                        continue;
                    for (int rise = 1; rise <= CLEAR_ABOVE; rise++) {
                        BlockPos at = marker.above(rise);
                        BlockState old = region.getBlockState(at);
                        if (!naturalTreeOrVine(old) || region.getBlockEntity(at) != null
                                || !region.ensureCanWrite(at)) continue;
                        region.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
                        changed = true;
                    }
                    break;
                }
            }
        }
        return changed;
    }

    /** Survey the continuous three-wide walk before accepting a natural start. */
    static boolean preflightStart(Structure.GenerationContext context,
            PoolElementStructurePiece facade, int baseY) {
        BoundingBox box = facade.getBoundingBox();
        Direction outward = facade.getRotation().rotate(Direction.SOUTH);
        Direction lateral = facade.getRotation().rotate(Direction.EAST);
        for (int depth = 1; depth <= LENGTH; depth++) {
            int surface = step(baseY, depth);
            for (int side = -1; side <= 1; side++) {
                BlockPos at = column(box, outward, lateral, depth, side);
                HushwireDryApproach.Column terrain = HushwireDryApproach.sample(
                        context, at.getX(), at.getZ());
                if (terrain == null || !terrain.dry() || !terrain.clear()) return false;
                int ground = terrain.feet() - 1;
                if (ground > surface + 1 || surface - ground > MAX_RISE) return false;
            }
        }
        return true;
    }

    static BoundingBox approachBounds(PoolElementStructurePiece facade, int baseY) {
        BoundingBox box = facade.getBoundingBox();
        Direction outward = facade.getRotation().rotate(Direction.SOUTH);
        Direction lateral = facade.getRotation().rotate(Direction.EAST);
        BlockPos a = column(box, outward, lateral, 1, -HALF_WIDTH);
        BlockPos b = column(box, outward, lateral, LENGTH, HALF_WIDTH);
        return new BoundingBox(Math.min(a.getX(), b.getX()), baseY - MAX_RISE,
                Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()),
                baseY + 4 + CLEAR_ABOVE, Math.max(a.getZ(), b.getZ()));
    }

    static boolean applyCenterChunk(WorldGenRegion region, ChunkPos center,
            List<StructurePiece> pieces, PoolElementStructurePiece facade,
            List<BoundingBox> protectedPieces) {
        if (region.getChunk(center.x(), center.z()).getPersistedStatus()
                .isOrAfter(ChunkStatus.FULL)
                || !(facade.getElement() instanceof SinglePoolElement single)
                || !FACADES.contains(single.getTemplateLocation())) return false;
        BoundingBox box = facade.getBoundingBox();
        Direction outward = facade.getRotation().rotate(Direction.SOUTH);
        Direction lateral = facade.getRotation().rotate(Direction.EAST);
        List<Write> writes = new ArrayList<>();
        boolean local = false;
        for (int depth = 1; depth <= LENGTH; depth++) {
            for (int side = -HALF_WIDTH; side <= HALF_WIDTH; side++) {
                BlockPos column = column(box, outward, lateral, depth, side);
                if ((column.getX() >> 4) != center.x()
                        || (column.getZ() >> 4) != center.z()) continue;
                local = true;
                ColumnPlan planned = preflight(region, pieces, protectedPieces,
                        column, depth, side, box.minY());
                // A broken central walk is worse than leaving this chunk's
                // natural ground intact. Outer columns may retain trees.
                if (!planned.valid()) {
                    if (Math.abs(side) <= 1) {
                        traceReject(center, column, planned.reason());
                        return false;
                    }
                    continue;
                }
                writes.addAll(planned.writes());
            }
        }
        if (!local) return false;
        if (writes.isEmpty()) {
            traceReject(center, facade.getPosition(), "no authored apron writes");
            return false;
        }
        for (Write write : writes) {
            if (!region.ensureCanWrite(write.pos())) {
                traceReject(center, write.pos(), "outside writable new center chunk");
                return false;
            }
        }
        for (Write write : writes) region.setBlock(write.pos(), write.state(), 2);
        return true;
    }

    private static ColumnPlan preflight(WorldGenRegion region, List<StructurePiece> pieces,
            List<BoundingBox> protectedPieces, BlockPos column,
            int depth, int side, int baseY) {
        int x = column.getX(), z = column.getZ();
        if (insideOtherPiece(pieces, x, z)
                || insideOtherProtectedPiece(pieces, protectedPieces, x, z))
            return ColumnPlan.invalid("protected-piece overlap at " + column);
        GroundSample sampled = naturalGround(region, x, z, baseY);
        int ground = sampled.y();
        if (ground == Integer.MIN_VALUE) return ColumnPlan.invalid(sampled.reason());
        // Hold a level viewing deck before a one-block-per-step descent. The
        // old earth-filled ramp hid the lower hall behind its own soil bank.
        int step = step(baseY, depth);
        if (ground > step + 1) return ColumnPlan.valid(List.of());
        int surface = Math.max(ground, step);
        if (surface - ground > MAX_RISE || surface + CLEAR_ABOVE >= region.getMaxY())
            return ColumnPlan.invalid("rise or ceiling at " + column + " ground="
                    + ground + " surface=" + surface);
        List<Write> writes = new ArrayList<>();
        for (int y = ground + 1; y <= surface + CLEAR_ABOVE; y++) {
            BlockPos at = new BlockPos(x, y, z);
            BlockState old = region.getBlockState(at);
            if (region.getBlockEntity(at) != null || !old.getFluidState().isEmpty()
                    || !naturalCover(old))
                return ColumnPlan.invalid("cover at " + at + " state=" + old);
            if (y < surface) {
                // Stone retaining faces hide the exposed soil behind each
                // uphill riser; edge trim and regular piers carry the deck.
                boolean riser = depth < LENGTH && step(baseY, depth)
                        > step(baseY, depth + 1);
                if (riser && y >= surface - 2)
                    writes.add(new Write(at, y == surface - 1 && Math.abs(side) <= 2
                            ? DomainBlocks.FORUM_MOSAIC.defaultBlockState()
                            : Blocks.CHISELED_SANDSTONE.defaultBlockState()));
                else if (Math.abs(side) == HALF_WIDTH
                        && (y >= surface - 2 || (depth - 1) % 4 == 0))
                    writes.add(new Write(at, Blocks.CHISELED_SANDSTONE.defaultBlockState()));
                else if (!old.isAir())
                    writes.add(new Write(at, Blocks.AIR.defaultBlockState()));
            } else if (y == surface) {
                writes.add(new Write(at, walkway(side)));
            } else if (!old.isAir()) {
                writes.add(new Write(at, Blocks.AIR.defaultBlockState()));
            }
        }
        if (surface == ground) {
            writes.add(new Write(column.atY(surface), walkway(side)));
        }
        return ColumnPlan.valid(List.copyOf(writes));
    }

    private static int step(int baseY, int depth) {
        // The thirteen-cell top landing meets the court floor and sits one
        // step below the school. Four dressed risers meet natural ground.
        return baseY + 4 - Math.max(0, (depth - 11) / 3);
    }

    private static BlockState walkway(int side) {
        return Math.abs(side) <= 2 ? Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState()
                : Blocks.CHISELED_RED_SANDSTONE.defaultBlockState();
    }

    static boolean arrivalTile(BlockState state) {
        return state.is(Blocks.SMOOTH_RED_SANDSTONE)
                || state.is(Blocks.CHISELED_RED_SANDSTONE);
    }

    static boolean naturalTreeOrVine(BlockState state) {
        if (!BuiltInRegistries.BLOCK.getKey(state.getBlock())
                .getNamespace().equals("minecraft")) return false;
        if (state.is(BlockTags.LOGS) || state.is(Blocks.VINE)) return true;
        return state.getBlock() instanceof LeavesBlock
                && !state.getValue(LeavesBlock.PERSISTENT);
    }

    private static GroundSample naturalGround(WorldGenRegion region, int x, int z,
            int baseY) {
        int top = region.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        for (int y = top - 1; y >= Math.max(region.getMinY(), baseY - 20); y--) {
            BlockPos at = new BlockPos(x, y, z);
            BlockState state = region.getBlockState(at);
            if (naturalCover(state)) continue;
            if (!state.getFluidState().isEmpty()
                    || region.getBlockEntity(at) != null
                    || !naturalSoil(state))
                return GroundSample.invalid("soil at " + at + " state=" + state);
            if (!shallowNaturalRoof(region, at))
                return GroundSample.invalid("roof support at " + at + " state=" + state
                        + " below=" + region.getBlockState(at.below()));
            return new GroundSample(y, "");
        }
        return GroundSample.invalid("no natural ground at x=" + x + " z=" + z
                + " below surface=" + top);
    }

    private static boolean shallowNaturalRoof(WorldGenRegion region, BlockPos soil) {
        for (int depth = 1; depth <= MAX_SHALLOW_ROOF_GAP; depth++) {
            BlockPos at = soil.below(depth);
            BlockState state = region.getBlockState(at);
            if (region.getBlockEntity(at) != null || !state.getFluidState().isEmpty())
                return false;
            if (state.isSolid())
                return BuiltInRegistries.BLOCK.getKey(state.getBlock())
                        .getNamespace().equals("minecraft")
                        || state.is(DomainBlocks.FORUM_PLASTER);
            if (!state.isAir()) return false;
        }
        return false;
    }

    private static boolean naturalSoil(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL)
                || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.STONE)
                || state.is(Blocks.ANDESITE)
                // Forum Glade's own ore feature replaces dirt and grass with
                // plaster outcrops before this late arrival pass.
                || state.is(DomainBlocks.FORUM_PLASTER);
    }

    private static boolean naturalCover(BlockState state) {
        if (state.isAir()) return true;
        if (!BuiltInRegistries.BLOCK.getKey(state.getBlock())
                .getNamespace().equals("minecraft")) return false;
        // Wild flowers can cross into an arrival chunk before its structure
        // pass. They are natural cover over surveyed soil, including tall
        // peonies that are not generally replaceable block states.
        if (state.is(BlockTags.FLOWERS)) return state.getFluidState().isEmpty();
        if (state.is(BlockTags.LOGS)) return true;
        if (state.getBlock() instanceof LeavesBlock leaves)
            return !state.getValue(LeavesBlock.PERSISTENT);
        return state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    private static BlockPos column(BoundingBox box, Direction outward,
            Direction lateral, int depth, int side) {
        int cx = (box.minX() + box.maxX()) / 2;
        int cz = (box.minZ() + box.maxZ()) / 2;
        int edgeX = outward == Direction.EAST ? box.maxX()
                : outward == Direction.WEST ? box.minX() : cx;
        int edgeZ = outward == Direction.SOUTH ? box.maxZ()
                : outward == Direction.NORTH ? box.minZ() : cz;
        return new BlockPos(edgeX + outward.getStepX() * depth
                        + lateral.getStepX() * side, box.minY(),
                edgeZ + outward.getStepZ() * depth + lateral.getStepZ() * side);
    }

    private static boolean insideOtherPiece(List<StructurePiece> pieces, int x, int z) {
        for (StructurePiece piece : pieces) {
            if (ownReference(piece)) continue;
            if (contains(piece.getBoundingBox(), x, z)) return true;
        }
        return false;
    }

    private static boolean insideOtherProtectedPiece(List<StructurePiece> pieces,
            List<BoundingBox> boxes, int x, int z) {
        for (BoundingBox box : boxes) {
            if (!contains(box, x, z)) continue;
            boolean ownCarrier = pieces.stream().anyMatch(piece ->
                    ownReference(piece) && sameBox(box, piece.getBoundingBox()));
            if (!ownCarrier) return true;
        }
        return false;
    }

    private static boolean ownReference(StructurePiece piece) {
        return piece instanceof PoolElementStructurePiece pooled
                && pooled.getElement() instanceof SinglePoolElement single
                && (REFERENCE.equals(single.getTemplateLocation())
                || ARRIVAL_REFERENCE.equals(single.getTemplateLocation()));
    }

    private static boolean sameBox(BoundingBox a, BoundingBox b) {
        return a.minX() == b.minX() && a.maxX() == b.maxX()
                && a.minY() == b.minY() && a.maxY() == b.maxY()
                && a.minZ() == b.minZ() && a.maxZ() == b.maxZ();
    }

    private static boolean contains(BoundingBox box, int x, int z) {
        return x >= box.minX() && x <= box.maxX()
                && z >= box.minZ() && z <= box.maxZ();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("gems", path);
    }

    private static void traceReject(ChunkPos chunk, BlockPos column, String reason) {
        if (DEBUG && DEBUG_COUNT.getAndIncrement() < 96)
            System.out.println("FAM_V10_APRON_REJECT chunk=" + chunk + " column="
                    + column + " reason=" + reason);
    }

    private record Write(BlockPos pos, BlockState state) { }
    private record GroundSample(int y, String reason) {
        private static GroundSample invalid(String reason) {
            return new GroundSample(Integer.MIN_VALUE, reason);
        }
    }
    private record ColumnPlan(boolean valid, List<Write> writes, String reason) {
        private static ColumnPlan valid(List<Write> writes) {
            return new ColumnPlan(true, writes, "");
        }
        private static ColumnPlan invalid(String reason) {
            return new ColumnPlan(false, List.of(), reason);
        }
    }
}
