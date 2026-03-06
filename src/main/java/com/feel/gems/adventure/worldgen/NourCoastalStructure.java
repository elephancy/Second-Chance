package com.feel.gems.adventure.worldgen;

import com.feel.gems.GemsMod;
import com.feel.gems.adventure.worldgen.site.BoundedJigsawStart;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

/** New harbor starts stand on shallow seabed beside a clear boat approach. */
public final class NourCoastalStructure extends Structure implements BoundedJigsawStart {
    public static final MapCodec<NourCoastalStructure> CODEC = JigsawStructure.CODEC.xmap(
            NourCoastalStructure::new, structure -> structure.jigsaw);
    private static final StructureType<NourCoastalStructure> TYPE = () -> CODEC;
    private static final Set<String> V5 = Set.of(
            "gems:nour_signal_harbor_station_v5",
            "gems:nour_boathouse_station_v5",
            "gems:nour_foghouse_station_v5");
    private static final LongAdder JIGSAW = new LongAdder();
    private static final LongAdder PIECES = new LongAdder();
    private static final LongAdder[] SURVEY = java.util.Arrays.stream(
            NourCoastalNoiseSurvey.Reason.values())
            .map(ignored -> new LongAdder()).toArray(LongAdder[]::new);
    private final JigsawStructure jigsaw;

    private NourCoastalStructure(JigsawStructure jigsaw) {
        super(new StructureSettings(jigsaw.biomes(), jigsaw.spawnOverrides(),
                jigsaw.step(), jigsaw.terrainAdaptation()));
        this.jigsaw = jigsaw;
    }

    @Override
    public JigsawStructure reservationDelegate() { return jigsaw; }

    @Override
    public int reservationExtraReachBlocks() { return 12; }

    @Override
    public List<BoundingBox> reservationApproaches(StructureStart start) {
        PoolElementStructurePiece root = null;
        Set<String> names = new java.util.HashSet<>();
        for (StructurePiece piece : start.getPieces()) {
            if (!(piece instanceof PoolElementStructurePiece pooled)
                    || !(pooled.getElement() instanceof SinglePoolElement single)) return null;
            String name = single.getTemplateLocation().toString();
            names.add(name);
            if (name.equals("gems:nour_signal_harbor_station_v5")) root = pooled;
        }
        if (start.getPieces().size() != 3 || !names.equals(V5) || root == null
                || root.getBoundingBox().getXSpan() != 47
                || root.getBoundingBox().getZSpan() != 47) return null;
        // The three-lane boat approach is mandatory water from the south
        // mooring to twelve blocks beyond the saved root. Distant sea-view
        // samples are visual context, not a traversed or authored lane.
        return List.of(BoundedJigsawStart.localBox(root, 22, 47, 24, 58));
    }

