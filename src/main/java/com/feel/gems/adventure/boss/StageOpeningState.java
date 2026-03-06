package com.feel.gems.adventure.boss;

import com.feel.gems.GemsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** One world-shared, one-use opening for the next Cadenza rematch. */
public final class StageOpeningState extends SavedData {
    public record Selection(String label, String hint) { }

    static final Codec<StageOpeningState> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(Codec.INT.optionalFieldOf("pending", -1)
                    .forGetter(state -> state.pending)).apply(instance, StageOpeningState::new));
    private static final SavedDataType<StageOpeningState> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace(GemsMod.MOD_ID + "_stage_opening"),
            StageOpeningState::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    private int pending;

    public StageOpeningState() {
        this(-1);
    }

    private StageOpeningState(int pending) {
        this.pending = pending >= 0 && pending < CadenzaPattern.Act.values().length ? pending : -1;
    }

    public static StageOpeningState get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(TYPE);
    }

    public Selection pending() {
        CadenzaPattern.Act act = peekAct();
        return act == null ? null : new Selection(act.label, act.hint);
    }

    public Selection cycle() {
        pending = (pending + 1) % CadenzaPattern.Act.values().length;
        setDirty();
        return pending();
    }

    CadenzaPattern.Act consume() {
        CadenzaPattern.Act act = peekAct();
        if (act != null) {
            pending = -1;
            setDirty();
        }
        return act;
    }

    private CadenzaPattern.Act peekAct() {
        return pending < 0 ? null : CadenzaPattern.Act.values()[pending];
    }
}
