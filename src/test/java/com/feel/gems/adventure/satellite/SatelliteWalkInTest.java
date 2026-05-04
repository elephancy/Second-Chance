package com.feel.gems.adventure.satellite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

final class SatelliteWalkInTest {
    private static final BoundingBox BOX = new BoundingBox(0, 20, 0, 46, 77, 46);

    @Test
    void localNorthEntranceRotatesToEverySavedFacing() {
        Map<Rotation, BlockPos> firstCell = Map.of(
                Rotation.NONE, new BlockPos(22, 69, 15),
                Rotation.CLOCKWISE_90, new BlockPos(31, 69, 22),
                Rotation.CLOCKWISE_180, new BlockPos(24, 69, 31),
                Rotation.COUNTERCLOCKWISE_90, new BlockPos(15, 69, 24));
        for (var entry : firstCell.entrySet()) {
            Rotation rotation = entry.getKey();
            assertEquals(entry.getValue(), SatelliteWalkIn.point(BOX, rotation, -1, 8, 69));
            var cells = new HashSet<BlockPos>();
            for (int lateral = -1; lateral <= 1; lateral++) {
                for (int outward = 7; outward <= 8; outward++) {
                    BlockPos cell = SatelliteWalkIn.point(BOX, rotation,
                            lateral, outward, 69);
                    assertTrue(BOX.isInside(cell));
                    cells.add(cell);
                }
            }
            assertEquals(6, cells.size());
        }
    }
}
