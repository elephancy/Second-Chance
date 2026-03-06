package com.feel.gems.adventure.worldgen.site;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidPiece;
import net.minecraft.world.level.levelgen.structure.structures.BuriedTreasurePieces;
import net.minecraft.world.level.levelgen.structure.structures.BuriedTreasureStructure;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftPieces;
import net.minecraft.world.level.levelgen.structure.structures.NetherFortressPieces;
import net.minecraft.world.level.levelgen.structure.structures.NetherFortressStructure;
import net.minecraft.world.level.levelgen.structure.structures.NetherFossilPieces;
import net.minecraft.world.level.levelgen.structure.structures.NetherFossilStructure;
import net.minecraft.world.level.levelgen.structure.structures.OceanRuinPieces;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentStructure;
import net.minecraft.world.level.levelgen.structure.structures.RuinedPortalPiece;
import net.minecraft.world.level.levelgen.structure.structures.ShipwreckPieces;
import net.minecraft.world.level.levelgen.structure.structures.ShipwreckStructure;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces;
import net.minecraft.world.level.levelgen.structure.pools.LegacySinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockRotProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.ProtectedBlockProcessor;

/**
 * Read-only, generation-time collision reservation for a rare new site.
 * It simulates starts from the dimension's registered structure sets, without
 * requesting chunks or changing existing starts. Foreign pieces may settle
 * vertically during post-processing, so an X/Z overlap reserves the column
 * even when the noise-only Y intervals differ. Only audited structure/piece
 * families have a known horizontal influence area; an unverified valid start
 * returns UNKNOWN. Loaded-chunk protection is also required for old worlds
 * and datapacks.
 */
