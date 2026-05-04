package com.feel.gems.adventure.worldgen.site;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces;
import net.minecraft.world.level.levelgen.structure.structures.ShipwreckPieces;

/** Test-only equality proof against every registered set's exhaustive scan. */
public final class ReservationCandidateEquivalence {
    private ReservationCandidateEquivalence() { }

    /** Exercise the exact vanilla ocean pieces behind the relay reservation. */
    public static void verifyVanillaOceanInfluence(ServerLevel level) {
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var monument = registry.getOrThrow(ResourceKey.create(Registries.STRUCTURE,
                Identifier.fromNamespaceAndPath("minecraft", "monument")));
        var shipwreck = registry.getOrThrow(ResourceKey.create(Registries.STRUCTURE,
                Identifier.fromNamespaceAndPath("minecraft", "shipwreck")));
        var building = new OceanMonumentPieces.MonumentBuilding(
                RandomSource.create(7L), 0, 0, Direction.NORTH);
        BoundingBox monumentBox = building.getBoundingBox();
        BoundingBox monumentInfluence = StructureStartReservation.verifiedInfluence(
                monument, building, level.registryAccess());
        if (monumentInfluence == null || monumentBox.getXSpan() != 58
                || monumentBox.getZSpan() != 58
                || monumentInfluence.minX() != monumentBox.minX() - 6
                || monumentInfluence.maxX() != monumentBox.maxX() + 6
                || monumentInfluence.minZ() != monumentBox.minZ() - 6
                || monumentInfluence.maxZ() != monumentBox.maxZ() + 6)
            throw new IllegalStateException("Monument water-clearing fringe is unreserved");

        var pieces = new StructurePiecesBuilder();
        var wreck = ShipwreckPieces.addRandomPiece(
                level.getServer().getStructureTemplateManager(),
                new BlockPos(0, 64, 0), Rotation.NONE, pieces,
                RandomSource.create(11L), false);
        BoundingBox wreckBox = wreck.getBoundingBox();
        BoundingBox wreckInfluence = StructureStartReservation.verifiedInfluence(
                shipwreck, wreck, level.registryAccess());
        if (wreckInfluence == null
                || wreckInfluence.minX() != wreckBox.minX()
                || wreckInfluence.maxX() != wreckBox.maxX()
                || wreckInfluence.minZ() != wreckBox.minZ()
                || wreckInfluence.maxZ() != wreckBox.maxZ()
                || StructureStartReservation.verifiedInfluence(
                        monument, wreck, level.registryAccess()) != null
                || StructureStartReservation.verifiedInfluence(
                        shipwreck, building, level.registryAccess()) != null)
            throw new IllegalStateException("Shipwreck bounds or exact-type guard failed");
        System.out.println("SATELLITE_OCEAN_FOREIGN_INFLUENCE_PASS");
    }

