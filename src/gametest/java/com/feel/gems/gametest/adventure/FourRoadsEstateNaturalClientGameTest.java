package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.estate.EstateBlocks;
import com.feel.gems.adventure.estate.EstateSiteIntegrity;
import com.feel.gems.adventure.estate.FourRoadsEstateStructure;
import com.feel.gems.adventure.worldgen.site.GuardedNewChunkSiteAccess;
import com.feel.gems.adventure.exploration.ExplorationDeliveryAccount;
import com.feel.gems.adventure.exploration.ExplorationProgress;
import com.feel.gems.adventure.exploration.ExplorationState;
import com.feel.gems.state.GemsPersistentDataHolder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Opt-in 26.3 worldgen gate across independent seeds and all four estate exits. */
@SuppressWarnings("UnstableApiUsage")
public final class FourRoadsEstateNaturalClientGameTest implements FabricClientGameTest {
    private static final String[] SEEDS = {
            "FourRoadsEstate-Common-1", "FourRoadsEstate-Common-2",
            "FourRoadsEstate-Common-3"
    };
    private static final String[] REACH_SEEDS = {
            "FourRoadsEstate-Common-1", "FourRoadsEstate-Common-2",
            "FourRoadsEstate-Common-3", "FourRoadsEstate-Common-4",
            "FourRoadsEstate-Common-5", "FourRoadsEstate-Common-6"
    };
    private static final int SEARCH_CHUNKS = Math.min(512,
            Math.max(1, Integer.getInteger("gems.fourRoadsEstateSearchChunks", 512)));
    private static final int REACH_CHUNKS = Math.min(256,
            Math.max(64, Integer.getInteger("gems.fourRoadsEstateReachChunks", 64)));
    private static final int TREE_FREE_REACH_CHUNKS = Math.min(512,
            Math.max(64, Integer.getInteger("gems.fourRoadsEstateTreeFreeReachChunks", 512)));
    private static final String TARGET_SEED =
            System.getProperty("gems.fourRoadsEstateTargetSeed", "");
    private static final int TARGET_X = Integer.getInteger("gems.fourRoadsEstateTargetX", 0);
    private static final int TARGET_Z = Integer.getInteger("gems.fourRoadsEstateTargetZ", 0);
    private static final boolean FIXED_SAVED_GATE =
            Boolean.getBoolean("gems.fourRoadsEstateFixedSavedGate");
    private static final boolean COHORT_512 =
            Boolean.getBoolean("gems.fourRoadsEstateCohort512");
    private static final boolean TRACE_PLACEMENT_LATENCY =
            Boolean.getBoolean("gems.fourRoadsEstateTracePlacementLatency");
    private static final ResourceKey<Structure> ESTATE = ResourceKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath("gems", "four_roads_estate_v1"));
    private static final TagKey<Structure> TARGET = TagKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath("gems", "atlas_target/four_roads_estate_v1"));
    private static final ResourceKey<net.minecraft.world.level.biome.Biome> COMMONS =
            ResourceKey.create(Registries.BIOME,
                    Identifier.fromNamespaceAndPath("gems", "estate_commons"));
    private static final TagKey<net.minecraft.world.level.biome.Biome> ESTATE_BIOMES =
            TagKey.create(Registries.BIOME,
                    Identifier.fromNamespaceAndPath("gems", "has_structure/four_roads_estate_v1"));

    private record Survey(String seed, String result, BoundingBox bounds,
                          Map<Character, BlockPos> gates, BlockPos foyer,
                          BlockPos returnStone) {
        boolean ready() { return "READY".equals(result); }
    }

    private record ReferencedStart(Structure structure, Identifier id, long chunk) { }

    private record ChunkActivity(int pending, int loaded, int tick) { }

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!Boolean.getBoolean("gems.fourRoadsEstateGate")) return;
        if (COHORT_512) {
            runCohort512(context);
            return;
        }
        if (Boolean.getBoolean("gems.fourRoadsEstateReservationProbe")) {
            String target = System.getProperty(
                    "gems.fourRoadsEstateReservationTargetSeed", "");
            String[] probeSeeds = switch (target) {
                case "" -> new String[]{SEEDS[0], SEEDS[2]};
                case "Common-3" -> new String[]{SEEDS[2]};
                default -> throw new IllegalArgumentException(
                        "Unsupported recorded reservation target: " + target);
            };
            for (String seed : probeSeeds) {
                try (TestSingleplayerContext world = context.worldBuilder()
                        .setUseConsistentSettings(false)
                        .adjustSettings(settings -> {
                            settings.setSeed(seed);
                            settings.setGenerateStructures(true);
                        }).create()) {
                    world.getConnection().waitForChunksRender();
                    awaitReservationQuiescence(context, world, seed);
                    world.getServer().runOnServer(server ->
                            EstateReservationProbe.verify(server.overworld(), seed));
                }
            }
            return;
        }
        if (Boolean.getBoolean("gems.fourRoadsEstateTreeFreeReach")) {
            for (String seed : SEEDS) {
                try (TestSingleplayerContext world = context.worldBuilder()
                        .setUseConsistentSettings(false)
                        .adjustSettings(settings -> {
                            settings.setSeed(seed);
                            settings.setGenerateStructures(true);
                        }).create()) {
                    world.getConnection().waitForChunksRender();
                    world.getServer().runOnServer(server ->
                            treeFreeReach(server.overworld(), seed));
                }
            }
            return;
        }
        if (Boolean.getBoolean("gems.fourRoadsEstateBatchEquivalence")) {
            for (String seed : new String[]{SEEDS[0], SEEDS[2]}) {
                int x = seed.equals(SEEDS[0]) ? -1024 : 2816;
                int z = seed.equals(SEEDS[0]) ? 1536 : -1792;
                try (TestSingleplayerContext world = context.worldBuilder()
                        .setUseConsistentSettings(false)
                        .adjustSettings(settings -> {
                            settings.setSeed(seed);
                            settings.setGenerateStructures(true);
                        }).create()) {
                    world.getConnection().waitForChunksRender();
                    world.getServer().runOnServer(server ->
                            EstateBatchNoiseProbe.verify(server.overworld(), seed, x, z));
                }
            }
            return;
        }
        if (Boolean.getBoolean("gems.fourRoadsEstateExactTownCollision")) {
            for (String seed : REACH_SEEDS) {
                if (Boolean.getBoolean("gems.fourRoadsEstateExactRejectionOnly")
                        && !seed.endsWith("-1") && !seed.endsWith("-3")) continue;
                try (TestSingleplayerContext world = context.worldBuilder()
                        .setUseConsistentSettings(false)
                        .adjustSettings(settings -> {
                            settings.setSeed(seed);
                            settings.setGenerateStructures(true);
                        }).create()) {
                    world.getConnection().waitForChunksRender();
                    world.getServer().runOnServer(server ->
                            exactTownCollision(server.overworld(), seed));
                }
            }
            return;
        }
        if (Boolean.getBoolean("gems.fourRoadsEstateBiomeReachOnly")) {
            for (String seed : REACH_SEEDS) {
                try (TestSingleplayerContext world = context.worldBuilder()
                        .setUseConsistentSettings(false)
                        .adjustSettings(settings -> {
                            settings.setSeed(seed);
                            settings.setGenerateStructures(true);
                        }).create()) {
                    world.getConnection().waitForChunksRender();
                    world.getServer().runOnServer(server -> {
                        raisedReach(server.overworld(), seed, 18, new int[]{0, 16});
                        if (Boolean.getBoolean("gems.fourRoadsEstateCollisionReach"))
                            raisedReach(server.overworld(), seed, 16, new int[]{0});
                    });
                }
            }
            return;
        }
        int located = 0;
        int ready = 0;
        int exercised = 0;
        List<String> outcomes = new ArrayList<>();
        for (String seed : SEEDS) {
            if (!TARGET_SEED.isEmpty() && !TARGET_SEED.equals(seed)) continue;
            if (FIXED_SAVED_GATE && seed.equals(SEEDS[1])) {
                // The earlier six-seed, radius-256 upper-bound diagnostic
                // recorded zero 16-grid potentials for this fixed seed.
                // This run exercises actual saved starts at the other two
                // predeclared coordinates without repeating that scan.
                outcomes.add(seed + ":NO_RECORDED_16_GRID_POTENTIAL");
                continue;
            }
            Map<FourRoadsEstateStructure.Rejection, Long> before =
                    FourRoadsEstateStructure.generationDiagnostics();
            try (TestSingleplayerContext world = context.worldBuilder()
                    .setUseConsistentSettings(false)
                    .adjustSettings(settings -> {
                        settings.setSeed(seed);
                        settings.setGenerateStructures(true);
                    }).create()) {
                world.getConnection().waitForChunksRender();
                Survey survey = world.getServer().computeOnServer(server ->
                        survey(server.overworld(), seed));
                Map<FourRoadsEstateStructure.Rejection, Long> diagnostic =
                        diagnosticDelta(before, FourRoadsEstateStructure.generationDiagnostics());
                System.out.println("FOUR_ROADS_ESTATE_DIAGNOSTICS seed=" + seed
                        + " counts=" + diagnostic);
                outcomes.add(seed + ":" + survey.result());
                if (!"NO_START".equals(survey.result())) located++;
                if (!survey.ready()) continue;
                ready++;
                if (FIXED_SAVED_GATE && !world.getServer().computeOnServer(server ->
                        EstateLoadedRouteProbe.verify(server.overworld(),
                                savedEstateStart(server.overworld(), survey.bounds()),
                                survey.gates(), survey.foyer()))) {
                    throw new IllegalStateException("Estate loaded voxel route failed: " + seed);
                }
                if (FIXED_SAVED_GATE || exercised == 0) {
                    world.getServer().runOnServer(server ->
                            probeControls(server.overworld(),
                                    world.getConnection().getServerPlayer(), survey));
                    exercised++;
                }
            }
        }
        System.out.println((FIXED_SAVED_GATE ? "FOUR_ROADS_ESTATE_FIXED_SAVED"
                : "FOUR_ROADS_ESTATE_NATURAL") + " seeds=" + outcomes.size()
                + " searchChunks=" + SEARCH_CHUNKS
                + " located=" + located + " ready=" + ready
                + " controlProbes=" + exercised + " outcomes=" + outcomes);
        if (!TARGET_SEED.isEmpty()) {
            System.out.println("FOUR_ROADS_ESTATE_TARGET_PROBE seed=" + TARGET_SEED
                    + " origin=" + TARGET_X + "," + TARGET_Z
                    + " located=" + located + " ready=" + ready
                    + " controlProbes=" + exercised);
            return;
        }
        // One lucky site is insufficient evidence for an intentionally
        // discoverable estate across otherwise normal Overworld seeds.
        if (ready < 2 || located != ready
                || exercised != (FIXED_SAVED_GATE ? ready : 1)) {
            throw new IllegalStateException("Estate natural placement gate failed: " + outcomes);
        }
    }

    /** Fixed seeds and nearest-ring order; every selected site gets a saved audit. */
    private static void runCohort512(ClientGameTestContext context) {
        int ready = 0;
        List<String> outcomes = new ArrayList<>(SEEDS.length);
        for (String seed : SEEDS) {
            try (TestSingleplayerContext world = context.worldBuilder()
                    .setUseConsistentSettings(false)
                    .adjustSettings(settings -> {
                        settings.setSeed(seed);
                        settings.setGenerateStructures(true);
                    }).create()) {
                world.getConnection().waitForChunksRender();
                awaitReservationQuiescence(context, world, seed);
                List<ChunkPos> candidates = world.getServer().computeOnServer(server ->
                        EstateCohort512Probe.candidates(server.overworld()));
                int scanned = 0, valid = 0, non25 = 0, conflicts = 0;
                int unknown = 0, loaded = 0;
                ChunkPos selected = null;
                while (scanned < candidates.size()) {
                    int offset = scanned;
                    EstateCohort512Probe.BatchResult batch =
                            world.getServer().computeOnServer(server ->
                                    EstateCohort512Probe.inspect(server.overworld(),
                                            seed, candidates, offset));
                    scanned += batch.examined();
                    valid += batch.validStarts();
                    non25 += batch.non25();
                    conflicts += batch.conflict();
                    unknown += batch.unknown();
                    loaded += batch.loaded();
                    if (scanned % 128 < EstateCohort512Probe.batchSize()
                            || batch.selected() != null)
                        System.out.println("FOUR_ROADS_ESTATE_COHORT512_PROGRESS seed=" + seed
                                + " scanned=" + scanned + "/" + candidates.size()
                                + " valid=" + valid + " conflict=" + conflicts
                                + " unknown=" + unknown + " loaded=" + loaded
                                + " non25=" + non25
                                + " batchMs=" + batch.elapsedMillis());
                    if (batch.selected() != null) {
                        selected = batch.selected();
                        break;
                    }
                    context.waitTicks(1);
                }
                if (selected == null) {
                    outcomes.add(seed + ":NO_CLEAR_SITE");
                    continue;
                }
                ChunkPos chosen = selected;
                Survey survey = world.getServer().computeOnServer(server ->
                        survey(server.overworld(), seed, chosen));
                String outcome = seed + ":" + survey.result() + "@"
                        + chosen.x() + "," + chosen.z();
                outcomes.add(outcome);
                if (!survey.ready()) continue;
                boolean physical = world.getServer().computeOnServer(server ->
                        EstateLoadedRouteProbe.verify(server.overworld(),
                                savedEstateStart(server.overworld(), survey.bounds()),
                                survey.gates(), survey.foyer()));
                if (!physical)
                    throw new IllegalStateException("Estate cohort route failed: " + outcome);
                world.getServer().runOnServer(server ->
                        probeControls(server.overworld(),
                                world.getConnection().getServerPlayer(), survey));
                ready++;
                System.out.println("FOUR_ROADS_ESTATE_COHORT512_READY seed=" + seed
                        + " chunk=" + chosen.x() + "," + chosen.z()
                        + " pieces=25 seams=24 physicalRoutes=4 controls=4");
            }
        }
        System.out.println("FOUR_ROADS_ESTATE_COHORT512_SUMMARY seeds=3"
                + " radiusChunks=512 spacing=16 separation=15 ready=" + ready
                + " outcomes=" + outcomes);
        if (ready < 2)
            throw new IllegalStateException("Estate fixed cohort needs two saved READY sites: "
                    + outcomes);
    }

    /** Let startup chunk workers settle before direct, noise-only template reads. */
    private static void awaitReservationQuiescence(ClientGameTestContext context,
            TestSingleplayerContext world, String seed) {
        int previousLoaded = -1;
        int stable = 0;
        for (int sampleIndex = 1; sampleIndex <= 30; sampleIndex++) {
            context.waitTicks(20);
            ChunkActivity sample = world.getServer().computeOnServer(server -> {
                var source = server.overworld().getChunkSource();
                return new ChunkActivity(source.getPendingTasksCount(),
                        source.getLoadedChunksCount(), server.getTickCount());
            });
            stable = sample.pending() == 0 && sample.loaded() == previousLoaded
                    ? stable + 1 : 0;
            System.out.println("FOUR_ROADS_ESTATE_RESERVATION_QUIESCENCE seed=" + seed
                    + " sample=" + sampleIndex + " tick=" + sample.tick()
                    + " pending=" + sample.pending() + " loaded=" + sample.loaded()
                    + " stable=" + stable);
            if (stable >= 3) return;
            previousLoaded = sample.loaded();
        }
        throw new IllegalStateException("Estate reservation fixture inconclusive: "
                + "startup chunk work did not settle for " + seed);
    }

    private static Map<FourRoadsEstateStructure.Rejection, Long> diagnosticDelta(
            Map<FourRoadsEstateStructure.Rejection, Long> before,
            Map<FourRoadsEstateStructure.Rejection, Long> after) {
        Map<FourRoadsEstateStructure.Rejection, Long> delta =
                new EnumMap<>(FourRoadsEstateStructure.Rejection.class);
        for (FourRoadsEstateStructure.Rejection reason :
                FourRoadsEstateStructure.Rejection.values()) {
            delta.put(reason, after.get(reason) - before.get(reason));
        }
        return delta;
    }

    private static Survey survey(ServerLevel level, String seed) {
        return survey(level, seed, null);
    }

    private static Survey survey(ServerLevel level, String seed,
                                 ChunkPos cohortSite) {
        // A direct noise probe before the forced load warms templates/noise
        // and makes the worker-path latency measurement misleading.
        if (!TARGET_SEED.isEmpty() && !TRACE_PLACEMENT_LATENCY)
            targetNoise(level, seed);
        int[] fixed = cohortSite != null
                ? new int[]{cohortSite.getMinBlockX(), cohortSite.getMinBlockZ()}
                : FIXED_SAVED_GATE ? switch (seed) {
            case "FourRoadsEstate-Common-1" -> new int[]{-1024, 1536};
            case "FourRoadsEstate-Common-3" -> new int[]{2816, -1792};
            default -> null;
        } : null;
        boolean strictSavedGate = FIXED_SAVED_GATE || cohortSite != null;
        int targetX = fixed == null ? TARGET_X : fixed[0];
        int targetZ = fixed == null ? TARGET_Z : fixed[1];
        boolean targetChunk = fixed != null || !TARGET_SEED.isEmpty();
        BlockPos located;
        if (!targetChunk) {
            located = level.findNearestMapStructure(TARGET, new BlockPos(0, 80, 0),
                    SEARCH_CHUNKS, false);
        } else {
            Structure target = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                    .getValue(ESTATE);
            StructureSet set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)
                    .getValue(ResourceKey.create(Registries.STRUCTURE_SET,
                            Identifier.fromNamespaceAndPath("gems", "four_roads_estate_v1")));
            if (set != null && set.placement() instanceof RandomSpreadStructurePlacement spread) {
                var potential = spread.getPotentialStructureChunk(level.getSeed(),
                        targetX >> 4, targetZ >> 4);
                System.out.println("FOUR_ROADS_ESTATE_RUNTIME_PLACEMENT seed=" + seed
                        + " spacing=" + spread.spacing()
                        + " separation=" + spread.separation()
                        + " requestedChunk=" + (targetX >> 4) + "," + (targetZ >> 4)
                        + " potentialChunk=" + potential.x() + "," + potential.z());
                if (fixed != null && (spread.spacing() != 16
                        || spread.separation() != 15
                        || potential.x() != (targetX >> 4)
                        || potential.z() != (targetZ >> 4)))
                    throw new IllegalStateException("Fixed Estate site is off the staged lattice: "
                            + seed + " @ " + targetX + "," + targetZ);
            } else {
                System.out.println("FOUR_ROADS_ESTATE_RUNTIME_PLACEMENT seed=" + seed
                        + " set=" + (set != null) + " randomSpread=false");
            }
            var source = level.getChunkSource();
            int loadedBefore = source.getLoadedChunksCount();
            int pendingBefore = source.getPendingTasksCount();
            long forceStarted = System.nanoTime();
            var exactChunk = level.getChunk(targetX >> 4, targetZ >> 4);
            if (TRACE_PLACEMENT_LATENCY)
                System.out.println("FOUR_ROADS_ESTATE_FORCED_START seed=" + seed
                        + " chunk=" + exactChunk.getPos()
                        + " requestThread=" + Thread.currentThread().getName()
                        + " wallMs=" + (System.nanoTime() - forceStarted) / 1_000_000
                        + " pending=" + pendingBefore + "->" + source.getPendingTasksCount()
                        + " loaded=" + loadedBefore + "->" + source.getLoadedChunksCount());
            StructureStart exact = target == null ? null : level.structureManager()
                    .getStartForStructure(target, exactChunk);
            located = exact != null && exact.isValid()
                    ? new BlockPos(targetX, 80, targetZ) : null;
            System.out.println("FOUR_ROADS_ESTATE_EXACT_CHUNK seed=" + seed
                    + " chunk=" + (targetX >> 4) + "," + (targetZ >> 4)
                    + " savedStart=" + (exact != null && exact.isValid()));
        }
        if (located == null) {
            if (TARGET_SEED.isEmpty() && !strictSavedGate)
                raisedReach(level, seed, 18, new int[]{0, 16});
            return new Survey(seed, "NO_START", null, Map.of(), null, null);
        }
        Structure estate = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValue(ESTATE);
        if (estate == null) return new Survey(seed, "NO_REGISTRY", null, Map.of(), null, null);
        StructureStart start = null;
        int centerX = located.getX() >> 4;
        int centerZ = located.getZ() >> 4;
        for (int radius = 0; radius <= 6 && start == null; radius++) {
            for (int dx = -radius; dx <= radius && start == null; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    var chunk = level.getChunk(centerX + dx, centerZ + dz);
                    StructureStart candidate = level.structureManager()
                            .getStartForStructure(estate, chunk);
                    if (candidate != null && candidate.isValid()) {
                        start = candidate;
                        break;
                    }
                }
            }
        }
        if (start == null) return new Survey(seed, "MISSING_SAVED_START", null, Map.of(), null, null);
        BoundingBox box = start.getBoundingBox();
        int bodyLoadedBefore = level.getChunkSource().getLoadedChunksCount();
        long bodyStarted = System.nanoTime();
        for (int cx = (box.minX() - 12) >> 4; cx <= (box.maxX() + 12) >> 4; cx++) {
            for (int cz = (box.minZ() - 12) >> 4; cz <= (box.maxZ() + 12) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        if (TRACE_PLACEMENT_LATENCY)
            System.out.println("FOUR_ROADS_ESTATE_FORCED_BODY seed=" + seed
                    + " bounds=" + box + " requestThread="
                    + Thread.currentThread().getName()
                    + " wallMs=" + (System.nanoTime() - bodyStarted) / 1_000_000
                    + " loaded=" + bodyLoadedBefore + "->"
                    + level.getChunkSource().getLoadedChunksCount());
        boolean referencedConflict = strictSavedGate
                && inspectReferencedVanillaStarts(level, seed, start);
        if (strictSavedGate) traceFirstApproachCells(level, seed, start);
        EstateSiteIntegrity.Result integrity = EstateSiteIntegrity.inspectLoaded(level, start);
        if (strictSavedGate && integrity == EstateSiteIntegrity.Result.CANOPY_INTRUSION)
            inspectCanopyIntrusion(level, seed, start);
        if (integrity != EstateSiteIntegrity.Result.READY) {
            return new Survey(seed, integrity.name(), box, Map.of(), null, null);
        }
        if (referencedConflict) {
            return new Survey(seed, "REFERENCED_STRUCTURE_CONFLICT",
                    box, Map.of(), null, null);
        }
        Map<Character, List<BlockPos>> candidates = new HashMap<>();
        BlockPos foyer = null;
        BlockPos returnStone = null;
        for (var piece : start.getPieces()) {
            BoundingBox part = piece.getBoundingBox();
            int y = part.minY() + 4;
            for (int x = part.minX(); x <= part.maxX(); x++) {
                for (int z = part.minZ(); z <= part.maxZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Block block = level.getBlockState(pos).getBlock();
                    char wing = wing(block);
                    if (wing != '?') candidates.computeIfAbsent(wing, ignored ->
                            new ArrayList<>()).add(pos);
                    else if (block == EstateBlocks.FOYER) foyer = pos;
                    else if (block == EstateBlocks.RETURN) returnStone = pos;
                }
            }
        }
        if (foyer == null || returnStone == null || candidates.size() != 4) {
            return new Survey(seed, "MISSING_CONTROLS", box, Map.of(), foyer, returnStone);
        }
        Map<Character, BlockPos> gates = new HashMap<>();
        for (char wing : new char[]{'N', 'E', 'S', 'W'}) {
            List<BlockPos> band = candidates.get(wing);
            if (band == null || band.size() != 3) {
                return new Survey(seed, "BAD_GATE_BAND", box, Map.of(), foyer, returnStone);
            }
            gates.put(wing, band.get(1));
        }
        return new Survey(seed, "READY", box, Map.copyOf(gates), foyer, returnStone);
    }

    /** The saved gate apron has a roof above its authored grade floor. */
    private static void traceFirstApproachCells(ServerLevel level, String seed,
                                                StructureStart start) {
        BoundingBox body = start.getBoundingBox();
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox box = piece.getBoundingBox();
            int y = box.minY() + 4;
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    char wing = wing(level.getBlockState(new BlockPos(x, y, z)).getBlock());
                    if (wing == '?') continue;
                    int west = x - body.minX();
                    int east = body.maxX() - x;
                    int north = z - body.minZ();
                    int south = body.maxZ() - z;
                    int distance = Math.min(Math.min(west, east), Math.min(north, south));
                    int dx = distance == west ? -1 : distance == east ? 1 : 0;
                    int dz = dx != 0 ? 0 : distance == north ? -1 : 1;
                    int outerX = x + dx * (distance + 1);
                    int outerZ = z + dz * (distance + 1);
                    LevelChunk outer = level.getChunkSource().getChunkNow(outerX >> 4,
                            outerZ >> 4);
                    if (outer == null) continue;
                    int outerY = outer.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            outerX & 15, outerZ & 15);
                    int authoredY = y + Math.round((outerY - y) / (float) distance);
                    int firstX = x + dx + dz * -1;
                    int firstZ = z + dz + dx * -1;
                    LevelChunk first = level.getChunkSource().getChunkNow(firstX >> 4,
                            firstZ >> 4);
                    if (first == null) continue;
                    int heightmapY = first.getHeight(
                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            firstX & 15, firstZ & 15);
                    BlockPos floor = new BlockPos(firstX, authoredY, firstZ);
                    System.out.println("FOUR_ROADS_ESTATE_APPROACH_FIRST seed=" + seed
                            + " wing=" + wing + " pos=" + floor
                            + " markerY=" + y + " outerY=" + outerY
                            + " authored=" + level.getBlockState(floor).getBlock()
                            + " head1=" + level.getBlockState(floor.above()).getBlock()
                            + " head2=" + level.getBlockState(floor.above(2)).getBlock()
                            + " heightmapY=" + heightmapY);
                }
            }
        }
    }

    private static StructureStart savedEstateStart(ServerLevel level, BoundingBox bounds) {
        Structure estate = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValue(ESTATE);
        if (estate == null) return null;
        for (int cx = bounds.minX() >> 4; cx <= bounds.maxX() >> 4; cx++) {
            for (int cz = bounds.minZ() >> 4; cz <= bounds.maxZ() >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                StructureStart start = chunk.getStartForStructure(estate);
                if (start != null && start.isValid()) {
                    BoundingBox candidate = start.getBoundingBox();
                    if (candidate.minX() == bounds.minX()
                            && candidate.minZ() == bounds.minZ()
                            && candidate.maxX() == bounds.maxX()
                            && candidate.maxZ() == bounds.maxZ()) return start;
                }
            }
        }
        return null;
    }

    /**
     * GameTest only: resolve the specific vanilla start chunks referenced by
     * the loaded estate, then report their actual piece intersections. The
     * production loaded audit still fails closed on every unavailable start.
     */
    private static boolean inspectReferencedVanillaStarts(ServerLevel level,
                                                            String seed,
                                                            StructureStart estate) {
        BoundingBox box = estate.getBoundingBox();
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Set<ReferencedStart> references = new HashSet<>();
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) throw new IllegalStateException(
                        "Estate body chunk unavailable after loading: " + cx + "," + cz);
                for (var entry : chunk.getAllReferences().entrySet()) {
                    Identifier id = registry.getKey(entry.getKey());
                    if (id == null) throw new IllegalStateException(
                            "Unregistered structure reference in " + chunk.getPos());
                    if (!id.getNamespace().equals("minecraft")) continue;
                    for (long packed : entry.getValue()) {
                        references.add(new ReferencedStart(entry.getKey(), id, packed));
                    }
                }
            }
        }
        if (references.size() > 64) throw new IllegalStateException(
                "Estate referenced more than 64 vanilla starts: " + references.size());
        List<BoundingBox> arrivals = arrivalEnvelopes(level, estate);
        int loaded = 0, starts = 0, pieceHits = 0, arrivalHits = 0;
        boolean conflict = false;
        for (ReferencedStart reference : references.stream()
                .sorted(Comparator.comparing((ReferencedStart r) -> r.id().toString())
                        .thenComparingInt(r -> ChunkPos.getX(r.chunk()))
                        .thenComparingInt(r -> ChunkPos.getZ(r.chunk())))
                .toList()) {
            int cx = ChunkPos.getX(reference.chunk());
            int cz = ChunkPos.getZ(reference.chunk());
            if (cx < (box.minX() >> 4) - 32 || cx > (box.maxX() >> 4) + 32
                    || cz < (box.minZ() >> 4) - 32 || cz > (box.maxZ() >> 4) + 32)
                throw new IllegalStateException("Estate reference exceeds bounded diagnostic: "
                        + reference.id() + " @ " + cx + "," + cz);
            boolean wasLoaded = level.getChunkSource().getChunkNow(cx, cz) != null;
            LevelChunk chunk = level.getChunk(cx, cz);
            if (!wasLoaded) loaded++;
            StructureStart other = chunk.getStartForStructure(reference.structure());
            if (other == null || !other.isValid())
                throw new IllegalStateException("Referenced vanilla start missing: "
                        + reference.id() + " @ " + cx + "," + cz);
            starts++;
            int localPieceHits = 0, localArrivalHits = 0;
            for (StructurePiece foreign : other.getPieces()) {
                BoundingBox foreignBox = foreign.getBoundingBox();
                for (StructurePiece own : estate.getPieces()) {
                    if (foreignBox.intersects(own.getBoundingBox())) {
                        localPieceHits++;
                        break;
                    }
                }
                for (BoundingBox arrival : arrivals) {
                    if (foreignBox.intersects(arrival)) {
                        localArrivalHits++;
                        break;
                    }
                }
            }
            pieceHits += localPieceHits;
            arrivalHits += localArrivalHits;
            conflict |= localPieceHits > 0 || localArrivalHits > 0;
            System.out.println("FOUR_ROADS_ESTATE_REFERENCED_START seed=" + seed
                    + " structure=" + reference.id() + " chunk=" + cx + "," + cz
                    + " newlyLoaded=" + !wasLoaded
                    + " pieces=" + other.getPieces().size()
                    + " estatePieceHits=" + localPieceHits
                    + " arrivalHits=" + localArrivalHits
                    + " bounds=" + other.getBoundingBox());
        }
        System.out.println("FOUR_ROADS_ESTATE_REFERENCE_AUDIT seed=" + seed
                + " referenced=" + references.size() + " newlyLoaded=" + loaded
                + " validStarts=" + starts + " estatePieceHits=" + pieceHits
                + " arrivalHits=" + arrivalHits + " conflict=" + conflict);
        return conflict;
    }

    private static List<BoundingBox> arrivalEnvelopes(ServerLevel level,
                                                       StructureStart estate) {
        List<BoundingBox> result = new ArrayList<>(4 * 3 * 8);
        int gates = 0;
        for (StructurePiece piece : estate.getPieces()) {
            if (!(piece instanceof PoolElementStructurePiece pooled)
                    || !(pooled.getElement() instanceof SinglePoolElement single)) continue;
            Identifier id = single.getTemplateLocation();
            if (!id.getNamespace().equals("gems")) continue;
            int x, z;
            Direction local;
            switch (id.getPath()) {
                case "four_roads_estate_v1_0_2" -> { x = 19; z = 5; local = Direction.NORTH; }
                case "four_roads_estate_v1_2_4" -> { x = 40; z = 19; local = Direction.EAST; }
                case "four_roads_estate_v1_4_2" -> { x = 19; z = 40; local = Direction.SOUTH; }
                case "four_roads_estate_v1_2_0" -> { x = 5; z = 19; local = Direction.WEST; }
                default -> { continue; }
            }
            BlockPos marker = StructureTemplate.transform(new BlockPos(x, 4, z),
                    Mirror.NONE, pooled.getRotation(), BlockPos.ZERO)
                    .offset(pooled.getPosition());
            Direction outward = pooled.getRotation().rotate(local);
            BoundingBox part = piece.getBoundingBox();
            int distance = switch (outward) {
                case NORTH -> marker.getZ() - part.minZ();
                case EAST -> part.maxX() - marker.getX();
                case SOUTH -> part.maxZ() - marker.getZ();
                case WEST -> marker.getX() - part.minX();
                default -> 0;
            };
            if (distance < 5 || distance > 6)
                throw new IllegalStateException("Estate arrival marker offset invalid: " + id);
            gates++;
            for (int step = distance + 1; step <= distance + 8; step++) {
                for (int lane = -1; lane <= 1; lane++) {
                    BlockPos pos = marker.relative(outward, step)
                            .relative(outward.getClockWise(), lane);
                    LevelChunk chunk = level.getChunkSource().getChunkNow(
                            pos.getX() >> 4, pos.getZ() >> 4);
                    if (chunk == null) throw new IllegalStateException(
                            "Estate arrival chunk unavailable: " + pos);
                    int floorY = chunk.getHeight(
                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            pos.getX() & 15, pos.getZ() & 15);
                    result.add(new BoundingBox(pos.getX(), floorY, pos.getZ(),
                            pos.getX(), floorY + 2, pos.getZ()));
                }
            }
        }
        if (gates != 4 || result.size() != 96)
            throw new IllegalStateException("Estate did not yield four arrival envelopes: "
                    + gates + "/" + result.size());
        return result;
    }

    /** Diagnostic of the exact loaded canopy predicate and its chunk guard. */
    private static void inspectCanopyIntrusion(ServerLevel level, String seed,
                                               StructureStart estate) {
        Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValue(ESTATE);
        GuardedNewChunkSiteAccess guard = new GuardedNewChunkSiteAccess(level,
                Set.of(structure));
        int intrusions = 0;
        Map<ChunkPos, Integer> byChunk = new HashMap<>();
        for (StructurePiece piece : estate.getPieces()) {
            BoundingBox box = piece.getBoundingBox();
            if (box.getYSpan() != 18) continue;
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    for (int y = box.minY() + 5; y <= box.minY() + 17; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        var state = level.getBlockState(pos);
                        if (!state.is(BlockTags.LOGS)
                                && !(state.getBlock() instanceof LeavesBlock
                                        && !state.getValue(LeavesBlock.PERSISTENT))) continue;
                        intrusions++;
                        ChunkPos chunkPos = new ChunkPos(x >> 4, z >> 4);
                        byChunk.merge(chunkPos, 1, Integer::sum);
                        if (intrusions > 24) continue;
                        LevelChunk chunk = level.getChunkSource().getChunkNow(
                                chunkPos.x(), chunkPos.z());
                        int foreignRefs = 0;
                        StringBuilder refNames = new StringBuilder();
                        for (var entry : chunk.getAllReferences().entrySet()) {
                            if (entry.getKey() == structure || entry.getValue().isEmpty()) continue;
                            foreignRefs += entry.getValue().size();
                            if (!refNames.isEmpty()) refNames.append(',');
                            refNames.append(level.registryAccess()
                                    .lookupOrThrow(Registries.STRUCTURE)
                                    .getKey(entry.getKey()));
                        }
                        boolean deck = true;
                        for (int floorY = box.minY(); floorY <= box.minY() + 3; floorY++)
                            deck &= chunk.getBlockState(new BlockPos(x, floorY, z))
                                    .is(net.minecraft.world.level.block.Blocks.STONE_BRICKS);
                        System.out.println("FOUR_ROADS_ESTATE_CANOPY_CELL seed=" + seed
                                + " pos=" + pos + " state="
                                + BuiltInRegistries.BLOCK.getKey(state.getBlock())
                                + " chunk=" + chunkPos
                                + " status=" + chunk.getPersistedStatus()
                                + " new=" + guard.isNewChunk(chunkPos)
                                + " foreignRefs=" + foreignRefs
                                + " refs=" + refNames + " deck=" + deck);
                    }
                }
            }
        }
        System.out.println("FOUR_ROADS_ESTATE_CANOPY_SUMMARY seed=" + seed
                + " intrusions=" + intrusions + " chunks=" + byChunk);
    }

    private static void targetNoise(ServerLevel level, String seed) {
        var source = level.getChunkSource();
        var generator = source.getGenerator();
        var resolver = generator.getBiomeSource().createUncachedResolver(source.randomState());
        for (int[] point : new int[][]{{TARGET_X, TARGET_Z},
                {TARGET_X & ~15, TARGET_Z & ~15}}) {
            int x = point[0];
            int z = point[1];
            int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                    level, source.randomState()) - 1;
            var at68 = resolver.getNoiseBiome(x >> 2, 17, z >> 2);
            var atSurface = resolver.getNoiseBiome(x >> 2, surface >> 2, z >> 2);
            int taggedLevels = 0;
            for (int qy = -16; qy <= 80; qy++) {
                if (resolver.getNoiseBiome(x >> 2, qy, z >> 2).is(ESTATE_BIOMES)) {
                    taggedLevels++;
                }
            }
            System.out.println("FOUR_ROADS_ESTATE_TARGET_NOISE seed=" + seed
                    + " x=" + x + " z=" + z + " surface=" + surface
                    + " y68Eligible=" + estateEligible(at68)
                    + " y68Tagged=" + at68.is(ESTATE_BIOMES)
                    + " surfaceEligible=" + estateEligible(atSurface)
                    + " surfaceTagged=" + atSurface.is(ESTATE_BIOMES)
                    + " taggedVerticalQuartLevels=" + taggedLevels);
        }
    }

    /** Candidate-only biome reach; no chunks, starts, or blocks are generated. */
    private static void treeFreeReach(ServerLevel level, String seed) {
        var source = level.getChunkSource();
        var resolver = source.getGenerator().getBiomeSource()
                .createUncachedResolver(source.randomState());
        int radiusCells = TREE_FREE_REACH_CHUNKS / 16;
        int candidates = 0, centerBiome = 0, y80Envelope = 0;
        int surfaceEnvelope = 0, dryApproaches = 0, raisedPotential = 0;
        Map<String, Integer> outcomes = new java.util.TreeMap<>();
        for (int rx = -radiusCells; rx <= radiusCells; rx++) {
            for (int rz = -radiusCells; rz <= radiusCells; rz++) {
                // The staged spacing=16,separation=15 set has exactly one
                // potential start at each cell's minimum chunk (phase zero).
                int x = rx * 256, z = rz * 256;
                candidates++;
                boolean loaded = source.hasChunk(x >> 4, z >> 4);
                int centerY = surface(level, x, z);
                if (centerY >= 55 && centerY <= 145
                        && estateEligible(resolver.getNoiseBiome(
                                x >> 2, centerY >> 2, z >> 2))) {
                    centerBiome++;
                    if (treeFreeEnvelope(level, resolver, x, z, false)) {
                        y80Envelope++;
                        if (treeFreeEnvelope(level, resolver, x, z, true)) {
                            surfaceEnvelope++;
                            if (naturalApproaches(level, x, z)) {
                                dryApproaches++;
                                PhaseEstimate estimate = raisedEstimate(level, x, z);
                                outcomes.merge(estimate.status(), 1, Integer::sum);
                                if (estimate.potential()) raisedPotential++;
                                System.out.println("FOUR_ROADS_ESTATE_TREE_FREE_SITE seed=" + seed
                                        + " x=" + x + " z=" + z
                                        + " status=" + estimate.status()
                                        + " sampledLow=" + estimate.low()
                                        + " sampledHigh=" + estimate.high()
                                        + " rotation=" + estimate.rotation());
                            }
                        }
                    }
                }
                if (source.hasChunk(x >> 4, z >> 4) != loaded)
                    throw new IllegalStateException("Tree-free noise probe loaded candidate chunk "
                            + x + "," + z);
            }
        }
        System.out.println("FOUR_ROADS_ESTATE_TREE_FREE_REACH seed=" + seed
                + " radiusChunks=" + TREE_FREE_REACH_CHUNKS
                + " spacingChunks=16 candidates=" + candidates
                + " centerBiome=" + centerBiome
                + " y80Envelope=" + y80Envelope
                + " surfaceEnvelope=" + surfaceEnvelope
                + " dryApproaches=" + dryApproaches
                + " raisedUpperBound=" + raisedPotential
                + " outcomes=" + outcomes);
    }

    /** The 235-block body plus twelve blocks covers all four 3x8 arrivals. */
    private static boolean treeFreeEnvelope(ServerLevel level,
            net.minecraft.world.level.biome.BiomeResolver resolver,
            int centerX, int centerZ, boolean atSurface) {
        int minQX = Math.floorDiv(centerX - 129, 4);
        int maxQX = Math.floorDiv(centerX + 129, 4);
        int minQZ = Math.floorDiv(centerZ - 129, 4);
        int maxQZ = Math.floorDiv(centerZ + 129, 4);
        // Reject broad neighboring tree biomes before the full quart walk.
        int midQX = Math.floorDiv(centerX, 4);
        int midQZ = Math.floorDiv(centerZ, 4);
        for (int qx : new int[]{minQX, midQX, maxQX}) {
            for (int qz : new int[]{minQZ, midQZ, maxQZ}) {
                if (!treeFreeAt(level, resolver, qx, qz, atSurface)) return false;
            }
        }
        for (int qx = minQX; qx <= maxQX; qx++) {
            for (int qz = minQZ; qz <= maxQZ; qz++) {
                if (!treeFreeAt(level, resolver, qx, qz, atSurface)) return false;
            }
        }
        return true;
    }

    private static boolean treeFreeAt(ServerLevel level,
            net.minecraft.world.level.biome.BiomeResolver resolver,
            int qx, int qz, boolean atSurface) {
        int qy = atSurface ? surface(level, qx << 2, qz << 2) >> 2 : 20;
        return treeFreeBiome(resolver.getNoiseBiome(qx, qy, qz));
    }

    /** Candidate whitelist: no tree feature sources within canopy reach. */
    private static boolean treeFreeBiome(Holder<net.minecraft.world.level.biome.Biome> biome) {
        return biome.is(COMMONS) || biome.is(Biomes.DESERT)
                || biome.is(Biomes.BADLANDS) || biome.is(Biomes.ERODED_BADLANDS)
                || biome.is(Biomes.BEACH) || biome.is(Biomes.SNOWY_BEACH)
                || biome.is(Biomes.STONY_SHORE);
    }

    /** Pier-based noise reach check; never generates a chunk or changes placement. */
    private static void raisedReach(ServerLevel level, String seed,
                                    int spacingChunks, int[] phases) {
        var source = level.getChunkSource();
        var resolver = source.getGenerator().getBiomeSource()
                .createUncachedResolver(source.randomState());
        int candidates = 0;
        int centerBiome = 0;
        int naturalApproaches = 0;
        int potential = 0;
        int potentialWithoutTown = 0;
        Map<String, Integer> outcomes = new java.util.TreeMap<>();
        String firstPotential = "none";
        int radius = REACH_CHUNKS * 16;
        int cellBlocks = spacingChunks * 16;
        int cellRadius = Math.min(32, (radius + cellBlocks - 1) / cellBlocks);
        // A different site contract from the old full-biome slab scan:
        // center biome, four dry exits, cut/water-clearance over the body,
        // and support only at authored two-by-two footings and stair bents.
        // Four 0/16 phases of the 18-grid are hypothetical. The 16-grid
        // phase-zero comparison is also diagnostic; no structure set changes.
        for (int rx = -cellRadius; rx <= cellRadius; rx++) {
            for (int rz = -cellRadius; rz <= cellRadius; rz++) {
                for (int ox : phases) {
                    for (int oz : phases) {
                        int x = rx * cellBlocks + ox;
                        int z = rz * cellBlocks + oz;
                        if (Math.abs(x) > radius || Math.abs(z) > radius) continue;
                        candidates++;
                        int centerY = surface(level, x, z);
                        if (centerY < 55 || centerY > 145
                                || !estateEligible(resolver.getNoiseBiome(
                                        x >> 2, centerY >> 2, z >> 2))) continue;
                        centerBiome++;
                        if (!naturalApproaches(level, x, z)) continue;
                        naturalApproaches++;
                        PhaseEstimate estimate = raisedEstimate(level, x, z);
                        outcomes.merge(estimate.status(), 1, Integer::sum);
                        if (estimate.potential() && "none".equals(firstPotential))
                            firstPotential = x + "," + z + "@" + estimate.rotation();
                        if (estimate.potential()) {
                            potential++;
                            if (Boolean.getBoolean("gems.fourRoadsEstateCollisionReach")) {
                                TownRisk town = townRisk(level, resolver, x, z);
                                if (town.eligible() == 0) potentialWithoutTown++;
                                System.out.println("FOUR_ROADS_ESTATE_TOWN_RISK seed=" + seed
                                        + " spacingChunks=" + spacingChunks
                                        + " x=" + x + " z=" + z
                                        + " candidateStarts=" + town.candidates()
                                        + " eligibleStarts=" + town.eligible()
                                        + " nearby=" + town.nearby());
                            }
                        }
                        System.out.println("FOUR_ROADS_ESTATE_PHASE_SITE seed=" + seed
                                + " spacingChunks=" + spacingChunks
                                + " x=" + x + " z=" + z + " phase=" + ox + "," + oz
                                + " status=" + estimate.status()
                                + " sampledLow=" + estimate.low()
                                + " sampledHigh=" + estimate.high()
                                + " maxLocalRelief=" + estimate.maxLocalRelief()
                                + " rotation=" + estimate.rotation()
                                + " selectedBase=" + estimate.selectedBase());
                    }
                }
            }
        }
        System.out.println("FOUR_ROADS_ESTATE_RAISED_REACH seed=" + seed
                + " radiusChunks=" + REACH_CHUNKS
                + " spacingChunks=" + spacingChunks
                + " hypotheticalPhases=" + (phases.length * phases.length)
                + " candidates=" + candidates
                + " centerBiome=" + centerBiome + " dryApproaches=" + naturalApproaches
                + " potential=" + potential
                + " potentialWithoutEligibleTown=" + potentialWithoutTown
                + " outcomes=" + outcomes + " firstPotential=" + firstPotential);
    }

    private record TownRisk(int candidates, int eligible, List<String> nearby) { }

    private record ExactTownRisk(int candidateChunks, int generatedStarts,
                                 int intersectingPieces, int envelopeStarts,
                                 Set<String> overlappingStarts) { }

    /** Recorded noise potentials only; this mode never searches or places blocks. */
    private static int[][] recordedCollisionSites(String seed) {
        return switch (seed) {
            case "FourRoadsEstate-Common-1" -> new int[][]{
                    {18, -1728, -2592}, {18, -1712, -2592},
                    {18, -1712, -2576}, {18, -1152, -848},
                    {18, 1744, 2032}, {18, 2032, 1728},
                    {18, 2304, 1728}, {16, -1024, 1536},
                    {16, 2048, 1792}};
            case "FourRoadsEstate-Common-2" -> new int[][]{
                    {18, -560, 2304}, {18, -560, 2320}};
            case "FourRoadsEstate-Common-3" -> new int[][]{
                    {16, 2816, -1792}};
            case "FourRoadsEstate-Common-4" -> new int[][]{
                    {16, -3584, 3328}};
            case "FourRoadsEstate-Common-5" -> new int[][]{
                    {18, 3744, -576}, {16, -2304, -3840}};
            case "FourRoadsEstate-Common-6" -> new int[][]{
                    {18, -3168, 4032}, {18, -2592, -1728},
                    {18, -2592, -1712}, {18, -1424, -1440},
                    {18, 1152, -2880}};
            default -> throw new IllegalArgumentException(seed);
        };
    }

    private static void exactTownCollision(ServerLevel level, String seed) {
        Holder<Structure> estate = level.registryAccess()
                .lookupOrThrow(Registries.STRUCTURE).getOrThrow(ESTATE);
        int[] valid = new int[2];
        int[] pieceClear = new int[2];
        int[] envelopeClear = new int[2];
        int[] invalid = new int[2];
        for (int[] site : recordedCollisionSites(seed)) {
            int spacing = site[0], x = site[1], z = site[2];
            if (Boolean.getBoolean("gems.fourRoadsEstateExactRejectionOnly")
                    && !(seed.endsWith("-1") && x == -1024 && z == 1536)
                    && !(seed.endsWith("-3") && x == 2816 && z == -1792)) continue;
            int index = spacing == 16 ? 1 : 0;
            ChunkPos chunk = new ChunkPos(x >> 4, z >> 4);
            boolean wasLoaded = level.getChunkSource().hasChunk(chunk.x(), chunk.z());
            Map<FourRoadsEstateStructure.Rejection, Long> before =
                    FourRoadsEstateStructure.generationDiagnostics();
            long estateStarted = System.nanoTime();
            StructureStart start = generateNoiseStart(level, estate, chunk);
            long estateMs = (System.nanoTime() - estateStarted) / 1_000_000;
            if (Boolean.getBoolean("gems.fourRoadsEstateExactRejectionOnly"))
                verifyNoiseColumnSurface(level, seed, x, z);
            Map<FourRoadsEstateStructure.Rejection, Long> after =
                    FourRoadsEstateStructure.generationDiagnostics();
            Map<FourRoadsEstateStructure.Rejection, Long> reasons =
                    new EnumMap<>(FourRoadsEstateStructure.Rejection.class);
            for (var reason : FourRoadsEstateStructure.Rejection.values()) {
                long delta = after.getOrDefault(reason, 0L)
                        - before.getOrDefault(reason, 0L);
                if (delta > 0) reasons.put(reason, delta);
            }
            if (level.getChunkSource().hasChunk(chunk.x(), chunk.z()) != wasLoaded)
                throw new IllegalStateException("Estate noise probe loaded candidate chunk " + chunk);
            if (!start.isValid() || start.getPieces().size() != 25) {
                invalid[index]++;
                System.out.println("FOUR_ROADS_ESTATE_EXACT_TOWN seed=" + seed
                        + " spacingChunks=" + spacing + " x=" + x + " z=" + z
                        + " estatePieces=" + start.getPieces().size()
                        + " status=INVALID_ESTATE reasons=" + reasons
                        + " estateMs=" + estateMs);
                if (seed.endsWith("-1") && x == -1728 && z == -2592)
                    throw new IllegalStateException("Saved Common-1 Estate sentinel did not regenerate");
                continue;
            }
            valid[index]++;
            long townStarted = System.nanoTime();
            ExactTownRisk risk = exactTownRisk(level, start, x, z);
            long townMs = (System.nanoTime() - townStarted) / 1_000_000;
            if (risk.intersectingPieces() == 0) pieceClear[index]++;
            if (risk.envelopeStarts() == 0) envelopeClear[index]++;
            System.out.println("FOUR_ROADS_ESTATE_EXACT_TOWN seed=" + seed
                    + " spacingChunks=" + spacing + " x=" + x + " z=" + z
                    + " estatePieces=" + start.getPieces().size()
                    + " candidateTownChunks=" + risk.candidateChunks()
                    + " generatedTownStarts=" + risk.generatedStarts()
                    + " intersectingTownPieces=" + risk.intersectingPieces()
                    + " envelopeTownStarts=" + risk.envelopeStarts()
                    + " overlappingStarts=" + risk.overlappingStarts()
                    + " estateMs=" + estateMs + " townMs=" + townMs);
            if (seed.endsWith("-1") && x == -1728 && z == -2592) {
                Set<String> expected = Set.of(
                        "second_chance_town_themed@-1872,-2656",
                        "second_chance_town_themed@-1904,-2432",
                        "second_chance_town_themed@-1648,-2624");
                if (!risk.overlappingStarts().equals(expected)
                        || risk.intersectingPieces() != 14)
                    throw new IllegalStateException("Saved Common-1 collision sentinel mismatch: "
                            + risk);
            }
        }
        System.out.println("FOUR_ROADS_ESTATE_EXACT_TOWN_SUMMARY seed=" + seed
                + " spacing18_valid=" + valid[0] + " spacing18_pieceClear=" + pieceClear[0]
                + " spacing18_envelopeClear=" + envelopeClear[0]
                + " spacing18_invalid=" + invalid[0]
                + " spacing16_valid=" + valid[1] + " spacing16_pieceClear=" + pieceClear[1]
                + " spacing16_envelopeClear=" + envelopeClear[1]
                + " spacing16_invalid=" + invalid[1]);
    }

    private static StructureStart generateNoiseStart(ServerLevel level,
            Holder<Structure> holder, ChunkPos chunk) {
        var source = level.getChunkSource();
        var randomState = source.randomState();
        return holder.value().generate(holder, level.dimension(),
                level.registryAccess(), source.getGenerator(),
                source.getGenerator().getBiomeSource(),
                randomState.createClimateSampler(SamplerContext.EMPTY_UNCACHED),
                randomState, level.getServer().getStructureTemplateManager(),
                level.getSeed(), chunk, 0, level, holder.value().biomes()::contains);
    }

    /** Assert the single-column replacement matches 26.3's heightmap result. */
    private static void verifyNoiseColumnSurface(ServerLevel level,
            String seed, int centerX, int centerZ) {
        var source = level.getChunkSource();
        var generator = source.getGenerator();
        int checked = 0;
        for (int dx = -117; dx <= 117; dx += 23) {
            for (int dz = -117; dz <= 117; dz += 23) {
                int x = centerX + dx, z = centerZ + dz;
                NoiseColumn column = generator.getBaseColumn(x, z, level,
                        source.randomState());
                int columnTop = level.getMinY() - 1;
                for (int y = level.getMaxY() - 1; y >= level.getMinY(); y--) {
                    if (Heightmap.Types.WORLD_SURFACE_WG.isOpaque()
                            .test(column.getBlock(y))) {
                        columnTop = y;
                        break;
                    }
                }
                int heightmapTop = generator.getBaseHeight(x, z,
                        Heightmap.Types.WORLD_SURFACE_WG, level,
                        source.randomState()) - 1;
                if (columnTop != heightmapTop)
                    throw new IllegalStateException("Estate noise-column height mismatch "
                            + seed + " @ " + x + "," + z + ": "
                            + columnTop + " vs " + heightmapTop);
                checked++;
            }
        }
        System.out.println("FOUR_ROADS_ESTATE_COLUMN_EQUIVALENCE seed=" + seed
                + " x=" + centerX + " z=" + centerZ + " matched=" + checked);
    }

    private static ExactTownRisk exactTownRisk(ServerLevel level,
            StructureStart estate, int estateX, int estateZ) {
        // Town jigsaws allow 64 blocks from the root center. The root and
        // largest authored child are at most 63 wide; 288 exceeds the
        // Estate half-width (117) plus root-center offset (31), reach (64),
        // and maximum piece width (63). It also covers the four approaches.
        int reach = 288;
        int candidates = 0, generated = 0, intersecting = 0, envelope = 0;
        Set<String> overlapping = new HashSet<>();
        var sets = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        for (String name : new String[]{"second_chance_town_themed",
                "second_chance_town"}) {
            StructureSet set = sets.getValue(ResourceKey.create(Registries.STRUCTURE_SET,
                    Identifier.fromNamespaceAndPath("gems", name)));
            if (set == null || set.structures().size() != 1
                    || !(set.placement() instanceof RandomSpreadStructurePlacement placement))
                throw new IllegalStateException("Unexpected town placement: " + name);
            Holder<Structure> town = set.structures().getFirst().structure();
            Set<Long> seen = new HashSet<>();
            for (int cx = (estateX - reach) >> 4;
                 cx <= (estateX + reach) >> 4; cx++) {
                for (int cz = (estateZ - reach) >> 4;
                     cz <= (estateZ + reach) >> 4; cz++) {
                    ChunkPos candidate = placement.getPotentialStructureChunk(
                            level.getSeed(), cx, cz);
                    if (!seen.add(candidate.pack())) continue;
                    int x = candidate.getMinBlockX(), z = candidate.getMinBlockZ();
                    if (Math.abs(x - estateX) > reach
                            || Math.abs(z - estateZ) > reach) continue;
                    candidates++;
                    boolean wasLoaded = level.getChunkSource()
                            .hasChunk(candidate.x(), candidate.z());
                    StructureStart townStart = generateNoiseStart(level, town, candidate);
                    if (level.getChunkSource().hasChunk(candidate.x(), candidate.z())
                            != wasLoaded)
                        throw new IllegalStateException("Town noise probe loaded candidate chunk "
                                + candidate);
                    if (!townStart.isValid()) continue;
                    generated++;
                    boolean envelopeHit = false;
                    boolean pieceHit = false;
                    for (var townPiece : townStart.getPieces()) {
                        BoundingBox townBox = townPiece.getBoundingBox();
                        if (townBox.maxX() >= estateX - 125
                                && townBox.minX() <= estateX + 125
                                && townBox.maxZ() >= estateZ - 125
                                && townBox.minZ() <= estateZ + 125)
                            envelopeHit = true;
                        for (var estatePiece : estate.getPieces()) {
                            if (townBox.intersects(estatePiece.getBoundingBox())) {
                                intersecting++;
                                pieceHit = true;
                                break;
                            }
                        }
                    }
                    if (envelopeHit) envelope++;
                    if (pieceHit) overlapping.add(name + "@" + x + "," + z);
                }
            }
        }
        return new ExactTownRisk(candidates, generated, intersecting,
                envelope, Set.copyOf(overlapping));
    }

    /** Noise-only upper-bound collision screen for the two existing town sets. */
    private static TownRisk townRisk(ServerLevel level,
            net.minecraft.world.level.biome.BiomeResolver resolver,
            int estateX, int estateZ) {
        // Saved natural towns in Common-1 occupy at most 93 blocks from
        // their start chunk minimum in each axis. Round up to 96 here.
        int reach = 117 + 96;
        int candidates = 0;
        int eligible = 0;
        List<String> nearby = new ArrayList<>();
        var sets = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        for (String name : new String[]{"second_chance_town_themed",
                "second_chance_town"}) {
            StructureSet set = sets.getValue(ResourceKey.create(Registries.STRUCTURE_SET,
                    Identifier.fromNamespaceAndPath("gems", name)));
            if (set == null
                    || !(set.placement() instanceof RandomSpreadStructurePlacement placement))
                continue;
            Set<Long> seen = new HashSet<>();
            for (int cx = (estateX - reach) >> 4;
                 cx <= (estateX + reach) >> 4; cx++) {
                for (int cz = (estateZ - reach) >> 4;
                     cz <= (estateZ + reach) >> 4; cz++) {
                    var candidate = placement.getPotentialStructureChunk(level.getSeed(),
                            cx, cz);
                    if (!seen.add(candidate.pack())) continue;
                    int x = candidate.getMinBlockX();
                    int z = candidate.getMinBlockZ();
                    if (Math.abs(x - estateX) > reach
                            || Math.abs(z - estateZ) > reach) continue;
                    candidates++;
                    int y = surface(level, x, z);
                    var biome = resolver.getNoiseBiome(x >> 2, y >> 2, z >> 2);
                    if (set.structures().getFirst().structure().value().biomes()
                            .contains(biome)) {
                        eligible++;
                        nearby.add(name + "@" + x + "," + z);
                    }
                }
            }
        }
        return new TownRisk(candidates, eligible, List.copyOf(nearby));
    }

    private record PhaseEstimate(String status, int low, int high,
                                 int maxLocalRelief, int rotation,
                                 int selectedBase) {
        boolean potential() { return "POTENTIAL".equals(status); }
    }

    /** Raised-deck noise estimate; never generates or changes a chunk. */
    private static PhaseEstimate raisedEstimate(ServerLevel level,
            int centerX, int centerZ) {
        var source = level.getChunkSource();
        var generator = source.getGenerator();
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        int maxLocal = 0;
        int[] worldLower = new int[25];
        int[] worldUpper = new int[25];
        for (int wz = 0; wz < 5; wz++) {
            for (int wx = 0; wx < 5; wx++) {
                int startX = centerX - 117 + wx * 47;
                int startZ = centerZ - 117 + wz * 47;
                int endX = startX + 46;
                int endZ = startZ + 46;
                int moduleLow = Integer.MAX_VALUE;
                int moduleHigh = Integer.MIN_VALUE;
                int lower = level.getMinY() + 32;
                int upper = level.getMaxY() - (wz == 2 && wx == 2 ? 48 : 18);
                for (int x = startX; x <= endX; x = Math.min(x + 4, endX)) {
                    for (int z = startZ; z <= endZ; z = Math.min(z + 4, endZ)) {
                        int y = surface(level, x, z);
                        low = Math.min(low, y);
                        high = Math.max(high, y);
                        if (y < 55 || y > 145)
                            return new PhaseEstimate("ELEVATION", low, high,
                                    maxLocal, -1, -1);
                        var column = generator.getBaseColumn(x, z,
                                level, source.randomState());
                        var top = column.getBlock(y);
                        boolean wet = top.is(net.minecraft.world.level.block.Blocks.WATER);
                        if (!wet && (!top.isSolid() || !top.getFluidState().isEmpty()))
                            return new PhaseEstimate("BODY_SURFACE", low, high,
                                    maxLocal, -1, -1);
                        // A four-thick authored deck may bridge low ground or
                        // water. No fill is demanded beneath an open span.
                        // High terrain must remain within the 12-block cut;
                        // the walking floor stays two above any water crest.
                        lower = Math.max(lower, wet ? y - 2 : y - 12);
                        moduleLow = Math.min(moduleLow, y);
                        moduleHigh = Math.max(moduleHigh, y);
                        maxLocal = Math.max(maxLocal, moduleHigh - moduleLow);
                        if (lower > upper)
                            return new PhaseEstimate("BODY_INTERVAL", low, high,
                                    maxLocal, -1, -1);
                        if (z == endZ) break;
                    }
                    if (x == endX) break;
                }
                int[] interval = {lower, upper};
                if (!footingInterval(level, startX, startZ, interval))
                    return new PhaseEstimate("FOOTING_INTERVAL", low, high,
                            maxLocal, -1, -1);
                worldLower[wz * 5 + wx] = interval[0];
                worldUpper[wz * 5 + wx] = interval[1];
            }
        }
        String furthest = "SEAM_OR_GATE";
        int furthestRotation = -1;
        int furthestBase = -1;
        for (int turn = 0; turn < 4; turn++) {
            int[] lower = new int[25];
            int[] upper = new int[25];
            for (int id = 0; id < 25; id++) {
                int[] world = rotateGrid(id % 5, id / 5, turn);
                int index = world[1] * 5 + world[0];
                lower[id] = worldLower[index];
                upper[id] = worldUpper[index];
            }
            if (!gateIntervals(level, centerX, centerZ, turn, lower, upper)
                    || !constrainIntervals(12, lower, upper)) continue;
            int centerY = surface(level, centerX, centerZ);
            int[] bases = new int[25];
            bases[12] = Math.clamp(centerY, lower[12], upper[12]);
            assignIntervals(12, bases, lower, upper);
            furthest = "FOOTING";
            furthestRotation = turn;
            furthestBase = bases[12];
            if (!footingAnchors(level, centerX, centerZ, turn, bases)) continue;
            furthest = "SEAM_ROUTE";
            if (!seamRoutes(level, centerX, centerZ, turn, bases)) continue;
            furthest = "APPROACH";
            if (!dryApproaches(level, centerX, centerZ, turn, bases)) continue;
            return new PhaseEstimate("POTENTIAL", low, high, maxLocal,
                    turn, bases[12]);
        }
        return new PhaseEstimate(furthest, low, high, maxLocal,
                furthestRotation, furthestBase);
    }

    /** First pair of natural dry solids beneath a proposed footing. */
    private static int anchorTop(net.minecraft.world.level.NoiseColumn column, int topY) {
        int dry = 0;
        for (int depth = 0; depth <= 64; depth++) {
            var state = column.getBlock(topY - depth);
            if (state.isSolid() && state.getFluidState().isEmpty()) {
                if (++dry == 2) return topY - depth + 1;
            } else if (state.isAir()
                    || state.is(net.minecraft.world.level.block.Blocks.WATER)) {
                dry = 0;
            } else return Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }

    /** A span may float over low ground; only its 5x5 pier clusters need beds. */
    private static boolean footingInterval(ServerLevel level, int minX, int minZ,
                                           int[] interval) {
        var source = level.getChunkSource();
        var generator = source.getGenerator();
        for (int px : new int[]{0, 12, 24, 36, 46}) {
            for (int pz : new int[]{0, 12, 24, 36, 46}) {
                for (int dx : px == 46 ? new int[]{-1, 0} : new int[]{0, 1}) {
                    for (int dz : pz == 46 ? new int[]{-1, 0} : new int[]{0, 1}) {
                        int x = minX + px + dx;
                        int z = minZ + pz + dz;
                        int topY = surface(level, x, z);
                        if (topY < 55 || topY > 145) return false;
                        var column = generator.getBaseColumn(x, z, level,
                                source.randomState());
                        var top = column.getBlock(topY);
                        boolean wet = top.is(net.minecraft.world.level.block.Blocks.WATER);
                        if (!wet && (!top.isSolid() || !top.getFluidState().isEmpty()))
                            return false;
                        int anchor = anchorTop(column, topY);
                        if (anchor == Integer.MIN_VALUE) return false;
                        interval[0] = Math.max(interval[0], wet ? topY - 2 : topY - 12);
                        // From base-1 the second natural bed block must be
                        // reached by depth 31, matching the 32-write cap.
                        interval[1] = Math.min(interval[1], anchor + 31);
                        if (interval[0] > interval[1]) return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean naturalApproaches(ServerLevel level, int centerX, int centerZ) {
        var source = level.getChunkSource();
        var generator = source.getGenerator();
        // Outside the north/east/south/west wall, exactly 3 lanes x 8 rows.
        int[][] lanes = {{113, -1, 0, -1}, {235, 113, 1, 0},
                {113, 235, 0, 1}, {-1, 113, -1, 0}};
        for (int[] lane : lanes) {
            int[] previous = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
            for (int step = 0; step < 8; step++) {
                int low = Integer.MAX_VALUE;
                int high = Integer.MIN_VALUE;
                for (int offset = -1; offset <= 1; offset++) {
                    int x = centerX - 117 + lane[0] + lane[2] * step
                            - lane[3] * offset;
                    int z = centerZ - 117 + lane[1] + lane[3] * step
                            + lane[2] * offset;
                    int y = surface(level, x, z);
                    if (y < 55 || y > 145
                            || previous[offset + 1] != Integer.MIN_VALUE
                                    && Math.abs(y - previous[offset + 1]) > 1)
                        return false;
                    var column = generator.getBaseColumn(x, z, level,
                            source.randomState());
                    var top = column.getBlock(y);
                    var below = column.getBlock(y - 1);
                    if (!top.isSolid() || !top.getFluidState().isEmpty()
                            || !below.isSolid() || !below.getFluidState().isEmpty())
                        return false;
                    previous[offset + 1] = y;
                    low = Math.min(low, y);
                    high = Math.max(high, y);
                }
                if (high - low > 1) return false;
            }
        }
        return true;
    }

    private static boolean anchored(net.minecraft.world.level.NoiseColumn column,
                                    int topY, int maxDepth, int maxWater) {
        int dry = 0;
        int water = 0;
        for (int depth = 0; depth <= maxDepth; depth++) {
            var state = column.getBlock(topY - depth);
            if (state.isSolid() && state.getFluidState().isEmpty()) {
                if (++dry == 2) return true;
            } else if (state.isAir()) {
                dry = 0;
            } else if (state.is(net.minecraft.world.level.block.Blocks.WATER)
                    && ++water <= maxWater) {
                dry = 0;
            } else return false;
        }
        return false;
    }

    private static void assignIntervals(int id, int[] bases, int[] lower, int[] upper) {
        for (int child = 0; child < 25; child++) {
            if (FourRoadsEstateStructure.parentModule(child) != id) continue;
            bases[child] = Math.clamp(bases[id], lower[child], upper[child]);
            assignIntervals(child, bases, lower, upper);
        }
    }

    /** Five-by-five shafts per module, each a clipped two-by-two footing. */
    private static boolean footingAnchors(ServerLevel level, int centerX, int centerZ,
                                          int turn, int[] bases) {
        var source = level.getChunkSource();
        var generator = source.getGenerator();
        for (int id = 0; id < 25; id++) {
            int[] tile = rotateGrid(id % 5, id / 5, turn);
            int minX = centerX - 117 + tile[0] * 47;
            int minZ = centerZ - 117 + tile[1] * 47;
            for (int px : new int[]{0, 12, 24, 36, 46}) {
                for (int pz : new int[]{0, 12, 24, 36, 46}) {
                    for (int dx : px == 46 ? new int[]{-1, 0} : new int[]{0, 1}) {
                        for (int dz : pz == 46 ? new int[]{-1, 0} : new int[]{0, 1}) {
                            var column = generator.getBaseColumn(minX + px + dx,
                                    minZ + pz + dz, level, source.randomState());
                            if (!anchored(column, bases[id] - 1, 31, 32)) return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private static boolean seamRoutes(ServerLevel level, int centerX, int centerZ,
                                      int turn, int[] bases) {
        var source = level.getChunkSource();
        var generator = source.getGenerator();
        for (int child = 0; child < 25; child++) {
            int parent = FourRoadsEstateStructure.parentModule(child);
            if (parent < 0) continue;
            int dx = parent % 5 - child % 5;
            int dz = parent / 5 - child / 5;
            int localX = dx > 0 ? 40 : dx < 0 ? 5 : 19;
            int localZ = dz > 0 ? 40 : dz < 0 ? 5 : 19;
            int[] start = rotateFootprint((child % 5) * 47 + localX,
                    (child / 5) * 47 + localZ, turn);
            int[] direction = rotateDirection(dx, dz, turn);
            int fromY = bases[child] + 4;
            int toY = bases[parent] + 4;
            for (int step = 0; step <= 12; step++) {
                int floorY = fromY + Math.round((toY - fromY) * step / 12.0f);
                for (int lane = -1; lane <= 1; lane++) {
                    int x = centerX - 117 + start[0] + direction[0] * step
                            - direction[1] * lane;
                    int z = centerZ - 117 + start[1] + direction[1] * step
                            + direction[0] * lane;
                    int top = surface(level, x, z);
                    var column = generator.getBaseColumn(x, z, level,
                            source.randomState());
                    var surface = column.getBlock(top);
                    boolean wet = surface.is(net.minecraft.world.level.block.Blocks.WATER);
                    if (top < 55 || top > 145
                            || !wet && (!surface.isSolid()
                                    || !surface.getFluidState().isEmpty())
                            || wet && floorY < top + 2
                            || !wet && top - (floorY - 4) > 12
                            // A masonry stringer spans at most six stairs
                            // between supported cross-bents.
                            || step % 6 == 0
                                    && !anchored(column, floorY - 1, 31, 32))
                        return false;
                }
            }
        }
        return true;
    }

    private static boolean dryApproaches(ServerLevel level, int centerX, int centerZ,
                                         int turn, int[] bases) {
        var source = level.getChunkSource();
        var generator = source.getGenerator();
        int[][] gates = {
                {2, 113, 5, 0, -1, 5},
                {14, 228, 113, 1, 0, 6},
                {22, 113, 228, 0, 1, 6},
                {10, 5, 113, -1, 0, 5}
        };
        for (int[] gate : gates) {
            int[] marker = rotateFootprint(gate[1], gate[2], turn);
            int[] outward = rotateDirection(gate[3], gate[4], turn);
            int centerEdgeX = centerX - 117 + marker[0]
                    + outward[0] * (gate[5] + 1);
            int centerEdgeZ = centerZ - 117 + marker[1]
                    + outward[1] * (gate[5] + 1);
            int edgeY = surface(level, centerEdgeX, centerEdgeZ);
            if (Math.abs(edgeY - bases[gate[0]] - 4) > gate[5]) return false;
            int[] previous = {edgeY, edgeY, edgeY};
            for (int step = gate[5] + 1; step <= gate[5] + 8; step++) {
                int rowLow = Integer.MAX_VALUE;
                int rowHigh = Integer.MIN_VALUE;
                for (int lane = -1; lane <= 1; lane++) {
                    int x = centerX - 117 + marker[0] + outward[0] * step
                            - outward[1] * lane;
                    int z = centerZ - 117 + marker[1] + outward[1] * step
                            + outward[0] * lane;
                    int y = surface(level, x, z);
                    var column = generator.getBaseColumn(x, z, level,
                            source.randomState());
                    if (y < 55 || y > 145 || Math.abs(y - previous[lane + 1]) > 1
                            || !column.getBlock(y).isSolid()
                            || !column.getBlock(y).getFluidState().isEmpty()
                            || !column.getBlock(y - 1).isSolid()
                            || !column.getBlock(y - 1).getFluidState().isEmpty())
                        return false;
                    previous[lane + 1] = y;
                    rowLow = Math.min(rowLow, y);
                    rowHigh = Math.max(rowHigh, y);
                }
                if (rowHigh - rowLow > 1) return false;
            }
        }
        return true;
    }

    private static int surface(ServerLevel level, int x, int z) {
        var source = level.getChunkSource();
        return source.getGenerator().getBaseHeight(x, z,
                Heightmap.Types.WORLD_SURFACE_WG, level, source.randomState()) - 1;
    }

    private static int[] rotateGrid(int x, int z, int turn) {
        return switch (turn) {
            case 1 -> new int[]{4 - z, x};
            case 2 -> new int[]{4 - x, 4 - z};
            case 3 -> new int[]{z, 4 - x};
            default -> new int[]{x, z};
        };
    }

    private static int[] rotateFootprint(int x, int z, int turn) {
        return switch (turn) {
            case 1 -> new int[]{234 - z, x};
            case 2 -> new int[]{234 - x, 234 - z};
            case 3 -> new int[]{z, 234 - x};
            default -> new int[]{x, z};
        };
    }

    private static boolean gateIntervals(ServerLevel level, int centerX, int centerZ,
                                         int turn, int[] lower, int[] upper) {
        int[][] gates = {
                {2, 113, 5, 0, -1, 5},
                {14, 228, 113, 1, 0, 6},
                {22, 113, 228, 0, 1, 6},
                {10, 5, 113, -1, 0, 5}
        };
        for (int[] gate : gates) {
            int[] marker = rotateFootprint(gate[1], gate[2], turn);
            int[] outward = rotateDirection(gate[3], gate[4], turn);
            int edgeX = centerX - 117 + marker[0]
                    + outward[0] * (gate[5] + 1);
            int edgeZ = centerZ - 117 + marker[1]
                    + outward[1] * (gate[5] + 1);
            int edgeY = surface(level, edgeX, edgeZ);
            lower[gate[0]] = Math.max(lower[gate[0]], edgeY - 4 - gate[5]);
            upper[gate[0]] = Math.min(upper[gate[0]], edgeY - 4 + gate[5]);
            if (lower[gate[0]] > upper[gate[0]]) return false;
        }
        return true;
    }

    private static int[] rotateDirection(int x, int z, int turn) {
        return switch (turn) {
            case 1 -> new int[]{-z, x};
            case 2 -> new int[]{-x, -z};
            case 3 -> new int[]{z, -x};
            default -> new int[]{x, z};
        };
    }

    private static boolean constrainIntervals(int id, int[] lower, int[] upper) {
        for (int child = 0; child < 25; child++) {
            if (FourRoadsEstateStructure.parentModule(child) != id) continue;
            if (!constrainIntervals(child, lower, upper)) return false;
            lower[id] = Math.max(lower[id], lower[child] - 10);
            upper[id] = Math.min(upper[id], upper[child] + 10);
        }
        return lower[id] <= upper[id];
    }

    private static boolean estateEligible(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome) {
        return biome.is(COMMONS) || biome.is(Biomes.DESERT)
                || biome.is(Biomes.PLAINS);
    }

    private static void probeControls(ServerLevel level, ServerPlayer player, Survey site) {
        var callback = UseBlockCallback.EVENT.invoker();
        String activity = ExplorationProgress.activityId("estate",
                site.foyer().getX(), site.foyer().getZ());
        int pearlsBefore = countPearls(player);
        int passes = 0;
        for (char wing : new char[]{'N', 'E', 'S', 'W'}) {
            BlockPos gate = site.gates().get(wing);
            activate(level, player, callback, gate);
            CompoundTag state = ((GemsPersistentDataHolder) player).gems$getPersistentData()
                    .getCompound("FourRoadsEstate").orElseThrow();
            if (state.getLongOr("entry", 0L) != gate.asLong()
                    || state.getLongOr("foyer", 0L) != site.foyer().asLong()) {
                throw new IllegalStateException("Gate " + wing + " did not save the estate entry");
            }
            // This probe intentionally skips the physical road. A direct
            // teleport to the foyer must not earn the estate clear.
            activate(level, player, callback, site.foyer());
            if (state.getBooleanOr("won", false)
                    || ExplorationState.get(level.getServer()).model()
                            .earned(player.getUUID(), activity)
                    || ExplorationDeliveryAccount.of(player).has(activity)
                    || countPearls(player) != pearlsBefore) {
                throw new IllegalStateException("Foyer bypass incorrectly rewarded " + wing);
            }
            passes++;
        }
        System.out.println("FOUR_ROADS_ESTATE_CONTROL_PROBE start=" + site.bounds()
                + " gates=" + passes + " teleportBypassRejected=true");
    }

    private static void activate(ServerLevel level, ServerPlayer player,
                                 UseBlockCallback callback, BlockPos pos) {
        player.teleportTo(level, pos.getX() + 0.5, pos.getY() + 1,
                pos.getZ() + 0.5, EnumSet.noneOf(Relative.class),
                player.getYRot(), player.getXRot(), false);
        InteractionResult result = callback.interact(player, level, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        if (result != InteractionResult.SUCCESS_SERVER) {
            throw new IllegalStateException("Estate control ignored at " + pos + ": " + result);
        }
    }

    private static int countPearls(ServerPlayer player) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (stack.is(Items.ENDER_PEARL)) count += stack.getCount();
        }
        return count;
    }

    private static char wing(Block block) {
        if (block == EstateBlocks.ENTRY_N) return 'N';
        if (block == EstateBlocks.ENTRY_E) return 'E';
        if (block == EstateBlocks.ENTRY_S) return 'S';
        if (block == EstateBlocks.ENTRY_W) return 'W';
        return '?';
    }
}
