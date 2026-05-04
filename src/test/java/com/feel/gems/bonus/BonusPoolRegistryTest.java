package com.feel.gems.bonus;

import com.feel.gems.power.registry.ModAbilities;
import com.feel.gems.power.registry.ModPassives;
import com.feel.gems.power.registry.PowerIds;
import com.feel.gems.testutil.MinecraftBootstrap;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BonusPoolRegistry which defines claimable bonus abilities and passives.
 */
public class BonusPoolRegistryTest {

    @Test
    void bonusPoolPowersAreRegistered() {
        MinecraftBootstrap.ensure();
        for (Identifier id : BonusPoolRegistry.BONUS_ABILITIES) {
            assertNotNull(ModAbilities.get(id), "Missing bonus ability implementation for " + id);
        }
        for (Identifier id : BonusPoolRegistry.BONUS_PASSIVES) {
            assertNotNull(ModPassives.get(id), "Missing bonus passive implementation for " + id);
        }
    }

    @Test
    void bonusAbilitiesListHasCorrectCount() {
        assertEquals(50, BonusPoolRegistry.BONUS_ABILITIES.size(),
                "Should have exactly 50 bonus abilities defined");
    }

    @Test
    void bonusPassivesListHasCorrectCount() {
        assertEquals(50, BonusPoolRegistry.BONUS_PASSIVES.size(),
                "Should have exactly 50 bonus passives defined");
    }

    @Test
    void bonusAbilitiesAllHaveBonusPrefix() {
        for (Identifier id : BonusPoolRegistry.BONUS_ABILITIES) {
            assertTrue(id.getPath().startsWith("bonus_"),
                    "Bonus ability should have bonus_ prefix: " + id);
        }
    }

    @Test
    void bonusPassivesAllHaveBonusPrefix() {
        for (Identifier id : BonusPoolRegistry.BONUS_PASSIVES) {
            assertTrue(id.getPath().startsWith("bonus_"),
                    "Bonus passive should have bonus_ prefix: " + id);
        }
    }

    @Test
    void isBonusAbilityReturnsTrueForBonusAbilities() {
        assertTrue(BonusPoolRegistry.isBonusAbility(PowerIds.BONUS_THUNDERSTRIKE));
        assertTrue(BonusPoolRegistry.isBonusAbility(PowerIds.BONUS_SANCTUARY));
        assertTrue(BonusPoolRegistry.isBonusAbility(PowerIds.BONUS_CHAIN_LIGHTNING));
    }

    @Test
    void isBonusAbilityReturnsFalseForNormalAbilities() {
        assertFalse(BonusPoolRegistry.isBonusAbility(PowerIds.AIR_DASH));
        assertFalse(BonusPoolRegistry.isBonusAbility(PowerIds.FIREBALL));
        assertFalse(BonusPoolRegistry.isBonusAbility(PowerIds.NULLIFY));
    }

    @Test
    void isBonusAbilityReturnsFalseForPassives() {
        assertFalse(BonusPoolRegistry.isBonusAbility(PowerIds.BONUS_THORNS_AURA));
        assertFalse(BonusPoolRegistry.isBonusAbility(PowerIds.BONUS_LIFESTEAL));
    }

    @Test
    void isBonusPassiveReturnsTrueForBonusPassives() {
        assertTrue(BonusPoolRegistry.isBonusPassive(PowerIds.BONUS_THORNS_AURA));
        assertTrue(BonusPoolRegistry.isBonusPassive(PowerIds.BONUS_POISON_IMMUNITY));
        assertTrue(BonusPoolRegistry.isBonusPassive(PowerIds.BONUS_LIFESTEAL));
    }

    @Test
    void isBonusPassiveReturnsFalseForNormalPassives() {
        assertFalse(BonusPoolRegistry.isBonusPassive(PowerIds.FIRE_RESISTANCE));
        assertFalse(BonusPoolRegistry.isBonusPassive(PowerIds.FALL_DAMAGE_IMMUNITY));
        assertFalse(BonusPoolRegistry.isBonusPassive(PowerIds.SOUL_CAPTURE));
    }

    @Test
    void isBonusPassiveReturnsFalseForAbilities() {
        assertFalse(BonusPoolRegistry.isBonusPassive(PowerIds.BONUS_THUNDERSTRIKE));
        assertFalse(BonusPoolRegistry.isBonusPassive(PowerIds.BONUS_SANCTUARY));
    }

    @Test
    void prismBlacklistContainsStrongPowers() {
        assertTrue(BonusPoolRegistry.isBlacklisted(PowerIds.VOID_IMMUNITY),
                "Void immunity should be blacklisted from Prism");
        assertTrue(BonusPoolRegistry.isBlacklisted(PowerIds.CHAOS_RANDOM_ROTATION),
                "Chaos rotation should be blacklisted from Prism");
        assertTrue(BonusPoolRegistry.isBlacklisted(PowerIds.NULLIFY),
                "Nullify should be blacklisted from Prism");
    }

    @Test
    void prismBlacklistDoesNotContainRegularPowers() {
        assertFalse(BonusPoolRegistry.isBlacklisted(PowerIds.AIR_DASH));
        assertFalse(BonusPoolRegistry.isBlacklisted(PowerIds.FIREBALL));
        assertFalse(BonusPoolRegistry.isBlacklisted(PowerIds.BONUS_THUNDERSTRIKE));
    }

    @Test
    void noOverlapBetweenAbilitiesAndPassives() {
        for (Identifier ability : BonusPoolRegistry.BONUS_ABILITIES) {
            assertFalse(BonusPoolRegistry.BONUS_PASSIVES.contains(ability),
                    "Ability " + ability + " should not also be in passives list");
        }
        for (Identifier passive : BonusPoolRegistry.BONUS_PASSIVES) {
            assertFalse(BonusPoolRegistry.BONUS_ABILITIES.contains(passive),
                    "Passive " + passive + " should not also be in abilities list");
        }
    }

    @Test
    void noDuplicatesInAbilitiesList() {
        long uniqueCount = BonusPoolRegistry.BONUS_ABILITIES.stream().distinct().count();
        assertEquals(BonusPoolRegistry.BONUS_ABILITIES.size(), uniqueCount,
                "Bonus abilities list should have no duplicates");
    }

    @Test
    void noDuplicatesInPassivesList() {
        long uniqueCount = BonusPoolRegistry.BONUS_PASSIVES.stream().distinct().count();
        assertEquals(BonusPoolRegistry.BONUS_PASSIVES.size(), uniqueCount,
                "Bonus passives list should have no duplicates");
    }
}
