package com.feel.gems.bonus;

import com.feel.gems.power.registry.PowerIds;
import com.feel.gems.testutil.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests for PrismSelectionsState which tracks Prism gem ability/passive selections.
 */
public class PrismSelectionsStateTest {

    private PrismSelectionsState state;
    private UUID player1;
    private UUID player2;

    @BeforeAll
    static void initBootstrap() {
        MinecraftBootstrap.ensure();
    }

    @BeforeEach
    void setUp() {
        state = new PrismSelectionsState();
        player1 = UUID.randomUUID();
        player2 = UUID.randomUUID();
    }

    @Test
    void freshStateHasEmptySelection() {
        var selection = state.getSelection(player1);
        assertTrue(selection.gemAbilities().isEmpty());
        assertTrue(selection.bonusAbilities().isEmpty());
        assertTrue(selection.gemPassives().isEmpty());
        assertTrue(selection.bonusPassives().isEmpty());
        assertEquals(0, selection.totalAbilities());
        assertEquals(0, selection.totalPassives());
    }

    @Test
    void emptySelectionFactoryMethod() {
        var empty = PrismSelectionsState.PrismSelection.empty();
        assertTrue(empty.allAbilities().isEmpty());
        assertTrue(empty.allPassives().isEmpty());
    }

    // =========== Gem Ability Tests ===========

    @Test
    void canAddGemAbility() {
        // We need a real registered ability ID - use AIR_DASH
        boolean success = state.addGemAbility(player1, PowerIds.AIR_DASH);
        assertTrue(success, "Should be able to add valid gem ability");
        
        var selection = state.getSelection(player1);
        assertTrue(selection.gemAbilities().contains(PowerIds.AIR_DASH));
        assertEquals(1, selection.totalAbilities());
    }

    @Test
    void canAddMaxThreeGemAbilities() {
        assertTrue(state.addGemAbility(player1, PowerIds.AIR_DASH));
        assertTrue(state.addGemAbility(player1, PowerIds.FIREBALL));
        assertTrue(state.addGemAbility(player1, PowerIds.FLUX_BEAM));
        
        // Fourth should fail
        boolean fourth = state.addGemAbility(player1, PowerIds.DOUBLE_JUMP);
        assertFalse(fourth, "Should not be able to add more than 3 gem abilities");
        assertEquals(3, state.getSelection(player1).gemAbilities().size());
    }

    @Test
    void cannotAddDuplicateGemAbility() {
        state.addGemAbility(player1, PowerIds.AIR_DASH);
        
        boolean duplicate = state.addGemAbility(player1, PowerIds.AIR_DASH);
        assertFalse(duplicate, "Should not be able to add duplicate ability");
        assertEquals(1, state.getSelection(player1).gemAbilities().size());
    }

    @Test
    void cannotAddBlacklistedAbility() {
        boolean success = state.addGemAbility(player1, PowerIds.VOID_IMMUNITY);
        assertFalse(success, "Should not be able to add blacklisted ability");
        assertTrue(state.getSelection(player1).gemAbilities().isEmpty());
    }

    @Test
    void cannotAddNullifyToGemAbilities() {
        boolean success = state.addGemAbility(player1, PowerIds.NULLIFY);
        assertFalse(success, "Nullify is blacklisted for Prism");
        assertTrue(state.getSelection(player1).gemAbilities().isEmpty());
    }

    // =========== Bonus Ability Tests ===========

    @Test
    void canAddBonusAbility() {
        boolean success = state.addBonusAbility(player1, PowerIds.BONUS_THUNDERSTRIKE);
        assertTrue(success, "Should be able to add valid bonus ability");

        var selection = state.getSelection(player1);
        assertTrue(selection.bonusAbilities().contains(PowerIds.BONUS_THUNDERSTRIKE));
        assertEquals(1, selection.bonusAbilities().size());
        assertEquals(1, selection.totalAbilities());
    }

    @Test
    void canAddMaxTwoBonusAbilities() {
        assertTrue(state.addBonusAbility(player1, PowerIds.BONUS_THUNDERSTRIKE));
        assertTrue(state.addBonusAbility(player1, PowerIds.BONUS_FROSTBITE));

        boolean third = state.addBonusAbility(player1, PowerIds.BONUS_EARTHSHATTER);
        assertFalse(third, "Should not be able to add more than 2 bonus abilities");
        assertEquals(2, state.getSelection(player1).bonusAbilities().size());
    }

