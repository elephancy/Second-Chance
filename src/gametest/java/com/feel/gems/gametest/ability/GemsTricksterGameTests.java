package com.feel.gems.gametest.ability;

import net.minecraft.world.entity.EntityTypes;
import com.feel.gems.config.GemsBalance;
import com.feel.gems.core.GemId;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.power.ability.trickster.TricksterGlitchStepAbility;
import com.feel.gems.power.ability.trickster.TricksterMindGamesAbility;
import com.feel.gems.power.ability.trickster.TricksterMirageAbility;
import com.feel.gems.power.ability.trickster.TricksterPuppetMasterAbility;
import com.feel.gems.power.ability.trickster.TricksterShadowSwapAbility;
import com.feel.gems.power.ability.trickster.TricksterMindGamesRuntime;
import com.feel.gems.power.ability.trickster.TricksterMirageRuntime;
import com.feel.gems.power.ability.trickster.TricksterPuppetRuntime;
import com.feel.gems.power.gem.trickster.TricksterPassiveRuntime;
import com.feel.gems.power.runtime.GemPowers;
import com.feel.gems.state.GemPlayerState;
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
import net.minecraft.world.phys.Vec3;




public final class GemsTricksterGameTests {

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
    public void glitchStepTeleportsAndDamagesAfterimage(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer enemy = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(enemy);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(enemy, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.TRICKSTER);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        Vec3 startPos = player.position();
        float enemyBefore = enemy.getHealth();

        context.runAtTickTime(5L, () -> {
            boolean ok = new TricksterGlitchStepAbility().activate(player);
            if (!ok) {
                context.fail("Glitch Step did not activate");
            }
        });

        context.runAtTickTime(15L, () -> {
            Vec3 endPos = player.position();
            double dist = startPos.distanceTo(endPos);
            
            if (dist < 1.0D) {
                context.fail("Glitch Step did not teleport player");
            }
            if (enemy.getHealth() >= enemyBefore) {
                context.fail("Glitch Step afterimage should damage nearby enemies");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void mirageCreatesRuntimeClones(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        GemsGameTestUtil.placeStoneFloor(context, 1, 6);
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        BlockPos marker = BlockPos.containing(context.absoluteVec(new Vec3(0.5D, 1.0D, 5.5D)));

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.TRICKSTER);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            aimAt(player, world, Vec3.atCenterOf(marker));
            player.setDeltaMovement(Vec3.ZERO);
            boolean ok = new TricksterMirageAbility().activate(player);
            if (!ok) {
                context.fail("Mirage did not activate");
                return;
            }
        });

        context.runAtTickTime(15L, () -> {
            if (!TricksterMirageRuntime.hasMirages(player)) {
                context.fail("Mirage should register a clone in runtime");
                return;
            }
            if (TricksterMirageRuntime.getMiragePosition(player) == null) {
                context.fail("Mirage should expose clone position");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void shadowSwapTeleportsBothPlayers(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(target);

        Vec3 playerPos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        Vec3 targetPos = context.absoluteVec(new Vec3(0.5D, 2.0D, 5.5D));
        teleport(player, world, playerPos.x, playerPos.y, playerPos.z, 0.0F, 0.0F);
        teleport(target, world, targetPos.x, targetPos.y, targetPos.z, 180.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.TRICKSTER);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new TricksterShadowSwapAbility().activate(player);
            if (!ok) {
                context.fail("Shadow Swap did not activate");
            }
        });

        context.runAtTickTime(15L, () -> {
            double playerDistToOriginal = player.position().distanceToSqr(playerPos);
            double targetDistToOriginal = target.position().distanceToSqr(targetPos);
            
            if (playerDistToOriginal < 1.0D || targetDistToOriginal < 1.0D) {
                context.fail("Shadow Swap should swap player positions");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 300)
    public void mindGamesConfusesMobTargets(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        GemsGameTestUtil.placeStoneFloor(context, 1, 6);
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        Vec3 mobPos = context.absoluteVec(new Vec3(0.5D, 2.0D, 3.5D));
        var zombie = EntityTypes.ZOMBIE.create(world, e -> {}, BlockPos.containing(mobPos), EntitySpawnReason.TRIGGERED, false, false);
        if (zombie == null) {
            context.fail("Failed to spawn zombie");
            return;
        }
        zombie.snapTo(mobPos.x, mobPos.y, mobPos.z, 0.0F, 0.0F);
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        zombie.setDeltaMovement(Vec3.ZERO);
        world.addFreshEntity(zombie);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.TRICKSTER);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(20L, () -> {
            aimAt(player, world, mobPos.add(0.0D, 1.0D, 0.0D));
            player.setDeltaMovement(Vec3.ZERO);
            boolean ok = new TricksterMindGamesAbility().activate(player);
            if (!ok) {
                context.fail("Mind Games did not activate with a mob target");
                return;
            }
        });

        GemsGameTestUtil.assertEventually(
                context,
                22L,
                120L,
                2L,
                () -> zombie.getEffect(MobEffects.SLOWNESS) != null
                        && zombie.getEffect(MobEffects.NAUSEA) != null
                        && TricksterMindGamesRuntime.isMobConfused(zombie),
                "Mind Games should apply confusion effects to mobs"
        );
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 300)
    public void puppetMasterMarksMobAsPuppeted(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        // Spawn a mob to control
        Vec3 mobPos = context.absoluteVec(new Vec3(0.5D, 2.0D, 3.5D));
        var zombie = EntityTypes.ZOMBIE.create(world, e -> {}, BlockPos.containing(mobPos), EntitySpawnReason.TRIGGERED, false, false);
        if (zombie == null) {
            context.fail("Failed to spawn zombie");
            return;
        }
        zombie.snapTo(mobPos.x, mobPos.y, mobPos.z, 0.0F, 0.0F);
        world.addFreshEntity(zombie);
        aimAt(player, world, mobPos.add(0.0D, 1.0D, 0.0D));

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.TRICKSTER);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new TricksterPuppetMasterAbility().activate(player);
            if (!ok) {
                context.fail("Puppet Master did not activate with a mob target");
                return;
            }
        });

        context.runAtTickTime(15L, () -> {
            if (!TricksterPuppetRuntime.isMobPuppeted(zombie)) {
                context.fail("Puppet Master should mark the mob as puppeted");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void sleightOfHandPassiveCanPreventConsumption(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.TRICKSTER);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean triggered = false;
            for (int i = 0; i < 200; i++) {
                if (TricksterPassiveRuntime.shouldNotConsumeThrowable(player)) {
                    triggered = true;
                    break;
                }
            }
            if (!triggered && GemsBalance.v().trickster().sleightOfHandChance() > 0.0F) {
                context.fail("Sleight of Hand should occasionally prevent item consumption");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void tricksterSlipperyCanRemoveSlowness(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.TRICKSTER);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.SLOWNESS, 200, 0, false, false));
            for (int i = 0; i < 200; i++) {
                TricksterPassiveRuntime.tryRemoveSlowEffects(player);
                if (player.getEffect(MobEffects.SLOWNESS) == null) {
                    break;
                }
            }
            if (player.getEffect(MobEffects.SLOWNESS) != null && GemsBalance.v().trickster().slipperyChance() > 0.0F) {
                context.fail("Slippery should occasionally remove slowing effects");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void glitchStepHasCooldownConfigured(GameTestHelper context) {
        // Direct activate() bypasses cooldown system (handled by GemAbilities.activateByIndex)
        // Just verify the cooldown config is valid
        var cfg = GemsBalance.v().trickster();
        if (cfg.glitchStepCooldownTicks() <= 0) {
            context.fail("Glitch Step cooldown must be positive");
        }
        context.succeed();
    }
}
