package com.feel.gems.adventure.worldgen.site;

import com.feel.gems.adventure.estate.FourRoadsEstateStructure;
import com.feel.gems.adventure.satellite.SatelliteStructure;
import com.feel.gems.adventure.undercard.UndercardStructure;
import com.feel.gems.adventure.worldgen.AliDryArrivalStructure;
import com.feel.gems.adventure.worldgen.FamV10TrialStructure;
import com.feel.gems.adventure.worldgen.HushwireApproachStructure;
import com.feel.gems.adventure.worldgen.NourCoastalStructure;
import com.feel.gems.adventure.worldgen.OthmanThemedStructure;
import com.feel.gems.adventure.worldgen.SoryalShoreStructure;
import java.util.Map;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Exact custom jigsaw wrappers audited for both start reach and piece influence. */
final class AuditedJigsawTypes {
    record Entry(Class<? extends Structure> exactClass, int pieceApronBlocks) { }

    private static final Map<String, Entry> TYPES = Map.ofEntries(
            Map.entry("gems:four_roads_estate_v1", new Entry(FourRoadsEstateStructure.class, 15)),
            Map.entry("gems:satellite_v1", new Entry(SatelliteStructure.class, 15)),
            Map.entry("gems:undercard_v1", new Entry(UndercardStructure.class, 15)),
            Map.entry("gems:dimension_chapter_v1", new Entry(DimensionChapterStructure.class, 15)),
            Map.entry("gems:othman_themed", new Entry(OthmanThemedStructure.class, 15)),
            Map.entry("gems:soryal_shore_v1", new Entry(SoryalShoreStructure.class, 15)),
            Map.entry("gems:nour_coastal_v1", new Entry(NourCoastalStructure.class, 15)),
            Map.entry("gems:hushwire_approach_v10", new Entry(HushwireApproachStructure.class, 15)),
            Map.entry("gems:ali_dry_arrival", new Entry(AliDryArrivalStructure.class, 15)),
            Map.entry("gems:fam_forum_public_v1", new Entry(FamV10TrialStructure.class, 15)),
            Map.entry("gems:fam_forum_trial_v1", new Entry(FamV10TrialStructure.class, 15)));

    private AuditedJigsawTypes() { }

    static Entry get(String type) { return TYPES.get(type); }
}
