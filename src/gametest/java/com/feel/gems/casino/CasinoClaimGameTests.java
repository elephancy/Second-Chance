package com.feel.gems.casino;

import com.feel.gems.gametest.util.GemsGameTestUtil;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Casino payouts must not debit an account for discarded creative inventory items. */
public final class CasinoClaimGameTests {
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 80)
    public void creativeFullInventoryKeepsUndeliveredEmeraldsInAccount(GameTestHelper context) {
        var player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        var inventory = player.getInventory();
        int slots = inventory.getNonEquipmentItems().size();
        for (int slot = 0; slot < slots; slot++) {
            inventory.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        inventory.setItem(0, new ItemStack(Items.EMERALD, 63));
        var account = CasinoPlayerAccount.of(player);
        account.credit(3);

        EmeraldStakes.deliverClaims(player, account);
        if (GemsGameTestUtil.countItem(player, Items.EMERALD) != 64
                || account.claimBalance() != 2
                || GemsGameTestUtil.countItem(player, Items.COBBLESTONE) != (slots - 1) * 64) {
            context.fail("A full creative inventory discarded or overpaid casino emeralds");
            return;
        }

        inventory.setItem(1, ItemStack.EMPTY);
        EmeraldStakes.deliverClaims(player, account);
        EmeraldStakes.deliverClaims(player, account);
        if (GemsGameTestUtil.countItem(player, Items.EMERALD) != 66
                || account.claimBalance() != 0) {
            context.fail("Pending casino emeralds did not arrive exactly once after making room");
            return;
        }
        context.succeed();
    }
}
