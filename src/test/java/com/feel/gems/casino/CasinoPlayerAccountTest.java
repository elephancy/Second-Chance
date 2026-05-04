package com.feel.gems.casino;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class CasinoPlayerAccountTest {
    @Test
    void unresolvedStakeRefundsOnceAfterPlayerdataReload() {
        CompoundTag savedPlayer = new CompoundTag();
        CasinoPlayerAccount.in(savedPlayer).hold(4);

        CasinoPlayerAccount reloaded = CasinoPlayerAccount.in(savedPlayer.copy());
        reloaded.refund();
        reloaded.refund();
        assertEquals(0, reloaded.pending());
        assertEquals(4, reloaded.claimBalance());
        assertEquals(4, CasinoPlayerAccount.in(savedPlayer.copy()).pending());
    }

    @Test
    void settlementAndDailyBonusPersistInTheSamePlayerRecord() {
        CompoundTag player = new CompoundTag();
        CasinoPlayerAccount account = CasinoPlayerAccount.in(player);
        account.hold(2);
        assertTrue(account.claimTriviaBonus(10));
        account.settle(3);

        CasinoPlayerAccount reloaded = CasinoPlayerAccount.in(player.copy());
        assertEquals(0, reloaded.pending());
        assertEquals(3, reloaded.claimBalance());
        assertFalse(reloaded.claimTriviaBonus(10));
        assertFalse(reloaded.claimTriviaBonus(9));
        assertTrue(reloaded.claimTriviaBonus(11));
        assertThrows(IllegalStateException.class, () -> reloaded.settle(3));
    }

    @Test
    void unpaidPayoutRemainsClaimableWhenDeliveryCannotFit() {
        CompoundTag player = new CompoundTag();
        CasinoPlayerAccount account = CasinoPlayerAccount.in(player);
        account.hold(3);
        account.settle(21);
        account.setClaimBalance(8); // Inventory took 13; eight still have no slot.

        CompoundTag reloadedPlayer = player.copy();
        CasinoPlayerAccount reloaded = CasinoPlayerAccount.in(reloadedPlayer);
        assertEquals(8, reloaded.claimBalance());
        reloaded.setClaimBalance(0);
        assertEquals(0, CasinoPlayerAccount.in(reloadedPlayer.copy()).claimBalance());
    }

    @Test
    void interruptedCoopReservationRecoversEachPlayerIndependently() {
        CompoundTag firstPlayer = new CompoundTag();
        CompoundTag secondPlayer = new CompoundTag();
        CasinoPlayerAccount.in(firstPlayer).hold(2);
        // Crash before the second player is debited or their account is held.
        CasinoPlayerAccount first = CasinoPlayerAccount.in(firstPlayer.copy());
        CasinoPlayerAccount second = CasinoPlayerAccount.in(secondPlayer.copy());
        first.refund();
        second.refund();
        assertEquals(2, first.claimBalance());
        assertEquals(0, second.claimBalance());
    }

    @Test
    void provisionsReceiptPersistsWithPlayerAndDoesNotReopenOnClockRewind() {
        CompoundTag player = new CompoundTag();
        CasinoPlayerAccount account = CasinoPlayerAccount.in(player);
        assertFalse(account.provisionsClaimed(0));
        account.markProvisionsClaimed(0);
        CasinoPlayerAccount reloaded = CasinoPlayerAccount.in(player.copy());
        assertTrue(reloaded.provisionsClaimed(0));
        assertFalse(reloaded.provisionsClaimed(1));
        reloaded.markProvisionsClaimed(1);
        assertTrue(reloaded.provisionsClaimed(0));
        assertTrue(reloaded.provisionsClaimed(1));
        assertFalse(reloaded.provisionsClaimed(2));
    }
}
