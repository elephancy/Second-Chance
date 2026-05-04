package com.feel.gems.adventure.worldgen;

import com.feel.gems.testutil.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DistrictArrivalAccessReachTest {
    @Test
    void rejectsLookaheadPastTheThreeByThreeRegion() {
        MinecraftBootstrap.ensure();
        ChunkPos center = new ChunkPos(0, 0);

        assertTrue(DistrictArrivalAccessFeature.withinReach(center, new BlockPos(31, 70, 0)));
        assertFalse(DistrictArrivalAccessFeature.withinReach(center, new BlockPos(32, 70, 0)));
        assertFalse(DistrictArrivalAccessFeature.withinReach(center, new BlockPos(47, 70, 0)));
        assertTrue(DistrictArrivalAccessFeature.withinReach(center, new BlockPos(-16, 70, -16)));
        assertFalse(DistrictArrivalAccessFeature.withinReach(center, new BlockPos(-17, 70, -16)));
    }
}
