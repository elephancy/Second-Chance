package com.feel.gems.adventure.worldgen;

import com.feel.gems.GemsMod;
import com.feel.gems.adventure.worldgen.site.BoundedJigsawStart;
import com.feel.gems.adventure.worldgen.site.StructureStartReservation;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Bounded v10 Fam foundation shared by the public and isolated trial starts. */
public final class FamV10TrialStructure extends Structure implements BoundedJigsawStart {
    private static final int MAX_RAISE = 8;
    private static final int MAX_SUPPORT_DEPTH = 8;
    // This structure has no production lattice. Keep one bounded rejection
    // census for the isolated natural-world trial instead of logging every
    // candidate chunk or changing its generation decisions.
    private static final AtomicInteger DIAGNOSTIC_ATTEMPTS = new AtomicInteger();
    private static final ConcurrentHashMap<String, LongAdder> REJECTIONS =
            new ConcurrentHashMap<>();
    private static final ResourceKey<Structure> PUBLIC = ResourceKey.create(
            Registries.STRUCTURE, id("fam_forum"));
    private static final ResourceKey<Structure> TRIAL = ResourceKey.create(
            Registries.STRUCTURE, id("fam_forum_v10_trial"));
    public static final MapCodec<FamV10TrialStructure> PUBLIC_CODEC =
            JigsawStructure.CODEC.xmap(jigsaw -> new FamV10TrialStructure(jigsaw, PUBLIC),
                    structure -> structure.jigsaw);
    public static final MapCodec<FamV10TrialStructure> TRIAL_CODEC =
            JigsawStructure.CODEC.xmap(jigsaw -> new FamV10TrialStructure(jigsaw, TRIAL),
                    structure -> structure.jigsaw);
    private static final StructureType<FamV10TrialStructure> PUBLIC_TYPE = () -> PUBLIC_CODEC;
    private static final StructureType<FamV10TrialStructure> TRIAL_TYPE = () -> TRIAL_CODEC;
    private static final Identifier ROOT = id("fam_forum_arcaded_court_v10");
    private static final Identifier STORY = id("fam_story_hall_reading_garden_v10");
    private static final Identifier SCHOOL = id("fam_open_school_workshop_v10");
    private static final Identifier REFERENCE = id("fam_east_reference_v1");
    private static final Identifier ARRIVAL_REFERENCE = id("fam_arrival_reference_v10");
    private static final int[][] ROOT_FEET = join(
            grid(new int[] {4, 12, 23, 34, 42}, new int[] {10, 20, 30, 40}),
            grid(new int[] {12, 23, 34}, new int[] {2, 8}));
    private static final int[][] STORY_FEET = join(
            join(grid(new int[] {4, 12, 20}, new int[] {8, 20, 32}),
                    grid(new int[] {28, 36}, new int[] {8, 20, 36})),
            grid(new int[] {4, 10, 17}, new int[] {35, 39}));
    private static final int[][] SCHOOL_FEET = join(
            join(grid(new int[] {24, 32, 39}, new int[] {8, 20, 32}),
                    grid(new int[] {4, 12}, new int[] {8, 20, 34})),
            grid(new int[] {4, 13, 22, 31, 37}, new int[] {35, 39}));

    private final JigsawStructure jigsaw;
    private final ResourceKey<Structure> self;

    private FamV10TrialStructure(JigsawStructure jigsaw, ResourceKey<Structure> self) {
        super(new StructureSettings(jigsaw.biomes(), jigsaw.spawnOverrides(),
                jigsaw.step(), jigsaw.terrainAdaptation()));
        this.jigsaw = jigsaw;
        this.self = self;
    }

