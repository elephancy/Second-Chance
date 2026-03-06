package com.feel.gems.adventure;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A single-use aid harvested from a wet Reedrunner without harming it. */
public final class RiverwardPlumeItem extends Item {
    public RiverwardPlumeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        if (!(world instanceof ServerLevel level) || !(user instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = player.getItemInHand(hand);
        player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 12 * 20, 0));
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 5 * 20, 0));
        player.sendSystemMessage(Component.translatable("message.gems.item.riverward_plume.used"), true);
        level.playSound(null, player.blockPosition(), AdventureSounds.CREATURE_REED,
                SoundSource.PLAYERS, 0.45F, 1.13F);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.SUCCESS_SERVER;
    }
}