    @Test
    void cannotAddNonBonusAsBonusAbility() {
        boolean success = state.addBonusAbility(player1, PowerIds.AIR_DASH);
        assertFalse(success, "Regular ability should not be addable as bonus ability");
    }

    @Test
    void cannotAddDuplicateBonusAbility() {
        assertTrue(state.addBonusAbility(player1, PowerIds.BONUS_THUNDERSTRIKE));

        assertFalse(state.addBonusAbility(player1, PowerIds.BONUS_THUNDERSTRIKE));
        assertEquals(1, state.getSelection(player1).bonusAbilities().size());
    }

    // =========== Gem Passive Tests ===========

    @Test
    void canAddGemPassive() {
        boolean success = state.addGemPassive(player1, PowerIds.FIRE_RESISTANCE);
        assertTrue(success, "Should be able to add valid gem passive");
        
        var selection = state.getSelection(player1);
        assertTrue(selection.gemPassives().contains(PowerIds.FIRE_RESISTANCE));
        assertEquals(1, selection.totalPassives());
    }

    @Test
    void canAddMaxThreeGemPassives() {
        assertTrue(state.addGemPassive(player1, PowerIds.FIRE_RESISTANCE));
        assertTrue(state.addGemPassive(player1, PowerIds.FALL_DAMAGE_IMMUNITY));
        assertTrue(state.addGemPassive(player1, PowerIds.AUTO_SMELT));
        
        boolean fourth = state.addGemPassive(player1, PowerIds.LUCK);
        assertFalse(fourth, "Should not be able to add more than 3 gem passives");
        assertEquals(3, state.getSelection(player1).gemPassives().size());
    }

    @Test
    void cannotAddDuplicateGemPassive() {
        state.addGemPassive(player1, PowerIds.FIRE_RESISTANCE);
        
        boolean duplicate = state.addGemPassive(player1, PowerIds.FIRE_RESISTANCE);
        assertFalse(duplicate, "Should not be able to add duplicate passive");
        assertEquals(1, state.getSelection(player1).gemPassives().size());
    }

    // =========== Bonus Passive Tests ===========

    @Test
    void canAddBonusPassive() {
        boolean success = state.addBonusPassive(player1, PowerIds.BONUS_THORNS_AURA);
        assertTrue(success, "Should be able to add valid bonus passive");

        var selection = state.getSelection(player1);
        assertTrue(selection.bonusPassives().contains(PowerIds.BONUS_THORNS_AURA));
        assertEquals(1, selection.bonusPassives().size());
        assertEquals(1, selection.totalPassives());
    }

    @Test
    void canAddMaxTwoBonusPassives() {
        assertTrue(state.addBonusPassive(player1, PowerIds.BONUS_THORNS_AURA));
        assertTrue(state.addBonusPassive(player1, PowerIds.BONUS_LIFESTEAL));

        boolean third = state.addBonusPassive(player1, PowerIds.BONUS_DODGE_CHANCE);
        assertFalse(third, "Should not be able to add more than 2 bonus passives");
        assertEquals(2, state.getSelection(player1).bonusPassives().size());
    }

    @Test
    void cannotAddNonBonusAsBonusPassive() {
        boolean success = state.addBonusPassive(player1, PowerIds.FIRE_RESISTANCE);
        assertFalse(success, "Regular passive should not be addable as bonus passive");
    }

    // =========== Combined Limits Tests ===========

    @Test
    void maxTotalAbilitiesIsFive() {
        assertTrue(state.addGemAbility(player1, PowerIds.AIR_DASH));
        assertTrue(state.addGemAbility(player1, PowerIds.FIREBALL));
        assertTrue(state.addGemAbility(player1, PowerIds.FLUX_BEAM));
        assertTrue(state.addBonusAbility(player1, PowerIds.BONUS_THUNDERSTRIKE));
        assertTrue(state.addBonusAbility(player1, PowerIds.BONUS_FROSTBITE));

        var selection = state.getSelection(player1);
        assertEquals(5, selection.totalAbilities());
        assertEquals(5, selection.allAbilities().size());
    }

    @Test
    void maxTotalPassivesIsFive() {
        assertTrue(state.addGemPassive(player1, PowerIds.FIRE_RESISTANCE));
        assertTrue(state.addGemPassive(player1, PowerIds.FALL_DAMAGE_IMMUNITY));
        assertTrue(state.addGemPassive(player1, PowerIds.AUTO_SMELT));
        assertTrue(state.addBonusPassive(player1, PowerIds.BONUS_THORNS_AURA));
        assertTrue(state.addBonusPassive(player1, PowerIds.BONUS_LIFESTEAL));

        var selection = state.getSelection(player1);
        assertEquals(5, selection.totalPassives());
        assertEquals(5, selection.allPassives().size());
    }

