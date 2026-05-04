package com.feel.gems.gametest.ability;

import net.minecraft.world.entity.EntityTypes;
import com.feel.gems.config.GemsBalance;
import com.feel.gems.core.GemId;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.power.ability.speed.ArcShotAbility;
import com.feel.gems.power.ability.speed.SpeedAfterimageAbility;
import com.feel.gems.power.ability.speed.SpeedSlipstreamAbility;
import com.feel.gems.power.ability.speed.SpeedStormAbility;
import com.feel.gems.power.ability.speed.SpeedTempoShiftAbility;
import com.feel.gems.power.ability.speed.TerminalVelocityAbility;
import com.feel.gems.power.gem.speed.SpeedFrictionlessSteps;
import com.feel.gems.power.gem.speed.SpeedMomentum;
import com.feel.gems.power.registry.PowerIds;
import com.feel.gems.power.runtime.AbilityRuntime;
import com.feel.gems.power.runtime.GemAbilityCooldowns;
import com.feel.gems.power.runtime.GemPowers;
import com.feel.gems.state.GemPlayerState;
import com.feel.gems.trust.GemTrust;
import java.util.EnumSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;




public final class GemsSpeedGameTests {

    private static void teleport(ServerPlayer player, ServerLevel world, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(world, x, y, z, EnumSet.noneOf(Relative.class), yaw, pitch, false);
    }

