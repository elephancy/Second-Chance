package com.feel.gems.adventure.worldgen;

import com.feel.gems.GemsMod;
import com.feel.gems.adventure.worldgen.site.BoundedJigsawStart;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;

/** Rejects submerged sites before placing the current library. */
public final class OthmanThemedStructure extends Structure implements BoundedJigsawStart {
    public static final MapCodec<OthmanThemedStructure> CODEC = JigsawStructure.CODEC.xmap(
            OthmanThemedStructure::new, structure -> structure.jigsaw);
    private static final StructureType<OthmanThemedStructure> TYPE = () -> CODEC;

    private final JigsawStructure jigsaw;

    private OthmanThemedStructure(JigsawStructure jigsaw) {
        super(new StructureSettings(jigsaw.biomes(), jigsaw.spawnOverrides(),
                jigsaw.step(), jigsaw.terrainAdaptation()));
        this.jigsaw = jigsaw;
    }

    @Override
    public JigsawStructure reservationDelegate() { return jigsaw; }

    @Override
    public int reservationExtraReachBlocks() { return 17; }

    @Override
    public List<BoundingBox> reservationApproaches(StructureStart start) {
        PoolElementStructurePiece root = null;
        for (StructurePiece piece : start.getPieces()) {
            if (!(piece instanceof PoolElementStructurePiece pooled)
                    || !(pooled.getElement() instanceof SinglePoolElement single)) return null;
            String template = single.getTemplateLocation().toString();
            if (template.equals("gems:othman_library_archive_v11")) {
                if (root != null) return null;
                root = pooled;
            }
        }
        if (root == null || root.getBoundingBox().getXSpan() != 47
                || root.getBoundingBox().getZSpan() != 47) return null;
        // The checked woodland entry is 40 blocks from the root center,
        // seventeen beyond its 47-wide body. A symmetric apron also covers
        // all four rotations and the authored bank-side approaches.
        return List.of(BoundedJigsawStart.apron(root.getBoundingBox(), 17));
    }

    public static void init() {
        Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
                Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "othman_themed"), TYPE);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        Optional<GenerationStub> candidate = jigsaw.findValidGenerationPoint(context);
        if (candidate.isEmpty()) return Optional.empty();

        // Plan once and sample the combined hall and annex bounds before
        // placing blocks. Reuse the pieces so rotation and choices stay fixed.
        GenerationStub stub = candidate.get();
        StructurePiecesBuilder pieces = stub.getPiecesBuilder();
        if (pieces.isEmpty() || !hasMostlyDryFootprint(context, pieces.getBoundingBox())) {
            return Optional.empty();
        }
        return Optional.of(new GenerationStub(stub.position(), Either.right(pieces)));
    }

    private static boolean hasMostlyDryFootprint(GenerationContext context, BoundingBox bounds) {
        int centerX = (bounds.minX() + bounds.maxX()) / 2;
        int centerZ = (bounds.minZ() + bounds.maxZ()) / 2;
        int[] xs = {bounds.minX() + 4, centerX, bounds.maxX() - 4};
        int[] zs = {bounds.minZ() + 4, centerZ, bounds.maxZ() - 4};
        int wet = 0;

        for (int x : xs) {
            for (int z : zs) {
                int height = context.chunkGenerator().getBaseHeight(x, z,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        context.heightAccessor(), context.randomState());
                NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z,
                        context.heightAccessor(), context.randomState());
                if (!column.getBlock(height - 1).getFluidState().isEmpty() && ++wet >= 5) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void afterPlace(WorldGenLevel world, StructureManager structures,
            ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
            ChunkPos chunk, PiecesContainer container) {
        for (StructurePiece piece : container.pieces()) {
            BoundingBox box = piece.getBoundingBox();
            if (!(((box.getXSpan() == 47 || box.getXSpan() == 55) && box.getZSpan() == 21)
                    || (box.getXSpan() == 21
                            && (box.getZSpan() == 47 || box.getZSpan() == 55)))) continue;
            // The authored viaduct marks exactly six stone feet at template
            // level zero. Find them after rotation, then extend only through
            // air or water until two solid riverbed blocks hold the pier.
            for (int x = Math.max(box.minX(), chunkBox.minX());
                    x <= Math.min(box.maxX(), chunkBox.maxX()); x++) {
                for (int z = Math.max(box.minZ(), chunkBox.minZ());
                        z <= Math.min(box.maxZ(), chunkBox.maxZ()); z++) {
                    BlockPos base = new BlockPos(x, box.minY(), z);
                    if (!world.getBlockState(base).is(Blocks.STONE_BRICKS)) continue;
                    int gap = gapToRiverbed(world, base.below());
                    if (gap < 0) continue;
                    for (int depth = 0; depth < gap; depth++) {
                        world.setBlock(base.below(depth + 1), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    private static int gapToRiverbed(WorldGenLevel world, BlockPos start) {
        for (int gap = 0; gap <= 12; gap++) {
            BlockPos pos = start.below(gap);
            BlockState state = world.getBlockState(pos);
            if (state.isAir() || state.is(Blocks.WATER)) continue;
            if (!state.isSolid() || !world.getBlockState(pos.below()).isSolid()) return -1;
            return gap;
        }
        return -1;
    }

    @Override
    public StructureType<?> type() {
        return TYPE;
    }
}
