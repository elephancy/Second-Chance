package com.feel.gems.adventure.worldgen;

import com.feel.gems.adventure.DomainBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** A short dry-land ramp into the library's existing perimeter walk. */
final class OthmanDryApproach {
    private static final BlockState FLOOR = DomainBlocks.ARCHIVE_INLAY.defaultBlockState();
    private static final BlockState SUPPORT = Blocks.POLISHED_DIORITE.defaultBlockState();
    private static final BlockState PIER = DomainBlocks.ARCHIVE_LIMESTONE.defaultBlockState();
    private static final BlockState CAP = Blocks.SEA_LANTERN.defaultBlockState();

    private OthmanDryApproach() { }

    static boolean place(WorldGenRegion region, BlockPos marker, int px, int pz) {
        if (Math.abs(px) + Math.abs(pz) != 1) return false;
        // The buried v3 marker is at root-local (3,2,2). Its amethyst neighbor
        // points along local +Z, toward the public portico. The marker is also
        // the root-side ramp endpoint, so this runs from one anchor chunk.
        int wx = -pz;
        int wz = px;
        int cx = marker.getX() + 21 * px - 20 * wx;
        int cz = marker.getZ() + 21 * pz - 20 * wz;
        int base = marker.getY() - 2;
        List<Placement> writes = new ArrayList<>();
        int removedLeaves = 0;
        int removedOther = 0;

        // The first seven cells must be dry natural ground close to the hall's
        // base. This selects the B-like west shore. C's accepted bank approach
        // lies below this contour and is left exactly as generated.
        for (int u = -37; u <= -31; u++) {
            for (int v = 20; v <= 21; v++) {
                BlockPos pos = point(cx, cz, px, pz, wx, wz, u, v, base + 1);
                if (!available(region, pos)) return false;
                int ground = ground(region, pos, base - 5);
                if (ground < base - 1 || ground > base + 1) return false;
            }
        }

        // Two lanes rise by at most one block at a time, meeting the existing
        // south-edge promenade at u=-21. The rest of that authored promenade
        // continues to u=21 and turns to the active portico at v=0.
        for (int u = -37; u <= -21; u++) {
            int floorY = base + (u <= -26 ? 1 : u <= -23 ? 2 : 3);
            for (int v = 20; v <= 21; v++) {
                BlockPos floor = point(cx, cz, px, pz, wx, wz, u, v, floorY);
                if (!available(region, floor)) return false;
                int ground = ground(region, floor, base - 5);
                if (ground < base - 2 || ground >= floorY || floorY - ground - 1 > 2) {
                    // The final authored stone surface already occupies its
                    // own floor cell and has solid support below it.
                    if (!(u == -21 && authoredFloor(region.getBlockState(floor)))) return false;
                }
                if (u != -21 || !authoredFloor(region.getBlockState(floor))) {
                    BlockState state = region.getBlockState(floor);
                    if (!vacant(state) && !state.equals(FLOOR)) return false;
                    writes.add(new Placement(floor, FLOOR));
                    if (state.is(BlockTags.LEAVES)) removedLeaves++;
                    else if (!state.isAir() && !state.equals(FLOOR)) removedOther++;
                }
                for (int y = ground + 1; y < floorY; y++) {
                    BlockPos support = new BlockPos(floor.getX(), y, floor.getZ());
                    BlockState state = region.getBlockState(support);
                    if (!vacant(state) && !state.equals(SUPPORT)) return false;
                    writes.add(new Placement(support, SUPPORT));
                    if (state.is(BlockTags.LEAVES)) removedLeaves++;
                    else if (!state.isAir() && !state.equals(SUPPORT)) removedOther++;
                }
                for (int y = floorY + 1; y <= floorY + 3; y++) {
                    BlockPos head = new BlockPos(floor.getX(), y, floor.getZ());
                    BlockState state = region.getBlockState(head);
                    if (!vacant(state)) return false;
                    if (!state.isAir()) {
                        writes.add(new Placement(head, Blocks.AIR.defaultBlockState()));
                        if (state.is(BlockTags.LEAVES)) removedLeaves++;
                        else removedOther++;
                    }
                }
            }
        }
        if (removedLeaves > 32 || removedOther > 20) return false;

        // A small illuminated gate makes the otherwise quiet dry entry
        // discoverable from the surrounding woodland. It clears no trunks.
        for (int v : new int[] {19, 22}) {
            BlockPos foot = point(cx, cz, px, pz, wx, wz, -35, v, base + 1);
            if (!available(region, foot)) return false;
            int ground = ground(region, foot, base - 5);
            if (ground < base - 1 || ground > base || base + 1 - ground > 2) return false;
            for (int y = ground + 1; y <= base + 4; y++) {
                BlockPos pos = new BlockPos(foot.getX(), y, foot.getZ());
                BlockState state = region.getBlockState(pos);
                if (!vacant(state) && !state.equals(PIER)) return false;
                writes.add(new Placement(pos, PIER));
            }
            BlockPos lamp = new BlockPos(foot.getX(), base + 5, foot.getZ());
            if (!vacant(region.getBlockState(lamp)) && !region.getBlockState(lamp).equals(CAP)) return false;
            writes.add(new Placement(lamp, CAP));
        }
        for (int v = 20; v <= 21; v++) {
            BlockPos lintel = point(cx, cz, px, pz, wx, wz, -35, v, base + 5);
            BlockState state = region.getBlockState(lintel);
            if (!vacant(state) && !state.equals(PIER)) return false;
            writes.add(new Placement(lintel, PIER));
        }

        // One invocation owns both adjacent ramp chunks. Commit only after
        // every cell has passed the formed-ground and write-radius checks.
        if (writes.stream().anyMatch(write -> !available(region, write.pos())
                || !region.ensureCanWrite(write.pos()))) return false;
        boolean changed = false;
        for (Placement write : writes) {
            BlockPos pos = write.pos();
            if (!region.getBlockState(pos).equals(write.state())) {
                region.setBlock(pos, write.state(), 2);
                changed = true;
            }
        }
        return changed;
    }

    private static BlockPos point(int cx, int cz, int px, int pz, int wx, int wz,
            int u, int v, int y) {
        return new BlockPos(cx + px * u + wx * v, y, cz + pz * u + wz * v);
    }

    private static boolean available(WorldGenRegion region, BlockPos pos) {
        return OthmanBankApproachFeature.withinWriteRadius(region, pos);
    }

    private static int ground(WorldGenRegion region, BlockPos floor, int lowest) {
        for (int y = floor.getY() - 1; y >= lowest; y--) {
            BlockState state = region.getBlockState(new BlockPos(floor.getX(), y, floor.getZ()));
            if (vacant(state)) continue;
            if (state.isSolid() && state.getFluidState().isEmpty()) return y;
            return Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }

    private static boolean vacant(BlockState state) {
        return state.isAir() || state.is(BlockTags.LEAVES) || state.is(Blocks.LEAF_LITTER)
                || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.VINE) || state.is(Blocks.SNOW);
    }

    private static boolean authoredFloor(BlockState state) {
        return state.is(DomainBlocks.ARCHIVE_LIMESTONE)
                || state.is(DomainBlocks.ARCHIVE_INLAY)
                || state.is(Blocks.POLISHED_DIORITE);
    }

    private record Placement(BlockPos pos, BlockState state) { }
}
