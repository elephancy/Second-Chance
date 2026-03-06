package com.feel.gems.adventure.boss;

import com.feel.gems.particle.ModParticles;
import com.feel.gems.sound.ModSounds;
import com.feel.gems.util.GemsTeleport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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

/** Record a short walk and safely replay that relative move from somewhere new. */
public final class RehearsalMarkItem extends Item {
    private static final int RECORD_WINDOW = 20 * 10;
    private static final int COOLDOWN = 20 * 25;

    public RehearsalMarkItem(Properties settings) {
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
        boolean ready = data.getBooleanOr("Ready", false);
        boolean recording = data.getBooleanOr("Recording", false);
        if (player.isShiftKeyDown() || (!ready && !recording)) {
            begin(serverWorld, player, stack);
        } else if (recording) {
            finish(serverWorld, player, stack, data);
        } else {
            replay(serverWorld, player, stack, data);
        }
        return InteractionResult.SUCCESS;
    }

    private static void begin(ServerLevel world, ServerPlayer player, ItemStack stack) {
        BlockPos origin = player.blockPosition();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> {
            data.putBoolean("Recording", true);
            data.putBoolean("Ready", false);
            data.putString("RecordDim", world.dimension().identifier().toString());
            data.putLong("RecordUntil", world.getGameTime() + RECORD_WINDOW);
            data.putInt("RecordX", origin.getX());
            data.putInt("RecordY", origin.getY());
            data.putInt("RecordZ", origin.getZ());
        });
        player.sendSystemMessage(Component.literal("Walk 2 to 8 blocks, then use the mark again to save that move.")
                .withStyle(ChatFormatting.GOLD), true);
    }

    private static void finish(ServerLevel world, ServerPlayer player,
                               ItemStack stack, CompoundTag data) {
        if (world.getGameTime() > data.getLongOr("RecordUntil", 0)
                || !world.dimension().identifier().toString()
                .equals(data.getStringOr("RecordDim", ""))) {
            begin(world, player, stack);
            return;
        }
        BlockPos here = player.blockPosition();
        int dx = here.getX() - data.getIntOr("RecordX", here.getX());
        int dy = here.getY() - data.getIntOr("RecordY", here.getY());
        int dz = here.getZ() - data.getIntOr("RecordZ", here.getZ());
        int distanceSquared = dx * dx + dy * dy + dz * dz;
        if (distanceSquared < 4 || distanceSquared > 64 || Math.abs(dy) > 3) {
            player.sendSystemMessage(Component.literal("That move needs 2 to 8 blocks of travel and no steep drop.")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putBoolean("Recording", false);
            tag.putBoolean("Ready", true);
            tag.remove("RecordDim");
            tag.remove("RecordUntil");
            tag.remove("RecordX");
            tag.remove("RecordY");
            tag.remove("RecordZ");
            tag.putInt("MoveX", dx);
            tag.putInt("MoveY", dy);
            tag.putInt("MoveZ", dz);
        });
        player.sendSystemMessage(Component.literal("Move saved. Use the mark again to repeat it from a new spot.")
                .withStyle(ChatFormatting.GOLD), true);
    }

    private static void replay(ServerLevel world, ServerPlayer player,
                               ItemStack stack, CompoundTag data) {
        int dx = data.getIntOr("MoveX", 0);
        int dy = data.getIntOr("MoveY", 0);
        int dz = data.getIntOr("MoveZ", 0);
        int lengthSquared = dx * dx + dy * dy + dz * dz;
        if (lengthSquared < 4 || lengthSquared > 64 || Math.abs(dy) > 3) {
            begin(world, player, stack);
            return;
        }
        BlockPos destination = player.blockPosition().offset(dx, dy, dz);
        Vec3 to = Vec3.atBottomCenterOf(destination);
        Vec3 from = player.position();
        if (!safe(world, player, destination) || !clearPath(world, player, from, to)) {
            player.sendSystemMessage(Component.literal("That move is blocked here. Try it from a clear path.")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (GemsTeleport.teleport(player, world, to.x, to.y, to.z,
                player.getYRot(), player.getXRot())) {
            world.sendParticles(ModParticles.GEM_RING, from.x, from.y + 1, from.z,
                    8, 0.25, 0.4, 0.25, 0.01);
            world.sendParticles(ModParticles.GEM_RING, to.x, to.y + 1, to.z,
                    8, 0.25, 0.4, 0.25, 0.01);
            world.playSound(null, to.x, to.y, to.z, ModSounds.PHASE_SHIFT,
                    SoundSource.PLAYERS, 0.75F, 1.25F);
            player.getCooldowns().addCooldown(stack, COOLDOWN);
            player.sendSystemMessage(Component.literal("Take it from the top.").withStyle(ChatFormatting.GOLD), true);
        }
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

    private static boolean clearPath(ServerLevel world, ServerPlayer player,
                                     Vec3 from, Vec3 to) {
        double distance = from.distanceTo(to);
        int steps = Math.max(1, (int) Math.ceil(distance * 2));
        for (int i = 1; i <= steps; i++) {
            Vec3 point = from.lerp(to, (double) i / steps);
            if (!world.noCollision(player, player.getBoundingBox().move(
                    point.x - from.x, point.y - from.y, point.z - from.z))) return false;
        }
        return true;
    }
}
