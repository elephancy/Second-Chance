package com.feel.gems.adventure;

import com.feel.gems.adventure.AdventureSounds;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Server-owned, short-lived coastal rendezvous markers; no blocks or chunks are retained. */
public final class SignalBuoyRuntime {
    private static final int RANGE = 96;
    private static final int DURATION_TICKS = 20 * 30;
    private static final int RECHARGE_TICKS = 20 * 90;
    private static final DustParticleOptions FOAM = new DustParticleOptions(0xE5FCF5, 2.0F);
    private static final DustParticleOptions AMBER = new DustParticleOptions(0xFFD17C, 1.8F);
    private static final Map<UUID, Marker> MARKERS = new HashMap<>();
    private static final Map<UUID, Long> READY_AT = new HashMap<>();

    private record Marker(ResourceKey<Level> dimension, BlockPos pos, String owner, long until) { }

    private SignalBuoyRuntime() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(SignalBuoyRuntime::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUUID();
            MARKERS.remove(id);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            MARKERS.clear();
            READY_AT.clear();
        });
    }

    public static InteractionResult launch(ServerLevel level, ServerPlayer player, ItemStack stack) {
        long now = level.getServer().getTickCount();
        long remaining = READY_AT.getOrDefault(player.getUUID(), 0L) - now;
        if (remaining > 0 || player.getCooldowns().isOnCooldown(stack)) {
            tell(player, "Harbor Buoy ready in " + ((Math.max(remaining, 0) + 19) / 20) + " seconds.");
            return InteractionResult.SUCCESS_SERVER;
        }
        BlockPos anchor = nearestSurfaceWater(level, player.blockPosition());
        if (anchor == null) {
            tell(player, "Stand near open water to launch the buoy.");
            return InteractionResult.SUCCESS_SERVER;
        }
        MARKERS.put(player.getUUID(), new Marker(level.dimension(), anchor,
                player.getName().getString(), now + DURATION_TICKS));
        READY_AT.put(player.getUUID(), now + RECHARGE_TICKS);
        player.getCooldowns().addCooldown(stack, RECHARGE_TICKS);
        level.playSound(null, anchor, AdventureSounds.HUSHWIRE_TRUE, SoundSource.PLAYERS, 0.55F, 0.8F);
        tell(player, "Buoy lit for 30 seconds. Nearby friends can follow its light.");
        return InteractionResult.SUCCESS_SERVER;
    }

    private static BlockPos nearestSurfaceWater(ServerLevel level, BlockPos origin) {
        BlockPos nearest = null;
        int best = Integer.MAX_VALUE;
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    int distance = dx * dx + dy * dy + dz * dz;
                    if (distance >= best) continue;
                    probe.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!level.getFluidState(probe).is(FluidTags.WATER)
                            || level.getFluidState(probe.above()).is(FluidTags.WATER)) continue;
                    nearest = probe.immutable();
                    best = distance;
                }
            }
        }
        return nearest;
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        if (now % 200 == 0) READY_AT.values().removeIf(ready -> ready <= now);
        if (now % 10 != 0 || MARKERS.isEmpty()) return;
        MARKERS.values().removeIf(marker -> now >= marker.until());
        for (Marker marker : MARKERS.values()) {
            ServerLevel level = server.getLevel(marker.dimension());
            if (level == null || !level.hasChunkAt(marker.pos())) continue;
            particles(level, marker.pos(), now);
        }
        if (now % 40 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Marker nearest = null;
            long best = (long) RANGE * RANGE;
            for (Marker marker : MARKERS.values()) {
                if (!player.level().dimension().equals(marker.dimension())) continue;
                long dx = (long) marker.pos().getX() - player.getBlockX();
                long dz = (long) marker.pos().getZ() - player.getBlockZ();
                long distance = dx * dx + dz * dz;
                if (distance >= best) continue;
                nearest = marker;
                best = distance;
            }
            if (nearest == null) continue;
            long dx = (long) nearest.pos().getX() - player.getBlockX();
            long dz = (long) nearest.pos().getZ() - player.getBlockZ();
            int roundedDistance = (int) Math.round(Math.sqrt(best) / 10.0) * 10;
            tell(player, nearest.owner() + "'s buoy: " + (roundedDistance < 10
                    ? "nearby" : heading(dx, dz) + ", about "
                    + roundedDistance + " blocks"));
        }
    }

    private static String heading(long dx, long dz) {
        String northSouth = dz < -3 ? "north" : dz > 3 ? "south" : "";
        String eastWest = dx < -3 ? "west" : dx > 3 ? "east" : "";
        if (northSouth.isEmpty()) return eastWest.isEmpty() ? "nearby" : eastWest;
        return northSouth + eastWest;
    }

    private static void particles(ServerLevel level, BlockPos pos, long tick) {
        double centerX = pos.getX() + 0.5;
        double centerY = pos.getY() + 1.05;
        double centerZ = pos.getZ() + 0.5;
        double phase = (tick / 10 % 12) * Math.PI / 6;
        for (int i = 0; i < 12; i++) {
            double angle = phase + i * Math.PI / 6;
            level.sendParticles(FOAM, centerX + Math.cos(angle) * 1.4, centerY,
                    centerZ + Math.sin(angle) * 1.4, 1, 0, 0, 0, 0);
        }
        for (int i = 0; i < 5; i++) {
            level.sendParticles(AMBER, centerX, centerY + i * 0.5,
                    centerZ, 1, 0, 0, 0, 0);
        }
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), true);
    }
}
