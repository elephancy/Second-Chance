package com.feel.gems.adventure.estate;

import com.feel.gems.GemsMod;
import com.feel.gems.adventure.worldgen.site.BoundedJigsawStart;
import com.feel.gems.adventure.worldgen.site.StructureStartReservation;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.Registry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

/** One saved start for all 25 versioned estate modules. */
public final class FourRoadsEstateStructure extends Structure implements BoundedJigsawStart {
    public static final MapCodec<FourRoadsEstateStructure> CODEC = JigsawStructure.CODEC.xmap(
            FourRoadsEstateStructure::new, structure -> structure.jigsaw);
    private static final StructureType<FourRoadsEstateStructure> TYPE = () -> CODEC;
    private static final ResourceKey<Structure> STRUCTURE_KEY = ResourceKey.create(
            Registries.STRUCTURE, Identifier.fromNamespaceAndPath(
                    GemsMod.MOD_ID, "four_roads_estate_v1"));
    private static final int MODULE = 47;
    public enum Rejection { JIGSAW, JIGSAW_BIOME, GRID, HEIGHT_BOUNDS, ELEVATION, BIOME,
        SOLID_SUPPORT, SLOPE, RESERVATION, ACCEPTED }
    private static final boolean DIAGNOSTICS = Boolean.getBoolean("gems.fourRoadsEstateGate");
    private static final boolean COHORT_REASONS = Boolean.getBoolean("gems.estateCohortDedicated");
    private static final ThreadLocal<Rejection> LAST_COHORT_REASON = new ThreadLocal<>();
    private static final LongAdder[] COUNTS = java.util.Arrays.stream(Rejection.values())
            .map(ignored -> new LongAdder()).toArray(LongAdder[]::new);

    private final JigsawStructure jigsaw;

    private FourRoadsEstateStructure(JigsawStructure jigsaw) {
        super(new StructureSettings(jigsaw.biomes(), jigsaw.spawnOverrides(),
                jigsaw.step(), jigsaw.terrainAdaptation()));
        this.jigsaw = jigsaw;
    }

    @Override
    public JigsawStructure reservationDelegate() { return jigsaw; }

    @Override
    public int reservationExtraReachBlocks() { return 8; }

    @Override
    public List<BoundingBox> reservationApproaches(StructureStart start) {
        if (!completeGrid(start.getPieces())) return null;
        List<EstateTerrainPlan.Gate> gates = EstateTerrainPlan.gates(start.getPieces());
        if (gates.size() != 4) return null;
        List<BoundingBox> boxes = new ArrayList<>(4);
        for (EstateTerrainPlan.Gate gate : gates) {
            BlockPos left = gate.at(gate.distance() + 1, -1);
            BlockPos right = gate.at(gate.distance() + 8, 1);
            BoundingBox body = gate.piece().getBoundingBox();
            boxes.add(new BoundingBox(Math.min(left.getX(), right.getX()), body.minY(),
                    Math.min(left.getZ(), right.getZ()),
                    Math.max(left.getX(), right.getX()), body.maxY(),
                    Math.max(left.getZ(), right.getZ())));
        }
        return List.copyOf(boxes);
    }

