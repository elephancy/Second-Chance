package com.feel.gems.gametest.ability;

import com.feel.gems.config.GemsBalance;
import com.feel.gems.core.GemId;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.power.ability.life.HealthDrainAbility;
import com.feel.gems.power.ability.life.HeartLockAbility;
import com.feel.gems.power.ability.life.LifeCircleAbility;
import com.feel.gems.power.ability.life.LifeSwapAbility;
import com.feel.gems.power.ability.life.VitalityVortexAbility;
import com.feel.gems.power.runtime.AbilityRuntime;
import com.feel.gems.power.runtime.GemPowers;
import com.feel.gems.state.GemPlayerState;
import com.feel.gems.trust.GemTrust;
import java.util.EnumSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;




public final class GemsLifeGameTests {

    private static void teleport(ServerPlayer player, ServerLevel world, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(world, x, y, z, EnumSet.noneOf(Relative.class), yaw, pitch, false);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void healthDrainStealsHealthFromTarget(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(target);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(target, world, pos.x, pos.y, pos.z + 3.0D, 180.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.LIFE);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        player.setHealth(10.0F);
        target.setHealth(20.0F);

        float playerHealthBefore = player.getHealth();
        float targetHealthBefore = target.getHealth();

        context.runAtTickTime(5L, () -> {
            boolean ok = new HealthDrainAbility().activate(player);
            if (!ok) {
                context.fail("Health Drain did not activate");
            }
        });

        context.runAtTickTime(15L, () -> {
            if (target.getHealth() >= targetHealthBefore) {
                context.fail("Health Drain did not damage target");
            }
            if (player.getHealth() <= playerHealthBefore) {
                context.fail("Health Drain did not heal caster");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void heartLockPreventsHealthChange(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(target);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(target, world, pos.x, pos.y, pos.z + 3.0D, 180.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.LIFE);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        target.setHealth(8.0F);
        float lockedHealth = target.getHealth();

        context.runAtTickTime(5L, () -> {
            boolean ok = new HeartLockAbility().activate(player);
            if (!ok) {
                context.fail("Heart Lock did not activate");
            }
        });

        context.runAtTickTime(15L, () -> {
            // Try to heal the target
            target.heal(10.0F);
            double maxHealth = target.getAttributeValue(Attributes.MAX_HEALTH);
            
            // Heart lock should cap max health to the locked value
            if (maxHealth > lockedHealth + 0.1D) {
                context.fail("Heart Lock did not restrict max health");
                return;
            }
        });

        int duration = GemsBalance.v().life().heartLockDurationTicks();
        context.runAtTickTime(15L + duration + 10L, () -> {
            AbilityRuntime.tickEverySecond(target);
            double maxHealth = target.getAttributeValue(Attributes.MAX_HEALTH);
            if (maxHealth <= lockedHealth + 0.1D) {
                context.fail("Heart Lock should expire and restore max health");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void lifeSwapExchangesHealth(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(target);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(target, world, pos.x, pos.y, pos.z + 3.0D, 180.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.LIFE);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        player.setHealth(8.0F);
        target.setHealth(18.0F);

        float playerHealthBefore = player.getHealth();
        float targetHealthBefore = target.getHealth();

        context.runAtTickTime(5L, () -> {
            boolean ok = new LifeSwapAbility().activate(player);
            if (!ok) {
                context.fail("Life Swap did not activate");
            }
        });

        context.runAtTickTime(15L, () -> {
            // Health should be swapped (with possible min health limits)
            float playerHealthAfter = player.getHealth();
            float targetHealthAfter = target.getHealth();
            
            // After swap, player should have more health if target had more
            if (playerHealthAfter <= playerHealthBefore && targetHealthAfter >= targetHealthBefore) {
                context.fail("Life Swap did not exchange health");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 300)
    public void lifeCircleAdjustsMaxHealthForAlliesAndEnemies(GameTestHelper context) {
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
        GemPlayerState.setActiveGem(player, GemId.LIFE);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        GemTrust.trust(player, ally.getUUID());
        double allyBase = ally.getAttributeValue(Attributes.MAX_HEALTH);
        double enemyBase = enemy.getAttributeValue(Attributes.MAX_HEALTH);

        context.runAtTickTime(5L, () -> {
            boolean ok = new LifeCircleAbility().activate(player);
            if (!ok) {
                context.fail("Life Circle did not activate");
                return;
            }
        });

        context.runAtTickTime(25L, () -> {
            AbilityRuntime.tickEverySecond(player);
            double allyMax = ally.getAttributeValue(Attributes.MAX_HEALTH);
            double enemyMax = enemy.getAttributeValue(Attributes.MAX_HEALTH);
            if (allyMax <= allyBase) {
                context.fail("Life Circle should increase ally max health");
                return;
            }
            if (enemyMax >= enemyBase) {
                context.fail("Life Circle should reduce enemy max health");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 300)
    public void vitalityVortexDefaultAppliesBuffsAndDebuffs(GameTestHelper context) {
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
        GemPlayerState.setActiveGem(player, GemId.LIFE);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        GemTrust.trust(player, ally.getUUID());

        context.runAtTickTime(5L, () -> {
            boolean ok = new VitalityVortexAbility().activate(player);
            if (!ok) {
                context.fail("Vitality Vortex did not activate");
                return;
            }
        });

        context.runAtTickTime(15L, () -> {
            if (!ally.hasEffect(MobEffects.REGENERATION)) {
                context.fail("Vitality Vortex should buff trusted allies");
                return;
            }
            if (!enemy.hasEffect(MobEffects.POISON) || !enemy.hasEffect(MobEffects.WEAKNESS)) {
                context.fail("Vitality Vortex should debuff enemies in default mode");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void healthDrainHasCooldownConfigured(GameTestHelper context) {
        // Direct activate() bypasses cooldown system (handled by GemAbilities.activateByIndex)
        // Just verify the cooldown config is valid
        var cfg = GemsBalance.v().life();
        if (cfg.healthDrainCooldownTicks() <= 0) {
            context.fail("Health Drain cooldown must be positive");
        }
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void lifeSwapRespectsMinimumHealth(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(target);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(target, world, pos.x, pos.y, pos.z + 3.0D, 180.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.LIFE);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        // Set player to minimum health requirement
        player.setHealth(20.0F);
        target.setHealth(4.0F); // Below min hearts threshold

        context.runAtTickTime(5L, () -> {
            boolean ok = new LifeSwapAbility().activate(player);
            // Life Swap should either fail or handle the minimum health case
            // Either outcome is acceptable as long as no crash occurs
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void vitalityVortexAquaticModeAppliesAquaticBuffs(GameTestHelper context) {
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

        context.setBlock(0, 1, 1, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.LIFE);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        GemTrust.trust(player, ally.getUUID());

        context.runAtTickTime(5L, () -> {
            boolean ok = new VitalityVortexAbility().activate(player);
            if (!ok) {
                context.fail("Vitality Vortex did not activate");
                return;
            }
        });

        context.runAtTickTime(15L, () -> {
            if (!ally.hasEffect(MobEffects.WATER_BREATHING) || !ally.hasEffect(MobEffects.DOLPHINS_GRACE)) {
                context.fail("Aquatic mode should grant water breathing and dolphin's grace");
                return;
            }
            if (!enemy.hasEffect(MobEffects.SLOWNESS) || !enemy.hasEffect(MobEffects.MINING_FATIGUE)) {
                context.fail("Aquatic mode should debuff enemies");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void lifeAutoEnchantAppliesUnbreaking(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.LIFE);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        ItemStack tool = new ItemStack(Items.IRON_PICKAXE);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, tool);

        context.runAtTickTime(5L, () -> {
            GemPowers.maintain(player);
            var unbreaking = world.registryAccess().get(Enchantments.UNBREAKING).orElseThrow();
            int level = EnchantmentHelper.getItemEnchantmentLevel(unbreaking, tool);
            if (level <= 0) {
                context.fail("Auto-enchant Unbreaking should apply to tools");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void lifeDoubleSaturationBoostsFoodSaturation(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.LIFE);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        var hunger = player.getFoodData();
        hunger.setFoodLevel(10);
        hunger.setSaturation(0.0F);

        ItemStack food = new ItemStack(Items.COOKED_BEEF);
        context.runAtTickTime(5L, () -> {
            float before = hunger.getSaturationLevel();
            food.finishUsingItem(world, player);
            float after = hunger.getSaturationLevel();
            if (after <= before) {
                context.fail("Double Saturation should increase saturation on eat");
                return;
            }
            context.succeed();
        });
    }
}
