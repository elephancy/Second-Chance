package com.feel.gems.adventure;

import com.feel.gems.GemsMod;
import com.feel.gems.adventure.nether.EmberV6Readiness;
import com.feel.gems.adventure.nether.EmberV6ReadinessState;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Server-owned, bounded Atlas lookups. World and ticket state is touched only on the server thread. */
final class RumorAtlasLocator {
    private static final int MAX_QUEUE = 8;
    private static final int MAX_SEARCH_TICKS = 1_200;
    private static final int SEARCH_SLICE_TICKS = 160;
    private static final int FIRST_PROGRESS_TICKS = 200;
    private static final int PROGRESS_INTERVAL_TICKS = 400;
    private static final long STEP_BUDGET_NS = 3_000_000L;
    private static final TicketType TICKET_TYPE = new TicketType(1_809L, TicketType.FLAG_LOADING);
    private static final int TICKET_LEVEL = ChunkLevel.byStatus(ChunkStatus.STRUCTURE_STARTS);
    private static final Identifier EMBER_V6 = Identifier.fromNamespaceAndPath(
            GemsMod.MOD_ID, "redline_ember_bridge_v6");
    private static final Map<MinecraftServer, RumorAtlasLocator> SERVICES = new HashMap<>();
    private static boolean registered;

    record Result(BlockPos found, boolean nearbyCanonical, boolean reusedCache,
                  boolean provisional, String error) { }
    private record Request(UUID player, ServerLevel level, BlockPos origin, String targetId,
                           int themedRings, int fallbackRings, boolean cached,
                           BlockPos cachedResult, Consumer<Result> callback) { }
    private record Stage(String tag, int rings, boolean nearbyOnly) { }
    private record Placement(RandomSpreadStructurePlacement value,
                             List<Holder<Structure>> structures,
                             boolean planned) { }
    private record PlannedCandidate(Placement placement, ChunkPos chunk) { }

    private final MinecraftServer server;
    private final ExecutorService worker;
    private final AtlasCandidateOracle oracle = new AtlasCandidateOracle();
    private final ArrayDeque<Job> queue = new ArrayDeque<>();
    private Job active;
    private long activeAt;
    private boolean stopping;

    private final class Job {
        private final Request request;
        private Search search;
        private long activeTicks;
        private long nextProgressAt = FIRST_PROGRESS_TICKS;

        private Job(Request request) {
            this.request = request;
        }
    }

