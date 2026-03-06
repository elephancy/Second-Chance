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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
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

/** Rejects new Hushwire starts without a supported, visible built procession and dry arrival. */
public final class HushwireApproachStructure extends Structure implements BoundedJigsawStart {
    public static final MapCodec<HushwireApproachStructure> CODEC = JigsawStructure.CODEC.xmap(
            jigsaw -> new HushwireApproachStructure(jigsaw, false), structure -> structure.jigsaw);
    public static final MapCodec<HushwireApproachStructure> V10_CODEC = JigsawStructure.CODEC.xmap(
            jigsaw -> new HushwireApproachStructure(jigsaw, true), structure -> structure.jigsaw);
    private static final StructureType<HushwireApproachStructure> TYPE = () -> CODEC;
    private static final StructureType<HushwireApproachStructure> V10_TYPE = () -> V10_CODEC;
    private static final String V10_HALL = "gems:hushwire_record_hall_v10";
    private static final String V10_PROCESSION = "gems:hushwire_procession_v5";
    private static final String V10_RECEIVER = "gems:hushwire_receiver_field_v5";
    private static final String V10_CRYPT = "gems:hushwire_relay_crypt_v5";
    private static final ResourceKey<Structure> V10_SELF = ResourceKey.create(
            Registries.STRUCTURE, Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "hushwire_v10"));

    private final JigsawStructure jigsaw;
    private final boolean v10;

    private HushwireApproachStructure(JigsawStructure jigsaw, boolean v10) {
        super(new StructureSettings(jigsaw.biomes(), jigsaw.spawnOverrides(),
                jigsaw.step(), jigsaw.terrainAdaptation()));
        this.jigsaw = jigsaw;
        this.v10 = v10;
    }

    public static void init() {
        Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
                Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "hushwire_approach_v10"), V10_TYPE);
    }

    @Override
    public JigsawStructure reservationDelegate() { return jigsaw; }

    @Override
    public int reservationExtraReachBlocks() { return HushwireDryApproach.LENGTH; }

    @Override
    public List<BoundingBox> reservationApproaches(StructureStart start) {
        if (!v10 || start.getPieces().size() != 4) return null;
        PoolElementStructurePiece hall = null, procession = null;
        boolean receiver = false, crypt = false;
        for (StructurePiece piece : start.getPieces()) {
            if (!(piece instanceof PoolElementStructurePiece pooled)
                    || !(pooled.getElement() instanceof SinglePoolElement single)) return null;
            String name = single.getTemplateLocation().toString();
            if (name.equals(V10_HALL)) {
                if (hall != null) return null;
                hall = pooled;
            } else if (name.equals(V10_PROCESSION)) {
                if (procession != null) return null;
                procession = pooled;
            } else if (name.equals(V10_RECEIVER)) {
                if (receiver) return null;
                receiver = true;
            } else if (name.equals(V10_CRYPT)) {
                if (crypt) return null;
                crypt = true;
            } else return null;
        }
        if (hall == null || !receiver || !crypt
                || hall.getBoundingBox().getXSpan() != 47
                || hall.getBoundingBox().getZSpan() != 47) return null;
        if (procession == null || !connectedChild(hall, procession, Direction.SOUTH, 29, 27))
            return null;
        return List.of(BoundedJigsawStart.localBox(procession, 13, 27, 15,
                26 + HushwireDryApproach.LENGTH));
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!v10) return Optional.empty();
        Optional<GenerationStub> candidate = jigsaw.findValidGenerationPoint(context);
        if (candidate.isEmpty()) return Optional.empty();
        GenerationStub stub = candidate.get();
        StructurePiecesBuilder builder = stub.getPiecesBuilder();
        if (builder.isEmpty()) return Optional.empty();
        PoolElementStructurePiece hall = null, receiver = null, crypt = null, procession = null;
        List<BoundingBox> occupied = new ArrayList<>(4);
        for (StructurePiece piece : builder.build().pieces()) {
            if (!(piece instanceof PoolElementStructurePiece pooled)
                    || !(pooled.getElement() instanceof SinglePoolElement single)) return Optional.empty();
            String template = single.getTemplateLocation().toString();
            if (template.equals(V10_HALL)) {
                if (hall != null) return Optional.empty();
                hall = pooled;
            } else if (template.equals(V10_RECEIVER)) {
                if (receiver != null) return Optional.empty();
                receiver = pooled;
            } else if (template.equals(V10_CRYPT)) {
                if (crypt != null) return Optional.empty();
                crypt = pooled;
            } else if (template.equals(V10_PROCESSION)) {
                if (procession != null) return Optional.empty();
                procession = pooled;
            } else return Optional.empty();
            occupied.add(pooled.getBoundingBox());
        }
        if (hall == null || receiver == null || crypt == null || procession == null
                || occupied.size() != 4
                || hall.getBoundingBox().getXSpan() != 47
                || hall.getBoundingBox().getZSpan() != 47
                || !connectedChild(hall, receiver, Direction.EAST)
                || !connectedChild(hall, crypt, Direction.WEST)
                || !connectedChild(hall, procession, Direction.SOUTH, 29, 27))
            return reject(context, "pieces-or-child-position");
        if (!supportedChildSeam(context, hall, receiver, Direction.EAST)
                || !supportedChildSeam(context, hall, crypt, Direction.WEST))
            return reject(context, "wet-or-unsupported-child-seam");
        if (!supportedProcession(context, procession, hall.getBoundingBox().minY()))
            return reject(context, "wet-or-unsupported-procession");
        if (!HushwireDryApproach.connected(context, procession, 14, 26,
                hall.getBoundingBox().minY() + 1))
            return reject(context, "wet-or-steep-outer-approach");
        if (!arrivalSkyline(context, procession))
            return reject(context, "arrival-skyline");
        BoundingBox approach = BoundedJigsawStart.localBox(procession, 13, 27, 15,
                26 + HushwireDryApproach.LENGTH);
        StructureStartReservation.Verdict reservation = StructureStartReservation.inspect(
                context, Level.OVERWORLD, V10_SELF, occupied, List.of(approach));
        if (!reservation.clear())
            return reject(context, "neighbor-" + reservation.status()
                    + "-" + reservation.reason());
        System.out.println("HUSHWIRE_APPROACH_ACCEPT chunk=" + context.chunkPos()
                + " root=" + hall.getPosition() + " rotation=" + hall.getRotation());
        return Optional.of(new GenerationStub(stub.position(), Either.right(builder)));
    }

    private static Optional<GenerationStub> reject(GenerationContext context, String reason) {
        System.out.println("HUSHWIRE_APPROACH_REJECT chunk=" + context.chunkPos()
                + " reason=" + reason);
        return Optional.empty();
    }

    private static boolean connectedChild(PoolElementStructurePiece hall,
                                          PoolElementStructurePiece child,
                                          Direction localSide) {
        return connectedChild(hall, child, localSide, 41, 41);
    }

    private static boolean connectedChild(PoolElementStructurePiece hall,
                                          PoolElementStructurePiece child,
                                          Direction localSide, int width, int depth) {
        BoundingBox a = hall.getBoundingBox(), b = child.getBoundingBox();
        Direction side = hall.getRotation().rotate(localSide);
        if (b.getXSpan() != (side.getAxis() == Direction.Axis.Z ? width : depth)
                || b.getZSpan() != (side.getAxis() == Direction.Axis.Z ? depth : width))
            return false;
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

    private static boolean supportedChildSeam(GenerationContext context,
                                               PoolElementStructurePiece hall,
                                               PoolElementStructurePiece child,
                                               Direction localSide) {
        Direction side = hall.getRotation().rotate(localSide);
        Direction along = hall.getRotation().rotate(Direction.SOUTH);
        BlockPos joint = StructureTemplate.transform(new BlockPos(
                localSide == Direction.EAST ? 46 : 0, 0, 23),
                Mirror.NONE, hall.getRotation(), BlockPos.ZERO).offset(hall.getPosition());
        // Both halves of the connected doorway need dry bearing ground. The
        // pieces supply the actual walk floor four blocks above this terrain.
        for (int depth = 0; depth <= 2; depth++) {
            BlockPos center = joint.relative(side, depth);
            for (int lateral = -1; lateral <= 1; lateral++) {
                BlockPos pos = center.relative(along, lateral);
                HushwireDryApproach.Column column = HushwireDryApproach.sample(
                        context, pos.getX(), pos.getZ());
                int base = child.getBoundingBox().minY();
                if (column == null || column.feet() > base + 5
                        || !HushwireDryApproach.solidContact(context,
                                pos.getX(), pos.getZ(), base - 3, base))
                    return false;
            }
        }
        return true;
    }

    private static boolean arrivalSkyline(GenerationContext context,
                                           PoolElementStructurePiece procession) {
        Direction outward = procession.getRotation().rotate(Direction.SOUTH);
        Direction lateral = procession.getRotation().rotate(Direction.EAST);
        BlockPos stair = StructureTemplate.transform(new BlockPos(14, 0, 26),
                Mirror.NONE, procession.getRotation(), BlockPos.ZERO)
                .offset(procession.getPosition());
        int hallY = procession.getBoundingBox().minY() + 8;
        for (int depth : new int[] {4, 8, 12}) {
            for (int side : new int[] {-5, 5}) {
                BlockPos pos = stair.relative(outward, depth).relative(lateral, side);
                HushwireDryApproach.Column column = HushwireDryApproach.sample(
                        context, pos.getX(), pos.getZ());
                if (column == null
                        || column.feet() > hallY + 10)
                    return false;
            }
        }
        return true;
    }

    private static boolean supportedProcession(GenerationContext context,
                                                PoolElementStructurePiece procession,
                                                int hallY) {
        if (procession.getBoundingBox().minY() != hallY - 8) return false;
        for (int x : new int[] {1, 14, 27}) {
            for (int z : new int[] {2, 8, 16, 25}) {
                BlockPos point = StructureTemplate.transform(new BlockPos(x, 0, z),
                        Mirror.NONE, procession.getRotation(), BlockPos.ZERO)
                        .offset(procession.getPosition());
                HushwireDryApproach.Column column = HushwireDryApproach.sample(
                        context, point.getX(), point.getZ());
                int deckFeet = hallY + (z == 25 ? (x == 14 ? 1 : 3)
                        : z == 2 ? 3 : 4);
                if (column == null || column.feet() > deckFeet
                        || !HushwireDryApproach.solidContact(context,
                                point.getX(), point.getZ(), hallY - 8, hallY))
                    return false;
            }
        }
        return true;
    }

    @Override
    public StructureType<?> type() { return v10 ? V10_TYPE : TYPE; }
}
