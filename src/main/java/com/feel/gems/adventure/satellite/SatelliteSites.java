package com.feel.gems.adventure.satellite;

import com.feel.gems.GemsMod;
import com.feel.gems.adventure.worldgen.AdventureBiomes;
import com.feel.gems.adventure.worldgen.site.SiteDefinition;
import com.feel.gems.adventure.worldgen.site.SiteVolume;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;

/** Placement data for the inactive ocean and buried v1 structure starts. */
public final class SatelliteSites {
    public static final Identifier OCEAN_ID = Identifier.fromNamespaceAndPath(
            GemsMod.MOD_ID, "nour_sunken_relay_v1");
    public static final Identifier ARCHIVE_ID = Identifier.fromNamespaceAndPath(
            GemsMod.MOD_ID, "branch_underarchive_v1");

    private SatelliteSites() { }

    public static SiteDefinition ocean() {
        // Root marker is template (23,19,23); ocean-floor candidate must be
        // near Y 30-31 so its Y+37 boat platform lies near the sea surface.
        return new SiteDefinition(OCEAN_ID, 1, Level.OVERWORLD,
                Set.of(Biomes.DEEP_OCEAN, Biomes.DEEP_COLD_OCEAN,
                        Biomes.DEEP_LUKEWARM_OCEAN, Biomes.DEEP_FROZEN_OCEAN),
                SiteDefinition.Setting.OCEAN, 128, 72, 45, 46, 1,
                31, 4, 0, 64, List.of(
                    new SiteVolume(SiteVolume.Use.BUILD, -23, -19, -23, 23, 23, 23),
                    new SiteVolume(SiteVolume.Use.APPROACH, -10, 17, -10, 10, 24, 10),
                    new SiteVolume(SiteVolume.Use.CLEARANCE, -27, -21, -27, 27, 25, 27)));
    }

    public static SiteDefinition archive() {
        // Root is template (23,28,23), at least 21 blocks under the natural
        // surface. The underground room floor is another 24 blocks below.
        return new SiteDefinition(ARCHIVE_ID, 1, Level.OVERWORLD,
                Set.of(Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA,
                        AdventureBiomes.BRANCH_TAIGA),
                SiteDefinition.Setting.UNDERGROUND, 120, 64, 48, 112, 3,
                0, 5, 15, 128, List.of(
                    new SiteVolume(SiteVolume.Use.BUILD, -23, -28, -23, 23, 29, 23),
                    new SiteVolume(SiteVolume.Use.APPROACH, -8, 18, -14, 8, 31, 8),
                    new SiteVolume(SiteVolume.Use.CLEARANCE, -25, -30, -25, 25, 31, 25)));
    }
}