    private RumorAtlasLocator(MinecraftServer server) {
        this.server = server;
        this.worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "gems-atlas-" + Integer.toHexString(System.identityHashCode(server)));
            thread.setDaemon(true);
            return thread;
        });
    }

    static void register() {
        if (registered) return;
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            RumorAtlasLocator locator = SERVICES.get(server);
            if (locator != null) locator.tick();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            RumorAtlasLocator locator = SERVICES.get(server);
            if (locator != null) locator.cancel(handler.getPlayer().getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            RumorAtlasLocator locator = SERVICES.remove(server);
            if (locator != null) locator.stop();
        });
    }

    static boolean submit(ServerLevel level, UUID player, BlockPos origin, String targetId,
                          int themedRings, int fallbackRings, boolean hasCachedResult,
                          BlockPos cachedResult, Consumer<Result> callback) {
        MinecraftServer server = level.getServer();
        RumorAtlasLocator locator = SERVICES.computeIfAbsent(server, RumorAtlasLocator::new);
        return locator.enqueue(new Request(player, level, origin.immutable(), targetId, themedRings,
                fallbackRings, hasCachedResult, cachedResult, callback));
    }

    /** Checks the exact accepted lookup, including its callback identity. */
    static boolean hasAccepted(ServerLevel level, UUID player, String targetId,
                               Consumer<Result> callback) {
        RumorAtlasLocator locator = SERVICES.get(level.getServer());
        if (locator == null || locator.stopping) return false;
        if (locator.active != null && locator.accepted(locator.active, level, player, targetId, callback)) {
            return true;
        }
        return locator.queue.stream().anyMatch(job -> locator.accepted(job, level, player, targetId, callback));
    }

    private boolean accepted(Job job, ServerLevel level, UUID player, String targetId,
                             Consumer<Result> callback) {
        Request request = job.request;
        return (job.search == null || !job.search.cancelled)
                && request.level() == level && request.player().equals(player)
                && request.targetId().equals(targetId) && request.callback() == callback;
    }

    private boolean enqueue(Request request) {
        if (stopping) return false;
        // Reject an overfull queue before replacing an active search. A full
        // queue of other players must not erase this player's existing clue.
        if (queue.size() >= MAX_QUEUE && queue.stream()
                .noneMatch(waiting -> waiting.request.player().equals(request.player()))) return false;
        // A newer click replaces the same player's old request without sending a stale clue.
        cancel(request.player());
        queue.addLast(new Job(request));
        return true;
    }

    private void cancel(UUID player) {
        Iterator<Job> waiting = queue.iterator();
        while (waiting.hasNext()) {
            Job job = waiting.next();
            if (!job.request.player().equals(player)) continue;
            if (job.search != null) job.search.cancel();
            waiting.remove();
        }
        if (active != null && active.request.player().equals(player)) {
            if (active.search != null) active.search.cancel();
            if (active.search == null || active.search.ticket == null) {
                active = null;
                activeAt = 0;
            }
        }
    }

    private void tick() {
        if (stopping) return;
        Iterator<Job> waiting = queue.iterator();
        while (waiting.hasNext()) {
            Job job = waiting.next();
            if (server.getPlayerList().getPlayer(job.request.player()) != null) continue;
            if (job.search != null) job.search.cancel();
            waiting.remove();
        }
        if (active != null && active.search != null && active.search.cancelled) {
            active.search.releaseTicket();
            if (active.search.ticket == null) {
                active = null;
                activeAt = 0;
            }
        }
        if (active == null) {
            while (!queue.isEmpty()) {
                Job next = queue.removeFirst();
                if (server.getPlayerList().getPlayer(next.request.player()) == null) continue;
                active = next;
                activeAt = server.getTickCount();
                try {
                    if (next.search == null) next.search = new Search(next.request);
                } catch (Throwable error) {
                    GemsMod.LOGGER.error("Atlas search could not start", error);
                    finish(new Result(null, false, false, false,
                            "The Atlas cannot read this trail right now."));
                    continue;
                }
                break;
            }
        }
        Job job = active;
        if (job == null) return;
        Search current = job.search;
        long sliceTicks = server.getTickCount() - activeAt;
        long activeTicks = job.activeTicks + sliceTicks;
        if (!current.done) {
            if (activeTicks > MAX_SEARCH_TICKS) {
                current.fail("Atlas search timed out");
            } else {
                current.advance(server.getTickCount());
            }
        }
        // Chunk futures can pump server work while advance runs. Shutdown or
        // cancellation may replace the active search before control returns.
        if (active != job || job.search != current) return;
        if (current.cancelled) return;
        if (current.done) {
            Result result = new Result(current.found, current.nearbyCanonical,
                    current.reusedCache, current.provisional,
                    current.failure == null ? null : "The Atlas cannot read this trail right now. Try again shortly.");
            if (!current.releaseTicket()) return;
            if (active != job || job.search != current) return;
            current.reportPlan();
            finish(result);
        } else {
            if (activeTicks >= job.nextProgressAt) {
                ServerPlayer player = server.getPlayerList().getPlayer(job.request.player());
                if (player != null && player.level() == job.request.level()) {
                    player.sendSystemMessage(Component.literal("Still looking for this place..."), true);
                }
                job.nextProgressAt = activeTicks + PROGRESS_INTERVAL_TICKS;
            }
            if (!queue.isEmpty() && sliceTicks >= SEARCH_SLICE_TICKS
                    && current.ticket == null && current.pending == null) {
                // Retain the scan cursor. A chunk request keeps this turn until
                // its ticket is released, so rotation never multiplies tickets.
                job.activeTicks = activeTicks;
                queue.addLast(job);
                active = null;
                activeAt = 0;
            }
        }
    }

    private void finish(Result result) {
        Job completed = active;
        active = null;
        activeAt = 0;
        if (completed != null) respond(completed.request, result);
    }

    private void respond(Request request, Result result) {
        if (server.getPlayerList().getPlayer(request.player()) == null) return;
        try {
            request.callback().accept(result);
        } catch (Throwable error) {
            GemsMod.LOGGER.error("Atlas search response failed", error);
        }
    }

    private void stop() {
        stopping = true;
        for (Job job : queue) {
            if (job.search != null) job.search.cancel();
        }
        queue.clear();
        if (active != null && active.search != null) {
            active.search.cancel();
            if (!active.search.releaseTicket()) GemsMod.LOGGER.error("Atlas ticket could not be released at server stop");
        }
        active = null;
        activeAt = 0;
        worker.shutdownNow();
        oracle.stop();
    }

    private final class Search {
        private final Request request;
        private final ServerLevel level;
        private final ServerChunkCache source;
        private final TicketStorage storage;
        private final int baseChunkX;
        private final int baseChunkZ;
        private final long seed;
        private final List<Stage> stages = new ArrayList<>();
        private List<Placement> placements = List.of();
        private int stageIndex;
        private int ring;
        private int placementIndex;
        private int dx;
        private int dz;
        private int structureIndex;
        private int requestedAt;
        private boolean placementMatched;
        private boolean dispatched;
        private boolean done;
        private boolean cancelled;
        private boolean nearbyCanonical;
        private boolean reusedCache;
        private BlockPos found;
        private BlockPos best;
        private double bestDistance = Double.MAX_VALUE;
        private BlockPos unknownBest;
        private double unknownBestDistance = Double.MAX_VALUE;
        private boolean provisional;
        private String failure;
        private ChunkPos candidate;
        private Placement candidatePlacement;
        private Ticket ticket;
        private CompletableFuture<ChunkResult<ChunkAccess>> pending;
        private final long startedNanos = System.nanoTime();
        private final Object resourcesAtStart;
        private int plannedAbsent;
        private int plannedPossible;
        private int plannedFallback;
        // Only a few upcoming candidates are planned at once. They are read
        // in the original ring order, regardless of worker completion order.
        private final Map<PlannedCandidate, CompletableFuture<AtlasCandidateOracle.Plan>>
                oraclePlans = new HashMap<>();

        private Search(Request request) {
            this.request = request;
            this.level = request.level();
            this.source = level.getChunkSource();
            this.storage = Objects.requireNonNull(source.getDataStorage().get(TicketStorage.TYPE));
            this.baseChunkX = SectionPos.blockToSectionCoord(request.origin().getX());
            this.baseChunkZ = SectionPos.blockToSectionCoord(request.origin().getZ());
            this.seed = source.getGeneratorState().getLevelSeed();
            this.resourcesAtStart = server.getResourceManager();
            if (request.cached() && request.themedRings() == 0) {
                found = request.cachedResult();
                reusedCache = true;
                done = true;
                return;
            }
            if (request.themedRings() > 0) {
                stages.add(new Stage("atlas_target/" + request.targetId(), 1, true));
                stages.add(new Stage("themed_target/" + request.targetId(), request.themedRings(), false));
            }
            stages.add(new Stage("atlas_target/" + request.targetId(), request.fallbackRings(), false));
            enterStage();
        }

        private void advance(int serverTick) {
            if (done || cancelled) return;
            try {
                if (pending != null || ticket != null) {
                    pollRequest(serverTick);
                    if (pending != null || ticket != null || done) return;
                }
                long deadline = System.nanoTime() + STEP_BUDGET_NS;
                while (!done && ticket == null && System.nanoTime() < deadline) {
                    if (candidate != null) {
                        if (examineCandidate(serverTick)) return;
                        continue;
                    }
                    if (placementIndex >= placements.size()) {
                        if (best != null) {
                            acceptStageResult(best);
                            continue;
                        }
                        ring++;
                        placementIndex = 0;
                        dx = -ring;
                        dz = -ring;
                        placementMatched = false;
                        oraclePlans.clear();
                        if (ring > stages.get(stageIndex).rings()) nextStage();
                        continue;
                    }
                    if (placementMatched || dx > ring) {
                        placementIndex++;
                        dx = -ring;
                        dz = -ring;
                        placementMatched = false;
                        oraclePlans.clear();
                        continue;
                    }
                    int ox = dx;
                    int oz = dz++;
                    if (dz > ring) {
                        dz = -ring;
                        dx++;
                    }
                    if (ox != -ring && ox != ring && oz != -ring && oz != ring) continue;
                    candidatePlacement = placements.get(placementIndex);
                    int spacing = candidatePlacement.value().spacing();
                    candidate = candidatePlacement.value().getPotentialStructureChunk(seed,
                            baseChunkX + spacing * ox, baseChunkZ + spacing * oz);
                    structureIndex = 0;
                    prefetchCandidateRing();
                }
            } catch (Throwable error) {
                fail(error.toString());
            }
        }

        private void enterStage() {
            if (stageIndex >= stages.size()) {
                if (unknownBest != null) {
                    found = unknownBest;
                    provisional = true;
                }
                done = true;
                return;
            }
            Stage stage = stages.get(stageIndex);
            TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE,
                    Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, stage.tag()));
            var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            Map<RandomSpreadStructurePlacement, LinkedHashSet<Holder<Structure>>> grouped = new LinkedHashMap<>();
            for (Holder<Structure> holder : registry.getTagOrEmpty(tag)) {
                for (StructurePlacement placement : source.getGeneratorState().getPlacementsForStructure(holder)) {
                    if (!(placement instanceof RandomSpreadStructurePlacement spread)) {
                        throw new IllegalStateException("Atlas target has unsupported placement: " + stage.tag());
                    }
                    grouped.computeIfAbsent(spread, ignored -> new LinkedHashSet<>()).add(holder);
                }
            }
            placements = grouped.entrySet().stream()
                    .map(entry -> {
                        List<Holder<Structure>> structures = List.copyOf(entry.getValue());
                        return new Placement(entry.getKey(), structures,
                                oracle.supports(level, entry.getKey(), structures));
                    }).toList();
            ring = 0;
            placementIndex = 0;
            dx = 0;
            dz = 0;
            placementMatched = false;
            best = null;
            bestDistance = Double.MAX_VALUE;
            candidate = null;
            oraclePlans.clear();
        }

        private void nextStage() {
            stageIndex++;
            if (stageIndex == 1 && request.themedRings() > 0 && request.cached()) {
                found = request.cachedResult();
                reusedCache = true;
                done = true;
            } else {
                enterStage();
            }
        }

        private void acceptStageResult(BlockPos result) {
            if (stages.get(stageIndex).nearbyOnly()) {
                long x = (long) result.getX() - request.origin().getX();
                long z = (long) result.getZ() - request.origin().getZ();
                if (x * x + z * z > 256L * 256L) {
                    nextStage();
                    return;
                }
                nearbyCanonical = true;
            }
            found = result;
            done = true;
        }

        /** True while the current candidate waits for its worker result. */
        private boolean examineCandidate(int serverTick) {
            while (structureIndex < candidatePlacement.structures().size()) {
                Holder<Structure> holder = candidatePlacement.structures().get(structureIndex);
                if (candidatePlacement.planned()) {
                    PlannedCandidate key = new PlannedCandidate(candidatePlacement, candidate);
                    CompletableFuture<AtlasCandidateOracle.Plan> plan =
                            oraclePlans.computeIfAbsent(key,
                                    next -> oracle.evaluate(level, holder, next.chunk()));
                    if (!plan.isDone()) return true;
                    AtlasCandidateOracle.Plan verdict = plan.getNow(null);
                    oraclePlans.remove(key);
                    AtlasCandidateOracle.Outcome outcome = verdict == null
                            ? AtlasCandidateOracle.Outcome.FALLBACK
                            : verdict.outcome();
                    if (outcome == AtlasCandidateOracle.Outcome.RETRY) return true;
                    // A chunk loaded or scheduled after the no-NBT scan may
                    // contain a saved start. Its normal presence path wins.
                    boolean tracked = AtlasCandidateOracle.tracked(level, candidate)
                            || server.getResourceManager() != resourcesAtStart
                            || verdict == null
                            || AtlasCandidateOracle.saveEpochChanged(level, candidate,
                                    verdict.saveEpoch());
                    if (!tracked && outcome == AtlasCandidateOracle.Outcome.ABSENT_NEW) {
                        plannedAbsent++;
                        structureIndex++;
                        continue;
                    }
                    if (!tracked && outcome == AtlasCandidateOracle.Outcome.POSSIBLE_NEW
                            && level.getServer().getWorldGenSettings().options().generateStructures()
                            && candidatePlacement.value().isStructureChunk(
                                    source.getGeneratorState(), candidate.x(), candidate.z())) {
                        plannedPossible++;
                        // The audited virgin candidate has already passed the
                        // exact structure-generation call. A second chunk
                        // request repeats that work on the server thread.
                        matched(candidatePlacement.value().getLocatePos(candidate));
                        return false;
                    }
                    plannedFallback++;
                }
                StructureCheckResult presence = level.structureManager().checkStructurePresence(
                        candidate, holder.value(), candidatePlacement.value(), false);
                if (presence == StructureCheckResult.START_NOT_PRESENT) {
                    structureIndex++;
                    continue;
                }
                if (presence == StructureCheckResult.START_PRESENT
                        && !isEmberV6(holder)) {
                    matched(candidatePlacement.value().getLocatePos(candidate));
                    return false;
                }
                requestChunk(serverTick);
                return false;
            }
            candidate = null;
            candidatePlacement = null;
            return false;
        }

        private void requestChunk(int serverTick) {
            ticket = new Ticket(TICKET_TYPE, TICKET_LEVEL);
            source.addTicket(ticket, candidate);
            requestedAt = serverTick;
            dispatched = false;
        }

        private void reportPlan() {
            if (plannedAbsent + plannedPossible + plannedFallback == 0) return;
            GemsMod.LOGGER.info(
                    "Atlas planner lookup target={} absent={} possible={} fallback={} elapsedMs={}",
                    request.targetId(), plannedAbsent, plannedPossible, plannedFallback,
                    (System.nanoTime() - startedNanos) / 1_000_000);
        }

        private void prefetchCandidateRing() {
            if (!candidatePlacement.planned()) return;
            Holder<Structure> holder = candidatePlacement.structures().getFirst();
            oraclePlans.computeIfAbsent(new PlannedCandidate(candidatePlacement, candidate),
                    next -> oracle.evaluate(level, holder, next.chunk()));
            // The Estate's dedicated worker surveys one footprint at a time,
            // with at most four queued candidates. Results are consumed by the
            // scan cursor in their original ring order.
            int nextX = dx;
            int nextZ = dz;
            int spacing = candidatePlacement.value().spacing();
            while (nextX <= ring && oraclePlans.size() < 4) {
                if (nextX == -ring || nextX == ring
                        || nextZ == -ring || nextZ == ring) {
                    ChunkPos upcoming = candidatePlacement.value().getPotentialStructureChunk(seed,
                            baseChunkX + spacing * nextX,
                            baseChunkZ + spacing * nextZ);
                    oraclePlans.computeIfAbsent(new PlannedCandidate(candidatePlacement, upcoming),
                            next -> oracle.evaluate(level, holder, next.chunk()));
                }
                nextZ++;
                if (nextZ > ring) {
                    nextZ = -ring;
                    nextX++;
                }
            }
        }

        private void pollRequest(int serverTick) {
            if (serverTick - requestedAt > MAX_SEARCH_TICKS) {
                fail("Atlas chunk request timed out");
                return;
            }
            if (!dispatched) {
                if (serverTick == requestedAt) return;
                ChunkPos pos = candidate;
                pending = CompletableFuture.supplyAsync(() -> source.getChunkFuture(
                        pos.x(), pos.z(), ChunkStatus.STRUCTURE_STARTS, false), worker)
                        .thenCompose(future -> future);
                dispatched = true;
                return;
            }
            if (!pending.isDone()) return;
            ChunkResult<ChunkAccess> result;
            try {
                result = pending.join();
            } catch (RuntimeException error) {
                fail("Atlas chunk future failed: " + error);
                return;
            }
            if (!result.isSuccess()) {
                fail("Atlas chunk result failed: " + result.getError());
                return;
            }
            ChunkAccess chunk = result.orElse(null);
            if (chunk == null) {
                fail("Atlas chunk result was empty");
                return;
            }
            Holder<Structure> holder = candidatePlacement.structures().get(structureIndex);
            StructureStart start = level.structureManager().getStartForStructure(holder.value(), chunk);
            if (!releaseTicket()) {
                fail("Atlas ticket cleanup failed");
                return;
            }
            if (start != null && start.isValid()) {
                if (isEmberV6(holder)) {
                    EmberV6Start site = emberV6Start(start);
                    if (site == null) {
                        structureIndex++;
                        return;
                    }
                    EmberV6ReadinessState.Verdict status = EmberV6Readiness.verdict(
                            level, candidate, site.root(), site.arch(), holder.value());
                    if (status == EmberV6ReadinessState.Verdict.READY) {
                        matched(site.arch());
                    } else if (status == EmberV6ReadinessState.Verdict.UNKNOWN) {
                        provisionalMatch(site.arch());
                    } else {
                        structureIndex++;
                    }
                } else {
                    matched(candidatePlacement.value().getLocatePos(start.getChunkPos()));
                }
            } else {
                structureIndex++;
            }
        }

        private static boolean isEmberV6(Holder<Structure> holder) {
            return holder.unwrapKey().map(key -> key.identifier().equals(EMBER_V6))
                    .orElse(false);
        }

        private record EmberV6Start(BlockPos root, BlockPos arch) { }

        private static EmberV6Start emberV6Start(StructureStart start) {
            if (start.getPieces().size() != 1
                    || !(start.getPieces().getFirst() instanceof PoolElementStructurePiece piece)
                    || !(piece.getElement() instanceof SinglePoolElement single)
                    || !single.getTemplateLocation().equals(EMBER_V6)) return null;
            BlockPos arch = StructureTemplate.transform(new BlockPos(39, 5, 70),
                    Mirror.NONE, piece.getRotation(), BlockPos.ZERO)
                    .offset(piece.getPosition());
            BlockPos root = StructureTemplate.transform(new BlockPos(23, 4, 23),
                    Mirror.NONE, piece.getRotation(), BlockPos.ZERO)
                    .offset(piece.getPosition());
            var box = piece.getBoundingBox();
            if (arch.getX() < box.minX() || arch.getX() > box.maxX()
                    || arch.getY() < box.minY() || arch.getY() > box.maxY()
                    || arch.getZ() < box.minZ() || arch.getZ() > box.maxZ()
                    || !box.isInside(root)) return null;
            return new EmberV6Start(root, arch);
        }

        private void provisionalMatch(BlockPos result) {
            double distance = request.origin().distSqr(result);
            if (distance < unknownBestDistance) {
                unknownBestDistance = distance;
                unknownBest = result;
            }
            // Keep searching this ring and later rings for a recorded READY.
            candidate = null;
            candidatePlacement = null;
        }

        private void matched(BlockPos result) {
            double distance = request.origin().distSqr(result);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = result;
            }
            placementMatched = true;
            candidate = null;
            candidatePlacement = null;
        }

        private void fail(String message) {
            failure = message;
            done = true;
            GemsMod.LOGGER.warn("Atlas lookup failed: {}", message);
        }

        private void cancel() {
            cancelled = true;
            for (CompletableFuture<AtlasCandidateOracle.Plan> plan : oraclePlans.values())
                plan.cancel(false);
            oraclePlans.clear();
            releaseTicket();
        }

        private boolean releaseTicket() {
            if (pending != null) pending.cancel(false);
            pending = null;
            dispatched = false;
            if (ticket == null) return true;
            try {
                storage.removeTicket(ticket, candidate);
                ticket = null;
                return true;
            } catch (Throwable error) {
                GemsMod.LOGGER.error("Atlas ticket release failed at {}", candidate, error);
                return false;
            }
        }
    }
}
