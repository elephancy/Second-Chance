package com.feel.gems.gametest.coverage;

import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.power.api.GemPassive;
import com.feel.gems.power.registry.ModPassives;
import com.feel.gems.power.runtime.GemPowers;
import com.feel.gems.state.GemPlayerState;
import java.util.EnumSet;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

public final class GemsPassiveCoverageGameTests {
    private static void teleport(ServerPlayer player, ServerLevel world, double x, double y, double z) {
        player.teleportTo(world, x, y, z, EnumSet.noneOf(Relative.class), 0.0F, 0.0F, false);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void allPassivesApplyAndRemoveWithoutCrashing(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setEnergy(player, GemPlayerState.MAX_ENERGY);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            for (Map.Entry<net.minecraft.resources.Identifier, GemPassive> entry : ModPassives.all().entrySet()) {
                try {
                    GemPassive passive = entry.getValue();
                    passive.apply(player);
                    passive.remove(player);
                } catch (Throwable t) {
                    context.fail("Passive crashed: " + entry.getKey() + " -> " + t.getMessage());
                    return;
                }
            }
            context.succeed();
        });
    }
}
