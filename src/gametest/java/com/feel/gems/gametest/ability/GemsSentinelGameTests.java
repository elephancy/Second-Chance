package com.feel.gems.gametest.ability;

import com.feel.gems.config.GemsBalance;
import com.feel.gems.core.GemId;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.power.ability.sentinel.*;
import com.feel.gems.power.gem.sentinel.SentinelPassiveRuntime;
import com.feel.gems.power.runtime.AbilityRestrictions;
import com.feel.gems.power.runtime.GemPowers;
import com.feel.gems.state.GemPlayerState;
import com.feel.gems.trust.GemTrust;
import java.util.EnumSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

public final class GemsSentinelGameTests {

    private static void teleport(ServerPlayer player, ServerLevel world, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(world, x, y, z, EnumSet.noneOf(Relative.class), yaw, pitch, false);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void sentinelTauntActivates(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(target);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(target, world, pos.x + 2.0D, pos.y, pos.z, 180.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SENTINEL);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new SentinelTauntAbility().activate(player);
            if (!ok) {
                context.fail("Sentinel Taunt did not activate");
                return;
            }
            if (!SentinelTauntRuntime.isTaunted(target)) {
                context.fail("Taunt should mark nearby enemies as taunted");
                return;
            }
            if (player.getEffect(MobEffects.RESISTANCE) == null) {
                context.fail("Taunt should grant Resistance to the caster");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void sentinelShieldWallActivates(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SENTINEL);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new SentinelShieldWallAbility().activate(player);
            if (!ok) {
                context.fail("Shield Wall did not activate");
                return;
            }
            String worldId = world.dimension().identifier().toString();
            BlockPos basePos = player.blockPosition().relative(player.getDirection(), 2);
            if (!SentinelShieldWallRuntime.isInWall(Vec3.atCenterOf(basePos), worldId)) {
                context.fail("Shield Wall should register wall positions");
                return;
            }
            if (!SentinelShieldWallRuntime.hasAnyWalls()) {
                context.fail("Shield Wall runtime should track active walls");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void sentinelRallyCryActivates(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer ally = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(ally);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(ally, world, pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SENTINEL);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        GemTrust.trust(player, ally.getUUID());
        GemTrust.trust(ally, player.getUUID());
        GemTrust.trust(ally, player.getUUID());
        ally.setHealth(6.0F);

        context.runAtTickTime(5L, () -> {
            boolean ok = new SentinelRallyCryAbility().activate(player);
            if (!ok) {
                context.fail("Rally Cry did not activate");
                return;
            }
            if (ally.getHealth() <= 6.0F) {
                context.fail("Rally Cry should heal trusted allies");
                return;
            }
            if (ally.getEffect(MobEffects.RESISTANCE) == null) {
                context.fail("Rally Cry should grant Resistance to allies");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void sentinelLockdownActivates(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer enemy = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(enemy);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(enemy, world, pos.x + 2.0D, pos.y, pos.z, 180.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SENTINEL);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new SentinelLockdownAbility().activate(player);
            if (!ok) {
                context.fail("Lockdown did not activate");
                return;
            }
            SentinelLockdownRuntime.tick(world.getGameTime(), world.dimension().identifier().toString(), world);
        });

        context.runAtTickTime(15L, () -> {
            if (!SentinelLockdownRuntime.isInLockdownZone(enemy.position(), enemy.getUUID(), world.dimension().identifier().toString())) {
                context.fail("Lockdown should create a zone affecting enemies");
                return;
            }
            if (!AbilityRestrictions.isSuppressed(enemy)) {
                context.fail("Lockdown should suppress enemy abilities");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void sentinelInterventionRedirectsDamage(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer ally = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer attacker = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(ally);
        GemsGameTestUtil.forceSurvival(attacker);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(ally, world, pos.x + 3.0D, pos.y, pos.z, 0.0F, 0.0F);
        teleport(attacker, world, pos.x + 3.0D, pos.y, pos.z + 2.0D, 180.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SENTINEL);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(4L, () -> {
            GemTrust.trust(player, ally.getUUID());
            GemTrust.trust(ally, player.getUUID());
        });

        context.runAtTickTime(6L, () -> {
            boolean ok = new SentinelInterventionAbility().activate(player);
            if (!ok) {
                context.fail("Intervention did not activate with a trusted ally nearby");
            }
        });

        context.runAtTickTime(12L, () -> {
            if (SentinelInterventionRuntime.getProtector(ally) != player) {
                context.fail("Intervention should mark the sentinel as the ally's protector");
            }
        });

        context.runAtTickTime(20L, () -> {
            float allyBefore = ally.getHealth();
            float sentinelBefore = player.getHealth();
            ally.hurtServer(world, attacker.damageSources().playerAttack(attacker), 4.0F);
            if (ally.getHealth() < allyBefore) {
                context.fail("Intervention should redirect damage away from the ally");
                return;
            }
            if (player.getHealth() >= sentinelBefore) {
                context.fail("Intervention should transfer damage to the sentinel");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void sentinelGuardianAuraReducesDamage(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer sentinel = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer ally = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer attacker = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(sentinel);
        GemsGameTestUtil.forceSurvival(ally);
        GemsGameTestUtil.forceSurvival(attacker);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(sentinel, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(ally, world, pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);
        teleport(attacker, world, pos.x + 2.0D, pos.y, pos.z + 2.0D, 180.0F, 0.0F);

        GemPlayerState.initIfNeeded(sentinel);
        GemPlayerState.setActiveGem(sentinel, GemId.SENTINEL);
        GemPlayerState.setEnergy(sentinel, 5);
        GemPowers.sync(sentinel);

        GemTrust.trust(sentinel, ally.getUUID());

        context.runAtTickTime(5L, () -> {
            float before = ally.getHealth();
            ally.hurtServer(world, attacker.damageSources().playerAttack(attacker), 6.0F);
            float taken = before - ally.getHealth();
            if (taken <= 0.0F) {
                context.fail("Guardian Aura test did not apply damage");
                return;
            }
            if (!SentinelPassiveRuntime.isProtectedByGuardianAura(ally)) {
                context.fail("Guardian Aura should detect protected allies nearby");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void sentinelFortressGrantsResistanceAfterStandingStill(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SENTINEL);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        int required = GemsBalance.v().sentinel().fortressStandStillTicks();

        context.runAtTickTime(5L, () -> SentinelPassiveRuntime.tickFortress(player));
        context.runAtTickTime(5L + required + 2L, () -> {
            SentinelPassiveRuntime.tickFortress(player);
            if (player.getEffect(MobEffects.RESISTANCE) == null) {
                context.fail("Fortress should grant Resistance after standing still");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void sentinelRetributionThornsReflectsDamage(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer sentinel = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer attacker = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(sentinel);
        GemsGameTestUtil.forceSurvival(attacker);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(sentinel, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(attacker, world, pos.x + 2.0D, pos.y, pos.z, 180.0F, 0.0F);

        GemPlayerState.initIfNeeded(sentinel);
        GemPlayerState.setActiveGem(sentinel, GemId.SENTINEL);
        GemPlayerState.setEnergy(sentinel, 5);
        GemPowers.sync(sentinel);

        context.runAtTickTime(5L, () -> {
            float attackerBefore = attacker.getHealth();
            sentinel.hurtServer(world, attacker.damageSources().playerAttack(attacker), 4.0F);
            if (attacker.getHealth() >= attackerBefore) {
                context.fail("Retribution Thorns should reflect damage to the attacker");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void sentinelConfigValuesValid(GameTestHelper context) {
        var cfg = GemsBalance.v().sentinel();
        
        if (cfg.tauntCooldownTicks() < 0) {
            context.fail("Taunt cooldown cannot be negative");
        }
        if (cfg.shieldWallCooldownTicks() < 0) {
            context.fail("Shield Wall cooldown cannot be negative");
        }
        if (cfg.rallyCryCooldownTicks() < 0) {
            context.fail("Rally Cry cooldown cannot be negative");
        }
        
        context.succeed();
    }
}
