package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.AdventureEntities;
import com.feel.gems.adventure.DomainBlocks;
import com.feel.gems.adventure.worldgen.NourCoastalStructure;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Opt-in screenshot gate for generated 26.3 districts. The starts and their pieces come from
 * normal world generation; only the creature portraits are staged, using production entity types.
 * Screenshots still require a human visual and traversal review.
 */
@SuppressWarnings("UnstableApiUsage")
public final class NaturalDistrictVisualClientGameTest implements FabricClientGameTest {
    private static final Identifier FAM_EAST_REFERENCE =
            Identifier.fromNamespaceAndPath("gems", "fam_east_reference_v1");
    private record Area(String target, EntityType<? extends PathfinderMob> creature) {}

    private static final List<Area> SEED_A = List.of(
            new Area("second_chance_town", null),
            new Area("framefall_gallery", null),
            new Area("lastlight_exchange", null),
            new Area("rehearsal_works", null),
            new Area("branch_vault", null),
            new Area("redline_house", null),
            new Area("hushwire", null),
            new Area("unwritten_court", null),
            new Area("inkbound_workshop", AdventureEntities.INKLING));

    private static final List<Area> SEED_B = List.of(
            new Area("khaled_floodgate", AdventureEntities.REEDRUNNER),
            new Area("nour_signal_harbor", AdventureEntities.BEACON_GULL),
            new Area("fam_forum", AdventureEntities.CHORUS_HARE),
            new Area("adl_survey_house", AdventureEntities.CAIRN_BEETLE),
            new Area("othman_library", AdventureEntities.CATALOG_OWL),
            new Area("yehia_story_lodge", AdventureEntities.STORYBOOK_FOLDWING),
            new Area("omar_lost_quarter", AdventureEntities.DUST_SKIPPER),
            new Area("soryal_repair_yard", AdventureEntities.RIVET_CRAB),
            new Area("ali_splitstone_cloister", AdventureEntities.SPLITSTONE_RAM));

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!Boolean.getBoolean("gems.naturalDistrictVisualSweep")) return;
        String requestedTarget = System.getProperty("gems.naturalDistrictTarget", "");
        boolean soryalCOnly = requestedTarget.equals("soryal_repair_yard:seed-c");
        String target = soryalCOnly ? "soryal_repair_yard" : requestedTarget;
        boolean coastalProbe = Boolean.getBoolean("gems.nourCoastalProbeOnly");
        boolean boatProbe = Boolean.getBoolean("gems.nourPlayerBoatProbe");
        List<Area> seedA = (coastalProbe || boatProbe
                ? List.of(new Area("nour_signal_harbor", null)) : SEED_A).stream()
                .filter(area -> target.isEmpty() || area.target().equals(target)).toList();
        List<Area> seedB = (boatProbe || soryalCOnly ? List.<Area>of() : SEED_B).stream()
                .filter(area -> target.isEmpty() || area.target().equals(target)).toList();
        List<String> failures = new ArrayList<>();
        if (!seedA.isEmpty()) {
            try {
                sweep(context, "SecondChanceBiomeSmoke", "seed-a", seedA, failures,
                        coastalProbe, boatProbe);
            } catch (RuntimeException error) {
                recordFailure(failures, "seed-a", "world", "lifecycle", error);
            }
        }
        if (!seedB.isEmpty()) {
            try {
                sweep(context, "SecondChanceBiomeSmokeB", "seed-b", seedB, failures,
                        coastalProbe, boatProbe);
            } catch (RuntimeException error) {
                recordFailure(failures, "seed-b", "world", "lifecycle", error);
            }
        }
        boolean soryalSeedC = target.equals("soryal_repair_yard");
        if (soryalSeedC) {
            try {
                sweep(context, "SecondChanceBiomeSmokeC", "seed-c",
                        List.of(new Area("soryal_repair_yard", AdventureEntities.RIVET_CRAB)),
                        failures, false, false);
            } catch (RuntimeException error) {
                recordFailure(failures, "seed-c", "world", "lifecycle", error);
            }
        }
        System.out.println("NATURAL_DISTRICT_SWEEP_SUMMARY areas="
                + (seedA.size() + seedB.size() + (soryalSeedC ? 1 : 0))
                + " failures=" + failures.size());
        if (coastalProbe) System.out.println("NOUR_COASTAL_CANDIDATES "
                + NourCoastalStructure.candidateCounts());
        if (!failures.isEmpty()) {
            throw new IllegalStateException("Natural district sweep failures ("
                    + failures.size() + "):\n" + String.join("\n", failures));
        }
    }

    private static void sweep(ClientGameTestContext context, String seed, String label,
            List<Area> areas, List<String> failures, boolean coastalProbe,
            boolean boatProbe) {
        try (TestSingleplayerContext world = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(settings -> {
                    settings.setSeed(seed);
                    settings.setGenerateStructures(true);
                }).create()) {
            world.getConnection().waitForChunksRender();
            for (Area area : areas) {
                Site site;
                try {
                    site = world.getServer().computeOnServer(server -> {
                    ServerLevel level = server.overworld();
                    var registry = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
                    TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE,
                            Identifier.fromNamespaceAndPath("gems", "themed_target/" + area.target()));
                    boolean themed = registry.getTagOrEmpty(tag).iterator().hasNext();
                    int rings = themed ? themedSearchRings(area.target()) : 0;
                    BlockPos located = themed ? level.findNearestMapStructure(tag,
                            new BlockPos(0, 80, 0), rings, false) : null;
                    if (located == null) {
                        tag = TagKey.create(Registries.STRUCTURE,
                                Identifier.fromNamespaceAndPath("gems", "atlas_target/" + area.target()));
                        int fallbackRings = switch (area.target()) {
                            case "lastlight_exchange", "branch_vault", "unwritten_court" -> 192;
                            default -> 128;
                        };
                        located = level.findNearestMapStructure(tag,
                                new BlockPos(0, 80, 0), fallbackRings, false);
                    }
                    if (located == null) {
                        throw new IllegalStateException("No natural " + area.target()
                                + " start in the Atlas search reach on " + seed);
                    }
                    LocatedStart resolved = findNearbyStart(level, tag, located);
                    StructureStart start = resolved.start();
                    if (area.target().equals("khaled_floodgate")) {
                        KhaledRiverwardNaturalProbe.inspectPieces(start);
                    }
                    if (area.target().equals("nour_signal_harbor")) {
                        NourHarborV5NaturalProbe.inspectPieces(start);
                    }
                    int viewLimit = area.target().equals("second_chance_town") ? 8
                            : area.target().equals("othman_library") ? 5 : 3;
                    List<BoundingBox> buildings = selectBuildings(start, viewLimit);
                    if (area.target().equals("inkbound_workshop")) {
                        // The new press terrace makes that child wider than
                        // the root; keep route index 0 bound to the actual
                        // saved root, independent of piece footprint area.
                        BoundingBox rootBox = start.getPieces().getFirst().getBoundingBox();
                        buildings = new ArrayList<>(buildings);
                        buildings.removeIf(box -> sameBounds(box, rootBox));
                        buildings.addFirst(rootBox);
                    }
                    if (buildings.size() < 3) {
                        throw new IllegalStateException(area.target() + " has only "
                                + buildings.size() + " distinct building pieces");
                    }
                    for (BoundingBox building : buildings) loadBuilding(level, building);
                    if (area.target().equals("nour_signal_harbor")
                            && (coastalProbe || boatProbe)) {
                        BoundingBox root = buildings.getFirst();
                        long distance = Math.round(Math.hypot(root.getCenter().getX(),
                                root.getCenter().getZ()));
                        System.out.println("NOUR_COASTAL_LOCATE seed=" + label
                                + " center=" + root.getCenter() + " distance="
                                + distance + " candidates="
                                + NourCoastalStructure.candidateCounts());
                        if (distance > 8_000) {
                            throw new IllegalStateException("Natural Nour harbor lies "
                                    + distance + " blocks from origin, beyond the "
                                    + "8,000-block diagnostic reach target");
                        }
                        NourHarborV5NaturalProbe.inspectSavedFoundation(level, start);
                    }
                    System.out.println("NATURAL_DISTRICT_SITE seed=" + seed + " target="
                            + area.target() + " structure=" + resolved.structureId()
                            + " located=" + located + " startChunk="
                            + start.getChunkPos() + " buildings=" + buildings);
                    Rotation rootRotation = start.getPieces().stream()
                            .filter(piece -> piece instanceof PoolElementStructurePiece)
                            .map(piece -> ((PoolElementStructurePiece) piece).getRotation())
                            .findFirst().orElseThrow();
                    BlockPos rootPosition = area.target().equals("nour_signal_harbor")
                            ? start.getPieces().stream()
                                    .filter(piece -> piece instanceof PoolElementStructurePiece pooled
                                            && pooled.getElement() instanceof SinglePoolElement single
                                            && single.getTemplateLocation().toString().equals(
                                                "gems:nour_signal_harbor_station_v5"))
                                    .map(piece -> ((PoolElementStructurePiece) piece).getPosition())
                                    .findFirst().orElse(start.getPieces().getFirst().getBoundingBox().getCenter())
                            : start.getPieces().getFirst().getBoundingBox().getCenter();
                    return new Site(buildings, start.getPieces().stream()
                            .filter(NaturalDistrictVisualClientGameTest::occupiesCameraSpace)
                            .map(piece -> piece.getBoundingBox()).toList(), rootRotation,
                            rootPosition);
                    });
                } catch (RuntimeException error) {
                    recordFailure(failures, label, area.target(), "site", error);
                    continue;
                }
                if (coastalProbe) {
                    continue;
                }
                if (boatProbe && area.target().equals("nour_signal_harbor")) {
                    attempt(failures, label, area.target(), "player-boat-arrival", () ->
                            NourHarborV5NaturalProbe.runPlayerBoatArrival(context, world,
                                    site.rootPosition(), site.rootRotation(), site.buildings()));
                    continue;
                }
                if (area.target().equals("nour_signal_harbor")) {
                    // Offshore arrival has a boat-to-dock route, not a dry
                    // exterior bank gate. Its dedicated probe stages the
                    // interaction cameras; normal boat input is a later gate.
                    attempt(failures, label, area.target(), "harbor-v5-interactions", () ->
                            NourHarborV5NaturalProbe.run(context, world, site.buildings()));
                    continue;
                }

                String name = label + "-" + area.target();
                boolean soryalBoat = area.target().equals("soryal_repair_yard")
                        && SoryalAmphibiousNaturalProbe.hasBoatLane(world,
                                site.buildings().getFirst(), site.rootRotation());
                if (soryalBoat) {
                    attempt(failures, label, area.target(), "boat-to-door", () ->
                            SoryalAmphibiousNaturalProbe.run(context, world,
                                    site.buildings().getFirst(), site.rootRotation(), label));
                }
                List<NaturalDistrictRouteProbe.WalkApproach> verifiedApproaches =
                        new ArrayList<>();
                List<NaturalDistrictRouteProbe.Gate> gates =
                        soryalBoat ? List.of()
                                : NaturalDistrictRouteProbe.gatesFor(
                                        area.target(), site.rootRotation(),
                                        area.target().equals("hushwire")
                                                && site.allPieces().stream().anyMatch(box ->
                                                        box.getXSpan() * box.getZSpan()
                                                                == 29 * 27));
                for (NaturalDistrictRouteProbe.Gate gate : gates) {
                    attempt(failures, label, area.target(), "walk-" + gate.name(), () ->
                            verifiedApproaches.add(NaturalDistrictRouteProbe.walk(context, world,
                                    area.target(), site.buildings(), site.allPieces(), gate)));
                }
                if (!verifiedApproaches.isEmpty()) {
                    attempt(failures, label, area.target(), "arrival", () ->
                            captureWalkArrival(context, world, verifiedApproaches.getFirst(),
                                    site.allPieces(), "natural-" + name + "-arrival"));
                } else if (gates.isEmpty() && !soryalBoat) {
                    attempt(failures, label, area.target(), "arrival", () ->
                            capture(context, world, site.buildings().getFirst(), site.allPieces(),
                                    28, 0, false, "natural-" + name + "-arrival"));
                }
                if (!gates.isEmpty() || soryalBoat) {
                    attempt(failures, label, area.target(), "architecture-wide", () ->
                            capture(context, world, site.buildings().getFirst(), site.allPieces(),
                                    28, 0, area.target().equals("inkbound_workshop"),
                                    "natural-" + name + "-architecture-wide",
                                    area.target().equals("hushwire")
                                            ? site.rootRotation().rotate(Direction.SOUTH)
                                                    .getSerializedName() : null));
                }
                if (area.target().equals("second_chance_town")
                        || area.target().equals("othman_library")
                        || area.target().equals("framefall_gallery")) {
                    attempt(failures, label, area.target(), "arrival-alternate", () ->
                            capture(context, world, site.buildings().getFirst(), site.allPieces(),
                                    28, 1, false, "natural-" + name + "-arrival-alternate"));
                }
                for (int i = 0; i < site.buildings().size(); i++) {
                    int index = i;
                    attempt(failures, label, area.target(), "building-" + (i + 1), () ->
                            capture(context, world, site.buildings().get(index), site.allPieces(),
                                    12, 0, index > 0 || area.target().equals("inkbound_workshop"),
                                    "natural-" + name + "-building-" + (index + 1),
                                    area.target().equals("hushwire") && index == 0
                                            ? site.rootRotation().rotate(Direction.SOUTH)
                                                    .getSerializedName() : null));
                }
                if (area.creature() != null) {
                    attempt(failures, label, area.target(), "creature", () ->
                            creaturePortrait(context, world, area,
                                    site.buildings(), site.allPieces(),
                                    "natural-" + name + "-creature"));
                }
                if (area.target().equals("khaled_floodgate")
                        && !verifiedApproaches.isEmpty()) {
                    attempt(failures, label, area.target(), "whole-area-mechanic", () ->
                            KhaledRiverwardNaturalProbe.run(context, world,
                                    site.buildings(), verifiedApproaches.getFirst()));
                }
            }
        }
    }

    private static void attempt(List<String> failures, String seed, String target,
            String step, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException error) {
            recordFailure(failures, seed, target, step, error);
        }
    }

    private static void recordFailure(List<String> failures, String seed, String target,
            String step, RuntimeException error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        String message = cause.getMessage() == null ? "" : cause.getMessage()
                .replace('\n', ' ').replace('\r', ' ');
        String failure = "seed=" + seed + " target=" + target + " step=" + step
                + " type=" + cause.getClass().getSimpleName() + " message=" + message;
        failures.add(failure);
        System.err.println("NATURAL_DISTRICT_FAILURE " + failure);
        error.printStackTrace();
    }

    private record Site(List<BoundingBox> buildings, List<BoundingBox> allPieces,
                        Rotation rootRotation, BlockPos rootPosition) {}

    private record LocatedStart(StructureStart start, String structureId) {}

    private static int themedSearchRings(String target) {
        return switch (target) {
            case "rehearsal_works", "redline_house", "othman_library" -> 16;
            default -> 8;
        };
    }

    /** Locate positions can sit beside the saved start chunk; inspect nearby chunks in distance order. */
    private static LocatedStart findNearbyStart(ServerLevel level, TagKey<Structure> tag,
            BlockPos located) {
        var registry = level.getServer().registryAccess().lookupOrThrow(Registries.STRUCTURE);
        int centerX = located.getX() >> 4;
        int centerZ = located.getZ() >> 4;
        for (int radius = 0; radius <= 5; radius++) {
            LocatedStart nearest = null;
            long nearestDistance = Long.MAX_VALUE;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    var chunk = level.getChunk(centerX + dx, centerZ + dz);
                    for (var holder : registry.getTagOrEmpty(tag)) {
                        StructureStart candidate = level.structureManager()
                                .getStartForStructure(holder.value(), chunk);
                        if (candidate == null || !candidate.isValid()) continue;
                        var startChunk = candidate.getChunkPos();
                        long x = (long) startChunk.x() * 16 + 8 - located.getX();
                        long z = (long) startChunk.z() * 16 + 8 - located.getZ();
                        long distance = x * x + z * z;
                        if (distance < nearestDistance) {
                            String id = holder.unwrapKey()
                                    .map(key -> key.identifier().toString())
                                    .orElseThrow();
                            nearest = new LocatedStart(candidate, id);
                            nearestDistance = distance;
                        }
                    }
                }
            }
            if (nearest != null) return nearest;
        }
        throw new IllegalStateException("Located structure tag " + tag.location()
                + " has no saved start within five chunks of " + located);
    }

    private static List<BoundingBox> selectBuildings(StructureStart start, int limit) {
        List<BoundingBox> candidates = start.getPieces().stream()
                .map(piece -> piece.getBoundingBox())
                .filter(box -> box.getXSpan() >= 10 && box.getZSpan() >= 10)
                .sorted(Comparator.comparingInt(
                        (BoundingBox box) -> box.getXSpan() * box.getZSpan()).reversed())
                .toList();
        List<BoundingBox> selected = new ArrayList<>();
        for (BoundingBox box : candidates) {
            int x = (box.minX() + box.maxX()) / 2;
            int z = (box.minZ() + box.maxZ()) / 2;
            boolean separate = selected.stream().allMatch(other -> {
                int dx = x - (other.minX() + other.maxX()) / 2;
                int dz = z - (other.minZ() + other.maxZ()) / 2;
                return dx * dx + dz * dz >= 16 * 16;
            });
            if (separate) selected.add(box);
            if (selected.size() == limit) break;
        }
        return List.copyOf(selected);
    }

    private static boolean occupiesCameraSpace(StructurePiece piece) {
        // This sparse reference carrier exists only to make the Fam start
        // discoverable from the east generation chunk. Its NBT has one
        // jigsaw connector and one structure_void; the intervening air is
        // ordinary terrain, not an occupied building or wall.
        return !(piece instanceof PoolElementStructurePiece pooled
                && pooled.getElement() instanceof SinglePoolElement single
                && FAM_EAST_REFERENCE.equals(single.getTemplateLocation()));
    }

    private static void loadBuilding(ServerLevel level, BoundingBox box) {
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
            for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                level.getChunk(x, z);
            }
        }
    }

    private static final class NoStandingSurfaceException extends RuntimeException {
        private NoStandingSurfaceException(int x, int z) {
            super("No clear ground near " + x + "," + z);
        }
    }

    private static BlockPos safeSurface(ServerLevel level, int x, int z) {
        return safeSurface(level, x, z, 6);
    }

    private static BlockPos safeSurface(ServerLevel level, int x, int z, int maxRadius) {
        for (int radius = 0; radius <= maxRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int sampleX = x + dx;
                    int sampleZ = z + dz;
                    level.getChunk(sampleX >> 4, sampleZ >> 4);
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            sampleX, sampleZ);
                    BlockPos feet = new BlockPos(sampleX, y, sampleZ);
                    if (level.getBlockState(feet.below()).isSolid()
                            && level.getBlockState(feet).getFluidState().isEmpty()
                            && level.getBlockState(feet.above()).getFluidState().isEmpty()
                            && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                            && level.getBlockState(feet.above())
                                    .getCollisionShape(level, feet.above()).isEmpty()) {
                        return feet;
                    }
                }
            }
        }
        throw new NoStandingSurfaceException(x, z);
    }

    private static final List<String> APPROACH_SIDES = List.of("south", "north", "east", "west");

    private record Approach(String source, String side, BlockPos feet, int distance, int offset,
                            int facadeRise, int clearRays, int score) {}

    private static BlockPos approachPoint(BoundingBox building, String side, int distance,
            int offset) {
        int centerX = (building.minX() + building.maxX()) / 2;
        int centerZ = (building.minZ() + building.maxZ()) / 2;
        return switch (side) {
            case "south" -> new BlockPos(centerX + offset, 0, building.maxZ() + distance);
            case "north" -> new BlockPos(centerX + offset, 0, building.minZ() - distance);
            case "east" -> new BlockPos(building.maxX() + distance, 0, centerZ + offset);
            case "west" -> new BlockPos(building.minX() - distance, 0, centerZ + offset);
            default -> throw new IllegalArgumentException("Unknown side: " + side);
        };
    }

    private static List<Approach> standingApproaches(ServerLevel level, ServerPlayer player,
            BoundingBox building, List<BoundingBox> allPieces, int baseDistance,
            int facadeRise) {
        return standingApproaches(level, player, building, allPieces,
                baseDistance, facadeRise, 2);
    }

    private static List<Approach> standingApproaches(ServerLevel level, ServerPlayer player,
            BoundingBox building, List<BoundingBox> allPieces, int baseDistance,
            int facadeRise, int pieceMargin) {
        List<Approach> approaches = new ArrayList<>();
        int[] offsets = pieceMargin == 0
                ? new int[]{0, -1, 1, -2, 2, -4, 4, -10, 10, -20, 20}
                : new int[]{0, -10, 10, -20, 20};
        for (String side : APPROACH_SIDES) {
            for (int distance : new int[]{Math.max(12, baseDistance - 4), baseDistance, baseDistance + 8, baseDistance + 12,
                    baseDistance + 24, baseDistance + 36}) {
                for (int offset : offsets) {
                    BlockPos sample = approachPoint(building, side, distance, offset);
                    try {
                        BlockPos feet = safeSurface(level, sample.getX(), sample.getZ());
                        if (!allPieces.stream().allMatch(piece ->
                                    outsidePiece(feet, piece, pieceMargin))
                                || feet.getY() > building.maxY() - 5
                                || nearTree(level, feet)) continue;
                        int clearRays = clearFacadeRays(level, player, building, side, feet,
                                facadeRise);
                        if (clearRays < 2) continue;
                        int score = clearRays * 100
                                - Math.abs(feet.getY() - building.minY() - 3) * 4
                                - Math.abs(distance - baseDistance)
                                - Math.abs(offset) / 2;
                        approaches.add(new Approach(facadeRise > 8
                                ? "exterior-upper-facade" : "exterior", side, feet,
                                distance, offset, facadeRise, clearRays, score));
                    } catch (NoStandingSurfaceException noGround) {
                        // Water, cliffs and blocked ground are ordinary candidate misses.
                    }
                }
            }
        }
        approaches.sort(Comparator.comparingInt(Approach::score).reversed());
        return approaches;
    }

    /** A side building may be viewed from a low, connected piece when terrain hides its exterior. */
    private static List<Approach> standingWalkwayApproaches(ServerLevel level, ServerPlayer player,
            BoundingBox target, List<BoundingBox> allPieces, boolean hamedTerrace) {
        List<Approach> approaches = new ArrayList<>();
        for (BoundingBox other : allPieces) {
            if (sameBounds(other, target)) continue;
            int centerX = (other.minX() + other.maxX()) / 2;
            int centerZ = (other.minZ() + other.maxZ()) / 2;
            int[] xs = {other.minX() + 3, other.minX() + 8, centerX,
                    other.maxX() - 8, other.maxX() - 3};
            int[] zs = {other.minZ() + 3, other.minZ() + 8, centerZ,
                    other.maxZ() - 8, other.maxZ() - 3};
            for (int x : xs) {
                for (int z : zs) {
                    if (x < other.minX() + 2 || x > other.maxX() - 2
                            || z < other.minZ() + 2 || z > other.maxZ() - 2) continue;
                    BlockPos feet = lowWalkwaySurface(level, x, z, other, target,
                            hamedTerrace);
                    if (feet == null || !outsidePiece(feet, target, 2)
                            || nearTree(level, feet)) continue;
                    String side = sideFacing(target, feet);
                    if (side == null) continue;
                    int facadeRise = hamedTerrace
                            ? Math.max(8, other.minY() + 8 - target.minY()) : 8;
                    int clearRays = clearFacadeRays(level, player, target, side, feet,
                            true, facadeRise);
                    if (clearRays < 2) continue;
                    int distance = (int) Math.round(Math.hypot(
                            feet.getX() - (target.minX() + target.maxX()) / 2.0,
                            feet.getZ() - (target.minZ() + target.maxZ()) / 2.0));
                    int preferredDistance = hamedTerrace
                            && target.minY() + 4 < other.minY() ? 32 : 16;
                    int score = clearRays * 100
                            - Math.abs(feet.getY() - target.minY() - 3) * 4
                            - Math.abs(distance - preferredDistance);
                    approaches.add(new Approach("connected-walkway", side, feet,
                            distance, 0, facadeRise, clearRays, score));
                }
            }
        }
        approaches.sort(Comparator.comparingInt(Approach::score).reversed());
        return approaches;
    }

    /** The raised Hushwire procession has a proven public deck inside its own piece. */
    private static List<Approach> standingProcessionApproaches(ServerLevel level,
            ServerPlayer player, BoundingBox target, List<BoundingBox> allPieces) {
        Direction inward = null;
        for (BoundingBox other : allPieces) {
            if (sameBounds(other, target)
                    || other.getXSpan() < 40 || other.getZSpan() < 40) continue;
            if (other.minX() == target.maxX() + 1) inward = Direction.EAST;
            else if (other.maxX() + 1 == target.minX()) inward = Direction.WEST;
            else if (other.minZ() == target.maxZ() + 1) inward = Direction.SOUTH;
            else if (other.maxZ() + 1 == target.minZ()) inward = Direction.NORTH;
            if (inward != null) break;
        }
        if (inward == null) return List.of();
        int centerX = (target.minX() + target.maxX()) / 2;
        int centerZ = (target.minZ() + target.maxZ()) / 2;
        int outerX = inward == Direction.EAST ? target.minX()
                : inward == Direction.WEST ? target.maxX() : centerX;
        int outerZ = inward == Direction.SOUTH ? target.minZ()
                : inward == Direction.NORTH ? target.maxZ() : centerZ;
        int crossX = -inward.getStepZ();
        int crossZ = inward.getStepX();
        List<Approach> approaches = new ArrayList<>();
        for (int depth : new int[]{2, 3, 4, 5, 6, 7}) {
            for (int offset : new int[]{0, -2, 2, -4, 4}) {
                int x = outerX + inward.getStepX() * depth + crossX * offset;
                int z = outerZ + inward.getStepZ() * depth + crossZ * offset;
                if (x < target.minX() || x > target.maxX()
                        || z < target.minZ() || z > target.maxZ()) continue;
                for (int y = target.minY() + 9; y <= target.minY() + 14; y++) {
                    BlockPos feet = new BlockPos(x, y, z);
                    var floor = level.getBlockState(feet.below());
                    if ((!floor.is(Blocks.DEEPSLATE_TILES)
                            && !Blocks.CUT_COPPER.asList().contains(floor.getBlock()))
                            || !level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                            || !level.getBlockState(feet.above())
                                    .getCollisionShape(level, feet.above()).isEmpty()
                            || nearTree(level, feet)) continue;
                    Vec3 eye = new Vec3(x + 0.5, y + 1.62, z + 0.5);
                    int clear = 0;
                    for (int lateral : new int[]{-2, 0, 2}) {
                        Vec3 focus = new Vec3(x + 0.5 + inward.getStepX() * 12
                                + crossX * lateral, target.minY() + 18,
                                z + 0.5 + inward.getStepZ() * 12 + crossZ * lateral);
                        var hit = level.clip(new ClipContext(eye, focus,
                                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                        if (hit.getType() == HitResult.Type.MISS
                                || hit.getLocation().distanceToSqr(eye) >= 7 * 7) clear++;
                    }
                    if (clear < 2) continue;
                    approaches.add(new Approach("procession-deck", inward.getOpposite()
                            .getSerializedName(), feet, depth, offset, 18, clear,
                            clear * 100 - Math.abs(offset) * 5 - Math.abs(depth - 3)));
                }
            }
        }
        approaches.sort(Comparator.comparingInt(Approach::score).reversed());
        return approaches;
    }

    private static boolean clearCentralFacade(ServerLevel level, ServerPlayer player,
            BoundingBox building, Approach camera) {
        Vec3 eye = new Vec3(camera.feet().getX() + 0.5,
                camera.feet().getY() + 1.62, camera.feet().getZ() + 0.5);
        double x = (building.minX() + building.maxX() + 1) / 2.0;
        double z = (building.minZ() + building.maxZ() + 1) / 2.0;
        double y = building.minY() + camera.facadeRise();
        Vec3 facade = switch (camera.side()) {
            case "south" -> new Vec3(x, y, building.maxZ() + 1);
            case "north" -> new Vec3(x, y, building.minZ() - 1);
            case "east" -> new Vec3(building.maxX() + 1, y, z);
            case "west" -> new Vec3(building.minX() - 1, y, z);
            default -> throw new IllegalArgumentException(camera.side());
        };
        var hit = level.clip(new ClipContext(eye, facade,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS
                || hit.getLocation().distanceToSqr(facade) <= 4.0;
    }

    /** View the crypt's memorial room from its hall-connected public threshold. */
    private static List<Approach> standingCryptApproaches(ServerLevel level,
            ServerPlayer player, BoundingBox target, List<BoundingBox> allPieces) {
        Direction inward = null;
        for (BoundingBox other : allPieces) {
            if (sameBounds(other, target)
                    || other.getXSpan() < 45 || other.getZSpan() < 45) continue;
            if (other.minX() == target.maxX() + 1) inward = Direction.WEST;
            else if (other.maxX() + 1 == target.minX()) inward = Direction.EAST;
            else if (other.minZ() == target.maxZ() + 1) inward = Direction.NORTH;
            else if (other.maxZ() + 1 == target.minZ()) inward = Direction.SOUTH;
            if (inward != null) break;
        }
        if (inward == null) return List.of();
        int edgeX = inward == Direction.EAST ? target.minX()
                : inward == Direction.WEST ? target.maxX()
                : (target.minX() + target.maxX()) / 2;
        int edgeZ = inward == Direction.SOUTH ? target.minZ()
                : inward == Direction.NORTH ? target.maxZ()
                : (target.minZ() + target.maxZ()) / 2;
        int crossX = -inward.getStepZ();
        int crossZ = inward.getStepX();
        List<Approach> approaches = new ArrayList<>();
        for (int depth : new int[]{4, 6, 8}) {
            for (int offset : new int[]{0, -3, 3, -6, 6}) {
                int x = edgeX + inward.getStepX() * depth + crossX * offset;
                int z = edgeZ + inward.getStepZ() * depth + crossZ * offset;
                BlockPos feet = new BlockPos(x, target.minY() + 5, z);
                if (!level.getBlockState(feet.below()).is(DomainBlocks.HUSHWIRE_SLATE)
                        || !level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                        || !level.getBlockState(feet.above())
                                .getCollisionShape(level, feet.above()).isEmpty()
                        || nearTree(level, feet)) continue;
                Vec3 eye = new Vec3(x + 0.5, feet.getY() + 1.62, z + 0.5);
                int clear = 0;
                for (int lateral : new int[]{-3, 0, 3}) {
                    Vec3 focus = new Vec3(x + 0.5 + inward.getStepX() * 12
                            + crossX * lateral, target.minY() + 13,
                            z + 0.5 + inward.getStepZ() * 12 + crossZ * lateral);
                    var hit = level.clip(new ClipContext(eye, focus,
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                    if (hit.getType() == HitResult.Type.MISS
                            || hit.getLocation().distanceToSqr(eye) >= 7 * 7) clear++;
                }
                if (clear < 2) continue;
                approaches.add(new Approach("crypt-threshold", inward.getOpposite()
                        .getSerializedName(), feet, depth, offset, 13, clear,
                        clear * 100 - Math.abs(offset) * 5 - Math.abs(depth - 4)));
            }
        }
        approaches.sort(Comparator.comparingInt(Approach::score).reversed());
        return approaches;
    }

    private static boolean sameBounds(BoundingBox a, BoundingBox b) {
        return a.minX() == b.minX() && a.minY() == b.minY()
                && a.minZ() == b.minZ() && a.maxX() == b.maxX()
                && a.maxY() == b.maxY() && a.maxZ() == b.maxZ();
    }

    private static BlockPos lowWalkwaySurface(ServerLevel level, int x, int z,
            BoundingBox other, BoundingBox target, boolean hamedTerrace) {
        level.getChunk(x >> 4, z >> 4);
        int allowance = hamedTerrace ? 18 : 8;
        int highest = Math.min(other.minY() + allowance,
                target.minY() + allowance);
        int lowest = Math.max(other.minY(), target.minY() - 6);
        for (int y = highest; y >= lowest; y--) {
            BlockPos feet = new BlockPos(x, y, z);
            if (level.getBlockState(feet.below()).isSolid()
                    && level.getBlockState(feet).getFluidState().isEmpty()
                    && level.getBlockState(feet.above()).getFluidState().isEmpty()
                    && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(feet.above())
                            .getCollisionShape(level, feet.above()).isEmpty()) return feet;
        }
        return null;
    }

    private static String sideFacing(BoundingBox target, BlockPos feet) {
        int north = target.minZ() - feet.getZ();
        int south = feet.getZ() - target.maxZ();
        int east = feet.getX() - target.maxX();
        int west = target.minX() - feet.getX();
        int largest = Math.max(Math.max(north, south), Math.max(east, west));
        if (largest <= 2) return null;
        if (largest == north) return "north";
        if (largest == south) return "south";
        if (largest == east) return "east";
        return "west";
    }

    private static boolean outsideAllPieces(BlockPos feet, List<BoundingBox> allPieces) {
        return allPieces.stream().allMatch(box -> outsidePiece(feet, box, 2));
    }

    private static boolean outsidePiece(BlockPos feet, BoundingBox box, int margin) {
        return feet.getX() < box.minX() - margin || feet.getX() > box.maxX() + margin
                || feet.getZ() < box.minZ() - margin || feet.getZ() > box.maxZ() + margin;
    }

    private static boolean nearTree(ServerLevel level, BlockPos feet) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 1; dy <= 3; dy++) {
                    var state = level.getBlockState(feet.offset(dx, dy, dz));
                    if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                            || state.is(Blocks.BAMBOO) || state.is(Blocks.BAMBOO_SAPLING)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static int clearFacadeRays(ServerLevel level, ServerPlayer player,
            BoundingBox building, String side, BlockPos feet, int facadeRise) {
        return clearFacadeRays(level, player, building, side, feet, false, facadeRise);
    }

    private static int clearFacadeRays(ServerLevel level, ServerPlayer player,
            BoundingBox building, String side, BlockPos feet, boolean fromWalkway,
            int facadeRise) {
        Vec3 eye = new Vec3(feet.getX() + 0.5, feet.getY() + 1.62,
                feet.getZ() + 0.5);
        double y = building.minY() + Math.min(facadeRise,
                building.maxY() - building.minY() - 2);
        int clear = 0;
        for (double fraction : new double[]{0.3, 0.5, 0.7}) {
            double x = building.minX() + fraction * building.getXSpan();
            double z = building.minZ() + fraction * building.getZSpan();
            double inset = fromWalkway ? 4.0 : -1.0;
            Vec3 facade = switch (side) {
                case "south" -> new Vec3(x, y, building.maxZ() - inset);
                case "north" -> new Vec3(x, y, building.minZ() + inset);
                case "east" -> new Vec3(building.maxX() - inset, y, z);
                case "west" -> new Vec3(building.minX() + inset, y, z);
                default -> throw new IllegalArgumentException("Unknown side: " + side);
            };
            var hit = level.clip(new ClipContext(eye, facade,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.MISS) {
                clear++;
            } else if (fromWalkway) {
                BlockPos block = hit.getBlockPos();
                if (block.getX() >= building.minX() && block.getX() <= building.maxX()
                        && block.getY() >= building.minY() && block.getY() <= building.maxY()
                        && block.getZ() >= building.minZ() && block.getZ() <= building.maxZ()) {
                    clear++;
                }
            } else if (hit.getLocation().distanceToSqr(facade) <= 4.0) {
                clear++;
            }
        }
        return clear;
    }

    private record CapturedView(boolean captured, boolean upperFacadeOnly, BlockPos feet) {}

    private record CameraGroundSample(long serverTick, boolean alive, boolean onGround,
            Vec3 position, Vec3 velocity, BlockPos actualFeet, String actualSupport,
            String plannedSupport, boolean plannedCell, boolean plannedY) {
        boolean settled() {
            return alive && onGround && plannedCell && plannedY;
        }
    }

    private record WalkArrival(NaturalDistrictRouteProbe.Step camera,
                               NaturalDistrictRouteProbe.Step entry,
                               boolean clearEntry, double doorwayDistance,
                               double buildingDistance,
                               int groundSteps, int examined) {}

    private record ExteriorNode(NaturalDistrictRouteProbe.Step step, int groundSteps) {}

    /** Photograph the doorway from the player's proven route, before the wide facade survey. */
    private static void captureWalkArrival(ClientGameTestContext context,
            TestSingleplayerContext world, NaturalDistrictRouteProbe.WalkApproach approach,
            List<BoundingBox> allPieces, String name) {
        WalkArrival view = world.getServer().computeOnServer(server -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = world.getConnection().getServerPlayer();
            WalkArrival arrival = findWalkArrival(level, player, approach, allPieces);
            NaturalDistrictRouteProbe.Step camera = arrival.camera();
            double dx = arrival.entry().feet().getX() - camera.feet().getX();
            double dz = arrival.entry().feet().getZ() - camera.feet().getZ();
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            double horizontal = Math.hypot(dx, dz);
            float pitch = (float) Math.toDegrees(Math.atan2(
                    camera.standingY() - arrival.entry().standingY(), horizontal));
            player.setNoGravity(false);
            player.teleportTo(level, camera.feet().getX() + 0.5, camera.standingY(),
                    camera.feet().getZ() + 0.5,
                    EnumSet.noneOf(Relative.class), yaw, pitch, false);
            System.out.println("NATURAL_DISTRICT_ROUTE_VIEW name=" + name
                    + " source=connected-natural-ground feet=" + camera.feet()
                    + " entry=" + arrival.entry().feet()
                    + " clearEntry=" + arrival.clearEntry()
                    + " doorwayDistance=" + arrival.doorwayDistance()
                    + " buildingDistance=" + arrival.buildingDistance()
                    + " groundSteps=" + arrival.groundSteps()
                    + " examined=" + arrival.examined()
                    + " building=" + approach.building());
            return arrival;
        });
        context.waitTicks(10);
        world.getConnection().waitForChunksRender();
        world.getServer().runOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            if (!player.isAlive() || !player.onGround()) {
                throw new IllegalStateException(name
                        + " did not remain standing on its verified approach");
            }
        });
        clearOverlays(context);
        context.takeScreenshot(name);
        if (!view.clearEntry()) {
            throw new IllegalStateException(name + " has no connected natural-ground"
                    + " view at least 12 blocks from the doorway with a clear ray;"
                    + " entry=" + view.entry().feet()
                    + " bestDoorwayDistance=" + view.doorwayDistance()
                    + " bestBuildingDistance=" + view.buildingDistance()
                    + " examined=" + view.examined());
        }
    }

    private static WalkArrival findWalkArrival(ServerLevel level, ServerPlayer player,
            NaturalDistrictRouteProbe.WalkApproach approach, List<BoundingBox> allPieces) {
        NaturalDistrictRouteProbe.Step source = approach.steps().getFirst();
        ArrayDeque<ExteriorNode> frontier = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        frontier.add(new ExteriorNode(source, 0));
        visited.add(new BlockPos(source.feet().getX(), 0, source.feet().getZ()));
        int outwardX = -approach.gate().inward().getStepX();
        int outwardZ = -approach.gate().inward().getStepZ();
        int entryOutward = (approach.entry().feet().getX() - source.feet().getX()) * outwardX
                + (approach.entry().feet().getZ() - source.feet().getZ()) * outwardZ;
        WalkArrival diagnostic = new WalkArrival(source, approach.entry(), false,
                distanceFromEntry(source.feet(), approach.entry().feet()),
                distanceFromBuilding(source.feet(), approach.building()), 0, 0);
        int examined = 0;
        while (!frontier.isEmpty() && examined < 900) {
            ExteriorNode node = frontier.removeFirst();
            examined++;
            NaturalDistrictRouteProbe.Step step = node.step();
            double doorwayDistance = distanceFromEntry(step.feet(), approach.entry().feet());
            double buildingDistance = distanceFromBuilding(step.feet(), approach.building());
            if (doorwayDistance >= 12.0
                    && step.feet().getY() <= approach.building().maxY() - 5
                    && naturalStandingSurface(level, step)) {
                boolean clear = clearEntryRay(level, player, step, approach.entry());
                if (clear) {
                    return new WalkArrival(step, approach.entry(), true,
                            doorwayDistance, buildingDistance,
                            node.groundSteps(), examined);
                }
                if (doorwayDistance < diagnostic.doorwayDistance()) {
                    diagnostic = new WalkArrival(step, approach.entry(), false,
                            doorwayDistance, buildingDistance,
                            node.groundSteps(), examined);
                }
            }
            if (node.groundSteps() >= 40) continue;
            for (int[] direction : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = step.feet().getX() + direction[0];
                int z = step.feet().getZ() + direction[1];
                int dx = x - source.feet().getX();
                int dz = z - source.feet().getZ();
                int outward = dx * outwardX + dz * outwardZ;
                int lateral = dx * outwardZ - dz * outwardX;
                if (outward < Math.min(-2, entryOutward)
                        || outward > 36 || Math.abs(lateral) > 12) continue;
                BlockPos horizontal = new BlockPos(x, 0, z);
                if (!visited.add(horizontal)) continue;
                try {
                    BlockPos feet = safeSurface(level, x, z, 0);
                    if (Math.abs(feet.getY() - step.standingY()) > 1.05
                            || allPieces.stream().anyMatch(piece ->
                                    !outsidePiece(feet, piece, 0))) continue;
                    frontier.addLast(new ExteriorNode(
                            new NaturalDistrictRouteProbe.Step(feet, feet.getY()),
                            node.groundSteps() + 1));
                } catch (NoStandingSurfaceException noGround) {
                    // Water and blocked ground do not connect to the verified walk.
                }
            }
        }
        return new WalkArrival(diagnostic.camera(), diagnostic.entry(), false,
                diagnostic.doorwayDistance(), diagnostic.buildingDistance(),
                diagnostic.groundSteps(), examined);
    }

    private static boolean naturalStandingSurface(ServerLevel level,
            NaturalDistrictRouteProbe.Step step) {
        try {
            BlockPos feet = safeSurface(level, step.feet().getX(), step.feet().getZ(), 0);
            return feet.equals(step.feet())
                    && Math.abs(step.standingY() - feet.getY()) < 0.1
                    && !level.getBlockState(feet.below()).is(BlockTags.LEAVES)
                    && !level.getBlockState(feet.below()).is(BlockTags.LOGS);
        } catch (NoStandingSurfaceException noGround) {
            return false;
        }
    }

    private static double distanceFromBuilding(BlockPos feet, BoundingBox building) {
        int dx = Math.max(Math.max(building.minX() - feet.getX(), 0),
                feet.getX() - building.maxX());
        int dz = Math.max(Math.max(building.minZ() - feet.getZ(), 0),
                feet.getZ() - building.maxZ());
        return Math.hypot(dx, dz);
    }

    private static double distanceFromEntry(BlockPos feet, BlockPos entry) {
        return Math.hypot(feet.getX() - entry.getX(), feet.getZ() - entry.getZ());
    }

    private static boolean clearEntryRay(ServerLevel level, ServerPlayer player,
            NaturalDistrictRouteProbe.Step camera, NaturalDistrictRouteProbe.Step entry) {
        Vec3 eye = new Vec3(camera.feet().getX() + 0.5,
                camera.standingY() + 1.62, camera.feet().getZ() + 0.5);
        // Stairs may put the first inside footfall below the visible arch.
        // Keep the ray on the same doorway column and require an actual open
        // cell, rather than treating a low tread as the entire entrance.
        for (int rise = 1; rise <= 5; rise++) {
            BlockPos opening = entry.feet().above(rise);
            if (!level.getBlockState(opening).getCollisionShape(level, opening).isEmpty()) {
                continue;
            }
            Vec3 doorway = new Vec3(opening.getX() + 0.5,
                    entry.standingY() + rise + 0.5, opening.getZ() + 0.5);
            var hit = level.clip(new ClipContext(eye, doorway,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }

    static void capture(ClientGameTestContext context, TestSingleplayerContext world,
            BoundingBox building, List<BoundingBox> allPieces, int distance,
            int approachIndex, boolean allowWalkwayFallback, String name) {
        capture(context, world, building, allPieces, distance, approachIndex,
                allowWalkwayFallback, name, null);
    }

    private static void capture(ClientGameTestContext context, TestSingleplayerContext world,
            BoundingBox building, List<BoundingBox> allPieces, int distance,
            int approachIndex, boolean allowWalkwayFallback, String name,
            String requiredSide) {
        CapturedView view = world.getServer().computeOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            ServerLevel level = server.overworld();
            boolean hamedPress = name.contains("-inkbound_workshop-building-2");
            // Hushwire's proven natural bank is one block outside its v8
            // processional footprint. It is a valid exterior standing cell;
            // retain the two clear facade rays and tree/ground checks.
            int pieceMargin = name.contains("-hushwire-")
                    && "east".equals(requiredSide) ? 0 : 2;
            List<Approach> approaches = hamedPress
                    ? standingWalkwayApproaches(level, player, building, allPieces, true)
                    : standingApproaches(level, player, building, allPieces,
                            distance, 8, pieceMargin);
            if (requiredSide != null) {
                approaches = approaches.stream().filter(candidate ->
                        candidate.side().equals(requiredSide)).toList();
            }
            if (name.contains("-hushwire-hushwire_relay_crypt_")) {
                approaches = approaches.stream().filter(candidate ->
                        clearCentralFacade(level, player, building, candidate)).toList();
                List<Approach> threshold = standingCryptApproaches(
                        level, player, building, allPieces);
                if (!threshold.isEmpty()) approaches = threshold;
            }
            // Dense forests can hide the lower wall while leaving a distinct, authored
            // upper facade visible from untouched ground. Keep the low view first.
            if (approaches.isEmpty() && !allowWalkwayFallback
                    && building.maxY() - building.minY() >= 30) {
                approaches = standingApproaches(level, player,
                        building, allPieces, distance, 18, pieceMargin);
                if (requiredSide != null) {
                    approaches = approaches.stream().filter(candidate ->
                            candidate.side().equals(requiredSide)).toList();
                }
            }
            if (approaches.isEmpty() && allowWalkwayFallback && !hamedPress) {
                approaches = standingWalkwayApproaches(level, player, building, allPieces,
                        name.contains("-inkbound_workshop-"));
            }
            if (approaches.isEmpty() && name.contains("-hushwire-hushwire_procession_")) {
                approaches = standingProcessionApproaches(level, player, building, allPieces);
            }
            if (approaches.isEmpty() && name.contains("-hushwire-hushwire_relay_crypt_")) {
                approaches = standingCryptApproaches(level, player, building, allPieces);
            }
            List<Approach> candidates = approaches;
            if (candidates.isEmpty()) {
                if (approachIndex > 0) {
                    System.out.println("NATURAL_DISTRICT_VIEW_SKIPPED name=" + name
                            + " reason=no_clear_exterior_view building=" + building);
                    return new CapturedView(false, false, null);
                }
                throw new IllegalStateException("No clear exterior"
                        + (allowWalkwayFallback ? " or connected walkway" : " ground")
                        + " view for " + name + " requiredSide=" + requiredSide
                        + " building=" + building);
            }
            Approach approach = candidates.getFirst();
            if (approachIndex > 0) {
                Approach primary = approach;
                Approach alternate = candidates.stream()
                        .filter(candidate -> !candidate.side().equals(primary.side()))
                        .findFirst().orElseGet(() -> candidates.stream()
                                .filter(candidate -> {
                                    long dx = candidate.feet().getX() - primary.feet().getX();
                                    long dz = candidate.feet().getZ() - primary.feet().getZ();
                                    return dx * dx + dz * dz >= 12 * 12;
                                })
                                .findFirst().orElse(null));
                if (alternate == null) {
                    System.out.println("NATURAL_DISTRICT_VIEW_SKIPPED name=" + name
                            + " reason=no_separated_clear_exterior_view primary="
                            + primary.feet() + " candidates=" + candidates.size()
                            + " building=" + building);
                    return new CapturedView(false, false, null);
                }
                approach = alternate;
            }
            BlockPos feet = approach.feet();
            double targetX = (building.minX() + building.maxX() + 1) / 2.0;
            double targetZ = (building.minZ() + building.maxZ() + 1) / 2.0;
            float yaw = (float) Math.toDegrees(Math.atan2(
                    feet.getX() + 0.5 - targetX, targetZ - feet.getZ() - 0.5));
            double targetY = building.minY() + Math.min(approach.facadeRise(),
                    building.maxY() - building.minY() - 2);
            double horizontal = Math.hypot(feet.getX() + 0.5 - targetX,
                    feet.getZ() + 0.5 - targetZ);
            float pitch = (float) Math.toDegrees(Math.atan2(feet.getY() + 1.62 - targetY,
                    horizontal));
            player.setNoGravity(false);
            player.teleportTo(level, feet.getX() + 0.5, feet.getY(),
                    feet.getZ() + 0.5, EnumSet.noneOf(Relative.class), yaw, pitch, false);
            System.out.println("NATURAL_DISTRICT_VIEW name=" + name + " source="
                    + approach.source() + " side="
                    + approach.side() + " feet=" + feet + " distance=" + approach.distance()
                    + " offset=" + approach.offset() + " clearRays=" + approach.clearRays()
                    + " score=" + approach.score() + " candidates=" + candidates.size()
                    + " building=" + building);
            return new CapturedView(true,
                    approach.source().equals("exterior-upper-facade"), feet);
        });
        if (!view.captured()) return;
        context.waitTicks(30);
        world.getConnection().waitForChunksRender();
        BlockPos plannedFeet = view.feet();
        CameraGroundSample sample = cameraGroundSample(world, plannedFeet);
        long firstServerTick = sample.serverTick();
        int extraClientTicks = 0;
        while (!sample.settled() && sample.alive() && extraClientTicks < 60) {
            context.waitTicks(5);
            extraClientTicks += 5;
            sample = cameraGroundSample(world, plannedFeet);
        }
        if (!sample.settled()) {
            throw new IllegalStateException(name + " did not remain standing at planned camera "
                    + plannedFeet + " after " + extraClientTicks + " extra client ticks and "
                    + (sample.serverTick() - firstServerTick) + " server ticks; " + sample);
        }
        clearOverlays(context);
        context.takeScreenshot(name);
        if (view.upperFacadeOnly()) {
            throw new IllegalStateException(name + " has an upper-facade diagnostic image, "
                    + "but no clear natural-ground view of its lower facade or entrance");
        }
    }

    private static CameraGroundSample cameraGroundSample(TestSingleplayerContext world,
            BlockPos plannedFeet) {
        return world.getServer().computeOnServer(server -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = world.getConnection().getServerPlayer();
            BlockPos actualFeet = player.blockPosition();
            return new CameraGroundSample(level.getGameTime(), player.isAlive(),
                    player.onGround(), player.position(), player.getDeltaMovement(),
                    actualFeet, level.getBlockState(actualFeet.below()).toString(),
                    level.getBlockState(plannedFeet.below()).toString(),
                    actualFeet.getX() == plannedFeet.getX()
                            && actualFeet.getZ() == plannedFeet.getZ(),
                    Math.abs(player.getY() - plannedFeet.getY()) <= 0.1);
        });
    }

    private record CreaturePortrait(Approach camera, BlockPos creatureFeet,
                                    double separation, int score) {}

    private static CreaturePortrait findCreaturePortrait(ServerLevel level, ServerPlayer player,
            List<BoundingBox> buildings, List<BoundingBox> allPieces) {
        for (BoundingBox building : buildings) {
            List<Approach> cameras = standingApproaches(level, player, building, allPieces,
                    12, 8);
            // Rank a useful facade-facing set first, then widen only if the terrain is difficult.
            for (int start = 0; start < cameras.size(); start += 16) {
                CreaturePortrait best = null;
                for (Approach camera : cameras.subList(start,
                        Math.min(start + 16, cameras.size()))) {
                    BlockPos cameraFeet = camera.feet();
                    Vec3 eye = new Vec3(cameraFeet.getX() + 0.5,
                            cameraFeet.getY() + 1.62, cameraFeet.getZ() + 0.5);
                    double facadeX = (building.minX() + building.maxX() + 1) / 2.0
                            - eye.x;
                    double facadeZ = (building.minZ() + building.maxZ() + 1) / 2.0
                            - eye.z;
                    double facadeDistance = Math.hypot(facadeX, facadeZ);
                    for (BlockPos creatureFeet : reachableGround(level, cameraFeet, allPieces)) {
                        double creatureX = creatureFeet.getX() + 0.5 - eye.x;
                        double creatureZ = creatureFeet.getZ() + 0.5 - eye.z;
                        double separation = Math.hypot(creatureX, creatureZ);
                        int heightDifference = Math.abs(creatureFeet.getY() - cameraFeet.getY());
                        double alignment = (facadeX * creatureX + facadeZ * creatureZ)
                                / (facadeDistance * separation);
                        if (separation < 2.5 || separation > 6.5 || heightDifference > 1
                                || alignment < 0.7
                                || !level.getBlockState(creatureFeet).isAir()
                                || !level.getBlockState(creatureFeet.above()).isAir()
                                || nearTree(level, creatureFeet)
                                || !clearCreatureBody(level, player, eye, creatureFeet,
                                        separation)) continue;
                        int score = camera.score() + (int) (alignment * 80
                                - Math.abs(separation - 3.5) * 20)
                                - heightDifference * 80;
                        CreaturePortrait candidate = new CreaturePortrait(camera, creatureFeet,
                                separation, score);
                        if (best == null || candidate.score() > best.score()) best = candidate;
                    }
                }
                if (best != null) return best;
            }
        }
        throw new IllegalStateException("No low-body-visible, reachable exterior creature position at "
                + buildings);
    }

    /** Collider rays catch terrain lips; air samples also catch non-solid grass and flowers. */
    private static boolean clearCreatureBody(ServerLevel level, ServerPlayer player,
            Vec3 eye, BlockPos feet, double separation) {
        double x = feet.getX() + 0.5;
        double z = feet.getZ() + 0.5;
        Vec3 low = new Vec3(x, feet.getY() + 0.22, z);
        Vec3 high = new Vec3(x, feet.getY() + 0.5, z);
        double sideX = (z - eye.z) / separation * 0.2;
        double sideZ = (eye.x - x) / separation * 0.2;
        for (Vec3 target : new Vec3[]{low, high,
                new Vec3(x + sideX, feet.getY() + 0.35, z + sideZ),
                new Vec3(x - sideX, feet.getY() + 0.35, z - sideZ)}) {
            if (level.clip(new ClipContext(eye, target,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                    player)).getType() != HitResult.Type.MISS) return false;
        }
        for (int sample = 3; sample <= 9; sample++) {
            Vec3 foreground = eye.lerp(low, sample / 10.0);
            if (!level.getBlockState(BlockPos.containing(foreground)).isAir()) return false;
        }
        return true;
    }

    /** Traverse actual clear, dry standing cells; a portrait must be walkable from its camera. */
    private static List<BlockPos> reachableGround(ServerLevel level, BlockPos camera,
            List<BoundingBox> allPieces) {
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        List<BlockPos> reachable = new ArrayList<>();
        frontier.add(camera);
        visited.add(groundKey(camera.getX(), camera.getZ()));
        while (!frontier.isEmpty()) {
            BlockPos current = frontier.removeFirst();
            for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = current.getX() + step[0];
                int z = current.getZ() + step[1];
                if (Math.abs(x - camera.getX()) > 7 || Math.abs(z - camera.getZ()) > 7) {
                    continue;
                }
                long key = groundKey(x, z);
                if (visited.contains(key)) continue;
                BlockPos next;
                try {
                    next = safeSurface(level, x, z, 0);
                } catch (NoStandingSurfaceException noGround) {
                    continue;
                }
                if (Math.abs(next.getY() - current.getY()) > 1
                        || !outsideAllPieces(next, allPieces)) continue;
                visited.add(key);
                frontier.addLast(next);
                reachable.add(next);
            }
        }
        return reachable;
    }

    private static long groundKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static void creaturePortrait(ClientGameTestContext context,
            TestSingleplayerContext world, Area area, List<BoundingBox> buildings,
            List<BoundingBox> allPieces, String name) {
        world.getServer().runOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            ServerLevel level = server.overworld();
            CreaturePortrait portrait = findCreaturePortrait(level, player, buildings, allPieces);
            BlockPos cameraFeet = portrait.camera().feet();
            BlockPos creatureFeet = portrait.creatureFeet();
            PathfinderMob creature = area.creature().create(level, EntitySpawnReason.EVENT);
            if (creature == null) throw new IllegalStateException("Could not create " + area.creature());
            creature.setPos(creatureFeet.getX() + 0.5, creatureFeet.getY(),
                    creatureFeet.getZ() + 0.5);
            creature.setPersistenceRequired();
            creature.setNoAi(true);
            if (!level.addFreshEntity(creature)) {
                throw new IllegalStateException("Could not stage native creature for " + area.target());
            }
            player.setNoGravity(false);
            float yaw = (float) Math.toDegrees(Math.atan2(
                    cameraFeet.getX() - creatureFeet.getX(),
                    creatureFeet.getZ() - cameraFeet.getZ()));
            float pitch = (float) Math.toDegrees(Math.atan2(cameraFeet.getY() + 1.62
                    - creatureFeet.getY() - Math.min(1.0, creature.getBbHeight() * 0.5),
                    portrait.separation()));
            player.teleportTo(level, cameraFeet.getX() + 0.5, cameraFeet.getY(),
                    cameraFeet.getZ() + 0.5, EnumSet.noneOf(Relative.class),
                    yaw, pitch, false);
            if (creature.interact(player, InteractionHand.MAIN_HAND,
                    new Vec3(0.0, creature.getBbHeight() * 0.5, 0.0))
                    != InteractionResult.SUCCESS_SERVER) {
                throw new IllegalStateException("Native creature interaction failed for "
                        + area.target());
            }
            System.out.println("NATURAL_DISTRICT_CREATURE target=" + area.target()
                    + " type=" + creature.getType() + " pos=" + creature.blockPosition()
                    + " camera=" + cameraFeet + " side=" + portrait.camera().side()
                    + " separation=" + portrait.separation()
                    + " portraitScore=" + portrait.score() + " lowBodyRays=4"
                    + " clearFacadeRays=" + portrait.camera().clearRays());
        });
        context.waitTicks(30);
        world.getConnection().waitForChunksRender();
        world.getServer().runOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            if (!player.isAlive() || !player.onGround()) {
                throw new IllegalStateException(name + " camera did not remain standing");
            }
        });
        clearOverlays(context);
        context.takeScreenshot(name);
    }

    private static void clearOverlays(ClientGameTestContext context) {
        context.runOnClient(client -> {
            client.gui.hud.getChat().clearMessages(false);
            client.gui.toastManager().clear();
        });
    }
}
