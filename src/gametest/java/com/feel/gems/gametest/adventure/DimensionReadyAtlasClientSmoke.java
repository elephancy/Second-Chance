package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.AdventureItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sends one real item-use packet and checks the nonprovisional saved arch bearing. */
public final class DimensionReadyAtlasClientSmoke implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("gems-dimension-ready-atlas-client");
    private boolean enabled;
    private boolean connecting;
    private boolean prompted;
    private boolean used;
    private boolean complete;
    private int ticks;
    private int titleTicks;
    private int promptTick;
    private int responseTick;

    @Override
    public void onInitializeClient() {
        enabled = Boolean.getBoolean("gems.dimensionReadyAtlas.client");
        if (!enabled) return;
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> receive(message));
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, type, time) ->
                receive(message));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void receive(Component message) {
        if (!enabled) return;
        String value = message.getString();
        if (value.startsWith("DIMREADY|FAILED|")) fail(value);
        if (value.equals("DIMREADY|COMPLETE")) {
            complete = true;
            LOG.info("DIMREADY_CLIENT_COMPLETE realItemPacket=true readyBearing=true");
            return;
        }
        if (value.equals("DIMREADY|USE_ATLAS")) {
            prompted = true;
            promptTick = ticks;
            return;
        }
        if (!used || responseTick != 0 || !value.contains("Ember Bridge")
                || value.startsWith("Looking for Ember Bridge")) return;
        if (!value.startsWith("Ember Bridge lies ")
                || !value.contains("about 1200 blocks away")) {
            fail("Atlas returned a provisional or wrong Ember bearing: " + value);
            return;
        }
        responseTick = ticks;
        LOG.info("DIMREADY_ATLAS_READY_PASS text={} packet=true", value);
        Minecraft.getInstance().getConnection().sendCommand("dimready ack");
    }

    private void tick(Minecraft client) {
        if (!enabled) return;
        ticks++;
        if (complete) {
            if (ticks % 40 == 0) client.stop();
            return;
        }
        if (!connecting && client.gui.overlay() == null
                && client.gui.screen() instanceof TitleScreen) titleTicks++;
        else titleTicks = 0;
        if (!connecting && client.gui.overlay() == null
                && (titleTicks >= 40 || ticks >= 200)) {
            connecting = true;
            String address = "127.0.0.1:"
                    + Integer.getInteger("gems.dimensionReadyAtlas.port", 25589);
            LOG.info("DIMREADY_CLIENT_CONNECT address={}", address);
            ConnectScreen.startConnecting(client.gui.screen(), client,
                    ServerAddress.parseString(address),
                    new ServerData("Dimension ready Atlas QA", address, ServerData.Type.OTHER),
                    false, null);
        }
        if (ticks > 20 * 210) fail("Client/Atlas timeout");
        if (!prompted || used || ticks - promptTick < 25) return;
        if (client.player == null || client.level == null || client.gameMode == null
                || client.getConnection() == null || client.gui.screen() != null) return;
        if (client.getSingleplayerServer() != null)
            fail("Integrated server would invalidate connected Atlas proof");
        if (!client.player.getMainHandItem().is(AdventureItems.RUMOR_ATLAS))
            fail("Server Atlas item/page has not reached client");
        var result = client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
        used = true;
        LOG.info("DIMREADY_CLIENT_USE packet=true result={} feet={}",
                result, client.player.blockPosition());
    }

    private static void fail(String reason) {
        LOG.error("DIMREADY_CLIENT_FAILED reason={}", reason);
        throw new IllegalStateException("Dimension ready Atlas client: " + reason);
    }
}
