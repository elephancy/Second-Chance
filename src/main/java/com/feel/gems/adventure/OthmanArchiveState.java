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

/** Two read rooms and one published lead are shared across the world. */
public final class OthmanArchiveState extends SavedData {
    public static final int CATALOG = 1;
    public static final int STUDY = 2;
    private static final Codec<OthmanArchiveState> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf("read", 0).forGetter(state -> state.read),
                    Codec.INT.optionalFieldOf("published", 0).forGetter(state -> state.published)
            ).apply(instance, OthmanArchiveState::new));
    private static final SavedDataType<OthmanArchiveState> TYPE =
            new SavedDataType<>(Identifier.withDefaultNamespace(GemsMod.MOD_ID + "_othman_archive"),
                    OthmanArchiveState::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    private int read;
    private int published;

    public OthmanArchiveState() {
        this(0, 0);
    }

    private OthmanArchiveState(int read, int published) {
        this.read = read & (CATALOG | STUDY);
        this.published = published == CATALOG || published == STUDY ? published : 0;
    }

    public static OthmanArchiveState get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean record(int note) {
        if (note != CATALOG && note != STUDY) throw new IllegalArgumentException("Unknown archive note");
        if ((read & note) != 0) return false;
        read |= note;
        setDirty();
        return true;
    }

    public boolean ready() {
        return read == (CATALOG | STUDY);
    }

    public int published() {
        return published;
    }

    public boolean publish(int note) {
        if (note != CATALOG && note != STUDY) throw new IllegalArgumentException("Unknown published note");
        if (!ready() || published != 0) return false;
        published = note;
        setDirty();
        return true;
    }
}
