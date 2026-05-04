package com.feel.gems.adventure.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

final class CadenzaPatternTest {
    @Test
    void habitsChooseLearnableMoves() {
        assertEquals(CadenzaPattern.Act.SPOTLIGHT,
                CadenzaPattern.choose(1, false, 0.5, null));
        assertEquals(CadenzaPattern.Act.CROSSCUT,
                CadenzaPattern.choose(1, false, 5.0, null));
        assertEquals(CadenzaPattern.Act.CHORUS,
                CadenzaPattern.choose(2, true, 8.0, null));
        assertEquals(CadenzaPattern.Act.CROSSCUT,
                CadenzaPattern.choose(2, true, 0.0, CadenzaPattern.Act.CROSSCUT));
    }

    @Test
    void markCyclesThroughAllThreeMoves() {
        CadenzaPattern.Act act = CadenzaPattern.Act.SPOTLIGHT;
        assertEquals(CadenzaPattern.Act.CROSSCUT, act.next());
        assertEquals(CadenzaPattern.Act.CHORUS, act.next().next());
        assertEquals(act, act.next().next().next());
    }
}
