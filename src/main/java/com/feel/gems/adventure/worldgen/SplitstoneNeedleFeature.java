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

/** Sparse, paired rock fins with a clear walking gap in Splitstone Heights. */
public final class SplitstoneNeedleFeature implements Feature {
    public static final MapCodec<SplitstoneNeedleFeature> CODEC =
            MapCodec.unit(new SplitstoneNeedleFeature());

    private record Cell(BlockPos pos, BlockState state) { }

    private SplitstoneNeedleFeature() { }

    public static void init() {
        Registry.register(BuiltInRegistries.FEATURE_TYPE,
                Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "splitstone_needles"), CODEC);
    }

    @Override
    public MapCodec<SplitstoneNeedleFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random,
                         BlockPos origin) {
        // Keep the entire 9 x 9 silhouette inside its start chunk. Apart from
        // avoiding cut-off fins, this cannot reach a built neighbouring chunk.
        int localX = origin.getX() & 15;
        int localZ = origin.getZ() & 15;
        if (localX < 4 || localX > 11 || localZ < 4 || localZ > 11) return false;

        int centerY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                origin.getX(), origin.getZ());

        // u crosses the cleft; v runs through it. The two faces sit five
        // blocks apart, so the centre remains an open route in either rotation.
        boolean xAcross = random.nextBoolean();
        int ux = xAcross ? 1 : 0;
        int uz = xAcross ? 0 : 1;
        int vx = -uz;
        int vz = ux;

        // Keep spruce trunks and low branches away from both entrances and
        // the sides of the gap. They can leave a technically open path that
        // reads as a wall of foliage from walking height.
        for (int v = -4; v <= 4; v++) {
            for (int u = -2; u <= 2; u++) {
                int x = origin.getX() + ux * u + vx * v;
                int z = origin.getZ() + uz * u + vz * v;
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                for (int y = surface - 1; y <= surface + 5; y++) {
                    BlockState state = level.getBlockState(new BlockPos(x, y, z));
                    if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) return false;
                }
            }
        }

        for (int v = -3; v <= 3; v++) {
            for (int u = -1; u <= 1; u++) {
                int x = origin.getX() + ux * u + vx * v;
                int z = origin.getZ() + uz * u + vz * v;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (Math.abs(y - centerY) > 2 || !isNaturalFloor(level, x, y, z)) return false;
                for (int above = 0; above < 3; above++) {
                    if (!canOccupy(level, new BlockPos(x, y + above, z))) return false;
                }
            }
        }

        List<Cell> cells = new ArrayList<>(220);
        for (int side : new int[] {-1, 1}) {
            for (int v = -2; v <= 2; v++) {
                int x = origin.getX() + ux * side * 3 + vx * v;
                int z = origin.getZ() + uz * side * 3 + vz * v;
                int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (Math.abs(groundY - centerY) > 2 || !isNaturalFloor(level, x, groundY, z)) {
                    return false;
                }
                int height = 12 + random.nextInt(4) - 2 * Math.abs(v);
                if (groundY + height >= level.getMaxY()) return false;

                for (int y = 0; y < height; y++) {
                    // The last three blocks lean out of the cleft. Join the
                    // bend at its base, then let unequal crowns break the line.
                    int across = side * (y >= height - 3 ? 4 : 3);
                    BlockPos at = offset(origin, ux, uz, vx, vz, across, v, groundY + y);
                    cells.add(new Cell(at, stoneFor(y, height, v, side)));
                    if (y == height - 3) {
                        cells.add(new Cell(offset(origin, ux, uz, vx, vz,
                                side * 3, v, groundY + y), Blocks.CALCITE.defaultBlockState()));
                    }
                    if (y < 3) {
                        cells.add(new Cell(offset(origin, ux, uz, vx, vz,
                                side * 4, v, groundY + y),
                                DomainBlocks.SPLITSTONE.defaultBlockState()));
                    }
                }
            }
        }

        // Fail before changing the chunk if one fin would cut through a tree,
        // liquid, or another structure. Replaceable grasses are harmless.
        for (Cell cell : cells) {
            if (!canOccupy(level, cell.pos())) return false;
        }
        for (Cell cell : cells) {
            level.setBlock(cell.pos(), cell.state(), 2);
        }
        return true;
    }

    private static BlockPos offset(BlockPos origin, int ux, int uz, int vx, int vz,
                                   int u, int v, int y) {
        return new BlockPos(origin.getX() + ux * u + vx * v, y,
                origin.getZ() + uz * u + vz * v);
    }

    private static BlockState stoneFor(int y, int height, int v, int side) {
        if (y < height / 3) return DomainBlocks.SPLITSTONE.defaultBlockState();
        if ((y + v + side + 16) % 6 == 0) return Blocks.STONE.defaultBlockState();
        return Blocks.CALCITE.defaultBlockState();
    }

    private static boolean isNaturalFloor(WorldGenLevel level, int x, int y, int z) {
        BlockPos floor = new BlockPos(x, y - 1, z);
        BlockState state = level.getBlockState(floor);
        return level.getBiome(floor).is(AdventureBiomes.SPLITSTONE_HEIGHTS)
                && state.getFluidState().isEmpty()
                && (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)
                    || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL)
                    || state.is(Blocks.STONE) || state.is(Blocks.ANDESITE)
                    || state.is(Blocks.CALCITE) || state.is(Blocks.GRAVEL)
                    || state.is(DomainBlocks.SPLITSTONE));
    }

    private static boolean canOccupy(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return level.ensureCanWrite(pos) && state.getFluidState().isEmpty()
                && (state.isAir() || state.canBeReplaced());
    }
}
