package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.exploration.ExplorationBlocks;
import com.feel.gems.adventure.exploration.ExplorationDeliveryAccount;
import com.feel.gems.adventure.exploration.ExplorationProgress;
import com.feel.gems.adventure.exploration.ExplorationState;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Marker callbacks must not mint a discovery for an ineligible puzzle solver. */
public final class ExplorationCompletionGameTests {
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void spectatorAndDeadSolverKeepCipherProgressWithoutRewards(GameTestHelper context) {
        var level = context.getLevel();
        BlockPos plaque = context.absolutePos(new BlockPos(1, 2, 1));
        BlockPos seal = context.absolutePos(new BlockPos(3, 2, 1));
        level.setBlock(plaque, ExplorationBlocks.CIPHER_PLAQUE.defaultBlockState(), 3);
        level.setBlock(seal, ExplorationBlocks.CIPHER_SEAL.defaultBlockState(), 3);
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);
        var state = ExplorationState.get(level.getServer());
        var id = ExplorationProgress.activityId("cipher", plaque.getX(), plaque.getZ());
        state.startCipher(player.getUUID(), plaque.asLong());
        state.pressCipher(player.getUUID(), 0);
        state.pressCipher(player.getUUID(), 2);
        state.pressCipher(player.getUUID(), 1);

        player.setGameMode(GameType.SPECTATOR);
        useSeal(level, player, seal);
        assertUnchanged(context, state, player, id, plaque);

        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(0F);
        useSeal(level, player, seal);
        assertUnchanged(context, state, player, id, plaque);

        player.setHealth(20F);
        useSeal(level, player, seal);
        if (!state.model().earned(player.getUUID(), id)
                || !ExplorationDeliveryAccount.of(player).has(id)
                || state.model().journey(player.getUUID()).cipherPlaque()
                    != ExplorationProgress.Journey.NONE) {
            context.fail("Living solver did not receive a completion and clear progress");
            return;
        }
        context.succeed();
    }

    private static void useSeal(net.minecraft.server.level.ServerLevel level,
                                ServerPlayer player, BlockPos seal) {
        UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(seal), Direction.UP, seal, false));
    }

    private static void assertUnchanged(GameTestHelper context, ExplorationState state,
                                        ServerPlayer player, String id, BlockPos plaque) {
        var progress = state.model();
        var journey = progress.journey(player.getUUID());
        if (progress.discovered(id) || progress.earned(player.getUUID(), id)
                || ExplorationDeliveryAccount.of(player).has(id)
                || journey.cipherPlaque() != plaque.asLong() || journey.cipherStep() != 3) {
            context.fail("Ineligible solver changed cipher discovery, reward, or puzzle progress");
        }
    }
}
