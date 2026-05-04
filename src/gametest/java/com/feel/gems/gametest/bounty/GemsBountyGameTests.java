package com.feel.gems.gametest.bounty;

import com.feel.gems.bounty.BountyBoard;
import com.feel.gems.bounty.BountyState;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.item.ModItems;
import com.feel.gems.state.GemPlayerState;
import java.util.EnumSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class GemsBountyGameTests {
    private static void teleport(ServerPlayer player, ServerLevel world, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(world, x, y, z, EnumSet.noneOf(Relative.class), yaw, pitch, false);
    }

    private static int countHearts(ServerPlayer player) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ModItems.HEART)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bountyPlaceAndClaimRewards(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        GemsGameTestUtil.placeStoneFloor(context, 6);

        ServerPlayer placer = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer killer = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(placer);
        GemsGameTestUtil.forceSurvival(target);
        GemsGameTestUtil.forceSurvival(killer);

        Vec3 base = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(placer, world, base.x, base.y, base.z, 0.0F, 0.0F);
        teleport(target, world, base.x + 2.0D, base.y, base.z, 0.0F, 0.0F);
        teleport(killer, world, base.x - 2.0D, base.y, base.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(placer);
        GemPlayerState.setMaxHearts(placer, 8);
        GemPlayerState.applyMaxHearts(placer);
        GemPlayerState.setEnergy(placer, 4);
        GemPlayerState.initIfNeeded(killer);
        GemPlayerState.setEnergy(killer, 0);

        BountyBoard.PlaceResult result = BountyBoard.placeBounty(placer, target, 3, 2);
        if (result != BountyBoard.PlaceResult.SUCCESS) {
            context.fail("Failed to place bounty");
            return;
        }
        if (GemPlayerState.getMaxHearts(placer) != 5 || GemPlayerState.getEnergy(placer) != 2) {
            context.fail("Bounty cost did not deduct correctly");
            return;
        }

        context.runAtTickTime(5L, () -> BountyBoard.handleKill(target, killer, 0, 0));

        GemsGameTestUtil.assertEventually(
                context,
                6L,
                40L,
                2L,
                () -> countHearts(killer) >= 3
                        && GemPlayerState.getEnergy(killer) == 2
                        && BountyState.get(world.getServer()).getTarget(target.getUUID()) == null,
                "Bounty rewards were not delivered or cleared"
        );
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bountyTargetKillsPlacerPaysOut(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        GemsGameTestUtil.placeStoneFloor(context, 6);

        ServerPlayer placer = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(placer);
        GemsGameTestUtil.forceSurvival(target);

        Vec3 base = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(placer, world, base.x, base.y, base.z, 0.0F, 0.0F);
        teleport(target, world, base.x + 2.0D, base.y, base.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(placer);
        GemPlayerState.setMaxHearts(placer, 7);
        GemPlayerState.applyMaxHearts(placer);
        GemPlayerState.setEnergy(placer, 3);
        GemPlayerState.initIfNeeded(target);
        GemPlayerState.setEnergy(target, 0);

        BountyBoard.PlaceResult result = BountyBoard.placeBounty(placer, target, 2, 1);
        if (result != BountyBoard.PlaceResult.SUCCESS) {
            context.fail("Failed to place bounty");
            return;
        }

        context.runAtTickTime(5L, () -> BountyBoard.handleKill(placer, target, 0, 0));

        GemsGameTestUtil.assertEventually(
                context,
                6L,
                40L,
                2L,
                () -> countHearts(target) >= 2
                        && GemPlayerState.getEnergy(target) == 1
                        && BountyState.get(world.getServer()).getTarget(target.getUUID()) == null,
                "Target did not receive placer bounty"
        );
    }
}
