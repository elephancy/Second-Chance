package com.feel.gems.gametest.adventure;

import com.feel.gems.casino.CasinoViewPayload;
import com.feel.gems.client.casino.CasinoScreen;
import com.feel.gems.friend.FriendEntity;
import java.lang.reflect.Field;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Real sign and GUI packets from each of two disposable dedicated clients. */
public final class CasinoNetworkClientSmoke implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("casino-network-client");
    private static final BlockPos SIGN = new BlockPos(20, 150, 23);
    private static final Pattern CLUE = Pattern.compile("CASINO_CLUE_([AB])_(Ruby|Jade|Azure)");
    private static final int FIRST_JOIN_NOTICE_TICKS = 20 * 20;
    private static final int SCREEN_RENDER_TICKS = 4;
    private String role;
    private String phase;
    private boolean connecting;
    private boolean attached;
    private boolean done;
    private boolean sentClue;
    private boolean guessed;
    private boolean hostVisibleLogged;
    private boolean sizeApplied;
    private boolean waitCaptured;
    private boolean privateCaptured;
    private String remoteSignal;
    private int ticks;
    private int titleTicks;
    private int activeTicks;
    private int stage;
    private int stageTick;
    private int doneTick;
    private int worldReadyTicks;
    private int previewSeenTick = -1;
    private int waitSeenTick = -1;
    private int playSeenTick = -1;
    private int resultSeenTick = -1;
    private int pendingSeenTick = -1;
    private int screenshots;

    @Override
    public void onInitializeClient() {
        role = System.getProperty("gems.casinoNetwork.client", "");
        if (!role.equals("A") && !role.equals("B")) return;
        phase = System.getProperty("gems.casinoNetwork.phase", "initial");
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> clue(message));
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, type, time) -> clue(message));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void clue(Component message) {
        Matcher match = CLUE.matcher(message.getString());
        if (!match.find() || match.group(1).equals(role)) return;
        remoteSignal = match.group(2);
        LOG.info("CASINO_NETWORK_PARTNER_CLUE role={} clue={} receivedOverChat=true",
                role, remoteSignal);
    }

    private void tick(Minecraft client) {
        ticks++;
        if (done) {
            if (client.getConnection() == null || ticks - doneTick > 20 * 30) {
                LOG.info("CASINO_NETWORK_CLIENT_COMPLETE role={} phase={} screenshots={}",
                        role, phase, screenshots);
                client.stop();
            }
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
                        + Integer.getInteger("gems.casinoNetwork.port", 25587);
                ConnectScreen.startConnecting(client.gui.screen(), client,
                        ServerAddress.parseString(address),
                        new ServerData("Casino network QA", address, ServerData.Type.OTHER),
                        false, null);
                LOG.info("CASINO_NETWORK_CONNECT role={} phase={} address={}", role, phase, address);
            }
            if (client.player == null || client.level == null || client.gameMode == null
                    || client.getConnection() == null
                    || client.level.getBlockEntity(SIGN) == null) {
                if (ticks > 20 * 150) fail("connection or saved sign timeout");
                return;
            }
            if (client.getSingleplayerServer() != null) fail("integrated server instead of dedicated");
            if (!sizeApplied) {
                int width = role.equals("A") ? 854 : 1280;
                int height = role.equals("A") ? 480 : 720;
                client.getWindow().setWindowed(width, height);
                client.options.guiScale().set(2);
                client.resizeGui();
                sizeApplied = true;
                LOG.info("CASINO_NETWORK_LEDGER_SIZE role={} requested={}x{} guiScale=2",
                        role, width, height);
            }
            // This disposable test client has no tutorial history. Keep its onboarding
            // prompt from covering the connected table capture for the whole run.
            client.getTutorial().setStep(TutorialSteps.NONE);
            attached = true;
            activeTicks = 0;
            LOG.info("CASINO_NETWORK_ATTACHED role={} phase={} player={} integrated=false",
                    role, phase, client.player.getName().getString());
        }
        activeTicks++;
        if (activeTicks > 20 * 150) fail("Signal Table action timeout stage=" + stage);
        switch (stage) {
            case 0 -> {
                if (client.gui.screen() != null || client.gui.overlay() != null) {
                    worldReadyTicks = 0;
                    return;
                }
                if (phase.equals("resume") && !hostVisible(client)) return;
                if (phase.equals("resume") && !hostVisibleLogged) {
                    hostVisibleLogged = true;
                    LOG.info("CASINO_NETWORK_CLIENT_SAVED_HOST role={} ragab=visible", role);
                }
                worldReadyTicks++;
                if (worldReadyTicks == 15) client.player.setXRot(18.0F);
                if (worldReadyTicks == FIRST_JOIN_NOTICE_TICKS) screenshot(client, "sign-before-use");
                if (worldReadyTicks < FIRST_JOIN_NOTICE_TICKS + SCREEN_RENDER_TICKS) return;
                useSign(client);
                stage(1);
            }
            case 1 -> {
                CasinoViewPayload view = view(client);
                if (view == null || !view.phase().equals("PREVIEW")) return;
                if (previewSeenTick < 0) previewSeenTick = activeTicks;
                if (activeTicks - previewSeenTick < SCREEN_RENDER_TICKS) return;
                if (!view.gameId().equals("coop") || view.stake() != 2
                        || view.emeralds() != (phase.equals("resume") ? 65 : 64))
                    fail("wrong saved sign preview or wallet: " + view);
                assertLedgerState(client, view, "PREVIEW", phase.equals("resume") ? 65 : 64, false);
                screenshot(client, phase.equals("resume") ? "reopen-preview" : "compact-wide-preview");
                if (phase.equals("resume")) {
                    done = true;
                    doneTick = ticks;
                    LOG.info("CASINO_NETWORK_REOPEN_PASS role={} wallet=65 stake=2 signPacket=true",
                            role);
                    return;
                }
                press(client, view, "commit");
                stage(2);
            }
            case 2 -> {
                CasinoViewPayload view = view(client);
                if (view == null) return;
                if (view.phase().equals("COOP_WAIT")) {
                    if (view.emeralds() != 64) fail("waiting seat took an emerald stake");
                    if (waitSeenTick < 0) waitSeenTick = activeTicks;
                    if (!waitCaptured && activeTicks - waitSeenTick >= SCREEN_RENDER_TICKS) {
                        assertLedgerState(client, view, "COOP_WAIT", 64, false);
                        screenshot(client, "seat-wait-no-stake");
                        waitCaptured = true;
                    }
                    return;
                }
                if (!view.phase().equals("COOP_PLAY")) return;
                if (view.emeralds() != 62) fail("active first round did not debit exactly two");
                if (playSeenTick < 0) playSeenTick = activeTicks;
                if (!sentClue) {
                    assertLedgerState(client, view, "COOP_PLAY", 62, true);
                    String own = ownSignal(view);
                    client.getConnection().sendChat("CASINO_CLUE_" + role + "_" + own);
                    sentClue = true;
                    LOG.info("CASINO_NETWORK_PRIVATE_VIEW role={} signal={} onlyOwnSignal=true wallet=62",
                            role, own);
                }
                if (activeTicks - playSeenTick < SCREEN_RENDER_TICKS) return;
                if (!privateCaptured) {
                    screenshot(client, "private-signal");
                    privateCaptured = true;
                }
                if (remoteSignal != null && !guessed) {
                    press(client, view, "guess_" + remoteSignal.toLowerCase(java.util.Locale.ROOT));
                    guessed = true;
                    stage(3);
                }
            }
            case 3 -> {
                CasinoViewPayload view = view(client);
                if (view == null || !view.phase().equals("RESULT")) return;
                if (!view.message().startsWith("Both guesses were right.") || view.emeralds() != 65)
                    fail("paid duo result or payout wrong: " + view.message()
                            + "/" + view.emeralds());
                if (resultSeenTick < 0) resultSeenTick = activeTicks;
                if (activeTicks - resultSeenTick < SCREEN_RENDER_TICKS) return;
                screenshot(client, "paid-result");
                LOG.info("CASINO_NETWORK_ROUND_WIN role={} wallet=65 result={} automatedButtons=true",
                        role, view.message());
                stage(4);
            }
            case 4 -> {
                if (activeTicks - stageTick < 60) return;
                client.gui.setScreen(null);
                useSign(client);
                stage(5);
            }
            case 5 -> {
                CasinoViewPayload view = view(client);
                if (view == null || !view.phase().equals("PREVIEW")) return;
                if (!view.gameId().equals("coop") || view.emeralds() != 65)
                    fail("second sign open lost paid round balance");
                press(client, view, "commit");
                stage(6);
            }
            case 6 -> {
                CasinoViewPayload view = view(client);
                if (view == null || !view.phase().equals("COOP_PLAY")) return;
                if (view.emeralds() != 63) fail("second round took wrong stake");
                if (pendingSeenTick < 0) pendingSeenTick = activeTicks;
                if (activeTicks - pendingSeenTick < SCREEN_RENDER_TICKS) return;
                assertLedgerState(client, view, "COOP_PLAY", 63, true);
                screenshot(client, "pending-save");
                done = true;
                doneTick = ticks;
                LOG.info("CASINO_NETWORK_PENDING_RESTART role={} wallet=63 round=active stake=2",
                        role);
            }
            default -> fail("invalid stage " + stage);
        }
    }

    private void stage(int next) {
        stage = next;
        stageTick = activeTicks;
        previewSeenTick = -1;
    }

    private void useSign(Minecraft client) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(SIGN), Direction.UP, SIGN, false);
        InteractionResult result = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
        if (result == InteractionResult.PASS) fail("client sign use was not consumed");
        LOG.info("CASINO_NETWORK_SIGN_USE role={} phase={} result={}", role, phase, result);
    }

    private static boolean hostVisible(Minecraft client) {
        return !client.level.getEntitiesOfClass(FriendEntity.class,
                new AABB(new BlockPos(15, 150, 24)).inflate(5),
                friend -> friend.isAlive() && "ragab".equals(friend.friendId())).isEmpty();
    }

    private static String ownSignal(CasinoViewPayload view) {
        var own = view.board().stream().filter(line -> line.startsWith("Your color: ")).toList();
        if (own.size() != 1) fail("private signal absent or duplicated");
        for (String line : view.board()) {
            if (line.startsWith("Partner signal:")) fail("partner signal leaked into private view");
        }
        String signal = own.getFirst().substring("Your color: ".length());
        if (!signal.equals("Ruby") && !signal.equals("Jade") && !signal.equals("Azure"))
            fail("unexpected private signal " + signal);
        return signal;
    }

    /** Assert a connected server payload and its actionable screen before visual capture. */
    private void assertLedgerState(Minecraft client, CasinoViewPayload view,
                                   String expectedPhase, int wallet, boolean held) {
        if (!expectedPhase.equals(view.phase()) || !"coop".equals(view.gameId())
                || view.stake() != 2 || view.emeralds() != wallet
                || view.title().startsWith("Free:")) {
            fail("connected ledger payload mismatch phase=" + view.phase()
                    + " stake=" + view.stake() + " wallet=" + view.emeralds());
        }
        if (!(client.gui.screen() instanceof CasinoScreen screen)) {
            fail("connected casino screen missing");
            return;
        }
        String exitLabel = held ? "Close view" : "Leave table";
        boolean exitVisible = screen.children().stream().filter(Button.class::isInstance)
                .map(Button.class::cast).anyMatch(button -> button.visible
                        && exitLabel.equals(button.getMessage().getString()));
        if (!exitVisible) fail("connected casino screen missing " + exitLabel);
        LOG.info("CASINO_NETWORK_LEDGER_STATE role={} phase={} state={} wallet={} stake=2 revision={} source=server",
                role, phase, expectedPhase, wallet, view.revision());
    }

    private static void press(Minecraft client, CasinoViewPayload view, String id) {
        String label = view.choices().stream().filter(choice -> choice.id().equals(id))
                .map(CasinoViewPayload.Choice::label).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing casino action " + id));
        if (!(client.gui.screen() instanceof CasinoScreen screen))
            throw new IllegalStateException("casino GUI missing");
        Button button = screen.children().stream().filter(child -> child instanceof Button)
                .map(child -> (Button) child)
                .filter(candidate -> candidate.getMessage().getString().equals(label))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing GUI button " + label));
        if (!button.active || !button.visible) fail("casino button unavailable " + label);
        button.onPress(null);
    }

    private static CasinoViewPayload view(Minecraft client) {
        if (!(client.gui.screen() instanceof CasinoScreen screen)) return null;
        try {
            Field field = CasinoScreen.class.getDeclaredField("view");
            field.setAccessible(true);
            return (CasinoViewPayload) field.get(screen);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot inspect connected casino view", error);
        }
    }

    private void screenshot(Minecraft client, String name) {
        if (Boolean.getBoolean("gems.casinoNoCapture")) return;
        String target = "casino-network-" + phase + "-" + role + "-" + name + ".png";
        Screenshot.grab(client.gameDirectory, target,
                client.gameRenderer.mainRenderTarget(), 1,
                message -> LOG.info("CASINO_NETWORK_SCREENSHOT_RESULT role={} name={} result={}",
                        role, name, message.getString()));
        screenshots++;
        LOG.info("CASINO_NETWORK_SCREENSHOT role={} name={} runDir={}",
                role, name, client.gameDirectory);
    }

    private static void fail(String reason) {
        LOG.error("CASINO_NETWORK_FAILED reason={}", reason);
        throw new IllegalStateException("Casino client fixture: " + reason);
    }
}
