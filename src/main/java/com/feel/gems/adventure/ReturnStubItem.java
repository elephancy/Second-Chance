package com.feel.gems.adventure;

import com.feel.gems.adventure.nether.NetherChapterState;
import com.feel.gems.particle.ModParticles;
import com.feel.gems.sound.ModSounds;
import com.feel.gems.util.GemsTeleport;
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
import net.minecraft.world.phys.Vec3;

/** Short, shared group return with a loaded and clear destination. */
public final class ReturnStubItem extends Item {
    private static final int RANGE = 128;
    private static final int ANNEX_RANGE = 256;
    private static final int HEIGHT_RANGE = 32;
    private static final int PERSONAL_COOLDOWN = 20 * 45;

    public ReturnStubItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        if (!(world instanceof ServerLevel level) || !(user instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        if (level.dimension() != Level.OVERWORLD) {
            tell(player, "The Return Board works in the Overworld.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!CampaignPersistentState.get(level.getServer()).model().worldChanged(LandmarkKey.LASTLIGHT)) {
            tell(player, "Clear Lastlight before using a Return Stub.");
            return InteractionResult.SUCCESS_SERVER;
        }
        ItemStack stack = player.getItemInHand(hand);
        LastlightReturnState state = LastlightReturnState.get(level.getServer());
        boolean annexRestored = NetherChapterState.get(level.getServer()).annexCleared();
        int range = annexRestored ? ANNEX_RANGE : RANGE;
        int seconds = annexRestored ? 120 : 60;
        long now = level.getGameTime();
        if (player.isShiftKeyDown()) {
            if (!TownhallSafeGround.clear(level, player, player.blockPosition())) {
                tell(player, "Stand on solid, open, dry ground to set a return point.");
            } else if (state.mark(player.blockPosition(), now, 20L * seconds)) {
                level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                        player.getName().getString() + " set a " + seconds + "-second group return point."
                                + " Return Stubs work within " + range + " blocks."), false);
                level.playSound(null, player.blockPosition(), ModSounds.ARCANE_CHIME,
                        SoundSource.PLAYERS, 0.55F, 1.15F);
            } else {
                tell(player, "A return point is already active for "
                        + ((state.remaining(now) + 19) / 20) + " more seconds.");
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        BlockPos point = state.activePoint(now);
        if (point == null) {
            tell(player, "No return point is active. Sneak-use a stub on safe ground to set one.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.SUCCESS_SERVER;
        if (!TownhallSafeGround.within(player.blockPosition(), point, range, HEIGHT_RANGE)) {
            tell(player, "The group return point is farther than " + range
                    + " blocks or 32 blocks of height.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!TownhallSafeGround.clear(level, player, point)) {
            tell(player, "The return point is blocked, unsafe, or unloaded. Visit it before trying again.");
            return InteractionResult.SUCCESS_SERVER;
        }
        Vec3 destination = Vec3.atBottomCenterOf(point);
        Vec3 departure = player.position().add(0, 1, 0);
        if (!GemsTeleport.teleport(player, level, destination.x, destination.y, destination.z,
                player.getYRot(), player.getXRot())) {
            tell(player, "The return could not open. Try again while the point is active.");
            return InteractionResult.SUCCESS_SERVER;
        }
        level.sendParticles(ModParticles.GEM_RING, departure.x, departure.y, departure.z,
                8, 0.25, 0.35, 0.25, 0.01);
        level.sendParticles(ModParticles.GEM_RING, destination.x, destination.y + 1, destination.z,
                8, 0.25, 0.35, 0.25, 0.01);
        level.playSound(null, point, ModSounds.PHASE_SHIFT, SoundSource.PLAYERS, 0.7F, 0.9F);
        player.getCooldowns().addCooldown(stack, PERSONAL_COOLDOWN);
        tell(player, "You returned to the group's marked point.");
        return InteractionResult.SUCCESS_SERVER;
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), true);
    }
}
