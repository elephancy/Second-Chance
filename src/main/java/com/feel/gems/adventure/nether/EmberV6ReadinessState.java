package com.feel.gems.adventure.nether;

import com.feel.gems.GemsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Last observed loaded gate verdict for a saved v6 Ember owner, not a noise prediction. */
public final class EmberV6ReadinessState extends SavedData {
    public enum Verdict { UNKNOWN, READY, SEALED }

    private record Entry(int ownerX, int ownerZ, long root, long arch, boolean ready) {
        private static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.INT.fieldOf("ownerX").forGetter(Entry::ownerX),
                        Codec.INT.fieldOf("ownerZ").forGetter(Entry::ownerZ),
                        Codec.LONG.fieldOf("root").forGetter(Entry::root),
                        Codec.LONG.fieldOf("arch").forGetter(Entry::arch),
                        Codec.BOOL.fieldOf("ready").forGetter(Entry::ready)
                ).apply(instance, Entry::new));
    }

    static final Codec<EmberV6ReadinessState> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("version", 1).forGetter(state -> 1),
                    Entry.CODEC.listOf().optionalFieldOf("sites", List.of())
                            .forGetter(state -> new ArrayList<>(state.sites.values()))
            ).apply(instance, EmberV6ReadinessState::new));
    private static final SavedDataType<EmberV6ReadinessState> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace(GemsMod.MOD_ID + "_ember_v6_readiness_v1"),
            EmberV6ReadinessState::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    private final Map<Long, Entry> sites = new HashMap<>();

    public EmberV6ReadinessState() {
        this(1, List.of());
    }

    private EmberV6ReadinessState(int version, List<Entry> saved) {
        if (version != 1) throw new IllegalArgumentException("Unknown Ember readiness version");
        for (Entry entry : saved) {
            sites.putIfAbsent(key(entry.ownerX(), entry.ownerZ()), entry);
        }
    }

    public static EmberV6ReadinessState get(ServerLevel level) {
        if (level.dimension() != Level.NETHER) {
            throw new IllegalArgumentException("Ember readiness belongs to the Nether");
        }
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    private static long key(int x, int z) {
        return (x & 0xffffffffL) | ((long) z << 32);
    }

    /** A reused owner with different saved geometry is unknown, never blocked. */
    public Verdict verdict(ChunkPos owner, BlockPos root, BlockPos arch) {
        Entry entry = sites.get(key(owner.x(), owner.z()));
        if (entry == null || entry.root() != root.asLong()
                || entry.arch() != arch.asLong()) return Verdict.UNKNOWN;
        return entry.ready() ? Verdict.READY : Verdict.SEALED;
    }

    public void observe(ChunkPos owner, BlockPos root, BlockPos arch, Verdict verdict) {
        if (verdict == Verdict.UNKNOWN) return;
        Entry next = new Entry(owner.x(), owner.z(), root.asLong(), arch.asLong(),
                verdict == Verdict.READY);
        Entry previous = sites.put(key(owner.x(), owner.z()), next);
        if (!next.equals(previous)) setDirty();
    }
}
