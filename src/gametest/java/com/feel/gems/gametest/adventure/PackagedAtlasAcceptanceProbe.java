package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.AdventureItems;
import com.feel.gems.adventure.LandmarkKey;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

/** Disposable packaged-server Atlas item-action probe; never included in a release jar. */
public final class PackagedAtlasAcceptanceProbe implements ModInitializer {
    private static final int MAX_RESPONSE_TICKS = 1_300;
    private static final Target[] TARGETS = {
            new Target("fam_forum", "Fam's Forum", 4, false),
            new Target("ali_splitstone_cloister", "Ali's Splitstone Cloister", 10, false),
            new Target("lastlight_ash_annex", "Ash Annex", 15, true)
    };

    private record Target(String id, String label, int extraPage, boolean travelHint) { }
    private record Message(String text, boolean overlay, long nanos) { }

    private CapturePlayer player;
    private Target active;
    private int next;
    private int actionTick;
    private int nextActionTick = 100;
    private long actionNanos;
    private long tickBegan;
    // START_SERVER_TICK through this END_SERVER_TICK listener excludes work after
    // the listener and cannot be reported as whole-tick MSPT.
    private long maxTickEventSpanNanos;
    private int eventSpansOver250Ms;
    private int eventSpansOver1000Ms;
    private boolean finished;
    private boolean columnChecked;

