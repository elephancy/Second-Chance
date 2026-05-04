package com.feel.gems.gametest.item;

import com.feel.gems.adventure.AdventureItems;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.item.InventorySpace;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A full creative inventory must not silently consume a service item. */
public final class InventorySpaceGameTests {
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 80)
    public void wholeServiceItemWaitsForARealSlot(GameTestHelper context) {
        var player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        var inventory = player.getInventory();
        int slots = inventory.getNonEquipmentItems().size();
        for (int slot = 0; slot < slots; slot++) {
            inventory.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        ItemStack chalk = new ItemStack(AdventureItems.SHORTCUT_CHALK);
        if (InventorySpace.insertWhole(player, chalk)
                || chalk.isEmpty()
                || GemsGameTestUtil.hasItem(player, AdventureItems.SHORTCUT_CHALK)) {
            context.fail("Full creative inventory consumed the service reward");
            return;
        }
        inventory.setItem(0, ItemStack.EMPTY);
        if (!InventorySpace.insertWhole(player, chalk)
                || !GemsGameTestUtil.hasItem(player, AdventureItems.SHORTCUT_CHALK)
                || GemsGameTestUtil.countItem(player, Items.COBBLESTONE) != (slots - 1) * 64) {
            context.fail("Service reward did not enter the freed slot intact");
            return;
        }
        context.succeed();
    }
}
