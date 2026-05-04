package com.feel.gems.gametest.ability;

import com.feel.gems.config.GemsBalance;
import com.feel.gems.core.GemId;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.power.ability.beacon.BeaconAuraAbility;
import com.feel.gems.power.gem.beacon.BeaconAuraRuntime;
import com.feel.gems.power.gem.beacon.BeaconSupportRuntime;
import com.feel.gems.power.runtime.GemPowers;
import com.feel.gems.state.GemPlayerState;
import com.feel.gems.trust.GemTrust;
import java.util.EnumSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

public final class GemsBeaconGameTests {

    private static void teleport(ServerPlayer player, ServerLevel world, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(world, x, y, z, EnumSet.noneOf(Relative.class), yaw, pitch, false);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void beaconAuraStrengthActivates(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.BEACON);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new BeaconAuraAbility(BeaconAuraRuntime.AuraType.STRENGTH).activate(player);
            if (!ok) {
                context.fail("Beacon Aura Strength did not activate");
            }
            BeaconAuraRuntime.AuraType active = BeaconAuraRuntime.activeType(player);
            if (active != BeaconAuraRuntime.AuraType.STRENGTH) {
                context.fail("Aura type was not set to STRENGTH");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void beaconAuraResistanceActivates(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.BEACON);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new BeaconAuraAbility(BeaconAuraRuntime.AuraType.RESISTANCE).activate(player);
            if (!ok) {
                context.fail("Beacon Aura Resistance did not activate");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void beaconAuraToggleOff(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.BEACON);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            new BeaconAuraAbility(BeaconAuraRuntime.AuraType.STRENGTH).activate(player);
        });

        context.runAtTickTime(10L, () -> {
            new BeaconAuraAbility(BeaconAuraRuntime.AuraType.STRENGTH).activate(player);
            BeaconAuraRuntime.AuraType active = BeaconAuraRuntime.activeType(player);
            if (active != null) {
                context.fail("Aura should be toggled off");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void beaconAuraAppliesToAlliesAndDebuffsEnemies(GameTestHelper context) {
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
        teleport(enemy, world, pos.x - 2.0D, pos.y, pos.z, 180.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.BEACON);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        GemTrust.trust(player, ally.getUUID());

        context.runAtTickTime(5L, () -> {
            new BeaconAuraAbility(BeaconAuraRuntime.AuraType.SPEED).activate(player);
            BeaconAuraRuntime.tickEverySecond(player);
        });

        context.runAtTickTime(15L, () -> {
            if (ally.getEffect(MobEffects.SPEED) == null) {
                context.fail("Beacon aura should buff trusted allies");
                return;
            }
            if (enemy.getEffect(MobEffects.SLOWNESS) == null || enemy.getEffect(MobEffects.WEAKNESS) == null) {
                context.fail("Beacon aura should debuff enemies with slowness and weakness");
                return;
            }
            if (enemy.getEffect(MobEffects.SPEED) != null) {
                context.fail("Beacon aura should not apply positive effects to enemies");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void beaconRallyGrantsAbsorptionToAllies(GameTestHelper context) {
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
        teleport(enemy, world, pos.x - 2.0D, pos.y, pos.z, 180.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.BEACON);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        GemTrust.trust(player, ally.getUUID());

        context.runAtTickTime(5L, () -> new BeaconAuraAbility(BeaconAuraRuntime.AuraType.RESISTANCE).activate(player));

        context.runAtTickTime(15L, () -> {
            if (ally.getEffect(MobEffects.ABSORPTION) == null) {
                context.fail("Beacon Rally should grant Absorption to allies");
                return;
            }
            if (enemy.getEffect(MobEffects.WEAKNESS) == null) {
                context.fail("Beacon Rally should apply Weakness to enemies");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void beaconCoreAppliesRegenPulse(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer ally = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(ally);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(ally, world, pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.BEACON);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        GemTrust.trust(player, ally.getUUID());

        context.runAtTickTime(5L, () -> BeaconSupportRuntime.tickEverySecond(player));

        context.runAtTickTime(15L, () -> {
            if (ally.getEffect(MobEffects.REGENERATION) == null) {
                context.fail("Beacon Core should grant Regeneration to trusted allies");
                return;
            }
            if (player.getEffect(MobEffects.REGENERATION) == null) {
                context.fail("Beacon Core should grant Regeneration to self");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void beaconStabilizeReducesHarmfulEffects(GameTestHelper context) {
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
        teleport(enemy, world, pos.x - 2.0D, pos.y, pos.z, 180.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.BEACON);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        GemTrust.trust(player, ally.getUUID());

        MobEffectInstance poison = new MobEffectInstance(MobEffects.POISON, 200, 0, true, false, false);
        ally.addEffect(poison);

        context.runAtTickTime(5L, () -> BeaconSupportRuntime.tickEverySecond(player));

        context.runAtTickTime(15L, () -> {
            var remaining = ally.getEffect(MobEffects.POISON);
            if (remaining != null && remaining.getDuration() >= poison.getDuration()) {
                context.fail("Stabilize should reduce harmful effect duration on allies");
                return;
            }
            if (enemy.getEffect(MobEffects.MINING_FATIGUE) == null) {
                context.fail("Stabilize should debuff enemies with Mining Fatigue");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void beaconConfigValuesValid(GameTestHelper context) {
        var cfg = GemsBalance.v().beacon();
        
        if (cfg.auraCooldownTicks() < 0) {
            context.fail("Aura cooldown cannot be negative");
        }
        if (cfg.auraRadiusBlocks() <= 0) {
            context.fail("Aura radius must be positive");
        }
        
        context.succeed();
    }
}
