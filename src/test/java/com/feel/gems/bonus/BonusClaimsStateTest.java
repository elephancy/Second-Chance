package com.feel.gems.bonus;

import com.feel.gems.power.registry.PowerIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests for BonusClaimsState which manages server-wide unique bonus claims.
 */
public class BonusClaimsStateTest {

    private BonusClaimsState state;
    private UUID player1;
    private UUID player2;
    private UUID player3;

    @BeforeEach
    void setUp() {
        state = new BonusClaimsState();
        player1 = UUID.randomUUID();
        player2 = UUID.randomUUID();
        player3 = UUID.randomUUID();
    }

    @Test
    void freshStateHasNoClaims() {
        assertTrue(state.getPlayerAbilities(player1).isEmpty());
        assertTrue(state.getPlayerPassives(player1).isEmpty());
        assertTrue(state.getAvailableAbilities().size() == BonusPoolRegistry.BONUS_ABILITIES.size());
        assertTrue(state.getAvailablePassives().size() == BonusPoolRegistry.BONUS_PASSIVES.size());
    }

    @Test
    void canClaimBonusAbility() {
        boolean success = state.claimAbility(player1, PowerIds.BONUS_THUNDERSTRIKE);
        assertTrue(success, "First claim should succeed");
        assertTrue(state.getPlayerAbilities(player1).contains(PowerIds.BONUS_THUNDERSTRIKE));
        assertEquals(player1, state.getAbilityClaimant(PowerIds.BONUS_THUNDERSTRIKE));
    }

    @Test
    void canClaimBonusPassive() {
        boolean success = state.claimPassive(player1, PowerIds.BONUS_THORNS_AURA);
        assertTrue(success, "First claim should succeed");
        assertTrue(state.getPlayerPassives(player1).contains(PowerIds.BONUS_THORNS_AURA));
        assertEquals(player1, state.getPassiveClaimant(PowerIds.BONUS_THORNS_AURA));
    }

    @Test
    void cannotClaimNonBonusAbility() {
        boolean success = state.claimAbility(player1, PowerIds.AIR_DASH);
        assertFalse(success, "Should not be able to claim non-bonus ability");
        assertTrue(state.getPlayerAbilities(player1).isEmpty());
    }

    @Test
    void cannotClaimNonBonusPassive() {
        boolean success = state.claimPassive(player1, PowerIds.FIRE_RESISTANCE);
        assertFalse(success, "Should not be able to claim non-bonus passive");
        assertTrue(state.getPlayerPassives(player1).isEmpty());
    }

    @Test
    void cannotClaimAbilityAlreadyClaimedByOther() {
        state.claimAbility(player1, PowerIds.BONUS_THUNDERSTRIKE);
        
        boolean success = state.claimAbility(player2, PowerIds.BONUS_THUNDERSTRIKE);
        assertFalse(success, "Cannot claim ability already claimed by another player");
        assertFalse(state.getPlayerAbilities(player2).contains(PowerIds.BONUS_THUNDERSTRIKE));
        assertEquals(player1, state.getAbilityClaimant(PowerIds.BONUS_THUNDERSTRIKE));
    }

    @Test
    void cannotClaimPassiveAlreadyClaimedByOther() {
        state.claimPassive(player1, PowerIds.BONUS_THORNS_AURA);
        
        boolean success = state.claimPassive(player2, PowerIds.BONUS_THORNS_AURA);
        assertFalse(success, "Cannot claim passive already claimed by another player");
        assertFalse(state.getPlayerPassives(player2).contains(PowerIds.BONUS_THORNS_AURA));
        assertEquals(player1, state.getPassiveClaimant(PowerIds.BONUS_THORNS_AURA));
    }

    @Test
    void samePlayerCanReclaimOwnAbility() {
        state.claimAbility(player1, PowerIds.BONUS_THUNDERSTRIKE);
        
        // Re-claiming own ability should succeed (idempotent)
        boolean success = state.claimAbility(player1, PowerIds.BONUS_THUNDERSTRIKE);
        assertTrue(success, "Player should be able to re-claim own ability");
        assertEquals(1, state.getPlayerAbilities(player1).size());
    }

