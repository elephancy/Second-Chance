package com.feel.gems.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OthmanArchiveStateTest {
    @Test
    void bothRoomsAreRequiredAndTheFirstSharedLeadPersists() {
        OthmanArchiveState archive = new OthmanArchiveState();
        assertFalse(archive.publish(OthmanArchiveState.CATALOG));
        assertTrue(archive.record(OthmanArchiveState.CATALOG));
        assertFalse(archive.ready());
        assertFalse(archive.publish(OthmanArchiveState.CATALOG));
        assertTrue(archive.record(OthmanArchiveState.STUDY));
        assertTrue(archive.ready());
        assertTrue(archive.publish(OthmanArchiveState.STUDY));
        assertEquals(OthmanArchiveState.STUDY, archive.published());
        assertFalse(archive.publish(OthmanArchiveState.CATALOG));
    }
}
