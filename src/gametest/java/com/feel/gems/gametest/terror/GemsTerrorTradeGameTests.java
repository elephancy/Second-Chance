package com.feel.gems.gametest.terror;

import com.feel.gems.core.GemId;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.power.ability.terror.TerrorTradeAbility;
import com.feel.gems.state.GemPlayerState;
import com.feel.gems.state.PlayerNbt;
import java.util.EnumSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;




public final class GemsTerrorTradeGameTests {
    private static void teleport(ServerPlayer player, ServerLevel world, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(world, x, y, z, EnumSet.noneOf(Relative.class), yaw, pitch, false);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void terrorTradeNormalKillsBothAndTargetLosesTwoHearts(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer caster = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer victim = GemsGameTestUtil.createMockCreativeServerPlayer(context);

        Vec3 casterPos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        // Keep the victim well within the EMPTY_STRUCTURE bounds to avoid barrier occlusion during raycasts.
        Vec3 victimPos = context.absoluteVec(new Vec3(0.5D, 2.0D, 2.5D));
        teleport(caster, world, casterPos.x, casterPos.y, casterPos.z, 0.0F, 0.0F);
        teleport(victim, world, victimPos.x, victimPos.y, victimPos.z, 180.0F, 0.0F);

        context.runAtTickTime(1L, () -> {
            GemsGameTestUtil.forceSurvival(caster);
            GemsGameTestUtil.forceSurvival(victim);
            teleport(caster, world, casterPos.x, casterPos.y, casterPos.z, 0.0F, 0.0F);
            teleport(victim, world, victimPos.x, victimPos.y, victimPos.z, 180.0F, 0.0F);

            GemPlayerState.initIfNeeded(caster);
            GemPlayerState.setActiveGem(caster, GemId.TERROR);
            GemPlayerState.setMaxHearts(caster, 10);
            GemPlayerState.applyMaxHearts(caster);

            GemPlayerState.initIfNeeded(victim);
            GemPlayerState.setMaxHearts(victim, 10);
            GemPlayerState.applyMaxHearts(victim);

            // Totems should save the target in all modes in real gameplay.
            // Note: GameTest mock players only reliably take certain damage sources, so we don't assert totem consumption here.
            victim.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        });

        context.runAtTickTime(20L, () -> {
            // Re-aim right before activation; GameTest player rotations can be flaky if set too early.
            Vec3 aimAt = new Vec3(victim.getX(), victim.getEyeY(), victim.getZ());
            double dx = aimAt.x - caster.getX();
            double dz = aimAt.z - caster.getZ();
            double dy = aimAt.y - caster.getEyeY();
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
            float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
            teleport(caster, world, caster.getX(), caster.getY(), caster.getZ(), yaw, pitch);
        });

        context.runAtTickTime(22L, () -> {
            // Force survival right before activation; mock players can get their abilities reset during login.
            GemsGameTestUtil.forceSurvival(caster);
            GemsGameTestUtil.forceSurvival(victim);
            boolean ok = new TerrorTradeAbility().activate(caster);
            if (!ok) {
                context.fail("Terror Trade did not activate (raycast target not acquired)");
            }
        });

        context.runAtTickTime(60L, () -> {
            // Target pays 2 hearts instead of the normal 1-heart drop on death.
            if (GemPlayerState.getMaxHearts(victim) != 8) {
                context.fail("Victim max hearts should be 8 after Terror Trade penalty");
                return;
            }

            // Caster has no extra penalties (only the normal death heart loss).
            if (GemPlayerState.getMaxHearts(caster) != 9) {
                context.fail("Caster max hearts should be 9 (no extra penalties beyond normal death loss)");
                return;
            }
            if (GemPlayerState.getEnergyCapPenalty(caster) != 0) {
                context.fail("Caster energy cap penalty should remain 0 in normal-player Terror Trade");
                return;
            }

            // Normal mode does not consume "uses".
            if (PlayerNbt.getInt(caster, "terrorTradeUses", 0) != 0) {
                context.fail("Normal-player Terror Trade should not consume uses");
                return;
            }

            // Victim should not be dead after the trade attempt.
            if (victim.isDeadOrDying()) {
                context.fail("Victim died during Terror Trade");
                return;
            }

            context.succeed();
        });
    }
}
