package com.feel.gems.adventure.estate;

import com.feel.gems.GemsMod;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.block.Mirror;

/** Noise-only reservation of 25 dry decks, anchored piers and four approaches. */
final class EstateTerrainPlan {
    private static final int SAMPLE_STEP = 4;
    private static final int APPROACH = 8;
    private static final boolean TRACE_STAGES = Boolean.getBoolean(
            "gems.estateCohortDedicated") || Boolean.getBoolean(
            "gems.fourRoadsEstatePlannerStats");
    // A dry hollow a few blocks below sea level is usable; deeper canyon
    // floors are excluded even when their local slope is otherwise legal.
    private static final int MIN_SURFACE = 55;
    // The visible outside edge may stand above a hollow only within the
    // existing 32-block support envelope. Interior non-load spans are held
    // by twelve-block beams between independently anchored piers.
    private static final int MAX_EXTERIOR_DROP = EstateRaisedGeometry.MAX_SUPPORT_DEPTH;
    private static final int MAX_BEAM_DROP = EstateRaisedGeometry.MAX_SUPPORT_DEPTH
            + EstateRaisedGeometry.MAX_SEAM_RISE + 4;
    private static final int MAX_SURFACE = 145;
    private static final int MAX_CUT = EstateRaisedGeometry.MAX_CUT;
    private static final int MAX_SEAM_RISE = EstateRaisedGeometry.MAX_SEAM_RISE;
    private static final ResourceKey<Biome> SUBSTRATE = ResourceKey.create(
            Registries.BIOME, Identifier.fromNamespaceAndPath(GemsMod.MOD_ID,
                    "estate_commons"));

    record Gate(PoolElementStructurePiece piece, BlockPos marker, Direction outward,
                int distance) {
        BlockPos at(int step, int lane) {
            return marker.relative(outward, step)
                    .relative(outward.getClockWise(), lane);
        }
    }

    record Plan(int[] bases) {
        int baseY(int id) { return bases[id]; }
    }

    private record SurfaceColumn(int y, EstateNoiseSurvey.Column column) { }

    private enum Stage {
        VALIDATION, NATURAL_APPROACH, COARSE_BODY, FOOTING_INTERVAL,
        EXACT_BODY, HEIGHT_TREE, SELECTED_FOOTINGS, BEAMS, ARRIVALS, SEAMS
    }

    /** Allocated only by opt-in QA; production planning has no counters. */
    private static final class StageCounts {
        private final long[] nanos = new long[Stage.values().length];
        private final int[] columns = new int[Stage.values().length];
        private Stage stage = Stage.VALIDATION;
        private long began = System.nanoTime();
        private final long started = began;
        private int exteriorColumns;
        private FourRoadsEstateStructure.Rejection result;

        void enter(Stage next) {
            long now = System.nanoTime();
            nanos[stage.ordinal()] += now - began;
            stage = next;
            began = now;
        }

        void column() { columns[stage.ordinal()]++; }

        void exterior() { exteriorColumns++; }

        void finish(Structure.GenerationContext context) {
            long now = System.nanoTime();
            nanos[stage.ordinal()] += now - began;
            StringBuilder summary = new StringBuilder();
            for (Stage item : Stage.values()) {
                if (summary.length() > 0) summary.append(' ');
                summary.append(item).append('=').append(columns[item.ordinal()])
                        .append('/').append(nanos[item.ordinal()] / 1_000_000);
            }
            System.out.println("FOUR_ROADS_ESTATE_PLANNER_STAGES chunk="
                    + context.chunkPos() + " result=" + result + " last=" + stage
                    + " exteriorColumns=" + exteriorColumns + " count/ms=" + summary
                    + " totalMs=" + (now - started) / 1_000_000);
        }
    }

    private EstateTerrainPlan() { }

