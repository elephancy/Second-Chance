package com.feel.gems.adventure.undercard;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/** Noise-stage rejection only; carvers and saved structure starts are later data. */
final class UndercardNoiseSurvey {
    enum Outcome { BIOME, ELEVATION, SLOPE, CAVE, HAZARD, ACCEPTED }

    private static final int APRON = 8;
    private static final int SOLID_BELOW = 4;
    private static final int DEEP_DARK_RADIUS = UndercardSite.HAZARD_HALO;
    private static final int CITY_REACH_CHUNKS = 18;
    private static final ResourceKey<StructureSet> CITY_SET = ResourceKey.create(
            Registries.STRUCTURE_SET,
            Identifier.fromNamespaceAndPath("minecraft", "ancient_cities"));

    private UndercardNoiseSurvey() { }

    static Outcome assess(Structure.GenerationContext context, BoundingBox box,
                          Rotation rotation, boolean sealedV5) {
        if (box.minY() - SOLID_BELOW < context.heightAccessor().getMinY()
                || box.maxY() >= context.heightAccessor().getMaxY()) return Outcome.ELEVATION;
        int rootY = box.minY() + 22;
        // The venue is measured against its local surface below. A high dry
        // plateau can still bury the ring to the same depth as a low desert.
        if (rootY < 48 || rootY > (sealedV5 ? 112 : 86)) return Outcome.ELEVATION;
        if (sealedV5) return assessSealedV5(context, box, rotation);
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        int minX = box.minX() - APRON;
        int maxX = box.maxX() + APRON;
        int minZ = box.minZ() - APRON;
        int maxZ = box.maxZ() + APRON;
        for (int x = minX; x <= maxX; x = next(x, maxX)) {
            for (int z = minZ; z <= maxZ; z = next(z, maxZ)) {
                int surface = context.chunkGenerator().getBaseHeight(x, z,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        context.heightAccessor(), context.randomState()) - 1;
                if (surface < box.minY() + 28 || surface > box.minY() + 36)
                    return Outcome.ELEVATION;
                low = Math.min(low, surface);
                high = Math.max(high, surface);
                if (high - low > 4) return Outcome.SLOPE;
                if (!context.validBiome().test(context.biomeResolver().getNoiseBiome(
                        x >> 2, surface >> 2, z >> 2))) return Outcome.BIOME;
                NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z,
                        context.heightAccessor(), context.randomState());
                for (int y = box.minY() - SOLID_BELOW; y <= box.minY() + 22; y++) {
                    var state = column.getBlock(y);
                    if (!state.isSolid() || !state.getFluidState().isEmpty())
                        return Outcome.CAVE;
                }
                if (z == maxZ) break;
            }
            if (x == maxX) break;
        }
        if (!dryShaftColumn(context, box, rotation)) return Outcome.CAVE;
        return noDeepDarkInReach(context, box) && noPossibleCityInReach(context, box)
                ? Outcome.ACCEPTED : Outcome.HAZARD;
    }

    /** V5 brings its own floor and roof, so only its entry needs a flat band. */
    private static Outcome assessSealedV5(Structure.GenerationContext context,
                                          BoundingBox box, Rotation rotation) {
        Direction outward = rotation.rotate(Direction.SOUTH);
        int centerX = box.getCenter().getX();
        int centerZ = box.getCenter().getZ();
        // The dry entrance biome is the contract for this buried venue. Check
        // it before expensive 79x79 footing columns on shifted candidates.
        for (int distance : new int[]{4, 14}) {
            int x = centerX + outward.getStepX() * distance;
            int z = centerZ + outward.getStepZ() * distance;
            int surface = context.chunkGenerator().getBaseHeight(x, z,
                    Heightmap.Types.WORLD_SURFACE_WG,
                    context.heightAccessor(), context.randomState()) - 1;
            if (!context.validBiome().test(context.biomeResolver().getNoiseBiome(
                    x >> 2, surface >> 2, z >> 2))) return Outcome.BIOME;
        }
        int prior = Integer.MIN_VALUE;
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int distance = 4; distance <= 14; distance++) {
            int x = centerX + outward.getStepX() * distance;
            int z = centerZ + outward.getStepZ() * distance;
            int surface = context.chunkGenerator().getBaseHeight(x, z,
                    Heightmap.Types.WORLD_SURFACE_WG,
                    context.heightAccessor(), context.randomState()) - 1;
            if (surface < box.minY() + 27 || surface > box.minY() + 37)
                return Outcome.ELEVATION;
            if (prior != Integer.MIN_VALUE && Math.abs(surface - prior) > 2)
                return Outcome.SLOPE;
            low = Math.min(low, surface);
            high = Math.max(high, surface);
            if (high - low > 6) return Outcome.SLOPE;
            prior = surface;
        }
        // The buried hall may cross an irregular biome border or a carver:
        // its authored shell replaces those cells. A four-block dry footing
        // must still be sound beneath the whole footprint. Water or lava in
        // the buried volume is rejected before any structure is generated.
        for (int x = box.minX() - APRON; x <= box.maxX() + APRON;
             x = next(x, box.maxX() + APRON)) {
            for (int z = box.minZ() - APRON; z <= box.maxZ() + APRON;
                 z = next(z, box.maxZ() + APRON)) {
                int surface = context.chunkGenerator().getBaseHeight(x, z,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        context.heightAccessor(), context.randomState()) - 1;
                if (surface < box.minY() + 26) return Outcome.ELEVATION;
                NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z,
                        context.heightAccessor(), context.randomState());
                for (int y = box.minY() - SOLID_BELOW; y < box.minY(); y++) {
                    var state = column.getBlock(y);
                    if (!state.isSolid() || !state.getFluidState().isEmpty())
                        return Outcome.CAVE;
                }
                for (int y = box.minY(); y <= box.minY() + 24; y++) {
                    if (!column.getBlock(y).getFluidState().isEmpty())
                        return Outcome.CAVE;
                }
                if (z == box.maxZ() + APRON) break;
            }
            if (x == box.maxX() + APRON) break;
        }
        if (!dryShaftColumn(context, box, rotation)) return Outcome.CAVE;
        return noDeepDarkInReach(context, box) && noPossibleCityInReach(context, box)
                ? Outcome.ACCEPTED : Outcome.HAZARD;
    }

    /** A waterlogged ladder can flood the sealed service route from inside. */
    private static boolean dryShaftColumn(Structure.GenerationContext context,
                                          BoundingBox box, Rotation rotation) {
        Direction outward = rotation.rotate(Direction.SOUTH);
        int x = box.getCenter().getX() + outward.getStepX() * 20;
        int z = box.getCenter().getZ() + outward.getStepZ() * 20;
        NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z,
                context.heightAccessor(), context.randomState());
        for (int y = box.minY() + 4; y <= box.minY() + 34; y++) {
            if (!column.getBlock(y).getFluidState().isEmpty()) return false;
        }
        return true;
    }

    /**
     * The vanilla placement exclusion tests possible city grid cells even when
     * their noise biome cannot generate a city. Inspect the same nearby grid,
     * but reject only cells with Deep Dark at the city's underground anchor.
     * Loaded-world validation still rejects actual city pieces and sculk.
     */
    private static boolean noPossibleCityInReach(Structure.GenerationContext context,
                                                  BoundingBox box) {
        StructureSet set = context.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)
                .getValue(CITY_SET);
        if (set == null || !(set.placement() instanceof RandomSpreadStructurePlacement spread)) {
            return false;
        }
        int centerX = box.getCenter().getX();
        int centerZ = box.getCenter().getZ();
        int chunkX = centerX >> 4;
        int chunkZ = centerZ >> 4;
        int spacing = spread.spacing();
        for (int rx = Math.floorDiv(chunkX - CITY_REACH_CHUNKS, spacing);
             rx <= Math.floorDiv(chunkX + CITY_REACH_CHUNKS, spacing); rx++) {
            for (int rz = Math.floorDiv(chunkZ - CITY_REACH_CHUNKS, spacing);
                 rz <= Math.floorDiv(chunkZ + CITY_REACH_CHUNKS, spacing); rz++) {
                var candidate = spread.getPotentialStructureChunk(context.seed(),
                        rx * spacing, rz * spacing);
                int cityX = candidate.getMinBlockX() + 8;
                int cityZ = candidate.getMinBlockZ() + 8;
                if (Math.abs(cityX - centerX) > CITY_REACH_CHUNKS * 16
                        || Math.abs(cityZ - centerZ) > CITY_REACH_CHUNKS * 16) continue;
                if (deepDarkCityAnchor(context, cityX, cityZ)) return false;
            }
        }
        return true;
    }

    private static boolean deepDarkCityAnchor(Structure.GenerationContext context,
                                               int cityX, int cityZ) {
        for (int qx = (cityX - 20) >> 2; qx <= (cityX + 20) >> 2; qx++) {
            for (int qz = (cityZ - 20) >> 2; qz <= (cityZ + 20) >> 2; qz++) {
                for (int qy = -40 >> 2; qy <= -12 >> 2; qy++) {
                    if (context.biomeResolver().getNoiseBiome(qx, qy, qz)
                            .is(Biomes.DEEP_DARK)) return true;
                }
            }
        }
        return false;
    }

    private static boolean noDeepDarkInReach(Structure.GenerationContext context,
                                             BoundingBox box) {
        int cx = box.getCenter().getX();
        int cz = box.getCenter().getZ();
        int minY = Math.max(context.heightAccessor().getMinY(),
                box.minY() - DEEP_DARK_RADIUS);
        int maxY = Math.min(context.heightAccessor().getMaxY() - 1,
                box.maxY() + DEEP_DARK_RADIUS);
        for (int qx = (cx - DEEP_DARK_RADIUS) >> 2;
             qx <= (cx + DEEP_DARK_RADIUS) >> 2; qx++) {
            int x = (qx << 2) + 2;
            for (int qz = (cz - DEEP_DARK_RADIUS) >> 2;
                 qz <= (cz + DEEP_DARK_RADIUS) >> 2; qz++) {
                int z = (qz << 2) + 2;
                int dx = x - cx;
                int dz = z - cz;
                if (dx * dx + dz * dz > DEEP_DARK_RADIUS * DEEP_DARK_RADIUS) continue;
                for (int qy = minY >> 2; qy <= maxY >> 2; qy++) {
                    if (context.biomeResolver().getNoiseBiome(qx, qy, qz)
                            .is(Biomes.DEEP_DARK)) return false;
                }
            }
        }
        return true;
    }

    private static int next(int current, int maximum) {
        return Math.min(maximum, current + 4);
    }
}
