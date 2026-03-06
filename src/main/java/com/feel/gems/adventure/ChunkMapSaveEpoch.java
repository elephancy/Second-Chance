package com.feel.gems.adventure;

/** Server-thread record of chunk save and unload boundaries. */
public interface ChunkMapSaveEpoch {
    long gems$saveEpoch();
    boolean gems$changedSince(long chunk, long epoch);
}
