package com.feel.gems.casino;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerPlayer;

/** Inventory-backed stakes; all operations run on the server thread. */
final class EmeraldStakes {
    private EmeraldStakes() {
    }

    static int count(ServerPlayer player) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Items.EMERALD)) count += stack.getCount();
        }
        return count;
    }

    static boolean debit(ServerPlayer player, int amount) {
        if (amount < 0 || count(player) < amount) return false;
        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(Items.EMERALD)) continue;
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        return remaining == 0;
    }

    static void deliverClaims(ServerPlayer player, CasinoPlayerAccount account) {
        long balance = account.claimBalance();
        var carried = player.getInventory().getNonEquipmentItems();
        ItemStack plainEmerald = new ItemStack(Items.EMERALD);
        for (ItemStack stack : carried) {
            if (balance == 0) break;
            if (!ItemStack.isSameItemSameComponents(stack, plainEmerald)) continue;
            int delivered = (int) Math.min(balance, stack.getMaxStackSize() - stack.getCount());
            if (delivered <= 0) continue;
            stack.grow(delivered);
            balance -= delivered;
            account.setClaimBalance(balance);
        }
        for (int slot = 0; slot < carried.size() && balance > 0; slot++) {
            if (!carried.get(slot).isEmpty()) continue;
            int delivered = (int) Math.min(plainEmerald.getMaxStackSize(), balance);
            player.getInventory().setItem(slot, new ItemStack(Items.EMERALD, delivered));
            balance -= delivered;
            account.setClaimBalance(balance);
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }
}
