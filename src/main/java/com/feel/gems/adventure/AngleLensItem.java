package com.feel.gems.adventure;

import com.feel.gems.GemsMod;
import com.feel.gems.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;

/** A reusable shared route mark, grounded by a player's real arrival. */
public final class AngleLensItem extends Item {
    private static final String TARGET = "second_chance_angle_target";
    private static final int MARK_RADIUS_CHUNKS = 8;

    public AngleLensItem(Properties properties) {
        super(properties);
    }

    static LandmarkKey target(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return LandmarkKey.values()[Math.floorMod(tag.getIntOr(TARGET, 0), LandmarkKey.values().length)];
    }

    static void setTarget(ItemStack stack, LandmarkKey target) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                tag -> tag.putInt(TARGET, target.ordinal()));
    }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        if (!(world instanceof ServerLevel level) || !(user instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        if (level.dimension() != Level.OVERWORLD) {
            tell(player, "The Angle Lens tracks Overworld expeditions.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!CampaignPersistentState.get(level.getServer()).model().worldChanged(LandmarkKey.FRAMEFALL)) {
            tell(player, "Clear Framefall before using the Angle Lens.");
            return InteractionResult.SUCCESS_SERVER;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.SUCCESS_SERVER;
        LandmarkKey target = target(stack);
        if (player.isShiftKeyDown()) {
            return mark(level, player, stack, target);
        }
        BlockPos point = AngleApproachState.get(level.getServer()).approach(target);
        if (point == null) {
            tell(player, "No approach is marked for " + target.label()
                    + ". Stand near its landmark and sneak-use the lens on clear ground.");
            return InteractionResult.SUCCESS_SERVER;
        }
        long dx = (long) point.getX() - player.getBlockX();
        long dz = (long) point.getZ() - player.getBlockZ();
        int distance = (int) Math.round(Math.hypot(dx, dz) / 10.0) * 10;
        tell(player, target.label() + " approach: " + RumorAtlasItem.direction(dx, dz)
                + ", about " + distance + " blocks away. Check the route as you travel.");
        return InteractionResult.SUCCESS_SERVER;
    }

    private static InteractionResult mark(ServerLevel level, ServerPlayer player,
                                          ItemStack stack, LandmarkKey target) {
        BlockPos here = player.blockPosition();
        if (!TownhallSafeGround.clear(level, player, here)) {
            tell(player, "Stand on solid, open, dry ground before marking an approach.");
            return InteractionResult.SUCCESS_SERVER;
        }
        // Structure lookup happens only on this explicit action, at most once a second per lens.
        player.getCooldowns().addCooldown(stack, 20);
        TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE,
                Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "atlas_target/" + target.id()));
        BlockPos found = level.findNearestMapStructure(tag, here, MARK_RADIUS_CHUNKS, false);
        if (found == null || !level.hasChunkAt(found)
                || !TownhallSafeGround.within(here, found, 96, 48)) {
            tell(player, "Move within 96 blocks of " + target.label()
                    + " and keep its landmark loaded before marking the approach.");
            return InteractionResult.SUCCESS_SERVER;
        }
        boolean changed = AngleApproachState.get(level.getServer()).mark(target, here);
        if (changed) {
            level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                    player.getName().getString() + " marked a clear approach to " + target.label()
                            + ". Set an Angle Lens to that place to find it."), false);
            level.playSound(null, here, ModSounds.ARCANE_CHIME, SoundSource.PLAYERS, 0.55F, 1.25F);
        } else {
            tell(player, "This approach is already marked for " + target.label() + ".");
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), true);
    }
}
