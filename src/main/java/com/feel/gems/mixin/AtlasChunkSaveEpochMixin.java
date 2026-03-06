package com.feel.gems.mixin;

import com.feel.gems.adventure.ChunkMapSaveEpoch;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Advances before a chunk enters unloading or crosses the save boundary. */
@Mixin(ChunkMap.class)
public abstract class AtlasChunkSaveEpochMixin implements ChunkMapSaveEpoch {
    @Unique private long gems$atlasSaveEpoch;
    @Unique private long gems$atlasEvictionEpoch;
    @Unique private final Map<Long, Long> gems$atlasChangedChunks = new LinkedHashMap<>();

    @Inject(method = "save", at = @At("HEAD"))
    private void gems$markAtlasSave(ChunkAccess chunk,
            CallbackInfoReturnable<Boolean> callback) {
        gems$markAtlasChange(chunk.getPos().pack());
    }

    @Inject(method = "scheduleUnload", at = @At("HEAD"))
    private void gems$markAtlasUnload(long position, ChunkHolder holder,
            CallbackInfo callback) {
        gems$markAtlasChange(position);
    }

    @Unique
    private void gems$markAtlasChange(long position) {
        long epoch = ++gems$atlasSaveEpoch;
        gems$atlasChangedChunks.remove(position);
        gems$atlasChangedChunks.put(position, epoch);
        if (gems$atlasChangedChunks.size() > 512) {
            Iterator<Map.Entry<Long, Long>> entries = gems$atlasChangedChunks.entrySet().iterator();
            Map.Entry<Long, Long> eldest = entries.next();
            gems$atlasEvictionEpoch = eldest.getValue();
            entries.remove();
        }
    }

    @Override
    public long gems$saveEpoch() {
        return gems$atlasSaveEpoch;
    }

    @Override
    public boolean gems$changedSince(long chunk, long epoch) {
        Long changed = gems$atlasChangedChunks.get(chunk);
        return epoch < 0 || gems$atlasEvictionEpoch > epoch
                || changed != null && changed > epoch;
    }
}
