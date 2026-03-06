package com.feel.gems.adventure.worldgen;

import com.feel.gems.mixin.accessor.ChunkMapSaveInvoker;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/** Saves a known, loaded chunk footprint through vanilla's chunk save path. */
public final class ScopedChunkSave {
    private ScopedChunkSave() { }

    public static int flushLoaded(ServerLevel level, Set<ChunkPos> footprint) {
        if (level.noSave() || footprint.isEmpty()) {
            throw new IllegalStateException("Cannot flush an empty or no-save chunk footprint");
        }
        List<ChunkPos> ordered = footprint.stream()
                .sorted(Comparator.comparingInt(ChunkPos::x).thenComparingInt(ChunkPos::z))
                .toList();
        // Validate the entire footprint before scheduling any writes. getChunkNow
        // does not load a missing chunk or generate terrain on this save path.
        List<LevelChunk> loaded = ordered.stream().map(pos -> {
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
            if (chunk == null) {
                throw new IllegalStateException("Guarded crossing chunk unloaded before save: " + pos);
            }
            return chunk;
        }).toList();

        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        ChunkMapSaveInvoker saver = (ChunkMapSaveInvoker) chunkMap;
        int scheduled = 0;
        for (LevelChunk chunk : loaded) {
            boolean wasUnsaved = chunk.isUnsaved();
            boolean saved = saver.gems$saveChunk(chunk);
            if (wasUnsaved && !saved) {
                throw new IllegalStateException("Vanilla rejected a dirty guarded crossing chunk save: "
                        + chunk.getPos());
            }
            if (saved) scheduled++;
        }
        // Vanilla's region worker waits for queued chunk writes and flushes
        // region files. Joining this barrier keeps the gate behind the deck.
        chunkMap.synchronize(true).join();
        return scheduled;
    }
}
