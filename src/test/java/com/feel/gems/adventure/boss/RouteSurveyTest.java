package com.feel.gems.adventure.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

final class RouteSurveyTest {
    @Test
    void findsTwoDistinctWalkableBranchesWithoutCrossingWalls() {
        RouteSurvey.Terrain corridors = new RouteSurvey.Terrain() {
            @Override
            public boolean walkable(RouteSurvey.Point point) {
                return point.y() == 0 && (point.x() == 0 || point.z() == 0)
                        && Math.abs(point.x()) <= 10 && Math.abs(point.z()) <= 10;
            }

            @Override
            public boolean daylight(RouteSurvey.Point point) {
                return point.z() <= -9 || point.x() >= 9;
            }
        };
        var paths = RouteSurvey.find(corridors);
        assertEquals(2, paths.size());
        assertEquals(Set.of(RouteSurvey.Direction.NORTH, RouteSurvey.Direction.EAST),
                Set.of(paths.get(0).direction(), paths.get(1).direction()));
        assertTrue(paths.stream().allMatch(path -> path.daylight()
                && path.points().stream().allMatch(corridors::walkable)));
    }
}
