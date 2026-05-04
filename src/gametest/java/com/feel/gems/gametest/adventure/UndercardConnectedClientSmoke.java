package com.feel.gems.gametest.adventure;

import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Real client input and packets for the copied natural Undercard venue. */
public final class UndercardConnectedClientSmoke implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("gems-undercard-connected-client");
    private static final BlockPos GATE = new BlockPos(-701, 64, -1135);
    private static final BlockPos BELL = new BlockPos(-731, 36, -1135);
    private final String role = System.getProperty("gems.undercardConnected.role", "");
    private final String phase = System.getProperty("gems.undercardConnected.phase", "");
    private boolean connecting;
    private boolean attached;
    private boolean complete;
    private int ticks;
    private int titleTicks;
    private int actionAt;
    private int scoreTarget = -1;
    private int hits;
    private int waypoint;
    private String action = "";
    private List<BlockPos> path = List.of();

    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("gems.undercardConnected.client")) return;
        if (!(role.equals("A") || role.equals("B"))
                || !(phase.equals("initial") || phase.equals("restart")))
            throw new IllegalArgumentException("Undercard connected client role/phase required");
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> receive(message.getString()));
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, type, time) ->
                receive(message.getString()));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void receive(String value) {
        if (value.startsWith("UDC|FAIL|")) fail(value);
        if (value.equals("UDC|COMPLETE|" + phase)) {
            if (!action.isEmpty() || !path.isEmpty()) fail("completion with pending input");
            complete = true;
            LOG.info("UDC_CLIENT_PASS phase={} role={} realConnection=true normalInput=true hits={}",
                    phase, role, hits);
        } else if (value.equals("UDC|PREPARE")) {
            accept("prepare");
        } else if (value.startsWith("UDC|DESCEND|")) {
            accept("descend");
        } else if (value.startsWith("UDC|ASCEND|")) {
            accept("ascend");
        } else if (value.startsWith("UDC|WALK|")) {
            String[] parts = value.split("\\|", 4);
            if (parts.length != 4 || !(parts[2].equals("in") || parts[2].equals("out")))
                fail("bad route instruction");
            accept("walk-" + parts[2]);
            path = decode(parts[3]);
            waypoint = 1;
            LOG.info("UDC_CLIENT_ROUTE phase={} role={} direction={} steps={}",
                    phase, role, parts[2], path.size());
        } else if (value.startsWith("UDC|USE|")) {
            accept("use-" + value.substring("UDC|USE|".length()));
        } else if (value.startsWith("UDC|SCORE|")) {
            accept("score");
            scoreTarget = Integer.parseInt(value.substring("UDC|SCORE|".length()));
        } else if (value.equals("UDC|DISCONNECT")) {
            LOG.info("UDC_CLIENT_DISCONNECT phase={} role={} requested=true", phase, role);
            Minecraft.getInstance().stop();
        }
    }

    private void accept(String next) {
        if (!action.isEmpty()) fail("overlapping input: " + action + " then " + next);
        action = next;
        actionAt = ticks;
    }

    private void tick(Minecraft client) {
        ticks++;
        if (complete) { stopKeys(client); client.stop(); return; }
        if (!attached) {
            if (client.gui.overlay() == null && client.gui.screen() instanceof TitleScreen)
                titleTicks++;
            else titleTicks = 0;
            if (!connecting && (titleTicks >= 40 || ticks >= 200)) {
                connecting = true;
                String address = "127.0.0.1:"
                        + System.getProperty("gems.undercardConnected.port", "25609");
                LOG.info("UDC_CLIENT_CONNECT phase={} role={} address={} titleTicks={}",
                        phase, role, address, titleTicks);
                ConnectScreen.startConnecting(client.gui.screen(), client,
                        ServerAddress.parseString(address),
                        new ServerData("Undercard QA", address, ServerData.Type.OTHER), false, null);
            }
            if (client.player != null && client.getConnection() != null) {
                attached = true;
                if (client.getSingleplayerServer() != null)
                    fail("connected gate reached an integrated server");
                LOG.info("UDC_CLIENT_JOIN phase={} role={} connected=true", phase, role);
            }
            if (ticks > 20 * 180) fail("client could not connect");
            return;
        }
        if (client.player == null || client.getConnection() == null)
            fail("lost connected player");
        if (ticks > 20 * 360) fail("client interaction timeout");
        if (action.isEmpty()) return;
        if (ticks - actionAt > 20 * 90) fail("input timed out: " + action);
        if (action.equals("prepare")) {
            client.options.keyShift.setDown(true);
            action = "";
            client.getConnection().sendCommand("udc prepared");
        } else if (action.equals("descend")) {
            // Sneak pins a player to a ladder. Release it and let the backed
            // ladder's normal, slow slide carry the player down the shaft.
            client.options.keyShift.setDown(false);
            if ((ticks - actionAt) % 100 == 0)
                LOG.info("UDC_CLIENT_DESCENT_PROGRESS y={} elapsedTicks={}",
                        client.player.getY(), ticks - actionAt);
            if (client.player.getY() <= 37.8) {
                action = "";
                client.getConnection().sendCommand("udc descended");
                LOG.info("UDC_CLIENT_DESCENDED normalInput=true y={}", client.player.getY());
            }
        } else if (action.equals("ascend")) {
            client.player.setYRot(-90);
            client.options.keyUp.setDown(true);
            if ((ticks - actionAt) % 100 == 0)
                LOG.info("UDC_CLIENT_ASCENT_PROGRESS y={} elapsedTicks={}",
                        client.player.getY(), ticks - actionAt);
            if (client.player.getY() >= GATE.getY() - .5) {
                client.options.keyUp.setDown(false);
                client.options.keyShift.setDown(true);
                action = "";
                client.getConnection().sendCommand("udc ascended");
                LOG.info("UDC_CLIENT_ASCENDED normalInput=true y={}", client.player.getY());
            }
        } else if (action.startsWith("walk-")) {
            drive(client);
        } else if (action.startsWith("use-")) {
            client.options.keyShift.setDown(!action.equals("use-solo-after-disconnect"));
            if (ticks - actionAt < 10 || !client.level.hasChunkAt(BELL)
                    || client.player.distanceToSqr(Vec3.atCenterOf(BELL)) > 4.5 * 4.5) {
                if ((ticks - actionAt) % 20 == 0) {
                    LOG.info("UDC_CLIENT_USE_WAIT role={} stage={} pos={} bellLoaded={}",
                            role, action, client.player.position(), client.level.hasChunkAt(BELL));
                }
                return;
            }
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(BELL),
                    Direction.UP, BELL, false);
            var result = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
            client.options.keyShift.setDown(false);
            String stage = action.substring("use-".length());
            action = "";
            client.getConnection().sendCommand("udc " + stage);
            LOG.info("UDC_CLIENT_USE role={} stage={} pos={} result={} packet=true",
                    role, stage, client.player.position(), result);
        } else if (action.equals("score")) {
            if (hits >= 3) { action = ""; return; }
            if (ticks - actionAt < 15 + hits * 24) return;
            var target = client.level.getEntity(scoreTarget);
            if (target == null) fail("duel target not tracked by client");
            client.gameMode.attack(client.player, target);
            hits++;
            client.getConnection().sendCommand("udc hit");
            LOG.info("UDC_CLIENT_SCORE role={} hit={} realAttackPacket=true", role, hits);
            if (hits == 3) action = "";
        }
    }

    private void drive(Minecraft client) {
        client.options.keyShift.setDown(false);
        if (waypoint >= path.size()) {
            stopKeys(client);
            String done = action;
            path = List.of(); action = "";
            client.getConnection().sendCommand("udc " + done);
            LOG.info("UDC_CLIENT_WALK role={} direction={} normalInput=true steps={}",
                    role, done, waypoint);
            return;
        }
        BlockPos targetFeet = path.get(waypoint);
        Vec3 target = Vec3.atBottomCenterOf(targetFeet);
        double dx = target.x - client.player.getX();
        double dz = target.z - client.player.getZ();
        if (client.player.blockPosition().getX() == targetFeet.getX()
                && client.player.blockPosition().getZ() == targetFeet.getZ()
                && dx * dx + dz * dz < .35 * .35) {
            waypoint++;
            actionAt = ticks;
            return;
        }
        if (!client.level.hasChunkAt(targetFeet)) { stopKeys(client); return; }
        client.player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        client.options.keyUp.setDown(true);
    }

    private static List<BlockPos> decode(String encoded) {
        List<BlockPos> result = new ArrayList<>();
        for (String token : encoded.split(";")) {
            String[] xyz = token.split(",");
            if (xyz.length != 3 || result.size() > 4096)
                throw new IllegalArgumentException("invalid bounded Undercard route");
            result.add(new BlockPos(Integer.parseInt(xyz[0]),
                    Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2])));
        }
        if (result.size() < 2) throw new IllegalArgumentException("empty Undercard route");
        return result;
    }

    private static void stopKeys(Minecraft client) {
        client.options.keyUp.setDown(false);
        client.options.keyShift.setDown(false);
    }

    private static void fail(String reason) {
        LOG.error("UDC_CLIENT_FAIL {}", reason);
        throw new IllegalStateException(reason);
    }
}
