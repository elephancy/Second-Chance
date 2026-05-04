package com.feel.gems.adventure.worldgen.site;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

final class SitePlacementContractTest {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("gems", "site_contract_test");

    @Test
    void templateVersionDoesNotMoveTheCandidate() {
        SiteCandidate first = SitePlanner.candidate(definition(1, 2), 88122L, -4, 7, 64);
        SiteCandidate upgraded = SitePlanner.candidate(definition(2, 2), 88122L, -4, 7, 64);
        assertEquals(first, upgraded);
    }

    @Test
    void rotatedBoundsIncludeTheWholeApproach() {
        SiteVolume volume = new SiteVolume(SiteVolume.Use.APPROACH,
                0, 0, 0, 1, 2, 3);
        BoundingBox rotated = volume.at(new BlockPos(8, 64, 8), 1);
        assertEquals(5, rotated.minX());
        assertEquals(8, rotated.maxX());
        assertEquals(8, rotated.minZ());
        assertEquals(9, rotated.maxZ());
    }

    @Test
    void lateFeatureRejectsACompleteTwoChunkFootprint() {
        SiteDefinition definition = definition(1, 16);
        SiteCandidate candidate = new SiteCandidate(new BlockPos(8, 64, 8), 0);
        SitePlacementPlan plan = plan(candidate.anchor());
        SitePreflight.Result result = SitePreflight.check(definition, candidate,
                plan, new FixtureAccess(SiteAccess.Seam.WORLDGEN_REGION, false));
        assertEquals(SitePreflight.Status.NEEDS_SCHEDULER, result.status());
        assertEquals(SitePreflight.Reason.ENGINE_SEAM, result.reason());
    }

    @Test
    void newChunkStampAndProtectedAreaAreRequired() {
        SiteDefinition definition = definition(1, 2);
        SiteCandidate candidate = new SiteCandidate(new BlockPos(8, 64, 8), 0);
        SitePlacementPlan plan = plan(candidate.anchor());
        SitePreflight.Result stale = SitePreflight.check(definition, candidate,
                plan, new FixtureAccess(SiteAccess.Seam.GUARDED_NEW_CHUNKS, true));
        assertEquals(SitePreflight.Reason.EXISTING_CHUNK, stale.reason());
        SitePreflight.Result ready = SitePreflight.check(definition, candidate,
                plan, new FixtureAccess(SiteAccess.Seam.GUARDED_NEW_CHUNKS, false));
        assertEquals(SitePreflight.Status.READY, ready.status());
    }

    private static SiteDefinition definition(int version, int maxX) {
        return new SiteDefinition(ID, version, Level.OVERWORLD, Set.of(Biomes.PLAINS),
                SiteDefinition.Setting.SURFACE, 80, 40, 48, 100,
                2, 0, 0, 0, 0,
                List.of(new SiteVolume(SiteVolume.Use.BUILD,
                        -2, 0, -2, maxX, 2, 2)));
    }

    private static SitePlacementPlan plan(BlockPos pos) {
        return new SitePlacementPlan(ID, 1,
                Map.of(pos, new SitePlacementPlan.Write(Blocks.STONE_BRICKS.defaultBlockState(),
                        SitePlacementPlan.Replace.AIR, null)), List.of());
    }

    private record FixtureAccess(SiteAccess.Seam seam, boolean oldChunk) implements SiteAccess {
        @Override public ResourceKey<Level> dimension() { return Level.OVERWORLD; }
        @Override public int minY() { return -64; }
        @Override public int maxY() { return 320; }
        @Override public boolean canInspectChunk(ChunkPos chunk) { return true; }
        @Override public boolean isNewChunk(ChunkPos chunk) { return !oldChunk; }
        @Override public boolean canWrite(BlockPos pos) { return true; }
        @Override public Inspection protectedVolume(BoundingBox bounds) { return Inspection.CLEAR; }
        @Override public boolean biomeMatches(BlockPos pos, Set<ResourceKey<Biome>> allowed) {
            return allowed.contains(Biomes.PLAINS);
        }
        @Override public Column column(int x, int z, int anchorY, SiteDefinition definition) {
            return new Column(63, 0, 0, 0, 0);
        }
        @Override public BlockState state(BlockPos pos) { return Blocks.AIR.defaultBlockState(); }
        @Override public boolean hasBlockEntity(BlockPos pos) { return false; }
        @Override public boolean isNaturalTerrain(BlockState state) { return false; }
    }
}
