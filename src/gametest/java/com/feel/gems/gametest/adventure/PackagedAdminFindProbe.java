package com.feel.gems.gametest.adventure;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.level.GameType;

/** Disposable packaged-server command probe; excluded from both production jars. */
public final class PackagedAdminFindProbe implements ModInitializer {
    private static final UUID OPERATOR_ID = UUID.fromString(
            "03eb7af5-bada-4ca8-a5bc-43e50723f731");
    private static final String COMMAND = "secondchance admin find four_roads_estate";
    private static final int START_X = 15104;
    private static final int START_Z = -10496;
    private static final long MAX_WAIT_NS = 60_000_000_000L;

    private record Message(String text, long nanos) { }

    private CapturePlayer player;
    private long commandNanos;
    private int commandTick;
    private boolean finished;

    @Override
    public void onInitialize() {
        if (!Boolean.getBoolean("gems.packagedAdminFindAcceptance")) return;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (finished) return;
            try {
                tick(server);
            } catch (Exception failure) {
                finished = true;
                System.out.println("PACKAGED_ADMIN_FIND_FAIL reason=" + failure);
            }
        });
    }

    private void tick(MinecraftServer server) {
        if (server.getTickCount() < 80) return;
        if (player == null) {
            ServerLevel level = server.overworld();
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                    new GameProfile(OPERATOR_ID, "EstateOpProbe"), false);
            player = new CapturePlayer(server, level, cookie);
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.CREATIVE);
            player.teleportTo(level, START_X + 0.5, 90, START_Z + 0.5,
                    Set.of(), 0.0F, 0.0F, true);
            require(player.isCreative(), "connected player is not Creative");
            require(server.getPlayerList().getPlayer(OPERATOR_ID) == player,
                    "operator is not in the live player list");
            require(player.createCommandSourceStack().permissions().hasPermission(
                    new Permission.HasCommandLevel(PermissionLevel.byId(2))),
                    "connected player lacks operator level 2");
            player.messages.clear();
            commandTick = server.getTickCount();
            commandNanos = System.nanoTime();
            server.getCommands().performPrefixedCommand(
                    player.createCommandSourceStack(), COMMAND);
            require(player.messages.stream().anyMatch(message ->
                            message.text().equals("Searching for four roads estate nearby...")),
                    "operator did not receive the search prompt: " + player.messages);
            System.out.println("PACKAGED_ADMIN_FIND_START tick=" + commandTick
                    + " origin=" + player.blockPosition() + " command=" + COMMAND);
            return;
        }
        Message answer = player.messages.stream().filter(message ->
                message.text().startsWith("Nearest four roads estate start: ")
                        || message.text().startsWith("Possible four roads estate start: "))
                .findFirst().orElse(null);
        if (answer != null) {
            String[] coordinates = answer.text().split("start: ", 2)[1].split("[ .]", 4);
            require(coordinates.length >= 3, "malformed finder coordinates: " + answer.text());
            int x = Integer.parseInt(coordinates[0]);
            int z = Integer.parseInt(coordinates[2]);
            require(Math.abs(x - START_X) <= 128 && Math.abs(z - START_Z) <= 128,
                    "finder pointed away from the saved Estate: " + answer.text());
            double elapsedMs = Math.round((answer.nanos() - commandNanos) / 100_000.0) / 10.0;
            require(elapsedMs <= 60_000.0,
                    "operator response exceeded 60 seconds: " + elapsedMs + " ms");
            finished = true;
            System.out.println("PACKAGED_ADMIN_FIND_PASS latencyMs=" + elapsedMs
                    + " ticks=" + (server.getTickCount() - commandTick)
                    + " operatorCreative=true operatorLevel2=true reply=" + answer.text());
            return;
        }
        require(System.nanoTime() - commandNanos <= MAX_WAIT_NS,
                "operator response exceeded 60 seconds; messages=" + player.messages);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class CapturePlayer extends ServerPlayer {
        private final List<Message> messages = new ArrayList<>();

        private CapturePlayer(MinecraftServer server, ServerLevel level,
                              CommonListenerCookie cookie) {
            super(server, level, cookie.gameProfile(), cookie.clientInformation());
        }

        @Override
        public void sendSystemMessage(Component message, boolean overlay) {
            if (messages != null) messages.add(new Message(message.getString(), System.nanoTime()));
            super.sendSystemMessage(message, overlay);
        }
    }
}
