package com.feel.gems.adventure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class CampaignDeliveryAccountTest {
    private static final UUID PLAYER = UUID.fromString("2d23bcaf-a19f-4ba3-a38a-2ed7052500c0");

    @Test
    void receiptSurvivesPlayerdataReloadAndRepairsLaggingWorldProgress() {
        CompoundTag playerdata = new CompoundTag();
        CampaignDeliveryAccount issued = CampaignDeliveryAccount.in(playerdata);
        issued.markLore(LandmarkKey.BRANCH);
        issued.markSignature(LandmarkKey.BRANCH);
        issued.markSupplies(LandmarkKey.BRANCH);

        CampaignDeliveryAccount reloaded = CampaignDeliveryAccount.in(playerdata.copy());
        CampaignProgress staleWorld = new CampaignProgress();
        assertTrue(staleWorld.reconcileDelivered(PLAYER, reloaded.loreMask(), reloaded.signatureMask()));
        assertTrue(staleWorld.hasCleared(PLAYER, LandmarkKey.BRANCH));
        assertFalse(staleWorld.needsLore(PLAYER, LandmarkKey.BRANCH));
        assertFalse(staleWorld.needsSignature(PLAYER, LandmarkKey.BRANCH));
        assertTrue(reloaded.hasSupplies(LandmarkKey.BRANCH));
        assertFalse(reloaded.hasSupplies(LandmarkKey.REDLINE));
        assertFalse(staleWorld.worldChanged(LandmarkKey.BRANCH));
    }

    @Test
    void savedWorldDeliveryDoesNotInventAPlayerdataReceipt() {
        CampaignProgress savedWorld = new CampaignProgress();
        savedWorld.recordVictory(List.of(PLAYER), LandmarkKey.REDLINE);
        savedWorld.markLoreDelivered(PLAYER, LandmarkKey.REDLINE);
        savedWorld.markSignatureDelivered(PLAYER, LandmarkKey.REDLINE);

        CampaignDeliveryAccount stalePlayer = CampaignDeliveryAccount.in(new CompoundTag());
        assertTrue(savedWorld.hasCleared(PLAYER, LandmarkKey.REDLINE));
        assertFalse(stalePlayer.hasLore(LandmarkKey.REDLINE));
        assertFalse(stalePlayer.hasSignature(LandmarkKey.REDLINE));
        assertFalse(stalePlayer.hasSupplies(LandmarkKey.REDLINE));
    }
}
