package com.feel.gems.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class CampaignProgressTest {
    private static final UUID FIRST = UUID.fromString("f2728d1d-161f-45d6-9871-b884aa67a502");
    private static final UUID SECOND = UUID.fromString("13027899-e761-4b1f-a305-d6972b1216f5");

    @Test
    void firstVisitIsPersonalAndRepeatedVisitsDoNotChangeProgress() {
        CampaignProgress campaign = new CampaignProgress();
        assertTrue(campaign.visit(FIRST, LandmarkKey.FRAMEFALL));
        assertFalse(campaign.visit(FIRST, LandmarkKey.FRAMEFALL));
        assertEquals(LandmarkKey.FRAMEFALL.bit(), campaign.progress(FIRST).visited());
        assertEquals(0, campaign.progress(SECOND).visited());
        assertTrue(campaign.visit(SECOND, LandmarkKey.FRAMEFALL));
    }

    @Test
    void eachCoopParticipantKeepsIndependentRewardsAcrossRematchesAndReconnects() {
        CampaignProgress campaign = new CampaignProgress();
        var victory = campaign.recordVictory(List.of(FIRST, SECOND), LandmarkKey.FRAMEFALL);
        assertTrue(victory.firstWorldChange());
        assertEquals(Map.of(FIRST, true, SECOND, true), victory.firstClears());
        assertTrue(campaign.needsLore(FIRST, LandmarkKey.FRAMEFALL));
        assertTrue(campaign.needsLore(SECOND, LandmarkKey.FRAMEFALL));

        campaign.markLoreDelivered(FIRST, LandmarkKey.FRAMEFALL);
        campaign.markSignatureDelivered(FIRST, LandmarkKey.FRAMEFALL);
        CampaignProgress reloaded = CampaignProgress.fromSnapshot(campaign.snapshot());
        assertFalse(reloaded.needsLore(FIRST, LandmarkKey.FRAMEFALL));
        assertTrue(reloaded.needsLore(SECOND, LandmarkKey.FRAMEFALL));
        assertTrue(reloaded.needsSignature(SECOND, LandmarkKey.FRAMEFALL));

        var rematch = reloaded.recordVictory(List.of(FIRST, SECOND), LandmarkKey.FRAMEFALL);
        assertFalse(rematch.firstWorldChange());
        assertEquals(Map.of(FIRST, false, SECOND, false), rematch.firstClears());
        assertFalse(reloaded.needsSignature(FIRST, LandmarkKey.FRAMEFALL));
        assertTrue(reloaded.needsSignature(SECOND, LandmarkKey.FRAMEFALL));
    }

    @Test
    void lateJoinerCanEarnPersonalRevealAfterWorldAlreadyChanged() {
        CampaignProgress campaign = new CampaignProgress();
        campaign.recordVictory(List.of(FIRST), LandmarkKey.HUSHWIRE);
        assertTrue(campaign.worldChanged(LandmarkKey.HUSHWIRE));
        assertFalse(campaign.hasCleared(SECOND, LandmarkKey.HUSHWIRE));

        var lateVictory = campaign.recordVictory(List.of(SECOND), LandmarkKey.HUSHWIRE);
        assertFalse(lateVictory.firstWorldChange());
        assertTrue(lateVictory.firstClears().get(SECOND));
        assertTrue(campaign.needsLore(SECOND, LandmarkKey.HUSHWIRE));
        assertThrows(IllegalStateException.class,
                () -> campaign.markLoreDelivered(SECOND, LandmarkKey.FRAMEFALL));
    }

    @Test
    void playerReceiptRestoresAWorldSaveThatLaggedBehindDelivery() {
        CampaignProgress staleWorld = new CampaignProgress();
        int bit = LandmarkKey.REDLINE.bit();
        assertTrue(staleWorld.reconcileDelivered(FIRST, bit, bit));
        assertTrue(staleWorld.hasCleared(FIRST, LandmarkKey.REDLINE));
        assertFalse(staleWorld.worldChanged(LandmarkKey.REDLINE));
        assertFalse(staleWorld.needsLore(FIRST, LandmarkKey.REDLINE));
        assertFalse(staleWorld.needsSignature(FIRST, LandmarkKey.REDLINE));
        assertFalse(staleWorld.reconcileDelivered(FIRST, bit, bit));

        CampaignProgress reloaded = CampaignProgress.fromSnapshot(staleWorld.snapshot());
        assertTrue(reloaded.hasCleared(FIRST, LandmarkKey.REDLINE));
        assertEquals(staleWorld.progress(FIRST), reloaded.progress(FIRST));
    }
}
