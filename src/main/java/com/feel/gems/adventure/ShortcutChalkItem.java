package com.feel.gems.adventure;

import com.feel.gems.sound.ModSounds;
import com.feel.gems.util.GemsTeleport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.level.block.Blocks;

/** Two private, local route marks stored on the chalk itself. */
public final class ShortcutChalkItem extends Item {
    private static final String FIRST = "second_chance_first_mark";
    private static final String SECOND = "second_chance_second_mark";
    private static final DustParticleOptions DUST = new DustParticleOptions(0x9ACED9, 1.1F);

    public ShortcutChalkItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        if (!(world instanceof ServerLevel level) || !(user instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        if (level.dimension() != Level.OVERWORLD) {
            tell(player, "Shortcut Chalk works in the Overworld.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!FriendDomainUnlockState.get(level.getServer()).cleared("ali")) {
            tell(player, "Find Ali's missing door before setting a shortcut.");
            return InteractionResult.SUCCESS_SERVER;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.SUCCESS_SERVER;
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        BlockPos first = read(data, FIRST);
        BlockPos second = read(data, SECOND);
        BlockPos here = player.blockPosition();
        if (player.isShiftKeyDown()) {
            if (!safe(level, here)) {
                tell(player, "Stand on a solid, open, dry spot to mark this route.");
                return InteractionResult.SUCCESS_SERVER;
            }
            if (first == null || second != null) {
                CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                    tag.putLong(FIRST, here.asLong());
                    tag.remove(SECOND);
                });
                tell(player, second == null ? "First mark set. Sneak-use again nearby for the second."
                        : "New first mark set. Sneak-use again nearby for the second.");
            } else if (!ShortcutRoute.inRange(first, here)) {
                tell(player, "The second mark must be within 128 blocks and 32 blocks of height.");
                return InteractionResult.SUCCESS_SERVER;
            } else if (ShortcutRoute.near(first, here)) {
                tell(player, "Move at least five blocks away before setting the second mark.");
                return InteractionResult.SUCCESS_SERVER;
            } else {
                CustomData.update(DataComponents.CUSTOM_DATA, stack,
                        tag -> tag.putLong(SECOND, here.asLong()));
                tell(player, "Shortcut ready. Use the chalk beside either mark to travel to the other.");
            }
            dust(level, here);
            level.playSound(null, here, ModSounds.ARCANE_CHIME, SoundSource.PLAYERS, 0.5F, 1.4F);
            player.getCooldowns().addCooldown(stack, 20);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (first == null || second == null) {
            tell(player, "Sneak-use the chalk at two safe places to make a shortcut.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!ShortcutRoute.inRange(first, second)) {
            tell(player, "These marks no longer form a local route. Sneak-use to start a new pair.");
            return InteractionResult.SUCCESS_SERVER;
        }
        BlockPos destination = ShortcutRoute.near(here, first) ? second
                : ShortcutRoute.near(here, second) ? first : null;
        if (destination == null) {
            tell(player, "Stand within four blocks of one of your two marks.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!safe(level, destination)) {
            tell(player, "The other mark is blocked or unloaded. Clear it or visit that area first.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!GemsTeleport.teleport(player, level, destination.getX() + 0.5,
                destination.getY(), destination.getZ() + 0.5, player.getYRot(), player.getXRot())) {
            tell(player, "The route could not open. Try again from the mark.");
            return InteractionResult.SUCCESS_SERVER;
        }
        dust(level, destination);
        level.playSound(null, destination, ModSounds.PHASE_SHIFT, SoundSource.PLAYERS, 0.7F, 1.2F);
        player.getCooldowns().addCooldown(stack, 20 * 5);
        tell(player, "You took your shortcut.");
        AliRouteRuntime.onShortcutTravel(level, player, here, destination);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static boolean safe(ServerLevel level, BlockPos feet) {
        BlockPos below = feet.below();
        BlockPos head = feet.above();
        if (!level.hasChunkAt(below) || !level.hasChunkAt(head)) return false;
        var support = level.getBlockState(below);
        var floor = level.getBlockState(feet);
        return !support.is(Blocks.MAGMA_BLOCK)
                && !support.is(Blocks.CAMPFIRE)
                && !support.is(Blocks.SOUL_CAMPFIRE)
                && !floor.is(Blocks.FIRE)
                && !floor.is(Blocks.SOUL_FIRE)
                && level.getWorldBorder().isWithinBounds(feet)
                && !support.getCollisionShape(level, below).isEmpty()
                && floor.getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(head).getCollisionShape(level, head).isEmpty()
                && level.getFluidState(feet).isEmpty()
                && level.getFluidState(head).isEmpty();
    }

    private static BlockPos read(CompoundTag tag, String key) {
        return tag.contains(key) ? BlockPos.of(tag.getLongOr(key, 0L)) : null;
    }

    private static void dust(ServerLevel level, BlockPos pos) {
        level.sendParticles(DUST, pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5,
                16, 0.45, 0.15, 0.45, 0.01);
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.AQUA), true);
    }
}
