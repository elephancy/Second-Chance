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

/** Shared saved progress for Release's field connections. */
public final class ReleaseRouteState extends SavedData {
    public static final int HIGH_MAP = 1;
    public static final int CUT_PASSAGE = 2;
    public static final int FAM_STORY = 1;
    public static final int FAM_SCHOOL = 2;
    private static final long FAM_WINDOW_TICKS = 20L * 60 * 5;
    private static final long UNSET = Long.MIN_VALUE;
    private static final Codec<ReleaseRouteState> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.BOOL.optionalFieldOf("nourRelayComplete", false)
                            .forGetter(state -> state.nourRelayComplete),
                    Codec.LONG.optionalFieldOf("aliHighAnchor", UNSET)
                            .forGetter(state -> state.aliHighAnchor),
                    Codec.LONG.optionalFieldOf("aliLowAnchor", UNSET)
                            .forGetter(state -> state.aliLowAnchor),
                    Codec.BOOL.optionalFieldOf("aliShortcutComplete", false)
                            .forGetter(state -> state.aliShortcutComplete),
                    Codec.INT.optionalFieldOf("famDispatchSerial", 0)
                            .forGetter(state -> state.famDispatchSerial),
                    Codec.INT.optionalFieldOf("famDispatchStops", 0)
                            .forGetter(state -> state.famDispatchStops),
                    Codec.LONG.optionalFieldOf("famDispatchStarted", 0L)
                            .forGetter(state -> state.famDispatchStarted),
                    Codec.BOOL.optionalFieldOf("famDispatchComplete", false)
                            .forGetter(state -> state.famDispatchComplete)
            ).apply(instance, ReleaseRouteState::new));
    private static final SavedDataType<ReleaseRouteState> TYPE =
            new SavedDataType<>(Identifier.withDefaultNamespace(GemsMod.MOD_ID + "_release_route"),
                    ReleaseRouteState::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    private boolean nourRelayComplete;
    private long aliHighAnchor = UNSET;
    private long aliLowAnchor = UNSET;
    private boolean aliShortcutComplete;
    private int famDispatchSerial;
    private int famDispatchStops;
    private long famDispatchStarted;
    private boolean famDispatchComplete;

    public ReleaseRouteState() { }

    private ReleaseRouteState(boolean savedComplete, long savedHigh, long savedLow,
                              boolean savedAliComplete, int savedSerial, int savedStops,
                              long savedStarted, boolean savedFamComplete) {
        nourRelayComplete = savedComplete;
        aliHighAnchor = savedHigh;
        aliLowAnchor = savedLow;
        if (aliHighAnchor != UNSET && aliLowAnchor != UNSET
                && !ShortcutRoute.inRange(BlockPos.of(aliHighAnchor), BlockPos.of(aliLowAnchor))) {
            aliHighAnchor = UNSET;
            aliLowAnchor = UNSET;
        }
        aliShortcutComplete = savedAliComplete && nourRelayComplete && aliAnchorsReady();
        famDispatchSerial = Math.max(0, savedSerial);
        famDispatchStops = savedStops & (FAM_STORY | FAM_SCHOOL);
        famDispatchStarted = Math.max(0, savedStarted);
        famDispatchComplete = savedFamComplete && aliShortcutComplete;
    }

    public static ReleaseRouteState get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean nourRelayComplete() {
        return nourRelayComplete;
    }

    public boolean finishNourRelay() {
        if (nourRelayComplete) return false;
        nourRelayComplete = true;
        setDirty();
        return true;
    }

    public boolean aliAnchorsReady() {
        return aliHighAnchor != UNSET && aliLowAnchor != UNSET;
    }

    public boolean aliShortcutComplete() {
        return aliShortcutComplete;
    }

    public boolean recordAliAnchor(int site, BlockPos pos) {
        validSite(site);
        if (!nourRelayComplete || aliShortcutComplete) return false;
        long current = site == HIGH_MAP ? aliHighAnchor : aliLowAnchor;
        if (current != UNSET) return false;
        long other = site == HIGH_MAP ? aliLowAnchor : aliHighAnchor;
        if (other != UNSET && !ShortcutRoute.inRange(pos, BlockPos.of(other))) return false;
        if (site == HIGH_MAP) aliHighAnchor = pos.asLong();
        else aliLowAnchor = pos.asLong();
        setDirty();
        return true;
    }

    /** A real chalk teleport must connect the two saved physical anchors. */
    public boolean matchesAliTrip(BlockPos from, BlockPos to) {
        if (!aliAnchorsReady() || aliShortcutComplete) return false;
        BlockPos high = BlockPos.of(aliHighAnchor);
        BlockPos low = BlockPos.of(aliLowAnchor);
        return (ShortcutRoute.near(from, high) && ShortcutRoute.near(to, low))
                || (ShortcutRoute.near(from, low) && ShortcutRoute.near(to, high));
    }

    public BlockPos aliHighAnchor() {
        return aliHighAnchor == UNSET ? null : BlockPos.of(aliHighAnchor);
    }

    public BlockPos aliLowAnchor() {
        return aliLowAnchor == UNSET ? null : BlockPos.of(aliLowAnchor);
    }

    public boolean finishAliShortcut() {
        if (!nourRelayComplete || !aliAnchorsReady() || aliShortcutComplete) return false;
        aliShortcutComplete = true;
        setDirty();
        return true;
    }

    public boolean famDispatchComplete() {
        return famDispatchComplete;
    }

    public int famDispatchStops() {
        return famDispatchStops;
    }

    public int famDispatchSerial() {
        return famDispatchSerial;
    }

    public boolean famDispatchActive(long gameTime) {
        return aliShortcutComplete && !famDispatchComplete && famDispatchSerial > 0
                && gameTime >= famDispatchStarted
                && gameTime - famDispatchStarted <= FAM_WINDOW_TICKS;
    }

    /** A timed replacement invalidates a lost or stale physical dispatch. */
    public int startFamDispatch(long gameTime) {
        if (!aliShortcutComplete || famDispatchComplete || famDispatchActive(gameTime)) return 0;
        famDispatchSerial = famDispatchSerial == Integer.MAX_VALUE ? 1 : famDispatchSerial + 1;
        famDispatchStops = 0;
        famDispatchStarted = gameTime;
        setDirty();
        return famDispatchSerial;
    }

    public boolean stampFamDispatch(int serial, int stop, long gameTime) {
        if (stop != FAM_STORY && stop != FAM_SCHOOL) throw new IllegalArgumentException("Unknown forum stop");
        if (!famDispatchActive(gameTime) || serial != famDispatchSerial || (famDispatchStops & stop) != 0) {
            return false;
        }
        famDispatchStops |= stop;
        setDirty();
        return true;
    }

    public boolean finishFamDispatch(int serial, long gameTime) {
        if (!famDispatchActive(gameTime) || serial != famDispatchSerial
                || famDispatchStops != (FAM_STORY | FAM_SCHOOL)) return false;
        famDispatchComplete = true;
        setDirty();
        return true;
    }

    private static void validSite(int site) {
        if (site != HIGH_MAP && site != CUT_PASSAGE) {
            throw new IllegalArgumentException("Unknown Ali Release anchor");
        }
    }
}
