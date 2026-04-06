package com.feel.gems.client.casino;

import com.feel.gems.casino.CasinoViewPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class CasinoClientNetworking {
    private CasinoClientNetworking() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CasinoViewPayload.ID, (payload, context) ->
                context.client().execute(() -> CasinoScreen.show(context.client(), payload)));
    }
}
