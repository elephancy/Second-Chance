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

/** Two side stories and a shared, forgiving three-stop garden route. */
public final class YehiaStoryState extends SavedData {
    public static final int TRUTH = 1;
    public static final int PROP = 2;
    public static final int MUSHROOM = 0;
    public static final int LANTERN = 1;
    public static final int TREE = 2;
    private static final Codec<YehiaStoryState> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf("read", 0).forGetter(state -> state.read),
                    Codec.INT.optionalFieldOf("step", -1).forGetter(state -> state.step),
                    Codec.BOOL.optionalFieldOf("completed", false).forGetter(state -> state.completed)
            ).apply(instance, YehiaStoryState::new));
    private static final SavedDataType<YehiaStoryState> TYPE =
            new SavedDataType<>(Identifier.withDefaultNamespace(GemsMod.MOD_ID + "_yehia_story"),
                    YehiaStoryState::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    private int read;
    private int step;
    private boolean completed;

    public YehiaStoryState() {
        this(0, -1, false);
    }

    private YehiaStoryState(int read, int step, boolean completed) {
        this.read = read & (TRUTH | PROP);
        this.completed = completed;
        this.step = completed ? 3 : Math.clamp(step, -1, 2);
    }

    public static YehiaStoryState get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(TYPE);
    }

    public void record(int clue) {
        if (clue != TRUTH && clue != PROP) throw new IllegalArgumentException("Unknown story clue");
        if ((read & clue) == 0) {
            read |= clue;
            setDirty();
        }
    }

    public boolean ready() {
        return read == (TRUTH | PROP);
    }

    public boolean completed() {
        return completed;
    }

    public boolean started() {
        return step >= 0;
    }

    public int step() {
        return step;
    }

    public boolean start() {
        if (completed) return false;
        if (step < 0) {
            step = 0;
            setDirty();
        }
        return true;
    }

    public boolean mark(int marker) {
        if (marker < MUSHROOM || marker > TREE) throw new IllegalArgumentException("Unknown story marker");
        if (step < 0 || completed) return false;
        if (marker == step) {
            step++;
            if (step == 3) completed = true;
        } else {
            step = 0;
        }
        setDirty();
        return completed;
    }
}
