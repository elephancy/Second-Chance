package com.feel.gems.adventure.exploration;

import com.feel.gems.item.InventorySpace;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** One server-owned completion entry point for independent exploration sites. */
public final class ExplorationRewards {
    private ExplorationRewards() { }

    /**
     * Records a world discovery and each eligible player's first clear.
     * A disconnected participant can be recorded with {@link #completeForIds}.
     * The origin is the stable generated site anchor, not the reward trigger.
     */
    public static ExplorationProgress.Result complete(ServerLevel level, String activityId,
                                                      BlockPos origin,
                                                      Collection<ServerPlayer> participants) {
        Objects.requireNonNull(participants, "participants");
        List<UUID> ids = participants.stream().map(ServerPlayer::getUUID).distinct().toList();
        return completeForIds(level, activityId, origin, ids);
    }

    public static ExplorationProgress.Result completeForIds(ServerLevel level, String activityId,
                                                            BlockPos origin,
                                                            Collection<UUID> participants) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(origin, "origin");
        if (level.dimension() != Level.OVERWORLD) {
            throw new IllegalArgumentException("Exploration sites belong in the Overworld");
        }
        ExplorationProgress.validateId(activityId);
        String stable = ExplorationProgress.activityId(activityId.substring(0, activityId.indexOf(':')),
                origin.getX(), origin.getZ());
        if (!stable.equals(activityId)) {
            throw new IllegalArgumentException("Activity ID must match its generated origin");
        }
        ExplorationState state = ExplorationState.get(level.getServer());
        ExplorationProgress.Result result = state.complete(activityId, participants);
        if (result.firstWorldDiscovery()) {
            level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                    activityId.startsWith("estate_mastery:")
                            ? "The four-road survey was completed for the first time."
                            : activityId.startsWith("estate_")
                            ? "The first " + label(activityId) + " survey is recorded."
                            : "A new " + label(activityId) + " was found. Its route is open to everyone.")
                    .withStyle(ChatFormatting.AQUA), false);
        }
        for (UUID id : result.firstPlayerClears()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player == null) continue;
            player.sendSystemMessage(Component.literal("First clear: " + label(activityId)
                    + ". Field supplies are waiting if your inventory is full.")
                    .withStyle(ChatFormatting.GOLD), false);
            if (!activityId.startsWith("estate_")) {
                player.sendSystemMessage(Component.literal(
                        "Sneak-use any side-site marker for a travel rest. It recharges in ten minutes.")
                        .withStyle(ChatFormatting.GRAY), false);
            }
            deliverPending(player);
        }
        return result;
    }

    /** Called on join and infrequently while online; no world scan. */
    public static void reconcileOnJoin(ServerPlayer player) {
        ExplorationState state = ExplorationState.get(player.level().getServer());
        for (String receipt : ExplorationDeliveryAccount.of(player).receipts()) {
            state.reconcileReceipt(player.getUUID(), receipt);
        }
        deliverPending(player);
    }

    public static void deliverPending(ServerPlayer player) {
        if (!player.isAlive()) return;
        ExplorationState state = ExplorationState.get(player.level().getServer());
        ExplorationDeliveryAccount account = ExplorationDeliveryAccount.of(player);
        Set<String> receipts = account.receipts();
        Set<String> earned = state.model().pending(player.getUUID());
        for (String activityId : earned.stream().sorted().toList()) {
            if (receipts.contains(activityId)) continue;
            ItemStack supplies = supplies(activityId);
            if (!InventorySpace.insertWhole(player, supplies)) return;
            // Receipt and inventory are both in playerdata; a retry cannot mint twice.
            account.mark(activityId);
            receipts.add(activityId);
            player.sendSystemMessage(Component.literal("Field supplies collected: "
                    + label(activityId) + ".").withStyle(ChatFormatting.GRAY), false);
        }
    }

    private static ItemStack supplies(String id) {
        ItemStack stack;
        if (id.startsWith("estate:")) {
            stack = new ItemStack(Items.ENDER_PEARL, 4);
        } else if (id.startsWith("estate_n:")) {
            stack = new ItemStack(Items.MOSS_BLOCK, 12);
        } else if (id.startsWith("estate_e:")) {
            stack = new ItemStack(Items.AMETHYST_SHARD, 12);
        } else if (id.startsWith("estate_s:")) {
            stack = new ItemStack(Items.PRISMARINE_SHARD, 12);
        } else if (id.startsWith("estate_w:")) {
            stack = new ItemStack(Items.COPPER_INGOT, 12);
        } else if (id.startsWith("estate_mastery:")) {
            stack = new ItemStack(Items.SPYGLASS);
        } else if (id.startsWith("maze:")) {
            stack = new ItemStack(Items.ECHO_SHARD, 3);
        } else if (id.startsWith("cipher:")) {
            stack = new ItemStack(Items.LAPIS_LAZULI, 8);
        } else if (id.startsWith("vista:")) {
            stack = new ItemStack(Items.EXPERIENCE_BOTTLE, 4);
        } else {
            stack = new ItemStack(Items.GOLDEN_CARROT, 4);
        }
        String name = id.startsWith("estate_mastery:") ? "Cartographer's Spyglass"
                : id.startsWith("estate_") ? label(id) + " cache"
                : label(id) + " supplies";
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static String label(String id) {
        if (id.startsWith("estate:")) return "Four Roads Estate";
        if (id.startsWith("estate_n:")) return "Garden Road";
        if (id.startsWith("estate_e:")) return "Mirror Road";
        if (id.startsWith("estate_s:")) return "Sluice Road";
        if (id.startsWith("estate_w:")) return "Clock Road";
        if (id.startsWith("estate_mastery:")) return "Four Roads survey";
        if (id.startsWith("maze:")) return "labyrinth";
        if (id.startsWith("cipher:")) return "star cipher";
        if (id.startsWith("vista:")) return "lookout";
        return "lost trail";
    }
}
