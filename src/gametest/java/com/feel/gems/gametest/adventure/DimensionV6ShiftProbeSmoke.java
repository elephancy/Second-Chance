package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.worldgen.site.DimensionSiteNoiseSurvey;
import java.util.OptionalInt;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Disposable, noise-only root-shift experiment; never places or loads a site. */
public final class DimensionV6ShiftProbeSmoke implements ModInitializer {
    private static final ResourceKey<Structure> EMBER = ResourceKey.create(
            Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath("gems", "redline_ember_bridge_v6"));
    private static final int[][] ROOTS = {
            {-7, 119}, {519, -1511}, {871, -1495}, {1639, -359},
            {1577, -7}, {-1833, -311}, {583, -1769}, {2007, -233}
    };
    private static final int[][] SHIFTS = {
            {0, 0}, {8, 0}, {-8, 0}, {0, 8}, {0, -8},
            {16, 0}, {-16, 0}, {0, 16}, {0, -16}
    };
    private static final Direction[][] AXES = {
            {Direction.EAST, Direction.SOUTH},
            {Direction.SOUTH, Direction.WEST},
            {Direction.WEST, Direction.NORTH},
            {Direction.NORTH, Direction.EAST}
    };
    private static final int TOTAL = ROOTS.length * SHIFTS.length * AXES.length;
    private static final long WALL_LIMIT_MILLIS = 180_000;

    private Structure.GenerationContext context;
    private int index;
    private int accepted;
    private long startedNanos;
    private long longestMillis;
    private boolean done;

    @Override
    public void onInitialize() {
        if (!Boolean.getBoolean("gems.dimensionV6ShiftProbe")) return;
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }

    private void tick(MinecraftServer server) {
        if (done || server.getTickCount() < 20) return;
        try {
            if (context == null) {
                ServerLevel nether = server.getLevel(Level.NETHER);
                if (nether == null) throw new IllegalStateException("Nether unavailable");
                Holder<Structure> holder = nether.registryAccess()
                        .lookupOrThrow(Registries.STRUCTURE).getOrThrow(EMBER);
                var source = nether.getChunkSource();
                var randomState = source.randomState();
                context = new Structure.GenerationContext(nether.registryAccess(),
                        source.getGenerator(), source.getGenerator().getBiomeSource(),
                        randomState.createClimateSampler(SamplerContext.EMPTY_UNCACHED),
                        randomState, server.getStructureTemplateManager(),
                        nether.getSeed(), new ChunkPos(0, 0), nether,
                        holder.value().biomes()::contains);
                startedNanos = System.nanoTime();
                System.out.println("DIMENSION_V6_SHIFT_START seed=" + nether.getSeed()
                        + " roots=" + ROOTS.length + " shifts=" + SHIFTS.length
                        + " rotations=" + AXES.length + " total=" + TOTAL);
            }
            if (index >= TOTAL) {
                done = true;
                System.out.println("DIMENSION_V6_SHIFT_DONE probes=" + index
                        + " accepts=" + accepted + " totalMillis=" + elapsed(startedNanos)
                        + " longestMillis=" + longestMillis);
                return;
            }
            if (elapsed(startedNanos) > WALL_LIMIT_MILLIS) {
                throw new IllegalStateException("bounded 180-second probe timeout at "
                        + index + "/" + TOTAL);
            }
            int rootIndex = index / (SHIFTS.length * AXES.length);
            int shiftIndex = index / AXES.length % SHIFTS.length;
            int axisIndex = index % AXES.length;
            int[] root = ROOTS[rootIndex];
            int[] shift = SHIFTS[shiftIndex];
            Direction east = AXES[axisIndex][0];
            Direction south = AXES[axisIndex][1];
            int x = root[0] + shift[0];
            int z = root[1] + shift[1];
            BoundingBox box = boxAt(x, z, east, south);
            long began = System.nanoTime();
            OptionalInt base = DimensionSiteNoiseSurvey.emberV6Base(
                    context, box, east, south);
            long millis = elapsed(began);
            longestMillis = Math.max(longestMillis, millis);
            if (base.isPresent()) accepted++;
            System.out.println("DIMENSION_V6_SHIFT root=" + root[0] + "," + root[1]
                    + " delta=" + shift[0] + "," + shift[1]
                    + " east=" + east + " south=" + south
                    + " base=" + (base.isPresent()
                            ? Integer.toString(base.getAsInt()) : "none")
                    + " millis=" + millis);
            index++;
        } catch (Throwable failure) {
            done = true;
            System.out.println("DIMENSION_V6_SHIFT_FAILED index=" + index
                    + " type=" + failure.getClass().getSimpleName()
                    + " message=" + failure.getMessage());
            failure.printStackTrace();
        }
    }

    private static BoundingBox boxAt(int rootX, int rootZ,
                                     Direction east, Direction south) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int localX : new int[]{0, 46}) {
            for (int localZ : new int[]{0, 70}) {
                int x = rootX + (localX - 23) * east.getStepX()
                        + (localZ - 23) * south.getStepX();
                int z = rootZ + (localX - 23) * east.getStepZ()
                        + (localZ - 23) * south.getStepZ();
                minX = Math.min(minX, x);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxZ = Math.max(maxZ, z);
            }
        }
        return new BoundingBox(minX, 29, minZ, maxX, 59, maxZ);
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
