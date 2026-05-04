package com.feel.gems.gametest.ability;

import net.minecraft.world.entity.EntityTypes;
import com.feel.gems.config.GemsBalance;
import com.feel.gems.core.GemId;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.power.ability.air.AirDashAbility;
import com.feel.gems.power.ability.air.AirCrosswindAbility;
import com.feel.gems.power.ability.air.AirWindJumpAbility;
import com.feel.gems.power.ability.beacon.BeaconAuraAbility;
import com.feel.gems.power.ability.hunter.HunterPackTacticsAbility;
import com.feel.gems.power.ability.hunter.HunterPackTacticsRuntime;
import com.feel.gems.power.ability.pillager.PillagerFangsAbility;
import com.feel.gems.power.ability.pillager.PillagerVolleyAbility;
import com.feel.gems.power.ability.spy.SpyMimicFormAbility;
import com.feel.gems.power.ability.spy.SpyStealAbility;
import com.feel.gems.power.ability.summoner.SummonRecallAbility;
import com.feel.gems.power.ability.summoner.SummonSlotAbility;
import com.feel.gems.power.ability.terror.PanicRingAbility;
import com.feel.gems.power.ability.sentinel.SentinelInterventionAbility;
import com.feel.gems.power.gem.beacon.BeaconAuraRuntime;
import com.feel.gems.power.gem.hunter.HunterPreyMarkRuntime;
import com.feel.gems.power.gem.pillager.PillagerDiscipline;
import com.feel.gems.power.gem.pillager.PillagerVolleyRuntime;
import com.feel.gems.power.gem.spy.SpySystem;
import com.feel.gems.power.gem.summoner.SummonerSummons;
import com.feel.gems.power.registry.PowerIds;
import com.feel.gems.power.runtime.AbilityDisables;
import com.feel.gems.power.runtime.AbilityRuntime;
import com.feel.gems.power.runtime.GemPowers;
import com.feel.gems.state.GemPlayerState;
import com.feel.gems.trust.GemTrust;
import com.feel.gems.util.GemsTime;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;




public final class GemsAbilityGameTests {

