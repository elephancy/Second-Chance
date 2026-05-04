package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.DomainBlocks;
import com.feel.gems.adventure.boss.PaintedVerdictEntity;
import com.feel.gems.adventure.boss.VerdictSequence;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Real client input/attack packets for a natural Framefall first-clear slice. */
public final class ConnectedJourneyClientSmoke implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("gems-connected-journey-client");
    private String phase;
    private String role;
    private boolean connecting;
    private boolean attached;
    private boolean completed;
    private boolean fighting;
    private boolean deskPending;
    private boolean deskAwaiting;
    private BlockPos desk;
    private VerdictSequence.Phase previous = VerdictSequence.Phase.DORMANT;
    private int ticks;
    private int titleTicks;
    private int fightTicks;
    private int phaseTicks;
    private int attacks;
    private int movedTicks;
    private int uses;
    private int deskAt;

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("gems.connectedJourney.client")) return;
        phase = System.getProperty("gems.connectedJourney.phase", "");
        role = System.getProperty("gems.connectedJourney.role", "");
        if (!(phase.equals("initial") || phase.equals("resume"))
                || !(role.equals("A") || role.equals("B")))
            throw new IllegalArgumentException("Explicit connected journey phase and role required");
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> receive(message));
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, type, time) -> receive(message));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void receive(Component message) {
        String value = message.getString();
        if (value.startsWith("CJ|FAILED|")) fail("server: " + value);
        if (value.equals("CJ|COMPLETE|" + phase)) {
            if (deskPending || deskAwaiting || fighting) fail("completed with pending input");
            completed = true;
            LOG.info("CJ_CLIENT_COMPLETE phase={} role={} attacks={} normalMoveTicks={} realUsePackets={}",
                    phase, role, attacks, movedTicks, uses);
            return;
        }
        if (value.equals("CJ|FIGHT")) {
            if (!phase.equals("initial") || !role.equals("A") || fighting || deskPending)
                fail("unexpected fight stage");
            fighting = true;
            fightTicks = ticks;
            LOG.info("CJ_CLIENT_FIGHT_BEGIN realClient=true stagedTransport=true");
            return;
        }
        if (value.startsWith("CJ|USE|angle|")) {
            if (deskPending || deskAwaiting) fail("duplicate desk instruction");
            String[] parts = value.split("\\|");
            if (parts.length != 6) fail("malformed desk instruction");
            desk = new BlockPos(Integer.parseInt(parts[3]), Integer.parseInt(parts[4]),
                    Integer.parseInt(parts[5]));
            fighting = false;
            deskPending = true;
            deskAt = ticks;
            stopKeys(Minecraft.getInstance());
            LOG.info("CJ_CLIENT_DESK phase={} role={} target={}", phase, role, desk);
            return;
        }
        if (deskAwaiting && (value.startsWith("Angle Lens ready for Framefall Gallery.")
                || value.startsWith("Lens: Framefall Gallery."))) {
            deskAwaiting = false;
            Minecraft client = Minecraft.getInstance();
            if (client.getConnection() == null) fail("disconnected before desk acknowledgement");
            client.getConnection().sendCommand("connectedjourney ack angle");
            LOG.info("CJ_CLIENT_DESK_ACK role={} productionMessage={} packet=true", role, value);
        }
    }

    private void tick(Minecraft client) {
        ticks++;
        if (completed) {
            stopKeys(client);
            if (ticks % 40 == 0) client.stop();
            return;
        }
        if (!attached) {
            if (client.gui.overlay() == null && client.gui.screen() instanceof TitleScreen)
                titleTicks++;
            else titleTicks = 0;
            if (!connecting && (titleTicks >= 40 || ticks >= 200)) {
                connecting = true;
                String address = "127.0.0.1:"
                        + System.getProperty("gems.connectedJourney.port", "25597");
                ConnectScreen.startConnecting(client.gui.screen(), client,
                        ServerAddress.parseString(address),
                        new ServerData("Connected journey QA", address, ServerData.Type.OTHER),
                        false, null);
            }
            if (client.player == null || client.level == null || client.gameMode == null
                    || client.getConnection() == null) {
                if (ticks > 20 * 180) fail("dedicated join timeout");
                return;
            }
            if (client.getSingleplayerServer() != null) fail("integrated server invalidates dedicated proof");
            attached = true;
            LOG.info("CJ_CLIENT_ATTACHED phase={} role={} dedicated=true", phase, role);
        }
        if (fighting) fight(client);
        if (deskPending) useDesk(client);
        if (deskAwaiting && ticks - deskAt > 20 * 30)
            fail("natural Angle Desk did not answer real use packet");
        if (ticks > 20 * 900) fail("fifteen-minute client timeout");
    }

    private void fight(Minecraft client) {
        if (ticks - fightTicks > 20 * 360) fail("first boss fight exceeded six minutes");
        var bosses = client.level.getEntitiesOfClass(PaintedVerdictEntity.class,
                new AABB(client.player.blockPosition()).inflate(32));
        if (bosses.size() != 1) {
            stopKeys(client);
            if (ticks - fightTicks > 20 * 30) fail("one natural boss was not visible to client");
            return;
        }
        PaintedVerdictEntity boss = bosses.getFirst();
        VerdictSequence.Phase phaseNow = boss.phase();
        if (phaseNow != previous) {
            LOG.info("CJ_CLIENT_BOSS_PHASE {} -> {} health={}", previous, phaseNow,
                    boss.getHealth());
            previous = phaseNow;
            phaseTicks = 0;
        } else phaseTicks++;
        double dx = boss.getX() - client.player.getX();
        double dz = boss.getZ() - client.player.getZ();
        double distance = Math.hypot(dx, dz);
        client.player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        client.player.setXRot(0.0F);
        boolean dodge = phaseNow == VerdictSequence.Phase.TELL
                && phaseTicks >= 32 && phaseTicks <= 49;
        client.options.keyRight.setDown(dodge);
        client.options.keyUp.setDown(!dodge && distance > 2.4);
        client.options.keyJump.setDown(!dodge && distance > 2.4
                && client.player.horizontalCollision);
        if (dodge || distance > 2.4) movedTicks++;
        if (phaseNow == VerdictSequence.Phase.RECOVERY && distance <= 3.2
                && phaseTicks % 14 == 2) {
            client.gameMode.attack(client.player, boss);
            attacks++;
            LOG.info("CJ_CLIENT_ATTACK count={} distance={} phase={} health={}",
                    attacks, distance, phaseNow, boss.getHealth());
        }
    }

    private void useDesk(Minecraft client) {
        if (ticks - deskAt < 25 || client.gui.screen() != null) return;
        if (!client.level.getBlockState(desk).is(DomainBlocks.TOWNHALL_ANGLE_DESK)
                || client.player.distanceToSqr(Vec3.atCenterOf(desk)) > 25) {
            if (ticks - deskAt > 20 * 30) fail("natural Angle Desk was not reachable");
            return;
        }
        deskPending = false;
        deskAwaiting = true;
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(desk), Direction.UP, desk, false);
        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
        uses++;
        LOG.info("CJ_CLIENT_USE phase={} role={} target={} realPacket=true", phase, role, desk);
    }

    private static void stopKeys(Minecraft client) {
        client.options.keyUp.setDown(false);
        client.options.keyRight.setDown(false);
        client.options.keyJump.setDown(false);
    }

    private static void fail(String reason) {
        LOG.error("CJ_CLIENT_FAILED reason={}", reason);
        throw new IllegalStateException("Connected journey client: " + reason);
    }
}
