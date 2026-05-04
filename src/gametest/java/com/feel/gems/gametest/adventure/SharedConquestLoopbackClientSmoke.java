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

/** Sends real ledger-use packets from an opt-in dedicated loopback client. */
public final class SharedConquestLoopbackClientSmoke implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("gems-shared-conquest-client");
    private static final BlockPos LEDGER = new BlockPos(0, 150, -1);
    private String role;
    private String phase;
    private boolean connecting;
    private boolean attached;
    private boolean complete;
    private String requested;
    private String awaiting;
    private int ticks;
    private int titleTicks;
    private int requestedAt;
    private int packets;

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("gems.sharedConquestLoopback.client")) return;
        role = System.getProperty("gems.sharedConquestLoopback.role", "");
        phase = System.getProperty("gems.sharedConquestLoopback.phase", "");
        if (!(role.equals("A") || role.equals("B"))
                || !(phase.equals("initial") || phase.equals("resume")))
            throw new IllegalArgumentException("Explicit two-client role and phase required");
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> receive(message));
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, type, time) -> receive(message));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void receive(Component message) {
        String value = message.getString();
        if (value.startsWith("SCMP|FAILED|")) fail("server: " + value);
        if (value.equals("SCMP|COMPLETE|" + phase)) {
            if (requested != null || awaiting != null) fail("server completed with pending ledger use");
            complete = true;
            LOG.info("SCMP_CLIENT_COMPLETE phase={} role={} realUsePackets={}", phase, role, packets);
            return;
        }
        if (value.startsWith("SCMP|USE|")) {
            if (requested != null || awaiting != null) fail("overlapping ledger instruction");
            requested = value.substring("SCMP|USE|".length());
            if (!(requested.equals("locked") || requested.equals("unlocked")
                    || requested.equals("persisted") || requested.equals("late")))
                fail("unknown ledger stage " + requested);
            requestedAt = ticks;
            LOG.info("SCMP_CLIENT_INSTRUCTION phase={} role={} stage={}", phase, role, requested);
            return;
        }
        String expected = awaiting != null && awaiting.equals("locked")
                ? "Clear Hushwire to call a scout."
                : "A scout will stay beside you for 30 seconds.";
        if (awaiting != null && value.equals(expected)) {
            String stage = awaiting;
            awaiting = null;
            Minecraft client = Minecraft.getInstance();
            if (client.getConnection() == null) fail("disconnected before ledger acknowledgement");
            client.getConnection().sendCommand("scmp ack " + stage);
            LOG.info("SCMP_CLIENT_LEDGER phase={} role={} stage={} productionMessage={} packet=true",
                    phase, role, stage, value);
        }
    }

    private void tick(Minecraft client) {
        ticks++;
        if (complete) {
            client.stop();
            return;
        }
        if (!attached) {
            if (client.gui.overlay() == null && client.gui.screen() instanceof TitleScreen)
                titleTicks++;
            else titleTicks = 0;
            if (!connecting && titleTicks >= 100) {
                connecting = true;
                String address = "127.0.0.1:"
                        + System.getProperty("gems.sharedConquestLoopback.port", "25594");
                LOG.info("SCMP_CLIENT_CONNECT phase={} role={} address={}", phase, role, address);
                ConnectScreen.startConnecting(client.gui.screen(), client,
                        ServerAddress.parseString(address),
                        new ServerData("Shared conquest QA", address, ServerData.Type.OTHER),
                        false, null);
            }
            if (client.player == null || client.level == null || client.gameMode == null
                    || client.getConnection() == null) {
                if (ticks > 20 * 240) fail("connection timeout");
                return;
            }
            if (client.getSingleplayerServer() != null) fail("connected to integrated server");
            if (!client.level.getBlockState(LEDGER).is(DomainBlocks.TOWNHALL_LEDGER)) {
                if (ticks > 20 * 240) fail("staged ledger never loaded");
                return;
            }
            attached = true;
            LOG.info("SCMP_CLIENT_ATTACHED phase={} role={} integrated=false", phase, role);
        }
        if (requested != null && ticks - requestedAt >= 15) {
            String stage = requested;
            requested = null;
            awaiting = stage;
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(LEDGER), Direction.UP, LEDGER, false);
            var result = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
            packets++;
            LOG.info("SCMP_CLIENT_USE phase={} role={} stage={} result={}", phase, role, stage, result);
        }
        if (awaiting != null && ticks - requestedAt > 20 * 30)
            fail("no production ledger message after stage " + awaiting);
        if (ticks > 20 * 480) fail("eight-minute client timeout");
    }

    private static void fail(String reason) {
        LOG.error("SCMP_CLIENT_FAILED reason={}", reason);
        throw new IllegalStateException("Shared conquest loopback client: " + reason);
    }
}
