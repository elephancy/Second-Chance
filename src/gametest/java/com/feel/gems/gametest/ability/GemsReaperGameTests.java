package com.feel.gems.gametest.ability;

import net.minecraft.world.entity.EntityTypes;
import com.feel.gems.config.GemsBalance;
import com.feel.gems.core.GemId;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.power.ability.reaper.ReaperBloodChargeAbility;
import com.feel.gems.power.ability.reaper.ReaperDeathOathAbility;
import com.feel.gems.power.ability.reaper.ReaperGraveSteedAbility;
import com.feel.gems.power.ability.reaper.ReaperRetributionAbility;
import com.feel.gems.power.ability.reaper.ReaperScytheSweepAbility;
import com.feel.gems.power.ability.reaper.ReaperShadowCloneAbility;
import com.feel.gems.power.ability.reaper.ReaperWitheringStrikesAbility;
import com.feel.gems.power.gem.reaper.ReaperBloodCharge;
import com.feel.gems.power.runtime.AbilityRuntime;
import com.feel.gems.power.runtime.GemPowers;
import com.feel.gems.state.GemPlayerState;
import java.util.EnumSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class GemsReaperGameTests {

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
    public void bloodChargeStoresMultiplier(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.REAPER);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean started = new ReaperBloodChargeAbility().activate(player);
            if (started) {
                context.fail("Blood Charge should not consume on start");
            }
            if (!ReaperBloodCharge.isCharging(player)) {
                context.fail("Blood Charge should start charging");
            }
            AbilityRuntime.tickEverySecond(player);
            boolean stored = new ReaperBloodChargeAbility().activate(player);
            if (!stored) {
                context.fail("Blood Charge should store a buff on second activation");
            }
            float mult = ReaperBloodCharge.consumeMultiplierIfActive(player);
            if (mult <= 1.0F) {
                context.fail("Blood Charge should grant a damage multiplier");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void scytheSweepDamagesNearby(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        Zombie enemy = EntityTypes.ZOMBIE.create(world, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if (enemy == null) {
            context.fail("Failed to create zombie");
            return;
        }
        enemy.snapTo(pos.x, pos.y, pos.z + 2.0D, 0.0F, 0.0F);
        world.addFreshEntity(enemy);
        aimAt(player, world, enemy.position().add(0.0D, 1.2D, 0.0D));

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.REAPER);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new ReaperScytheSweepAbility().activate(player);
            if (!ok) {
                context.fail("Scythe Sweep did not activate");
            }
            if (enemy.getHealth() >= enemy.getMaxHealth()) {
                context.fail("Scythe Sweep should damage enemies");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void witheringStrikesAppliesWither(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.REAPER);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new ReaperWitheringStrikesAbility().activate(player);
            if (!ok) {
                context.fail("Withering Strikes did not activate");
            }
        });

        context.runAtTickTime(15L, () -> {
            Zombie target = EntityTypes.ZOMBIE.create(world, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
            if (target == null) {
                context.fail("Failed to create zombie");
                return;
            }
            target.snapTo(pos.x, pos.y, pos.z + 2.0D, 0.0F, 0.0F);
            world.addFreshEntity(target);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, Items.DIAMOND_SWORD.getDefaultInstance());
            player.attack(target);
            if (!target.hasEffect(MobEffects.WITHER)) {
                context.fail("Withering Strikes should apply Wither on hit");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void shadowCloneSpawnsClone(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.REAPER);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            int before = world.getEntitiesOfClass(com.feel.gems.entity.ShadowCloneEntity.class, player.getBoundingBox().inflate(8.0D), e -> true).size();
            boolean ok = new ReaperShadowCloneAbility().activate(player);
            if (!ok) {
                context.fail("Shadow Clone did not activate");
            }
            int after = world.getEntitiesOfClass(com.feel.gems.entity.ShadowCloneEntity.class, player.getBoundingBox().inflate(8.0D), e -> true).size();
            if (after <= before) {
                context.fail("Shadow Clone should spawn clones");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void retributionReflectsDamage(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        Zombie attacker = EntityTypes.ZOMBIE.create(world, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        GemsGameTestUtil.forceSurvival(player);

        if (attacker == null) {
            context.fail("Failed to create zombie");
            return;
        }

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        attacker.snapTo(pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);
        world.addFreshEntity(attacker);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.REAPER);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new ReaperRetributionAbility().activate(player);
            if (!ok) {
                context.fail("Retribution did not activate");
            }
            float beforePlayer = player.getHealth();
            float beforeAttacker = attacker.getHealth();
            player.hurtServer(world, player.damageSources().mobAttack(attacker), 6.0F);
            if (player.getHealth() < beforePlayer) {
                context.fail("Retribution should prevent incoming damage");
            }
            if (attacker.getHealth() >= beforeAttacker) {
                context.fail("Retribution should reflect damage to the attacker");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void deathOathMarksTarget(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        Zombie target = EntityTypes.ZOMBIE.create(world, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if (target == null) {
            context.fail("Failed to create zombie");
            return;
        }
        target.snapTo(pos.x, pos.y, pos.z + 3.0D, 180.0F, 0.0F);
        world.addFreshEntity(target);
        aimAt(player, world, target.position().add(0.0D, 1.2D, 0.0D));

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.REAPER);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new ReaperDeathOathAbility().activate(player);
            if (!ok) {
                context.fail("Death Oath did not activate");
            }
            if (!AbilityRuntime.isReaperDeathOathActive(player)) {
                context.fail("Death Oath should mark a target");
            }
            if (!target.getUUID().equals(AbilityRuntime.reaperDeathOathTarget(player))) {
                context.fail("Death Oath should store the target UUID");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void graveSteedSummonsHorse(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.REAPER);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new ReaperGraveSteedAbility().activate(player);
            if (!ok) {
                context.fail("Grave Steed did not activate");
            }
            SkeletonHorse horse = world.getEntitiesOfClass(SkeletonHorse.class, player.getBoundingBox().inflate(6.0D), e -> true)
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (horse == null) {
                context.fail("Grave Steed should spawn a skeleton horse");
            }
            if (!player.isPassenger()) {
                context.fail("Grave Steed should mount the player");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void reaperPassivesApply(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.REAPER);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            ItemStack food = Items.ROTTEN_FLESH.getDefaultInstance();
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.HUNGER, 200, 0));
            food.finishUsingItem(world, player);
        });

        context.runAtTickTime(25L, () -> {
            if (player.hasEffect(MobEffects.HUNGER)) {
                context.fail("Rot Eater should clear hunger from rotten flesh");
                return;
            }

            Zombie undead = EntityTypes.ZOMBIE.create(world, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
            if (undead == null) {
                context.fail("Failed to create zombie");
                return;
            }
            undead.snapTo(pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);
            world.addFreshEntity(undead);
            float before = player.getHealth();
            player.hurtServer(world, player.damageSources().mobAttack(undead), 6.0F);
            float delta = before - player.getHealth();
            float expectedMax = 6.0F * GemsBalance.v().reaper().undeadWardDamageMultiplier() + 0.1F;
            if (delta > expectedMax) {
                context.fail("Undead Ward should reduce damage from undead");
            }

            undead.setHealth(1.0F);
            undead.hurtServer(world, world.damageSources().playerAttack(player), 10.0F);
            if (!player.hasEffect(MobEffects.REGENERATION)) {
                context.fail("Harvest should grant regeneration on mob kills");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void reaperConfigValuesValid(GameTestHelper context) {
        var cfg = GemsBalance.v().reaper();
        
        if (cfg.scytheSweepCooldownTicks() < 0) {
            context.fail("Scythe Sweep cooldown cannot be negative");
        }
        if (cfg.witheringStrikesCooldownTicks() < 0) {
            context.fail("Withering Strikes cooldown cannot be negative");
        }
        if (cfg.shadowCloneCooldownTicks() < 0) {
            context.fail("Shadow Clone cooldown cannot be negative");
        }
        if (cfg.retributionCooldownTicks() < 0) {
            context.fail("Retribution cooldown cannot be negative");
        }
        if (cfg.deathOathCooldownTicks() < 0) {
            context.fail("Death Oath cooldown cannot be negative");
        }
        if (cfg.graveSteedCooldownTicks() < 0) {
            context.fail("Grave Steed cooldown cannot be negative");
        }
        
        context.succeed();
    }
}
