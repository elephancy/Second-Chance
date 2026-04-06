package com.feel.gems.client.friend;

import com.feel.gems.friend.FriendDialoguePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class FriendClientNetworking {
    private FriendClientNetworking() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(FriendDialoguePayload.ID, (payload, context) ->
                context.client().execute(() -> FriendDialogueScreen.show(context.client(), payload)));
    }
}