    @Test
    void playerCanClaimMaxTwoBonusAbilities() {
        assertTrue(state.claimAbility(player1, PowerIds.BONUS_THUNDERSTRIKE));
        assertTrue(state.claimAbility(player1, PowerIds.BONUS_FROSTBITE));
        
        boolean thirdClaim = state.claimAbility(player1, PowerIds.BONUS_EARTHSHATTER);
        assertFalse(thirdClaim, "Should not be able to claim more than 2 abilities");
        assertEquals(2, state.getPlayerAbilities(player1).size());
    }

    @Test
    void playerCanClaimMaxTwoBonusPassives() {
        assertTrue(state.claimPassive(player1, PowerIds.BONUS_THORNS_AURA));
        assertTrue(state.claimPassive(player1, PowerIds.BONUS_LIFESTEAL));
        
        boolean thirdClaim = state.claimPassive(player1, PowerIds.BONUS_DODGE_CHANCE);
        assertFalse(thirdClaim, "Should not be able to claim more than 2 passives");
        assertEquals(2, state.getPlayerPassives(player1).size());
    }

    @Test
    void releaseAllClaimsClearsPlayerClaims() {
        state.claimAbility(player1, PowerIds.BONUS_THUNDERSTRIKE);
        state.claimAbility(player1, PowerIds.BONUS_FROSTBITE);
        state.claimPassive(player1, PowerIds.BONUS_THORNS_AURA);
        
        state.releaseAllClaims(player1);
        
        assertTrue(state.getPlayerAbilities(player1).isEmpty());
        assertTrue(state.getPlayerPassives(player1).isEmpty());
        assertNull(state.getAbilityClaimant(PowerIds.BONUS_THUNDERSTRIKE));
        assertNull(state.getAbilityClaimant(PowerIds.BONUS_FROSTBITE));
        assertNull(state.getPassiveClaimant(PowerIds.BONUS_THORNS_AURA));
    }

    @Test
    void releaseAllowsOtherPlayerToClaim() {
        state.claimAbility(player1, PowerIds.BONUS_THUNDERSTRIKE);
        state.releaseAllClaims(player1);
        
        boolean success = state.claimAbility(player2, PowerIds.BONUS_THUNDERSTRIKE);
        assertTrue(success, "Other player should be able to claim after release");
        assertEquals(player2, state.getAbilityClaimant(PowerIds.BONUS_THUNDERSTRIKE));
    }

    @Test
    void isAbilityAvailableReflectsState() {
        assertTrue(state.isAbilityAvailable(PowerIds.BONUS_THUNDERSTRIKE));
        
        state.claimAbility(player1, PowerIds.BONUS_THUNDERSTRIKE);
        
        assertFalse(state.isAbilityAvailable(PowerIds.BONUS_THUNDERSTRIKE));
        assertTrue(state.isAbilityAvailable(PowerIds.BONUS_FROSTBITE));
    }

    @Test
    void isPassiveAvailableReflectsState() {
        assertTrue(state.isPassiveAvailable(PowerIds.BONUS_THORNS_AURA));
        
        state.claimPassive(player1, PowerIds.BONUS_THORNS_AURA);
        
        assertFalse(state.isPassiveAvailable(PowerIds.BONUS_THORNS_AURA));
        assertTrue(state.isPassiveAvailable(PowerIds.BONUS_LIFESTEAL));
    }

    @Test
    void getAvailableAbilitiesExcludesClaimedOnes() {
        int totalAbilities = BonusPoolRegistry.BONUS_ABILITIES.size();
        assertEquals(totalAbilities, state.getAvailableAbilities().size());
        
        state.claimAbility(player1, PowerIds.BONUS_THUNDERSTRIKE);
        state.claimAbility(player2, PowerIds.BONUS_FROSTBITE);
        
        var available = state.getAvailableAbilities();
        assertEquals(totalAbilities - 2, available.size());
        assertFalse(available.contains(PowerIds.BONUS_THUNDERSTRIKE));
        assertFalse(available.contains(PowerIds.BONUS_FROSTBITE));
    }

