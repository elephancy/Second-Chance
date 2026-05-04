package com.feel.gems.gametest.ability;

import net.minecraft.world.entity.EntityTypes;
import com.feel.gems.config.GemsBalance;
import com.feel.gems.core.GemId;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.power.ability.space.*;
import com.feel.gems.power.gem.space.SpaceAnomalies;
import com.feel.gems.power.gem.space.SpaceLunarScaling;
import com.feel.gems.power.runtime.AbilityRuntime;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class GemsSpaceGameTests {

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
    public void spaceGravityFieldActivates(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer enemy = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(enemy);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(enemy, world, pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SPACE);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new SpaceGravityFieldAbility().activate(player);
            if (!ok) {
                context.fail("Gravity Field did not activate");
            }
            double before = enemy.getAttributeValue(Attributes.GRAVITY);
            AbilityRuntime.tickEverySecond(player);
            double after = enemy.getAttributeValue(Attributes.GRAVITY);
            if (before == after) {
                context.fail("Gravity Field should modify gravity for nearby enemies");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void spaceBlackHoleActivates(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        GemsGameTestUtil.placeStoneFloor(context, 10);
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SPACE);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        Zombie target = EntityTypes.ZOMBIE.create(world, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if (target == null) {
            context.fail("Failed to create zombie");
            return;
        }
        target.snapTo(pos.x + 3.0D, pos.y, pos.z, 0.0F, 0.0F);
        // Disable AI and gravity so the zombie doesn't walk toward player or fall,
        // allowing us to isolate the black hole's pull effect on velocity.
        target.setNoAi(true);
        target.setNoGravity(true);
        world.addFreshEntity(target);
        Vec3 center = player.position().add(0.0D, 0.2D, 0.0D);

        context.runAtTickTime(5L, () -> {
            boolean ok = new SpaceBlackHoleAbility().activate(player);
            if (!ok) {
                context.fail("Black Hole did not activate");
            }
        });

        // Tick the anomaly system continuously. Physics runs when world time >= nextPhysicsTick
        // (which is ~20 ticks after spawn). We tick frequently to ensure the stride-based
        // physics logic fires and accumulates velocity on the target.
        for (long t = 6L; t <= 50L; t++) {
            context.runAtTickTime(t, () -> SpaceAnomalies.tick(world.getServer()));
        }

        context.runAtTickTime(55L, () -> {
            Vec3 delta = center.subtract(target.position());
            Vec3 velocity = target.getDeltaMovement();
            // The black hole should have applied inward velocity toward the center.
            // Check that velocity has a positive component toward the center (dot product > 0).
            if (velocity.lengthSqr() < 0.0001D || velocity.dot(delta) <= 0.0D) {
                context.fail("Black Hole should pull targets inward");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void spaceWhiteHoleActivates(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SPACE);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        Zombie target = EntityTypes.ZOMBIE.create(world, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if (target == null) {
            context.fail("Failed to create zombie");
            return;
        }
        target.snapTo(pos.x + 2.5D, pos.y, pos.z, 0.0F, 0.0F);
        world.addFreshEntity(target);
        Vec3 center = player.position().add(0.0D, 0.2D, 0.0D);

        context.runAtTickTime(5L, () -> {
            boolean ok = new SpaceWhiteHoleAbility().activate(player);
            if (!ok) {
                context.fail("White Hole did not activate");
            }
        });

        context.runAtTickTime(30L, () -> {
            SpaceAnomalies.tick(world.getServer());
            Vec3 delta = target.position().subtract(center);
            if (target.getDeltaMovement().dot(delta) <= 0.0D) {
                context.fail("White Hole should push targets outward");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void spaceOrbitalLaserActivates(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        // Keep a minimal platform (ray targeting is sensitive to nearby blocks in GameTest).
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                context.setBlock(x, 1, z, Blocks.STONE.defaultBlockState());
            }
        }
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SPACE);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        BlockPos target = new BlockPos((int) pos.x, (int) pos.y, (int) pos.z + 6);
        world.setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());
        Zombie victim = EntityTypes.ZOMBIE.create(world, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if (victim == null) {
            context.fail("Failed to create zombie");
            return;
        }
        victim.snapTo(target.getX() + 0.5D, target.getY() + 1.0D, target.getZ() + 0.5D, 0.0F, 0.0F);
        victim.setNoAi(true);
        victim.setNoGravity(true);
        world.addFreshEntity(victim);
        aimAt(player, world, Vec3.atCenterOf(target));

        float[] before = new float[1];
        context.runAtTickTime(4L, () -> before[0] = victim.getHealth());

        context.runAtTickTime(5L, () -> {
            player.setShiftKeyDown(false);
            boolean ok = new SpaceOrbitalLaserAbility().activate(player);
            if (!ok) {
                context.fail("Orbital Laser did not activate");
            }
        });

        int delay = Math.max(1, GemsBalance.v().space().orbitalLaserDelayTicks());
        context.runAtTickTime(5L + delay + 5L, () -> {
            if (victim.getHealth() >= before[0]) {
                context.fail("Orbital Laser should damage targets at the strike location");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void spacePassivesApply(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        GemsGameTestUtil.placeStoneFloor(context, 10);
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SPACE);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            GemPowers.maintain(player);
            if (!player.hasEffect(MobEffects.SLOW_FALLING)) {
                context.fail("Low Gravity should apply slow falling");
            }
            world.clockManager().setTotalTicks(world.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_CLOCK).getOrThrow(net.minecraft.world.clock.WorldClocks.OVERWORLD), 0L);
            float full = SpaceLunarScaling.multiplier(world);
            world.clockManager().setTotalTicks(world.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_CLOCK).getOrThrow(net.minecraft.world.clock.WorldClocks.OVERWORLD), 4L * 24000L);
            float newMoon = SpaceLunarScaling.multiplier(world);
            if (full <= newMoon) {
                context.fail("Lunar Scaling should be stronger at full moon");
            }

            world.clockManager().setTotalTicks(world.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_CLOCK).getOrThrow(net.minecraft.world.clock.WorldClocks.OVERWORLD), 18000L);
            Skeleton shooter = EntityTypes.SKELETON.create(world, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
            if (shooter == null) {
                context.fail("Failed to create skeleton");
                return;
            }
            shooter.snapTo(player.getX() + 2.0D, player.getY(), player.getZ(), 0.0F, 0.0F);
            shooter.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
            world.addFreshEntity(shooter);
            net.minecraft.world.entity.projectile.arrow.Arrow arrow = new net.minecraft.world.entity.projectile.arrow.Arrow(
                    world,
                    shooter,
                    new ItemStack(Items.ARROW),
                    shooter.getMainHandItem()
            );
            float before = player.getHealth();
            player.hurtServer(world, player.damageSources().arrow(arrow, shooter), 6.0F);
            float delta = before - player.getHealth();
            float max = 6.0F * GemsBalance.v().space().starshieldProjectileDamageMultiplier() + 0.1F;
            if (delta > max) {
                context.fail("Starshield should reduce projectile damage at night");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void spaceConfigValuesValid(GameTestHelper context) {
        var cfg = GemsBalance.v().space();
        
        if (cfg.gravityFieldCooldownTicks() < 0) {
            context.fail("Gravity Field cooldown cannot be negative");
        }
        if (cfg.blackHoleCooldownTicks() < 0) {
            context.fail("Black Hole cooldown cannot be negative");
        }
        if (cfg.whiteHoleCooldownTicks() < 0) {
            context.fail("White Hole cooldown cannot be negative");
        }
        
        context.succeed();
    }
}
