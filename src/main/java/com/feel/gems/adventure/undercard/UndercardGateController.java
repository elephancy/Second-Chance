package com.feel.gems.adventure.undercard;

import com.feel.gems.GemsMod;
import com.feel.gems.adventure.worldgen.ScopedChunkSave;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/** Durable, one-cell gate opener for saved, inspected Undercard structures. */
public final class UndercardGateController {
    private static final int MAX_PENDING = 32;
    private static final int MAX_SHELL_CHUNKS = 128;
    private static final int MAX_REFERENCED_STARTS = 24;
    private static final int MAX_SITES_PER_TICK = 4;
    private static final int SHELL_LOAD_BATCH = 12;
    private static final int SHELL_LOAD_TIMEOUT_TICKS = 20 * 90;
    private static final TicketType SHELL_TICKET = new TicketType(20L * 240 + 17,
            TicketType.FLAG_LOADING);
    private static final ResourceKey<Structure> SITE = ResourceKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "undercard"));
    private static final ResourceKey<Structure> V5_TRIAL = ResourceKey.create(
            Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "undercard_v5_trial"));
    private static final List<ResourceKey<Structure>> SITES = List.of(SITE, V5_TRIAL);
    private static final AttachmentType<String> RECEIPT = AttachmentRegistry.createPersistent(
            Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "undercard_gate_receipt_v1"),
            Codec.STRING);
    private static final Map<ServerLevel, LinkedHashMap<BlockPos, Pending>> PENDING =
            new HashMap<>();
    private static boolean initialized;

    private static final class Pending {
        final ResourceKey<Structure> site;
        final ChunkPos startChunk;
        final BlockPos root;
        final BoundingBox box;
        final List<ChunkPos> shellChunks;
        final Set<ChunkPos> shellTickets = new HashSet<>();
        final Map<ChunkPos, CompletableFuture<?>> shellLoads = new HashMap<>();
        long nextTick;
        int backoff = 40;
        int shellIssued;
        long shellLoadStarted = -1;
        boolean shellFinalized;
        boolean shellBlocked;
        boolean referencesResolved;
        final boolean v5;
        final Set<ChunkPos> shellTouched = new HashSet<>();

        Pending(ResourceKey<Structure> site, ChunkPos startChunk,
                BlockPos root, BoundingBox box, boolean v5, long nextTick) {
            this.site = site;
            this.startChunk = startChunk;
            this.root = root.immutable();
            this.box = box;
            this.shellChunks = shellChunks(box, root, startChunk);
            this.v5 = v5;
            this.nextTick = nextTick;
        }
    }

    private enum Outcome { COMPLETE, WAIT, BLOCKED }

    private UndercardGateController() { }

    /** Register after site block/type and new-chunk stamp registration. */
    public static void init() {
        if (initialized) return;
        initialized = true;
        UndercardShellCustody.init();
        ServerChunkEvents.CHUNK_GENERATE.register((level, chunk) -> {
            if (level.dimension() != Level.OVERWORLD) return;
            if (!hasVenueReference(level, chunk)) return;
            wakeNearLoadedChunk(level, chunk);
        });
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, newlyGenerated) -> {
            if (level.dimension() != Level.OVERWORLD) return;
            LinkedHashMap<BlockPos, Pending> waking = PENDING.get(level);
            if (waking != null) {
                for (Pending pending : waking.values()) {
                    if (Math.abs(chunk.getPos().x() - (pending.root.getX() >> 4)) <= 9
                            && Math.abs(chunk.getPos().z() - (pending.root.getZ() >> 4)) <= 9) {
                        pending.nextTick = Math.min(pending.nextTick,
                                level.getGameTime() + 20);
                    }
                }
            }
            // A generated venue can become visible through a neighboring
            // chunk's saved reference before its start chunk fires a load
            // callback. Rediscover it from that reference without loading
            // any chunk or scanning the world each tick.
            for (ResourceKey<Structure> site : SITES) {
                Structure structure = level.registryAccess().lookupOrThrow(
                        Registries.STRUCTURE).getValue(site);
                if (structure == null) continue;
                var references = chunk.getAllReferences().get(structure);
                queueStart(level, chunk, structure, site);
                if (references == null) continue;
                for (long reference : references) {
                    int x = (int) reference;
                    int z = (int) (reference >>> 32);
                    LevelChunk startChunk = level.getChunkSource().getChunkNow(x, z);
                    if (startChunk != null && startChunk != chunk) {
                        queueStart(level, startChunk, structure, site);
                    }
                }
            }
            wakeNearLoadedChunk(level, chunk);
        });
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            LinkedHashMap<BlockPos, Pending> sites = PENDING.get(level);
            if (sites == null || sites.isEmpty()) return;
            if (level.getGameTime() % 20 != 0) return;
            for (int work = 0; work < MAX_SITES_PER_TICK; work++) {
                Map.Entry<BlockPos, Pending> due = null;
                for (Map.Entry<BlockPos, Pending> entry : sites.entrySet()) {
                    if (entry.getValue().nextTick <= level.getGameTime()) {
                        due = entry;
                        break;
                    }
                }
                if (due == null) break;
                Pending pending = due.getValue();
                Outcome outcome;
                try {
                    outcome = inspectAndOpen(level, pending);
                } catch (RuntimeException failure) {
                    GemsMod.LOGGER.error("Undercard gate remains sealed at {}",
                            pending.root, failure);
                    outcome = Outcome.WAIT;
                }
                sites.remove(due.getKey());
                if (outcome == Outcome.WAIT) {
                    pending.nextTick = level.getGameTime()
                            + (pending.shellFinalized ? pending.backoff : 5);
                    if (pending.shellFinalized)
                        pending.backoff = Math.min(1_200, pending.backoff * 2);
                    sites.put(pending.root, pending);
                } else {
                    releaseShellTickets(level, pending);
                    if (outcome == Outcome.BLOCKED)
                        GemsMod.LOGGER.warn("Undercard gate stays sealed at {}", pending.root);
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            PENDING.forEach((level, sites) -> sites.values().forEach(
                    pending -> releaseShellTickets(level, pending)));
            PENDING.clear();
            UndercardV5ShellFinalizer.clear();
        });
    }

    private static void queueStart(ServerLevel level, LevelChunk startChunk,
                                   Structure structure, ResourceKey<Structure> site) {
        StructureStart start = startChunk.getStartForStructure(structure);
        if (start == null || !start.isValid()
                || !UndercardStructure.oneAuthoredPiece(start.getPieces())) return;
        BoundingBox box = start.getPieces().getFirst().getBoundingBox();
        BlockPos root = new BlockPos(box.getCenter().getX(), box.minY() + 22,
                box.getCenter().getZ());
        LinkedHashMap<BlockPos, Pending> sites = PENDING.computeIfAbsent(level,
                ignored -> new LinkedHashMap<>());
        if (sites.containsKey(root)) return;
        // Keep custody and player exposure until this site resolves. Dropping
        // an older pending site could later permit a delayed in-session repair.
        if (sites.size() >= MAX_PENDING) return;
        try {
            sites.put(root, new Pending(site, startChunk.getPos(), root,
                    box, UndercardV5ShellFinalizer.isV5(start),
                    level.getGameTime() + 20));
        } catch (IllegalArgumentException invalidBounds) {
            GemsMod.LOGGER.warn("Undercard start has invalid bounds at {}", root,
                    invalidBounds);
        }
    }

    private static Outcome inspectAndOpen(ServerLevel level, Pending pending) {
        if (level.noSave()) return Outcome.BLOCKED;
        if (pending.shellLoadStarted >= 0
                && level.getGameTime() - pending.shellLoadStarted > 20 * 180)
            return Outcome.BLOCKED;
        LevelChunk rootChunk = level.getChunkSource().getChunkNow(
                pending.root.getX() >> 4, pending.root.getZ() >> 4);
        String prior = rootChunk == null ? null : rootChunk.getAttached(RECEIPT);
        // A completed site is never reopened after a player's later edits.
        if (prior != null && prior.endsWith(":COMPLETE")) return Outcome.COMPLETE;
        if (pending.v5 && !pending.shellFinalized) {
            if (pending.shellBlocked) return Outcome.BLOCKED;
            Outcome loaded = loadShellFootprint(level, pending);
            if (loaded != Outcome.COMPLETE) return loaded;
            if (!pending.referencesResolved) {
                Outcome references = addReferencedStarts(level, pending);
                if (references != Outcome.COMPLETE) return references;
                loaded = loadShellFootprint(level, pending);
                if (loaded != Outcome.COMPLETE) return loaded;
            }
        }
        LevelChunk startChunk = level.getChunkSource().getChunkNow(
                pending.startChunk.x(), pending.startChunk.z());
        rootChunk = level.getChunkSource().getChunkNow(
                pending.root.getX() >> 4, pending.root.getZ() >> 4);
        if (startChunk == null || rootChunk == null) return Outcome.WAIT;
        // The root may have been loaded by the bounded ticket set above.
        // Use its actual persisted receipt for crash recovery.
        prior = rootChunk.getAttached(RECEIPT);
        if (prior != null && prior.endsWith(":COMPLETE")) return Outcome.COMPLETE;
        Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValue(pending.site);
        if (structure == null) return Outcome.BLOCKED;
        StructureStart start = startChunk.getStartForStructure(structure);
        if (start == null || !start.isValid()) return Outcome.BLOCKED;
        if (UndercardV5ShellFinalizer.isV5(start)) {
            if (pending.shellBlocked) return Outcome.BLOCKED;
            if (!pending.shellFinalized) {
                UndercardV5ShellFinalizer.Result finalization =
                        finalizeFreshShell(level, pending);
                if (finalization == UndercardV5ShellFinalizer.Result.WAIT)
                    return Outcome.WAIT;
                if (finalization != UndercardV5ShellFinalizer.Result.READY)
                    return Outcome.BLOCKED;
            }
        }
        UndercardSiteIntegrity.Inspection inspection =
                UndercardSiteIntegrity.inspectLoaded(level, start, structure);
        if (inspection.status() == UndercardSiteIntegrity.Status.WAIT) return Outcome.WAIT;
        if (inspection.status() != UndercardSiteIntegrity.Status.READY
                || !pending.root.equals(inspection.root())) {
            GemsMod.LOGGER.warn("Undercard {} rejected at {}: {}", pending.site.identifier(),
                    pending.root, inspection.reason());
            return Outcome.BLOCKED;
        }
        BlockPos gate = inspection.gate();
        LevelChunk gateChunk = level.getChunkSource().getChunkNow(gate.getX() >> 4,
                gate.getZ() >> 4);
        if (gateChunk == null) return Outcome.WAIT;
        String key = "undercard-v1:" + pending.root.asLong() + ":" + gate.asLong();
        if (prior != null && !(key + ":PREPARED").equals(prior)) return Outcome.BLOCKED;
        var sealed = UndercardBlocks.GATE.defaultBlockState();
        var opened = level.getBlockState(gate.below());
        if (!opened.is(Blocks.LADDER)) return Outcome.BLOCKED;
        var current = level.getBlockState(gate);
        if (!current.equals(sealed) && !(prior != null && current.equals(opened))) {
            return Outcome.BLOCKED;
        }
        ChunkPos rootPos = rootChunk.getPos();
        if (prior == null) {
            rootChunk.setAttached(RECEIPT, key + ":PREPARED");
            ScopedChunkSave.flushLoaded(level, Set.of(rootPos));
        }
        if (current.equals(sealed)) {
            if (!level.setBlock(gate, opened, 2)) return Outcome.WAIT;
            ScopedChunkSave.flushLoaded(level, Set.of(gateChunk.getPos()));
        }
        rootChunk.setAttached(RECEIPT, key + ":COMPLETE");
        ScopedChunkSave.flushLoaded(level, Set.of(rootPos));
        GemsMod.LOGGER.info("Undercard safe entry opened at {}", gate);
        return Outcome.COMPLETE;
    }

    private static UndercardV5ShellFinalizer.Result finalizeFreshShell(
            ServerLevel level, Pending pending) {
        LevelChunk startChunk = level.getChunkSource().getChunkNow(
                pending.startChunk.x(), pending.startChunk.z());
        if (startChunk == null) return UndercardV5ShellFinalizer.Result.WAIT;
        Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValue(pending.site);
        if (structure == null) return UndercardV5ShellFinalizer.Result.BLOCKED;
        StructureStart start = startChunk.getStartForStructure(structure);
        if (start == null || !start.isValid()) return UndercardV5ShellFinalizer.Result.BLOCKED;
        if (!UndercardV5ShellFinalizer.isV5(start)) {
            pending.shellFinalized = true;
            return UndercardV5ShellFinalizer.Result.READY;
        }
        UndercardV5ShellFinalizer.Result result =
                UndercardV5ShellFinalizer.finish(level, start, structure,
                        pending.shellTouched);
        if (result == UndercardV5ShellFinalizer.Result.READY) {
            // Save repaired shell chunks while their exact tickets still hold
            // them loaded; only then can the gate receipt be considered.
            if (!pending.shellTouched.isEmpty()) {
                ScopedChunkSave.flushLoaded(level, pending.shellTouched);
                pending.shellTouched.clear();
            }
            pending.shellFinalized = true;
        } else if (result == UndercardV5ShellFinalizer.Result.BLOCKED) {
            pending.shellBlocked = true;
        }
        return result;
    }

    private static List<ChunkPos> shellChunks(BoundingBox box, BlockPos root,
                                               ChunkPos owner) {
        List<ChunkPos> chunks = new ArrayList<>();
        // The shell comparison needs a two-block ring; integrity also needs
        // the eight-block approach and the nearby 64-block hazard core.
        for (int cx = (box.minX() - 8) >> 4; cx <= (box.maxX() + 8) >> 4; cx++) {
            for (int cz = (box.minZ() - 8) >> 4; cz <= (box.maxZ() + 8) >> 4; cz++) {
                chunks.add(new ChunkPos(cx, cz));
            }
        }
        for (int cx = (root.getX() - 64) >> 4;
             cx <= (root.getX() + 64) >> 4; cx++) {
            for (int cz = (root.getZ() - 64) >> 4;
                 cz <= (root.getZ() + 64) >> 4; cz++) {
                int x0 = cx << 4, z0 = cz << 4;
                int dx = Math.max(0, Math.max(x0 - root.getX(),
                        root.getX() - (x0 + 15)));
                int dz = Math.max(0, Math.max(z0 - root.getZ(),
                        root.getZ() - (z0 + 15)));
                ChunkPos position = new ChunkPos(cx, cz);
                if (dx * dx + dz * dz <= 64 * 64 && !chunks.contains(position))
                    chunks.add(position);
            }
        }
        if (!chunks.contains(owner)) chunks.add(owner);
        if (chunks.size() > MAX_SHELL_CHUNKS)
            throw new IllegalArgumentException("Undercard shell exceeds bounded chunk footprint");
        return chunks;
    }

    private static Outcome addReferencedStarts(ServerLevel level, Pending pending) {
        Set<Long> references = new HashSet<>();
        Structure self = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValue(pending.site);
        if (self == null) return Outcome.BLOCKED;
        for (int cx = pending.box.minX() >> 4; cx <= pending.box.maxX() >> 4; cx++) {
            for (int cz = pending.box.minZ() >> 4; cz <= pending.box.maxZ() >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) return Outcome.WAIT;
                for (var entry : chunk.getAllReferences().entrySet()) {
                    if (entry.getKey() == self) continue;
                    references.addAll(entry.getValue());
                    if (references.size() > MAX_REFERENCED_STARTS) return Outcome.BLOCKED;
                }
            }
        }
        for (long reference : references.stream().sorted().toList()) {
            ChunkPos position = new ChunkPos(ChunkPos.getX(reference),
                    ChunkPos.getZ(reference));
            if (!pending.shellChunks.contains(position)) pending.shellChunks.add(position);
        }
        if (pending.shellChunks.size() > MAX_SHELL_CHUNKS + MAX_REFERENCED_STARTS)
            return Outcome.BLOCKED;
        pending.referencesResolved = true;
        return Outcome.COMPLETE;
    }

    private static Outcome loadShellFootprint(ServerLevel level, Pending pending) {
        long now = level.getGameTime();
        if (pending.shellLoadStarted < 0) pending.shellLoadStarted = now;
        if (now - pending.shellLoadStarted > SHELL_LOAD_TIMEOUT_TICKS)
            return Outcome.BLOCKED;
        if (pending.shellLoads.values().stream().anyMatch(
                future -> future.isCompletedExceptionally() || future.isCancelled()))
            return Outcome.BLOCKED;
        pending.shellLoads.entrySet().removeIf(entry -> entry.getValue().isDone());
        int issued = 0;
        while (pending.shellIssued < pending.shellChunks.size()
                && issued < SHELL_LOAD_BATCH) {
            ChunkPos chunk = pending.shellChunks.get(pending.shellIssued++);
            pending.shellTickets.add(chunk);
            pending.shellLoads.put(chunk, level.getChunkSource()
                    .addTicketAndLoadWithRadius(SHELL_TICKET, chunk, 0));
            issued++;
        }
        if (pending.shellIssued < pending.shellChunks.size()
                || !pending.shellLoads.isEmpty()) return Outcome.WAIT;
        for (ChunkPos chunk : pending.shellChunks) {
            if (level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) == null)
                return Outcome.WAIT;
        }
        return Outcome.COMPLETE;
    }

    private static void releaseShellTickets(ServerLevel level, Pending pending) {
        for (ChunkPos chunk : pending.shellTickets)
            level.getChunkSource().removeTicketWithRadius(SHELL_TICKET, chunk, 0);
        pending.shellTickets.clear();
        pending.shellLoads.clear();
    }

    private static boolean hasVenueReference(ServerLevel level, LevelChunk chunk) {
        for (ResourceKey<Structure> site : SITES) {
            Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                    .getValue(site);
            if (structure == null) continue;
            StructureStart start = chunk.getStartForStructure(structure);
            if (start != null && start.isValid()) return true;
            var references = chunk.getAllReferences().get(structure);
            if (references != null && !references.isEmpty()) return true;
        }
        return false;
    }

    private static void wakeNearLoadedChunk(ServerLevel level, LevelChunk chunk) {
        LinkedHashMap<BlockPos, Pending> waking = PENDING.get(level);
        if (waking == null) return;
        for (Pending pending : waking.values()) {
            if (!pending.shellFinalized && !pending.shellBlocked
                    && Math.abs(chunk.getPos().x() - (pending.root.getX() >> 4)) <= 9
                    && Math.abs(chunk.getPos().z() - (pending.root.getZ() >> 4)) <= 9) {
                pending.nextTick = Math.min(pending.nextTick, level.getGameTime() + 1);
            }
        }
    }
}
