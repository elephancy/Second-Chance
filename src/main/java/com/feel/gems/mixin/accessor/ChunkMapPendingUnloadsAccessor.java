package com.feel.gems.mixin.accessor;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Server-thread guard while an Atlas disk scan is deciding if a chunk is new. */
@Mixin(ChunkMap.class)
public interface ChunkMapPendingUnloadsAccessor {
    @Accessor("pendingUnloads")
    Long2ObjectLinkedOpenHashMap<ChunkHolder> gems$pendingUnloads();
}
