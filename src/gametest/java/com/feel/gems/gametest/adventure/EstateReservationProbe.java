package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.estate.FourRoadsEstateStructure;
import com.feel.gems.adventure.worldgen.site.StructureStartReservation;
import com.feel.gems.adventure.worldgen.site.ReservationCandidateEquivalence;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/** Recorded 26.3 saved-world sentinels for the shared noise-only reservation. */
final class EstateReservationProbe {
    private static final ResourceKey<Structure> ESTATE = structure("gems",
            "four_roads_estate_v1");

    private EstateReservationProbe() { }

    record Candidate(String status, FourRoadsEstateStructure.Rejection generationReason,
                     StructureStartReservation.Verdict reservation) { }

    /** Noise-only cohort gate; no chunk may be loaded or changed by this check. */
    static Candidate inspectCandidate(ServerLevel level, ChunkPos chunk) {
        var source = level.getChunkSource();
        int loadedBefore = source.getLoadedChunksCount();
        if (source.hasChunk(chunk.x(), chunk.z()))
            return new Candidate("ALREADY_LOADED", null, null);
        long directStarted = System.nanoTime();
        FourRoadsEstateStructure.beginCohortProbe();
        StructureStart start = noiseStart(level, ESTATE, chunk.x(), chunk.z());
        FourRoadsEstateStructure.Rejection generationReason =
                FourRoadsEstateStructure.lastCohortReason();
        if (start.isValid() && Boolean.getBoolean(
                "gems.fourRoadsEstateTracePlacementLatency"))
            System.out.println("FOUR_ROADS_ESTATE_DIRECT_NOISE seed=" + level.getSeed()
                    + " chunk=" + chunk + " thread=" + Thread.currentThread().getName()
                    + " ms=" + (System.nanoTime() - directStarted) / 1_000_000
                    + " pieces=" + start.getPieces().size());
        if (source.getLoadedChunksCount() != loadedBefore
                || source.hasChunk(chunk.x(), chunk.z()))
            throw new IllegalStateException("Estate direct noise loaded a chunk: " + chunk);
        if (!start.isValid()) return new Candidate("NO_START", generationReason, null);
        if (start.getPieces().size() != 25)
            return new Candidate("NON_25_PIECE_START", generationReason, null);
        Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getOrThrow(ESTATE).value();
        if (!(structure instanceof FourRoadsEstateStructure estateStructure))
            return new Candidate("UNVERIFIED_STRUCTURE_TYPE", generationReason, null);
        List<BoundingBox> arrivals = estateStructure.reservationApproaches(start);
        if (arrivals == null || arrivals.size() != 4)
            return new Candidate("UNVERIFIED_ARRIVALS", generationReason, null);
        List<BoundingBox> occupied = start.getPieces().stream()
                .map(piece -> piece.getBoundingBox()).toList();
        // A startup/player-loaded body or arrival chunk is an existing site,
        // even in this disposable world. Never select it for forced placement.
        for (BoundingBox box : java.util.stream.Stream.concat(
                occupied.stream(), arrivals.stream()).toList()) {
            for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
                for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                    if (source.hasChunk(cx, cz))
                        return new Candidate("BODY_OR_ARRIVAL_ALREADY_LOADED",
                                generationReason, null);
                }
            }
        }
        var verdict = StructureStartReservation.inspect(generationContext(level, chunk),
                level.dimension(), ESTATE, occupied, arrivals);
        int loadedAfter = source.getLoadedChunksCount();
        if (loadedAfter != loadedBefore || source.hasChunk(chunk.x(), chunk.z()))
            throw new IllegalStateException("Estate cohort noise check loaded chunks: "
                    + chunk + " " + loadedBefore + "->" + loadedAfter);
        return new Candidate(verdict.status().name(), generationReason, verdict);
    }

    static void verify(ServerLevel level, String seed) {
        if (seed.endsWith("-1")) {
            inspectEstate(level, seed, -1024, 1536,
                    StructureStartReservation.Status.CLEAR, "SAVED_CLEAR");
            StructureStart sentinel = inspectEstate(level, seed, -1728, -2592,
                    StructureStartReservation.Status.CONFLICT, "THREE_TOWNS");
            exactTownSentinel(level, sentinel);
            StructureStart mineshaft = noiseStart(level,
                    structure("minecraft", "mineshaft"), -76, 86);
            if (!mineshaft.isValid() || mineshaft.getPieces().size() != 104)
                throw new IllegalStateException("Recorded deep mineshaft changed");
            StructureStart clear = noiseStart(level, ESTATE, -64, 96);
            int columnHits = pieceColumnHits(clear, mineshaft);
            int volumeHits = pieceHits(clear, mineshaft);
            if (columnHits != 0)
                throw new IllegalStateException("Deep mineshaft column intersects estate: "
                        + columnHits);
            System.out.println("FOUR_ROADS_ESTATE_RESERVATION_MINESHAFT seed=" + seed
                    + " pieces=" + mineshaft.getPieces().size()
                    + " columnHits=" + columnHits + " volumeHits=" + volumeHits);
            placementContracts(level);
        } else if (seed.endsWith("-3")) {
            StructureStart estate = inspectEstate(level, seed, 2816, -1792,
                    StructureStartReservation.Status.CONFLICT, "PYRAMID_PORTAL");
            StructureStart pyramid = null;
            StructureStart portal = null;
            for (var expected : List.of(
                    new Foreign("desert_pyramid", 181, -105),
                    new Foreign("ruined_portal_desert", 178, -117))) {
                StructureStart foreign = noiseStart(level,
                        structure("minecraft", expected.id()),
                        expected.cx(), expected.cz());
                int columnHits = pieceColumnHits(estate, foreign);
                int volumeHits = pieceHits(estate, foreign);
                System.out.println("FOUR_ROADS_ESTATE_RESERVATION_FOREIGN seed=" + seed
                        + " structure=minecraft:" + expected.id()
                        + " chunk=" + expected.cx() + "," + expected.cz()
                        + " valid=" + foreign.isValid()
                        + " pieces=" + foreign.getPieces().size()
                        + " bounds=" + (foreign.isValid() ? foreign.getBoundingBox() : "none")
                        + " columnHits=" + columnHits + " volumeHits=" + volumeHits);
                if (!foreign.isValid() || columnHits < 1)
                    throw new IllegalStateException("Saved foreign start not reproduced: "
                            + expected + " columnHits=" + columnHits);
                if (expected.id().equals("desert_pyramid")) pyramid = foreign;
                if (expected.id().equals("ruined_portal_desert")) portal = foreign;
            }
            StructureStart finalPyramid = pyramid;
            List<BoundingBox> pyramidColumns = estate.getPieces().stream()
                    .map(piece -> piece.getBoundingBox())
                    .filter(box -> finalPyramid.getPieces().stream().anyMatch(other ->
                            columnsOverlap(box, other.getBoundingBox())))
                    .toList();
            int loadedBefore = level.getChunkSource().getLoadedChunksCount();
            var pyramidVerdict = StructureStartReservation.inspect(
                    generationContext(level, new ChunkPos(176, -112)), level.dimension(),
                    ESTATE, pyramidColumns, List.of());
            int loadedAfter = level.getChunkSource().getLoadedChunksCount();
            System.out.println("FOUR_ROADS_ESTATE_RESERVATION_PYRAMID_ONLY seed=" + seed
                    + " status=" + pyramidVerdict.status()
                    + " reason=" + pyramidVerdict.reason()
                    + " sitePieces=" + pyramidColumns.size()
                    + " candidates=" + pyramidVerdict.candidateChunks()
                    + " starts=" + pyramidVerdict.generatedStarts()
                    + " ms=" + pyramidVerdict.elapsedMillis()
                    + " loadedBefore=" + loadedBefore + " loadedAfter=" + loadedAfter);
            if (pyramidVerdict.status() != StructureStartReservation.Status.CONFLICT
                    || !pyramidVerdict.reason().contains("minecraft:desert_pyramid")
                    || loadedAfter != loadedBefore)
                throw new IllegalStateException("Pyramid-only reservation missed saved start: "
                        + pyramidVerdict);
            BoundingBox portalBox = portal.getPieces().getFirst().getBoundingBox();
            int fringeX = portalBox.getCenter().getX() - 12;
            int fringeZ = portalBox.getCenter().getZ();
            if (fringeX >= portalBox.minX())
                throw new IllegalStateException("Portal fringe is inside saved piece box");
            BoundingBox fringe = new BoundingBox(fringeX, portalBox.minY(), fringeZ,
                    fringeX, portalBox.maxY(), fringeZ);
            int fringeLoadedBefore = level.getChunkSource().getLoadedChunksCount();
            var fringeVerdict = StructureStartReservation.inspect(
                    generationContext(level, new ChunkPos(176, -112)), level.dimension(),
                    ESTATE, List.of(fringe), List.of());
            int fringeLoadedAfter = level.getChunkSource().getLoadedChunksCount();
            System.out.println("FOUR_ROADS_ESTATE_RESERVATION_PORTAL_FRINGE seed=" + seed
                    + " x=" + fringeX + " z=" + fringeZ
                    + " piece=" + portalBox
                    + " status=" + fringeVerdict.status()
                    + " reason=" + fringeVerdict.reason()
                    + " candidates=" + fringeVerdict.candidateChunks()
                    + " starts=" + fringeVerdict.generatedStarts()
                    + " ms=" + fringeVerdict.elapsedMillis()
                    + " loadedBefore=" + fringeLoadedBefore
                    + " loadedAfter=" + fringeLoadedAfter);
            if (fringeVerdict.status() != StructureStartReservation.Status.CONFLICT
                    || !fringeVerdict.reason().contains("minecraft:ruined_portal_desert")
                    || fringeLoadedAfter != fringeLoadedBefore)
                throw new IllegalStateException("Portal fringe reservation missed decoration: "
                        + fringeVerdict);
        }
    }

    private record Foreign(String id, int cx, int cz) { }

    private static StructureStart inspectEstate(ServerLevel level, String seed,
            int x, int z, StructureStartReservation.Status expected, String label) {
        ChunkPos chunk = new ChunkPos(x >> 4, z >> 4);
        long directStarted = System.nanoTime();
        StructureStart estate = noiseStart(level, ESTATE, chunk.x(), chunk.z());
        if (Boolean.getBoolean("gems.fourRoadsEstateTracePlacementLatency"))
            System.out.println("FOUR_ROADS_ESTATE_DIRECT_NOISE seed=" + seed
                    + " chunk=" + chunk + " thread=" + Thread.currentThread().getName()
                    + " ms=" + (System.nanoTime() - directStarted) / 1_000_000
                    + " pieces=" + estate.getPieces().size());
        if (!estate.isValid() || estate.getPieces().size() != 25)
            throw new IllegalStateException("Recorded estate missing at " + x + "," + z);
        List<BoundingBox> occupied = estate.getPieces().stream()
                .map(piece -> piece.getBoundingBox()).toList();
        Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getOrThrow(ESTATE).value();
        if (!(structure instanceof FourRoadsEstateStructure estateStructure))
            throw new IllegalStateException("Estate reservation structure type changed");
        List<BoundingBox> arrivals = estateStructure.reservationApproaches(estate);
        if (arrivals == null || arrivals.size() != 4)
            throw new IllegalStateException("Estate reservation lost four arrivals");
        Structure.GenerationContext generation = generationContext(level, chunk);
        var source = level.getChunkSource();
        int loadedBefore = source.getLoadedChunksCount();
        boolean candidateLoadedBefore = source.hasChunk(chunk.x(), chunk.z());
        var verdict = StructureStartReservation.inspect(generation,
                level.dimension(), ESTATE, occupied, arrivals);
        int loadedAfter = source.getLoadedChunksCount();
        boolean candidateLoadedAfter = source.hasChunk(chunk.x(), chunk.z());
        System.out.println("FOUR_ROADS_ESTATE_RESERVATION seed=" + seed
                + " label=" + label + " x=" + x + " z=" + z
                + " status=" + verdict.status() + " reason=" + verdict.reason()
                + " candidates=" + verdict.candidateChunks()
                + " starts=" + verdict.generatedStarts()
                + " pieces=" + verdict.comparedPieces()
                + " ms=" + verdict.elapsedMillis()
                + " loadedBefore=" + loadedBefore
                + " loadedAfter=" + loadedAfter
                + " candidateLoadedBefore=" + candidateLoadedBefore
                + " candidateLoadedAfter=" + candidateLoadedAfter);
        if (verdict.status() != expected
                || candidateLoadedAfter != candidateLoadedBefore)
            throw new IllegalStateException("Reservation sentinel mismatch: " + label
                    + " " + verdict);
        if (label.equals("SAVED_CLEAR") || label.equals("PYRAMID_PORTAL")) {
            if (Boolean.getBoolean("gems.fourRoadsEstateCandidateEquivalence")) {
                List<BoundingBox> site = new java.util.ArrayList<>(occupied);
                site.addAll(arrivals);
                ReservationCandidateEquivalence.verify(generation, site, label);
            }
            if (Boolean.getBoolean("gems.fourRoadsEstateStateParity"))
                ReservationCandidateEquivalence.verifyStateParity(level, generation,
                        ESTATE, occupied, arrivals, label, verdict);
        }
        return estate;
    }

    private static void exactTownSentinel(ServerLevel level, StructureStart estate) {
        StructureSet set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)
                .getOrThrow(ResourceKey.create(Registries.STRUCTURE_SET,
                        Identifier.fromNamespaceAndPath("gems",
                                "second_chance_town_themed"))).value();
        if (set.structures().size() != 1)
            throw new IllegalStateException("Themed town set changed");
        int hits = 0;
        for (int[] chunk : new int[][]{{-117, -166}, {-119, -152}, {-103, -164}}) {
            StructureStart town = noiseStart(level, set.structures().getFirst().structure(),
                    chunk[0], chunk[1]);
            if (!town.isValid())
                throw new IllegalStateException("Recorded town missing at "
                        + chunk[0] + "," + chunk[1]);
            hits += pieceHits(estate, town);
        }
        if (hits != 14)
            throw new IllegalStateException("Saved triple-town intersection changed: " + hits);
        System.out.println("FOUR_ROADS_ESTATE_RESERVATION_TOWNS starts=3 bodyHits=" + hits);
    }

    private static void placementContracts(ServerLevel level) {
        var sets = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        StructureSet mineshafts = sets.getOrThrow(ResourceKey.create(
                Registries.STRUCTURE_SET,
                Identifier.fromNamespaceAndPath("minecraft", "mineshafts"))).value();
        StructureSet portals = sets.getOrThrow(ResourceKey.create(
                Registries.STRUCTURE_SET,
                Identifier.fromNamespaceAndPath("minecraft", "ruined_portals"))).value();
        StructureSet outposts = sets.getOrThrow(ResourceKey.create(
                Registries.STRUCTURE_SET,
                Identifier.fromNamespaceAndPath("minecraft", "pillager_outposts"))).value();
        if (mineshafts.structures().size() != 2 || portals.structures().size() != 7
                || !(outposts.placement() instanceof RandomSpreadStructurePlacement spread))
            throw new IllegalStateException("Multi-entry or exclusion set changed");
        var source = level.getChunkSource();
        var state = source.getGenerator().createState(sets,
                source.randomState(), level.getSeed());
        boolean excluded = false;
        int spacing = spread.spacing();
        for (int gx = -32; gx <= 32 && !excluded; gx++) {
            for (int gz = -32; gz <= 32; gz++) {
                ChunkPos candidate = spread.getPotentialStructureChunk(level.getSeed(),
                        gx * spacing, gz * spacing);
                if (!spread.isStructureChunk(state, candidate.x(), candidate.z())) {
                    excluded = true;
                    System.out.println("FOUR_ROADS_ESTATE_RESERVATION_EXCLUSION"
                            + " set=minecraft:pillager_outposts"
                            + " chunk=" + candidate);
                    break;
                }
            }
        }
        if (!excluded) throw new IllegalStateException("No exclusion-zone sample found");
        System.out.println("FOUR_ROADS_ESTATE_RESERVATION_SET_ENTRIES"
                + " mineshafts=2 ruinedPortals=7 outpostExclusion=true");
    }

    private static int pieceHits(StructureStart estate, StructureStart foreign) {
        if (!foreign.isValid()) return 0;
        int hits = 0;
        for (var other : foreign.getPieces()) {
            for (var own : estate.getPieces()) {
                if (other.getBoundingBox().intersects(own.getBoundingBox())) {
                    hits++;
                    break;
                }
            }
        }
        return hits;
    }

    private static int pieceColumnHits(StructureStart estate, StructureStart foreign) {
        if (!foreign.isValid()) return 0;
        int hits = 0;
        for (var other : foreign.getPieces()) {
            BoundingBox a = other.getBoundingBox();
            for (var own : estate.getPieces()) {
                BoundingBox b = own.getBoundingBox();
                if (columnsOverlap(a, b)) {
                    hits++;
                    break;
                }
            }
        }
        return hits;
    }

    private static boolean columnsOverlap(BoundingBox a, BoundingBox b) {
        return a.maxX() >= b.minX() && b.maxX() >= a.minX()
                && a.maxZ() >= b.minZ() && b.maxZ() >= a.minZ();
    }

    private static Structure.GenerationContext generationContext(ServerLevel level,
            ChunkPos chunk) {
        Holder<Structure> holder = level.registryAccess()
                .lookupOrThrow(Registries.STRUCTURE).getOrThrow(ESTATE);
        var source = level.getChunkSource();
        var randomState = source.randomState();
        return new Structure.GenerationContext(level.registryAccess(),
                source.getGenerator(), source.getGenerator().getBiomeSource(),
                randomState.createClimateSampler(SamplerContext.EMPTY_UNCACHED),
                randomState, level.getServer().getStructureTemplateManager(),
                level.getSeed(), chunk, level, holder.value().biomes()::contains);
    }

    private static StructureStart noiseStart(ServerLevel level,
            ResourceKey<Structure> structure, int cx, int cz) {
        Holder<Structure> holder = level.registryAccess()
                .lookupOrThrow(Registries.STRUCTURE).getOrThrow(structure);
        return noiseStart(level, holder, cx, cz);
    }

    private static StructureStart noiseStart(ServerLevel level,
            Holder<Structure> holder, int cx, int cz) {
        var source = level.getChunkSource();
        var randomState = source.randomState();
        return holder.value().generate(holder, level.dimension(),
                level.registryAccess(), source.getGenerator(),
                source.getGenerator().getBiomeSource(),
                randomState.createClimateSampler(SamplerContext.EMPTY_UNCACHED),
                randomState, level.getServer().getStructureTemplateManager(),
                level.getSeed(), new ChunkPos(cx, cz), 0, level,
                holder.value().biomes()::contains);
    }

    private static ResourceKey<Structure> structure(String namespace, String path) {
        return ResourceKey.create(Registries.STRUCTURE,
                Identifier.fromNamespaceAndPath(namespace, path));
    }
}
