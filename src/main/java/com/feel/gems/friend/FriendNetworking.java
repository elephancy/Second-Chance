package com.feel.gems.friend;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class FriendNetworking {
    private FriendNetworking() {
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(FriendDialoguePayload.ID, FriendDialoguePayload.CODEC);
    }
}
