package com.feel.gems.gametest.adventure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/** Fixed Common-1/2/3 radius-512 search; only the staged set is inspected. */
final class EstateCohort512Probe {
    private static final int RADIUS = 512;
    private static final int SPACING = 16;
    private static final int BATCH = 8;
    private static final ResourceKey<StructureSet> SET = ResourceKey.create(
            Registries.STRUCTURE_SET,
            Identifier.fromNamespaceAndPath("gems", "four_roads_estate_v1"));

    record BatchResult(int examined, int validStarts, int non25, int conflict,
                       int unknown, int loaded, ChunkPos selected, long elapsedMillis) { }

    private EstateCohort512Probe() { }

    static int batchSize() { return BATCH; }

    static List<ChunkPos> candidates(ServerLevel level) {
        StructureSet set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)
                .getOrThrow(SET).value();
        if (!(set.placement() instanceof RandomSpreadStructurePlacement placement)
                || placement.spacing() != SPACING || placement.separation() != 15
                || set.structures().size() != 1)
            throw new IllegalStateException("Estate cohort requires staged 16/15 set");
        var state = level.getChunkSource().getGeneratorState();
        Set<Long> seen = new HashSet<>();
        List<ChunkPos> ordered = new ArrayList<>();
        for (int rx = -RADIUS / SPACING; rx <= RADIUS / SPACING; rx++) {
            for (int rz = -RADIUS / SPACING; rz <= RADIUS / SPACING; rz++) {
                ChunkPos candidate = placement.getPotentialStructureChunk(level.getSeed(),
                        rx * SPACING, rz * SPACING);
                if (Math.max(Math.abs(candidate.x()), Math.abs(candidate.z())) > RADIUS
                        || !placement.isStructureChunk(state, candidate.x(), candidate.z())
                        || !seen.add(candidate.pack())) continue;
                ordered.add(candidate);
            }
        }
        ordered.sort(Comparator.comparingInt(EstateCohort512Probe::ring)
                .thenComparingLong(candidate -> (long) candidate.x() * candidate.x()
                        + (long) candidate.z() * candidate.z())
                .thenComparingInt(ChunkPos::x).thenComparingInt(ChunkPos::z));
        System.out.println("FOUR_ROADS_ESTATE_COHORT512_LATTICE seed=" + level.getSeed()
                + " radiusChunks=" + RADIUS + " spacing=" + SPACING
                + " candidates=" + ordered.size() + " order=ring,distance,x,z");
        return List.copyOf(ordered);
    }

    static BatchResult inspect(ServerLevel level, String seed,
                               List<ChunkPos> candidates, int offset) {
        long started = System.nanoTime();
        int examined = 0, valid = 0, non25 = 0, conflict = 0, unknown = 0, loaded = 0;
        ChunkPos selected = null;
        for (int i = offset; i < Math.min(offset + BATCH, candidates.size()); i++) {
            ChunkPos chunk = candidates.get(i);
            EstateReservationProbe.Candidate probe =
                    EstateReservationProbe.inspectCandidate(level, chunk);
            examined++;
            switch (probe.status()) {
                case "NO_START" -> { }
                case "NON_25_PIECE_START" -> non25++;
                case "ALREADY_LOADED", "BODY_OR_ARRIVAL_ALREADY_LOADED" -> loaded++;
                case "CLEAR" -> {
                    valid++;
                    selected = chunk;
                }
                case "CONFLICT" -> {
                    valid++;
                    conflict++;
                }
                case "UNKNOWN" -> {
                    valid++;
                    unknown++;
                }
                default -> throw new IllegalStateException("Estate cohort site unverified: "
                        + probe.status() + " @ " + chunk);
            }
            if (probe.reservation() != null)
                System.out.println("FOUR_ROADS_ESTATE_COHORT512_SITE seed=" + seed
                        + " chunk=" + chunk.x() + "," + chunk.z()
                        + " status=" + probe.status()
                        + " reason=" + probe.reservation().reason()
                        + " candidates=" + probe.reservation().candidateChunks()
                        + " starts=" + probe.reservation().generatedStarts()
                        + " ms=" + probe.reservation().elapsedMillis());
            if (selected != null) break;
        }
        return new BatchResult(examined, valid, non25, conflict, unknown, loaded,
                selected, (System.nanoTime() - started) / 1_000_000);
    }

    private static int ring(ChunkPos candidate) {
        return Math.max(Math.abs(candidate.x()), Math.abs(candidate.z()));
    }
}
