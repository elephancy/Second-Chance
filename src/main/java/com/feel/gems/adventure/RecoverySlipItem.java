package com.feel.gems.adventure;

import com.feel.gems.adventure.AdventureSounds;
import java.util.Comparator;
import java.util.UUID;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Omar's transferable receipt leads helpers to a real last fall and nearby loose items. */
public final class RecoverySlipItem extends Item {
    private static final String OWNER = "second_chance_recovery_owner";
    private static final DustParticleOptions PAPER = new DustParticleOptions(0xF1D7A8, 1.1F);

    public RecoverySlipItem(Properties properties) {
        super(properties);
    }

    static void bind(ItemStack stack, UUID owner) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                data -> data.putString(OWNER, owner.toString()));
    }

    static boolean belongsTo(ItemStack stack, UUID owner) {
        return owner.equals(owner(stack));
    }

    private static UUID owner(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String value = data.getStringOr(OWNER, "");
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        if (!(world instanceof ServerLevel level) || !(user instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!FriendDomainUnlockState.get(level.getServer()).cleared("omar")) {
            tell(player, "Find Omar's missing compass before using a Recovery Slip.");
            return InteractionResult.SUCCESS_SERVER;
        }
        UUID owner = owner(stack);
        if (owner == null) {
            tell(player, "This slip has no owner. Claim one at Omar's townhall board.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.SUCCESS_SERVER;
        player.getCooldowns().addCooldown(stack, 20);
        OmarRecoveryState.Trace trace = OmarRecoveryState.get(level.getServer()).trace(owner);
        if (trace == null) {
            tell(player, "No fall is recorded for this slip yet.");
            return InteractionResult.SUCCESS_SERVER;
        }
        OmarRecoveryPlan.Range range = OmarRecoveryPlan.range(level.dimension().identifier(),
                player.blockPosition(), trace);
        if (range == OmarRecoveryPlan.Range.OTHER_DIMENSION) {
            tell(player, "The last fall was in another dimension. The slip is quiet here.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (range == OmarRecoveryPlan.Range.FAR) {
            tell(player, "The fall is too distant. Search within 1,024 blocks.");
            return InteractionResult.SUCCESS_SERVER;
        }
        BlockPos fall = trace.pos();
        long dx = (long) fall.getX() - player.getBlockX();
        long dz = (long) fall.getZ() - player.getBlockZ();
        if (range == OmarRecoveryPlan.Range.BEARING) {
            int distance = (int) Math.round(Math.hypot(dx, dz) / 10.0) * 10;
            tell(player, "The last fall lies " + RumorAtlasItem.direction(dx, dz)
                    + ", about " + distance + " blocks away. Hand this slip to a friend to help search.");
        } else {
            sweep(level, player, fall);
        }
        level.playSound(null, player.blockPosition(), AdventureSounds.STORY_PAGE_TURN,
                SoundSource.PLAYERS, 0.45F, 0.9F);
        player.getCooldowns().addCooldown(stack, 20 * 4);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static void sweep(ServerLevel level, ServerPlayer player, BlockPos fall) {
        if (!level.hasChunkAt(fall)) {
            tell(player, "You are close, but the fall site is not loaded yet. Move toward it.");
            return;
        }
        ItemEntity nearest = level.getEntitiesOfClass(ItemEntity.class,
                        new AABB(fall).inflate(12), item -> item.isAlive() && !item.getItem().isEmpty())
                .stream().min(Comparator.comparingDouble(item -> item.distanceToSqr(player))).orElse(null);
        if (nearest == null) {
            tell(player, "The last fall is " + heightHint(fall.getY() - player.getBlockY())
                    + ". No loose item is nearby now; it may have moved or vanished.");
            return;
        }
        BlockPos item = nearest.blockPosition();
        long dx = (long) item.getX() - player.getBlockX();
        long dz = (long) item.getZ() - player.getBlockZ();
        tell(player, "A loose item is " + (Math.abs(dx) + Math.abs(dz) < 3
                ? "very close" : RumorAtlasItem.direction(dx, dz) + " of you")
                + " and " + heightHint(item.getY() - player.getBlockY())
                + ". It may not be the missing gear.");
        level.sendParticles(PAPER, nearest.getX(), nearest.getY() + 0.5, nearest.getZ(),
                12, 0.4, 0.35, 0.4, 0.01);
    }

    private static String heightHint(int difference) {
        if (difference > 3) return "above you";
        if (difference < -3) return "below you";
        return "near your height";
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.GOLD), false);
    }
}
