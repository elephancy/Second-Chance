package com.feel.gems.gametest.ability;

import net.minecraft.world.entity.EntityTypes;
import com.feel.gems.config.GemsBalance;
import com.feel.gems.core.GemId;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.power.ability.flux.*;
import com.feel.gems.power.gem.flux.FluxCharge;
import com.feel.gems.power.passive.flux.FluxCapacitorPassive;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class GemsFluxGameTests {

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
    public void fluxBeamRequiresTarget(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.FLUX);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new FluxBeamAbility().activate(player);
            if (ok) {
                context.fail("Flux Beam should fail without target");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void fluxBeamDamagesEnemyAndConsumesCharge(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.FLUX);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        Zombie zombie = EntityTypes.ZOMBIE.create(world, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if (zombie == null) {
            context.fail("Failed to create zombie");
            return;
        }
        zombie.snapTo(pos.x, pos.y, pos.z + 3.0D, 180.0F, 0.0F);
        world.addFreshEntity(zombie);
        aimAt(player, world, zombie.position().add(0.0D, 1.2D, 0.0D));

        FluxCharge.set(player, 100);
        float before = zombie.getHealth();

        context.runAtTickTime(5L, () -> {
            boolean ok = new FluxBeamAbility().activate(player);
            if (!ok) {
                context.fail("Flux Beam did not activate");
                return;
            }
            if (zombie.getHealth() >= before) {
                context.fail("Flux Beam should damage the target");
                return;
            }
            if (FluxCharge.get(player) != 0) {
                context.fail("Flux Beam should consume charge");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void fluxBeamRepairsTrustedAllyArmor(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer ally = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(ally);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(ally, world, pos.x, pos.y, pos.z + 3.0D, 180.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.FLUX);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        GemTrust.trust(player, ally.getUUID());

        ItemStack armor = new ItemStack(Items.IRON_CHESTPLATE);
        armor.setDamageValue(armor.getMaxDamage() / 2);
        ally.setItemSlot(EquipmentSlot.CHEST, armor);
        int before = armor.getDamageValue();

        FluxCharge.set(player, 120);
        aimAt(player, world, ally.position().add(0.0D, 1.2D, 0.0D));

        context.runAtTickTime(5L, () -> {
            boolean ok = new FluxBeamAbility().activate(player);
            if (!ok) {
                context.fail("Flux Beam did not activate");
                return;
            }
            int after = ally.getItemBySlot(EquipmentSlot.CHEST).getDamageValue();
            if (after >= before) {
                context.fail("Flux Beam should repair trusted ally armor");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void fluxDischargeRequiresCharge(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.FLUX);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);
        FluxCharge.set(player, 0);

        context.runAtTickTime(5L, () -> {
            boolean ok = new FluxDischargeAbility().activate(player);
            int minCharge = GemsBalance.v().flux().fluxDischargeMinCharge();
            if (minCharge > 0 && ok) {
                context.fail("Discharge should fail without enough charge");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void fluxDischargeDamagesAndResetsCharge(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.FLUX);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        Zombie zombie = EntityTypes.ZOMBIE.create(world, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if (zombie == null) {
            context.fail("Failed to create zombie");
            return;
        }
        zombie.snapTo(pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);
        world.addFreshEntity(zombie);
        float before = zombie.getHealth();

        FluxCharge.set(player, Math.max(GemsBalance.v().flux().fluxDischargeMinCharge(), 50));

        context.runAtTickTime(5L, () -> {
            boolean ok = new FluxDischargeAbility().activate(player);
            if (!ok) {
                context.fail("Flux Discharge did not activate");
                return;
            }
            if (zombie.getHealth() >= before) {
                context.fail("Flux Discharge should damage targets");
                return;
            }
            if (FluxCharge.get(player) != 0) {
                context.fail("Flux Discharge should reset charge to 0");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void fluxSurgeRequiresCharge(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.FLUX);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);
        FluxCharge.set(player, 0); // No charge

        context.runAtTickTime(5L, () -> {
            // Surge requires charge - should fail without enough
            boolean ok = new FluxSurgeAbility().activate(player);
            int cost = GemsBalance.v().flux().fluxSurgeChargeCost();
            if (cost > 0 && ok) {
                context.fail("Flux Surge should fail without enough charge");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void fluxSurgeAppliesBuffsAndKnockback(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer enemy = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(enemy);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(enemy, world, pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.FLUX);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        FluxCharge.set(player, Math.max(GemsBalance.v().flux().fluxSurgeChargeCost(), 50));

        context.runAtTickTime(5L, () -> {
            boolean ok = new FluxSurgeAbility().activate(player);
            if (!ok) {
                context.fail("Flux Surge did not activate");
                return;
            }
            if (!player.hasEffect(MobEffects.SPEED)) {
                context.fail("Flux Surge should grant speed");
                return;
            }
            if (enemy.getDeltaMovement().lengthSqr() <= 1.0E-4D) {
                context.fail("Flux Surge should knock back nearby enemies");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void fluxStaticBurstRequiresStoredDamage(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.FLUX);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            // StaticBurst requires stored damage from damage taken - should fail without any
            boolean ok = new StaticBurstAbility().activate(player);
            if (ok) {
                context.fail("Static Burst should fail without stored damage");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void fluxStaticBurstDamagesNearbyTargets(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer enemy = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(enemy);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(enemy, world, pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.FLUX);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        float before = enemy.getHealth();
        StaticBurstAbility.onDamaged(player, 6.0F);

        context.runAtTickTime(5L, () -> {
            boolean ok = new StaticBurstAbility().activate(player);
            if (!ok) {
                context.fail("Static Burst did not activate");
                return;
            }
            if (enemy.getHealth() >= before) {
                context.fail("Static Burst should damage nearby enemies");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void fluxChargeTracking(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.FLUX);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            FluxCharge.set(player, 50);
            int charge = FluxCharge.get(player);
            if (charge != 50) {
                context.fail("Flux charge should be 50, got " + charge);
            }
            FluxCharge.set(player, 75);
            charge = FluxCharge.get(player);
            if (charge != 75) {
                context.fail("Flux charge should be 75, got " + charge);
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void fluxChargeStorageConsumesFuel(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.FLUX);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND_BLOCK));
        context.runAtTickTime(5L, () -> {
            boolean ok = FluxCharge.tryConsumeChargeItem(player);
            if (!ok) {
                context.fail("Flux charge storage did not consume fuel");
                return;
            }
            if (FluxCharge.get(player) <= 0) {
                context.fail("Flux charge should increase after consuming fuel");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 300)
    public void fluxOverchargeRampIncreasesCharge(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.FLUX);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        // Overcharge only starts once charge reaches 100, and it starts counting from when 100 was reached.
        // Set to 100 and tick once to establish the "at 100" timestamp.
        FluxCharge.set(player, 100);
        context.runAtTickTime(5L, () -> FluxCharge.tickOvercharge(player));

        int delay = GemsBalance.v().flux().overchargeDelayTicks();
        int[] baseline = new int[1];
        context.runAtTickTime(5L + delay + 5L, () -> baseline[0] = FluxCharge.get(player));
        context.runAtTickTime(5L + delay + 30L, () -> {
            FluxCharge.tickOvercharge(player);
            int after = FluxCharge.get(player);
            if (after <= baseline[0]) {
                context.fail("Overcharge ramp should increase charge");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void fluxCapacitorGrantsAbsorption(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.FLUX);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        FluxCharge.set(player, GemsBalance.v().flux().fluxCapacitorChargeThreshold());
        new FluxCapacitorPassive().maintain(player);

        context.runAtTickTime(5L, () -> {
            if (!player.hasEffect(MobEffects.ABSORPTION)) {
                context.fail("Flux Capacitor should grant absorption at high charge");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void fluxConductivityAddsChargeOnDamage(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.FLUX);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        FluxCharge.set(player, 0);
        StaticBurstAbility.onDamaged(player, 6.0F);

        context.runAtTickTime(5L, () -> {
            if (FluxCharge.get(player) <= 0) {
                context.fail("Flux Conductivity should add charge on damage");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void fluxInsulationReducesDamageAtHighCharge(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer charged = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer baseline = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(charged);
        GemsGameTestUtil.forceSurvival(baseline);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(charged, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(baseline, world, pos.x + 3.0D, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(charged);
        GemPlayerState.setActiveGem(charged, GemId.FLUX);
        GemPlayerState.setEnergy(charged, 5);
        GemPowers.sync(charged);

        GemPlayerState.initIfNeeded(baseline);
        GemPlayerState.setActiveGem(baseline, GemId.FLUX);
        GemPlayerState.setEnergy(baseline, 5);
        GemPowers.sync(baseline);

        FluxCharge.set(charged, GemsBalance.v().flux().fluxInsulationChargeThreshold());
        FluxCharge.set(baseline, 0);

        context.runAtTickTime(5L, () -> {
            float damage = 6.0F;
            charged.hurtServer(world, charged.damageSources().magic(), damage);
            baseline.hurtServer(world, baseline.damageSources().magic(), damage);

            if (charged.getHealth() >= baseline.getHealth()) {
                context.succeed();
                return;
            }
            context.fail("Flux Insulation should reduce damage at high charge");
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void fluxConfigValuesValid(GameTestHelper context) {
        var cfg = GemsBalance.v().flux();
        
        if (cfg.fluxBeamCooldownTicks() < 0) {
            context.fail("Flux Beam cooldown cannot be negative");
        }
        if (cfg.fluxDischargeCooldownTicks() < 0) {
            context.fail("Discharge cooldown cannot be negative");
        }
        if (cfg.fluxSurgeCooldownTicks() < 0) {
            context.fail("Surge cooldown cannot be negative");
        }
        
        context.succeed();
    }
}
