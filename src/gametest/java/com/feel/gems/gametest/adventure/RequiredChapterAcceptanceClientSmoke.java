package com.feel.gems.gametest.adventure;

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
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opt-in client half of the two-player dedicated chapter receipt. */
public final class RequiredChapterAcceptanceClientSmoke implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("gems-required-chapter-client");
    private boolean enabled;
    private boolean connecting;
    private boolean attached;
    private boolean complete;
    private boolean sawJoinProgress;
    private String phase;
    private String role;
    private String route;
    private int ticks;
    private int titleTicks;
    private int attachedAt;
    private int receivedAt;
    private int lastPacketAt;
    private int attempts;
    private int packets;
    private Instruction pending;
    private Instruction awaiting;

    @Override
    public void onInitializeClient() {
        enabled = Boolean.getBoolean("gems.requiredChapterAcceptance.client");
        if (!enabled) return;
        phase = System.getProperty("gems.requiredChapterAcceptance.phase", "");
        role = System.getProperty("gems.requiredChapterAcceptance.role", "");
        route = System.getProperty("gems.requiredChapterAcceptance.route", "remember");
        if (!(phase.equals("before") || phase.equals("after") || phase.equals("court"))
                || !(role.equals("A") || role.equals("B")))
            fail("Phase and role must be explicit");
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> receive(message));
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, type, time) ->
                receive(message));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void receive(Component message) {
        String value = message.getString();
        if (value.startsWith("REQCH|FAILED|")) fail("Server: " + value);
        if (value.equals("REQCH|COMPLETE")) {
            if (pending != null || awaiting != null) fail("Completed with an unacknowledged use");
            complete = true;
            LOG.info("REQCH_CLIENT_COMPLETE phase={} route={} role={} realPackets={} lateJoinProgress={}",
                    phase, route, role, packets, sawJoinProgress);
            return;
        }
        if (value.startsWith("REQCH|USE|")) {
            if (pending != null || awaiting != null) fail("Overlapping chapter instructions");
            pending = Instruction.parse(value);
            receivedAt = ticks;
            LOG.info("REQCH_CLIENT_INSTRUCTION phase={} role={} stage={} target={}",
                    phase, role, pending.stage, pending.pos);
            return;
        }
        if (value.startsWith("This world chose " + routeLabel() + ".")
                && value.contains("2/3")) {
            sawJoinProgress = true;
            LOG.info("REQCH_CLIENT_LATE_JOIN phase={} role={} sharedRecords=2/3 text={}",
                    phase, role, value);
        }
        Instruction active = awaiting;
        if (active != null && value.startsWith(active.expected)) {
            LOG.info("REQCH_CLIENT_GUIDANCE phase={} role={} stage={} text={}",
                    phase, role, active.stage, value);
            Minecraft client = Minecraft.getInstance();
            if (client.getConnection() == null) fail("Disconnected before guidance ACK");
            client.getConnection().sendCommand("reqchapter ack " + active.stage);
            LOG.info("REQCH_CLIENT_ACK phase={} role={} stage={}", phase, role, active.stage);
            awaiting = null;
        }
    }

    private void tick(Minecraft client) {
        if (!enabled) return;
        ticks++;
        if (complete) {
            if (ticks % 40 == 0) client.stop();
            return;
        }
        if (!attached) {
            if (client.gui.overlay() == null && client.gui.screen() instanceof TitleScreen)
                titleTicks++;
            else titleTicks = 0;
            if (!connecting && client.gui.overlay() == null
                    && (titleTicks >= 40 || ticks >= 200)) {
                connecting = true;
                String address = "127.0.0.1:"
                        + Integer.getInteger("gems.requiredChapterAcceptance.port", 25592);
                LOG.info("REQCH_CLIENT_CONNECT phase={} role={} address={}", phase, role, address);
                ConnectScreen.startConnecting(client.gui.screen(), client,
                        ServerAddress.parseString(address),
                        new ServerData("Required chapter acceptance", address, ServerData.Type.OTHER),
                        false, null);
            }
            if (client.player == null || client.level == null || client.gameMode == null
                    || client.getConnection() == null) {
                if (ticks > 20 * 150) fail("Dedicated loopback join timeout");
                return;
            }
            if (client.getSingleplayerServer() != null)
                fail("Integrated server invalidates dedicated evidence");
            attached = true;
            attachedAt = ticks;
            LOG.info("REQCH_CLIENT_ATTACHED phase={} role={} player={} integrated=false",
                    phase, role, client.player.getName().getString());
        }
        if (ticks > 20 * 720) fail("Twelve-minute connected fixture timeout");
        if (phase.equals("after") && role.equals("B") && !sawJoinProgress
                && ticks - attachedAt > 20 * 30)
            fail("Late join did not receive persisted Ash 2/3 guidance");
        if (awaiting != null) {
            if (awaiting.deferred) {
                if (ticks - lastPacketAt > 20 * 12)
                    fail("Timed hold did not resolve: " + awaiting.stage);
                return;
            }
            if (ticks - lastPacketAt < 40) return;
            if (attempts >= 3) fail("No production response to real packet " + awaiting.stage);
            sendUse(client, awaiting);
            return;
        }
        Instruction instruction = pending;
        if (instruction == null || ticks - receivedAt < 24) return;
        if (phase.equals("after") && role.equals("B") && !sawJoinProgress) return;
        if (client.gui.screen() != null || ticks - attachedAt < 40) {
            if (ticks - receivedAt > 20 * 30)
                fail("World screen did not clear for " + instruction.stage);
            return;
        }
        if (client.level.getBlockState(instruction.pos).isAir()
                || client.player.distanceToSqr(Vec3.atCenterOf(instruction.pos)) > 25) {
            if (ticks - receivedAt > 20 * 30)
                fail("Saved chapter control did not become reachable: " + instruction.stage);
            return;
        }
        pending = null;
        awaiting = instruction;
        attempts = 0;
        sendUse(client, instruction);
    }

    private void sendUse(Minecraft client, Instruction instruction) {
        if (client.player == null || client.gameMode == null || client.level == null
                || client.level.getBlockState(instruction.pos).isAir()
                || client.player.distanceToSqr(Vec3.atCenterOf(instruction.pos)) > 25)
            fail("Control disappeared before packet " + instruction.stage);
        if (instruction.stage.equals("court_east_room") && route.equals("repair")) {
            int slot = -1;
            for (int index = 0; index < 9; index++)
                if (client.player.getInventory().getItem(index).is(Items.GLASS_PANE)) {
                    slot = index;
                    break;
                }
            if (slot < 0) fail("Test-only glass pane missing from hotbar");
            client.player.getInventory().setSelectedSlot(slot);
        }
        attempts++;
        lastPacketAt = ticks;
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(instruction.pos),
                Direction.UP, instruction.pos, false);
        var result = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
        packets++;
        LOG.info("REQCH_CLIENT_PACKET phase={} role={} stage={} target={} attempt={} result={}",
                phase, role, instruction.stage, instruction.pos, attempts, result);
    }

    private static void fail(String reason) {
        LOG.error("REQCH_CLIENT_FAILED reason={}", reason);
        throw new IllegalStateException("Required chapter client: " + reason);
    }

    private String routeLabel() {
        return Character.toUpperCase(route.charAt(0)) + route.substring(1);
    }

    private record Instruction(String stage, BlockPos pos, String expected, boolean deferred) {
        static Instruction parse(String value) {
            String[] fields = value.split("\\|", -1);
            if (fields.length != 8 || !fields[0].equals("REQCH")
                    || !fields[1].equals("USE") || fields[6].isBlank())
                throw new IllegalArgumentException("Invalid chapter instruction: " + value);
            return new Instruction(fields[2], new BlockPos(Integer.parseInt(fields[3]),
                    Integer.parseInt(fields[4]), Integer.parseInt(fields[5])), fields[6],
                    Boolean.parseBoolean(fields[7]));
        }
    }
}
