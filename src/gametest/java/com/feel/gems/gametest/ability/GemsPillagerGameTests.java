package com.feel.gems.gametest.ability;

import com.feel.gems.config.GemsBalance;
import com.feel.gems.core.GemId;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.power.ability.pillager.*;
import com.feel.gems.power.gem.pillager.PillagerDiscipline;
import com.feel.gems.power.gem.pillager.PillagerVolleyRuntime;
import com.feel.gems.power.runtime.GemPowers;
import com.feel.gems.state.GemPlayerState;
import com.feel.gems.trust.GemTrust;
import java.util.EnumSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

public final class GemsPillagerGameTests {

    private static void teleport(ServerPlayer player, ServerLevel world, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(world, x, y, z, EnumSet.noneOf(Relative.class), yaw, pitch, false);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void pillagerFangsActivates(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.PILLAGER);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            int before = world.getEntitiesOfClass(EvokerFangs.class, player.getBoundingBox().inflate(8.0D), e -> true).size();
            boolean ok = new PillagerFangsAbility().activate(player);
            if (!ok) {
                context.fail("Pillager Fangs did not activate");
                return;
            }
            int after = world.getEntitiesOfClass(EvokerFangs.class, player.getBoundingBox().inflate(8.0D), e -> true).size();
            if (after <= before) {
                context.fail("Pillager Fangs should spawn evoker fangs");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void pillagerSnareAppliesSlownessAndGlowing(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(target);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(target, world, pos.x, pos.y, pos.z + 4.0D, 180.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.PILLAGER);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new PillagerSnareAbility().activate(player);
            if (!ok) {
                context.fail("Pillager Snare did not activate");
                return;
            }
        });

        context.runAtTickTime(15L, () -> {
            if (target.getEffect(MobEffects.GLOWING) == null) {
                context.fail("Snare should apply Glowing");
                return;
            }
            if (target.getEffect(MobEffects.SLOWNESS) == null) {
                context.fail("Snare should apply Slowness");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void pillagerVolleyFiresArrows(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.PILLAGER);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new PillagerVolleyAbility().activate(player);
            if (!ok) {
                context.fail("Pillager Volley did not activate");
                return;
            }
        });

        context.runAtTickTime(10L, () -> PillagerVolleyRuntime.tick(world.getServer()));

        context.runAtTickTime(20L, () -> {
            int arrows = world.getEntitiesOfClass(Arrow.class, player.getBoundingBox().inflate(12.0D), e -> true).size();
            if (arrows <= 0) {
                context.fail("Volley should fire arrows over time");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void pillagerWarhornBuffsAlliesAndDebuffsEnemies(GameTestHelper context) {
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
        GemPlayerState.setActiveGem(player, GemId.PILLAGER);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        GemTrust.trust(player, ally.getUUID());

        context.runAtTickTime(5L, () -> {
            boolean ok = new PillagerWarhornAbility().activate(player);
            if (!ok) {
                context.fail("Pillager Warhorn did not activate");
                return;
            }
        });

        context.runAtTickTime(15L, () -> {
            if (ally.getEffect(MobEffects.SPEED) == null) {
                context.fail("Warhorn should buff allies with Speed");
                return;
            }
            if (enemy.getEffect(MobEffects.SLOWNESS) == null) {
                context.fail("Warhorn should slow enemies");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void pillagerRavageDamagesAndKnocksBack(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(target);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(target, world, pos.x, pos.y, pos.z + 2.0D, 180.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.PILLAGER);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        float before = target.getHealth();

        context.runAtTickTime(5L, () -> {
            boolean ok = new PillagerRavageAbility().activate(player);
            if (!ok) {
                context.fail("Pillager Ravage did not activate with a target");
                return;
            }
        });

        context.runAtTickTime(15L, () -> {
            if (target.getHealth() >= before) {
                context.fail("Pillager Ravage should damage the target");
                return;
            }
            if (target.getDeltaMovement().lengthSqr() <= 0.01D) {
                context.fail("Pillager Ravage should knock back the target");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void pillagerVindicatorBreakGrantsStrength(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.PILLAGER);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new PillagerVindicatorBreakAbility().activate(player);
            if (!ok) {
                context.fail("Vindicator Break did not activate");
                return;
            }
            if (player.getEffect(MobEffects.STRENGTH) == null) {
                context.fail("Vindicator Break should grant Strength");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void pillagerRaidersTrainingBoostsProjectileVelocity(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.PILLAGER);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            Arrow arrow = new Arrow(world, player, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
            arrow.setPos(player.getX(), player.getEyeY(), player.getZ());
            arrow.shoot(1.0D, 0.0D, 0.0D, 1.0F, 0.0F);
            double speed = arrow.getDeltaMovement().length();
            if (speed <= 1.0D) {
                context.fail("Raider's Training should increase projectile velocity");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void pillagerIllagerDisciplineGrantsResistanceAtLowHealth(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.PILLAGER);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            player.setHealth(2.0F);
            PillagerDiscipline.tick(player);
            if (player.getEffect(MobEffects.RESISTANCE) == null) {
                context.fail("Illager Discipline should grant Resistance at low health");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void pillagerCrossbowMasteryAutoEnchants(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.PILLAGER);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        player.setItemInHand(InteractionHand.MAIN_HAND, crossbow);

        context.runAtTickTime(5L, () -> {
            GemPowers.maintain(player);
            var quickCharge = world.registryAccess().get(Enchantments.QUICK_CHARGE).orElseThrow();
            int level = EnchantmentHelper.getItemEnchantmentLevel(quickCharge, crossbow);
            if (level <= 0) {
                context.fail("Crossbow Mastery should apply Quick Charge");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void pillagerRaiderStrideGrantsSpeed(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.PILLAGER);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            GemPowers.maintain(player);
            if (player.getEffect(MobEffects.SPEED) == null) {
                context.fail("Raider's Stride should grant Speed");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void pillagerConfigValuesValid(GameTestHelper context) {
        var cfg = GemsBalance.v().pillager();
        
        if (cfg.fangsCooldownTicks() < 0) {
            context.fail("Fangs cooldown cannot be negative");
        }
        if (cfg.snareCooldownTicks() < 0) {
            context.fail("Snare cooldown cannot be negative");
        }
        if (cfg.volleyCooldownTicks() < 0) {
            context.fail("Volley cooldown cannot be negative");
        }
        
        context.succeed();
    }
}
