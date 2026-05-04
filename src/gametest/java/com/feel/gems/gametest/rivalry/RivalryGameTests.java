package com.feel.gems.gametest.rivalry;

import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.rivalry.RivalryManager;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

public final class RivalryGameTests {
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void assignsTargetAndAppliesDamageBonus(GameTestHelper context) {
        ServerPlayer p1 = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer p2 = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(p1);
        GemsGameTestUtil.forceSurvival(p2);

        RivalryManager.assignTarget(p1);
        if (!RivalryManager.isRivalryTarget(p1, p2)) {
            context.fail("Expected p2 to be assigned as p1 rivalry target");
            return;
        }

        float base = 4.0f;
        float expected = base * RivalryManager.getDamageMultiplier();
        float actual = RivalryManager.applyRivalryBonus(p1, p2, base);
        if (Math.abs(actual - expected) > 0.0001f) {
            context.fail("Rivalry bonus damage mismatch: expected " + expected + " got " + actual);
            return;
        }

        GemsGameTestUtil.complete(context);
    }
}
