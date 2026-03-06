package com.feel.gems.adventure;

import com.feel.gems.particle.ModParticles;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Keeps the shared rescue point visible to players who arrive after the bell rang. */
public final class RescueDispatchRuntime {
    private static final int PARTICLE_INTERVAL_TICKS = 20;
    private static final int VIEW_DISTANCE_SQUARED = 48 * 48;

    private RescueDispatchRuntime() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % PARTICLE_INTERVAL_TICKS != 0) return;
            ServerLevel level = server.getLevel(Level.OVERWORLD);
            if (level == null) return;
            var point = RescueDispatchState.get(server).activePoint(level.getGameTime());
            if (point == null || !level.hasChunkAt(point)) return;
            double x = point.getX() + 0.5;
            double y = point.getY() + 1.2;
            double z = point.getZ() + 0.5;
            for (var player : level.players()) {
                if (!player.isSpectator() && player.distanceToSqr(x, y, z) <= VIEW_DISTANCE_SQUARED) {
                    level.sendParticles(ModParticles.GEM_RING, x, y, z,
                            6, 0.25, 0.45, 0.25, 0.01);
                    break;
                }
            }
        });
    }
}
