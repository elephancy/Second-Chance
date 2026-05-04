package com.feel.gems.gametest.coverage;

import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.power.api.GemAbility;
import com.feel.gems.power.registry.ModAbilities;
import com.feel.gems.power.runtime.GemPowers;
import com.feel.gems.state.GemPlayerState;
import java.util.EnumSet;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class GemsAbilityCoverageGameTests {
    private static void teleport(ServerPlayer player, ServerLevel world, double x, double y, double z) {
        player.teleportTo(world, x, y, z, EnumSet.noneOf(Relative.class), 0.0F, 0.0F, false);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void allAbilitiesActivateWithoutCrashing(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setEnergy(player, GemPlayerState.MAX_ENERGY);
        GemPowers.sync(player);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));

        context.runAtTickTime(5L, () -> {
            for (Map.Entry<net.minecraft.resources.Identifier, GemAbility> entry : ModAbilities.all().entrySet()) {
                try {
                    entry.getValue().activate(player);
                } catch (Throwable t) {
                    context.fail("Ability crashed: " + entry.getKey() + " -> " + t.getMessage());
                    return;
                }
            }
            context.succeed();
        });
    }
}