    static Optional<Plan> survey(Structure.GenerationContext context,
                                 List<StructurePiece> pieces, BoundingBox bounds,
                                 Consumer<FourRoadsEstateStructure.Rejection> reject) {
        StageCounts counts = TRACE_STAGES ? new StageCounts() : null;
        Consumer<FourRoadsEstateStructure.Rejection> observed = reason -> {
            if (counts != null) counts.result = reason;
            if (reject != null) reject.accept(reason);
        };
        try {
            return surveyChecked(context, pieces, bounds, observed, counts);
        } finally {
            if (counts != null) counts.finish(context);
        }
    }

    private static Optional<Plan> surveyChecked(Structure.GenerationContext context,
                                 List<StructurePiece> pieces, BoundingBox bounds,
                                 Consumer<FourRoadsEstateStructure.Rejection> reject,
                                 StageCounts counts) {
        EstateModuleGraph.Module[] modules = EstateModuleGraph.modules(pieces);
        List<EstateModuleGraph.Seam> seams = EstateModuleGraph.seams(modules);
        List<Gate> gates = gates(pieces);
        if (modules == null || seams.size() != 24 || gates.size() != 4
                || bounds.minX() < -29_999_880
                || bounds.maxX() > 29_999_880 || bounds.minZ() < -29_999_880
                || bounds.maxZ() > 29_999_880) {
            return fail(reject, FourRoadsEstateStructure.Rejection.HEIGHT_BOUNDS);
        }
        BlockPos center = bounds.getCenter();
        int centerSurface = surface(context, center);
        if (centerSurface < MIN_SURFACE || centerSurface > MAX_SURFACE
                || !estateBiome(context, center.getX(), centerSurface, center.getZ())) {
            return fail(reject, FourRoadsEstateStructure.Rejection.BIOME);
        }
        if (counts != null) counts.enter(Stage.NATURAL_APPROACH);
        for (Gate gate : gates) {
            if (!naturalApproach(context, gate, counts)) {
                return failAt(context, reject, FourRoadsEstateStructure.Rejection.SLOPE,
                        "natural approach", gate.marker().getX(), gate.marker().getY(),
                        gate.marker().getZ());
            }
        }
        EstateNoiseSurvey noise = new EstateNoiseSurvey(context);
        int[] lower = new int[25];
        int[] upper = new int[25];
        for (EstateModuleGraph.Module module : modules) {
            BoundingBox box = module.piece().getBoundingBox();
            int floorMin = context.heightAccessor().getMinY() + 32;
            // Saved v1/v3 modules and new v4 axial halls have different
            // heights. Reserve the actual placed piece, including its roof.
            int floorMax = context.heightAccessor().getMaxY() - box.getYSpan();
            if (counts != null) counts.enter(Stage.COARSE_BODY);
            for (int x = box.minX(); x <= box.maxX(); x = next(x, box.maxX())) {
                for (int z = box.minZ(); z <= box.maxZ(); z = next(z, box.maxZ())) {
                    SurfaceColumn sample = sampleColumn(noise, x, z);
                    if (counts != null) counts.column();
                    int y = sample.y();
                    if (y < context.heightAccessor().getMinY() || y > MAX_SURFACE) {
                        return failAt(context, reject,
                                FourRoadsEstateStructure.Rejection.ELEVATION,
                                "module=" + module.id(), x, y, z);
                    }
                    var top = sample.column().getBlock(y);
                    boolean wet = top.is(Blocks.WATER);
                    if (!wet && (!top.isSolid() || !top.getFluidState().isEmpty()))
                        return failAt(context, reject,
                                FourRoadsEstateStructure.Rejection.SOLID_SUPPORT,
                                "module=" + module.id() + " surface", x, y, z);
                    floorMin = Math.max(floorMin, wet ? y - 2 : y - MAX_CUT);
                    if (onExterior(bounds, x, z)) {
                        floorMax = Math.min(floorMax, y + MAX_EXTERIOR_DROP);
                        if (counts != null) counts.exterior();
                    }
                    if (floorMin > floorMax)
                        return failAt(context, reject, FourRoadsEstateStructure.Rejection.SLOPE,
                                "module=" + module.id() + " deck interval",
                                x, y, z);
                    if (z == box.maxZ()) break;
                }
                if (x == box.maxX()) break;
            }
            int id = module.id();
            int[] interval = {floorMin, floorMax};
            if (counts != null) counts.enter(Stage.FOOTING_INTERVAL);
            if (!footingInterval(noise, box, interval, counts))
                return failAt(context, reject,
                        FourRoadsEstateStructure.Rejection.SOLID_SUPPORT,
                        "module=" + id + " footing interval",
                        box.minX(), interval[0], box.minZ());
            lower[id] = interval[0];
            upper[id] = interval[1];
            for (Gate gate : gates) {
                if (gate.piece() != module.piece()) continue;
                int edgeY = surface(context, gate.at(gate.distance() + 1, 0));
                lower[id] = Math.max(lower[id], edgeY - 4 - gate.distance());
                upper[id] = Math.min(upper[id], edgeY - 4 + gate.distance());
            }
            // The early four-block sample is only a rejection preflight.
            // Refine the legal interval with every body column before the
            // 24-seam tree chooses heights. Every surface still obeys the
            // 12-cut/roof clearance; only actual loads and the visible
            // exterior constrain how high the deck may span a hollow.
            if (counts != null) counts.enter(Stage.EXACT_BODY);
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    SurfaceColumn sample = sampleColumn(noise, x, z);
                    if (counts != null) counts.column();
                    int y = sample.y();
                    if (y < context.heightAccessor().getMinY() || y > MAX_SURFACE)
                        return failAt(context, reject,
                                FourRoadsEstateStructure.Rejection.ELEVATION,
                                "module=" + id + " exact", x, y, z);
                    var top = sample.column().getBlock(y);
                    boolean wet = top.is(Blocks.WATER);
                    if (!wet && (!top.isSolid() || !top.getFluidState().isEmpty()))
                        return failAt(context, reject,
                                FourRoadsEstateStructure.Rejection.SOLID_SUPPORT,
                                "module=" + id + " exact surface", x, y, z);
                    lower[id] = Math.max(lower[id], wet ? y - 2 : y - MAX_CUT);
                    if (onExterior(bounds, x, z)) {
                        upper[id] = Math.min(upper[id], y + MAX_EXTERIOR_DROP);
                        if (counts != null) counts.exterior();
                    }
                    if (lower[id] > upper[id])
                        return failAt(context, reject,
                                FourRoadsEstateStructure.Rejection.SLOPE,
                                "module=" + id + " exact interval="
                                        + lower[id] + ".." + upper[id], x, y, z);
                }
            }
            if (lower[id] > upper[id]) {
                return failAt(context, reject, FourRoadsEstateStructure.Rejection.SLOPE,
                        "module=" + id + " interval=" + lower[id] + ".." + upper[id],
                        box.minX(), lower[id], box.minZ());
            }
        }
        if (counts != null) counts.enter(Stage.HEIGHT_TREE);
        if (!constrainTree(12, lower, upper)) {
            return fail(reject, FourRoadsEstateStructure.Rejection.SLOPE);
        }
        int[] bases = new int[25];
        bases[12] = Math.clamp(centerSurface, lower[12], upper[12]);
        assignTree(12, bases, lower, upper);
        // Keep the independent exact footing proof after choosing heights.
        // Interval bounds alone must never authorize an unsupported pier.
        if (counts != null) counts.enter(Stage.SELECTED_FOOTINGS);
        for (EstateModuleGraph.Module module : modules) {
            BoundingBox box = module.piece().getBoundingBox();
            for (int px : EstateRaisedGeometry.FOOTING_OFFSETS) {
                for (int pz : EstateRaisedGeometry.FOOTING_OFFSETS) {
                    for (int dx = px == 46 ? -1 : 0;
                         dx <= (px == 46 ? 0 : 1); dx++) {
                        for (int dz = pz == 46 ? -1 : 0;
                             dz <= (pz == 46 ? 0 : 1); dz++) {
                            int x = box.minX() + px + dx;
                            int z = box.minZ() + pz + dz;
                            EstateNoiseSurvey.Column column = sampleColumn(noise, x, z).column();
                            if (counts != null) counts.column();
                            if (!anchored(column, bases[module.id()] - 1,
                                    EstateRaisedGeometry.MAX_SUPPORT_DEPTH - 1))
                                return failAt(context, reject,
                                        FourRoadsEstateStructure.Rejection.SOLID_SUPPORT,
                                        "module=" + module.id() + " selected footing",
                                        x, bases[module.id()] - 1, z);
                        }
                    }
                }
            }
        }
        // The two underside ribs span at most twelve blocks between the
        // anchored 2x2 piers. Every beam cell must be natural solid or a
        // fillable air/water cell; lava and unknown substrate fail closed.
        if (counts != null) counts.enter(Stage.BEAMS);
        for (EstateModuleGraph.Module module : modules) {
            BoundingBox box = module.piece().getBoundingBox();
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    if (!EstateRaisedGeometry.beam(box, x, z)) continue;
                    EstateNoiseSurvey.Column column = sampleColumn(noise, x, z).column();
                    if (counts != null) counts.column();
                    if (bases[module.id()] - column.topY() > MAX_BEAM_DROP
                            || !beamCompatible(column, bases[module.id()]))
                        return failAt(context, reject,
                                FourRoadsEstateStructure.Rejection.SOLID_SUPPORT,
                                "module=" + module.id() + " beam",
                                x, bases[module.id()] - 1, z);
                }
            }
        }
        if (counts != null) counts.enter(Stage.ARRIVALS);
        for (Gate gate : gates) {
            int id = moduleId(modules, gate.piece());
            int markerY = bases[id] + 4;
            if (!approach(context, gate, markerY, counts)) {
                return failAt(context, reject, FourRoadsEstateStructure.Rejection.SLOPE,
                        "approach", gate.marker().getX(), markerY, gate.marker().getZ());
            }
        }
        if (counts != null) counts.enter(Stage.SEAMS);
        for (EstateModuleGraph.Seam seam : seams) {
            int fromY = bases[seam.child().id()] + 4;
            int toY = bases[seam.parent().id()] + 4;
            for (int step = 0; step <= 12; step++) {
                int floorY = fromY + Math.round((toY - fromY) * step / 12.0f);
                for (int lane = -1; lane <= 1; lane++) {
                    BlockPos pos = seam.from().relative(seam.direction(), step)
                            .relative(seam.direction().getClockWise(), lane);
                    SurfaceColumn sample = sampleColumn(noise,
                            pos.getX(), pos.getZ());
                    if (counts != null) counts.column();
                    int natural = sample.y();
                    EstateNoiseSurvey.Column column = sample.column();
                    var top = column.getBlock(natural);
                    boolean wet = top.is(Blocks.WATER);
                    if (natural < MIN_SURFACE || natural > MAX_SURFACE
                            || (wet ? floorY < natural + 2
                                    : !top.isSolid() || !top.getFluidState().isEmpty()
                                            || natural - (floorY - 4) > MAX_CUT)
                            || step % 6 == 0 && !anchored(column, floorY - 1,
                                    EstateRaisedGeometry.MAX_SUPPORT_DEPTH - 1)) {
                        return failAt(context, reject, FourRoadsEstateStructure.Rejection.SLOPE,
                                "seam=" + seam.child().id(), pos.getX(), floorY, pos.getZ());
                    }
                }
            }
        }
        if (counts != null) counts.result = FourRoadsEstateStructure.Rejection.ACCEPTED;
        return Optional.of(new Plan(bases));
    }

    private static boolean constrainTree(int id, int[] lower, int[] upper) {
        for (int child = 0; child < 25; child++) {
            if (EstateModuleGraph.parent(child) != id) continue;
            if (!constrainTree(child, lower, upper)) return false;
            lower[id] = Math.max(lower[id], lower[child] - MAX_SEAM_RISE);
            upper[id] = Math.min(upper[id], upper[child] + MAX_SEAM_RISE);
        }
        return lower[id] <= upper[id];
    }

    private static void assignTree(int id, int[] bases, int[] lower, int[] upper) {
        for (int child = 0; child < 25; child++) {
            if (EstateModuleGraph.parent(child) != id) continue;
            bases[child] = Math.clamp(bases[id], lower[child], upper[child]);
            assignTree(child, bases, lower, upper);
        }
    }

    /** A 5x5 grid of clipped 2x2 piers, with at most 32 blocks to dry bed. */
    private static boolean footingInterval(EstateNoiseSurvey noise,
                                           BoundingBox box, int[] interval,
                                           StageCounts counts) {
        for (int px : EstateRaisedGeometry.FOOTING_OFFSETS) {
            for (int pz : EstateRaisedGeometry.FOOTING_OFFSETS) {
                for (int dx = px == 46 ? -1 : 0; dx <= (px == 46 ? 0 : 1); dx++) {
                    for (int dz = pz == 46 ? -1 : 0; dz <= (pz == 46 ? 0 : 1); dz++) {
                        int x = box.minX() + px + dx;
                        int z = box.minZ() + pz + dz;
                        SurfaceColumn sample = sampleColumn(noise, x, z);
                        if (counts != null) counts.column();
                        int topY = sample.y();
                        if (topY < MIN_SURFACE || topY > MAX_SURFACE) return false;
                        EstateNoiseSurvey.Column column = sample.column();
                        var top = column.getBlock(topY);
                        boolean wet = top.is(Blocks.WATER);
                        if (!wet && (!top.isSolid() || !top.getFluidState().isEmpty()))
                            return false;
                        int anchor = anchorTop(column, topY);
                        if (anchor == Integer.MIN_VALUE) return false;
                        interval[0] = Math.max(interval[0], wet ? topY - 2 : topY - MAX_CUT);
                        interval[1] = Math.min(interval[1], anchor
                                + EstateRaisedGeometry.MAX_SUPPORT_DEPTH - 1);
                        if (interval[0] > interval[1]) return false;
                    }
                }
            }
        }
        return true;
    }

    private static int anchorTop(EstateNoiseSurvey.Column column, int topY) {
        int dry = 0;
        for (int depth = 0; depth <= 64; depth++) {
            var state = column.getBlock(topY - depth);
            if (state.isSolid() && state.getFluidState().isEmpty()) {
                if (++dry == 2) return topY - depth + 1;
            } else if (state.isAir() || state.is(Blocks.WATER)) {
                dry = 0;
            } else return Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }

    /** Cheap natural-lane preflight before surveying 25 full modules. */
    private static boolean naturalApproach(Structure.GenerationContext context, Gate gate,
                                           StageCounts counts) {
        int[] previous = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int step = gate.distance() + 1;
             step <= gate.distance() + APPROACH; step++) {
            int low = Integer.MAX_VALUE;
            int high = Integer.MIN_VALUE;
            for (int lane = -1; lane <= 1; lane++) {
                BlockPos pos = gate.at(step, lane);
                int y = surface(context, pos);
                if (counts != null) counts.column();
                if (y < MIN_SURFACE || y > MAX_SURFACE
                        || previous[lane + 1] != Integer.MIN_VALUE
                                && Math.abs(y - previous[lane + 1]) > 1
                        || !supported(context, pos.getX(), y, pos.getZ())) return false;
                previous[lane + 1] = y;
                low = Math.min(low, y);
                high = Math.max(high, y);
            }
            if (high - low > 1) return false;
        }
        return true;
    }

    private static int moduleId(EstateModuleGraph.Module[] modules,
                                PoolElementStructurePiece piece) {
        for (EstateModuleGraph.Module module : modules)
            if (module.piece() == piece) return module.id();
        throw new IllegalStateException("Estate gate did not belong to a saved module");
    }

    static EstateModuleGraph.Module nearest(EstateModuleGraph.Module[] modules,
                                            int x, int z) {
        EstateModuleGraph.Module result = null;
        int best = Integer.MAX_VALUE;
        for (EstateModuleGraph.Module module : modules) {
            BoundingBox box = module.piece().getBoundingBox();
            int dx = Math.max(box.minX() - x, Math.max(0, x - box.maxX()));
            int dz = Math.max(box.minZ() - z, Math.max(0, z - box.maxZ()));
            int distance = dx * dx + dz * dz;
            if (distance < best) {
                best = distance;
                result = module;
            }
        }
        return result;
    }

    static List<Gate> gates(List<StructurePiece> pieces) {
        List<Gate> gates = new ArrayList<>(4);
        for (StructurePiece piece : pieces) {
            if (!(piece instanceof PoolElementStructurePiece pooled)
                    || !(pooled.getElement() instanceof SinglePoolElement single)) continue;
            Identifier id = single.getTemplateLocation();
            if (!GemsMod.MOD_ID.equals(id.getNamespace())) continue;
            int x;
            int z;
            Direction local;
            switch (id.getPath()) {
                case "four_roads_estate_v1_0_2" -> { x = 19; z = 5; local = Direction.NORTH; }
                case "four_roads_estate_v1_2_4" -> { x = 40; z = 19; local = Direction.EAST; }
                case "four_roads_estate_v1_4_2" -> { x = 19; z = 40; local = Direction.SOUTH; }
                case "four_roads_estate_v1_2_0" -> { x = 5; z = 19; local = Direction.WEST; }
                default -> { continue; }
            }
            BlockPos marker = StructureTemplate.transform(new BlockPos(x, 4, z),
                    Mirror.NONE, pooled.getRotation(), BlockPos.ZERO)
                    .offset(pooled.getPosition());
            Direction outward = pooled.getRotation().rotate(local);
            BoundingBox box = pooled.getBoundingBox();
            int distance = switch (outward) {
                case NORTH -> marker.getZ() - box.minZ();
                case EAST -> box.maxX() - marker.getX();
                case SOUTH -> box.maxZ() - marker.getZ();
                case WEST -> marker.getX() - box.minX();
                default -> 0;
            };
            if (distance < 5 || distance > 6) return List.of();
            gates.add(new Gate(pooled, marker, outward, distance));
        }
        return List.copyOf(gates);
    }

    private static boolean approach(Structure.GenerationContext context, Gate gate,
                                    int markerY, StageCounts counts) {
        int edgeY = surface(context, gate.at(gate.distance() + 1, 0));
        if (counts != null) counts.column();
        if (edgeY < MIN_SURFACE || edgeY > MAX_SURFACE
                || Math.abs(edgeY - markerY) > gate.distance()) return false;
        int[] previous = {edgeY, edgeY, edgeY};
        for (int step = gate.distance() + 1;
             step <= gate.distance() + APPROACH; step++) {
            int rowLow = Integer.MAX_VALUE;
            int rowHigh = Integer.MIN_VALUE;
            for (int lane = -1; lane <= 1; lane++) {
                BlockPos pos = gate.at(step, lane);
                int y = surface(context, pos);
                if (counts != null) counts.column();
                if (y < MIN_SURFACE || y > MAX_SURFACE
                        || Math.abs(y - previous[lane + 1]) > 1
                        || !supported(context, pos.getX(), y, pos.getZ())) return false;
                rowLow = Math.min(rowLow, y);
                rowHigh = Math.max(rowHigh, y);
                previous[lane + 1] = y;
            }
            if (rowHigh - rowLow > 1) return false;
        }
        return true;
    }

    private static boolean onExterior(BoundingBox site, int x, int z) {
        return x == site.minX() || x == site.maxX()
                || z == site.minZ() || z == site.maxZ();
    }

    private static boolean beamCompatible(EstateNoiseSurvey.Column column, int baseY) {
        for (int depth = 1; depth <= 2; depth++) {
            var state = column.getBlock(baseY - depth);
            if (!state.isAir() && !state.is(Blocks.WATER)
                    && (!state.isSolid() || !state.getFluidState().isEmpty())) return false;
        }
        return true;
    }

    private static boolean supported(Structure.GenerationContext context, int x,
                                     int y, int z) {
        NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z,
                context.heightAccessor(), context.randomState());
        for (int depth = 0; depth < 2; depth++) {
            var state = column.getBlock(y - depth);
            if (!state.isSolid() || !state.getFluidState().isEmpty()) return false;
        }
        return true;
    }

    private static boolean anchored(EstateNoiseSurvey.Column column,
                                    int topY, int maxDepth) {
        int naturalRun = 0;
        int water = 0;
        for (int depth = 0; depth <= maxDepth; depth++) {
            var state = column.getBlock(topY - depth);
            if (state.isSolid() && state.getFluidState().isEmpty()) {
                if (++naturalRun == 2) return true;
            } else if (state.isAir()) {
                naturalRun = 0;
            } else if (state.is(Blocks.WATER)
                    && ++water <= EstateRaisedGeometry.MAX_SUPPORT_DEPTH) {
                naturalRun = 0;
            } else {
                return false;
            }
        }
        return false;
    }

    private static boolean estateBiome(Structure.GenerationContext context,
                                       int x, int y, int z) {
        var biome = context.biomeResolver().getNoiseBiome(x >> 2, y >> 2, z >> 2);
        return context.validBiome().test(biome) && estateBiomeAllowed(biome);
    }

    private static boolean estateBiomeAllowed(net.minecraft.core.Holder<Biome> biome) {
        return biome.is(SUBSTRATE) || biome.is(Biomes.DESERT)
                || biome.is(Biomes.PLAINS);
    }

    private static int surface(Structure.GenerationContext context, BlockPos pos) {
        return surface(context, pos.getX(), pos.getZ());
    }

    /** Reuse a candidate-scoped chunk batch for every sampled body column. */
    private static SurfaceColumn sampleColumn(EstateNoiseSurvey noise,
                                              int x, int z) {
        EstateNoiseSurvey.Surface sample = noise.sample(x, z);
        return new SurfaceColumn(sample.y(), sample.column());
    }

    private static int surface(Structure.GenerationContext context, int x, int z) {
        return context.chunkGenerator().getBaseHeight(x, z,
                Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(),
                context.randomState()) - 1;
    }

    private static int next(int value, int max) {
        return Math.min(value + SAMPLE_STEP, max);
    }

    private static Optional<Plan> fail(
            Consumer<FourRoadsEstateStructure.Rejection> reject,
            FourRoadsEstateStructure.Rejection reason) {
        if (reject != null) reject.accept(reason);
        return Optional.empty();
    }

    private static Optional<Plan> failAt(Structure.GenerationContext context,
            Consumer<FourRoadsEstateStructure.Rejection> reject,
            FourRoadsEstateStructure.Rejection reason, String stage,
            int x, int y, int z) {
        boolean trace = Boolean.getBoolean("gems.fourRoadsEstateTraceRejections");
        if (trace || !System.getProperty("gems.fourRoadsEstateTargetSeed", "").isEmpty()) {
            int targetX = Integer.getInteger("gems.fourRoadsEstateTargetX", 0);
            int targetZ = Integer.getInteger("gems.fourRoadsEstateTargetZ", 0);
            if (trace || context.chunkPos().x() == (targetX >> 4)
                    && context.chunkPos().z() == (targetZ >> 4)) {
                System.out.println("FOUR_ROADS_ESTATE_TARGET_REJECTION reason=" + reason
                        + " stage=" + stage + " sample=" + x + "," + y + "," + z
                        + " top=" + context.chunkGenerator().getBaseColumn(x, z,
                                context.heightAccessor(), context.randomState()).getBlock(
                                        surface(context, x, z)));
                if (reason == FourRoadsEstateStructure.Rejection.SOLID_SUPPORT) {
                    NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z,
                            context.heightAccessor(), context.randomState());
                    StringBuilder layers = new StringBuilder();
                    for (int depth = 0; depth <= 16; depth++) {
                        if (depth > 0) layers.append(' ');
                        layers.append(depth).append(':').append(column.getBlock(y - depth));
                    }
                    System.out.println("FOUR_ROADS_ESTATE_TARGET_COLUMN " + layers);
                }
            }
        }
        return fail(reject, reason);
    }
}
