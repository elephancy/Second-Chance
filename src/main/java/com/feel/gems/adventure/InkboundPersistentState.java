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

/** Save-bound workshop discoveries shared with late joiners. */
public final class InkboundPersistentState extends SavedData {
    private static final Codec<InkboundPersistentState> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf("triedMask", 0)
                            .forGetter(state -> state.progress.triedMask())
            ).apply(instance, InkboundPersistentState::new));
    private static final SavedDataType<InkboundPersistentState> TYPE =
            new SavedDataType<>(Identifier.withDefaultNamespace(GemsMod.MOD_ID + "_inkbound"),
                    InkboundPersistentState::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    private final InkboundProgress progress;

    public InkboundPersistentState() {
        this(0);
    }

    private InkboundPersistentState(int triedMask) {
        progress = new InkboundProgress(triedMask);
    }

    public static InkboundPersistentState get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(TYPE);
    }

    public InkboundProgress model() {
        return progress;
    }

    public boolean record(int pair) {
        if (!progress.record(pair)) return false;
        setDirty();
        return true;
    }
}
