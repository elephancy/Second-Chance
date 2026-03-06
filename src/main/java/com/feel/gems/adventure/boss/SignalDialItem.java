package com.feel.gems.adventure.boss;

import com.feel.gems.particle.ModParticles;
import com.feel.gems.adventure.AdventureSounds;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Monster;
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
import net.minecraft.world.level.Level;

/** Hear through Hushwire's false calls or briefly call a scouting echo. */
public final class SignalDialItem extends Item {
    public SignalDialItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        if (!(world instanceof ServerLevel serverWorld)
                || !(user instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.SUCCESS;
        if (player.isShiftKeyDown()) return callEcho(serverWorld, player, stack);

        List<EmptyEncoreEntity> encores = serverWorld.getEntitiesOfClass(EmptyEncoreEntity.class,
                new AABB(player.blockPosition()).inflate(30), EmptyEncoreEntity::isAlive);
        for (EmptyEncoreEntity encore : encores) {
            if (encore.showTruth(player)) {
                player.getCooldowns().addCooldown(stack, 20 * 15);
                serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                        AdventureSounds.HUSHWIRE_TRUE, SoundSource.PLAYERS, 0.6F, 1.2F);
                return InteractionResult.SUCCESS_SERVER;
            }
        }
        List<Monster> threats = serverWorld.getEntitiesOfClass(Monster.class,
                new AABB(player.blockPosition()).inflate(16), Monster::isAlive).stream()
                .sorted(Comparator.comparingDouble(player::distanceToSqr))
                .limit(8).toList();
        for (Monster threat : threats) {
            serverWorld.sendParticles(ModParticles.GEM_STAR, threat.getX(), threat.getY() + 1.2,
                    threat.getZ(), 8, 0.35, 0.35, 0.35, 0.01);
        }
        player.sendSystemMessage(Component.literal(threats.isEmpty()
                ? "No nearby monsters." : "Your dial spots " + threats.size() + " nearby threat"
                        + (threats.size() == 1 ? "." : "s."))
                .withStyle(ChatFormatting.AQUA), true);
        player.getCooldowns().addCooldown(stack, threats.isEmpty() ? 20 * 5 : 20 * 15);
        return InteractionResult.SUCCESS_SERVER;
    }

    private InteractionResult callEcho(ServerLevel world, ServerPlayer player, ItemStack stack) {
        SignalEchoEntity echo = BossFeatures.SIGNAL_ECHO.create(world, EntitySpawnReason.MOB_SUMMONED);
        if (echo == null) return InteractionResult.FAIL;
        echo.call(player);
        echo.snapTo(player.getX() + 1, player.getY(), player.getZ() + 1,
                player.getYRot(), player.getXRot());
        if (!world.addFreshEntity(echo)) {
            player.sendSystemMessage(Component.literal("No room for a scout here. Try an open spot nearby.")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.SUCCESS_SERVER;
        }
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                AdventureSounds.HUSHWIRE_TRUE, SoundSource.PLAYERS, 0.7F, 1.05F);
        player.sendSystemMessage(Component.literal("Your echo will scout beside you for thirty seconds.")
                .withStyle(ChatFormatting.AQUA), true);
        player.getCooldowns().addCooldown(stack, 20 * 60);
        return InteractionResult.SUCCESS_SERVER;
    }
}
