package com.feel.gems.gametest.ability;

import net.minecraft.world.entity.EntityTypes;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.power.ability.bonus.BonusArcaneMissilesAbility;
import com.feel.gems.power.ability.bonus.BonusBanishmentAbility;
import com.feel.gems.power.ability.bonus.BonusBerserkerRageAbility;
import com.feel.gems.power.ability.bonus.BonusBlinkAbility;
import com.feel.gems.power.ability.bonus.BonusBloodlustAbility;
import com.feel.gems.power.ability.bonus.BonusChainLightningAbility;
import com.feel.gems.power.ability.bonus.BonusCorpseExplosionAbility;
import com.feel.gems.power.ability.bonus.BonusCrystalCageAbility;
import com.feel.gems.power.ability.bonus.BonusCurseBoltAbility;
import com.feel.gems.power.ability.bonus.BonusDecoyTrapAbility;
import com.feel.gems.power.ability.bonus.BonusDoomBoltAbility;
import com.feel.gems.power.ability.bonus.BonusEarthshatterAbility;
import com.feel.gems.power.ability.bonus.BonusEtherealStepAbility;
import com.feel.gems.power.ability.bonus.BonusFrostbiteAbility;
import com.feel.gems.power.ability.bonus.BonusGravityCrushAbility;
import com.feel.gems.power.ability.bonus.BonusGravityWellAbility;
import com.feel.gems.power.ability.bonus.BonusIceWallAbility;
import com.feel.gems.power.ability.bonus.BonusIcicleBarrageAbility;
import com.feel.gems.power.ability.bonus.BonusInfernoDashAbility;
import com.feel.gems.power.ability.bonus.BonusLifeTapAbility;
import com.feel.gems.power.ability.bonus.BonusMagmaPoolAbility;
import com.feel.gems.power.ability.bonus.BonusMindSpikeAbility;
import com.feel.gems.power.ability.bonus.BonusMirrorImageAbility;
import com.feel.gems.power.ability.bonus.BonusOverchargeAbility;
import com.feel.gems.power.ability.bonus.BonusPlagueCloudAbility;
import com.feel.gems.power.ability.bonus.BonusPurgeAbility;
import com.feel.gems.power.ability.bonus.BonusQuicksandAbility;
import com.feel.gems.power.ability.bonus.BonusRadiantBurstAbility;
import com.feel.gems.power.ability.bonus.BonusReflectionWardAbility;
import com.feel.gems.power.ability.bonus.BonusSanctuaryAbility;
import com.feel.gems.power.ability.bonus.BonusSearingLightAbility;
import com.feel.gems.power.ability.bonus.BonusShadowstepAbility;
import com.feel.gems.power.ability.bonus.BonusSmokeScreenAbility;
import com.feel.gems.power.ability.bonus.BonusSonicBoomAbility;
import com.feel.gems.power.ability.bonus.BonusSoulLinkAbility;
import com.feel.gems.power.ability.bonus.BonusSoulSwapAbility;
import com.feel.gems.power.ability.bonus.BonusSpectralBladeAbility;
import com.feel.gems.power.ability.bonus.BonusSpectralChainsAbility;
import com.feel.gems.power.ability.bonus.BonusStarfallAbility;
import com.feel.gems.power.ability.bonus.BonusThornsNovaAbility;
import com.feel.gems.power.ability.bonus.BonusThunderstrikeAbility;
import com.feel.gems.power.ability.bonus.BonusTidalWaveAbility;
import com.feel.gems.power.ability.bonus.BonusTimewarpAbility;
import com.feel.gems.power.ability.bonus.BonusTremorAbility;
import com.feel.gems.power.ability.bonus.BonusVampiricTouchAbility;
import com.feel.gems.power.ability.bonus.BonusVenomsprayAbility;
import com.feel.gems.power.ability.bonus.BonusVortexStrikeAbility;
import com.feel.gems.power.ability.bonus.BonusVulnerabilityAbility;
import com.feel.gems.power.ability.bonus.BonusWarpStrikeAbility;
import com.feel.gems.power.ability.bonus.BonusWindSlashAbility;
import com.feel.gems.power.runtime.EtherealState;
import com.feel.gems.power.runtime.GemPowers;
import com.feel.gems.power.util.Targeting;
import com.feel.gems.state.GemPlayerState;
import com.feel.gems.state.PlayerStateManager;
import java.util.EnumSet;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Behavior tests for all bonus abilities.
 */
public final class GemsBonusAbilityGameTests {

    private static void teleport(ServerPlayer player, ServerLevel world, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(world, x, y, z, EnumSet.noneOf(Relative.class), yaw, pitch, false);
    }

    private static ServerPlayer setupPlayer(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.resetPlayerForTest(player);
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setEnergy(player, 10);
        GemPowers.sync(player);
        return player;
    }

    private static void aimAt(ServerPlayer player, ServerLevel world, Vec3 target) {
        Vec3 pos = player.position();
        double dx = target.x - pos.x;
        double dz = target.z - pos.z;
        double dy = target.y - player.getEyeY();
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        player.teleportTo(world, pos.x, pos.y, pos.z, EnumSet.noneOf(Relative.class), yaw, pitch, false);
    }

    private static void placePlatform(GameTestHelper context, int radius) {
        int safeRadius = Math.max(1, Math.min(radius, 2));
        for (int x = -safeRadius; x <= safeRadius; x++) {
            for (int z = -safeRadius; z <= safeRadius; z++) {
                context.setBlock(x, 1, z, Blocks.STONE.defaultBlockState());
            }
        }
    }

