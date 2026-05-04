package com.feel.gems.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class AdlSurveyStateTest {
    @Test
    void bothSightlinesAndMapAreRequiredBeforeTheCairn() {
        AdlSurveyState state = new AdlSurveyState();
        assertFalse(state.align());
        assertTrue(state.observe(AdlSurveyState.EAST));
        assertFalse(state.observe(AdlSurveyState.EAST));
        assertFalse(state.align());
        assertTrue(state.observe(AdlSurveyState.WEST));
        assertTrue(state.bothObserved());
        assertFalse(state.mapped());
        assertTrue(state.align());
        assertTrue(state.mapped());
        assertFalse(state.align());
    }

    @Test
    void aSharedPinCanMoveAndClearWithoutLosingSurveyProgress() {
        AdlSurveyState state = new AdlSurveyState();
        assertNull(state.pin());
        assertTrue(state.setPin(new BlockPos(120, 78, -90)));
        assertEquals(new BlockPos(120, 78, -90), state.pin());
        assertFalse(state.setPin(new BlockPos(120, 78, -90)));
        assertTrue(state.setPin(new BlockPos(-400, 64, 20)));
        assertEquals(new BlockPos(-400, 64, 20), state.pin());
        assertTrue(state.clearPin());
        assertFalse(state.clearPin());
        assertNull(state.pin());
    }
}
