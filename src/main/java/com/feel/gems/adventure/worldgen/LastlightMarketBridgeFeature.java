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
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** A new-start waterfront bridge, placed only when every cell is open. */
public final class LastlightMarketBridgeFeature implements Feature {
    public static final MapCodec<LastlightMarketBridgeFeature> CODEC =
            MapCodec.unit(new LastlightMarketBridgeFeature());
    private static final Identifier ID = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID,
            "lastlight_market_bridge");
    private static final ResourceKey<PlacedFeature> PLACED =
            ResourceKey.create(Registries.PLACED_FEATURE, ID);

    private LastlightMarketBridgeFeature() { }

    public static void init() {
        Registry.register(BuiltInRegistries.FEATURE_TYPE, ID, CODEC);
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld()
                        .or(BiomeSelectors.includeByKey(AdventureBiomes.LASTLIGHT_YARD)),
                GenerationStep.Decoration.TOP_LAYER_MODIFICATION, PLACED);
    }

    @Override
    public MapCodec<LastlightMarketBridgeFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random,
                         BlockPos origin) {
        if (!(level instanceof WorldGenRegion region)) return false;
        ChunkPos center = region.getCenter();
        ChunkAccess current = region.getChunk(center.x(), center.z());
        LevelChunkSection[] sections = current.getSections();
        boolean changed = false;
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (!section.maybeHas(state -> state.is(Blocks.OBSERVER))) continue;
            int y0 = region.getMinY() + index * 16;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!state.is(Blocks.OBSERVER)) continue;
                        BlockPos marker = new BlockPos(center.getMinBlockX() + x,
                                y0 + y, center.getMinBlockZ() + z);
                        if (!region.getBlockState(marker.above())
                                .is(DomainBlocks.STATION_ASHLAR)
                                || !region.getBlockState(marker.above(2))
                                        .is(Blocks.CUT_SANDSTONE)) continue;
                        changed |= bridge(region, marker,
                                state.getValue(ObserverBlock.FACING));
                    }
                }
            }
        }
        return changed;
    }

    private static boolean bridge(WorldGenRegion region, BlockPos marker, Direction outward) {
        if (!outward.getAxis().isHorizontal()) return false;
        int px = outward.getStepX();
        int pz = outward.getStepZ();
        int sx = -pz;
        int sz = px;
        int baseY = marker.getY() - 2;
        ChunkPos center = region.getCenter();
        List<Placement> writes = new ArrayList<>();

        // One marker owns the whole bridge. A crossing that straddles chunks,
        // meets terrain or vegetation, or has no dry shore is left untouched.
        for (int distance = 1; distance <= 13; distance++) {
            int floorY = baseY + Math.max(0, 4 - (distance - 1) / 3);
            for (int side = -1; side <= 1; side++) {
                BlockPos floor = new BlockPos(marker.getX() + px * distance + sx * side,
                        floorY, marker.getZ() + pz * distance + sz * side);
                if (!clear(region, center, floor, 4)) return false;
                if (distance == 13) {
                    BlockState shore = region.getBlockState(
                            new BlockPos(floor.getX(), baseY - 1, floor.getZ()));
                    if (!shore.isSolid() || !shore.getFluidState().isEmpty()) return false;
                }
                writes.add(new Placement(floor,
                        distance % 4 == 0 ? DomainBlocks.STATION_ASHLAR.defaultBlockState()
                                : Blocks.DARK_OAK_PLANKS.defaultBlockState()));
            }
            if (distance == 13) continue;
            for (int side : new int[] {-2, 2}) {
                BlockPos railBase = new BlockPos(marker.getX() + px * distance + sx * side,
                        floorY, marker.getZ() + pz * distance + sz * side);
                if (!clear(region, center, railBase, 3)) return false;
                writes.add(new Placement(railBase, Blocks.DARK_OAK_PLANKS.defaultBlockState()));
                writes.add(new Placement(railBase.above(),
                        Blocks.DARK_OAK_FENCE.defaultBlockState()));
            }
        }
        for (Placement write : writes) {
            region.setBlock(write.pos(), write.state(), 2);
        }
        return true;
    }

    private static boolean clear(WorldGenRegion region, ChunkPos center,
                                 BlockPos floor, int height) {
        if ((floor.getX() >> 4) != center.x() || (floor.getZ() >> 4) != center.z()) {
            return false;
        }
        for (int rise = 0; rise < height; rise++) {
            BlockPos pos = floor.above(rise);
            if (!region.ensureCanWrite(pos) || !region.getBlockState(pos).isAir()) {
                return false;
            }
        }
        return true;
    }

    private record Placement(BlockPos pos, BlockState state) { }
}
