package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.DomainBlocks;
import com.feel.gems.adventure.AliRouteState;
import com.feel.gems.adventure.OthmanArchiveState;
import com.feel.gems.adventure.worldgen.AliDryArrivalStructure;
import com.feel.gems.adventure.worldgen.site.BoundedJigsawStart;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.Heightmap;

/** One opt-in, task-scoped natural-world gate for the three full district candidates. */
@SuppressWarnings("UnstableApiUsage")
public final class GroupedDistrictFullTrialClientGameTest implements FabricClientGameTest {
    private static final String SEED_A = "SecondChanceDistrictFullA-20261003";
    private static final String SEED_B = "SecondChanceDistrictFullB-20261003";
    private static final int ALI_MAX_CHUNK_RADIUS = 256;
    // Seed A has 14 final-site biome candidates in the 256-chunk trial lattice.
    private static final int ALI_MAX_CHUNK_LOADS = 14;
    private static final int ALI_SITE_STEP = 16;
    private static final int ALI_SITE_RING = 4;
    private static final int ALI_MAX_DOWN_SHIFT = 12;
    private static final TagKey<Biome> ALI_BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath("gems",
                    "has_structure/ali_splitstone_cloister"));
    private static final List<Target> TARGETS = List.of(
            new Target("hushwire", "hushwire_v10", List.of(
                    "hushwire_record_hall_v10", "hushwire_receiver_field_v5",
                    "hushwire_relay_crypt_v5", "hushwire_procession_v5")),
            new Target("othman_library", "othman_library_themed", List.of(
                    "othman_library_archive_v11", "othman_catalog_rotunda_v5",
                    "othman_study_arrival_wing_v7")),
            new Target("ali_splitstone_cloister", "ali_splitstone_cloister", List.of(
                    "ali_splitstone_citadel_full_v6", "ali_high_atlas_hall_full_v5",
                    "ali_cut_chapel_full_v5")));

    private static List<Target> activeTargets() {
        String selection = System.getProperty("gems.groupedDistrictTargets", "");
        if (selection.isEmpty()) return TARGETS;
        if (selection.equals("ali_splitstone_cloister")) return List.of(TARGETS.get(2));
        require(selection.equals("hushwire,othman_library"),
                "Focused district gate accepts Ali alone or Hushwire and Othman together");
        return TARGETS.subList(0, 2);
    }

    private record Target(String name, String structure, List<String> templates) {
        String root() { return "gems:" + templates.getFirst(); }
    }
    private record Site(Target target, ChunkPos startChunk, Rotation rotation,
                        Map<String, BoundingBox> pieces, String receipt) {
        BoundingBox root() { return pieces.get(target.root()); }
        List<BoundingBox> allBoxes() { return List.copyOf(pieces.values()); }
    }
    private record AliCandidate(ChunkPos chunk, int finalSiteOptions,
                                int roughCells, int heightSpan,
                                List<String> siteBiomes) {
        boolean tagged() { return finalSiteOptions > 0; }
        int distance() { return Math.max(Math.abs(chunk.x()), Math.abs(chunk.z())); }
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!Boolean.getBoolean("gems.groupedDistrictFullTrial")) return;
        String seed = System.getProperty("gems.groupedDistrictSeed", "");
        require(SEED_A.equals(seed) || SEED_B.equals(seed),
                "Grouped trial seed must be one of the two predeclared values");
        boolean restart = Boolean.getBoolean("gems.groupedDistrictRestart");
        boolean control = Boolean.getBoolean("gems.groupedDistrictControl");
        require(!restart || !control, "Control and restart modes cannot be combined");
        if (restart) {
            Path save = context.computeOnClient(client -> client.gameDirectory.toPath()
                    .resolve("saves").resolve("New World"));
            require(Files.isRegularFile(save.resolve("level.dat")),
                    "Restart must open a copied closed save");
            try (TestSingleplayerContext world = new TestWorldSaveImpl(context, save).open()) {
                world.getConnection().waitForChunksRender();
                exercise(context, world, seed, true);
            }
        } else {
            try (TestSingleplayerContext world = context.worldBuilder()
                    .setUseConsistentSettings(false)
                    .adjustSettings(settings -> {
                        settings.setSeed(seed);
                        settings.setGenerateStructures(true);
                    }).create()) {
                world.getConnection().waitForChunksRender();
                if (control) inspectUnmodifiedControl(context, world, seed);
                else exercise(context, world, seed, false);
            }
        }
    }

    private static void exercise(ClientGameTestContext context,
                                 TestSingleplayerContext world, String seed,
                                 boolean restart) {
        world.getServer().runOnServer(server -> {
            if (Boolean.getBoolean("gems.aliPublicGate"))
                assertEffectivePublicAliPack(server.overworld());
            else if (Boolean.getBoolean("gems.groupedDistrictPublicGate"))
                assertEffectivePublicHushwireOthmanPack(server.overworld());
            else assertEffectiveTrialPack(server.overworld());
        });
        Map<String, ChunkPos> expectedChunks = restart
                || Boolean.getBoolean("gems.groupedDistrictTraceExactChunks")
                ? expectedChunks() : Map.of();
        List<String> receipts = new ArrayList<>();
        Set<String> lightingIssues = new LinkedHashSet<>();
        for (Target target : activeTargets()) {
            int issuesBeforeSite = lightingIssues.size();
            Site site = world.getServer().computeOnServer(server ->
                    locate(server.overworld(), target, expectedChunks.get(target.name()), seed));
            receipts.add(site.receipt());
            System.out.println("GROUPED_DISTRICT_START phase="
                    + (restart ? "restart" : "initial") + " seed=" + seed
                    + " receipt=" + site.receipt());
            String outside = world.getServer().computeOnServer(server ->
                    outsideHash(server.overworld(), site.allBoxes()));
            System.out.println("GROUPED_DISTRICT_OUTSIDE phase="
                    + (restart ? "restart" : "initial") + " seed=" + seed
                    + " target=" + target.name() + " sha256=" + outside);
            if (target.name().equals("ali_splitstone_cloister"))
                auditAliThreeWide(world, site, seed, restart);
            List<BoundingBox> all = site.allBoxes();
            List<BoundingBox> visibleBuildings = orderedBoxes(site);
            List<NaturalDistrictRouteProbe.Gate> gates = gates(site);
            List<NaturalDistrictRouteProbe.WalkApproach> walks = new ArrayList<>();
            world.getServer().runOnServer(server -> {
                ServerLevel level = server.overworld();
                level.clockManager().setTotalTicks(level.registryAccess()
                        .lookupOrThrow(Registries.WORLD_CLOCK)
                        .getOrThrow(WorldClocks.OVERWORLD), 1000L);
            });
            for (NaturalDistrictRouteProbe.Gate gate : gates) {
                NaturalDistrictRouteProbe.WalkApproach walk = NaturalDistrictRouteProbe.walk(
                        context, world, target.name(), visibleBuildings,
                        visibleBuildings, gate);
                walks.add(walk);
                auditWalk(world, site, walk, lightingIssues);
                for (int piece = 1; piece < target.templates().size(); piece++) {
                    String destinationName = target.templates().get(piece);
                    BoundingBox destination = site.pieces().get("gems:" + destinationName);
                    NaturalDistrictRouteProbe.WalkApproach intra =
                            NaturalDistrictRouteProbe.walkBetween(context, world,
                                    target.name(), "to-" + destinationName,
                                    visibleBuildings, destination);
                    walks.add(intra);
                    auditWalk(world, site, intra, lightingIssues);
                    if (piece + 1 < target.templates().size()) {
                        NaturalDistrictRouteProbe.WalkApproach back =
                                NaturalDistrictRouteProbe.walkBetween(context, world,
                                        target.name(), "return-root-" + piece,
                                        visibleBuildings, site.root());
                        walks.add(back);
                        auditWalk(world, site, back, lightingIssues);
                    }
                }
                NaturalDistrictRouteProbe.WalkApproach back =
                        NaturalDistrictRouteProbe.walkBetween(context, world,
                                target.name(), "public-return",
                                visibleBuildings, site.root());
                walks.add(back);
                auditWalk(world, site, back, lightingIssues);
            }
            for (String template : target.templates()) {
                BoundingBox box = site.pieces().get("gems:" + template);
                NaturalDistrictVisualClientGameTest.capture(context, world, box, all,
                        12, 0, true, imageName(seed, target, template, "day"));
            }
            world.getServer().runOnServer(server -> {
                ServerLevel level = server.overworld();
                level.clockManager().setTotalTicks(level.registryAccess()
                        .lookupOrThrow(Registries.WORLD_CLOCK)
                        .getOrThrow(WorldClocks.OVERWORLD), 13000L);
            });
            for (String template : target.templates()) {
                BoundingBox box = site.pieces().get("gems:" + template);
                NaturalDistrictVisualClientGameTest.capture(context, world, box, all,
                        12, 0, true, imageName(seed, target, template, "night"));
            }
            for (NaturalDistrictRouteProbe.WalkApproach walk : walks) {
                auditWalk(world, site, walk, lightingIssues);
            }
            inspectControls(world, site, restart);
            System.out.println((lightingIssues.size() == issuesBeforeSite
                    ? "GROUPED_DISTRICT_SITE_PASS phase="
                    : "GROUPED_DISTRICT_SITE_LIGHT_ISSUES phase=")
                    + (restart ? "restart" : "initial") + " seed=" + seed
                    + " target=" + target.name() + " walks=" + walks.size()
                    + " views=" + target.templates().size() * 2);
        }
        String extraStart = System.getProperty("gems.groupedDistrictTraceExtraStartChunk", "");
        if (!extraStart.isBlank()) {
            require(!restart && Boolean.getBoolean("gems.groupedDistrictTraceExactChunks")
                    && seed.equals(SEED_B) && extraStart.equals("3,-20"),
                    "Trace may fully load only B's saved Othman owner chunk");
            world.getServer().runOnServer(server -> {
                ServerLevel level = server.overworld();
                Identifier id = Identifier.fromNamespaceAndPath("gems", "othman_library_themed");
                Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                        .getValue(ResourceKey.create(Registries.STRUCTURE, id));
                require(structure != null, "Othman structure missing before extra start load");
                ChunkPos owner = new ChunkPos(3, -20);
                StructureStart start = level.structureManager().getStartForStructure(structure,
                        level.getChunk(owner.x(), owner.z()));
                require(start != null && start.isValid() && start.getChunkPos().equals(owner),
                        "Extra Othman owner chunk lacks its exact saved start");
            });
            System.out.println("GROUPED_DISTRICT_TRACE_EXTRA_START_FULL seed=" + seed
                    + " target=othman_library chunk=" + extraStart);
        }
        require(lightingIssues.isEmpty(), "Grouped district interior light issues: "
                + String.join("; ", lightingIssues));
        String sha = sha256(String.join("\n", receipts));
        if (restart) {
            require(sha.equals(System.getProperty("gems.groupedDistrictExpectedSha256", "")),
                    "Saved start/template/bounds receipt changed across JVM restart: " + sha);
        }
        System.out.println("GROUPED_DISTRICT_PASS phase="
                + (restart ? "restart" : "initial") + " seed=" + seed
                + " starts=" + activeTargets().size() + " sha256=" + sha);
    }

    private static void inspectUnmodifiedControl(ClientGameTestContext context,
                                                  TestSingleplayerContext world, String seed) {
        Path input = context.computeOnClient(client -> client.gameDirectory.toPath()
                .resolve("grouped-control-boxes.txt"));
        require(Files.isRegularFile(input),
                "Unmodified control needs initial trial's exact piece boxes");
        try {
            List<String> lines = Files.readAllLines(input, StandardCharsets.UTF_8);
            List<Target> targets = activeTargets();
            require(lines.size() == targets.size(), "Control must cover selected targets");
            for (int index = 0; index < targets.size(); index++) {
                Target target = targets.get(index);
                String[] parts = lines.get(index).split("=", 2);
                require(parts.length == 2 && parts[0].equals(target.name()),
                        "Control box order/target mismatch");
                List<BoundingBox> boxes = new ArrayList<>();
                for (String raw : parts[1].split("\\|")) {
                    String[] numbers = raw.split(",");
                    require(numbers.length == 6, "Bad control bounding box");
                    int[] values = new int[6];
                    for (int i = 0; i < 6; i++) values[i] = Integer.parseInt(numbers[i]);
                    boxes.add(new BoundingBox(values[0], values[1], values[2],
                            values[3], values[4], values[5]));
                }
                require(boxes.size() == target.templates().size(),
                        "Control piece count differs for " + target.name());
                String outside = world.getServer().computeOnServer(server ->
                        outsideHash(server.overworld(), boxes));
                System.out.println("GROUPED_DISTRICT_OUTSIDE phase=control seed=" + seed
                        + " target=" + target.name() + " sha256=" + outside);
            }
            System.out.println("GROUPED_DISTRICT_CONTROL_PASS seed=" + seed
                    + " targets=" + targets.size() + " mode=unmodified-worldgen");
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot read control box manifest", error);
        }
    }

    private static String outsideHash(ServerLevel level, List<BoundingBox> boxes) {
        Set<Long> shell = new HashSet<>();
        for (BoundingBox box : boxes) {
            for (int x = box.minX() - 3; x <= box.maxX() + 3; x++) {
                for (int z = box.minZ() - 3; z <= box.maxZ() + 3; z++) {
                    level.getChunk(x >> 4, z >> 4);
                    for (int y = box.minY() - 4; y <= box.maxY() + 4; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (boxes.stream().noneMatch(piece -> piece.isInside(pos))) {
                            shell.add(pos.asLong());
                        }
                    }
                }
            }
        }
        List<Long> ordered = new ArrayList<>(shell);
        Collections.sort(ordered);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (long encoded : ordered) {
                BlockPos pos = BlockPos.of(encoded);
                String sample = encoded + "=" + level.getBlockState(pos) + "\n";
                digest.update(sample.getBytes(StandardCharsets.UTF_8));
            }
            String hash = java.util.HexFormat.of().formatHex(digest.digest());
            System.out.println("GROUPED_DISTRICT_OUTSIDE_SAMPLE samples=" + ordered.size()
                    + " sha256=" + hash);
            return hash;
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static List<BoundingBox> orderedBoxes(Site site) {
        return site.target().templates().stream()
                .map(name -> site.pieces().get("gems:" + name)).toList();
    }

    private static List<NaturalDistrictRouteProbe.Gate> gates(Site site) {
        return switch (site.target().name()) {
            case "hushwire" -> List.of(new NaturalDistrictRouteProbe.Gate(
                    "hall-entry", 0, site.rotation().rotate(Direction.NORTH), 28, 6, 4));
            case "othman_library" -> List.of(
                    new NaturalDistrictRouteProbe.Gate(
                            "library-south", 0, Direction.NORTH, 2, 13, 3, 0, 2),
                    new NaturalDistrictRouteProbe.Gate(
                            "library-north", 0, Direction.SOUTH, 2, 13, 2, 0, 2));
            case "ali_splitstone_cloister" -> List.of(new NaturalDistrictRouteProbe.Gate(
                    "citadel-north", 0, site.rotation().rotate(Direction.SOUTH), 16, 6, 4));
            default -> throw new IllegalStateException("Unknown grouped target");
        };
    }

    private static void auditAliThreeWide(TestSingleplayerContext world,
            Site site, String seed, boolean restart) {
        world.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            BoundingBox carrier = site.pieces().get("gems:ali_arrival_reference_v5");
            require(carrier != null && carrier.getYSpan() == 1
                    && carrier.getXSpan() * carrier.getZSpan() == 180,
                    "Ali saved arrival carrier missing or malformed");
            Direction outward = site.rotation().rotate(Direction.NORTH);
            Direction lateral = site.rotation().rotate(Direction.EAST);
            BlockPos door = site.root().getCenter().relative(outward, 23);
            int[][] floorY = new int[21][9];
            int valid = 0;
            for (int depth = 1; depth <= 20; depth++) {
                for (int cross = -3; cross <= 3; cross++) {
                    BlockPos column = door.relative(outward, depth)
                            .relative(lateral, cross);
                    for (int y = carrier.minY() - 4; y <= carrier.minY() + 4; y++) {
                        BlockPos floor = column.atY(y);
                        if (!level.getBlockState(floor).is(DomainBlocks.WAYMARK_TILE))
                            continue;
                        boolean wide = true;
                        for (int side : new int[]{-1, 0, 1}) {
                            BlockPos paved = floor.relative(lateral, side);
                            BlockState state = level.getBlockState(paved);
                            wide &= side == 0 ? state.is(DomainBlocks.WAYMARK_TILE)
                                    : state.is(DomainBlocks.SPLITSTONE)
                                            || state.is(DomainBlocks.WAYMARK_TILE);
                            for (int rise = 1; rise <= 2; rise++) {
                                BlockPos air = paved.above(rise);
                                BlockState cover = level.getBlockState(air);
                                wide &= cover.getCollisionShape(level, air).isEmpty()
                                        && cover.getFluidState().isEmpty();
                            }
                        }
                        if (wide) {
                            floorY[depth][cross + 4] = y;
                            valid++;
                        }
                    }
                }
            }
            ArrayDeque<int[]> queue = new ArrayDeque<>();
            boolean[][] visited = new boolean[21][9];
            for (int cross = -1; cross <= 1; cross++) {
                if (floorY[1][cross + 4] == 0) continue;
                visited[1][cross + 4] = true;
                queue.addLast(new int[]{1, cross});
            }
            boolean bank = false;
            while (!queue.isEmpty()) {
                int[] at = queue.removeFirst();
                if (at[0] >= 16) {
                    bank = true;
                    break;
                }
                for (int[] delta : new int[][]{{1, 0}, {-1, 0},
                        {0, 1}, {0, -1}}) {
                    int depth = at[0] + delta[0];
                    int cross = at[1] + delta[1];
                    if (depth < 1 || depth > 20 || Math.abs(cross) > 3
                            || visited[depth][cross + 4]
                            || floorY[depth][cross + 4] == 0
                            || Math.abs(floorY[depth][cross + 4]
                                    - floorY[at[0]][at[1] + 4]) > 1) continue;
                    visited[depth][cross + 4] = true;
                    queue.addLast(new int[]{depth, cross});
                }
            }
            require(bank, "Ali exact three-wide exterior route does not reach dry bank"
                    + " valid=" + valid + " start=" + site.startChunk());
            System.out.println("ALI_THREE_WIDE_PASS phase="
                    + (restart ? "restart" : "initial") + " seed=" + seed
                    + " valid=" + valid + " bank=true carrier=" + bounds(carrier));
        });
    }

    private static Site locate(ServerLevel level, Target target, ChunkPos savedChunk,
                               String seed) {
        Identifier id = Identifier.fromNamespaceAndPath("gems", target.structure());
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Structure structure = registry.getValue(ResourceKey.create(Registries.STRUCTURE, id));
        require(structure != null, "Trial structure not registered: " + id);
        StructureStart start = null;
        if (savedChunk == null) {
            start = target.name().equals("othman_library")
                    ? locateThemedOthman(level, structure, seed)
                    : target.name().equals("ali_splitstone_cloister")
                    ? locateAliBounded(level, structure)
                    : locateAtlasTarget(level, target, structure);
        } else {
            start = level.structureManager().getStartForStructure(structure,
                    level.getChunk(savedChunk.x(), savedChunk.z()));
            require(start != null && start.isValid()
                    && start.getChunkPos().equals(savedChunk),
                    "Copied save lost exact start chunk for " + target.name());
        }
        require(start != null && start.isValid(),
                "Located " + target.name() + " but exact trial ID has no saved start");
        Map<String, BoundingBox> pieces = new HashMap<>();
        Rotation rootRotation = null;
        for (StructurePiece piece : start.getPieces()) {
            require(piece instanceof PoolElementStructurePiece,
                    "Unexpected non-pool piece in " + target.name());
            PoolElementStructurePiece pooled = (PoolElementStructurePiece) piece;
            require(pooled.getElement() instanceof SinglePoolElement,
                    "Unexpected non-single pool piece in " + target.name());
            String name = ((SinglePoolElement) pooled.getElement())
                    .getTemplateLocation().toString();
            require(pieces.putIfAbsent(name, pooled.getBoundingBox()) == null,
                    "Duplicate template in " + target.name() + ": " + name);
            if (name.equals(target.root())) rootRotation = pooled.getRotation();
        }
        Set<String> expected = new HashSet<>();
        for (String template : target.templates()) expected.add("gems:" + template);
        if (target.name().equals("ali_splitstone_cloister"))
            expected.add("gems:ali_arrival_reference_v5");
        require(pieces.keySet().equals(expected),
                "Wrong exact trial templates for " + target.name() + ": " + pieces.keySet());
        require(rootRotation != null, "Missing trial root rotation for " + target.name());
        for (BoundingBox box : pieces.values()) {
            for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
                for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) level.getChunk(x, z);
            }
        }
        ChunkPos chunk = start.getChunkPos();
        StringBuilder receipt = new StringBuilder(target.name()).append(';')
                .append(id).append(';').append(chunk.x()).append(',').append(chunk.z())
                .append(';').append(rootRotation);
        for (String template : target.templates()) {
            receipt.append(';').append("gems:").append(template).append('@')
                    .append(bounds(pieces.get("gems:" + template)));
        }
        if (target.name().equals("ali_splitstone_cloister"))
            receipt.append(";gems:ali_arrival_reference_v5@")
                    .append(bounds(pieces.get("gems:ali_arrival_reference_v5")));
        return new Site(target, chunk, rootRotation, Map.copyOf(pieces), receipt.toString());
    }

    private static StructureStart locateAtlasTarget(ServerLevel level, Target target,
                                                     Structure structure) {
        TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE,
                Identifier.fromNamespaceAndPath("gems", "atlas_target/" + target.name()));
        BlockPos located = level.findNearestMapStructure(tag, new BlockPos(0, 80, 0),
                128, false);
        require(located != null, "No natural " + target.name()
                + " start within fixed search radius");
        int cx = located.getX() >> 4;
        int cz = located.getZ() >> 4;
        for (int radius = 0; radius <= 5; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    StructureStart candidate = level.structureManager()
                            .getStartForStructure(structure, level.getChunk(cx + dx, cz + dz));
                    if (candidate != null && candidate.isValid()) return candidate;
                }
            }
        }
        return null;
    }

    /** Exact trial lattice; noise ranks all candidates before bounded chunk loads. */
    private static StructureStart locateAliBounded(ServerLevel level,
                                                    Structure structure) {
        StructureSet set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)
                .getValue(ResourceKey.create(Registries.STRUCTURE_SET,
                        Identifier.fromNamespaceAndPath("gems",
                                "ali_splitstone_cloister")));
        require(set != null && set.placement() instanceof RandomSpreadStructurePlacement
                        && set.structures().size() == 1
                        && set.structures().getFirst().structure().value() == structure,
                "Unexpected Ali trial structure set");
        RandomSpreadStructurePlacement spread = (RandomSpreadStructurePlacement)
                set.placement();
        require(spread.spacing() == 64 && spread.separation() == 24,
                "Ali trial lattice changed");
        require(structure instanceof AliDryArrivalStructure ali
                        && ali.reservationMaximumHorizontalShiftBlocks()
                                == ALI_SITE_STEP * ALI_SITE_RING,
                "Ali trial site-search reach changed");
        var source = level.getChunkSource();
        var state = source.getGeneratorState();
        var resolver = source.getGenerator().getBiomeSource()
                .createUncachedResolver(source.randomState());
        List<AliCandidate> candidates = new ArrayList<>();
        Set<ChunkPos> unique = new HashSet<>();
        int regionRadius = Math.floorDiv(ALI_MAX_CHUNK_RADIUS, spread.spacing());
        for (int rx = -regionRadius; rx <= regionRadius; rx++) {
            for (int rz = -regionRadius; rz <= regionRadius; rz++) {
                ChunkPos chunk = spread.getPotentialStructureChunk(level.getSeed(),
                        rx * spread.spacing(), rz * spread.spacing());
                if (Math.max(Math.abs(chunk.x()), Math.abs(chunk.z()))
                            > ALI_MAX_CHUNK_RADIUS
                        || !spread.isStructureChunk(state, chunk.x(), chunk.z())
                        || !unique.add(chunk)) continue;
                boolean loaded = source.hasChunk(chunk.x(), chunk.z());
                AliCandidate candidate = aliNoiseCandidate(level, resolver, chunk);
                require(source.hasChunk(chunk.x(), chunk.z()) == loaded,
                        "Ali noise survey loaded a candidate chunk " + chunk);
                candidates.add(candidate);
            }
        }
        require(!candidates.isEmpty() && candidates.size() <= 65,
                "Ali lattice exceeded 256-chunk candidate bound: "
                        + candidates.size());
        candidates.sort(Comparator.comparingInt(AliCandidate::finalSiteOptions).reversed()
                .thenComparingInt(AliCandidate::roughCells)
                .thenComparingInt(AliCandidate::heightSpan)
                .thenComparingInt(AliCandidate::distance)
                .thenComparingInt(candidate -> candidate.chunk().x())
                .thenComparingInt(candidate -> candidate.chunk().z()));
        int tagged = 0, loaded = 0, rejected = 0;
        for (int rank = 0; rank < candidates.size(); rank++) {
            AliCandidate candidate = candidates.get(rank);
            if (candidate.tagged()) tagged++;
            System.out.println("GROUPED_DISTRICT_ALI_CANDIDATE rank=" + rank
                    + " chunk=" + candidate.chunk() + " tagged=" + candidate.tagged()
                    + " finalSiteOptions=" + candidate.finalSiteOptions()
                    + " rough=" + candidate.roughCells()
                    + " span=" + candidate.heightSpan()
                    + " siteBiomes=" + candidate.siteBiomes());
        }
        for (AliCandidate candidate : candidates) {
            if (loaded >= ALI_MAX_CHUNK_LOADS) break;
            loaded++;
            ChunkPos chunk = candidate.chunk();
            StructureStart start = level.structureManager().getStartForStructure(
                    structure, level.getChunk(chunk.x(), chunk.z()));
            boolean valid = start != null && start.isValid()
                    && start.getChunkPos().equals(chunk);
            System.out.println("GROUPED_DISTRICT_ALI_TRY chunk=" + chunk
                    + " result=" + (valid ? "accepted" : "rejected")
                    + " loaded=" + loaded);
            if (valid) {
                System.out.println("GROUPED_DISTRICT_ALI_SEARCH maxChunkRadius=256"
                        + " potential=" + candidates.size() + " tagged=" + tagged
                        + " loaded=" + loaded + " rejected=" + rejected
                        + " selected=" + chunk);
                return start;
            }
            rejected++;
        }
        System.out.println("GROUPED_DISTRICT_ALI_SEARCH maxChunkRadius=256"
                + " potential=" + candidates.size() + " tagged=" + tagged
                + " loaded=" + loaded + " rejected=" + rejected
                + " loadCap=" + ALI_MAX_CHUNK_LOADS + " selected=none");
        throw new IllegalStateException("No natural Ali v5 start among bounded"
                + " noise-ranked candidates");
    }

    private static AliCandidate aliNoiseCandidate(ServerLevel level,
            net.minecraft.world.level.biome.BiomeResolver resolver,
            ChunkPos chunk) {
        var source = level.getChunkSource();
        var generator = source.getGenerator();
        int centerX = chunk.getMinBlockX() + 8;
        int centerZ = chunk.getMinBlockZ() + 8;
        // Jigsaw anchors the 47-wide root at the chunk corner. Its four
        // rotations put the root center at +/-23 blocks from that corner.
        // Ali may then move that center on a 16-block grid, up to 64 blocks,
        // and lower it by at most 12. Rank the biome at those final sites;
        // the old chunk-center vertical column missed valid shifted sites.
        int finalSiteOptions = 0;
        Set<String> siteBiomes = new TreeSet<>();
        for (int rootX : new int[]{chunk.getMinBlockX() - 23,
                chunk.getMinBlockX() + 23}) {
            for (int rootZ : new int[]{chunk.getMinBlockZ() - 23,
                    chunk.getMinBlockZ() + 23}) {
                int projectedY = generator.getBaseHeight(rootX, rootZ,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        level, source.randomState());
                if (projectedY <= level.getMinY()
                        || projectedY >= level.getMaxY()) continue;
                for (int sx = -ALI_SITE_RING; sx <= ALI_SITE_RING; sx++) {
                    for (int sz = -ALI_SITE_RING; sz <= ALI_SITE_RING; sz++) {
                        int x = rootX + sx * ALI_SITE_STEP;
                        int z = rootZ + sz * ALI_SITE_STEP;
                        if (Math.abs(x >> 4) > ALI_MAX_CHUNK_RADIUS
                                || Math.abs(z >> 4) > ALI_MAX_CHUNK_RADIUS
                                || Math.abs((x >> 4) - chunk.x()) > 8
                                || Math.abs((z >> 4) - chunk.z()) > 8) continue;
                        boolean allowed = false;
                        for (int quartY = (projectedY - ALI_MAX_DOWN_SHIFT) >> 2;
                             quartY <= projectedY >> 2; quartY++) {
                            var biome = resolver.getNoiseBiome(x >> 2, quartY, z >> 2);
                            siteBiomes.add(biome.unwrapKey()
                                    .map(key -> key.identifier().toString())
                                    .orElse("unkeyed"));
                            allowed |= biome.is(ALI_BIOMES);
                        }
                        if (allowed) finalSiteOptions++;
                    }
                }
            }
        }
        int bestRough = Integer.MAX_VALUE, bestSpan = Integer.MAX_VALUE;
        for (boolean longX : new boolean[]{true, false}) {
            int rough = 0, low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
            for (int along : new int[]{-60, -40, -20, 0, 20, 40, 60}) {
                for (int cross : new int[]{-20, 0, 20}) {
                    int x = centerX + (longX ? along : cross);
                    int z = centerZ + (longX ? cross : along);
                    int feet = generator.getBaseHeight(x, z,
                            Heightmap.Types.WORLD_SURFACE_WG,
                            level, source.randomState());
                    if (feet <= level.getMinY() || feet + 2 >= level.getMaxY()) {
                        rough++;
                        continue;
                    }
                    NoiseColumn noise = generator.getBaseColumn(x, z,
                            level, source.randomState());
                    BlockState support = noise.getBlock(feet - 1);
                    if (!support.isSolid() || !support.getFluidState().isEmpty()
                            || !noise.getBlock(feet).isAir()) rough++;
                    low = Math.min(low, feet);
                    high = Math.max(high, feet);
                }
            }
            int span = low == Integer.MAX_VALUE ? Integer.MAX_VALUE : high - low;
            if (rough < bestRough || rough == bestRough && span < bestSpan) {
                bestRough = rough;
                bestSpan = span;
            }
        }
        return new AliCandidate(chunk, finalSiteOptions, bestRough, bestSpan,
                List.copyOf(siteBiomes));
    }

    /** A locate of the canonical Atlas tag cannot identify the excluded themed set. */
    private static StructureStart locateThemedOthman(ServerLevel level, Structure structure,
                                                      String seed) {
        StructureSet set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)
                .getValue(ResourceKey.create(Registries.STRUCTURE_SET,
                        Identifier.fromNamespaceAndPath("gems", "othman_library_themed")));
        require(set != null && set.placement() instanceof RandomSpreadStructurePlacement
                        && set.structures().size() == 1
                        && set.structures().getFirst().structure().value() == structure,
                "Unexpected Othman themed placement in grouped trial");
        RandomSpreadStructurePlacement spread = (RandomSpreadStructurePlacement) set.placement();
        if (SEED_A.equals(seed) || SEED_B.equals(seed)) {
            // Cohort 33 generated A's exact start. The failed cohort-32 B
            // save also contains an exact v11 themed start in chunk [3,-8].
            // Verify either hint against the live placement and saved start
            // instead of generating rejected full chunks on every rerun.
            ChunkPos hinted = SEED_A.equals(seed)
                    ? new ChunkPos(-108, 99) : new ChunkPos(3, -8);
            var state = level.getChunkSource().getGeneratorState();
            require(level.getSeed() == (long) seed.hashCode()
                            && spread.isStructureChunk(state, hinted.x(), hinted.z())
                            && spread.getPotentialStructureChunk(level.getSeed(),
                                    hinted.x(), hinted.z()).equals(hinted),
                    "Othman hint is not an active themed placement for " + seed
                            + ": " + hinted);
            StructureStart start = level.structureManager().getStartForStructure(
                    structure, level.getChunk(hinted.x(), hinted.z()));
            require(start != null && start.isValid()
                            && start.getChunkPos().equals(hinted),
                    "Othman hint has no saved exact themed start for " + seed
                            + ": " + hinted);
            System.out.println("GROUPED_DISTRICT_OTHMAN_SEARCH mode=verified-hint"
                    + " eligible=1 rejected=0 selected=" + hinted);
            return start;
        }
        TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE,
                Identifier.fromNamespaceAndPath("gems", "atlas_target/othman_library"));
        BlockPos located = level.findNearestMapStructure(tag, new BlockPos(0, 80, 0),
                128, false);
        require(located != null, "No natural themed Othman start within fixed search radius");
        var state = level.getChunkSource().getGeneratorState();
        ChunkPos locatedChunk = new ChunkPos(located.getX() >> 4, located.getZ() >> 4);
        ChunkPos potential = spread.getPotentialStructureChunk(level.getSeed(),
                locatedChunk.x(), locatedChunk.z());
        for (ChunkPos chunk : locatedChunk.equals(potential)
                ? List.of(locatedChunk) : List.of(locatedChunk, potential)) {
            if (!spread.isStructureChunk(state, chunk.x(), chunk.z())) continue;
            StructureStart start = level.structureManager()
                    .getStartForStructure(structure, level.getChunk(chunk.x(), chunk.z()));
            if (start != null && start.isValid()
                    && start.getChunkPos().equals(chunk)) {
                System.out.println("GROUPED_DISTRICT_OTHMAN_SEARCH mode=themed-locate"
                        + " located=" + located + " selected=" + chunk);
                return start;
            }
        }
        throw new IllegalStateException("Themed Othman locate returned " + located
                + " but neither the locate chunk " + locatedChunk
                + " nor placement chunk " + potential + " has a saved exact start");
    }

    private static void assertEffectivePublicAliPack(ServerLevel level) {
        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE,
                Identifier.fromNamespaceAndPath("gems", "ali_splitstone_cloister"));
        Structure structure = structures.getValue(key);
        require(structure instanceof AliDryArrivalStructure,
                "Main-only Ali public ID did not use the accepted dry-arrival wrapper");
        JigsawStructure delegate = ((AliDryArrivalStructure) structure)
                .reservationDelegate();
        require(delegate.getStartPool().is(ResourceKey.create(Registries.TEMPLATE_POOL,
                        Identifier.fromNamespaceAndPath("gems",
                                "ali_splitstone_citadel_full_v6"))),
                "Main-only Ali start pool did not use the accepted v5 trio");
        StructureSet set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)
                .getValue(ResourceKey.create(Registries.STRUCTURE_SET,
                        Identifier.fromNamespaceAndPath("gems",
                                "ali_splitstone_cloister")));
        require(set != null && set.placement() instanceof RandomSpreadStructurePlacement
                        && set.structures().size() == 1
                        && set.structures().getFirst().structure().is(key)
                        && ((RandomSpreadStructurePlacement) set.placement()).spacing() == 64
                        && ((RandomSpreadStructurePlacement) set.placement()).separation() == 24,
                "Main-only Ali public placement lattice changed");
        TagKey<Biome> biomeTag = TagKey.create(Registries.BIOME,
                Identifier.fromNamespaceAndPath("gems",
                        "has_structure/ali_splitstone_cloister"));
        Set<String> biomes = new HashSet<>();
        for (var holder : level.registryAccess().lookupOrThrow(Registries.BIOME)
                .getTagOrEmpty(biomeTag))
            biomes.add(holder.unwrapKey().orElseThrow().identifier().toString());
        require(biomes.equals(Set.of("gems:splitstone_heights",
                        "minecraft:meadow", "minecraft:grove",
                        "minecraft:stony_peaks", "minecraft:snowy_slopes",
                        "minecraft:taiga", "minecraft:snowy_taiga")),
                "Main-only Ali biome set differs from the accepted trial: " + biomes);
        System.out.println("ALI_PUBLIC_EFFECTIVE_PACK_PASS id=gems:ali_splitstone_cloister"
                + " pool=gems:ali_splitstone_citadel_full_v6 biomes=7"
                + " spacing=64 separation=24");
    }

    /** Assert the public Hushwire/Othman registry without the trial overlay. */
    private static void assertEffectivePublicHushwireOthmanPack(ServerLevel level) {
        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var sets = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        for (Target target : TARGETS.subList(0, 2)) {
            ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE,
                    Identifier.fromNamespaceAndPath("gems", target.structure()));
            String setName = target.name().equals("hushwire")
                    ? "hushwire_v8" : "othman_library_themed";
            StructureSet set = sets.getValue(ResourceKey.create(Registries.STRUCTURE_SET,
                    Identifier.fromNamespaceAndPath("gems", setName)));
            require(set != null && set.structures().size() == 1
                            && set.structures().getFirst().structure().is(key),
                    "Public " + target.name() + " structure set is not the staged version");
            TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE,
                    Identifier.fromNamespaceAndPath("gems", "atlas_target/" + target.name()));
            int count = 0;
            Set<String> tagIds = new HashSet<>();
            for (var holder : structures.getTagOrEmpty(tag)) {
                count++;
                holder.unwrapKey().ifPresent(id -> tagIds.add(id.identifier().toString()));
            }
            Set<String> expectedTag = target.name().equals("hushwire")
                    ? Set.of("gems:hushwire_v10")
                    : Set.of("gems:othman_library_themed");
            require(count == expectedTag.size() && tagIds.equals(expectedTag),
                    "Public " + target.name() + " atlas tag must identify its current start");
            Structure structure = structures.getValue(key);
            JigsawStructure jigsaw = structure instanceof BoundedJigsawStart bounded
                    ? bounded.reservationDelegate() : structure instanceof JigsawStructure direct
                    ? direct : null;
            String poolName = target.name().equals("hushwire")
                    ? "hushwire_v10_trial" : "othman_library_archive_v11";
            require(jigsaw != null && jigsaw.getStartPool().is(ResourceKey.create(
                            Registries.TEMPLATE_POOL,
                            Identifier.fromNamespaceAndPath("gems", poolName))),
                    "Public " + target.name() + " start pool is not the staged version");
        }
        System.out.println("GROUPED_DISTRICT_PUBLIC_PACK_PASS targets=hushwire,othman_library");
    }

    /** Inspect the loaded registry, since a valid overlay file can still lose pack priority. */
    private static void assertEffectiveTrialPack(ServerLevel level) {
        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        ResourceKey<Structure> v10 = ResourceKey.create(Registries.STRUCTURE,
                Identifier.fromNamespaceAndPath("gems", "hushwire_v10"));
        StructureSet hushwireSet = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)
                .getValue(ResourceKey.create(Registries.STRUCTURE_SET,
                        Identifier.fromNamespaceAndPath("gems", "hushwire_v8")));
        require(hushwireSet != null && hushwireSet.structures().size() == 1
                        && hushwireSet.structures().getFirst().structure().is(v10),
                "Grouped trial overlay lost pack priority: effective Hushwire set is not v10");
        TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE,
                Identifier.fromNamespaceAndPath("gems", "atlas_target/hushwire"));
        int tagCount = 0;
        boolean tagIsV10 = false;
        for (var holder : structures.getTagOrEmpty(tag)) {
            tagCount++;
            tagIsV10 |= holder.is(v10);
        }
        require(tagCount == 1 && tagIsV10,
                "Grouped trial overlay lost pack priority: Hushwire atlas tag is not v10-only");
        TagKey<Structure> othmanTag = TagKey.create(Registries.STRUCTURE,
                Identifier.fromNamespaceAndPath("gems", "atlas_target/othman_library"));
        ResourceKey<Structure> themedOthman = ResourceKey.create(Registries.STRUCTURE,
                Identifier.fromNamespaceAndPath("gems", "othman_library_themed"));
        int othmanTagCount = 0;
        boolean othmanIsThemed = false;
        for (var holder : structures.getTagOrEmpty(othmanTag)) {
            othmanTagCount++;
            othmanIsThemed |= holder.is(themedOthman);
        }
        require(othmanTagCount == 1 && othmanIsThemed,
                "Grouped trial overlay lost pack priority: Othman atlas tag is not themed-only");
        ResourceKey<Structure> aliKey = ResourceKey.create(Registries.STRUCTURE,
                Identifier.fromNamespaceAndPath("gems", "ali_splitstone_cloister"));
        TagKey<Structure> aliTag = TagKey.create(Registries.STRUCTURE,
                Identifier.fromNamespaceAndPath("gems", "atlas_target/ali_splitstone_cloister"));
        int aliTagCount = 0;
        boolean aliTagMatches = false;
        for (var holder : structures.getTagOrEmpty(aliTag)) {
            aliTagCount++;
            aliTagMatches |= holder.is(aliKey);
        }
        require(aliTagCount == 1 && aliTagMatches,
                "Grouped trial Ali atlas tag must contain only the public structure ID");
        TagKey<Biome> aliBiomeTag = TagKey.create(Registries.BIOME,
                Identifier.fromNamespaceAndPath("gems", "has_structure/ali_splitstone_cloister"));
        Set<String> effectiveAliBiomes = new HashSet<>();
        for (var holder : level.registryAccess().lookupOrThrow(Registries.BIOME)
                .getTagOrEmpty(aliBiomeTag)) {
            effectiveAliBiomes.add(holder.unwrapKey().orElseThrow().identifier().toString());
        }
        require(effectiveAliBiomes.equals(Set.of("gems:splitstone_heights",
                        "minecraft:meadow", "minecraft:grove",
                        "minecraft:stony_peaks", "minecraft:snowy_slopes",
                        "minecraft:taiga", "minecraft:snowy_taiga")),
                "Grouped trial Ali highland biome tag lost pack priority: " + effectiveAliBiomes);
        for (Target target : TARGETS) {
            Structure structure = structures.getValue(ResourceKey.create(Registries.STRUCTURE,
                    Identifier.fromNamespaceAndPath("gems", target.structure())));
            require(structure != null, "Missing grouped trial structure: " + target.structure());
            JigsawStructure jigsaw = structure instanceof BoundedJigsawStart bounded
                    ? bounded.reservationDelegate() : structure instanceof JigsawStructure direct
                    ? direct : null;
            require(!target.name().equals("ali_splitstone_cloister")
                            || structure instanceof AliDryArrivalStructure,
                    "Grouped trial Ali structure is not the dry-arrival wrapper");
            require(jigsaw != null && jigsaw.getStartPool().is(ResourceKey.create(
                            Registries.TEMPLATE_POOL,
                            Identifier.fromNamespaceAndPath("gems", target.name().equals("hushwire")
                                    ? "hushwire_v10_trial" : target.name().equals("othman_library")
                                    ? "othman_library_archive_v11" : "ali_splitstone_citadel_full_v6"))),
                    "Grouped trial overlay lost pack priority: wrong start pool for " + target.name());
        }
        System.out.println("GROUPED_DISTRICT_EFFECTIVE_PACK_PASS "
                + "hushwireSet=v10 hushwireTag=v10-only aliTag=solo aliBiomes=5 startPools=3");
    }

    private static String imageName(String seed, Target target, String template, String time) {
        return "grouped-" + (SEED_A.equals(seed) ? "a" : "b") + "-"
                + target.name() + "-" + template + "-" + time;
    }

    private static String bounds(BoundingBox box) {
        return box.minX() + "," + box.minY() + "," + box.minZ() + ","
                + box.maxX() + "," + box.maxY() + "," + box.maxZ();
    }

    private static void auditWalk(TestSingleplayerContext world, Site site,
                                  NaturalDistrictRouteProbe.WalkApproach walk,
                                  Set<String> lightingIssues) {
        String issue = world.getServer().computeOnServer(server -> {
            ServerLevel level = server.overworld();
            int interior = 0;
            int dark = 0;
            int minLight = 15;
            List<BlockPos> darkFeet = new ArrayList<>();
            for (NaturalDistrictRouteProbe.Step node : walk.steps()) {
                BlockPos feet = node.feet();
                var state = level.getBlockState(feet);
                var head = level.getBlockState(feet.above());
                require(state.getCollisionShape(level, feet).isEmpty()
                                && head.getCollisionShape(level, feet.above()).isEmpty()
                                && state.getFluidState().isEmpty()
                                && head.getFluidState().isEmpty(),
                        "Route collision/fluid regression at " + feet);
                if (site.allBoxes().stream().noneMatch(box -> box.isInside(feet))
                        || level.canSeeSky(feet)) continue;
                interior++;
                int light = level.getBrightness(LightLayer.BLOCK, feet);
                minLight = Math.min(minLight, light);
                if (light == 0) {
                    dark++;
                    darkFeet.add(feet);
                }
            }
            // Return legs can run entirely outside or beneath open sky. They
            // still need clear footing, but have no interior light to sample.
            boolean interiorExpected = !walk.gate().name().startsWith("return-root-")
                    && !walk.gate().name().equals("public-return");
            System.out.println("GROUPED_DISTRICT_LIGHT target=" + site.target().name()
                    + " gate=" + walk.gate().name() + " interiorNodes=" + interior
                    + " minBlockLight=" + (interior == 0 ? "n/a" : minLight)
                    + " dark=" + dark + " darkFeet=" + darkFeet);
            if ((!interiorExpected || interior > 0) && dark == 0) return null;
            return site.target().name() + "/" + walk.gate().name()
                    + " interior=" + interior + " dark=" + dark
                    + " darkFeet=" + darkFeet;
        });
        if (issue != null) lightingIssues.add(issue);
    }

    private static void inspectControls(TestSingleplayerContext world, Site site,
                                        boolean restart) {
        List<Block> expected = switch (site.target().name()) {
            case "hushwire" -> List.of(DomainBlocks.HUSHWIRE_SLATE);
            case "othman_library" -> List.of(DomainBlocks.OTHMAN_PUBLIC_CATALOG,
                    DomainBlocks.OTHMAN_PRIVATE_NOTE, DomainBlocks.OTHMAN_PUBLISH_DESK);
            case "ali_splitstone_cloister" -> List.of(DomainBlocks.ALI_HIGH_MAP,
                    DomainBlocks.ALI_GATE_RUBBING, DomainBlocks.ALI_MAP_TABLE,
                    DomainBlocks.ALI_WEST_DOOR);
            default -> throw new IllegalStateException("Unknown grouped target");
        };
        world.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            Map<Block, BlockPos> positions = new HashMap<>();
            for (Block control : expected) {
                for (BoundingBox box : site.allBoxes()) {
                    for (int x = box.minX(); x <= box.maxX() && !positions.containsKey(control); x++) {
                        for (int y = box.minY(); y <= box.maxY() && !positions.containsKey(control); y++) {
                            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                                BlockPos pos = new BlockPos(x, y, z);
                                if (level.getBlockState(pos).is(control)) {
                                    positions.put(control, pos);
                                    break;
                                }
                            }
                        }
                    }
                }
                require(positions.containsKey(control), "Missing expected control block " + control
                        + " in " + site.target().name());
            }
            var player = world.getConnection().getServerPlayer();
            if (site.target().name().equals("othman_library")) {
                OthmanArchiveState state = OthmanArchiveState.get(server);
                if (restart) require(state.ready()
                        && state.published() == OthmanArchiveState.CATALOG,
                        "Othman's published lead did not persist");
                for (Block block : expected) use(level, player, positions.get(block));
                require(state.ready() && state.published() == OthmanArchiveState.CATALOG,
                        "Othman catalog, study, and publishing controls failed");
            } else if (site.target().name().equals("ali_splitstone_cloister")) {
                AliRouteState state = AliRouteState.get(server);
                if (restart) require(state.ready() && state.solved(),
                        "Ali's west route did not persist");
                for (Block block : expected) use(level, player, positions.get(block));
                require(state.ready() && state.solved(),
                        "Ali high map, rubbing, table, and west door failed");
            } else {
                BoundingBox hall = site.root();
                int bluePads = 0;
                for (int x = hall.minX(); x <= hall.maxX(); x++) {
                    for (int z = hall.minZ(); z <= hall.maxZ(); z++) {
                        for (int y = hall.minY(); y <= Math.min(hall.maxY(), hall.minY() + 10); y++) {
                            if (level.getBlockState(new BlockPos(x, y, z))
                                    .is(Blocks.CONCRETE.blue())) bluePads++;
                        }
                    }
                }
                require(bluePads >= 3, "Hushwire's three receiver pads are missing");
                System.out.println("GROUPED_DISTRICT_RECEIVERS bluePads=" + bluePads
                        + " bossPuzzle=dedicated-server-gate");
            }
            System.out.println("GROUPED_DISTRICT_CONTROLS target=" + site.target().name()
                    + " found=" + expected.size() + " state="
                    + (site.target().name().equals("hushwire")
                    ? "authored-pads" : "server-callback-persisted"));
        });
    }

    private static void use(ServerLevel level, net.minecraft.server.level.ServerPlayer player,
                            BlockPos pos) {
        InteractionResult result = UseBlockCallback.EVENT.invoker().interact(
                player, level, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        require(result == InteractionResult.SUCCESS_SERVER,
                "Control did not accept server use at " + pos + ": " + result);
    }

    private static Map<String, ChunkPos> expectedChunks() {
        String value = System.getProperty("gems.groupedDistrictExpectedChunks", "");
        Map<String, ChunkPos> chunks = new HashMap<>();
        for (String part : value.split(";")) {
            String[] bits = part.split(":");
            require(bits.length == 2, "Bad restart start-chunk manifest");
            String[] coordinates = bits[1].split(",");
            require(coordinates.length == 2, "Bad restart chunk coordinates");
            chunks.put(bits[0], new ChunkPos(
                    Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1])));
        }
        require(chunks.keySet().equals(activeTargets().stream()
                .map(Target::name).collect(java.util.stream.Collectors.toSet())),
                "Restart needs the exact selected saved start chunks");
        return chunks;
    }

    private static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
