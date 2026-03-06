package com.feel.gems.adventure.undercard;

import com.feel.gems.GemsMod;
import com.feel.gems.adventure.worldgen.site.BoundedJigsawStart;
import com.feel.gems.adventure.worldgen.site.StructureStartReservation;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

/** Conservative pre-carver filter for a test-only natural Undercard start. */
public final class UndercardStructure extends Structure implements BoundedJigsawStart {
    private static final boolean DIAGNOSTIC = Boolean.getBoolean(
            "gems.undercardNaturalDiagnostic") || Boolean.getBoolean(
            "gems.undercardV5Acceptance");
    // Index zero is jigsaw planning; the remaining slots follow survey outcomes.
    private static final AtomicLongArray DIAGNOSTIC_COUNTS = new AtomicLongArray(
            UndercardNoiseSurvey.Outcome.values().length + 1);
    private static final AtomicLong RESERVATION_CONFLICTS = new AtomicLong();
    private static final AtomicLong RESERVATION_UNKNOWNS = new AtomicLong();
    private static final Identifier V5_POOL = Identifier.fromNamespaceAndPath(
            GemsMod.MOD_ID, "undercard_v5");
    private static final Identifier V5_TRIAL_POOL = Identifier.fromNamespaceAndPath(
            GemsMod.MOD_ID, "undercard_v5_trial");
    private static final ResourceKey<Structure> STABLE_SELF = ResourceKey.create(
            Registries.STRUCTURE, Identifier.fromNamespaceAndPath(GemsMod.MOD_ID,
                    "undercard"));
    private static final ResourceKey<Structure> TRIAL_SELF = ResourceKey.create(
            Registries.STRUCTURE, Identifier.fromNamespaceAndPath(GemsMod.MOD_ID,
                    "undercard_v5_trial"));
    // Each shift stays within the start's vanilla eight-chunk reference halo.
    private static final int[][] V5_SITE_OFFSETS = {
            {0, 0}, {8, 0}, {-8, 0}, {0, 8}, {0, -8},
            {16, 0}, {-16, 0}, {0, 16}, {0, -16},
            {16, 16}, {16, -16}, {-16, 16}, {-16, -16}
    };
    public static final MapCodec<UndercardStructure> CODEC = JigsawStructure.CODEC.xmap(
            UndercardStructure::new, structure -> structure.jigsaw);
    private static final StructureType<UndercardStructure> TYPE = () -> CODEC;
    private final JigsawStructure jigsaw;

    private UndercardStructure(JigsawStructure jigsaw) {
        super(new StructureSettings(jigsaw.biomes(), jigsaw.spawnOverrides(),
                jigsaw.step(), jigsaw.terrainAdaptation()));
        this.jigsaw = jigsaw;
    }

    @Override
    public JigsawStructure reservationDelegate() { return jigsaw; }

    @Override
    public int reservationExtraReachBlocks() { return 8; }

    @Override
    public int reservationMaximumHorizontalShiftBlocks() { return 16; }

    @Override
    public List<BoundingBox> reservationApproaches(StructureStart start) {
        if (!oneAuthoredPiece(start.getPieces())) return null;
        return List.of(BoundedJigsawStart.apron(
                start.getPieces().getFirst().getBoundingBox(), 8));
    }

