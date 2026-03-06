package com.feel.gems.adventure.satellite;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Maps the archive's local north entrance into its saved jigsaw rotation. */
final class SatelliteWalkIn {
    private SatelliteWalkIn() { }

    static BlockPos point(BoundingBox box, Rotation rotation, int lateral,
                          int outward, int y) {
        Direction east = rotation.rotate(Direction.EAST);
        Direction north = rotation.rotate(Direction.NORTH);
        return new BlockPos(box.minX() + 23 + east.getStepX() * lateral
                        + north.getStepX() * outward,
                y,
                box.minZ() + 23 + east.getStepZ() * lateral
                        + north.getStepZ() * outward);
    }
}
