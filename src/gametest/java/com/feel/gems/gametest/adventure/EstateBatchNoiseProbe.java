package com.feel.gems.gametest.adventure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
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

/** Diagnostic-only proof that a chunk-sized noise volume matches every column API state. */
final class EstateBatchNoiseProbe {
    private static final int BODY_RADIUS = 117;

    private EstateBatchNoiseProbe() { }

    static void verify(ServerLevel level, String seed, int centerX, int centerZ) {
        var source = level.getChunkSource();
        if (!(source.getGenerator() instanceof NoiseBasedChunkGenerator generator))
            throw new IllegalStateException("Estate batch probe requires a noise generator");
        var randomState = source.randomState();
        NoiseGeneratorSettings settings = generator.generatorSettings().value();
        NoiseSettings noiseSettings = settings.noiseSettings().clampToHeightAccessor(level);
        Aquifer.FluidPicker fluids = fluidPicker(settings);
        int minX = centerX - BODY_RADIUS, maxX = centerX + BODY_RADIUS;
        int minZ = centerZ - BODY_RADIUS, maxZ = centerZ + BODY_RADIUS;
        Map<ChunkPos, Boolean> loaded = new HashMap<>();
        for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
            for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
                loaded.put(new ChunkPos(cx, cz), source.hasChunk(cx, cz));
            }
        }
        long batchNanos = 0, columnNanos = 0;
        long matchedStates = 0;
        int matchedColumns = 0;
        List<Long> chunkBatchMillis = new ArrayList<>(loaded.size());
        for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
            for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
                int chunkX = cx << 4, chunkZ = cz << 4;
                DensityVolume volume = new DensityVolume(16, noiseSettings.height(), 16,
                        chunkX, noiseSettings.minY(), chunkZ);
                BlockState[] states = new BlockState[volume.size()];
                long batchStarted = System.nanoTime();
                try (NoiseChunk noise = new NoiseChunk(randomState, null, settings,
                        fluids, Blender.empty(), volume)) {
                    try (ScopedDensityBuffer density = noise.cachingSamplers()
                            .get(settings.noiseRouter().finalDensity()).sampleVolume(volume)) {
                        for (int iz = 0; iz < 16; iz++) {
                            int z = chunkZ + iz;
                            for (int ix = 0; ix < 16; ix++) {
                                int x = chunkX + ix;
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
                long elapsed = System.nanoTime() - batchStarted;
                batchNanos += elapsed;
                chunkBatchMillis.add(elapsed / 1_000_000);
                for (int z = Math.max(minZ, chunkZ); z <= Math.min(maxZ, chunkZ + 15); z++) {
                    for (int x = Math.max(minX, chunkX); x <= Math.min(maxX, chunkX + 15); x++) {
                        long columnStarted = System.nanoTime();
                        NoiseColumn column = generator.getBaseColumn(x, z, level, randomState);
                        columnNanos += System.nanoTime() - columnStarted;
                        int batchTop = level.getMinY() - 1;
                        int columnTop = level.getMinY() - 1;
                        for (int iy = volume.sizeY() - 1; iy >= 0; iy--) {
                            int y = volume.blockY(iy);
                            BlockState batch = states[volume.indexUnchecked(x - chunkX,
                                    iy, z - chunkZ)];
                            BlockState single = column.getBlock(y);
                            if (!batch.equals(single))
                                throw new IllegalStateException("Estate batch mismatch " + seed
                                        + " @ " + new BlockPos(x, y, z) + ": "
                                        + batch + " vs " + single);
                            if (batchTop < level.getMinY()
                                    && Heightmap.Types.WORLD_SURFACE_WG.isOpaque().test(batch))
                                batchTop = y;
                            if (columnTop < level.getMinY()
                                    && Heightmap.Types.WORLD_SURFACE_WG.isOpaque().test(single))
                                columnTop = y;
                            matchedStates++;
                        }
                        if (batchTop != columnTop)
                            throw new IllegalStateException("Estate batch top mismatch " + seed
                                    + " @ " + x + "," + z);
                        matchedColumns++;
                    }
                }
            }
        }
        for (var entry : loaded.entrySet()) {
            ChunkPos pos = entry.getKey();
            if (source.hasChunk(pos.x(), pos.z()) != entry.getValue())
                throw new IllegalStateException("Estate batch probe changed loaded state " + pos);
        }
        Collections.sort(chunkBatchMillis);
        int p50 = chunkBatchMillis.size() / 2;
        int p95 = Math.min(chunkBatchMillis.size() - 1,
                (int) Math.ceil(chunkBatchMillis.size() * .95) - 1);
        if (matchedColumns != 235 * 235)
            throw new IllegalStateException("Estate batch probe missed body columns: "
                    + matchedColumns);
        System.out.println("FOUR_ROADS_ESTATE_BATCH_EQUIVALENCE seed=" + seed
                + " x=" + centerX + " z=" + centerZ
                + " matchedColumns=" + matchedColumns
                + " matchedStates=" + matchedStates
                + " chunks=" + chunkBatchMillis.size()
                + " batchMs=" + batchNanos / 1_000_000
                + " columnMs=" + columnNanos / 1_000_000
                + " chunkP50Ms=" + chunkBatchMillis.get(p50)
                + " chunkP95Ms=" + chunkBatchMillis.get(p95)
                + " loadedStateChanges=0");
    }

    // Mirrors NoiseBasedChunkGenerator.createFluidPicker in 26.3. The full
    // state-by-state comparison above guards against future semantic drift.
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