public final class StructureStartReservation {
    private static final int MAX_SET_SEARCH_CELLS = 8_192;
    private static final long MAX_TOTAL_GRID_CHECKS = 200_000;
    private static final int MAX_GENERATED_STARTS = 256;
    private static final int MAX_PIECES_PER_START = 4_096;
    private static final int MAX_CACHED_STARTS_PER_THREAD = 128;
    private static final ThreadLocal<Boolean> SIMULATING =
            ThreadLocal.withInitial(() -> false);
    // A shifted or rotated site repeatedly compares the same deterministic
    // foreign starts. Keep only this thread's recent read-only simulations;
    // the key includes every worldgen input that may change the result.
    private static final ThreadLocal<LinkedHashMap<StartKey, StructureStart>> STARTS =
            ThreadLocal.withInitial(() -> new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<StartKey, StructureStart> eldest) {
                    return size() > MAX_CACHED_STARTS_PER_THREAD;
                }
            });
    private static final ThreadLocal<PhaseTiming> TIMING = new ThreadLocal<>();
    private static final boolean TRACE_TIMING = Boolean.getBoolean(
            "gems.fourRoadsEstateReservationTiming");
    // SHA-256 of audited official 26.3 files in minecraft-common.jar.
    // The classpath originals are separately pinned before any runtime registry
    // value can be compared, so a datapack replacement cannot become the baseline.
    private static final Map<String, String> VANILLA_PROCESSOR_SHA256 = Map.ofEntries(
            Map.entry("trial_chambers_copper_bulb_degradation", "d68262cf360c94d88d9c0ad4fbd7509527d4654ebb30e1ac1e77e8d2fa8fecec"),
            Map.entry("ancient_city_generic_degradation", "265fcfedd34aa5c23fff26bdf76b8a4263522940d7ec0fcc311215293d03eee4"),
            Map.entry("ancient_city_walls_degradation", "72dfce28b1428711a84ec2a52bbabccc189f268de1133edde54afd194814e827"),
            Map.entry("ancient_city_start_degradation", "be77949ac9035ea0222308ca18c5640f63158f607797252d2ad26c0634603cda"),
            Map.entry("bridge", "a0617e5a780fe2d84bfaf41cfe5fb1a1c1b085639a6032199eb766aa321948de"),
            Map.entry("entrance_replacement", "37cec476cc523c39e84fa45164f967d666c71760904223d83f90601486888c5a"),
            Map.entry("high_rampart", "18d95b04e15ca98877cb48991523818967afbce0238be56de0b2d3a06bc65214"),
            Map.entry("side_wall_degradation", "9f1a38af35634c064c007877aa24887c5e497991b234d632e513b25d4ba5224f"),
            Map.entry("stable_degradation", "3f553988781d00cd215740369f42bfbc9cbae8b3d210f53254c6b1d13f1bdd0c"),
            Map.entry("high_wall", "3274837e2b126b4395dec1e56027f544f1964ca9d7f656351437f19c8c98032a"),
            Map.entry("treasure_rooms", "6795b3712ebd851aed5b8d95c4d0253b1ab3387f1da74a7cd0af58992e8aee42"),
            Map.entry("bottom_rampart", "306f672e2d19ab633f4494b86694380e0898d7704b153be4e9807ea907e1ff01"),
            Map.entry("rampart_degradation", "3337ecad2f33fa238b3da3288a3afdc00db4224a97432c18d1ff9da274a78ed3"),
            Map.entry("roof", "872bdec4e88538ff6d594e907a11f74bf37c365f680fb583b53cd78b4e048f77"),
            Map.entry("bastion_generic_degradation", "a52eb54159fd45e40e3e313f167244975797541f03687f0915bb37358e83236d"),
            Map.entry("housing", "59626137accf456f3c7f147dfabc4053819eb3f25ac3be3e19ae52a2015ef636"));
    private static final Map<String, JsonElement> VANILLA_PROCESSOR_JSON =
            loadVanillaProcessorJson();

    public enum Status { CLEAR, CONFLICT, UNKNOWN }

    public record Verdict(Status status, String reason, int candidateChunks,
                          int generatedStarts, int comparedPieces, long elapsedMillis) {
        public boolean clear() { return status == Status.CLEAR; }
    }

    private StructureStartReservation() { }

    public static Verdict inspect(Structure.GenerationContext context,
                                  ResourceKey<Level> dimension,
                                  ResourceKey<Structure> self,
                                  List<BoundingBox> occupied,
                                  List<BoundingBox> arrivals) {
        return inspect(context, dimension, self, occupied, arrivals, null);
    }

    /** Package-local diagnostic seam; production callers always use fresh state. */
    static Verdict inspectWithState(Structure.GenerationContext context,
                                    ResourceKey<Level> dimension,
                                    ResourceKey<Structure> self,
                                    List<BoundingBox> occupied,
                                    List<BoundingBox> arrivals,
                                    ChunkGeneratorStructureState state) {
        if (state == null) return new Verdict(Status.UNKNOWN,
                "missing supplied structure state", 0, 0, 0, 0);
        return inspect(context, dimension, self, occupied, arrivals, state);
    }

    private static Verdict inspect(Structure.GenerationContext context,
                                   ResourceKey<Level> dimension,
                                   ResourceKey<Structure> self,
                                   List<BoundingBox> occupied,
                                   List<BoundingBox> arrivals,
                                   ChunkGeneratorStructureState suppliedState) {
        long started = System.nanoTime();
        // A simulated foreign start may use this same API. Allow its normal
        // biome/terrain/piece generation while this outer reservation treats
        // it as a conservative potential conflict. This never writes a start.
        if (SIMULATING.get()) return new Verdict(Status.CLEAR,
                "nested noise simulation", 0, 0, 0, 0);
        PhaseTiming timing = TRACE_TIMING ? new PhaseTiming() : null;
        if (timing != null) TIMING.set(timing);
        if (context == null || dimension == null || self == null || occupied == null
                || occupied.isEmpty() || arrivals == null)
            return verdict(Status.UNKNOWN, "missing site contract", 0, 0, 0, started);
        if (suppliedState != null && suppliedState.getLevelSeed() != context.seed())
            return verdict(Status.UNKNOWN, "structure state seed mismatch",
                    0, 0, 0, started);
        List<BoundingBox> site = new ArrayList<>(occupied.size() + arrivals.size());
        site.addAll(occupied);
        site.addAll(arrivals);
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BoundingBox box : site) {
            if (box == null || box.minX() > box.maxX()
                    || box.minY() > box.maxY() || box.minZ() > box.maxZ())
                return verdict(Status.UNKNOWN, "invalid site box", 0, 0, 0, started);
            minX = Math.min(minX, box.minX());
            minZ = Math.min(minZ, box.minZ());
            maxX = Math.max(maxX, box.maxX());
            maxZ = Math.max(maxZ, box.maxZ());
        }
        int candidates = 0, generated = 0, compared = 0;
        long gridChecks = 0;
        try {
            long stateStarted = System.nanoTime();
            ChunkGeneratorStructureState state = suppliedState != null ? suppliedState
                    : context.chunkGenerator().createState(
                            context.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET),
                            context.randomState(), context.seed());
            if (timing != null) timing.stateNanos += System.nanoTime() - stateStarted;
            StructureStartReach.Session reaches = new StructureStartReach.Session();
            for (Holder<StructureSet> holder : state.possibleStructureSets()) {
                StructureSet set = holder.value();
                if (set.structures().isEmpty())
                    return verdict(Status.UNKNOWN, "empty structure set",
                            candidates, generated, compared, started);
                int reach = 0;
                long reachStarted = System.nanoTime();
                for (var entry : set.structures()) {
                    int entryReach = reaches.chunks(entry.structure().value(),
                            context.structureTemplateManager(), context.registryAccess());
                    if (entryReach < 1)
                        return verdict(Status.UNKNOWN,
                                "unverified start reach: " + entry.structure()
                                        .unwrapKey().map(Object::toString).orElse("unregistered"),
                                candidates, generated, compared, started);
                    reach = Math.max(reach, entryReach);
                }
                if (timing != null) timing.reachNanos += System.nanoTime() - reachStarted;
                long lowX = (long) (minX >> 4) - reach;
                long highX = (long) (maxX >> 4) + reach;
                long lowZ = (long) (minZ >> 4) - reach;
                long highZ = (long) (maxZ >> 4) + reach;
                long area = (highX - lowX + 1) * (highZ - lowZ + 1);
                if (lowX <= Integer.MIN_VALUE || highX >= Integer.MAX_VALUE
                        || lowZ <= Integer.MIN_VALUE || highZ >= Integer.MAX_VALUE
                        || area <= 0 || area > MAX_SET_SEARCH_CELLS)
                    return verdict(Status.UNKNOWN, "candidate search exceeds bound",
                            candidates, generated, compared, started);
                if (gridChecks + area > MAX_TOTAL_GRID_CHECKS)
                    return verdict(Status.UNKNOWN, "total candidate budget exceeded",
                            candidates, generated, compared, started);
                gridChecks += area;
                int minCx = (int) lowX, maxCx = (int) highX;
                int minCz = (int) lowZ, maxCz = (int) highZ;
                if (timing != null) timing.gridStarted = System.nanoTime();
                List<ChunkPos> selected = candidateChunks(set.placement(), state,
                        minCx, maxCx, minCz, maxCz);
                if (selected == null)
                    return verdict(Status.UNKNOWN, "candidate enumeration overflow",
                            candidates, generated, compared, started);
                for (ChunkPos candidate : selected) {
                        int cx = candidate.x(), cz = candidate.z();
                        candidates++;
                        List<StructureSet.StructureSelectionEntry> choices =
                                new ArrayList<>(set.structures());
                        WorldgenRandom random = new WorldgenRandom(
                                new LegacyRandomSource(0L));
                        random.setLargeFeatureSeed(state.getLevelSeed(), cx, cz);
                        while (!choices.isEmpty()) {
                            int total = 0;
                            for (var entry : choices) {
                                if (entry.weight() <= 0)
                                    return verdict(Status.UNKNOWN,
                                            "nonpositive structure weight",
                                            candidates, generated, compared, started);
                                total = Math.addExact(total, entry.weight());
                            }
                            int index = 0;
                            if (choices.size() > 1) {
                                int draw = random.nextInt(total);
                                while ((draw -= choices.get(index).weight()) >= 0) index++;
                            }
                            var entry = choices.remove(index);
                            Holder<Structure> structure = entry.structure();
                            if (structure.unwrapKey().isEmpty())
                                return verdict(Status.UNKNOWN,
                                        "unregistered structure holder",
                                        candidates, generated, compared, started);
                            // The current start is already the caller's site.
                            if (structure.is(self) && candidate.equals(context.chunkPos()))
                                break;
                            StartKey key = new StartKey(context, dimension,
                                    structure.value(), candidate);
                            var cache = STARTS.get();
                            StructureStart start = cache.get(key);
                            if (start == null) {
                                SIMULATING.set(true);
                                long generationStarted = System.nanoTime();
                                try {
                                    start = structure.value().generate(structure, dimension,
                                            context.registryAccess(), context.chunkGenerator(),
                                            context.biomeSource(), context.climateSampler(),
                                            context.randomState(),
                                            context.structureTemplateManager(), context.seed(),
                                            candidate, 0, context.heightAccessor(),
                                            structure.value().biomes()::contains);
                                } finally {
                                    if (timing != null)
                                        timing.generationNanos += System.nanoTime() - generationStarted;
                                    SIMULATING.set(false);
                                }
                                cache.put(key, start);
                            } else if (timing != null) {
                                timing.cacheHits++;
                            }
                            if (!start.isValid()) continue;
                            generated++;
                            if (generated > MAX_GENERATED_STARTS)
                                return verdict(Status.UNKNOWN,
                                        "too many generated starts",
                                        candidates, generated, compared, started);
                            if (start.getPieces().isEmpty()
                                    || start.getPieces().size() > MAX_PIECES_PER_START)
                                return verdict(Status.UNKNOWN,
                                        "foreign piece count outside bound",
                                        candidates, generated, compared, started);
                            for (StructurePiece piece : start.getPieces()) {
                                long comparisonStarted = System.nanoTime();
                                BoundingBox foreign = verifiedInfluence(
                                        structure, piece, context.registryAccess());
                                if (timing != null)
                                    timing.comparisonNanos += System.nanoTime() - comparisonStarted;
                                if (foreign == null)
                                    return verdict(Status.UNKNOWN,
                                            "unverified foreign bounds: "
                                                    + structure.unwrapKey().orElseThrow()
                                                    + " / " + piece.getClass().getName(),
                                            candidates, generated, compared, started);
                                if (foreign.minX() > foreign.maxX()
                                        || foreign.minY() > foreign.maxY()
                                        || foreign.minZ() > foreign.maxZ())
                                    return verdict(Status.UNKNOWN,
                                            "foreign piece has invalid bounds",
                                            candidates, generated, compared, started);
                                compared++;
                                if (overlapsAnyColumn(foreign, site)) {
                                    if (Boolean.getBoolean("gems.fourRoadsEstateTraceRejections"))
                                        traceColumnOverlap(context.chunkPos(), structure,
                                                candidate, foreign, site, occupied.size());
                                    return verdict(Status.CONFLICT,
                                            structure.unwrapKey().orElseThrow()
                                                    + " at " + candidate,
                                            candidates, generated, compared, started);
                                }
                            }
                            if (structure.value() instanceof BoundedJigsawStart bounded) {
                                List<BoundingBox> approaches = bounded.reservationApproaches(start);
                                if (approaches == null || approaches.size() > 16)
                                    return verdict(Status.UNKNOWN,
                                            "unverified foreign approaches: "
                                                    + structure.unwrapKey().orElseThrow(),
                                            candidates, generated, compared, started);
                                for (BoundingBox approach : approaches) {
                                    if (approach == null || approach.minX() > approach.maxX()
                                            || approach.minZ() > approach.maxZ()
                                            || approach.minY() > approach.maxY())
                                        return verdict(Status.UNKNOWN,
                                                "invalid foreign approach bounds",
                                                candidates, generated, compared, started);
                                    compared++;
                                    if (overlapsAnyColumn(approach, site))
                                        return verdict(Status.CONFLICT,
                                                structure.unwrapKey().orElseThrow()
                                                        + " approach at " + candidate,
                                                candidates, generated, compared, started);
                                }
                            }
                            break;
                        }
                }
                if (timing != null) {
                    timing.gridNanos += System.nanoTime() - timing.gridStarted;
                    timing.gridStarted = 0;
                }
            }
            return verdict(Status.CLEAR, "all registered starts clear",
                    candidates, generated, compared, started);
        } catch (RuntimeException exception) {
            return verdict(Status.UNKNOWN, exception.getClass().getSimpleName(),
                    candidates, generated, compared, started);
        }
    }

    /** Exact placement candidates in the bounded rectangle, in chunk order. */
    static List<ChunkPos> candidateChunks(StructurePlacement placement,
            ChunkGeneratorStructureState state, int minCx, int maxCx,
            int minCz, int maxCz) {
        List<ChunkPos> selected = new ArrayList<>();
        if (placement.getClass() == RandomSpreadStructurePlacement.class) {
            RandomSpreadStructurePlacement spread = (RandomSpreadStructurePlacement) placement;
            int spacing = spread.spacing();
            if (spacing < 1) return null;
            int minRegionX = Math.floorDiv(minCx, spacing);
            int maxRegionX = Math.floorDiv(maxCx, spacing);
            int minRegionZ = Math.floorDiv(minCz, spacing);
            int maxRegionZ = Math.floorDiv(maxCz, spacing);
            for (long rx = minRegionX; rx <= maxRegionX; rx++) {
                for (long rz = minRegionZ; rz <= maxRegionZ; rz++) {
                    long sampleX = rx * spacing, sampleZ = rz * spacing;
                    if (sampleX < Integer.MIN_VALUE || sampleX > Integer.MAX_VALUE
                            || sampleZ < Integer.MIN_VALUE || sampleZ > Integer.MAX_VALUE)
                        return null;
                    ChunkPos possible = spread.getPotentialStructureChunk(
                            state.getLevelSeed(), (int) sampleX, (int) sampleZ);
                    if (inBounds(possible, minCx, maxCx, minCz, maxCz)
                            && spread.isStructureChunk(state, possible.x(), possible.z()))
                        selected.add(possible);
                }
            }
        } else if (placement.getClass() == ConcentricRingsStructurePlacement.class) {
            ConcentricRingsStructurePlacement rings =
                    (ConcentricRingsStructurePlacement) placement;
            List<ChunkPos> positions = state.getRingPositionsFor(rings);
            if (positions == null) return null;
            for (ChunkPos possible : new LinkedHashSet<>(positions)) {
                if (inBounds(possible, minCx, maxCx, minCz, maxCz)
                        && rings.isStructureChunk(state, possible.x(), possible.z()))
                    selected.add(possible);
            }
        } else {
            // Unknown placements or subclasses may use another candidate
            // pattern. Retain the complete bounded scan for those rules.
            for (int cx = minCx; cx <= maxCx; cx++) {
                for (int cz = minCz; cz <= maxCz; cz++) {
                    if (placement.isStructureChunk(state, cx, cz))
                        selected.add(new ChunkPos(cx, cz));
                }
            }
        }
        selected.sort(Comparator.comparingInt(ChunkPos::x)
                .thenComparingInt(ChunkPos::z));
        return selected;
    }

    private static boolean inBounds(ChunkPos chunk, int minCx, int maxCx,
                                    int minCz, int maxCz) {
        return chunk.x() >= minCx && chunk.x() <= maxCx
                && chunk.z() >= minCz && chunk.z() <= maxCz;
    }

    /** Audited 26.3 types only; exact class identity prevents subclass surprises. */
    static BoundingBox verifiedInfluence(Holder<Structure> structureHolder,
                                                  StructurePiece piece,
                                                  RegistryAccess registries) {
        Structure structure = structureHolder.value();
        Identifier type = BuiltInRegistries.STRUCTURE_TYPE.getKey(structure.type());
        if (type == null) return null;
        String id = type.toString();
        Class<?> kind = piece.getClass();
        BoundingBox box = piece.getBoundingBox();
        if (box == null) return null;
        AuditedJigsawTypes.Entry audit = AuditedJigsawTypes.get(id);
        if ((id.equals("minecraft:jigsaw")
                || audit != null && structure.getClass() == audit.exactClass())
                && kind == PoolElementStructurePiece.class
                && verifiedPoolElement((PoolElementStructurePiece) piece, registries,
                        structureHolder.unwrapKey().orElse(null),
                        structure.getClass() == JigsawStructure.class))
            return BoundedJigsawStart.apron(box,
                    audit == null ? 15 : audit.pieceApronBlocks());
        if (id.equals("minecraft:buried_treasure")
                && structure.getClass() == BuriedTreasureStructure.class
                && kind == BuriedTreasurePieces.BuriedTreasurePiece.class) {
            // 26.3 settles this piece vertically at the center, then may
            // replace each of the six neighboring blocks around the chest.
            return BoundedJigsawStart.apron(box, 1);
        }
        if (id.equals("minecraft:mineshaft")
                && (kind == MineshaftPieces.MineShaftRoom.class
                || kind == MineshaftPieces.MineShaftCrossing.class
                || kind == MineshaftPieces.MineShaftStairs.class
                || kind == MineshaftPieces.MineShaftCorridor.class)) return box;
        if (id.equals("minecraft:fortress")
                && structure.getClass() == NetherFortressStructure.class
                && fortressPiece(kind)) {
            // All 26.3 concrete fortress pieces use StructurePiece's
            // chunk-box-clipped helpers. MonsterThrone's direct spawner write
            // checks the same box; foundations continue only vertically.
            return box;
        }
        if (id.equals("minecraft:nether_fossil")
                && structure.getClass() == NetherFossilStructure.class
                && kind == NetherFossilPieces.NetherFossilPiece.class) {
            // The template keeps its saved X/Z origin; the dried-ghast write
            // draws inside the template box and checks the chunk box.
            return box;
        }
        if (id.equals("minecraft:desert_pyramid")
                && kind == DesertPyramidPiece.class) return box;
        if (id.equals("minecraft:stronghold") && strongholdPiece(kind)) {
            // All 13 concrete 26.3 stronghold pieces use StructurePiece's
            // chunk-box-clipped placement helpers. PortalRoom's one direct
            // setBlock is explicitly guarded by chunkBox.isInside(pos).
            // No concrete piece moves its X/Z bounding box in postProcess.
            return box;
        }
        if (id.equals("minecraft:ocean_ruin")
                && kind == OceanRuinPieces.OceanRuinPiece.class) {
            // 26.3 OceanRuinPiece.postProcess settles templatePosition vertically
            // against OCEAN_FLOOR_WG and recomputes its box at the same X/Z.
            // Its processors and chest/drowned markers stay in that template.
            return box;
        }
        if (id.equals("minecraft:shipwreck")
                && structure.getClass() == ShipwreckStructure.class
                && kind == ShipwreckPieces.ShipwreckPiece.class) {
            // 26.3 ShipwreckPiece.postProcess changes only template Y before
            // placing it. Its loot markers use the same X/Z column.
            return box;
        }
        if (id.equals("minecraft:ocean_monument")
                && structure.getClass() == OceanMonumentStructure.class
                && kind == OceanMonumentPieces.MonumentBuilding.class) {
            // The 58-square 26.3 building clears water from local -5..62,
            // five blocks beyond its 0..57 box; child pieces stay inside it.
            return BoundedJigsawStart.apron(box, 6);
        }
        if (id.equals("minecraft:ruined_portal")
                && kind == RuinedPortalPiece.class) {
            // RuinedPortalPiece.spreadNetherrack visits center +/-14 in X/Z,
            // beyond the template piece's own box. Its drip columns remain
            // under the template. Include both regions, without Y reliance.
            int centerX = box.getCenter().getX();
            int centerZ = box.getCenter().getZ();
            return new BoundingBox(Math.min(box.minX(), Math.subtractExact(centerX, 14)),
                    box.minY(), Math.min(box.minZ(), Math.subtractExact(centerZ, 14)),
                    Math.max(box.maxX(), Math.addExact(centerX, 14)), box.maxY(),
                    Math.max(box.maxZ(), Math.addExact(centerZ, 14)));
        }
        return null;
    }

    private static boolean strongholdPiece(Class<?> kind) {
        return kind == StrongholdPieces.Straight.class
                || kind == StrongholdPieces.StraightStairsDown.class
                || kind == StrongholdPieces.PrisonHall.class
                || kind == StrongholdPieces.FiveCrossing.class
                || kind == StrongholdPieces.StairsDown.class
                || kind == StrongholdPieces.StartPiece.class
                || kind == StrongholdPieces.RoomCrossing.class
                || kind == StrongholdPieces.FillerCorridor.class
                || kind == StrongholdPieces.LeftTurn.class
                || kind == StrongholdPieces.RightTurn.class
                || kind == StrongholdPieces.ChestCorridor.class
                || kind == StrongholdPieces.Library.class
                || kind == StrongholdPieces.PortalRoom.class;
    }

    private static boolean fortressPiece(Class<?> kind) {
        if (kind.getDeclaringClass() != NetherFortressPieces.class) return false;
        return switch (kind.getSimpleName()) {
            case "StartPiece", "CastleSmallCorridorLeftTurnPiece",
                    "CastleSmallCorridorRightTurnPiece", "CastleCorridorStairsPiece",
                    "BridgeStraight", "CastleSmallCorridorCrossingPiece",
                    "MonsterThrone", "CastleCorridorTBalconyPiece",
                    "BridgeCrossing", "CastleEntrance", "CastleStalkRoom",
                    "StairsRoom", "CastleSmallCorridorPiece", "RoomCrossing",
                    "BridgeEndFiller" -> true;
            default -> false;
        };
    }

    private static boolean verifiedPoolElement(PoolElementStructurePiece piece,
                                               RegistryAccess registries,
                                               ResourceKey<Structure> structureKey,
                                               boolean exactVanillaJigsaw) {
        StructurePoolElement element = piece.getElement();
        Class<?> kind = element.getClass();
        if (kind != SinglePoolElement.class
                && kind != LegacySinglePoolElement.class) return false;
        // A custom processor's finalizeProcessing can write arbitrarily far
        // from its piece. Empty lists are safe; the four nonempty vanilla
        // lists below are accepted only after exact 26.3 content and class
        // checks. Unknown serialization and datapack overrides fail closed.
        JsonElement encoded = StructurePoolElement.CODEC.encodeStart(
                RegistryOps.create(JsonOps.INSTANCE, registries), element)
                .result().orElse(null);
        if (!(encoded instanceof JsonObject object)) return false;
        JsonElement processors = object.get("processors");
        if (processors instanceof JsonObject inline) {
            JsonElement entries = inline.get("processors");
            return entries instanceof JsonArray array && array.size() == 0;
        }
        if (processors instanceof JsonPrimitive id && id.isString()) {
            Identifier key = Identifier.tryParse(id.getAsString());
            if (key == null) return false;
            StructureProcessorList list = registries.lookupOrThrow(Registries.PROCESSOR_LIST)
                    .getValue(ResourceKey.create(Registries.PROCESSOR_LIST, key));
            if (list == null) return false;
            if (list.list().isEmpty()) return true;
            return exactVanillaJigsaw && verifiedVanillaProcessorList(
                    structureKey, key, list, registries);
        }
        return false;
    }

    private static boolean verifiedVanillaProcessorList(ResourceKey<Structure> structureKey,
            Identifier processorKey, StructureProcessorList runtime,
            RegistryAccess registries) {
        if (structureKey == null || !processorKey.getNamespace().equals("minecraft"))
            return false;
        String structureId = structureKey.identifier().toString();
        String name = processorKey.getPath();
        boolean trial = structureId.equals("minecraft:trial_chambers")
                && name.equals("trial_chambers_copper_bulb_degradation");
        boolean ancient = structureId.equals("minecraft:ancient_city")
                && (name.equals("ancient_city_generic_degradation")
                || name.equals("ancient_city_walls_degradation")
                || name.equals("ancient_city_start_degradation"));
        boolean bastion = structureId.equals("minecraft:bastion_remnant")
                && VANILLA_PROCESSOR_SHA256.containsKey(name)
                && !trial && !ancient;
        if (!trial && !ancient && !bastion) return false;
        List<?> processors = runtime.list();
        boolean hasRot = name.equals("ancient_city_generic_degradation")
                || name.equals("ancient_city_walls_degradation");
        if (bastion) {
            if (processors.size() != 1
                    || processors.getFirst().getClass() != RuleProcessor.class) return false;
        } else if (processors.size() != (hasRot ? 3 : 2)
                || hasRot && processors.get(0).getClass() != BlockRotProcessor.class
                || processors.get(hasRot ? 1 : 0).getClass() != RuleProcessor.class
                || processors.get(hasRot ? 2 : 1).getClass()
                        != ProtectedBlockProcessor.class) return false;
        JsonElement official = VANILLA_PROCESSOR_JSON.get(name);
        if (official == null) return false;
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        StructureProcessorList expected = StructureProcessorType.DIRECT_CODEC
                .parse(ops, official).result().orElse(null);
        if (expected == null) return false;
        JsonElement expectedEncoded = StructureProcessorType.DIRECT_CODEC
                .encodeStart(ops, expected).result().orElse(null);
        JsonElement runtimeEncoded = StructureProcessorType.DIRECT_CODEC
                .encodeStart(ops, runtime).result().orElse(null);
        return expectedEncoded != null && expectedEncoded.equals(runtimeEncoded);
    }

    private static Map<String, JsonElement> loadVanillaProcessorJson() {
        Map<String, JsonElement> originals = new HashMap<>();
        for (var entry : VANILLA_PROCESSOR_SHA256.entrySet()) {
            String path = "/data/minecraft/worldgen/processor_list/"
                    + entry.getKey() + ".json";
            try (InputStream stream = StructureStartReservation.class
                    .getResourceAsStream(path)) {
                if (stream == null) continue;
                byte[] bytes = stream.readAllBytes();
                String digest = HexFormat.of().formatHex(MessageDigest
                        .getInstance("SHA-256").digest(bytes));
                if (!digest.equals(entry.getValue())) continue;
                JsonElement json = JsonParser.parseString(
                        new String(bytes, StandardCharsets.UTF_8));
                if (json.isJsonObject()) originals.put(entry.getKey(), json);
            } catch (IOException | NoSuchAlgorithmException | RuntimeException ignored) {
                // Missing or changed vanilla resources retain UNKNOWN.
            }
        }
        return Map.copyOf(originals);
    }

    private static boolean overlapsAnyColumn(BoundingBox foreign,
                                             List<BoundingBox> site) {
        for (BoundingBox box : site) {
            if (foreign.maxX() >= box.minX() && box.maxX() >= foreign.minX()
                    && foreign.maxZ() >= box.minZ() && box.maxZ() >= foreign.minZ())
                return true;
        }
        return false;
    }

    private static void traceColumnOverlap(ChunkPos estate, Holder<Structure> structure,
                                           ChunkPos candidate, BoundingBox foreign,
                                           List<BoundingBox> site, int occupiedCount) {
        for (int index = 0; index < site.size(); index++) {
            BoundingBox box = site.get(index);
            if (foreign.maxX() < box.minX() || box.maxX() < foreign.minX()
                    || foreign.maxZ() < box.minZ() || box.maxZ() < foreign.minZ()) continue;
            System.out.println("FOUR_ROADS_ESTATE_RESERVATION_Y_SPAN estate=" + estate
                    + " foreign=" + structure.unwrapKey().orElseThrow()
                    + " start=" + candidate + " foreignBox=" + foreign
                    + " siteKind=" + (index < occupiedCount ? "occupied" : "arrival")
                    + " siteIndex=" + (index < occupiedCount ? index : index - occupiedCount)
                    + " siteBox=" + box
                    + " overlapsY=" + (foreign.maxY() >= box.minY()
                            && box.maxY() >= foreign.minY()));
        }
    }

    private static Verdict verdict(Status status, String reason, int candidates,
                                   int generated, int compared, long started) {
        long elapsed = System.nanoTime() - started;
        PhaseTiming timing = TIMING.get();
        if (timing != null) {
            if (timing.gridStarted != 0)
                timing.gridNanos += System.nanoTime() - timing.gridStarted;
            long selection = Math.max(0, timing.gridNanos
                    - timing.generationNanos - timing.comparisonNanos);
            System.out.println("STRUCTURE_START_RESERVATION_TIMING status=" + status
                    + " reason=" + reason
                    + " candidates=" + candidates + " starts=" + generated
                    + " pieces=" + compared
                    + " stateMs=" + timing.stateNanos / 1_000_000
                    + " reachMs=" + timing.reachNanos / 1_000_000
                    + " gridMs=" + selection / 1_000_000
                    + " generationMs=" + timing.generationNanos / 1_000_000
                    + " cacheHits=" + timing.cacheHits
                    + " comparisonMs=" + timing.comparisonNanos / 1_000_000
                    + " totalMs=" + elapsed / 1_000_000);
            TIMING.remove();
        }
        return new Verdict(status, reason, candidates, generated, compared,
                elapsed / 1_000_000);
    }

    private static final class PhaseTiming {
        private long stateNanos;
        private long reachNanos;
        private long gridNanos;
        private long gridStarted;
        private long generationNanos;
        private long cacheHits;
        private long comparisonNanos;
    }

    /** Equality deliberately uses object identity for worldgen inputs. */
    private record StartKey(long seed, ResourceKey<Level> dimension,
                            Structure structure, int chunkX, int chunkZ,
                            Object registries, Object generator, Object biomeSource,
                            Object climate, Object randomState, Object templates,
                            Object heights) {
        private StartKey(Structure.GenerationContext context,
                         ResourceKey<Level> dimension, Structure structure,
                         ChunkPos candidate) {
            this(context.seed(), dimension, structure, candidate.x(), candidate.z(),
                    context.registryAccess(), context.chunkGenerator(),
                    context.biomeSource(), context.climateSampler(),
                    context.randomState(), context.structureTemplateManager(),
                    context.heightAccessor());
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof StartKey key)) return false;
            return seed == key.seed && dimension == key.dimension
                    && structure == key.structure && chunkX == key.chunkX
                    && chunkZ == key.chunkZ && registries == key.registries
                    && generator == key.generator && biomeSource == key.biomeSource
                    && climate == key.climate && randomState == key.randomState
                    && templates == key.templates && heights == key.heights;
        }

        @Override
        public int hashCode() {
            int hash = Long.hashCode(seed);
            hash = 31 * hash + System.identityHashCode(dimension);
            hash = 31 * hash + System.identityHashCode(structure);
            hash = 31 * hash + System.identityHashCode(registries);
            hash = 31 * hash + System.identityHashCode(generator);
            hash = 31 * hash + System.identityHashCode(biomeSource);
            hash = 31 * hash + System.identityHashCode(climate);
            hash = 31 * hash + System.identityHashCode(randomState);
            hash = 31 * hash + System.identityHashCode(templates);
            hash = 31 * hash + System.identityHashCode(heights);
            hash = 31 * hash + chunkX;
            return 31 * hash + chunkZ;
        }
    }
}