    private static Zombie spawnZombie(ServerLevel world, Vec3 pos) {
        Zombie zombie = EntityTypes.ZOMBIE.create(world, EntitySpawnReason.TRIGGERED);
        if (zombie == null) {
            return null;
        }
        zombie.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        // Clear any armor to ensure consistent damage calculations in tests
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            zombie.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
        }
        world.addFreshEntity(zombie);
        return zombie;
    }

    private static Vec3 origin(GameTestHelper context) {
        return context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
    }

    private static Vec3 isolatedOrigin(GameTestHelper context, ServerPlayer player) {
        Vec3 base = origin(context);
        return new Vec3(base.x, base.y + 40.0D, base.z);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusArcaneMissilesSpawnsFireballs(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        AABB box = new AABB(player.blockPosition()).inflate(6.0);
        int before = world.getEntitiesOfClass(SmallFireball.class, box, e -> true).size();

        context.runAtTickTime(5L, () -> {
            boolean ok = new BonusArcaneMissilesAbility().activate(player);
            if (!ok) {
                context.fail("Arcane Missiles did not activate");
            }
            int after = world.getEntitiesOfClass(SmallFireball.class, box, e -> true).size();
            if (after - before < 5) {
                context.fail("Arcane Missiles did not spawn 5 fireballs");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusBanishmentTeleportsTarget(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        Vec3 base = isolatedOrigin(context, player);
        teleport(player, world, base.x, base.y, base.z, 0.0F, 0.0F);
        player.setNoGravity(true);
        Vec3 targetPos = base.add(0.0D, 0.0D, 6.0D);
        Zombie target = spawnZombie(world, targetPos);
        if (target == null) {
            context.fail("Failed to spawn zombie");
            return;
        }
        target.setNoAi(true);
        target.setNoGravity(true);
        aimAt(player, world, targetPos.add(0.0D, 1.0D, 0.0D));
        Vec3 before = target.position();

        context.runAtTickTime(5L, () -> {
            aimAt(player, world, target.position().add(0.0D, 1.0D, 0.0D));
            boolean ok = new BonusBanishmentAbility().activate(player);
            if (!ok) {
                context.fail("Banishment did not activate");
            }
            Vec3 after = target.position();
            if (after.distanceTo(before) < 40.0D) {
                context.fail("Banishment did not teleport target far enough");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusBerserkerRageScalesDamage(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        placePlatform(context, 10);
        ServerPlayer player = setupPlayer(context);
        Zombie dummy = spawnZombie(world, origin(context).add(2.0D, 0.0D, 0.0D));
        if (dummy == null) {
            context.fail("Failed to spawn zombie");
            return;
        }
        dummy.setNoAi(true);
        dummy.setNoGravity(true);
        dummy.setDeltaMovement(Vec3.ZERO);

        float boost = BonusBerserkerRageAbility.getDamageBoostMultiplier();
        float taken = BonusBerserkerRageAbility.getDamageTakenMultiplier();
        if (boost <= 1.0F && taken <= 1.0F) {
            context.succeed();
            return;
        }

        float[] baseTaken = new float[1];
        float[] baseDealt = new float[1];

        context.runAtTickTime(40L, () -> {
            player.setHealth(20.0F);
            float before = player.getHealth();
            player.hurtServer(world, player.damageSources().generic(), 4.0F);
            baseTaken[0] = before - player.getHealth();

            dummy.setHealth(dummy.getMaxHealth());
            before = dummy.getHealth();
            dummy.hurtServer(world, world.damageSources().playerAttack(player), 4.0F);
            baseDealt[0] = before - dummy.getHealth();
        });

        // Avoid invulnerability frame issues by spacing the "rage" hits out.
        context.runAtTickTime(80L, () -> {
            dummy.setHealth(dummy.getMaxHealth());
            player.setHealth(20.0F);
            // Clear any existing berserker rage state from other tests
            PlayerStateManager.clearPersistent(player, "bonus_berserker_rage_until");
            boolean ok = new BonusBerserkerRageAbility().activate(player);
            if (!ok || !BonusBerserkerRageAbility.isActive(player)) {
                context.fail("Berserker Rage did not activate");
                return;
            }
        });

        context.runAtTickTime(120L, () -> {
            // Verify rage is still active
            if (!BonusBerserkerRageAbility.isActive(player)) {
                context.fail("Berserker Rage should still be active at tick 40");
                return;
            }
            
            float before = player.getHealth();
            player.hurtServer(world, player.damageSources().generic(), 4.0F);
            float rageTaken = before - player.getHealth();

            before = dummy.getHealth();
            dummy.hurtServer(world, world.damageSources().playerAttack(player), 4.0F);
            float rageDealt = before - dummy.getHealth();

            // Check that damage taken increased (if multiplier > 1)
            if (taken > 1.0F && rageTaken < baseTaken[0] + 0.1F) {
                context.fail("Berserker Rage should increase damage taken");
                return;
            }
            // Check that damage dealt increased (if multiplier > 1)
            // Use a more lenient check: rage damage should be noticeably higher than baseline
            if (boost > 1.0F && rageDealt < baseDealt[0] + 0.5F) {
                context.fail("Berserker Rage should increase damage dealt");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusBlinkTeleportsForward(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        Vec3 before = player.position();
        aimAt(player, world, before.add(0.0D, 0.0D, 6.0D));

        context.runAtTickTime(5L, () -> {
            boolean ok = new BonusBlinkAbility().activate(player);
            if (!ok) {
                context.fail("Blink did not activate");
            }
            if (player.position().distanceTo(before) < 4.0D) {
                context.fail("Blink did not move player far enough");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusBloodlustScalesWithEnemies(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        Vec3 base = isolatedOrigin(context, player);
        teleport(player, world, base.x, base.y, base.z, 0.0F, 0.0F);
        player.setNoGravity(true);
        Zombie z1 = spawnZombie(world, base.add(2.0D, 0.0D, 2.0D));
        Zombie z2 = spawnZombie(world, base.add(-2.0D, 0.0D, 2.0D));
        Zombie z3 = spawnZombie(world, base.add(0.0D, 0.0D, -2.0D));
        if (z1 == null || z2 == null || z3 == null) {
            context.fail("Failed to spawn zombies");
            return;
        }
        for (Zombie zombie : java.util.List.of(z1, z2, z3)) {
            zombie.setNoAi(true);
            zombie.setNoGravity(true);
            zombie.setDeltaMovement(Vec3.ZERO);
        }

        context.runAtTickTime(20L, () -> {
            boolean ok = new BonusBloodlustAbility().activate(player);
            if (!ok) {
                context.fail("Bloodlust did not activate");
            }
        });

        GemsGameTestUtil.assertEventually(
                context,
                10L,
                160L,
                2L,
                () -> {
                    MobEffectInstance haste = player.getEffect(MobEffects.HASTE);
                    MobEffectInstance strength = player.getEffect(MobEffects.STRENGTH);
                    return haste != null && haste.getAmplifier() >= 2
                            && strength != null && strength.getAmplifier() >= 1;
                },
                "Bloodlust effects mismatch"
        );
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusChainLightningSpawnsBolts(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        placePlatform(context, 12);
        ServerPlayer player = setupPlayer(context);
        Vec3 base = origin(context);
        Zombie z1 = spawnZombie(world, base.add(3.0D, 0.0D, 0.0D));
        Zombie z2 = spawnZombie(world, base.add(-3.0D, 0.0D, 0.0D));
        if (z1 == null || z2 == null) {
            context.fail("Failed to spawn zombies");
            return;
        }
        for (Zombie zombie : java.util.List.of(z1, z2)) {
            zombie.setNoAi(true);
            zombie.setNoGravity(true);
            zombie.setDeltaMovement(Vec3.ZERO);
        }
        AABB box = new AABB(player.blockPosition()).inflate(12.0);
        int before = world.getEntitiesOfClass(LightningBolt.class, box, e -> true).size();

        context.runAtTickTime(20L, () -> {
            boolean ok = new BonusChainLightningAbility().activate(player);
            if (!ok) {
                context.fail("Chain Lightning did not activate");
            }
        });

        GemsGameTestUtil.assertEventually(
                context,
                21L,
                30L,
                1L,
                () -> {
                    int after = world.getEntitiesOfClass(LightningBolt.class, box, e -> true).size();
                    return after - before >= 2;
                },
                "Chain Lightning did not spawn enough lightning"
        );
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusCorpseExplosionDetonatesLowHealth(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        Vec3 base = origin(context);
        Zombie corpse = spawnZombie(world, base.add(2.0D, 0.0D, 0.0D));
        Zombie victim = spawnZombie(world, base.add(3.0D, 0.0D, 0.0D));
        if (corpse == null || victim == null) {
            context.fail("Failed to spawn zombies");
            return;
        }
        float victimBefore = victim.getHealth();

        context.runAtTickTime(5L, () -> {
            boolean ok = new BonusCorpseExplosionAbility().activate(player);
            if (!ok) {
                context.fail("Corpse Explosion did not activate");
            }
        });

        context.runAtTickTime(8L, () -> {
            corpse.hurtServer(world, world.damageSources().generic(), 1000.0F);
        });

        context.runAtTickTime(12L, () -> {
            if (victim.getHealth() >= victimBefore) {
                context.fail("Corpse Explosion did not damage nearby target");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusCrystalCageBuildsCage(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        placePlatform(context, 10);
        Vec3 targetPos = origin(context).add(0.0D, 0.0D, 6.0D);
        Zombie target = spawnZombie(world, targetPos);
        if (target == null) {
            context.fail("Failed to spawn zombie");
            return;
        }
        target.setNoAi(true);
        target.setNoGravity(true);
        target.setDeltaMovement(Vec3.ZERO);
        BlockPos center = target.blockPosition();

        context.runAtTickTime(20L, () -> {
            aimAt(player, world, targetPos.add(0.0D, 1.0D, 0.0D));
            boolean ok = new BonusCrystalCageAbility().activate(player);
            if (!ok) {
                context.fail("Crystal Cage did not activate");
            }
        });

        GemsGameTestUtil.assertEventually(
                context,
                22L,
                80L,
                2L,
                () -> {
                    int amethyst = 0;
                    for (int y = 0; y <= 2; y++) {
                        for (int x = -1; x <= 1; x++) {
                            for (int z = -1; z <= 1; z++) {
                                if (Math.abs(x) == 1 || Math.abs(z) == 1 || y == 2) {
                                    if (world.getBlockState(center.offset(x, y, z)).is(Blocks.AMETHYST_BLOCK)) {
                                        amethyst++;
                                    }
                                }
                            }
                        }
                    }
                    return amethyst >= 8;
                },
                "Crystal Cage did not place enough blocks"
        );
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusCurseBoltDrainsHealth(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        placePlatform(context, 6);
        Vec3 targetPos = origin(context).add(2.0D, 0.0D, 0.0D);
        Zombie target = spawnZombie(world, targetPos);
        if (target == null) {
            context.fail("Failed to spawn zombie");
            return;
        }
        target.setNoAi(true);
        target.setNoGravity(true);
        target.setDeltaMovement(Vec3.ZERO);
        target.setItemSlot(
                net.minecraft.world.entity.EquipmentSlot.HEAD,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET)
        );
        target.setRemainingFireTicks(0);
        var maxHealth = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(20.0D);
        }
        float[] playerBefore = new float[1];
        float[] targetBefore = new float[1];

        context.runAtTickTime(40L, () -> {
            aimAt(player, world, targetPos.add(0.0D, 1.0D, 0.0D));
            player.setDeltaMovement(Vec3.ZERO);
            player.setHealth(8.0F);
            playerBefore[0] = player.getHealth();
            targetBefore[0] = target.getHealth();
            boolean ok = new BonusCurseBoltAbility().activate(player);
            if (!ok) {
                context.fail("Curse Bolt did not activate");
            }
        });

        GemsGameTestUtil.assertEventually(
                context,
                41L,
                160L,
                1L,
                () -> target.getHealth() < targetBefore[0] && player.getHealth() > playerBefore[0],
                "Curse Bolt did not damage target and heal player"
        );
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusDecoyTrapSpawnsTrapItem(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        AABB box = new AABB(player.blockPosition()).inflate(4.0);

        context.runAtTickTime(5L, () -> {
            boolean ok = new BonusDecoyTrapAbility().activate(player);
            if (!ok) {
                context.fail("Decoy Trap did not activate");
            }
            List<ItemEntity> items = world.getEntitiesOfClass(ItemEntity.class, box, e -> true);
            if (items.isEmpty()) {
                context.fail("Decoy Trap did not spawn item");
                return;
            }
            ItemEntity item = items.get(0);
            if (!player.getUUID().equals(BonusDecoyTrapAbility.getTrapOwner(item))) {
                context.fail("Decoy Trap owner mismatch");
            }
            Zombie picker = spawnZombie(world, item.position());
            if (picker == null) {
                context.fail("Failed to spawn picker");
                return;
            }
            boolean triggered = BonusDecoyTrapAbility.triggerTrap(item, picker);
            if (!triggered || item.isAlive()) {
                context.fail("Decoy Trap did not trigger");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusDoomBoltSpawnsWitherSkull(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        AABB box = new AABB(player.blockPosition()).inflate(12.0);
        int before = world.getEntitiesOfClass(WitherSkull.class, box, e -> true).size();

        context.runAtTickTime(5L, () -> {
            boolean ok = new BonusDoomBoltAbility().activate(player);
            if (!ok) {
                context.fail("Doom Bolt did not activate");
            }
        });

        GemsGameTestUtil.assertEventually(
                context,
                6L,
                30L,
                1L,
                () -> {
                    int after = world.getEntitiesOfClass(WitherSkull.class, box, e -> true).size();
                    return after - before >= 1;
                },
                "Doom Bolt did not spawn Wither Skull"
        );
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusEarthshatterDamagesAndLaunches(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        Zombie target = spawnZombie(world, origin(context).add(2.0D, 0.0D, 0.0D));
        if (target == null) {
            context.fail("Failed to spawn zombie");
            return;
        }
        float before = target.getHealth();

        context.runAtTickTime(5L, () -> {
            boolean ok = new BonusEarthshatterAbility().activate(player);
            if (!ok) {
                context.fail("Earthshatter did not activate");
            }
            if (target.getHealth() >= before) {
                context.fail("Earthshatter did not damage target");
            }
            if (target.getDeltaMovement().y <= 0.1D) {
                context.fail("Earthshatter did not launch target");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusEtherealStepPhasesThroughWall(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        Vec3 base = origin(context);
        BlockPos wallBase = BlockPos.containing(base.add(0.0D, 0.0D, 3.0D));
        world.setBlockAndUpdate(wallBase, Blocks.STONE.defaultBlockState());
        world.setBlockAndUpdate(wallBase.above(), Blocks.STONE.defaultBlockState());
        Vec3 before = player.position();
        aimAt(player, world, base.add(0.0D, 0.0D, 6.0D));

        context.runAtTickTime(5L, () -> {
            boolean ok = new BonusEtherealStepAbility().activate(player);
            if (!ok) {
                context.fail("Ethereal Step did not activate");
            }
            if (player.getZ() <= before.z + 2.5D) {
                context.fail("Ethereal Step did not pass wall");
            }
            if (!EtherealState.isEthereal(player)) {
                context.fail("Ethereal Step did not set ethereal state");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusFrostbiteAppliesSlowness(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        Zombie target = spawnZombie(world, origin(context).add(2.0D, 0.0D, 0.0D));
        if (target == null) {
            context.fail("Failed to spawn zombie");
            return;
        }

        context.runAtTickTime(5L, () -> {
            boolean ok = new BonusFrostbiteAbility().activate(player);
            if (!ok) {
                context.fail("Frostbite did not activate");
            }
            if (!target.hasEffect(MobEffects.SLOWNESS) || !target.hasEffect(MobEffects.MINING_FATIGUE)) {
                context.fail("Frostbite did not apply effects");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusGravityCrushRootsTarget(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        Vec3 base = isolatedOrigin(context, player);
        teleport(player, world, base.x, base.y, base.z, 0.0F, 0.0F);
        player.setNoGravity(true);
        Vec3 targetPos = base.add(0.0D, 0.0D, 2.0D);
        Zombie target = spawnZombie(world, targetPos);
        if (target == null) {
            context.fail("Failed to spawn zombie");
            return;
        }
        target.setNoAi(true);
        target.setNoGravity(true);
        target.setDeltaMovement(Vec3.ZERO);
        float before = target.getHealth();

        context.runAtTickTime(5L, () -> {
            aimAt(player, world, targetPos.add(0.0D, 1.0D, 0.0D));
            boolean ok = new BonusGravityCrushAbility().activate(player);
            if (!ok) {
                context.fail("Gravity Crush did not activate");
            }
        });

        GemsGameTestUtil.assertEventually(
                context,
                6L,
                40L,
                2L,
                () -> target.getHealth() < before
                        && target.hasEffect(MobEffects.SLOWNESS)
                        && target.hasEffect(MobEffects.JUMP_BOOST),
                "Gravity Crush did not slam, damage, or root the target"
        );
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusGravityWellPullsTargets(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        Vec3 targetPos = origin(context).add(5.0D, 0.0D, 0.0D);
        Zombie target = spawnZombie(world, targetPos);
        if (target == null) {
            context.fail("Failed to spawn zombie");
            return;
        }

        context.runAtTickTime(5L, () -> {
            boolean ok = new BonusGravityWellAbility().activate(player);
            if (!ok) {
                context.fail("Gravity Well did not activate");
            }
            Vec3 toPlayer = player.position().subtract(target.position()).normalize();
            if (target.getDeltaMovement().dot(toPlayer) < 0.5D) {
                context.fail("Gravity Well did not pull target");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusIceWallPlacesPackedIce(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        Vec3 base = origin(context);
        placePlatform(context, 12);
        BlockPos target = BlockPos.containing(base.add(0.0D, 1.0D, 4.0D));
        world.setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());
        aimAt(player, world, Vec3.atCenterOf(target));
        BlockPos[] center = new BlockPos[] { target };

        context.runAtTickTime(5L, () -> {
            var hit = world.clip(new net.minecraft.world.level.ClipContext(
                    player.getEyePosition(),
                    player.getEyePosition().add(player.getLookAngle().scale(10)),
                    net.minecraft.world.level.ClipContext.Block.OUTLINE,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                    player
            ));
            center[0] = BlockPos.containing(hit.getLocation());

            boolean ok = new BonusIceWallAbility().activate(player);
            if (!ok) {
                context.fail("Ice Wall did not activate");
            }
            int packed = 0;
            for (int h = 0; h < 4; h++) {
                for (int w = -2; w <= 2; w++) {
                    if (world.getBlockState(center[0].offset(w, h, 0)).is(Blocks.PACKED_ICE)) {
                        packed++;
                    }
                }
            }
            if (packed < 6) {
                context.fail("Ice Wall did not place packed ice");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusIcicleBarrageFreezesTargets(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        Vec3 targetPos = origin(context).add(0.0D, 0.0D, 6.0D);
        Zombie target = spawnZombie(world, targetPos);
        if (target == null) {
            context.fail("Failed to spawn zombie");
            return;
        }
        aimAt(player, world, targetPos.add(0.0D, 1.0D, 0.0D));
        float before = target.getHealth();

        context.runAtTickTime(5L, () -> {
            boolean ok = new BonusIcicleBarrageAbility().activate(player);
            if (!ok) {
                context.fail("Icicle Barrage did not activate");
            }
            if (target.getHealth() >= before) {
                context.fail("Icicle Barrage did not damage target");
            }
            if (target.getTicksFrozen() <= 0) {
                context.fail("Icicle Barrage did not freeze target");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusInfernoDashLeavesFire(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        placePlatform(context, 12);
        Vec3 base = origin(context);
        int dashDistance = 2;

        context.runAtTickTime(5L, () -> {
            aimAt(player, world, base.add(0.0D, 0.0D, dashDistance));
            player.setDeltaMovement(Vec3.ZERO);
            boolean ok = new BonusInfernoDashAbility().activate(player);
            if (!ok) {
                context.fail("Inferno Dash did not activate");
            }
            boolean fireFound = false;
            for (int i = 1; i <= dashDistance; i++) {
                BlockPos pos = BlockPos.containing(base.add(0.0D, 0.0D, i));
                if (world.getBlockState(pos).is(Blocks.FIRE)) {
                    fireFound = true;
                    break;
                }
            }
            if (!fireFound) {
                context.fail("Inferno Dash did not place fire");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusLifeTapCostsHealthAndReducesCooldowns(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        player.setHealth(20.0F);
        float before = player.getHealth();

        long now = world.getGameTime();
        // Give the player a fake cooldown to reduce.
        com.feel.gems.power.runtime.GemAbilityCooldowns.setNextAllowedTick(
                player,
                com.feel.gems.power.registry.PowerIds.BONUS_THUNDERSTRIKE,
                now + 200L
        );

        context.runAtTickTime(5L, () -> {
            player.setHealth(20.0F);
            boolean ok = new BonusLifeTapAbility().activate(player);
            if (!ok) {
                context.fail("Life Tap did not activate");
            }
            if (player.getHealth() >= before) {
                context.fail("Life Tap did not cost health");
            }
            long after = com.feel.gems.power.runtime.GemAbilityCooldowns.nextAllowedTick(player, com.feel.gems.power.registry.PowerIds.BONUS_THUNDERSTRIKE);
            if (after >= now + 200L) {
                context.fail("Life Tap did not reduce cooldowns");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusMagmaPoolCreatesMagma(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        placePlatform(context, 6);
        aimAt(player, world, Vec3.atCenterOf(player.blockPosition().below()));
        BlockPos[] center = new BlockPos[] { player.blockPosition().below().above() };

        context.runAtTickTime(5L, () -> {
            var hit = player.pick(16.0D, 1.0F, false);
            if (hit instanceof net.minecraft.world.phys.BlockHitResult bhr) {
                center[0] = bhr.getBlockPos().above();
            }
            boolean ok = new BonusMagmaPoolAbility().activate(player);
            if (!ok) {
                context.fail("Magma Pool did not activate");
            }
            int lava = 0;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (world.getBlockState(center[0].offset(dx, 0, dz)).is(Blocks.LAVA)) {
                        lava++;
                    }
                }
            }
            if (lava < 5) {
                context.fail("Magma Pool did not place lava");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusMindSpikeDamagesAndGlows(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        Vec3 targetPos = origin(context).add(0.0D, 0.0D, 6.0D);
        Zombie target = spawnZombie(world, targetPos);
        if (target == null) {
            context.fail("Failed to spawn zombie");
            return;
        }
        aimAt(player, world, targetPos.add(0.0D, 1.0D, 0.0D));
        float before = target.getHealth();

        context.runAtTickTime(5L, () -> {
            boolean ok = new BonusMindSpikeAbility().activate(player);
            if (!ok) {
                context.fail("Mind Spike did not activate");
            }
            if (target.getHealth() >= before) {
                context.fail("Mind Spike did not damage target");
            }
            if (!target.hasEffect(MobEffects.GLOWING)) {
                context.fail("Mind Spike did not apply glowing");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusMirrorImageSpawnsIllusions(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        AABB box = new AABB(player.blockPosition()).inflate(4.0);

        context.runAtTickTime(5L, () -> {
            boolean ok = new BonusMirrorImageAbility().activate(player);
            if (!ok) {
                context.fail("Mirror Image did not activate");
            }
            List<ArmorStand> stands = world.getEntitiesOfClass(ArmorStand.class, box,
                    e -> e.entityTags().contains("gems_mirror_image"));
            if (stands.size() < 3) {
                context.fail("Mirror Image did not spawn 3 illusions");
            }
            if (!player.hasEffect(MobEffects.INVISIBILITY)) {
                context.fail("Mirror Image did not cloak player");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusOverchargeMarksPlayer(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        player.setHealth(20.0F);

        context.runAtTickTime(20L, () -> {
            float before = player.getHealth();
            boolean ok = new BonusOverchargeAbility().activate(player);
            if (!ok) {
                context.fail("Overcharge did not activate");
            }
        });

        GemsGameTestUtil.assertEventually(
                context,
                21L,
                80L,
                2L,
                () -> player.getHealth() < 20.0F && BonusOverchargeAbility.isActive(player),
                "Overcharge did not cost health or apply state"
        );
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusPlagueCloudCreatesAreaEffect(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        Vec3 base = isolatedOrigin(context, player);
        teleport(player, world, base.x, base.y, base.z, 0.0F, 0.0F);
        player.setNoGravity(true);
        AABB box = new AABB(player.blockPosition()).inflate(16.0);
        Vec3 targetPos = base.add(0.0D, 0.0D, 2.0D);
        BlockPos marker = BlockPos.containing(targetPos.x, targetPos.y - 1.0D, targetPos.z);
        world.setBlockAndUpdate(marker, Blocks.STONE.defaultBlockState());
        Cow target = EntityTypes.COW.create(world, EntitySpawnReason.TRIGGERED);
        if (target == null) {
            context.fail("Failed to spawn cow");
            return;
        }
        target.snapTo(targetPos.x, targetPos.y, targetPos.z, 0.0F, 0.0F);
        target.setNoAi(true);
        world.addFreshEntity(target);
        target.setNoGravity(true);
        target.setDeltaMovement(Vec3.ZERO);
        Vec3[] cloudPos = new Vec3[1];

        context.runAtTickTime(40L, () -> {
            aimAt(player, world, Vec3.atCenterOf(marker));
            boolean ok = new BonusPlagueCloudAbility().activate(player);
            if (!ok) {
                context.fail("Plague Cloud did not activate");
            }
        });

        GemsGameTestUtil.assertEventually(
                context,
                42L,
                160L,
                2L,
                () -> {
                    if (cloudPos[0] == null) {
                        List<AreaEffectCloud> clouds = world.getEntitiesOfClass(AreaEffectCloud.class, box, e -> true);
                        if (!clouds.isEmpty()) {
                            cloudPos[0] = clouds.get(0).position();
                            Vec3 pos = cloudPos[0];
                            target.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
                            target.setDeltaMovement(Vec3.ZERO);
                        }
                    }
                    return target.hasEffect(MobEffects.POISON) && target.hasEffect(MobEffects.WEAKNESS);
                },
                "Plague Cloud should apply poison and weakness to targets inside it"
        );
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusPurgeRemovesBuffs(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        Vec3 targetPos = origin(context).add(0.0D, 0.0D, 6.0D);
        Zombie target = spawnZombie(world, targetPos);
        if (target == null) {
            context.fail("Failed to spawn zombie");
            return;
        }
        target.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 1));
        aimAt(player, world, targetPos.add(0.0D, 1.0D, 0.0D));

        context.runAtTickTime(5L, () -> {
            boolean ok = new BonusPurgeAbility().activate(player);
            if (!ok) {
                context.fail("Purge did not activate");
            }
            if (target.hasEffect(MobEffects.SPEED)) {
                context.fail("Purge did not remove buff");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusQuicksandAppliesSlownessAndCobwebs(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        placePlatform(context, 6);
        Vec3 targetPos = origin(context).add(0.0D, 0.0D, 6.0D);
        Zombie target = spawnZombie(world, targetPos);
        if (target == null) {
            context.fail("Failed to spawn zombie");
            return;
        }
        aimAt(player, world, targetPos.add(0.0D, 1.0D, 0.0D));
        BlockPos center = BlockPos.containing(targetPos);

        context.runAtTickTime(5L, () -> {
            boolean ok = new BonusQuicksandAbility().activate(player);
            if (!ok) {
                context.fail("Quicksand did not activate");
            }
            if (!target.hasEffect(MobEffects.SLOWNESS)) {
                context.fail("Quicksand did not apply slowness");
            }
            boolean cobwebFound = false;
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    if (world.getBlockState(center.offset(x, 0, z)).is(Blocks.COBWEB)) {
                        cobwebFound = true;
                        break;
                    }
                }
            }
            if (!cobwebFound) {
                context.fail("Quicksand did not place cobwebs");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusRadiantBurstBlindsEnemies(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        Zombie target = spawnZombie(world, origin(context).add(2.0D, 0.0D, 0.0D));
        if (target == null) {
            context.fail("Failed to spawn zombie");
            return;
        }
        target.setNoAi(true);
        target.setNoGravity(true);
        target.setDeltaMovement(Vec3.ZERO);

        context.runAtTickTime(20L, () -> {
            boolean ok = new BonusRadiantBurstAbility().activate(player);
            if (!ok) {
                context.fail("Radiant Burst did not activate");
            }
        });

        GemsGameTestUtil.assertEventually(
                context,
                22L,
                80L,
                2L,
                () -> target.hasEffect(MobEffects.BLINDNESS) && target.hasEffect(MobEffects.GLOWING),
                "Radiant Burst did not apply effects"
        );
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusReflectionWardTagsPlayer(GameTestHelper context) {
        ServerPlayer player = setupPlayer(context);
        context.runAtTickTime(5L, () -> {
            boolean ok = new BonusReflectionWardAbility().activate(player);
            if (!ok) {
                context.fail("Reflection Ward did not activate");
            }
            boolean found = player.entityTags().stream().anyMatch(tag -> tag.startsWith("gems_reflection_ward:"));
            if (!found) {
                context.fail("Reflection Ward did not set tag");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusSanctuaryBuffsSelfAndPushes(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        Zombie target = spawnZombie(world, origin(context).add(2.0D, 0.0D, 0.0D));
        if (target == null) {
            context.fail("Failed to spawn zombie");
            return;
        }

        context.runAtTickTime(5L, () -> {
            boolean ok = new BonusSanctuaryAbility().activate(player);
            if (!ok) {
                context.fail("Sanctuary did not activate");
            }
            if (!player.hasEffect(MobEffects.REGENERATION) || !player.hasEffect(MobEffects.RESISTANCE)) {
                context.fail("Sanctuary did not buff player");
            }
            Vec3 toTarget = target.position().subtract(player.position()).normalize();
            if (target.getDeltaMovement().dot(toTarget) < 0.5D) {
                context.fail("Sanctuary did not push enemy");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusSearingLightBurnsUndead(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        Vec3 targetPos = origin(context).add(0.0D, 0.0D, 8.0D);
        Zombie target = spawnZombie(world, targetPos);
        if (target == null) {
            context.fail("Failed to spawn zombie");
            return;
        }
        aimAt(player, world, targetPos.add(0.0D, 1.0D, 0.0D));

        context.runAtTickTime(5L, () -> {
            boolean ok = new BonusSearingLightAbility().activate(player);
            if (!ok) {
                context.fail("Searing Light did not activate");
            }
            if (!target.isOnFire()) {
                context.fail("Searing Light did not ignite undead");
            }
            if (target.getHealth() > 6.0F) {
                context.fail("Searing Light did not deal bonus damage");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusShadowstepTeleportsForward(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        Vec3 before = player.position();
        aimAt(player, world, before.add(0.0D, 0.0D, 12.0D));

        context.runAtTickTime(5L, () -> {
            boolean ok = new BonusShadowstepAbility().activate(player);
            if (!ok) {
                context.fail("Shadowstep did not activate");
            }
            if (player.position().distanceTo(before) < 3.5D) {
                context.fail("Shadowstep did not move player far enough");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusSmokeScreenGrantsInvisibility(GameTestHelper context) {
        ServerPlayer player = setupPlayer(context);
        context.runAtTickTime(5L, () -> {
            boolean ok = new BonusSmokeScreenAbility().activate(player);
            if (!ok) {
                context.fail("Smoke Screen did not activate");
            }
            if (!player.hasEffect(MobEffects.INVISIBILITY) || !player.hasEffect(MobEffects.SPEED)) {
                context.fail("Smoke Screen missing effects");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusSonicBoomDamagesAndStaggers(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        Zombie target = spawnZombie(world, origin(context).add(2.0D, 0.0D, 0.0D));
        if (target == null) {
            context.fail("Failed to spawn zombie");
            return;
        }
        float before = target.getHealth();

        context.runAtTickTime(5L, () -> {
            boolean ok = new BonusSonicBoomAbility().activate(player);
            if (!ok) {
                context.fail("Sonic Boom did not activate");
            }
            if (target.getHealth() >= before) {
                context.fail("Sonic Boom did not damage target");
            }
            if (!target.hasEffect(MobEffects.SLOWNESS) || !target.hasEffect(MobEffects.NAUSEA)) {
                context.fail("Sonic Boom missing effects");
            }
            Vec3 away = target.position().subtract(player.position()).normalize();
            if (target.getDeltaMovement().dot(away) < 0.2D) {
                context.fail("Sonic Boom did not knock back target");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusSoulLinkAppliesGlowing(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        placePlatform(context, 10);
        Vec3 targetPos = origin(context).add(0.0D, 0.0D, 6.0D);
        Zombie target = spawnZombie(world, targetPos);
        if (target == null) {
            context.fail("Failed to spawn zombie");
            return;
        }
        target.setNoAi(true);
        target.setNoGravity(true);
        target.setDeltaMovement(Vec3.ZERO);

        context.runAtTickTime(40L, () -> {
            aimAt(player, world, targetPos.add(0.0D, 1.0D, 0.0D));
            boolean ok = new BonusSoulLinkAbility().activate(player);
            if (!ok) {
                context.fail("Soul Link did not activate");
            }
        });

        GemsGameTestUtil.assertEventually(
                context,
                42L,
                160L,
                2L,
                () -> player.hasEffect(MobEffects.GLOWING) && target.hasEffect(MobEffects.GLOWING),
                "Soul Link missing glowing effects"
        );
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusSoulSwapSwapsPositions(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        Vec3 base = isolatedOrigin(context, player);
        teleport(player, world, base.x, base.y, base.z, 0.0F, 0.0F);
        player.setNoGravity(true);
        Vec3 playerPos = player.position();
        Vec3 targetPos = base.add(0.0D, 3.0D, 0.0D);
        Zombie target = spawnZombie(world, targetPos);
        if (target == null) {
            context.fail("Failed to spawn zombie");
            return;
        }
        target.setNoAi(true);
        target.setNoGravity(true);
        target.setDeltaMovement(Vec3.ZERO);

        context.runAtTickTime(5L, () -> {
            AABB cleanupBox = new AABB(player.blockPosition()).inflate(4.0D);
            for (LivingEntity living : world.getEntitiesOfClass(LivingEntity.class, cleanupBox, e -> e != player && e != target)) {
                living.discard();
            }
            aimAt(player, world, targetPos.add(0.0D, 1.0D, 0.0D));
            player.setDeltaMovement(Vec3.ZERO);
            boolean ok = new BonusSoulSwapAbility().activate(player);
            if (!ok) {
                context.fail("Soul Swap did not activate");
            }
        });

        GemsGameTestUtil.assertEventually(
                context,
                10L,
                180L,
                2L,
                () -> player.position().distanceTo(targetPos) <= 1.5D
                        && target.position().distanceTo(playerPos) <= 1.5D,
                "Soul Swap did not move player and target"
        );
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusSpectralBladeSpawnsVex(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        AABB box = new AABB(player.blockPosition()).inflate(4.0);

        context.runAtTickTime(5L, () -> {
            boolean ok = new BonusSpectralBladeAbility().activate(player);
            if (!ok) {
                context.fail("Spectral Blade did not activate");
            }
            List<Vex> vexes = world.getEntitiesOfClass(Vex.class, box,
                    vex -> BonusSpectralBladeAbility.isOwnedBy(vex, player));
            if (vexes.isEmpty()) {
                context.fail("Spectral Blade did not spawn owned vex");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusSpectralChainsRootsTargets(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        Zombie target = spawnZombie(world, origin(context).add(2.0D, 0.0D, 0.0D));
        if (target == null) {
            context.fail("Failed to spawn zombie");
            return;
        }

        context.runAtTickTime(5L, () -> {
            boolean ok = new BonusSpectralChainsAbility().activate(player);
            if (!ok) {
                context.fail("Spectral Chains did not activate");
            }
            if (!target.hasEffect(MobEffects.SLOWNESS) || !target.hasEffect(MobEffects.JUMP_BOOST)) {
                context.fail("Spectral Chains missing effects");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusStarfallSummonsMeteors(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        AABB box = new AABB(player.blockPosition()).inflate(40.0);
        int before = world.getEntitiesOfClass(SmallFireball.class, box, e -> true).size();

        context.runAtTickTime(5L, () -> {
            boolean ok = new BonusStarfallAbility().activate(player);
            if (!ok) {
                context.fail("Starfall did not activate");
            }
            int after = world.getEntitiesOfClass(SmallFireball.class, box, e -> true).size();
            if (after - before < 8) {
                context.fail("Starfall did not spawn enough meteors");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusThornsNovaDamagesTargets(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        Zombie target = spawnZombie(world, origin(context).add(2.0D, 0.0D, 0.0D));
        if (target == null) {
            context.fail("Failed to spawn zombie");
            return;
        }
        float before = target.getHealth();

        context.runAtTickTime(5L, () -> {
            boolean ok = new BonusThornsNovaAbility().activate(player);
            if (!ok) {
                context.fail("Thorns Nova did not activate");
            }
            if (target.getHealth() >= before) {
                context.fail("Thorns Nova did not damage target");
            }
            Vec3 away = target.position().subtract(player.position()).normalize();
            if (target.getDeltaMovement().dot(away) < 0.1D) {
                context.fail("Thorns Nova did not knock back target");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusThunderstrikeSummonsLightning(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        AABB box = new AABB(player.blockPosition()).inflate(60.0);
        int before = world.getEntitiesOfClass(LightningBolt.class, box, e -> true).size();

        context.runAtTickTime(5L, () -> {
            boolean ok = new BonusThunderstrikeAbility().activate(player);
            if (!ok) {
                context.fail("Thunderstrike did not activate");
            }
            int after = world.getEntitiesOfClass(LightningBolt.class, box, e -> true).size();
            if (after - before < 1) {
                context.fail("Thunderstrike did not spawn lightning");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusTidalWaveKnocksBack(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        Vec3 targetPos = origin(context).add(0.0D, 0.0D, 6.0D);
        Zombie target = spawnZombie(world, targetPos);
        if (target == null) {
            context.fail("Failed to spawn zombie");
            return;
        }
        aimAt(player, world, targetPos.add(0.0D, 1.0D, 0.0D));

        context.runAtTickTime(5L, () -> {
            boolean ok = new BonusTidalWaveAbility().activate(player);
            if (!ok) {
                context.fail("Tidal Wave did not activate");
            }
            if (!target.hasEffect(MobEffects.SLOWNESS)) {
                context.fail("Tidal Wave did not slow target");
            }
            Vec3 away = target.position().subtract(player.position()).normalize();
            if (target.getDeltaMovement().dot(away) < 0.2D) {
                context.fail("Tidal Wave did not knock back target");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusTimewarpBoostsSpeed(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        placePlatform(context, 10);
        Zombie target = spawnZombie(world, origin(context).add(2.0D, 0.0D, 0.0D));
        if (target == null) {
            context.fail("Failed to spawn zombie");
            return;
        }
        target.setNoAi(true);
        target.setNoGravity(true);
        target.setDeltaMovement(Vec3.ZERO);
        context.runAtTickTime(40L, () -> {
            boolean ok = new BonusTimewarpAbility().activate(player);
            if (!ok) {
                context.fail("Timewarp did not activate");
                return;
            }
        });

        GemsGameTestUtil.assertEventually(
                context,
                42L,
                120L,
                2L,
                () -> target.hasEffect(MobEffects.SLOWNESS),
                "Timewarp should apply Slowness to nearby enemies"
        );
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusTremorSlowsEnemies(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        placePlatform(context, 12);
        ServerPlayer player = setupPlayer(context);
        Zombie target = spawnZombie(world, origin(context).add(2.0D, 0.0D, 0.0D));
        if (target == null) {
            context.fail("Failed to spawn zombie");
            return;
        }

        context.runAtTickTime(5L, () -> {
            boolean ok = new BonusTremorAbility().activate(player);
            if (!ok) {
                context.fail("Tremor did not activate");
            }
            if (!target.hasEffect(MobEffects.SLOWNESS) || !target.hasEffect(MobEffects.MINING_FATIGUE)) {
                context.fail("Tremor missing effects");
            }
            if (target.getDeltaMovement().y <= 0.05D) {
                context.fail("Tremor did not stagger target");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusVampiricTouchDrainsAndHeals(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        Vec3 base = isolatedOrigin(context, player);
        teleport(player, world, base.x, base.y, base.z, 0.0F, 0.0F);
        player.setNoGravity(true);
        Vec3 targetPos = base.add(0.0D, 0.0D, 1.5D);
        Cow target = EntityTypes.COW.create(world, EntitySpawnReason.TRIGGERED);
        if (target == null) {
            context.fail("Failed to create target");
            return;
        }
        target.snapTo(targetPos.x, targetPos.y, targetPos.z, 0.0F, 0.0F);
        target.setNoAi(true);
        target.setNoGravity(true);
        world.addFreshEntity(target);
        aimAt(player, world, targetPos.add(0.0D, 1.0D, 0.0D));
        player.setHealth(10.0F);

        context.runAtTickTime(5L, () -> {
            aimAt(player, world, targetPos.add(0.0D, 1.0D, 0.0D));
            target.setDeltaMovement(Vec3.ZERO);
            if (Targeting.raycastLiving(player, 4.0D) != target) {
                context.fail("Vampiric Touch target was obscured before activation");
            }
            float playerBefore = player.getHealth();
            float targetBefore = target.getHealth();
            boolean ok = new BonusVampiricTouchAbility().activate(player);
            if (!ok) {
                context.fail("Vampiric Touch did not activate");
            }
            if (target.getHealth() >= targetBefore) {
                context.fail("Vampiric Touch did not damage target");
            }
            if (player.getHealth() <= playerBefore) {
                context.fail("Vampiric Touch did not heal player");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusVenomsprayPoisonsTargets(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        placePlatform(context, 4);
        Vec3 targetPos = origin(context).add(0.0D, 0.0D, 2.0D);
        Cow target = EntityTypes.COW.create(world, EntitySpawnReason.TRIGGERED);
        if (target == null) {
            context.fail("Failed to spawn cow");
            return;
        }
        target.snapTo(targetPos.x, targetPos.y, targetPos.z, 0.0F, 0.0F);
        target.setNoAi(true);
        world.addFreshEntity(target);
        target.setNoGravity(true);
        target.setDeltaMovement(Vec3.ZERO);
        float before = target.getHealth();

        context.runAtTickTime(40L, () -> {
            aimAt(player, world, targetPos.add(0.0D, 1.0D, 0.0D));
            boolean ok = new BonusVenomsprayAbility().activate(player);
            if (!ok) {
                context.fail("Venomspray did not activate");
            }
        });

        GemsGameTestUtil.assertEventually(
                context,
                42L,
                160L,
                2L,
                () -> target.getHealth() < before && target.hasEffect(MobEffects.POISON),
                "Venomspray did not damage target or apply Poison"
        );
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusVortexStrikePullsTargets(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        Vec3 targetPos = origin(context).add(2.0D, 0.0D, 0.0D);
        Zombie target = spawnZombie(world, targetPos);
        if (target == null) {
            context.fail("Failed to spawn zombie");
            return;
        }
        float before = target.getHealth();

        context.runAtTickTime(5L, () -> {
            boolean ok = new BonusVortexStrikeAbility().activate(player);
            if (!ok) {
                context.fail("Vortex Strike did not activate");
            }
            if (target.getHealth() >= before) {
                context.fail("Vortex Strike did not damage target");
            }
            Vec3 toPlayer = player.position().subtract(target.position()).normalize();
            if (target.getDeltaMovement().dot(toPlayer) < 0.1D) {
                context.fail("Vortex Strike did not pull target");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusVulnerabilityMarksTarget(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        Vec3 base = origin(context).add(0.0D, 120.0D, 0.0D);
        teleport(player, world, base.x, base.y, base.z, 0.0F, 0.0F);
        player.setNoGravity(true);
        Vec3 targetPos = base.add(0.0D, 0.0D, 6.0D);
        Zombie target = spawnZombie(world, targetPos);
        if (target == null) {
            context.fail("Failed to spawn zombie");
            return;
        }
        target.setNoAi(true);
        target.setNoGravity(true);
        target.setDeltaMovement(Vec3.ZERO);

        context.runAtTickTime(5L, () -> {
            aimAt(player, world, target.getEyePosition());
            boolean ok = new BonusVulnerabilityAbility().activate(player);
            if (!ok) {
                context.fail("Vulnerability did not activate");
            }
            if (!target.hasEffect(MobEffects.WEAKNESS)) {
                context.fail("Vulnerability did not apply weakness");
            }
            boolean tagged = target.entityTags().stream().anyMatch(tag -> tag.startsWith("gems_vulnerable:"));
            if (!tagged) {
                context.fail("Vulnerability did not tag target");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusWarpStrikeTeleportsBehindAndDamages(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        Vec3 targetPos = origin(context).add(0.0D, 0.0D, 6.0D);
        Zombie target = spawnZombie(world, targetPos);
        if (target == null) {
            context.fail("Failed to spawn zombie");
            return;
        }
        target.setNoAi(true);
        aimAt(player, world, targetPos.add(0.0D, 1.0D, 0.0D));
        float before = target.getHealth();

        context.runAtTickTime(5L, () -> {
            aimAt(player, world, target.position().add(0.0D, 1.0D, 0.0D));
            boolean ok = new BonusWarpStrikeAbility().activate(player);
            if (!ok) {
                context.fail("Warp Strike did not activate");
            }
            if (target.getHealth() >= before) {
                context.fail("Warp Strike did not damage target");
            }
            Vec3 targetFacing = Vec3.directionFromRotation(0, target.getYRot()).normalize();
            Vec3 expected = target.position().subtract(targetFacing.scale(2.0));
            if (player.position().distanceTo(expected) > 1.5D) {
                context.fail("Warp Strike did not place player behind target");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusWindSlashDamagesTargets(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        Vec3 targetPos = origin(context).add(0.0D, 0.0D, 4.0D);
        Zombie target = spawnZombie(world, targetPos);
        if (target == null) {
            context.fail("Failed to spawn zombie");
            return;
        }
        aimAt(player, world, targetPos.add(0.0D, 1.0D, 0.0D));
        float before = target.getHealth();

        context.runAtTickTime(5L, () -> {
            boolean ok = new BonusWindSlashAbility().activate(player);
            if (!ok) {
                context.fail("Wind Slash did not activate");
            }
            if (target.getHealth() >= before) {
                context.fail("Wind Slash did not damage target");
            }
            if (target.getDeltaMovement().length() < 0.2D) {
                context.fail("Wind Slash did not push target");
            }
            context.succeed();
        });
    }
}
