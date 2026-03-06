package com.feel.gems.adventure.worldgen.site;

import com.feel.gems.GemsMod;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
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
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** A single saved, versioned body with a dimension-specific noise preflight. */
public final class DimensionChapterStructure extends Structure implements BoundedJigsawStart {
    public static final MapCodec<DimensionChapterStructure> CODEC =
            JigsawStructure.CODEC.xmap(DimensionChapterStructure::new,
                    structure -> structure.jigsaw);
    private static final StructureType<DimensionChapterStructure> TYPE = () -> CODEC;
    private static final ResourceKey<Structure> EMBER_V6 = ResourceKey.create(
            Registries.STRUCTURE, Identifier.fromNamespaceAndPath(GemsMod.MOD_ID,
                    "redline_ember_bridge_v6"));
    private static final Identifier EMBER_V6_TEMPLATE = Identifier.fromNamespaceAndPath(
            GemsMod.MOD_ID, "redline_ember_bridge_v6");
    private static final Identifier ASH_V5_TEMPLATE = Identifier.fromNamespaceAndPath(
            GemsMod.MOD_ID, "lastlight_ash_annex_v5");
    private static final Identifier END_V4_TEMPLATE = Identifier.fromNamespaceAndPath(
            GemsMod.MOD_ID, "unwritten_end_source_v4");
    private static final int V6_PUBLIC_AIR = 0x0fe0; // Local Y5..11.
    private final JigsawStructure jigsaw;

    private DimensionChapterStructure(JigsawStructure jigsaw) {
        super(new StructureSettings(jigsaw.biomes(), jigsaw.spawnOverrides(),
                jigsaw.step(), jigsaw.terrainAdaptation()));
        this.jigsaw = jigsaw;
    }

    @Override
    public JigsawStructure reservationDelegate() { return jigsaw; }

    @Override
    public int reservationExtraReachBlocks() { return 32; }

    @Override
    public int reservationMaximumHorizontalShiftBlocks() { return 16; }

    @Override
    public List<BoundingBox> reservationApproaches(StructureStart start) {
        if (start.getPieces().size() != 1
                || !(start.getPieces().getFirst() instanceof PoolElementStructurePiece pooled)
                || !(pooled.getElement() instanceof SinglePoolElement single)) return null;
        String name = single.getTemplateLocation().toString();
        BoundingBox box = pooled.getBoundingBox();
        int longSide = switch (name) {
            case "gems:lastlight_ash_annex_v5" -> 55;
            case "gems:redline_ember_bridge_v6" -> 71;
            case "gems:unwritten_end_source_v4" -> 47;
            default -> -1;
        };
        if (longSide < 0 || !((box.getXSpan() == 47 && box.getZSpan() == longSide)
                || (box.getZSpan() == 47 && box.getXSpan() == longSide))) return null;
        BoundingBox arrival = switch (name) {
            case "gems:lastlight_ash_annex_v5" ->
                    BoundedJigsawStart.localBox(pooled, 20, 55, 26, 62);
            case "gems:redline_ember_bridge_v6" ->
                    BoundedJigsawStart.localBox(pooled, 32, 71, 46, 86);
            case "gems:unwritten_end_source_v4" ->
                    BoundedJigsawStart.localBox(pooled, 20, -8, 26, -1);
            default -> throw new IllegalStateException("unrecognized dimension site");
        };
        return List.of(arrival);
    }

