package com.feel.gems.gametest.ability;

import net.minecraft.world.entity.EntityTypes;
import com.feel.gems.config.GemsBalance;
import com.feel.gems.core.GemId;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.power.ability.terror.*;
import com.feel.gems.power.gem.terror.TerrorBloodPrice;
import com.feel.gems.power.gem.terror.TerrorRemoteChargeRuntime;
import com.feel.gems.power.gem.terror.TerrorRigRuntime;
import com.feel.gems.power.runtime.GemPowers;
import com.feel.gems.state.GemPlayerState;
import java.util.EnumSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class GemsTerrorGameTests {

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
    public void terrorPanicRingActivates(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.TERROR);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            int before = world.getEntitiesOfClass(PrimedTnt.class, player.getBoundingBox().inflate(16.0D), e -> true).size();
            boolean ok = new PanicRingAbility().activate(player);
            if (!ok) {
                context.fail("Panic Ring did not activate");
            }
            int after = world.getEntitiesOfClass(PrimedTnt.class, player.getBoundingBox().inflate(16.0D), e -> true).size();
            int expected = GemsBalance.v().terror().panicRingTntCount();
            if (after - before < expected) {
                context.fail("Panic Ring should spawn the configured TNT count");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void terrorBreachChargeActivates(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.TERROR);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        Zombie target = EntityTypes.ZOMBIE.create(world, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if (target == null) {
            context.fail("Failed to create zombie");
            return;
        }
        target.snapTo(pos.x, pos.y, pos.z + 3.0D, 180.0F, 0.0F);
        world.addFreshEntity(target);
        aimAt(player, world, target.position().add(0.0D, 1.2D, 0.0D));
        float before = target.getHealth();

        context.runAtTickTime(5L, () -> {
            boolean ok = new TerrorBreachChargeAbility().activate(player);
            if (!ok) {
                context.fail("Breach Charge did not activate");
            }
            if (target.getHealth() >= before) {
                context.fail("Breach Charge should damage targets");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void terrorRemoteChargeActivates(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.TERROR);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        BlockPos target = new BlockPos((int) pos.x, (int) pos.y - 1, (int) pos.z + 2);
        world.setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());
        aimAt(player, world, Vec3.atCenterOf(target));

        context.runAtTickTime(5L, () -> {
            boolean ok = new TerrorRemoteChargeAbility().activate(player);
            if (!ok) {
                context.fail("Remote Charge should begin arming");
            }
            if (!TerrorRemoteChargeRuntime.isArming(player)) {
                context.fail("Remote Charge should enter arming state");
            }
            if (!TerrorRemoteChargeRuntime.tryArm(player, target)) {
                context.fail("Remote Charge should arm on a block");
            }
            if (!TerrorRemoteChargeRuntime.hasActiveCharge(player)) {
                context.fail("Remote Charge should have an active charge after arming");
            }
        });

        context.runAtTickTime(20L, () -> {
            int before = world.getEntitiesOfClass(PrimedTnt.class, player.getBoundingBox().inflate(16.0D), e -> true).size();
            boolean detonated = new TerrorRemoteChargeAbility().activate(player);
            if (!detonated) {
                context.fail("Remote Charge should detonate when armed");
            }
            int after = world.getEntitiesOfClass(PrimedTnt.class, player.getBoundingBox().inflate(16.0D), e -> true).size();
            if (after <= before) {
                context.fail("Remote Charge detonation should spawn TNT");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void terrorRigActivates(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer enemy = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(enemy);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        BlockPos rigPos = new BlockPos((int) pos.x, (int) pos.y - 1, (int) pos.z + 2);
        world.setBlockAndUpdate(rigPos, Blocks.STONE.defaultBlockState());
        aimAt(player, world, Vec3.atCenterOf(rigPos));

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.TERROR);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new TerrorRigAbility().activate(player);
            if (!ok) {
                context.fail("Rig did not activate");
            }
            if (!TerrorRigRuntime.hasTrap(world, rigPos)) {
                context.fail("Rig should arm the target block");
            }
        });

        context.runAtTickTime(20L, () -> {
            int before = world.getEntitiesOfClass(PrimedTnt.class, player.getBoundingBox().inflate(16.0D), e -> true).size();
            teleport(enemy, world, rigPos.getX() + 0.5D, rigPos.getY() + 1.1D, rigPos.getZ() + 0.5D, 0.0F, 0.0F);
            TerrorRigRuntime.checkStep(enemy);
            int after = world.getEntitiesOfClass(PrimedTnt.class, player.getBoundingBox().inflate(16.0D), e -> true).size();
            if (after <= before) {
                context.fail("Rig should detonate when stepped on");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void terrorTradeActivates(GameTestHelper context) {
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
        GemPlayerState.setActiveGem(player, GemId.TERROR);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new TerrorTradeAbility().activate(player);
            if (!ok) {
                context.fail("Terror Trade did not activate");
            }
        });

        context.runAtTickTime(20L, () -> {
            if (target.isAlive()) {
                context.fail("Terror Trade should kill the target");
            }
            if (player.isAlive()) {
                context.fail("Terror Trade should kill the caster");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void terrorPassivesApply(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer enemy = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(enemy);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(enemy, world, pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.TERROR);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.DARKNESS, 200, 0));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.BLINDNESS, 200, 0));

        context.runAtTickTime(5L, () -> {
            GemPowers.maintain(player);
            if (!enemy.hasEffect(MobEffects.DARKNESS)) {
                context.fail("Dread Aura should apply Darkness to nearby enemies");
            }
            if (player.hasEffect(MobEffects.DARKNESS) || player.hasEffect(MobEffects.BLINDNESS)) {
                context.fail("Fearless should clear Darkness and Blindness");
            }
            TerrorBloodPrice.onPlayerKill(player);
            if (!player.hasEffect(MobEffects.STRENGTH) || !player.hasEffect(MobEffects.RESISTANCE)) {
                context.fail("Blood Price should grant Strength and Resistance");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void terrorConfigValuesValid(GameTestHelper context) {
        var cfg = GemsBalance.v().terror();
        
        if (cfg.panicRingCooldownTicks() < 0) {
            context.fail("Panic Ring cooldown cannot be negative");
        }
        if (cfg.breachChargeCooldownTicks() < 0) {
            context.fail("Breach Charge cooldown cannot be negative");
        }
        if (cfg.remoteChargeCooldownTicks() < 0) {
            context.fail("Remote Charge cooldown cannot be negative");
        }
        
        context.succeed();
    }
}
