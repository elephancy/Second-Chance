package com.feel.gems.adventure;

import com.feel.gems.GemsMod;
import com.feel.gems.adventure.estate.FourRoadsEstateStructure;
import com.feel.gems.adventure.worldgen.AliDryArrivalStructure;
import com.feel.gems.mixin.accessor.ChunkMapPendingUnloadsAccessor;
import net.minecraft.SharedConstants;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.nbt.TagType;
import net.minecraft.nbt.visitors.SkipAll;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * An optional Atlas shortcut for audited structure sets. Vanilla's
 * presence check may call findValidGenerationPoint on the server thread even
 * for an unsaved chunk. This planner performs the exact Structure.generate
 * call on a small worker pool. It may skip only a generated non-start whose
 * chunk scan found no saved NBT; every other case uses normal chunk lookup.
 */
final class AtlasCandidateOracle {
    enum Outcome { ABSENT_NEW, POSSIBLE_NEW, FALLBACK, RETRY }
    record Plan(Outcome outcome, long saveEpoch) { }
    private static final Plan FALLBACK = new Plan(Outcome.FALLBACK, -1L);
    private static final Plan RETRY = new Plan(Outcome.RETRY, -1L);

    private static final Identifier ALI_ID = Identifier.fromNamespaceAndPath(
            GemsMod.MOD_ID, "ali_splitstone_cloister");
    private static final Identifier ESTATE_ID = Identifier.fromNamespaceAndPath(
            GemsMod.MOD_ID, "four_roads_estate_v1");
    private static final int MAX_GENERATIONS = 64;
    private static final int WORKERS = 3;
    private static final int MAX_OUTSTANDING = 11;
    private static final int MAX_ESTATE_QUEUED = 4;
    private static final int MAX_SCANS = 16;

    private record Key(ResourceKey<Level> dimension, Structure structure,
                       RegistryAccess registries, ChunkGenerator generator,
                       RandomState randomState, ResourceManager resources,
                       long chunk) { }

