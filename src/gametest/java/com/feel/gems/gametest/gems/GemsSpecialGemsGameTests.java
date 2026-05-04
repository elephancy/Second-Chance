package com.feel.gems.gametest.gems;

import com.feel.gems.core.GemDefinition;
import com.feel.gems.core.GemEnergyState;
import com.feel.gems.core.GemId;
import com.feel.gems.core.GemRegistry;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.power.gem.beacon.BeaconSupportRuntime;
import com.feel.gems.power.gem.chaos.ChaosSlotRuntime;
import com.feel.gems.power.gem.terror.TerrorDreadAuraPassive;
import com.feel.gems.power.registry.PowerIds;
import com.feel.gems.power.runtime.AbilityRuntime;
import com.feel.gems.power.runtime.GemPowers;
import com.feel.gems.state.GemPlayerState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;

/**
 * Game tests for special gems: Void, Chaos, and Prism.
 */
public final class GemsSpecialGemsGameTests {

    private static void teleport(ServerPlayer player, ServerLevel world, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(world, x, y, z, EnumSet.noneOf(Relative.class), yaw, pitch, false);
    }

    // ==================== Void Gem Tests ====================

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void voidGemRegisteredCorrectly(GameTestHelper context) {
        context.runAtTickTime(5L, () -> {
            GemDefinition voidDef = GemRegistry.definition(GemId.VOID);
            if (voidDef == null) {
                context.fail("Void gem definition should exist");
                return;
            }

            // Void gem should have the immunity passive
            boolean hasImmunity = voidDef.passives().contains(PowerIds.VOID_IMMUNITY);
            if (!hasImmunity) {
                context.fail("Void gem should have VOID_IMMUNITY passive");
            }

            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void voidGemCanBeSetAsActive(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        context.runAtTickTime(10L, () -> {
            GemPlayerState.initIfNeeded(player);
            GemPlayerState.setActiveGem(player, GemId.VOID);
            GemPlayerState.setEnergy(player, 5);
            GemPowers.sync(player);

            GemId active = GemPlayerState.getActiveGem(player);
            if (active != GemId.VOID) {
                context.fail("Active gem should be VOID, got " + active);
            }

            int energy = GemPlayerState.getEnergy(player);
            if (energy != 5) {
                context.fail("Energy should be 5, got " + energy);
            }

            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 120)
    public void voidImmunityBlocksBeaconRallyEffects(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer beacon = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer voidPlayer = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        beacon.setGameMode(GameType.SURVIVAL);
        voidPlayer.setGameMode(GameType.SURVIVAL);

        Vec3 beaconPos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        Vec3 voidPos = context.absoluteVec(new Vec3(1.5D, 2.0D, 0.5D));
        teleport(beacon, world, beaconPos.x, beaconPos.y, beaconPos.z, 0.0F, 0.0F);
        teleport(voidPlayer, world, voidPos.x, voidPos.y, voidPos.z, 0.0F, 0.0F);

        context.runAtTickTime(5L, () -> {
            GemPlayerState.initIfNeeded(beacon);
            GemPlayerState.setActiveGem(beacon, GemId.BEACON);
            GemPlayerState.setEnergy(beacon, 5);
            GemPowers.sync(beacon);

            GemPlayerState.initIfNeeded(voidPlayer);
            GemPlayerState.setActiveGem(voidPlayer, GemId.VOID);
            GemPlayerState.setEnergy(voidPlayer, 5);
            GemPowers.sync(voidPlayer);

            BeaconSupportRuntime.applyRally(beacon);
        });

        context.runAtTickTime(20L, () -> {
            if (voidPlayer.hasEffect(MobEffects.WEAKNESS) || voidPlayer.hasEffect(MobEffects.ABSORPTION)) {
                context.fail("Void player should not be affected by Beacon Rally status effects");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 120)
    public void voidImmunityBlocksHeatHazeZone(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer fire = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer voidPlayer = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        fire.setGameMode(GameType.SURVIVAL);
        voidPlayer.setGameMode(GameType.SURVIVAL);

        Vec3 firePos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        Vec3 voidPos = context.absoluteVec(new Vec3(1.5D, 2.0D, 0.5D));
        teleport(fire, world, firePos.x, firePos.y, firePos.z, 0.0F, 0.0F);
        teleport(voidPlayer, world, voidPos.x, voidPos.y, voidPos.z, 0.0F, 0.0F);

        context.runAtTickTime(5L, () -> {
            GemPlayerState.initIfNeeded(fire);
            GemPlayerState.setActiveGem(fire, GemId.FIRE);
            GemPlayerState.setEnergy(fire, 5);
            GemPowers.sync(fire);

            GemPlayerState.initIfNeeded(voidPlayer);
            GemPlayerState.setActiveGem(voidPlayer, GemId.VOID);
            GemPlayerState.setEnergy(voidPlayer, 5);
            GemPowers.sync(voidPlayer);

            AbilityRuntime.startHeatHazeZone(fire, 200);
            AbilityRuntime.tickEverySecond(fire);
        });

        context.runAtTickTime(20L, () -> {
            if (voidPlayer.hasEffect(MobEffects.MINING_FATIGUE)
                    || voidPlayer.hasEffect(MobEffects.WEAKNESS)
                    || voidPlayer.hasEffect(MobEffects.FIRE_RESISTANCE)) {
                context.fail("Void player should not be affected by Heat Haze Zone status effects");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 120)
    public void voidImmunityBlocksTerrorDreadAura(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer terror = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer voidPlayer = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        terror.setGameMode(GameType.SURVIVAL);
        voidPlayer.setGameMode(GameType.SURVIVAL);

        Vec3 terrorPos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        Vec3 voidPos = context.absoluteVec(new Vec3(1.5D, 2.0D, 0.5D));
        teleport(terror, world, terrorPos.x, terrorPos.y, terrorPos.z, 0.0F, 0.0F);
        teleport(voidPlayer, world, voidPos.x, voidPos.y, voidPos.z, 0.0F, 0.0F);

        context.runAtTickTime(5L, () -> {
            GemPlayerState.initIfNeeded(voidPlayer);
            GemPlayerState.setActiveGem(voidPlayer, GemId.VOID);
            GemPlayerState.setEnergy(voidPlayer, 5);
            GemPowers.sync(voidPlayer);

            GemPlayerState.initIfNeeded(terror);
            GemPlayerState.setActiveGem(terror, GemId.TERROR);
            GemPlayerState.setEnergy(terror, 5);
            GemPowers.sync(terror);

            new TerrorDreadAuraPassive().maintain(terror);
        });

        context.runAtTickTime(20L, () -> {
            if (voidPlayer.hasEffect(MobEffects.DARKNESS)) {
                context.fail("Void player should not be affected by Terror Dread Aura");
                return;
            }
            context.succeed();
        });
    }

    // ==================== Chaos Gem Tests ====================

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void chaosGemRegisteredCorrectly(GameTestHelper context) {
        context.runAtTickTime(5L, () -> {
            GemDefinition chaosDef = GemRegistry.definition(GemId.CHAOS);
            if (chaosDef == null) {
                context.fail("Chaos gem definition should exist");
                return;
            }

            // Chaos gem should have the random rotation passive
            boolean hasRotation = chaosDef.passives().contains(PowerIds.CHAOS_RANDOM_ROTATION);
            if (!hasRotation) {
                context.fail("Chaos gem should have CHAOS_RANDOM_ROTATION passive");
            }

            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void chaosGemCanBeSetAsActive(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        context.runAtTickTime(10L, () -> {
            GemPlayerState.initIfNeeded(player);
            GemPlayerState.setActiveGem(player, GemId.CHAOS);
            GemPlayerState.setEnergy(player, 8);
            GemPowers.sync(player);

            GemId active = GemPlayerState.getActiveGem(player);
            if (active != GemId.CHAOS) {
                context.fail("Active gem should be CHAOS, got " + active);
            }

            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void chaosSlotsClearWhenEnergyReachesZero(GameTestHelper context) {
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        context.runAtTickTime(10L, () -> {
            GemPlayerState.initIfNeeded(player);
            GemPlayerState.setActiveGem(player, GemId.CHAOS);
            GemPlayerState.setEnergy(player, 5);
            if (!ChaosSlotRuntime.activateSlot(player, 0)
                    || ChaosSlotRuntime.getState(player.getUUID()).getSlot(0).abilityId() == null) {
                context.fail("Chaos slot did not roll");
                return;
            }
            GemPlayerState.setEnergy(player, 0);
        });
        context.runAtTickTime(15L, () -> {
            if (ChaosSlotRuntime.getState(player.getUUID()).getSlot(0).abilityId() != null) {
                context.fail("Chaos slot stayed active at zero energy");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void chaosSlotAbilitiesStayLockedAtOneEnergy(GameTestHelper context) {
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        context.runAtTickTime(10L, () -> {
            GemPlayerState.initIfNeeded(player);
            GemPlayerState.setActiveGem(player, GemId.CHAOS);
            GemPlayerState.setEnergy(player, 1);
            if (!ChaosSlotRuntime.activateSlot(player, 0)) {
                context.fail("Chaos passive could not roll a slot at energy 1");
                return;
            }
            ChaosSlotRuntime.SlotState slot = ChaosSlotRuntime.getState(player.getUUID()).getSlot(0);
            if (slot.abilityId() == null || ChaosSlotRuntime.activateSlot(player, 0)
                    || ChaosSlotRuntime.getState(player.getUUID()).getSlot(0).lastAbilityUseTick() != 0) {
                context.fail("Chaos used an ability before the energy 2 unlock");
                return;
            }
            ChaosSlotRuntime.clearState(player);
            context.succeed();
        });
    }

    // ==================== Prism Gem Tests ====================

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void prismGemRegisteredCorrectly(GameTestHelper context) {
        context.runAtTickTime(5L, () -> {
            GemDefinition prismDef = GemRegistry.definition(GemId.PRISM);
            if (prismDef == null) {
                context.fail("Prism gem definition should exist");
                return;
            }

            // Prism gem is special - it gets abilities from selections, not fixed list
            // It should exist but may have different structure
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void prismGemCanBeSetAsActive(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        context.runAtTickTime(10L, () -> {
            GemPlayerState.initIfNeeded(player);
            GemPlayerState.setActiveGem(player, GemId.PRISM);
            GemPlayerState.setEnergy(player, 10);
            GemPowers.sync(player);

            GemId active = GemPlayerState.getActiveGem(player);
            if (active != GemId.PRISM) {
                context.fail("Active gem should be PRISM, got " + active);
            }

            context.succeed();
        });
    }

    // ==================== All Gems Registry Tests ====================

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void allGemsHaveDefinitions(GameTestHelper context) {
        context.runAtTickTime(5L, () -> {
            for (GemId gemId : GemId.values()) {
                GemDefinition def = GemRegistry.definition(gemId);
                if (def == null) {
                    context.fail("Gem " + gemId + " should have a definition");
                    return;
                }
                if (def.id() != gemId) {
                    context.fail("Definition id mismatch for " + gemId);
                    return;
                }
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void energyUnlockProgressionWorksForAllGems(GameTestHelper context) {
        context.runAtTickTime(5L, () -> {
            for (GemId gemId : GemId.values()) {
                GemDefinition def = GemRegistry.definition(gemId);
                if (def == null) continue;

                // At energy 0-1: no abilities
                var noAbilities = def.availableAbilities(new GemEnergyState(0));
                if (!noAbilities.isEmpty()) {
                    context.fail(gemId + " should have no abilities at energy 0");
                    return;
                }

                // At energy 1: passives unlock
                var passivesAtOne = def.availablePassives(new GemEnergyState(1));
                if (passivesAtOne.size() != def.passives().size() && !def.passives().isEmpty()) {
                    context.fail(gemId + " should unlock all passives at energy 1");
                    return;
                }

                // At energy 5+: all abilities unlock
                var allAtFive = def.availableAbilities(new GemEnergyState(5));
                if (allAtFive.size() != def.abilities().size()) {
                    context.fail(gemId + " should have all abilities at energy 5");
                    return;
                }
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void gemSwitchingWorks(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        context.runAtTickTime(10L, () -> {
            GemPlayerState.initIfNeeded(player);
            
            // Switch through several gems
            GemId[] toTest = {GemId.FIRE, GemId.AIR, GemId.VOID, GemId.CHAOS, GemId.PRISM, GemId.ASTRA};
            for (GemId gem : toTest) {
                GemPlayerState.setActiveGem(player, gem);
                GemPowers.sync(player);
                
                GemId active = GemPlayerState.getActiveGem(player);
                if (active != gem) {
                    context.fail("Failed to switch to " + gem + ", got " + active);
                    return;
                }
            }
            
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void ownedGemsCanIncludeSpecialGems(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        context.runAtTickTime(10L, () -> {
            GemPlayerState.initIfNeeded(player);
            
            // Set owned gems to include special ones
            EnumSet<GemId> owned = EnumSet.of(GemId.FIRE, GemId.VOID, GemId.CHAOS, GemId.PRISM);
            GemPlayerState.setOwnedGemsExact(player, owned);
            
            var retrieved = GemPlayerState.getOwnedGems(player);
            if (!retrieved.contains(GemId.VOID)) {
                context.fail("Owned gems should include VOID");
            }
            if (!retrieved.contains(GemId.CHAOS)) {
                context.fail("Owned gems should include CHAOS");
            }
            if (!retrieved.contains(GemId.PRISM)) {
                context.fail("Owned gems should include PRISM");
            }
            
            context.succeed();
        });
    }
}