    @Override
    public void onInitialize() {
        if (!Boolean.getBoolean("gems.packagedAtlasAcceptance")) return;
        ServerTickEvents.START_SERVER_TICK.register(server -> tickBegan = System.nanoTime());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (finished) return;
            try {
                boolean alreadySearching = active != null;
                tick(server);
                if (alreadySearching || active != null || next > 0) {
                    long duration = System.nanoTime() - tickBegan;
                    maxTickEventSpanNanos = Math.max(maxTickEventSpanNanos, duration);
                    if (duration >= 250_000_000L) eventSpansOver250Ms++;
                    if (duration >= 1_000_000_000L) eventSpansOver1000Ms++;
                }
            } catch (Exception failure) {
                finished = true;
                System.out.println("PACKAGED_ATLAS_FAIL reason=" + failure.getMessage());
            }
        });
    }

    private void tick(MinecraftServer server) {
        if (!columnChecked && server.getTickCount() >= 60) {
            assertAliColumnEquivalence(server.overworld());
            columnChecked = true;
            return;
        }
        if (player == null) {
            if (server.getTickCount() < 80) return;
            player = connect(server, server.overworld());
            System.out.println("PACKAGED_ATLAS_PLAYER dimension=" + player.level().dimension().identifier()
                    + " origin=" + player.blockPosition());
        }
        if (active == null) {
            if (server.getTickCount() < nextActionTick) return;
            if (next == TARGETS.length) {
                finished = true;
                System.out.println("PACKAGED_ATLAS_PASS targets=" + next + " maxTickEventSpanMs="
                        + millis(maxTickEventSpanNanos) + " eventSpansOver250Ms="
                        + eventSpansOver250Ms + " eventSpansOver1000Ms="
                        + eventSpansOver1000Ms);
                return;
            }
            start(server, TARGETS[next]);
            return;
        }
        require(server.getTickCount() - actionTick <= MAX_RESPONSE_TICKS,
                active.id() + " response timed out; messages=" + player.messages);
        Message reply = player.messages.stream()
                .filter(message -> active.travelHint() ? message.overlay() : !message.overlay())
                .filter(message -> active.travelHint()
                        ? message.text().startsWith("Use a Nether portal")
                        : !message.text().startsWith("Looking for "))
                .findFirst().orElse(null);
        if (reply == null) return;
        if (active.travelHint()) {
            require(reply.text().contains(active.label()) && reply.text().contains("Nether"),
                    active.id() + " returned the wrong travel instruction: " + reply.text());
        } else {
            require(reply.text().startsWith(active.label() + " lies ")
                            && reply.text().contains(" blocks away."),
                    active.id() + " did not return a natural target bearing: " + reply.text());
        }
        System.out.println("PACKAGED_ATLAS_RESULT target=" + active.id() + " latencyMs="
                + millis(reply.nanos() - actionNanos) + " ticks="
                + (server.getTickCount() - actionTick) + " maxTickEventSpanMs="
                + millis(maxTickEventSpanNanos)
                + " reply=" + reply.text());
        next++;
        active = null;
        nextActionTick = server.getTickCount() + 105;
    }

    /** Compare Ali's optimized noise-column height with the vanilla height API. */
    private static void assertAliColumnEquivalence(ServerLevel level) {
        String seed = System.getProperty("gems.aliColumnSeed", "");
        String[] site = System.getProperty("gems.aliColumnSite", "").split(",", -1);
        require(!seed.isBlank() && site.length == 2,
                "Ali column seed and saved site must be supplied by the packaged gate");
        int siteX = Integer.parseInt(site[0]);
        int siteZ = Integer.parseInt(site[1]);
        var source = level.getChunkSource();
        var generator = source.getGenerator();
        require(generator.getClass() == NoiseBasedChunkGenerator.class,
                "Ali column check did not exercise the optimized generator");
        int checked = 0, wet = 0;
        for (int[] center : new int[][]{{siteX, siteZ}, {0, 0}}) {
            int radius = center[0] == 0 && center[1] == 0 ? 24 : 48;
            for (int dx = -radius; dx <= radius; dx += 12) {
                for (int dz = -radius; dz <= radius; dz += 12) {
                    int x = center[0] + dx, z = center[1] + dz;
                    boolean loaded = source.hasChunk(x >> 4, z >> 4);
                    NoiseColumn column = generator.getBaseColumn(x, z, level,
                            source.randomState());
                    int feet = level.getMinY();
                    for (int y = level.getMaxY() - 1; y >= level.getMinY(); y--) {
                        if (Heightmap.Types.WORLD_SURFACE_WG.isOpaque()
                                .test(column.getBlock(y))) {
                            feet = y + 1;
                            break;
                        }
                    }
                    int height = generator.getBaseHeight(x, z,
                            Heightmap.Types.WORLD_SURFACE_WG, level,
                            source.randomState());
                    require(feet == height, "Ali noise-column height mismatch "
                            + seed + " @ " + x + "," + z + ": " + feet + " vs " + height);
                    if (feet > level.getMinY()
                            && !column.getBlock(feet - 1).getFluidState().isEmpty()) wet++;
                    require(source.hasChunk(x >> 4, z >> 4) == loaded,
                            "Ali noise survey loaded " + seed + " @ " + x + "," + z);
                    checked++;
                }
            }
        }
        require(checked == 106, "Ali column sample grid changed");
        System.out.println("ALI_COLUMN_EQUIVALENCE seed=" + seed + " columns=" + checked
                + " wet=" + wet + " site=" + siteX + "," + siteZ);
    }

    private void start(MinecraftServer server, Target target) {
        require(server.getPlayerList().getPlayer(player.getUUID()) == player,
                "capture player disconnected");
        ItemStack atlas = new ItemStack(AdventureItems.RUMOR_ATLAS);
        CustomData.update(DataComponents.CUSTOM_DATA, atlas,
                data -> data.putInt("rumor_atlas_target",
                        LandmarkKey.values().length + target.extraPage()));
        player.setItemInHand(InteractionHand.MAIN_HAND, atlas);
        player.messages.clear();
        active = target;
        actionTick = server.getTickCount();
        actionNanos = System.nanoTime();
        require(AdventureItems.RUMOR_ATLAS.use(player.level(), player, InteractionHand.MAIN_HAND)
                        == InteractionResult.SUCCESS,
                target.id() + " item use failed");
        if (!target.travelHint()) {
            require(player.messages.stream().anyMatch(message -> message.overlay()
                            && message.text().equals("Looking for " + target.label() + "...")),
                    target.id() + " did not show the lookup prompt: " + player.messages);
        }
        System.out.println("PACKAGED_ATLAS_USE target=" + target.id() + " tick=" + actionTick
                + " origin=" + player.blockPosition());
    }

    private static CapturePlayer connect(MinecraftServer server, ServerLevel level) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(
                UUID.fromString("b4585906-51c5-4eab-8c68-99f413e7f592"), "AtlasProbe"), false);
        CapturePlayer connected = new CapturePlayer(server, level, cookie);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, connected, cookie);
        return connected;
    }

    private static double millis(long nanos) {
        return Math.round(nanos / 100_000.0) / 10.0;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class CapturePlayer extends ServerPlayer {
        private final List<Message> messages = new ArrayList<>();

        private CapturePlayer(MinecraftServer server, ServerLevel level, CommonListenerCookie cookie) {
            super(server, level, cookie.gameProfile(), cookie.clientInformation());
        }

        @Override
        public void sendSystemMessage(Component message, boolean overlay) {
            if (messages != null) messages.add(new Message(message.getString(), overlay, System.nanoTime()));
            super.sendSystemMessage(message, overlay);
        }
    }
}
