package com.feel.gems.casino;

import com.feel.gems.GemsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** World-owned tournament scoreboard. Personal claims live with playerdata. */
public final class TournamentState extends SavedData {
    static final Codec<TournamentState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("day", -1L).forGetter(state -> state.day),
            Codec.INT.optionalFieldOf("wonTables", 0).forGetter(state -> state.wonTables)
    ).apply(instance, TournamentState::new));
    private static final SavedDataType<TournamentState> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace(GemsMod.MOD_ID + "_ragab_tournament"),
            TournamentState::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    private long day = -1;
    private int wonTables;

    public TournamentState() { }

    private TournamentState(long day, int wonTables) {
        this.day = day;
        this.wonTables = wonTables & ((1 << CasinoGame.values().length) - 1);
    }

    public static TournamentState get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(TYPE);
    }

    public static long worldDay(MinecraftServer server) {
        // Day time advances when players sleep; game time does not.
        long calendarDay = Math.floorDiv(server.getLevel(Level.OVERWORLD).getOverworldClockTime(), 24_000L);
        // An operator setting the clock backward should not reset a completed day.
        return Math.max(calendarDay, get(server).day);
    }

    public void onDay(long currentDay) {
        if (currentDay <= day) return;
        day = currentDay;
        wonTables = 0;
        setDirty();
    }

    public boolean record(long currentDay, CasinoGame game, boolean won) {
        onDay(currentDay);
        int next = TournamentProgress.record(wonTables, currentDay, game, won);
        if (next == wonTables) return false;
        wonTables = next;
        setDirty();
        return true;
    }

    public boolean completed(long currentDay) {
        onDay(currentDay);
        return TournamentProgress.completed(wonTables);
    }

    public int wonTables(long currentDay) {
        onDay(currentDay);
        return wonTables;
    }

    public String summary(long currentDay) {
        return TournamentProgress.summary(currentDay, wonTables(currentDay));
    }
}
