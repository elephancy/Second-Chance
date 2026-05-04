package com.feel.gems.core;

import java.util.List;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;


public class GemEnergySpecTest {

    @Test
    void abilityUnlockCountsFollowSpec() {
        // Using a 4-ability gem to exercise subrange and max cases.
        assertEquals(0, new GemEnergyState(0).unlockedAbilityCount(4));
        assertEquals(0, new GemEnergyState(1).unlockedAbilityCount(4));
        assertEquals(1, new GemEnergyState(2).unlockedAbilityCount(4));
        assertEquals(2, new GemEnergyState(3).unlockedAbilityCount(4));
        assertEquals(3, new GemEnergyState(4).unlockedAbilityCount(4));
        // At 5+ all abilities unlock regardless of count.
        assertEquals(4, new GemEnergyState(5).unlockedAbilityCount(4));
        assertEquals(4, new GemEnergyState(10).unlockedAbilityCount(4));
    }

    @Test
    void availableAbilitiesMirrorUnlockProgression() {
        var abilities = List.of(
                Identifier.fromNamespaceAndPath("gems", "a1"),
                Identifier.fromNamespaceAndPath("gems", "a2"),
                Identifier.fromNamespaceAndPath("gems", "a3")
        );
        var def = new GemDefinition(GemId.ASTRA, List.of(), abilities);

        assertEquals(List.of(), def.availableAbilities(new GemEnergyState(1)));
        assertEquals(List.of(abilities.get(0)), def.availableAbilities(new GemEnergyState(2)));
        assertEquals(abilities.subList(0, 2), def.availableAbilities(new GemEnergyState(3)));
        assertEquals(abilities, def.availableAbilities(new GemEnergyState(5)));
    }

    @Test
    void energyClampsAndLosesOneOnDeath() {
        assertEquals(0, new GemEnergyState(-5).value());
        assertEquals(10, new GemEnergyState(50).value());

        var start = new GemEnergyState(3);
        assertEquals(2, start.loseOne().value());
        assertEquals(0, new GemEnergyState(0).loseOne().value());
    }

    @Test
    void gainRespectsClampAndOverflowKeepsAbilities() {
        var state = new GemEnergyState(4).gain(6);
        assertEquals(10, state.value());
        assertEquals(3, state.unlockedAbilityCount(3));
    }

    @Test
    void passivesRequirePositiveEnergy() {
        var def = new GemDefinition(GemId.FIRE, List.of(Identifier.fromNamespaceAndPath("gems", "p1")), List.of());
        assertEquals(List.of(), def.availablePassives(new GemEnergyState(0)));
        assertEquals(1, def.availablePassives(new GemEnergyState(1)).size());
    }
}
