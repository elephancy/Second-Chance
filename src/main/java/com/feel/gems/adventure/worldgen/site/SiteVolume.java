package com.feel.gems.adventure.worldgen.site;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Inclusive local bounds. Every occupied, approach, and clearance cell must be declared. */
public record SiteVolume(Use use, int minX, int minY, int minZ,
                         int maxX, int maxY, int maxZ) {
    public enum Use { BUILD, FOUNDATION, APPROACH, CLEARANCE }

    public SiteVolume {
        if (use == null || minX > maxX || minY > maxY || minZ > maxZ
                || (long) maxX - minX > 511 || (long) maxZ - minZ > 511
                || (long) maxY - minY > 127) {
            throw new IllegalArgumentException("Invalid site volume");
        }
    }

    public BoundingBox at(BlockPos origin, int quarterTurns) {
        int[] a = rotate(minX, minZ, quarterTurns);
        int[] b = rotate(minX, maxZ, quarterTurns);
        int[] c = rotate(maxX, minZ, quarterTurns);
        int[] d = rotate(maxX, maxZ, quarterTurns);
        int lowX = Math.min(Math.min(a[0], b[0]), Math.min(c[0], d[0]));
        int highX = Math.max(Math.max(a[0], b[0]), Math.max(c[0], d[0]));
        int lowZ = Math.min(Math.min(a[1], b[1]), Math.min(c[1], d[1]));
        int highZ = Math.max(Math.max(a[1], b[1]), Math.max(c[1], d[1]));
        return new BoundingBox(Math.addExact(origin.getX(), lowX),
                Math.addExact(origin.getY(), minY), Math.addExact(origin.getZ(), lowZ),
                Math.addExact(origin.getX(), highX), Math.addExact(origin.getY(), maxY),
                Math.addExact(origin.getZ(), highZ));
    }

    private static int[] rotate(int x, int z, int turns) {
        return switch (Math.floorMod(turns, 4)) {
            case 0 -> new int[] {x, z};
            case 1 -> new int[] {Math.negateExact(z), x};
            case 2 -> new int[] {Math.negateExact(x), Math.negateExact(z)};
            default -> new int[] {z, Math.negateExact(x)};
        };
    }
}
