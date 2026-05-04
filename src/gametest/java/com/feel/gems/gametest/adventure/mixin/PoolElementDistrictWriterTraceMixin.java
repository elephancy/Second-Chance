package com.feel.gems.gametest.adventure.mixin;

import com.feel.gems.gametest.adventure.trace.DistrictWriterTrace;
import java.util.ArrayDeque;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Names the exact staged template and piece box while its blocks are written. */
@Mixin(PoolElementStructurePiece.class)
public abstract class PoolElementDistrictWriterTraceMixin {
    @Unique
    private static final ThreadLocal<ArrayDeque<Boolean>> GEMS_PIECE_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = "postProcess", at = @At("HEAD"))
    private void gems$beforeDistrictPiece(WorldGenLevel world, StructureManager structures,
            ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
            ChunkPos chunk, BlockPos pivot, CallbackInfo callback) {
        if (!DistrictWriterTrace.enabled()) return;
        PoolElementStructurePiece piece = (PoolElementStructurePiece) (Object) this;
        boolean tracked = piece.getElement() instanceof SinglePoolElement single
                && DistrictWriterTrace.beginPiece(single.getTemplateLocation().toString(),
                        piece.getBoundingBox());
        GEMS_PIECE_STACK.get().push(tracked);
    }

    @Inject(method = "postProcess", at = @At("RETURN"))
    private void gems$afterDistrictPiece(WorldGenLevel world, StructureManager structures,
            ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
            ChunkPos chunk, BlockPos pivot, CallbackInfo callback) {
        if (!DistrictWriterTrace.enabled()) return;
        ArrayDeque<Boolean> stack = GEMS_PIECE_STACK.get();
        if (stack.pop()) DistrictWriterTrace.endPiece();
        if (stack.isEmpty()) GEMS_PIECE_STACK.remove();
    }
}
