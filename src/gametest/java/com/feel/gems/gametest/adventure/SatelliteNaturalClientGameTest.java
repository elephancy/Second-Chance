package com.feel.gems.gametest.adventure;

import com.feel.gems.GemsMod;
import com.feel.gems.adventure.CampaignPersistentState;
import com.feel.gems.adventure.FriendDomainUnlockState;
import com.feel.gems.adventure.LandmarkKey;
import com.feel.gems.adventure.satellite.SatelliteBlocks;
import com.feel.gems.adventure.satellite.SatelliteLoadedAudit;
import com.feel.gems.adventure.satellite.SatelliteNoiseSurvey;
import com.feel.gems.adventure.satellite.SatelliteRuntime;
import com.feel.gems.adventure.satellite.SatelliteState;
import com.feel.gems.adventure.satellite.SatelliteStructure;
import com.feel.gems.adventure.satellite.SatelliteTownhallHints;
import com.feel.gems.adventure.worldgen.site.ReservationCandidateEquivalence;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.impl.client.gametest.world.TestWorldSaveImpl;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Opt-in, test-pack-only natural arrival probe; not a production start approval. */
@SuppressWarnings("UnstableApiUsage")
public final class SatelliteNaturalClientGameTest implements FabricClientGameTest {
    private static final String SEED = System.getProperty("gems.satelliteSeed", "SecondChanceSatellites-A");
    private static final int SEARCH_CHUNKS = Math.min(512,
            Math.max(32, Integer.getInteger("gems.satelliteSearchChunks", 256)));
    private static final String SITE = System.getProperty("gems.satelliteSite", "both");
    private static final Identifier OCEAN_ID = Identifier.fromNamespaceAndPath("gems", "nour_sunken_relay_v1");
    private static final Identifier ARCHIVE_ID = Identifier.fromNamespaceAndPath("gems", "branch_underarchive_v1");
    private static final Set<Block> OCEAN_CONTROLS = Set.of(
            SatelliteBlocks.SUNKEN_BUOY, SatelliteBlocks.SUNKEN_WEST_HYDROPHONE,
            SatelliteBlocks.SUNKEN_EAST_HYDROPHONE, SatelliteBlocks.SUNKEN_TUNING_CONSOLE,
            SatelliteBlocks.SUNKEN_RETURN);
    private static final Set<Block> ARCHIVE_CONTROLS = Set.of(
            SatelliteBlocks.UNDERARCHIVE_CLUE, SatelliteBlocks.UNDERARCHIVE_FIRST_INDEX,
            SatelliteBlocks.UNDERARCHIVE_SECOND_INDEX, SatelliteBlocks.UNDERARCHIVE_THIRD_INDEX,
            SatelliteBlocks.UNDERARCHIVE_DESK, SatelliteBlocks.UNDERARCHIVE_RETURN);

