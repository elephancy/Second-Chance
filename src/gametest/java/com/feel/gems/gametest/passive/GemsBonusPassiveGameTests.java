package com.feel.gems.gametest.passive;

import net.minecraft.world.entity.EntityTypes;
import com.feel.gems.bonus.BonusClaimsState;
import com.feel.gems.config.GemsBalance;
import com.feel.gems.core.GemId;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.power.bonus.BonusPassiveRuntime;
import com.feel.gems.power.registry.PowerIds;
import com.feel.gems.power.runtime.GemPowers;
import com.feel.gems.state.GemPlayerState;
import com.feel.gems.state.PlayerStateManager;
import com.feel.gems.trust.GemTrust;
import java.util.EnumSet;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Behavior tests for bonus passives that are implemented in runtime logic.
 */
public final class GemsBonusPassiveGameTests {

    private static void teleport(ServerPlayer player, ServerLevel world, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(world, x, y, z, EnumSet.noneOf(Relative.class), yaw, pitch, false);
    }

    private static ServerPlayer setupBonusPlayer(GameTestHelper context, Vec3 pos) {
        ServerLevel world = context.getLevel();
        GemsGameTestUtil.placeStoneFloor(context, 10);
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.resetPlayerForTest(player);
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.AIR);
        GemPlayerState.setEnergy(player, 10);
        GemPowers.sync(player);
        BonusClaimsState.get(world.getServer()).releaseAllClaims(player.getUUID());
        return player;
    }

    private static void claimBonusPassives(ServerPlayer player, Identifier... passives) {
        ServerLevel world = player.level();
        BonusClaimsState claims = BonusClaimsState.get(world.getServer());
        claims.releaseAllClaims(player.getUUID());
        for (Identifier passiveId : passives) {
            UUID claimant = claims.getPassiveClaimant(passiveId);
            if (claimant != null && !claimant.equals(player.getUUID())) {
                claims.releasePassive(claimant, passiveId);
            }
            if (!claims.claimPassive(player.getUUID(), passiveId)) {
                throw new IllegalStateException("Failed to claim bonus passive " + passiveId);
            }
        }
        GemPowers.sync(player);
    }

    private static void addBonusPassive(ServerPlayer player, Identifier passiveId) {
        ServerLevel world = player.level();
        BonusClaimsState claims = BonusClaimsState.get(world.getServer());
        UUID claimant = claims.getPassiveClaimant(passiveId);
        if (claimant != null && !claimant.equals(player.getUUID())) {
            claims.releasePassive(claimant, passiveId);
        }
        if (!claims.claimPassive(player.getUUID(), passiveId)) {
            throw new IllegalStateException("Failed to claim bonus passive " + passiveId);
        }
        GemPowers.sync(player);
    }

    private static void claimBonusPassive(ServerPlayer player, Identifier passiveId) {
        ServerLevel world = player.level();
        BonusClaimsState claims = BonusClaimsState.get(world.getServer());
        // GameTests share a single server + persistent state. If a prior test failed mid-run, it can leave
        // claims behind and make later tests fail to claim. Prefer deterministic tests by force-releasing.
        UUID claimant = claims.getPassiveClaimant(passiveId);
        if (claimant != null && !claimant.equals(player.getUUID())) {
            claims.releasePassive(claimant, passiveId);
        }
        // Ensure this test player starts clean (max 2 passives).
        claims.releaseAllClaims(player.getUUID());
        if (!claims.claimPassive(player.getUUID(), passiveId)) {
            throw new IllegalStateException("Failed to claim bonus passive " + passiveId);
        }
        GemPowers.sync(player);
    }

    private static void releaseBonusPassive(ServerPlayer player, Identifier passiveId) {
        BonusClaimsState claims = BonusClaimsState.get(player.level().getServer());
        claims.releasePassive(player.getUUID(), passiveId);
        GemPowers.sync(player);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusAdrenalineRushOnKillGrantsSpeed(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer killer = setupBonusPlayer(context, pos);
        ServerPlayer victim = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(victim);
        teleport(victim, world, pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);

        claimBonusPassive(killer, PowerIds.BONUS_ADRENALINE_RUSH);

        context.runAtTickTime(5L, () -> {
            BonusPassiveRuntime.onKill(killer, victim);
            if (killer.getEffect(MobEffects.SPEED) == null) {
                context.fail("Adrenaline Rush should grant Speed on kill");
                return;
            }
            releaseBonusPassive(killer, PowerIds.BONUS_ADRENALINE_RUSH);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusAdrenalineSurgeAppliesSpeedAndHaste(GameTestHelper context) {
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer player = setupBonusPlayer(context, pos);
        claimBonusPassive(player, PowerIds.BONUS_ADRENALINE_SURGE);

        context.runAtTickTime(5L, () -> {
            if (player.getEffect(MobEffects.SPEED) == null || player.getEffect(MobEffects.HASTE) == null) {
                context.fail("Adrenaline Surge should grant Speed and Haste");
                return;
            }
            releaseBonusPassive(player, PowerIds.BONUS_ADRENALINE_SURGE);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusArcaneBarrierAbsorbsFirstHit(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer victim = setupBonusPlayer(context, pos);
        ServerPlayer attacker = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(attacker);
        teleport(attacker, world, pos.x + 2.0D, pos.y, pos.z, 180.0F, 0.0F);

        claimBonusPassive(victim, PowerIds.BONUS_ARCANE_BARRIER);

        context.runAtTickTime(5L, () -> {
            float before = victim.getHealth();
            victim.hurtServer(world, attacker.damageSources().playerAttack(attacker), 4.0F);
            if (victim.getHealth() < before) {
                context.fail("Arcane Barrier should absorb the first hit");
                return;
            }
            victim.hurtServer(world, attacker.damageSources().playerAttack(attacker), 4.0F);
            if (victim.getHealth() >= before) {
                context.fail("Arcane Barrier should go on cooldown after the first hit");
                return;
            }
            releaseBonusPassive(victim, PowerIds.BONUS_ARCANE_BARRIER);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusAttackSpeedGrantsHaste(GameTestHelper context) {
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer player = setupBonusPlayer(context, pos);
        double before = player.getAttributeValue(Attributes.ATTACK_SPEED);
        claimBonusPassive(player, PowerIds.BONUS_ATTACK_SPEED);

        context.runAtTickTime(5L, () -> {
            double after = player.getAttributeValue(Attributes.ATTACK_SPEED);
            if (after <= before) {
                context.fail("Attack Speed should increase attack speed attribute");
                return;
            }
            releaseBonusPassive(player, PowerIds.BONUS_ATTACK_SPEED);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusBattleMedicHealsAllies(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer healer = setupBonusPlayer(context, pos);
        ServerPlayer ally = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(ally);
        teleport(ally, world, pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);
        ally.setHealth(10.0F);

        claimBonusPassive(healer, PowerIds.BONUS_BATTLE_MEDIC);
        GemTrust.trust(healer, ally.getUUID());

        context.runAtTickTime(5L, () -> {
            BonusPassiveRuntime.tickEverySecond(healer, world);
            if (ally.getHealth() <= 10.0F) {
                context.fail("Battle Medic should heal nearby allies");
                return;
            }
            releaseBonusPassive(healer, PowerIds.BONUS_BATTLE_MEDIC);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusBerserkerBloodAppliesHasteAtLowHealth(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer player = setupBonusPlayer(context, pos);
        claimBonusPassive(player, PowerIds.BONUS_BERSERKER_BLOOD);
        player.setHealth(4.0F);

        context.runAtTickTime(5L, () -> {
            BonusPassiveRuntime.tickEverySecond(player, world);
            var haste = player.getEffect(MobEffects.HASTE);
            if (haste == null || haste.getAmplifier() < 1) {
                context.fail("Berserker Blood should grant Haste at low health");
                return;
            }
            releaseBonusPassive(player, PowerIds.BONUS_BERSERKER_BLOOD);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusBloodthirstHealsOnKill(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer killer = setupBonusPlayer(context, pos);
        ServerPlayer victim = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(victim);
        teleport(victim, world, pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);
        killer.setHealth(10.0F);

        claimBonusPassive(killer, PowerIds.BONUS_BLOODTHIRST);

        context.runAtTickTime(5L, () -> {
            BonusPassiveRuntime.onKill(killer, victim);
            if (killer.getHealth() <= 10.0F) {
                context.fail("Bloodthirst should heal on kill");
                return;
            }
            releaseBonusPassive(killer, PowerIds.BONUS_BLOODTHIRST);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusBulwarkIncreasesBlockingReduction(GameTestHelper context) {
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer player = setupBonusPlayer(context, pos);
        claimBonusPassive(player, PowerIds.BONUS_BULWARK);

        context.runAtTickTime(5L, () -> {
            float mult = BonusPassiveRuntime.getBlockingDamageMultiplier(player);
            if (mult >= 1.0F) {
                context.fail("Bulwark should increase shield blocking reduction");
                return;
            }
            releaseBonusPassive(player, PowerIds.BONUS_BULWARK);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusCriticalStrikeCanDoubleDamage(GameTestHelper context) {
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer attacker = setupBonusPlayer(context, pos);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(target);
        teleport(target, context.getLevel(), pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);
        claimBonusPassive(attacker, PowerIds.BONUS_CRITICAL_STRIKE);

        context.runAtTickTime(5L, () -> {
            if (!GemPowers.isPassiveActive(attacker, PowerIds.BONUS_CRITICAL_STRIKE)) {
                context.fail("Critical Strike passive was not active after claiming");
                return;
            }
            attacker.getRandom().setSeed(123L);
            boolean crit = false;
            for (int i = 0; i < 500; i++) {
                float mult = BonusPassiveRuntime.getAttackDamageMultiplier(attacker, target, 4.0F);
                if (mult > 1.1F) {
                    crit = true;
                    break;
                }
            }
            if (!crit) {
                context.fail("Critical Strike should sometimes increase damage");
                return;
            }
            releaseBonusPassive(attacker, PowerIds.BONUS_CRITICAL_STRIKE);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusCullingBladeBoostsDamageToLowHealth(GameTestHelper context) {
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer attacker = setupBonusPlayer(context, pos);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(target);
        teleport(target, context.getLevel(), pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);
        float threshold = GemsBalance.v().bonusPool().cullingBladeThresholdPercent / 100.0F;
        target.setHealth(Math.max(1.0F, target.getMaxHealth() * threshold * 0.5F));
        claimBonusPassive(attacker, PowerIds.BONUS_CULLING_BLADE);

        context.runAtTickTime(5L, () -> {
            float baseDamage = 1.0F;
            float mult = BonusPassiveRuntime.getAttackDamageMultiplier(attacker, target, baseDamage);
            if (baseDamage * mult < target.getHealth() + 0.5F) {
                context.fail("Culling Blade should provide enough damage to execute low-health targets");
                return;
            }
            releaseBonusPassive(attacker, PowerIds.BONUS_CULLING_BLADE);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusDamageReductionAppliesResistance(GameTestHelper context) {
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer player = setupBonusPlayer(context, pos);
        claimBonusPassive(player, PowerIds.BONUS_DAMAGE_REDUCTION);

        context.runAtTickTime(5L, () -> {
            if (player.getEffect(MobEffects.RESISTANCE) == null) {
                context.fail("Damage Reduction should grant Resistance");
                return;
            }
            releaseBonusPassive(player, PowerIds.BONUS_DAMAGE_REDUCTION);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusDodgeChanceCanAvoidDamage(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer victim = setupBonusPlayer(context, pos);
        ServerPlayer attacker = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(attacker);
        teleport(attacker, world, pos.x + 2.0D, pos.y, pos.z, 180.0F, 0.0F);
        claimBonusPassive(victim, PowerIds.BONUS_DODGE_CHANCE);

        context.runAtTickTime(5L, () -> {
            boolean dodged = false;
            for (int i = 0; i < 200; i++) {
                float before = victim.getHealth();
                victim.hurtServer(world, attacker.damageSources().playerAttack(attacker), 2.0F);
                if (victim.getHealth() == before) {
                    dodged = true;
                    break;
                }
                victim.setHealth(Math.min(victim.getMaxHealth(), before));
            }
            if (!dodged) {
                context.fail("Dodge Chance should occasionally avoid damage");
                return;
            }
            releaseBonusPassive(victim, PowerIds.BONUS_DODGE_CHANCE);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusEchoStrikeCanTrigger(GameTestHelper context) {
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer attacker = setupBonusPlayer(context, pos);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(target);
        teleport(target, context.getLevel(), pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);
        claimBonusPassive(attacker, PowerIds.BONUS_ECHO_STRIKE);

        context.runAtTickTime(5L, () -> {
            boolean triggered = false;
            for (int i = 0; i < 200; i++) {
                if (BonusPassiveRuntime.shouldEchoStrike(attacker)) {
                    triggered = true;
                    break;
                }
            }
            if (!triggered) {
                context.fail("Echo Strike should occasionally trigger");
                return;
            }
            releaseBonusPassive(attacker, PowerIds.BONUS_ECHO_STRIKE);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusElementalHarmonyGrantsFireResistance(GameTestHelper context) {
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer player = setupBonusPlayer(context, pos);
        claimBonusPassive(player, PowerIds.BONUS_ELEMENTAL_HARMONY);

        context.runAtTickTime(5L, () -> {
            if (player.getEffect(MobEffects.FIRE_RESISTANCE) == null) {
                context.fail("Elemental Harmony should grant Fire Resistance");
                return;
            }
            releaseBonusPassive(player, PowerIds.BONUS_ELEMENTAL_HARMONY);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusExecutionerBoostsDamageToLowHealth(GameTestHelper context) {
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer attacker = setupBonusPlayer(context, pos);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(target);
        teleport(target, context.getLevel(), pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);
        target.setHealth(4.0F);
        claimBonusPassive(attacker, PowerIds.BONUS_EXECUTIONER);

        context.runAtTickTime(5L, () -> {
            float mult = BonusPassiveRuntime.getAttackDamageMultiplier(attacker, target, 4.0F);
            if (mult <= 1.0F) {
                context.fail("Executioner should boost damage to low-health targets");
                return;
            }
            releaseBonusPassive(attacker, PowerIds.BONUS_EXECUTIONER);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusFocusedMindReducesCooldowns(GameTestHelper context) {
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer player = setupBonusPlayer(context, pos);
        claimBonusPassive(player, PowerIds.BONUS_FOCUSED_MIND);

        context.runAtTickTime(5L, () -> {
            float mult = BonusPassiveRuntime.getCooldownMultiplier(player);
            if (mult >= 1.0F) {
                context.fail("Focused Mind should reduce cooldowns");
                return;
            }
            releaseBonusPassive(player, PowerIds.BONUS_FOCUSED_MIND);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusHungerResistGrantsSaturation(GameTestHelper context) {
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer player = setupBonusPlayer(context, pos);
        claimBonusPassive(player, PowerIds.BONUS_HUNGER_RESIST);

        context.runAtTickTime(5L, () -> {
            if (player.getEffect(MobEffects.SATURATION) == null) {
                context.fail("Hunger Resist should grant Saturation");
                return;
            }
            releaseBonusPassive(player, PowerIds.BONUS_HUNGER_RESIST);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusImpactAbsorbAddsAbsorption(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer victim = setupBonusPlayer(context, pos);
        ServerPlayer attacker = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(attacker);
        teleport(attacker, world, pos.x + 2.0D, pos.y, pos.z, 180.0F, 0.0F);
        claimBonusPassive(victim, PowerIds.BONUS_IMPACT_ABSORB);

        float[] beforeAbsorb = new float[1];
        float[] beforeHealth = new float[1];
        context.runAtTickTime(5L, () -> {
            if (!GemPowers.isPassiveActive(victim, PowerIds.BONUS_IMPACT_ABSORB)) {
                context.fail("Impact Absorb passive was not active after claiming");
                return;
            }
            beforeAbsorb[0] = victim.getAbsorptionAmount();
            beforeHealth[0] = victim.getHealth();
            boolean dealt = victim.hurtServer(world, attacker.damageSources().playerAttack(attacker), 4.0F);
            if (!dealt) {
                context.fail("Impact Absorb test: damage was not dealt (invulnerable?)");
                return;
            }
            if (victim.getHealth() >= beforeHealth[0]) {
                context.fail("Impact Absorb test: health did not decrease");
                return;
            }
            // Check absorption was applied immediately in the same tick
            if (victim.getAbsorptionAmount() <= beforeAbsorb[0]) {
                context.fail("Impact Absorb should grant absorption immediately");
                return;
            }
        });

        context.runAtTickTime(10L, () -> {
            // Recheck on tick 10 to ensure absorption persists
            if (victim.getAbsorptionAmount() <= beforeAbsorb[0]) {
                context.fail("Impact Absorb should grant absorption on hit");
                return;
            }
            releaseBonusPassive(victim, PowerIds.BONUS_IMPACT_ABSORB);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusIntimidateWeakensNearbyMobs(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer player = setupBonusPlayer(context, pos);
        claimBonusPassive(player, PowerIds.BONUS_INTIMIDATE);

        Vec3 mobPos = context.absoluteVec(new Vec3(0.5D, 2.0D, 4.0D));
        Mob zombie = EntityTypes.ZOMBIE.create(world, e -> {}, BlockPos.containing(mobPos), EntitySpawnReason.TRIGGERED, false, false);
        if (zombie == null) {
            context.fail("Failed to spawn zombie");
            return;
        }
        zombie.snapTo(mobPos.x, mobPos.y, mobPos.z, 0.0F, 0.0F);
        world.addFreshEntity(zombie);

        context.runAtTickTime(5L, () -> {
            BonusPassiveRuntime.tickEverySecond(player, world);
            if (zombie.getEffect(MobEffects.WEAKNESS) == null) {
                context.fail("Intimidate should apply Weakness to nearby mobs");
                return;
            }
            releaseBonusPassive(player, PowerIds.BONUS_INTIMIDATE);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusIroncladIncreasesArmorAttribute(GameTestHelper context) {
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer player = setupBonusPlayer(context, pos);
        double[] before = new double[1];

        context.runAtTickTime(5L, () -> player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE)));
        context.runAtTickTime(15L, () -> before[0] = player.getAttributeValue(Attributes.ARMOR));
        context.runAtTickTime(20L, () -> claimBonusPassive(player, PowerIds.BONUS_IRONCLAD));

        context.runAtTickTime(35L, () -> {
            if (!GemPowers.isPassiveActive(player, PowerIds.BONUS_IRONCLAD)) {
                context.fail("Ironclad passive was not active after claiming");
                return;
            }
            var armor = player.getAttribute(Attributes.ARMOR);
            if (armor == null || armor.getModifier(net.minecraft.resources.Identifier.fromNamespaceAndPath("gems", "bonus_ironclad")) == null) {
                context.fail("Ironclad should apply an armor modifier");
                return;
            }
            double after = player.getAttributeValue(Attributes.ARMOR);
            if (after <= before[0]) {
                context.fail("Ironclad should increase armor attribute");
                return;
            }
            releaseBonusPassive(player, PowerIds.BONUS_IRONCLAD);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusLastStandBoostsDamageWhenLowHealth(GameTestHelper context) {
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer attacker = setupBonusPlayer(context, pos);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(target);
        teleport(target, context.getLevel(), pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);
        attacker.setHealth(4.0F);
        claimBonusPassive(attacker, PowerIds.BONUS_LAST_STAND);

        context.runAtTickTime(5L, () -> {
            float mult = BonusPassiveRuntime.getAttackDamageMultiplier(attacker, target, 4.0F);
            if (mult <= 1.0F) {
                context.fail("Last Stand should boost damage at low health");
                return;
            }
            releaseBonusPassive(attacker, PowerIds.BONUS_LAST_STAND);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusLifestealHealsOnDamage(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer attacker = setupBonusPlayer(context, pos);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(target);
        teleport(target, world, pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);
        attacker.setHealth(10.0F);
        claimBonusPassive(attacker, PowerIds.BONUS_LIFESTEAL);

        context.runAtTickTime(5L, () -> {
            float before = attacker.getHealth();
            target.hurtServer(world, attacker.damageSources().playerAttack(attacker), 4.0F);
            if (attacker.getHealth() <= before) {
                context.fail("Lifesteal should heal the attacker");
                return;
            }
            releaseBonusPassive(attacker, PowerIds.BONUS_LIFESTEAL);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusManaShieldConsumesXp(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer victim = setupBonusPlayer(context, pos);
        ServerPlayer attacker = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(attacker);
        teleport(attacker, world, pos.x + 2.0D, pos.y, pos.z, 180.0F, 0.0F);
        victim.experienceLevel = 10;
        claimBonusPassive(victim, PowerIds.BONUS_MANA_SHIELD);

        context.runAtTickTime(5L, () -> {
            int before = victim.experienceLevel;
            victim.hurtServer(world, attacker.damageSources().playerAttack(attacker), 6.0F);
            if (victim.experienceLevel >= before) {
                context.fail("Mana Shield should consume XP to absorb damage");
                return;
            }
            releaseBonusPassive(victim, PowerIds.BONUS_MANA_SHIELD);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusNemesisBoostsDamageToLastKiller(GameTestHelper context) {
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer attacker = setupBonusPlayer(context, pos);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(target);
        teleport(target, context.getLevel(), pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);
        claimBonusPassive(attacker, PowerIds.BONUS_NEMESIS);

        context.runAtTickTime(5L, () -> {
            BonusPassiveRuntime.setLastKiller(attacker, target.getUUID());
            float mult = BonusPassiveRuntime.getAttackDamageMultiplier(attacker, target, 4.0F);
            if (mult <= 1.0F) {
                context.fail("Nemesis should boost damage against the last killer");
                return;
            }
            releaseBonusPassive(attacker, PowerIds.BONUS_NEMESIS);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusOpportunistBoostsDamageVsDistractedMob(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer attacker = setupBonusPlayer(context, pos);
        ServerPlayer ally = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(ally);
        teleport(ally, world, pos.x + 6.0D, pos.y, pos.z, 0.0F, 0.0F);
        claimBonusPassive(attacker, PowerIds.BONUS_OPPORTUNIST);

        Mob zombie = EntityTypes.ZOMBIE.create(world, e -> {}, BlockPos.containing(pos.add(3.0D, 0.0D, 0.0D)), EntitySpawnReason.TRIGGERED, false, false);
        if (zombie == null) {
            context.fail("Failed to spawn zombie");
            return;
        }
        zombie.snapTo(pos.x + 3.0D, pos.y, pos.z, -90.0F, 0.0F);
        zombie.setTarget(ally);
        world.addFreshEntity(zombie);

        context.runAtTickTime(5L, () -> {
            float mult = BonusPassiveRuntime.getAttackDamageMultiplier(attacker, zombie, 4.0F);
            if (mult <= 1.0F) {
                context.fail("Opportunist should boost damage when target is focused elsewhere");
                return;
            }
            releaseBonusPassive(attacker, PowerIds.BONUS_OPPORTUNIST);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusOverflowingVitalityIncreasesMaxHealth(GameTestHelper context) {
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer player = setupBonusPlayer(context, pos);
        double before = player.getAttributeValue(Attributes.MAX_HEALTH);
        claimBonusPassive(player, PowerIds.BONUS_OVERFLOWING_VITALITY);

        context.runAtTickTime(5L, () -> {
            double after = player.getAttributeValue(Attributes.MAX_HEALTH);
            if (after <= before) {
                context.fail("Overflowing Vitality should increase max health");
                return;
            }
            releaseBonusPassive(player, PowerIds.BONUS_OVERFLOWING_VITALITY);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusPredatorSenseGlowsLowHealthEnemies(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer player = setupBonusPlayer(context, pos);
        claimBonusPassive(player, PowerIds.BONUS_PREDATOR_SENSE);

        Mob zombie = EntityTypes.ZOMBIE.create(world, e -> {}, BlockPos.containing(pos.add(3.0D, 0.0D, 0.0D)), EntitySpawnReason.TRIGGERED, false, false);
        if (zombie == null) {
            context.fail("Failed to spawn zombie");
            return;
        }
        zombie.snapTo(pos.x + 3.0D, pos.y, pos.z, 0.0F, 0.0F);
        zombie.setHealth(4.0F);
        world.addFreshEntity(zombie);

        context.runAtTickTime(5L, () -> {
            BonusPassiveRuntime.tickEverySecond(player, world);
            if (zombie.getEffect(MobEffects.GLOWING) == null) {
                context.fail("Predator Sense should apply Glowing to low-health enemies");
                return;
            }
            releaseBonusPassive(player, PowerIds.BONUS_PREDATOR_SENSE);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusRegenerationBoostGrantsRegen(GameTestHelper context) {
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer player = setupBonusPlayer(context, pos);
        claimBonusPassive(player, PowerIds.BONUS_REGENERATION_BOOST);

        context.runAtTickTime(5L, () -> {
            if (player.getEffect(MobEffects.REGENERATION) == null) {
                context.fail("Regeneration Boost should grant Regeneration");
                return;
            }
            releaseBonusPassive(player, PowerIds.BONUS_REGENERATION_BOOST);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusSecondWindPreventsLethalDamage(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer victim = setupBonusPlayer(context, pos);
        ServerPlayer attacker = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(attacker);
        teleport(attacker, world, pos.x + 2.0D, pos.y, pos.z, 180.0F, 0.0F);
        victim.setHealth(2.0F);
        claimBonusPassive(victim, PowerIds.BONUS_SECOND_WIND);

        context.runAtTickTime(5L, () -> {
            victim.hurtServer(world, attacker.damageSources().playerAttack(attacker), 6.0F);
            if (victim.getHealth() <= 0.0F) {
                context.fail("Second Wind should prevent lethal damage once");
                return;
            }
            releaseBonusPassive(victim, PowerIds.BONUS_SECOND_WIND);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusSpectralFormCanPhaseThroughDamage(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer player = setupBonusPlayer(context, pos);
        claimBonusPassive(player, PowerIds.BONUS_SPECTRAL_FORM);

        context.runAtTickTime(5L, () -> {
            player.getRandom().setSeed(123L);
            boolean phased = false;
            for (int i = 0; i < 200; i++) {
                float scaled = BonusPassiveRuntime.getDefenseDamageMultiplier(player, 4.0F, world, null);
                if (scaled <= 0.0F) {
                    phased = true;
                    break;
                }
            }
            if (!phased) {
                context.fail("Spectral Form should sometimes phase through attacks");
                return;
            }
            releaseBonusPassive(player, PowerIds.BONUS_SPECTRAL_FORM);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusSteelResolveKnockbackImmunityFlag(GameTestHelper context) {
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer player = setupBonusPlayer(context, pos);
        claimBonusPassive(player, PowerIds.BONUS_STEEL_RESOLVE);

        context.runAtTickTime(5L, () -> {
            if (!BonusPassiveRuntime.isKnockbackImmune(player)) {
                context.fail("Steel Resolve should mark player as knockback immune");
                return;
            }
            releaseBonusPassive(player, PowerIds.BONUS_STEEL_RESOLVE);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusStoneSkinReducesDamage(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer victim = setupBonusPlayer(context, pos);
        ServerPlayer attacker = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(attacker);
        teleport(attacker, world, pos.x + 2.0D, pos.y, pos.z, 180.0F, 0.0F);
        claimBonusPassive(victim, PowerIds.BONUS_STONE_SKIN);

        context.runAtTickTime(5L, () -> {
            float before = victim.getHealth();
            victim.hurtServer(world, attacker.damageSources().playerAttack(attacker), 4.0F);
            float taken = before - victim.getHealth();
            if (taken >= 4.0F) {
                context.fail("Stone Skin should reduce incoming damage");
                return;
            }
            releaseBonusPassive(victim, PowerIds.BONUS_STONE_SKIN);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusThickSkinReducesDamage(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer victim = setupBonusPlayer(context, pos);
        ServerPlayer attacker = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(attacker);
        teleport(attacker, world, pos.x + 2.0D, pos.y, pos.z, 180.0F, 0.0F);
        claimBonusPassive(victim, PowerIds.BONUS_THICK_SKIN);

        context.runAtTickTime(5L, () -> {
            if (!GemPowers.isPassiveActive(victim, PowerIds.BONUS_THICK_SKIN)) {
                context.fail("Thick Skin passive was not active after claiming");
                return;
            }
            net.minecraft.world.entity.projectile.arrow.Arrow arrow = EntityTypes.ARROW.create(world, EntitySpawnReason.TRIGGERED);
            if (arrow == null) {
                context.fail("Failed to create arrow");
                return;
            }
            arrow.setOwner(attacker);
            arrow.snapTo(attacker.getX(), attacker.getEyeY(), attacker.getZ(), attacker.getYRot(), attacker.getXRot());
            world.addFreshEntity(arrow);
            float before = victim.getHealth();
            victim.hurtServer(world, world.damageSources().arrow(arrow, attacker), 4.0F);
            float taken = before - victim.getHealth();
            float reduction = GemsBalance.v().bonusPool().thickSkinProjectileReductionPercent() / 100.0F;
            float max = 4.0F * (1.0F - reduction) + 0.1F;
            if (taken > max) {
                context.fail("Thick Skin should reduce projectile damage");
                return;
            }
            releaseBonusPassive(victim, PowerIds.BONUS_THICK_SKIN);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusThornsAuraReflectsDamage(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer victim = setupBonusPlayer(context, pos);
        ServerPlayer attacker = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(attacker);
        teleport(attacker, world, pos.x + 2.0D, pos.y, pos.z, 180.0F, 0.0F);
        claimBonusPassive(victim, PowerIds.BONUS_THORNS_AURA);

        context.runAtTickTime(5L, () -> {
            float attackerBefore = attacker.getHealth();
            victim.hurtServer(world, attacker.damageSources().playerAttack(attacker), 4.0F);
            if (attacker.getHealth() >= attackerBefore) {
                context.fail("Thorns Aura should reflect damage to attackers");
                return;
            }
            releaseBonusPassive(victim, PowerIds.BONUS_THORNS_AURA);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusWarCryBuffsAlliesOnHit(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer killer = setupBonusPlayer(context, pos);
        ServerPlayer ally = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(ally);
        teleport(ally, world, pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);
        GemTrust.trust(killer, ally.getUUID());
        claimBonusPassive(killer, PowerIds.BONUS_WAR_CRY);

        context.runAtTickTime(5L, () -> {
            BonusPassiveRuntime.onKill(killer, ally);
            if (ally.getEffect(MobEffects.STRENGTH) == null) {
                context.fail("War Cry should grant Strength to nearby allies");
                return;
            }
            releaseBonusPassive(killer, PowerIds.BONUS_WAR_CRY);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusVengeanceBoostsNextAttack(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer attacker = setupBonusPlayer(context, pos);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer hitter = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(target);
        GemsGameTestUtil.forceSurvival(hitter);
        teleport(target, world, pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);
        teleport(hitter, world, pos.x + 2.0D, pos.y, pos.z + 2.0D, 180.0F, 0.0F);
        claimBonusPassive(attacker, PowerIds.BONUS_VENGEANCE);

        context.runAtTickTime(5L, () -> {
            attacker.hurtServer(world, hitter.damageSources().playerAttack(hitter), 2.0F);
            float mult = BonusPassiveRuntime.getAttackDamageMultiplier(attacker, target, 4.0F);
            if (mult <= 1.0F) {
                context.fail("Vengeance should boost the next attack after taking damage");
                return;
            }
            releaseBonusPassive(attacker, PowerIds.BONUS_VENGEANCE);
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusHuntersInstinctBoostsDamageToFleeingTargets(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer attacker = setupBonusPlayer(context, pos);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(target);
        teleport(target, world, pos.x + 2.0D, pos.y, pos.z, -90.0F, 0.0F);
        claimBonusPassives(attacker, PowerIds.BONUS_CRITICAL_STRIKE, PowerIds.BONUS_HUNTERS_INSTINCT);

        GemsGameTestUtil.assertEventually(
                context,
                20L,
                80L,
                5L,
                () -> {
                    teleport(target, world, pos.x + 2.0D, pos.y, pos.z, -90.0F, 0.0F);
                    target.setSprinting(true);
                    target.setDeltaMovement(1.0D, 0.0D, 0.0D);

                    float chance = BonusPassiveRuntime.getCriticalStrikeChance(attacker, target);
                    float baseline = GemsBalance.v().bonusPool().criticalStrikeChanceBonus / 100.0f;
                    float expected = baseline + (GemsBalance.v().bonusPool().huntersInstinctCritBoostPercent / 100.0f);
                    return chance >= expected - 0.001f;
                },
                "Hunter's Instinct should increase crit chance against fleeing targets"
        );
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bonusCounterStrikeBoostsNextAttack(GameTestHelper context) {
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer attacker = setupBonusPlayer(context, pos);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(target);
        teleport(target, context.getLevel(), pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);
        claimBonusPassive(attacker, PowerIds.BONUS_COUNTER_STRIKE);

        context.runAtTickTime(5L, () -> {
            BonusPassiveRuntime.triggerCounterStrike(attacker);
            float mult = BonusPassiveRuntime.getAttackDamageMultiplier(attacker, target, 4.0F);
            if (mult <= 1.0F) {
                context.fail("Counter Strike should boost the next attack after blocking");
                return;
            }
            releaseBonusPassive(attacker, PowerIds.BONUS_COUNTER_STRIKE);
            context.succeed();
        });
    }
}
