package com.feel.gems.adventure.endsource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.feel.gems.adventure.CampaignRouteState.Path;
import com.mojang.serialization.JsonOps;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class EndSourceStateTest {
    @Test
    void routeEvidenceAndPersonalCourtReturnSurviveReload() {
        EndSourceState state = new EndSourceState();
        UUID visitor = UUID.randomUUID();
        BlockPos court = new BlockPos(82, 71, -240);
        state.recordCourtReturn(visitor, court);
        assertTrue(state.clear(Path.REPAIR));

        var encoded = EndSourceState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
        EndSourceState restored = EndSourceState.CODEC
                .parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertTrue(restored.cleared(Path.REPAIR));
        assertFalse(restored.cleared(Path.REMEMBER));
        assertFalse(restored.clear(Path.RELEASE));
        assertEquals(court, restored.courtReturn(visitor));
    }
}
