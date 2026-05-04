package com.feel.gems.gametest.leaderboard;

import com.feel.gems.core.GemId;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.mastery.LeaderboardTracker;
import com.feel.gems.stats.GemsStats;
import com.feel.gems.state.GemPlayerState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;

public final class GemsLeaderboardGameTests {
    private static void teleport(ServerPlayer player, ServerLevel world, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(world, x, y, z, EnumSet.noneOf(Relative.class), yaw, pitch, false);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void leaderboardPerGemKills(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        GemsGameTestUtil.placeStoneFloor(context, 6);
        ServerPlayer killer = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer victim = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(killer);
        GemsGameTestUtil.forceSurvival(victim);

        Vec3 base = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(killer, world, base.x, base.y, base.z, 0.0F, 0.0F);
        teleport(victim, world, base.x + 2.0D, base.y, base.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(killer);
        GemPlayerState.initIfNeeded(victim);
        GemPlayerState.setActiveGem(killer, GemId.ASTRA);
        GemPlayerState.setActiveGem(victim, GemId.FIRE);

        GemsStats.recordPlayerKill(killer, victim, false, false, false);
        LeaderboardTracker.updateLeaderboards(world.getServer());

        LeaderboardTracker.LeaderboardEntry leader = LeaderboardTracker.getLeader(LeaderboardTracker.LeaderboardCategory.MOST_KILLS_ASTRA);
        if (leader == null || !leader.playerId().equals(killer.getUUID())) {
            context.fail("Per-gem leaderboard did not select Astra leader");
            return;
        }
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void leaderboardLifetimeStats(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        GemsGameTestUtil.placeStoneFloor(context, 6);
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer other = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(other);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.initIfNeeded(other);

        Identifier testAbility = Identifier.fromNamespaceAndPath("gems", "test_cast");
        GemsStats.recordAbilityUse(player, testAbility);
        GemsStats.recordAbilityUse(player, testAbility);
        GemsStats.recordSynergyTrigger(player, Identifier.fromNamespaceAndPath("gems", "test_synergy"));
        GemsStats.recordDamageDealt(player, 12.0F);

        LeaderboardTracker.updateLeaderboards(world.getServer());

        LeaderboardTracker.LeaderboardEntry casts = LeaderboardTracker.getLeader(LeaderboardTracker.LeaderboardCategory.MOST_ABILITY_CASTS);
        LeaderboardTracker.LeaderboardEntry synergies = LeaderboardTracker.getLeader(LeaderboardTracker.LeaderboardCategory.MOST_SYNERGY_TRIGGERS);
        LeaderboardTracker.LeaderboardEntry damage = LeaderboardTracker.getLeader(LeaderboardTracker.LeaderboardCategory.MOST_DAMAGE_DEALT);

        if (casts == null || !casts.playerId().equals(player.getUUID())) {
            context.fail("Ability cast leaderboard did not select player");
            return;
        }
        if (synergies == null || !synergies.playerId().equals(player.getUUID())) {
            context.fail("Synergy trigger leaderboard did not select player");
            return;
        }
        if (damage == null || !damage.playerId().equals(player.getUUID())) {
            context.fail("Damage dealt leaderboard did not select player");
            return;
        }
        context.succeed();
    }
}
