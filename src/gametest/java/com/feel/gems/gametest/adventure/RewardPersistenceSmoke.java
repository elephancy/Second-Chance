package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.CampaignDeliveryAccount;
import com.feel.gems.adventure.CampaignPersistentState;
import com.feel.gems.adventure.CampaignRewards;
import com.feel.gems.adventure.LandmarkKey;
import com.feel.gems.adventure.boss.BossFeatures;
import com.feel.gems.adventure.boss.CampaignSignatures;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Three real server launches against one save; only active in the isolated smoke run. */
public final class RewardPersistenceSmoke implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("gems-reward-persistence-smoke");
    private static final UUID PLAYER_ID = UUID.fromString("6c96a965-3788-44d4-8340-a298063503e0");
    private static final Path PHASE = Path.of("reward-persistence-phase.txt");
    private boolean ran;
    private int completedStage;
    private int stopAtTick;

    @Override
    public void onInitialize() {
        if (!Boolean.getBoolean("gems.rewardPersistenceSmoke")) return;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!ran && server.getTickCount() >= 20) {
                ran = true;
                try {
                    int stage = Files.exists(PHASE) ? Integer.parseInt(Files.readString(PHASE).trim()) + 1 : 1;
                    if (stage <= 3) {
                        runStage(server, stage);
                        completedStage = stage;
                        LOGGER.info("REWARD_PERSISTENCE_STAGE_{}_PASS", stage);
                    } else {
                        LOGGER.info("REWARD_PERSISTENCE_ALREADY_COMPLETE");
                    }
                } catch (Exception failure) {
                    LOGGER.error("REWARD_PERSISTENCE_FAILED", failure);
                }
                stopAtTick = server.getTickCount() + 40;
            }
            if (stopAtTick > 0 && server.getTickCount() >= stopAtTick) server.halt(false);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            if (completedStage == 0) return;
            try {
                Files.writeString(PHASE, Integer.toString(completedStage));
            } catch (IOException failure) {
                LOGGER.error("Could not record saved reward smoke stage", failure);
            }
        });
    }

    private static void runStage(MinecraftServer server, int stage) {
        ServerPlayer player = connect(server);
        var state = CampaignPersistentState.get(server);
        var account = CampaignDeliveryAccount.of(player, state);
        if (stage == 1) {
            var inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getNonEquipmentItems().size(); slot++) {
                inventory.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
            inventory.setChanged();
            CampaignRewards.recordVictoryForIds(server, List.of(PLAYER_ID), LandmarkKey.FRAMEFALL);
            CampaignSignatures.deliver(player);
            require(state.model().hasCleared(PLAYER_ID, LandmarkKey.FRAMEFALL)
                    && !account.hasLore(LandmarkKey.FRAMEFALL)
                    && !account.hasSignature(LandmarkKey.FRAMEFALL)
                    && !account.hasSupplies(LandmarkKey.FRAMEFALL)
                    && count(player, Items.WRITTEN_BOOK) == 0
                    && count(player, BossFeatures.PARALLAX_FRAME) == 0
                    && count(player, Items.AMETHYST_SHARD) == 0,
                    "A full inventory lost the pending clear or received an item");
            require(server.getPlayerList().getPlayer(PLAYER_ID) == player,
                    "The embedded player is not a member of the dedicated server player list");
            server.getPlayerList().saveAll();
            LOGGER.info("REWARD_PERSISTENCE_STAGE_1_PLAYER_SAVED cobble={}",
                    count(player, Items.COBBLESTONE));
            return;
        }

        require(state.model().hasCleared(PLAYER_ID, LandmarkKey.FRAMEFALL),
                "The boss clear was not restored from the previous server save");
        if (stage == 2) {
            require(!account.hasLore(LandmarkKey.FRAMEFALL)
                    && !account.hasSignature(LandmarkKey.FRAMEFALL)
                    && !account.hasSupplies(LandmarkKey.FRAMEFALL)
                    && count(player, Items.WRITTEN_BOOK) == 0
                    && count(player, BossFeatures.PARALLAX_FRAME) == 0
                    && count(player, Items.AMETHYST_SHARD) == 0
                    && count(player, Items.COBBLESTONE) == 36 * 64,
                    "Full inventory or pending receipts did not survive restart/reconnect: lore="
                            + account.hasLore(LandmarkKey.FRAMEFALL)
                            + ", signature=" + account.hasSignature(LandmarkKey.FRAMEFALL)
                            + ", supplies=" + account.hasSupplies(LandmarkKey.FRAMEFALL)
                            + ", books=" + count(player, Items.WRITTEN_BOOK)
                            + ", frames=" + count(player, BossFeatures.PARALLAX_FRAME)
                            + ", shards=" + count(player, Items.AMETHYST_SHARD)
                            + ", cobble=" + count(player, Items.COBBLESTONE)
                            + ", slots=" + player.getInventory().getNonEquipmentItems().size());
            for (int slot = 0; slot < 3; slot++) player.getInventory().setItem(slot, ItemStack.EMPTY);
            CampaignRewards.deliverPendingLore(player);
            CampaignSignatures.deliver(player);
        }
        assertDelivered(player, account);
        require(count(player, Items.COBBLESTONE) == 33 * 64,
                "The original carried inventory did not survive delivery/reconnect");
        CampaignRewards.deliverPendingLore(player);
        CampaignSignatures.deliver(player);
        assertDelivered(player, account);
    }

    private static void assertDelivered(ServerPlayer player, CampaignDeliveryAccount account) {
        require(account.hasLore(LandmarkKey.FRAMEFALL)
                && account.hasSignature(LandmarkKey.FRAMEFALL)
                && account.hasSupplies(LandmarkKey.FRAMEFALL)
                && count(player, Items.WRITTEN_BOOK) == 1
                && count(player, BossFeatures.PARALLAX_FRAME) == 1
                && count(player, Items.AMETHYST_SHARD) == 2,
                "Saved or retried boss rewards are missing or duplicated: lore="
                        + count(player, Items.WRITTEN_BOOK) + ", signature="
                        + count(player, BossFeatures.PARALLAX_FRAME) + ", supplies="
                        + count(player, Items.AMETHYST_SHARD));
    }

    private static ServerPlayer connect(MinecraftServer server) {
        GameProfile profile = new GameProfile(PLAYER_ID, "RewardProbe");
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(server, server.overworld(),
                cookie.gameProfile(), cookie.clientInformation());
        // 26.3 loads playerdata before PlayerList.placeNewPlayer in the login
        // pipeline. Embedded connections bypass that earlier login step.
        server.getPlayerList().loadPlayerData(player.nameAndId()).ifPresent(data ->
                player.load(TagValueInput.create(ProblemReporter.DISCARDING,
                        server.registryAccess(), data)));
        LOGGER.info("REWARD_PERSISTENCE_PRELOGIN_LOADED cobble={}", count(player, Items.COBBLESTONE));
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        if (player.connection != null) {
            for (int tick = 0; tick < 80 && !player.connection.hasClientLoaded(); tick++) {
                player.connection.tickClientLoadTimeout();
            }
        }
        return player;
    }

    private static int count(ServerPlayer player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
