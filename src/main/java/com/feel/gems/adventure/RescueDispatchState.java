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

/** A single public rescue rally, shared by late joiners and saved across restarts. */
public final class RescueDispatchState extends SavedData {
    public static final long WINDOW_TICKS = 20L * 60 * 3;
    static final Codec<RescueDispatchState> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.LONG.optionalFieldOf("point", 0L).forGetter(state -> state.point),
                    Codec.LONG.optionalFieldOf("expires", 0L).forGetter(state -> state.expires)
            ).apply(instance, RescueDispatchState::new));
    private static final SavedDataType<RescueDispatchState> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace(GemsMod.MOD_ID + "_rescue_dispatch"),
            RescueDispatchState::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    private long point;
    private long expires;

    public RescueDispatchState() {
        this(0L, 0L);
    }

    private RescueDispatchState(long point, long expires) {
        this.point = point;
        this.expires = expires;
    }

    public static RescueDispatchState get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(TYPE);
    }

    public BlockPos activePoint(long now) {
        return now < expires ? BlockPos.of(point) : null;
    }

    public long remaining(long now) {
        return Math.max(0L, expires - now);
    }

    public boolean call(BlockPos position, long now) {
        return call(position, now, WINDOW_TICKS);
    }

    public boolean call(BlockPos position, long now, long windowTicks) {
        if (windowTicks <= 0 || windowTicks > 20L * 60 * 5) {
            throw new IllegalArgumentException("Invalid rescue window");
        }
        if (activePoint(now) != null) return false;
        point = position.asLong();
        expires = now + windowTicks;
        setDirty();
        return true;
    }
}
