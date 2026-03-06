package com.feel.gems.adventure.satellite;

import com.feel.gems.GemsMod;
import com.feel.gems.adventure.worldgen.site.BoundedJigsawStart;
import com.feel.gems.adventure.worldgen.site.StructureStartReservation;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
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

/** Single-piece satellite starts filtered against base terrain before carving. */
public final class SatelliteStructure extends Structure implements BoundedJigsawStart {
    public static final MapCodec<SatelliteStructure> CODEC = JigsawStructure.CODEC.xmap(
            SatelliteStructure::new, structure -> structure.jigsaw);
    private static final StructureType<SatelliteStructure> TYPE = () -> CODEC;
    private final JigsawStructure jigsaw;

    private SatelliteStructure(JigsawStructure jigsaw) {
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
        if (start.getPieces().size() != 1
                || !(start.getPieces().getFirst() instanceof PoolElementStructurePiece pooled)
                || !(pooled.getElement() instanceof SinglePoolElement single)) return null;
        BoundingBox box = pooled.getBoundingBox();
        int apron = switch (single.getTemplateLocation().toString()) {
            case "gems:nour_sunken_relay_v1", "gems:nour_sunken_relay_v2",
                    "gems:nour_sunken_relay_v3" ->
                    box.getXSpan() == 47 && box.getZSpan() == 47 && box.getYSpan() == 43
                            ? 8 : -1;
            case "gems:nour_sunken_relay_v4" ->
                    box.getXSpan() == 47 && box.getZSpan() == 47 && box.getYSpan() == 50
                            ? 8 : -1;
            case "gems:branch_underarchive_v1", "gems:branch_underarchive_v2" ->
                    box.getXSpan() == 47 && box.getZSpan() == 47 && box.getYSpan() == 58
                            ? 2 : -1;
            default -> -1;
        };
        return apron < 0 ? null : List.of(BoundedJigsawStart.apron(box, apron));
    }

    /** Register the codec even while production structure sets remain absent. */
    public static void init() {
        Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
                Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "satellite_v1"), TYPE);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        Optional<GenerationStub> proposed = jigsaw.findValidGenerationPoint(context);
        if (proposed.isEmpty()) {
            SatelliteNoiseSurvey.recordUnresolved("JIGSAW_EMPTY");
            return Optional.empty();
        }
        GenerationStub stub = proposed.get();
        StructurePiecesBuilder builder = stub.getPiecesBuilder();
        var pieces = builder.build().pieces();
        if (pieces.size() != 1) {
            SatelliteNoiseSurvey.recordUnresolved("PIECE_COUNT_" + pieces.size());
            return Optional.empty();
        }
        StructurePiece piece = pieces.getFirst();
        if (!(piece instanceof PoolElementStructurePiece pooled)
                || !(pooled.getElement() instanceof SinglePoolElement single)) {
            SatelliteNoiseSurvey.recordUnresolved("PIECE_TYPE");
            return Optional.empty();
        }
        String template = single.getTemplateLocation().toString();
        SatelliteNoiseSurvey.Site site = switch (template) {
            case "gems:nour_sunken_relay_v1", "gems:nour_sunken_relay_v2",
                    "gems:nour_sunken_relay_v3" ->
                    SatelliteNoiseSurvey.Site.OCEAN;
            case "gems:nour_sunken_relay_v4" ->
                    SatelliteNoiseSurvey.Site.OCEAN_V4;
            case "gems:branch_underarchive_v1", "gems:branch_underarchive_v2" ->
                    SatelliteNoiseSurvey.Site.ARCHIVE;
            default -> null;
        };
        if (site == null) {
            SatelliteNoiseSurvey.recordUnresolved("TEMPLATE");
            return Optional.empty();
        }
        if (!SatelliteNoiseSurvey.accepts(context, piece.getBoundingBox(), site,
                pooled.getRotation())) {
            return Optional.empty();
        }
        // Reserve the exact saved piece and the same horizontal arrival apron
        // that the loaded-world audit protects. Old starts are read from saves;
        // this check only decides whether a new current-template start exists.
        if (site == SatelliteNoiseSurvey.Site.OCEAN_V4
                || template.equals("gems:branch_underarchive_v2")) {
            BoundingBox occupied = piece.getBoundingBox();
            int arrivalApron = site == SatelliteNoiseSurvey.Site.OCEAN_V4 ? 8 : 2;
            ResourceKey<Structure> self = ResourceKey.create(Registries.STRUCTURE,
                    site == SatelliteNoiseSurvey.Site.OCEAN_V4
                            ? SatelliteSites.OCEAN_ID : SatelliteSites.ARCHIVE_ID);
            StructureStartReservation.Verdict reservation =
                    StructureStartReservation.inspect(context, Level.OVERWORLD, self,
                            List.of(occupied),
                            List.of(BoundedJigsawStart.apron(occupied, arrivalApron)));
            if (!reservation.clear()) {
                SatelliteNoiseSurvey.recordUnresolved("RESERVATION_" + reservation.status());
                GemsMod.LOGGER.info("Satellite reservation rejected {} at {}: {} ({})",
                        template, context.chunkPos(), reservation.status(),
                        reservation.reason());
                return Optional.empty();
            }
        }
        return Optional.of(new GenerationStub(stub.position(), Either.right(builder)));
    }

    @Override
    public StructureType<?> type() { return TYPE; }
}