    private static void teleport(ServerPlayer player, ServerLevel world, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(world, x, y, z, EnumSet.noneOf(Relative.class), yaw, pitch, false);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void astralCameraReturnsToStart(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        Vec3 startPos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        BlockPos startBlock = BlockPos.containing(startPos);
        teleport(player, world, startPos.x, startPos.y, startPos.z, 0.0F, 0.0F);

        AbilityRuntime.startAstralCamera(player, 20);

        Vec3 movedPos = context.absoluteVec(new Vec3(6.5D, 2.0D, 0.5D));
        teleport(player, world, movedPos.x, movedPos.y, movedPos.z, 90.0F, 0.0F);

        context.runAtTickTime(80L, () -> {
            if (!player.blockPosition().equals(startBlock)) {
                context.fail("Astral Camera did not return player to start position");
            }
            if (player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
                context.fail("Astral Camera did not restore original gamemode");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void heartLockDoesNotOscillate(GameTestHelper context) {
        ServerLevel world = context.getLevel();

        ServerPlayer caster = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        caster.setGameMode(GameType.SURVIVAL);

        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        target.setGameMode(GameType.SURVIVAL);

        Vec3 startPos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(caster, world, startPos.x, startPos.y, startPos.z, 0.0F, 0.0F);
        teleport(target, world, startPos.x + 1.0D, startPos.y, startPos.z, 0.0F, 0.0F);

        target.setHealth(6.0F); // lock to 3 hearts (6 health points)
        AbilityRuntime.startHeartLock(caster, target, 120);

        float lockedMax = target.getHealth();
        long[] checks = new long[]{25L, 45L, 65L, 85L};
        for (long tick : checks) {
            context.runAtTickTime(tick, () -> {
                double max = target.getAttributeValue(Attributes.MAX_HEALTH);
                if (Math.abs(max - lockedMax) > 0.01D) {
                    context.fail("Heart Lock oscillated: expected max=" + lockedMax + " got max=" + max);
                }
            });
        }
        context.runAtTickTime(110L, context::succeed);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void panicRingSpawnsConfiguredTnt(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        int expected = GemsBalance.v().terror().panicRingTntCount();
        context.runAtTickTime(2L, () -> {
            boolean ok = new PanicRingAbility().activate(player);
            if (!ok) {
                context.fail("Panic Ring did not activate");
            }
        });

        context.runAtTickTime(10L, () -> {
            AABB box = new AABB(player.blockPosition()).inflate(6.0D);
            int found = world.getEntitiesOfClass(PrimedTnt.class, box, e -> true).size();
            if (found < expected) {
                context.fail("Expected at least " + expected + " primed TNT, found " + found);
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 400)
    public void summonerSummonsHaveNoDropsAndRecallWorks(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SUMMONER);
        GemPlayerState.setEnergy(player, 10);
        GemPowers.sync(player);

        int expected = GemsBalance.v().summoner().slot1().stream().mapToInt(s -> s.count).sum();
        context.runAtTickTime(2L, () -> {
            boolean ok = new SummonSlotAbility(1).activate(player);
            if (!ok) {
                context.fail("Summon slot 1 did not activate");
            }
        });

        context.runAtTickTime(20L, () -> {
            AABB box = new AABB(player.blockPosition()).inflate(12.0D);
            var summons = world.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box,
                    e -> SummonerSummons.isSummon(e) && player.getUUID().equals(SummonerSummons.ownerUuid(e)));
            if (summons.size() < expected) {
                context.fail("Expected at least " + expected + " summons, found " + summons.size());
            }

            // Kill one summon and ensure it drops no items or XP.
            var mob = summons.getFirst();
            mob.hurtServer(world, mob.damageSources().fellOutOfWorld(), 10_000.0F);

            context.runAtTickTime(40L, () -> {
                AABB lootBox = new AABB(player.blockPosition()).inflate(12.0D);
                int items = world.getEntitiesOfClass(ItemEntity.class, lootBox, e -> true).size();
                int xp = world.getEntitiesOfClass(ExperienceOrb.class, lootBox, e -> true).size();
                if (items > 0) {
                    context.fail("Summons dropped items unexpectedly (" + items + ")");
                }
                if (xp > 0) {
                    context.fail("Summons dropped XP unexpectedly (" + xp + ")");
                }

                boolean recalled = new SummonRecallAbility().activate(player);
                if (!recalled) {
                    context.fail("Recall did not activate");
                }
                context.runAtTickTime(60L, () -> {
                    int after = world.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, lootBox,
                            e -> SummonerSummons.isSummon(e) && player.getUUID().equals(SummonerSummons.ownerUuid(e))).size();
                    if (after != 0) {
                        context.fail("Expected all summons to be recalled, remaining=" + after);
                    }
                    context.succeed();
                });
            });
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void pillagerFangsSpawnsFangs(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        Vec3 startPos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, startPos.x, startPos.y, startPos.z, 0.0F, 0.0F);

        Vec3 targetPos = context.absoluteVec(new Vec3(0.5D, 2.0D, 1.5D));
        var target = EntityTypes.ARMOR_STAND.create(world, armorStand -> { }, BlockPos.containing(targetPos), EntitySpawnReason.TRIGGERED, false, false);
        if (target == null) {
            context.fail("Failed to create target entity");
            return;
        }
        target.snapTo(targetPos.x, targetPos.y, targetPos.z, 0.0F, 0.0F);
        target.setNoGravity(true);
        world.addFreshEntity(target);

        // Aim at the target.
        Vec3 aimAt = new Vec3(target.getX(), target.getEyeY(), target.getZ());
        double dx = aimAt.x - player.getX();
        double dz = aimAt.z - player.getZ();
        double dy = aimAt.y - player.getEyeY();
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        teleport(player, world, startPos.x, startPos.y, startPos.z, yaw, pitch);

        context.runAtTickTime(5L, () -> {
            boolean activated = new PillagerFangsAbility().activate(player);
            if (!activated) {
                context.fail("Fangs did not activate");
                return;
            }

            AABB box = new AABB(target.blockPosition()).inflate(24.0D);
            int fangs = world.getEntitiesOfClass(EvokerFangs.class, box, e -> true).size();
            if (fangs <= 0) {
                context.fail("Expected fangs entities to be spawned");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 220)
    public void spyStillnessCloakAppliesInvisibility(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        Vec3 startPos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, startPos.x, startPos.y, startPos.z, 0.0F, 0.0F);

        context.runAtTickTime(10L, () -> {
            GemPlayerState.initIfNeeded(player);
            GemPlayerState.setActiveGem(player, GemId.SPY);
            GemPlayerState.setEnergy(player, 5);
        });

        // Tick the stillness cloak logic deterministically.
        for (long t = 20L; t <= 140L; t += 20L) {
            long at = t;
            context.runAtTickTime(at, () -> SpySystem.tickEverySecond(player));
        }

        context.runAtTickTime(160L, () -> {
            if (!player.hasEffect(MobEffects.INVISIBILITY)) {
                context.fail("Expected Stillness Cloak to apply invisibility");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void spyStealDisablesVictimAbility(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        var server = world.getServer();
        if (server == null) {
            context.fail("No server instance");
            return;
        }

        ServerPlayer spy = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer victim = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        spy.setGameMode(GameType.SURVIVAL);
        victim.setGameMode(GameType.SURVIVAL);

        Vec3 spyPos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        Vec3 victimPos = context.absoluteVec(new Vec3(0.5D, 2.0D, 1.5D));
        // Yaw 0 faces toward +Z (victim is in front).
        teleport(spy, world, spyPos.x, spyPos.y, spyPos.z, 0.0F, 0.0F);
        teleport(victim, world, victimPos.x, victimPos.y, victimPos.z, 180.0F, 0.0F);

        context.runAtTickTime(10L, () -> {
            GemPlayerState.initIfNeeded(spy);
            GemPlayerState.setActiveGem(spy, GemId.SPY);
            GemPlayerState.setEnergy(spy, 5);

            GemPlayerState.initIfNeeded(victim);
            GemPlayerState.setActiveGem(victim, GemId.ASTRA);
            GemPlayerState.setEnergy(victim, 5);
        });

        Identifier stolen = com.feel.gems.power.registry.PowerIds.ASTRAL_DAGGERS;

        context.runAtTickTime(20L, () -> {
            // Simulate the victim casting an ability in front of the spy enough times.
            for (int i = 0; i < GemsBalance.v().spy().stealRequiredWitnessCount(); i++) {
                SpySystem.onAbilityUsed(server, victim, stolen);
            }

            boolean ok = new SpyStealAbility().activate(spy);
            if (!ok) {
                context.fail("Steal did not activate");
                return;
            }
            if (!AbilityDisables.isDisabled(victim, stolen)) {
                context.fail("Expected victim ability to be disabled after steal");
                return;
            }
            if (!stolen.equals(SpySystem.selectedStolenAbility(spy))) {
                context.fail("Expected stolen ability to be selected after steal");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void airDashAppliesVelocityAndIFrames(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        context.runAtTickTime(2L, () -> {
            boolean ok = new AirDashAbility().activate(player);
            if (!ok) {
                context.fail("Air Dash did not activate");
            }
        });

        context.runAtTickTime(8L, () -> {
            Vec3 vel = player.getDeltaMovement();
            if (vel.lengthSqr() < 0.01D) {
                context.fail("Dash did not apply forward velocity");
                return;
            }
            var res = player.getEffect(MobEffects.RESISTANCE);
            if (res == null || res.getDuration() <= 0) {
                context.fail("Dash did not grant temporary i-frames");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void airWindJumpResetsFallAndLaunchesUp(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        context.runAtTickTime(2L, () -> {
            player.fallDistance = 10.0F;
            boolean ok = new AirWindJumpAbility().activate(player);
            if (!ok) {
                context.fail("Wind Jump did not activate");
            }
        });

        context.runAtTickTime(8L, () -> {
            if (player.fallDistance != 0.0F) {
                context.fail("Wind Jump did not reset fall distance");
                return;
            }
            if (player.getDeltaMovement().y <= 0.0D) {
                context.fail("Wind Jump did not add upward velocity");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void airCrosswindSlowsAndKnocksEnemies(GameTestHelper context) {
        ServerLevel world = context.getLevel();

        ServerPlayer caster = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        caster.setGameMode(GameType.SURVIVAL);

        ServerPlayer ally = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ally.setGameMode(GameType.SURVIVAL);

        ServerPlayer enemy = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        enemy.setGameMode(GameType.SURVIVAL);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(caster, world, pos.x, pos.y, pos.z, 90.0F, 0.0F);
        teleport(ally, world, pos.x + 1.0D, pos.y, pos.z + 1.0D, 90.0F, 0.0F);
        teleport(enemy, world, pos.x + 2.0D, pos.y, pos.z, 270.0F, 0.0F);

        GemTrust.trust(caster, ally.getUUID());
        final float enemyHealthBefore = enemy.getHealth();
        final float allyHealthBefore = ally.getHealth();
        AtomicBoolean knockbackSeen = new AtomicBoolean(false);

        context.runAtTickTime(2L, () -> {
            boolean ok = new AirCrosswindAbility().activate(caster);
            if (!ok) {
                context.fail("Crosswind did not activate");
            }
        });

        context.runAtTickTime(4L, () -> {
            if (enemy.getDeltaMovement().lengthSqr() > 0.0D) {
                knockbackSeen.set(true);
            }
        });

        context.runAtTickTime(10L, () -> {
            var cfg = GemsBalance.v().air();
            if (ally.hasEffect(MobEffects.SLOWNESS)) {
                context.fail("Trusted ally should not receive slowness");
                return;
            }
            if (ally.getHealth() < allyHealthBefore) {
                context.fail("Trusted ally should not take damage from crosswind");
                return;
            }
            boolean damageActive = cfg.crosswindDamage() > 0.0F;
            boolean slowActive = cfg.crosswindSlownessDurationTicks() > 0;
            boolean knockbackActive = cfg.crosswindKnockback() > 0.0D;
            boolean sawDamage = enemy.getHealth() < enemyHealthBefore;
            boolean sawSlow = enemy.hasEffect(MobEffects.SLOWNESS);
            boolean sawKnockback = knockbackSeen.get();

            if (!damageActive && !slowActive && !knockbackActive) {
                context.succeed();
                return;
            }
            if ((damageActive && sawDamage) || (slowActive && sawSlow) || (knockbackActive && sawKnockback)) {
                context.succeed();
                return;
            }

            context.fail(
                "Crosswind did not affect enemy. "
                    + "damage=" + sawDamage
                    + " slow=" + sawSlow
                    + " knockback=" + sawKnockback
                    + " cfgDamage=" + cfg.crosswindDamage()
                    + " cfgSlow=" + cfg.crosswindSlownessDurationTicks()
                    + " cfgKnockback=" + cfg.crosswindKnockback());
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 220)
    public void beaconAuraAppliesOnlyToTrusted(GameTestHelper context) {
        ServerLevel world = context.getLevel();

        ServerPlayer beacon = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        beacon.setGameMode(GameType.SURVIVAL);

        ServerPlayer trusted = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        trusted.setGameMode(GameType.SURVIVAL);

        ServerPlayer untrusted = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        untrusted.setGameMode(GameType.SURVIVAL);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(beacon, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(trusted, world, pos.x + 1.5D, pos.y, pos.z, 0.0F, 0.0F);
        teleport(untrusted, world, pos.x + 3.0D, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(beacon);
        GemPlayerState.setActiveGem(beacon, GemId.BEACON);
        GemPlayerState.setEnergy(beacon, 10);
        GemPowers.sync(beacon);
        GemTrust.trust(beacon, trusted.getUUID());

        context.runAtTickTime(4L, () -> {
            boolean ok = new BeaconAuraAbility(BeaconAuraRuntime.AuraType.SPEED).activate(beacon);
            if (!ok) {
                context.fail("Beacon aura did not activate");
            }
        });

        for (long tick = 8L; tick <= 32L; tick += 8L) {
            long at = tick;
            context.runAtTickTime(at, () -> BeaconAuraRuntime.tickEverySecond(beacon));
        }

        context.runAtTickTime(40L, () -> {
            var eff = trusted.getEffect(MobEffects.SPEED);
            if (eff == null) {
                context.fail("Trusted player did not receive beacon aura");
                return;
            }
            int expectedAmp = GemsBalance.v().beacon().auraSpeedAmplifier();
            if (eff.getAmplifier() != expectedAmp) {
                context.fail("Unexpected aura amplifier: " + eff.getAmplifier() + " expected=" + expectedAmp);
                return;
            }
            if (untrusted.hasEffect(MobEffects.SPEED)) {
                context.fail("Untrusted player should not receive beacon aura");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 260)
    public void pillagerVolleyFiresAndStopsWhenEnergyGone(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        var server = world.getServer();
        if (server == null) {
            context.fail("No server instance");
            return;
        }

        final int[] arrowsAtStop = new int[1];

        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.PILLAGER);
        GemPlayerState.setEnergy(player, 10);
        GemPowers.sync(player);

        context.runAtTickTime(2L, () -> {
            boolean ok = new PillagerVolleyAbility().activate(player);
            if (!ok) {
                context.fail("Pillager Volley did not activate");
            }
        });

        for (long tick = 5L; tick <= 80L; tick += 10L) {
            long at = tick;
            context.runAtTickTime(at, () -> PillagerVolleyRuntime.tick(server));
        }

        context.runAtTickTime(90L, () -> {
            AABB box = new AABB(player.blockPosition()).inflate(24.0D);
            int arrows = world.getEntitiesOfClass(Arrow.class, box, e -> true).size();
            if (arrows <= 0) {
                context.fail("Volley did not spawn arrows");
                return;
            }
            arrowsAtStop[0] = arrows;
        });

        context.runAtTickTime(100L, () -> GemPlayerState.setEnergy(player, 0));
        for (long tick = 110L; tick <= 200L; tick += 20L) {
            long at = tick;
            context.runAtTickTime(at, () -> PillagerVolleyRuntime.tick(server));
        }

        context.runAtTickTime(220L, () -> {
            AABB box = new AABB(player.blockPosition()).inflate(24.0D);
            int arrowsAfter = world.getEntitiesOfClass(Arrow.class, box, e -> true).size();
            // Energy drop should stop spawning new arrows; count should stay stable after the stop tick window.
            if (arrowsAfter != arrowsAtStop[0]) {
                context.fail("Volley continued firing after energy hit zero (before=" + arrowsAtStop[0] + " after=" + arrowsAfter + ")");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void pillagerDisciplineTriggersBelowThreshold(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.PILLAGER);
        GemPlayerState.setEnergy(player, 10);

        context.runAtTickTime(2L, () -> {
            player.setHealth(20.0F);
            PillagerDiscipline.tick(player);
            if (player.hasEffect(MobEffects.RESISTANCE)) {
                context.fail("Discipline should not trigger at high health");
            }
            player.setHealth(6.0F);
            PillagerDiscipline.tick(player);
        });

        context.runAtTickTime(40L, () -> {
            var res = player.getEffect(MobEffects.RESISTANCE);
            if (res == null || res.getDuration() <= 0) {
                context.fail("Discipline did not grant resistance when low health");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 340)
    public void spyMimicFormAppliesAndCleansUp(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer spy = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        spy.setGameMode(GameType.SURVIVAL);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(spy, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        var pig = EntityTypes.PIG.create(world, e -> { }, BlockPos.containing(pos), EntitySpawnReason.TRIGGERED, false, false);
        if (pig == null) {
            context.fail("Failed to spawn pig to mimic");
            return;
        }
        pig.snapTo(spy.getX(), spy.getY(), spy.getZ(), 0.0F, 0.0F);
        world.addFreshEntity(pig);

        GemPlayerState.initIfNeeded(spy);
        GemPlayerState.setActiveGem(spy, GemId.SPY);
        GemPlayerState.setEnergy(spy, 10);

        context.runAtTickTime(10L, () -> {
            SpySystem.recordLastKilledMob(spy, pig);
            pig.discard();
            boolean ok = new SpyMimicFormAbility().activate(spy);
            if (!ok) {
                context.fail("Mimic Form did not activate after recording kill");
            }
        });

        context.runAtTickTime(20L, () -> {
            double baseMax = spy.getAttributeValue(Attributes.MAX_HEALTH);
            spy.hurtServer(world, spy.damageSources().fellOutOfWorld(), 0.0F); // force attribute sync
            if (baseMax <= 20.0D) {
                context.fail("Mimic Form did not increase max health");
                return;
            }
            if (!spy.hasEffect(MobEffects.INVISIBILITY)) {
                context.fail("Mimic Form should grant invisibility during the form");
                return;
            }
        });

        for (long tick = 40L; tick <= 260L; tick += 20L) {
            long at = tick;
            context.runAtTickTime(at, () -> SpySystem.tickEverySecond(spy));
        }

        context.runAtTickTime(300L, () -> {
            double after = spy.getAttributeValue(Attributes.MAX_HEALTH);
            if (after > 20.1D) {
                context.fail("Mimic Form buffs did not clear");
                return;
            }
            if (spy.hasEffect(MobEffects.INVISIBILITY)) {
                context.fail("Mimic Form invisibility should expire after duration");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void spyStealRespectsWitnessCountEvenOnColdCache(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer spy = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer caster = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        spy.setGameMode(GameType.SURVIVAL);
        caster.setGameMode(GameType.SURVIVAL);

        Vec3 spyPos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        Vec3 casterPos = context.absoluteVec(new Vec3(0.5D, 2.0D, 6.5D));
        teleport(spy, world, spyPos.x, spyPos.y, spyPos.z, 0.0F, 0.0F); // yaw 0 faces +Z
        teleport(caster, world, casterPos.x, casterPos.y, casterPos.z, 180.0F, 0.0F);

        GemPlayerState.initIfNeeded(spy);
        GemPlayerState.setActiveGem(spy, GemId.SPY);
        GemPlayerState.setEnergy(spy, 10);

        Identifier observed = PowerIds.FIREBALL;
        int required = GemsBalance.v().spy().stealRequiredWitnessCount();
        for (int i = 0; i < required; i++) {
            SpySystem.onAbilityUsed(world.getServer(), caster, observed);
        }

        int seen = SpySystem.witnessedCount(spy, observed);
        if (seen < required) {
            context.fail("Spy did not record required observations; saw=" + seen + " required=" + required);
            return;
        }
        if (!SpySystem.canSteal(spy, observed)) {
            context.fail("Spy cannot steal after required observations (cold cache path)");
            return;
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void summonerRecallCleansSummons(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer owner = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        owner.setGameMode(GameType.SURVIVAL);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        var zombie = EntityTypes.ZOMBIE.create(world, e -> { }, BlockPos.containing(pos), EntitySpawnReason.TRIGGERED, false, false);
        if (zombie == null) {
            context.fail("Failed to create zombie summon");
            return;
        }
        zombie.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        zombie.setPersistenceRequired();
        world.addFreshEntity(zombie);

        long until = GemsTime.now(owner) + 200;
        SummonerSummons.mark(zombie, owner.getUUID(), until);
        SummonerSummons.trackSpawn(owner, zombie);

        int tracked = SummonerSummons.pruneAndCount(owner);
        if (tracked != 1) {
            context.fail("Expected one tracked summon, got " + tracked);
            return;
        }

        SummonerSummons.discardAll(owner);
        if (SummonerSummons.findEntity(world.getServer(), zombie.getUUID()) != null) {
            context.fail("Summon was not discarded on recall");
            return;
        }
        if (!SummonerSummons.ownedSummonUuids(owner).isEmpty()) {
            context.fail("Owned summon list not cleared on recall");
            return;
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 120)
    public void hunterPackTacticsIncreasesDamage(GameTestHelper context) {
        ServerLevel world = context.getLevel();

        ServerPlayer hunter = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer ally = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer victim = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(hunter);
        GemsGameTestUtil.forceSurvival(ally);
        GemsGameTestUtil.forceSurvival(victim);

        Vec3 base = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(hunter, world, base.x, base.y, base.z, 0.0F, 0.0F);
        teleport(ally, world, base.x + 1.0D, base.y, base.z, 0.0F, 0.0F);
        teleport(victim, world, base.x + 2.0D, base.y, base.z, 0.0F, 0.0F);

        final float[] baselineHolder = new float[1];

        context.runAtTickTime(5L, () -> {
            GemPlayerState.initIfNeeded(hunter);
            GemPlayerState.setActiveGem(hunter, GemId.HUNTER);
            GemPlayerState.setEnergy(hunter, 5);
            GemPowers.sync(hunter);

            // Keep the ally "neutral" so other passives don't interfere.
            GemPlayerState.initIfNeeded(ally);
            GemPlayerState.setActiveGem(ally, GemId.FIRE);
            GemPlayerState.setEnergy(ally, 0);
            GemPowers.sync(ally);

            GemPlayerState.initIfNeeded(victim);
            GemPlayerState.setActiveGem(victim, GemId.FIRE);
            GemPlayerState.setEnergy(victim, 0);
            GemPowers.sync(victim);

            float baseDamage = 4.0F;
            float before = victim.getHealth();
            victim.hurtServer(world, ally.damageSources().playerAttack(ally), baseDamage);
            baselineHolder[0] = before - victim.getHealth();
            victim.setHealth(before);
        });

        context.runAtTickTime(25L, () -> {
            GemTrust.trust(hunter, ally.getUUID());
            HunterPreyMarkRuntime.applyMark(hunter, victim);

            boolean ok = new HunterPackTacticsAbility().activate(hunter);
            if (!ok) {
                context.fail("Pack Tactics did not activate");
            }
        });

        GemsGameTestUtil.assertEventually(
                context,
                30L,
                80L,
                5L,
                () -> {
                    if (!HunterPackTacticsRuntime.hasBuffAgainst(ally, victim.getUUID())) {
                        return false;
                    }
                    float before = victim.getHealth();
                    float baseDamage = 4.0F;
                    victim.hurtServer(world, ally.damageSources().playerAttack(ally), baseDamage);
                    float buffedDealt = before - victim.getHealth();
                    victim.setHealth(before);
                    return buffedDealt > baselineHolder[0] + 0.01F;
                },
                "Expected Pack Tactics to increase damage"
        );
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 120)
    public void sentinelInterventionRedirectsDamage(GameTestHelper context) {
        ServerLevel world = context.getLevel();

        ServerPlayer sentinel = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer ally = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer attacker = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(sentinel);
        GemsGameTestUtil.forceSurvival(ally);
        GemsGameTestUtil.forceSurvival(attacker);

        Vec3 base = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(sentinel, world, base.x, base.y, base.z, 0.0F, 0.0F);
        teleport(ally, world, base.x + 1.0D, base.y, base.z, 0.0F, 0.0F);
        teleport(attacker, world, base.x + 2.0D, base.y, base.z, 0.0F, 0.0F);

        // Set up gem state before runAtTick
        GemPlayerState.initIfNeeded(sentinel);
        GemPlayerState.setActiveGem(sentinel, GemId.SENTINEL);
        GemPlayerState.setEnergy(sentinel, 5);
        GemPowers.sync(sentinel);

        GemPlayerState.initIfNeeded(ally);
        GemPlayerState.setActiveGem(ally, GemId.FIRE);
        GemPlayerState.setEnergy(ally, 0);
        GemPowers.sync(ally);

        GemTrust.trust(sentinel, ally.getUUID());
        GemTrust.trust(ally, sentinel.getUUID());

        context.runAtTickTime(5L, () -> {
            boolean ok = new SentinelInterventionAbility().activate(sentinel);
            if (!ok) {
                context.fail("Intervention did not activate");
                return;
            }
        });

        // Test damage redirection on a separate tick to ensure state is propagated
        context.runAtTickTime(15L, () -> {
            float allyBefore = ally.getHealth();
            float sentinelBefore = sentinel.getHealth();
            ally.hurtServer(world, attacker.damageSources().playerAttack(attacker), 4.0F);
            if (ally.getHealth() != allyBefore) {
                context.fail("Expected ally to take no damage while protected");
                return;
            }
            if (sentinel.getHealth() >= sentinelBefore) {
                context.fail("Expected sentinel to absorb redirected damage");
                return;
            }

            // Protection is single-use.
            ally.hurtServer(world, attacker.damageSources().playerAttack(attacker), 4.0F);
            if (ally.getHealth() >= allyBefore) {
                context.fail("Expected ally to take damage after protection is consumed");
                return;
            }

            context.succeed();
        });
    }

}

