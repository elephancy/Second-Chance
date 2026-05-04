package com.feel.gems.gametest.ability;

import net.minecraft.world.entity.EntityTypes;
import com.feel.gems.config.GemsBalance;
import com.feel.gems.core.GemId;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.power.ability.fire.CosyCampfireAbility;
import com.feel.gems.power.ability.fire.FireballAbility;
import com.feel.gems.power.ability.fire.HeatHazeZoneAbility;
import com.feel.gems.power.ability.fire.MeteorShowerAbility;
import com.feel.gems.power.runtime.AbilityRuntime;
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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;




public final class GemsFireGameTests {

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
    public void fireballLaunchesProjectile(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.FIRE);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new FireballAbility().activate(player);
            if (!ok) {
                context.fail("Fireball did not activate");
                return;
            }
        });

        context.runAtTickTime(6L, () -> new FireballAbility().activate(player));

        context.runAtTickTime(15L, () -> {
            AABB box = new AABB(player.blockPosition()).inflate(12.0D);
            int found = world.getEntitiesOfClass(LargeFireball.class, box, e -> true).size();
            if (found == 0) {
                context.fail("Expected a fireball entity to spawn");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void cosyCampfireHealsAllies(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer ally = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(ally);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(ally, world, pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.FIRE);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        GemTrust.trust(player, ally.getUUID());
        ally.setHealth(10.0F);

        context.runAtTickTime(5L, () -> {
            boolean ok = new CosyCampfireAbility().activate(player);
            if (!ok) {
                context.fail("Cosy Campfire did not activate");
                return;
            }
        });

        context.runAtTickTime(25L, () -> {
            AbilityRuntime.tickEverySecond(player);
            if (!ally.hasEffect(MobEffects.REGENERATION)) {
                context.fail("Cosy Campfire should grant regeneration to trusted allies");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void heatHazeZoneAppliesBuffsAndDebuffs(GameTestHelper context) {
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
        GemPlayerState.setActiveGem(player, GemId.FIRE);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        GemTrust.trust(player, ally.getUUID());

        context.runAtTickTime(5L, () -> {
            boolean ok = new HeatHazeZoneAbility().activate(player);
            if (!ok) {
                context.fail("Heat Haze Zone did not activate");
                return;
            }
        });

        context.runAtTickTime(25L, () -> {
            AbilityRuntime.tickEverySecond(player);
            if (!ally.hasEffect(MobEffects.FIRE_RESISTANCE)) {
                context.fail("Heat Haze Zone should grant Fire Resistance to allies");
                return;
            }
            if (!enemy.hasEffect(MobEffects.MINING_FATIGUE) || !enemy.hasEffect(MobEffects.WEAKNESS)) {
                context.fail("Heat Haze Zone should debuff enemies");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 300)
    public void meteorShowerRainsFireballs(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        // Create a target area
        Vec3 targetPos = context.absoluteVec(new Vec3(0.5D, 2.0D, 5.5D));
        // Ensure the ability's block raycast has something to hit at the target location.
        BlockPos targetBlock = BlockPos.containing(targetPos.x, targetPos.y - 1.0D, targetPos.z);
        world.setBlockAndUpdate(targetBlock, Blocks.STONE.defaultBlockState());
        var target = EntityTypes.ARMOR_STAND.create(world, e -> {}, BlockPos.containing(targetPos), EntitySpawnReason.TRIGGERED, false, false);
        if (target != null) {
            target.snapTo(targetPos.x, targetPos.y, targetPos.z, 0.0F, 0.0F);
            target.setNoGravity(true);
            world.addFreshEntity(target);
        }

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.FIRE);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);
        aimAt(player, world, Vec3.atCenterOf(targetBlock));

        context.runAtTickTime(5L, () -> {
            boolean ok = new MeteorShowerAbility().activate(player);
            if (!ok) {
                context.fail("Meteor Shower did not activate");
                return;
            }
        });

        // Meteors are fast and typically impact/despawn well before tick 40; validate shortly after activation.
        context.runAtTickTime(10L, () -> {
            AABB box = new AABB(BlockPos.containing(targetPos)).inflate(16.0D, 30.0D, 16.0D);
            int expected = GemsBalance.v().fire().meteorShowerCount();
            int found = world.getEntitiesOfClass(LargeFireball.class, box, e -> e.entityTags().contains("gems_meteor")).size();
            if (found < expected) {
                context.fail("Expected at least " + expected + " meteors, found " + found);
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void fireAutoEnchantAppliesFireAspect(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.FIRE);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, sword);

        context.runAtTickTime(5L, () -> {
            GemPowers.maintain(player);
            var fireAspect = world.registryAccess().get(Enchantments.FIRE_ASPECT).orElseThrow();
            int level = EnchantmentHelper.getItemEnchantmentLevel(fireAspect, sword);
            if (level <= 0) {
                context.fail("Auto-enchant Fire Aspect should apply to melee weapons");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void fireAutoSmeltDropsSmeltedItems(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        BlockPos blockPos = BlockPos.containing(pos);
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.FIRE);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        ItemStack tool = new ItemStack(Items.IRON_PICKAXE);
        context.runAtTickTime(5L, () -> {
            var drops = Block.getDrops(Blocks.IRON_ORE.defaultBlockState(), world, blockPos, null, player, tool);
            boolean hasIngot = drops.stream().anyMatch(stack -> stack.is(Items.IRON_INGOT));
            if (!hasIngot) {
                context.fail("Auto-smelt should convert ore drops into ingots");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void fireballHasCooldownConfigured(GameTestHelper context) {
        // Direct activate() bypasses cooldown system (handled by GemAbilities.activateByIndex)
        // Just verify the cooldown config is valid
        var cfg = GemsBalance.v().fire();
        if (cfg.fireballInternalCooldownTicks() <= 0) {
            context.fail("Fireball cooldown must be positive");
        }
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void fireConfigValuesValid(GameTestHelper context) {
        var cfg = GemsBalance.v().fire();
        
        if (cfg.fireballInternalCooldownTicks() < 0) {
            context.fail("Fireball cooldown cannot be negative");
        }
        if (cfg.cosyCampfireCooldownTicks() < 0) {
            context.fail("Cosy Campfire cooldown cannot be negative");
        }
        if (cfg.heatHazeCooldownTicks() < 0) {
            context.fail("Heat Haze Zone cooldown cannot be negative");
        }
        if (cfg.meteorShowerCooldownTicks() < 0) {
            context.fail("Meteor Shower cooldown cannot be negative");
        }
        
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void fireImmunityPassiveGrantsFireResistance(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.FIRE);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            GemPowers.maintain(player);
            if (!player.hasEffect(MobEffects.FIRE_RESISTANCE)) {
                context.fail("Fire Resistance passive should apply the effect");
                return;
            }
            context.succeed();
        });
    }
}
