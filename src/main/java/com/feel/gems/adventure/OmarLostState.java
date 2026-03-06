package com.feel.gems.adventure;

import com.feel.gems.GemsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** The two surviving records and the object a group recovered by deduction. */
public final class OmarLostState extends SavedData {
    public static final int RECEIPT = 1;
    public static final int LEDGER = 2;
    public static final int COMPASS = 0;
    public static final int CLOCK = 1;
    public static final int BOOK = 2;
    public enum Choice { WRONG, SOLVED, ALREADY_SOLVED }

    private static final Codec<OmarLostState> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf("read", 0).forGetter(state -> state.read),
                    Codec.BOOL.optionalFieldOf("solved", false).forGetter(state -> state.solved)
            ).apply(instance, OmarLostState::new));
    private static final SavedDataType<OmarLostState> TYPE =
            new SavedDataType<>(Identifier.withDefaultNamespace(GemsMod.MOD_ID + "_omar_lost"),
                    OmarLostState::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    private int read;
    private boolean solved;

    public OmarLostState() {
        this(0, false);
    }

    private OmarLostState(int read, boolean solved) {
        this.read = read & (RECEIPT | LEDGER);
        this.solved = solved;
    }

    public static OmarLostState get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(TYPE);
    }

    public void record(int record) {
        if (record != RECEIPT && record != LEDGER) throw new IllegalArgumentException("Unknown lost record");
        if ((read & record) == 0) {
            read |= record;
            setDirty();
        }
    }

    public boolean ready() {
        return read == (RECEIPT | LEDGER);
    }

    public boolean solved() {
        return solved;
    }

    public Choice choose(int object) {
        if (object < COMPASS || object > BOOK) throw new IllegalArgumentException("Unknown lost object");
        if (solved) return Choice.ALREADY_SOLVED;
        if (object != COMPASS) return Choice.WRONG;
        solved = true;
        setDirty();
        return Choice.SOLVED;
    }
}
