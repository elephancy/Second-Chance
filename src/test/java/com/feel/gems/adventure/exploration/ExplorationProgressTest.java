package com.feel.gems.adventure.exploration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class ExplorationProgressTest {
    private static final UUID ADA = UUID.fromString("79fe6812-3517-43bf-bf5d-bf6ec5384a10");
    private static final UUID BEN = UUID.fromString("f0cc38bf-bdd0-4c9e-9392-96801c198230");

    @Test
    void oneWorldDiscoveryGivesEachParticipantAnIndependentPersistentFirstClear() {
        ExplorationProgress first = new ExplorationProgress();
        String site = ExplorationProgress.activityId("maze", -1200, 248);
        var win = first.complete(site, List.of(ADA, BEN, ADA));
        assertTrue(win.firstWorldDiscovery());
        assertEquals(java.util.Set.of(ADA, BEN), win.firstPlayerClears());

        ExplorationProgress reloaded = ExplorationProgress.fromSnapshot(first.snapshot());
        assertFalse(reloaded.complete(site, List.of(ADA, BEN)).firstWorldDiscovery());
        assertTrue(reloaded.complete(site, List.of(ADA, BEN)).firstPlayerClears().isEmpty());
        UUID late = UUID.randomUUID();
        assertEquals(java.util.Set.of(late), reloaded.complete(site, List.of(late)).firstPlayerClears());
    }

    @Test
    void fourRoadReceiptsStaySiteAndPlayerScopedAcrossReload() {
        ExplorationProgress progress = new ExplorationProgress();
        int x = -1064;
        int z = 1496;
        String originalWin = ExplorationProgress.activityId("estate", x, z);
        progress.complete(originalWin, List.of(ADA));
        assertEquals(0, progress.estateRoadCount(ADA, x, z));

        for (char wing : new char[]{'N', 'E', 'S'}) {
            String road = ExplorationProgress.estateRoadId(wing, x, z);
            assertEquals(java.util.Set.of(ADA), progress.complete(road, List.of(ADA))
                    .firstPlayerClears());
            assertTrue(progress.complete(road, List.of(ADA)).firstPlayerClears().isEmpty());
        }
        assertEquals(3, progress.estateRoadCount(ADA, x, z));
        assertEquals(0, progress.estateRoadCount(BEN, x, z));
        assertEquals(0, progress.estateRoadCount(ADA, x + 235, z));
        assertFalse(progress.hasEstateMastery(ADA));

        String north = ExplorationProgress.estateRoadId('N', x, z);
        assertEquals(java.util.Set.of(BEN), progress.complete(north, List.of(BEN))
                .firstPlayerClears());
        assertEquals(1, progress.estateRoadCount(BEN, x, z));

        ExplorationProgress restored = ExplorationProgress.fromSnapshot(progress.snapshot());
        assertTrue(restored.complete(north, List.of(ADA, BEN)).firstPlayerClears().isEmpty());
        restored.complete(ExplorationProgress.estateRoadId('W', x, z), List.of(ADA));
        assertEquals(4, restored.estateRoadCount(ADA, x, z));
        String mastery = ExplorationProgress.activityId("estate_mastery", x, z);
        assertEquals(java.util.Set.of(ADA), restored.complete(mastery, List.of(ADA))
                .firstPlayerClears());
        assertTrue(ExplorationProgress.fromSnapshot(restored.snapshot()).hasEstateMastery(ADA));
        assertFalse(restored.hasEstateMastery(BEN));
        assertThrows(IllegalArgumentException.class,
                () -> ExplorationProgress.estateRoadId('X', x, z));
    }

    @Test
    void trailAndCipherStepsSurviveReloadAndWrongSymbolResetsOnlyCipher() {
        ExplorationProgress progress = new ExplorationProgress();
        progress.startTrail(ADA, 83012L);
        assertTrue(progress.markTrailTurn(ADA));
        progress.startCipher(ADA, 49330L);
        assertEquals(1, progress.pressCipher(ADA, 0));
        assertEquals(0, progress.pressCipher(ADA, 1));
        assertEquals(1, progress.pressCipher(ADA, 0));
        assertEquals(2, progress.pressCipher(ADA, 2));
        assertEquals(3, progress.pressCipher(ADA, 1));

        ExplorationProgress restored = ExplorationProgress.fromSnapshot(progress.snapshot());
        assertEquals(1, restored.journey(ADA).trailStep());
        assertEquals(3, restored.journey(ADA).cipherStep());
        restored.clearCipher(ADA);
        assertEquals(1, restored.journey(ADA).trailStep());
        assertEquals(ExplorationProgress.Journey.NONE, restored.journey(ADA).cipherPlaque());
    }

    @Test
    void PlayerReceiptRepairsAStaleWorldSaveWithoutRepeatingTheReward() {
        String site = ExplorationProgress.activityId("vista", 184, -291);
        CompoundTag savedPlayer = new CompoundTag();
        ExplorationDeliveryAccount.in(savedPlayer).mark(site);
        ExplorationProgress staleWorld = new ExplorationProgress();
        for (String receipt : ExplorationDeliveryAccount.in(savedPlayer.copy()).receipts()) {
            assertTrue(staleWorld.reconcileReceipt(ADA, receipt));
        }
        assertTrue(staleWorld.discovered(site));
        assertTrue(staleWorld.earned(ADA, site));
        assertFalse(staleWorld.reconcileReceipt(ADA, site));
        assertTrue(staleWorld.complete(site, List.of(ADA)).firstPlayerClears().isEmpty());
    }

    @Test
    void EarnedFieldRestCooldownPersistsBesideSupplyReceipt() {
        String site = ExplorationProgress.activityId("trail", 43, 85);
        CompoundTag player = new CompoundTag();
        ExplorationDeliveryAccount account = ExplorationDeliveryAccount.in(player);
        account.mark(site);
        account.setFieldReadyAt(12_000L);
        ExplorationDeliveryAccount reloaded = ExplorationDeliveryAccount.in(player.copy());
        assertTrue(reloaded.has(site));
        assertEquals(12_000L, reloaded.fieldReadyAt());
    }

    @Test
    void forgedActivityKeysCannotCreateInfiniteRewardClasses() {
        ExplorationProgress progress = new ExplorationProgress();
        assertThrows(IllegalArgumentException.class, () -> progress.complete("maze:whatever", List.of(ADA)));
        assertThrows(IllegalArgumentException.class, () -> progress.complete("maze:0:0", List.of()));
    }

    @Test
    void nearbyCompanionsNeedProgressAtTheSameTrailOrCipherInstance() {
        ExplorationProgress progress = new ExplorationProgress();
        long trail = 13000L;
        long otherTrail = 13001L;
        progress.startTrail(ADA, trail);
        assertFalse(ready("trail", trail, progress.journey(ADA)));
        progress.markTrailTurn(ADA);
        assertTrue(ready("trail", trail, progress.journey(ADA)));
        assertFalse(ready("trail", otherTrail, progress.journey(ADA)));
        assertFalse(ready("trail", trail, progress.journey(BEN)));

        long cipher = 85000L;
        progress.startCipher(BEN, cipher);
        progress.pressCipher(BEN, 0);
        progress.pressCipher(BEN, 2);
        assertFalse(ready("cipher", cipher, progress.journey(BEN)));
        progress.pressCipher(BEN, 1);
        assertTrue(ready("cipher", cipher, progress.journey(BEN)));
        assertFalse(ready("cipher", cipher + 1, progress.journey(BEN)));
    }

    @Test
    void VistaCompanionNeedsTheirOwnEquipmentBearingSkyAndRoofPosition() {
        var empty = ExplorationProgress.Journey.EMPTY;
        assertTrue(ExplorationParticipation.ready("vista", 0L, empty, true, true, true, true));
        assertFalse(ExplorationParticipation.ready("vista", 0L, empty, false, true, true, true));
        assertFalse(ExplorationParticipation.ready("vista", 0L, empty, true, false, true, true));
        assertFalse(ExplorationParticipation.ready("vista", 0L, empty, true, true, false, true));
        assertFalse(ExplorationParticipation.ready("vista", 0L, empty, true, true, true, false));
    }

    private static boolean ready(String kind, long origin, ExplorationProgress.Journey journey) {
        return ExplorationParticipation.ready(kind, origin, journey,
                false, false, false, false);
    }
}
