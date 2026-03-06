package com.feel.gems.adventure;

import com.feel.gems.GemsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** One world-shared, physically checked approach mark for each boss expedition. */
public final class AngleApproachState extends SavedData {
    private static final Codec<AngleApproachState> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(Codec.unboundedMap(Codec.STRING, Codec.LONG)
                    .optionalFieldOf("approaches", Map.of())
                    .forGetter(state -> Map.copyOf(state.approaches)))
                    .apply(instance, AngleApproachState::new));
    private static final SavedDataType<AngleApproachState> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace(GemsMod.MOD_ID + "_angle_approaches"),
            AngleApproachState::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    private final Map<String, Long> approaches;

    public AngleApproachState() {
        this(Map.of());
    }

    private AngleApproachState(Map<String, Long> approaches) {
        this.approaches = new HashMap<>(approaches);
    }

    public static AngleApproachState get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(TYPE);
    }

    public BlockPos approach(LandmarkKey target) {
        Long packed = approaches.get(target.id());
        return packed == null ? null : BlockPos.of(packed);
    }

    public boolean mark(LandmarkKey target, BlockPos position) {
        Long previous = approaches.put(target.id(), position.asLong());
        if (previous != null && previous == position.asLong()) return false;
        setDirty();
        return true;
    }
}
