package com.feel.gems.adventure.worldgen;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FamArrivalGradeGeometryTest {
    @Test
    void eastSwaleCoversOnlyTheMeasuredBank() {
        BoundingBox root = new BoundingBox(-8864, 101, 8386,
                -8818, 136, 8432);
        Direction outward = Rotation.COUNTERCLOCKWISE_90.rotate(Direction.SOUTH);
        Direction lateral = Rotation.COUNTERCLOCKWISE_90.rotate(Direction.WEST);
        List<FamArrivalGrade.Column> columns = FamArrivalGrade.plan(root,
                outward, lateral);
        assertEquals(90, columns.size());
        assertTrue(columns.stream().allMatch(column ->
                column.base().getX() >= -8794 && column.base().getX() <= -8785
                        && column.base().getY() == 100
                        && column.base().getZ() >= 8405 && column.base().getZ() <= 8413));
        assertTrue(columns.stream().anyMatch(column ->
                column.base().getX() == -8785 && column.base().getZ() == 8409));
        assertFalse(columns.stream().anyMatch(column ->
                column.base().getX() >= -8784 || column.base().getX() <= -8795));
    }

    @Test
    void allRotationsKeepTheSameBoundedTenByNineFootprint() {
        BoundingBox root = new BoundingBox(0, 101, 0, 46, 136, 46);
        for (Rotation rotation : Rotation.values()) {
            List<FamArrivalGrade.Column> columns = FamArrivalGrade.plan(root,
                    rotation.rotate(Direction.SOUTH), rotation.rotate(Direction.WEST));
            assertEquals(90, columns.size());
            assertEquals(90, columns.stream().map(FamArrivalGrade.Column::base)
                    .distinct().count());
            assertTrue(columns.stream().allMatch(column ->
                    column.depth() >= 24 && column.depth() <= 33
                            && Math.abs(column.side()) <= 4
                            && column.base().getY() == 100));
        }
    }
}
