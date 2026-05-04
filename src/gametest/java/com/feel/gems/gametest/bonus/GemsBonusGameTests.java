package com.feel.gems.gametest.bonus;

import com.feel.gems.bonus.BonusClaimsState;
import com.feel.gems.bonus.BonusPoolRegistry;
import com.feel.gems.bonus.PrismSelectionsState;
import com.feel.gems.core.GemId;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.power.registry.PowerIds;
import com.feel.gems.state.GemPlayerState;
import com.feel.gems.trust.GemTrust;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Game tests for the bonus pool system and special gem mechanics.
 */
public final class GemsBonusGameTests {

    private static void teleport(ServerPlayer player, ServerLevel world, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(world, x, y, z, EnumSet.noneOf(Relative.class), yaw, pitch, false);
    }

    // ==================== Bonus Claims Tests ====================

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void bonusClaimsAreServerWideUnique(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        MinecraftServer server = world.getServer();
        if (server == null) {
            context.fail("No server instance");
            return;
        }

        ServerPlayer player1 = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer player2 = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player1.setGameMode(GameType.SURVIVAL);
        player2.setGameMode(GameType.SURVIVAL);

        Vec3 pos1 = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        Vec3 pos2 = context.absoluteVec(new Vec3(2.5D, 2.0D, 0.5D));
        teleport(player1, world, pos1.x, pos1.y, pos1.z, 0.0F, 0.0F);
        teleport(player2, world, pos2.x, pos2.y, pos2.z, 0.0F, 0.0F);

        context.runAtTickTime(10L, () -> {
            BonusClaimsState claimsState = BonusClaimsState.get(server);
            
            // Player 1 claims thunderstrike
            boolean claim1 = claimsState.claimAbility(player1.getUUID(), PowerIds.BONUS_THUNDERSTRIKE);
            if (!claim1) {
                context.fail("Player 1 should be able to claim unclaimed ability");
            }

            // Player 2 tries to claim same ability - should fail
            boolean claim2 = claimsState.claimAbility(player2.getUUID(), PowerIds.BONUS_THUNDERSTRIKE);
            if (claim2) {
                context.fail("Player 2 should NOT be able to claim ability already claimed by player 1");
            }

            // Player 2 can claim a different ability
            boolean claim3 = claimsState.claimAbility(player2.getUUID(), PowerIds.BONUS_FROSTBITE);
            if (!claim3) {
                context.fail("Player 2 should be able to claim a different unclaimed ability");
            }

            // Cleanup
            claimsState.releaseAllClaims(player1.getUUID());
            claimsState.releaseAllClaims(player2.getUUID());

            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void bonusClaimsMaxTwoAbilitiesPerPlayer(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        MinecraftServer server = world.getServer();
        if (server == null) {
            context.fail("No server instance");
            return;
        }

        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        context.runAtTickTime(10L, () -> {
            BonusClaimsState claimsState = BonusClaimsState.get(server);
            UUID playerUuid = player.getUUID();

            // First two claims should succeed
            boolean first = claimsState.claimAbility(playerUuid, PowerIds.BONUS_THUNDERSTRIKE);
            boolean second = claimsState.claimAbility(playerUuid, PowerIds.BONUS_FROSTBITE);
            if (!first || !second) {
                context.fail("First two ability claims should succeed");
            }

            // Third claim should fail (max 2)
            boolean third = claimsState.claimAbility(playerUuid, PowerIds.BONUS_EARTHSHATTER);
            if (third) {
                context.fail("Third ability claim should fail (max 2)");
            }

            // Player should have exactly 2 abilities
            int count = claimsState.getPlayerAbilities(playerUuid).size();
            if (count != 2) {
                context.fail("Expected 2 abilities, got " + count);
            }

            // Cleanup
            claimsState.releaseAllClaims(playerUuid);

            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void bonusClaimsReleaseReallowsOthers(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        MinecraftServer server = world.getServer();
        if (server == null) {
            context.fail("No server instance");
            return;
        }

        ServerPlayer player1 = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer player2 = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player1.setGameMode(GameType.SURVIVAL);
        player2.setGameMode(GameType.SURVIVAL);

        context.runAtTickTime(10L, () -> {
            BonusClaimsState claimsState = BonusClaimsState.get(server);
            
            // Player 1 claims
            claimsState.claimAbility(player1.getUUID(), PowerIds.BONUS_THUNDERSTRIKE);
            
            // Player 2 cannot claim
            boolean beforeRelease = claimsState.claimAbility(player2.getUUID(), PowerIds.BONUS_THUNDERSTRIKE);
            if (beforeRelease) {
                context.fail("Should not claim before release");
            }

            // Player 1 releases
            claimsState.releaseAllClaims(player1.getUUID());

            // Now player 2 can claim
            boolean afterRelease = claimsState.claimAbility(player2.getUUID(), PowerIds.BONUS_THUNDERSTRIKE);
            if (!afterRelease) {
                context.fail("Should be able to claim after other player releases");
            }

            // Cleanup
            claimsState.releaseAllClaims(player2.getUUID());

            context.succeed();
        });
    }

    // ==================== Trust System Tests ====================

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void trustSystemAllowsSelfTrust(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        context.runAtTickTime(10L, () -> {
            // Player should always trust themselves
            boolean trustsSelf = GemTrust.isTrusted(player, player);
            if (!trustsSelf) {
                context.fail("Player should always trust themselves");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void trustSystemManualTrustWorks(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer owner = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer other = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        owner.setGameMode(GameType.SURVIVAL);
        other.setGameMode(GameType.SURVIVAL);

        Vec3 pos1 = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        Vec3 pos2 = context.absoluteVec(new Vec3(2.5D, 2.0D, 0.5D));
        teleport(owner, world, pos1.x, pos1.y, pos1.z, 0.0F, 0.0F);
        teleport(other, world, pos2.x, pos2.y, pos2.z, 0.0F, 0.0F);

        context.runAtTickTime(10L, () -> {
            // Initially other should not be trusted
            boolean initiallyTrusted = GemTrust.isTrusted(owner, other);
            if (initiallyTrusted) {
                context.fail("Other player should not be trusted initially");
            }

            // Add trust
            GemTrust.trust(owner, other.getUUID());

            // Now should be trusted
            boolean afterTrust = GemTrust.isTrusted(owner, other);
            if (!afterTrust) {
                context.fail("Other player should be trusted after adding");
            }

            // Remove trust
            GemTrust.untrust(owner, other.getUUID());

            // Should no longer be trusted
            boolean afterUntrust = GemTrust.isTrusted(owner, other);
            if (afterUntrust) {
                context.fail("Other player should not be trusted after removing");
            }

            context.succeed();
        });
    }

    // ==================== Special Gems Tests ====================

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void voidGemExistsAndHasImmunityPassive(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        context.runAtTickTime(10L, () -> {
            GemPlayerState.initIfNeeded(player);
            GemPlayerState.setActiveGem(player, GemId.VOID);
            GemPlayerState.setEnergy(player, 5);

            GemId active = GemPlayerState.getActiveGem(player);
            if (active != GemId.VOID) {
                context.fail("Active gem should be VOID");
            }

            // Verify VOID_IMMUNITY is in blacklist (special handling)
            if (!BonusPoolRegistry.isBlacklisted(PowerIds.VOID_IMMUNITY)) {
                context.fail("VOID_IMMUNITY should be blacklisted from Prism");
            }

            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void chaosGemExistsAndHasRandomRotation(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        context.runAtTickTime(10L, () -> {
            GemPlayerState.initIfNeeded(player);
            GemPlayerState.setActiveGem(player, GemId.CHAOS);
            GemPlayerState.setEnergy(player, 5);

            GemId active = GemPlayerState.getActiveGem(player);
            if (active != GemId.CHAOS) {
                context.fail("Active gem should be CHAOS");
            }

            // Verify CHAOS_RANDOM_ROTATION is in blacklist
            if (!BonusPoolRegistry.isBlacklisted(PowerIds.CHAOS_RANDOM_ROTATION)) {
                context.fail("CHAOS_RANDOM_ROTATION should be blacklisted from Prism");
            }

            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void prismGemSelectionsWork(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        MinecraftServer server = world.getServer();
        if (server == null) {
            context.fail("No server instance");
            return;
        }

        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        context.runAtTickTime(10L, () -> {
            GemPlayerState.initIfNeeded(player);
            GemPlayerState.setActiveGem(player, GemId.PRISM);
            GemPlayerState.setEnergy(player, 10);

            GemId active = GemPlayerState.getActiveGem(player);
            if (active != GemId.PRISM) {
                context.fail("Active gem should be PRISM");
            }

            // Test prism selections
            PrismSelectionsState prismState = PrismSelectionsState.get(server);
            UUID playerUuid = player.getUUID();
            
            // Can add gem abilities (max 3)
            boolean added1 = prismState.addGemAbility(playerUuid, PowerIds.AIR_DASH);
            if (!added1) {
                context.fail("Should be able to add gem ability to Prism");
            }

            // Cannot add blacklisted
            boolean addedBlacklist = prismState.addGemAbility(playerUuid, PowerIds.VOID_IMMUNITY);
            if (addedBlacklist) {
                context.fail("Should NOT be able to add blacklisted ability");
            }

            // Cleanup
            prismState.clearSelections(playerUuid);

            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void prismSelectionsLimitCorrectly(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        MinecraftServer server = world.getServer();
        if (server == null) {
            context.fail("No server instance");
            return;
        }

        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        context.runAtTickTime(10L, () -> {
            PrismSelectionsState prismState = PrismSelectionsState.get(server);
            UUID playerUuid = player.getUUID();

            // Add 3 gem abilities (max)
            prismState.addGemAbility(playerUuid, PowerIds.AIR_DASH);
            prismState.addGemAbility(playerUuid, PowerIds.FIREBALL);
            prismState.addGemAbility(playerUuid, PowerIds.FLUX_BEAM);

            // 4th should fail
            boolean fourth = prismState.addGemAbility(playerUuid, PowerIds.DOUBLE_JUMP);
            if (fourth) {
                context.fail("Should not allow more than 3 gem abilities");
            }

            // Add 2 bonus abilities (max)
            prismState.addBonusAbility(playerUuid, PowerIds.BONUS_THUNDERSTRIKE);
            prismState.addBonusAbility(playerUuid, PowerIds.BONUS_FROSTBITE);

            // 3rd bonus should fail
            boolean thirdBonus = prismState.addBonusAbility(playerUuid, PowerIds.BONUS_EARTHSHATTER);
            if (thirdBonus) {
                context.fail("Should not allow more than 2 bonus abilities");
            }

            var selection = prismState.getSelection(playerUuid);
            if (selection.totalAbilities() != 5) {
                context.fail("Expected 5 total abilities (3 gem + 2 bonus), got " + selection.totalAbilities());
            }

            // Cleanup
            prismState.clearSelections(playerUuid);

            context.succeed();
        });
    }
}