    public static void init() {
        Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
                id("fam_forum_public_v1"), PUBLIC_TYPE);
        Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
                id("fam_forum_trial_v1"), TRIAL_TYPE);
        FamV10ArrivalFinish.init();
    }

    @Override
    public JigsawStructure reservationDelegate() { return jigsaw; }

    @Override
    public int reservationExtraReachBlocks() { return 0; }

    @Override
    public List<BoundingBox> reservationApproaches(StructureStart start) {
        // Saved v8 public starts keep their original pieces. Only a new v10
        // start uses this exact three-building family and its arrival carriers.
        PoolElementStructurePiece root = null, story = null, school = null;
        int arrivals = 0;
        for (StructurePiece piece : start.getPieces()) {
            if (!(piece instanceof PoolElementStructurePiece pooled)
                    || !(pooled.getElement() instanceof SinglePoolElement single)) return null;
            Identifier name = single.getTemplateLocation();
            if (ROOT.equals(name)) {
                if (root != null) return null;
                root = pooled;
            } else if (STORY.equals(name)) {
                if (story != null) return null;
                story = pooled;
            } else if (SCHOOL.equals(name)) {
                if (school != null) return null;
                school = pooled;
            }
            else if (ARRIVAL_REFERENCE.equals(name)) arrivals++;
            else if (!REFERENCE.equals(name)) return null;
        }
        return root != null && story != null && school != null && arrivals == 2
                ? List.of(FamV10ArrivalTerrace.approachBounds(root,
                                  root.getBoundingBox().minY()),
                          FamV10ArrivalTerrace.approachBounds(story,
                                  story.getBoundingBox().minY()),
                          FamV10ArrivalTerrace.approachBounds(school,
                                  school.getBoundingBox().minY())) : null;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        Optional<GenerationStub> candidate = jigsaw.findValidGenerationPoint(context);
        if (candidate.isEmpty()) return reject(context, "jigsaw-or-biome");
        GenerationStub stub = candidate.get();
        StructurePiecesBuilder builder = stub.getPiecesBuilder();
        if (builder.isEmpty()) return reject(context, "empty-jigsaw");
        PoolElementStructurePiece root = null, story = null, school = null;
        List<PoolElementStructurePiece> planned = new ArrayList<>(6);
        List<BoundingBox> occupied = new ArrayList<>(6);
        int arrivals = 0;
        for (StructurePiece piece : builder.build().pieces()) {
            if (!(piece instanceof PoolElementStructurePiece pooled)
                    || !(pooled.getElement() instanceof SinglePoolElement single))
                return reject(context, "non-single-piece");
            Identifier name = single.getTemplateLocation();
            if (ROOT.equals(name)) {
                if (root != null) return reject(context, "duplicate-root");
                root = pooled;
            } else if (STORY.equals(name)) {
                if (story != null) return reject(context, "duplicate-story");
                story = pooled;
            } else if (SCHOOL.equals(name)) {
                if (school != null) return reject(context, "duplicate-school");
                school = pooled;
            } else if (ARRIVAL_REFERENCE.equals(name)) arrivals++;
            else if (!REFERENCE.equals(name)) return reject(context, "foreign-piece");
            planned.add(pooled);
            occupied.add(pooled.getBoundingBox());
        }
        if (root == null || story == null || school == null || arrivals != 2)
            return reject(context, "incomplete-trio");
        if (!adjoins(root, story, Direction.EAST)
                || !adjoins(root, school, Direction.WEST))
            return reject(context, "nonadjacent-trio");
        List<Footing> sampled = new ArrayList<>();
        if (!sampleFootings(context, root, ROOT_FEET, sampled))
            return reject(context, "root-footings");
        if (!sampleFootings(context, story, STORY_FEET, sampled))
            return reject(context, "story-footings");
        if (!sampleFootings(context, school, SCHOOL_FEET, sampled))
            return reject(context, "school-footings");
        int baseY = root.getBoundingBox().minY();
        int highestGround = sampled.stream().mapToInt(Footing::groundFeet)
                .max().orElseThrow();
        int shift = Math.max(0, highestGround - baseY - 3);
        if (shift > MAX_RAISE) return reject(context, "height-raise");
        int finalBase = baseY + shift;
        if (sampled.stream().anyMatch(footing ->
                footing.groundFeet() < finalBase - MAX_SUPPORT_DEPTH))
            return reject(context, "support-depth");
        var finalBiome = context.biomeResolver().getNoiseBiome(
                QuartPos.fromBlock(stub.position().getX()),
                QuartPos.fromBlock(stub.position().getY() + shift),
                QuartPos.fromBlock(stub.position().getZ()));
        if (!context.validBiome().test(finalBiome))
            return reject(context, "shifted-biome");
        if (!FamV10ArrivalTerrace.preflightStart(context, root, finalBase)
                || !FamV10ArrivalTerrace.preflightStart(context, story, finalBase)
                || !FamV10ArrivalTerrace.preflightStart(context, school, finalBase))
            return reject(context, "arrival-terrain");
        occupied = occupied.stream().map(box -> new BoundingBox(
                box.minX(), box.minY() + shift, box.minZ(),
                box.maxX(), box.maxY() + shift, box.maxZ())).toList();
        List<BoundingBox> approaches = List.of(
                FamV10ArrivalTerrace.approachBounds(root, finalBase),
                FamV10ArrivalTerrace.approachBounds(story, finalBase),
                FamV10ArrivalTerrace.approachBounds(school, finalBase));
        StructureStartReservation.Verdict reservation = StructureStartReservation.inspect(
                context, Level.OVERWORLD, self, occupied, approaches);
        if (!reservation.clear()) return reject(context,
                "reservation-" + reservation.status() + ":" + reservation.reason());
        for (PoolElementStructurePiece piece : planned) movePlannedPiece(piece, shift);
        System.out.println("FAM_V10_ACCEPT id=" + self + " chunk=" + context.chunkPos()
                + " root=" + root.getPosition() + " rotation=" + root.getRotation()
                + " raise=" + shift + " minGround="
                + sampled.stream().mapToInt(Footing::groundFeet).min().orElseThrow()
                + " maxGround=" + highestGround);
        return Optional.of(new GenerationStub(stub.position().offset(0, shift, 0),
                Either.right(builder)));
    }

    private static Optional<GenerationStub> reject(GenerationContext context, String reason) {
        REJECTIONS.computeIfAbsent(reason, unused -> new LongAdder()).increment();
        int attempts = DIAGNOSTIC_ATTEMPTS.incrementAndGet();
        if (attempts == 1 || (attempts <= 8192 && attempts % 128 == 0)) {
            java.util.TreeMap<String, Long> counts = new java.util.TreeMap<>();
            REJECTIONS.forEach((key, value) -> counts.put(key, value.sum()));
            System.out.println("FAM_V10_REJECTIONS attempts=" + attempts
                    + " lastChunk=" + context.chunkPos() + " counts=" + counts);
        }
        return Optional.empty();
    }

    private record Footing(int groundFeet) { }

    /** Survey only the authored pier coordinates; never inspect or grade a halo. */
    private static boolean sampleFootings(GenerationContext context,
                                          PoolElementStructurePiece piece,
                                          int[][] samples, List<Footing> out) {
        for (int[] local : samples) {
            BlockPos point = StructureTemplate.transform(new BlockPos(local[0], 0, local[1]),
                    Mirror.NONE, piece.getRotation(), BlockPos.ZERO).offset(piece.getPosition());
            HushwireDryApproach.Column column = HushwireDryApproach.sample(
                    context, point.getX(), point.getZ());
            if (column == null || !column.dry() || !column.clear()) return false;
            out.add(new Footing(column.feet()));
        }
        return true;
    }

    private static void movePlannedPiece(PoolElementStructurePiece piece, int shift) {
        if (shift == 0) return;
        List<JigsawJunction> moved = piece.getJunctions().stream()
                .map(junction -> new JigsawJunction(junction.getSourceX(),
                        junction.getSourceGroundY() + shift,
                        junction.getSourceZ(), junction.getDeltaY(),
                        junction.getDestProjection())).toList();
        piece.move(0, shift, 0);
        piece.getJunctions().clear();
        piece.getJunctions().addAll(moved);
    }

    /** Extend only accepted v10 piers through air, in this new center chunk. */
    @Override
    public void afterPlace(WorldGenLevel world, StructureManager structures,
            ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
            ChunkPos chunk, PiecesContainer container) {
        if (!(world instanceof WorldGenRegion region) || !region.getCenter().equals(chunk)
                || region.getChunk(chunk.x(), chunk.z()).getPersistedStatus()
                        .isOrAfter(ChunkStatus.FULL)) return;
        PoolElementStructurePiece root = null, story = null, school = null;
        for (StructurePiece piece : container.pieces()) {
            if (!(piece instanceof PoolElementStructurePiece pooled)
                    || !(pooled.getElement() instanceof SinglePoolElement single)) return;
            Identifier name = single.getTemplateLocation();
            if (ROOT.equals(name)) {
                if (root != null) return;
                root = pooled;
            } else if (STORY.equals(name)) {
                if (story != null) return;
                story = pooled;
            } else if (SCHOOL.equals(name)) {
                if (school != null) return;
                school = pooled;
            } else if (!REFERENCE.equals(name) && !ARRIVAL_REFERENCE.equals(name)) return;
        }
        if (root == null || story == null || school == null
                || !adjoins(root, story, Direction.EAST)
                || !adjoins(root, school, Direction.WEST)) return;
        // The accepted start reserved each full-width apron against actual
        // foreign pieces. A remote reference alone is not a collision.
        List<Write> writes = new ArrayList<>();
        if (!preflightPiers(region, chunkBox, root, ROOT_FEET, writes)
                || !preflightPiers(region, chunkBox, story, STORY_FEET, writes)
                || !preflightPiers(region, chunkBox, school, SCHOOL_FEET, writes)) return;
        for (Write write : writes) region.setBlock(write.pos(), write.state(), 2);
        // Each wing has a sparse 24-cell carrier, so structure placement
        // invokes this pass in every approach chunk without a remote lookup.
        List<StructurePiece> pieces = container.pieces();
        List<BoundingBox> protectedPieces = pieces.stream()
                .map(StructurePiece::getBoundingBox).toList();
        FamV10ArrivalTerrace.applyCenterChunk(region, chunk, pieces, root, protectedPieces);
        FamV10ArrivalTerrace.applyCenterChunk(region, chunk, pieces, school, protectedPieces);
        FamV10ArrivalTerrace.applyCenterChunk(region, chunk, pieces, story, protectedPieces);
    }

    private record Write(BlockPos pos, BlockState state) { }

    private static boolean preflightPiers(WorldGenRegion region, BoundingBox chunkBox,
            PoolElementStructurePiece piece, int[][] samples, List<Write> writes) {
        int baseY = piece.getBoundingBox().minY();
        for (int[] local : samples) {
            BlockPos point = StructureTemplate.transform(new BlockPos(local[0], 0, local[1]),
                    Mirror.NONE, piece.getRotation(), BlockPos.ZERO).offset(piece.getPosition());
            if (point.getX() < chunkBox.minX() || point.getX() > chunkBox.maxX()
                    || point.getZ() < chunkBox.minZ() || point.getZ() > chunkBox.maxZ())
                continue;
            boolean grounded = false;
            for (int depth = 1; depth <= MAX_SUPPORT_DEPTH + 1; depth++) {
                BlockPos pos = new BlockPos(point.getX(), baseY - depth, point.getZ());
                if (!chunkBox.isInside(pos) || !region.ensureCanWrite(pos)
                        || region.getBlockEntity(pos) != null) return false;
                BlockState state = region.getBlockState(pos);
                if (!state.getFluidState().isEmpty() || state.is(BlockTags.LEAVES)
                        || state.is(BlockTags.LOGS)) return false;
                if (state.isSolid()) {
                    grounded = true;
                    break;
                }
                if (!state.isAir() || depth > MAX_SUPPORT_DEPTH) return false;
                writes.add(new Write(pos, Blocks.CHISELED_SANDSTONE.defaultBlockState()));
            }
            if (!grounded) return false;
        }
        return true;
    }

    private static boolean adjoins(PoolElementStructurePiece root,
                                   PoolElementStructurePiece child, Direction localSide) {
        BoundingBox a = root.getBoundingBox(), b = child.getBoundingBox();
        Direction side = root.getRotation().rotate(localSide);
        if (a.getXSpan() != 47 || a.getZSpan() != 47
                || b.getXSpan() != 41 || b.getZSpan() != 41
                || b.minY() != a.minY()) return false;
        return switch (side) {
            case EAST -> b.minX() == a.maxX() + 1 && overlap(a.minZ(), a.maxZ(), b.minZ(), b.maxZ()) >= 3;
            case WEST -> b.maxX() + 1 == a.minX() && overlap(a.minZ(), a.maxZ(), b.minZ(), b.maxZ()) >= 3;
            case SOUTH -> b.minZ() == a.maxZ() + 1 && overlap(a.minX(), a.maxX(), b.minX(), b.maxX()) >= 3;
            case NORTH -> b.maxZ() + 1 == a.minZ() && overlap(a.minX(), a.maxX(), b.minX(), b.maxX()) >= 3;
            default -> false;
        };
    }

    private static int overlap(int a0, int a1, int b0, int b1) {
        return Math.max(0, Math.min(a1, b1) - Math.max(a0, b0) + 1);
    }

    private static int[][] grid(int[] xs, int[] zs) {
        int[][] points = new int[xs.length * zs.length][2];
        int next = 0;
        for (int x : xs) for (int z : zs) points[next++] = new int[] {x, z};
        return points;
    }

    private static int[][] join(int[][] first, int[][] second) {
        int[][] combined = new int[first.length + second.length][];
        System.arraycopy(first, 0, combined, 0, first.length);
        System.arraycopy(second, 0, combined, first.length, second.length);
        return combined;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, path);
    }

    @Override
    public StructureType<?> type() { return self.equals(PUBLIC) ? PUBLIC_TYPE : TRIAL_TYPE; }
}
