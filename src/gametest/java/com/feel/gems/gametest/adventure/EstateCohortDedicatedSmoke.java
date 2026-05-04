package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.estate.EstateBlocks;
import com.feel.gems.adventure.estate.EstateSiteIntegrity;
import com.feel.gems.adventure.estate.FourRoadsEstateStructure;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/** Disposable dedicated-server fixture. No production registration or worldgen writes. */
public final class EstateCohortDedicatedSmoke implements ModInitializer {
    private static final ResourceKey<Structure> ESTATE = ResourceKey.create(
            Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath("gems", "four_roads_estate_v1"));
    private static final int QUIET_SAMPLES = 30;
    private static final int TICKETS_PER_TICK = 2;
    private static final int MAX_IN_FLIGHT = 8;
    private static final int MAX_REFERENCES = 64;
    // The fixed 16/15 radius-512 lattice has up to 4,225 sites at one per tick.
    private static final int MAX_PHASE_TICKS = 6000;

    private enum Phase {
        QUIET, SCAN, ROOT, BODY, SETTLE, REFERENCES, AUDIT, RELEASE, COMPLETE
    }

    private record Reference(Structure structure, Identifier id, long chunk) { }
    private record Markers(Map<Character, BlockPos> gates, BlockPos foyer,
                           BlockPos returnStone) { }

    private final boolean enabled = Boolean.getBoolean("gems.estateCohortDedicated");
    private final boolean restart = Boolean.getBoolean("gems.estateCohortResume");
    private final boolean reverseBody = Boolean.getBoolean("gems.estateCohortReverseBody");
    private final String diagnosticChunks = System.getProperty("gems.estateCohortDiagnosticChunks", "");
    private final String seedName = System.getProperty("gems.estateCohortSeed", "");
    private Phase phase = Phase.QUIET;
    private int phaseStarted;
    private int samples;
    private int stable;
    private int priorLoaded = -1;
    private List<ChunkPos> candidates = List.of();
    private int examined;
    private int non25;
    private int conflicts;
    private int unknown;
    private int loaded;
    private final Map<String, Integer> noStartStages = new HashMap<>();
    private ChunkPos selected;
    private StructureStart start;
    private List<ChunkPos> work = List.of();
    private int workOffset;
    private final Set<Long> inFlight = new HashSet<>();
    private final Set<Long> forced = new HashSet<>();
    private List<Reference> references = List.of();
    private String result = "INCONCLUSIVE";
    private String reason = "UNSET";
    private long rootRequested;
    private long bodyStarted;

    @Override
    public void onInitialize() {
        if (!enabled) return;
        if (!seedName.matches("FourRoadsEstate-Common-[123]"))
            throw new IllegalArgumentException("Estate dedicated fixture requires fixed seed");
        if (!diagnosticChunks.isEmpty() && restart)
            throw new IllegalArgumentException("Estate rejection diagnostics require a fresh trial");
        if (restart) {
            selected = new ChunkPos(Integer.getInteger("gems.estateCohortChunkX",
                    Integer.MIN_VALUE), Integer.getInteger("gems.estateCohortChunkZ",
                    Integer.MIN_VALUE));
            if (selected.x() == Integer.MIN_VALUE || selected.z() == Integer.MIN_VALUE)
                throw new IllegalArgumentException("Restart requires saved start chunk");
        }
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }

    private void tick(MinecraftServer server) {
        if (phase == Phase.COMPLETE) return;
        ServerLevel level = server.overworld();
        if (level == null) return;
        try {
            if (level.getSeed() != (long) seedName.hashCode()) {
                fail("SEED_MISMATCH actual=" + level.getSeed());
                return;
            }
            if (server.getTickCount() - phaseStarted > MAX_PHASE_TICKS) {
                fail("PHASE_TIMEOUT_" + phase);
                return;
            }
            switch (phase) {
                case QUIET -> quiet(level, server);
                case SCAN -> scan(level, server);
                case ROOT -> root(level, server);
                case BODY, REFERENCES -> load(level, server);
                case SETTLE -> settle(level, server);
                case AUDIT -> audit(level, server);
                case RELEASE -> release(level, server);
                case COMPLETE -> { }
            }
        } catch (RuntimeException error) {
            error.printStackTrace();
            fail("EXCEPTION_" + error.getClass().getSimpleName());
        }
    }

