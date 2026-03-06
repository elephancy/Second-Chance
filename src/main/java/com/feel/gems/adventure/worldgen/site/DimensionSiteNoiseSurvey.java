package com.feel.gems.adventure.worldgen.site;

import java.util.EnumMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.atomic.LongAdder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Read-only 26.3 base-noise and walk-in survey for the dimension sites. */
public final class DimensionSiteNoiseSurvey {
    public enum Site { ASH_ANNEX, EMBER_BRIDGE, ASH_ANNEX_V2, EMBER_BRIDGE_V2,
        ASH_ANNEX_V3, ASH_ANNEX_V4, ASH_ANNEX_V5,
        EMBER_BRIDGE_V3, EMBER_BRIDGE_V4, EMBER_BRIDGE_V5,
        EMBER_BRIDGE_V6,
        END_SOURCE, END_SOURCE_V2, END_SOURCE_V3, END_SOURCE_V4 }

    private enum Reason {
        ACCEPT, SHAPE_OR_BOUNDS, MASK_INVALID, BIOME, ASH_CLEARANCE, ASH_SUPPORT,
        EMBER_CLEARANCE, EMBER_LAVA, EMBER_BANKS, NETHER_ARRIVAL,
        NETHER_RUNWAY, NETHER_GALLERY, NETHER_ANCHOR, NETHER_BASE_CAP,
        END_DISTANCE, END_SURFACE, END_SUPPORT, END_RELIEF,
        END_CLEARANCE, END_ARRIVAL
    }

    private static final int WORLD_LIMIT = 29_999_900;
    private static final boolean DIAGNOSTICS = Boolean.getBoolean(
            "gems.dimensionPlacementDiagnostics");
    private static final boolean V6_TRACE = DIAGNOSTICS && Boolean.getBoolean(
            "gems.dimensionPlacementV6Trace");
    private static final EnumMap<Site, EnumMap<Reason, LongAdder>> COUNTS =
            new EnumMap<>(Site.class);
    private static boolean diagnosticsRegistered;

    static {
        if (DIAGNOSTICS) {
            for (Site site : Site.values()) {
                EnumMap<Reason, LongAdder> byReason = new EnumMap<>(Reason.class);
                for (Reason reason : Reason.values()) byReason.put(reason, new LongAdder());
                COUNTS.put(site, byReason);
            }
        }
    }

    private DimensionSiteNoiseSurvey() { }

