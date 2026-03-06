package com.feel.gems.adventure.worldgen;

import com.feel.gems.GemsMod;
import com.feel.gems.adventure.DomainBlocks;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Finishes only the versioned Othman pieces identified in the current chunk. */
public final class OthmanBankApproachFeature implements Feature {
    public static final MapCodec<OthmanBankApproachFeature> CODEC =
            MapCodec.unit(new OthmanBankApproachFeature());
    private static final Identifier ID = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID,
            "othman_bank_approach");
    private static final ResourceKey<PlacedFeature> PLACED = ResourceKey.create(Registries.PLACED_FEATURE, ID);

    private OthmanBankApproachFeature() { }

    public static void init() {
        Registry.register(BuiltInRegistries.FEATURE_TYPE, ID, CODEC);
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld()
                        .or(BiomeSelectors.includeByKey(AdventureBiomes.ARCHIVE_COMMONS)),
                GenerationStep.Decoration.TOP_LAYER_MODIFICATION, PLACED);
    }

    @Override
    public MapCodec<OthmanBankApproachFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random,
                         BlockPos origin) {
        if (!(level instanceof WorldGenRegion region)) return false;
        ChunkPos center = region.getCenter();
        ChunkAccess current = region.getChunk(center.x(), center.z());
        // A saved FULL chunk, including one with player edits, is never
        // revisited by the v11 arrival clearing.
        if (current.getPersistedStatus().isOrAfter(ChunkStatus.FULL)) return false;
        LevelChunkSection[] sections = current.getSections();
        boolean changed = false;

        // Search only the current chunk's palette; never follow a remote
        // structure reference. Only versioned v3/v4 pieces contain these pairs.
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (!section.maybeHas(state -> state.is(Blocks.OBSERVER))) continue;
            int y0 = region.getMinY() + index * 16;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState markerState = section.getBlockState(x, y, z);
                        if (!markerState.is(Blocks.OBSERVER)) continue;
                        BlockPos marker = new BlockPos(center.getMinBlockX() + x,
                                y0 + y, center.getMinBlockZ() + z);
                        changed |= finishMarker(region, marker,
                                markerState.getValue(ObserverBlock.FACING));
                    }
                }
            }
        }
        changed |= clearVersionedNorthTrees(region, center);
        return changed;
    }

    /** Each decoration pass edits only its own new chunk of the north band. */
    private static boolean clearVersionedNorthTrees(WorldGenRegion region, ChunkPos center) {
        boolean changed = false;
        // A root marker can be in the next chunk when the exterior bank is
        // decorated. Read only the generation region's immediate chunk ring.
        for (int cz = center.z() - 1; cz <= center.z() + 1; cz++) {
            for (int cx = center.x() - 1; cx <= center.x() + 1; cx++) {
                if (!region.hasChunk(cx, cz)) continue;
                ChunkAccess chunk = region.getChunk(cx, cz);
                LevelChunkSection[] sections = chunk.getSections();
                for (int index = 0; index < sections.length; index++) {
                    LevelChunkSection section = sections[index];
                    if (!section.maybeHas(state -> state.is(Blocks.OBSERVER))) continue;
                    int y0 = region.getMinY() + index * 16;
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                BlockState state = section.getBlockState(x, y, z);
                                if (!state.is(Blocks.OBSERVER)) continue;
                                BlockPos marker = new BlockPos((cx << 4) + x,
                                        y0 + y, (cz << 4) + z);
                                Direction inward = state.getValue(ObserverBlock.FACING);
                                if (!inward.getAxis().isHorizontal()
                                        || !region.getBlockState(marker.above())
                                                .is(DomainBlocks.ARCHIVE_INLAY)
                                        || !region.getBlockState(marker.relative(inward))
                                                .is(Blocks.AMETHYST_BLOCK)
                                        || !region.getBlockState(marker.above(3))
                                                .is(Blocks.OCHRE_FROGLIGHT)) continue;
                                changed |= clearNorthBandInCenter(region, center,
                                        marker, inward);
                            }
                        }
                    }
                }
            }
        }
        return changed;
    }

    private static boolean clearNorthBandInCenter(WorldGenRegion region,
            ChunkPos center, BlockPos marker, Direction inward) {
        int ix = inward.getStepX();
        int iz = inward.getStepZ();
        int sx = -iz;
        int sz = ix;
        List<BlockPos> clear = new ArrayList<>();
        // Marker is local (23,1,3). This is only local x=22..24,
        // z=-4..0, and y=1..7. The z=0 stair is left intact below y=3.
        for (int step = 3; step <= 7; step++) {
            for (int side = -1; side <= 1; side++) {
                int x = marker.getX() - step * ix + side * sx;
                int z = marker.getZ() - step * iz + side * sz;
                if ((x >> 4) != center.x() || (z >> 4) != center.z()) continue;
                for (int y = marker.getY() + (step == 3 ? 2 : 0);
                        y <= marker.getY() + 6; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!withinWriteRadius(region, pos)
                            || region.getBlockEntity(pos) != null) return false;
                    BlockState state = region.getBlockState(pos);
                    if (!state.getFluidState().isEmpty()) return false;
                    boolean naturalLeaves = state.getBlock() instanceof LeavesBlock leaves
                            && state.is(BlockTags.LEAVES)
                            && !state.getValue(LeavesBlock.PERSISTENT);
                    boolean naturalLog = state.is(BlockTags.LOGS)
                            && BuiltInRegistries.BLOCK.getKey(state.getBlock())
                                    .getNamespace().equals("minecraft");
                    if (naturalLeaves || naturalLog || state.is(Blocks.LEAF_LITTER)) {
                        if (!region.ensureCanWrite(pos)) return false;
                        clear.add(pos);
                    } else if (!state.isAir() && !state.is(Blocks.SHORT_GRASS)
                            && !state.is(Blocks.TALL_GRASS) && !state.is(Blocks.VINE)) {
                        return false;
                    }
                }
            }
        }
        // Refuse unusually dense or foreign obstacles rather than widening
        // the clearing or silently cutting through another feature.
        if (clear.isEmpty() || clear.size() > 64) return false;
        for (BlockPos pos : clear) {
            region.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
        System.out.println("OTHMAN_NORTH_TREE_CLEAR center=" + center
                + " marker=" + marker + " cells=" + clear.size());
        return true;
    }

    private static boolean finishMarker(WorldGenRegion region, BlockPos marker,
                                        Direction direction) {
        if (!direction.getAxis().isHorizontal()) return false;
        if (region.getBlockState(marker.above()).is(Blocks.POLISHED_DIORITE)
                && region.getBlockState(marker.relative(direction)).is(Blocks.AMETHYST_BLOCK)) {
            return OthmanNorthPortal.place(region, marker,
                    direction.getStepX(), direction.getStepZ());
        }
        if (region.getBlockState(marker.above()).is(DomainBlocks.ARCHIVE_LIMESTONE)) {
            return OthmanDryApproach.place(region, marker,
                    direction.getStepX(), direction.getStepZ());
        }
        if (region.getBlockState(marker.above()).isAir()) {
            if (region.getBlockState(marker.relative(direction)).is(DomainBlocks.ARCHIVE_INLAY)) {
                return dryBankBeyondLanding(region, marker, direction)
                        && clearBank(region, marker, direction);
            }
            return clearBank(region, marker, direction);
        }
        return false;
    }

    private static boolean dryBankBeyondLanding(WorldGenRegion region, BlockPos marker,
                                                Direction inward) {
        // The accepted C shore remains dry for these three cells. B has only
        // two short grass ledges before open water, so its v4 wing is left
        // entirely alone while the separate north portal supplies entry.
        for (int step = 3; step <= 5; step++) {
            BlockPos column = marker.relative(inward.getOpposite(), step);
            if (!withinWriteRadius(region, column)) return false;
            boolean found = false;
            for (int y = marker.getY() + 1; y >= marker.getY() - 1; y--) {
                BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
                BlockState state = region.getBlockState(pos);
                if (state.isAir() || state.is(BlockTags.LEAVES)
                        || state.is(Blocks.LEAF_LITTER) || state.is(Blocks.SHORT_GRASS)
                        || state.is(Blocks.TALL_GRASS) || state.is(Blocks.VINE)) continue;
                if (!state.isSolid() || !state.getFluidState().isEmpty()) return false;
                found = true;
                break;
            }
            if (!found) return false;
        }
        return true;
    }

    private static boolean clearBank(WorldGenRegion region, BlockPos marker, Direction inward) {
        int ix = inward.getStepX();
        int iz = inward.getStepZ();
        int sx = -iz;
        int sz = ix;
        List<BlockPos> clear = new ArrayList<>();
        // The marker is two cells inward from the accepted landing's end.
        // One authored inlay and four exterior cells, three blocks wide.
        for (int step = -1; step <= 4; step++) {
            for (int side = -1; side <= 1; side++) {
                int x = marker.getX() - (2 + step) * ix + side * sx;
                int z = marker.getZ() - (2 + step) * iz + side * sz;
                for (int y = marker.getY(); y <= marker.getY() + 7; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!withinWriteRadius(region, pos)) return false;
                    BlockState state = region.getBlockState(pos);
                    if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                            || state.is(Blocks.LEAF_LITTER) || state.is(Blocks.VINE)) {
                        clear.add(pos);
                    }
                }
            }
        }
        if (clear.stream().anyMatch(pos -> !region.ensureCanWrite(pos))) return false;
        for (BlockPos pos : clear) {
            region.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
        return !clear.isEmpty();
    }

    static boolean withinWriteRadius(WorldGenRegion region, BlockPos pos) {
        ChunkPos center = region.getCenter();
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        return Math.abs(cx - center.x()) <= 1 && Math.abs(cz - center.z()) <= 1
                && region.hasChunk(cx, cz);
    }
}
