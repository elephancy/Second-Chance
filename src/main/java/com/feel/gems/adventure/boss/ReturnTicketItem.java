package com.feel.gems.adventure.boss;

import com.feel.gems.particle.ModParticles;
import com.feel.gems.sound.ModSounds;
import com.feel.gems.util.GemsTeleport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Mark a position, then return to it within twenty seconds if it is still safe. */
public final class ReturnTicketItem extends Item {
    private static final String DIMENSION = "ReturnDim";
    private static final String X = "ReturnX";
    private static final String Y = "ReturnY";
    private static final String Z = "ReturnZ";
    private static final String UNTIL = "ReturnUntil";
    private static final long WINDOW_TICKS = 20 * 20;
    private static final int COOLDOWN_TICKS = 20 * 45;

    public ReturnTicketItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        if (!(world instanceof ServerLevel serverWorld)
                || !(user instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.SUCCESS;
        CustomData component = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag data = component == null ? new CompoundTag() : component.copyTag();
        String dimension = serverWorld.dimension().identifier().toString();
        long until = data.getLongOr(UNTIL, 0L);
        if (until <= serverWorld.getGameTime() || !dimension.equals(data.getStringOr(DIMENSION, ""))) {
            mark(serverWorld, player, stack);
            return InteractionResult.SUCCESS;
        }
        BlockPos destination = new BlockPos(data.getIntOr(X, 0), data.getIntOr(Y, 0), data.getIntOr(Z, 0));
        if (!safe(serverWorld, player, destination)) {
            player.sendSystemMessage(Component.literal("The return point is blocked. Find a clear space or wait to mark a new one.")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.SUCCESS;
        }
        Vec3 from = player.position().add(0, 1, 0);
        Vec3 to = Vec3.atBottomCenterOf(destination);
        if (GemsTeleport.teleport(player, serverWorld, to.x, to.y, to.z,
                player.getYRot(), player.getXRot())) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, ticket -> {
                ticket.remove(DIMENSION);
                ticket.remove(X);
                ticket.remove(Y);
                ticket.remove(Z);
                ticket.remove(UNTIL);
            });
            serverWorld.sendParticles(ModParticles.GEM_RING, from.x, from.y, from.z,
                    8, 0.3, 0.4, 0.3, 0.01);
            serverWorld.sendParticles(ModParticles.GEM_RING, to.x, to.y + 1, to.z,
                    8, 0.3, 0.4, 0.3, 0.01);
            serverWorld.playSound(null, to.x, to.y, to.z, ModSounds.PHASE_SHIFT,
                    SoundSource.PLAYERS, 0.8F, 0.8F);
            player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
            player.sendSystemMessage(Component.literal("Right on time.").withStyle(ChatFormatting.GOLD), true);
        }
        return InteractionResult.SUCCESS;
    }

    private static void mark(ServerLevel world, ServerPlayer player, ItemStack stack) {
        BlockPos here = player.blockPosition();
        if (!safe(world, player, here)) {
            player.sendSystemMessage(Component.literal("Stand on clear, solid ground to mark a return point.")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> {
            data.putString(DIMENSION, world.dimension().identifier().toString());
            data.putInt(X, here.getX());
            data.putInt(Y, here.getY());
            data.putInt(Z, here.getZ());
            data.putLong(UNTIL, world.getGameTime() + WINDOW_TICKS);
        });
        player.sendSystemMessage(Component.literal("Return point marked for 20 seconds. Use the ticket again to come back.")
                .withStyle(ChatFormatting.GOLD), true);
    }

    private static boolean safe(ServerLevel world, ServerPlayer player, BlockPos position) {
        if (!world.getWorldBorder().isWithinBounds(position)
                || !world.getBlockState(position.below()).isSolid()
                || !world.getBlockState(position).getCollisionShape(world, position).isEmpty()
                || !world.getBlockState(position.above()).getCollisionShape(world, position.above()).isEmpty()
                || !world.getFluidState(position).isEmpty()
                || !world.getFluidState(position.above()).isEmpty()) return false;
        Vec3 center = Vec3.atBottomCenterOf(position);
        return world.noCollision(player, player.getBoundingBox().move(
                center.x - player.getX(), center.y - player.getY(), center.z - player.getZ()));
    }
}
