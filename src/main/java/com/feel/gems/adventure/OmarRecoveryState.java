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

/** The last fall of each player, recorded at death before a respawn or duel can move them. */
public final class OmarRecoveryState extends SavedData {
    public record Trace(Identifier dimension, int x, int y, int z) {
        static final Codec<Trace> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("dimension").forGetter(Trace::dimension),
                Codec.INT.fieldOf("x").forGetter(Trace::x),
                Codec.INT.fieldOf("y").forGetter(Trace::y),
                Codec.INT.fieldOf("z").forGetter(Trace::z)
        ).apply(instance, Trace::new));

        public BlockPos pos() {
            return new BlockPos(x, y, z);
        }
    }

    static final Codec<OmarRecoveryState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(UUIDUtil.AUTHLIB_CODEC, Trace.CODEC)
                    .optionalFieldOf("falls", Map.of()).forGetter(state -> state.falls)
    ).apply(instance, OmarRecoveryState::new));
    private static final SavedDataType<OmarRecoveryState> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace(GemsMod.MOD_ID + "_omar_recovery"),
            OmarRecoveryState::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    private final Map<UUID, Trace> falls = new HashMap<>();

    public OmarRecoveryState() { }

    private OmarRecoveryState(Map<UUID, Trace> falls) {
        this.falls.putAll(falls);
    }

    public static OmarRecoveryState get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(TYPE);
    }

    public void record(UUID player, Identifier dimension, BlockPos pos) {
        Trace next = new Trace(dimension, pos.getX(), pos.getY(), pos.getZ());
        if (next.equals(falls.put(player, next))) return;
        setDirty();
    }

    public Trace trace(UUID player) {
        return falls.get(player);
    }
}
