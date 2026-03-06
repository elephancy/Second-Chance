package com.feel.gems.adventure;

import com.feel.gems.GemsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Personal Overworld departure points; every use is revalidated live. */
public final class DimensionReturnState extends SavedData {
    private static final Codec<DimensionReturnState> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("version", 1).forGetter(state -> 1),
                    Codec.unboundedMap(UUIDUtil.AUTHLIB_CODEC, Codec.LONG)
                            .optionalFieldOf("departures", Map.of())
                            .forGetter(state -> state.departures)
            ).apply(instance, DimensionReturnState::new));
    private static final SavedDataType<DimensionReturnState> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace(GemsMod.MOD_ID + "_dimension_returns_v1"),
            DimensionReturnState::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    private final Map<UUID, Long> departures = new HashMap<>();

    public DimensionReturnState() {
        this(1, Map.of());
    }

    private DimensionReturnState(int version, Map<UUID, Long> saved) {
        if (version != 1) throw new IllegalArgumentException("Unknown dimension return save version");
        departures.putAll(saved);
    }

    public static DimensionReturnState get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(TYPE);
    }

    public BlockPos departure(UUID player) {
        Long packed = departures.get(player);
        return packed == null ? null : BlockPos.of(packed);
    }

    void remember(UUID player, BlockPos feet) {
        long packed = feet.asLong();
        Long previous = departures.put(player, packed);
        if (previous == null || previous.longValue() != packed) setDirty();
    }
}
