package com.feel.gems.adventure;

import com.feel.gems.GemsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Shared survey observations and the one persistent Overworld route pin. */
public final class AdlSurveyState extends SavedData {
    public static final int EAST = 1;
    public static final int WEST = 2;
    private static final int BOTH = EAST | WEST;

    private static final Codec<AdlSurveyState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("sights", 0).forGetter(state -> state.sights),
            Codec.BOOL.optionalFieldOf("mapped", false).forGetter(state -> state.mapped),
            Codec.BOOL.optionalFieldOf("pinned", false).forGetter(state -> state.pinned),
            Codec.INT.optionalFieldOf("pinX", 0).forGetter(state -> state.pinX),
            Codec.INT.optionalFieldOf("pinY", 0).forGetter(state -> state.pinY),
            Codec.INT.optionalFieldOf("pinZ", 0).forGetter(state -> state.pinZ)
    ).apply(instance, AdlSurveyState::new));
    private static final SavedDataType<AdlSurveyState> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace(GemsMod.MOD_ID + "_adl_survey"),
            AdlSurveyState::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    private int sights;
    private boolean mapped;
    private boolean pinned;
    private int pinX;
    private int pinY;
    private int pinZ;

    public AdlSurveyState() {
        this(0, false, false, 0, 0, 0);
    }

    private AdlSurveyState(int sights, boolean mapped, boolean pinned,
                           int pinX, int pinY, int pinZ) {
        this.sights = sights & BOTH;
        this.mapped = mapped && this.sights == BOTH;
        this.pinned = pinned;
        this.pinX = pinX;
        this.pinY = pinY;
        this.pinZ = pinZ;
    }

    public static AdlSurveyState get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean observe(int sight) {
        if (sight != EAST && sight != WEST) throw new IllegalArgumentException("Unknown sightpost");
        if ((sights & sight) != 0) return false;
        sights |= sight;
        setDirty();
        return true;
    }

    public boolean bothObserved() {
        return sights == BOTH;
    }

    public boolean align() {
        if (!bothObserved() || mapped) return false;
        mapped = true;
        setDirty();
        return true;
    }

    public boolean mapped() {
        return mapped;
    }

    public BlockPos pin() {
        return pinned ? new BlockPos(pinX, pinY, pinZ) : null;
    }

    public boolean setPin(BlockPos pos) {
        if (pinned && pinX == pos.getX() && pinY == pos.getY() && pinZ == pos.getZ()) return false;
        pinned = true;
        pinX = pos.getX();
        pinY = pos.getY();
        pinZ = pos.getZ();
        setDirty();
        return true;
    }

    public boolean clearPin() {
        if (!pinned) return false;
        pinned = false;
        setDirty();
        return true;
    }
}