    /** Register the codec even while the production structure set is absent. */
    public static void init() {
        Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
                Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "undercard_v1"), TYPE);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ResourceKey<Structure> v5Self = v5Self();
        // Jigsaw checks the buried root's biome before any surface arrival can
        // be assessed. For v5, plan geometry first; the strict survey checks
        // the actual dry entrance biome at every proposed final position.
        GenerationContext planning = v5Self == null ? context
                : new GenerationContext(context.registryAccess(), context.chunkGenerator(),
                        context.biomeSource(), context.climateSampler(),
                        context.biomeResolver(), context.randomState(),
                        context.structureTemplateManager(), context.random(),
                        context.seed(), context.chunkPos(), context.heightAccessor(),
                        biome -> true);
        Optional<GenerationStub> candidate = jigsaw.findValidGenerationPoint(planning);
        if (candidate.isEmpty()) {
            count(0);
            return Optional.empty();
        }
        GenerationStub stub = candidate.get();
        StructurePiecesBuilder pieces = stub.getPiecesBuilder();
        List<StructurePiece> built = pieces.build().pieces();
        if (!oneAuthoredPiece(built)) {
            count(0);
            return Optional.empty();
        }
        PoolElementStructurePiece piece = (PoolElementStructurePiece) built.getFirst();
        SinglePoolElement template = (SinglePoolElement) piece.getElement();
        boolean sealedV5 = template.getTemplateLocation().getPath()
                .equals("undercard_ring_v5");
        if (sealedV5 && v5Self == null) return Optional.empty();
        int[][] offsets = sealedV5 ? V5_SITE_OFFSETS : new int[][]{{0, 0}};
        BoundingBox original = pieces.getBoundingBox();
        for (int[] offset : offsets) {
            int dx = offset[0], dz = offset[1];
            BoundingBox box = moved(original, dx, dz);
            UndercardNoiseSurvey.Outcome outcome = UndercardNoiseSurvey.assess(context,
                    box, piece.getRotation(), sealedV5);
            if (outcome != UndercardNoiseSurvey.Outcome.ACCEPTED) {
                count(outcome.ordinal() + 1);
                continue;
            }
            if (sealedV5) {
                StructureStartReservation.Verdict reservation =
                        StructureStartReservation.inspect(context, Level.OVERWORLD,
                                v5Self, List.of(box),
                                List.of(BoundedJigsawStart.apron(box, 8)));
                if (!reservation.clear()) {
                    if (DIAGNOSTIC) {
                        long rejected = (reservation.status()
                                == StructureStartReservation.Status.CONFLICT
                                ? RESERVATION_CONFLICTS : RESERVATION_UNKNOWNS)
                                .incrementAndGet();
                        if (rejected <= 12) System.out.println(
                                "UNDERCARD_RESERVATION_REJECT chunk=" + context.chunkPos()
                                        + " offset=" + dx + "," + dz
                                        + " status=" + reservation.status()
                                        + " reason=" + reservation.reason());
                    }
                    continue;
                }
            }
            count(UndercardNoiseSurvey.Outcome.ACCEPTED.ordinal() + 1);
            if (dx != 0 || dz != 0) piece.move(dx, 0, dz);
            if (DIAGNOSTIC && diagnosticCount(UndercardNoiseSurvey.Outcome.ACCEPTED) <= 12) {
                System.out.println("UNDERCARD_ACCEPTED_CANDIDATE chunk=" + context.chunkPos()
                        + " pos=" + stub.position().offset(dx, 0, dz)
                        + " offset=" + dx + "," + dz + " box=" + box);
            }
            return Optional.of(new GenerationStub(stub.position().offset(dx, 0, dz),
                    Either.right(pieces)));
        }
        return Optional.empty();
    }

    private ResourceKey<Structure> v5Self() {
        Identifier pool = jigsaw.getStartPool().unwrapKey()
                .map(ResourceKey::identifier).orElse(null);
        if (V5_POOL.equals(pool)) return STABLE_SELF;
        if (V5_TRIAL_POOL.equals(pool)) return TRIAL_SELF;
        return null;
    }

    private static BoundingBox moved(BoundingBox box, int dx, int dz) {
        return new BoundingBox(Math.addExact(box.minX(), dx), box.minY(),
                Math.addExact(box.minZ(), dz), Math.addExact(box.maxX(), dx),
                box.maxY(), Math.addExact(box.maxZ(), dz));
    }

    /** Cumulative test-only counters; no terrain checks or production placement are changed. */
    public static String diagnosticCounts() {
        return "jigsaw=" + DIAGNOSTIC_COUNTS.get(0)
                + " biome=" + diagnosticCount(UndercardNoiseSurvey.Outcome.BIOME)
                + " elevation=" + diagnosticCount(UndercardNoiseSurvey.Outcome.ELEVATION)
                + " slope=" + diagnosticCount(UndercardNoiseSurvey.Outcome.SLOPE)
                + " cave=" + diagnosticCount(UndercardNoiseSurvey.Outcome.CAVE)
                + " hazard=" + diagnosticCount(UndercardNoiseSurvey.Outcome.HAZARD)
                + " reservationConflict=" + RESERVATION_CONFLICTS.get()
                + " reservationUnknown=" + RESERVATION_UNKNOWNS.get()
                + " accepted=" + diagnosticCount(UndercardNoiseSurvey.Outcome.ACCEPTED);
    }

    private static long diagnosticCount(UndercardNoiseSurvey.Outcome outcome) {
        return DIAGNOSTIC_COUNTS.get(outcome.ordinal() + 1);
    }

    private static void count(int index) {
        if (DIAGNOSTIC) DIAGNOSTIC_COUNTS.incrementAndGet(index);
    }

    public static boolean oneAuthoredPiece(StructurePiecesBuilder pieces) {
        return oneAuthoredPiece(pieces.build().pieces());
    }

    public static boolean oneAuthoredPiece(List<StructurePiece> built) {
        if (built.size() != 1 || !(built.getFirst() instanceof PoolElementStructurePiece pooled)
                || !(pooled.getElement() instanceof SinglePoolElement single)) return false;
        Identifier location = single.getTemplateLocation();
        int span = switch (location.getPath()) {
            case "undercard_ring_v1", "undercard_ring_v2", "undercard_ring_v3" -> 47;
            case "undercard_ring_v4", "undercard_ring_v5" -> 63;
            default -> 0;
        };
        if (!location.getNamespace().equals(GemsMod.MOD_ID) || span == 0) return false;
        BoundingBox box = built.getFirst().getBoundingBox();
        return box.getXSpan() == span && box.getYSpan() == 48 && box.getZSpan() == span;
    }

    @Override
    public StructureType<?> type() { return TYPE; }
}
