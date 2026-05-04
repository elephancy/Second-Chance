package com.feel.gems.adventure;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.feel.gems.adventure.CampaignRouteState.Path;
import com.feel.gems.adventure.endsource.EndSourceState;
import com.feel.gems.adventure.nether.NetherChapterState;
import org.junit.jupiter.api.Test;

class RequiredChaptersTest {
    @Test
    void chapterActivationIsAtomicAcrossTheThreeStructureSets() {
        assertEquals(RequiredChapters.Activation.INCOMPLETE,
                RequiredChapters.activation(false, false, false));
        assertEquals(RequiredChapters.Activation.ACTIVE,
                RequiredChapters.activation(true, true, true));
        for (int mask = 1; mask < 7; mask++) {
            assertEquals(RequiredChapters.Activation.INCOMPLETE,
                    RequiredChapters.activation((mask & 1) != 0, (mask & 2) != 0,
                            (mask & 4) != 0));
        }
    }

    @Test
    void sharedPartialProgressSelectsTheNextChapterAfterAJoinOrRestart() {
        NetherChapterState nether = new NetherChapterState();
        EndSourceState source = new EndSourceState();

        assertTrue(RequiredChapters.nextLead(Path.REMEMBER, nether, source)
                .contains("0/3"));
        nether.readRecord(NetherChapterState.REGISTER);
        assertTrue(RequiredChapters.nextLead(Path.REMEMBER, nether, source)
                .contains("1/3"));
        nether.readRecord(NetherChapterState.SIGNAL);
        nether.readRecord(NetherChapterState.PLATFORM);
        assertTrue(RequiredChapters.nextLead(Path.REMEMBER, nether, source)
                .contains("Set its departure board"));
        nether.clearAnnex();

        assertTrue(RequiredChapters.nextLead(Path.REMEMBER, nether, source)
                .contains("0/2"));
        nether.restoreShelter(NetherChapterState.EAST_SHELTER);
        assertTrue(RequiredChapters.nextLead(Path.REMEMBER, nether, source)
                .contains("1/2"));
        nether.restoreShelter(NetherChapterState.WEST_SHELTER);
        assertTrue(RequiredChapters.nextLead(Path.REMEMBER, nether, source)
                .contains("Begin the evacuation"));
        nether.clearBridge();

        assertTrue(RequiredChapters.nextLead(Path.REMEMBER, nether, source)
                .contains("west and east stories"));
        assertTrue(RequiredChapters.nextLead(Path.REPAIR, nether, source)
                .contains("protect both side lenses"));
        assertTrue(RequiredChapters.nextLead(Path.RELEASE, nether, source)
                .contains("four changing calls"));
        source.clear(Path.REMEMBER);
        assertNull(RequiredChapters.nextLead(Path.REMEMBER, nether, source));
    }
}
