package com.feel.gems.adventure;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

/** Server-only post-victory entitlement and retryable personal delivery. */
public final class CampaignRewards {
    private static final String LORE_KEY = "rumor_atlas_identity";
    private static final Map<ServerPlayer, Integer> NEXT_LORE_WARNING_TICK = new WeakHashMap<>();

    private CampaignRewards() {
    }

    public static CampaignProgress.Victory recordVictory(
            MinecraftServer server, Collection<ServerPlayer> participants,
            LandmarkKey landmark) {
        return recordVictoryForIds(server,
                participants.stream().map(ServerPlayer::getUUID).toList(), landmark);
    }

    /** Save every contributor's entitlement, including players who died or disconnected. */
    public static CampaignProgress.Victory recordVictoryForIds(
            MinecraftServer server, Collection<UUID> participants, LandmarkKey landmark) {
        List<UUID> ids = participants.stream().distinct().toList();
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Victory has no eligible participants");
        }
        CampaignProgress.Victory victory = CampaignPersistentState.get(server)
                .recordVictory(ids, landmark);
        for (ServerPlayer player : onlinePlayers(server, ids)) {
            if (victory.firstClears().get(player.getUUID())) {
                player.sendSystemMessage(Component.literal("The account breaks. An identity remains.")
                        .withStyle(ChatFormatting.GOLD), false);
            }
            deliverPendingLore(player);
            if (victory.firstClears().get(player.getUUID())
                    && !CampaignDeliveryAccount.of(player, CampaignPersistentState.get(server))
                            .hasSupplies(landmark)) {
                player.sendSystemMessage(Component.literal(
                        "Make inventory space to collect your first-clear supplies.")
                        .withStyle(ChatFormatting.GRAY), true);
            }
        }
        return victory;
    }

    public static List<ServerPlayer> onlinePlayers(MinecraftServer server,
                                                    Collection<UUID> ids) {
        return ids.stream().map(server.getPlayerList()::getPlayer)
                .filter(Objects::nonNull).filter(ServerPlayer::isAlive).toList();
    }

    public static void deliverPendingLore(ServerPlayer player) {
        if (!player.isAlive()) return;
        CampaignPersistentState state = CampaignPersistentState.get(player.level().getServer());
        CampaignDeliveryAccount account = CampaignDeliveryAccount.of(player, state);
        UUID id = player.getUUID();
        int waitingLore = 0;
        for (LandmarkKey landmark : LandmarkKey.values()) {
            if (!state.model().hasCleared(id, landmark) || account.hasLore(landmark)) {
                continue;
            }
            if (alreadyCarried(player, landmark)) {
                account.markLore(landmark);
                state.reconcileDelivered(id, landmark.bit(), 0);
                continue;
            }
            int slot = firstEmptySlot(player);
            if (slot < 0) {
                waitingLore++;
                continue;
            }
            ItemStack book = IdentityLore.create(landmark);
            CustomData.update(DataComponents.CUSTOM_DATA, book,
                    nbt -> nbt.putString(LORE_KEY, landmark.id()));
            // Inventory.add can void a failed insertion for creative players.
            // Place the whole book in one known-free slot before recording it.
            player.getInventory().setItem(slot, book);
            account.markLore(landmark);
            state.reconcileDelivered(id, landmark.bit(), 0);
            player.sendSystemMessage(Component.literal("An identity account was added to your inventory.")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        int tick = player.level().getServer().getTickCount();
        if (waitingLore > 0 && tick >= NEXT_LORE_WARNING_TICK.getOrDefault(player, Integer.MIN_VALUE)) {
            player.sendSystemMessage(Component.literal(waitingLore == 1
                    ? "An identity account is waiting for inventory space."
                    : waitingLore + " identity accounts are waiting for inventory space.")
                    .withStyle(ChatFormatting.GRAY), true);
            NEXT_LORE_WARNING_TICK.put(player, tick + 600);
        }
        deliverPendingSupplies(player, state, account);
    }

    /** Replay commodity loot stays immediate; the first-clear copy uses a saved receipt. */
    public static void deliverRematchSupplies(Collection<ServerPlayer> players,
                                             LandmarkKey landmark,
                                             CampaignProgress.Victory victory) {
        for (ServerPlayer player : players) {
            if (!Boolean.FALSE.equals(victory.firstClears().get(player.getUUID()))) continue;
            ItemStack supplies = suppliesFor(landmark);
            int slot = firstEmptySlot(player);
            if (slot >= 0) {
                player.getInventory().setItem(slot, supplies);
            } else {
                player.drop(supplies, false, Prediction.SERVER_ONLY);
            }
        }
    }

    private static void deliverPendingSupplies(ServerPlayer player,
                                               CampaignPersistentState state,
                                               CampaignDeliveryAccount account) {
        UUID id = player.getUUID();
        for (LandmarkKey landmark : LandmarkKey.values()) {
            if (!state.model().hasCleared(id, landmark) || account.hasSupplies(landmark)) continue;
            int slot = firstEmptySlot(player);
            if (slot < 0) {
                continue;
            }
            // A whole stack enters one empty carried slot. Receipt and inventory
            // then save together in playerdata, so retry never adds a fragment twice.
            player.getInventory().setItem(slot, suppliesFor(landmark));
            account.markSupplies(landmark);
            player.sendSystemMessage(Component.literal("First-clear supplies added: "
                    + landmark.id() + ".").withStyle(ChatFormatting.GRAY), false);
        }
    }

    private static int firstEmptySlot(ServerPlayer player) {
        var carried = player.getInventory().getNonEquipmentItems();
        for (int slot = 0; slot < carried.size(); slot++) {
            if (carried.get(slot).isEmpty()) return slot;
        }
        return -1;
    }

    private static ItemStack suppliesFor(LandmarkKey landmark) {
        return switch (landmark) {
            case FRAMEFALL -> new ItemStack(Items.AMETHYST_SHARD, 2);
            case LASTLIGHT -> new ItemStack(Items.CLOCK);
            case REHEARSAL -> new ItemStack(Items.GLOW_INK_SAC, 2);
            case BRANCH -> new ItemStack(Items.MAP);
            case REDLINE -> new ItemStack(Items.GOLDEN_CARROT, 3);
            case HUSHWIRE -> new ItemStack(Items.REDSTONE, 8);
            case UNWRITTEN -> new ItemStack(Items.AMETHYST_SHARD, 8);
        };
    }

    private static boolean alreadyCarried(ServerPlayer player, LandmarkKey landmark) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data != null && landmark.id().equals(
                    data.copyTag().getStringOr(LORE_KEY, ""))) {
                return true;
            }
        }
        return false;
    }
}
