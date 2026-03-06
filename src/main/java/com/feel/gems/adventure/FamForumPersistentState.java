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

/** The two forum clues are shared and survive a server restart. */
public final class FamForumPersistentState extends SavedData {
    public static final int STORY = 1;
    public static final int SCHOOL = 2;
    private static final Codec<FamForumPersistentState> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf("clues", 0)
                            .forGetter(state -> state.clues)
            ).apply(instance, FamForumPersistentState::new));
    private static final SavedDataType<FamForumPersistentState> TYPE =
            new SavedDataType<>(Identifier.withDefaultNamespace(GemsMod.MOD_ID + "_fam_forum"),
                    FamForumPersistentState::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    private int clues;

    public FamForumPersistentState() {
        this(0);
    }

    private FamForumPersistentState(int clues) {
        this.clues = clues & (STORY | SCHOOL);
    }

    public static FamForumPersistentState get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean record(int clue) {
        if (clue != STORY && clue != SCHOOL) throw new IllegalArgumentException("Unknown forum clue");
        if ((clues & clue) != 0) return false;
        clues |= clue;
        setDirty();
        return true;
    }

    public boolean ready() {
        return clues == (STORY | SCHOOL);
    }
}
