package com.feel.gems.adventure.boss;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class InkLaneTest {
    private static final Vec3 FROM = new Vec3(0, 2, 0);
    private static final Vec3 END = new Vec3(0, 2, 10);

    @Test
    void finiteLaneDoesNotHitBehindCasterOrBeyondLockedPoint() {
        assertFalse(InkLane.intersects(FROM, END, new Vec3(0, 2, -0.5), 1.0));
        assertFalse(InkLane.intersects(FROM, END, new Vec3(0, 2, 10.5), 1.0));
        assertTrue(InkLane.intersects(FROM, END, new Vec3(0.5, 2, 5), 1.0));
        assertFalse(InkLane.intersects(FROM, END, new Vec3(1.5, 2, 5), 1.0));
    }
}
