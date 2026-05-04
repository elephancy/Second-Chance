package com.feel.gems.gametest.augment;

import com.feel.gems.augment.AugmentDefinition;
import com.feel.gems.augment.AugmentInstance;
import com.feel.gems.augment.AugmentRegistry;
import com.feel.gems.augment.AugmentRarity;
import com.feel.gems.augment.AugmentRuntime;
import com.feel.gems.augment.AugmentTarget;
import com.feel.gems.core.GemId;
import com.feel.gems.core.GemRegistry;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.item.ModItems;
import com.feel.gems.state.GemPlayerState;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

public final class AugmentGameTests {
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void gemAugmentsApplyAndAffectStats(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.FIRE);
        ItemStack gemStack = new ItemStack(ModItems.gemItem(GemId.FIRE));

        for (AugmentDefinition def : AugmentRegistry.all()) {
            if (def.target() != AugmentTarget.GEM) {
                continue;
            }
            AugmentInstance inst = new AugmentInstance(def.id(), AugmentRarity.COMMON, 1.0f);
            if (!AugmentRuntime.applyGemAugment(player, gemStack, inst)) {
                context.fail("Failed to apply gem augment: " + def.id());
                return;
            }
        }

        float cooldownMultiplier = AugmentRuntime.cooldownMultiplier(player, GemId.FIRE);
        if (cooldownMultiplier >= 1.0f) {
            context.fail("Expected cooldown multiplier to be < 1 after augments");
            return;
        }

        List<Identifier> passives = GemRegistry.definition(GemId.FIRE).passives();
        if (!passives.isEmpty()) {
            int bonus = AugmentRuntime.passiveAmplifierBonus(player, passives.get(0));
            if (bonus <= 0) {
                context.fail("Expected passive amplifier bonus from resonance augment");
                return;
            }
        }

        GemsGameTestUtil.complete(context);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void legendaryInscriptionsApplyAndModifyAttributes(GameTestHelper context) {
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        for (AugmentDefinition def : AugmentRegistry.all()) {
            if (def.target() != AugmentTarget.LEGENDARY) {
                continue;
            }
            ItemStack legendary = new ItemStack(ModItems.DUELISTS_RAPIER);
            AugmentInstance inst = new AugmentInstance(def.id(), AugmentRarity.COMMON, 1.0f);
            if (!AugmentRuntime.applyLegendaryAugment(player, legendary, inst)) {
                context.fail("Failed to apply legendary inscription: " + def.id());
                return;
            }
            if (AugmentRuntime.getLegendaryAugments(legendary).isEmpty()) {
                context.fail("Legendary inscription did not persist: " + def.id());
                return;
            }
        }

        ItemStack edge = new ItemStack(ModItems.DUELISTS_RAPIER);
        ItemStack swift = new ItemStack(ModItems.HUNTERS_TROPHY_NECKLACE);
        ItemStack ward = new ItemStack(ModItems.SUPREME_HELMET);
        AugmentRuntime.applyLegendaryAugment(player, edge, new AugmentInstance("edge", AugmentRarity.COMMON, 1.0f));
        AugmentRuntime.applyLegendaryAugment(player, swift, new AugmentInstance("swift", AugmentRarity.COMMON, 1.0f));
        AugmentRuntime.applyLegendaryAugment(player, ward, new AugmentInstance("ward", AugmentRarity.COMMON, 1.0f));

        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, edge);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, swift);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ward);

        AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
        if (damage == null || armor == null) {
            context.fail("Missing expected player attributes");
            return;
        }
        double baseDamage = damage.getBaseValue();
        double baseArmor = armor.getBaseValue();

        AugmentRuntime.applyLegendaryModifiers(player);

        if (damage.getValue() <= baseDamage) {
            context.fail("Expected legendary edge inscription to increase attack damage");
            return;
        }
        if (armor.getValue() <= baseArmor) {
            context.fail("Expected legendary ward inscription to increase armor");
            return;
        }

        GemsGameTestUtil.complete(context);
    }
}
