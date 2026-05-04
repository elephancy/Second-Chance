package com.feel.gems.adventure.worldgen.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.feel.gems.adventure.worldgen.FamV10TrialStructure;
import com.feel.gems.adventure.worldgen.HushwireApproachStructure;
import org.junit.jupiter.api.Test;

final class AuditedJigsawTypesTest {
    @Test
    void currentHushwireAndFamUseTheSharedBoundedAudit() {
        var audit = AuditedJigsawTypes.get("gems:hushwire_approach_v10");
        assertEquals(HushwireApproachStructure.class, audit.exactClass());
        assertEquals(15, audit.pieceApronBlocks());
        assertEquals(FamV10TrialStructure.class,
                AuditedJigsawTypes.get("gems:fam_forum_trial_v1").exactClass());
    }

    @Test
    void unknownVersionsAndVanillaTypesRemainUnauditedHere() {
        assertNull(AuditedJigsawTypes.get("gems:hushwire_approach_v1"));
        assertNull(AuditedJigsawTypes.get("gems:hushwire_approach_v11"));
        assertNull(AuditedJigsawTypes.get("gems:unreviewed_jigsaw_v1"));
        assertNull(AuditedJigsawTypes.get("minecraft:jigsaw"));
    }
}
