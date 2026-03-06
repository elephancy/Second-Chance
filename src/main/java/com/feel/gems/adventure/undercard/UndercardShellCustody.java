package com.feel.gems.adventure.undercard;

import com.feel.gems.GemsMod;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Durable, per-cell proof that a newly generated Undercard shell has not been
 * touched since its chunk became FULL. A saved chunk without this proof may be
 * inspected without writes, but may never receive a delayed shell repair.
 */
public final class UndercardShellCustody {
    private static final String CLEAN = "v1:-";
    private static final String OVERFLOW = "v1:!";
    private static final int MAX_MUTATIONS = 1_024;
    private static final int MAX_ENCODED = 3 + MAX_MUTATIONS * 6;
    private static final AttachmentType<String> WATCH = AttachmentRegistry.createPersistent(
            Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "undercard_shell_watch_v1"),
            Codec.STRING);
    private static final ResourceKey<Structure> SITE = ResourceKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "undercard"));
    private static final ResourceKey<Structure> V5_TRIAL = ResourceKey.create(
            Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "undercard_v5_trial"));
    private static final Map<LevelChunk, Watch> ACTIVE = new ConcurrentHashMap<>();
    private static final ThreadLocal<Authoring> AUTHORING = new ThreadLocal<>();
    private static boolean initialized;

    private UndercardShellCustody() { }

    /** Register before the first natural chunk reaches FULL. */
    public static void init() {
        if (initialized) return;
        initialized = true;
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, newlyGenerated) -> {
            if (level.dimension() != Level.OVERWORLD) return;
            // Structure references are already present at FULL, even if the
            // owner/start chunk has not itself finished loading. Start the
            // watch before this chunk is exposed to player interaction.
            if (newlyGenerated && hasVenueReference(level, chunk)
                    && chunk.getAttached(WATCH) == null) {
                chunk.setAttached(WATCH, CLEAN);
            }
            if (chunk.getAttached(WATCH) != null) watch(chunk);
        });
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> ACTIVE.remove(chunk));
        // SERVER_STOPPING precedes vanilla's final world saves. Keep watching
        // until shutdown has actually finished.
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ACTIVE.clear());
    }

    /** Called at LevelChunk.setBlockState HEAD, including same-state attempts. */
    public static void recordMutation(LevelChunk chunk, BlockPos pos) {
        Authoring authored = AUTHORING.get();
        if (authored != null && !authored.consumed && authored.position.equals(pos)) {
            authored.consumed = true;
            return;
        }
        Watch watch = watch(chunk);
        if (watch == null) return;
        watch.record(chunk, pos);
    }

    /** A mismatch is writable only if this exact saved cell was never set after FULL. */
    static boolean canRepair(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4,
                pos.getZ() >> 4);
        Watch watch = chunk == null ? null : watch(chunk);
        return watch != null && watch.canRepair(chunk, pos);
    }

    /** Suppress only the one intended setBlockState call, not neighboring updates. */
    static boolean setAuthoredBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (AUTHORING.get() != null)
            throw new IllegalStateException("Nested Undercard shell write");
        AUTHORING.set(new Authoring(pos.immutable()));
        try {
            return level.setBlock(pos, state, 2);
        } finally {
            AUTHORING.remove();
        }
    }

    private static Watch watch(LevelChunk chunk) {
        String saved = chunk.getAttached(WATCH);
        if (saved == null) return null;
        Watch cached = ACTIVE.get(chunk);
        if (cached != null && cached.serialized.equals(saved)) return cached;
        Watch parsed = Watch.parse(chunk, saved);
        ACTIVE.put(chunk, parsed);
        return parsed;
    }

    private static boolean hasVenueReference(ServerLevel level, LevelChunk chunk) {
        for (ResourceKey<Structure> key : List.of(SITE, V5_TRIAL)) {
            Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                    .getValue(key);
            if (structure == null) continue;
            StructureStart start = chunk.getStartForStructure(structure);
            if (start != null && start.isValid()) return true;
            var references = chunk.getAllReferences().get(structure);
            if (references != null && !references.isEmpty()) return true;
        }
        return false;
    }

    private static int pack(LevelChunk chunk, BlockPos pos) {
        if ((pos.getX() >> 4) != chunk.getPos().x()
                || (pos.getZ() >> 4) != chunk.getPos().z()) return -1;
        int localY = pos.getY() - chunk.getMinY();
        if (localY < 0 || localY >= chunk.getHeight() || localY >= 0x1000) return -1;
        return (localY << 8) | ((pos.getZ() & 15) << 4) | (pos.getX() & 15);
    }

    private static final class Authoring {
        final BlockPos position;
        boolean consumed;

        Authoring(BlockPos position) { this.position = position; }
    }

    private static final class Watch {
        final TreeSet<Integer> mutations;
        boolean blocked;
        String serialized;

        private Watch(TreeSet<Integer> mutations, boolean blocked, String serialized) {
            this.mutations = mutations;
            this.blocked = blocked;
            this.serialized = serialized;
        }

        static Watch parse(LevelChunk chunk, String saved) {
            TreeSet<Integer> mutations = new TreeSet<>();
            if (CLEAN.equals(saved)) return new Watch(mutations, false, saved);
            if (OVERFLOW.equals(saved)) return new Watch(mutations, true, saved);
            if (!saved.startsWith("v1:") || saved.length() > MAX_ENCODED)
                return new Watch(mutations, true, saved);
            String[] values = saved.substring(3).split(",", -1);
            if (values.length == 0 || values.length > MAX_MUTATIONS)
                return new Watch(mutations, true, saved);
            int previous = -1;
            for (String value : values) {
                if (!value.matches("[0-9a-f]{5}"))
                    return new Watch(new TreeSet<>(), true, saved);
                int packed = Integer.parseInt(value, 16);
                if (packed <= previous || (packed >>> 8) >= chunk.getHeight())
                    return new Watch(new TreeSet<>(), true, saved);
                mutations.add(packed);
                previous = packed;
            }
            return new Watch(mutations, false, saved);
        }

        synchronized boolean canRepair(LevelChunk chunk, BlockPos pos) {
            int packed = pack(chunk, pos);
            return !blocked && packed >= 0 && !mutations.contains(packed);
        }

        synchronized void record(LevelChunk chunk, BlockPos pos) {
            if (blocked) return;
            int packed = pack(chunk, pos);
            if (packed >= 0 && mutations.contains(packed)) return;
            if (packed < 0 || mutations.size() >= MAX_MUTATIONS) {
                blocked = true;
                serialized = OVERFLOW;
                chunk.setAttached(WATCH, OVERFLOW);
                return;
            }
            mutations.add(packed);
            StringBuilder value = new StringBuilder("v1:");
            for (int item : mutations) {
                if (value.length() > 3) value.append(',');
                String hex = Integer.toHexString(item);
                value.append("0".repeat(5 - hex.length())).append(hex);
            }
            serialized = value.toString();
            chunk.setAttached(WATCH, serialized);
        }
    }
}