    /** Compare the current worldgen state with the inspector's fresh state. */
    public static void verifyStateParity(ServerLevel level,
                                         Structure.GenerationContext context,
                                         ResourceKey<Structure> self,
                                         List<BoundingBox> occupied,
                                         List<BoundingBox> arrivals,
                                         String label,
                                         StructureStartReservation.Verdict freshVerdict) {
        var source = level.getChunkSource();
        int loadedBefore = source.getLoadedChunksCount();
        ChunkGeneratorStructureState authoritative = source.getGeneratorState();
        ChunkGeneratorStructureState fresh = context.chunkGenerator().createState(
                context.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET),
                context.randomState(), context.seed());
        if (authoritative.getLevelSeed() != fresh.getLevelSeed())
            throw new IllegalStateException("Estate structure-state seed mismatch: " + label);
        Map<String, Holder<StructureSet>> authoritativeSets = new TreeMap<>();
        for (var holder : authoritative.possibleStructureSets())
            if (authoritativeSets.put(holder.unwrapKey().orElseThrow().toString(),
                    holder) != null)
                throw new IllegalStateException("Duplicate authoritative structure set");
        Map<String, Holder<StructureSet>> freshSets = new TreeMap<>();
        for (var holder : fresh.possibleStructureSets())
            if (freshSets.put(holder.unwrapKey().orElseThrow().toString(), holder) != null)
                throw new IllegalStateException("Duplicate fresh structure set");
        if (!authoritativeSets.keySet().equals(freshSets.keySet()))
            throw new IllegalStateException("Estate structure-state set mismatch: " + label);
        int candidates = 0;
        long freshGridNanos = 0, authoritativeGridNanos = 0;
        String slowestSet = "none";
        long slowestNanos = 0;
        StructureStartReach.Session reaches = new StructureStartReach.Session();
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BoundingBox box : occupied) {
            minX = Math.min(minX, box.minX());
            minZ = Math.min(minZ, box.minZ());
            maxX = Math.max(maxX, box.maxX());
            maxZ = Math.max(maxZ, box.maxZ());
        }
        for (BoundingBox box : arrivals) {
            minX = Math.min(minX, box.minX());
            minZ = Math.min(minZ, box.minZ());
            maxX = Math.max(maxX, box.maxX());
            maxZ = Math.max(maxZ, box.maxZ());
        }
        for (var entry : freshSets.entrySet()) {
            var set = entry.getValue().value();
            int reach = 0;
            for (var choice : set.structures()) {
                int bound = reaches.chunks(choice.structure().value(),
                        context.structureTemplateManager(), context.registryAccess());
                if (bound < 1)
                    throw new IllegalStateException("Unbounded state-parity set: "
                            + entry.getKey());
                reach = Math.max(reach, bound);
            }
            int minCx = (minX >> 4) - reach, maxCx = (maxX >> 4) + reach;
            int minCz = (minZ >> 4) - reach, maxCz = (maxZ >> 4) + reach;
            long started = System.nanoTime();
            List<ChunkPos> fromFresh = StructureStartReservation.candidateChunks(
                    set.placement(), fresh, minCx, maxCx, minCz, maxCz);
            long freshNanos = System.nanoTime() - started;
            started = System.nanoTime();
            List<ChunkPos> fromAuthoritative = StructureStartReservation.candidateChunks(
                    authoritativeSets.get(entry.getKey()).value().placement(),
                    authoritative, minCx, maxCx, minCz, maxCz);
            long authoritativeNanos = System.nanoTime() - started;
            if (fromFresh == null || !fromFresh.equals(fromAuthoritative))
                throw new IllegalStateException("Estate structure-state candidate mismatch: "
                        + label + " set=" + entry.getKey());
            freshGridNanos += freshNanos;
            authoritativeGridNanos += authoritativeNanos;
            if (freshNanos > slowestNanos) {
                slowestNanos = freshNanos;
                slowestSet = entry.getKey();
            }
            candidates += fromFresh.size();
        }
        long started = System.nanoTime();
        var authoritativeVerdict = StructureStartReservation.inspectWithState(
                context, level.dimension(), self, occupied, arrivals, authoritative);
        long authoritativeNanos = System.nanoTime() - started;
        int loadedAfter = source.getLoadedChunksCount();
        if (loadedAfter != loadedBefore
                || freshVerdict.status() != authoritativeVerdict.status()
                || !freshVerdict.reason().equals(authoritativeVerdict.reason())
                || freshVerdict.candidateChunks() != authoritativeVerdict.candidateChunks()
                || freshVerdict.generatedStarts() != authoritativeVerdict.generatedStarts()
                || freshVerdict.comparedPieces() != authoritativeVerdict.comparedPieces())
            throw new IllegalStateException("Estate structure-state verdict mismatch: "
                    + label + " fresh=" + freshVerdict
                    + " authoritative=" + authoritativeVerdict
                    + " loaded=" + loadedBefore + "->" + loadedAfter);
        System.out.println("FOUR_ROADS_ESTATE_RESERVATION_STATE_PARITY label=" + label
                + " sets=" + freshSets.size() + " candidates=" + candidates
                + " freshGridMs=" + freshGridNanos / 1_000_000
                + " authoritativeGridMs=" + authoritativeGridNanos / 1_000_000
                + " slowestFreshSet=" + slowestSet
                + " slowestFreshSetMs=" + slowestNanos / 1_000_000
                + " freshVerdictMs=" + freshVerdict.elapsedMillis()
                + " authoritativeVerdictMs=" + authoritativeNanos / 1_000_000
                + " loaded=" + loadedBefore + "->" + loadedAfter);
    }

    public static void verify(Structure.GenerationContext context,
                              List<BoundingBox> site, String label) {
        if (site.isEmpty()) throw new IllegalArgumentException("empty site");
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BoundingBox box : site) {
            minX = Math.min(minX, box.minX());
            minZ = Math.min(minZ, box.minZ());
            maxX = Math.max(maxX, box.maxX());
            maxZ = Math.max(maxZ, box.maxZ());
        }
        ChunkGeneratorStructureState state = context.chunkGenerator().createState(
                context.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET),
                context.randomState(), context.seed());
        StructureStartReach.Session reaches = new StructureStartReach.Session();
        int sets = 0, cells = 0, candidates = 0;
        long started = System.nanoTime();
        for (var holder : state.possibleStructureSets()) {
            var set = holder.value();
            int reach = 0;
            for (var entry : set.structures()) {
                int bound = reaches.chunks(entry.structure().value(),
                        context.structureTemplateManager(), context.registryAccess());
                if (bound < 1)
                    throw new IllegalStateException("Unbounded set in equality test: " + holder);
                reach = Math.max(reach, bound);
            }
            int minCx = (minX >> 4) - reach, maxCx = (maxX >> 4) + reach;
            int minCz = (minZ >> 4) - reach, maxCz = (maxZ >> 4) + reach;
            List<ChunkPos> compact = StructureStartReservation.candidateChunks(
                    set.placement(), state, minCx, maxCx, minCz, maxCz);
            if (compact == null)
                throw new IllegalStateException("Candidate enumeration overflow: " + holder);
            List<ChunkPos> exhaustive = new ArrayList<>();
            for (int cx = minCx; cx <= maxCx; cx++) {
                for (int cz = minCz; cz <= maxCz; cz++) {
                    if (set.placement().isStructureChunk(state, cx, cz))
                        exhaustive.add(new ChunkPos(cx, cz));
                }
            }
            if (!compact.equals(exhaustive))
                throw new IllegalStateException("Candidate mismatch for " + holder
                        + " compact=" + compact + " exhaustive=" + exhaustive);
            sets++;
            cells += (maxCx - minCx + 1) * (maxCz - minCz + 1);
            candidates += compact.size();
        }
        System.out.println("FOUR_ROADS_ESTATE_RESERVATION_CANDIDATE_EQUIVALENCE"
                + " label=" + label + " sets=" + sets + " exhaustiveCells=" + cells
                + " candidates=" + candidates
                + " ms=" + (System.nanoTime() - started) / 1_000_000);
    }
}
