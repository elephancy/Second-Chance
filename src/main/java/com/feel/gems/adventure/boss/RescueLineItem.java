package com.feel.gems.adventure.boss;

import com.feel.gems.particle.ModParticles;
import com.feel.gems.sound.ModSounds;
import java.util.Comparator;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

/** Point at a friend to share a short shield; use with no friend in sight for yourself. */
public final class RescueLineItem extends Item {
    private static final int RANGE = 12;
    private static final int COOLDOWN = 35 * 20;

    public RescueLineItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        if (!(world instanceof ServerLevel serverWorld)
                || !(user instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.SUCCESS;

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        ServerPlayer ally = serverWorld.getEntitiesOfClass(ServerPlayer.class,
                new AABB(player.blockPosition()).inflate(RANGE),
                candidate -> candidate != player && candidate.isAlive() && !candidate.isSpectator()
                        && player.hasLineOfSight(candidate)
                        && candidate.distanceToSqr(player) <= RANGE * RANGE
                        && candidate.getEyePosition().subtract(eye).normalize().dot(look) >= 0.85)
                .stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);

        if (ally == null) {
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 6 * 20, 0));
            player.sendSystemMessage(Component.literal("A shelter for yourself.")
                    .withStyle(ChatFormatting.GOLD), true);
            serverWorld.sendParticles(ModParticles.GEM_RING, player.getX(), player.getY() + 1,
                    player.getZ(), 12, 0.5, 0.4, 0.5, 0.01);
        } else {
            ally.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 8 * 20, 0));
            ally.addEffect(new MobEffectInstance(MobEffects.SPEED, 4 * 20, 0));
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 4 * 20, 0));
            Vec3 end = ally.getEyePosition();
            for (int step = 1; step <= 10; step++) {
                Vec3 point = eye.lerp(end, step / 11.0);
                serverWorld.sendParticles(ModParticles.GEM_RING, point.x, point.y, point.z,
                        1, 0.02, 0.02, 0.02, 0);
            }
            player.sendSystemMessage(Component.literal("You sheltered " + ally.getName().getString() + ".")
                    .withStyle(ChatFormatting.GOLD), true);
            ally.sendSystemMessage(Component.literal(player.getName().getString() + " has your back.")
                    .withStyle(ChatFormatting.GOLD), true);
        }
        serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.PHASE_SHIFT, SoundSource.PLAYERS, 0.8F, 1.1F);
        player.getCooldowns().addCooldown(stack, COOLDOWN);
        return InteractionResult.SUCCESS_SERVER;
    }
}
