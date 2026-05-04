package com.feel.gems.gametest.loadout;

import com.feel.gems.core.GemId;
import com.feel.gems.core.GemRegistry;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.loadout.GemLoadout;
import com.feel.gems.loadout.LoadoutManager;
import com.feel.gems.state.GemPlayerState;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class LoadoutPresetGameTests {
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void saveAndLoadPresetRestoresSettings(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.FIRE);
        GemPlayerState.setEnergy(player, 6);

        List<Identifier> abilities = GemRegistry.definition(GemId.FIRE).abilities();
        List<Identifier> customOrder = new java.util.ArrayList<>(abilities);
        if (customOrder.size() > 1) {
            java.util.Collections.swap(customOrder, 0, 1);
        }
        LoadoutManager.saveAbilityOrder(player, GemId.FIRE, customOrder);
        LoadoutManager.saveHudLayout(player, new GemLoadout.HudLayout(GemLoadout.HudPosition.BOTTOM_RIGHT, false, false, true));
        GemPlayerState.setPassivesEnabled(player, false);

        GemLoadout loadout = LoadoutManager.createFromCurrent(player, "Test");
        int index = LoadoutManager.savePreset(player, loadout);
        if (index != 0) {
            context.fail("Expected first preset index 0 but got " + index);
            return;
        }

        LoadoutManager.saveAbilityOrder(player, GemId.FIRE, abilities);
        LoadoutManager.saveHudLayout(player, GemLoadout.HudLayout.defaults());
        GemPlayerState.setPassivesEnabled(player, true);

        if (!LoadoutManager.loadPreset(player, GemId.FIRE, 0)) {
            context.fail("Failed to load preset");
            return;
        }

        if (GemPlayerState.arePassivesEnabled(player)) {
            context.fail("Expected passives to be disabled after loading preset");
            return;
        }

        if (!LoadoutManager.getAbilityOrder(player, GemId.FIRE).equals(customOrder)) {
            context.fail("Ability order not restored by preset");
            return;
        }

        GemLoadout.HudLayout hud = LoadoutManager.getHudLayout(player);
        if (hud.position() != GemLoadout.HudPosition.BOTTOM_RIGHT || hud.showCooldowns() || hud.showEnergy() || !hud.compactMode()) {
            context.fail("HUD layout not restored by preset");
            return;
        }

        if (LoadoutManager.getActivePresetIndex(player, GemId.FIRE) != 0) {
            context.fail("Active preset index was not set after load");
            return;
        }

        GemsGameTestUtil.complete(context);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void cannotSavePresetBelowEnergy(GameTestHelper context) {
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.ASTRA);
        GemPlayerState.setEnergy(player, 0);

        GemLoadout loadout = LoadoutManager.createFromCurrent(player, "Locked");
        int index = LoadoutManager.savePreset(player, loadout);
        if (index >= 0) {
            context.fail("Preset saved despite insufficient energy");
            return;
        }
        GemsGameTestUtil.complete(context);
    }
}
