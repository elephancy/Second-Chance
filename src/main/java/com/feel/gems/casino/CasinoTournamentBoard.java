package com.feel.gems.casino;

import com.feel.gems.adventure.DomainBlocks;
import com.feel.gems.adventure.FriendDomainUnlockState;
import com.feel.gems.item.InventorySpace;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Public, no-cash daily scoreboard and one provision claim per player per day. */
public final class CasinoTournamentBoard {
    private CasinoTournamentBoard() { }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.getBlockState(hit.getBlockPos()).is(DomainBlocks.TOWNHALL_TOURNAMENT_BOARD)) {
                return InteractionResult.PASS;
            }
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            long day = TournamentState.worldDay(level.getServer());
            TournamentState state = TournamentState.get(level.getServer());
            serverPlayer.sendSystemMessage(Component.literal(state.summary(day)), false);
            if (!state.completed(day)) {
                serverPlayer.sendSystemMessage(Component.literal(
                        "Visit Ragab's casino, choose Daily free tournament, and win three different featured tables. The Signal Table is optional and needs two players."), false);
                return InteractionResult.SUCCESS_SERVER;
            }
            // Repairs the domain unlock if a save was interrupted between the
            // tournament score and the separate friend-domain data file.
            FriendDomainUnlockState.get(level.getServer()).clear("ragab");
            CasinoPlayerAccount account = CasinoPlayerAccount.of(serverPlayer);
            if (account.provisionsClaimed(day)) {
                serverPlayer.sendSystemMessage(Component.literal(
                        "Today's group provisions are already claimed. A new challenge opens next Minecraft day."), false);
                return InteractionResult.SUCCESS_SERVER;
            }
            if (!InventorySpace.hasEmptySlots(serverPlayer, 2)) {
                serverPlayer.sendSystemMessage(Component.literal(
                        "Make room for two stacks, then use this board again to claim today's provisions."), false);
                return InteractionResult.SUCCESS_SERVER;
            }
            int firstEmpty = -1;
            var carried = serverPlayer.getInventory().getNonEquipmentItems();
            for (int slot = 0; slot < carried.size(); slot++) {
                if (carried.get(slot).isEmpty()) {
                    firstEmpty = slot;
                    break;
                }
            }
            if (!InventorySpace.insertWhole(serverPlayer, new ItemStack(Items.BREAD, 2))) {
                serverPlayer.sendSystemMessage(Component.literal(
                        "Make room for two stacks, then use this board again to claim today's provisions."), false);
                return InteractionResult.SUCCESS_SERVER;
            }
            if (!InventorySpace.insertWhole(serverPlayer, new ItemStack(Items.TORCH, 4))) {
                // The two-slot preflight makes this unreachable during normal server-thread
                // execution, but avoid a partial grant if insertion ever changes.
                if (firstEmpty >= 0) serverPlayer.getInventory().setItem(firstEmpty, ItemStack.EMPTY);
                serverPlayer.sendSystemMessage(Component.literal(
                        "Make room for two stacks, then use this board again to claim today's provisions."), false);
                return InteractionResult.SUCCESS_SERVER;
            }
            account.markProvisionsClaimed(day);
            serverPlayer.sendSystemMessage(Component.literal(
                    "The group earned today's free provisions: two bread and four torches. No emeralds were needed."), false);
            return InteractionResult.SUCCESS_SERVER;
        });
    }

}
