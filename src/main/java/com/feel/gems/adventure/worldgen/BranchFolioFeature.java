package com.feel.gems.adventure.worldgen;

import com.feel.gems.GemsMod;
import com.feel.gems.adventure.DomainBlocks;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

/** Sparse archive stacks with a walkable fork in newly generated Branch Taiga. */
public final class BranchFolioFeature implements Feature {
    public static final MapCodec<BranchFolioFeature> CODEC =
            MapCodec.unit(new BranchFolioFeature());

    private record Cell(BlockPos pos, BlockState state) { }

    private BranchFolioFeature() { }

    public static void init() {
        Registry.register(BuiltInRegistries.FEATURE_TYPE,
                Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "branch_folios"), CODEC);
    }

    @Override
    public MapCodec<BranchFolioFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random,
                         BlockPos origin) {
        int localX = origin.getX() & 15;
        int localZ = origin.getZ() & 15;
        if (localX < 4 || localX > 11 || localZ < 4 || localZ > 11) return false;

        int centerY = surface(level, origin.getX(), origin.getZ());
        boolean xAcross = random.nextBoolean();
        int ux = xAcross ? 1 : 0;
        int uz = xAcross ? 0 : 1;
        int vx = -uz;
        int vz = ux;

        // Validate the entire fork before placing any block. Trees, water,
        // steep banks and any non-natural ground leave this chunk untouched.
        for (int v = -3; v <= 3; v++) {
            for (int u = -3; u <= 3; u++) {
                int x = origin.getX() + ux * u + vx * v;
                int z = origin.getZ() + uz * u + vz * v;
                int y = surface(level, x, z);
                if (Math.abs(y - centerY) > 1 || !naturalFloor(level, x, y, z)) return false;
                for (int height = 0; height <= 6; height++) {
                    BlockPos pos = new BlockPos(x, y + height, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)
                            || !occupiable(level, pos)) return false;
                }
            }
        }

        List<Cell> cells = new ArrayList<>(70);
        for (int side : new int[] {-1, 1}) {
            for (int v = -2; v <= 2; v++) {
                int u = side * 3;
                int x = origin.getX() + ux * u + vx * v;
                int z = origin.getZ() + uz * u + vz * v;
                int y = surface(level, x, z);
                int height = 4 + random.nextInt(3) - Math.abs(v);
                for (int rise = 0; rise < height; rise++) {
                    cells.add(new Cell(new BlockPos(x, y + rise, z),
                            rise == height - 1 && v == 0
                                    ? DomainBlocks.BRANCH_INDEX.defaultBlockState()
                                    : DomainBlocks.FOLIO_STONE.defaultBlockState()));
                }
            }
        }
        for (int v : new int[] {-3, 0, 3}) {
            BlockPos floor = new BlockPos(origin.getX() + vx * v,
                    surface(level, origin.getX() + vx * v,
                            origin.getZ() + vz * v) - 1,
                    origin.getZ() + vz * v);
            cells.add(new Cell(floor, DomainBlocks.BRANCH_INDEX.defaultBlockState()));
        }
        for (Cell cell : cells) {
            level.setBlock(cell.pos(), cell.state(), 2);
        }
        return true;
    }

    private static int surface(WorldGenLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    private static boolean naturalFloor(WorldGenLevel level, int x, int y, int z) {
        BlockPos floor = new BlockPos(x, y - 1, z);
        BlockState state = level.getBlockState(floor);
        return level.getBiome(floor).is(AdventureBiomes.BRANCH_TAIGA)
                && state.getFluidState().isEmpty()
                && (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)
                    || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL)
                    || state.is(Blocks.STONE) || state.is(Blocks.MOSS_BLOCK)
                    || state.is(Blocks.STONE_BRICKS));
    }

    private static boolean occupiable(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return level.ensureCanWrite(pos) && state.getFluidState().isEmpty()
                && (state.isAir() || state.canBeReplaced());
    }
}