    private record Site(Identifier id, StructureStart start, BoundingBox box,
                        BlockPos root, Map<Block, BlockPos> controls) {
        BlockPos control(Block block) {
            BlockPos pos = controls.get(block);
            if (pos == null) throw new IllegalStateException("Missing " + block + " at " + id);
            return pos;
        }
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!Boolean.getBoolean("gems.satelliteNaturalGate")) return;
        require(Set.of("both", "ocean", "archive").contains(SITE),
                "Invalid satellite site selector: " + SITE);
        if (Boolean.getBoolean("gems.satelliteNoStartControl")) {
            require(SITE.equals("ocean") && !Boolean.getBoolean("gems.satelliteRestartOnly"),
                    "Nour no-start control needs a fresh ocean world");
            generateOceanNoStartControl(context);
            return;
        }
        if (Boolean.getBoolean("gems.satelliteRestartOnly")) {
            if (SITE.equals("ocean")) reopenOcean(context);
            else if (SITE.equals("archive")) reopenArchiveV2(context);
            else throw new IllegalStateException("Satellite restart needs one selected site");
            return;
        }
        try (TestSingleplayerContext world = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(settings -> {
                    settings.setSeed(SEED);
                    settings.setGenerateStructures(true);
                }).create()) {
            world.getConnection().waitForChunksRender();
            if (!SITE.equals("archive"))
                world.getServer().runOnServer(server ->
                        ReservationCandidateEquivalence.verifyVanillaOceanInfluence(
                                server.overworld()));
            if (Boolean.getBoolean("gems.satellitePresearchOnly")) {
                world.getServer().runOnServer(server -> {
                    ServerLevel level = server.overworld();
                    GemsMod.LOGGER.info("Satellite presearch {} radius={} seed={}: {}",
                            OCEAN_ID, SEARCH_CHUNKS, SEED, presearch(level, OCEAN_ID));
                    GemsMod.LOGGER.info("Satellite presearch {} radius={} seed={}: {}",
                            ARCHIVE_ID, SEARCH_CHUNKS, SEED, presearch(level, ARCHIVE_ID));
                });
                return;
            }
            Site ocean = SITE.equals("archive") ? null : world.getServer().computeOnServer(server ->
                    locate(server.overworld(), OCEAN_ID, OCEAN_CONTROLS, 50, 19,
                            SatelliteBlocks.SUNKEN_ROOT));
            Site archive = SITE.equals("ocean") ? null : world.getServer().computeOnServer(server ->
                    locate(server.overworld(), ARCHIVE_ID, ARCHIVE_CONTROLS, 58, 28,
                            SatelliteBlocks.UNDERARCHIVE_ROOT));
            if (ocean != null) {
                SatellitePlayerRouteProbe.run(context, world, ocean.box(), ocean.root(),
                        ocean.control(SatelliteBlocks.SUNKEN_BUOY),
                        ocean.control(SatelliteBlocks.SUNKEN_RETURN), true);
            }
            if (archive != null) {
                SatellitePlayerRouteProbe.run(context, world, archive.box(), archive.root(),
                        archive.control(SatelliteBlocks.UNDERARCHIVE_CLUE),
                        archive.control(SatelliteBlocks.UNDERARCHIVE_RETURN), false);
            }
            world.getServer().runOnServer(server -> {
                ServerLevel level = server.overworld();
                ServerPlayer player = world.getConnection().getServerPlayer();
                var failures = new ArrayList<String>();
                if (ocean != null) {
                    try {
                        oceanArrival(level, player, ocean);
                        oceanControlSequence(level, player, ocean);
                        GemsMod.LOGGER.info("Satellite saved ocean arrival, return and control sequence passed: {}",
                                ocean.box());
                    } catch (IllegalStateException error) {
                        failures.add("ocean: " + error.getMessage());
                    }
                }
                if (archive != null) {
                    try {
                        archiveArrival(level, player, archive);
                        archiveControlSequence(level, player, archive);
                        GemsMod.LOGGER.info("Satellite saved archive arrival, return and control sequence passed: {}",
                                archive.box());
                    } catch (IllegalStateException error) {
                        failures.add("archive: " + error.getMessage());
                    }
                }
                require(failures.isEmpty(), "Satellite saved-world failures: "
                        + String.join("; ", failures));
            });
            require((SITE.equals("archive") || ocean != null)
                            && (SITE.equals("ocean") || archive != null),
                    "No saved natural start for "
                            + (ocean == null && !SITE.equals("archive") ? "ocean " : "")
                            + (archive == null && !SITE.equals("ocean") ? "archive " : "")
                            + "within " + SEARCH_CHUNKS + " chunks on " + SEED);
            System.out.println("SATELLITE_NATURAL_PASS seed=" + SEED + " site=" + SITE
                    + " ocean=" + (ocean == null ? "not checked" : ocean.box())
                    + " archive=" + (archive == null ? "not checked" : archive.box()));
        }
    }

    /** Reopens a copied save generated by the current ocean relay. */
    private static void reopenOcean(ClientGameTestContext context) {
        require(SITE.equals("ocean"), "Satellite restart accepts the ocean site only");
        int[] bounds = coordinates("gems.satelliteRestartBox", 6);
        BoundingBox expected = new BoundingBox(bounds[0], bounds[1], bounds[2],
                bounds[3], bounds[4], bounds[5]);
        require(expected.getXSpan() == 47 && expected.getYSpan() == 50
                        && expected.getZSpan() == 47,
                "Satellite restart expected box is not the selected ocean relay");
        int[] rootCoordinates = coordinates("gems.satelliteRestartRoot", 3);
        BlockPos root = new BlockPos(rootCoordinates[0], rootCoordinates[1],
                rootCoordinates[2]);
        require(root.equals(new BlockPos(expected.minX() + 23,
                        expected.minY() + 19, expected.minZ() + 23)),
                "Satellite restart root disagrees with saved v4 box");
        Path save = context.computeOnClient(client -> client.gameDirectory.toPath()
                .resolve("saves").resolve("New World"));
        require(Files.isRegularFile(save.resolve("level.dat")),
                "Satellite restart copy has no saved world: " + save);
        // Fabric's test save implementation opens a named world from this
        // client's game directory; the driver supplies only a disposable copy.
        try (TestSingleplayerContext world = new TestWorldSaveImpl(context, save).open()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runOnServer(server -> {
                ServerLevel level = server.overworld();
                Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                        .getValue(ResourceKey.create(Registries.STRUCTURE, OCEAN_ID));
                require(structure instanceof SatelliteStructure,
                        "Reopened ocean registry lost its satellite safety filter");
                for (int cx = (expected.minX() - 8) >> 4;
                     cx <= (expected.maxX() + 8) >> 4; cx++) {
                    for (int cz = (expected.minZ() - 8) >> 4;
                         cz <= (expected.maxZ() + 8) >> 4; cz++) {
                        level.getChunk(cx, cz);
                    }
                }
                StructureStart start = level.structureManager().getStructureWithPieceAt(
                        root.getX(), root.getY(), root.getZ(), structure);
                require(start != null && start.isValid() && start.getPieces().size() == 1
                                && start.getPieces().getFirst() instanceof PoolElementStructurePiece pooled
                                && pooled.getElement() instanceof SinglePoolElement single
                                && single.getTemplateLocation().equals(Identifier.fromNamespaceAndPath(
                                        "gems", "nour_sunken_relay_v4")),
                        "Reopened ocean root is not the selected saved start");
                BoundingBox actual = start.getPieces().getFirst().getBoundingBox();
                require(actual.minX() == expected.minX() && actual.minY() == expected.minY()
                                && actual.minZ() == expected.minZ()
                                && actual.maxX() == expected.maxX()
                                && actual.maxY() == expected.maxY()
                                && actual.maxZ() == expected.maxZ()
                                && level.getBlockState(root).is(SatelliteBlocks.SUNKEN_ROOT),
                        "Reopened ocean box or root changed");
                Map<Block, BlockPos> controls = controlsInBox(level, actual, OCEAN_CONTROLS,
                        OCEAN_ID);
                require(SatelliteLoadedAudit.inspectLoaded(level, start,
                                SatelliteLoadedAudit.Site.OCEAN)
                                == SatelliteLoadedAudit.Verdict.READY,
                        "Reopened ocean site failed its loaded safety audit");
                SatelliteState saved = SatelliteState.get(server);
                require(saved.oceanComplete(root.asLong())
                                && saved.sharedOceanBearing().filter(root::equals).isPresent()
                                && !SatelliteTownhallHints.signalTable(server).isBlank(),
                        "Reopened ocean clear or shared bearing did not persist");
                Site site = new Site(OCEAN_ID, start, actual, root, controls);
                oceanArrival(level, world.getConnection().getServerPlayer(), site);
                require(saved.oceanComplete(root.asLong())
                                && saved.sharedOceanBearing().filter(root::equals).isPresent(),
                        "Buoy/return altered the reopened ocean receipt");
                System.out.println("SATELLITE_OCEAN_V4_RESTART_PASS root=" + root
                        + " box=" + actual + " loadedAudit=READY sharedBearing=true buoyReturn=true");
            });
            Map<Block, BlockPos> controls = world.getServer().computeOnServer(server ->
                    controlsInBox(server.overworld(), expected, OCEAN_CONTROLS, OCEAN_ID));
            SatellitePlayerRouteProbe.run(context, world, expected, root,
                    controls.get(SatelliteBlocks.SUNKEN_BUOY),
                    controls.get(SatelliteBlocks.SUNKEN_RETURN), true);
        }
    }

    /** Generates only the saved region needed for a same-seed no-start boundary audit. */
    private static void generateOceanNoStartControl(ClientGameTestContext context) {
        int[] bounds = coordinates("gems.satelliteRestartBox", 6);
        BoundingBox box = new BoundingBox(bounds[0], bounds[1], bounds[2],
                bounds[3], bounds[4], bounds[5]);
        require(box.getXSpan() == 47 && box.getYSpan() == 50 && box.getZSpan() == 47,
                "Nour no-start control expected box is not a v4 relay");
        BlockPos root = new BlockPos(box.minX() + 23, box.minY() + 19,
                box.minZ() + 23);
        try (TestSingleplayerContext world = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(settings -> {
                    settings.setSeed(SEED);
                    settings.setGenerateStructures(true);
                }).create()) {
            world.getServer().runOnServer(server -> {
                ServerLevel level = server.overworld();
                Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                        .getValue(ResourceKey.create(Registries.STRUCTURE, OCEAN_ID));
                require(structure instanceof SatelliteStructure,
                        "Nour control registry lost its satellite structure type");
                int chunks = 0;
                for (int cx = (box.minX() - 8) >> 4; cx <= (box.maxX() + 8) >> 4; cx++) {
                    for (int cz = (box.minZ() - 8) >> 4; cz <= (box.maxZ() + 8) >> 4; cz++) {
                        level.getChunk(cx, cz);
                        chunks++;
                    }
                }
                StructureStart start = level.structureManager().getStructureWithPieceAt(
                        root.getX(), root.getY(), root.getZ(), structure);
                require(start == null || !start.isValid(),
                        "Nour no-start control unexpectedly generated the trial structure");
                System.out.println("SATELLITE_OCEAN_NO_START_CONTROL_PASS seed=" + SEED
                        + " box=" + box + " chunks=" + chunks + " ownStart=false");
            });
        }
    }

    /** Reopens a copied v2 archive save without changing the original trial world. */
    private static void reopenArchiveV2(ClientGameTestContext context) {
        int[] bounds = coordinates("gems.satelliteRestartBox", 6);
        BoundingBox expected = new BoundingBox(bounds[0], bounds[1], bounds[2],
                bounds[3], bounds[4], bounds[5]);
        require(expected.getXSpan() == 47 && expected.getYSpan() == 58
                        && expected.getZSpan() == 47,
                "Satellite restart expected box is not a v2 Underarchive");
        int[] rootCoordinates = coordinates("gems.satelliteRestartRoot", 3);
        BlockPos root = new BlockPos(rootCoordinates[0], rootCoordinates[1],
                rootCoordinates[2]);
        require(root.equals(new BlockPos(expected.minX() + 23,
                        expected.minY() + 28, expected.minZ() + 23)),
                "Satellite archive restart root disagrees with saved v2 box");
        Path save = context.computeOnClient(client -> client.gameDirectory.toPath()
                .resolve("saves").resolve("New World"));
        require(Files.isRegularFile(save.resolve("level.dat")),
                "Satellite archive restart copy has no saved world: " + save);
        try (TestSingleplayerContext world = new TestWorldSaveImpl(context, save).open()) {
            world.getConnection().waitForChunksRender();
            if (Boolean.getBoolean("gems.satelliteResumeInitial")) {
                require(!Boolean.getBoolean("gems.satelliteLowViewArrival"),
                        "Archive initial resume must use its own copied save");
                Site site = world.getServer().computeOnServer(server -> {
                    ServerLevel level = server.overworld();
                    level.getChunk(root.getX() >> 4, root.getZ() >> 4);
                    Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                            .getValue(ResourceKey.create(Registries.STRUCTURE, ARCHIVE_ID));
                    require(structure instanceof SatelliteStructure,
                            "Resumed archive registry lost its safety filter");
                    StructureStart start = level.structureManager().getStructureWithPieceAt(
                            root.getX(), root.getY(), root.getZ(), structure);
                    require(start != null && start.isValid() && start.getPieces().size() == 1
                                    && start.getPieces().getFirst() instanceof PoolElementStructurePiece pooled
                                    && pooled.getElement() instanceof SinglePoolElement single
                                    && single.getTemplateLocation().equals(Identifier.fromNamespaceAndPath(
                                            "gems", "branch_underarchive_v2")),
                            "Resumed archive is not the saved v2 start");
                    BoundingBox actual = start.getPieces().getFirst().getBoundingBox();
                    require(actual.minX() == expected.minX() && actual.minY() == expected.minY()
                                    && actual.minZ() == expected.minZ()
                                    && actual.maxX() == expected.maxX()
                                    && actual.maxY() == expected.maxY()
                                    && actual.maxZ() == expected.maxZ()
                                    && level.getBlockState(root).is(SatelliteBlocks.UNDERARCHIVE_ROOT),
                            "Resumed archive box or root changed");
                    require(!SatelliteState.get(server).archiveComplete(root.asLong()),
                            "Initial resume copy already has a cleared archive");
                    return new Site(ARCHIVE_ID, start, actual, root,
                            controlsInBox(level, actual, ARCHIVE_CONTROLS, ARCHIVE_ID));
                });
                SatellitePlayerRouteProbe.run(context, world, expected, root,
                        site.control(SatelliteBlocks.UNDERARCHIVE_CLUE),
                        site.control(SatelliteBlocks.UNDERARCHIVE_RETURN), false);
                world.getServer().runOnServer(server -> {
                    require(SatelliteRuntime.archiveSafetyReady(root)
                                    && SatelliteRuntime.archiveSurveyIdle(),
                            "Resumed archive entry lacked a completed, released safety survey");
                    archiveControlSequence(server.overworld(),
                            world.getConnection().getServerPlayer(), site);
                    GemsMod.LOGGER.info("Satellite saved archive arrival, return and control sequence passed: {}",
                            expected);
                });
                System.out.println("SATELLITE_ARCHIVE_V2_RESUME_INITIAL_PASS root=" + root
                        + " box=" + expected + " savedClear=true clientRoute=true");
                return;
            }
            if (Boolean.getBoolean("gems.satelliteLowViewArrival")) {
                context.runOnClient(client -> client.options.renderDistance().set(6));
                Map<Block, BlockPos> controls = world.getServer().computeOnServer(server -> {
                    server.getPlayerList().setViewDistance(6);
                    ServerLevel level = server.overworld();
                    level.getChunk(root.getX() >> 4, root.getZ() >> 4);
                    Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                            .getValue(ResourceKey.create(Registries.STRUCTURE, ARCHIVE_ID));
                    require(structure instanceof SatelliteStructure,
                            "Low-view archive registry lost its satellite safety filter");
                    StructureStart start = level.structureManager().getStructureWithPieceAt(
                            root.getX(), root.getY(), root.getZ(), structure);
                    require(start != null && start.isValid() && start.getPieces().size() == 1
                                    && start.getPieces().getFirst() instanceof PoolElementStructurePiece pooled
                                    && pooled.getElement() instanceof SinglePoolElement single
                                    && single.getTemplateLocation().equals(Identifier.fromNamespaceAndPath(
                                            "gems", "branch_underarchive_v2")),
                            "Low-view archive is not the same saved v2 start");
                    BoundingBox actual = start.getPieces().getFirst().getBoundingBox();
                    require(actual.minX() == expected.minX() && actual.minY() == expected.minY()
                                    && actual.minZ() == expected.minZ()
                                    && actual.maxX() == expected.maxX()
                                    && actual.maxY() == expected.maxY()
                                    && actual.maxZ() == expected.maxZ()
                                    && level.getBlockState(root).is(SatelliteBlocks.UNDERARCHIVE_ROOT),
                            "Low-view archive box or root changed");
                    return controlsInBox(level, actual, ARCHIVE_CONTROLS, ARCHIVE_ID);
                });
                SatellitePlayerRouteProbe.runArchiveLowView(context, world, expected, root,
                        controls.get(SatelliteBlocks.UNDERARCHIVE_CLUE));
                return;
            }
            world.getServer().runOnServer(server -> {
                ServerLevel level = server.overworld();
                Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                        .getValue(ResourceKey.create(Registries.STRUCTURE, ARCHIVE_ID));
                require(structure instanceof SatelliteStructure,
                        "Reopened archive registry lost its satellite safety filter");
                loadArchiveHalo(level, root);
                StructureStart start = level.structureManager().getStructureWithPieceAt(
                        root.getX(), root.getY(), root.getZ(), structure);
                require(start != null && start.isValid() && start.getPieces().size() == 1
                                && start.getPieces().getFirst() instanceof PoolElementStructurePiece pooled
                                && pooled.getElement() instanceof SinglePoolElement single
                                && single.getTemplateLocation().equals(Identifier.fromNamespaceAndPath(
                                        "gems", "branch_underarchive_v2")),
                        "Reopened archive root is not the same saved v2 start");
                BoundingBox actual = start.getPieces().getFirst().getBoundingBox();
                require(actual.minX() == expected.minX() && actual.minY() == expected.minY()
                                && actual.minZ() == expected.minZ()
                                && actual.maxX() == expected.maxX()
                                && actual.maxY() == expected.maxY()
                                && actual.maxZ() == expected.maxZ()
                                && level.getBlockState(root).is(SatelliteBlocks.UNDERARCHIVE_ROOT),
                        "Reopened archive box or root changed");
                Map<Block, BlockPos> controls = controlsInBox(level, actual, ARCHIVE_CONTROLS,
                        ARCHIVE_ID);
                require(SatelliteLoadedAudit.inspectLoaded(level, start,
                                SatelliteLoadedAudit.Site.ARCHIVE)
                                == SatelliteLoadedAudit.Verdict.READY,
                        "Reopened archive site failed its loaded safety audit");
                SatelliteState saved = SatelliteState.get(server);
                require(saved.archiveComplete(root.asLong())
                                && saved.sharedArchiveBearing().filter(root::equals).isPresent()
                                && !SatelliteTownhallHints.routeDesk(server).isBlank(),
                        "Reopened archive clear or shared bearing did not persist");
                archiveArrival(level, world.getConnection().getServerPlayer(),
                        new Site(ARCHIVE_ID, start, actual, root, controls));
                require(saved.archiveComplete(root.asLong())
                                && saved.sharedArchiveBearing().filter(root::equals).isPresent(),
                        "Clue/return altered the reopened archive receipt");
                System.out.println("SATELLITE_ARCHIVE_V2_RESTART_PASS root=" + root
                        + " box=" + actual + " loadedAudit=READY sharedBearing=true clueReturn=true");
            });
            Map<Block, BlockPos> controls = world.getServer().computeOnServer(server ->
                    controlsInBox(server.overworld(), expected, ARCHIVE_CONTROLS, ARCHIVE_ID));
            SatellitePlayerRouteProbe.run(context, world, expected, root,
                    controls.get(SatelliteBlocks.UNDERARCHIVE_CLUE),
                    controls.get(SatelliteBlocks.UNDERARCHIVE_RETURN), false);
        }
    }

    private static int[] coordinates(String property, int size) {
        String raw = System.getProperty(property, "");
        String[] parts = raw.split(",", -1);
        require(parts.length == size, "Missing satellite restart coordinates: " + property);
        int[] values = new int[size];
        try {
            for (int i = 0; i < size; i++) values[i] = Integer.parseInt(parts[i]);
        } catch (NumberFormatException error) {
            throw new IllegalStateException("Invalid satellite restart coordinates: " + property,
                    error);
        }
        return values;
    }

    private static Site locate(ServerLevel level, Identifier id, Set<Block> controls,
                               int height, int rootLocalY, Block rootBlock) {
        if (id.equals(OCEAN_ID)) {
            for (Block hull : new Block[]{SatelliteBlocks.SUNKEN_DECK_TILE,
                    SatelliteBlocks.SUNKEN_WAVE_INLAY}) {
                var state = hull.defaultBlockState();
                require(state.isSolid() && state.getFluidState().isEmpty()
                                && state.is(BlockTags.FEATURES_CANNOT_REPLACE)
                                && state.is(BlockTags.GEODE_INVALID_BLOCKS),
                        "Ocean hull is not dry, solid and feature-protected: " + hull);
            }
        }
        ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, id);
        TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE,
                Identifier.fromNamespaceAndPath("gems", id.equals(OCEAN_ID)
                        ? "satellite_ocean_trial" : "satellite_archive_trial"));
        if (Boolean.getBoolean("gems.satellitePlacementDiagnostics")) {
            GemsMod.LOGGER.info("Satellite presearch {} radius={} seed={}: {}", id,
                    SEARCH_CHUNKS, SEED, presearch(level, id));
        }
        BlockPos found = level.findNearestMapStructure(tag, new BlockPos(0, 90, 0),
                SEARCH_CHUNKS, false);
        GemsMod.LOGGER.info("Satellite natural search {} radius={} seed={} rejection counts: {}",
                id, SEARCH_CHUNKS, SEED, SatelliteNoiseSurvey.diagnosticSummary());
        GemsMod.LOGGER.info("Satellite natural locate {} seed={} saved-start position: {}",
                id, SEED, found);
        if (found == null) return null;
        Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(key);
        require(structure instanceof SatelliteStructure,
                "Test-pack satellite filter did not replace the inactive jigsaw definition: " + id);
        StructureStart start = null;
        int centerX = found.getX() >> 4;
        int centerZ = found.getZ() >> 4;
        for (int radius = 0; radius <= 5 && start == null; radius++) {
            for (int dx = -radius; dx <= radius && start == null; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    var chunk = level.getChunk(centerX + dx, centerZ + dz);
                    StructureStart candidate = level.structureManager()
                            .getStartForStructure(structure, chunk);
                    if (candidate != null && candidate.isValid()) {
                        start = candidate;
                        break;
                    }
                }
            }
        }
        require(start != null && start.getPieces().size() == 1,
                "Satellite has no single saved start near " + found + ": " + id);
        if (id.equals(OCEAN_ID)) {
            require(start.getPieces().getFirst() instanceof PoolElementStructurePiece pooled
                            && pooled.getElement() instanceof SinglePoolElement single
                            && single.getTemplateLocation().equals(Identifier.fromNamespaceAndPath(
                                    "gems", "nour_sunken_relay_v4")),
                    "New ocean start did not save the beacon v4 template: " + id);
        } else if (id.equals(ARCHIVE_ID)) {
            require(start.getPieces().getFirst() instanceof PoolElementStructurePiece pooled
                            && pooled.getElement() instanceof SinglePoolElement single
                            && single.getTemplateLocation().equals(Identifier.fromNamespaceAndPath(
                                    "gems", "branch_underarchive_v2")),
                    "New archive start did not save the gallery v2 template: " + id);
        }
        BoundingBox box = start.getPieces().getFirst().getBoundingBox();
        require(box.getXSpan() == 47 && box.getYSpan() == height && box.getZSpan() == 47,
                "Unexpected satellite dimensions: " + id + " " + box);
        // The loaded safety audit reads Nour's eight-block boat approach. The
        // archive test below loads its larger city/sculk halo before entry.
        int approach = id.equals(OCEAN_ID) ? 8 : 2;
        for (int cx = (box.minX() - approach) >> 4;
             cx <= (box.maxX() + approach) >> 4; cx++) {
            for (int cz = (box.minZ() - approach) >> 4;
                 cz <= (box.maxZ() + approach) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        BlockPos root = new BlockPos(box.minX() + 23, box.minY() + rootLocalY, box.minZ() + 23);
        require(level.getBlockState(root).is(rootBlock), "Missing intact satellite root: " + id);
        return new Site(id, start, box, root, controlsInBox(level, box, controls, id));
    }

    private static Map<Block, BlockPos> controlsInBox(ServerLevel level, BoundingBox box,
                                                       Set<Block> controls, Identifier id) {
        Map<Block, BlockPos> foundControls = new HashMap<>();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Block block = level.getBlockState(pos).getBlock();
                    if (!controls.contains(block)) continue;
                    require(foundControls.put(block, pos) == null,
                            "Duplicate satellite control " + block + " at " + id);
                }
            }
        }
        require(foundControls.keySet().equals(controls), "Missing satellite controls at " + id
                + ": " + foundControls.keySet());
        return Map.copyOf(foundControls);
    }

    private static String presearch(ServerLevel level, Identifier id) {
        Identifier setId = Identifier.fromNamespaceAndPath("gems", id.equals(OCEAN_ID)
                ? "nour_sunken_relay" : "branch_underarchive_trial");
        StructureSet set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)
                .getValue(ResourceKey.create(Registries.STRUCTURE_SET, setId));
        Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValue(ResourceKey.create(Registries.STRUCTURE, id));
        var pool = level.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL)
                .getValue(ResourceKey.create(Registries.TEMPLATE_POOL, id));
        Identifier templateId = id.equals(OCEAN_ID)
                ? Identifier.fromNamespaceAndPath("gems", "nour_sunken_relay_v4")
                : Identifier.fromNamespaceAndPath("gems", "branch_underarchive_v2");
        var template = level.getServer().getStructureTemplateManager().get(templateId);
        TagKey<Biome> biomeTag = TagKey.create(Registries.BIOME,
                Identifier.fromNamespaceAndPath("gems", "has_structure/" + id.getPath()));
        var chunkSource = level.getChunkSource();
        var resolver = chunkSource.getGenerator().getBiomeSource()
                .createUncachedResolver(chunkSource.randomState());
        int eligible = 0;
        int usable = 0;
        int sampled = 0;
        for (int cx = -SEARCH_CHUNKS; cx <= SEARCH_CHUNKS; cx += 8) {
            for (int cz = -SEARCH_CHUNKS; cz <= SEARCH_CHUNKS; cz += 8) {
                var biome = resolver.getNoiseBiome(cx * 4, 16, cz * 4);
                sampled++;
                if (!biome.is(biomeTag)) continue;
                eligible++;
                if (!biome.is(Biomes.DEEP_FROZEN_OCEAN)) usable++;
            }
        }
        return "placementSet=" + setId + " present=" + (set != null) + " structure="
                + (structure == null ? "missing" : structure.getClass().getSimpleName())
                + " poolSize=" + (pool == null ? "missing" : pool.size())
                + " template=" + templateId + " templateSize="
                + template.map(value -> value.getSize().toString())
                        .orElse("missing")
                + " biomeSamples=" + eligible + "/" + sampled
                + " nonFrozen=" + usable;
    }

    private static void oceanArrival(ServerLevel level, ServerPlayer player, Site site) {
        BoundingBox box = site.box();
        require(box.minY() >= 26 && box.minY() <= 27, "Ocean seabed is at wrong depth");
        var oceanSafety = SatelliteLoadedAudit.inspectLoaded(level, site.start(),
                SatelliteLoadedAudit.Site.OCEAN);
        require(oceanSafety == SatelliteLoadedAudit.Verdict.READY,
                "Natural ocean site failed its saved-world safety survey: " + oceanSafety);
        BlockPos dock = site.root().above(19);
        BlockPos chamber = site.root().below(13);
        require(safe(level, dock) && safe(level, chamber), "Ocean dock or dry chamber is blocked");
        require(site.control(SatelliteBlocks.SUNKEN_BUOY).getY() == box.minY() + 37,
                "Buoy is below the sea surface");
        for (int y : new int[]{40, 55, 62}) {
            for (BlockPos pos : new BlockPos[]{
                    new BlockPos(box.minX() - 2, y, site.root().getZ()),
                    new BlockPos(box.maxX() + 2, y, site.root().getZ()),
                    new BlockPos(site.root().getX(), y, box.minZ() - 2),
                    new BlockPos(site.root().getX(), y, box.maxZ() + 2)}) {
                require(level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER),
                        "Boat approach is not deep open water at " + pos);
            }
        }
        player.teleportTo(level, dock.getX() + 0.5, dock.getY(), dock.getZ() + 0.5,
                EnumSet.noneOf(Relative.class), 0F, 0F, false);
        activate(level, player, site.control(SatelliteBlocks.SUNKEN_BUOY));
        require(player.blockPosition().equals(chamber), "Buoy did not reach the dry room");
        activate(level, player, site.control(SatelliteBlocks.SUNKEN_RETURN));
        require(player.blockPosition().equals(dock), "Ocean return missed the dock");
    }

    private static void archiveArrival(ServerLevel level, ServerPlayer player, Site site) {
        BoundingBox box = site.box();
        BlockPos surface = site.root().above(22);
        BlockPos hall = site.root().below(22);
        require(safe(level, surface) && safe(level, hall),
                "Underarchive surface or hall landing is blocked");
        require(site.control(SatelliteBlocks.UNDERARCHIVE_CLUE).getY() == box.minY() + 49,
                "Underarchive surface clue is buried");
        loadArchiveHalo(level, site.root());
        var archiveSafety = SatelliteLoadedAudit.inspectLoaded(level, site.start(),
                SatelliteLoadedAudit.Site.ARCHIVE);
        require(archiveSafety == SatelliteLoadedAudit.Verdict.READY,
                "Natural Underarchive failed its saved-world safety survey: " + archiveSafety);
        player.teleportTo(level, surface.getX() + 0.5, surface.getY(), surface.getZ() + 0.5,
                EnumSet.noneOf(Relative.class), 0F, 0F, false);
        activate(level, player, site.control(SatelliteBlocks.UNDERARCHIVE_CLUE));
        require(player.blockPosition().equals(hall), "Underarchive clue did not reach the hall");
        activate(level, player, site.control(SatelliteBlocks.UNDERARCHIVE_RETURN));
        require(player.blockPosition().equals(surface), "Underarchive return missed the surface");
    }

    private static void loadArchiveHalo(ServerLevel level, BlockPos root) {
        for (int cx = (root.getX() - 128) >> 4; cx <= (root.getX() + 128) >> 4; cx++) {
            for (int cz = (root.getZ() - 128) >> 4; cz <= (root.getZ() + 128) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
    }

    /** Server control sequence only; physical solo navigation remains a separate gate. */
    private static void oceanControlSequence(ServerLevel level, ServerPlayer player, Site site) {
        FriendDomainUnlockState.get(level.getServer()).clear("nour");
        BlockPos root = site.root();
        activate(level, player, site.control(SatelliteBlocks.SUNKEN_TUNING_CONSOLE));
        activate(level, player, site.control(SatelliteBlocks.SUNKEN_WEST_HYDROPHONE));
        activate(level, player, site.control(SatelliteBlocks.SUNKEN_EAST_HYDROPHONE));
        activate(level, player, site.control(SatelliteBlocks.SUNKEN_TUNING_CONSOLE));
        SatelliteState state = SatelliteState.get(level.getServer());
        require(state.oceanComplete(root.asLong()),
                "Natural ocean control sequence did not save its shared clear");
        require(state.sharedOceanBearing().isPresent()
                        && !SatelliteTownhallHints.signalTable(level.getServer()).isBlank(),
                "Natural ocean clear did not expose a late-join bearing");
    }

    /** The shelves are visited in story order; no second player is required. */
    private static void archiveControlSequence(ServerLevel level, ServerPlayer player, Site site) {
        CampaignPersistentState.get(level.getServer()).recordVictory(
                List.of(player.getUUID()), LandmarkKey.BRANCH);
        BlockPos root = site.root();
        activate(level, player, site.control(SatelliteBlocks.UNDERARCHIVE_DESK));
        activate(level, player, site.control(SatelliteBlocks.UNDERARCHIVE_FIRST_INDEX));
        activate(level, player, site.control(SatelliteBlocks.UNDERARCHIVE_THIRD_INDEX));
        activate(level, player, site.control(SatelliteBlocks.UNDERARCHIVE_SECOND_INDEX));
        activate(level, player, site.control(SatelliteBlocks.UNDERARCHIVE_DESK));
        SatelliteState state = SatelliteState.get(level.getServer());
        require(state.archiveComplete(root.asLong()),
                "Natural archive control sequence did not save its shared clear");
        require(state.sharedArchiveBearing().isPresent()
                        && !SatelliteTownhallHints.routeDesk(level.getServer()).isBlank(),
                "Natural archive clear did not expose a late-join bearing");
    }

    private static boolean safe(ServerLevel level, BlockPos feet) {
        return !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && level.getFluidState(feet).isEmpty() && level.getFluidState(feet.above()).isEmpty();
    }

    private static void activate(ServerLevel level, ServerPlayer player, BlockPos pos) {
        InteractionResult result = UseBlockCallback.EVENT.invoker().interact(player, level,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        require(result == InteractionResult.SUCCESS_SERVER,
                "Satellite control ignored use at " + pos + ": " + result);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
