package com.feel.gems.adventure.worldgen.site;

import com.feel.gems.GemsMod;
import com.feel.gems.adventure.worldgen.ScopedChunkSave;
import com.mojang.serialization.Codec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Server-thread, bounded transaction for new-chunk approaches and site gates.
 * A persistent PREPARED receipt precedes any block write. A site-specific
 * marker scanner must call {@link #submit} again after restart; requests in
 * memory alone are never considered durable.
 */
public final class GuardedSiteScheduler {
    private static final int MAX_CHUNKS = 24;
    private static final int MAX_WRITES = 128;
    private static final int MAX_SCAN_CELLS = 150_000;
    private static final int RETRY_START_TICKS = 40;
    private static final int RETRY_MAX_TICKS = 1_200;
    private static final boolean DIMENSION_DIAGNOSTICS = Boolean.getBoolean(
            "gems.dimensionPlacementDiagnostics");
    private static final AttachmentType<String> RECEIPT = AttachmentRegistry.createPersistent(
            Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "site_receipt_v1"), Codec.STRING);
    private static final Map<ServerLevel, LinkedHashMap<BlockPos, Request>> PENDING =
            new HashMap<>();
    private static boolean initialized;

    private GuardedSiteScheduler() { }

    public record Gate(BlockPos pos, BlockState sealed, BlockState open) {
        public Gate {
            if (pos == null || sealed == null || open == null || sealed.equals(open)) {
                throw new IllegalArgumentException("Invalid guarded site gate");
            }
            pos = pos.immutable();
        }
    }

    public enum Outcome { WAIT, COMPLETE, BLOCKED, NEEDS_CONTROLLED_SUBSTRATE }

    /** Register once during mod initialization, before generation events. */
    public static void init() {
        if (initialized) return;
        initialized = true;
        GuardedNewChunkSiteAccess.init();
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, newlyGenerated) -> {
            LinkedHashMap<BlockPos, Request> queued = PENDING.get(level);
            if (queued == null || queued.isEmpty()) return;
            long wake = level.getGameTime() + 20;
            for (Request request : queued.values()) {
                if (request.footprint.contains(chunk.getPos())) {
                    request.nextAttemptTick = Math.min(request.nextAttemptTick, wake);
                }
            }
        });
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            LinkedHashMap<BlockPos, Request> queued = PENDING.get(level);
            if (queued == null || queued.isEmpty() || level.getGameTime() % 20 != 0) return;
            Map.Entry<BlockPos, Request> first = null;
            for (Map.Entry<BlockPos, Request> entry : queued.entrySet()) {
                if (entry.getValue().nextAttemptTick <= level.getGameTime()) {
                    first = entry;
                    break;
                }
            }
            if (first == null) return;
            Request request = first.getValue();
            Outcome outcome;
            try {
                outcome = place(level, request);
            } catch (RuntimeException failure) {
                GemsMod.LOGGER.error("Guarded site {} remains sealed at {}",
                        request.definition.id(), request.candidate.anchor(), failure);
                outcome = Outcome.BLOCKED;
            }
            queued.remove(first.getKey());
            if (outcome == Outcome.WAIT) {
                request.nextAttemptTick = level.getGameTime() + request.backoffTicks;
                request.backoffTicks = Math.min(request.backoffTicks * 2, RETRY_MAX_TICKS);
                queued.put(first.getKey(), request);
            } else {
                if (request.observer != null) {
                    try {
                        request.observer.accept(outcome);
                    } catch (RuntimeException failure) {
                        GemsMod.LOGGER.error("Guarded site verdict observer failed at {}",
                                request.candidate.anchor(), failure);
                    }
                }
                if (outcome != Outcome.COMPLETE) {
                    GemsMod.LOGGER.warn("Guarded site {} stays sealed at {}: {}",
                            request.definition.id(), request.candidate.anchor(), outcome);
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> PENDING.clear());
    }

    /**
     * Queue a deterministic request discovered from a saved root marker.
     * The marker must remain sealed until this scheduler returns COMPLETE.
     */
    public static void submit(ServerLevel level, SiteDefinition definition,
                              SiteCandidate candidate, SitePlacementPlan plan,
                              SiteAccess access, List<Gate> gates) {
        submit(level, definition, candidate, plan, access, gates, null);
    }

    /** Terminal outcomes may update a site's durable discovery status. */
    public static void submit(ServerLevel level, SiteDefinition definition,
                              SiteCandidate candidate, SitePlacementPlan plan,
                              SiteAccess access, List<Gate> gates,
                              Consumer<Outcome> observer) {
        if (gates.isEmpty()) throw new IllegalArgumentException("A guarded site needs a sealed gate");
        Request request = new Request(definition, candidate, plan, access,
                List.copyOf(gates), observer, level.getGameTime() + 1);
        PENDING.computeIfAbsent(level, ignored -> new LinkedHashMap<>())
                .putIfAbsent(candidate.anchor(), request);
    }

    private static Outcome place(ServerLevel level, Request request) {
        SiteDefinition definition = request.definition;
        SiteCandidate candidate = request.candidate;
        SitePlacementPlan plan = request.plan;
        SiteAccess access = request.access;
        if (level.noSave() || access.seam() != SiteAccess.Seam.GUARDED_NEW_CHUNKS
                || !access.dimension().equals(level.dimension())) return Outcome.BLOCKED;
        if (!inside(candidate.bounds(definition), candidate.anchor())) return Outcome.BLOCKED;
        List<ChunkPos> footprint = candidate.chunks(definition);
        if (footprint.size() > MAX_CHUNKS || plan.writes().size() > MAX_WRITES
                || scanCells(candidate.bounds(definition)) > MAX_SCAN_CELLS) {
            return Outcome.NEEDS_CONTROLLED_SUBSTRATE;
        }
        // Once a chunk is FULL, its new-generation stamp does not prove that
        // a player never replaced natural terrain or a fluid. Late commits
        // therefore only fill verified air; carving belongs to generation.
        if (plan.writes().values().stream().anyMatch(write ->
                write.replace() != SitePlacementPlan.Replace.AIR)) {
            return Outcome.NEEDS_CONTROLLED_SUBSTRATE;
        }
        for (Gate gate : request.gates) {
            if (!inside(candidate.bounds(definition), gate.pos())) return Outcome.BLOCKED;
            if (plan.writes().containsKey(gate.pos())) return Outcome.BLOCKED;
            if (plan.requirements().stream().anyMatch(requirement ->
                    requirement.pos().equals(gate.pos()))) return Outcome.BLOCKED;
            if (!footprint.contains(new ChunkPos(gate.pos().getX() >> 4,
                    gate.pos().getZ() >> 4))) return Outcome.BLOCKED;
        }
        for (ChunkPos chunk : footprint) {
            if (!access.canInspectChunk(chunk)) return Outcome.WAIT;
            if (!access.isNewChunk(chunk)) return Outcome.BLOCKED;
        }
        ChunkPos rootPos = new ChunkPos(candidate.anchor().getX() >> 4,
                candidate.anchor().getZ() >> 4);
        LevelChunk root = level.getChunkSource().getChunkNow(rootPos.x(), rootPos.z());
        if (root == null || !access.isNewChunk(rootPos)) return Outcome.WAIT;
        String key = key(definition, candidate, plan, request.gates);
        String receipt = root.getAttached(RECEIPT);
        if ((key + ":COMPLETE").equals(receipt)) return Outcome.COMPLETE;
        boolean resuming = (key + ":PREPARED").equals(receipt);
        if (receipt != null && !resuming) return Outcome.BLOCKED;
        SitePreflight.Result preflight = SitePreflight.check(definition, candidate,
                plan, access, resuming);
        if (preflight.status() == SitePreflight.Status.NEEDS_SCHEDULER) {
            return Outcome.NEEDS_CONTROLLED_SUBSTRATE;
        }
        if (preflight.status() != SitePreflight.Status.READY) {
            if (DIMENSION_DIAGNOSTICS && definition.dimension()
                    == net.minecraft.world.level.Level.NETHER) {
                GemsMod.LOGGER.warn("Dimension gate preflight {} at {}: {} near {}",
                        definition.id(), candidate.anchor(), preflight.reason(), preflight.at());
            }
            return preflight.reason() == SitePreflight.Reason.CHUNK_UNAVAILABLE
                    || preflight.reason() == SitePreflight.Reason.PROTECTION_UNPROVEN
                    ? Outcome.WAIT : Outcome.BLOCKED;
        }
        for (Gate gate : request.gates) {
            BlockState current = access.state(gate.pos());
            if (access.hasBlockEntity(gate.pos())
                    || (!current.equals(gate.sealed())
                            && !(resuming && current.equals(gate.open())))) {
                return Outcome.BLOCKED;
            }
        }
        if (!resuming) {
            root.setAttached(RECEIPT, key + ":PREPARED");
            ScopedChunkSave.flushLoaded(level, Set.of(rootPos));
        }
        List<Map.Entry<BlockPos, SitePlacementPlan.Write>> writes = new ArrayList<>(
                plan.writes().entrySet());
        writes.sort(Comparator.comparingInt((Map.Entry<BlockPos,
                SitePlacementPlan.Write> entry) -> entry.getKey().getX())
                .thenComparingInt(entry -> entry.getKey().getZ())
                .thenComparingInt(entry -> entry.getKey().getY()));
        for (Map.Entry<BlockPos, SitePlacementPlan.Write> entry : writes) {
            BlockPos pos = entry.getKey();
            BlockState state = entry.getValue().state();
            if (access.state(pos).equals(state)) continue;
            if (!level.setBlock(pos, state, 2)) return Outcome.BLOCKED;
        }
        Set<ChunkPos> bodyChunks = chunksOf(plan.writes().keySet());
        if (!bodyChunks.isEmpty()) ScopedChunkSave.flushLoaded(level, bodyChunks);
        for (Gate gate : request.gates) {
            if (access.state(gate.pos()).equals(gate.open())) continue;
            if (!level.setBlock(gate.pos(), gate.open(), 2)) return Outcome.BLOCKED;
        }
        ScopedChunkSave.flushLoaded(level, chunksOf(request.gates.stream()
                .map(Gate::pos).toList()));
        root.setAttached(RECEIPT, key + ":COMPLETE");
        ScopedChunkSave.flushLoaded(level, Set.of(rootPos));
        GemsMod.LOGGER.info("Guarded site {} completed at {} across {} chunks and {} cells",
                definition.id(), candidate.anchor(), footprint.size(), writes.size());
        return Outcome.COMPLETE;
    }

    private static long scanCells(List<BoundingBox> bounds) {
        long total = 0;
        for (BoundingBox box : bounds) {
            total += (long) box.getXSpan() * box.getYSpan() * box.getZSpan();
            if (total > MAX_SCAN_CELLS) return total;
        }
        return total;
    }

    private static boolean inside(List<BoundingBox> bounds, BlockPos pos) {
        return bounds.stream().anyMatch(box -> box.isInside(pos));
    }

    private static Set<ChunkPos> chunksOf(Iterable<BlockPos> positions) {
        java.util.Set<ChunkPos> result = new java.util.HashSet<>();
        for (BlockPos pos : positions) {
            result.add(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4));
        }
        return Set.copyOf(result);
    }

    private static String key(SiteDefinition definition, SiteCandidate candidate,
                              SitePlacementPlan plan, List<Gate> gates) {
        StringBuilder manifest = new StringBuilder(definition.id().toString())
                .append('|').append(definition.version()).append('|')
                .append(definition.dimension()).append('|').append(definition.setting())
                .append('|').append(definition.minAnchorY()).append(',')
                .append(definition.maxAnchorY()).append(',').append(definition.maxSlope())
                .append(',').append(definition.minWaterDepth()).append(',')
                .append(definition.minSupportDepth()).append(',')
                .append(definition.minRoofThickness()).append(',')
                .append(definition.minHazardDistance()).append('|')
                .append(candidate.anchor().getX()).append(',')
                .append(candidate.anchor().getY()).append(',')
                .append(candidate.anchor().getZ()).append('|')
                .append(candidate.quarterTurns());
        definition.allowedBiomes().stream().map(Object::toString).sorted()
                .forEach(biome -> manifest.append("|biome=").append(biome));
        definition.volumes().forEach(volume -> manifest.append("|volume=").append(volume));
        plan.writes().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator
                        .comparingInt((BlockPos pos) -> pos.getX())
                        .thenComparingInt(pos -> pos.getY())
                        .thenComparingInt(pos -> pos.getZ())))
                .forEach(entry -> manifest.append('|').append(entry.getKey())
                        .append('=').append(entry.getValue().state())
                        .append('/').append(entry.getValue().replace())
                        .append('/').append(entry.getValue().expected()));
        plan.requirements().stream()
                .sorted(Comparator.comparingInt((SitePlacementPlan.Requirement requirement) ->
                        requirement.pos().getX())
                        .thenComparingInt(requirement -> requirement.pos().getY())
                        .thenComparingInt(requirement -> requirement.pos().getZ()))
                .forEach(requirement -> manifest.append("|require=")
                        .append(requirement.pos()).append('/')
                        .append(requirement.condition()).append('/')
                        .append(requirement.expected()));
        gates.stream().sorted(Comparator.comparingInt((Gate gate) -> gate.pos().getX())
                        .thenComparingInt(gate -> gate.pos().getY())
                        .thenComparingInt(gate -> gate.pos().getZ()))
                .forEach(gate -> manifest.append('|').append(gate.pos())
                        .append('=').append(gate.sealed()).append('>').append(gate.open()));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(manifest.toString().getBytes(StandardCharsets.UTF_8));
            return definition.id() + "@" + candidate.anchor() + ":"
                    + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static final class Request {
        private final SiteDefinition definition;
        private final SiteCandidate candidate;
        private final SitePlacementPlan plan;
        private final SiteAccess access;
        private final List<Gate> gates;
        private final Consumer<Outcome> observer;
        private final Set<ChunkPos> footprint;
        private long nextAttemptTick;
        private int backoffTicks = RETRY_START_TICKS;

        private Request(SiteDefinition definition, SiteCandidate candidate,
                        SitePlacementPlan plan, SiteAccess access, List<Gate> gates,
                        Consumer<Outcome> observer, long nextAttemptTick) {
            this.definition = definition;
            this.candidate = candidate;
            this.plan = plan;
            this.access = access;
            this.gates = gates;
            this.observer = observer;
            this.footprint = Set.copyOf(candidate.chunks(definition));
            this.nextAttemptTick = nextAttemptTick;
        }
    }
}
