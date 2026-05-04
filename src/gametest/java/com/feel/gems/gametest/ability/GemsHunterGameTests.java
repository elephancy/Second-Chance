package com.feel.gems.gametest.ability;

import com.feel.gems.config.GemsBalance;
import com.feel.gems.core.GemId;
import com.feel.gems.entity.HunterPackEntity;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.power.ability.hunter.*;
import com.feel.gems.power.gem.hunter.HunterPreyMarkRuntime;
import com.feel.gems.power.gem.hunter.HunterTrophyHunterRuntime;
import com.feel.gems.power.ability.hunter.HunterPackTacticsRuntime;
import com.feel.gems.power.runtime.GemPowers;
import com.feel.gems.power.runtime.AbilityRuntime;
import com.feel.gems.state.GemPlayerState;
import com.feel.gems.state.GemsPersistentDataHolder;
import com.feel.gems.trust.GemTrust;
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
import net.minecraft.world.phys.Vec3;

public final class GemsHunterGameTests {

    private static void teleport(ServerPlayer player, ServerLevel world, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(world, x, y, z, EnumSet.noneOf(Relative.class), yaw, pitch, false);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void hunterPreyMarkAppliesOnHitAndGrantsGlowing(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer hunter = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(hunter);
        GemsGameTestUtil.forceSurvival(target);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(hunter, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(target, world, pos.x, pos.y, pos.z + 2.0D, 180.0F, 0.0F);

        GemPlayerState.initIfNeeded(hunter);
        GemPlayerState.setActiveGem(hunter, GemId.HUNTER);
        GemPlayerState.setEnergy(hunter, 5);
        GemPowers.sync(hunter);

        context.runAtTickTime(5L, () -> {
            hunter.attack(target);
        });

        context.runAtTickTime(15L, () -> {
            if (!HunterPreyMarkRuntime.isMarked(hunter, target)) {
                context.fail("Prey Mark should apply to the last attacked target");
                return;
            }
            if (target.getEffect(MobEffects.GLOWING) == null) {
                context.fail("Tracker's Eye should grant Glowing on marked targets");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void hunterPounceLaunchesTowardMarkedTarget(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer hunter = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(hunter);
        GemsGameTestUtil.forceSurvival(target);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(hunter, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(target, world, pos.x, pos.y, pos.z + 6.0D, 180.0F, 0.0F);

        GemPlayerState.initIfNeeded(hunter);
        GemPlayerState.setActiveGem(hunter, GemId.HUNTER);
        GemPlayerState.setEnergy(hunter, 5);
        GemPowers.sync(hunter);

        context.runAtTickTime(5L, () -> hunter.attack(target));

        context.runAtTickTime(10L, () -> {
            boolean ok = new HunterPounceAbility().activate(hunter);
            if (!ok) {
                context.fail("Pounce did not activate with a marked target");
                return;
            }
        });

        context.runAtTickTime(20L, () -> {
            if (hunter.getDeltaMovement().lengthSqr() <= 0.05D) {
                context.fail("Pounce should launch the hunter toward the marked target");
                return;
            }
            if (!HunterPounceRuntime.isPouncing(hunter)) {
                context.fail("Pounce should register an active pounce state");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void hunterNetShotAppliesSlownessAndNetted(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer hunter = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(hunter);
        GemsGameTestUtil.forceSurvival(target);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(hunter, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(target, world, pos.x, pos.y, pos.z + 4.0D, 180.0F, 0.0F);

        GemPlayerState.initIfNeeded(hunter);
        GemPlayerState.setActiveGem(hunter, GemId.HUNTER);
        GemPlayerState.setEnergy(hunter, 5);
        GemPowers.sync(hunter);

        context.runAtTickTime(5L, () -> {
            boolean ok = new HunterNetShotAbility().activate(hunter);
            if (!ok) {
                context.fail("Net Shot did not activate");
            }
        });

        context.runAtTickTime(15L, () -> {
            if (target.getEffect(MobEffects.SLOWNESS) == null) {
                context.fail("Net Shot should apply Slowness");
                return;
            }
            if (!HunterNetShotAbility.isNetted(target)) {
                context.fail("Net Shot should mark target as netted");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void hunterCripplingShotAppliesSlowness(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer hunter = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(hunter);
        GemsGameTestUtil.forceSurvival(target);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(hunter, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(target, world, pos.x, pos.y, pos.z + 4.0D, 180.0F, 0.0F);

        GemPlayerState.initIfNeeded(hunter);
        GemPlayerState.setActiveGem(hunter, GemId.HUNTER);
        GemPlayerState.setEnergy(hunter, 5);
        GemPowers.sync(hunter);

        context.runAtTickTime(5L, () -> {
            boolean ok = new HunterCripplingShotAbility().activate(hunter);
            if (!ok) {
                context.fail("Crippling Shot did not activate");
                return;
            }
        });

        context.runAtTickTime(15L, () -> {
            if (target.getEffect(MobEffects.SLOWNESS) == null) {
                context.fail("Crippling Shot should apply Slowness");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void hunterHuntingTrapRootsAndDamages(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer hunter = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(hunter);
        GemsGameTestUtil.forceSurvival(target);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(hunter, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(target, world, pos.x + 3.0D, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(hunter);
        GemPlayerState.setActiveGem(hunter, GemId.HUNTER);
        GemPlayerState.setEnergy(hunter, 5);
        GemPowers.sync(hunter);

        context.runAtTickTime(5L, () -> {
            boolean ok = new HunterHuntingTrapAbility().activate(hunter);
            if (!ok) {
                context.fail("Hunting Trap did not activate");
                return;
            }
        });

        context.runAtTickTime(10L, () -> {
            float before = target.getHealth();
            teleport(target, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
            HunterHuntingTrapAbility.checkStep(target);
            if (target.getHealth() >= before) {
                context.fail("Hunting Trap should damage targets that step on it");
                return;
            }
            if (target.getEffect(MobEffects.SLOWNESS) == null) {
                context.fail("Hunting Trap should root targets with Slowness");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void hunterPackTacticsGrantsBuffToAllies(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer hunter = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer ally = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(hunter);
        GemsGameTestUtil.forceSurvival(ally);
        GemsGameTestUtil.forceSurvival(target);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(hunter, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(ally, world, pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);
        teleport(target, world, pos.x, pos.y, pos.z + 2.0D, 180.0F, 0.0F);

        GemPlayerState.initIfNeeded(hunter);
        GemPlayerState.setActiveGem(hunter, GemId.HUNTER);
        GemPlayerState.setEnergy(hunter, 5);
        GemPowers.sync(hunter);

        GemTrust.trust(hunter, ally.getUUID());

        context.runAtTickTime(5L, () -> {
            hunter.attack(target);
            boolean ok = new HunterPackTacticsAbility().activate(hunter);
            if (!ok) {
                context.fail("Pack Tactics did not activate with a marked target");
                return;
            }
        });

        context.runAtTickTime(15L, () -> {
            if (!HunterPackTacticsRuntime.hasBuffAgainst(ally, target.getUUID())) {
                context.fail("Pack Tactics should grant buff to trusted allies");
                return;
            }
            if (!HunterPackTacticsRuntime.hasBuffAgainst(hunter, target.getUUID())) {
                context.fail("Pack Tactics should grant buff to the caster");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void hunterCallThePackSummonsAllies(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer hunter = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(hunter);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(hunter, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(hunter);
        GemPlayerState.setActiveGem(hunter, GemId.HUNTER);
        GemPlayerState.setEnergy(hunter, 5);
        GemPowers.sync(hunter);

        context.runAtTickTime(5L, () -> {
            boolean ok = new HunterCallThePackAbility().activate(hunter);
            if (!ok) {
                context.fail("Call The Pack did not activate");
                return;
            }
        });

        context.runAtTickTime(15L, () -> {
            int count = world.getEntitiesOfClass(HunterPackEntity.class, hunter.getBoundingBox().inflate(12.0D), e -> true).size();
            if (count <= 0) {
                context.fail("Call The Pack should summon hunter pack entities");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void hunterOriginTrackingStartsBounty(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer hunter = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(hunter);
        GemsGameTestUtil.forceSurvival(target);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(hunter, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(target, world, pos.x + 3.0D, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(hunter);
        GemPlayerState.setActiveGem(hunter, GemId.HUNTER);
        GemPlayerState.setEnergy(hunter, 5);
        GemPowers.sync(hunter);

        ItemStack stack = new ItemStack(Items.DIAMOND);
        AbilityRuntime.setOwnerWithName(stack, target.getUUID(), target.getName().getString());
        hunter.setItemInHand(InteractionHand.MAIN_HAND, stack);

        context.runAtTickTime(5L, () -> {
            boolean ok = new HunterOriginTrackingAbility().activate(hunter);
            if (!ok) {
                context.fail("Origin Tracking did not activate with a valid first owner");
                return;
            }
            var nbt = ((GemsPersistentDataHolder) hunter).gems$getPersistentData();
            if (!nbt.contains("bountyTarget")) {
                context.fail("Origin Tracking should start a bounty on the first owner");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void hunterTrophyHunterStoresStolenPassive(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer hunter = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer victim = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(hunter);
        GemsGameTestUtil.forceSurvival(victim);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(hunter, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(victim, world, pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(hunter);
        GemPlayerState.setActiveGem(hunter, GemId.HUNTER);
        GemPlayerState.setEnergy(hunter, 5);
        GemPowers.sync(hunter);

        GemPlayerState.initIfNeeded(victim);
        GemPlayerState.setActiveGem(victim, GemId.AIR);
        GemPlayerState.setEnergy(victim, 5);
        GemPowers.sync(victim);

        context.runAtTickTime(5L, () -> {
            HunterTrophyHunterRuntime.onPlayerKill(hunter, victim);
            if (HunterTrophyHunterRuntime.getTrophyPassive(hunter) == null) {
                context.fail("Trophy Hunter should store a stolen passive");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void hunterConfigValuesValid(GameTestHelper context) {
        var cfg = GemsBalance.v().hunter();
        
        if (cfg.pounceCooldownTicks() < 0) {
            context.fail("Pounce cooldown cannot be negative");
        }
        if (cfg.netShotCooldownTicks() < 0) {
            context.fail("Net Shot cooldown cannot be negative");
        }
        
        context.succeed();
    }
}
