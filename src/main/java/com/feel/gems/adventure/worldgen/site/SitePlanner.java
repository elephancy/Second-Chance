package com.feel.gems.adventure.worldgen.site;

import net.minecraft.core.BlockPos;

/** Coordinate-only planner. Site preflight must inspect the actual generated world. */
public final class SitePlanner {
    private SitePlanner() { }

    public static SiteCandidate candidate(SiteDefinition definition, long worldSeed,
                                          int regionX, int regionZ, int anchorY) {
        // Geometry versions must not move a site's region coordinates in a
        // long-lived world; only newly generated starts use the new template.
        long value = mix(worldSeed ^ stableId(definition.id().toString())
                ^ ((long) regionX * 0x632BE59BD9B4E019L)
                ^ ((long) regionZ * 0x9E3779B97F4A7C15L));
        int spread = definition.spacingChunks() - definition.separationChunks();
        int offsetX = (int) Long.remainderUnsigned(value, spread);
        value = mix(value);
        int offsetZ = (int) Long.remainderUnsigned(value, spread);
        value = mix(value);
        int rotation = (int) (value & 3);
        int chunkX = Math.addExact(Math.multiplyExact(regionX, definition.spacingChunks()), offsetX);
        int chunkZ = Math.addExact(Math.multiplyExact(regionZ, definition.spacingChunks()), offsetZ);
        int x = Math.addExact(Math.multiplyExact(chunkX, 16), 8);
        int z = Math.addExact(Math.multiplyExact(chunkZ, 16), 8);
        return new SiteCandidate(new BlockPos(x, anchorY, z), rotation);
    }

    private static long stableId(String id) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < id.length(); i++) {
            hash ^= id.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
