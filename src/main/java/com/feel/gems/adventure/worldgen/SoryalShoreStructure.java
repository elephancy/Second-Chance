package com.feel.gems.adventure.worldgen;

import com.feel.gems.GemsMod;
import com.feel.gems.adventure.worldgen.site.BoundedJigsawStart;
import com.feel.gems.adventure.worldgen.site.StructureStartReservation;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Plans the intact Soryal jigsaw only with a bounded land or boat arrival. */
public final class SoryalShoreStructure extends Structure implements BoundedJigsawStart {
    public static final MapCodec<SoryalShoreStructure> CODEC = JigsawStructure.CODEC.xmap(
            SoryalShoreStructure::new, structure -> structure.jigsaw);
    private static final StructureType<SoryalShoreStructure> TYPE = () -> CODEC;
    private static final String ROOT = "gems:soryal_reclamation_district_v11";
    private static final String QUAY = "gems:soryal_sorting_quay_v7";
    private static final String HALL = "gems:soryal_reuse_hall_v7";
    private static final int APPROACH_LENGTH = 12;
    private static final ResourceKey<Structure> ACTIVE_KEY = ResourceKey.create(
            Registries.STRUCTURE, Identifier.fromNamespaceAndPath(GemsMod.MOD_ID,
                    "soryal_repair_yard_v8"));
    private enum Approach { NONE, DRY, BOAT }

    private final JigsawStructure jigsaw;

    private SoryalShoreStructure(JigsawStructure jigsaw) {
        super(new StructureSettings(jigsaw.biomes(), jigsaw.spawnOverrides(),
                jigsaw.step(), jigsaw.terrainAdaptation()));
        this.jigsaw = jigsaw;
    }

