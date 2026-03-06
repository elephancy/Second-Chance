package com.feel.gems.adventure.boss;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bounded walkable-route search; world reads are supplied by the item on use. */
public final class RouteSurvey {
    public enum Direction {
        NORTH(0, -1, "north"), EAST(1, 0, "east"),
        SOUTH(0, 1, "south"), WEST(-1, 0, "west");

        final int dx;
        final int dz;
        public final String label;

        Direction(int dx, int dz, String label) {
            this.dx = dx;
            this.dz = dz;
            this.label = label;
        }
    }

    public record Point(int x, int y, int z) {
        Point move(Direction direction, int rise) {
            return new Point(x + direction.dx, y + rise, z + direction.dz);
        }
    }

    public record Path(Direction direction, List<Point> points, boolean daylight) { }

    public interface Terrain {
        boolean walkable(Point point);
        boolean daylight(Point point);
    }

    private record Visit(Point point, Point parent, Direction first, int steps) { }

    private RouteSurvey() { }

    public static List<Path> find(Terrain terrain) {
        Point origin = new Point(0, 0, 0);
        Map<Point, Visit> seen = new HashMap<>();
        ArrayDeque<Visit> queue = new ArrayDeque<>();
        Visit start = new Visit(origin, null, null, 0);
        queue.add(start);
        seen.put(origin, start);
        Map<Direction, Visit> best = new HashMap<>();
        while (!queue.isEmpty() && seen.size() < 600) {
            Visit current = queue.removeFirst();
            if (current.first() != null && current.steps() >= 5) {
                Visit previous = best.get(current.first());
                if (previous == null || score(current, terrain) > score(previous, terrain)) {
                    best.put(current.first(), current);
                }
            }
            for (Direction direction : Direction.values()) {
                for (int rise : new int[]{0, 1, -1}) {
                    Point next = current.point().move(direction, rise);
                    if (Math.max(Math.abs(next.x()), Math.abs(next.z())) > 14
                            || Math.abs(next.y()) > 3 || seen.containsKey(next)
                            || !terrain.walkable(next)) continue;
                    Visit visit = new Visit(next, current.point(),
                            current.first() == null ? direction : current.first(),
                            current.steps() + 1);
                    seen.put(next, visit);
                    queue.addLast(visit);
                    if (seen.size() >= 600) break;
                }
                if (seen.size() >= 600) break;
            }
        }
        return best.values().stream()
                .sorted(Comparator.comparingInt((Visit visit) -> score(visit, terrain)).reversed())
                .limit(2)
                .map(visit -> new Path(visit.first(), reconstruct(visit, seen),
                        terrain.daylight(visit.point())))
                .toList();
    }

    private static int score(Visit visit, Terrain terrain) {
        return (terrain.daylight(visit.point()) ? 100 : 0) + visit.steps();
    }

    private static List<Point> reconstruct(Visit end, Map<Point, Visit> seen) {
        ArrayList<Point> result = new ArrayList<>();
        Visit current = end;
        while (current.parent() != null) {
            result.add(current.point());
            current = seen.get(current.parent());
        }
        return result.reversed();
    }
}
