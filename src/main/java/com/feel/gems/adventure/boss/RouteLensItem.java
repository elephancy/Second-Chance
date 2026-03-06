package com.feel.gems.adventure.boss;

import com.feel.gems.sound.ModSounds;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
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

/** Reveal two walkable route choices through nearby walls and turns. */
public final class RouteLensItem extends Item {
    private static final DustParticleOptions FIRST = new DustParticleOptions(0x61D3DE, 1.05F);
    private static final DustParticleOptions SECOND = new DustParticleOptions(0xE5BD6E, 1.05F);

    public RouteLensItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        if (!(world instanceof ServerLevel serverWorld)
                || !(user instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.SUCCESS;
        BlockPos origin = player.blockPosition();
        RouteSurvey.Terrain terrain = new RouteSurvey.Terrain() {
            private BlockPos absolute(RouteSurvey.Point point) {
                return origin.offset(point.x(), point.y(), point.z());
            }

            @Override
            public boolean walkable(RouteSurvey.Point point) {
                BlockPos feet = absolute(point);
                BlockPos below = feet.below();
                return serverWorld.hasChunkAt(feet)
                        && serverWorld.getWorldBorder().isWithinBounds(feet)
                        && !serverWorld.getBlockState(below).getCollisionShape(serverWorld, below).isEmpty()
                        && serverWorld.getBlockState(feet).getCollisionShape(serverWorld, feet).isEmpty()
                        && serverWorld.getBlockState(feet.above()).getCollisionShape(serverWorld, feet.above()).isEmpty()
                        && serverWorld.getFluidState(feet).isEmpty()
                        && serverWorld.getFluidState(feet.above()).isEmpty();
            }

            @Override
            public boolean daylight(RouteSurvey.Point point) {
                return serverWorld.canSeeSky(absolute(point));
            }
        };
        List<RouteSurvey.Path> paths = RouteSurvey.find(terrain);
        if (paths.isEmpty()) {
            player.sendSystemMessage(Component.literal("No clear route nearby. Try from another junction.")
                    .withStyle(ChatFormatting.GRAY), true);
            player.getCooldowns().addCooldown(stack, 20 * 5);
            return InteractionResult.SUCCESS;
        }
        for (int i = 0; i < paths.size(); i++) {
            RouteSurvey.Path path = paths.get(i);
            DustParticleOptions color = i == 0 ? FIRST : SECOND;
            for (RouteSurvey.Point point : path.points()) {
                BlockPos position = origin.offset(point.x(), point.y(), point.z());
                serverWorld.sendParticles(color, position.getX() + 0.5,
                        position.getY() + 0.12, position.getZ() + 0.5,
                        1, 0.06, 0.02, 0.06, 0);
            }
        }
        String description = String.join("; ", paths.stream()
                .map(path -> path.direction().label
                        + (path.daylight() ? " to daylight" : " through the rooms"))
                .toList());
        player.sendSystemMessage(Component.literal("Routes: " + description + ". Follow the lights.")
                .withStyle(ChatFormatting.AQUA), true);
        serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.ARCANE_CHIME, SoundSource.PLAYERS, 0.6F, 1.35F);
        player.getCooldowns().addCooldown(stack, 20 * 25);
        return InteractionResult.SUCCESS;
    }
}
