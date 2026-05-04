package com.feel.gems.adventure.nether;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

class NetherChapterStateTest {
    @Test
    void sharedPartialRecordsAndSheltersSurviveReloadBeforeEitherClear() {
        NetherChapterState state = new NetherChapterState();
        state.readRecord(NetherChapterState.REGISTER);
        state.restoreShelter(NetherChapterState.EAST_SHELTER);

        var encoded = NetherChapterState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
        NetherChapterState restored = NetherChapterState.CODEC
                .parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(1, restored.recordCount());
        assertEquals(1, restored.shelterCount());
        assertFalse(restored.annexCleared());
        assertFalse(restored.bridgeCleared());

        restored.readRecord(NetherChapterState.SIGNAL);
        restored.readRecord(NetherChapterState.PLATFORM);
        assertTrue(restored.clearAnnex());
        restored.restoreShelter(NetherChapterState.WEST_SHELTER);
        assertTrue(restored.clearBridge());
        var completed = NetherChapterState.CODEC.encodeStart(JsonOps.INSTANCE, restored)
                .getOrThrow();
        NetherChapterState afterSecondReload = NetherChapterState.CODEC
                .parse(JsonOps.INSTANCE, completed).getOrThrow();
        assertTrue(afterSecondReload.requiredChaptersCleared());
    }
}
