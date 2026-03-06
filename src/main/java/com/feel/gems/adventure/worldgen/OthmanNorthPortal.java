package com.feel.gems.adventure.worldgen;

import com.feel.gems.adventure.DomainBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Marks a natural two-wide north entry without replacing any terrain. */
final class OthmanNorthPortal {
    private static final BlockState POST = DomainBlocks.ARCHIVE_LIMESTONE.defaultBlockState();
    private static final BlockState TRIM = Blocks.POLISHED_DIORITE.defaultBlockState();
    private static final BlockState LINTEL = Blocks.DARK_PRISMARINE.defaultBlockState();
    private static final BlockState LIGHT = Blocks.SEA_LANTERN.defaultBlockState();

    private OthmanNorthPortal() { }

    static boolean place(WorldGenRegion region, BlockPos marker, int px, int pz) {
        if (Math.abs(px) + Math.abs(pz) != 1) return false;
        // The versioned marker is root-local (5,2,15). Local +Z faces the
        // public portico; local +X turns toward the study wing.
        int qx = pz;
        int qz = -px;
        int ox = marker.getX() - 15 * px - 5 * qx;
        int oz = marker.getZ() - 15 * pz - 5 * qz;
        int base = marker.getY() - 2;

        // In seed B the first two rows are untouched woodland, then the
        // existing root plinth supplies a complete stair and inlay arcade.
        for (int lx = -2; lx <= -1; lx++) {
            for (int lz = 4; lz <= 5; lz++) {
                BlockPos floor = point(ox, oz, px, pz, qx, qz, lx, lz, base);
                if (!available(region, floor) || !naturalDryFloor(region.getBlockState(floor))
                        || !clearHead(region, floor)) return false;
            }
        }
        for (int lx = 0; lx <= 4; lx++) {
            int level = lx == 0 ? 1 : lx == 1 ? 2 : lx <= 3 ? 3 : 4;
            for (int lz = 4; lz <= 5; lz++) {
                BlockPos floor = point(ox, oz, px, pz, qx, qz,
                        lx, lz, base + level);
                BlockState expected = lx == 0 ? Blocks.STONE_BRICKS.defaultBlockState()
                        : lx == 1 ? TRIM : lx <= 3 ? POST
                        : DomainBlocks.ARCHIVE_INLAY.defaultBlockState();
                if (!available(region, floor) || !region.getBlockState(floor).equals(expected)
                        || !clearHead(region, floor)) return false;
            }
        }
        for (int lx = 4; lx <= 5; lx++) {
            for (int lz = 4; lz <= 24; lz++) {
                BlockPos floor = point(ox, oz, px, pz, qx, qz,
                        lx, lz, base + 4);
                if (!available(region, floor)
                        || !region.getBlockState(floor).is(DomainBlocks.ARCHIVE_INLAY)
                        || !clearHead(region, floor)) return false;
            }
        }
        for (int lx = 5; lx <= 13; lx++) {
            for (int lz = 23; lz <= 24; lz++) {
                BlockPos floor = point(ox, oz, px, pz, qx, qz,
                        lx, lz, base + 4);
                if (!available(region, floor)
                        || !region.getBlockState(floor).is(DomainBlocks.ARCHIVE_INLAY)
                        || !clearHead(region, floor)) return false;
            }
        }

        // The portal stands on authored stone only. It consumes no leaf,
        // plant, soil, log, fluid or previously saved structure block.
        List<Placement> writes = new ArrayList<>();
        for (int lx = 0; lx <= 1; lx++) {
            for (int lz : new int[] {3, 6}) {
                for (int y = lx == 0 ? 2 : 3; y <= 8; y++) {
                    writes.add(new Placement(point(ox, oz, px, pz, qx, qz,
                            lx, lz, base + y), y == 8 ? TRIM : POST));
                }
            }
            for (int lz = 3; lz <= 6; lz++) {
                writes.add(new Placement(point(ox, oz, px, pz, qx, qz,
                        lx, lz, base + 9), LINTEL));
            }
        }
        for (int lz = 4; lz <= 5; lz++) {
            writes.add(new Placement(point(ox, oz, px, pz, qx, qz,
                    0, lz, base + 10), LIGHT));
        }
        for (Placement write : writes) {
            if (!available(region, write.pos()) || !region.ensureCanWrite(write.pos())) {
                return false;
            }
            BlockState current = region.getBlockState(write.pos());
            if (!current.isAir() && !current.equals(write.state())) return false;
        }
        boolean changed = false;
        for (Placement write : writes) {
            if (!region.getBlockState(write.pos()).equals(write.state())) {
                region.setBlock(write.pos(), write.state(), 2);
                changed = true;
            }
        }
        return changed;
    }

    private static BlockPos point(int ox, int oz, int px, int pz, int qx, int qz,
            int lx, int lz, int y) {
        return new BlockPos(ox + lx * qx + lz * px, y,
                oz + lx * qz + lz * pz);
    }

    private static boolean available(WorldGenRegion region, BlockPos pos) {
        return OthmanBankApproachFeature.withinWriteRadius(region, pos);
    }

    private static boolean naturalDryFloor(BlockState state) {
        return state.isSolid() && state.getFluidState().isEmpty()
                && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS)
                && !state.is(Blocks.SNOW_BLOCK);
    }

    private static boolean clearHead(WorldGenRegion region, BlockPos floor) {
        for (int rise = 1; rise <= 3; rise++) {
            BlockPos pos = floor.above(rise);
            if (!available(region, pos)) return false;
            BlockState state = region.getBlockState(pos);
            if (!state.isAir() && !state.is(Blocks.LEAF_LITTER)
                    && !state.is(Blocks.SHORT_GRASS) && !state.is(Blocks.TALL_GRASS)
                    && !state.is(Blocks.VINE)) return false;
        }
        return true;
    }

    private record Placement(BlockPos pos, BlockState state) { }
}
