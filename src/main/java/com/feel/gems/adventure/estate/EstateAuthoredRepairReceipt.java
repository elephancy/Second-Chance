package com.feel.gems.adventure.estate;

import com.feel.gems.GemsMod;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * A small, exact repair mask captured from the placed templates before features.
 * It belongs to one generating chunk; an old FULL chunk is never repaired.
 */
final class EstateAuthoredRepairReceipt {
    private static final int VERSION = 2;
    private static final int HEADER = 7;
    private static final int HALF = 117;
    private static final int MAX_PASSAGE_CELLS = 16 * 16 * 4;
    private static final int MAX_SUPPORT_CELLS = 512;
    private static final int MAX_CELLS = MAX_PASSAGE_CELLS + MAX_SUPPORT_CELLS;
    private static final int MAX_REPAIRS = 128;
    private static final String[] SECTORS = {"NNNNE", "WNNEE", "WWMEE", "WWSSE", "WSSSS"};
    private static final AttachmentType<List<Integer>> RECEIPT =
            AttachmentRegistry.createPersistent(Identifier.fromNamespaceAndPath(
                    GemsMod.MOD_ID, "estate_authored_repair_v1"), Codec.INT.listOf());

    private EstateAuthoredRepairReceipt() { }

    static void init() { /* Force attachment registration during mod initialization. */ }

    static void capture(WorldGenRegion region, ChunkPos center,
                        PiecesContainer container) {
        long began = System.nanoTime();
        EstateModuleGraph.Module[] modules = EstateModuleGraph.modules(container.pieces());
        if (modules == null || EstateModuleGraph.seams(modules).size() != 24
                || !FourRoadsEstateStructure.completeGrid(container.pieces())) return;
        BoundingBox site = bounds(container.pieces());
        int rootX = site.minX() + HALF;
        int rootZ = site.minZ() + HALF;
        if (site.getXSpan() != 235 || site.getZSpan() != 235
                || (rootX & 15) != 0 || (rootZ & 15) != 0) return;
        Rotation rotation = modules[12].piece().getRotation();
        for (EstateModuleGraph.Module module : modules) {
            if (module.piece().getRotation() != rotation) return;
        }
        Set<Long> transitions = transitions(modules, container.pieces());
        if (transitions == null) return;
        Map<Integer, Integer> cells = new HashMap<>();
        Set<Integer> supports = new HashSet<>();
        for (EstateModuleGraph.Module module : modules) {
            if (!horizontalOverlap(module.piece().getBoundingBox(), center)) continue;
            if (!appendSupport(region, center, site, supports, module)) return;
            if (module.id() == 12) continue;
            PoolElementStructurePiece piece = module.piece();
            var element = piece.getElement();
            if (!(element instanceof net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement single))
                return;
            var template = region.getLevel().getServer().getStructureTemplateManager()
                    .getOrCreate(single.getTemplateLocation());
            var settings = new StructurePlaceSettings().setRotation(piece.getRotation());
            if (!append(region, center, site, transitions, cells, piece, template, settings,
                    Blocks.AIR, 0)) return;
            int wallKind = wallKind(module.id());
            if (wallKind == 0 || !append(region, center, site, transitions, cells,
                    piece, template, settings, wall(wallKind), wallKind)) return;
        }
        if (cells.isEmpty() && supports.isEmpty()) return;
        if (cells.size() > MAX_PASSAGE_CELLS || supports.size() > MAX_SUPPORT_CELLS
                || cells.size() + supports.size() > MAX_CELLS) return;
        List<Integer> encoded = new ArrayList<>(HEADER + cells.size() + supports.size());
        encoded.add(VERSION);
        encoded.add(rootX >> 4);
        encoded.add(rootZ >> 4);
        encoded.add(center.x());
        encoded.add(center.z());
        encoded.add(rotation.ordinal());
        encoded.add(cells.size() + supports.size());
        cells.entrySet().stream().map(entry -> (entry.getValue() << 19) | entry.getKey())
                .forEach(encoded::add);
        encoded.addAll(supports);
        encoded.subList(HEADER, encoded.size()).sort(Integer::compare);
        region.getChunk(center.x(), center.z()).setAttached(RECEIPT, List.copyOf(encoded));
        if (Boolean.getBoolean("gems.fourRoadsEstateGate"))
            System.out.println("FOUR_ROADS_ESTATE_AUTHORED_MASK chunk=" + center
                    + " passageCells=" + cells.size() + " supportCells="
                    + supports.size() + " ms="
                    + (System.nanoTime() - began) / 1_000_000);
    }

