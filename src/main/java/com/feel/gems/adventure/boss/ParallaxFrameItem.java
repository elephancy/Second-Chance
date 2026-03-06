package com.feel.gems.adventure.boss;

import com.feel.gems.particle.ModParticles;
import com.feel.gems.sound.ModSounds;
import com.feel.gems.util.GemsTeleport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/** A Framefall trophy that makes a short, safe crossing to a visible surface. */
public final class ParallaxFrameItem extends Item {
    private static final double MAX_DISTANCE_SQUARED = 8.5 * 8.5;
    private static final int COOLDOWN_TICKS = 20 * 25;

    public ParallaxFrameItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player user = context.getPlayer();
        if (!(context.getLevel() instanceof ServerLevel world)
                || !(user instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.SUCCESS;
        BlockPos destination = context.getClickedPos().relative(context.getClickedFace());
        Vec3 center = Vec3.atBottomCenterOf(destination);
        boolean roomForPlayer = world.noCollision(player, player.getBoundingBox().move(
                center.x - player.getX(), center.y - player.getY(), center.z - player.getZ()));
        if (player.distanceToSqr(center) > MAX_DISTANCE_SQUARED
                || !roomForPlayer
                || !world.getBlockState(destination).getCollisionShape(world, destination).isEmpty()
                || !world.getBlockState(destination.above()).getCollisionShape(world, destination.above()).isEmpty()
                || !world.getFluidState(destination).isEmpty()
                || !world.getFluidState(destination.above()).isEmpty()
                || !world.getBlockState(destination.below()).isSolid()
                || !world.getWorldBorder().isWithinBounds(destination)) {
            player.sendSystemMessage(Component.literal("No safe landing inside that frame.")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.SUCCESS;
        }
        Vec3 departure = player.position().add(0.0, 1.0, 0.0);
        if (GemsTeleport.teleport(player, world, center.x, center.y, center.z,
                player.getYRot(), player.getXRot())) {
            world.sendParticles(ModParticles.GEM_RING, departure.x, departure.y, departure.z,
                    6, 0.2, 0.35, 0.2, 0.02);
            world.sendParticles(ModParticles.GEM_RING, center.x, center.y + 1.0, center.z,
                    6, 0.2, 0.35, 0.2, 0.02);
            world.playSound(null, center.x, center.y, center.z,
                    ModSounds.PHASE_SHIFT, SoundSource.PLAYERS, 0.7F, 1.05F);
            player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
            player.sendSystemMessage(Component.literal("The angle opens.").withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResult.SUCCESS;
    }
}
