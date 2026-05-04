package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.AdventureItems;
import java.util.HashSet;
import java.util.Set;
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

/** Sends real loopback play packets and captures actual rendered frames from a copied save. */
public final class DimensionChapterLiveClientSmoke implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("gems-dimension-live-client");
    private boolean enabled;
    private boolean connecting;
    private boolean attached;
    private boolean complete;
    private int ticks;
    private int titleTicks;
    private int attachedTick;
    private Instruction pending;
    private ControlAttempt waitingControl;
    private int pendingTick;
    private int shots;
    private int uses;
    private int atlasAckTick;
    private int cleanShotTick;
    private boolean restoreHud;
    private BlockPos walkGoal;
    private BlockPos walkStep;
    private int walkStartTick;
    private String lastUseStage = "none";
    private final Set<String> seenInstructions = new HashSet<>();

    @Override
    public void onInitializeClient() {
        enabled = Boolean.getBoolean("gems.dimensionLive.client");
        if (!enabled) return;
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> receive(message));
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, type, time) ->
                receive(message));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void receive(Component message) {
        String value = message.getString();
        if (value.startsWith("DIMLIVE|FAILED|")) fail("Server failed: " + value);
        if (value.equals("DIMLIVE|COMPLETE")) {
            complete = true;
            LOG.info("DIMLIVE_CLIENT_COMPLETE uses={} screenshots={}", uses, shots);
            return;
        }
        if (value.equals("DIMLIVE|WALK_DONE")) {
            Minecraft client = Minecraft.getInstance();
            client.options.keyUp.setDown(false);
            client.options.keyJump.setDown(false);
            walkGoal = null;
            walkStep = null;
            LOG.info("DIMLIVE_CLIENT_WALK_DONE movement=normal-input");
            return;
        }
        if (value.startsWith("DIMLIVE|")) {
            Instruction instruction = Instruction.parse(value);
            if (instruction != null && seenInstructions.add(value)) {
                pending = instruction;
                pendingTick = ticks;
                LOG.info("DIMLIVE_CLIENT_INSTRUCTION {}", value);
            }
        } else if (attached && !value.isBlank()) {
            LOG.info("DIMLIVE_CLIENT_GUIDANCE stage={} message={}", lastUseStage, value);
            ControlAttempt control = waitingControl;
            if (control != null && control.guidance(value)) {
                if (value.contains("sealed") || value.contains("incomplete"))
                    fail("Natural chapter control rejected its saved site: " + value);
                LOG.info("DIMLIVE_CLIENT_CONTROL_RESPONSE stage={} attempts={} text={}",
                        control.stage, control.attempts, value);
                Minecraft.getInstance().getConnection().sendCommand(
                        "dimlive ack " + control.stage);
                waitingControl = null;
            }
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
            if (ticks % 200 == 0)
                LOG.info("DIMLIVE_CLIENT_WAIT ticks={} screen={} overlay={} titleTicks={} connecting={}",
                        ticks, client.gui.screen() == null ? "null"
                                : client.gui.screen().getClass().getSimpleName(),
                        client.gui.overlay() == null ? "null"
                                : client.gui.overlay().getClass().getSimpleName(),
                        titleTicks, connecting);
            // The test client may present a first-run screen instead of the
            // title. Resource reload must be finished before this fallback.
            if (!connecting && client.gui.overlay() == null
                    && (titleTicks >= 40 || ticks >= 200)) {
                connecting = true;
                String address = "127.0.0.1:"
                        + Integer.getInteger("gems.dimensionLive.port", 25586);
                LOG.info("DIMLIVE_CLIENT_CONNECT address={}", address);
                ConnectScreen.startConnecting(client.gui.screen(), client,
                        ServerAddress.parseString(address),
                        new ServerData("Dimension chapter live QA", address, ServerData.Type.OTHER),
                        false, null);
            }
            if (client.player == null || client.level == null || client.gameMode == null
                    || client.getConnection() == null) {
                if (ticks > 20 * 150) fail("Loopback connection timeout");
                return;
            }
            if (client.getSingleplayerServer() != null)
                fail("Integrated server would invalidate the dedicated-server evidence");
            attached = true;
            attachedTick = ticks;
            LOG.info("DIMLIVE_CLIENT_ATTACHED player={} integrated=false",
                    client.player.getName().getString());
        }
        if (ticks > 20 * 550) fail("Nine-minute live fixture timeout");
        driveWalk(client);
        if (waitingControl != null && ticks >= waitingControl.retryAt) {
            if (waitingControl.attempts >= 3)
                fail("No chapter guidance after three real packets to " + waitingControl.stage);
            waitingControl.attempts++;
            waitingControl.retryAt = ticks + 40;
            BlockPos pos = waitingControl.pos;
            if (client.level.getBlockState(pos).isAir()
                    || client.player.distanceToSqr(Vec3.atCenterOf(pos)) > 25.0)
                fail("Chapter control no longer loaded or reachable: " + waitingControl.stage);
            BlockHitResult retry = new BlockHitResult(Vec3.atCenterOf(pos),
                    Direction.UP, pos, false);
            var retryResult = client.gameMode.useItemOn(client.player,
                    InteractionHand.MAIN_HAND, retry);
            LOG.info("DIMLIVE_CLIENT_CONTROL_RETRY stage={} attempt={} result={}",
                    waitingControl.stage, waitingControl.attempts, retryResult);
        }
        if (atlasAckTick > 0 && ticks >= atlasAckTick) {
            client.getConnection().sendCommand("dimlive ack atlas");
            LOG.info("DIMLIVE_CLIENT_ATLAS_ACK_SENT delayTicks=12 feet={}",
                    client.player.blockPosition());
            atlasAckTick = 0;
        }
        Instruction instruction = pending;
        if (instruction == null || ticks - pendingTick < 24) return;
        if (client.gui.screen() != null || ticks - attachedTick < 40) {
            if (ticks % 40 == 0)
                LOG.info("DIMLIVE_CLIENT_ACTION_WAIT stage={} screen={} attachedTicks={}",
                        instruction.stage,
                        client.gui.screen() == null ? "null"
                                : client.gui.screen().getClass().getSimpleName(),
                        ticks - attachedTick);
            if (ticks - pendingTick > 20 * 30)
                fail("World screen/input not ready for " + instruction.stage);
            return;
        }
        switch (instruction.kind) {
            case "WALK" -> {
                walkGoal = instruction.pos;
                walkStep = instruction.step;
                walkStartTick = ticks;
                LOG.info("DIMLIVE_CLIENT_WALK_START stage={} target={} step={} input=key-up-and-jump",
                        instruction.stage, walkGoal, walkStep);
                pending = null;
            }
            case "ATLAS" -> {
                int atlasSlot = -1;
                for (int slot = 0; slot < 9; slot++)
                    if (client.player.getInventory().getItem(slot).is(AdventureItems.RUMOR_ATLAS)) {
                        atlasSlot = slot;
                        break;
                    }
                if (atlasSlot < 0) fail("Atlas missing from hotbar");
                client.player.getInventory().setSelectedSlot(atlasSlot);
                var result = client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
                lastUseStage = "atlas";
                LOG.info("DIMLIVE_CLIENT_USE stage=atlas packet=true result={}", result);
                uses++;
                atlasAckTick = ticks + 12;
                pending = null;
            }
            case "USE" -> {
                if (client.level.getBlockState(instruction.pos).isAir()
                        || client.player.distanceToSqr(Vec3.atCenterOf(instruction.pos)) > 25.0) {
                    if (ticks - pendingTick > 20 * 30)
                        fail("Control did not become reachable: " + instruction.stage);
                    return;
                }
                if (instruction.sneak && ticks - pendingTick < 32) {
                    client.options.keyShift.setDown(true);
                    return;
                }
                screenshot(client, instruction.stage);
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(instruction.pos),
                        Direction.UP, instruction.pos, false);
                var result = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
                client.options.keyShift.setDown(false);
                lastUseStage = instruction.stage;
                LOG.info("DIMLIVE_CLIENT_USE stage={} packet=true sneak={} result={} target={}",
                        instruction.stage, instruction.sneak, result, instruction.pos);
                uses++;
                if (!instruction.sneak)
                    waitingControl = new ControlAttempt(instruction.stage,
                            instruction.pos, ticks + 40);
                pending = null;
            }
            case "SCREENSHOT" -> {
                if (instruction.stage.endsWith("-approach-clean")) {
                    // This is a separate visual receipt. Gameplay packets and
                    // their response gate run after the HUD is restored.
                    if (ticks - pendingTick < 160) return;
                    if (cleanShotTick == 0) {
                        client.gui.toastManager().clear();
                        restoreHud = !client.gui.hud.isHidden();
                        if (restoreHud) client.gui.hud.toggle();
                        cleanShotTick = ticks;
                        return;
                    }
                    if (ticks - cleanShotTick < 10) return;
                }
                screenshot(client, instruction.stage);
                if (restoreHud) client.gui.hud.toggle();
                restoreHud = false;
                cleanShotTick = 0;
                client.getConnection().sendCommand("dimlive ack " + instruction.stage);
                pending = null;
            }
            default -> fail("Unknown instruction: " + instruction.kind);
        }
    }

    private void driveWalk(Minecraft client) {
        if (walkGoal == null) return;
        if (ticks - walkStartTick > 20 * 50)
            fail("Normal-input Ember walk did not reach its saved goal");
        BlockPos feet = client.player.blockPosition();
        if (feet.getX() == walkGoal.getX() && feet.getZ() == walkGoal.getZ()
                && feet.getY() >= walkGoal.getY()) {
            client.options.keyUp.setDown(false);
            client.options.keyJump.setDown(false);
            LOG.info("DIMLIVE_CLIENT_WALK_GOAL feet={} target={} input=key-up-and-jump",
                    feet, walkGoal);
            walkGoal = null;
            return;
        }
        Vec3 target = Vec3.atCenterOf(walkGoal);
        double dx = target.x - client.player.getX();
        double dz = target.z - client.player.getZ();
        client.player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        client.options.keyUp.setDown(true);
        double sx = walkStep.getX() + .5 - client.player.getX();
        double sz = walkStep.getZ() + .5 - client.player.getZ();
        client.options.keyJump.setDown(feet.getY() < walkStep.getY()
                && sx * sx + sz * sz < 2.8 * 2.8);
    }

    private void screenshot(Minecraft client, String stage) {
        // 26.3 exposes the current render target through GameRenderer.
        // Distinct names make each actual in-game frame auditable.
        Screenshot.grab(client.gameDirectory, "dimlive-" + stage + ".png",
                client.gameRenderer.mainRenderTarget(), 1,
                message -> LOG.info("DIMLIVE_SCREENSHOT_RESULT stage={} message={}",
                        stage, message.getString()));
        shots++;
        LOG.info("DIMLIVE_CLIENT_SCREENSHOT stage={} ordinal={} runDir={}",
                stage, shots, client.gameDirectory);
    }

    private static void fail(String reason) {
        LOG.error("DIMLIVE_CLIENT_FAILED reason={}", reason);
        throw new IllegalStateException("Dimension live client: " + reason);
    }

    private record Instruction(String kind, String stage, BlockPos pos,
                               BlockPos step, boolean sneak) {
        static Instruction parse(String value) {
            String[] fields = value.split("\\|");
            if (fields.length == 2 && fields[1].equals("ATLAS"))
                return new Instruction("ATLAS", "atlas", BlockPos.ZERO, BlockPos.ZERO, false);
            if (fields.length == 3 && fields[1].equals("SCREENSHOT"))
                return new Instruction("SCREENSHOT", fields[2], BlockPos.ZERO, BlockPos.ZERO,
                        false);
            if (fields.length == 9 && fields[1].equals("WALK"))
                return new Instruction("WALK", fields[2], new BlockPos(
                        Integer.parseInt(fields[3]), Integer.parseInt(fields[4]),
                        Integer.parseInt(fields[5])), new BlockPos(
                        Integer.parseInt(fields[6]), Integer.parseInt(fields[7]),
                        Integer.parseInt(fields[8])), false);
            if (fields.length == 7 && fields[1].equals("USE"))
                return new Instruction("USE", fields[2], new BlockPos(
                        Integer.parseInt(fields[3]), Integer.parseInt(fields[4]),
                        Integer.parseInt(fields[5])), BlockPos.ZERO,
                        Boolean.parseBoolean(fields[6]));
            return null;
        }
    }

    private static final class ControlAttempt {
        private final String stage;
        private final BlockPos pos;
        private int attempts = 1;
        private int retryAt;

        private ControlAttempt(String stage, BlockPos pos, int retryAt) {
            this.stage = stage;
            this.pos = pos;
            this.retryAt = retryAt;
        }

        private boolean guidance(String message) {
            if (stage.equals("ash_annex-control"))
                return message.startsWith("Find the Lastlight Exchange")
                        || message.startsWith("Ash register:")
                        || message.startsWith("The Ash Annex board")
                        || message.startsWith("This site is sealed");
            if (stage.equals("ember_bridge-control"))
                return message.startsWith("Clear Redline House")
                        || message.startsWith("Restore both shelters")
                        || message.startsWith("Evacuation started")
                        || message.startsWith("The Ember Bridge is open")
                        || message.startsWith("This site is sealed");
            if (stage.equals("end_source-control"))
                return message.startsWith("Choose a route at")
                        || message.startsWith("Restore the Ash Annex")
                        || message.startsWith("Recover all seven identities")
                        || message.startsWith("Remember:")
                        || message.startsWith("Repair:")
                        || message.startsWith("Release:")
                        || message.startsWith("This observatory is incomplete");
            return false;
        }
    }
}