    private final ExecutorService workers;
    private final ExecutorService estateWorker;
    private final Semaphore outstanding = new Semaphore(MAX_OUTSTANDING);
    // Queue a few Estate candidates, but retain only one terrain survey at a time.
    private final Semaphore estateQueued = new Semaphore(MAX_ESTATE_QUEUED);
    private final Semaphore scans = new Semaphore(MAX_SCANS);
    private final AtomicLong generatedCount = new AtomicLong();
    private final AtomicLong generationNanos = new AtomicLong();
    private final AtomicLong scannedCount = new AtomicLong();
    private final AtomicLong scanNanos = new AtomicLong();
    private final AtomicLong earlyTracked = new AtomicLong();
    private final AtomicLong postGenerationTracked = new AtomicLong();
    private final AtomicLong generationErrors = new AtomicLong();
    private final AtomicLong scanPermitsExhausted = new AtomicLong();
    private final AtomicLong scanErrors = new AtomicLong();
    private final AtomicLong savedNbt = new AtomicLong();
    private final AtomicBoolean loggedGenerationError = new AtomicBoolean();
    // Accessed on the server thread only; futures do not mutate this map.
    private final LinkedHashMap<Key, CompletableFuture<Boolean>> generations =
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Key, CompletableFuture<Boolean>> eldest) {
                    return size() > MAX_GENERATIONS;
                }
            };
    private volatile boolean stopped;

    AtlasCandidateOracle() {
        workers = Executors.newFixedThreadPool(WORKERS, task -> {
            Thread thread = new Thread(task, "gems-atlas-plan");
            thread.setDaemon(true);
            return thread;
        });
        estateWorker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "gems-atlas-estate");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** A datapack override, extra set, or changed placement leaves this path. */
    boolean supports(ServerLevel level, RandomSpreadStructurePlacement placement,
                     List<Holder<Structure>> structures) {
        if (stopped || structures.size() != 1
                || !level.getServer().getWorldGenSettings().options().generateStructures()
                || SharedConstants.DEBUG_DISABLE_STRUCTURES) return false;
        Holder<Structure> holder = structures.getFirst();
        if (holder.unwrapKey().isEmpty()) return false;
        Identifier id = holder.unwrapKey().orElseThrow().identifier();
        boolean ali = id.equals(ALI_ID)
                && holder.value().getClass() == AliDryArrivalStructure.class;
        boolean estate = id.equals(ESTATE_ID)
                && holder.value().getClass() == FourRoadsEstateStructure.class;
        if (!ali && !estate) return false;
        var state = level.getChunkSource().getGeneratorState();
        StructureSet actualSet = null;
        int matchingSets = 0;
        for (Holder<StructureSet> set : state.possibleStructureSets()) {
            for (var entry : set.value().structures()) {
                if (entry.structure().value() != holder.value()) continue;
                matchingSets++;
                actualSet = set.value();
            }
        }
        if (matchingSets != 1 || actualSet == null
                || actualSet.structures().size() != 1
                || actualSet.placement() != placement
                && !actualSet.placement().equals(placement)) return false;
        try {
            var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
            JsonElement expectedStructure = bundled(
                    "/data/gems/worldgen/structure/" + id.getPath() + ".json");
            JsonElement expectedSet = bundled(
                    "/data/gems/worldgen/structure_set/" + id.getPath() + ".json");
            if (expectedStructure == null || expectedSet == null) return false;
            Structure builtInStructure = Structure.DIRECT_CODEC.parse(ops, expectedStructure)
                    .result().orElse(null);
            StructureSet builtInSet = StructureSet.DIRECT_CODEC.parse(ops, expectedSet)
                    .result().orElse(null);
            if (builtInStructure == null || builtInSet == null) return false;
            JsonElement normalizedStructure = Structure.DIRECT_CODEC.encodeStart(
                    ops, builtInStructure).result().orElse(null);
            JsonElement liveStructure = Structure.DIRECT_CODEC.encodeStart(
                    ops, holder.value()).result().orElse(null);
            JsonElement normalizedSet = StructureSet.DIRECT_CODEC.encodeStart(
                    ops, builtInSet).result().orElse(null);
            JsonElement liveSet = StructureSet.DIRECT_CODEC.encodeStart(
                    ops, actualSet).result().orElse(null);
            return normalizedStructure != null && normalizedStructure.equals(liveStructure)
                    && normalizedSet != null && normalizedSet.equals(liveSet);
        } catch (RuntimeException error) {
            GemsMod.LOGGER.warn("Atlas planner could not verify {} worldgen data", id, error);
            return false;
        }
    }

    /** Called only on the server thread; captured worldgen values are immutable. */
    CompletableFuture<Plan> evaluate(ServerLevel level, Holder<Structure> holder,
                                      ChunkPos chunk) {
        if (stopped) return CompletableFuture.completedFuture(FALLBACK);
        var source = level.getChunkSource();
        ChunkScanAccess scanner = source.chunkScanner();
        boolean alreadyTracked = tracked(level, chunk);
        if (alreadyTracked) {
            earlyTracked.incrementAndGet();
            return CompletableFuture.completedFuture(FALLBACK);
        }

        Key key = new Key(level.dimension(), holder.value(),
                level.registryAccess(), source.getGenerator(),
                source.randomState(), level.getServer().getResourceManager(),
                chunk.pack());
        CompletableFuture<Boolean> generated = generations.get(key);
        if (generated == null) {
            // A repeated click cannot grow either executor queue. Estate
            // saturation retries later instead of surveying on the tick thread.
            boolean estate = holder.value().getClass() == FourRoadsEstateStructure.class;
            if (estate && !estateQueued.tryAcquire())
                return CompletableFuture.completedFuture(RETRY);
            if (!outstanding.tryAcquire()) {
                if (estate) estateQueued.release();
                return CompletableFuture.completedFuture(estate ? RETRY : FALLBACK);
            }
            var registries = level.registryAccess();
            ChunkGenerator generator = source.getGenerator();
            RandomState randomState = source.randomState();
            StructureTemplateManager templates = level.getServer().getStructureTemplateManager();
            long seed = source.getGeneratorState().getLevelSeed();
            LevelHeightAccessor height = LevelHeightAccessor.create(
                    level.getMinY(), level.getHeight());
            var biomeSource = generator.getBiomeSource();
            ResourceKey<Level> dimension = level.dimension();
            try {
                generated = CompletableFuture.supplyAsync(() -> {
                    long started = System.nanoTime();
                    try {
                        if (stopped) return false;
                        return holder.value().generate(holder, dimension, registries,
                                generator, biomeSource,
                                randomState.createClimateSampler(
                                        SamplerContext.builder().enableCaches().build()),
                                randomState, templates, seed, chunk, 0, height,
                                holder.value().biomes()::contains).isValid();
                    } finally {
                        generatedCount.incrementAndGet();
                        generationNanos.addAndGet(System.nanoTime() - started);
                        outstanding.release();
                        if (estate) estateQueued.release();
                    }
                }, estate ? estateWorker : workers);
            } catch (RuntimeException error) {
                outstanding.release();
                if (estate) estateQueued.release();
                throw error;
            }
            generations.put(key, generated);
        }
        // The generation future finishes on a worker, but the custody check
        // and epoch capture run on the server thread. IOWorker then scans
        // pending writes and disk without loading or generating the chunk.
        CompletableFuture<Plan> planned = new CompletableFuture<>();
        var server = level.getServer();
        generated.whenComplete((valid, error) -> {
            if (planned.isCancelled()) return;
            if (error != null && !stopped) {
                generationErrors.incrementAndGet();
                if (loggedGenerationError.compareAndSet(false, true)) {
                    GemsMod.LOGGER.warn("Atlas planner generation failed at {}",
                            chunk, error);
                }
            }
            try {
                server.execute(() -> {
                    if (planned.isCancelled()) return;
                    if (stopped || error != null) {
                        planned.complete(FALLBACK);
                        return;
                    }
                    if (tracked(level, chunk)) {
                        postGenerationTracked.incrementAndGet();
                        planned.complete(FALLBACK);
                        return;
                    }
                    if (!scans.tryAcquire()) {
                        scanPermitsExhausted.incrementAndGet();
                        planned.complete(FALLBACK);
                        return;
                    }
                    long epoch = ((ChunkMapSaveEpoch) source.chunkMap).gems$saveEpoch();
                    RootPresence presence = new RootPresence();
                    long started = System.nanoTime();
                    try {
                        scanner.scanChunk(chunk, presence).whenComplete((ignored, scanError) -> {
                            scans.release();
                            scannedCount.incrementAndGet();
                            scanNanos.addAndGet(System.nanoTime() - started);
                            if (scanError != null) {
                                scanErrors.incrementAndGet();
                                planned.complete(FALLBACK);
                            } else if (presence.present) {
                                savedNbt.incrementAndGet();
                                planned.complete(FALLBACK);
                            } else {
                                planned.complete(new Plan(valid ? Outcome.POSSIBLE_NEW
                                        : Outcome.ABSENT_NEW, epoch));
                            }
                        });
                    } catch (RuntimeException scanError) {
                        scans.release();
                        scanErrors.incrementAndGet();
                        planned.complete(FALLBACK);
                    }
                });
            } catch (RuntimeException scheduleError) {
                planned.complete(FALLBACK);
            }
        });
        return planned;
    }

    void stop() {
        stopped = true;
        long count = generatedCount.get();
        if (count > 0 || earlyTracked.get() > 0) GemsMod.LOGGER.info(
                "Atlas planner: generated={} totalGenerateMs={} scans={} totalScanMs={} earlyTracked={} postgenTracked={} generationError={} scanPermit={} scanError={} savedNbt={}",
                count, generationNanos.get() / 1_000_000,
                scannedCount.get(), scanNanos.get() / 1_000_000,
                earlyTracked.get(), postGenerationTracked.get(), generationErrors.get(),
                scanPermitsExhausted.get(), scanErrors.get(), savedNbt.get());
        for (CompletableFuture<Boolean> generation : generations.values())
            generation.cancel(true);
        generations.clear();
        workers.shutdownNow();
        estateWorker.shutdownNow();
    }

    /** All three chunk-map locations are read on the server thread only. */
    static boolean tracked(ServerLevel level, ChunkPos chunk) {
        var source = level.getChunkSource();
        var map = source.chunkMap;
        return source.hasChunk(chunk.x(), chunk.z())
                || map.getUpdatingChunkIfPresent(chunk.pack()) != null
                || ((ChunkMapPendingUnloadsAccessor) map)
                        .gems$pendingUnloads().containsKey(chunk.pack());
    }

    static boolean saveEpochChanged(ServerLevel level, ChunkPos chunk, long before) {
        return ((ChunkMapSaveEpoch) level.getChunkSource().chunkMap)
                .gems$changedSince(chunk.pack(), before);
    }

    private static JsonElement bundled(String path) {
        try (var stream = AtlasCandidateOracle.class.getResourceAsStream(path)) {
            if (stream == null) return null;
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (Exception error) {
            return null;
        }
    }

    private static final class RootPresence implements SkipAll {
        private volatile boolean present;

        @Override
        public StreamTagVisitor.ValueResult visitRootEntry(TagType<?> type) {
            present = true;
            return StreamTagVisitor.ValueResult.HALT;
        }
    }
}