    private static boolean append(WorldGenRegion region, ChunkPos center,
            BoundingBox site, Set<Long> transitions, Map<Integer, Integer> cells,
            PoolElementStructurePiece piece,
            net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate template,
            StructurePlaceSettings settings, Block expected, int kind) {
        int baseY = piece.getBoundingBox().minY();
        if (baseY < 43 || baseY > 176) return false;
        for (var authored : template.filterBlocks(piece.getPosition(), settings, expected)) {
            BlockPos pos = authored.pos();
            int dy = pos.getY() - baseY - 5;
            if (dy < 0 || dy > 3 || pos.getX() >> 4 != center.x()
                    || pos.getZ() >> 4 != center.z()
                    || transitions.contains(ChunkPos.pack(pos.getX(), pos.getZ()))) continue;
            var current = region.getBlockState(pos);
            if (pos.getX() < site.minX() || pos.getX() > site.maxX()
                    || pos.getZ() < site.minZ() || pos.getZ() > site.maxZ()
                    || region.getBlockEntity(pos) != null
                    || !current.is(expected) && (!wetNatural(current)
                            || !deck(region, pos.getX(), baseY, pos.getZ()))) return false;
            int key = ((baseY + 64) << 10) | (dy << 8)
                    | ((pos.getX() & 15) << 4) | (pos.getZ() & 15);
            Integer prior = cells.putIfAbsent(key, kind);
            if (prior != null && prior != kind) return false;
            if (cells.size() > MAX_PASSAGE_CELLS) return false;
        }
        return true;
    }

    /** Record only the two beam layers and the actually authored pier prefix. */
    private static boolean appendSupport(WorldGenRegion region, ChunkPos center,
            BoundingBox site, Set<Integer> supports, EstateModuleGraph.Module module) {
        BoundingBox box = module.piece().getBoundingBox();
        int baseY = module.baseY();
        if (baseY < 43 || baseY > 176) return false;
        for (int x = Math.max(box.minX(), center.getMinBlockX());
             x <= Math.min(box.maxX(), center.getMaxBlockX()); x++) {
            for (int z = Math.max(box.minZ(), center.getMinBlockZ());
                 z <= Math.min(box.maxZ(), center.getMaxBlockZ()); z++) {
                if (x < site.minX() || x > site.maxX()
                        || z < site.minZ() || z > site.maxZ()) return false;
                boolean pier = EstateRaisedGeometry.footing(box, x, z);
                if (!pier && !EstateRaisedGeometry.beam(box, x, z)) continue;
                for (int depth = 1; depth <= 2; depth++) {
                    BlockPos pos = new BlockPos(x, baseY - depth, z);
                    var state = region.getBlockState(pos);
                    if (!state.isSolid() || !state.getFluidState().isEmpty()
                            || region.getBlockEntity(pos) != null) return false;
                    if (state.is(Blocks.STONE_BRICKS))
                        supports.add(supportCell(baseY, x, z, 5, depth - 1));
                    else if (pier && state.is(Blocks.DEEPSLATE_BRICKS))
                        supports.add(supportCell(baseY, x, z, 6 + depth, 0));
                    if (supports.size() > MAX_SUPPORT_CELLS) return false;
                }
                if (!pier) continue;
                int naturalRun = 0;
                boolean anchored = false;
                for (int depth = 1; depth <= EstateRaisedGeometry.MAX_SUPPORT_DEPTH;
                     depth++) {
                    BlockPos pos = new BlockPos(x, baseY - depth, z);
                    var state = region.getBlockState(pos);
                    if (!state.isSolid() || !state.getFluidState().isEmpty()
                            || region.getBlockEntity(pos) != null) return false;
                    if (state.is(Blocks.DEEPSLATE_BRICKS)) {
                        supports.add(supportCell(baseY, x, z, 6 + depth, 0));
                        naturalRun = 0;
                    } else if (state.is(Blocks.STONE_BRICKS)
                            || state.is(Blocks.SMOOTH_STONE)) {
                        naturalRun = 0;
                    } else if (++naturalRun == 2) {
                        anchored = true;
                        break;
                    }
                    if (supports.size() > MAX_SUPPORT_CELLS) return false;
                }
                if (!anchored) return false;
            }
        }
        return true;
    }

    private static int supportCell(int baseY, int x, int z, int kind, int dy) {
        int key = ((baseY + 64) << 10) | (dy << 8)
                | ((x & 15) << 4) | (z & 15);
        return (kind << 19) | key;
    }

