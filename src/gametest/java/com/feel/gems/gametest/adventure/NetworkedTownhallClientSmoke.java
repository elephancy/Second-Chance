package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.DomainBlocks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Drives actual client-to-server use packets over a local dedicated connection. */
public final class NetworkedTownhallClientSmoke implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("gems-network-townhall-client");
    private static final int Y = 150;
    private static final BlockPos STORY = new BlockPos(1, Y, 0);
    private static final BlockPos SCHOOL = new BlockPos(0, Y, 1);
    private static final BlockPos FORUM = new BlockPos(-1, Y, 0);
    private static final BlockPos WELCOME = new BlockPos(0, Y, -1);
    private boolean attached;
    private boolean connecting;
    private boolean bJoined;
    private boolean welcomeRead;
    private int ticks;
    private int titleReadyTicks;
    private int nextAction;
    private int stopAt;

    @Override
    public void onInitializeClient() {
        String role = System.getProperty("gems.networkTownhallClient", "");
        if (!role.equals("A") && !role.equals("B")) return;
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> receive(role, message));
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, type, time) ->
                receive(role, message));
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client, role));
    }

    private void receive(String role, Component message) {
        String text = message.getString();
        if (role.equals("A") && text.equals("NETWORK_SMOKE_B_JOINED")) {
            bJoined = true;
            LOGGER.info("NETWORK_CLIENT_A_SAW_LATE_JOIN");
            stopAt = ticks + 80;
        }
        if (role.equals("B") && text.startsWith("Make a Rumor Atlas with a compass and paper.")) {
            welcomeRead = true;
            LOGGER.info("NETWORK_CLIENT_B_WELCOME sharedGuide=true text={}", text);
            stopAt = ticks + 60;
        }
    }

    private void tick(Minecraft client, String role) {
        ticks++;
        if (!attached) {
            if (client.gui.overlay() == null && client.gui.screen() instanceof TitleScreen) {
                titleReadyTicks++;
            } else {
                titleReadyTicks = 0;
            }
            if (ticks % 200 == 0) {
                LOGGER.info("NETWORK_CLIENT_WAIT role={} ticks={} screen={} overlay={} titleReadyTicks={} connecting={}",
                        role, ticks,
                        client.gui.screen() == null ? "null" : client.gui.screen().getClass().getName(),
                        client.gui.overlay() == null ? "null" : client.gui.overlay().getClass().getName(),
                        titleReadyTicks, connecting);
            }
            if (!connecting && titleReadyTicks >= 100) {
                connecting = true;
                String address = "127.0.0.1:25575";
                LOGGER.info("NETWORK_CLIENT_CONNECT role={} address={}", role, address);
                ConnectScreen.startConnecting(client.gui.screen(), client,
                        ServerAddress.parseString(address),
                        new ServerData("Network smoke", address, ServerData.Type.OTHER),
                        false, null);
            }
            if (client.player == null || client.level == null || client.gameMode == null
                    || client.getConnection() == null) {
                if (ticks > 20 * 180) stop(client, role, "connection timeout");
                return;
            }
            if (client.getSingleplayerServer() != null) {
                stop(client, role, "integrated server rather than dedicated connection");
                return;
            }
            if (!client.level.getBlockState(STORY).is(DomainBlocks.FAM_STORY_CLUE)
                    || !client.level.getBlockState(WELCOME).is(DomainBlocks.TOWNHALL_WELCOME_BOARD)) {
                if (ticks > 20 * 180) stop(client, role, "fixture blocks never loaded");
                return;
            }
            attached = true;
            ticks = 0;
            LOGGER.info("NETWORK_CLIENT_ATTACHED role={} player={} integrated=false", role,
                    client.player.getName().getString());
        }
        if (role.equals("A")) {
            if (nextAction == 0 && ticks >= 30) {
                use(client, role, "story", STORY);
                nextAction++;
            } else if (nextAction == 1 && ticks >= 50) {
                use(client, role, "school", SCHOOL);
                nextAction++;
            } else if (nextAction == 2 && ticks >= 70) {
                use(client, role, "forum", FORUM);
                nextAction++;
            } else if (nextAction == 3 && ticks >= 90) {
                use(client, role, "townhall-welcome", WELCOME);
                nextAction++;
            }
        } else if (nextAction == 0 && ticks >= 30) {
            use(client, role, "townhall-welcome", WELCOME);
            nextAction++;
        }
        if (stopAt > 0 && ticks >= stopAt) {
            LOGGER.info("NETWORK_CLIENT_COMPLETE role={} actions={} lateJoin={} welcomeRead={}",
                    role, nextAction, bJoined, welcomeRead);
            client.stop();
        }
        if (ticks > 20 * 180) stop(client, role, "action/message timeout");
    }

    private static void use(Minecraft client, String role, String step, BlockPos block) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(block), Direction.UP, block, false);
        var result = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
        LOGGER.info("NETWORK_CLIENT_USE role={} step={} result={}", role, step, result);
    }

    private static void stop(Minecraft client, String role, String reason) {
        LOGGER.error("NETWORK_CLIENT_FAILED role={} reason={}", role, reason);
        throw new IllegalStateException("Network townhall client " + role + " failed: " + reason);
    }
}
