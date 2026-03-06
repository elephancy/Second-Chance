package com.feel.gems.adventure;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Carry Adl's shared route marker into the field. */
public final class AtlasPinItem extends Item {
    public AtlasPinItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        if (!(world instanceof ServerLevel level) || !(user instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        return AdlSurveyRuntime.usePin(level, player);
    }
}
