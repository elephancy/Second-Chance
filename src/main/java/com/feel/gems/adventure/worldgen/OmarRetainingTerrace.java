package com.feel.gems.adventure.worldgen;

import com.feel.gems.GemsMod;
import com.feel.gems.adventure.DomainBlocks;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;

/** The one-time, saved-terrain retaining face below a newly generated Omar v5 frontage. */
public final class OmarRetainingTerrace {
    private static final ResourceKey<Structure> PUBLIC = ResourceKey.create(
            Registries.STRUCTURE, id("omar_lost_quarter"));
    private static final Set<Identifier> EXACT_PIECES = Set.of(
            id("omar_lost_quarter_wadi_v5"),
            id("omar_exchange_court_caravan_v3"),
            id("omar_memory_loft_caravan_v3"),
            id("omar_return_oasis_caravan_v3"));
    private static final AttachmentType<String> NEW_MARKER = AttachmentRegistry.createPersistent(
            id("omar_retaining_marker_v1"), Codec.STRING);
    private static final AttachmentType<String> JOURNAL = AttachmentRegistry.createPersistent(
            id("omar_retaining_journal_v1"), Codec.STRING);
    private static final AttachmentType<String> FINISHED = AttachmentRegistry.createPersistent(
            id("omar_retaining_finished_v1"), Codec.STRING);
    private static final Map<LevelChunk, Fresh> FRESH = new ConcurrentHashMap<>();
    private static final Map<ServerLevel, Map<ChunkPos, Fresh>> FRESH_HISTORY =
            new ConcurrentHashMap<>();
    private static final Set<LevelChunk> JOURNALED = ConcurrentHashMap.newKeySet();
    private static final Map<LevelChunk, Set<BlockPos>> PENDING_WRITES =
            new ConcurrentHashMap<>();
    private static final Map<BlockPos, Set<ChunkPos>> ACTIVE_FOOTPRINT = new HashMap<>();
    private static final Map<BlockPos, BoundingBox> ACTIVE_ENVELOPE = new HashMap<>();
    private static final Map<ServerLevel, LinkedHashSet<BlockPos>> RECOVERY = new HashMap<>();
    private static final ThreadLocal<Boolean> AUTHORING =
            ThreadLocal.withInitial(() -> false);
    private static final int FACE_DEPTH = 13;
    private static final int FACE_DROP = 9;
    private static final int FACE_HEIGHT = FACE_DROP + 1;
    private static final int MAX_CHANGED = 512;
    private static final int BASE_ROUTE_WRITES = 31;
    private static final int MAX_FRESH_MUTATIONS = 2048;
    private static boolean initialized;

    private OmarRetainingTerrace() { }