    public static void init() {
        Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
                Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "nour_coastal_v1"), TYPE);
    }

    public static String candidateCounts() {
        StringBuilder result = new StringBuilder("jigsaw=").append(JIGSAW.sum())
                .append(" pieces=").append(PIECES.sum());
        for (NourCoastalNoiseSurvey.Reason reason : NourCoastalNoiseSurvey.Reason.values()) {
            result.append(' ').append(reason).append('=').append(SURVEY[reason.ordinal()].sum());
        }
        return result.toString();
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        Optional<GenerationStub> candidate = jigsaw.findValidGenerationPoint(context);
        if (candidate.isEmpty()) {
            JIGSAW.increment();
            return Optional.empty();
        }
        GenerationStub stub = candidate.get();
        StructurePiecesBuilder builder = stub.getPiecesBuilder();
        List<PoolElementStructurePiece> pieces = new ArrayList<>();
        Set<String> names = new java.util.HashSet<>();
        for (StructurePiece piece : builder.build().pieces()) {
            if (!(piece instanceof PoolElementStructurePiece pooled)
                    || !(pooled.getElement() instanceof SinglePoolElement single)) {
                PIECES.increment();
                return Optional.empty();
            }
            pieces.add(pooled);
            names.add(single.getTemplateLocation().toString());
        }
        if (pieces.size() != 3 || !names.equals(V5)) {
            PIECES.increment();
            return Optional.empty();
        }
        PoolElementStructurePiece root = pieces.stream()
                .filter(piece -> piece.getElement() instanceof SinglePoolElement single
                        && single.getTemplateLocation().toString().equals(
                            "gems:nour_signal_harbor_station_v5"))
                .findFirst().orElseThrow();
        int originalRootY = root.getPosition().getY();
        NourCoastalNoiseSurvey.Reason farthest = NourCoastalNoiseSurvey.Reason.HEIGHT;
        // An ocean biome projects a jigsaw to the sea surface. Search only
        // bounded dock elevations, moving the whole planned jigsaw
        // (including its junctions) before the saved start is committed.
        for (int baseY = 60; baseY <= 62; baseY++) {
            int shift = baseY - root.getPosition().getY();
            for (PoolElementStructurePiece piece : pieces) movePlannedPiece(piece, shift);
            NourCoastalNoiseSurvey.Reason reason = NourCoastalNoiseSurvey.assess(
                    context, root, pieces);
            if (reason == NourCoastalNoiseSurvey.Reason.ACCEPT) {
                SURVEY[reason.ordinal()].increment();
                return Optional.of(new GenerationStub(stub.position().offset(
                        0, baseY - originalRootY, 0), Either.right(builder)));
            }
            if (reason.ordinal() > farthest.ordinal()) farthest = reason;
        }
        SURVEY[farthest.ordinal()].increment();
        return Optional.empty();
    }

    private static void movePlannedPiece(PoolElementStructurePiece piece, int shift) {
        if (shift == 0) return;
        // PoolElementStructurePiece.move updates its position and box, but
        // Minecraft 26.3 does not shift its serialized terrain junctions.
        // Their ground Y must follow the three-piece body as one unit.
        List<JigsawJunction> moved = piece.getJunctions().stream()
                .map(junction -> new JigsawJunction(junction.getSourceX(),
                        junction.getSourceGroundY() + shift,
                        junction.getSourceZ(), junction.getDeltaY(),
                        junction.getDestProjection()))
                .toList();
        piece.move(0, shift, 0);
        piece.getJunctions().clear();
        piece.getJunctions().addAll(moved);
    }

    /** Trestle beams and short piles are entirely beneath the saved v5 pieces. */
    @Override
    public void afterPlace(WorldGenLevel world, StructureManager structures,
            ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
            ChunkPos chunk, PiecesContainer container) {
        if (!(world instanceof WorldGenRegion region) || !region.getCenter().equals(chunk)
                || region.getChunk(chunk.x(), chunk.z()).getPersistedStatus()
                        .isOrAfter(ChunkStatus.FULL)) return;
        for (StructurePiece piece : container.pieces()) {
            if (!(piece instanceof PoolElementStructurePiece pooled)
                    || !(pooled.getElement() instanceof SinglePoolElement single)
                    || !V5.contains(single.getTemplateLocation().toString())) continue;
            BoundingBox box = piece.getBoundingBox();
            int y = box.minY() - 1;
            int[] xs = {box.minX() + 4, (box.minX() + box.maxX()) / 2, box.maxX() - 4};
            int[] zs = {box.minZ() + 4, (box.minZ() + box.maxZ()) / 2, box.maxZ() - 4};
            for (int x : xs) for (int z : zs) pile(region, chunkBox, x, y, z);
            for (int x = Math.max(box.minX() + 4, chunkBox.minX());
                    x <= Math.min(box.maxX() - 4, chunkBox.maxX()); x++) {
                for (int z : zs) beam(region, chunkBox, x, y, z);
            }
            for (int z = Math.max(box.minZ() + 4, chunkBox.minZ());
                    z <= Math.min(box.maxZ() - 4, chunkBox.maxZ()); z++) {
                for (int x : xs) beam(region, chunkBox, x, y, z);
            }
        }
    }

    private static void pile(WorldGenRegion region, BoundingBox chunkBox,
                             int x, int y, int z) {
        for (int depth = 0; depth < 25; depth++) {
            BlockPos pos = new BlockPos(x, y - depth, z);
            if (!chunkBox.isInside(pos) || !region.ensureCanWrite(pos)) return;
            var state = region.getBlockState(pos);
            if (state.isSolid() && state.getFluidState().isEmpty()) return;
            if (!state.isAir() && !state.is(Blocks.WATER)) return;
            region.setBlock(pos, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), 2);
        }
    }

    private static void beam(WorldGenRegion region, BoundingBox chunkBox,
                             int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!chunkBox.isInside(pos) || !region.ensureCanWrite(pos)) return;
        var state = region.getBlockState(pos);
        if (state.isAir() || state.is(Blocks.WATER)) {
            region.setBlock(pos, Blocks.DARK_OAK_PLANKS.defaultBlockState(), 2);
        }
    }

    @Override
    public StructureType<?> type() { return TYPE; }
}
