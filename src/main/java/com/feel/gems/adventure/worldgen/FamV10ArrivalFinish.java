package com.feel.gems.adventure.worldgen;

import com.feel.gems.GemsMod;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;

/** One-time foliage finish for newly generated v10 Fam arrival chunks. */
final class FamV10ArrivalFinish {
    private static final ResourceKey<Structure> PUBLIC = ResourceKey.create(
            Registries.STRUCTURE, id("fam_forum"));
    private static final ResourceKey<Structure> TRIAL = ResourceKey.create(
            Registries.STRUCTURE, id("fam_forum_v10_trial"));
    private static final Identifier ROOT = id("fam_forum_arcaded_court_v10");
    private static final Identifier STORY = id("fam_story_hall_reading_garden_v10");
    private static final Identifier SCHOOL = id("fam_open_school_workshop_v10");
    private static final Identifier EAST_REFERENCE = id("fam_east_reference_v1");
    private static final Identifier ARRIVAL_REFERENCE = id("fam_arrival_reference_v10");
    private static final AttachmentType<String> DONE = AttachmentRegistry.createPersistent(
            id("fam_v10_arrival_finish_v1"), Codec.STRING);
    private static final String STAMP = "v1";
    private static final int MAX_PENDING = 128;
    private static final int MAX_TICK_ATTEMPTS = 16;
    private static final int MAX_WRITES = 3_072;
    private static final int CUSTODY_TICKS = 600;
    private static final int MAX_DEBUG = 96;
    private static final boolean DEBUG = Boolean.getBoolean("gems.famForumV10NaturalGate")
            || Boolean.getBoolean("gems.famForumV10PublicGate")
            || Boolean.getBoolean("gems.famV10ArrivalDebug");
    private static final Map<ServerLevel, LinkedHashMap<ChunkPos, Boolean>> PENDING =
            new HashMap<>();
    private static final Map<ServerLevel, LinkedHashMap<ChunkPos, Long>> CUSTODY =
            new HashMap<>();
    private static final AtomicInteger DEBUG_COUNT = new AtomicInteger();
    private static boolean initialized;

    private FamV10ArrivalFinish() { }

