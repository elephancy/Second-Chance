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

/** Shared fragments and a forgiving three-piece repair in Soryal's yard. */
public final class SoryalRepairState extends SavedData {
    public static final int SCRAP = 1;
    public static final int LOFT = 2;
    public static final int COPPER = 0;
    public static final int GLASS = 1;
    public static final int BRICK = 2;
    public enum Mark { WRONG, PROGRESS, SOLVED, ALREADY_SOLVED }

    private static final Codec<SoryalRepairState> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf("read", 0).forGetter(state -> state.read),
                    Codec.INT.optionalFieldOf("step", 0).forGetter(state -> state.step),
                    Codec.BOOL.optionalFieldOf("solved", false).forGetter(state -> state.solved)
            ).apply(instance, SoryalRepairState::new));
    private static final SavedDataType<SoryalRepairState> TYPE =
            new SavedDataType<>(Identifier.withDefaultNamespace(GemsMod.MOD_ID + "_soryal_repair"),
                    SoryalRepairState::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    private int read;
    private int step;
    private boolean solved;

    public SoryalRepairState() {
        this(0, 0, false);
    }

    private SoryalRepairState(int read, int step, boolean solved) {
        this.read = read & (SCRAP | LOFT);
        this.solved = solved;
        this.step = solved ? 3 : Math.clamp(step, 0, 2);
    }

    public static SoryalRepairState get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(TYPE);
    }

    public void record(int fragment) {
        if (fragment != SCRAP && fragment != LOFT) throw new IllegalArgumentException("Unknown repair fragment");
        if ((read & fragment) == 0) {
            read |= fragment;
            setDirty();
        }
    }

    public boolean ready() {
        return read == (SCRAP | LOFT);
    }

    public boolean solved() {
        return solved;
    }

    public int step() {
        return step;
    }

    public Mark mark(int piece) {
        if (piece < COPPER || piece > BRICK) throw new IllegalArgumentException("Unknown repair piece");
        if (solved) return Mark.ALREADY_SOLVED;
        if (piece != step) {
            step = 0;
            setDirty();
            return Mark.WRONG;
        }
        step++;
        if (step == 3) solved = true;
        setDirty();
        return solved ? Mark.SOLVED : Mark.PROGRESS;
    }
}