    /** Opt-in aggregate counts only; no per-candidate generation log spam. */
    public static void initDiagnostics() {
        if (!DIAGNOSTICS || diagnosticsRegistered) return;
        diagnosticsRegistered = true;
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Site site : Site.values()) {
                StringBuilder line = new StringBuilder("DIMENSION_SITE_NOISE site=")
                        .append(site);
                for (Reason reason : Reason.values()) {
                    LongAdder count = COUNTS.get(site).get(reason);
                    line.append(' ').append(reason).append('=').append(count.sumThenReset());
                }
                System.out.println(line);
            }
        });
    }

    public static boolean accepts(Structure.GenerationContext context,
                                  BoundingBox body, Site site,
                                  Direction localEast, Direction localSouth) {
        Reason reason = assess(context, body, site, localEast, localSouth);
        if (DIAGNOSTICS) COUNTS.get(site).get(reason).increment();
        return reason == Reason.ACCEPT;
    }

    /** Bounded new-chunk rock-face search; the v1 Y64 start remains untouched. */
    public static OptionalInt ashV2Base(Structure.GenerationContext context,
                                        BoundingBox planned,
                                        Direction east, Direction south) {
        for (int base : new int[]{64, 56, 72, 48, 80, 40}) {
            Reason reason = assess(context,
                    planned.moved(0, base - planned.minY(), 0),
                    Site.ASH_ANNEX_V2, east, south);
            if (DIAGNOSTICS) COUNTS.get(Site.ASH_ANNEX_V2).get(reason).increment();
            if (reason == Reason.ACCEPT) return OptionalInt.of(base);
        }
        return OptionalInt.empty();
    }

    /** Find a dry natural walk-in above the lava sea for the authored crucible. */
    public static OptionalInt emberV2Base(Structure.GenerationContext context,
                                          BoundingBox planned,
                                          Direction east, Direction south) {
        for (int base : new int[]{56, 64, 48, 72, 40, 80, 32, 29}) {
            Reason reason = assess(context,
                    planned.moved(0, base - planned.minY(), 0),
                    Site.EMBER_BRIDGE_V2, east, south);
            if (DIAGNOSTICS) COUNTS.get(Site.EMBER_BRIDGE_V2).get(reason).increment();
            if (reason == Reason.ACCEPT) return OptionalInt.of(base);
        }
        return OptionalInt.empty();
    }

    /** V3 owns the former exterior approach inside its 55-block template. */
    public static OptionalInt ashV3Base(Structure.GenerationContext context,
                                        BoundingBox planned,
                                        Direction east, Direction south) {
        for (int base : new int[]{64, 56, 72, 48, 80, 40}) {
            Reason reason = assess(context,
                    planned.moved(0, base - planned.minY(), 0),
                    Site.ASH_ANNEX_V3, east, south);
            if (DIAGNOSTICS) COUNTS.get(Site.ASH_ANNEX_V3).get(reason).increment();
            if (reason == Reason.ACCEPT) return OptionalInt.of(base);
        }
        return OptionalInt.empty();
    }

    /** V4 changes Ash geometry inside the same guarded v3 footprint. */
    public static OptionalInt ashV4Base(Structure.GenerationContext context,
                                        BoundingBox planned,
                                        Direction east, Direction south) {
        for (int base : new int[]{64, 56, 72, 48, 80, 40}) {
            Reason reason = assess(context,
                    planned.moved(0, base - planned.minY(), 0),
                    Site.ASH_ANNEX_V4, east, south);
            if (DIAGNOSTICS) COUNTS.get(Site.ASH_ANNEX_V4).get(reason).increment();
            if (reason == Reason.ACCEPT) return OptionalInt.of(base);
        }
        return OptionalInt.empty();
    }

    /** V5 changes only the Ash interior passage; it retains v4's site envelope. */
    public static OptionalInt ashV5Base(Structure.GenerationContext context,
                                        BoundingBox planned,
                                        Direction east, Direction south) {
        for (int base : new int[]{64, 56, 72, 48, 80, 40}) {
            Reason reason = assess(context,
                    planned.moved(0, base - planned.minY(), 0),
                    Site.ASH_ANNEX_V5, east, south);
            if (DIAGNOSTICS) COUNTS.get(Site.ASH_ANNEX_V5).get(reason).increment();
            if (reason == Reason.ACCEPT) return OptionalInt.of(base);
        }
        return OptionalInt.empty();
    }

    public static OptionalInt emberV3Base(Structure.GenerationContext context,
                                          BoundingBox planned,
                                          Direction east, Direction south) {
        for (int base : new int[]{56, 64, 48, 72, 40, 80, 32, 29}) {
            Reason reason = assess(context,
                    planned.moved(0, base - planned.minY(), 0),
                    Site.EMBER_BRIDGE_V3, east, south);
            if (DIAGNOSTICS) COUNTS.get(Site.EMBER_BRIDGE_V3).get(reason).increment();
            if (reason == Reason.ACCEPT) return OptionalInt.of(base);
        }
        return OptionalInt.empty();
    }

    /** V4 retains the authored v3 Ember build but requires a real outer runway. */
    public static OptionalInt emberV4Base(Structure.GenerationContext context,
                                          BoundingBox planned,
                                          Direction east, Direction south) {
        for (int base : new int[]{56, 64, 48, 72, 40, 80, 32, 29}) {
            Reason reason = assess(context,
                    planned.moved(0, base - planned.minY(), 0),
                    Site.EMBER_BRIDGE_V4, east, south);
            if (DIAGNOSTICS) COUNTS.get(Site.EMBER_BRIDGE_V4).get(reason).increment();
            if (reason == Reason.ACCEPT) return OptionalInt.of(base);
        }
        return OptionalInt.empty();
    }

    /** V5's gallery needs its own clear cavern and supported ground. */
    public static OptionalInt emberV5Base(Structure.GenerationContext context,
                                          BoundingBox planned,
                                          Direction east, Direction south) {
        for (int base : new int[]{56, 64, 48, 72, 40, 80, 32, 29}) {
            Reason reason = assess(context,
                    planned.moved(0, base - planned.minY(), 0),
                    Site.EMBER_BRIDGE_V5, east, south);
            if (DIAGNOSTICS) COUNTS.get(Site.EMBER_BRIDGE_V5).get(reason).increment();
            if (reason == Reason.ACCEPT) return OptionalInt.of(base);
        }
        return OptionalInt.empty();
    }

    /** V6 retains the full natural runway, using authored trusses and pads. */
    public static OptionalInt emberV6Base(Structure.GenerationContext context,
                                          BoundingBox planned,
                                          Direction east, Direction south) {
        if (!DimensionContactMask.valid() || !DimensionV6GalleryMask.valid()) {
            countV6(Reason.MASK_INVALID);
            return OptionalInt.empty();
        }
        List<V6Base> candidates = v6BaseCandidates(context, planned, east, south);
        if (candidates.isEmpty()) return OptionalInt.empty();
        BoundingBox preflightCore = core(planned, east, south, 24);
        int rootX = preflightCore.minX() + 23;
        int rootZ = preflightCore.minZ() + 23;
        boolean trace = V6_TRACE && nearV6Origin(rootX, rootZ);
        candidates.sort(Comparator.comparingInt(V6Base::score).reversed()
                .thenComparingInt(candidate -> Math.abs(candidate.base() - 56))
                .thenComparingInt(V6Base::base));
        final int exactCap = 16;
        for (int index = 0; index < Math.min(exactCap, candidates.size()); index++) {
            int base = candidates.get(index).base();
            Reason reason = assess(context,
                    planned.moved(0, base - planned.minY(), 0),
                    Site.EMBER_BRIDGE_V6, east, south);
            countV6(reason);
            if (trace) System.out.println("DIMENSION_V6_EXACT root=" + rootX + ","
                    + rootZ + " east=" + east + " south=" + south
                    + " rank=" + index + " base=" + base
                    + " score=" + candidates.get(index).score()
                    + " reason=" + reason);
            if (reason == Reason.ACCEPT) return OptionalInt.of(base);
        }
        if (candidates.size() > exactCap && DIAGNOSTICS) {
            COUNTS.get(Site.EMBER_BRIDGE_V6).get(Reason.NETHER_BASE_CAP)
                    .add(candidates.size() - exactCap);
        }
        // Diagnostic-only checks expose whether the bounded height cap hid a
        // viable nearby candidate. They never alter the selected start.
        if (trace) for (int index = exactCap; index < candidates.size(); index++) {
            V6Base omitted = candidates.get(index);
            Reason reason = assess(context,
                    planned.moved(0, omitted.base() - planned.minY(), 0),
                    Site.EMBER_BRIDGE_V6, east, south);
            System.out.println("DIMENSION_V6_CAP root=" + rootX + "," + rootZ
                    + " east=" + east + " south=" + south
                    + " rank=" + index + " base=" + omitted.base()
                    + " score=" + omitted.score() + " reason=" + reason);
        }
        return OptionalInt.empty();
    }

    private record V6Base(int base, int score) { }

    private static void countV6(Reason reason) {
        if (DIAGNOSTICS) COUNTS.get(Site.EMBER_BRIDGE_V6).get(reason).increment();
    }

    private static boolean nearV6Origin(int x, int z) {
        return (long) x * x + (long) z * z <= 2048L * 2048L;
    }

    /** Cache 36 pad and 15 far-runway columns once per orientation. */
    private static List<V6Base> v6BaseCandidates(
            Structure.GenerationContext context, BoundingBox body,
            Direction east, Direction south) {
        BoundingBox preflightCore = core(body, east, south, 24);
        int centerX = preflightCore.minX() + 23;
        int centerZ = preflightCore.minZ() + 23;
        List<Integer> biomeBases = new ArrayList<>();
        for (int base = 29; base <= 80; base++) {
            boolean validBiome = true;
            for (int localZ : new int[] {18, 23, 28}) {
                int x = world(centerX, centerZ, 39, localZ, east, south, true);
                int z = world(centerX, centerZ, 39, localZ, east, south, false);
                if (!biome(context, x, z, base + 5)) validBiome = false;
            }
            if (validBiome) biomeBases.add(base);
            else countV6(Reason.BIOME);
        }
        boolean trace = V6_TRACE && nearV6Origin(centerX, centerZ);
        if (biomeBases.isEmpty()) {
            if (trace) System.out.println("DIMENSION_V6_NEAR root=" + centerX
                    + "," + centerZ + " east=" + east + " south=" + south
                    + " biomeHeights=0 anchorHeights=0 exactEligible=0");
            return List.of();
        }
        NoiseColumn[][] pads = new NoiseColumn[4][9];
        int pad = 0;
        for (int cx : new int[] {6, 40}) {
            for (int cz : new int[] {14, 32}) {
                int cell = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int x = world(centerX, centerZ, cx + dx, cz + dz,
                                east, south, true);
                        int z = world(centerX, centerZ, cx + dx, cz + dz,
                                east, south, false);
                        pads[pad][cell++] = column(context, x, z);
                    }
                }
                pad++;
            }
        }
        NoiseColumn[] far = new NoiseColumn[15];
        for (int lx = 32; lx <= 46; lx++) {
            int x = world(centerX, centerZ, lx, 86, east, south, true);
            int z = world(centerX, centerZ, lx, 86, east, south, false);
            far[lx - 32] = column(context, x, z);
        }
        List<V6Base> candidates = new ArrayList<>();
        int anchorHeights = 0;
        for (int base : biomeBases) {
            int weakestPad = 9;
            int allContacts = 0;
            for (NoiseColumn[] group : pads) {
                int contacts = 0;
                for (NoiseColumn sample : group) {
                    if (support(sample, base - 1, 2)) contacts++;
                }
                weakestPad = Math.min(weakestPad, contacts);
                allContacts += contacts;
            }
            if (weakestPad < 3) {
                countV6(Reason.NETHER_ANCHOR);
                continue;
            }
            anchorHeights++;
            int farCells = 0;
            for (NoiseColumn sample : far) {
                for (int feet = base + 2; feet <= base + 8; feet++) {
                    if (drySolid(sample.getBlock(feet - 1))
                            && free(sample.getBlock(feet))
                            && free(sample.getBlock(feet + 1))) farCells++;
                }
            }
            if (farCells < 3) {
                countV6(Reason.NETHER_RUNWAY);
                continue;
            }
            candidates.add(new V6Base(base,
                    weakestPad * 10_000 + allContacts * 128 + farCells));
        }
        if (trace) System.out.println("DIMENSION_V6_NEAR root=" + centerX
                + "," + centerZ + " east=" + east + " south=" + south
                + " biomeHeights=" + biomeBases.size()
                + " anchorHeights=" + anchorHeights
                + " exactEligible=" + candidates.size());
        return candidates;
    }

    private static Reason assess(Structure.GenerationContext context,
                                 BoundingBox body, Site site,
                                 Direction east, Direction south) {
        boolean end = site == Site.END_SOURCE || site == Site.END_SOURCE_V2
                || site == Site.END_SOURCE_V3 || site == Site.END_SOURCE_V4;
        boolean extended = site == Site.ASH_ANNEX_V3 || site == Site.ASH_ANNEX_V4
                || site == Site.ASH_ANNEX_V5
                || site == Site.EMBER_BRIDGE_V3 || site == Site.EMBER_BRIDGE_V4;
        boolean gallery = site == Site.EMBER_BRIDGE_V5
                || site == Site.EMBER_BRIDGE_V6;
        int height = end ? 39 : 31;
        // Radius 26 foundation plus 12 island setback reaches 15 blocks
        // beyond the square template's 23-block half-width.
        int halo = site == Site.END_SOURCE ? 15
                : site == Site.EMBER_BRIDGE_V4 || gallery ? 16 : 8;
        if (!(gallery ? (body.getXSpan() == 47 && body.getZSpan() == 71
                        || body.getXSpan() == 71 && body.getZSpan() == 47)
                : extended ? (body.getXSpan() == 47 && body.getZSpan() == 55
                        || body.getXSpan() == 55 && body.getZSpan() == 47)
                : body.getXSpan() == 47 && body.getZSpan() == 47)
                || body.getYSpan() != height || east == null || south == null
                || east.getAxis() == Direction.Axis.Y
                || south.getAxis() == Direction.Axis.Y
                || east.getAxis() == south.getAxis()
                || (long) body.minX() - halo < -WORLD_LIMIT
                || (long) body.maxX() + halo > WORLD_LIMIT
                || (long) body.minZ() - halo < -WORLD_LIMIT
                || (long) body.maxZ() + halo > WORLD_LIMIT
                || body.minY() - 6 < context.heightAccessor().getMinY()
                || body.maxY() + 8 >= context.heightAccessor().getMaxY()) {
            return Reason.SHAPE_OR_BOUNDS;
        }
        return switch (site) {
            case END_SOURCE -> end(context, body, east, south);
            case END_SOURCE_V2 -> endV2(context, body, east, south);
            case END_SOURCE_V3 -> endV2(context, body, east, south);
            case END_SOURCE_V4 -> endV2(context, body, east, south);
            case ASH_ANNEX_V2 -> ashV2(context, body, east, south);
            case EMBER_BRIDGE_V2 -> emberV2(context, body, east, south);
            case ASH_ANNEX_V3 -> ashV3(context, body, east, south);
            case ASH_ANNEX_V4 -> ashV3(context, body, east, south);
            case ASH_ANNEX_V5 -> ashV3(context, body, east, south);
            case EMBER_BRIDGE_V3 -> emberV2(context,
                    core(body, east, south), east, south, site);
            case EMBER_BRIDGE_V4 -> emberV4(context,
                    core(body, east, south), east, south);
            case EMBER_BRIDGE_V5 -> emberV5(context,
                    core(body, east, south, 24), east, south);
            case EMBER_BRIDGE_V6 -> emberV6(context,
                    core(body, east, south, 24), east, south);
            default -> nether(context, body, site, east, south);
        };
    }

    private static BoundingBox core(BoundingBox body, Direction east, Direction south) {
        return core(body, east, south, 8);
    }

    private static BoundingBox core(BoundingBox body, Direction east, Direction south,
                                    int extension) {
        // Local root (23,4,23) stays fixed; the extension is only south of it.
        // Rotation can put that extension on any world edge.
        int x = body.minX() + (east.getStepX() != 0 ? 23 : 0)
                + (south.getStepX() > 0 ? 23 : south.getStepX() < 0
                        ? 23 + extension : 0);
        int z = body.minZ() + (east.getStepZ() != 0 ? 23 : 0)
                + (south.getStepZ() > 0 ? 23 : south.getStepZ() < 0
                        ? 23 + extension : 0);
        return new BoundingBox(x - 23, body.minY(), z - 23,
                x + 23, body.maxY(), z + 23);
    }

    private static Reason ashV2(Structure.GenerationContext context,
                                BoundingBox body, Direction east, Direction south) {
        return ashV2(context, body, east, south, Site.ASH_ANNEX_V2);
    }

    private static Reason ashV2(Structure.GenerationContext context,
                                BoundingBox body, Direction east, Direction south,
                                Site site) {
        if (!DimensionContactMask.valid()) return Reason.MASK_INVALID;
        int base = body.minY();
        int centerX = body.minX() + 23;
        int centerZ = body.minZ() + 23;
        for (int lx : new int[]{4, 12, 20, 28, 36, 44}) {
            for (int lz : new int[]{4, 12, 20, 28, 36, 44}) {
                int x = world(centerX, centerZ, lx, lz, east, south, true);
                int z = world(centerX, centerZ, lx, lz, east, south, false);
                if (!biome(context, x, z, base + 5)) return Reason.BIOME;
            }
        }
        // The loaded preflight covers all body columns. Use the same biome
        // contract before saving a start, after cheap sparse rejection.
        for (int x = body.minX(); x <= body.maxX(); x++) {
            for (int z = body.minZ(); z <= body.maxZ(); z++) {
                if (!biome(context, x, z, base + 5)) return Reason.BIOME;
            }
        }
        if (!clearance(context, body, east, south, DimensionContactMask.ash())) {
            return Reason.ASH_CLEARANCE;
        }
        if (!arrival(context, body, site, east, south)) {
            return Reason.NETHER_ARRIVAL;
        }
        int supported = 0;
        int[] quadrants = new int[4];
        for (int lx = 4; lx <= 40; lx += 4) {
            for (int lz = 4; lz <= 40; lz += 4) {
                int x = world(centerX, centerZ, lx, lz, east, south, true);
                int z = world(centerX, centerZ, lx, lz, east, south, false);
                if (support(column(context, x, z), base - 1, 4)) {
                    supported++;
                    quadrants[(lx < 23 ? 0 : 1) + (lz < 23 ? 0 : 2)]++;
                }
            }
        }
        if (supported < 70) return Reason.ASH_SUPPORT;
        for (int quadrant : quadrants) {
            if (quadrant < 12) return Reason.ASH_SUPPORT;
        }
        return Reason.ACCEPT;
    }

    private static Reason ashV3(Structure.GenerationContext context,
                                BoundingBox body, Direction east, Direction south) {
        Reason coreReason = ashV2(context, core(body, east, south), east, south,
                Site.ASH_ANNEX_V3);
        if (coreReason != Reason.ACCEPT) return coreReason;
        // The loaded site definition checks every authored v3 column, so a
        // biome seam in its eight-block extension must be rejected early too.
        for (int x = body.minX(); x <= body.maxX(); x++) {
            for (int z = body.minZ(); z <= body.maxZ(); z++) {
                if (!biome(context, x, z, body.minY() + 5)) return Reason.BIOME;
            }
        }
        return Reason.ACCEPT;
    }

    private static Reason emberV2(Structure.GenerationContext context,
                                  BoundingBox body, Direction east, Direction south) {
        return emberV2(context, body, east, south, Site.EMBER_BRIDGE_V2);
    }

    private static Reason emberV2(Structure.GenerationContext context,
                                  BoundingBox body, Direction east, Direction south,
                                  Site site) {
        if (!DimensionContactMask.valid()) return Reason.MASK_INVALID;
        int base = body.minY();
        int centerX = body.minX() + 23;
        int centerZ = body.minZ() + 23;
        // Keep the public east refuge in warped forest. Nether biome seams
        // may cross the sealed outer walls without invalidating the site.
        for (int lz : new int[]{18, 23, 28}) {
            int x = world(centerX, centerZ, 39, lz, east, south, true);
            int z = world(centerX, centerZ, 39, lz, east, south, false);
            if (!biome(context, x, z, base + 5)) return Reason.BIOME;
        }
        if (!clearance(context, body, east, south, DimensionContactMask.ember())) {
            return Reason.EMBER_CLEARANCE;
        }
        if (site != Site.EMBER_BRIDGE_V5
                && !arrival(context, body, site, east, south)) {
            return Reason.NETHER_ARRIVAL;
        }
        int basinSupported = 0;
        for (int lx = 15; lx <= 31; lx += 4) {
            for (int lz = 15; lz <= 31; lz += 4) {
                int x = world(centerX, centerZ, lx, lz, east, south, true);
                int z = world(centerX, centerZ, lx, lz, east, south, false);
                if (support(column(context, x, z), base - 1, 4)) {
                    basinSupported++;
                }
            }
        }
        if (basinSupported < 18) return Reason.EMBER_BANKS;
        int[] bankSupported = new int[2];
        for (int lx = 2; lx <= 44; lx++) {
            if (lx % 2 != 0) continue;
            int bank = lx <= 12 ? 0 : lx >= 34 ? 1 : -1;
            if (bank < 0) continue;
            for (int lz = 12; lz <= 34; lz += 2) {
                int x = world(centerX, centerZ, lx, lz, east, south, true);
                int z = world(centerX, centerZ, lx, lz, east, south, false);
                if (support(column(context, x, z), base - 1, 4)) {
                    bankSupported[bank]++;
                }
            }
        }
        if (bankSupported[0] < 25 || bankSupported[1] < 25) {
            return Reason.EMBER_BANKS;
        }
        for (int lx : new int[]{6, 8, 38, 40}) {
            int x = world(centerX, centerZ, lx, 23, east, south, true);
            int z = world(centerX, centerZ, lx, 23, east, south, false);
            if (!support(column(context, x, z), base - 1, 4)) {
                return Reason.EMBER_BANKS;
            }
        }
        return Reason.ACCEPT;
    }

    private static Reason emberV4(Structure.GenerationContext context,
                                  BoundingBox core,
                                  Direction east, Direction south) {
        Reason base = emberV2(context, core, east, south, Site.EMBER_BRIDGE_V4);
        if (base != Reason.ACCEPT) return base;
        return outerRunway(context, core, east, south)
                ? Reason.ACCEPT : Reason.NETHER_RUNWAY;
    }

    private static Reason emberV5(Structure.GenerationContext context,
                                  BoundingBox core,
                                  Direction east, Direction south) {
        Reason base = emberV2(context, core, east, south, Site.EMBER_BRIDGE_V5);
        if (base != Reason.ACCEPT) return base;
        if (!galleryClearAndSupported(context, core, east, south)) {
            return Reason.NETHER_GALLERY;
        }
        return outerRunway(context, core, east, south, 71, 86)
                ? Reason.ACCEPT : Reason.NETHER_RUNWAY;
    }

    private static Reason emberV6(Structure.GenerationContext context,
                                  BoundingBox core,
                                  Direction east, Direction south) {
        if (!DimensionContactMask.valid() || !DimensionV6GalleryMask.valid()) {
            return Reason.MASK_INVALID;
        }
        int base = core.minY();
        int centerX = core.minX() + 23;
        int centerZ = core.minZ() + 23;
        // Keep the public refuge inside warped forest while permitting a
        // harmless biome seam along the sealed crucible's outer shell.
        for (int lz : new int[] {18, 23, 28}) {
            int x = world(centerX, centerZ, 39, lz, east, south, true);
            int z = world(centerX, centerZ, 39, lz, east, south, false);
            if (!biome(context, x, z, base + 5)) return Reason.BIOME;
        }
        if (!clearance(context, core, east, south,
                DimensionContactMask.ember())) return Reason.EMBER_CLEARANCE;
        if (!v6PadsHaveNoiseContact(context, core, east, south)) {
            return Reason.NETHER_ANCHOR;
        }
        if (!outerRunway(context, core, east, south, 71, 86)) {
            return Reason.NETHER_RUNWAY;
        }
        return v6GalleryFits(context, core, east, south)
                ? Reason.ACCEPT : Reason.NETHER_GALLERY;
    }

    /** Four distributed 3x3 contacts replace the broad v2/v5 bank demand. */
    private static boolean v6PadsHaveNoiseContact(
            Structure.GenerationContext context, BoundingBox core,
            Direction east, Direction south) {
        int centerX = core.minX() + 23;
        int centerZ = core.minZ() + 23;
        for (int cx : new int[] {6, 40}) {
            for (int cz : new int[] {14, 32}) {
                int contacts = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int x = world(centerX, centerZ, cx + dx, cz + dz,
                                east, south, true);
                        int z = world(centerX, centerZ, cx + dx, cz + dz,
                                east, south, false);
                        if (support(column(context, x, z), core.minY() - 1, 2)) {
                            contacts++;
                        }
                    }
                }
                if (contacts < 3) return false;
            }
        }
        return true;
    }

    /** Exact generated mask prevents fluid contact and caps dry rock fitting. */
    private static boolean v6GalleryFits(Structure.GenerationContext context,
                                         BoundingBox core, Direction east,
                                         Direction south) {
        int centerX = core.minX() + 23;
        int centerZ = core.minZ() + 23;
        int rockReplaced = 0;
        for (DimensionV6GalleryMask.Column contact
                : DimensionV6GalleryMask.columns()) {
            int x = world(centerX, centerZ, contact.x(), contact.z(),
                    east, south, true);
            int z = world(centerX, centerZ, contact.x(), contact.z(),
                    east, south, false);
            NoiseColumn ground = column(context, x, z);
            int air = contact.airBits();
            while (air != 0) {
                int y = Integer.numberOfTrailingZeros(air);
                if (!free(ground.getBlock(core.minY() + y))) return false;
                air &= air - 1;
            }
            int solid = contact.solidBits();
            while (solid != 0) {
                int y = Integer.numberOfTrailingZeros(solid);
                BlockState existing = ground.getBlock(core.minY() + y);
                if (!existing.isAir()) {
                    if (!drySolid(existing) || !v6DryRock(existing)
                            || ++rockReplaced > DimensionV6GalleryMask.rockCap()) {
                        return false;
                    }
                }
                solid &= solid - 1;
            }
        }
        return true;
    }

    private static boolean v6DryRock(BlockState state) {
        return state.is(Blocks.NETHERRACK) || state.is(Blocks.WARPED_NYLIUM)
                || state.is(Blocks.BLACKSTONE) || state.is(Blocks.BASALT)
                || state.is(Blocks.SMOOTH_BASALT)
                || state.is(Blocks.NETHER_QUARTZ_ORE)
                || state.is(Blocks.NETHER_GOLD_ORE)
                || state.is(Blocks.SOUL_SOIL);
    }

    /** No visible rock is replaced; the buried plinth sits on two dry layers. */
    private static boolean galleryClearAndSupported(Structure.GenerationContext context,
                                                    BoundingBox core,
                                                    Direction east, Direction south) {
        int base = core.minY();
        int centerX = core.minX() + 23;
        int centerZ = core.minZ() + 23;
        for (int localZ = 55; localZ <= 70; localZ++) {
            for (int localX = 32; localX <= 46; localX++) {
                int x = world(centerX, centerZ, localX, localZ, east, south, true);
                int z = world(centerX, centerZ, localX, localZ, east, south, false);
                NoiseColumn ground = column(context, x, z);
                if (!support(ground, base - 1, 2)) return false;
                for (int y = 5; y <= 21; y++) {
                    if (!free(ground.getBlock(base + y))) return false;
                }
            }
        }
        return true;
    }

    /** Read-only base-noise mirror of the loaded 15-wide, one-step walk. */
    private static boolean outerRunway(Structure.GenerationContext context,
                                       BoundingBox core,
                                       Direction east, Direction south) {
        return outerRunway(context, core, east, south, 55, 70);
    }

    private static boolean outerRunway(Structure.GenerationContext context,
                                       BoundingBox core,
                                       Direction east, Direction south,
                                       int firstZ, int lastZ) {
        final int width = 15;
        final int depth = 16; // Natural 16-block runway beyond the authored deck.
        final int heights = 7;
        int base = core.minY();
        int centerX = core.minX() + 23;
        int centerZ = core.minZ() + 23;
        boolean[] walkable = new boolean[width * depth * heights];
        // Reject a cliff cheaply before collecting the whole path.
        int farCells = 0;
        for (int lx = 32; lx <= 46; lx++) {
            int x = world(centerX, centerZ, lx, lastZ, east, south, true);
            int z = world(centerX, centerZ, lx, lastZ, east, south, false);
            NoiseColumn ground = column(context, x, z);
            for (int feet = base + 2; feet <= base + 8; feet++) {
                if (drySolid(ground.getBlock(feet - 1))
                        && free(ground.getBlock(feet))
                        && free(ground.getBlock(feet + 1))) farCells++;
            }
        }
        if (farCells < 3) return false;
        for (int localZ = firstZ; localZ <= lastZ; localZ++) {
            for (int localX = 32; localX <= 46; localX++) {
                int x = world(centerX, centerZ, localX, localZ, east, south, true);
                int z = world(centerX, centerZ, localX, localZ, east, south, false);
                NoiseColumn ground = column(context, x, z);
                for (int feet = base + 2; feet <= base + 8; feet++) {
                    int index = ((localZ - firstZ) * width + localX - 32)
                            * heights + feet - base - 2;
                    walkable[index] = drySolid(ground.getBlock(feet - 1))
                            && free(ground.getBlock(feet))
                            && free(ground.getBlock(feet + 1));
                }
            }
        }
        boolean[] seen = new boolean[walkable.length];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int localX = 37; localX <= 41; localX++) {
            for (int y = 2; y <= 4; y++) {
                int index = (localX - 32) * heights + y;
                if (walkable[index]) {
                    seen[index] = true;
                    queue.add(index);
                }
            }
        }
        int farReached = 0;
        while (!queue.isEmpty()) {
            int index = queue.removeFirst();
            int y = index % heights;
            int flat = index / heights;
            int x = flat % width;
            int z = flat / width;
            if (z == depth - 1 && ++farReached >= 3) return true;
            for (int direction = 0; direction < 4; direction++) {
                int nx = x + (direction == 0 ? 1 : direction == 1 ? -1 : 0);
                int nz = z + (direction == 2 ? 1 : direction == 3 ? -1 : 0);
                if (nx < 0 || nx >= width || nz < 0 || nz >= depth) continue;
                for (int dy = -1; dy <= 1; dy++) {
                    int ny = y + dy;
                    if (ny < 0 || ny >= heights) continue;
                    int next = (nz * width + nx) * heights + ny;
                    if (walkable[next] && !seen[next]) {
                        seen[next] = true;
                        queue.addLast(next);
                    }
                }
            }
        }
        return false;
    }

    private static boolean clearance(Structure.GenerationContext context,
                                     BoundingBox body, Direction east, Direction south,
                                     java.util.List<DimensionContactMask.Column> mask) {
        int centerX = body.minX() + 23;
        int centerZ = body.minZ() + 23;
        for (DimensionContactMask.Column contact : mask) {
            int x = world(centerX, centerZ, contact.x(), contact.z(),
                    east, south, true);
            int z = world(centerX, centerZ, contact.x(), contact.z(),
                    east, south, false);
            NoiseColumn column = column(context, x, z);
            int bits = contact.airBits();
            while (bits != 0) {
                int y = Integer.numberOfTrailingZeros(bits);
                if (!free(column.getBlock(body.minY() + y))) return false;
                bits &= bits - 1;
            }
        }
        return true;
    }

    private static Reason nether(Structure.GenerationContext context,
                                 BoundingBox body, Site site,
                                 Direction east, Direction south) {
        int base = body.minY();
        int centerX = body.minX() + 23;
        int centerZ = body.minZ() + 23;
        int ashSupported = 0;
        int[] ashQuadrants = new int[4];
        int[] bankSupported = new int[2];
        int[] bankRaised = new int[2];
        // Noise columns are expensive in 26.3. Finish the cheap biome survey,
        // then reject obvious blocked sites with a bounded set of probes
        // before checking every authored footprint column exactly.
        for (int x = body.minX(); x <= body.maxX(); x++) {
            for (int z = body.minZ(); z <= body.maxZ(); z++) {
                if (!biome(context, x, z, base + 5)) return Reason.BIOME;
            }
        }
        int[] probes = {4, 12, 20, 28, 36, 44};
        for (int lx : probes) {
            for (int lz : probes) {
                int x = world(centerX, centerZ, lx, lz, east, south, true);
                int z = world(centerX, centerZ, lx, lz, east, south, false);
                NoiseColumn column = column(context, x, z);
                if (footprint(site, lx, lz)
                        && (!free(column.getBlock(base + 5))
                        || !free(column.getBlock(base + 16))
                        || !free(column.getBlock(body.maxY() + 2)))) {
                    return site == Site.ASH_ANNEX
                            ? Reason.ASH_CLEARANCE : Reason.EMBER_CLEARANCE;
                }
            }
        }
        if (site == Site.EMBER_BRIDGE) {
            for (int lx : new int[]{17, 23, 29}) {
                for (int lz : new int[]{16, 23, 30}) {
                    int x = world(centerX, centerZ, lx, lz, east, south, true);
                    int z = world(centerX, centerZ, lx, lz, east, south, false);
                    NoiseColumn column = column(context, x, z);
                    if (!naturalGorge(column, base)) return Reason.EMBER_LAVA;
                }
            }
        }
        if (!arrival(context, body, site, east, south)) {
            return Reason.NETHER_ARRIVAL;
        }
        if (DIAGNOSTICS) {
            System.out.println("DIMENSION_SITE_EXACT site=" + site
                    + " center=" + centerX + "," + centerZ);
        }
        // The authored plinth bridges bounded pockets. Ember's actual lava
        // span cannot also require dry ground under the entire 47x47 square.
        for (int x = body.minX(); x <= body.maxX(); x++) {
            for (int z = body.minZ(); z <= body.maxZ(); z++) {
                int lx = local(x, z, centerX, centerZ, east);
                int lz = local(x, z, centerX, centerZ, south);
                if (!footprint(site, lx, lz)) continue;
                NoiseColumn column = column(context, x, z);
                if (!free(column.getBlock(base + 5))
                        || !free(column.getBlock(base + 16))
                        || !free(column.getBlock(body.maxY() + 2))) {
                    return site == Site.ASH_ANNEX
                            ? Reason.ASH_CLEARANCE : Reason.EMBER_CLEARANCE;
                }
                if (site == Site.ASH_ANNEX) {
                    if (lx >= 4 && lx <= 40 && lz >= 4 && lz <= 40
                            && lx % 4 == 0 && lz % 4 == 0
                            && support(column, base - 1, 4)) {
                        ashSupported++;
                        ashQuadrants[(lx < 23 ? 0 : 1) + (lz < 23 ? 0 : 2)]++;
                    }
                } else {
                    if (lx >= 17 && lx <= 29 && lz >= 16 && lz <= 30) {
                        if (!naturalGorge(column, base)) return Reason.EMBER_LAVA;
                    }
                    int bank = lx >= 2 && lx <= 12 ? 0
                            : lx >= 34 && lx <= 44 ? 1 : -1;
                    if (bank >= 0 && lz >= 12 && lz <= 34) {
                        if (lx % 2 == 0 && lz % 2 == 0
                                && support(column, base - 1, 4)) {
                            bankSupported[bank]++;
                        }
                        if (lx >= (bank == 0 ? 5 : 37)
                                && lx <= (bank == 0 ? 10 : 42)
                                && lz >= 20 && lz <= 26
                                && drySolid(column.getBlock(base + 4))) {
                            bankRaised[bank]++;
                        }
                    }
                }
            }
        }
        if (site == Site.ASH_ANNEX) {
            // 100 distributed probes, at least half in each quadrant.
            if (ashSupported < 70) return Reason.ASH_SUPPORT;
            for (int supported : ashQuadrants) {
                if (supported < 12) return Reason.ASH_SUPPORT;
            }
        } else if (bankSupported[0] < 30 || bankSupported[1] < 30
                || bankRaised[0] < 21 || bankRaised[1] < 21) {
            return Reason.EMBER_BANKS;
        }
        return Reason.ACCEPT;
    }

    private static Reason end(Structure.GenerationContext context,
                              BoundingBox body, Direction east, Direction south) {
        long dx = nearestZero((long) body.minX() - 15,
                (long) body.maxX() + 15);
        long dz = nearestZero((long) body.minZ() - 15,
                (long) body.maxZ() + 15);
        if (Math.max(dx, dz) < 1152) return Reason.END_DISTANCE;
        int base = body.minY();
        int centerX = body.minX() + 23;
        int centerZ = body.minZ() + 23;
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        if (!arrival(context, body, Site.END_SOURCE, east, south)) {
            return Reason.END_ARRIVAL;
        }
        for (int lx : new int[]{-9, 7, 23, 39, 55}) {
            for (int lz : new int[]{-9, 7, 23, 39, 55}) {
                int radius2 = (lx - 23) * (lx - 23) + (lz - 23) * (lz - 23);
                if (radius2 > 38 * 38) continue;
                int x = world(centerX, centerZ, lx, lz, east, south, true);
                int z = world(centerX, centerZ, lx, lz, east, south, false);
                if (!biome(context, x, z, base + 5)) return Reason.BIOME;
                int ground = context.chunkGenerator().getBaseHeight(x, z,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        context.heightAccessor(), context.randomState()) - 1;
                NoiseColumn column = column(context, x, z);
                if (radius2 <= 26 * 26) {
                    if (ground < base - 1 || ground > base + 2) {
                        return Reason.END_SURFACE;
                    }
                    if (!support(column, base - 1, 6)) return Reason.END_SUPPORT;
                    if (!free(column.getBlock(base + 5))) return Reason.END_CLEARANCE;
                } else if (ground < base - 5 || ground > base + 4
                        || !support(column, ground, 4)) {
                    return Reason.END_SUPPORT;
                }
            }
        }
        if (DIAGNOSTICS) {
            System.out.println("DIMENSION_SITE_EXACT site=END_SOURCE center="
                    + centerX + "," + centerZ);
        }
        // The NBT foundation fits radius 26, with a 12-block island setback.
        // Empty corners of its bounding square do not need land beneath them.
        for (int x = centerX - 38; x <= centerX + 38; x++) {
            for (int z = centerZ - 38; z <= centerZ + 38; z++) {
                int rx = x - centerX;
                int rz = z - centerZ;
                int radius2 = rx * rx + rz * rz;
                if (radius2 > 38 * 38) continue;
                if (!biome(context, x, z, base + 5)) return Reason.BIOME;
                int ground = context.chunkGenerator().getBaseHeight(x, z,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        context.heightAccessor(), context.randomState()) - 1;
                NoiseColumn column = column(context, x, z);
                if (radius2 <= 26 * 26) {
                    int lx = local(x, z, centerX, centerZ, east);
                    int lz = local(x, z, centerX, centerZ, south);
                    // The Y4 north threshold needs one higher natural step
                    // immediately outside the authored foundation.
                    boolean entranceApron = lx >= 20 && lx <= 26
                            && lz >= -2 && lz <= 0;
                    if (ground < base - 1
                            || ground > base + (entranceApron ? 3 : 2)) {
                        return Reason.END_SURFACE;
                    }
                    if (!support(column, base - 1, 6)) return Reason.END_SUPPORT;
                    if (!free(column.getBlock(base + 5))) return Reason.END_CLEARANCE;
                    if (!entranceApron) {
                        low = Math.min(low, ground);
                        high = Math.max(high, ground);
                        if (high - low > 3) return Reason.END_RELIEF;
                    }
                } else {
                    if (ground < base - 5 || ground > base + 4) {
                        return Reason.END_SURFACE;
                    }
                    if (!support(column, ground, 4)) return Reason.END_SUPPORT;
                }
            }
        }
        return Reason.ACCEPT;
    }

    private static Reason endV2(Structure.GenerationContext context,
                                BoundingBox body, Direction east, Direction south) {
        // The v2 authored radius-22 plinth and continuous rail protect every
        // public edge. It may project over an island shoulder, but needs a
        // real central anchor and a natural north walk-in. Do not perform the
        // v1 4,500-column flat-island scan on the server's locate tick.
        long dx = nearestZero((long) body.minX() - 8,
                (long) body.maxX() + 8);
        long dz = nearestZero((long) body.minZ() - 8,
                (long) body.maxZ() + 8);
        if (Math.max(dx, dz) < 1152) return Reason.END_DISTANCE;
        int base = body.minY();
        int centerX = body.minX() + 23;
        int centerZ = body.minZ() + 23;
        for (int lx : new int[]{19, 23, 27}) {
            for (int lz : new int[]{19, 23, 27}) {
                int x = world(centerX, centerZ, lx, lz, east, south, true);
                int z = world(centerX, centerZ, lx, lz, east, south, false);
                if (!biome(context, x, z, base + 5)) return Reason.BIOME;
                NoiseColumn column = column(context, x, z);
                if (!support(column, base - 1, 4)) return Reason.END_SUPPORT;
                if (!free(column.getBlock(base + 5))) return Reason.END_CLEARANCE;
            }
        }
        if (!arrival(context, body, Site.END_SOURCE_V2, east, south)) {
            return Reason.END_ARRIVAL;
        }
        return Reason.ACCEPT;
    }

    private static boolean arrival(Structure.GenerationContext context,
                                   BoundingBox body, Site site,
                                   Direction east, Direction south) {
        int base = body.minY();
        int centerX = body.minX() + 23;
        int centerZ = body.minZ() + 23;
        int centerLocalX = site == Site.EMBER_BRIDGE
                || site == Site.EMBER_BRIDGE_V2
                || site == Site.EMBER_BRIDGE_V3
                || site == Site.EMBER_BRIDGE_V4 ? 39 : 23;
        boolean end = site == Site.END_SOURCE || site == Site.END_SOURCE_V2
                || site == Site.END_SOURCE_V3 || site == Site.END_SOURCE_V4;
        boolean extended = site == Site.ASH_ANNEX_V3 || site == Site.ASH_ANNEX_V5
                || site == Site.EMBER_BRIDGE_V3 || site == Site.EMBER_BRIDGE_V4;
        int fromZ = end ? -8 : extended ? 55 : 47;
        // V2 End Z0 is an authored lower threshold. The natural path must
        // join it from Z-1 within one step; natural Z0 is overwritten.
        int toZ = site == Site.END_SOURCE_V2 || site == Site.END_SOURCE_V3
                || site == Site.END_SOURCE_V4
                ? -1 : end ? 0 : extended ? 62 : 54;
        int minFeet = end ? base - 4 : base + 2;
        int maxFeet = base + 8;
        boolean[][] previous = new boolean[7][maxFeet - minFeet + 1];
        for (int lz = fromZ; lz <= toZ; lz++) {
            boolean[][] current = new boolean[7][maxFeet - minFeet + 1];
            for (int offset = -3; offset <= 3; offset++) {
                int lx = centerLocalX + offset;
                int x = centerX + (lx - 23) * east.getStepX()
                        + (lz - 23) * south.getStepX();
                int z = centerZ + (lx - 23) * east.getStepZ()
                        + (lz - 23) * south.getStepZ();
                if (site != Site.EMBER_BRIDGE_V2
                        && site != Site.EMBER_BRIDGE_V3
                        && site != Site.EMBER_BRIDGE_V4
                        && !biome(context, x, z, base + 5)) continue;
                NoiseColumn column = column(context, x, z);
                for (int feet = minFeet; feet <= maxFeet; feet++) {
                    if (!drySolid(column.getBlock(feet - 1))
                            || !free(column.getBlock(feet))
                            || !free(column.getBlock(feet + 1))) continue;
                    int yIndex = feet - minFeet;
                    if (lz == fromZ) {
                        // Nether meets the authored Y4 deck. End may rise
                        // through the outer island apron toward its plinth.
                        current[offset + 3][yIndex] = end
                                || Math.abs(feet - (base + 5)) <= 1;
                    } else {
                        for (int px = Math.max(0, offset + 2);
                                px <= Math.min(6, offset + 4); px++) {
                            for (int py = Math.max(0, yIndex - 1);
                                    py <= Math.min(previous[0].length - 1, yIndex + 1);
                                    py++) {
                                if (previous[px][py]) current[offset + 3][yIndex] = true;
                            }
                        }
                    }
                }
            }
            previous = current;
        }
        for (int offsetIndex = 0; offsetIndex < previous.length; offsetIndex++) {
            if ((site == Site.END_SOURCE_V2 || site == Site.END_SOURCE_V3
                    || site == Site.END_SOURCE_V4)
                    && (offsetIndex < 1 || offsetIndex > 5)) continue;
            boolean[] row = previous[offsetIndex];
            for (int yIndex = 0; yIndex < row.length; yIndex++) {
                if (!row[yIndex]) continue;
                if (site == Site.END_SOURCE_V2 || site == Site.END_SOURCE_V3
                        || site == Site.END_SOURCE_V4) {
                    if (Math.abs(minFeet + yIndex - (base + 4)) <= 1) return true;
                } else if (!end || minFeet + yIndex >= base + 4) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int local(int x, int z, int centerX, int centerZ,
                             Direction axis) {
        return 23 + (x - centerX) * axis.getStepX()
                + (z - centerZ) * axis.getStepZ();
    }

    private static int world(int centerX, int centerZ, int lx, int lz,
                             Direction east, Direction south, boolean x) {
        return (x ? centerX : centerZ)
                + (lx - 23) * (x ? east.getStepX() : east.getStepZ())
                + (lz - 23) * (x ? south.getStepX() : south.getStepZ());
    }

    private static boolean naturalGorge(NoiseColumn column, int base) {
        // Vanilla 26.3 Nether sea_level is 32: natural lava fills Y30-31,
        // and the authored cap completes the open Y32 layer beneath the deck.
        return column.getBlock(base + 1).is(Blocks.LAVA)
                && column.getBlock(base + 2).is(Blocks.LAVA)
                && free(column.getBlock(base + 3));
    }

    private static boolean footprint(Site site, int x, int z) {
        return site == Site.ASH_ANNEX
                ? (x >= 2 && x <= 44 && z >= 2 && z <= 44)
                    || (x >= 19 && x <= 27 && z >= 45 && z <= 46)
                : (x >= 2 && x <= 44 && z >= 4 && z <= 42)
                    || (x >= 35 && x <= 43 && z >= 43 && z <= 46);
    }

    private static NoiseColumn column(Structure.GenerationContext context,
                                      int x, int z) {
        return context.chunkGenerator().getBaseColumn(x, z,
                context.heightAccessor(), context.randomState());
    }

    private static boolean biome(Structure.GenerationContext context,
                                 int x, int z, int y) {
        Holder<Biome> biome = context.biomeResolver().getNoiseBiome(x >> 2,
                y >> 2, z >> 2);
        return context.validBiome().test(biome);
    }

    private static boolean support(NoiseColumn column, int top, int depth) {
        for (int i = 0; i < depth; i++) {
            if (!drySolid(column.getBlock(top - i))) return false;
        }
        return true;
    }

    private static long nearestZero(long min, long max) {
        if (min > 0) return min;
        if (max < 0) return -max;
        return 0;
    }

    private static boolean drySolid(BlockState state) {
        return state.isSolid() && state.getFluidState().isEmpty();
    }

    private static boolean free(BlockState state) {
        return state.isAir() && state.getFluidState().isEmpty();
    }
}
