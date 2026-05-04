package com.feel.gems.gametest.ability;

import com.feel.gems.config.GemsBalance;
import com.feel.gems.core.GemId;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.power.ability.air.AirCrosswindAbility;
import com.feel.gems.power.ability.air.AirDashAbility;
import com.feel.gems.power.ability.air.AirGaleSlamAbility;
import com.feel.gems.power.ability.air.AirWindJumpAbility;
import com.feel.gems.power.runtime.GemPowers;
import com.feel.gems.state.GemPlayerState;
import java.util.EnumSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

public final class GemsAirGameTests {

    private static void teleport(ServerPlayer player, ServerLevel world, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(world, x, y, z, EnumSet.noneOf(Relative.class), yaw, pitch, false);
    }

    private static ServerPlayer setupAirPlayer(GameTestHelper context, Vec3 pos, float yaw, float pitch) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        teleport(player, world, pos.x, pos.y, pos.z, yaw, pitch);
        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.AIR);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);
        return player;
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void airWindJumpLaunchesPlayer(GameTestHelper context) {
        GemsGameTestUtil.placeStoneFloor(context, 6);
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer player = setupAirPlayer(context, pos, 0.0F, 0.0F);
        Vec3 start = player.position();

        context.runAtTickTime(5L, () -> {
            boolean ok = new AirWindJumpAbility().activate(player);
            if (!ok) {
                context.fail("Wind Jump did not activate");
            }
        });

        context.runAtTickTime(7L, () -> {
            Vec3 vel = player.getDeltaMovement();
            if (vel.y <= 0.05D) {
                context.fail("Wind Jump did not launch player upward");
            }
            double forward = GemsBalance.v().air().windJumpForwardVelocity();
            if (forward > 0.0D && vel.horizontalDistanceSqr() <= 0.01D) {
                context.fail("Wind Jump did not apply forward velocity");
            }
        });

        context.runAtTickTime(15L, () -> {
            Vec3 now = player.position();
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void airDashGrantsResistanceAndVelocity(GameTestHelper context) {
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer player = setupAirPlayer(context, pos, 0.0F, 0.0F);

        context.runAtTickTime(5L, () -> {
            boolean ok = new AirDashAbility().activate(player);
            if (!ok) {
                context.fail("Air Dash did not activate");
            }
        });

        context.runAtTickTime(15L, () -> {
            Vec3 vel = player.getDeltaMovement();
            if (vel.lengthSqr() <= 0.05D) {
                context.fail("Air Dash did not apply velocity");
            }
            int iFrameTicks = GemsBalance.v().air().dashIFrameDurationTicks();
            if (iFrameTicks > 0 && player.getEffect(MobEffects.RESISTANCE) == null) {
                context.fail("Air Dash should grant Resistance during i-frame window");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void airCrosswindDamagesAndSlowsEnemy(GameTestHelper context) {
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer player = setupAirPlayer(context, pos, 0.0F, 0.0F);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(target);
        teleport(target, context.getLevel(), pos.x + 3.0D, pos.y, pos.z, 180.0F, 0.0F);
        float healthBefore = target.getHealth();

        context.runAtTickTime(5L, () -> {
            boolean ok = new AirCrosswindAbility().activate(player);
            if (!ok) {
                context.fail("Crosswind did not activate");
            }
        });

        context.runAtTickTime(15L, () -> {
            if (target.getHealth() >= healthBefore) {
                context.fail("Crosswind did not damage the enemy");
            }
            if (target.getEffect(MobEffects.SLOWNESS) == null) {
                context.fail("Crosswind should apply Slowness to enemies");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void airGaleSlamDamagesNearbyTargets(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 3.0D, 0.5D));
        ServerPlayer player = setupAirPlayer(context, pos, 0.0F, 0.0F);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer bystander = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(target);
        GemsGameTestUtil.forceSurvival(bystander);
        teleport(target, world, pos.x, pos.y, pos.z + 2.0D, 180.0F, 0.0F);
        teleport(bystander, world, pos.x + 1.5D, pos.y, pos.z + 2.0D, 180.0F, 0.0F);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MACE));
        float bystanderBefore = bystander.getHealth();

        context.runAtTickTime(5L, () -> {
            boolean ok = new AirGaleSlamAbility().activate(player);
            if (!ok) {
                context.fail("Gale Slam did not activate");
            }
        });

        context.runAtTickTime(10L, () -> player.attack(target));

        context.runAtTickTime(20L, () -> {
            if (bystander.getHealth() >= bystanderBefore) {
                context.fail("Gale Slam should damage nearby targets on hit");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void airMacePassiveGrantsEnchantedMace(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer player = setupAirPlayer(context, pos, 0.0F, 0.0F);

        context.runAtTickTime(5L, () -> {
            GemPowers.maintain(player);
            if (!GemsGameTestUtil.containsAirMace(player)) {
                context.fail("Windburst Mace passive should grant an Air Mace");
                return;
            }
            ItemStack mace = ItemStack.EMPTY;
            var inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.is(Items.MACE)) {
                    mace = stack;
                    break;
                }
            }
            if (mace.isEmpty()) {
                context.fail("Expected player to have an Air Mace");
                return;
            }
            var breachEntry = world.registryAccess().get(Enchantments.BREACH).orElseThrow();
            var windBurstEntry = world.registryAccess().get(Enchantments.WIND_BURST).orElseThrow();
            var fireAspectEntry = world.registryAccess().get(Enchantments.FIRE_ASPECT).orElseThrow();
            int breach = EnchantmentHelper.getItemEnchantmentLevel(breachEntry, mace);
            int windBurst = EnchantmentHelper.getItemEnchantmentLevel(windBurstEntry, mace);
            int fireAspect = EnchantmentHelper.getItemEnchantmentLevel(fireAspectEntry, mace);
            var cfg = GemsBalance.v().air();
            if (cfg.airMaceBreachLevel() > 0 && breach < cfg.airMaceBreachLevel()) {
                context.fail("Air mace should have Breach enchantment");
                return;
            }
            if (cfg.airMaceWindBurstLevel() > 0 && windBurst < cfg.airMaceWindBurstLevel()) {
                context.fail("Air mace should have Wind Burst enchantment");
                return;
            }
            if (cfg.airMaceFireAspectLevel() > 0 && fireAspect < cfg.airMaceFireAspectLevel()) {
                context.fail("Air mace should have Fire Aspect enchantment");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void airWindShearAddsSlownessOnHit(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer player = setupAirPlayer(context, pos, 0.0F, 0.0F);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(target);
        teleport(target, world, pos.x, pos.y, pos.z + 2.0D, 180.0F, 0.0F);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MACE));

        context.runAtTickTime(5L, () -> player.attack(target));

        context.runAtTickTime(15L, () -> {
            if (target.getEffect(MobEffects.SLOWNESS) == null) {
                context.fail("Wind Shear should apply Slowness on hit");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void airAerialGuardReducesDamage(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer guard = setupAirPlayer(context, pos, 0.0F, 0.0F);
        guard.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MACE));

        ServerPlayer control = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(control);
        teleport(control, world, pos.x + 3.0D, pos.y, pos.z, 180.0F, 0.0F);

        ServerPlayer attacker = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(attacker);
        teleport(attacker, world, pos.x + 1.5D, pos.y, pos.z + 3.0D, 0.0F, 0.0F);

        context.runAtTickTime(5L, () -> {
            float guardBefore = guard.getHealth();
            float controlBefore = control.getHealth();
            guard.hurtServer(world, attacker.damageSources().playerAttack(attacker), 6.0F);
            control.hurtServer(world, attacker.damageSources().playerAttack(attacker), 6.0F);
            float guardTaken = guardBefore - guard.getHealth();
            float controlTaken = controlBefore - control.getHealth();
            if (guardTaken >= controlTaken) {
                context.fail("Aerial Guard should reduce incoming damage");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void airAerialGuardReducesKnockback(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer guard = setupAirPlayer(context, pos, 0.0F, 0.0F);
        guard.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MACE));

        ServerPlayer control = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(control);
        teleport(control, world, pos.x + 3.0D, pos.y, pos.z, 180.0F, 0.0F);

        context.runAtTickTime(5L, () -> {
            guard.setDeltaMovement(Vec3.ZERO);
            control.setDeltaMovement(Vec3.ZERO);
            guard.knockback(1.0D, 1.0D, 0.0D, world.damageSources().generic(), 1.0F);
            control.knockback(1.0D, 1.0D, 0.0D, world.damageSources().generic(), 1.0F);
        });

        context.runAtTickTime(10L, () -> {
            double guardKb = guard.getDeltaMovement().lengthSqr();
            double controlKb = control.getDeltaMovement().lengthSqr();
            if (guardKb >= controlKb) {
                context.fail("Aerial Guard should reduce knockback while holding the mace");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void airAerialGuardReducesFallDamage(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 6.0D, 0.5D));
        ServerPlayer guard = setupAirPlayer(context, pos, 0.0F, 0.0F);
        guard.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MACE));

        ServerPlayer control = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(control);
        teleport(control, world, pos.x + 3.0D, pos.y, pos.z, 180.0F, 0.0F);

        context.runAtTickTime(5L, () -> {
            float guardBefore = guard.getHealth();
            float controlBefore = control.getHealth();
            guard.causeFallDamage(10.0D, 1.0F, guard.damageSources().fall());
            control.causeFallDamage(10.0D, 1.0F, control.damageSources().fall());
            float guardTaken = guardBefore - guard.getHealth();
            float controlTaken = controlBefore - control.getHealth();
            if (guardTaken >= controlTaken) {
                context.fail("Aerial Guard should reduce fall damage");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void airConfigValuesValid(GameTestHelper context) {
        var cfg = GemsBalance.v().air();

        if (cfg.windJumpCooldownTicks() < 0) {
            context.fail("Wind Jump cooldown cannot be negative");
        }
        if (cfg.dashCooldownTicks() < 0) {
            context.fail("Dash cooldown cannot be negative");
        }
        if (cfg.crosswindCooldownTicks() < 0) {
            context.fail("Crosswind cooldown cannot be negative");
        }
        if (cfg.galeSlamCooldownTicks() < 0) {
            context.fail("Gale Slam cooldown cannot be negative");
        }

        context.succeed();
    }
}
