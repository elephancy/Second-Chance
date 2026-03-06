package com.feel.gems.adventure;

import com.feel.gems.adventure.satellite.SatelliteTownhallHints;

import com.feel.gems.sound.ModSounds;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Server-owned harbor call and a bounded public landmark broadcast. */
public final class NourSignalRuntime {
    private static final String DOMAIN = "nour";
    // A miss should allow another search soon without applying the full cooldown.
    private static final int FAILED_LOOKUP_RETRY_TICKS = 20 * 5;
    private static NourSignalPlan plan = new NourSignalPlan();
    private static final Map<UUID, Integer> selected = new HashMap<>();
    private static Ping ping;
    private static long tableReadyTick;
    private static Pending pending;
    private static long nextSearchId;

    private record Ping(BlockPos target, String label, long until) { }
    private record Pending(UUID player, long id, ServerLevel level, String targetId,
                           Consumer<RumorAtlasLocator.Result> callback) {
        boolean accepted() {
            return RumorAtlasLocator.hasAccepted(level, player, targetId, callback);
        }
    }

    private NourSignalRuntime() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            Block block = world.getBlockState(hit.getBlockPos()).getBlock();
            if (block != DomainBlocks.NOUR_BOAT_CLUE
                    && block != DomainBlocks.NOUR_FOG_CLUE
                    && block != DomainBlocks.NOUR_BLUE_LAMP
                    && block != DomainBlocks.NOUR_AMBER_LAMP
                    && block != DomainBlocks.NOUR_WHITE_LAMP
                    && block != DomainBlocks.TOWNHALL_SIGNAL_TABLE) {
                return InteractionResult.PASS;
            }
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            if (block == DomainBlocks.TOWNHALL_SIGNAL_TABLE) {
                return table(level, serverPlayer, hit.getBlockPos());
            }
            if (block == DomainBlocks.NOUR_BOAT_CLUE) {
                plan.readBoat();
                tell(serverPlayer, "Boathouse log: a blue sail left the dock; a white lantern hung at the empty berth when it returned.");
            } else if (block == DomainBlocks.NOUR_FOG_CLUE) {
                plan.readFog();
                tell(serverPlayer, "Foghouse log: the amber horn answered the boat from beyond the fog. Its return came from the harbor.");
            } else {
                tune(level, serverPlayer, color(block));
            }
            return InteractionResult.SUCCESS_SERVER;
        });
        ServerTickEvents.END_SERVER_TICK.register(NourSignalRuntime::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUUID();
            selected.remove(id);
            if (pending != null && pending.player().equals(id)) pending = null;
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            plan = new NourSignalPlan();
            selected.clear();
            ping = null;
            tableReadyTick = 0;
            pending = null;
            nextSearchId = 0;
        });
    }

    private static NourSignalPlan.Color color(Block block) {
        if (block == DomainBlocks.NOUR_BLUE_LAMP) return NourSignalPlan.Color.BLUE;
        if (block == DomainBlocks.NOUR_AMBER_LAMP) return NourSignalPlan.Color.AMBER;
        return NourSignalPlan.Color.WHITE;
    }

    private static void tune(ServerLevel level, ServerPlayer player, NourSignalPlan.Color color) {
        if (FriendDomainUnlockState.get(level.getServer()).cleared(DOMAIN)) {
            tell(player, "The harbor lamps are set. Everyone can use the Harbor Map at the townhall.");
            return;
        }
        NourSignalPlan.Result result = plan.press(color);
        switch (result) {
            case NEXT -> tell(player, "The call carries across the harbor. Another lamp is waiting.");
            case RESET -> tell(player, "The call breaks and the lamps go dark. The whole signal must be sent again.");
            case COMPLETE -> {
                FriendDomainUnlockState.get(level.getServer()).clear(DOMAIN);
                level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                        "Nour's harbor is ready. Everyone can use the Harbor Map at the townhall."), false);
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        ModSounds.ARCANE_CHIME, SoundSource.BLOCKS, 0.65F, 1.25F);
            }
        }
    }

    private static InteractionResult table(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!FriendDomainUnlockState.get(level.getServer()).cleared(DOMAIN)) {
            tell(player, "Nour's harbor call is still unfinished. Explore the harbor.");
            return InteractionResult.SUCCESS_SERVER;
        }
        LandmarkKey[] landmarks = LandmarkKey.values();
        int index = selected.getOrDefault(player.getUUID(), 0);
        if (player.isShiftKeyDown()) {
            index = (index + 1) % landmarks.length;
            selected.put(player.getUUID(), index);
            tell(player, "Selected: " + landmarks[index].label() + ". Use the map to share its direction.");
            return InteractionResult.SUCCESS_SERVER;
        }
        long now = level.getServer().getTickCount();
        if (now < tableReadyTick) {
            tell(player, "Harbor Map ready in " + ((tableReadyTick - now + 19) / 20) + " seconds.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (pending != null && pending.accepted()) {
            tell(player, "The Harbor Map is still looking. Try again in a moment.");
            return InteractionResult.SUCCESS_SERVER;
        }
        pending = null;
        LandmarkKey target = landmarks[index];
        long searchId = ++nextSearchId;
        Consumer<RumorAtlasLocator.Result> callback = result -> {
            if (pending == null || pending.id() != searchId) return;
            pending = null;
            if (level.getServer().getPlayerList().getPlayer(player.getUUID()) != player
                    || player.level() != level) return;
            long finishedAt = level.getServer().getTickCount();
            if (result.error() != null) {
                tell(player, result.error());
                return;
            }
            if (result.found() == null || result.provisional()) {
                tableReadyTick = finishedAt + FAILED_LOOKUP_RETRY_TICKS;
                tell(player, result.provisional()
                        ? "That place is not confirmed yet. Try again in 5 seconds."
                        : "No nearby " + target.label()
                                + " found. Try another place in 5 seconds.");
                return;
            }
            ping = new Ping(result.found(), target.label(), finishedAt + 20 * 30);
            tableReadyTick = finishedAt + 20 * 90;
            level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                    player.getName().getString() + " shared the way to " + target.label()
                            + " for 30 seconds with everyone in the Overworld."), false);
            broadcast(level.getServer());
            String oceanLead = SatelliteTownhallHints.signalTable(level.getServer());
            if (!oceanLead.isEmpty()) tell(player, oceanLead);
            level.playSound(null, pos, ModSounds.ARCANE_CHIME, SoundSource.BLOCKS, 0.6F, 1.15F);
        };
        boolean accepted = RumorAtlasLocator.submit(level, player.getUUID(), pos, target.id(),
                0, 128, false, null, callback);
        if (!accepted) {
            tell(player, "The Harbor Map is busy. Try again shortly.");
            return InteractionResult.SUCCESS_SERVER;
        }
        pending = new Pending(player.getUUID(), searchId, level, target.id(), callback);
        tell(player, "Looking for " + target.label() + "...");
        return InteractionResult.SUCCESS_SERVER;
    }

    private static void tick(MinecraftServer server) {
        if (pending != null && server.getTickCount() % 20 == 0 && !pending.accepted()) pending = null;
        if (ping == null || server.getTickCount() % 40 != 0) return;
        if (server.getTickCount() >= ping.until()) {
            ping = null;
            return;
        }
        broadcast(server);
    }

    private static void broadcast(MinecraftServer server) {
        if (ping == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level().dimension() != Level.OVERWORLD) continue;
            long dx = (long) ping.target().getX() - player.getBlockX();
            long dz = (long) ping.target().getZ() - player.getBlockZ();
            int distance = (int) Math.round(Math.hypot(dx, dz) / 100.0) * 100;
            tell(player, ping.label() + ": " + RumorAtlasItem.direction(dx, dz)
                    + ", about " + distance + " blocks");
        }
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), true);
    }
}
