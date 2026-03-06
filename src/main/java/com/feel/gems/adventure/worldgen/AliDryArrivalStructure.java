package com.feel.gems.adventure.worldgen;

import com.feel.gems.GemsMod;
import com.feel.gems.adventure.worldgen.site.BoundedJigsawStart;
import com.feel.gems.adventure.worldgen.site.StructureStartReservation;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** Keeps Ali's authored north entrance on dry connected ground. */
public final class AliDryArrivalStructure extends Structure implements BoundedJigsawStart {
    public static final MapCodec<AliDryArrivalStructure> CODEC = JigsawStructure.CODEC.xmap(
            AliDryArrivalStructure::new, structure -> structure.jigsaw);
    private static final StructureType<AliDryArrivalStructure> TYPE = () -> CODEC;
    private static final String ROOT = "gems:ali_splitstone_citadel_full_v6";
    private static final String ATLAS = "gems:ali_high_atlas_hall_full_v5";
    private static final String CHAPEL = "gems:ali_cut_chapel_full_v5";
    private static final String ARRIVAL_REFERENCE = "gems:ali_arrival_reference_v5";
    private static final int APPROACH_LENGTH = 20;
    private static final int APPROACH_HALF_WIDTH = 4;
    private static final int BANK_START = 16;
    private static final int MAX_DOWN_SHIFT = 12;
    private static final int MAX_EMBED_ABOVE_DECK = 4;
    // A cut is allowed only through explicitly authored AIR above a solid deck.
    private static final int MAX_AUTHORED_CUT = 6;
    // Omitted template voxels can retain a small natural mound above an authored floor.
    // Grade only those exact cells, wholly inside new structure pieces.
    private static final int MAX_GRADE_COLUMNS = 64;
    private static final int MAX_GRADE_HEIGHT = 4;
    private static final int SITE_STEP = 16;
    private static final int SITE_RING = 4;
    private static final int PREFILTER_STEP = 8;
    private static final int MAX_FULL_FITS = 16;
    private static final int MAX_CACHED_NOISE_COLUMNS = 8_192;
    // ChunkGenerator.createReferences only inspects starts within eight chunks.
    private static final int MAX_REFERENCE_CHUNKS = 8;
    private static final ResourceKey<Structure> SELF = ResourceKey.create(
            Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "ali_splitstone_cloister"));
    private static final AtomicInteger REJECTED = new AtomicInteger();
    // Exhaustive rejected-site histograms are useful for diagnosis, but each
    // failed candidate otherwise samples the entire footprint and perimeter.
    private static final boolean FIT_DIAGNOSTICS =
            Boolean.getBoolean("gems.aliFitDiagnostics");

    private final JigsawStructure jigsaw;

    private AliDryArrivalStructure(JigsawStructure jigsaw) {
        super(new StructureSettings(jigsaw.biomes(), jigsaw.spawnOverrides(),
                jigsaw.step(), jigsaw.terrainAdaptation()));
        this.jigsaw = jigsaw;
    }

    public static void init() {
        Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
                Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "ali_dry_arrival"), TYPE);
    }

    @Override
    public JigsawStructure reservationDelegate() { return jigsaw; }

    @Override
    public int reservationExtraReachBlocks() { return APPROACH_LENGTH; }

    @Override
    public int reservationMaximumHorizontalShiftBlocks() {
        return SITE_STEP * SITE_RING;
    }

    @Override
    public List<BoundingBox> reservationApproaches(StructureStart start) {
        PoolElementStructurePiece root = null, carrier = null;
        int atlas = 0, chapel = 0;
        for (StructurePiece piece : start.getPieces()) {
            if (!(piece instanceof PoolElementStructurePiece pooled)
                    || !(pooled.getElement() instanceof SinglePoolElement single))
                return null;
            String name = single.getTemplateLocation().toString();
            if (name.equals(ROOT)) {
                if (root != null) return null;
                root = pooled;
            } else if (name.equals(ATLAS)) {
                atlas++;
            } else if (name.equals(CHAPEL)) {
                chapel++;
            } else if (name.equals(ARRIVAL_REFERENCE)) {
                if (carrier != null) return null;
                carrier = pooled;
            } else return null;
        }
        if (start.getPieces().size() != 4 || root == null || atlas != 1 || chapel != 1
                || carrier == null
                || root.getBoundingBox().getXSpan() != 47
                || root.getBoundingBox().getZSpan() != 47) return null;
        if (!sameHorizontalBox(carrier.getBoundingBox(),
                northArrivalApron(root))) return null;
        if (carrier.getBoundingBox().minY()
                != root.getBoundingBox().minY() + 5
                || carrier.getBoundingBox().maxY()
                        != root.getBoundingBox().minY() + 5) return null;
        if (currentV5Root(start) == null) return null;
        return List.of(northArrivalApron(root));
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        // The jigsaw root is anchored at the chunk corner. Its early biome
        // check runs at the rotated template center, before our bounded site
        // search can move the finished trio to a valid dry biome.
        GenerationContext geometryContext = new GenerationContext(
                context.registryAccess(), context.chunkGenerator(),
                context.biomeSource(), context.climateSampler(),
                context.biomeResolver(), context.randomState(),
                context.structureTemplateManager(), context.random(),
                context.seed(), context.chunkPos(), context.heightAccessor(),
                biome -> true);
        Optional<GenerationStub> candidate = jigsaw.findValidGenerationPoint(geometryContext);
        if (candidate.isEmpty()) return Optional.empty();
        GenerationStub stub = candidate.get();
        StructurePiecesBuilder builder = stub.getPiecesBuilder();
        if (builder.isEmpty()) return reject(context, "empty-jigsaw");
        PoolElementStructurePiece root = null;
        int atlas = 0, chapel = 0, count = 0;
        List<PoolElementStructurePiece> pieces = new ArrayList<>();
        for (StructurePiece piece : builder.build().pieces()) {
            if (!(piece instanceof PoolElementStructurePiece pooled)
                    || !(pooled.getElement() instanceof SinglePoolElement single))
                return reject(context, "non-single-piece");
            String name = single.getTemplateLocation().toString();
            if (name.equals(ROOT)) {
                if (root != null) return reject(context, "duplicate-root");
                root = pooled;
            } else if (name.equals(ATLAS)) atlas++;
            else if (name.equals(CHAPEL)) chapel++;
            else return reject(context, "unexpected-piece");
            pieces.add(pooled);
            count++;
        }
        if (root == null || atlas != 1 || chapel != 1 || count != 3
                || root.getBoundingBox().getXSpan() != 47
                || root.getBoundingBox().getZSpan() != 47)
            return reject(context, "incomplete-v5-trio");
        List<PieceMask> masks = new ArrayList<>();
        for (PoolElementStructurePiece piece : pieces) {
            PieceMask mask = PieceMask.read(context.structureTemplateManager(), piece);
            if (mask == null) return reject(context, "invalid-v5-template-mask");
            masks.add(mask);
        }
        Map<String, Integer> search = new TreeMap<>();
        String firstRejectedSite = "none";
        int fullFits = 0;
        long searchStarted = System.nanoTime();
        List<SiteOffset> sites = siteOffsets();
        // Shifted sites overlap heavily. Reuse their deterministic noise columns,
        // but cap the cache so a rejected candidate cannot retain every ring.
        Map<Long, ApproachCell> footprint = new LinkedHashMap<>(
                1_024, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, ApproachCell> eldest) {
                return size() > MAX_CACHED_NOISE_COLUMNS;
            }
        };
        searchSites:
        for (SiteOffset site : sites) {
            int dx = site.dx(), dz = site.dz();
            if (!withinReferenceRadius(context, pieces, root, dx, dz)) {
                search.merge("outsideReferenceRadius", 1, Integer::sum);
                continue;
            }
            // Every possible shift is in [-12, 0]. If none of those quart-Y
            // biomes is valid, no terrain sample can make this site eligible.
            if (!hasValidSiteBiome(context, stub.position(), dx, dz)) {
                search.merge("noSiteBiome", 1, Integer::sum);
                continue;
            }
            List<Integer> shifts = candidateShifts(context, root, dx, dz, footprint);
            if (shifts.isEmpty()) {
                search.merge("noDoorLevel", 1, Integer::sum);
                continue;
            }
            for (int shift : shifts) {
                BlockPos finalStart = stub.position().offset(dx, shift, dz);
                var finalBiome = context.biomeResolver().getNoiseBiome(
                        QuartPos.fromBlock(finalStart.getX()),
                        QuartPos.fromBlock(finalStart.getY()),
                        QuartPos.fromBlock(finalStart.getZ()));
                if (!context.validBiome().test(finalBiome)) {
                    search.merge("finalBiome", 1, Integer::sum);
                    if (firstRejectedSite.equals("none"))
                        firstRejectedSite = dx + "," + dz + ":" + shift
                                + ":finalBiome=" + finalBiome.unwrapKey()
                                        .map(key -> key.identifier().toString())
                                        .orElse("unkeyed");
                    continue;
                }
                String prefilter = sparseFit(context, masks, shift, dx, dz, footprint);
                if (!prefilter.equals("ok")) {
                    search.merge("prefilter-" + prefilter, 1, Integer::sum);
                    if (firstRejectedSite.equals("none"))
                        firstRejectedSite = dx + "," + dz + ":" + shift + ":" + prefilter;
                    continue;
                }
                ApproachSurvey survey = dryApproach(context, root, shift, dx, dz,
                        footprint);
                if (!survey.connected()) {
                    search.merge("route", 1, Integer::sum);
                    if (firstRejectedSite.equals("none"))
                        firstRejectedSite = dx + "," + dz + ":" + shift + ":"
                                + survey.detail();
                    continue;
                }
                if (fullFits >= MAX_FULL_FITS) {
                    search.merge("fullFitBudget", 1, Integer::sum);
                    break searchSites;
                }
                fullFits++;
                FitSurvey fit = footprintFit(context, masks, shift, dx, dz, footprint);
                if (!fit.safe()) {
                    search.merge("fullFit", 1, Integer::sum);
                    if (FIT_DIAGNOSTICS)
                        System.out.println("ALI_DRY_ARRIVAL_SITE_ATTEMPT chunk="
                                + context.chunkPos() + " offset=" + dx + "," + dz
                                + " shift=" + shift + " rotation=" + root.getRotation()
                                + " rootY=" + root.getBoundingBox().minY()
                                + " route=connected fit=" + fit.detail());
                    continue;
                }
                List<BoundingBox> occupied = new ArrayList<>();
                for (PoolElementStructurePiece piece : pieces)
                    occupied.add(translated(piece.getBoundingBox(), dx, shift, dz));
                BoundingBox approach = translated(northArrivalApron(root),
                        dx, shift, dz);
                StructureStartReservation.Verdict reservation =
                        StructureStartReservation.inspect(context, Level.OVERWORLD,
                                SELF, occupied, List.of(approach));
                if (!reservation.clear()) {
                    search.merge("neighbor-" + reservation.status(), 1, Integer::sum);
                    if (FIT_DIAGNOSTICS)
                        System.out.println("ALI_DRY_ARRIVAL_SITE_ATTEMPT chunk="
                                + context.chunkPos() + " offset=" + dx + "," + dz
                                + " shift=" + shift + " route=connected fit=fit-ok"
                                + " neighbor=" + reservation.status() + ":"
                                + reservation.reason());
                    continue;
                }
                for (PoolElementStructurePiece piece : pieces)
                    movePlannedPiece(piece, dx, shift, dz);
                PoolElementStructurePiece carrier = arrivalCarrier(
                        context.structureTemplateManager(), root);
                if (carrier == null) return reject(context, "invalid-arrival-carrier");
                builder.addPiece(carrier);
                System.out.println("ALI_DRY_ARRIVAL_ACCEPT chunk=" + context.chunkPos()
                        + " rotation=" + root.getRotation() + " offset=" + dx + "," + dz
                        + " shift=" + shift + " " + survey.detail()
                        + " " + fit.detail() + " search=" + search
                        + " fullFits=" + fullFits
                        + " elapsedMs=" + (System.nanoTime() - searchStarted) / 1_000_000
                        + " rejectedBeforeAccept=" + REJECTED.get());
                return Optional.of(new GenerationStub(finalStart, Either.right(builder)));
            }
        }
        if (FIT_DIAGNOSTICS)
            System.out.println("ALI_DRY_ARRIVAL_SITE_SEARCH chunk=" + context.chunkPos()
                    + " offsets=" + sites.size() + " histogram=" + search
                    + " fullFits=" + fullFits
                    + " elapsedMs=" + (System.nanoTime() - searchStarted) / 1_000_000
                    + " first=" + firstRejectedSite);
        return reject(context, "no-safe-bounded-site");
    }

    private record SiteOffset(int dx, int dz) { }

    private static BoundingBox translated(BoundingBox box, int dx, int dy, int dz) {
        return new BoundingBox(box.minX() + dx, box.minY() + dy, box.minZ() + dz,
                box.maxX() + dx, box.maxY() + dy, box.maxZ() + dz);
    }

    private static boolean sameHorizontalBox(BoundingBox a, BoundingBox b) {
        return a.minX() == b.minX() && a.maxX() == b.maxX()
                && a.minZ() == b.minZ() && a.maxZ() == b.maxZ();
    }

    private static boolean withinReferenceRadius(GenerationContext context,
            List<PoolElementStructurePiece> pieces,
            PoolElementStructurePiece root, int dx, int dz) {
        for (PoolElementStructurePiece piece : pieces)
            if (!referenced(context, translated(piece.getBoundingBox(), dx, 0, dz)))
                return false;
        return referenced(context, translated(northArrivalApron(root), dx, 0, dz));
    }

    private static BoundingBox northArrivalApron(PoolElementStructurePiece root) {
        // The authored seal is on local z=0; its exterior spans z=-1 through -20.
        return BoundedJigsawStart.localBox(root, 19, -APPROACH_LENGTH, 27, -1);
    }

    private static PoolElementStructurePiece arrivalCarrier(
            StructureTemplateManager manager, PoolElementStructurePiece root) {
        SinglePoolElement element = StructurePoolElement.single(ARRIVAL_REFERENCE)
                .apply(StructureTemplatePool.Projection.RIGID);
        BoundingBox target = northArrivalApron(root);
        BoundingBox raw = element.getBoundingBox(manager, BlockPos.ZERO,
                root.getRotation());
        if (raw.getXSpan() != target.getXSpan()
                || raw.getZSpan() != target.getZSpan() || raw.getYSpan() != 1)
            return null;
        BlockPos origin = new BlockPos(target.minX() - raw.minX(),
                root.getBoundingBox().minY() + 5 - raw.minY(),
                target.minZ() - raw.minZ());
        BoundingBox box = element.getBoundingBox(manager, origin, root.getRotation());
        if (box.minX() != target.minX() || box.maxX() != target.maxX()
                || box.minZ() != target.minZ() || box.maxZ() != target.maxZ()) return null;
        return new PoolElementStructurePiece(manager, element, origin,
                root.getGroundLevelDelta(), root.getRotation(), box,
                JigsawStructure.DEFAULT_LIQUID_SETTINGS);
    }

    static PoolElementStructurePiece currentV5Root(StructureStart start) {
        PoolElementStructurePiece root = null, atlas = null, chapel = null;
        Set<String> names = new HashSet<>();
        for (StructurePiece piece : start.getPieces()) {
            if (!(piece instanceof PoolElementStructurePiece pooled)
                    || !(pooled.getElement() instanceof SinglePoolElement single)) return null;
            String name = single.getTemplateLocation().toString();
            if (!Set.of(ROOT, ATLAS, CHAPEL, ARRIVAL_REFERENCE).contains(name)
                    || !names.add(name)) return null;
            if (name.equals(ROOT)) {
                if (root != null) return null;
                root = pooled;
            }
            if (name.equals(ATLAS)) atlas = pooled;
            if (name.equals(CHAPEL)) chapel = pooled;
        }
        if (root == null || names.size() != 4
                || !names.containsAll(Set.of(ATLAS, CHAPEL, ARRIVAL_REFERENCE))
                || root.getBoundingBox().getXSpan() != 47
                || root.getBoundingBox().getZSpan() != 47) return null;
        if (atlas == null || chapel == null
                    || !sameHorizontalBox(atlas.getBoundingBox(),
                            BoundedJigsawStart.localBox(root, 47, 3, 87, 43))
                    || !sameHorizontalBox(chapel.getBoundingBox(),
                            BoundedJigsawStart.localBox(root, -41, 3, -1, 43))
                    || atlas.getBoundingBox().minY() != root.getBoundingBox().minY()
                    || chapel.getBoundingBox().minY() != root.getBoundingBox().minY()
                    || atlas.getBoundingBox().getYSpan() != 35
                    || chapel.getBoundingBox().getYSpan() != 35) return null;
        for (StructurePiece piece : start.getPieces()) {
            PoolElementStructurePiece pooled = (PoolElementStructurePiece) piece;
            SinglePoolElement single = (SinglePoolElement) pooled.getElement();
            if (single.getTemplateLocation().toString().equals(ARRIVAL_REFERENCE)
                    && (!sameHorizontalBox(pooled.getBoundingBox(),
                            northArrivalApron(root))
                            || pooled.getBoundingBox().minY()
                                    != root.getBoundingBox().minY() + 5
                            || pooled.getBoundingBox().maxY()
                                    != root.getBoundingBox().minY() + 5)) return null;
        }
        return root;
    }

    static PoolElementStructurePiece currentV5Carrier(StructureStart start) {
        if (start == null || currentV5Root(start) == null
                || start.getPieces().size() != 4) return null;
        for (StructurePiece piece : start.getPieces()) {
            PoolElementStructurePiece pooled = (PoolElementStructurePiece) piece;
            if (((SinglePoolElement) pooled.getElement()).getTemplateLocation()
                    .toString().equals(ARRIVAL_REFERENCE)) return pooled;
        }
        return null;
    }

    static boolean hasCurrentV5Carrier(StructureStart start) {
        return currentV5Carrier(start) != null;
    }

    static boolean isCurrentV5Marker(StructureStart start, BlockPos marker,
                                     Direction outward) {
        PoolElementStructurePiece root = currentV5Root(start);
        if (root == null || root.getRotation().rotate(Direction.NORTH) != outward)
            return false;
        BlockPos authored = StructureTemplate.transform(new BlockPos(23, 3, 1),
                Mirror.NONE, root.getRotation(), BlockPos.ZERO)
                .offset(root.getPosition());
        return authored.equals(marker);
    }

    static boolean containsCurrentV5Template(StructureStart start) {
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof PoolElementStructurePiece pooled
                    && pooled.getElement() instanceof SinglePoolElement single
                    && Set.of(ROOT, ATLAS, CHAPEL).contains(
                            single.getTemplateLocation().toString())) return true;
        }
        return false;
    }

    private static boolean referenced(GenerationContext context, BoundingBox box) {
        return Math.abs((box.minX() >> 4) - context.chunkPos().x())
                    <= MAX_REFERENCE_CHUNKS
                && Math.abs((box.maxX() >> 4) - context.chunkPos().x())
                    <= MAX_REFERENCE_CHUNKS
                && Math.abs((box.minZ() >> 4) - context.chunkPos().z())
                    <= MAX_REFERENCE_CHUNKS
                && Math.abs((box.maxZ() >> 4) - context.chunkPos().z())
                    <= MAX_REFERENCE_CHUNKS;
    }

    private static List<SiteOffset> siteOffsets() {
        List<SiteOffset> offsets = new ArrayList<>();
        for (int x = -SITE_RING; x <= SITE_RING; x++) {
            for (int z = -SITE_RING; z <= SITE_RING; z++)
                offsets.add(new SiteOffset(x * SITE_STEP, z * SITE_STEP));
        }
        offsets.sort(Comparator.comparingInt((SiteOffset offset) ->
                        offset.dx() * offset.dx() + offset.dz() * offset.dz())
                .thenComparingInt(SiteOffset::dx).thenComparingInt(SiteOffset::dz));
        return offsets;
    }

    private static boolean hasValidSiteBiome(GenerationContext context,
            BlockPos start, int dx, int dz) {
        int quartX = QuartPos.fromBlock(start.getX() + dx);
        int quartZ = QuartPos.fromBlock(start.getZ() + dz);
        int low = QuartPos.fromBlock(start.getY() - MAX_DOWN_SHIFT);
        int high = QuartPos.fromBlock(start.getY());
        for (int quartY = low; quartY <= high; quartY++) {
            if (context.validBiome().test(context.biomeResolver().getNoiseBiome(
                    quartX, quartY, quartZ))) return true;
        }
        return false;
    }

    /** The door's first dry cells yield at most ten bounded candidate levels. */
    private static List<Integer> candidateShifts(GenerationContext context,
            PoolElementStructurePiece root, int dx, int dz,
            Map<Long, ApproachCell> cache) {
        Direction outward = root.getRotation().rotate(Direction.NORTH);
        Direction lateral = root.getRotation().rotate(Direction.EAST);
        BlockPos door = StructureTemplate.transform(new BlockPos(23, 0, 0),
                Mirror.NONE, root.getRotation(), BlockPos.ZERO)
                .offset(root.getPosition()).offset(dx, 0, dz);
        int doorFeet = root.getBoundingBox().minY() + 5;
        Set<Integer> shifts = new HashSet<>();
        shifts.add(0);
        for (int cross : new int[]{0, -1, 1}) {
            BlockPos first = door.relative(outward).relative(lateral, cross);
            ApproachCell cell = cachedSample(context, cache,
                    first.getX(), first.getZ());
            if (cell == null || !cell.walkable()) continue;
            for (int grade = -1; grade <= 1; grade++) {
                int shift = cell.feet() + grade - doorFeet;
                if (shift >= -MAX_DOWN_SHIFT && shift <= 0) shifts.add(shift);
            }
        }
        return shifts.stream().sorted(Comparator.reverseOrder()).toList();
    }

    /** Necessary mask checks on a fixed 8-block grid; full fit still decides. */
    private static String sparseFit(GenerationContext context, List<PieceMask> masks,
            int shift, int dx, int dz, Map<Long, ApproachCell> cache) {
        for (PieceMask mask : masks) {
            BoundingBox box = mask.box();
            int base = box.minY() + shift;
            int deckFeet = base + 5;
            if (base < context.heightAccessor().getMinY()
                    || box.maxY() + shift >= context.heightAccessor().getMaxY())
                return "height";
            for (int x : sparseAxis(box.minX(), box.maxX())) {
                for (int z : sparseAxis(box.minZ(), box.maxZ())) {
                    ApproachCell cell = cachedSample(context, cache, x + dx, z + dz);
                    if (cell == null) return "invalid";
                    byte foundation = mask.at(x, box.minY(), z);
                    byte deck = mask.at(x, box.minY() + 4, z);
                    boolean authored = false;
                    for (int y = box.minY(); y <= box.maxY(); y++)
                        authored |= mask.at(x, y, z) != OMITTED;
                    if (cell.fluid()) {
                        if (cell.fluidY() >= base
                                && mask.at(x, cell.fluidY() - shift, z) != SOLID)
                            return "unsealed-fluid";
                        if (cell.fluidY() < base && foundation == SOLID)
                            return "wet-foundation";
                    }
                    for (int y = base; y <= Math.min(box.maxY() + shift,
                            cell.feet() + 2); y++)
                        if (!cell.noise().getBlock(y).getFluidState().isEmpty()
                                && mask.at(x, y - shift, z) != SOLID)
                            return "fluid-voxel";
                    if (!authored) {
                        boolean besidePublicAir = false;
                        for (Direction side : Direction.Plane.HORIZONTAL)
                            besidePublicAir |= mask.at(x + side.getStepX(),
                                    box.minY() + 5, z + side.getStepZ()) == AIR
                                    || mask.at(x + side.getStepX(),
                                            box.minY() + 6, z + side.getStepZ()) == AIR;
                        if (!mask.flank(x, z) && besidePublicAir && (!cell.walkable()
                                || cell.feet() > deckFeet))
                            return "exposed-omission";
                        continue;
                    }
                    if (!cell.fluid() && !cell.walkable()) return "blocked";
                    if (foundation == SOLID && cell.feet() < base) return "foundation-gap";
                    if (cell.feet() > deckFeet && !mask.apron(x, z)) {
                        int cut = cell.feet() - deckFeet;
                        boolean fullyAuthored = true, explicitAir = true;
                        for (int y = box.minY() + 5; y < cell.feet() - shift; y++) {
                            byte voxel = mask.at(x, y, z);
                            fullyAuthored &= voxel != OMITTED;
                            explicitAir &= voxel == AIR;
                        }
                        boolean authoredFit = fullyAuthored
                                && (cut <= MAX_EMBED_ABOVE_DECK
                                        || deck == SOLID && cut <= MAX_AUTHORED_CUT
                                                && explicitAir);
                        if (!authoredFit && gradeCuts(mask, x, z, shift, dx, dz,
                                cell) == null)
                            return "unsafe-embed";
                    }
                }
            }
        }
        return "ok";
    }

    private static List<Integer> sparseAxis(int min, int max) {
        List<Integer> points = new ArrayList<>();
        for (int value = min; value <= max; value += PREFILTER_STEP)
            points.add(value);
        if (points.getLast() != max) points.add(max);
        return points;
    }

    private static final byte OMITTED = 0, AIR = 1, SOLID = 2;

    /** Only original terrain omitted by the template may be cut above a real deck. */
    private static List<BlockPos> gradeCuts(PieceMask mask, int x, int z,
            int shift, int dx, int dz, ApproachCell cell) {
        int base = mask.box().minY() + shift;
        int deckFeet = base + 5;
        int height = cell.feet() - deckFeet;
        if (height < 1 || height > MAX_GRADE_HEIGHT || cell.fluid()
                || mask.at(x, mask.box().minY() + 4, z) != SOLID)
            return null;
        List<BlockPos> cuts = new ArrayList<>(height);
        for (int y = deckFeet; y < cell.feet(); y++) {
            if (mask.at(x, y - shift, z) != OMITTED) continue;
            BlockState natural = cell.noise().getBlock(y);
            if (!naturalGradeBlock(natural)) return null;
            cuts.add(new BlockPos(x + dx, y, z + dz));
        }
        return cuts.isEmpty() ? null : List.copyOf(cuts);
    }

    static boolean naturalGradeBlock(BlockState state) {
        return state.getFluidState().isEmpty() && (state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL) || state.is(Blocks.STONE)
                || state.is(Blocks.ANDESITE) || state.is(Blocks.DIORITE)
                || state.is(Blocks.GRANITE) || state.is(Blocks.TUFF)
                || state.is(Blocks.CALCITE) || state.is(Blocks.GRAVEL)
                || state.is(Blocks.SNOW_BLOCK));
    }

    /** Read the placed palette, including explicit AIR; absent NBT voxels stay untouched. */
    private record PieceMask(BoundingBox box, Map<Long, Byte> voxels,
            Set<Long> exteriorApron, Set<Long> exteriorFlanks) {
        byte at(int x, int y, int z) {
            return voxels.getOrDefault(BlockPos.asLong(x, y, z), OMITTED);
        }
        boolean apron(int x, int z) {
            return exteriorApron.contains(BlockPos.asLong(x, 0, z));
        }
        boolean flank(int x, int z) {
            return exteriorFlanks.contains(BlockPos.asLong(x, 0, z));
        }

        static PieceMask read(StructureTemplateManager manager,
                              PoolElementStructurePiece piece) {
            if (!(piece.getElement() instanceof SinglePoolElement single)) return null;
            StructureTemplate template = manager.getOrCreate(single.getTemplateLocation());
            CompoundTag saved = template.save(new CompoundTag());
            // Multiple random palettes would make the mask ambiguous; v5 has one.
            if (saved.contains("palettes")) return null;
            ListTag palette = saved.getList("palette").orElse(null);
            ListTag blocks = saved.getList("blocks").orElse(null);
            if (palette == null || palette.isEmpty() || blocks == null || blocks.isEmpty())
                return null;
            List<Byte> kinds = new ArrayList<>();
            for (int index = 0; index < palette.size(); index++) {
                CompoundTag state = palette.getCompound(index).orElse(null);
                if (state == null) return null;
                String name = state.getString("id")
                        .orElseGet(() -> state.getString("Name").orElse(""));
                if (name.isEmpty()) return null;
                kinds.add(name.equals("minecraft:air") || name.equals("minecraft:cave_air")
                        || name.equals("minecraft:void_air") ? AIR : SOLID);
            }
            Map<Long, Byte> voxels = new HashMap<>();
            Map<Long, Integer> highest = new HashMap<>();
            for (int index = 0; index < blocks.size(); index++) {
                CompoundTag block = blocks.getCompound(index).orElse(null);
                if (block == null) return null;
                ListTag pos = block.getList("pos").orElse(null);
                int state = block.getIntOr("state", -1);
                if (pos == null || pos.size() != 3 || state < 0 || state >= kinds.size())
                    return null;
                BlockPos local = new BlockPos(pos.getIntOr(0, Integer.MIN_VALUE),
                        pos.getIntOr(1, Integer.MIN_VALUE),
                        pos.getIntOr(2, Integer.MIN_VALUE));
                if (local.getX() < 0 || local.getY() < 0 || local.getZ() < 0)
                    return null;
                BlockPos world = StructureTemplate.transform(local, Mirror.NONE,
                        piece.getRotation(), BlockPos.ZERO).offset(piece.getPosition());
                if (!piece.getBoundingBox().isInside(world)
                        || voxels.put(BlockPos.asLong(world.getX(), world.getY(),
                                world.getZ()), kinds.get(state)) != null)
                    return null;
                highest.merge(BlockPos.asLong(world.getX(), 0, world.getZ()),
                        local.getY(), Math::max);
            }
            Set<Long> apron = new HashSet<>();
            Set<Long> flanks = new HashSet<>();
            if (single.getTemplateLocation().toString().equals(ROOT)) {
                for (var entry : highest.entrySet()) {
                    if (entry.getValue() <= 3) apron.add(entry.getKey());
                }
                // The root's buried stone ring is exterior, never a public deck.
                if (apron.size() != 130) return null;
                for (int x : new int[]{0, 1, 2, 44, 45, 46}) {
                    for (int z : new int[]{20, 26}) {
                        BlockPos world = StructureTemplate.transform(
                                new BlockPos(x, 0, z), Mirror.NONE,
                                piece.getRotation(), BlockPos.ZERO)
                                .offset(piece.getPosition());
                        for (int y = piece.getBoundingBox().minY();
                                y <= piece.getBoundingBox().maxY(); y++)
                            if (voxels.containsKey(BlockPos.asLong(
                                    world.getX(), y, world.getZ()))) return null;
                        flanks.add(BlockPos.asLong(world.getX(), 0, world.getZ()));
                    }
                }
                if (flanks.size() != 12) return null;
            }
            return new PieceMask(piece.getBoundingBox(), voxels, apron, flanks);
        }
    }

    private record FitSurvey(boolean safe, String detail) { }

    /** Survey every column, including omissions and the one-block wet perimeter. */
    private static FitSurvey footprintFit(GenerationContext context,
            List<PieceMask> masks, int shift, int dx, int dz,
            Map<Long, ApproachCell> cache) {
        Map<String, Integer> counts = new TreeMap<>();
        String first = "none";
        Set<Long> gradeColumns = new HashSet<>();
        List<BlockPos> cuts = new ArrayList<>();
        for (PieceMask mask : masks) {
            if (!FIT_DIAGNOSTICS && !first.equals("none"))
                return rejectedFit(first);
            BoundingBox box = mask.box();
            int base = box.minY() + shift;
            int deckFeet = base + 5;
            if (base < context.heightAccessor().getMinY()
                    || box.maxY() + shift >= context.heightAccessor().getMaxY()) {
                counts.merge("heightBounds", 1, Integer::sum);
                if (first.equals("none")) first = "height-bounds:" + box + ":" + shift;
                continue;
            }
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    if (!FIT_DIAGNOSTICS && !first.equals("none"))
                        return rejectedFit(first);
                    ApproachCell cell = cachedSample(context, cache, x + dx, z + dz);
                    byte foundation = mask.at(x, box.minY(), z);
                    byte deck = mask.at(x, box.minY() + 4, z);
                    boolean authored = false;
                    for (int y = box.minY(); y <= box.maxY(); y++) {
                        if (mask.at(x, y, z) != OMITTED) {
                            authored = true;
                            break;
                        }
                    }
                    counts.merge(authored ? "authored" : "omitted", 1, Integer::sum);
                    if (cell == null) {
                        counts.merge("invalid", 1, Integer::sum);
                        if (first.equals("none"))
                            first = "invalid:" + (x + dx) + "," + (z + dz);
                        continue;
                    }
                    if (cell.fluid()) {
                        counts.merge("wet", 1, Integer::sum);
                        int fluidY = cell.fluidY();
                        if (fluidY >= base
                                && mask.at(x, fluidY - shift, z) != SOLID) {
                            counts.merge("unsealedFluid", 1, Integer::sum);
                            if (first.equals("none"))
                                first = "unsealed-fluid:" + (x + dx) + "," + (z + dz)
                                        + ":fluidY=" + fluidY + ":base=" + base
                                        + ":voxel=" + mask.at(x, fluidY - shift, z);
                        } else if (fluidY < base && foundation == SOLID) {
                            counts.merge("wetFoundationGap", 1, Integer::sum);
                            if (first.equals("none"))
                                first = "wet-foundation-gap:" + (x + dx) + "," + (z + dz)
                                        + ":fluidY=" + fluidY + ":base=" + base;
                        } else counts.merge("sealedOrBelowFluid", 1, Integer::sum);
                    }
                    // Noise columns can hide cave water below a dry surface.
                    for (int y = base; y <= Math.min(box.maxY() + shift,
                            cell.feet() + 2); y++) {
                        if (cell.noise().getBlock(y).getFluidState().isEmpty()) continue;
                        counts.merge("fluidVoxels", 1, Integer::sum);
                        if (mask.at(x, y - shift, z) != SOLID) {
                            counts.merge("unsealedFluidVoxels", 1, Integer::sum);
                            if (first.equals("none"))
                                first = "unsealed-fluid-voxel:" + (x + dx) + ","
                                        + y + "," + (z + dz);
                        }
                    }
                    if (!authored) {
                        if (!cell.fluid()) counts.merge("untouchedDry", 1, Integer::sum);
                        boolean besidePublicAir = false;
                        for (Direction side : Direction.Plane.HORIZONTAL) {
                            int besideX = x + side.getStepX();
                            int besideZ = z + side.getStepZ();
                            besidePublicAir |= mask.at(besideX, box.minY() + 5,
                                    besideZ) == AIR
                                    || mask.at(besideX, box.minY() + 6,
                                            besideZ) == AIR;
                        }
                        if (mask.flank(x, z)) {
                            counts.merge("exteriorFlank", 1, Integer::sum);
                        } else if (besidePublicAir && (!cell.walkable()
                                || cell.feet() > deckFeet)) {
                            counts.merge("exposedOmission", 1, Integer::sum);
                            if (first.equals("none"))
                                first = "exposed-omission:" + (x + dx) + "," + (z + dz)
                                        + ":feet=" + cell.feet() + ":deck=" + deckFeet
                                        + ":terrain=" + cell.failure();
                        }
                        continue;
                    }
                    if (!cell.fluid() && !cell.walkable()) {
                        counts.merge("blockedOrUnsupported", 1, Integer::sum);
                        if (first.equals("none"))
                            first = "footprint-" + cell.failure() + ":"
                                    + (x + dx) + "," + (z + dz);
                    } else if (!cell.fluid()) counts.merge("dry", 1, Integer::sum);
                    if (foundation == SOLID && cell.feet() < base) {
                        counts.merge("foundationGap", 1, Integer::sum);
                        if (first.equals("none"))
                            first = "foundation-gap:" + (x + dx) + "," + (z + dz)
                                    + ":feet=" + cell.feet() + ":base=" + base;
                    }
                    if (cell.feet() > deckFeet && mask.apron(x, z)) {
                        counts.merge("exteriorApronOverburden", 1, Integer::sum);
                    } else if (cell.feet() > deckFeet) {
                        int cut = cell.feet() - deckFeet;
                        boolean fullyAuthored = true;
                        boolean explicitAir = true;
                        for (int y = box.minY() + 5; y < cell.feet() - shift; y++) {
                            byte voxel = mask.at(x, y, z);
                            fullyAuthored &= voxel != OMITTED;
                            explicitAir &= voxel == AIR;
                        }
                        boolean authoredFit = fullyAuthored
                                && (cut <= MAX_EMBED_ABOVE_DECK
                                        || deck == SOLID && cut <= MAX_AUTHORED_CUT
                                        && explicitAir);
                        List<BlockPos> grade = authoredFit ? null
                                : gradeCuts(mask, x, z, shift, dx, dz, cell);
                        boolean allowed = authoredFit || grade != null;
                        if (grade != null) {
                            gradeColumns.add(BlockPos.asLong(x + dx, 0, z + dz));
                            cuts.addAll(grade);
                            counts.merge("boundedNaturalGrade", 1, Integer::sum);
                            if (gradeColumns.size() > MAX_GRADE_COLUMNS
                                    && first.equals("none"))
                                first = "grade-budget:" + gradeColumns.size();
                        } else counts.merge(allowed ? cut > MAX_EMBED_ABOVE_DECK
                                ? "authoredCut" : "boundedEmbed" : "unsafeEmbed",
                                1, Integer::sum);
                        if (!allowed && first.equals("none"))
                            first = "unsafe-embed:" + (x + dx) + "," + (z + dz)
                                    + ":feet=" + cell.feet() + ":deck=" + deckFeet
                                    + ":cut=" + cut + ":deckVoxel=" + deck
                                    + ":fullyAuthored=" + fullyAuthored
                                    + ":explicitAir=" + explicitAir;
                    }
                }
            }
            // A wet neighbor can enter an authored open edge or an untouched gap.
            // Inspect the entire rectangle perimeter, not only the public gate.
            for (int x = box.minX() - 1; x <= box.maxX() + 1; x++) {
                for (int z = box.minZ() - 1; z <= box.maxZ() + 1; z++) {
                    if (!FIT_DIAGNOSTICS && !first.equals("none"))
                        return rejectedFit(first);
                    int insideX = Math.max(box.minX(), Math.min(box.maxX(), x));
                    int insideZ = Math.max(box.minZ(), Math.min(box.maxZ(), z));
                    if (insideX == x && insideZ == z
                            || insideX != x && insideZ != z) continue;
                    ApproachCell neighbor = cachedSample(context, cache,
                            x + dx, z + dz);
                    counts.merge("perimeter", 1, Integer::sum);
                    if (neighbor == null) {
                        counts.merge("perimeterInvalid", 1, Integer::sum);
                        if (first.equals("none"))
                            first = "perimeter-invalid:" + (x + dx) + ","
                                    + (z + dz);
                    } else if (neighbor.fluid() && neighbor.fluidY() >= base
                            && mask.at(insideX, neighbor.fluidY() - shift,
                                    insideZ) != SOLID) {
                        counts.merge("perimeterFluid", 1, Integer::sum);
                        if (first.equals("none"))
                            first = "perimeter-fluid:" + (x + dx) + "," + (z + dz)
                                    + ":fluidY=" + neighbor.fluidY()
                                    + ":edge=" + (insideX + dx) + ","
                                    + (insideZ + dz);
                    }
                    if (neighbor != null) {
                        for (int y = base; y <= Math.min(box.maxY() + shift,
                                neighbor.feet() + 2); y++) {
                            if (neighbor.noise().getBlock(y).getFluidState().isEmpty())
                                continue;
                            counts.merge("perimeterFluidVoxels", 1, Integer::sum);
                            if (mask.at(insideX, y - shift, insideZ) != SOLID) {
                                counts.merge("perimeterUnsealedVoxels", 1, Integer::sum);
                                if (first.equals("none"))
                                    first = "perimeter-unsealed-fluid-voxel:"
                                            + (x + dx) + "," + y + "," + (z + dz);
                            }
                        }
                    }
                }
            }
        }
        if (first.equals("none")) {
            Set<BlockPos> unique = new HashSet<>(cuts);
            if (unique.size() != cuts.size()) first = "overlapping-grade-cuts";
            for (BlockPos cut : cuts) {
                for (PieceMask mask : masks) {
                    if (mask.at(cut.getX() - dx, cut.getY() - shift,
                            cut.getZ() - dz) != OMITTED) {
                        first = "grade-over-authored-voxel:" + cut;
                        break;
                    }
                }
                if (!first.equals("none")) break;
            }
        }
        boolean safe = first.equals("none");
        return new FitSurvey(safe, (safe ? "fit-ok" : "fit-reject")
                + " first=" + first + " histogram=" + counts
                + " gradeColumns=" + gradeColumns.size()
                + " gradeBlocks=" + cuts.size() + " cache=" + cache.size());
    }

    private static FitSurvey rejectedFit(String first) {
        return new FitSurvey(false, "fit-reject first=" + first
                + " histogram=skipped (set -Dgems.aliFitDiagnostics=true for full survey)");
    }

    private static ApproachCell cachedSample(GenerationContext context,
            Map<Long, ApproachCell> cache, int x, int z) {
        long key = BlockPos.asLong(x, 0, z);
        if (!cache.containsKey(key)) cache.put(key, sample(context, x, z));
        return cache.get(key);
    }

    /** Place only the cuts accepted by the noise preflight, in this new chunk. */
    @Override
    public void afterPlace(WorldGenLevel world, StructureManager structures,
            ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
            ChunkPos chunk, PiecesContainer container) {
        if (!(world instanceof WorldGenRegion region) || !region.getCenter().equals(chunk)
                || region.getChunk(chunk.x(), chunk.z()).getPersistedStatus()
                        .isOrAfter(ChunkStatus.FULL)) return;
        List<PoolElementStructurePiece> pieces = new ArrayList<>();
        Set<String> names = new HashSet<>();
        PoolElementStructurePiece root = null, carrier = null;
        for (StructurePiece piece : container.pieces()) {
            if (!(piece instanceof PoolElementStructurePiece pooled)
                    || !(pooled.getElement() instanceof SinglePoolElement single)) return;
            String name = single.getTemplateLocation().toString();
            if (!Set.of(ROOT, ATLAS, CHAPEL, ARRIVAL_REFERENCE).contains(name)
                    || !names.add(name)) return;
            if (name.equals(ROOT)) {
                if (root != null) return;
                root = pooled;
            }
            if (name.equals(ARRIVAL_REFERENCE)) carrier = pooled;
            pieces.add(pooled);
        }
        if (pieces.size() != 4 || names.size() != 4 || root == null
                || carrier == null || !sameHorizontalBox(carrier.getBoundingBox(),
                        northArrivalApron(root))
                || carrier.getBoundingBox().minY()
                        != root.getBoundingBox().minY() + 5) return;
        StructureTemplateManager manager = region.getLevel().getServer()
                .getStructureTemplateManager();
        List<PieceMask> masks = new ArrayList<>(3);
        for (PoolElementStructurePiece piece : pieces) {
            if (piece == carrier) continue;
            BoundingBox box = piece.getBoundingBox();
            if (box.maxX() < chunkBox.minX() || box.minX() > chunkBox.maxX()
                    || box.maxZ() < chunkBox.minZ()
                    || box.minZ() > chunkBox.maxZ()) continue;
            PieceMask mask = PieceMask.read(manager, piece);
            if (mask == null) return;
            masks.add(mask);
        }
        if (masks.isEmpty()) {
            AliArrivalTransition.gradeCenterChunk(region, generator, chunkBox,
                    root, carrier, container.pieces());
            return;
        }
        Map<BlockPos, BlockState> cuts = new HashMap<>();
        Set<Long> columns = new HashSet<>();
        for (PieceMask mask : masks) {
            BoundingBox box = mask.box();
            int minX = Math.max(box.minX(), chunkBox.minX());
            int maxX = Math.min(box.maxX(), chunkBox.maxX());
            int minZ = Math.max(box.minZ(), chunkBox.minZ());
            int maxZ = Math.min(box.maxZ(), chunkBox.maxZ());
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean authored = false;
                    for (int y = box.minY(); y <= box.maxY(); y++)
                        authored |= mask.at(x, y, z) != OMITTED;
                    if (!authored || mask.apron(x, z)) continue;
                    boolean omittedNearDeck = false;
                    for (int y = box.minY() + 5;
                            y < box.minY() + 5 + MAX_GRADE_HEIGHT; y++)
                        omittedNearDeck |= mask.at(x, y, z) == OMITTED;
                    if (!omittedNearDeck) continue;
                    ApproachCell cell = sample(generator, region, x, z);
                    if (cell == null || cell.feet() <= box.minY() + 5) continue;
                    boolean fullyAuthored = true;
                    for (int y = box.minY() + 5; y < cell.feet(); y++)
                        fullyAuthored &= mask.at(x, y, z) != OMITTED;
                    if (fullyAuthored) continue;
                    List<BlockPos> grade = gradeCuts(mask, x, z, 0, 0, 0, cell);
                    if (grade == null) return;
                    columns.add(BlockPos.asLong(x, 0, z));
                    if (columns.size() > MAX_GRADE_COLUMNS) return;
                    for (BlockPos pos : grade) {
                        if (!chunkBox.isInside(pos) || !region.ensureCanWrite(pos)
                                || cuts.putIfAbsent(pos,
                                        cell.noise().getBlock(pos.getY())) != null) return;
                    }
                }
            }
        }
        for (var cut : cuts.entrySet()) {
            BlockPos pos = cut.getKey();
            for (PieceMask mask : masks)
                if (mask.at(pos.getX(), pos.getY(), pos.getZ()) != OMITTED) return;
            BlockState found = region.getBlockState(pos);
            if (region.getBlockEntity(pos) != null
                    || !found.isAir() && !found.equals(cut.getValue())) return;
        }
        for (BlockPos pos : cuts.keySet()) {
            if (!region.getBlockState(pos).isAir())
                region.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
        AliArrivalTransition.gradeCenterChunk(region, generator, chunkBox,
                root, carrier, container.pieces());
    }

    private static void movePlannedPiece(PoolElementStructurePiece piece,
            int dx, int shift, int dz) {
        if (dx == 0 && shift == 0 && dz == 0) return;
        List<JigsawJunction> moved = piece.getJunctions().stream()
                .map(junction -> new JigsawJunction(junction.getSourceX() + dx,
                        junction.getSourceGroundY() + shift,
                        junction.getSourceZ() + dz, junction.getDeltaY(),
                        junction.getDestProjection())).toList();
        piece.move(dx, shift, dz);
        piece.getJunctions().clear();
        piece.getJunctions().addAll(moved);
    }

    private record ApproachCell(int feet, boolean solid, String support,
                                String wetBlock, int fluidY, String obstruction,
                                NoiseColumn noise) {
        boolean fluid() { return !wetBlock.isEmpty(); }
        boolean clear() { return obstruction.isEmpty(); }
        boolean walkable() { return solid && !fluid() && clear(); }
        String failure() {
            if (fluid()) return "fluid:" + wetBlock;
            if (!solid) return "unsupported:" + support;
            if (!clear()) return "blocked-headroom:" + obstruction;
            return "none";
        }
    }

    private record ApproachNode(int depth, int cross) { }
    private record ApproachSurvey(boolean connected, String detail,
                                  List<ApproachNode> route) { }

    @FunctionalInterface
    private interface ColumnSampler {
        ApproachCell at(int x, int z);
    }

    /** Fixed 20 by 9 noise-column survey; no chunks or terrain writes. */
    private static ApproachSurvey dryApproach(GenerationContext context,
                                               PoolElementStructurePiece root,
                                               int shift, int dx, int dz,
                                               Map<Long, ApproachCell> cache) {
        return surveyApproach(root, shift, dx, dz,
                (x, z) -> cachedSample(context, cache, x, z));
    }

    static List<BlockPos> arrivalRoute(WorldGenRegion region,
            ChunkGenerator generator, PoolElementStructurePiece root) {
        ApproachSurvey result = surveyApproach(root, 0, 0, 0,
                (x, z) -> sample(generator, region, x, z));
        if (!result.connected()) return List.of();
        Direction outward = root.getRotation().rotate(Direction.NORTH);
        Direction lateral = root.getRotation().rotate(Direction.EAST);
        BlockPos door = StructureTemplate.transform(new BlockPos(23, 0, 0),
                Mirror.NONE, root.getRotation(), BlockPos.ZERO)
                .offset(root.getPosition());
        List<BlockPos> route = new ArrayList<>(result.route().size());
        for (ApproachNode node : result.route()) {
            BlockPos column = door.relative(outward, node.depth())
                    .relative(lateral, node.cross());
            ApproachCell cell = sample(generator, region,
                    column.getX(), column.getZ());
            if (cell == null || !cell.walkable()) return List.of();
            route.add(column.atY(cell.feet() - 1));
        }
        return List.copyOf(route);
    }

    static int walkableFeet(WorldGenRegion region, ChunkGenerator generator,
            int x, int z) {
        ApproachCell cell = sample(generator, region, x, z);
        return cell != null && cell.walkable()
                ? cell.feet() : Integer.MIN_VALUE;
    }

    private static ApproachSurvey surveyApproach(PoolElementStructurePiece root,
            int shift, int dx, int dz, ColumnSampler sampler) {
        Direction outward = root.getRotation().rotate(Direction.NORTH);
        Direction lateral = root.getRotation().rotate(Direction.EAST);
        BlockPos door = StructureTemplate.transform(new BlockPos(23, 0, 0),
                Mirror.NONE, root.getRotation(), BlockPos.ZERO)
                .offset(root.getPosition()).offset(dx, 0, dz);
        int doorFeet = root.getBoundingBox().minY() + 5 + shift;
        ApproachCell[][] cells = new ApproachCell[APPROACH_LENGTH + 1]
                [APPROACH_HALF_WIDTH * 2 + 1];
        int dry = 0, fluid = 0, unsupported = 0, blocked = 0, invalid = 0;
        int sourceDry = 0;
        for (int depth = 1; depth <= APPROACH_LENGTH; depth++) {
            for (int cross = -APPROACH_HALF_WIDTH;
                    cross <= APPROACH_HALF_WIDTH; cross++) {
                BlockPos pos = door.relative(outward, depth).relative(lateral, cross);
                ApproachCell cell = sampler.at(pos.getX(), pos.getZ());
                cells[depth][cross + APPROACH_HALF_WIDTH] = cell;
                if (cell == null) invalid++;
                else if (cell.fluid()) fluid++;
                else if (!cell.solid()) unsupported++;
                else if (!cell.clear()) blocked++;
                else {
                    dry++;
                    if (depth >= BANK_START) sourceDry++;
                }
            }
        }

        boolean[][] seen = new boolean[APPROACH_LENGTH + 1]
                [APPROACH_HALF_WIDTH * 2 + 1];
        ArrayDeque<ApproachNode> frontier = new ArrayDeque<>();
        Map<ApproachNode, ApproachNode> previous = new HashMap<>();
        int gradeEdges = 0, reached = 0, maxDepth = 0;
        String firstFailure = "none";
        // The route must still connect to the authored central entrance.
        for (int cross : new int[]{0, -1, 1}) {
            ApproachCell cell = cells[1][cross + APPROACH_HALF_WIDTH];
            if (cell == null || !cell.walkable()
                    || !threeWide(cells, 1, cross)) {
                if (firstFailure.equals("none"))
                    firstFailure = "1:" + cross + ":three-wide:"
                            + failure(1, cross, cell);
            } else if (Math.abs(cell.feet() - doorFeet) > 1) {
                gradeEdges++;
                if (firstFailure.equals("none"))
                    firstFailure = "1:" + cross + ":door-grade:"
                            + doorFeet + "->" + cell.feet();
            } else {
                seen[1][cross + APPROACH_HALF_WIDTH] = true;
                ApproachNode start = new ApproachNode(1, cross);
                previous.put(start, null);
                frontier.addLast(start);
            }
        }
        boolean gateReached = !frontier.isEmpty();
        while (!frontier.isEmpty()) {
            ApproachNode node = frontier.removeFirst();
            reached++;
            maxDepth = Math.max(maxDepth, node.depth());
            if (node.depth() >= BANK_START) {
                List<ApproachNode> route = new ArrayList<>();
                for (ApproachNode at = node; at != null; at = previous.get(at))
                    route.add(at);
                java.util.Collections.reverse(route);
                return new ApproachSurvey(true, "source=" + node.depth() + ":"
                        + node.cross() + " reached=" + reached
                        + " dry=" + dry + " fluid=" + fluid
                        + " unsupported=" + unsupported + " blocked=" + blocked
                        + " invalid=" + invalid + " gradeEdges=" + gradeEdges,
                        List.copyOf(route));
            }
            ApproachCell here = cells[node.depth()][node.cross() + APPROACH_HALF_WIDTH];
            for (int[] delta : new int[][]{{1, 0}, {0, -1}, {0, 1}, {-1, 0}}) {
                int depth = node.depth() + delta[0];
                int cross = node.cross() + delta[1];
                if (depth < 1 || depth > APPROACH_LENGTH
                        || Math.abs(cross) > APPROACH_HALF_WIDTH
                        || seen[depth][cross + APPROACH_HALF_WIDTH]) continue;
                ApproachCell next = cells[depth][cross + APPROACH_HALF_WIDTH];
                if (next == null || !next.walkable()
                        || !threeWide(cells, depth, cross)) {
                    if (firstFailure.equals("none"))
                        firstFailure = depth + ":" + cross + ":three-wide:"
                                + failure(depth, cross, next);
                    continue;
                }
                if (Math.abs(next.feet() - here.feet()) > 1) {
                    gradeEdges++;
                    if (firstFailure.equals("none"))
                        firstFailure = depth + ":" + cross + ":grade:"
                                + here.feet() + "->" + next.feet();
                    continue;
                }
                seen[depth][cross + APPROACH_HALF_WIDTH] = true;
                ApproachNode nextNode = new ApproachNode(depth, cross);
                previous.put(nextNode, node);
                frontier.addLast(nextNode);
            }
        }
        String reason = !gateReached ? "gate-disconnected"
                : sourceDry == 0 ? "no-dry-bank" : "no-connected-bank";
        return new ApproachSurvey(false, reason
                + " doorFeet=" + doorFeet + " reached=" + reached
                + " maxDepth=" + maxDepth + " sourceDry=" + sourceDry
                + " dry=" + dry + " fluid=" + fluid
                + " unsupported=" + unsupported + " blocked=" + blocked
                + " invalid=" + invalid + " gradeEdges=" + gradeEdges
                + " firstFailure=" + firstFailure, List.of());
    }

    private static boolean threeWide(ApproachCell[][] cells, int depth,
            int cross) {
        ApproachCell middle = cells[depth][cross + APPROACH_HALF_WIDTH];
        if (middle == null || !middle.walkable()
                || Math.abs(cross) >= APPROACH_HALF_WIDTH) return false;
        for (int side : new int[]{-1, 1}) {
            ApproachCell shoulder = cells[depth]
                    [cross + side + APPROACH_HALF_WIDTH];
            if (shoulder == null || !shoulder.walkable()
                    || Math.abs(shoulder.feet() - middle.feet()) > MAX_GRADE_HEIGHT)
                return false;
        }
        return true;
    }

    private static String failure(int depth, int cross, ApproachCell cell) {
        return depth + ":" + cross + ":"
                + (cell == null ? "invalid-height" : cell.failure());
    }

    private static ApproachCell sample(GenerationContext context, int x, int z) {
        ChunkGenerator generator = context.chunkGenerator();
        int minY = context.heightAccessor().getMinY();
        int maxY = context.heightAccessor().getMaxY();
        // The vanilla noise generator computes the same column for both calls.
        // Keep the original height API for other generators, which may not.
        if (generator.getClass() != NoiseBasedChunkGenerator.class) {
            int feet = generator.getBaseHeight(x, z,
                    Heightmap.Types.WORLD_SURFACE_WG,
                    context.heightAccessor(), context.randomState());
            if (feet <= minY || feet + 3 >= maxY) return null;
            NoiseColumn noise = generator.getBaseColumn(x, z,
                    context.heightAccessor(), context.randomState());
            return sample(minY, maxY, feet, noise);
        }
        NoiseColumn noise = generator.getBaseColumn(x, z,
                context.heightAccessor(), context.randomState());
        int feet = columnFeet(noise, minY, maxY);
        return sample(minY, maxY, feet, noise);
    }

    private static ApproachCell sample(ChunkGenerator generator,
            WorldGenRegion region, int x, int z) {
        var state = region.getLevel().getChunkSource().randomState();
        int minY = region.getMinY();
        int maxY = region.getMaxY();
        if (generator.getClass() != NoiseBasedChunkGenerator.class) {
            int feet = generator.getBaseHeight(x, z,
                    Heightmap.Types.WORLD_SURFACE_WG, region, state);
            if (feet <= minY || feet + 3 >= maxY) return null;
            NoiseColumn noise = generator.getBaseColumn(x, z, region, state);
            return sample(minY, maxY, feet, noise);
        }
        NoiseColumn noise = generator.getBaseColumn(x, z, region, state);
        return sample(minY, maxY, columnFeet(noise, minY, maxY), noise);
    }

    private static int columnFeet(NoiseColumn noise, int minY, int maxY) {
        for (int y = maxY - 1; y >= minY; y--)
            if (Heightmap.Types.WORLD_SURFACE_WG.isOpaque().test(noise.getBlock(y)))
                return y + 1;
        return minY;
    }

    private static ApproachCell sample(int minY, int maxY, int feet,
            NoiseColumn noise) {
        if (feet <= minY || feet + 3 >= maxY) return null;
        BlockState support = noise.getBlock(feet - 1);
        String supportId = BuiltInRegistries.BLOCK.getKey(support.getBlock()).toString();
        String wetBlock = support.getFluidState().isEmpty() ? "" : supportId;
        int fluidY = wetBlock.isEmpty() ? Integer.MIN_VALUE : feet - 1;
        String obstruction = "";
        for (int y = feet; y < feet + 3; y++) {
            BlockState state = noise.getBlock(y);
            if (!state.isAir() && obstruction.isEmpty())
                obstruction = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            if (!state.getFluidState().isEmpty() && wetBlock.isEmpty()) {
                wetBlock = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                fluidY = y;
            }
        }
        return new ApproachCell(feet, support.isSolid(), supportId,
                wetBlock, fluidY, obstruction, noise);
    }

    private static Optional<GenerationStub> reject(GenerationContext context, String reason) {
        int count = REJECTED.incrementAndGet();
        if (FIT_DIAGNOSTICS)
            System.out.println("ALI_DRY_ARRIVAL_REJECT chunk=" + context.chunkPos()
                    + " reason=" + reason + " rejected=" + count);
        return Optional.empty();
    }

    @Override
    public StructureType<?> type() { return TYPE; }
}
