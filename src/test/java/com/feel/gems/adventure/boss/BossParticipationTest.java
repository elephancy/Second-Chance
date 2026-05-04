package com.feel.gems.adventure.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.feel.gems.adventure.CampaignProgress;
import com.feel.gems.adventure.LandmarkKey;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class BossParticipationTest {
    private static final UUID CONTRIBUTOR = UUID.fromString("6b95487b-8afa-4945-8734-c24005b30f95");
    private static final UUID SURVIVOR = UUID.fromString("c7a64ab1-8f65-48ee-849a-413327d53e55");
    private static final UUID PASSERBY = UUID.fromString("376a1923-c584-4a3a-9e70-4f4055f975e6");

    @Test
    void contributorsKeepPersonalClearAfterLeavingArenaOrDisconnecting() {
        Set<UUID> eligible = BossParticipation.eligibleIds(
                Map.of(CONTRIBUTOR, 20, SURVIVOR, 60, PASSERBY, 40),
                Set.of(CONTRIBUTOR));
        assertEquals(Set.of(CONTRIBUTOR, SURVIVOR), eligible);

        CampaignProgress progress = new CampaignProgress();
        progress.recordVictory(eligible, LandmarkKey.REDLINE);
        CampaignProgress reloaded = CampaignProgress.fromSnapshot(progress.snapshot());
        assertTrue(reloaded.needsLore(CONTRIBUTOR, LandmarkKey.REDLINE));
        assertTrue(reloaded.needsSignature(CONTRIBUTOR, LandmarkKey.REDLINE));
        assertTrue(reloaded.hasCleared(SURVIVOR, LandmarkKey.REDLINE));
        assertFalse(reloaded.hasCleared(PASSERBY, LandmarkKey.REDLINE));
    }
}
