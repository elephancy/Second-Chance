package com.feel.gems.adventure.estate;

import com.feel.gems.GemsMod;
import com.mojang.serialization.MapCodec;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Final generation pass for authored road decks and the four short exits.
 * Each invocation writes only its newly generating center chunk. Distant
 * structure starts are identified by saved local references and verified
 * again against an authored floor or gate marker before any write.
 */
public final class EstateArrivalClearanceFeature implements Feature {
    public static final MapCodec<EstateArrivalClearanceFeature> CODEC =
            MapCodec.unit(new EstateArrivalClearanceFeature());
    private static final Identifier ID = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID,
            "estate_arrival_clearance_v1");
    private static final ResourceKey<Structure> ESTATE = ResourceKey.create(
            Registries.STRUCTURE, Identifier.fromNamespaceAndPath(GemsMod.MOD_ID,
                    "four_roads_estate_v1"));
    private static final ResourceKey<PlacedFeature> PLACED = ResourceKey.create(
            Registries.PLACED_FEATURE, ID);
    private static final int HALF = 117;
    private static final int APPROACH = 8;
    private static final int CLEAR_HEIGHT = 26;
    private static final int REFERENCE_RING = 1;

    private EstateArrivalClearanceFeature() { }

    public static void init() {
        EstateAuthoredRepairReceipt.init();
        EstateRouteSite.init();
        Registry.register(BuiltInRegistries.FEATURE_TYPE, ID, CODEC);
        // Surface structures precede vegetation in 26.3. This final pass
        // also runs in neighboring biomes crossed by an estate wing or exit.
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld()
                        .or(BiomeSelectors.includeByKey(
                                com.feel.gems.adventure.worldgen.AdventureBiomes.ESTATE_COMMONS)),
                GenerationStep.Decoration.TOP_LAYER_MODIFICATION, PLACED);
    }

    @Override
    public MapCodec<EstateArrivalClearanceFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random,
                         BlockPos origin) {
        if (!(level instanceof WorldGenRegion region)) return false;
        ChunkPos center = region.getCenter();
        var chunk = region.getChunk(center.x(), center.z(),
                ChunkStatus.STRUCTURE_REFERENCES, false);
        if (chunk.getPersistedStatus().isOrAfter(ChunkStatus.FULL)) return false;
        Structure estate = region.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValue(ESTATE);
        if (estate == null) return false;
        // An unresolved foreign reference may protect an older structure.
        // Veto this whole chunk instead of guessing its piece bounds.
        for (var entry : chunk.getAllReferences().entrySet()) {
            if (entry.getKey() != estate && !entry.getValue().isEmpty()) return false;
        }

        Set<Long> seen = new HashSet<>();
        boolean changed = false;
        // Only the 3x3 reference ring is readable during this decoration
        // step. The saved start may be eight chunks away from an outer wing.
        for (int cz = center.z() - REFERENCE_RING;
             cz <= center.z() + REFERENCE_RING; cz++) {
            for (int cx = center.x() - REFERENCE_RING;
                 cx <= center.x() + REFERENCE_RING; cx++) {
                if (!region.hasChunk(cx, cz)) continue;
                var references = region.getChunk(cx, cz,
                        ChunkStatus.STRUCTURE_REFERENCES, false)
                        .getReferencesForStructure(estate);
                for (long reference : references) {
                    if (!seen.add(reference)) continue;
                    int startX = ChunkPos.getX(reference);
                    int startZ = ChunkPos.getZ(reference);
                    // The 235-square is centered on the start chunk's
                    // minimum block coordinates. Check the actual saved box
                    // when the start happens to be in the readable ring.
                    Footprint site = new Footprint((startX << 4) - HALF,
                            (startZ << 4) - HALF);
                    if (!site.near(center) || !validLocalStart(region, center,
                            estate, startX, startZ, site)) continue;
                    var ownReferences = chunk.getReferencesForStructure(estate);
                    if (ownReferences.size() == 1 && ownReferences.contains(reference)) {
                        changed |= EstateAuthoredRepairReceipt.repair(region, center,
                                startX, startZ);
                        changed |= clearRoadChunk(region, center, site);
                    }
                    changed |= clearExits(region, generator, center, site);
                }
            }
        }
        return changed;
    }

    private static boolean validLocalStart(WorldGenRegion region, ChunkPos center,
            Structure estate, int startX, int startZ, Footprint site) {
        if (Math.abs(startX - center.x()) > REFERENCE_RING
                || Math.abs(startZ - center.z()) > REFERENCE_RING) return true;
        if (!region.hasChunk(startX, startZ)) return false;
        StructureStart start = region.getChunk(startX, startZ,
                ChunkStatus.STRUCTURE_STARTS, false).getStartForStructure(estate);
        if (start == null || !start.isValid()
                || !FourRoadsEstateStructure.completeGrid(start.getPieces())) return false;
        BoundingBox box = start.getBoundingBox();
        return box.minX() == site.minX() && box.maxX() == site.maxX()
                && box.minZ() == site.minZ() && box.maxZ() == site.maxZ();
    }

    private static boolean clearRoadChunk(WorldGenRegion region,
            ChunkPos center, Footprint site) {
        EstateRouteSite.Site placed = EstateRouteSite.readGenerating(region, center);
        if (placed != null && (placed.bounds().minX() != site.minX()
                || placed.bounds().minZ() != site.minZ())) return false;
        boolean changed = false;
        for (int x = Math.max(center.getMinBlockX(), site.minX());
             x <= Math.min(center.getMaxBlockX(), site.maxX()); x++) {
            for (int z = Math.max(center.getMinBlockZ(), site.minZ());
                 z <= Math.min(center.getMaxBlockZ(), site.maxZ()); z++) {
                if (site.mansion(x, z)) continue; // Authored mansion logs.
                int base = deckBase(region, x, z);
                if (base == Integer.MIN_VALUE) continue;
                int roof = placed == null ? base + 17 : placed.roofAt(x, z);
                if (roof < base + 17) continue;
                // The older maze roofs end at base+17. New axial halls rise
                // higher, so keep their authored roofs clear of only natural
                // trees. Persistent decorative leaves are never removed.
                int clearTop = roof > base + 17 ? roof + 2 : roof;
                for (int y = base + 5;
                     y <= Math.min(clearTop, region.getMaxY() - 1); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = region.getBlockState(pos);
                    if (naturalTree(state) && region.getBlockEntity(pos) == null
                            && region.ensureCanWrite(pos)) {
                        region.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private static int deckBase(WorldGenRegion region, int x, int z) {
        // Every road module has a four-block stone-brick deck. This local
        // fingerprint prevents a remote reference alone from authorizing
        // deletion in the current chunk.
        for (int base = 43; base <= 176; base++) {
            boolean deck = true;
            for (int y = base; y <= base + 3; y++) {
                if (!region.getBlockState(new BlockPos(x, y, z))
                        .is(Blocks.STONE_BRICKS)) { deck = false; break; }
            }
            if (deck) return base;
        }
        return Integer.MIN_VALUE;
    }

    private static boolean clearExits(WorldGenRegion region, ChunkGenerator generator,
            ChunkPos center, Footprint site) {
        boolean changed = false;
        for (Direction outward : new Direction[]{
                Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            // Search only the expected three-wide marker band, five to six
            // blocks inside the saved square. All three authored marker
            // stones must be visible in the readable reference ring.
            for (int lateral = -8; lateral <= 8; lateral++) {
                for (int inset = 4; inset <= 8; inset++) {
                    int x = site.markerX(outward, lateral, inset);
                    int z = site.markerZ(outward, lateral, inset);
                    if (!readable(region, center, x, z)) continue;
                    for (int y = 47; y <= 153; y++) {
                        BlockPos marker = new BlockPos(x, y, z);
                        Block block = region.getBlockState(marker).getBlock();
                        if (!entry(block)
                                || !threeMarkers(region, center, marker, outward, block))
                            continue;
                        for (int step = inset + 1;
                             step <= inset + APPROACH; step++) {
                            for (int lane = -1; lane <= 1; lane++) {
                                BlockPos column = marker.relative(outward, step)
                                        .relative(outward.getClockWise(), lane);
                                if (column.getX() >> 4 != center.x()
                                        || column.getZ() >> 4 != center.z()) continue;
                                changed |= clearExteriorColumn(region, generator, column);
                            }
                        }
                    }
                }
            }
        }
        return changed;
    }

    private static boolean threeMarkers(WorldGenRegion region, ChunkPos center,
            BlockPos middle, Direction outward, Block block) {
        for (int lane : new int[]{-1, 1}) {
            BlockPos flank = middle.relative(outward.getClockWise(), lane);
            if (!readable(region, center, flank.getX(), flank.getZ())
                    || region.getBlockState(flank).getBlock() != block) return false;
        }
        return true;
    }

    private static boolean clearExteriorColumn(WorldGenRegion region,
            ChunkGenerator generator, BlockPos column) {
        int y = generator.getBaseHeight(column.getX(), column.getZ(),
                Heightmap.Types.WORLD_SURFACE_WG, region,
                region.getLevel().getChunkSource().randomState()) - 1;
        if (y < 55 || y > 145 || y + CLEAR_HEIGHT >= region.getMaxY()) return false;
        BlockPos floor = column.atY(y);
        BlockState ground = region.getBlockState(floor);
        if (!ground.isSolid() || !ground.getFluidState().isEmpty()
                || region.getBlockEntity(floor) != null
                || !region.ensureCanWrite(floor)) return false;
        for (int height = 1; height <= CLEAR_HEIGHT; height++) {
            BlockPos pos = floor.above(height);
            BlockState state = region.getBlockState(pos);
            if (!region.ensureCanWrite(pos) || region.getBlockEntity(pos) != null
                    || !state.getFluidState().isEmpty()
                    || !state.isAir() && !naturalTree(state)
                            && !state.canBeReplaced()) return false;
        }
        region.setBlock(floor, Blocks.SMOOTH_STONE.defaultBlockState(), 2);
        for (int height = 1; height <= CLEAR_HEIGHT; height++) {
            BlockPos pos = floor.above(height);
            if (!region.getBlockState(pos).isAir())
                region.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
        return true;
    }

    private static boolean naturalTree(BlockState state) {
        if (!BuiltInRegistries.BLOCK.getKey(state.getBlock())
                .getNamespace().equals("minecraft")) return false;
        return state.is(BlockTags.LOGS)
                || state.getBlock() instanceof LeavesBlock
                        && !state.getValue(LeavesBlock.PERSISTENT);
    }

    private static boolean entry(Block block) {
        return block == EstateBlocks.ENTRY_N || block == EstateBlocks.ENTRY_E
                || block == EstateBlocks.ENTRY_S || block == EstateBlocks.ENTRY_W;
    }

    private static boolean readable(WorldGenRegion region, ChunkPos center, int x, int z) {
        return Math.abs((x >> 4) - center.x()) <= REFERENCE_RING
                && Math.abs((z >> 4) - center.z()) <= REFERENCE_RING
                && region.hasChunk(x >> 4, z >> 4);
    }

    private record Footprint(int minX, int minZ) {
        int maxX() { return minX + HALF * 2; }
        int maxZ() { return minZ + HALF * 2; }

        boolean near(ChunkPos center) {
            return center.getMaxBlockX() >= minX - APPROACH
                    && center.getMinBlockX() <= maxX() + APPROACH
                    && center.getMaxBlockZ() >= minZ - APPROACH
                    && center.getMinBlockZ() <= maxZ() + APPROACH;
        }

        boolean mansion(int x, int z) {
            return x >= minX + 94 && x <= minX + 140
                    && z >= minZ + 94 && z <= minZ + 140;
        }

        int markerX(Direction outward, int lateral, int inset) {
            return switch (outward) {
                case NORTH, SOUTH -> minX + HALF + lateral;
                case EAST -> maxX() - inset;
                case WEST -> minX + inset;
                default -> throw new IllegalArgumentException();
            };
        }

        int markerZ(Direction outward, int lateral, int inset) {
            return switch (outward) {
                case EAST, WEST -> minZ + HALF + lateral;
                case SOUTH -> maxZ() - inset;
                case NORTH -> minZ + inset;
                default -> throw new IllegalArgumentException();
            };
        }
    }
}
