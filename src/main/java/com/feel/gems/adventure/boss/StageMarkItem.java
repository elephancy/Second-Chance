package com.feel.gems.adventure.boss;

import java.util.Comparator;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;

/** Choose the next move in an Unwritten encounter, including a later rematch. */
public final class StageMarkItem extends Item {
    public StageMarkItem(Properties settings) {
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
        CadenzaPattern.Act[] acts = CadenzaPattern.Act.values();
        int index = Math.floorMod(data.getIntOr("ChosenAct", 0), acts.length);
        if (player.isShiftKeyDown()) {
            CadenzaPattern.Act next = acts[index].next();
            CustomData.update(DataComponents.CUSTOM_DATA, stack,
                    tag -> tag.putInt("ChosenAct", next.ordinal()));
            player.sendSystemMessage(Component.literal("Stage Mark: " + next.label + ". " + next.hint)
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            player.getCooldowns().addCooldown(stack, 8);
            return InteractionResult.SUCCESS_SERVER;
        }
        StrayCadenzaEntity cadenza = serverWorld.getEntitiesOfClass(StrayCadenzaEntity.class,
                new AABB(player.blockPosition()).inflate(24), StrayCadenzaEntity::isAlive).stream()
                .min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
        if (cadenza == null || !cadenza.requestAct(player, acts[index])) {
            player.sendSystemMessage(Component.literal("No active stage nearby. Choose a move for a later bout.")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.SUCCESS_SERVER;
        }
        player.getCooldowns().addCooldown(stack, 20 * 15);
        return InteractionResult.SUCCESS_SERVER;
    }
}
