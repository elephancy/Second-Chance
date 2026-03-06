package com.feel.gems.casino;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class CasinoNetworking {
    private CasinoNetworking() {
    }

    public static void register() {
        CasinoFloorInteraction.register();
        PayloadTypeRegistry.serverboundPlay().register(CasinoActionPayload.ID, CasinoActionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CasinoViewPayload.ID, CasinoViewPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CasinoActionPayload.ID, (payload, context) ->
                context.server().execute(() -> CasinoService.action(context.player(), payload)));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                CasinoService.onJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                CasinoService.onDisconnect(server, handler.getPlayer()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                CasinoService.onRespawn(newPlayer.level().getServer(), newPlayer));
        ServerTickEvents.END_SERVER_TICK.register(CasinoService::onServerTick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CasinoService.onServerStopped());
    }
}
