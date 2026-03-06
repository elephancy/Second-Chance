package com.feel.gems.adventure;

import com.feel.gems.adventure.boss.BossFeatures;
import com.feel.gems.adventure.boss.SignalEchoEntity;
import com.feel.gems.adventure.AdventureSounds;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.BlockPos;

/** World-shared scout service on the townhall book; mutable state stays on the server thread. */
public final class TownHallServices {
    private static final Map<UUID, Long> ECHO_COOLDOWNS = new HashMap<>();

    private TownHallServices() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.getBlockState(hit.getBlockPos()).is(DomainBlocks.TOWNHALL_LEDGER)) {
                return InteractionResult.PASS;
            }
            if (!(world instanceof ServerLevel serverWorld)
                    || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            return useBook(serverWorld, serverPlayer);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ECHO_COOLDOWNS.clear());
    }

    private static InteractionResult useBook(ServerLevel world, ServerPlayer player) {
        if (!CampaignPersistentState.get(world.getServer()).model()
                .worldChanged(LandmarkKey.HUSHWIRE)) {
            player.sendSystemMessage(Component.literal(
                    "Clear Hushwire to call a scout."), true);
            return InteractionResult.SUCCESS_SERVER;
        }
        long now = world.getServer().getTickCount();
        long remaining = ECHO_COOLDOWNS.getOrDefault(player.getUUID(), 0L) - now;
        if (remaining > 0) {
            player.sendSystemMessage(Component.literal("You can call another scout in "
                    + ((remaining + 19) / 20) + " seconds."), true);
            return InteractionResult.SUCCESS_SERVER;
        }
        BlockPos spawn = player.blockPosition().offset(1, 0, 1);
        if (!world.getBlockState(spawn).isAir() || !world.getBlockState(spawn.above()).isAir()) {
            player.sendSystemMessage(Component.literal(
                    "Step into open space and try the book again."), true);
            return InteractionResult.SUCCESS_SERVER;
        }
        SignalEchoEntity echo = BossFeatures.SIGNAL_ECHO.create(world, EntitySpawnReason.MOB_SUMMONED);
        if (echo == null) return InteractionResult.FAIL;
        echo.call(player);
        echo.snapTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        if (!world.addFreshEntity(echo)) return InteractionResult.FAIL;
        ECHO_COOLDOWNS.put(player.getUUID(), now + 20 * 60);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                AdventureSounds.HUSHWIRE_TRUE, SoundSource.PLAYERS, 0.7F, 1.05F);
        player.sendSystemMessage(Component.literal(
                "A scout will stay beside you for 30 seconds."), true);
        return InteractionResult.SUCCESS_SERVER;
    }
}
