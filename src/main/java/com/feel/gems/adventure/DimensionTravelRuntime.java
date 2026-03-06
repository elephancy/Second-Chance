package com.feel.gems.adventure;

import com.feel.gems.adventure.nether.NetherChapterBlocks;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** A return interaction remains available even while a chapter gate is sealed. */
public final class DimensionTravelRuntime {
    private static boolean registered;

    private DimensionTravelRuntime() { }

    /** Register before chapter controls so sneak-use is an unambiguous return. */
    public static void register() {
        if (registered) return;
        registered = true;
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            Block block = world.getBlockState(hit.getBlockPos()).getBlock();
            boolean gate = block == NetherChapterBlocks.ANNEX_GATE
                    || block == NetherChapterBlocks.BRIDGE_GATE;
            boolean returnMark = block == NetherChapterBlocks.ANNEX_BOARD_RETURN
                    || block == NetherChapterBlocks.BRIDGE_EAST_SHELTER;
            if (!gate && !(returnMark && player.isShiftKeyDown())) {
                return InteractionResult.PASS;
            }
            if (!(world instanceof ServerLevel level)
                    || !(player instanceof ServerPlayer visitor)) {
                return InteractionResult.SUCCESS;
            }
            if (level.dimension() != Level.NETHER) {
                return InteractionResult.SUCCESS_SERVER;
            }
            DimensionReturnService.tryReturn(visitor, null);
            return InteractionResult.SUCCESS_SERVER;
        });
    }
}
