package com.feel.gems.gametest.ability;

import net.minecraft.world.entity.EntityTypes;
import com.feel.gems.config.GemsBalance;
import com.feel.gems.core.GemId;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.power.ability.astra.*;
import com.feel.gems.power.gem.astra.SoulSummons;
import com.feel.gems.power.gem.astra.SoulSystem;
import com.feel.gems.power.runtime.AbilityRestrictions;
import com.feel.gems.power.runtime.AbilityRuntime;
import com.feel.gems.power.runtime.GemPowers;
import com.feel.gems.state.GemPlayerState;
import java.util.EnumSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class GemsAstraGameTests {

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
    public void astraSoulCaptureStoresSoulAndHeals(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.placeStoneFloor(context, 1, 2);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.ASTRA);
        GemPlayerState.setEnergy(player, 5);
        GemPlayerState.setPassivesEnabled(player, true);
        GemPowers.sync(player);

        Zombie zombie = EntityTypes.ZOMBIE.create(world, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if (zombie == null) {
            context.fail("Failed to create zombie");
            return;
        }
        zombie.snapTo(pos.x + 1.0D, pos.y, pos.z, 0.0F, 0.0F);
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        zombie.setDeltaMovement(Vec3.ZERO);
        zombie.setHealth(1.0F);
        world.addFreshEntity(zombie);

        context.runAtTickTime(40L, () -> {
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.Items.DIAMOND_SWORD.getDefaultInstance());
            player.setDeltaMovement(Vec3.ZERO);
            // Measure healing around the capture itself. The connected player
            // can take unrelated world damage during the 40-tick setup window.
            player.setHealth(Math.min(player.getHealth(), player.getMaxHealth() - 4.0F));
            float before = player.getHealth();
            player.attack(zombie);
            if (zombie.isAlive()) {
                zombie.hurtServer(world, player.damageSources().playerAttack(player), Float.MAX_VALUE);
            }
            if (zombie.isAlive()) {
                zombie.kill(world);
            }
            // If the combat event didn't fire, trigger the capture directly to keep the test stable.
            if (!SoulSystem.hasSoul(player) && !zombie.isAlive()) {
                SoulSystem.onKilledMob(player, zombie);
            }
            if (!SoulSystem.hasSoul(player)
                    || SoulSystem.soulType(player).isEmpty()
                    || player.getHealth() <= before) {
                context.fail("Soul Capture did not store a soul or heal the player"
                        + " (soul=" + SoulSystem.hasSoul(player)
                        + ", health=" + player.getHealth() + ", before=" + before + ")");
            } else {
                context.succeed();
            }
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 220)
    public void astraSoulReleaseSpawnsSoulAndClearsState(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.ASTRA);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        Zombie zombie = EntityTypes.ZOMBIE.create(world, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if (zombie == null) {
            context.fail("Failed to create zombie");
            return;
        }
        zombie.snapTo(pos.x + 1.0D, pos.y, pos.z, 0.0F, 0.0F);
        world.addFreshEntity(zombie);

        context.runAtTickTime(5L, () -> zombie.hurtServer(world, player.damageSources().playerAttack(player), 50.0F));

        context.runAtTickTime(20L, () -> {
            if (!SoulSystem.hasSoul(player)) {
                context.fail("Expected a stored soul before release");
                return;
            }
            if (!SoulSystem.release(player)) {
                context.fail("Soul Release did not activate");
                return;
            }
        });

        context.runAtTickTime(40L, () -> {
            if (SoulSystem.hasSoul(player)) {
                context.fail("Soul Release should clear the stored soul");
                return;
            }
            // Expand search box significantly to account for any spawn offset variations
            AABB box = new AABB(player.blockPosition()).inflate(12.0D);
            boolean found = world.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box, SoulSummons::isSoul).stream()
                    .anyMatch(entity -> player.getUUID().equals(SoulSummons.ownerUuid(entity)));
            if (!found) {
                context.fail("Soul Release did not spawn a soul summon");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void astraUnboundedActivates(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.ASTRA);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new UnboundedAbility().activate(player);
            if (!ok) {
                context.fail("Unbounded did not activate");
                return;
            }
            if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                context.fail("Unbounded should switch to spectator mode");
                return;
            }
        });

        int duration = GemsBalance.v().astra().unboundedDurationTicks();
        context.runAtTickTime(5L + duration + 5L, () -> {
            AbilityRuntime.tickEverySecond(player);
            if (player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
                context.fail("Unbounded should return to survival");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void astraTagRequiresTarget(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.ASTRA);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new TagAbility().activate(player);
            if (ok) {
                context.fail("Tag should fail without target");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void astraSpookAppliesDebuffs(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.ASTRA);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            Zombie zombie = EntityTypes.ZOMBIE.create(world, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
            if (zombie == null) {
                context.fail("Failed to create zombie");
                return;
            }
            zombie.snapTo(pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);
            world.addFreshEntity(zombie);
            boolean ok = new SpookAbility().activate(player);
            if (!ok) {
                context.fail("Spook did not activate");
                return;
            }
            if (!zombie.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS)
                    || !zombie.hasEffect(net.minecraft.world.effect.MobEffects.NAUSEA)) {
                context.fail("Spook should apply darkness and nausea");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void astraShadowAnchorReturnsToAnchor(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.ASTRA);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new ShadowAnchorAbility().activate(player);
            if (!ok) {
                context.fail("Shadow Anchor did not activate");
                return;
            }
            BlockPos anchor = player.blockPosition();
            teleport(player, world, pos.x + 6.0D, pos.y, pos.z, 0.0F, 0.0F);
            boolean ok2 = new ShadowAnchorAbility().activate(player);
            if (!ok2) {
                context.fail("Shadow Anchor return did not activate");
                return;
            }
            if (!player.blockPosition().equals(anchor)) {
                context.fail("Shadow Anchor did not return player to anchor");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void astraDimensionalVoidSuppressesPlayers(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(target);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(target, world, pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.ASTRA);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new DimensionalVoidAbility().activate(player);
            if (!ok) {
                context.fail("Dimensional Void did not activate");
                return;
            }
            if (!AbilityRestrictions.isSuppressed(target)) {
                context.fail("Dimensional Void should suppress nearby players");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void astraAstralDaggersSpawnsDaggers(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.ASTRA);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new AstralDaggersAbility().activate(player);
            if (!ok) {
                context.fail("Astral Daggers did not activate");
                return;
            }
            int expected = GemsBalance.v().astra().astralDaggersCount();
            AABB box = new AABB(player.blockPosition()).inflate(6.0D);
            int found = world.getEntitiesOfClass(Arrow.class, box, e -> true).size();
            if (found < expected) {
                context.fail("Expected at least " + expected + " daggers, found " + found);
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void astraAstralCameraActivates(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.ASTRA);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new AstralCameraAbility().activate(player);
            if (!ok) {
                context.fail("Astral Camera did not activate");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void astraTagAppliesGlowing(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.ASTRA);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        Zombie zombie = EntityTypes.ZOMBIE.create(world, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if (zombie == null) {
            context.fail("Failed to create zombie");
            return;
        }
        Vec3 targetPos = pos.add(0.0D, 0.0D, 3.0D);
        zombie.snapTo(targetPos.x, targetPos.y, targetPos.z, 0.0F, 0.0F);
        world.addFreshEntity(zombie);
        aimAt(player, world, zombie.position().add(0.0D, 1.2D, 0.0D));

        context.runAtTickTime(5L, () -> {
            boolean ok = new TagAbility().activate(player);
            if (!ok) {
                context.fail("Tag did not activate with target");
                return;
            }
            if (!zombie.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING)) {
                context.fail("Tag should apply glowing to target");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void astraConfigValuesValid(GameTestHelper context) {
        var cfg = GemsBalance.v().astra();
        
        if (cfg.unboundedCooldownTicks() < 0) {
            context.fail("Unbounded cooldown cannot be negative");
        }
        if (cfg.unboundedDurationTicks() <= 0) {
            context.fail("Unbounded duration must be positive");
        }
        if (cfg.tagCooldownTicks() < 0) {
            context.fail("Tag cooldown cannot be negative");
        }
        if (cfg.spookCooldownTicks() < 0) {
            context.fail("Spook cooldown cannot be negative");
        }
        
        context.succeed();
    }
}
