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

/** One short-lived shared Overworld return point, saved with world game time. */
public final class LastlightReturnState extends SavedData {
    public static final long WINDOW_TICKS = 20L * 60;
    private static final Codec<LastlightReturnState> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.LONG.optionalFieldOf("point", 0L).forGetter(state -> state.point),
                    Codec.LONG.optionalFieldOf("expires", 0L).forGetter(state -> state.expires)
            ).apply(instance, LastlightReturnState::new));
    private static final SavedDataType<LastlightReturnState> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace(GemsMod.MOD_ID + "_lastlight_return"),
            LastlightReturnState::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    private long point;
    private long expires;

    public LastlightReturnState() {
        this(0L, 0L);
    }

    private LastlightReturnState(long point, long expires) {
        this.point = point;
        this.expires = expires;
    }

    public static LastlightReturnState get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(TYPE);
    }

    public BlockPos activePoint(long now) {
        return now < expires ? BlockPos.of(point) : null;
    }

    public long remaining(long now) {
        return Math.max(0L, expires - now);
    }

    public boolean mark(BlockPos position, long now) {
        return mark(position, now, WINDOW_TICKS);
    }

    public boolean mark(BlockPos position, long now, long windowTicks) {
        if (windowTicks <= 0 || windowTicks > 20L * 120) {
            throw new IllegalArgumentException("Invalid return window");
        }
        if (activePoint(now) != null) return false;
        point = position.asLong();
        expires = now + windowTicks;
        setDirty();
        return true;
    }
}
