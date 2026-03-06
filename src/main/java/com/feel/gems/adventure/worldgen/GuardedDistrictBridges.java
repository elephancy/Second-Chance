package com.feel.gems.adventure.worldgen;

import com.feel.gems.GemsMod;
import com.feel.gems.adventure.DomainBlocks;
import com.mojang.serialization.Codec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Finishes new district crossings and Ali's dry-land entry through
 * guarded, loaded new chunks. It never loads a chunk or clears terrain.
 */
public final class GuardedDistrictBridges {
    private static final ResourceKey<Structure> ALI_STRUCTURE = ResourceKey.create(
            Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "ali_splitstone_cloister"));
    private static final int ALI_V5_APPROACH_LENGTH = 20;
    private static final int ALI_V5_BANK_START = 16;
    private static final int ALI_V5_HALF_WIDTH = 4;
    private static final AttachmentType<String> NEW_CHUNK = AttachmentRegistry.createPersistent(
            Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "district_bridge_new_chunk_v4"),
            Codec.STRING);
    private static final String STAMP = "v4";
    private static final Map<ServerLevel, Pending> PENDING = new HashMap<>();
    private static final Map<ServerLevel, Long> LAST_COMMIT_TICK = new HashMap<>();

    private GuardedDistrictBridges() { }

    /** Only chunks generated after this bridge system was installed carry this stamp. */
    public static boolean hasNewChunkStamp(LevelChunk chunk) {
        return STAMP.equals(chunk.getAttached(NEW_CHUNK));
    }

    public static void init() {
        ServerChunkEvents.CHUNK_GENERATE.register((level, chunk) -> {
            if (level.dimension() == Level.OVERWORLD) chunk.setAttached(NEW_CHUNK, STAMP);
        });
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, newlyGenerated) -> {
            if (level.dimension() != Level.OVERWORLD) return;
            Pending pending = PENDING.computeIfAbsent(level, ignored -> new Pending());
            discover(level, chunk, pending);
            for (Request request : pending.requests.values()) {
                if (request.footprint().contains(chunk.getPos())) {
                    pending.ready.add(request.marker());
                }
            }
        });
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            Pending pending = PENDING.get(level);
            if (pending == null) return;
            pending.requests.entrySet().removeIf(entry -> {
                boolean unloading = chunkPos(entry.getKey()).equals(chunk.getPos());
                if (unloading) pending.ready.remove(entry.getKey());
                return unloading;
            });
        });
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            Pending pending = PENDING.get(level);
            if (pending == null || pending.ready.isEmpty()) return;
            // Each crossing synchronizes its loaded footprint up to three
            // times. Spread simultaneous requests across ticks.
            Iterator<BlockPos> next = pending.ready.iterator();
            BlockPos marker = next.next();
            next.remove();
            Request request = pending.requests.get(marker);
            if (request == null) return;
            try {
                PlaceResult result = attempt(level, request);
                if (result == PlaceResult.WAIT) return;
                if (result == PlaceResult.DEFERRED) {
                    pending.ready.add(marker);
                    return;
                }
                pending.requests.remove(marker);
                if (result == PlaceResult.BLOCKED && pending.logged.add(marker)) {
                    GemsMod.LOGGER.warn("{} crossing stays sealed at {}; its loaded, newly generated route is unavailable",
                            request.kind().label, marker);
                }
            } catch (RuntimeException failure) {
                pending.requests.remove(marker);
                GemsMod.LOGGER.error("{} crossing stays sealed after a save or placement failure at {}",
                        request.kind().label, marker, failure);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            PENDING.clear();
            LAST_COMMIT_TICK.clear();
        });
    }

    private static void discover(ServerLevel level, LevelChunk chunk, Pending pending) {
        LevelChunkSection[] sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (!section.maybeHas(state -> state.is(DomainBlocks.ALI_BRIDGE_REQUEST)
                    || state.is(DomainBlocks.YEHIA_BRIDGE_REQUEST))) continue;
            int y0 = level.getMinY() + index * 16;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        Kind kind = Kind.of(state.getBlock());
                        if (kind == null) continue;
                        BlockPos marker = new BlockPos(chunk.getPos().getMinBlockX() + x,
                                y0 + y, chunk.getPos().getMinBlockZ() + z);
                        Direction outward = state.getValue(HorizontalDirectionalBlock.FACING);
                        Request request = new Request(kind, marker, outward);
                        pending.requests.put(marker, request);
                        pending.ready.add(marker);
                    }
                }
            }
        }
    }

    private static PlaceResult attempt(ServerLevel level, Request request) {
        if (request.kind() == Kind.ALI) {
            Loaded loaded = new Loaded(level);
            AliVersion version = aliVersion(level, loaded, request);
            if (version == AliVersion.WAIT) return PlaceResult.WAIT;
            if (version == AliVersion.BLOCKED) return PlaceResult.BLOCKED;
            return attemptAliV5(level, loaded, request);
        }
        Manifest manifest = request.manifest();
        Plan plan = new Plan(request.kind().label, request.marker());
        BlockState pendingMarker = request.kind().request.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, request.outward());
        plan.finish(request.marker(), pendingMarker, request.kind().finished);
        manifest.exterior().forEach(plan::exterior);
        manifest.clearance().forEach(plan::requireAir);
        manifest.gate().forEach(pos -> plan.gate(pos,
                request.kind().seal.defaultBlockState()));
        List<BlockPos> bankFeet = manifest.bank();
        // Leaf litter can occupy the dry west-bank landing without
        // obstructing a player. Preserve it instead of clearing it.
        plan.passableDryStanding(bankFeet.get(1));
        plan.atLeastPassableDryStanding(bankFeet, 2);
        plan.atLeastFluid(manifest.terrainWitnesses(), request.kind().minimumWitnesses);
        return place(level, plan);
    }

    private enum AliVersion { CURRENT, WAIT, BLOCKED }

    private static AliVersion aliVersion(ServerLevel level, Loaded loaded,
                                         Request request) {
        LevelChunk markerChunk = loaded.chunk(chunkPos(request.marker()));
        if (markerChunk == null) return AliVersion.WAIT;
        Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValue(ALI_STRUCTURE);
        if (structure == null) return AliVersion.BLOCKED;
        for (long reference : markerChunk.getReferencesForStructure(structure)) {
            LevelChunk startChunk = loaded.chunk(new ChunkPos(
                    ChunkPos.getX(reference), ChunkPos.getZ(reference)));
            if (startChunk == null) return AliVersion.WAIT;
            StructureStart start = startChunk.getStartForStructure(structure);
            if (start == null || !start.isValid()) return AliVersion.BLOCKED;
            if (AliDryArrivalStructure.containsCurrentV5Template(start)) {
                if (AliDryArrivalStructure.isCurrentV5Marker(start,
                        request.marker(), request.outward())) return AliVersion.CURRENT;
            }
        }
        return AliVersion.BLOCKED;
    }

    /** V5 opens only its authored seal when an existing dry route reaches a bank. */
    private static PlaceResult attemptAliV5(ServerLevel level, Loaded loaded,
                                             Request request) {
        if (level.dimension() != Level.OVERWORLD || level.noSave())
            return PlaceResult.BLOCKED;
        for (ChunkPos pos : request.v5Footprint()) {
            LevelChunk chunk = loaded.chunk(pos);
            if (chunk == null) return PlaceResult.WAIT;
            if (!hasNewChunkStamp(chunk)) return PlaceResult.BLOCKED;
        }
        List<BlockPos> route = aliV5DryRoute(loaded, request);
        if (route.isEmpty()) return PlaceResult.BLOCKED;
        Plan plan = new Plan("Ali v5", request.marker());
        BlockState pendingMarker = request.kind().request.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, request.outward());
        plan.finish(request.marker(), pendingMarker, request.kind().finished);
        request.manifest().gate().forEach(pos -> plan.gate(pos,
                request.kind().seal.defaultBlockState()));
        // The entrance's authored path floor and inner air are still intact.
        BlockPos center = request.marker().relative(request.outward());
        Direction lateral = request.outward().getClockWise();
        for (int side = -1; side <= 1; side++) {
            BlockPos floor = center.relative(lateral, side).above();
            plan.requireState(floor, DomainBlocks.WAYMARK_TILE.defaultBlockState());
        }
        plan.dryStanding(request.marker().above(2));
        route.forEach(plan::dryStanding);
        request.v5Apron().forEach(plan::requireNewChunk);
        return place(level, plan);
    }

    private record AliRouteNode(int depth, int side, int feetY) { }

    /** Reads only the loaded 20 by 9 north apron; steps may change height by one. */
    private static List<BlockPos> aliV5DryRoute(Loaded loaded, Request request) {
        BlockPos gate = request.marker().relative(request.outward());
        int doorFeet = request.marker().getY() + 2;
        int minY = Math.max(loaded.level.getMinY() + 1, doorFeet - ALI_V5_APPROACH_LENGTH);
        int maxY = Math.min(loaded.level.getMaxY() - 2, doorFeet + ALI_V5_APPROACH_LENGTH);
        ArrayDeque<AliRouteNode> queue = new ArrayDeque<>();
        Map<AliRouteNode, AliRouteNode> previous = new HashMap<>();
        for (int side = -1; side <= 1; side++) {
            for (int y = doorFeet - 1; y <= doorFeet + 1; y++) {
                AliRouteNode start = new AliRouteNode(1, side, y);
                if (y < minY || y > maxY || !dryStanding(loaded, aliV5At(gate,
                        request.outward(), start))) continue;
                previous.put(start, null);
                queue.addLast(start);
            }
        }
        while (!queue.isEmpty()) {
            AliRouteNode current = queue.removeFirst();
            if (current.depth() >= ALI_V5_BANK_START) {
                List<BlockPos> route = new ArrayList<>();
                for (AliRouteNode node = current; node != null; node = previous.get(node))
                    route.add(aliV5At(gate, request.outward(), node));
                Collections.reverse(route);
                return route;
            }
            for (int[] delta : new int[][]{{1, 0}, {0, -1}, {0, 1}, {-1, 0}}) {
                int depth = current.depth() + delta[0];
                int side = current.side() + delta[1];
                if (depth < 1 || depth > ALI_V5_APPROACH_LENGTH
                        || Math.abs(side) > ALI_V5_HALF_WIDTH) continue;
                for (int y = current.feetY() - 1; y <= current.feetY() + 1; y++) {
                    AliRouteNode next = new AliRouteNode(depth, side, y);
                    if (y < minY || y > maxY || previous.containsKey(next)
                            || !dryStanding(loaded, aliV5At(gate,
                                    request.outward(), next))) continue;
                    previous.put(next, current);
                    queue.addLast(next);
                }
            }
        }
        return List.of();
    }

    private static BlockPos aliV5At(BlockPos gate, Direction outward,
                                     AliRouteNode node) {
        return at(gate, outward, node.side(), node.depth(), node.feetY());
    }

    /** A shared, one-shot transaction for sealed new-generation arrivals. */
    public static PlaceResult place(ServerLevel level, Plan plan) {
        if (level.dimension() != Level.OVERWORLD || level.noSave()
                || !plan.valid(level)) return PlaceResult.BLOCKED;
        Loaded loaded = new Loaded(level);
        for (ChunkPos pos : plan.footprint()) {
            LevelChunk chunk = loaded.chunk(pos);
            if (chunk == null) return PlaceResult.WAIT;
            if (!hasNewChunkStamp(chunk)) return PlaceResult.BLOCKED;
        }
        StateChange markerCompletion = plan.completion.get(plan.marker);
        if (loaded.state(plan.marker).equals(markerCompletion.completed())) {
            // A finished root is never repaired after a player changes its span.
            return PlaceResult.COMPLETE;
        }
        if (!preflight(plan, loaded, false)) return PlaceResult.BLOCKED;
        long tick = level.getGameTime();
        if (Long.valueOf(tick).equals(LAST_COMMIT_TICK.get(level))) {
            return PlaceResult.DEFERRED;
        }
        LAST_COMMIT_TICK.put(level, tick);

        List<Placed> changed = new ArrayList<>();
        List<Veneered> veneered = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> write : plan.exterior.entrySet()) {
            if (!loaded.state(write.getKey()).isAir()) continue;
            if (!level.setBlock(write.getKey(), write.getValue(), 2)) {
                rollback(level, changed);
                return PlaceResult.BLOCKED;
            }
            changed.add(new Placed(write.getKey(), write.getValue()));
        }
        for (Map.Entry<BlockPos, StateChange> entry : plan.veneer.entrySet()) {
            StateChange transition = entry.getValue();
            if (loaded.state(entry.getKey()).equals(transition.completed())) continue;
            if (!level.setBlock(entry.getKey(), transition.completed(), 2)) {
                rollbackVeneer(level, veneered);
                rollback(level, changed);
                return PlaceResult.BLOCKED;
            }
            veneered.add(new Veneered(entry.getKey(), transition));
        }
        // A copied 26.3 save verified this flush boundary after an intentional
        // fixture abort: the deck and generation stamp survived, a later gate
        // write did not. The authored gate stays closed on any failed flush.
        flush(level, plan, "deck");
        if (!preflight(plan, loaded, true)) return PlaceResult.BLOCKED;

        List<Placed> opened = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> gate : plan.gates.entrySet()) {
            if (loaded.state(gate.getKey()).isAir()) continue;
            if (!level.setBlock(gate.getKey(), Blocks.AIR.defaultBlockState(), 2)) {
                reseal(level, opened);
                return PlaceResult.BLOCKED;
            }
            opened.add(new Placed(gate.getKey(), gate.getValue()));
        }
        try {
            flush(level, plan, "gate");
        } catch (RuntimeException failure) {
            reseal(level, opened);
            try {
                flush(level, plan, "reseal");
            } catch (RuntimeException resealFailure) {
                failure.addSuppressed(resealFailure);
            }
            throw failure;
        }

        // Completion is persisted only after the open gate has been flushed.
        // If a crash interrupts this stage, pending markers reconcile exact
        // saved bridge and gate cells on the next load.
        for (Map.Entry<BlockPos, StateChange> entry : plan.completion.entrySet()) {
            StateChange transition = entry.getValue();
            BlockState current = loaded.state(entry.getKey());
            if (current.equals(transition.completed())) continue;
            if (!current.equals(transition.pending())
                    || !level.setBlock(entry.getKey(), transition.completed(), 2)) {
                return PlaceResult.BLOCKED;
            }
        }
        flush(level, plan, "complete");
        GemsMod.LOGGER.info("{} guarded crossing completed at {} with {} exterior cells",
                plan.label, plan.marker, plan.exterior.size());
        return PlaceResult.COMPLETE;
    }

    /** Read-only check before a caller persists its own pending terrain journal. */
    static PlaceResult preview(ServerLevel level, Plan plan) {
        if (level.dimension() != Level.OVERWORLD || level.noSave()
                || !plan.valid(level)) return PlaceResult.BLOCKED;
        Loaded loaded = new Loaded(level);
        for (ChunkPos pos : plan.footprint()) {
            LevelChunk chunk = loaded.chunk(pos);
            if (chunk == null) return PlaceResult.WAIT;
            if (!hasNewChunkStamp(chunk)) return PlaceResult.BLOCKED;
        }
        return preflight(plan, loaded, false) ? PlaceResult.COMPLETE
                : PlaceResult.BLOCKED;
    }

    private static boolean preflight(Plan plan, Loaded loaded, boolean exteriorComplete) {
        for (Map.Entry<BlockPos, StateChange> entry : plan.veneer.entrySet()) {
            BlockState current = loaded.state(entry.getKey());
            StateChange transition = entry.getValue();
            if (!current.equals(transition.pending())
                    && !current.equals(transition.completed())) return false;
            if (exteriorComplete && !current.equals(transition.completed())) return false;
        }
        for (Map.Entry<BlockPos, BlockState> write : plan.exterior.entrySet()) {
            BlockState current = loaded.state(write.getKey());
            if (exteriorComplete ? !matchesExterior(current, write.getValue())
                    : !current.isAir() && !matchesExterior(current, write.getValue())) return false;
        }
        for (BlockPos air : plan.air) {
            if (!loaded.state(air).isAir()) return false;
        }
        for (Map.Entry<BlockPos, BlockState> required : plan.states.entrySet()) {
            if (!loaded.state(required.getKey()).equals(required.getValue())) return false;
        }
        for (BlockPos feet : plan.dry) {
            if (!dryStanding(loaded, feet)) return false;
        }
        for (BlockPos feet : plan.passableDry) {
            if (!passableDryStanding(loaded, feet)) return false;
        }
        for (CountRequirement count : plan.counts) {
            int matches = 0;
            for (BlockPos pos : count.positions()) {
                BlockState state = loaded.state(pos);
                if (switch (count.kind()) {
                    case AIR -> state.isAir();
                    case FLUID -> !state.getFluidState().isEmpty();
                    case DRY_STANDING -> dryStanding(loaded, pos);
                    case PASSABLE_DRY_STANDING -> passableDryStanding(loaded, pos);
                }) matches++;
            }
            if (matches < count.minimum()) return false;
        }
        for (Map.Entry<BlockPos, BlockState> gate : plan.gates.entrySet()) {
            BlockState current = loaded.state(gate.getKey());
            if (!current.isAir() && !current.equals(gate.getValue())) return false;
        }
        for (Map.Entry<BlockPos, StateChange> entry : plan.completion.entrySet()) {
            BlockState current = loaded.state(entry.getKey());
            if (!current.equals(entry.getValue().pending())
                    && !current.equals(entry.getValue().completed())) return false;
        }
        return true;
    }

    private static boolean dryStanding(Loaded loaded, BlockPos feet) {
        BlockState support = loaded.state(feet.below());
        return support.isSolid() && support.getFluidState().isEmpty()
                && loaded.state(feet).isAir()
                && loaded.state(feet.above()).isAir();
    }

    private static boolean matchesExterior(BlockState current, BlockState expected) {
        if (current.equals(expected)) return true;
        // Vanilla wall/fence connections and red-mushroom side faces change
        // as adjacent authored cells are placed. Only these three materials
        // may be reconciled by block identity, and only while fluid-free.
        return current.getBlock() == expected.getBlock()
                && current.getFluidState().isEmpty()
                && (current.is(Blocks.STONE_BRICK_WALL)
                        || current.is(Blocks.BIRCH_FENCE)
                        || current.is(Blocks.RED_MUSHROOM_BLOCK));
    }

    private static boolean passableDryStanding(Loaded loaded, BlockPos feet) {
        BlockState support = loaded.state(feet.below());
        BlockState atFeet = loaded.state(feet);
        BlockState atHead = loaded.state(feet.above());
        return support.isSolid() && support.getFluidState().isEmpty()
                && atFeet.getFluidState().isEmpty() && atHead.getFluidState().isEmpty()
                && atFeet.getCollisionShape(loaded.level, feet).isEmpty()
                && atHead.getCollisionShape(loaded.level, feet.above()).isEmpty();
    }

    private static void flush(ServerLevel level, Plan plan, String stage) {
        long started = System.nanoTime();
        int saved = ScopedChunkSave.flushLoaded(level, plan.footprint());
        GemsMod.LOGGER.info("{} guarded crossing {} scoped flush at {} took {} ms for {} saved footprint chunks with {} loaded chunks",
                plan.label, stage, plan.marker,
                (System.nanoTime() - started) / 1_000_000,
                saved,
                level.getChunkSource().getLoadedChunksCount());
    }

    private static void rollback(ServerLevel level, List<Placed> changed) {
        for (int i = changed.size() - 1; i >= 0; i--) {
            Placed write = changed.get(i);
            if (level.getBlockState(write.pos()).equals(write.state())) {
                level.setBlock(write.pos(), Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    private static void rollbackVeneer(ServerLevel level, List<Veneered> changed) {
        for (int i = changed.size() - 1; i >= 0; i--) {
            Veneered write = changed.get(i);
            if (level.getBlockState(write.pos()).equals(write.transition().completed())) {
                level.setBlock(write.pos(), write.transition().pending(), 2);
            }
        }
    }

    private static void reseal(ServerLevel level, List<Placed> opened) {
        for (int i = opened.size() - 1; i >= 0; i--) {
            Placed write = opened.get(i);
            if (level.getBlockState(write.pos()).isAir()) {
                level.setBlock(write.pos(), write.state(), 2);
            }
        }
    }

    private static Manifest manifest(Request request) {
        Kind kind = request.kind();
        int deckY = request.marker().getY() + 1;
        BlockPos gateCenter = request.marker().relative(request.outward());
        List<BlockPos> gate = new ArrayList<>();
        for (int rise = 1; rise <= 2; rise++) {
            for (int side = -1; side <= 1; side++) {
                gate.add(at(gateCenter, request.outward(), side, 0, deckY + rise));
            }
        }
        if (kind == Kind.ALI)
            return new Manifest(Map.of(), List.copyOf(gate), List.of(), List.of(), List.of());
        Map<BlockPos, BlockState> exterior = new LinkedHashMap<>();
        List<BlockPos> clearance = new ArrayList<>();
        List<BlockPos> terrain = new ArrayList<>();
        for (int distance = 1; distance <= kind.length; distance++) {
            for (int side = -1; side <= 1; side++) {
                BlockPos floor = at(gateCenter, request.outward(), side, distance, deckY);
                BlockState deck = distance % 7 == 0
                        ? DomainBlocks.STORYBOOK_PATH.defaultBlockState()
                                : distance % 11 == 0 ? Blocks.RED_MUSHROOM_BLOCK.defaultBlockState()
                                : Blocks.BIRCH_PLANKS.defaultBlockState();
                exterior.put(floor, deck);
                clearance.add(floor.above());
                clearance.add(floor.above(2));
            }
            if (distance >= 2 && distance < kind.length) {
                for (int side : new int[] {-2, 2}) {
                    BlockPos rail = at(gateCenter, request.outward(), side, distance, deckY);
                    exterior.put(rail, Blocks.BIRCH_PLANKS.defaultBlockState());
                    exterior.put(rail.above(), distance % 8 == 0
                            ? Blocks.SEA_LANTERN.defaultBlockState()
                            : Blocks.BIRCH_FENCE.defaultBlockState());
                }
            }
            if (distance > 2 && distance < kind.length - 1) {
                terrain.add(at(gateCenter, request.outward(), 0, distance,
                        deckY - 1));
            }
        }
        List<BlockPos> bank = new ArrayList<>();
        for (int side = -1; side <= 1; side++) {
            bank.add(at(gateCenter, request.outward(), side, kind.length + 1, deckY));
        }
        return new Manifest(Collections.unmodifiableMap(exterior), List.copyOf(gate),
                List.copyOf(clearance), List.copyOf(bank), List.copyOf(terrain));
    }

    private static BlockPos at(BlockPos gate, Direction outward, int side,
                               int distance, int y) {
        return gate.relative(outward, distance)
                .offset(-outward.getStepZ() * side, 0, outward.getStepX() * side)
                .atY(y);
    }

    private static ChunkPos chunkPos(BlockPos pos) {
        return new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);
    }

    public enum PlaceResult { WAIT, DEFERRED, BLOCKED, COMPLETE }

    /**
     * A bounded crossing manifest. Callers must add every cell they inspect or
     * write; {@link #place} checks all of their chunks before changing any cell.
     */
    public static final class Plan {
        private final String label;
        private final BlockPos marker;
        private final Map<BlockPos, BlockState> exterior = new LinkedHashMap<>();
        private final Map<BlockPos, StateChange> veneer = new LinkedHashMap<>();
        private final Map<BlockPos, BlockState> gates = new LinkedHashMap<>();
        private final Map<BlockPos, StateChange> completion = new LinkedHashMap<>();
        private final Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        private final List<BlockPos> air = new ArrayList<>();
        private final List<BlockPos> dry = new ArrayList<>();
        private final List<BlockPos> passableDry = new ArrayList<>();
        private final List<CountRequirement> counts = new ArrayList<>();
        private final Set<BlockPos> extra = new HashSet<>();

        public Plan(String label, BlockPos marker) {
            this.label = label;
            this.marker = marker.immutable();
        }

        public Plan exterior(BlockPos pos, BlockState state) {
            putUnique(exterior, pos, state);
            return this;
        }

        /** Optional exact-state terrain face replacement for a guarded new start. */
        public Plan veneer(BlockPos pos, BlockState expected, BlockState authored) {
            StateChange old = veneer.putIfAbsent(pos.immutable(),
                    new StateChange(expected, authored));
            if (old != null && (!old.pending().equals(expected)
                    || !old.completed().equals(authored))) {
                throw new IllegalArgumentException("Conflicting veneer cell at " + pos);
            }
            return this;
        }

        /** Turn a known natural floor in this caller's manifest into an exact transition. */
        Plan veneerExterior(BlockPos pos, BlockState expected) {
            BlockState authored = exterior.remove(pos);
            if (authored == null) throw new IllegalArgumentException("No exterior cell at " + pos);
            return veneer(pos, expected, authored);
        }

        /** Clear a known low plant from one otherwise required headroom cell. */
        Plan veneerAir(BlockPos pos, BlockState expected) {
            if (!air.remove(pos)) throw new IllegalArgumentException("No air cell at " + pos);
            return veneer(pos, expected, Blocks.AIR.defaultBlockState());
        }

        Plan shiftPassableDryStanding(BlockPos old, BlockPos raised) {
            if (!passableDry.remove(old))
                throw new IllegalArgumentException("No landing witness at " + old);
            passableDry.add(raised.immutable());
            return this;
        }

        public Plan gate(BlockPos pos, BlockState sealed) {
            putUnique(gates, pos, sealed);
            return this;
        }

        public Plan finish(BlockPos pos, BlockState pending, BlockState completed) {
            StateChange old = completion.putIfAbsent(pos.immutable(),
                    new StateChange(pending, completed));
            if (old != null && (!old.pending().equals(pending)
                    || !old.completed().equals(completed))) {
                throw new IllegalArgumentException("Conflicting completion cell at " + pos);
            }
            return this;
        }

        public Plan requireState(BlockPos pos, BlockState state) {
            putUnique(states, pos, state);
            return this;
        }

        public Plan requireAir(BlockPos pos) {
            air.add(pos.immutable());
            return this;
        }

        /** Feet and one head cell must be air above dry solid support. */
        public Plan dryStanding(BlockPos feet) {
            dry.add(feet.immutable());
            return this;
        }

        /** Dry standing space may contain harmless, non-colliding vegetation. */
        public Plan passableDryStanding(BlockPos feet) {
            passableDry.add(feet.immutable());
            return this;
        }

        public Plan atLeastAir(List<BlockPos> positions, int minimum) {
            return count(positions, minimum, CountKind.AIR);
        }

        public Plan atLeastFluid(List<BlockPos> positions, int minimum) {
            return count(positions, minimum, CountKind.FLUID);
        }

        public Plan atLeastDryStanding(List<BlockPos> positions, int minimum) {
            return count(positions, minimum, CountKind.DRY_STANDING);
        }

        public Plan atLeastPassableDryStanding(List<BlockPos> positions, int minimum) {
            return count(positions, minimum, CountKind.PASSABLE_DRY_STANDING);
        }

        /** Include a read-only cell in the all-loaded, all-stamped footprint. */
        public Plan requireNewChunk(BlockPos pos) {
            extra.add(pos.immutable());
            return this;
        }

        private Plan count(List<BlockPos> positions, int minimum, CountKind kind) {
            if (positions.isEmpty() || minimum < 1 || minimum > positions.size()) {
                throw new IllegalArgumentException("Invalid counted crossing requirement");
            }
            counts.add(new CountRequirement(List.copyOf(positions), minimum, kind));
            return this;
        }

        /** The cells this guarded transaction may replace, including its marker. */
        Set<BlockPos> writePositions() {
            Set<BlockPos> writes = new HashSet<>(exterior.keySet());
            writes.addAll(veneer.keySet());
            writes.addAll(gates.keySet());
            writes.addAll(completion.keySet());
            return Set.copyOf(writes);
        }

        private Set<ChunkPos> footprint() {
            Set<ChunkPos> chunks = new HashSet<>();
            chunks.add(chunkPos(marker));
            exterior.keySet().forEach(pos -> chunks.add(chunkPos(pos)));
            veneer.keySet().forEach(pos -> chunks.add(chunkPos(pos)));
            gates.keySet().forEach(pos -> chunks.add(chunkPos(pos)));
            completion.keySet().forEach(pos -> chunks.add(chunkPos(pos)));
            states.keySet().forEach(pos -> chunks.add(chunkPos(pos)));
            air.forEach(pos -> chunks.add(chunkPos(pos)));
            dry.forEach(pos -> chunks.add(chunkPos(pos)));
            passableDry.forEach(pos -> chunks.add(chunkPos(pos)));
            extra.forEach(pos -> chunks.add(chunkPos(pos)));
            counts.forEach(count -> count.positions().forEach(
                    pos -> chunks.add(chunkPos(pos))));
            return chunks;
        }

        private boolean valid(ServerLevel level) {
            if (gates.isEmpty() || !completion.containsKey(marker)) return false;
            Set<BlockPos> all = new HashSet<>();
            all.add(marker);
            all.addAll(exterior.keySet());
            all.addAll(veneer.keySet());
            all.addAll(gates.keySet());
            all.addAll(completion.keySet());
            all.addAll(states.keySet());
            all.addAll(air);
            all.addAll(dry);
            all.addAll(passableDry);
            all.addAll(extra);
            counts.forEach(count -> all.addAll(count.positions()));
            for (BlockPos pos : all) {
                if (pos.getY() < level.getMinY() || pos.getY() >= level.getMaxY()) {
                    return false;
                }
            }
            Set<BlockPos> reserved = new HashSet<>(gates.keySet());
            reserved.addAll(completion.keySet());
            reserved.addAll(veneer.keySet());
            reserved.addAll(states.keySet());
            reserved.addAll(air);
            reserved.addAll(dry);
            reserved.addAll(passableDry);
            counts.forEach(count -> reserved.addAll(count.positions()));
            if (!Collections.disjoint(exterior.keySet(), reserved)) return false;
            reserved.clear();
            reserved.addAll(exterior.keySet());
            reserved.addAll(gates.keySet());
            reserved.addAll(completion.keySet());
            reserved.addAll(states.keySet());
            reserved.addAll(air);
            reserved.addAll(dry);
            reserved.addAll(passableDry);
            reserved.addAll(extra);
            counts.forEach(count -> reserved.addAll(count.positions()));
            if (!Collections.disjoint(veneer.keySet(), reserved)) return false;
            reserved.clear();
            reserved.addAll(air);
            reserved.addAll(dry);
            reserved.addAll(passableDry);
            counts.forEach(count -> reserved.addAll(count.positions()));
            return Collections.disjoint(gates.keySet(), reserved)
                    && Collections.disjoint(completion.keySet(), reserved);
        }

        private static void putUnique(Map<BlockPos, BlockState> cells,
                                      BlockPos pos, BlockState state) {
            BlockState old = cells.putIfAbsent(pos.immutable(), state);
            if (old != null && !old.equals(state)) {
                throw new IllegalArgumentException("Conflicting crossing cell at " + pos);
            }
        }
    }

    private enum CountKind { AIR, FLUID, DRY_STANDING, PASSABLE_DRY_STANDING }

    private record CountRequirement(List<BlockPos> positions, int minimum,
                                    CountKind kind) { }

    private record StateChange(BlockState pending, BlockState completed) { }

    private enum Kind {
        ALI("Ali", DomainBlocks.ALI_BRIDGE_REQUEST, DomainBlocks.ALI_BRIDGE_SEAL,
                Blocks.POLISHED_TUFF.defaultBlockState(), 15, 7),
        YEHIA("Yehia", DomainBlocks.YEHIA_BRIDGE_REQUEST, DomainBlocks.YEHIA_BRIDGE_SEAL,
                DomainBlocks.STORYBOOK_TURF.defaultBlockState(), 33, 12);

        private final String label;
        private final Block request;
        private final Block seal;
        private final BlockState finished;
        private final int length;
        private final int minimumWitnesses;

        Kind(String label, Block request, Block seal, BlockState finished,
             int length, int minimumWitnesses) {
            this.label = label;
            this.request = request;
            this.seal = seal;
            this.finished = finished;
            this.length = length;
            this.minimumWitnesses = minimumWitnesses;
        }

        private static Kind of(Block block) {
            if (block == DomainBlocks.ALI_BRIDGE_REQUEST) return ALI;
            if (block == DomainBlocks.YEHIA_BRIDGE_REQUEST) return YEHIA;
            return null;
        }
    }

    private static final class Request {
        private final Kind kind;
        private final BlockPos marker;
        private final Direction outward;
        private final Manifest manifest;
        private final Set<ChunkPos> footprint;
        private final List<BlockPos> v5Apron;
        private final Set<ChunkPos> v5Footprint;

        private Request(Kind kind, BlockPos marker, Direction outward) {
            this.kind = kind;
            this.marker = marker;
            this.outward = outward;
            this.manifest = GuardedDistrictBridges.manifest(this);
            Set<ChunkPos> chunks = new HashSet<>();
            chunks.add(chunkPos(marker));
            manifest.exterior().keySet().forEach(pos -> chunks.add(chunkPos(pos)));
            manifest.clearance().forEach(pos -> chunks.add(chunkPos(pos)));
            manifest.gate().forEach(pos -> chunks.add(chunkPos(pos)));
            manifest.bank().forEach(pos -> chunks.add(chunkPos(pos)));
            manifest.terrainWitnesses().forEach(pos -> chunks.add(chunkPos(pos)));
            this.footprint = Set.copyOf(chunks);
            if (kind == Kind.ALI) {
                List<BlockPos> apron = new ArrayList<>(ALI_V5_APPROACH_LENGTH
                        * (ALI_V5_HALF_WIDTH * 2 + 1));
                BlockPos gate = marker.relative(outward);
                for (int depth = 1; depth <= ALI_V5_APPROACH_LENGTH; depth++) {
                    for (int side = -ALI_V5_HALF_WIDTH;
                            side <= ALI_V5_HALF_WIDTH; side++) {
                        apron.add(at(gate, outward, side, depth, marker.getY() + 2));
                    }
                }
                this.v5Apron = List.copyOf(apron);
                Set<ChunkPos> v5Chunks = new HashSet<>(chunks);
                apron.forEach(pos -> v5Chunks.add(chunkPos(pos)));
                this.v5Footprint = Set.copyOf(v5Chunks);
            } else {
                this.v5Apron = List.of();
                this.v5Footprint = this.footprint;
            }
        }

        private Kind kind() { return kind; }

        private BlockPos marker() { return marker; }

        private Direction outward() { return outward; }

        private Manifest manifest() { return manifest; }

        private Set<ChunkPos> footprint() { return v5Footprint; }

        private List<BlockPos> v5Apron() { return v5Apron; }

        private Set<ChunkPos> v5Footprint() { return v5Footprint; }
    }

    private record Manifest(Map<BlockPos, BlockState> exterior, List<BlockPos> gate,
                            List<BlockPos> clearance, List<BlockPos> bank,
                            List<BlockPos> terrainWitnesses) { }

    private record Placed(BlockPos pos, BlockState state) { }
    private record Veneered(BlockPos pos, StateChange transition) { }

    private static final class Pending {
        private final Map<BlockPos, Request> requests = new HashMap<>();
        private final Set<BlockPos> ready = new HashSet<>();
        private final Set<BlockPos> logged = new HashSet<>();
    }

    private static final class Loaded {
        private final ServerLevel level;
        private final Map<ChunkPos, LevelChunk> chunks = new HashMap<>();

        private Loaded(ServerLevel level) {
            this.level = level;
        }

        private LevelChunk chunk(ChunkPos pos) {
            return chunks.computeIfAbsent(pos,
                    ignored -> level.getChunkSource().getChunkNow(pos.x(), pos.z()));
        }

        private BlockState state(BlockPos pos) {
            LevelChunk chunk = chunk(chunkPos(pos));
            if (chunk == null) throw new IllegalStateException("Chunk unloaded during guarded bridge preflight");
            return chunk.getBlockState(pos);
        }
    }
}
