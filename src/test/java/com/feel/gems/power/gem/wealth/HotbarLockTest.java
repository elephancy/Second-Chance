package com.feel.gems.power.gem.wealth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.nbt.CompoundTag;


public class HotbarLockTest {
    @Test
    void locksAndExpires() {
        CompoundTag nbt = new CompoundTag();
        long now = 1000L;

        HotbarLock.lock(nbt, now, 2, 60);
        assertEquals(2, HotbarLock.lockedSlot(nbt, now));
        assertEquals(2, HotbarLock.lockedSlot(nbt, now + 59));
        assertEquals(-1, HotbarLock.lockedSlot(nbt, now + 60));
        assertEquals(-1, HotbarLock.lockedSlot(nbt, now + 999));
    }

    @Test
    void clampsSlot() {
        CompoundTag nbt = new CompoundTag();
        HotbarLock.lock(nbt, 0L, 999, 20);
        assertEquals(8, HotbarLock.lockedSlot(nbt, 0L));
    }
}

