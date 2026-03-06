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

/** The two public map clues and the door the high map omitted. */
public final class AliRouteState extends SavedData {
    public static final int HIGH_MAP = 1;
    public static final int GATE_RUBBING = 2;
    public enum Choice { WRONG, SOLVED, ALREADY_SOLVED }

    private static final Codec<AliRouteState> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf("read", 0).forGetter(state -> state.read),
                    Codec.BOOL.optionalFieldOf("solved", false).forGetter(state -> state.solved)
            ).apply(instance, AliRouteState::new));
    private static final SavedDataType<AliRouteState> TYPE =
            new SavedDataType<>(Identifier.withDefaultNamespace(GemsMod.MOD_ID + "_ali_route"),
                    AliRouteState::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    private int read;
    private boolean solved;

    public AliRouteState() {
        this(0, false);
    }

    private AliRouteState(int read, boolean solved) {
        this.read = read & (HIGH_MAP | GATE_RUBBING);
        this.solved = solved;
    }

    public static AliRouteState get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(TYPE);
    }

    public void read(int clue) {
        if (clue != HIGH_MAP && clue != GATE_RUBBING) throw new IllegalArgumentException("Unknown route clue");
        if ((read & clue) == 0) {
            read |= clue;
            setDirty();
        }
    }

    public boolean ready() {
        return read == (HIGH_MAP | GATE_RUBBING);
    }

    public boolean solved() {
        return solved;
    }

    public Choice choose(boolean west) {
        if (solved) return Choice.ALREADY_SOLVED;
        if (!west) return Choice.WRONG;
        solved = true;
        setDirty();
        return Choice.SOLVED;
    }
}
