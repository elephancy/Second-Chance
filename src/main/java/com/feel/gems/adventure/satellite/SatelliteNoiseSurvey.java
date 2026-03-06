package com.feel.gems.adventure.satellite;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Base-noise rejection only; later carvers, structures and features remain unproven. */
public final class SatelliteNoiseSurvey {
    public enum Site { OCEAN, OCEAN_V4, ARCHIVE }

    private static final int WORLD_LIMIT = 29_999_900;
    private static final boolean DIAGNOSTICS = Boolean.getBoolean("gems.satellitePlacementDiagnostics");
    private static final ConcurrentHashMap<String, LongAdder> COUNTS = new ConcurrentHashMap<>();

    static {
        if (DIAGNOSTICS) Runtime.getRuntime().addShutdownHook(new Thread(() ->
                COUNTS.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                        .forEach(entry -> System.out.println("SATELLITE_PLACEMENT "
                                + entry.getKey() + "=" + entry.getValue().sum()))));
    }

    private SatelliteNoiseSurvey() { }

    public static boolean accepts(Structure.GenerationContext context, BoundingBox box,
                                  Site site, Rotation rotation) {
        boolean oceanSite = site != Site.ARCHIVE;
        int height = site == Site.OCEAN_V4 ? 50 : oceanSite ? 43 : 58;
        if (box.getXSpan() != 47 || box.getZSpan() != 47 || box.getYSpan() != height) {
            return reject(site, "SIZE");
        }
        int halo = oceanSite ? 8 : 128;
        if ((long) box.minX() - halo < -WORLD_LIMIT || (long) box.maxX() + halo > WORLD_LIMIT
                || (long) box.minZ() - halo < -WORLD_LIMIT
                || (long) box.maxZ() + halo > WORLD_LIMIT
                || box.minY() - 4 < context.heightAccessor().getMinY()
                || box.maxY() + 2 >= context.heightAccessor().getMaxY()) {
            return reject(site, "BOUNDS");
        }
        boolean accepted = oceanSite ? ocean(context, box, site == Site.OCEAN_V4)
                : archive(context, box, rotation);
        if (accepted) count(site, "ACCEPTED");
        return accepted;
    }

