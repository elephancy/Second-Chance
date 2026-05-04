package com.feel.gems.adventure.estate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Test-only shortest dry route through a saved wing and its four real seams. */
final class EstateConnectedPath {
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private EstateConnectedPath() { }

    static List<BlockPos> forWing(ServerLevel level, EstateRouteSite.Site site, char wing) {
        EstateRouteSite.Gate gate = site.gate(wing);
        if (gate == null) return List.of();
        List<BlockPos> route = new ArrayList<>();
        BlockPos from = gate.marker();
        int child = switch (wing) {
            case 'N' -> 2;
            case 'E' -> 14;
            case 'S' -> 22;
            case 'W' -> 10;
            default -> -1;
        };
        if (child < 0) return List.of();
        for (int i = 0; i < 4; i++) {
            EstateRouteSite.Seam seam = site.seam(child);
            if (seam == null || EstateSiteIntegrity.inspectSeamLoaded(level, site, seam)
                    != EstateSiteIntegrity.Result.READY) return List.of();
            List<BlockPos> room = room(level, site.module(child), from, seam.from());
            if (room.isEmpty()) return List.of();
            append(route, room);
            for (int step = 1; step <= 12; step++) append(route, List.of(seam.at(step, 0)));
            from = seam.to();
            child = seam.parent();
        }
        append(route, room(level, site.module(12), from, site.foyer()));
        if (route.size() < 2 || route.size() > 600
                || !route.getFirst().equals(gate.marker())
                || !route.getLast().equals(site.foyer())) return List.of();
        BlockPos stand = route.get(route.size() - 2);
        if (stand.distSqr(site.foyer()) != 1 || !walkable(level, stand)
                || EstateBlocks.isControl(level.getBlockState(stand).getBlock()))
            return List.of();
        // Stop the normal walk beside the contact block. The connected use
        // packet must make the first foyer claim, not stepOn during staging.
        return List.copyOf(route.subList(0, route.size() - 1));
    }

    private static void append(List<BlockPos> route, List<BlockPos> part) {
        for (BlockPos at : part)
            if (route.isEmpty() || !route.getLast().equals(at)) route.add(at);
    }

    private static List<BlockPos> room(ServerLevel level, BoundingBox box,
                                       BlockPos from, BlockPos to) {
        if (!walkable(level, from) || !walkable(level, to)) return List.of();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        open.add(from);
        parent.put(from, from);
        while (!open.isEmpty() && parent.size() < 2500) {
            BlockPos at = open.removeFirst();
            if (at.equals(to)) break;
            for (int[] direction : DIRECTIONS) {
                BlockPos next = at.offset(direction[0], 0, direction[1]);
                if (next.getX() < box.minX() || next.getX() > box.maxX()
                        || next.getZ() < box.minZ() || next.getZ() > box.maxZ()
                        || parent.containsKey(next) || !walkable(level, next)) continue;
                parent.put(next, at);
                open.addLast(next);
            }
        }
        if (!parent.containsKey(to)) return List.of();
        List<BlockPos> reverse = new ArrayList<>();
        for (BlockPos at = to; !at.equals(from); at = parent.get(at)) reverse.add(at);
        reverse.add(from);
        return reverse.reversed();
    }

    static boolean walkable(ServerLevel level, BlockPos floor) {
        if (level.getChunkSource().getChunkNow(floor.getX() >> 4, floor.getZ() >> 4) == null)
            return false;
        var support = level.getBlockState(floor);
        if (support.is(BlockTags.LOGS) || support.is(BlockTags.LEAVES)
                || support.getCollisionShape(level, floor).isEmpty()
                || !support.getFluidState().isEmpty()) return false;
        for (int rise = 1; rise <= 2; rise++) {
            BlockPos head = floor.above(rise);
            var state = level.getBlockState(head);
            if (!state.getCollisionShape(level, head).isEmpty()
                    || !state.getFluidState().isEmpty()) return false;
        }
        return true;
    }

    static String encode(List<BlockPos> route) {
        BlockPos origin = route.getFirst();
        StringBuilder output = new StringBuilder("ESTATEQA|PATH|" + origin.getX()
                + "|" + origin.getY() + "|" + origin.getZ() + "|");
        for (int i = 1; i < route.size(); i++) {
            BlockPos from = route.get(i - 1);
            BlockPos to = route.get(i);
            int dx = to.getX() - from.getX();
            int dy = to.getY() - from.getY();
            int dz = to.getZ() - from.getZ();
            int direction = dx == 1 && dz == 0 ? 0 : dx == -1 && dz == 0 ? 1
                    : dx == 0 && dz == 1 ? 2 : dx == 0 && dz == -1 ? 3 : -1;
            if (direction < 0 || Math.abs(dy) > 1)
                throw new IllegalStateException("Estate route has a nonadjacent step");
            output.append((char) ('A' + direction * 3 + dy + 1));
        }
        return output.toString();
    }
}
