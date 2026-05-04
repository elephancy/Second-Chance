package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.DomainBlocks;
import com.feel.gems.casino.CasinoGame;
import com.feel.gems.casino.TournamentState;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** The daily casino follows the visible Overworld clock, including sleep and clock commands. */
public final class CasinoTournamentClockGameTests {
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 80)
    public void sleepJumpOpensNewDayButClockRewindCannotRepeatClaim(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        BlockPos board = context.absolutePos(new BlockPos(2, 2, 1));
        context.setBlock(2, 2, 1, DomainBlocks.TOWNHALL_TOURNAMENT_BOARD);
        TournamentState tournament = TournamentState.get(level.getServer());
        var overworldClock = level.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK)
                .getOrThrow(WorldClocks.OVERWORLD);
        long originalClock = level.getOverworldClockTime();
        long originalGameTime = level.getGameTime();
        long firstDay = TournamentState.worldDay(level.getServer());
        long secondDay = firstDay + 1;
        int startingBread = GemsGameTestUtil.countItem(player, Items.BREAD);
        int startingTorches = GemsGameTestUtil.countItem(player, Items.TORCH);

        try {
            completeFeaturedDay(tournament, firstDay);
            if (!tournament.completed(firstDay)
                    || useBoard(player, level, board) != InteractionResult.SUCCESS_SERVER
                    || GemsGameTestUtil.countItem(player, Items.BREAD) != startingBread + 2
                    || GemsGameTestUtil.countItem(player, Items.TORCH) != startingTorches + 4) {
                context.fail("The first completed tournament day did not deliver one board claim");
                return;
            }

            // Sleep advances the visible clock without advancing server game time.
            level.clockManager().setTotalTicks(overworldClock, secondDay * 24_000L + 1_000L);
            if (level.getGameTime() != originalGameTime
                    || TournamentState.worldDay(level.getServer()) != secondDay
                    || tournament.completed(secondDay)) {
                context.fail("A visible-clock jump did not open a fresh tournament day");
                return;
            }
            if (useBoard(player, level, board) != InteractionResult.SUCCESS_SERVER
                    || GemsGameTestUtil.countItem(player, Items.BREAD) != startingBread + 2
                    || GemsGameTestUtil.countItem(player, Items.TORCH) != startingTorches + 4) {
                context.fail("An unfinished new day gave provisions before three fresh wins");
                return;
            }

            completeFeaturedDay(tournament, secondDay);
            if (!tournament.completed(secondDay)
                    || useBoard(player, level, board) != InteractionResult.SUCCESS_SERVER
                    || GemsGameTestUtil.countItem(player, Items.BREAD) != startingBread + 4
                    || GemsGameTestUtil.countItem(player, Items.TORCH) != startingTorches + 8) {
                context.fail("The new visible day did not award exactly one fresh claim");
                return;
            }

            // Simulate an operator rewinding /time into the previous day.
            level.clockManager().setTotalTicks(overworldClock, firstDay * 24_000L + 1_000L);
            if (TournamentState.worldDay(level.getServer()) != secondDay
                    || useBoard(player, level, board) != InteractionResult.SUCCESS_SERVER
                    || GemsGameTestUtil.countItem(player, Items.BREAD) != startingBread + 4
                    || GemsGameTestUtil.countItem(player, Items.TORCH) != startingTorches + 8) {
                context.fail("Rewinding the clock reopened or duplicated a claimed tournament day");
                return;
            }
            context.succeed();
        } finally {
            level.clockManager().setTotalTicks(overworldClock, originalClock);
        }
    }

    private static void completeFeaturedDay(TournamentState tournament, long day) {
        for (CasinoGame game : CasinoGame.values()) tournament.record(day, game, true);
    }

    private static InteractionResult useBoard(ServerPlayer player, ServerLevel level, BlockPos board) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(board), Direction.UP, board, false);
        return UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit);
    }
}