    // =========== Clear/Remove Tests ===========

    @Test
    void clearSelectionsRemovesAllForPlayer() {
        state.addGemAbility(player1, PowerIds.AIR_DASH);
        state.addGemPassive(player1, PowerIds.FIRE_RESISTANCE);
        
        state.clearSelections(player1);
        
        var selection = state.getSelection(player1);
        assertEquals(0, selection.totalAbilities());
        assertEquals(0, selection.totalPassives());
    }

    @Test
    void clearSelectionsDoesNotAffectOtherPlayers() {
        state.addGemAbility(player1, PowerIds.AIR_DASH);
        state.addGemAbility(player2, PowerIds.FIREBALL);
        
        state.clearSelections(player1);
        
        assertTrue(state.getSelection(player1).gemAbilities().isEmpty());
        assertTrue(state.getSelection(player2).gemAbilities().contains(PowerIds.FIREBALL));
    }

    @Test
    void removeAbilityRemovesSpecificAbility() {
        state.addGemAbility(player1, PowerIds.AIR_DASH);
        state.addGemAbility(player1, PowerIds.FIREBALL);
        
        state.removeAbility(player1, PowerIds.AIR_DASH);
        
        var selection = state.getSelection(player1);
        assertFalse(selection.gemAbilities().contains(PowerIds.AIR_DASH));
        assertTrue(selection.gemAbilities().contains(PowerIds.FIREBALL));
    }

    @Test
    void removePassiveRemovesSpecificPassive() {
        state.addGemPassive(player1, PowerIds.FIRE_RESISTANCE);
        state.addGemPassive(player1, PowerIds.FALL_DAMAGE_IMMUNITY);
        
        state.removePassive(player1, PowerIds.FIRE_RESISTANCE);
        
        var selection = state.getSelection(player1);
        assertFalse(selection.gemPassives().contains(PowerIds.FIRE_RESISTANCE));
        assertTrue(selection.gemPassives().contains(PowerIds.FALL_DAMAGE_IMMUNITY));
    }

    // =========== allAbilities/allPassives Tests ===========

    @Test
    void allAbilitiesCombinesGemAndBonus() {
        state.addGemAbility(player1, PowerIds.AIR_DASH);
        state.addBonusAbility(player1, PowerIds.BONUS_THUNDERSTRIKE);
        
        var all = state.getSelection(player1).allAbilities();
        assertEquals(2, all.size());
        assertTrue(all.contains(PowerIds.AIR_DASH));
        assertTrue(all.contains(PowerIds.BONUS_THUNDERSTRIKE));
    }

    @Test
    void allPassivesCombinesGemAndBonus() {
        state.addGemPassive(player1, PowerIds.FIRE_RESISTANCE);
        state.addBonusPassive(player1, PowerIds.BONUS_THORNS_AURA);
        
        var all = state.getSelection(player1).allPassives();
        assertEquals(2, all.size());
        assertTrue(all.contains(PowerIds.FIRE_RESISTANCE));
        assertTrue(all.contains(PowerIds.BONUS_THORNS_AURA));
    }

    // =========== Cross-Player Independence Tests ===========

    @Test
    void playersHaveIndependentSelections() {
        state.addGemAbility(player1, PowerIds.AIR_DASH);
        state.addGemAbility(player2, PowerIds.FIREBALL);
        
        assertTrue(state.getSelection(player1).gemAbilities().contains(PowerIds.AIR_DASH));
        assertFalse(state.getSelection(player1).gemAbilities().contains(PowerIds.FIREBALL));
        
        assertTrue(state.getSelection(player2).gemAbilities().contains(PowerIds.FIREBALL));
        assertFalse(state.getSelection(player2).gemAbilities().contains(PowerIds.AIR_DASH));
    }

    @Test
    void multiplePlayersCanSelectSameAbility() {
        // Unlike BonusClaimsState, PrismSelectionsState allows same ability for multiple players
        assertTrue(state.addGemAbility(player1, PowerIds.AIR_DASH));
        assertTrue(state.addGemAbility(player2, PowerIds.AIR_DASH));
        
        assertTrue(state.getSelection(player1).gemAbilities().contains(PowerIds.AIR_DASH));
        assertTrue(state.getSelection(player2).gemAbilities().contains(PowerIds.AIR_DASH));
    }
}
