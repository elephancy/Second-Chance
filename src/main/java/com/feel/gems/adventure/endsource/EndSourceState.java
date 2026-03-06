package com.feel.gems.adventure.endsource;

import com.feel.gems.GemsMod;
import com.feel.gems.adventure.CampaignRouteState.Path;
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

/** World-shared source clear, plus each player's last known Court return. */
public final class EndSourceState extends SavedData {
    static final Codec<EndSourceState> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("version", 1).forGetter(state -> 1),
                    Codec.STRING.optionalFieldOf("clearedPath", "").forGetter(state ->
                            state.clearedPath == null ? "" : state.clearedPath.id()),
                    Codec.unboundedMap(UUIDUtil.AUTHLIB_CODEC, Codec.LONG)
                            .optionalFieldOf("courtReturns", Map.of())
                            .forGetter(state -> state.courtReturns)
            ).apply(instance, EndSourceState::new));
    private static final SavedDataType<EndSourceState> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace(GemsMod.MOD_ID + "_end_source_v1"),
            EndSourceState::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    private Path clearedPath;
    private final Map<UUID, Long> courtReturns = new HashMap<>();

    public EndSourceState() {
        this(1, "", Map.of());
    }

    private EndSourceState(int version, String path, Map<UUID, Long> returns) {
        if (version != 1) throw new IllegalArgumentException("Unknown End Source save version");
        clearedPath = Path.from(path);
        courtReturns.putAll(returns);
    }

    public static EndSourceState get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(TYPE);
    }

    public Path clearedPath() { return clearedPath; }

    public boolean cleared(Path path) { return path != null && clearedPath == path; }

    /** All players receive the selected route's Court aid after one full clear. */
    public boolean clear(Path path) {
        if (path == null || clearedPath != null) return false;
        clearedPath = path;
        setDirty();
        return true;
    }

    /** Root calls this only after a player uses the real Overworld Court dais. */
    public void recordCourtReturn(UUID player, BlockPos dais) {
        long packed = dais.asLong();
        Long previous = courtReturns.put(player, packed);
        if (previous == null || previous.longValue() != packed) setDirty();
    }

    public BlockPos courtReturn(UUID player) {
        Long packed = courtReturns.get(player);
        return packed == null ? null : BlockPos.of(packed);
    }
}
