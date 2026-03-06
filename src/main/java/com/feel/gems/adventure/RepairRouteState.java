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

/** Shared, saved progress for the route's physical repairs. */
public final class RepairRouteState extends SavedData {
    public static final int YARD = 1;
    public static final int BRIDGE = 2;
    public static final int LOFT = 4;
    private static final int ALL_SORYAL = YARD | BRIDGE | LOFT;
    public static final int WEST_BRACE = 1;
    public static final int EAST_BRACE = 2;
    private static final int ALL_RIVERWARD = WEST_BRACE | EAST_BRACE;

    private static final Codec<RepairRouteState> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf("soryalRepairs", 0)
                            .forGetter(state -> state.soryalRepairs),
                    Codec.INT.optionalFieldOf("riverwardBraces", 0)
                            .forGetter(state -> state.riverwardBraces),
                    Codec.BOOL.optionalFieldOf("riverwardComplete", false)
                            .forGetter(state -> state.riverwardComplete),
                    Codec.BOOL.optionalFieldOf("redlineComplete", false)
                            .forGetter(state -> state.redlineComplete)
            ).apply(instance, RepairRouteState::new));
    private static final SavedDataType<RepairRouteState> TYPE =
            new SavedDataType<>(Identifier.withDefaultNamespace(GemsMod.MOD_ID + "_repair_route"),
                    RepairRouteState::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    private int soryalRepairs;
    private int riverwardBraces;
    private boolean riverwardComplete;
    private boolean redlineComplete;

    public RepairRouteState() {
        this(0, 0, false, false);
    }

    private RepairRouteState(int savedRepairs, int savedBraces, boolean savedComplete,
                             boolean savedRedline) {
        soryalRepairs = savedRepairs & ALL_SORYAL;
        riverwardBraces = savedBraces & ALL_RIVERWARD;
        riverwardComplete = savedComplete && riverwardBraces == ALL_RIVERWARD;
        redlineComplete = savedRedline && riverwardComplete;
    }

    public static RepairRouteState get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean repaired(int site) {
        valid(site);
        return (soryalRepairs & site) != 0;
    }

    public int repairedCount() {
        return Integer.bitCount(soryalRepairs);
    }

    public boolean soryalComplete() {
        return soryalRepairs == ALL_SORYAL;
    }

    /** Returns true only when a new site is accounted for. */
    public boolean recordRepair(int site) {
        valid(site);
        if (repaired(site)) return false;
        soryalRepairs |= site;
        setDirty();
        return true;
    }

    public boolean riverwardBraceRepaired(int site) {
        validBrace(site);
        return (riverwardBraces & site) != 0;
    }

    public int riverwardBraceCount() {
        return Integer.bitCount(riverwardBraces);
    }

    public boolean riverwardBracesReady() {
        return riverwardBraces == ALL_RIVERWARD;
    }

    public boolean riverwardComplete() {
        return riverwardComplete;
    }

    public boolean recordRiverwardBrace(int site) {
        validBrace(site);
        if (!soryalComplete() || riverwardBraceRepaired(site)) return false;
        riverwardBraces |= site;
        setDirty();
        return true;
    }

    public boolean finishRiverward() {
        if (!soryalComplete() || !riverwardBracesReady() || riverwardComplete) return false;
        riverwardComplete = true;
        setDirty();
        return true;
    }

    public boolean redlineComplete() {
        return redlineComplete;
    }

    public boolean finishRedline() {
        if (!riverwardComplete || redlineComplete) return false;
        redlineComplete = true;
        setDirty();
        return true;
    }

    private static void valid(int site) {
        if (site != YARD && site != BRIDGE && site != LOFT) {
            throw new IllegalArgumentException("Unknown repair site");
        }
    }

    private static void validBrace(int site) {
        if (site != WEST_BRACE && site != EAST_BRACE) {
            throw new IllegalArgumentException("Unknown Riverward brace");
        }
    }
}