    @Test
    void getAvailablePassivesExcludesClaimedOnes() {
        int totalPassives = BonusPoolRegistry.BONUS_PASSIVES.size();
        assertEquals(totalPassives, state.getAvailablePassives().size());
        
        state.claimPassive(player1, PowerIds.BONUS_THORNS_AURA);
        
        var available = state.getAvailablePassives();
        assertEquals(totalPassives - 1, available.size());
        assertFalse(available.contains(PowerIds.BONUS_THORNS_AURA));
    }

    @Test
    void constructorWithExistingClaimsRestoresState() {
        var abilityClaims = Map.of(
                PowerIds.BONUS_THUNDERSTRIKE, player1,
                PowerIds.BONUS_FROSTBITE, player2
        );
        var passiveClaims = Map.of(
                PowerIds.BONUS_THORNS_AURA, player1
        );
        
        BonusClaimsState restored = new BonusClaimsState(abilityClaims, passiveClaims, Map.of());
        
        assertEquals(player1, restored.getAbilityClaimant(PowerIds.BONUS_THUNDERSTRIKE));
        assertEquals(player2, restored.getAbilityClaimant(PowerIds.BONUS_FROSTBITE));
        assertEquals(player1, restored.getPassiveClaimant(PowerIds.BONUS_THORNS_AURA));
        
        assertTrue(restored.getPlayerAbilities(player1).contains(PowerIds.BONUS_THUNDERSTRIKE));
        assertTrue(restored.getPlayerAbilities(player2).contains(PowerIds.BONUS_FROSTBITE));
        assertTrue(restored.getPlayerPassives(player1).contains(PowerIds.BONUS_THORNS_AURA));
    }

    @Test
    void getPlayerAbilitiesReturnsImmutableCopy() {
        state.claimAbility(player1, PowerIds.BONUS_THUNDERSTRIKE);
        Set<Identifier> abilities = state.getPlayerAbilities(player1);
        
        assertThrows(UnsupportedOperationException.class, () -> {
            abilities.add(PowerIds.BONUS_FROSTBITE);
        }, "Returned set should be immutable");
    }

    @Test
    void getPlayerPassivesReturnsImmutableCopy() {
        state.claimPassive(player1, PowerIds.BONUS_THORNS_AURA);
        Set<Identifier> passives = state.getPlayerPassives(player1);
        
        assertThrows(UnsupportedOperationException.class, () -> {
            passives.add(PowerIds.BONUS_LIFESTEAL);
        }, "Returned set should be immutable");
    }

    @Test
    void multiplePlayersCanClaimDifferentAbilities() {
        assertTrue(state.claimAbility(player1, PowerIds.BONUS_THUNDERSTRIKE));
        assertTrue(state.claimAbility(player2, PowerIds.BONUS_FROSTBITE));
        assertTrue(state.claimAbility(player3, PowerIds.BONUS_EARTHSHATTER));
        
        assertEquals(player1, state.getAbilityClaimant(PowerIds.BONUS_THUNDERSTRIKE));
        assertEquals(player2, state.getAbilityClaimant(PowerIds.BONUS_FROSTBITE));
        assertEquals(player3, state.getAbilityClaimant(PowerIds.BONUS_EARTHSHATTER));
    }

    @Test
    void abilitiesAndPassivesAreIndependent() {
        // Player can have 2 abilities AND 2 passives
        assertTrue(state.claimAbility(player1, PowerIds.BONUS_THUNDERSTRIKE));
        assertTrue(state.claimAbility(player1, PowerIds.BONUS_FROSTBITE));
        assertTrue(state.claimPassive(player1, PowerIds.BONUS_THORNS_AURA));
        assertTrue(state.claimPassive(player1, PowerIds.BONUS_LIFESTEAL));
        
        assertEquals(2, state.getPlayerAbilities(player1).size());
        assertEquals(2, state.getPlayerPassives(player1).size());
    }
}
