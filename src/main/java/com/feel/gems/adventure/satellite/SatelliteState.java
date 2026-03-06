package com.feel.gems.adventure.satellite;

import com.feel.gems.GemsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Versioned, world-shared receipts for both friend field sites. */
public final class SatelliteState extends SavedData {
    private static final Codec<SatelliteState> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.LONG.listOf().optionalFieldOf("sunkenRelayV1", List.of())
                            .forGetter(state -> state.ocean.stream().sorted().toList()),
                    Codec.LONG.listOf().optionalFieldOf("underarchiveV1", List.of())
                            .forGetter(state -> state.archive.stream().sorted().toList())
            ).apply(instance, SatelliteState::new));
    private static final SavedDataType<SatelliteState> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace(GemsMod.MOD_ID + "_friend_satellites"),
            SatelliteState::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    private final Set<Long> ocean;
    private final Set<Long> archive;

    public SatelliteState() {
        ocean = new HashSet<>();
        archive = new HashSet<>();
    }

    private SatelliteState(List<Long> ocean, List<Long> archive) {
        this.ocean = new HashSet<>(ocean);
        this.archive = new HashSet<>(archive);
    }

    public static SatelliteState get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean oceanComplete(long root) {
        return ocean.contains(root);
    }

    public boolean archiveComplete(long root) {
        return archive.contains(root);
    }

    /** Townhall Signal Table may expose the deep-water bearing after one clear. */
    public boolean deepBearingUnlocked() {
        return !ocean.isEmpty();
    }

    /** Townhall Route Desk may expose Branch's safely mapped side passage. */
    public boolean underarchiveRouteUnlocked() {
        return !archive.isEmpty();
    }

    public Optional<BlockPos> sharedOceanBearing() {
        return ocean.stream().min(Long::compare).map(BlockPos::of);
    }

    public Optional<BlockPos> sharedArchiveBearing() {
        return archive.stream().min(Long::compare).map(BlockPos::of);
    }

    public boolean completeOcean(long root) {
        if (!ocean.add(root)) return false;
        setDirty();
        return true;
    }

    public boolean completeArchive(long root) {
        if (!archive.add(root)) return false;
        setDirty();
        return true;
    }
}
