package com.feel.gems.adventure.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

class BranchArrivalGradeTest {
    private static final BoundingBox ROOT = new BoundingBox(
            -1840, 63, -1712, -1794, 93, -1666);

    @Test
    void southGateFollowsMeasuredForestRise() {
        List<BranchArrivalGrade.Column> columns = BranchArrivalGrade.plan(
                ROOT, Direction.SOUTH, Direction.EAST);
        assertEquals(25, columns.size());
        assertEquals(new BlockPos(-1819, 64, -1665), columns.get(2).top());
        assertEquals(new BlockPos(-1819, 68, -1661), columns.get(22).top());
        assertEquals(new BlockPos(-1821, 66, -1663), columns.get(10).top());
        assertEquals(new BlockPos(-1817, 66, -1663), columns.get(14).top());
    }

    @Test
    void allRotationsKeepTheGateOutsideTheRoot() {
        for (Direction[] axes : new Direction[][]{
                {Direction.SOUTH, Direction.EAST},
                {Direction.WEST, Direction.SOUTH},
                {Direction.NORTH, Direction.WEST},
                {Direction.EAST, Direction.NORTH}}) {
            List<BranchArrivalGrade.Column> columns = BranchArrivalGrade.plan(
                    ROOT, axes[0], axes[1]);
            assertEquals(25, columns.size());
            for (BranchArrivalGrade.Column column : columns) {
                BlockPos top = column.top();
                assertEquals(ROOT.minY() + column.depth(), top.getY());
                assertEquals(column.depth(), switch (axes[0]) {
                    case SOUTH -> top.getZ() - ROOT.maxZ();
                    case WEST -> ROOT.minX() - top.getX();
                    case NORTH -> ROOT.minZ() - top.getZ();
                    case EAST -> top.getX() - ROOT.maxX();
                    default -> throw new AssertionError();
                });
            }
        }
        assertThrows(IllegalArgumentException.class, () ->
                BranchArrivalGrade.plan(ROOT, Direction.SOUTH, Direction.NORTH));
    }
}