    private static boolean ocean(Structure.GenerationContext context, BoundingBox box,
                                 boolean v4) {
        Site site = v4 ? Site.OCEAN_V4 : Site.OCEAN;
        if (box.minY() < SatelliteTerrainBounds.OCEAN_MIN_Y
                || box.minY() > SatelliteTerrainBounds.OCEAN_MAX_Y)
            return reject(site, "DEPTH");
        int minFloor = Integer.MAX_VALUE;
        int maxFloor = Integer.MIN_VALUE;
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                int floor = context.chunkGenerator().getBaseHeight(x, z,
                        Heightmap.Types.OCEAN_FLOOR_WG,
                        context.heightAccessor(), context.randomState()) - 1;
                if (!(v4 ? SatelliteTerrainBounds.oceanV4Floor(box.minY(), floor)
                        : SatelliteTerrainBounds.oceanFloor(box.minY(), floor))) {
                    return reject(site, "SEABED_HEIGHT");
                }
                minFloor = Math.min(minFloor, floor);
                maxFloor = Math.max(maxFloor, floor);
                Holder<Biome> biome = context.biomeResolver().getNoiseBiome(
                        x >> 2, floor >> 2, z >> 2);
                // Icebergs block the floating boat dock even when seabed depth fits.
                if (!context.validBiome().test(biome) || biome.is(Biomes.DEEP_FROZEN_OCEAN)) {
                    return reject(site, "BIOME");
                }
                NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z,
                        context.heightAccessor(), context.randomState());
                // V4's sealed four-layer foot bridges small natural pockets.
                // Nine widely separated rock anchors still tie it to the bed.
                boolean anchor = !v4 || (x == box.minX() + 6
                        || x == box.minX() + 23 || x == box.maxX() - 6)
                        && (z == box.minZ() + 6 || z == box.minZ() + 23
                        || z == box.maxZ() - 6);
                if (anchor) for (int y = floor - 3; y <= floor; y++) {
                    if (!drySolid(column, y)) return reject(site, "SUPPORT");
                }
                for (int y = floor + 1; y <= 62; y++) {
                    if (!column.getBlock(y).getFluidState().is(FluidTags.WATER))
                        return reject(site, "WATER_COLUMN");
                }
                if (!column.getBlock(63).isAir() || !column.getBlock(64).isAir()) {
                    return reject(site, "SEA_SURFACE");
                }
            }
        }
        if (maxFloor - minFloor > (v4
                ? SatelliteTerrainBounds.OCEAN_V4_FLOOR_MAX_OFFSET
                    - SatelliteTerrainBounds.OCEAN_V4_FLOOR_MIN_OFFSET
                : SatelliteTerrainBounds.OCEAN_FLOOR_MAX_OFFSET
                    - SatelliteTerrainBounds.OCEAN_FLOOR_MIN_OFFSET))
            return reject(site, "SEABED_SLOPE");
        if (v4) {
            // The saved-world audit checks every cell immediately outside the
            // authored foot. A four-block apron sample can miss a one-cell
            // shelf at the seam and save a site whose controls must stay shut.
            for (int x = box.minX() - 1; x <= box.maxX() + 1; x++) {
                for (int z = box.minZ() - 1; z <= box.maxZ() + 1; z++) {
                    if (x >= box.minX() && x <= box.maxX()
                            && z >= box.minZ() && z <= box.maxZ()) continue;
                    int floor = context.chunkGenerator().getBaseHeight(x, z,
                            Heightmap.Types.OCEAN_FLOOR_WG,
                            context.heightAccessor(), context.randomState()) - 1;
                    if (!SatelliteTerrainBounds.oceanV4Floor(box.minY(), floor))
                        return reject(site, "EXTERIOR_FLOOR");
                    NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z,
                            context.heightAccessor(), context.randomState());
                    // A seabed level with the authored foot leaves its whole
                    // four-block edge exposed. Accept it only on sound rock;
                    // final carving is checked again in the loaded audit.
                    if (floor == box.minY()) for (int y = floor - 3; y <= floor; y++) {
                        if (!drySolid(column, y)) return reject(site, "EXTERIOR_SUPPORT");
                    }
                    for (int y = floor + 1; y <= 62; y++) {
                        if (!column.getBlock(y).getFluidState().is(FluidTags.WATER))
                            return reject(site, "EXTERIOR_WATER");
                    }
                    if (!column.getBlock(63).isAir()
                            || !column.getBlock(64).isAir())
                        return reject(site, "EXTERIOR_SURFACE");
                }
            }
        }
        // The launch ring must remain open ocean; a nearby shore or iceberg
        // would leave the boat approach against rock or ice.
        for (int x = box.minX() - 8; x <= box.maxX() + 8; x += 4) {
            for (int z = box.minZ() - 8; z <= box.maxZ() + 8; z += 4) {
                if (x >= box.minX() && x <= box.maxX()
                        && z >= box.minZ() && z <= box.maxZ()) continue;
                int floor = context.chunkGenerator().getBaseHeight(x, z,
                        Heightmap.Types.OCEAN_FLOOR_WG,
                        context.heightAccessor(), context.randomState()) - 1;
                if (floor > SatelliteTerrainBounds.OCEAN_SHORE_MAX_Y)
                    return reject(site, "SHORE");
                NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z,
                        context.heightAccessor(), context.randomState());
                if (!column.getBlock(55).getFluidState().is(FluidTags.WATER)
                        || !column.getBlock(62).getFluidState().is(FluidTags.WATER)
                        || !column.getBlock(63).isAir()) return reject(site, "BOAT_APPROACH");
            }
        }
        return true;
    }

    private static boolean archive(Structure.GenerationContext context, BoundingBox box,
                                   Rotation rotation) {
        int root = box.minY() + 28;
        if (root < SatelliteTerrainBounds.ARCHIVE_ROOT_MIN_Y
                || root > SatelliteTerrainBounds.ARCHIVE_ROOT_MAX_Y)
            return reject(Site.ARCHIVE, "DEPTH");
        int lowSurface = Integer.MAX_VALUE;
        int highSurface = Integer.MIN_VALUE;
        int centerX = box.minX() + 23;
        int centerZ = box.minZ() + 23;
        // The only surface architecture is the 15-wide index shelter. The
        // authored stone shell below replaces the entire buried footprint,
        // including ordinary noise caves; it needs sound support, not a
        // naturally solid 47-by-52 cube.
        int radius = SatelliteTerrainBounds.ARCHIVE_SHELTER_RADIUS;
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                int surface = context.chunkGenerator().getBaseHeight(x, z,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        context.heightAccessor(), context.randomState()) - 1;
                if (!SatelliteTerrainBounds.archiveShelterSurface(box.minY(), surface)) {
                    return reject(Site.ARCHIVE, "SURFACE_HEIGHT");
                }
                lowSurface = Math.min(lowSurface, surface);
                highSurface = Math.max(highSurface, surface);
                Holder<Biome> biome = context.biomeResolver().getNoiseBiome(
                        x >> 2, surface >> 2, z >> 2);
                if (!context.validBiome().test(biome)) return reject(Site.ARCHIVE, "BIOME");
                NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z,
                        context.heightAccessor(), context.randomState());
                for (int y = surface + 1; y <= box.maxY() + 1; y++) {
                    if (!column.getBlock(y).isAir()) return reject(Site.ARCHIVE, "SURFACE_AIR");
                }
            }
        }
        if (highSurface - lowSurface > 4) return reject(Site.ARCHIVE, "SURFACE_SLOPE");
        // The local north clue rotates with the saved jigsaw piece. Survey
        // the same six world cells that the loaded audit will inspect.
        for (int lateral = -1; lateral <= 1; lateral++) {
            for (int outward = 7; outward <= 8; outward++) {
                BlockPos walk = SatelliteWalkIn.point(box, rotation,
                        lateral, outward, box.minY() + 49);
                int x = walk.getX();
                int z = walk.getZ();
                int surface = context.chunkGenerator().getBaseHeight(x, z,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        context.heightAccessor(), context.randomState()) - 1;
                if (surface < box.minY() + 48 || surface > box.minY() + 50)
                    return reject(Site.ARCHIVE, "WALK_IN");
                NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z,
                        context.heightAccessor(), context.randomState());
                if (!drySolid(column, surface) || !column.getBlock(surface + 1).isAir()
                        || !column.getBlock(surface + 2).isAir())
                    return reject(Site.ARCHIVE, "WALK_IN");
            }
        }
        if (!archiveNaturalApproach(context, box, rotation))
            return reject(Site.ARCHIVE, "APPROACH");
        // Nine anchors must sit on dry rock below the 41-wide stone foot.
        for (int x : new int[]{box.minX() + 4, centerX, box.maxX() - 4}) {
            for (int z : new int[]{box.minZ() + 4, centerZ, box.maxZ() - 4}) {
                NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z,
                        context.heightAccessor(), context.randomState());
                for (int y = box.minY() - 4; y < box.minY(); y++) {
                    if (!drySolid(column, y)) return reject(Site.ARCHIVE, "FOOT_SUPPORT");
                }
            }
        }
        // Ancient-city starts are unavailable from this pre-carver context.
        // Reject the Deep Dark noise biome throughout the wider halo. The
        // loaded audit still checks actual starts, sculk and final carving.
        for (int x = box.minX() - 128; x <= box.maxX() + 128; x += 8) {
            for (int z = box.minZ() - 128; z <= box.maxZ() + 128; z += 8) {
                for (int y = context.heightAccessor().getMinY(); y <= box.maxY(); y += 8) {
                    if (context.biomeResolver().getNoiseBiome(x >> 2, y >> 2, z >> 2)
                            .is(Biomes.DEEP_DARK)) return reject(Site.ARCHIVE, "DEEP_DARK");
                }
            }
        }
        return true;
    }

    /** The stone walk ends at outward 11; a player may meet it head-on or around either post. */
    private static boolean archiveNaturalApproach(Structure.GenerationContext context,
                                                  BoundingBox box, Rotation rotation) {
        return archiveCenterApproach(context, box, rotation)
                || archiveSideApproach(context, box, rotation, -4)
                || archiveSideApproach(context, box, rotation, 4);
    }

    private static boolean archiveCenterApproach(Structure.GenerationContext context,
                                                 BoundingBox box, Rotation rotation) {
        int previousSurface = box.minY() + 49;
        for (int outward = 12; outward <= 14; outward++) {
            int surface = archiveWalkSurface(context, box, rotation, 0, outward);
            // Every step must be walkable from the platform, while the last
            // cell must also satisfy the normal-input player's staging height.
            if (surface == Integer.MIN_VALUE || Math.abs(surface - previousSurface) > 1
                    || outward == 14 && Math.abs(surface - (box.minY() + 49)) > 2)
                return false;
            previousSurface = surface;
        }
        return true;
    }

    private static boolean archiveSideApproach(Structure.GenerationContext context,
                                               BoundingBox box, Rotation rotation, int lateral) {
        int previousSurface = Integer.MIN_VALUE;
        // Stay outside the support post at lateral 3 until outward 7, then
        // turn across the natural shoulder onto the five-wide stone walk.
        for (int outward = 14; outward >= 7; outward--) {
            int surface = archiveWalkSurface(context, box, rotation, lateral, outward);
            if (surface == Integer.MIN_VALUE || previousSurface != Integer.MIN_VALUE
                    && Math.abs(surface - previousSurface) > 1) return false;
            previousSurface = surface;
        }
        int shoulder = archiveWalkSurface(context, box, rotation,
                Integer.signum(lateral) * 3, 7);
        return shoulder != Integer.MIN_VALUE
                && Math.abs(shoulder - previousSurface) <= 1
                && Math.abs(shoulder - (box.minY() + 49)) <= 1;
    }

    private static int archiveWalkSurface(Structure.GenerationContext context,
                                          BoundingBox box, Rotation rotation,
                                          int lateral, int outward) {
        BlockPos walk = SatelliteWalkIn.point(box, rotation,
                lateral, outward, box.minY() + 50);
        int x = walk.getX();
        int z = walk.getZ();
        int surface = context.chunkGenerator().getBaseHeight(x, z,
                Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState()) - 1;
        NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z,
                context.heightAccessor(), context.randomState());
        return drySolid(column, surface) && column.getBlock(surface + 1).isAir()
                && column.getBlock(surface + 2).isAir()
                ? surface : Integer.MIN_VALUE;
    }

    private static boolean drySolid(NoiseColumn column, int y) {
        var state = column.getBlock(y);
        return state.isSolid() && state.getFluidState().isEmpty();
    }

    private static boolean reject(Site site, String reason) {
        count(site, reason);
        return false;
    }

    static void recordUnresolved(String reason) {
        count("UNRESOLVED:" + reason);
    }

    /** A snapshot for bounded natural-placement trials before the JVM exits. */
    public static String diagnosticSummary() {
        if (!DIAGNOSTICS) return "disabled";
        String summary = COUNTS.entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue().sum())
                .collect(java.util.stream.Collectors.joining(" "));
        return summary.isEmpty() ? "no candidates reached the satellite structure" : summary;
    }

    private static void count(Site site, String reason) {
        count(site + ":" + reason);
    }

    private static void count(String key) {
        if (DIAGNOSTICS) COUNTS.computeIfAbsent(key,
                ignored -> new LongAdder()).increment();
    }
}
