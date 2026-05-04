package com.feel.gems.gametest.adventure;

import com.feel.gems.GemsMod;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opt-in timing probe for the Atlas's exact synchronous structure search sequence. */
public final class AtlasTimingSmoke implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("gems-atlas-timing-smoke");
    private static final BlockPos ORIGIN = new BlockPos(0, 80, 0);
    private static final Target[] TARGETS = {
            new Target("second_chance_town", 8, 128),
            new Target("rehearsal_works", 16, 128),
            new Target("branch_vault", 8, 192),
            new Target("redline_house", 16, 128),
            new Target("othman_library", 16, 128),
            new Target("unwritten_court", 0, 192)
    };
    private int next;
    private int stopAt;

    private record Target(String id, int themedRings, int fallbackRings) { }

    @Override
    public void onInitialize() {
        if (!Boolean.getBoolean("gems.atlasTimingSmoke")) return;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int tick = server.getTickCount();
            if (stopAt > 0) {
                if (tick >= stopAt) server.halt(false);
                return;
            }
            if (tick < 80 || tick % 20 != 0) return;
            if (next >= TARGETS.length) {
                LOGGER.info("ATLAS_TIMING_COMPLETE targets={}", next);
                stopAt = tick + 20;
                return;
            }
            ServerLevel world = server.overworld();
            if (world == null) return;
            try {
                measure(world, TARGETS[next++], Boolean.getBoolean("gems.atlasTimingSmoke.staged"));
            } catch (Exception failure) {
                LOGGER.error("ATLAS_TIMING_FAILED target={}", TARGETS[next - 1].id(), failure);
                stopAt = tick + 20;
            }
        });
    }

    private static void measure(ServerLevel world, Target target, boolean staged) {
        long start = System.nanoTime();
        long nearbyNs = 0;
        long earlyThemedNs = 0;
        long themedNs = 0;
        long earlyFallbackNs = 0;
        long fallbackNs = 0;
        BlockPos found = null;
        if (target.themedRings() > 0) {
            found = world.findNearestMapStructure(tag("atlas_target/" + target.id()), ORIGIN, 1, false);
            nearbyNs = System.nanoTime() - start;
            if (found != null && horizontalDistanceSquared(found, ORIGIN) > 256L * 256L) found = null;
            if (found == null && staged) {
                long before = System.nanoTime();
                found = world.findNearestMapStructure(tag("themed_target/" + target.id()),
                        ORIGIN, Math.min(4, target.themedRings()), false);
                earlyThemedNs = System.nanoTime() - before;
            }
            if (found == null) {
                long before = System.nanoTime();
                found = world.findNearestMapStructure(tag("themed_target/" + target.id()),
                        ORIGIN, target.themedRings(), false);
                themedNs = System.nanoTime() - before;
            }
        }
        if (found == null) {
            if (staged) {
                long before = System.nanoTime();
                found = world.findNearestMapStructure(tag("atlas_target/" + target.id()),
                        ORIGIN, 8, false);
                earlyFallbackNs = System.nanoTime() - before;
            }
        }
        if (found == null) {
            long before = System.nanoTime();
            found = world.findNearestMapStructure(tag("atlas_target/" + target.id()),
                    ORIGIN, target.fallbackRings(), false);
            fallbackNs = System.nanoTime() - before;
        }
        LOGGER.info("ATLAS_TIMING mode={} target={} totalMs={} nearbyMs={} earlyThemedMs={} themedMs={} earlyFallbackMs={} fallbackMs={} found={}",
                staged ? "staged" : "baseline", target.id(), millis(System.nanoTime() - start),
                millis(nearbyNs), millis(earlyThemedNs), millis(themedNs),
                millis(earlyFallbackNs), millis(fallbackNs), found);
    }

    private static long horizontalDistanceSquared(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private static double millis(long nanos) {
        return Math.round(nanos / 100_000.0) / 10.0;
    }

    private static TagKey<Structure> tag(String path) {
        return TagKey.create(Registries.STRUCTURE,
                Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, path));
    }
}
