package com.feel.gems.casino;

import com.feel.gems.GemsMod;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The complete public/private view for one player; no client-side game outcome is trusted. */
public record CasinoViewPayload(UUID sessionId, int revision, String phase, String gameId, String title,
                                String rules, String message, int stake, int emeralds,
                                List<String> board, List<String> playerCards,
                                List<String> dealerCards, List<Choice> choices) implements CustomPacketPayload {
    public record Choice(String id, String label) {
    }

    public static final Type<CasinoViewPayload> ID = new Type<>(Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "casino_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CasinoViewPayload> CODEC = StreamCodec.of(
            CasinoViewPayload::write, CasinoViewPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    private static void write(RegistryFriendlyByteBuf buf, CasinoViewPayload payload) {
        buf.writeUUID(payload.sessionId());
        buf.writeVarInt(payload.revision());
        buf.writeUtf(payload.phase(), 16);
        buf.writeUtf(payload.gameId(), 32);
        buf.writeUtf(payload.title(), 64);
        buf.writeUtf(payload.rules(), 256);
        buf.writeUtf(payload.message(), 256);
        buf.writeVarInt(payload.stake());
        buf.writeVarInt(payload.emeralds());
        buf.writeVarInt(payload.board().size());
        for (String line : payload.board()) buf.writeUtf(line, 128);
        writeCards(buf, payload.playerCards());
        writeCards(buf, payload.dealerCards());
        buf.writeVarInt(payload.choices().size());
        for (Choice choice : payload.choices()) {
            buf.writeUtf(choice.id(), 32);
            buf.writeUtf(choice.label(), 64);
        }
    }

    private static CasinoViewPayload read(RegistryFriendlyByteBuf buf) {
        UUID session = buf.readUUID();
        int revision = buf.readVarInt();
        String phase = buf.readUtf(16);
        String game = buf.readUtf(32);
        String title = buf.readUtf(64);
        String rules = buf.readUtf(256);
        String message = buf.readUtf(256);
        int stake = buf.readVarInt();
        int emeralds = buf.readVarInt();
        int boardCount = boundedCount(buf.readVarInt(), 8);
        List<String> board = new ArrayList<>(boardCount);
        for (int i = 0; i < boardCount; i++) board.add(buf.readUtf(128));
        List<String> playerCards = readCards(buf);
        List<String> dealerCards = readCards(buf);
        int choiceCount = boundedCount(buf.readVarInt(), 8);
        List<Choice> choices = new ArrayList<>(choiceCount);
        for (int i = 0; i < choiceCount; i++) {
            choices.add(new Choice(buf.readUtf(32), buf.readUtf(64)));
        }
        return new CasinoViewPayload(session, revision, phase, game, title, rules, message,
                stake, emeralds, List.copyOf(board), playerCards, dealerCards,
                List.copyOf(choices));
    }

    private static void writeCards(RegistryFriendlyByteBuf buf, List<String> cards) {
        buf.writeVarInt(cards.size());
        for (String card : cards) buf.writeUtf(card, 32);
    }

    private static List<String> readCards(RegistryFriendlyByteBuf buf) {
        int count = boundedCount(buf.readVarInt(), 12);
        List<String> cards = new ArrayList<>(count);
        for (int i = 0; i < count; i++) cards.add(buf.readUtf(32));
        return List.copyOf(cards);
    }

    private static int boundedCount(int count, int max) {
        if (count < 0 || count > max) throw new IllegalArgumentException("Invalid casino view size");
        return count;
    }
}