    /** Restore exact authored air/wall and support cells, only during generation. */
    static boolean repair(WorldGenRegion region, ChunkPos center, int rootX, int rootZ) {
        long began = System.nanoTime();
        List<Integer> encoded = region.getChunk(center.x(), center.z()).getAttached(RECEIPT);
        if (encoded == null || encoded.size() < HEADER
                || encoded.get(0) != VERSION || encoded.get(1) != rootX
                || encoded.get(2) != rootZ || encoded.get(3) != center.x()
                || encoded.get(4) != center.z() || encoded.get(5) < 0
                || encoded.get(5) >= Rotation.values().length
                || encoded.get(6) < 1 || encoded.get(6) > MAX_CELLS
                || encoded.size() != HEADER + encoded.get(6)) return false;
        int minX = (rootX << 4) - HALF;
        int minZ = (rootZ << 4) - HALF;
        Rotation rotation = Rotation.values()[encoded.get(5)];
        List<Repair> repairs = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        int passageCells = 0;
        int supportCells = 0;
        int supportRepairs = 0;
        for (int i = HEADER; i < encoded.size(); i++) {
            int raw = encoded.get(i);
            int kind = raw >>> 19;
            int key = raw & ((1 << 19) - 1);
            int baseY = (key >>> 10) - 64;
            int dy = (key >>> 8) & 3;
            int x = center.getMinBlockX() + ((key >>> 4) & 15);
            int z = center.getMinBlockZ() + (key & 15);
            boolean support = kind >= 5;
            int depth = kind == 5 ? dy + 1 : kind - 6;
            int y = support ? baseY - depth : baseY + 5 + dy;
            int worldX = (x - minX) / 47;
            int worldZ = (z - minZ) / 47;
            if (kind > 38 || kind == 6 || kind == 5 && dy > 1
                    || kind >= 7 && dy != 0
                    || baseY < 43 || baseY > 176
                    || x < minX || x >= minX + 235 || z < minZ || z >= minZ + 235
                    || (support ? !supportCoordinate(kind, worldX, worldZ,
                            x - minX, z - minZ)
                            : !matchesWing(kind, rotation, worldX, worldZ)))
                return false;
            BlockPos pos = new BlockPos(x, y, z);
            if (!seen.add(pos)) return false;
            if (support ? ++supportCells > MAX_SUPPORT_CELLS
                    : ++passageCells > MAX_PASSAGE_CELLS) return false;
            var current = region.getBlockState(pos);
            Block expected = kind == 0 ? Blocks.AIR : kind <= 4 ? wall(kind)
                    : kind == 5 ? Blocks.STONE_BRICKS : Blocks.DEEPSLATE_BRICKS;
            if (support) {
                if (current.is(expected) && current.getFluidState().isEmpty()) continue;
                if (!replaceableSupport(current)) return false;
            } else {
                if (!wetNatural(current)) {
                    // Other modifications remain for the loaded integrity audit.
                    if (!current.getFluidState().isEmpty()) return false;
                    continue;
                }
            }
            if (region.getBlockEntity(pos) != null || !region.ensureCanWrite(pos)
                    || !deck(region, x, baseY, z)) return false;
            repairs.add(new Repair(pos, expected));
            if (support) supportRepairs++;
            if (repairs.size() > MAX_REPAIRS) return false;
        }
        // Seal breached walls before draining interior passage cells.
        repairs.sort(Comparator.comparingInt(repair -> repair.block() == Blocks.AIR ? 1 : 0));
        for (Repair repair : repairs)
            region.setBlock(repair.pos(), repair.block().defaultBlockState(), 2);
        if (Boolean.getBoolean("gems.fourRoadsEstateGate"))
            System.out.println("FOUR_ROADS_ESTATE_AUTHORED_REPAIR chunk=" + center
                    + " root=" + rootX + "," + rootZ + " maskCells=" + encoded.get(6)
                    + " passageCells=" + passageCells + " supportCells=" + supportCells
                    + " waterCells=" + (repairs.size() - supportRepairs)
                    + " supportRepairs=" + supportRepairs + " ms="
                    + (System.nanoTime() - began) / 1_000_000);
        return !repairs.isEmpty();
    }

    private record Repair(BlockPos pos, Block block) { }

    private static boolean wetNatural(
            net.minecraft.world.level.block.state.BlockState state) {
        return state.is(Blocks.WATER)
                || !state.getFluidState().isEmpty()
                        && (state.is(Blocks.SEAGRASS)
                                || state.is(Blocks.TALL_SEAGRASS)
                                || state.is(Blocks.KELP)
                                || state.is(Blocks.KELP_PLANT)
                                || state.is(Blocks.SEA_PICKLE)
                                || state.is(Blocks.BUBBLE_COLUMN));
    }

