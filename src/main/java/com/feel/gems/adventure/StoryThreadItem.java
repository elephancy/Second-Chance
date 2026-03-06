package com.feel.gems.adventure;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

/** On-demand, bounded search for one nearby stash; it never scans every tick. */
public final class StoryThreadItem extends Item {
    private static final int RADIUS = 18;
    private static final int VERTICAL = 6;

    public StoryThreadItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        if (!(world instanceof ServerLevel level) || !(user instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        if (!YehiaStoryState.get(level.getServer()).completed()) {
            player.sendSystemMessage(Component.literal("Finish Yehia's garden tale before the thread will guide you."));
            return InteractionResult.SUCCESS_SERVER;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) {
            player.sendSystemMessage(Component.literal("The thread needs a moment before another story."));
            return InteractionResult.SUCCESS_SERVER;
        }
        BlockPos origin = player.blockPosition();
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        BlockPos nearest = null;
        long nearestDistance = Long.MAX_VALUE;
        for (int x = origin.getX() - RADIUS; x <= origin.getX() + RADIUS; x++) {
            for (int z = origin.getZ() - RADIUS; z <= origin.getZ() + RADIUS; z++) {
                for (int y = origin.getY() - VERTICAL; y <= origin.getY() + VERTICAL; y++) {
                    probe.set(x, y, z);
                    if (!level.hasChunkAt(probe)) continue;
                    var block = level.getBlockState(probe);
                    if (!block.is(Blocks.CHEST) && !block.is(Blocks.TRAPPED_CHEST)
                            && !block.is(Blocks.BARREL)) continue;
                    if (!(level.getBlockEntity(probe) instanceof RandomizableContainerBlockEntity stash)
                            || stash.getLootTable() == null) continue;
                    long dx = (long) x - origin.getX();
                    long dy = (long) y - origin.getY();
                    long dz = (long) z - origin.getZ();
                    long distance = dx * dx + dy * dy + dz * dz;
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearest = probe.immutable();
                    }
                }
            }
        }
        if (nearest == null) {
            player.sendSystemMessage(Component.literal("No unopened stash catches the thread nearby. Try it inside a landmark."));
        } else {
            long dx = (long) nearest.getX() - origin.getX();
            long dz = (long) nearest.getZ() - origin.getZ();
            String height = nearest.getY() > origin.getY() + 2 ? " above you"
                    : nearest.getY() < origin.getY() - 2 ? " below you" : "";
            player.sendSystemMessage(Component.literal("A stash lies "
                    + RumorAtlasItem.direction(dx, dz) + height + ", about "
                    + Math.round(Math.sqrt(nearestDistance)) + " blocks away."));
        }
        player.getCooldowns().addCooldown(stack, 20 * 30);
        return InteractionResult.SUCCESS_SERVER;
    }
}