    private static void aimAt(ServerPlayer player, ServerLevel world, Vec3 target) {
        Vec3 pos = player.position();
        double dx = target.x - pos.x;
        double dz = target.z - pos.z;
        double dy = target.y - player.getEyeY();
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        teleport(player, world, pos.x, pos.y, pos.z, yaw, pitch);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void arcShotStrikesTargetsInLine(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SPEED);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        Zombie zombie = EntityTypes.ZOMBIE.create(world, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if (zombie == null) {
            context.fail("Failed to create zombie");
            return;
        }
        zombie.snapTo(pos.x, pos.y, pos.z + 4.0D, 180.0F, 0.0F);
        world.addFreshEntity(zombie);
        aimAt(player, world, zombie.position().add(0.0D, 1.0D, 0.0D));
        float before = zombie.getHealth();

        context.runAtTickTime(5L, () -> {
            boolean ok = new ArcShotAbility().activate(player);
            if (!ok) {
                context.fail("Arc Shot did not activate");
                return;
            }
            if (zombie.getHealth() >= before) {
                context.fail("Arc Shot should damage targets in its path");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void speedStormAppliesEffectsInRadius(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer ally = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer enemy = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(ally);
        GemsGameTestUtil.forceSurvival(enemy);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(ally, world, pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);
        teleport(enemy, world, pos.x + 3.0D, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SPEED);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        GemTrust.trust(player, ally.getUUID());

        context.runAtTickTime(5L, () -> {
            boolean ok = new SpeedStormAbility().activate(player);
            if (!ok) {
                context.fail("Speed Storm did not activate");
                return;
            }
        });

        context.runAtTickTime(15L, () -> {
            AbilityRuntime.tickEverySecond(player);
            if (!ally.hasEffect(MobEffects.SPEED) || !ally.hasEffect(MobEffects.HASTE)) {
                context.fail("Speed Storm should buff trusted allies");
                return;
            }
            if (!enemy.hasEffect(MobEffects.SLOWNESS) || !enemy.hasEffect(MobEffects.MINING_FATIGUE)) {
                context.fail("Speed Storm should debuff enemies");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void terminalVelocityGrantsSpeedAndHaste(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SPEED);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new TerminalVelocityAbility().activate(player);
            if (!ok) {
                context.fail("Terminal Velocity did not activate");
            }
        });

        context.runAtTickTime(15L, () -> {
            var speed = player.getEffect(MobEffects.SPEED);
            var haste = player.getEffect(MobEffects.HASTE);

            if (speed == null) {
                context.fail("Terminal Velocity did not grant speed");
            }
            if (haste == null) {
                context.fail("Terminal Velocity did not grant haste");
            }

            var cfg = GemsBalance.v().speed();
            if (speed.getAmplifier() != cfg.terminalVelocitySpeedAmplifier()) {
                context.fail("Terminal Velocity speed amplifier mismatch: got " + speed.getAmplifier() + " expected " + cfg.terminalVelocitySpeedAmplifier());
            }

            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void slipstreamBuffsAlliesAndSlowsEnemies(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer ally = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer enemy = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(ally);
        GemsGameTestUtil.forceSurvival(enemy);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(ally, world, pos.x, pos.y, pos.z + 2.0D, 180.0F, 0.0F);
        teleport(enemy, world, pos.x, pos.y, pos.z + 3.0D, 180.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SPEED);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        GemPlayerState.initIfNeeded(ally);
        GemPlayerState.setActiveGem(ally, GemId.SPEED);
        GemPlayerState.setEnergy(ally, 5);
        GemPowers.sync(ally);

        GemPlayerState.initIfNeeded(enemy);
        GemPlayerState.setActiveGem(enemy, GemId.SPEED);
        GemPlayerState.setEnergy(enemy, 5);
        GemPowers.sync(enemy);

        GemTrust.trust(player, ally.getUUID());

        context.runAtTickTime(5L, () -> {
            boolean ok = new SpeedSlipstreamAbility().activate(player);
            if (!ok) {
                context.fail("Slipstream did not activate");
                return;
            }
        });

        context.runAtTickTime(15L, () -> {
            AbilityRuntime.tickEverySecond(player);
            if (!ally.hasEffect(MobEffects.SPEED)) {
                context.fail("Slipstream should buff trusted allies");
                return;
            }
            if (!enemy.hasEffect(MobEffects.SLOWNESS)) {
                context.fail("Slipstream should slow enemies");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void afterimageGrantsSpeedAndInvisibility(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SPEED);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new SpeedAfterimageAbility().activate(player);
            if (!ok) {
                context.fail("Afterimage did not activate");
            }
        });

        context.runAtTickTime(15L, () -> {
            var speed = player.getEffect(MobEffects.SPEED);
            if (speed == null) {
                context.fail("Afterimage did not grant speed");
                return;
            }
            if (!player.hasEffect(MobEffects.INVISIBILITY)) {
                context.fail("Afterimage should grant invisibility");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void tempoShiftShiftsCooldownsForAlliesAndEnemies(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer ally = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer enemy = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(ally);
        GemsGameTestUtil.forceSurvival(enemy);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(ally, world, pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);
        teleport(enemy, world, pos.x + 3.0D, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SPEED);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        GemPlayerState.initIfNeeded(ally);
        GemPlayerState.setActiveGem(ally, GemId.SPEED);
        GemPlayerState.setEnergy(ally, 5);
        GemPowers.sync(ally);

        GemPlayerState.initIfNeeded(enemy);
        GemPlayerState.setActiveGem(enemy, GemId.SPEED);
        GemPlayerState.setEnergy(enemy, 5);
        GemPowers.sync(enemy);

        GemTrust.trust(player, ally.getUUID());

        long now = world.getGameTime();
        GemAbilityCooldowns.setNextAllowedTick(ally, PowerIds.TERMINAL_VELOCITY, now + 200);
        GemAbilityCooldowns.setNextAllowedTick(enemy, PowerIds.TERMINAL_VELOCITY, now + 200);

        context.runAtTickTime(5L, () -> {
            boolean ok = new SpeedTempoShiftAbility().activate(player);
            if (!ok) {
                context.fail("Tempo Shift did not activate");
                return;
            }
        });

        context.runAtTickTime(25L, () -> {
            AbilityRuntime.tickEverySecond(player);
            long allyNext = GemAbilityCooldowns.nextAllowedTick(ally, PowerIds.TERMINAL_VELOCITY);
            long enemyNext = GemAbilityCooldowns.nextAllowedTick(enemy, PowerIds.TERMINAL_VELOCITY);
            if (allyNext >= now + 200) {
                context.fail("Tempo Shift should reduce ally cooldowns");
                return;
            }
            if (enemyNext <= now + 200) {
                context.fail("Tempo Shift should increase enemy cooldowns");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void momentumPassiveScalesDamageWithSpeed(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(target);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(target, world, pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SPEED);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            // Set player to be moving fast
            player.setDeltaMovement(new Vec3(0.5D, 0.0D, 0.0D));
            
            float mult = SpeedMomentum.multiplier(player);
            var cfg = GemsBalance.v().speed();
            
            if (mult < cfg.momentumMinMultiplier() || mult > cfg.momentumMaxMultiplier()) {
                context.fail("Momentum multiplier out of range: " + mult);
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void frictionlessStepsGrantsSpeedOnFrictionBlocks(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SPEED);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        // Place cobweb under player
        context.setBlock(0, 1, 0, net.minecraft.world.level.block.Blocks.COBWEB.defaultBlockState());

        context.runAtTickTime(5L, () -> {
            // Frictionless steps ticks on the server, not per-player
            SpeedFrictionlessSteps.tick(world.getServer());
        });

        context.runAtTickTime(15L, () -> {
            if (!player.hasEffect(MobEffects.SPEED)) {
                context.fail("Frictionless Steps should grant speed on friction blocks");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void speedPassivesApplySpeedAndHaste(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SPEED);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            GemPowers.maintain(player);
            if (!player.hasEffect(MobEffects.SPEED) || !player.hasEffect(MobEffects.HASTE)) {
                context.fail("Speed passives should apply Speed and Haste");
                return;
            }
            context.succeed();
        });
    }
}