    private static boolean replaceableSupport(
            net.minecraft.world.level.block.state.BlockState state) {
        return state.isAir() || state.is(Blocks.WATER)
                || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS)
                || state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT)
                || state.is(Blocks.SEA_PICKLE) || state.is(Blocks.BUBBLE_COLUMN);
    }

    private static boolean supportCoordinate(int kind, int worldX, int worldZ,
                                             int relativeX, int relativeZ) {
        if (worldX < 0 || worldX > 4 || worldZ < 0 || worldZ > 4) return false;
        int localX = relativeX % 47;
        int localZ = relativeZ % 47;
        boolean footing = onFootingAxis(localX) && onFootingAxis(localZ);
        return kind == 5 ? onFootingAxis(localX) || onFootingAxis(localZ) : footing;
    }

    private static boolean onFootingAxis(int offset) {
        for (int point : EstateRaisedGeometry.FOOTING_OFFSETS) {
            if (point == 46 ? offset == 45 || offset == 46
                    : offset == point || offset == point + 1) return true;
        }
        return false;
    }

    private static boolean deck(WorldGenRegion region, int x, int baseY, int z) {
        for (int dy = 0; dy < 4; dy++) {
            if (!region.getBlockState(new BlockPos(x, baseY + dy, z))
                    .is(Blocks.STONE_BRICKS)) return false;
        }
        return true;
    }

    private static boolean matchesWing(int kind, Rotation rotation, int worldX, int worldZ) {
        if (worldX < 0 || worldX > 4 || worldZ < 0 || worldZ > 4) return false;
        int idX;
        int idZ;
        switch (rotation) {
            case CLOCKWISE_90 -> { idX = worldZ; idZ = 4 - worldX; }
            case CLOCKWISE_180 -> { idX = 4 - worldX; idZ = 4 - worldZ; }
            case COUNTERCLOCKWISE_90 -> { idX = 4 - worldZ; idZ = worldX; }
            default -> { idX = worldX; idZ = worldZ; }
        }
        char wing = SECTORS[idZ].charAt(idX);
        return wing != 'M' && (kind == 0 || kind == switch (wing) {
            case 'N' -> 1;
            case 'E' -> 2;
            case 'S' -> 3;
            case 'W' -> 4;
            default -> 0;
        });
    }

    private static int wallKind(int id) {
        return switch (SECTORS[id / 5].charAt(id % 5)) {
            case 'N' -> 1;
            case 'E' -> 2;
            case 'S' -> 3;
            case 'W' -> 4;
            default -> 0;
        };
    }

    private static Block wall(int kind) {
        return switch (kind) {
            case 1 -> Blocks.MOSSY_STONE_BRICKS;
            case 2 -> Blocks.CALCITE;
            case 3 -> Blocks.PRISMARINE_BRICKS;
            case 4 -> Blocks.DEEPSLATE_BRICKS;
            default -> throw new IllegalArgumentException("Not a road wall: " + kind);
        };
    }

    private static boolean horizontalOverlap(BoundingBox box, ChunkPos chunk) {
        return box.minX() <= chunk.getMaxBlockX() && box.maxX() >= chunk.getMinBlockX()
                && box.minZ() <= chunk.getMaxBlockZ() && box.maxZ() >= chunk.getMinBlockZ();
    }

    private static BoundingBox bounds(List<StructurePiece> pieces) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (StructurePiece piece : pieces) {
            BoundingBox box = piece.getBoundingBox();
            minX = Math.min(minX, box.minX()); minY = Math.min(minY, box.minY());
            minZ = Math.min(minZ, box.minZ()); maxX = Math.max(maxX, box.maxX());
            maxY = Math.max(maxY, box.maxY()); maxZ = Math.max(maxZ, box.maxZ());
        }
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Set<Long> transitions(EstateModuleGraph.Module[] modules,
                                         List<StructurePiece> pieces) {
        List<EstateTerrainPlan.Gate> gates = EstateTerrainPlan.gates(pieces);
        if (gates.size() != 4) return null;
        Set<Long> excluded = new HashSet<>();
        for (EstateModuleGraph.Seam seam : EstateModuleGraph.seams(modules)) {
            for (int step = 0; step <= 12; step++) {
                for (int lane = -2; lane <= 2; lane++) {
                    BlockPos pos = seam.at(step, lane);
                    excluded.add(ChunkPos.pack(pos.getX(), pos.getZ()));
                }
            }
        }
        for (EstateTerrainPlan.Gate gate : gates) {
            for (int step = 1; step <= gate.distance(); step++) {
                for (int lane = -1; lane <= 1; lane++) {
                    BlockPos pos = gate.at(step, lane);
                    excluded.add(ChunkPos.pack(pos.getX(), pos.getZ()));
                }
            }
        }
        return excluded;
    }
}
