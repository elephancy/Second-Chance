package com.feel.gems.power.gem.wealth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.nbt.CompoundTag;


public class WealthFumbleTest {
    @Test
    void appliesAndExpires() {
        CompoundTag nbt = new CompoundTag();
        long now = 500L;
        WealthFumble.apply(nbt, now, 40);

        assertTrue(WealthFumble.until(nbt, now) > now);
        assertTrue(WealthFumble.until(nbt, now + 39) > now + 39);
        assertEquals(0L, WealthFumble.until(nbt, now + 40));
        assertEquals(0L, WealthFumble.until(nbt, now + 999));
    }
}

