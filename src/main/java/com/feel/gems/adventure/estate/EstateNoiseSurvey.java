package com.feel.gems.adventure.estate;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Candidate-scoped noise-only columns; never obtains, loads or edits a world chunk. */
final class EstateNoiseSurvey {
    private static final int BELOW = 64; // footing anchorTop inspects top through top-64
    private static final int ABOVE = 48; // 32 support + 10 seam rise + 4 deck offset
    private static final int PROFILE = BELOW + 1 + ABOVE;
    private static final byte AIR = 0;
    private static final byte WATER = 1;
    private static final byte DRY_SOLID = 2;
    private static final byte OTHER = 3;

    record Surface(int y, Column column) { }

    final class Column {
        private final ChunkSamples chunk;
        private final int index;

        private Column(ChunkSamples chunk, int index) {
            this.chunk = chunk;
            this.index = index;
        }

        int topY() { return chunk.topY[index]; }

        /** Retains exactly the four predicates used by the Estate site contract. */
        BlockState getBlock(int y) {
            int depth = y - topY() + BELOW;
            byte kind = depth < 0 || depth >= PROFILE ? OTHER
                    : chunk.profile[index * PROFILE + depth];
            return switch (kind) {
                case AIR -> Blocks.AIR.defaultBlockState();
                case WATER -> Blocks.WATER.defaultBlockState();
                case DRY_SOLID -> Blocks.STONE.defaultBlockState();
                default -> Blocks.LAVA.defaultBlockState();
            };
        }
    }

    private static final class ChunkSamples {
        private final int[] topY = new int[256];
        private final byte[] profile = new byte[256 * PROFILE];
        private final Column[] columns = new Column[256];
    }

    private final Structure.GenerationContext context;
    private final NoiseBasedChunkGenerator generator;
    private final NoiseGeneratorSettings settings;
    private final NoiseSettings noiseSettings;
    private final Aquifer.FluidPicker fluids;
    private final Map<Long, ChunkSamples> chunks = new HashMap<>();

    EstateNoiseSurvey(Structure.GenerationContext context) {
        this.context = context;
        this.generator = context.chunkGenerator() instanceof NoiseBasedChunkGenerator noise
                ? noise : null;
        if (generator == null) {
            settings = null;
            noiseSettings = null;
            fluids = null;
        } else {
            settings = generator.generatorSettings().value();
            noiseSettings = settings.noiseSettings()
                    .clampToHeightAccessor(context.heightAccessor());
            fluids = fluidPicker(settings);
        }
    }

    Surface sample(int x, int z) {
        int cx = x >> 4, cz = z >> 4;
        long key = ((long) cx << 32) ^ (cz & 0xffffffffL);
        ChunkSamples chunk = chunks.computeIfAbsent(key, ignored ->
                generator == null ? directChunk(cx, cz) : batchChunk(cx, cz));
        int index = (z & 15) * 16 + (x & 15);
        Column column = chunk.columns[index];
        if (column == null) chunk.columns[index] = column = new Column(chunk, index);
        return new Surface(column.topY(), column);
    }

    private ChunkSamples batchChunk(int cx, int cz) {
        int minX = cx << 4, minZ = cz << 4;
        DensityVolume volume = new DensityVolume(16, noiseSettings.height(), 16,
                minX, noiseSettings.minY(), minZ);
        BlockState[] states = new BlockState[volume.size()];
        try (NoiseChunk noise = new NoiseChunk(context.randomState(), null,
                settings, fluids, Blender.empty(), volume)) {
            try (ScopedDensityBuffer density = noise.cachingSamplers()
                    .get(settings.noiseRouter().finalDensity()).sampleVolume(volume)) {
                for (int iz = 0; iz < 16; iz++) {
                    int z = minZ + iz;
                    for (int ix = 0; ix < 16; ix++) {
                        int x = minX + ix;
                        for (int iy = volume.sizeY() - 1; iy >= 0; iy--) {
                            int y = volume.blockY(iy);
                            int index = volume.indexUnchecked(ix, iy, iz);
                            BlockState state = noise.aquifer().computeSubstance(x, y, z,
                                    density.get(index));
                            states[index] = state == null ? settings.defaultBlock() : state;
                        }
                    }
                }
            }
        }
        ChunkSamples chunk = new ChunkSamples();
        for (int iz = 0; iz < 16; iz++) {
            for (int ix = 0; ix < 16; ix++) {
                int top = context.heightAccessor().getMinY() - 1;
                for (int iy = volume.sizeY() - 1; iy >= 0; iy--) {
                    int y = volume.blockY(iy);
                    if (Heightmap.Types.WORLD_SURFACE_WG.isOpaque().test(
                            states[volume.indexUnchecked(ix, iy, iz)])) {
                        top = y;
                        break;
                    }
                }
                int column = iz * 16 + ix;
                chunk.topY[column] = top;
                for (int relative = 0; relative < PROFILE; relative++) {
                    int y = top - BELOW + relative;
                    int iy = y - volume.minBlockY();
                    BlockState state = iy < 0 || iy >= volume.sizeY()
                            ? Blocks.AIR.defaultBlockState()
                            : states[volume.indexUnchecked(ix, iy, iz)];
                    chunk.profile[column * PROFILE + relative] = classify(state);
                }
            }
        }
        return chunk;
    }

    /** Preserve modded non-noise generator support through its exact column API. */
    private ChunkSamples directChunk(int cx, int cz) {
        ChunkSamples chunk = new ChunkSamples();
        for (int iz = 0; iz < 16; iz++) {
            for (int ix = 0; ix < 16; ix++) {
                int x = (cx << 4) + ix, z = (cz << 4) + iz;
                NoiseColumn states = context.chunkGenerator().getBaseColumn(x, z,
                        context.heightAccessor(), context.randomState());
                int top = context.heightAccessor().getMinY() - 1;
                for (int y = context.heightAccessor().getMaxY() - 1;
                     y >= context.heightAccessor().getMinY(); y--) {
                    if (Heightmap.Types.WORLD_SURFACE_WG.isOpaque()
                            .test(states.getBlock(y))) {
                        top = y;
                        break;
                    }
                }
                int column = iz * 16 + ix;
                chunk.topY[column] = top;
                for (int relative = 0; relative < PROFILE; relative++) {
                    int y = top - BELOW + relative;
                    chunk.profile[column * PROFILE + relative] =
                            classify(states.getBlock(y));
                }
            }
        }
        return chunk;
    }

    private static byte classify(BlockState state) {
        if (state.isAir()) return AIR;
        if (state.is(Blocks.WATER)) return WATER;
        if (state.isSolid() && state.getFluidState().isEmpty()) return DRY_SOLID;
        return OTHER;
    }

    // Matches 26.3 NoiseBasedChunkGenerator.createFluidPicker. Full-height
    // GameTest comparison against getBaseColumn guards this implementation.
    private static Aquifer.FluidPicker fluidPicker(NoiseGeneratorSettings settings) {
        Aquifer.FluidStatus lava = new Aquifer.FluidStatus(-54,
                Blocks.LAVA.defaultBlockState());
        Aquifer.FluidStatus normal = new Aquifer.FluidStatus(settings.seaLevel(),
                settings.defaultFluid());
        Aquifer.FluidStatus disabled = new Aquifer.FluidStatus(
                DimensionType.MIN_Y * 2, Blocks.AIR.defaultBlockState());
        return (x, y, z) -> SharedConstants.DEBUG_DISABLE_FLUID_GENERATION ? disabled
                : y < Math.min(-54, settings.seaLevel()) ? lava : normal;
    }
}
