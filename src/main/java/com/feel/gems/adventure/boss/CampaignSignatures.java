package com.feel.gems.adventure.boss;

import com.feel.gems.adventure.CampaignPersistentState;
import com.feel.gems.adventure.CampaignDeliveryAccount;
import com.feel.gems.adventure.LandmarkKey;
import java.util.Collection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Retryable, personal boss reward delivery backed by the campaign save. */
public final class CampaignSignatures {
    private CampaignSignatures() { }

    public static void deliver(Collection<ServerPlayer> players) {
        for (ServerPlayer player : players) deliver(player);
    }

    public static void deliver(ServerPlayer player) {
        deliver(player, true);
    }

    public static void deliverQuietly(ServerPlayer player) {
        deliver(player, false);
    }

    private static void deliver(ServerPlayer player, boolean notifyIfFull) {
        if (!player.isAlive()) return;
        CampaignPersistentState state = CampaignPersistentState.get(player.level().getServer());
        CampaignDeliveryAccount account = CampaignDeliveryAccount.of(player, state);
        deliver(player, state, account, LandmarkKey.FRAMEFALL, BossFeatures.PARALLAX_FRAME,
                "FramefallOwner", "The Parallax Frame is yours. Aim it at a safe landing across a gap.",
                "Your Parallax Frame waits until you make inventory space.", notifyIfFull);
        deliver(player, state, account, LandmarkKey.LASTLIGHT, BossFeatures.RETURN_TICKET,
                "LastlightOwner", "The Return Ticket is yours. Mark a point, then use it again within 20 seconds.",
                "Your Return Ticket waits until you make inventory space.", notifyIfFull);
        deliver(player, state, account, LandmarkKey.REHEARSAL, BossFeatures.REHEARSAL_MARK,
                "RehearsalOwner", "The Rehearsal Mark is yours. Record a short move, then repeat it elsewhere.",
                "Your Rehearsal Mark waits until you make inventory space.", notifyIfFull);
        deliver(player, state, account, LandmarkKey.BRANCH, BossFeatures.ROUTE_LENS,
                "BranchOwner", "The Route Lens is yours. Use it to reveal two nearby ways through.",
                "Your Route Lens waits until you make inventory space.", notifyIfFull);

        deliver(player, state, account, LandmarkKey.REDLINE, BossFeatures.RESCUE_LINE,
                "RedlineOwner", "The Rescue Line is yours. Point it at a friend to shelter them, or use it for yourself.",
                "Your Rescue Line waits until you make inventory space.", notifyIfFull);
        deliver(player, state, account, LandmarkKey.HUSHWIRE, BossFeatures.SIGNAL_DIAL,
                "HushwireOwner", "The Echo Dial is yours. Use it to find the real call; sneak-use it to call a scout.",
                "Your Echo Dial waits until you make inventory space.", notifyIfFull);
        deliver(player, state, account, LandmarkKey.UNWRITTEN, BossFeatures.STAGE_MARK,
                "UnwrittenOwner", "The Stage Mark is yours. Sneak-use to pick a move; use it by the Court to choose its next beat.",
                "Your Stage Mark waits until you make inventory space.", notifyIfFull);
    }

    private static void deliver(ServerPlayer player, CampaignPersistentState state,
                                CampaignDeliveryAccount account, LandmarkKey landmark, Item item,
                                String ownerKey, String success, String full, boolean notifyIfFull) {
        if (!state.model().hasCleared(player.getUUID(), landmark)
                || account.hasSignature(landmark)) return;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack carried = player.getInventory().getItem(slot);
            CustomData data = carried.get(DataComponents.CUSTOM_DATA);
            if (carried.is(item) && data != null
                    && player.getStringUUID().equals(data.copyTag().getStringOr(ownerKey, ""))) {
                account.markSignature(landmark);
                state.reconcileDelivered(player.getUUID(), 0, landmark.bit());
                return;
            }
        }
        ItemStack trophy = new ItemStack(item);
        CustomData.update(DataComponents.CUSTOM_DATA, trophy,
                data -> data.putString(ownerKey, player.getStringUUID()));
        int slot = firstEmptySlot(player);
        if (slot >= 0) {
            // Creative Inventory.add can void a failed insertion while reporting
            // success. The slot and receipt must save together in playerdata.
            player.getInventory().setItem(slot, trophy);
            account.markSignature(landmark);
            state.reconcileDelivered(player.getUUID(), 0, landmark.bit());
            player.sendSystemMessage(Component.literal(success).withStyle(ChatFormatting.GOLD), false);
        } else if (notifyIfFull) {
            player.sendSystemMessage(Component.literal(full).withStyle(ChatFormatting.GRAY), true);
        }
    }

    private static int firstEmptySlot(ServerPlayer player) {
        var carried = player.getInventory().getNonEquipmentItems();
        for (int slot = 0; slot < carried.size(); slot++) {
            if (carried.get(slot).isEmpty()) return slot;
        }
        return -1;
    }
}
