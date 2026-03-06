package com.feel.gems.casino;

import com.feel.gems.GemsMod;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A choice for exactly one revision of one server-owned casino session. */
public record CasinoActionPayload(UUID sessionId, int revision, String action) implements CustomPacketPayload {
    public static final Type<CasinoActionPayload> ID = new Type<>(Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "casino_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CasinoActionPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUUID(payload.sessionId());
                buf.writeVarInt(payload.revision());
                buf.writeUtf(payload.action(), 32);
            },
            buf -> new CasinoActionPayload(buf.readUUID(), buf.readVarInt(), buf.readUtf(32)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
