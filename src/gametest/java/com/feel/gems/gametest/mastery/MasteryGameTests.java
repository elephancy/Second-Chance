package com.feel.gems.gametest.mastery;

import com.feel.gems.core.GemId;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.mastery.GemMastery;
import com.feel.gems.mastery.MasteryReward;
import com.feel.gems.mastery.MasteryRewards;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

public final class MasteryGameTests {
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void masteryUsageUnlocksRewards(GameTestHelper context) {
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);

        GemId gem = GemId.FIRE;
        if (GemMastery.getUsage(player, gem) != 0) {
            context.fail("Expected initial mastery usage to be 0");
            return;
        }

        List<MasteryReward> auras = MasteryRewards.getUnlockedAuras(gem, 25);
        if (auras.isEmpty()) {
            context.fail("Expected at least one aura reward at 25 uses");
            return;
        }
        String auraId = auras.get(0).id();

        GemMastery.setSelectedAura(player, auraId);
        if (GemMastery.getSelectedAuraReward(player) != null) {
            context.fail("Aura reward should not be unlocked at 0 usage");
            return;
        }

        for (int i = 0; i < 25; i++) {
            GemMastery.incrementUsage(player, gem);
        }

        if (GemMastery.getUsage(player, gem) < 25) {
            context.fail("Mastery usage did not increment correctly");
            return;
        }

        GemMastery.setSelectedAura(player, auraId);
        if (GemMastery.getSelectedAuraReward(player) == null) {
            context.fail("Aura reward should be unlocked after reaching threshold");
            return;
        }

        GemsGameTestUtil.complete(context);
    }
}
