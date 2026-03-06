package com.feel.gems.adventure.worldgen.site;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Reservation contract for a custom structure that delegates its piece layout
 * to JigsawStructure. The reservation validates the delegate's root pool and
 * bounds, then also reserves any mandatory approach outside saved piece boxes.
 */
public interface BoundedJigsawStart {
    JigsawStructure reservationDelegate();

    /** Largest outward X/Z distance of a required approach from a saved piece. */
    int reservationExtraReachBlocks();

    /** Maximum post-jigsaw X/Z move of a start from its lattice chunk. */
    default int reservationMaximumHorizontalShiftBlocks() { return 0; }

    /** Return null for an unrecognized template, piece layout or version. */
    List<BoundingBox> reservationApproaches(StructureStart start);

    /** Inclusive local X/Z rectangle transformed by the saved piece rotation. */
    static BoundingBox localBox(PoolElementStructurePiece piece,
                                int minX, int minZ, int maxX, int maxZ) {
        if (minX > maxX || minZ > maxZ) throw new IllegalArgumentException("local box");
        int worldMinX = Integer.MAX_VALUE, worldMinZ = Integer.MAX_VALUE;
        int worldMaxX = Integer.MIN_VALUE, worldMaxZ = Integer.MIN_VALUE;
        for (int x : new int[]{minX, maxX}) {
            for (int z : new int[]{minZ, maxZ}) {
                BlockPos rotated = StructureTemplate.transform(new BlockPos(x, 0, z),
                        Mirror.NONE, piece.getRotation(), BlockPos.ZERO);
                int wx = Math.addExact(piece.getPosition().getX(), rotated.getX());
                int wz = Math.addExact(piece.getPosition().getZ(), rotated.getZ());
                worldMinX = Math.min(worldMinX, wx);
                worldMaxX = Math.max(worldMaxX, wx);
                worldMinZ = Math.min(worldMinZ, wz);
                worldMaxZ = Math.max(worldMaxZ, wz);
            }
        }
        BoundingBox body = piece.getBoundingBox();
        return new BoundingBox(worldMinX, body.minY(), worldMinZ,
                worldMaxX, body.maxY(), worldMaxZ);
    }

    /** Conservative horizontal apron around a saved box; overflow fails closed. */
    static BoundingBox apron(BoundingBox box, int blocks) {
        if (blocks < 0) throw new IllegalArgumentException("apron");
        return new BoundingBox(Math.subtractExact(box.minX(), blocks), box.minY(),
                Math.subtractExact(box.minZ(), blocks),
                Math.addExact(box.maxX(), blocks), box.maxY(),
                Math.addExact(box.maxZ(), blocks));
    }
}
