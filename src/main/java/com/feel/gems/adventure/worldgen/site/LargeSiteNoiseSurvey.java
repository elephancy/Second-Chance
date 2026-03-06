package com.feel.gems.adventure.worldgen.site;

import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Generation-time rejection for a large, fixed-piece structure. This sees
 * terrain noise, not trees, later structures, fluids after decoration, or
 * player edits. A caller must keep its structure set inactive until those
 * later conditions have a separate, proven solution.
 */
public final class LargeSiteNoiseSurvey {
    public enum Failure { HEIGHT_BOUNDS, ELEVATION, BIOME, SOLID_SUPPORT, SLOPE }

    public record SurfaceRules(int minY, int maxY, int maxSlope, int minSolidDepth,
                               int sampleStep, int approachLength) {
        public SurfaceRules {
            if (minY > maxY || maxSlope < 0 || minSolidDepth < 1
                    || sampleStep < 1 || sampleStep > 16 || approachLength < 0
                    || approachLength > 32) {
                throw new IllegalArgumentException("Invalid large-site surface rules");
            }
        }
    }

    private LargeSiteNoiseSurvey() { }

    /** Checks the entire bounding rectangle and every exterior approach band. */
    public static boolean supportsSurface(Structure.GenerationContext context,
                                          BoundingBox bounds, SurfaceRules rules) {
        return supportsSurface(context, bounds, rules, null);
    }

    /** An optional exact biome gate protects sites from later canopy features. */
    public static boolean supportsSurface(Structure.GenerationContext context,
                                          BoundingBox bounds, SurfaceRules rules,
                                          ResourceKey<Biome> requiredBiome) {
        return supportsSurface(context, bounds, rules, requiredBiome, null);
    }

    /** Optional first-rejection hook for an opt-in worldgen fixture. */
    public static boolean supportsSurface(Structure.GenerationContext context,
                                          BoundingBox bounds, SurfaceRules rules,
                                          ResourceKey<Biome> requiredBiome,
                                          Consumer<Failure> onFailure) {
        if (bounds.minY() < context.heightAccessor().getMinY()
                || bounds.maxY() >= context.heightAccessor().getMaxY()) {
            return reject(onFailure, Failure.HEIGHT_BOUNDS);
        }
        int minX = bounds.minX() - rules.approachLength();
        int maxX = bounds.maxX() + rules.approachLength();
        int minZ = bounds.minZ() - rules.approachLength();
        int maxZ = bounds.maxZ() + rules.approachLength();
        if (minX < -29_999_900 || maxX > 29_999_900
                || minZ < -29_999_900 || maxZ > 29_999_900) {
            return reject(onFailure, Failure.HEIGHT_BOUNDS);
        }

        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int x = minX; x <= maxX; x = next(x, maxX, rules.sampleStep())) {
            for (int z = minZ; z <= maxZ; z = next(z, maxZ, rules.sampleStep())) {
                int y = context.chunkGenerator().getBaseHeight(x, z,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        context.heightAccessor(), context.randomState()) - 1;
                if (y < rules.minY() || y > rules.maxY()) {
                    return reject(onFailure, Failure.ELEVATION);
                }
                Holder<Biome> biome = context.biomeResolver().getNoiseBiome(
                        x >> 2, y >> 2, z >> 2);
                if (!context.validBiome().test(biome)
                        || (requiredBiome != null && !biome.is(requiredBiome))) {
                    return reject(onFailure, Failure.BIOME);
                }
                NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z,
                        context.heightAccessor(), context.randomState());
                for (int depth = 0; depth < rules.minSolidDepth(); depth++) {
                    var state = column.getBlock(y - depth);
                    if (!state.isSolid() || !state.getFluidState().isEmpty()) {
                        return reject(onFailure, Failure.SOLID_SUPPORT);
                    }
                }
                low = Math.min(low, y);
                high = Math.max(high, y);
                if (high - low > rules.maxSlope()) {
                    return reject(onFailure, Failure.SLOPE);
                }
                if (z == maxZ) break;
            }
            if (x == maxX) break;
        }
        return low != Integer.MAX_VALUE;
    }

    private static boolean reject(Consumer<Failure> onFailure, Failure reason) {
        if (onFailure != null) onFailure.accept(reason);
        return false;
    }

    private static int next(int current, int maximum, int step) {
        return Math.min(maximum, current + step);
    }
}