    public static void init() {
        Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
                Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "soryal_shore_v1"), TYPE);
    }

    @Override
    public JigsawStructure reservationDelegate() { return jigsaw; }

    @Override
    public int reservationExtraReachBlocks() { return APPROACH_LENGTH; }

    @Override
    public List<BoundingBox> reservationApproaches(StructureStart start) {
        for (StructurePiece piece : start.getPieces()) {
            if (!(piece instanceof PoolElementStructurePiece pooled)
                    || !(pooled.getElement() instanceof SinglePoolElement single)) return null;
            String name = single.getTemplateLocation().toString();
            if (name.equals(ROOT)) {
                if (pooled.getBoundingBox().getXSpan() != 47
                        || pooled.getBoundingBox().getZSpan() != 47) return null;
                return List.of(BoundedJigsawStart.localBox(pooled,
                        24, 47, 26, 46 + APPROACH_LENGTH));
            }
        }
        return null;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        Optional<GenerationStub> candidate = jigsaw.findValidGenerationPoint(context);
        if (candidate.isEmpty()) return Optional.empty();
        GenerationStub stub = candidate.get();
        StructurePiecesBuilder builder = stub.getPiecesBuilder();
        if (builder.isEmpty()) return Optional.empty();
        PoolElementStructurePiece root = null;
        List<PoolElementStructurePiece> children = new ArrayList<>();
        for (StructurePiece piece : builder.build().pieces()) {
            if (!(piece instanceof PoolElementStructurePiece pooled)
                    || !(pooled.getElement() instanceof SinglePoolElement single)) return Optional.empty();
            String name = single.getTemplateLocation().toString();
            if (name.equals(ROOT)) {
                if (root != null) return Optional.empty();
                root = pooled;
            } else if (name.equals(QUAY) || name.equals(HALL)) children.add(pooled);
            else return Optional.empty();
        }
        if (root == null || root.getBoundingBox().getXSpan() != 47
                || root.getBoundingBox().getZSpan() != 47
                || children.size() != 2) return Optional.empty();
        if (!childrenFit(context, children)) {
            System.out.println("SORYAL_SHORE_REJECT reason=child-grade chunk=" + context.chunkPos());
            return Optional.empty();
        }
        Approach approach = approach(context, root);
        if (approach == Approach.NONE || !arrivalSkyline(context, root)) {
            System.out.println("SORYAL_SHORE_REJECT chunk=" + context.chunkPos()
                    + " root=" + root.getPosition() + " rotation=" + root.getRotation()
                    + " approach=" + approach);
            return Optional.empty();
        }
        List<BoundingBox> occupied = new ArrayList<>();
        occupied.add(root.getBoundingBox());
        for (PoolElementStructurePiece child : children) occupied.add(child.getBoundingBox());
        BoundingBox arrival = BoundedJigsawStart.localBox(root,
                24, 47, 26, 46 + APPROACH_LENGTH);
        StructureStartReservation.Verdict reservation = StructureStartReservation.inspect(
                context, Level.OVERWORLD, ACTIVE_KEY, occupied, List.of(arrival));
        if (!reservation.clear()) {
            System.out.println("SORYAL_SHORE_REJECT reason=reservation-"
                    + reservation.status() + " detail=" + reservation.reason()
                    + " chunk=" + context.chunkPos());
            return Optional.empty();
        }
        System.out.println("SORYAL_SHORE_ACCEPT chunk=" + context.chunkPos()
                + " approach=" + approach + " root=" + root.getPosition());
        return Optional.of(new GenerationStub(stub.position(), Either.right(builder)));
    }

    /** Natural columns only; no generated chunk loads or terrain edits. */
    private static Approach approach(GenerationContext context,
            PoolElementStructurePiece root) {
        Direction outward = root.getRotation().rotate(Direction.SOUTH);
        Direction lateral = root.getRotation().rotate(Direction.EAST);
        BlockPos edge = StructureTemplate.transform(new BlockPos(25, 0, 46),
                Mirror.NONE, root.getRotation(), BlockPos.ZERO).offset(root.getPosition());
        if (dryConnectedApproach(context, root, edge, outward, lateral)) return Approach.DRY;
        if (boatConnectedApproach(context, root, edge, outward, lateral)) return Approach.BOAT;
        return Approach.NONE;
    }

    private static boolean dryConnectedApproach(GenerationContext context,
            PoolElementStructurePiece root, BlockPos edge, Direction outward,
            Direction lateral) {
        int previousFeet = root.getBoundingBox().minY() + 1;
        for (int depth = 1; depth <= APPROACH_LENGTH; depth++) {
            BlockPos center = edge.relative(outward, depth);
            int centerFeet = dryFeet(context, center.getX(), center.getZ());
            if (centerFeet == Integer.MIN_VALUE
                    || Math.abs(centerFeet - previousFeet) > 1) return false;
            for (int side : new int[] {-1, 1}) {
                BlockPos flank = center.relative(lateral, side);
                int flankFeet = dryFeet(context, flank.getX(), flank.getZ());
                if (flankFeet == Integer.MIN_VALUE
                        || Math.abs(flankFeet - centerFeet) > 1) return false;
            }
            previousFeet = centerFeet;
        }
        return true;
    }

    /** Three clear, two-deep water columns lead directly to the built dock. */
    private static boolean boatConnectedApproach(GenerationContext context,
            PoolElementStructurePiece root, BlockPos edge, Direction outward,
            Direction lateral) {
        // Jigsaw's projected base is the sampled WORLD_SURFACE_WG top block.
        int waterY = root.getBoundingBox().minY();
        for (int depth = 1; depth <= APPROACH_LENGTH; depth++) {
            BlockPos center = edge.relative(outward, depth);
            for (int side = -1; side <= 1; side++) {
                BlockPos column = center.relative(lateral, side);
                NoiseColumn noise = context.chunkGenerator().getBaseColumn(
                        column.getX(), column.getZ(), context.heightAccessor(),
                        context.randomState());
                if (noise.getBlock(waterY).getFluidState().isEmpty()
                        || noise.getBlock(waterY - 1).getFluidState().isEmpty()
                        || !noise.getBlock(waterY + 1).isAir()
                        || !noise.getBlock(waterY + 2).isAir()) return false;
            }
        }
        return true;
    }

    /** Refuse child walls buried in a ridge or hanging far over a ravine. */
    private static boolean childrenFit(GenerationContext context,
            List<PoolElementStructurePiece> children) {
        for (PoolElementStructurePiece child : children) {
            BoundingBox box = child.getBoundingBox();
            int floorFeet = box.minY() + 5;
            for (int x : new int[] {box.minX(), (box.minX() + box.maxX()) / 2,
                    box.maxX()}) {
                for (int z : new int[] {box.minZ() - 2, box.maxZ() + 2}) {
                    int feet = surfaceFeet(context, x, z);
                    if (feet < floorFeet - 10 || feet > floorFeet + 6) return false;
                }
            }
            for (int z : new int[] {box.minZ(), (box.minZ() + box.maxZ()) / 2,
                    box.maxZ()}) {
                for (int x : new int[] {box.minX() - 2, box.maxX() + 2}) {
                    int feet = surfaceFeet(context, x, z);
                    if (feet < floorFeet - 10 || feet > floorFeet + 6) return false;
                }
            }
        }
        return true;
    }

    /** Base terrain must not stand above the approach beacon or hide its bay. */
    private static boolean arrivalSkyline(GenerationContext context,
            PoolElementStructurePiece root) {
        Direction outward = root.getRotation().rotate(Direction.SOUTH);
        Direction lateral = root.getRotation().rotate(Direction.EAST);
        BlockPos edge = StructureTemplate.transform(new BlockPos(25, 0, 46),
                Mirror.NONE, root.getRotation(), BlockPos.ZERO).offset(root.getPosition());
        for (int depth : new int[] {4, 8, 12}) {
            BlockPos center = edge.relative(outward, depth);
            for (int side : new int[] {-6, -4, 4, 6}) {
                BlockPos column = center.relative(lateral, side);
                if (surfaceFeet(context, column.getX(), column.getZ())
                        > root.getBoundingBox().minY() + 13) return false;
            }
        }
        return true;
    }

    private static int surfaceFeet(GenerationContext context, int x, int z) {
        return context.chunkGenerator().getBaseHeight(x, z,
                Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());
    }

    private static int dryFeet(GenerationContext context, int x, int z) {
        int feet = surfaceFeet(context, x, z);
        NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z,
                context.heightAccessor(), context.randomState());
        BlockState ground = column.getBlock(feet - 1);
        if (!ground.isSolid() || !ground.getFluidState().isEmpty()
                || !column.getBlock(feet).isAir()
                || !column.getBlock(feet + 1).isAir()) return Integer.MIN_VALUE;
        return feet;
    }

    @Override
    public StructureType<?> type() { return TYPE; }
}
