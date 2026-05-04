package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.estate.FourRoadsEstateStructure;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;

/** Opt-in saved-world voxel connectivity proof; never runs during normal play. */
final class EstateLoadedRouteProbe {
    private static final int MAX_VISITED = 180_000;
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private EstateLoadedRouteProbe() { }

    static boolean verify(ServerLevel level, StructureStart start,
                          Map<Character, BlockPos> gates, BlockPos foyer) {
        if (start == null || foyer == null) return false;
        Map<Integer, StructurePiece> modules = new HashMap<>();
        for (StructurePiece piece : start.getPieces()) {
            if (!(piece instanceof PoolElementStructurePiece pooled)
                    || !(pooled.getElement() instanceof SinglePoolElement single))
                return false;
            String name = single.getTemplateLocation().getPath();
            if (name.length() < 3 || name.charAt(name.length() - 2) != '_') return false;
            int z = name.charAt(name.length() - 3) - '0';
            int x = name.charAt(name.length() - 1) - '0';
            if (x < 0 || x > 4 || z < 0 || z > 4) return false;
            modules.put(z * 5 + x, piece);
        }
        if (modules.size() != 25) return false;
        for (char wing : new char[]{'N', 'E', 'S', 'W'}) {
            BlockPos marker = gates.get(wing);
            if (marker == null || !verifyWing(level, start.getBoundingBox(),
                    modules, wing, marker, foyer)) return false;
        }
        return true;
    }

    private static boolean verifyWing(ServerLevel level, BoundingBox body,
                                      Map<Integer, StructurePiece> modules,
                                      char wing, BlockPos marker, BlockPos foyer) {
        int west = marker.getX() - body.minX();
        int east = body.maxX() - marker.getX();
        int north = marker.getZ() - body.minZ();
        int south = body.maxZ() - marker.getZ();
        int distance = Math.min(Math.min(west, east), Math.min(north, south));
        if (distance < 5 || distance > 6) return false;
        int dx = distance == west ? -1 : distance == east ? 1 : 0;
        int dz = dx != 0 ? 0 : distance == north ? -1 : 1;
        int outerX = marker.getX() + dx * (distance + 8);
        int outerZ = marker.getZ() + dz * (distance + 8);
        LevelChunk outer = level.getChunkSource().getChunkNow(outerX >> 4, outerZ >> 4);
        if (outer == null) return false;
        BlockPos origin = new BlockPos(outerX,
                outer.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        outerX & 15, outerZ & 15), outerZ);
        if (!walkable(level, origin) || !walkable(level, foyer)) return false;

        ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        Map<BlockPos, BlockPos> previous = new HashMap<>();
        frontier.add(origin);
        previous.put(origin, origin);
        while (!frontier.isEmpty() && previous.size() < MAX_VISITED) {
            BlockPos current = frontier.removeFirst();
            if (current.equals(foyer)) break;
            for (int[] direction : DIRECTIONS) {
                for (int rise = -1; rise <= 1; rise++) {
                    BlockPos next = current.offset(direction[0], rise, direction[1]);
                    if (previous.containsKey(next) || !inside(body, marker, dx, dz,
                            distance, next) || !walkable(level, next)) continue;
                    previous.put(next, current);
                    frontier.addLast(next);
                }
            }
        }
        if (!previous.containsKey(foyer)) {
            System.out.println("FOUR_ROADS_ESTATE_VOXEL_ROUTE wing=" + wing
                    + " status=NO_PATH visited=" + previous.size()
                    + " start=" + origin + " foyer=" + foyer);
            return false;
        }

        List<BlockPos> reverse = new ArrayList<>();
        for (BlockPos at = foyer; !at.equals(origin); at = previous.get(at))
            reverse.add(at);
        reverse.add(origin);
        List<Integer> expected = new ArrayList<>();
        int module = switch (wing) { case 'N' -> 2; case 'E' -> 14;
            case 'S' -> 22; default -> 10; };
        while (module != -1) {
            expected.add(module);
            module = FourRoadsEstateStructure.parentModule(module);
        }
        int matched = 0;
        List<Integer> visitedModules = new ArrayList<>();
        for (int i = reverse.size() - 1; i >= 0; i--) {
            BlockPos at = reverse.get(i);
            int id = moduleAt(modules, at);
            if (id >= 0 && (visitedModules.isEmpty()
                    || visitedModules.getLast() != id)) visitedModules.add(id);
            if (matched < expected.size() && id == expected.get(matched)) matched++;
            // Reversing the route must remain a dry two-block-high walk.
            if (!walkable(level, at)) return false;
        }
        boolean valid = matched == expected.size() && reverse.size() >= 100;
        System.out.println("FOUR_ROADS_ESTATE_VOXEL_ROUTE wing=" + wing
                + " status=" + (valid ? "CONNECTED" : "WRONG_WING")
                + " outwardSteps=" + (reverse.size() - 1)
                + " returnSteps=" + (reverse.size() - 1)
                + " visited=" + previous.size() + " modules=" + visitedModules
                + " expected=" + expected);
        return valid;
    }

    private static int moduleAt(Map<Integer, StructurePiece> modules, BlockPos floor) {
        for (var entry : modules.entrySet()) {
            BoundingBox box = entry.getValue().getBoundingBox();
            if (floor.getX() >= box.minX() && floor.getX() <= box.maxX()
                    && floor.getZ() >= box.minZ() && floor.getZ() <= box.maxZ()
                    && floor.getY() == box.minY() + 4) return entry.getKey();
        }
        return -1;
    }

    private static boolean inside(BoundingBox body, BlockPos marker, int dx, int dz,
                                  int distance, BlockPos floor) {
        int x = floor.getX();
        int z = floor.getZ();
        if (floor.getY() < body.minY() - 2 || floor.getY() > body.maxY() - 5)
            return false;
        if (x >= body.minX() && x <= body.maxX()
                && z >= body.minZ() && z <= body.maxZ()) return true;
        int along = (x - marker.getX()) * dx + (z - marker.getZ()) * dz;
        int lateral = (x - marker.getX()) * dz + (z - marker.getZ()) * dx;
        return along > distance && along <= distance + 8
                && Math.abs(lateral) <= 1;
    }

    private static boolean walkable(ServerLevel level, BlockPos floor) {
        if (!level.getWorldBorder().isWithinBounds(floor)
                || level.getChunkSource().getChunkNow(floor.getX() >> 4,
                        floor.getZ() >> 4) == null) return false;
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
}
