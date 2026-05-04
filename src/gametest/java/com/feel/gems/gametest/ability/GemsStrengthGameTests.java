package com.feel.gems.gametest.ability;

import net.minecraft.world.entity.EntityTypes;
import com.feel.gems.config.GemsBalance;
import com.feel.gems.core.GemId;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.power.runtime.AbilityRuntime;
import com.feel.gems.power.ability.strength.*;
import com.feel.gems.power.runtime.GemPowers;
import com.feel.gems.state.GemPlayerState;
import com.feel.gems.state.GemsPersistentDataHolder;
import com.feel.gems.util.GemsNbt;
import java.util.EnumSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class GemsStrengthGameTests {

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
    public void strengthChadStrengthActivates(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.STRENGTH);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new ChadStrengthAbility().activate(player);
            if (!ok) {
                context.fail("Chad Strength did not activate");
            }
            if (!AbilityRuntime.isChadStrengthActive(player)) {
                context.fail("Chad Strength should be active after activation");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void strengthFrailerAppliesWeakness(GameTestHelper context) {
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
        GemPlayerState.setActiveGem(player, GemId.STRENGTH);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new FrailerAbility().activate(player);
            if (!ok) {
                context.fail("Frailer did not activate");
            }
            if (!target.hasEffect(MobEffects.WEAKNESS)) {
                context.fail("Frailer should apply Weakness");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void strengthNullifyClearsEnemyEffects(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer ally = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(ally);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        Zombie target = EntityTypes.ZOMBIE.create(world, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if (target == null) {
            context.fail("Failed to create zombie");
            return;
        }
        target.snapTo(pos.x + 2.0D, pos.y, pos.z, 180.0F, 0.0F);
        world.addFreshEntity(target);

        teleport(ally, world, pos.x + 1.0D, pos.y, pos.z + 1.0D, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.STRENGTH);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        com.feel.gems.trust.GemTrust.trust(player, ally.getUUID());
        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
        ally.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.WEAKNESS, 200, 0));

        context.runAtTickTime(5L, () -> {
            // Nullify activates the buff mode - effect applies on hit
            boolean ok = new NullifyAbility().activate(player);
            if (!ok) {
                context.fail("Nullify did not activate");
            }
            if (target.hasEffect(MobEffects.WEAKNESS)) {
                context.fail("Nullify should clear enemy effects");
            }
            if (!ally.hasEffect(MobEffects.WEAKNESS)) {
                context.fail("Nullify should not clear trusted ally effects");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void strengthBountyHuntingTracksOwner(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer owner = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(owner);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(owner, world, pos.x + 4.0D, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.STRENGTH);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        ItemStack token = new ItemStack(Items.DIAMOND);
        AbilityRuntime.setOwnerWithName(token, owner.getUUID(), owner.getName().getString());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, token);

        context.runAtTickTime(5L, () -> {
            boolean ok = new BountyHuntingAbility().activate(player);
            if (!ok) {
                context.fail("Bounty Hunting did not activate");
            }
            if (!player.getMainHandItem().isEmpty()) {
                context.fail("Bounty Hunting should consume the held item");
            }
            CompoundTag data = ((GemsPersistentDataHolder) player).gems$getPersistentData();
            if (data.getLongOr("bountyUntil", 0L) <= 0L) {
                context.fail("Bounty Hunting should set a tracking duration");
            }
            java.util.UUID tracked = GemsNbt.getUuid(data, "bountyTarget");
            if (tracked == null || !tracked.equals(owner.getUUID())) {
                context.fail("Bounty Hunting should track the owner");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void strengthPassivesApply(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.STRENGTH);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, sword);
        player.setHealth(4.0F);

        context.runAtTickTime(5L, () -> {
            GemPowers.maintain(player);
            if (!player.hasEffect(MobEffects.STRENGTH)) {
                context.fail("Strength passive should apply Strength");
            }
            int sharpness = net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                    world.registryAccess().get(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS).orElseThrow(),
                    sword
            );
            if (sharpness < 3) {
                context.fail("Auto-enchant Sharpness should apply");
            }
            if (!player.hasEffect(MobEffects.RESISTANCE)) {
                context.fail("Adrenaline should grant Resistance at low health");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void strengthConfigValuesValid(GameTestHelper context) {
        var cfg = GemsBalance.v().strength();
        
        if (cfg.chadCooldownTicks() < 0) {
            context.fail("Chad Strength cooldown cannot be negative");
        }
        if (cfg.frailerCooldownTicks() < 0) {
            context.fail("Frailer cooldown cannot be negative");
        }
        if (cfg.nullifyCooldownTicks() < 0) {
            context.fail("Nullify cooldown cannot be negative");
        }
        
        context.succeed();
    }
}