    private void quiet(ServerLevel level, MinecraftServer server) {
        if (server.getTickCount() - phaseStarted < 20) return;
        phaseStarted = server.getTickCount();
        var source = level.getChunkSource();
        int pending = source.getPendingTasksCount();
        int loadedNow = source.getLoadedChunksCount();
        stable = pending == 0 && loadedNow == priorLoaded ? stable + 1 : 0;
        priorLoaded = loadedNow;
        samples++;
        System.out.println("FOUR_ROADS_ESTATE_DEDICATED_QUIET seed=" + seedName
                + " phase=" + (restart ? "restart" : "trial")
                + " sample=" + samples + " pending=" + pending
                + " loaded=" + loadedNow + " stable=" + stable);
        if (stable >= 3) {
            if (restart) requestRoot(level, server);
            else {
                candidates = EstateCohort512Probe.candidates(level);
                if (!diagnosticChunks.isEmpty()) {
                    List<ChunkPos> focused = new ArrayList<>();
                    for (String coordinate : diagnosticChunks.split(";")) {
                        String[] pair = coordinate.split(",", -1);
                        if (pair.length != 2)
                            throw new IllegalArgumentException("Malformed Estate diagnostic chunk");
                        ChunkPos chunk = new ChunkPos(Integer.parseInt(pair[0]),
                                Integer.parseInt(pair[1]));
                        if (!candidates.contains(chunk) || focused.contains(chunk))
                            throw new IllegalArgumentException("Diagnostic chunk not unique in fixed lattice: " + chunk);
                        focused.add(chunk);
                    }
                    if (focused.isEmpty() || focused.size() > 16)
                        throw new IllegalArgumentException("Expected 1-16 fixed-lattice diagnostic chunks");
                    candidates = List.copyOf(focused);
                    System.out.println("FOUR_ROADS_ESTATE_DEDICATED_DIAGNOSTIC seed="
                            + seedName + " chunks=" + candidates);
                }
                next(Phase.SCAN, server);
            }
        } else if (samples >= QUIET_SAMPLES) {
            fail("STARTUP_NOT_QUIESCENT");
        }
    }

    private void scan(ServerLevel level, MinecraftServer server) {
        if (examined == candidates.size()) {
            result = diagnosticChunks.isEmpty() ? "NO_CLEAR_SITE" : "DIAGNOSTIC";
            reason = diagnosticChunks.isEmpty() ? "EXHAUSTED_FIXED_LATTICE"
                    : "FOCUSED_REJECTION_TRACE_COMPLETE";
            next(Phase.RELEASE, server);
            return;
        }
        ChunkPos chunk = candidates.get(examined++);
        EstateReservationProbe.Candidate candidate =
                EstateReservationProbe.inspectCandidate(level, chunk);
        switch (candidate.status()) {
            case "NO_START" -> noStartStages.merge(
                    candidate.generationReason() == null ? "BEFORE_ESTATE_GENERATION_POINT"
                            : candidate.generationReason().name(), 1, Integer::sum);
            case "NON_25_PIECE_START" -> non25++;
            case "ALREADY_LOADED", "BODY_OR_ARRIVAL_ALREADY_LOADED" -> loaded++;
            case "CONFLICT", "UNKNOWN" -> {
                if (candidate.status().equals("CONFLICT")) conflicts++;
                else unknown++;
                System.out.println("FOUR_ROADS_ESTATE_DEDICATED_RESERVATION seed=" + seedName
                        + " chunk=" + chunk.x() + "," + chunk.z()
                        + " status=" + candidate.status()
                        + " generation=" + candidate.generationReason()
                        + " reason=" + (candidate.reservation() == null ? "MISSING_VERDICT"
                                : candidate.reservation().reason())
                        + " candidates=" + (candidate.reservation() == null ? -1
                                : candidate.reservation().candidateChunks())
                        + " starts=" + (candidate.reservation() == null ? -1
                                : candidate.reservation().generatedStarts())
                        + " ms=" + (candidate.reservation() == null ? -1
                                : candidate.reservation().elapsedMillis()));
            }
            case "CLEAR" -> {
                if (!diagnosticChunks.isEmpty()) {
                    System.out.println("FOUR_ROADS_ESTATE_DEDICATED_DIAGNOSTIC_CLEAR seed="
                            + seedName + " chunk=" + chunk.x() + "," + chunk.z());
                    break;
                }
                selected = chunk;
                System.out.println("FOUR_ROADS_ESTATE_DEDICATED_SELECTED seed=" + seedName
                        + " chunk=" + chunk.x() + "," + chunk.z()
                        + " examined=" + examined + "/" + candidates.size()
                        + " conflicts=" + conflicts + " unknown=" + unknown
                        + " loaded=" + loaded + " non25=" + non25
                        + " reservationMs=" + candidate.reservation().elapsedMillis());
                requestRoot(level, server);
            }
            default -> fail("UNVERIFIED_CANDIDATE_" + candidate.status());
        }
        if (examined % 128 == 0)
            System.out.println("FOUR_ROADS_ESTATE_DEDICATED_SCAN seed=" + seedName
                    + " examined=" + examined + "/" + candidates.size()
                    + " conflicts=" + conflicts + " unknown=" + unknown
                    + " loaded=" + loaded + " non25=" + non25);
    }

