package com.feel.gems.gametest.ability;

import net.minecraft.world.entity.EntityTypes;
import com.feel.gems.config.GemsBalance;
import com.feel.gems.core.GemId;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.power.ability.puff.*;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.entity.SculkSensorBlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public final class GemsPuffGameTests {

    private static void teleport(ServerPlayer player, ServerLevel world, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(world, x, y, z, EnumSet.noneOf(Relative.class), yaw, pitch, false);
    }

    private static Vec3 isolatedBase(GameTestHelper context) {
        Vec3 base = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        return new Vec3(base.x, base.y + 40.0D, base.z);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void puffDoubleJumpLaunchesPlayer(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        GemsGameTestUtil.placeStoneFloor(context, 6);
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.PUFF);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(25L, () -> {
            teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
            player.setDeltaMovement(Vec3.ZERO);
        });

        context.runAtTickTime(60L, () -> {
            BlockPos below = player.blockPosition().below();
            if (world.getBlockState(below).isAir()) {
                context.fail("Test player is not standing on a solid block");
                return;
            }
            boolean ok = new DoubleJumpAbility().activate(player);
            if (ok) {
                context.fail("Double Jump should fail while on ground");
                return;
            }
        });

        context.runAtTickTime(70L, () -> {
            // The "empty" GameTest structure has a low ceiling; teleporting high up can clamp back to the floor.
            // Instead, create a temporary hole under the player so Double Jump is guaranteed to be "midair".
            BlockPos below = BlockPos.containing(pos.x, pos.y - 1.0D, pos.z);
            world.setBlockAndUpdate(below, Blocks.AIR.defaultBlockState());
            teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
            boolean ok = new DoubleJumpAbility().activate(player);
            if (!ok) {
                context.fail("Double Jump did not activate");
                return;
            }
        });

        context.runAtTickTime(90L, () -> {
            Vec3 vel = player.getDeltaMovement();
            if (vel.y < 0.0D) {
                context.fail("Double Jump should give upward velocity");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void puffDashDamagesEnemy(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.PUFF);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        Zombie zombie = EntityTypes.ZOMBIE.create(world, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if (zombie == null) {
            context.fail("Failed to create zombie");
            return;
        }
        zombie.snapTo(pos.x, pos.y, pos.z + 2.0D, 180.0F, 0.0F);
        world.addFreshEntity(zombie);
        float before = zombie.getHealth();

        context.runAtTickTime(5L, () -> {
            boolean ok = new DashAbility().activate(player);
            if (!ok) {
                context.fail("Puff Dash did not activate");
                return;
            }
        });

        context.runAtTickTime(15L, () -> {
            if (zombie.getHealth() >= before) {
                context.fail("Dash should damage enemies in the path");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void puffGustAppliesSlowness(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer enemy = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(enemy);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(enemy, world, pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.PUFF);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new PuffGustAbility().activate(player);
            if (!ok) {
                context.fail("Puff Gust did not activate");
                return;
            }
        });

        context.runAtTickTime(15L, () -> {
            if (!enemy.hasEffect(MobEffects.SLOWNESS)) {
                context.fail("Gust should apply slowness to enemies");
                return;
            }
            if (!player.hasEffect(MobEffects.SLOW_FALLING)) {
                context.fail("Gust should apply slow falling to the caster");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void puffBreezyBashLaunchesTarget(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.PUFF);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            Zombie zombie = EntityTypes.ZOMBIE.create(world, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
            if (zombie == null) {
                context.fail("Failed to create zombie");
                return;
            }
            zombie.snapTo(pos.x, pos.y, pos.z + 2.5D, 180.0F, 0.0F);
            world.addFreshEntity(zombie);
            boolean ok = new BreezyBashAbility().activate(player);
            if (!ok) {
                context.fail("Breezy Bash did not activate");
                return;
            }
            if (zombie.getDeltaMovement().y <= 0.0D) {
                context.fail("Breezy Bash should launch the target upward");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void puffGroupBreezyBashActivates(GameTestHelper context) {
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
        GemPlayerState.setActiveGem(player, GemId.PUFF);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        GemTrust.trust(player, ally.getUUID());

        context.runAtTickTime(5L, () -> {
            boolean ok = new GroupBreezyBashAbility().activate(player);
            if (!ok) {
                context.fail("Group Breezy Bash did not activate");
                return;
            }
        });

        context.runAtTickTime(15L, () -> {
            if (enemy.getDeltaMovement().lengthSqr() <= 1.0E-4D) {
                context.fail("Group Breezy Bash should knock back untrusted players");
                return;
            }
            if (ally.getDeltaMovement().lengthSqr() > 1.0E-4D) {
                context.fail("Group Breezy Bash should not affect trusted allies");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void puffConfigValuesValid(GameTestHelper context) {
        var cfg = GemsBalance.v().puff();
        
        if (cfg.doubleJumpCooldownTicks() < 0) {
            context.fail("Double Jump cooldown cannot be negative");
        }
        if (cfg.dashCooldownTicks() < 0) {
            context.fail("Dash cooldown cannot be negative");
        }
        if (cfg.gustCooldownTicks() < 0) {
            context.fail("Gust cooldown cannot be negative");
        }
        
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void puffPassivesApplyEnchantsAndWindborne(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 6.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.PUFF);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        ItemStack bow = new ItemStack(Items.BOW);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, bow);

        context.runAtTickTime(5L, () -> {
            player.setOnGround(false);
            GemPowers.maintain(player);
            int power = EnchantmentHelper.getItemEnchantmentLevel(world.registryAccess().get(Enchantments.POWER).orElseThrow(), bow);
            int punch = EnchantmentHelper.getItemEnchantmentLevel(world.registryAccess().get(Enchantments.PUNCH).orElseThrow(), bow);
            if (power <= 0 || punch <= 0) {
                context.fail("Auto-enchant Power and Punch should apply to bows");
                return;
            }
            if (!player.hasEffect(MobEffects.SLOW_FALLING)) {
                context.fail("Windborne should grant slow falling while airborne");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void puffFallDamageImmunityPreventsDamage(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 6.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.PUFF);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        float before = player.getHealth();
        context.runAtTickTime(5L, () -> {
            player.causeFallDamage(10.0D, 1.0F, player.damageSources().fall());
            if (player.getHealth() < before) {
                context.fail("Fall damage immunity should prevent damage");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void puffCropTrampleImmunityKeepsFarmland(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        context.setBlock(0, 1, 0, Blocks.FARMLAND.defaultBlockState());
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 3.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.PUFF);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        BlockPos farmlandPos = BlockPos.containing(context.absoluteVec(new Vec3(0.0D, 1.0D, 0.0D)));

        context.runAtTickTime(5L, () -> {
            Blocks.FARMLAND.fallOn(world, world.getBlockState(farmlandPos), farmlandPos, player, 6.0D);
            if (!world.getBlockState(farmlandPos).is(Blocks.FARMLAND)) {
                context.fail("Crop trample immunity should keep farmland intact");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void puffSculkSilencePreventsSensorTrigger(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 base = isolatedBase(context);
        BlockPos pos = BlockPos.containing(base.x, base.y - 1.0D, base.z);
        world.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        world.setBlockAndUpdate(pos, Blocks.SCULK_SENSOR.defaultBlockState());
        SculkSensorBlockEntity sensor = (SculkSensorBlockEntity) world.getBlockEntity(pos);
        if (sensor == null) {
            context.fail("Failed to create sculk sensor block entity");
            return;
        }

        Vec3 playerPos = base.add(0.0D, 0.0D, 2.0D);
        teleport(player, world, playerPos.x, playerPos.y, playerPos.z, 0.0F, 0.0F);
        player.setNoGravity(true);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.PUFF);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(10L, () -> {
            world.gameEvent(GameEvent.STEP, player.position(), GameEvent.Context.of(player));
        });
        GemsGameTestUtil.assertStaysTrue(
                context,
                8L,
                80L,
                2L,
                () -> SculkSensorBlock.canActivate(world.getBlockState(pos)),
                "Sculk Silence should block sculk sensor activation"
        );
    }
}
