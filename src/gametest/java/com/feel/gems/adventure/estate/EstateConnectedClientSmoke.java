package com.feel.gems.adventure.estate;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
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

/** Sends real use packets and walks a saved Estate wing with normal key input. */
public final class EstateConnectedClientSmoke implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("gems-estate-connected-client");
    private static final int RETURN_MAX_SECONDS = 45;
    private static final int VIEW_SETTLE_TICKS = 40;
    private final String requestedWing = System.getProperty("gems.estateConnected.wing", "N");
    private final boolean allRoads = requestedWing.equals("all");
    private final boolean restartOnly = Boolean.getBoolean("gems.estateConnected.restart");
    private char wing = allRoads ? 'N' : requestedWing.charAt(0);
    private int wingIndex;
    private boolean enabled;
    private boolean connecting;
    private boolean attached;
    private boolean complete;
    private int ticks;
    private int titleTicks;
    private int receivedAt;
    private int packetAt;
    private int walkAt;
    private int waypointAt;
    private int attempts;
    private int packets;
    private int waypoint;
    private int returnPacketTick;
    private int viewAt;
    private int gateViewAt;
    private int screenshots;
    private long returnPacketNanos;
    private boolean gateView;
    private BlockPos gateMarker;
    private BlockPos foyerMarker;
    private boolean previousHideGui;
    private View view;
    private View gateCamera;
    private Instruction pending;
    private Instruction awaiting;
    private List<BlockPos> path = List.of();

    @Override
    public void onInitializeClient() {
        enabled = Boolean.getBoolean("gems.estateConnected.client");
        if (!enabled) return;
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> receive(message));
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, type, time) ->
                receive(message));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void receive(Component message) {
        String value = message.getString();
        if (value.equals("ESTATEQA|COMPLETE|FAIL")) fail("Server fixture failed");
        if (value.equals("ESTATEQA|COMPLETE|PASS")) {
            if (pending != null || awaiting != null || !path.isEmpty()
                    || view != null || gateView
                    || screenshots != (restartOnly ? 0 : allRoads ? 8 : 2))
                fail("Completion with outstanding connected work");
            complete = true;
            LOG.info("ESTATE_CONNECTED_CLIENT_PASS wing={} allRoads={} restartOnly={} packets={} normalInputSteps={} screenshots={} integrated=false",
                    wing, allRoads, restartOnly, packets, waypoint, screenshots);
            return;
        }
        if (value.startsWith("ESTATEQA|WING|")) {
            String[] fields = value.split("\\|", 5);
            if (fields.length != 5 || fields[3].length() != 1
                    || !fields[3].equals(Integer.toString(wingIndex)))
                fail("Malformed or out-of-order Estate wing");
            char next = fields[2].charAt(0);
            if (fields[2].length() != 1 || "NESW".indexOf(next) < 0
                    || (allRoads && next != "NESW".charAt(wingIndex))
                    || (!allRoads && next != wing)
                    || pending != null || awaiting != null || !path.isEmpty()
                    || view != null || gateView || screenshots != wingIndex * 2)
                fail("Estate wing transition interrupted connected work");
            wing = next;
            LOG.info("ESTATE_CONNECTED_CLIENT_WING wing={} index={} total={}",
                    wing, wingIndex, fields[4]);
            return;
        }
        if (value.startsWith("ESTATEQA|USE|")) {
            if (pending != null || awaiting != null || !path.isEmpty()
                    || view != null || gateView)
                fail("Overlapping Estate instruction");
            pending = Instruction.parse(value);
            if (pending.stage.equals("PRE_FOYER")) foyerMarker = pending.pos;
            if (pending.stage.equals("GATE")) gateMarker = pending.pos;
            receivedAt = ticks;
            LOG.info("ESTATE_CONNECTED_CLIENT_USE stage={} target={}", pending.stage, pending.pos);
            return;
        }
        if (value.startsWith("ESTATEQA|PATH|")) {
            if (pending != null || awaiting != null || !path.isEmpty())
                fail("Path overlaps control instruction");
            path = decode(value);
            waypoint = 1;
            walkAt = ticks;
            waypointAt = ticks;
            LOG.info("ESTATE_CONNECTED_CLIENT_PATH normalKeyInput=true waypoints={}", path.size());
            return;
        }
        if (value.startsWith("ESTATEQA|VIEW|")) {
            if (pending != null || awaiting != null || !path.isEmpty()
                    || view != null || gateView || screenshots != wingIndex * 2)
                fail("Overlapping Estate view instruction");
            view = View.parse(value);
            viewAt = ticks;
            hideGui(Minecraft.getInstance());
            LOG.info("ESTATE_CONNECTED_CLIENT_VIEW wing={} point={} target={}",
                    wing, view.stand, view.look);
            return;
        }
        if (value.startsWith("ESTATEQA|GATEVIEW|")) {
            if (!gateView || gateCamera != null || awaiting != null
                    || screenshots != wingIndex * 2 + 1)
                fail("Out-of-order Estate gate view");
            gateCamera = View.parseGate(value);
            gateViewAt = ticks;
            LOG.info("ESTATE_CONNECTED_CLIENT_GATE_VIEW wing={} point={} target={}",
                    wing, gateCamera.stand, gateCamera.look);
            return;
        }
        if (awaiting != null && value.startsWith(awaiting.expected)) {
            if (awaiting.stage.equals("RETURN")) {
                long latencyMs = (System.nanoTime() - returnPacketNanos) / 1_000_000;
                if (returnPacketNanos == 0 || latencyMs > RETURN_MAX_SECONDS * 1000L)
                    fail("Return exceeded strict connected response limit");
                LOG.info("ESTATE_CONNECTED_CLIENT_RETURN_LATENCY ms={} from=first-use-packet to=outside-response",
                        latencyMs);
                if (gateMarker == null || screenshots != wingIndex * 2 + 1)
                    fail("Gate view lacks a completed chateau capture");
                gateView = true;
                gateViewAt = ticks;
                hideGui(Minecraft.getInstance());
                awaiting = null;
                Minecraft client = Minecraft.getInstance();
                if (client.getConnection() == null) fail("Lost connection before gate view");
                client.getConnection().sendCommand("estateqa gateview");
                return;
            }
            LOG.info("ESTATE_CONNECTED_CLIENT_GUIDANCE stage={} text={}", awaiting.stage, value);
            Minecraft client = Minecraft.getInstance();
            if (client.getConnection() == null) fail("Lost dedicated connection before ACK");
            client.getConnection().sendCommand("estateqa ack");
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
                        + Integer.getInteger("gems.estateConnected.port", 25593);
                ConnectScreen.startConnecting(client.gui.screen(), client,
                        ServerAddress.parseString(address),
                        new ServerData("Estate connected acceptance", address,
                                ServerData.Type.OTHER), false, null);
            }
            if (client.player == null || client.level == null || client.gameMode == null
                    || client.getConnection() == null) {
                if (ticks > 20 * 150) fail("Dedicated loopback join timeout");
                return;
            }
            if (client.getSingleplayerServer() != null)
                fail("Integrated server invalidates this fixture");
            attached = true;
            LOG.info("ESTATE_CONNECTED_CLIENT_JOIN dedicated=true player={}",
                    client.player.getName().getString());
        }
        if (ticks > 20 * (allRoads ? 3600 : 900))
            fail("Estate connected client overall timeout");
        if (view != null) {
            captureChateau(client);
            return;
        }
        if (gateView) {
            captureGate(client);
            return;
        }
        if (!path.isEmpty()) {
            drive(client);
            return;
        }
        if (awaiting != null) {
            if (awaiting.stage.equals("RETURN")) {
                if (ticks - returnPacketTick > 20 * RETURN_MAX_SECONDS
                        || System.nanoTime() - returnPacketNanos
                                > RETURN_MAX_SECONDS * 1_000_000_000L)
                    fail("Return did not answer within 45 seconds of its real use packet");
                return;
            }
            if (ticks - packetAt > 40 && attempts < 3) sendUse(client, awaiting);
            else if (ticks - packetAt > 20 * 12) fail("No production response for " + awaiting.stage);
            return;
        }
        if (pending == null || ticks - receivedAt < 30) return;
        var screen = client.gui.screen();
        double distanceSquared = client.player.distanceToSqr(Vec3.atCenterOf(pending.pos));
        boolean chunkLoaded = client.level.hasChunkAt(pending.pos);
        var block = chunkLoaded ? client.level.getBlockState(pending.pos) : null;
        boolean exactFoyer = !pending.stage.equals("PRE_FOYER")
                || block != null && block.getBlock() == EstateBlocks.FOYER;
        if (screen != null || distanceSquared > 25 || block == null || block.isAir()
                || !exactFoyer) {
            if (ticks - receivedAt > 20 * 30) {
                fail("Saved Estate control not ready: " + pending.stage
                        + " screen=" + (screen == null ? "none" : screen.getClass().getSimpleName())
                        + " distanceSquared=" + distanceSquared
                        + " chunkLoaded=" + chunkLoaded
                        + " clientBlock=" + (block == null ? "unloaded" : block.getBlock()));
            }
            return;
        }
        awaiting = pending;
        pending = null;
        attempts = 0;
        sendUse(client, awaiting);
    }

    private void sendUse(Minecraft client, Instruction instruction) {
        attempts++;
        packetAt = ticks;
        if (instruction.stage.equals("RETURN") && attempts == 1) {
            returnPacketTick = ticks;
            returnPacketNanos = System.nanoTime();
        }
        var hit = new BlockHitResult(Vec3.atCenterOf(instruction.pos), Direction.UP,
                instruction.pos, false);
        var result = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
        packets++;
        LOG.info("ESTATE_CONNECTED_CLIENT_PACKET stage={} attempt={} result={}",
                instruction.stage, attempts, result);
    }

    private void captureChateau(Minecraft client) {
        if (client.player == null || client.level == null || client.getConnection() == null)
            fail("Dedicated view disconnected");
        if (ticks - viewAt > 20 * 25) fail("Chateau view did not render");
        if (client.player.distanceToSqr(Vec3.atBottomCenterOf(view.stand.above())) > 16
                || !client.level.hasChunkAt(view.look)) return;
        aimAt(client, view.look);
        if (ticks - viewAt < VIEW_SETTLE_TICKS || client.gui.screen() != null) return;
        screenshot(client, "mansion-upper-approach");
        restoreGui(client);
        view = null;
        client.getConnection().sendCommand("estateqa ack");
    }

    private void captureGate(Minecraft client) {
        if (client.player == null || client.level == null || client.getConnection() == null)
            fail("Dedicated return view disconnected");
        if (ticks - gateViewAt > 20 * 25) fail("Gate return view did not render");
        if (gateCamera == null
                || client.player.distanceToSqr(Vec3.atBottomCenterOf(gateCamera.stand.above())) > 16
                || !client.level.hasChunkAt(gateCamera.look)) return;
        aimAt(client, gateCamera.look);
        if (ticks - gateViewAt < VIEW_SETTLE_TICKS || client.gui.screen() != null) return;
        screenshot(client, "outer-gate-after-return");
        restoreGui(client);
        gateView = false;
        gateCamera = null;
        client.getConnection().sendCommand("estateqa ack");
        if (allRoads && wingIndex < 3) wingIndex++;
    }

    private static void aimAt(Minecraft client, BlockPos target) {
        Vec3 delta = Vec3.atCenterOf(target).subtract(client.player.getEyePosition());
        client.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y,
                Math.hypot(delta.x, delta.z))));
    }

    private void hideGui(Minecraft client) {
        previousHideGui = client.gui.hud.isHidden();
        if (!previousHideGui) client.gui.hud.toggle();
    }

    private void restoreGui(Minecraft client) {
        if (client.gui.hud.isHidden() != previousHideGui) client.gui.hud.toggle();
    }

    private void screenshot(Minecraft client, String viewName) {
        String file = "estate-connected-v5-" + Character.toLowerCase(wing)
                + "-" + viewName + ".png";
        Screenshot.grab(client.gameDirectory, file,
                client.gameRenderer.mainRenderTarget(), 1,
                message -> LOG.info("ESTATE_CONNECTED_SCREENSHOT_RESULT wing={} view={} result={}",
                        wing, viewName, message.getString()));
        screenshots++;
        LOG.info("ESTATE_CONNECTED_SCREENSHOT wing={} view={} runDir={} playerEye=true humanReviewPending=true",
                wing, viewName, client.gameDirectory);
    }

    private void drive(Minecraft client) {
        if (ticks - walkAt > 20 * 420) fail("Estate wing normal-input walk timed out");
        if (waypoint >= path.size()) {
            stopKeys(client);
            path = List.of();
            client.getConnection().sendCommand("estateqa walked");
            LOG.info("ESTATE_CONNECTED_CLIENT_WALKED normalKeyInput=true steps={}", waypoint);
            return;
        }
        BlockPos targetFloor = path.get(waypoint);
        BlockPos feet = client.player.blockPosition();
        Vec3 target = Vec3.atBottomCenterOf(targetFloor.above());
        double dx = target.x - client.player.getX();
        double dz = target.z - client.player.getZ();
        if (feet.getX() == targetFloor.getX() && feet.getZ() == targetFloor.getZ()
                && feet.getY() >= targetFloor.getY() + 1
                && dx * dx + dz * dz < 0.45 * 0.45) {
            waypoint++;
            waypointAt = ticks;
            return;
        }
        if (ticks - waypointAt > 20 * 10)
            fail("Client stuck before waypoint " + waypoint);
        if (!client.level.hasChunkAt(targetFloor)) {
            stopKeys(client);
            return;
        }
        client.player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        client.options.keyUp.setDown(true);
        client.options.keyJump.setDown(targetFloor.getY() + 1 > feet.getY());
    }

    private static void stopKeys(Minecraft client) {
        client.options.keyUp.setDown(false);
        client.options.keyJump.setDown(false);
    }

    private static List<BlockPos> decode(String value) {
        String[] fields = value.split("\\|", 6);
        if (fields.length != 6) throw new IllegalArgumentException("Malformed Estate path");
        BlockPos current = new BlockPos(Integer.parseInt(fields[2]),
                Integer.parseInt(fields[3]), Integer.parseInt(fields[4]));
        List<BlockPos> result = new ArrayList<>();
        result.add(current);
        for (char code : fields[5].toCharArray()) {
            int valueIndex = code - 'A';
            if (valueIndex < 0 || valueIndex >= 12)
                throw new IllegalArgumentException("Malformed Estate path step");
            int direction = valueIndex / 3;
            int rise = valueIndex % 3 - 1;
            current = current.offset(direction == 0 ? 1 : direction == 1 ? -1 : 0,
                    rise, direction == 2 ? 1 : direction == 3 ? -1 : 0);
            result.add(current);
            if (result.size() > 600) throw new IllegalArgumentException("Estate path exceeds cap");
        }
        return List.copyOf(result);
    }

    private static void fail(String reason) {
        LOG.error("ESTATE_CONNECTED_CLIENT_FAILED reason={}", reason);
        throw new IllegalStateException("Estate connected client: " + reason);
    }

    private record Instruction(String stage, BlockPos pos, String expected) {
        static Instruction parse(String value) {
            String[] fields = value.split("\\|", 7);
            if (fields.length != 7) throw new IllegalArgumentException("Malformed Estate use");
            return new Instruction(fields[2], new BlockPos(Integer.parseInt(fields[3]),
                    Integer.parseInt(fields[4]), Integer.parseInt(fields[5])), fields[6]);
        }
    }

    private record View(BlockPos stand, BlockPos look) {
        static View parse(String value) {
            String[] fields = value.split("\\|", 9);
            if (fields.length != 9 || !fields[1].equals("VIEW")
                    || !fields[2].equals("CHATEAU"))
                throw new IllegalArgumentException("Malformed Estate view");
            return new View(new BlockPos(Integer.parseInt(fields[3]),
                    Integer.parseInt(fields[4]), Integer.parseInt(fields[5])),
                    new BlockPos(Integer.parseInt(fields[6]),
                            Integer.parseInt(fields[7]), Integer.parseInt(fields[8])));
        }

        static View parseGate(String value) {
            String[] fields = value.split("\\|", 8);
            if (fields.length != 8 || !fields[1].equals("GATEVIEW"))
                throw new IllegalArgumentException("Malformed Estate gate view");
            return new View(new BlockPos(Integer.parseInt(fields[2]),
                    Integer.parseInt(fields[3]), Integer.parseInt(fields[4])),
                    new BlockPos(Integer.parseInt(fields[5]),
                            Integer.parseInt(fields[6]), Integer.parseInt(fields[7])));
        }
    }
}