    /** Register before structure datapacks are read. Root mod initialization owns the call. */
    public static void init() {
        Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
                Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "four_roads_estate_v1"), TYPE);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (COHORT_REASONS) LAST_COHORT_REASON.remove();
        boolean traceRejections = Boolean.getBoolean("gems.fourRoadsEstateTraceRejections");
        boolean tracePlacement = Boolean.getBoolean("gems.fourRoadsEstateTracePlacementLatency");
        boolean traceTiming = traceRejections || tracePlacement;
        long jigsawStarted = traceTiming ? System.nanoTime() : 0L;
        Optional<GenerationStub> candidate = jigsaw.findGenerationPoint(context);
        long jigsawMs = traceTiming ? (System.nanoTime() - jigsawStarted) / 1_000_000 : 0L;
        if (candidate.isEmpty()) {
            count(Rejection.JIGSAW);
            if (traceRejections) System.out.println("FOUR_ROADS_ESTATE_PHASE_TIMING chunk="
                    + context.chunkPos() + " jigsawMs=" + jigsawMs
                    + " terrainMs=0 status=JIGSAW");
            if (DIAGNOSTICS && !System.getProperty("gems.fourRoadsEstateTargetSeed", "").isEmpty()) {
                int targetX = Integer.getInteger("gems.fourRoadsEstateTargetX", 0);
                int targetZ = Integer.getInteger("gems.fourRoadsEstateTargetZ", 0);
                if (context.chunkPos().x() == (targetX >> 4)
                        && context.chunkPos().z() == (targetZ >> 4)) {
                    int x = context.chunkPos().getMinBlockX();
                    int z = context.chunkPos().getMinBlockZ();
                    var quartBiome = context.biomeResolver().getNoiseBiome(x >> 2, 20, z >> 2);
                    System.out.println("FOUR_ROADS_ESTATE_JIGSAW_CONTEXT chunk="
                            + context.chunkPos() + " min=" + x + "," + z
                            + " column=" + context.couldValidBiomeExistInTerrainColumn(x, z)
                            + " y80Valid=" + context.validBiome().test(quartBiome)
                            + " y80TreeFree=" + (quartBiome.is(net.minecraft.world.level.biome.Biomes.DESERT)
                            || quartBiome.is(com.feel.gems.adventure.worldgen.AdventureBiomes.ESTATE_COMMONS)));
                }
            }
            return Optional.empty();
        }
        GenerationStub stub = candidate.get();
        if (!context.isValidBiome(stub)) {
            count(Rejection.JIGSAW_BIOME);
            return Optional.empty();
        }
        StructurePiecesBuilder pieces = stub.getPiecesBuilder();
        if (pieces.isEmpty() || !completeGrid(pieces)) {
            count(Rejection.GRID);
            return Optional.empty();
        }
        if (DIAGNOSTICS && !System.getProperty("gems.fourRoadsEstateTargetSeed", "").isEmpty()) {
            int targetX = Integer.getInteger("gems.fourRoadsEstateTargetX", 0);
            int targetZ = Integer.getInteger("gems.fourRoadsEstateTargetZ", 0);
            if (context.chunkPos().x() == (targetX >> 4)
                    && context.chunkPos().z() == (targetZ >> 4)) {
                BoundingBox box = pieces.getBoundingBox();
                EstateModuleGraph.Module[] targetModules =
                        EstateModuleGraph.modules(pieces.build().pieces());
                System.out.println("FOUR_ROADS_ESTATE_TARGET_BOUNDS chunk=" + context.chunkPos()
                        + " chunkMin=" + context.chunkPos().getMinBlockX() + ","
                        + context.chunkPos().getMinBlockZ()
                        + " bounds=" + box.minX() + "," + box.minZ() + ".."
                        + box.maxX() + "," + box.maxZ()
                        + " centerRotation=" + (targetModules == null ? "missing"
                                : targetModules[12].piece().getRotation()));
            }
        }
        long terrainStarted = traceTiming ? System.nanoTime() : 0L;
        Optional<EstateTerrainPlan.Plan> terrain = EstateTerrainPlan.survey(context,
                pieces.build().pieces(), pieces.getBoundingBox(),
                DIAGNOSTICS || COHORT_REASONS ? FourRoadsEstateStructure::count : null);
        if (traceRejections) System.out.println("FOUR_ROADS_ESTATE_PHASE_TIMING chunk="
                + context.chunkPos() + " jigsawMs=" + jigsawMs
                + " terrainMs=" + (System.nanoTime() - terrainStarted) / 1_000_000
                + " status=" + (terrain.isPresent() ? "PLAN" : "REJECTED"));
        if (terrain.isEmpty()) {
            return Optional.empty();
        }
        EstateModuleGraph.Module[] modules = EstateModuleGraph.modules(pieces.build().pieces());
        if (modules == null) {
            count(Rejection.GRID);
            return Optional.empty();
        }
        int centerShift = terrain.get().baseY(12) - modules[12].baseY();
        for (EstateModuleGraph.Module module : modules) {
            module.piece().move(0,
                    terrain.get().baseY(module.id()) - module.baseY(), 0);
        }
        if (EstateModuleGraph.seams(modules).size() != 24) {
            count(Rejection.GRID);
            return Optional.empty();
        }
        List<BoundingBox> occupied = new ArrayList<>(25);
        for (EstateModuleGraph.Module module : modules) {
            occupied.add(module.piece().getBoundingBox());
        }
        List<BoundingBox> arrivals = new ArrayList<>(4);
        for (EstateTerrainPlan.Gate gate : EstateTerrainPlan.gates(pieces.build().pieces())) {
            BlockPos left = gate.at(gate.distance() + 1, -1);
            BlockPos right = gate.at(gate.distance() + 8, 1);
            BoundingBox body = gate.piece().getBoundingBox();
            arrivals.add(new BoundingBox(Math.min(left.getX(), right.getX()), body.minY(),
                    Math.min(left.getZ(), right.getZ()),
                    Math.max(left.getX(), right.getX()), body.maxY(),
                    Math.max(left.getZ(), right.getZ())));
        }
        if (arrivals.size() != 4) {
            count(Rejection.GRID);
            return Optional.empty();
        }
        StructureStartReservation.Verdict reservation = StructureStartReservation.inspect(
                context, Level.OVERWORLD, STRUCTURE_KEY, occupied, arrivals);
        if (!reservation.clear()) {
            if (traceRejections) System.out.println("FOUR_ROADS_ESTATE_RESERVATION_REJECT chunk="
                    + context.chunkPos() + " status=" + reservation.status()
                    + " reason=" + reservation.reason());
            count(Rejection.RESERVATION);
            return Optional.empty();
        }
        count(Rejection.ACCEPTED);
        if (tracePlacement)
            System.out.println("FOUR_ROADS_ESTATE_ACCEPTED_LATENCY chunk="
                    + context.chunkPos() + " thread=" + Thread.currentThread().getName()
                    + " jigsawMs=" + jigsawMs
                    + " terrainMs=" + (System.nanoTime() - terrainStarted) / 1_000_000
                    + " totalMs=" + (System.nanoTime() - jigsawStarted) / 1_000_000
                    + " pieces=" + pieces.build().pieces().size());
        return Optional.of(new GenerationStub(stub.position().offset(0, centerShift, 0),
                Either.right(pieces)));
    }

    /** Each new chunk carries only its own share of piers, beams and stairs. */
    @Override
    public void afterPlace(WorldGenLevel world, StructureManager structures,
            ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
            ChunkPos chunk, PiecesContainer container) {
        if (!(world instanceof WorldGenRegion region) || !region.getCenter().equals(chunk)
                || region.getChunk(chunk.x(), chunk.z()).getPersistedStatus()
                        .isOrAfter(net.minecraft.world.level.chunk.status.ChunkStatus.FULL)
                || !completeGrid(container.pieces())) return;
        for (StructurePiece piece : container.pieces()) {
            BoundingBox box = piece.getBoundingBox();
            int minX = Math.max(box.minX(), chunkBox.minX());
            int maxX = Math.min(box.maxX(), chunkBox.maxX());
            int minZ = Math.max(box.minZ(), chunkBox.minZ());
            int maxZ = Math.min(box.maxZ(), chunkBox.maxZ());
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (EstateRaisedGeometry.footing(box, x, z))
                        support(region, x, box.minY() - 1, z,
                                Blocks.DEEPSLATE_BRICKS.defaultBlockState());
                    // The 4-thick template deck spans no more than twelve
                    // blocks between 2x2 load points. Two shallow underside
                    // ribs and the outside skirt are contained in the 235.
                    if (EstateRaisedGeometry.beam(box, x, z)) {
                        spanBeam(region, x, box.minY() - 1, z);
                        spanBeam(region, x, box.minY() - 2, z);
                    }
                }
            }
        }
        for (EstateTerrainPlan.Gate gate : EstateTerrainPlan.gates(container.pieces())) {
            grade(region, generator, chunkBox, gate);
        }
        for (EstateModuleGraph.Seam seam : EstateModuleGraph.seams(
                EstateModuleGraph.modules(container.pieces()))) {
            stair(region, chunkBox, seam);
        }
        EstateAuthoredRepairReceipt.capture(region, chunk, container);
        EstateRouteSite.capture(region, chunk, container);
    }

    private static void stair(WorldGenRegion region, BoundingBox chunkBox,
                              EstateModuleGraph.Seam seam) {
        int clearTop = Math.max(seam.from().getY(), seam.to().getY()) + 13;
        for (int step = 0; step <= 12; step++) {
            for (int lane = -1; lane <= 1; lane++) {
                BlockPos floor = seam.at(step, lane);
                if (!chunkBox.isInside(floor) || !region.ensureCanWrite(floor)) continue;
                if (step % 6 == 0) {
                    support(region, floor.getX(), floor.getY() - 1, floor.getZ(),
                            Blocks.DEEPSLATE_BRICKS.defaultBlockState());
                }
                if (step > 0 && step < 12) {
                    spanBeam(region, floor.getX(), floor.getY() - 1, floor.getZ());
                    spanBeam(region, floor.getX(), floor.getY() - 2, floor.getZ());
                    region.setBlock(floor, Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                }
                for (int y = floor.getY() + 1; y <= clearTop; y++) {
                    BlockPos clear = floor.atY(y);
                    if (!chunkBox.isInside(clear) || !region.ensureCanWrite(clear)) break;
                    region.setBlock(clear, Blocks.AIR.defaultBlockState(), 2);
                }
            }
            if (step == 0 || step == 12) continue;
            for (int side : new int[]{-2, 2}) {
                BlockPos rail = seam.at(step, side).above();
                for (int dy = 0; dy <= 1; dy++) {
                    BlockPos pos = rail.above(dy);
                    if (chunkBox.isInside(pos) && region.ensureCanWrite(pos)) {
                        region.setBlock(pos, Blocks.STONE_BRICKS.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    private static void support(WorldGenRegion region, int x, int startY, int z,
                                net.minecraft.world.level.block.state.BlockState material) {
        int naturalRun = 0;
        for (int depth = 0; depth < EstateRaisedGeometry.MAX_SUPPORT_DEPTH; depth++) {
            BlockPos pos = new BlockPos(x, startY - depth, z);
            if (!region.ensureCanWrite(pos)) return;
            var existing = region.getBlockState(pos);
            if (existing.isSolid() && existing.getFluidState().isEmpty()) {
                // A template deck or an earlier beam is not natural bed.
                if (existing.is(Blocks.STONE_BRICKS)
                        || existing.is(Blocks.DEEPSLATE_BRICKS)
                        || existing.is(Blocks.SMOOTH_STONE)) {
                    naturalRun = 0;
                } else if (++naturalRun == 2) return;
                continue;
            }
            naturalRun = 0;
            if (!replaceableSupport(existing) || region.getBlockEntity(pos) != null) return;
            region.setBlock(pos, material, 2);
        }
    }

    private static void spanBeam(WorldGenRegion region, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!region.ensureCanWrite(pos)) return;
        var state = region.getBlockState(pos);
        if (replaceableSupport(state) && region.getBlockEntity(pos) == null)
            region.setBlock(pos, Blocks.STONE_BRICKS.defaultBlockState(), 2);
    }

    private static boolean replaceableSupport(
            net.minecraft.world.level.block.state.BlockState state) {
        return state.isAir() || state.is(Blocks.WATER)
                || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS)
                || state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT)
                || state.is(Blocks.SEA_PICKLE) || state.is(Blocks.BUBBLE_COLUMN);
    }

    private static void grade(WorldGenRegion region, ChunkGenerator generator,
                              BoundingBox chunkBox, EstateTerrainPlan.Gate gate) {
        BlockPos outside = gate.at(gate.distance() + 1, 0);
        int ground = generator.getBaseHeight(outside.getX(), outside.getZ(),
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
                region, region.getLevel().getChunkSource().randomState()) - 1;
        int markerY = gate.marker().getY();
        if (Math.abs(ground - markerY) > gate.distance()) return;
        int baseY = gate.piece().getBoundingBox().minY();
        for (int step = 1; step <= gate.distance(); step++) {
            int y = markerY + Math.round((ground - markerY)
                    * (float) step / gate.distance());
            for (int lane = -1; lane <= 1; lane++) {
                BlockPos floor = gate.at(step, lane).atY(y);
                if (!chunkBox.isInside(floor) || !region.ensureCanWrite(floor)) continue;
                if (y < baseY) support(region, floor.getX(), y - 1,
                        floor.getZ(), Blocks.STONE_BRICKS.defaultBlockState());
                for (int fillY = Math.min(baseY, y); fillY <= y; fillY++) {
                    BlockPos pos = floor.atY(fillY);
                    if (!region.ensureCanWrite(pos)) break;
                    region.setBlock(pos, fillY == y
                            ? Blocks.SMOOTH_STONE.defaultBlockState()
                            : Blocks.STONE_BRICKS.defaultBlockState(), 2);
                }
                for (int clearY = y + 1;
                     clearY <= Math.max(baseY + 8, y + 3); clearY++) {
                    BlockPos pos = floor.atY(clearY);
                    if (!region.ensureCanWrite(pos)) break;
                    region.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    /** Cumulative counters; the opt-in fixture subtracts snapshots per seed. */
    public static Map<Rejection, Long> generationDiagnostics() {
        Map<Rejection, Long> snapshot = new EnumMap<>(Rejection.class);
        for (Rejection reason : Rejection.values()) {
            snapshot.put(reason, COUNTS[reason.ordinal()].sum());
        }
        return Map.copyOf(snapshot);
    }

    /** One direct noise query's outcome on the opt-in dedicated fixture thread. */
    public static Rejection lastCohortReason() {
        return COHORT_REASONS ? LAST_COHORT_REASON.get() : null;
    }

    /** Clear a prior query before Structure.generate may prefilter this chunk. */
    public static void beginCohortProbe() {
        if (COHORT_REASONS) LAST_COHORT_REASON.remove();
    }

    /** Read-only module attachment index for the staged noise reach fixture. */
    public static int parentModule(int id) {
        return EstateModuleGraph.parent(id);
    }

    private static void count(Rejection reason) {
        if (COHORT_REASONS) LAST_COHORT_REASON.set(reason);
        if (DIAGNOSTICS) COUNTS[reason.ordinal()].increment();
    }

    /** Used again on loaded starts before an entrance or reward can open. */
    public static boolean completeGrid(Iterable<StructurePiece> pieces) {
        List<TemplateCell> templates = new ArrayList<>(25);
        List<StructurePiece> actual = new ArrayList<>(25);
        for (StructurePiece piece : pieces) {
            if (!(piece instanceof PoolElementStructurePiece pooled)
                    || !(pooled.getElement() instanceof SinglePoolElement single)) return false;
            actual.add(piece);
            templates.add(new TemplateCell(single.getTemplateLocation(), piece.getBoundingBox()));
        }
        return completeGridGeometry(templates)
                && EstateModuleGraph.seams(EstateModuleGraph.modules(actual)).size() == 24;
    }

    /** Package-visible seam for the authored template coordinates and four rotations. */
    static boolean completeGridGeometry(List<TemplateCell> templates) {
        Set<Integer> cells = new HashSet<>();
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        List<PieceCell> cellsAt = new ArrayList<>(25);
        Identifier[] byCell = new Identifier[25];
        for (TemplateCell template : templates) {
            Identifier id = template.id();
            int cell = EstateModuleGraph.cellId(id);
            if (cell < 0 || !cells.add(cell)) return false;
            // Template names encode row (z) before column (x).
            int gx = cell % 5;
            int gz = cell / 5;
            byCell[cell] = id;
            BoundingBox box = template.bounds();
            if (box.getXSpan() != MODULE || box.getZSpan() != MODULE
                    || box.getYSpan() != EstateModuleGraph.expectedHeight(id)) return false;
            minX = Math.min(minX, box.minX());
            minZ = Math.min(minZ, box.minZ());
            maxX = Math.max(maxX, box.maxX());
            maxZ = Math.max(maxZ, box.maxZ());
            minY = Math.min(minY, box.minY());
            cellsAt.add(new PieceCell(gx, gz, box));
        }
        if (templates.size() != 25 || cells.size() != 25
                || maxX - minX + 1 != 235 || maxZ - minZ + 1 != 235
                || !EstateModuleGraph.coherentVersion(byCell)) return false;
        // IDs locate modules before the whole grid is rotated. We therefore
        // verify a complete nonoverlapping 5x5 tiling rather than assuming
        // that template x always points east in a rotated start.
        Set<Long> occupied = new HashSet<>();
        List<PlacedCell> located = new ArrayList<>(25);
        for (PieceCell cell : cellsAt) {
            BoundingBox box = cell.bounds();
            if ((box.minX() - minX) % MODULE != 0
                    || (box.minZ() - minZ) % MODULE != 0) return false;
            int x = (box.minX() - minX) / MODULE;
            int z = (box.minZ() - minZ) / MODULE;
            if (x < 0 || x > 4 || z < 0 || z > 4
                    || !occupied.add(((long) x << 32) | z)) return false;
            located.add(new PlacedCell(cell.idX(), cell.idZ(), x, z));
        }
        if (occupied.size() != 25) return false;
        for (int turn = 0; turn < 4; turn++) {
            boolean all = true;
            for (PlacedCell cell : located) {
                int expectedX = switch (turn) {
                    case 1 -> 4 - cell.idZ();
                    case 2 -> 4 - cell.idX();
                    case 3 -> cell.idZ();
                    default -> cell.idX();
                };
                int expectedZ = switch (turn) {
                    case 1 -> cell.idX();
                    case 2 -> 4 - cell.idZ();
                    case 3 -> 4 - cell.idX();
                    default -> cell.idZ();
                };
                if (cell.worldX() != expectedX || cell.worldZ() != expectedZ) {
                    all = false;
                    break;
                }
            }
            if (all) return true;
        }
        return false;
    }

    record TemplateCell(Identifier id, BoundingBox bounds) { }
    private record PieceCell(int idX, int idZ, BoundingBox bounds) { }
    private record PlacedCell(int idX, int idZ, int worldX, int worldZ) { }

    private static boolean completeGrid(StructurePiecesBuilder pieces) {
        return completeGrid(pieces.build().pieces());
    }

    @Override
    public StructureType<?> type() { return TYPE; }
}