    static void init() {
        if (initialized) return;
        initialized = true;
        ServerChunkEvents.CHUNK_GENERATE.register((level, chunk) -> {
            if (level.dimension() != Level.OVERWORLD) return;
            boolean marker = hasArrivalMarker(chunk);
            boolean reference = referencedStructure(level, chunk) != null;
            if (!marker && !reference) return;
            remember(level, chunk.getPos());
            trace("generate " + chunk.getPos() + " marker=" + marker
                    + " reference=" + reference);
            if (marker && reference) admit(level, chunk, "generate");
        });
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, newlyGenerated) -> {
            if (level.dimension() != Level.OVERWORLD) return;
            boolean marker = hasArrivalMarker(chunk);
            boolean reference = marker && referencedStructure(level, chunk) != null;
            if (marker || reference) trace("load " + chunk.getPos() + " new="
                    + newlyGenerated + " marker=" + marker + " reference=" + reference
                    + " custody=" + inCustody(level, chunk.getPos()));
            if (newlyGenerated && (marker || reference)) remember(level, chunk.getPos());
            if (marker && reference && inCustody(level, chunk.getPos()))
                admit(level, chunk, "load");
        });
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            LinkedHashMap<ChunkPos, Boolean> queued = PENDING.get(level);
            if (queued != null && queued.remove(chunk.getPos()) != null)
                trace("unload-pending " + chunk.getPos());
        });
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            if (level.dimension() == Level.OVERWORLD && level.getGameTime() % 5 == 0)
                drain(level);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            PENDING.clear();
            CUSTODY.clear();
        });
    }

    private static void remember(ServerLevel level, ChunkPos pos) {
        LinkedHashMap<ChunkPos, Long> known = CUSTODY.computeIfAbsent(level,
                ignored -> new LinkedHashMap<>());
        if (known.containsKey(pos)) return;
        if (known.size() >= MAX_PENDING) {
            GemsMod.LOGGER.warn("Fam v10 new-chunk arrival custody reached its bound");
            return;
        }
        known.put(pos, level.getGameTime() + CUSTODY_TICKS);
    }

    private static boolean inCustody(ServerLevel level, ChunkPos pos) {
        LinkedHashMap<ChunkPos, Long> known = CUSTODY.get(level);
        if (known == null) return false;
        Long deadline = known.get(pos);
        if (deadline == null) return false;
        if (level.getGameTime() <= deadline) return true;
        known.remove(pos);
        trace("custody-expired " + pos);
        return false;
    }

    private static void admit(ServerLevel level, LevelChunk chunk, String source) {
        ChunkPos pos = chunk.getPos();
        if (!inCustody(level, pos) || STAMP.equals(chunk.getAttached(DONE))) return;
        LinkedHashMap<ChunkPos, Boolean> queued = PENDING.computeIfAbsent(level,
                ignored -> new LinkedHashMap<>());
        if (queued.containsKey(pos)) return;
        if (queued.size() >= MAX_PENDING) {
            GemsMod.LOGGER.warn("Fam v10 arrival finish queue reached its bound");
            return;
        }
        queued.put(pos, Boolean.TRUE);
        trace("admit " + pos + " via=" + source + " pending=" + queued.size());
    }

    private static void drain(ServerLevel level) {
        LinkedHashMap<ChunkPos, Boolean> queued = PENDING.get(level);
        if (queued == null || queued.isEmpty()) return;
        // The load callback runs before a chunk is safely available through
        // the level cache. Touch blocks only from this bounded server tick.
        int attempts = Math.min(MAX_TICK_ATTEMPTS, queued.size());
        for (int i = 0; i < attempts; i++) {
            ChunkPos pos = queued.keySet().iterator().next();
            queued.remove(pos);
            if (!inCustody(level, pos)) continue;
            Result result = finish(level, pos);
            if (result == Result.BLOCKED)
                GemsMod.LOGGER.warn("Fam v10 new-chunk arrival finish stayed sealed at {}",
                        pos);
            if (result == Result.WAIT) queued.put(pos, Boolean.TRUE);
            else trace("commit " + pos + " result=" + result);
        }
    }

    private static Structure referencedStructure(ServerLevel level, LevelChunk chunk) {
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Structure publicSite = registry.getValue(PUBLIC);
        Structure trial = registry.getValue(TRIAL);
        boolean publicReference = publicSite != null
                && !chunk.getReferencesForStructure(publicSite).isEmpty();
        boolean trialReference = trial != null
                && !chunk.getReferencesForStructure(trial).isEmpty();
        // An overlapping public and trial reference has no unique owner.
        if (publicReference == trialReference) return null;
        return publicReference ? publicSite : trial;
    }

    private static boolean hasArrivalMarker(LevelChunk chunk) {
        for (LevelChunkSection section : chunk.getSections())
            if (section.maybeHas(FamV10ArrivalTerrace::arrivalTile)) return true;
        return false;
    }

    private static Result finish(ServerLevel level, ChunkPos pos) {
        LevelChunk chunk = loaded(level, pos);
        // Every inspected marker and foliage write belongs to this exact
        // chunk. Waiting for an unrelated 3x3 ring can leave a completed
        // structure permanently unfinished when remote chunks unload.
        if (chunk == null) {
            trace("wait-target " + pos);
            return Result.WAIT;
        }
        if (STAMP.equals(chunk.getAttached(DONE))) return Result.DONE;
        Structure structure = referencedStructure(level, chunk);
        if (structure == null) return Result.BLOCKED;
        List<PoolElementStructurePiece> facades = currentFacades(level, chunk, structure);
        if (facades == null) {
            trace("wait-owner " + pos);
            return Result.WAIT;
        }
        if (facades.isEmpty()) return Result.BLOCKED;
        LinkedHashSet<BlockPos> clear = new LinkedHashSet<>();
        int markers = 0;
        for (PoolElementStructurePiece facade : facades) {
            int baseY = facade.getBoundingBox().minY();
            BoundingBox apron = FamV10ArrivalTerrace.approachBounds(facade, baseY);
            int minX = Math.max(apron.minX(), pos.getMinBlockX());
            int maxX = Math.min(apron.maxX(), pos.getMaxBlockX());
            int minZ = Math.max(apron.minZ(), pos.getMinBlockZ());
            int maxZ = Math.min(apron.maxZ(), pos.getMaxBlockZ());
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = baseY; y <= baseY + 4; y++) {
                        BlockPos marker = new BlockPos(x, y, z);
                        if (!FamV10ArrivalTerrace.arrivalTile(level.getBlockState(marker)))
                            continue;
                        markers++;
                        for (int rise = 1; rise <= 12; rise++) {
                            BlockPos at = marker.above(rise);
                            if (FamV10ArrivalTerrace.naturalTreeOrVine(
                                    level.getBlockState(at))
                                    && level.getBlockEntity(at) == null) clear.add(at);
                        }
                    }
                }
            }
        }
        if (markers == 0 || clear.size() > MAX_WRITES) return Result.BLOCKED;
        for (BlockPos at : clear) {
            // Recheck before commit; a later block edit can only narrow the
            // natural foliage set, never authorize a different block write.
            if (FamV10ArrivalTerrace.naturalTreeOrVine(level.getBlockState(at))
                    && level.getBlockEntity(at) == null)
                level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
        }
        chunk.setAttached(DONE, STAMP);
        trace("done " + pos + " markers=" + markers + " cleared=" + clear.size());
        return Result.DONE;
    }

    private static List<PoolElementStructurePiece> currentFacades(ServerLevel level,
            LevelChunk target, Structure trial) {
        StructureStart start = null;
        int references = 0;
        for (long reference : target.getReferencesForStructure(trial)) {
            references++;
            if (references > 1) return List.of();
            LevelChunk owner = loaded(level, new ChunkPos(
                    ChunkPos.getX(reference), ChunkPos.getZ(reference)));
            if (owner == null) return null;
            start = owner.getStartForStructure(trial);
        }
        if (start == null || !start.isValid()) return List.of();
        PoolElementStructurePiece root = null, story = null, school = null;
        int east = 0, arrivals = 0;
        for (StructurePiece raw : start.getPieces()) {
            if (!(raw instanceof PoolElementStructurePiece pooled)
                    || !(pooled.getElement() instanceof SinglePoolElement single))
                return List.of();
            Identifier name = single.getTemplateLocation();
            if (ROOT.equals(name)) {
                if (root != null) return List.of();
                root = pooled;
            } else if (STORY.equals(name)) {
                if (story != null) return List.of();
                story = pooled;
            } else if (SCHOOL.equals(name)) {
                if (school != null) return List.of();
                school = pooled;
            } else if (EAST_REFERENCE.equals(name)) east++;
            else if (ARRIVAL_REFERENCE.equals(name)) arrivals++;
            else return List.of();
        }
        return root != null && story != null && school != null
                && east == 1 && arrivals == 2
                ? List.of(root, story, school) : List.of();
    }

    private static LevelChunk loaded(ServerLevel level, ChunkPos pos) {
        return level.getChunkSource().getChunkNow(pos.x(), pos.z());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, path);
    }

    private static void trace(String message) {
        if (DEBUG && DEBUG_COUNT.getAndIncrement() < MAX_DEBUG)
            GemsMod.LOGGER.info("FAM_V10_ARRIVAL {}", message);
    }

    private enum Result { WAIT, DONE, BLOCKED }
}