    private void requestRoot(ServerLevel level, MinecraftServer server) {
        if (selected == null) throw new IllegalStateException("No selected start");
        force(level, selected);
        rootRequested = System.nanoTime();
        next(Phase.ROOT, server);
    }

    private void root(ServerLevel level, MinecraftServer server) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(selected.x(), selected.z());
        if (chunk == null) return;
        Structure estate = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getOrThrow(ESTATE).value();
        start = chunk.getStartForStructure(estate);
        if (start == null || !start.isValid()
                || !FourRoadsEstateStructure.completeGrid(start.getPieces())
                || start.getPieces().size() != 25) {
            fail("ROOT_HAS_NO_SAVED_25_PIECE_START");
            return;
        }
        BoundingBox bounds = start.getBoundingBox();
        if (bounds.maxX() - bounds.minX() + 1 != 235
                || bounds.maxZ() - bounds.minZ() + 1 != 235) {
            fail("BODY_NOT_235_SQUARE");
            return;
        }
        System.out.println("FOUR_ROADS_ESTATE_DEDICATED_ROOT seed=" + seedName
                + " chunk=" + selected.x() + "," + selected.z()
                + " bounds=" + bounds + " pieces=" + start.getPieces().size()
                + " rootLoadMs=" + (System.nanoTime() - rootRequested) / 1_000_000);
        List<ChunkPos> body = new ArrayList<>();
        for (int cx = (bounds.minX() - 12) >> 4;
             cx <= (bounds.maxX() + 12) >> 4; cx++) {
            for (int cz = (bounds.minZ() - 12) >> 4;
                 cz <= (bounds.maxZ() + 12) >> 4; cz++) {
                body.add(new ChunkPos(cx, cz));
            }
        }
        // Both orders are fixed; the reverse fixture exposes neighbor-order bugs.
        Comparator<ChunkPos> order = Comparator.comparingInt(ChunkPos::x)
                .thenComparingInt(ChunkPos::z);
        body.sort(reverseBody ? order.reversed() : order);
        work = List.copyOf(body);
        workOffset = 0;
        bodyStarted = System.nanoTime();
        next(Phase.BODY, server);
    }

    private void load(ServerLevel level, MinecraftServer server) {
        inFlight.removeIf(packed -> level.getChunkSource().getChunkNow(
                ChunkPos.getX(packed), ChunkPos.getZ(packed)) != null);
        int added = 0;
        while (workOffset < work.size() && added < TICKETS_PER_TICK
                && inFlight.size() < MAX_IN_FLIGHT) {
            ChunkPos chunk = work.get(workOffset++);
            // Hold even a chunk loaded by its neighbor until the full audit.
            force(level, chunk);
            if (level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) == null)
                inFlight.add(chunk.pack());
            added++;
        }
        if (workOffset < work.size() || !inFlight.isEmpty()) return;
        if (phase == Phase.BODY) {
            System.out.println("FOUR_ROADS_ESTATE_DEDICATED_BODY seed=" + seedName
                    + " chunks=" + work.size() + " order="
                    + (reverseBody ? "reverse_xz" : "ascending_xz") + " bodyLoadMs="
                    + (System.nanoTime() - bodyStarted) / 1_000_000
                    + " loaded=" + level.getChunkSource().getLoadedChunksCount());
            next(Phase.SETTLE, server);
            stable = 0;
            samples = 0;
            priorLoaded = -1;
        } else {
            for (Reference reference : references) {
                LevelChunk owner = level.getChunkSource().getChunkNow(
                        ChunkPos.getX(reference.chunk()),
                        ChunkPos.getZ(reference.chunk()));
                if (owner == null || owner.getStartForStructure(reference.structure()) == null
                        || !owner.getStartForStructure(reference.structure()).isValid()) {
                    fail("REFERENCED_START_UNAVAILABLE_" + reference.id());
                    return;
                }
            }
            next(Phase.AUDIT, server);
        }
    }

    private void settle(ServerLevel level, MinecraftServer server) {
        if (server.getTickCount() - phaseStarted < 20) return;
        phaseStarted = server.getTickCount();
        var source = level.getChunkSource();
        int pending = source.getPendingTasksCount();
        int loadedNow = source.getLoadedChunksCount();
        stable = pending == 0 && loadedNow == priorLoaded ? stable + 1 : 0;
        priorLoaded = loadedNow;
        if (stable >= 3) {
            collectReferences(level);
            next(Phase.REFERENCES, server);
        } else if (++samples >= QUIET_SAMPLES) {
            fail("BODY_NOT_SETTLED");
        }
    }

    private void collectReferences(ServerLevel level) {
        BoundingBox bounds = start.getBoundingBox();
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Set<Reference> found = new HashSet<>();
        for (ChunkPos position : work) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(position.x(), position.z());
            if (chunk == null) throw new IllegalStateException("Body chunk unavailable");
            for (var entry : chunk.getAllReferences().entrySet()) {
                Identifier id = registry.getKey(entry.getKey());
                if (id == null) throw new IllegalStateException("Unregistered reference");
                if (id.equals(ESTATE.identifier())) continue;
                for (long packed : entry.getValue()) {
                    int cx = ChunkPos.getX(packed);
                    int cz = ChunkPos.getZ(packed);
                    if (cx < (bounds.minX() >> 4) - 32
                            || cx > (bounds.maxX() >> 4) + 32
                            || cz < (bounds.minZ() >> 4) - 32
                            || cz > (bounds.maxZ() >> 4) + 32)
                        throw new IllegalStateException("Reference outside 32-chunk bound " + id);
                    found.add(new Reference(entry.getKey(), id, packed));
                    if (found.size() > MAX_REFERENCES)
                        throw new IllegalStateException("Too many foreign references");
                }
            }
        }
        references = found.stream().sorted(Comparator
                .comparing((Reference r) -> r.id().toString())
                .thenComparingInt(r -> ChunkPos.getX(r.chunk()))
                .thenComparingInt(r -> ChunkPos.getZ(r.chunk()))).toList();
        Set<Long> positions = new HashSet<>();
        List<ChunkPos> owners = new ArrayList<>();
        for (Reference reference : references) {
            if (positions.add(reference.chunk()))
                owners.add(new ChunkPos(ChunkPos.getX(reference.chunk()),
                        ChunkPos.getZ(reference.chunk())));
        }
        work = List.copyOf(owners);
        workOffset = 0;
        System.out.println("FOUR_ROADS_ESTATE_DEDICATED_REFERENCES seed=" + seedName
                + " references=" + references.size() + " ownerChunks=" + work.size());
    }

    private void audit(ServerLevel level, MinecraftServer server) {
        long began = System.nanoTime();
        EstateSiteIntegrity.Result integrity = EstateSiteIntegrity.inspectLoaded(level, start);
        if (integrity != EstateSiteIntegrity.Result.READY) {
            result = "BLOCKED";
            reason = integrity.name();
            System.out.println("FOUR_ROADS_ESTATE_DEDICATED_AUDIT seed=" + seedName
                    + " integrity=" + integrity + " ms="
                    + (System.nanoTime() - began) / 1_000_000);
            next(Phase.RELEASE, server);
            return;
        }
        Markers markers = markers(level, start);
        if (markers == null) {
            fail("MISSING_FOUR_GATE_OR_FOYER_MARKERS");
            return;
        }
        long routeStarted = System.nanoTime();
        boolean routes = EstateLoadedRouteProbe.verify(level, start,
                markers.gates(), markers.foyer());
        System.out.println("FOUR_ROADS_ESTATE_DEDICATED_AUDIT seed=" + seedName
                + " integrity=" + integrity + " pieces=" + start.getPieces().size()
                + " seams=24 markers=6 routes=" + routes
                + " integrityAndMarkerMs=" + (routeStarted - began) / 1_000_000
                + " testOnlyVoxelRouteMs="
                + (System.nanoTime() - routeStarted) / 1_000_000
                + " controlsActivated=false");
        result = routes ? "READY" : "BLOCKED";
        reason = routes ? "SAVED_INTEGRITY_AND_FOUR_VOXEL_ROUTES"
                : "VOXEL_ROUTE_FAILURE";
        next(Phase.RELEASE, server);
    }

    private static Markers markers(ServerLevel level, StructureStart start) {
        Map<Character, List<BlockPos>> bands = new HashMap<>();
        BlockPos foyer = null;
        BlockPos returnStone = null;
        for (var piece : start.getPieces()) {
            BoundingBox box = piece.getBoundingBox();
            int y = box.minY() + 4;
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Block block = level.getBlockState(pos).getBlock();
                    char wing = wing(block);
                    if (wing != '?') bands.computeIfAbsent(wing,
                            ignored -> new ArrayList<>()).add(pos);
                    else if (block == EstateBlocks.FOYER) foyer = pos;
                    else if (block == EstateBlocks.RETURN) returnStone = pos;
                }
            }
        }
        if (foyer == null || returnStone == null || bands.size() != 4)
            return null;
        Map<Character, BlockPos> gates = new HashMap<>();
        for (char wing : new char[]{'N', 'E', 'S', 'W'}) {
            List<BlockPos> band = bands.get(wing);
            if (band == null || band.size() != 3) return null;
            gates.put(wing, band.get(1));
        }
        return new Markers(Map.copyOf(gates), foyer, returnStone);
    }

    private static char wing(Block block) {
        if (block == EstateBlocks.ENTRY_N) return 'N';
        if (block == EstateBlocks.ENTRY_E) return 'E';
        if (block == EstateBlocks.ENTRY_S) return 'S';
        if (block == EstateBlocks.ENTRY_W) return 'W';
        return '?';
    }

    private void release(ServerLevel level, MinecraftServer server) {
        int removed = 0;
        while (!forced.isEmpty() && removed < TICKETS_PER_TICK) {
            long packed = forced.iterator().next();
            forced.remove(packed);
            level.setChunkForced(ChunkPos.getX(packed), ChunkPos.getZ(packed), false);
            removed++;
        }
        if (!forced.isEmpty()) return;
        phase = Phase.COMPLETE;
        System.out.println("FOUR_ROADS_ESTATE_DEDICATED_RESULT phase="
                + (restart ? "restart" : "trial") + " status=" + result
                + " seed=" + seedName + " chunk="
                + (selected == null ? 0 : selected.x()) + ","
                + (selected == null ? 0 : selected.z())
                + " reason=" + reason + " examined=" + examined
                + " conflicts=" + conflicts + " unknown=" + unknown
                + " loaded=" + loaded + " non25=" + non25
                + " noStartStages=" + new java.util.TreeMap<>(noStartStages)
                + " tick=" + server.getTickCount());
    }

    private void force(ServerLevel level, ChunkPos chunk) {
        if (forced.add(chunk.pack()))
            level.setChunkForced(chunk.x(), chunk.z(), true);
    }

    private void fail(String detail) {
        result = "INCONCLUSIVE";
        reason = detail.replace(' ', '_');
        phase = Phase.RELEASE;
    }

    private void next(Phase target, MinecraftServer server) {
        phase = target;
        phaseStarted = server.getTickCount();
    }
}
