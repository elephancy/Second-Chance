package com.feel.gems.adventure.worldgen.site;

import java.util.List;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/** Immutable generation contract shared by structures in every dimension. */
public record SiteDefinition(Identifier id, int version, ResourceKey<Level> dimension,
                             Set<ResourceKey<Biome>> allowedBiomes,
                             Setting setting, int spacingChunks, int separationChunks,
                             int minAnchorY, int maxAnchorY, int maxSlope,
                             int minWaterDepth, int minSupportDepth,
                             int minRoofThickness, int minHazardDistance,
                             List<SiteVolume> volumes) {
    public enum Setting { SURFACE, OCEAN, UNDERGROUND, NETHER, END }

    public SiteDefinition {
        if (id == null || version < 1 || dimension == null || setting == null
                || allowedBiomes == null || allowedBiomes.isEmpty() || volumes == null
                || volumes.isEmpty() || spacingChunks < 2 || separationChunks < 0
                || separationChunks >= spacingChunks || minAnchorY > maxAnchorY
                || maxSlope < 0 || minWaterDepth < 0 || minSupportDepth < 0
                || minRoofThickness < 0 || minHazardDistance < 0) {
            throw new IllegalArgumentException("Invalid site definition");
        }
        if (volumes.stream().anyMatch(volume -> volume == null)) {
            throw new IllegalArgumentException("Null site volume");
        }
        if (volumes.stream().noneMatch(volume -> volume.use() == SiteVolume.Use.BUILD)) {
            throw new IllegalArgumentException("A site needs a build volume");
        }
        allowedBiomes = Set.copyOf(allowedBiomes);
        volumes = List.copyOf(volumes);
    }
}
