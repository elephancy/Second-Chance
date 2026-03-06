package com.feel.gems.adventure;

import com.feel.gems.adventure.nether.NetherChapterState;
import com.feel.gems.particle.ModParticles;
import com.feel.gems.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Calls a visible, time-limited team rally without moving or shielding anyone. */
public final class DispatchBellItem extends Item {
    private static final int HEAR_RANGE = 128;
    private static final int BRIDGE_HEAR_RANGE = 256;

    public DispatchBellItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        if (!(world instanceof ServerLevel level) || !(user instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.SUCCESS_SERVER;
        if (level.dimension() != Level.OVERWORLD || player.isSpectator()) {
            tell(player, "The Meeting Bell works in the Overworld.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!CampaignPersistentState.get(level.getServer()).model().worldChanged(LandmarkKey.REDLINE)) {
            tell(player, "Clear Redline to use the Meeting Bell.");
            return InteractionResult.SUCCESS_SERVER;
        }
        RescueDispatchState state = RescueDispatchState.get(level.getServer());
        boolean bridgeRestored = NetherChapterState.get(level.getServer()).bridgeCleared();
        int hearRange = bridgeRestored ? BRIDGE_HEAR_RANGE : HEAR_RANGE;
        int minutes = bridgeRestored ? 5 : 3;
        long now = level.getGameTime();
        if (player.isShiftKeyDown()) {
            BlockPos here = player.blockPosition();
            if (!TownhallSafeGround.clear(level, player, here)) {
                tell(player, "Stand on clear, solid ground to call a rally.");
                return InteractionResult.SUCCESS_SERVER;
            }
            if (!state.call(here, now, 20L * 60 * minutes)) {
                tell(player, "A rescue rally is already active for "
                        + ((state.remaining(now) + 19) / 20) + " more seconds.");
                return InteractionResult.SUCCESS_SERVER;
            }
            for (ServerPlayer nearby : level.players()) {
                if (nearby.isSpectator() || nearby.distanceToSqr(player) > hearRange * hearRange) continue;
                if (nearby == player) {
                    tell(nearby, "Rescue rally called here for " + minutes + " minutes.");
                } else {
                    long dx = (long) here.getX() - nearby.getBlockX();
                    long dz = (long) here.getZ() - nearby.getBlockZ();
                    tell(nearby, player.getName().getString() + " calls a rescue rally "
                            + RumorAtlasItem.direction(dx, dz) + ", about "
                            + (int) Math.round(Math.hypot(dx, dz) / 10.0) * 10 + " blocks away.");
                }
            }
            level.sendParticles(ModParticles.GEM_RING, player.getX(), player.getY() + 1,
                    player.getZ(), 18, 0.5, 0.7, 0.5, 0.03);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.PHASE_SHIFT, SoundSource.PLAYERS, 0.7F, 0.8F);
            player.getCooldowns().addCooldown(stack, 20 * 10);
            return InteractionResult.SUCCESS_SERVER;
        }
        BlockPos point = state.activePoint(now);
        if (point == null) {
            tell(player, "No rescue rally is active. Sneak-use on clear ground to call one.");
        } else {
            long dx = (long) point.getX() - player.getBlockX();
            long dz = (long) point.getZ() - player.getBlockZ();
            tell(player, "Rescue rally: " + RumorAtlasItem.direction(dx, dz) + ", about "
                    + (int) Math.round(Math.hypot(dx, dz) / 10.0) * 10 + " blocks; "
                    + ((state.remaining(now) + 19) / 20) + " seconds left.");
        }
        player.getCooldowns().addCooldown(stack, 20);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.GOLD), true);
    }
}
