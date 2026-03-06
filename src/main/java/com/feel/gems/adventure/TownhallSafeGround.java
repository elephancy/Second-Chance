package com.feel.gems.adventure;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Checks a destination without loading a chunk or accepting obvious hazards. */
final class TownhallSafeGround {
    private TownhallSafeGround() {
    }

    static boolean clear(ServerLevel level, ServerPlayer player, BlockPos feet) {
        BlockPos below = feet.below();
        BlockPos head = feet.above();
        if (!level.hasChunkAt(below) || !level.hasChunkAt(feet) || !level.hasChunkAt(head)
                || !level.getWorldBorder().isWithinBounds(feet)) return false;
        var support = level.getBlockState(below);
        var floor = level.getBlockState(feet);
        var ceiling = level.getBlockState(head);
        if (support.is(Blocks.MAGMA_BLOCK) || support.is(Blocks.CAMPFIRE)
                || support.is(Blocks.SOUL_CAMPFIRE) || support.is(Blocks.CACTUS)
                || floor.is(Blocks.FIRE) || floor.is(Blocks.SOUL_FIRE)
                || floor.is(Blocks.COBWEB) || floor.is(Blocks.SWEET_BERRY_BUSH)
                || ceiling.is(Blocks.FIRE) || ceiling.is(Blocks.SOUL_FIRE)
                || support.getCollisionShape(level, below).isEmpty()
                || !floor.getCollisionShape(level, feet).isEmpty()
                || !ceiling.getCollisionShape(level, head).isEmpty()
                || !level.getFluidState(feet).isEmpty()
                || !level.getFluidState(head).isEmpty()) return false;
        Vec3 center = Vec3.atBottomCenterOf(feet);
        return level.noCollision(player, player.getBoundingBox().move(
                center.x - player.getX(), center.y - player.getY(), center.z - player.getZ()));
    }

    static boolean within(BlockPos from, BlockPos to, int horizontal, int vertical) {
        long dx = (long) from.getX() - to.getX();
        long dz = (long) from.getZ() - to.getZ();
        return dx * dx + dz * dz <= (long) horizontal * horizontal
                && Math.abs((long) from.getY() - to.getY()) <= vertical;
    }
}