    /** Register before the three custom structure JSON files are decoded. */
    public static void init() {
        Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
                Identifier.fromNamespaceAndPath(GemsMod.MOD_ID,
                        "dimension_chapter_v1"), TYPE);
        DimensionSiteNoiseSurvey.initDiagnostics();
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        Optional<GenerationStub> planned = jigsaw.findValidGenerationPoint(context);
        if (planned.isEmpty()) return Optional.empty();
        GenerationStub stub = planned.get();
        StructurePiecesBuilder builder = stub.getPiecesBuilder();
        if (builder.isEmpty()) return Optional.empty();
        var pieces = builder.build().pieces();
        if (pieces.size() != 1) return Optional.empty();
        StructurePiece piece = pieces.getFirst();
        if (!(piece instanceof PoolElementStructurePiece pooled)
                || !(pooled.getElement() instanceof SinglePoolElement single)) {
            return Optional.empty();
        }
        DimensionSiteNoiseSurvey.Site site = switch (single.getTemplateLocation().toString()) {
            case "gems:lastlight_ash_annex_v5" -> DimensionSiteNoiseSurvey.Site.ASH_ANNEX_V5;
            case "gems:redline_ember_bridge_v6" -> DimensionSiteNoiseSurvey.Site.EMBER_BRIDGE_V6;
            case "gems:unwritten_end_source_v4" -> DimensionSiteNoiseSurvey.Site.END_SOURCE_V4;
            default -> null;
        };
        if (site == null) return Optional.empty();
        if (site == DimensionSiteNoiseSurvey.Site.EMBER_BRIDGE_V6) {
            return orientV6(context, stub, pooled, single);
        }
        if (site == DimensionSiteNoiseSurvey.Site.ASH_ANNEX_V5) {
            Direction east = pooled.getRotation().rotate(Direction.EAST);
            Direction south = pooled.getRotation().rotate(Direction.SOUTH);
            var selected = DimensionSiteNoiseSurvey.ashV5Base(
                    context, piece.getBoundingBox(), east, south);
            if (selected.isEmpty()) return Optional.empty();
            int shift = selected.getAsInt() - piece.getBoundingBox().minY();
            piece.move(0, shift, 0);
            if (!reservationClear(context, pooled, ASH_V5_TEMPLATE)) {
                return Optional.empty();
            }
            return Optional.of(new GenerationStub(stub.position().offset(0, shift, 0),
                    Either.right(builder)));
        }
        if (!DimensionSiteNoiseSurvey.accepts(context, piece.getBoundingBox(),
                site, pooled.getRotation().rotate(Direction.EAST),
                pooled.getRotation().rotate(Direction.SOUTH))) return Optional.empty();
        if (site == DimensionSiteNoiseSurvey.Site.END_SOURCE_V4
                && !reservationClear(context, pooled, END_V4_TEMPLATE))
            return Optional.empty();
        return Optional.of(new GenerationStub(stub.position(), Either.right(builder)));
    }

    /** Current trial versions must reserve both the body and mandatory arrival before saving a start. */
    private static boolean reservationClear(GenerationContext context,
                                            PoolElementStructurePiece piece,
                                            Identifier template) {
        BoundingBox arrival;
        ResourceKey<Level> dimension;
        if (ASH_V5_TEMPLATE.equals(template)) {
            arrival = BoundedJigsawStart.localBox(piece, 20, 55, 26, 62);
            dimension = Level.NETHER;
        } else if (EMBER_V6_TEMPLATE.equals(template)) {
            arrival = BoundedJigsawStart.localBox(piece, 32, 71, 46, 86);
            dimension = Level.NETHER;
        } else if (END_V4_TEMPLATE.equals(template)) {
            arrival = BoundedJigsawStart.localBox(piece, 20, -8, 26, -1);
            dimension = Level.END;
        } else {
            return false;
        }
        ResourceKey<Structure> self = ResourceKey.create(Registries.STRUCTURE, template);
        StructureStartReservation.Verdict verdict = StructureStartReservation.inspect(
                context, dimension, self, List.of(piece.getBoundingBox()), List.of(arrival));
        if (Boolean.getBoolean("gems.dimensionPlacementDiagnostics"))
            System.out.println("DIMENSION_CHAPTER_RESERVATION_TIMING template=" + template
                    + " chunk=" + context.chunkPos() + " status=" + verdict.status()
                    + " candidates=" + verdict.candidateChunks()
                    + " starts=" + verdict.generatedStarts()
                    + " pieces=" + verdict.comparedPieces()
                    + " elapsedMs=" + verdict.elapsedMillis());
        if (!verdict.clear()) {
            System.out.println("DIMENSION_CHAPTER_RESERVATION_REJECT template=" + template
                    + " chunk=" + context.chunkPos() + " status=" + verdict.status()
                    + " reason=" + verdict.reason());
        }
        return verdict.clear();
    }

    private static final int[][] V6_ROOT_SHIFTS = {
            {0, 0}, {8, 0}, {-8, 0}, {0, 8}, {0, -8},
            {16, 0}, {-16, 0}, {0, 16}, {0, -16}
    };

    /** Try bounded shifted roots and rotations, then save exactly one piece. */
    private static Optional<GenerationStub> orientV6(
            GenerationContext context, GenerationStub original,
            PoolElementStructurePiece planned, SinglePoolElement element) {
        var manager = context.structureTemplateManager();
        BoundingBox initial = element.getBoundingBox(manager, planned.getPosition(),
                planned.getRotation());
        if (!sameBox(initial, planned.getBoundingBox())) return Optional.empty();
        BlockPos rootLocal = new BlockPos(23, 4, 23);
        BlockPos rootWorld = StructureTemplate.transform(rootLocal, Mirror.NONE,
                planned.getRotation(), BlockPos.ZERO).offset(planned.getPosition());
        // The candidate chunk owns the saved start even when the selected
        // root marker lies in a neighbor. V1-v5 saved starts put the piece
        // origin at the owner chunk minimum; enforce that v6 invariant before
        // moving it so the staged set's separation proof remains valid.
        if (planned.getPosition().getX() != context.chunkPos().getMinBlockX()
                || planned.getPosition().getZ() != context.chunkPos().getMinBlockZ()) {
            return Optional.empty();
        }
        for (int[] shiftXZ : V6_ROOT_SHIFTS) {
            BlockPos shiftedRoot = rootWorld.offset(shiftXZ[0], 0, shiftXZ[1]);
            for (Rotation rotation : new Rotation[] {Rotation.NONE,
                    Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180,
                    Rotation.COUNTERCLOCKWISE_90}) {
                BlockPos rootOffset = StructureTemplate.transform(rootLocal,
                        Mirror.NONE, rotation, BlockPos.ZERO);
                BlockPos origin = shiftedRoot.subtract(rootOffset);
                BoundingBox bounds = element.getBoundingBox(manager, origin, rotation);
                if (bounds.getXSpan() != 47 && bounds.getXSpan() != 71) continue;
                var base = DimensionSiteNoiseSurvey.emberV6Base(context, bounds,
                        rotation.rotate(Direction.EAST), rotation.rotate(Direction.SOUTH));
                if (base.isEmpty()) continue;
                PoolElementStructurePiece selected = new PoolElementStructurePiece(
                        manager, element, origin, planned.getGroundLevelDelta(), rotation,
                        bounds, JigsawStructure.DEFAULT_LIQUID_SETTINGS);
                int shiftY = base.getAsInt() - bounds.minY();
                selected.move(0, shiftY, 0);
                if (!v6WithinReferenceHalo(context, selected)) continue;
                if (!reservationClear(context, selected, EMBER_V6_TEMPLATE)) continue;
                StructurePiecesBuilder selectedPieces = new StructurePiecesBuilder();
                selectedPieces.addPiece(selected);
                BlockPos center = new BlockPos(
                        (bounds.minX() + bounds.maxX()) / 2,
                        original.position().getY() + shiftY,
                        (bounds.minZ() + bounds.maxZ()) / 2);
                return Optional.of(new GenerationStub(center,
                        Either.right(selectedPieces)));
            }
        }
        return Optional.empty();
    }

    /** Vanilla creates references only from chunks in the start's eight-chunk halo. */
    private static boolean v6WithinReferenceHalo(GenerationContext context,
            PoolElementStructurePiece piece) {
        BoundingBox body = piece.getBoundingBox();
        BoundingBox runway = BoundedJigsawStart.localBox(piece, 32, 71, 46, 86);
        long startX = context.chunkPos().x();
        long startZ = context.chunkPos().z();
        for (BoundingBox box : List.of(body, runway)) {
            if ((box.minX() >> 4) < startX - 8 || (box.maxX() >> 4) > startX + 8
                    || (box.minZ() >> 4) < startZ - 8
                    || (box.maxZ() >> 4) > startZ + 8) return false;
        }
        return true;
    }

    private static boolean sameBox(BoundingBox first, BoundingBox second) {
        return first.minX() == second.minX() && first.minY() == second.minY()
                && first.minZ() == second.minZ() && first.maxX() == second.maxX()
                && first.maxY() == second.maxY() && first.maxZ() == second.maxZ();
    }

    /** Structure placement follows Nether vegetation; use its saved piece in this chunk. */
    @Override
    public void afterPlace(WorldGenLevel world, StructureManager structures,
                           ChunkGenerator generator, RandomSource random,
                           BoundingBox chunkBox, ChunkPos chunk, PiecesContainer container) {
        if (!(world instanceof WorldGenRegion region) || !region.getCenter().equals(chunk)
                || region.getChunk(chunk.x(), chunk.z()).getPersistedStatus()
                        .isOrAfter(ChunkStatus.FULL)
                || !DimensionV6GalleryMask.valid()
                || region.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                        .getValue(EMBER_V6) != this
                || container.pieces().size() != 1
                || !(container.pieces().getFirst() instanceof PoolElementStructurePiece piece)
                || !(piece.getElement() instanceof SinglePoolElement single)
                || !EMBER_V6_TEMPLATE.equals(single.getTemplateLocation())) return;
        for (DimensionV6GalleryMask.Column column : DimensionV6GalleryMask.columns()) {
            if (column.x() < 37 || column.x() > 41) continue;
            int bits = column.airBits() & V6_PUBLIC_AIR;
            while (bits != 0) {
                int y = Integer.numberOfTrailingZeros(bits);
                bits &= bits - 1;
                BlockPos rotated = StructureTemplate.transform(
                        new BlockPos(column.x(), y, column.z()), Mirror.NONE,
                        piece.getRotation(), BlockPos.ZERO);
                BlockPos pos = piece.getPosition().offset(rotated);
                if (!chunkBox.isInside(pos) || !piece.getBoundingBox().isInside(pos)
                        || !region.ensureCanWrite(pos)) continue;
                var state = region.getBlockState(pos);
                if (state.is(Blocks.WARPED_WART_BLOCK) || state.is(Blocks.WARPED_STEM)
                        || state.is(Blocks.SHROOMLIGHT)) {
                    region.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    @Override
    public StructureType<?> type() { return TYPE; }
}
