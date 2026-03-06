package com.feel.gems.friend;

import com.feel.gems.GemsMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A read-only conversation card, also used for the world's route epilogue. */
public record FriendDialoguePayload(String friendId, String name, String hint,
                                    String reaction, List<String> lines) implements CustomPacketPayload {
    public static final String ENDING_SCENE_PREFIX = "ending_";
    public static final Type<FriendDialoguePayload> ID = new Type<>(Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "friend_dialogue"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FriendDialoguePayload> CODEC = StreamCodec.of(
            FriendDialoguePayload::write, FriendDialoguePayload::read);

    public FriendDialoguePayload(String friendId, String name, String hint, List<String> lines) {
        this(friendId, name, hint, "", lines);
    }

    public boolean isEndingScene() {
        return friendId.startsWith(ENDING_SCENE_PREFIX);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    private static void write(RegistryFriendlyByteBuf buf, FriendDialoguePayload payload) {
        buf.writeUtf(payload.friendId(), 32);
        buf.writeUtf(payload.name(), 32);
        buf.writeUtf(payload.hint(), 160);
        buf.writeUtf(payload.reaction(), 160);
        buf.writeVarInt(payload.lines().size());
        for (String line : payload.lines()) buf.writeUtf(line, 160);
    }

    private static FriendDialoguePayload read(RegistryFriendlyByteBuf buf) {
        String friendId = buf.readUtf(32);
        String name = buf.readUtf(32);
        String hint = buf.readUtf(160);
        String reaction = buf.readUtf(160);
        int count = buf.readVarInt();
        if (count < 0 || count > 16) throw new IllegalArgumentException("Invalid friend dialogue size");
        List<String> lines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) lines.add(buf.readUtf(160));
        return new FriendDialoguePayload(friendId, name, hint, reaction, List.copyOf(lines));
    }
}