    static void init() {
        if (initialized) return;
        initialized = true;
        ServerChunkEvents.CHUNK_GENERATE.register((level, chunk) -> {
            if (level.dimension() != Level.OVERWORLD) return;
            Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                    .getValue(PUBLIC);
            if (structure == null || !chunk.getAllReferences().containsKey(structure)) return;
            Fresh fresh = new Fresh();
            FRESH_HISTORY.computeIfAbsent(level, ignored -> new ConcurrentHashMap<>())
                    .put(chunk.getPos(), fresh);
            FRESH.put(chunk, fresh);
            if (containsMarker(level, chunk)) chunk.setAttached(NEW_MARKER, "v1");
        });
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, newlyGenerated) -> {
            if (level.dimension() != Level.OVERWORLD) return;
            Map<ChunkPos, Fresh> history = FRESH_HISTORY.get(level);
            if (history != null) {
                Fresh fresh = history.get(chunk.getPos());
                if (fresh != null) FRESH.put(chunk, fresh);
            }
            String journal = chunk.getAttached(JOURNAL);
            if (isPending(journal)) {
                JOURNALED.add(chunk);
                Journal parsed = parseJournal(level, chunk, journal);
                if (parsed != null) PENDING_WRITES.put(chunk, parsed.cells());
                BlockPos marker = parsed == null ? markerFromJournal(journal)
                        : parsed.marker();
                if (marker != null) RECOVERY.computeIfAbsent(level,
                        ignored -> new LinkedHashSet<>()).add(marker);
            }
        });
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            FRESH.remove(chunk);
            JOURNALED.remove(chunk);
            PENDING_WRITES.remove(chunk);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            FRESH.clear();
            FRESH_HISTORY.clear();
            JOURNALED.clear();
            PENDING_WRITES.clear();
            ACTIVE_FOOTPRINT.clear();
            ACTIVE_ENVELOPE.clear();
            RECOVERY.clear();
        });
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            if (level.dimension() != Level.OVERWORLD || level.getGameTime() % 20 != 0)
                return;
            LinkedHashSet<BlockPos> queued = RECOVERY.get(level);
            if (queued == null || queued.isEmpty()) return;
            Iterator<BlockPos> next = queued.iterator();
            BlockPos marker = next.next();
            next.remove();
            Recovery outcome = recoverCompleted(level, marker);
            if (outcome == Recovery.WAIT) queued.add(marker);
            if (outcome == Recovery.BLOCKED) GemsMod.LOGGER.warn(
                    "Omar terrace journal stays pending at {}; recovery proof failed", marker);
        });
    }

    /** Called at LevelChunk.setBlockState HEAD, including same-state attempts. */
    public static void recordMutation(LevelChunk chunk, BlockPos pos) {
        Fresh fresh = FRESH.get(chunk);
        if (fresh == null && !JOURNALED.contains(chunk)) return;
        if (AUTHORING.get()) return;
        if (fresh != null) fresh.record(pos);
        if (!JOURNALED.contains(chunk)) return;
        String saved = chunk.getAttached(JOURNAL);
        if (!isPending(saved)) return;
        Set<BlockPos> protectedCells = PENDING_WRITES.get(chunk);
        if (protectedCells != null && !protectedCells.contains(pos)) return;
        chunk.setAttached(JOURNAL, saved.contains("|pending|")
                ? saved.replace("|pending|", "|dirty|")
                : saved.substring(0, saved.length() - "pending".length()) + "dirty");
        GemsMod.LOGGER.warn("Omar pending terrace write cell changed at {} in {}",
                pos, chunk.getPos());
        JOURNALED.remove(chunk);
        PENDING_WRITES.remove(chunk);
    }

    static void beginAuthoredWrite() { AUTHORING.set(true); }

    static void endAuthoredWrite() { AUTHORING.remove(); }

    static void completed(ServerLevel level, BlockPos marker) {
        Set<ChunkPos> footprint = ACTIVE_FOOTPRINT.get(marker);
        BoundingBox envelope = ACTIVE_ENVELOPE.get(marker);
        if (footprint == null || envelope == null) return;
        LevelChunk markerChunk = loaded(level, chunkPos(marker));
        if (markerChunk == null) throw new IllegalStateException("Omar marker unloaded before journal finish");
        for (ChunkPos pos : footprint) {
            LevelChunk chunk = loaded(level, pos);
            Journal journal = chunk == null ? null
                    : parseJournal(level, chunk, chunk.getAttached(JOURNAL));
            if (!validPending(journal, marker, envelope)) {
                throw new IllegalStateException("Omar terrace journal changed before completion at " + pos);
            }
        }
        // Persist this single finalization point first. A crash after it may
        // leave mixed cleanup tags, but can never cause another terrain write.
        markerChunk.setAttached(FINISHED, "v1");
        ScopedChunkSave.flushLoaded(level, Set.of(chunkPos(marker)));
        for (ChunkPos pos : footprint) {
            LevelChunk chunk = loaded(level, pos);
            chunk.setAttached(JOURNAL, chunk.getAttached(JOURNAL)
                    .replace("|pending|", "|done|"));
            JOURNALED.remove(chunk);
            PENDING_WRITES.remove(chunk);
        }
        ScopedChunkSave.flushLoaded(level, footprint);
        ACTIVE_FOOTPRINT.remove(marker);
        ACTIVE_ENVELOPE.remove(marker);
        Map<ChunkPos, Fresh> history = FRESH_HISTORY.get(level);
        for (ChunkPos pos : footprint) {
            if (history != null) history.remove(pos);
            FRESH.remove(loaded(level, pos));
        }
    }

    static Result add(ServerLevel level, GuardedDistrictBridges.Plan plan,
                      BlockPos marker, Direction outward) {
        return add(level, plan, marker, outward, false);
    }

    private static Result add(ServerLevel level, GuardedDistrictBridges.Plan plan,
                      BlockPos marker, Direction outward, boolean recovery) {
        LevelChunk markerChunk = loaded(level, chunkPos(marker));
        if (markerChunk == null) return Result.WAIT;
        // The terrace may only alter a chunk stamped during this generation.
        if (!"v1".equals(markerChunk.getAttached(NEW_MARKER))) return Result.BLOCKED;
        Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValue(PUBLIC);
        if (structure == null) return Result.BLOCKED;
        StructureStart selected = null;
        ChunkPos ownerPos = null;
        for (long reference : markerChunk.getReferencesForStructure(structure)) {
            ChunkPos pos = new ChunkPos(ChunkPos.getX(reference), ChunkPos.getZ(reference));
            LevelChunk owner = loaded(level, pos);
            if (owner == null) return Result.WAIT;
            StructureStart start = owner.getStartForStructure(structure);
            if (start == null || !start.isValid()) return Result.BLOCKED;
            PoolElementStructurePiece root = exactRoot(start, marker, outward);
            if (root == null) continue;
            if (selected != null) return Result.BLOCKED;
            selected = start;
            ownerPos = pos;
        }
        if (selected == null || ownerPos == null) return Result.BLOCKED;
        PoolElementStructurePiece root = exactRoot(selected, marker, outward);
        BoundingBox box = root.getBoundingBox();
        int minX = outward.getAxis() == Direction.Axis.Z ? box.minX() :
                (outward == Direction.WEST ? box.minX() - 13 : box.maxX());
        int maxX = outward.getAxis() == Direction.Axis.Z ? box.maxX() :
                (outward == Direction.EAST ? box.maxX() + 13 : box.minX());
        int minZ = outward.getAxis() == Direction.Axis.X ? box.minZ() :
                (outward == Direction.NORTH ? box.minZ() - 13 : box.maxZ());
        int maxZ = outward.getAxis() == Direction.Axis.X ? box.maxZ() :
                (outward == Direction.SOUTH ? box.maxZ() + 13 : box.minZ());
        // The authorized envelope is the full 47-wide, 14-deep approach and
        // sixteen Y levels, including the existing two-wide descending stair.
        if (maxX - minX + 1 > 47 || maxZ - minZ + 1 > 47
                || box.minY() - FACE_DROP < level.getMinY()
                || box.minY() + 6 >= level.getMaxY()) return Result.BLOCKED;
        BoundingBox envelope = new BoundingBox(minX, box.minY() - FACE_DROP,
                minZ, maxX, box.minY() + 6, maxZ);
        Set<ChunkPos> footprint = new LinkedHashSet<>();
        Set<BlockPos> savedPositions = new HashSet<>();
        boolean sawPending = false;
        int protectedCount = 0;
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                ChunkPos pos = new ChunkPos(cx, cz);
                LevelChunk chunk = loaded(level, pos);
                if (chunk == null) return Result.WAIT;
                footprint.add(pos);
                String saved = chunk.getAttached(JOURNAL);
                if (saved != null) {
                    Journal journal = parseJournal(level, chunk, saved);
                    if (!validPending(journal, marker, envelope)) return Result.BLOCKED;
                    protectedCount += journal.cells().size();
                    savedPositions.addAll(journal.cells());
                    sawPending = true;
                }
                // Every referenced foreign start is vetoed, including an
                // unavailable owner. No neighboring structure is approximated.
                for (var entry : chunk.getAllReferences().entrySet()) {
                    for (long reference : entry.getValue()) {
                        if (entry.getKey() != structure
                                || ChunkPos.getX(reference) != ownerPos.x()
                                || ChunkPos.getZ(reference) != ownerPos.z()) {
                            return Result.BLOCKED;
                        }
                    }
                }
                plan.requireNewChunk(new BlockPos(cx << 4, box.minY() + 6, cz << 4));
            }
        }
        if (sawPending) {
            for (ChunkPos pos : footprint) {
                LevelChunk chunk = loaded(level, pos);
                if (!validPending(parseJournal(level, chunk,
                        chunk.getAttached(JOURNAL)), marker, envelope)) return Result.BLOCKED;
            }
            if (protectedCount > MAX_CHANGED || !savedPositions.contains(marker))
                return Result.BLOCKED;
        } else {
            for (ChunkPos pos : footprint) {
                if (FRESH.get(loaded(level, pos)) == null) {
                    GemsMod.LOGGER.warn("Omar terrace has no live generation history at {}",
                            pos);
                    return Result.BLOCKED;
                }
            }
        }
        if (!recovery && !OmarWadiAccess.adaptFreshSlope(level, plan, marker, outward))
            return Result.BLOCKED;
        int faceCells = 0;
        for (int lateral = 0; lateral < 47; lateral++) {
            int axis = outward.getAxis() == Direction.Axis.Z
                    ? box.minX() + lateral : box.minZ() + lateral;
            int markerAxis = outward.getAxis() == Direction.Axis.Z
                    ? marker.getX() : marker.getZ();
            // Keep the existing two-wide stair and five blocks either side
            // wholly clear, including its headroom and natural landing.
            if (Math.abs(axis - markerAxis) <= 5) continue;
            for (int rise = 0; rise < FACE_HEIGHT; rise++) {
                int y = box.minY() - FACE_DROP + rise;
                BlockState authored = palette(lateral, rise);
                for (int depth = 1; depth <= FACE_DEPTH; depth++) {
                    BlockPos pos = face(box, outward, lateral, depth, y);
                    LevelChunk chunk = loaded(level, chunkPos(pos));
                    BlockState current = chunk.getBlockState(pos);
                    if (current.isAir() || current.is(Blocks.SHORT_GRASS)
                            || current.is(Blocks.DEAD_BUSH)) continue;
                    // Keep a natural bush and its support intact. A pending
                    // journal may already own that support cell; honor its
                    // exact saved write set during recovery.
                    if (chunk.getBlockState(pos.above()).is(Blocks.DEAD_BUSH)
                            && !savedPositions.contains(pos)) continue;
                    // The v5 template already carries some Lost Clay out in
                    // this apron. Keep that authored face unless a pending
                    // journal explicitly included its exact cell.
                    if (current.is(DomainBlocks.LOST_CLAY)
                            && !savedPositions.contains(pos)) continue;
                    BlockPos exposed = pos.relative(outward);
                    LevelChunk outer = loaded(level, chunkPos(exposed));
                    // The last face cell is inside the 14-deep envelope, but
                    // its exposure probe can be one block beyond it.
                    if (outer == null) continue;
                    BlockState outside = outer.getBlockState(exposed);
                    if (!outside.isAir() && !outside.is(Blocks.SHORT_GRASS)) continue;
                    for (StructurePiece piece : selected.getPieces()) {
                        if (piece != root && piece.getBoundingBox().isInside(pos)) {
                            return Result.BLOCKED;
                        }
                    }
                    if (!natural(current) && !current.equals(authored)) return Result.BLOCKED;
                    if (recovery && !current.equals(authored)) return Result.BLOCKED;
                    if (!current.getFluidState().isEmpty()
                            || level.getBlockEntity(pos) != null) return Result.BLOCKED;
                    plan.veneer(pos, current, authored);
                    faceCells++;
                    break;
                }
            }
        }
        if (plan.writePositions().size() > MAX_CHANGED)
            return Result.BLOCKED;
        if (sawPending && !recovery && !savedPositions.equals(plan.writePositions()))
            return Result.BLOCKED;
        if (sawPending && protectedCount < faceCells + BASE_ROUTE_WRITES)
            return Result.BLOCKED;
        if (recovery && !sawPending) return Result.BLOCKED;
        if (!sawPending) {
            // Generated structure/terrain updates elsewhere in a referenced
            // chunk do not invalidate this apron. Any post-generation touch
            // to a cell this transaction would overwrite does, including a
            // same-state player edit or command. A saved stamp alone cannot
            // supply the transient generation-session record after restart.
            Set<BlockPos> writes = plan.writePositions();
            for (ChunkPos pos : footprint) {
                Fresh fresh = FRESH.get(loaded(level, pos));
                if (fresh == null) {
                    GemsMod.LOGGER.warn("Omar terrace has no live generation history at {}",
                            pos);
                    return Result.BLOCKED;
                }
                String veto = fresh.veto(writes);
                if (veto != null) {
                    GemsMod.LOGGER.warn("Omar terrace write-cell freshness veto at {}: {}",
                            pos, veto);
                    return Result.BLOCKED;
                }
            }
        }
        if (!recovery) {
            GuardedDistrictBridges.PlaceResult preview =
                    GuardedDistrictBridges.preview(level, plan);
            if (preview == GuardedDistrictBridges.PlaceResult.WAIT) return Result.WAIT;
            if (preview != GuardedDistrictBridges.PlaceResult.COMPLETE) return Result.BLOCKED;
        }
        if (!sawPending) {
            // Save the exact footprint before any block write. An interrupted
            // save leaves no terrace cells; a partial journal fails closed.
            Set<BlockPos> writes = plan.writePositions();
            for (ChunkPos pos : footprint) {
                LevelChunk chunk = loaded(level, pos);
                Set<BlockPos> protectedCells = Set.copyOf(writes.stream()
                        .filter(write -> chunkPos(write).equals(pos)).toList());
                chunk.setAttached(JOURNAL,
                        journalFor(level, pos, marker, protectedCells));
                JOURNALED.add(chunk);
                PENDING_WRITES.put(chunk, protectedCells);
            }
            ScopedChunkSave.flushLoaded(level, footprint);
        }
        ACTIVE_FOOTPRINT.put(marker.immutable(), Set.copyOf(footprint));
        ACTIVE_ENVELOPE.put(marker.immutable(), envelope);
        GemsMod.LOGGER.info("Omar v5 retaining terrace planned at {} with {} exact face cells",
                marker, faceCells);
        return Result.READY;
    }

    private static Recovery recoverCompleted(ServerLevel level, BlockPos marker) {
        LevelChunk markerChunk = loaded(level, chunkPos(marker));
        if (markerChunk == null) return Recovery.WAIT;
        if ("v1".equals(markerChunk.getAttached(FINISHED))) return Recovery.DONE;
        BlockState state = markerChunk.getBlockState(marker);
        if (state.is(Blocks.OBSERVER)) return Recovery.HANDLED_BY_ACCESS;
        if (!state.is(DomainBlocks.LOST_CLAY)) return Recovery.BLOCKED;
        for (Direction outward : Direction.Plane.HORIZONTAL) {
            GuardedDistrictBridges.Plan proof = new GuardedDistrictBridges.Plan(
                    "Omar recovery", marker);
            Result candidate = add(level, proof, marker, outward, true);
            if (candidate == Result.WAIT) return Recovery.WAIT;
            if (candidate != Result.READY) continue;
            if (!completedStairAndGate(level, marker, outward)) return Recovery.BLOCKED;
            completed(level, marker);
            GemsMod.LOGGER.info("Omar v5 retaining terrace journal recovered at {}", marker);
            return Recovery.DONE;
        }
        return Recovery.BLOCKED;
    }

    private static boolean completedStairAndGate(ServerLevel level,
            BlockPos marker, Direction outward) {
        if (!level.getBlockState(marker.above()).is(Blocks.CHISELED_RED_SANDSTONE)
                || !level.getBlockState(marker.above(3)).is(DomainBlocks.LOST_TILE))
            return false;
        int baseY = marker.getY() - 1;
        for (int distance = 1; distance <= 13; distance++) {
            int firstSide = distance <= 7 ? -1 : distance == 8 ? 0
                    : distance <= 12 ? 1 : 0;
            for (int side = firstSide; side <= firstSide + 1; side++) {
                BlockPos floor = marker.relative(outward, distance)
                        .offset(-outward.getStepZ() * side, 0,
                                outward.getStepX() * side)
                        .atY(baseY + 5 - distance);
                if (!level.getBlockState(floor).is(DomainBlocks.LOST_TILE)
                        || !level.getBlockState(floor.above()).isAir()
                        || !level.getBlockState(floor.above(2)).isAir()) return false;
            }
        }
        for (int side = -1; side <= 0; side++) {
            BlockPos gate = marker.offset(-outward.getStepZ() * side, 0,
                    outward.getStepX() * side);
            if (!level.getBlockState(gate.above(4)).isAir()
                    || !level.getBlockState(gate.above(5)).isAir()) return false;
        }
        return true;
    }

    private static PoolElementStructurePiece exactRoot(StructureStart start,
            BlockPos marker, Direction outward) {
        if (start.getPieces().size() != EXACT_PIECES.size()) return null;
        Set<Identifier> seen = new HashSet<>();
        PoolElementStructurePiece root = null;
        for (StructurePiece piece : start.getPieces()) {
            if (!(piece instanceof PoolElementStructurePiece pooled)
                    || !(pooled.getElement() instanceof SinglePoolElement single)) return null;
            Identifier name = single.getTemplateLocation();
            if (!EXACT_PIECES.contains(name) || !seen.add(name)) return null;
            if (name.equals(id("omar_lost_quarter_wadi_v5"))) root = pooled;
        }
        if (root == null || seen.size() != EXACT_PIECES.size()) return null;
        BoundingBox box = root.getBoundingBox();
        if (box.getXSpan() != 47 || box.getZSpan() != 47
                || marker.getY() != box.minY() + 1
                || marker.getX() < box.minX() || marker.getX() > box.maxX()
                || marker.getZ() < box.minZ() || marker.getZ() > box.maxZ()) return null;
        boolean edge = switch (outward) {
            case NORTH -> marker.getZ() == box.minZ();
            case SOUTH -> marker.getZ() == box.maxZ();
            case WEST -> marker.getX() == box.minX();
            case EAST -> marker.getX() == box.maxX();
            default -> false;
        };
        return edge ? root : null;
    }

    private static BlockPos face(BoundingBox box, Direction outward,
                                 int lateral, int depth, int y) {
        return switch (outward) {
            case NORTH -> new BlockPos(box.minX() + lateral, y, box.minZ() - depth);
            case SOUTH -> new BlockPos(box.minX() + lateral, y, box.maxZ() + depth);
            case WEST -> new BlockPos(box.minX() - depth, y, box.minZ() + lateral);
            case EAST -> new BlockPos(box.maxX() + depth, y, box.minZ() + lateral);
            default -> throw new IllegalArgumentException("Horizontal entrance required");
        };
    }

    private static BlockState palette(int lateral, int rise) {
        if (rise == 0 || rise == 8 || rise == 9)
            return DomainBlocks.LOST_TILE.defaultBlockState();
        int arch = Math.floorMod(lateral - 2, 7);
        if (arch <= 2) return Blocks.CHISELED_RED_SANDSTONE.defaultBlockState();
        if (rise == 7 || (rise == 6 && (arch == 3 || arch == 6)))
            return Blocks.CUT_SANDSTONE.defaultBlockState();
        if (rise >= 2 && rise <= 4 && (lateral & 1) == 0)
            return DomainBlocks.LOST_TILE.defaultBlockState();
        return DomainBlocks.LOST_CLAY.defaultBlockState();
    }

    static boolean natural(BlockState state) {
        return state.is(Blocks.STONE) || state.is(Blocks.DIRT)
                || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ANDESITE) || state.is(Blocks.DIORITE)
                || state.is(Blocks.GRANITE) || state.is(Blocks.SANDSTONE)
                || state.is(Blocks.SAND);
    }

    private static LevelChunk loaded(ServerLevel level, ChunkPos pos) {
        return level.getChunkSource().getChunkNow(pos.x(), pos.z());
    }

    private static ChunkPos chunkPos(BlockPos pos) {
        return new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static boolean containsMarker(ServerLevel level, LevelChunk chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (!section.maybeHas(state -> state.is(Blocks.OBSERVER))) continue;
            int y0 = level.getMinY() + sectionIndex * 16;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!state.is(Blocks.OBSERVER)
                                || !state.getValue(ObserverBlock.FACING)
                                        .getAxis().isHorizontal()) continue;
                        BlockPos marker = new BlockPos(chunk.getPos().getMinBlockX() + x,
                                y0 + y, chunk.getPos().getMinBlockZ() + z);
                        if (chunk.getBlockState(marker.above())
                                        .is(Blocks.CHISELED_RED_SANDSTONE)
                                && chunk.getBlockState(marker.above(3))
                                        .is(DomainBlocks.LOST_TILE)) return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isPending(String journal) {
        return journal != null && (journal.contains("|pending|")
                || journal.endsWith("|pending"));
    }

    private static BlockPos markerFromJournal(String journal) {
        if (!isPending(journal)) return null;
        return parseMarker(journal.substring(0, journal.indexOf('|')));
    }

    private static BlockPos parseMarker(String value) {
        String[] coords = value.split(",", -1);
        if (coords.length != 3) return null;
        try {
            return new BlockPos(Integer.parseInt(coords[0]),
                    Integer.parseInt(coords[1]), Integer.parseInt(coords[2]));
        } catch (NumberFormatException malformed) {
            return null;
        }
    }

    private static String journalFor(ServerLevel level, ChunkPos chunk,
                                     BlockPos marker, Set<BlockPos> cells) {
        int[] packed = cells.stream().mapToInt(pos -> {
            if (!chunkPos(pos).equals(chunk) || pos.getY() < level.getMinY()
                    || pos.getY() >= level.getMaxY()) {
                throw new IllegalArgumentException("Omar journal cell outside its chunk: " + pos);
            }
            return ((pos.getY() - level.getMinY()) << 8)
                    | ((pos.getZ() & 15) << 4) | (pos.getX() & 15);
        }).sorted().toArray();
        StringBuilder result = new StringBuilder(journalId(marker)).append("|pending|v1:");
        if (packed.length == 0) return result.append('-').toString();
        for (int index = 0; index < packed.length; index++) {
            if (index > 0) result.append(',');
            String hex = Integer.toHexString(packed[index]);
            result.append("0".repeat(Math.max(0, 5 - hex.length()))).append(hex);
        }
        return result.toString();
    }

    private static Journal parseJournal(ServerLevel level, LevelChunk chunk, String saved) {
        if (saved == null || saved.length() > 4096) return null;
        String[] parts = saved.split("\\|", -1);
        if (parts.length != 3 || !parts[2].startsWith("v1:")) return null;
        BlockPos marker = parseMarker(parts[0]);
        if (marker == null || (!parts[1].equals("pending")
                && !parts[1].equals("done") && !parts[1].equals("dirty"))) return null;
        String payload = parts[2].substring(3);
        Set<BlockPos> cells = new HashSet<>();
        if (!payload.equals("-")) {
            String[] encoded = payload.split(",", -1);
            if (encoded.length == 0 || encoded.length > MAX_CHANGED) return null;
            int previous = -1;
            for (String item : encoded) {
                if (!item.matches("[0-9a-f]{5}")) return null;
                int packed = Integer.parseInt(item, 16);
                int localY = packed >>> 8;
                if (packed <= previous || localY >= level.getMaxY() - level.getMinY())
                    return null;
                previous = packed;
                cells.add(new BlockPos(chunk.getPos().getMinBlockX() + (packed & 15),
                        level.getMinY() + localY,
                        chunk.getPos().getMinBlockZ() + ((packed >>> 4) & 15)));
            }
        }
        return new Journal(marker, parts[1], Set.copyOf(cells));
    }

    private static boolean validPending(Journal journal, BlockPos marker,
                                        BoundingBox envelope) {
        if (journal == null || !journal.status().equals("pending")
                || !journal.marker().equals(marker)) return false;
        for (BlockPos pos : journal.cells()) {
            if (!envelope.isInside(pos)) return false;
        }
        return true;
    }

    private static String journalId(BlockPos marker) {
        return marker.getX() + "," + marker.getY() + "," + marker.getZ();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, path);
    }

    /** Volatile evidence only: saved chunks never recreate this permission. */
    private static final class Fresh {
        private final Set<BlockPos> mutations = new HashSet<>();
        private boolean overflow;

        synchronized void record(BlockPos pos) {
            if (overflow) return;
            if (mutations.size() >= MAX_FRESH_MUTATIONS) {
                overflow = true;
                mutations.clear();
            } else {
                mutations.add(pos.immutable());
            }
        }

        synchronized String veto(Set<BlockPos> writes) {
            if (overflow) return "mutation record overflow";
            for (BlockPos pos : mutations) {
                if (writes.contains(pos)) return "intervening setBlockState at " + pos;
            }
            return null;
        }
    }

    private record Journal(BlockPos marker, String status, Set<BlockPos> cells) { }

    enum Result { READY, WAIT, BLOCKED }
    private enum Recovery { WAIT, HANDLED_BY_ACCESS, DONE, BLOCKED }
}
