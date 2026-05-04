package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.boss.PaintedVerdictEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.phys.AABB;

/** Version-aware opt-in loaded-world gate for trial and stable Framefall starts. */
@SuppressWarnings("UnstableApiUsage")
public final class FramefallV5NaturalClientGameTest implements FabricClientGameTest {
    private static final String VERSION =
            System.getProperty("gems.framefallTrialVersion", "v5");
    private static final boolean PRODUCTION = Boolean.getBoolean("gems.framefallProductionNaturalGate");
    private static final ResourceKey<Structure> SITE = ResourceKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath("gems", PRODUCTION ? "framefall_gallery"
                    : "framefall_gallery_" + VERSION + "_trial"));
    private static final TagKey<Structure> TARGET = TagKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath("gems", PRODUCTION
                    ? "atlas_target/framefall_gallery" : "framefall_" + VERSION + "_trial"));
    private static final Set<String> EXPECTED = Set.of(
            "framefall_gallery_" + VERSION, "framefall_atelier_" + VERSION,
            "framefall_promenade_" + VERSION, "framefall_garden_" + VERSION,
            "framefall_observatory_" + VERSION);
    private static final int[][] CARDINAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int MAX_VISITED = 90_000;

    private record Piece(PoolElementStructurePiece saved, String name) {
        BoundingBox box() { return saved.getBoundingBox(); }

        BlockPos at(int localX, int localY, int localZ) {
            BoundingBox box = box();
            return switch (saved.getRotation()) {
                case NONE -> new BlockPos(box.minX() + localX, box.minY() + localY,
                        box.minZ() + localZ);
                case CLOCKWISE_90 -> new BlockPos(box.maxX() - localZ, box.minY() + localY,
                        box.minZ() + localX);
                case CLOCKWISE_180 -> new BlockPos(box.maxX() - localX, box.minY() + localY,
                        box.maxZ() - localZ);
                case COUNTERCLOCKWISE_90 -> new BlockPos(box.minX() + localZ,
                        box.minY() + localY, box.maxZ() - localX);
            };
        }
    }

    private record Site(StructureStart start, Map<String, Piece> pieces,
                        BlockPos arrival, Map<String, List<BlockPos>> routes) { }

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!Boolean.getBoolean("gems.framefallV5NaturalGate")
                && !Boolean.getBoolean("gems.framefallV6NaturalGate") && !PRODUCTION) return;
        require(VERSION.equals("v5") || VERSION.equals("v6"),
                "Unsupported Framefall trial version " + VERSION);
        require(!PRODUCTION || VERSION.equals("v6"),
                "Stable Framefall gate requires the v6 campus");
        String seed = System.getProperty("gems.framefall" + VERSION.toUpperCase()
                + "Seed", "Framefall-" + VERSION.toUpperCase() + "-Natural-A");
        boolean restart = Boolean.getBoolean("gems.framefallProductionRestart");
        require(!restart || PRODUCTION, "Framefall saved restart needs the stable ID gate");
        if (restart) {
            Path save = context.computeOnClient(client -> client.gameDirectory.toPath()
                    .resolve("saves").resolve("New World"));
            require(Files.isRegularFile(save.resolve("level.dat")),
                    "Framefall restart needs a copied closed save: " + save);
            try (TestSingleplayerContext world = new TestWorldSaveImpl(context, save).open()) {
                inspectAndCapture(context, world, seed, true);
            }
            return;
        }
        try (TestSingleplayerContext world = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(settings -> {
                    settings.setSeed(seed);
                    settings.setGenerateStructures(true);
                }).create()) {
            inspectAndCapture(context, world, seed, false);
        }
    }

    private static void inspectAndCapture(ClientGameTestContext context,
                                          TestSingleplayerContext world,
                                          String seed, boolean restart) {
        world.getConnection().waitForChunksRender();
        Site site = world.getServer().computeOnServer(server ->
                inspect(server.overworld(), seed));
        BoundingBox box = site.start().getBoundingBox();
        String startChunk = site.start().getChunkPos().x() + ","
                + site.start().getChunkPos().z();
        String bounds = box.minX() + "," + box.minY() + "," + box.minZ()
                + "," + box.maxX() + "," + box.maxY() + "," + box.maxZ();
        if (restart) {
            require(startChunk.equals(System.getProperty("gems.framefallV6ExpectedStart", ""))
                            && bounds.equals(System.getProperty("gems.framefallV6ExpectedBounds", "")),
                    "Framefall stable start or bounds changed across restart: "
                            + startChunk + " " + bounds);
        }
        Piece promenade = site.pieces().get("framefall_promenade_" + VERSION);
        capture(context, world, site.arrival(), yaw(promenade, Direction.SOUTH),
                "promenade-arrival");
        if (VERSION.equals("v6")) {
            capture(context, world, promenade.at(7, 7, 24),
                    yaw(promenade, Direction.WEST), "observatory-view-bay");
            Piece gallery = site.pieces().get("framefall_gallery_v6");
            capture(context, world, gallery.at(23, 5, 55),
                    yaw(gallery, Direction.NORTH), "gallery-facade-pad");
            Piece atelier = site.pieces().get("framefall_atelier_v6");
            capture(context, world, atelier.at(38, 5, 41),
                    yaw(atelier, Direction.NORTH), "atelier-kiln-pad");
            Piece garden = site.pieces().get("framefall_garden_v6");
            capture(context, world, garden.at(9, 5, 35),
                    yaw(garden, Direction.NORTH), "garden-arcade-pad");
        }
        String phase = !PRODUCTION ? "TRIAL_LOADED" : restart
                ? "PRODUCTION_RESTART" : "PRODUCTION_LOADED";
        System.out.println("FRAMEFALL_" + VERSION.toUpperCase() + "_" + phase + "_PASS seed="
                + seed + " startChunk=" + startChunk + " bounds=" + bounds
                + " pieces=" + site.pieces().keySet()
                + " routeSteps=" + site.routes().entrySet().stream()
                        .map(entry -> entry.getKey() + ":" + entry.getValue().size())
                        .toList());
    }

    private static float yaw(Piece piece, Direction localFacing) {
        return switch (piece.saved().getRotation().rotate(localFacing)) {
            case SOUTH -> 0.0F;
            case WEST -> 90.0F;
            case NORTH -> 180.0F;
            case EAST -> -90.0F;
            default -> throw new IllegalArgumentException("View must face horizontally");
        };
    }

    private static void capture(ClientGameTestContext context,
                                TestSingleplayerContext world, BlockPos feet,
                                float yaw, String view) {
        world.getServer().runOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            player.setNoGravity(false);
            player.teleportTo(server.overworld(), feet.getX() + 0.5,
                    feet.getY(), feet.getZ() + 0.5,
                    EnumSet.noneOf(Relative.class), yaw, 5.0F, false);
        });
        world.getConnection().waitForChunksRender();
        context.waitTicks(40);
        context.takeScreenshot("framefall-" + VERSION + "-trial-" + view);
    }

    private static Site inspect(ServerLevel level, String seed) {
        BlockPos located = level.findNearestMapStructure(TARGET,
                new BlockPos(0, 80, 0), 128, false);
        require(located != null, "No " + VERSION + " trial start within 128 chunks on " + seed);
        Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValue(SITE);
        require(structure != null, "Framefall " + VERSION
                + (PRODUCTION ? " stable structure" : " trial structure")
                + " is missing from registry");
        StructureStart start = null;
        int chunkX = located.getX() >> 4;
        int chunkZ = located.getZ() >> 4;
        for (int radius = 0; radius <= 5 && start == null; radius++) {
            for (int dx = -radius; dx <= radius && start == null; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    var chunk = level.getChunk(chunkX + dx, chunkZ + dz);
                    StructureStart candidate = level.structureManager()
                            .getStartForStructure(structure, chunk);
                    if (candidate != null && candidate.isValid()) {
                        start = candidate;
                        break;
                    }
                }
            }
        }
        require(start != null, "Located " + VERSION + " trial has no saved structure start");
        Map<String, Piece> pieces = new HashMap<>();
        for (var raw : start.getPieces()) {
            require(raw instanceof PoolElementStructurePiece,
                    VERSION + " trial contains a non-pool piece");
            PoolElementStructurePiece pooled = (PoolElementStructurePiece) raw;
            require(pooled.getElement() instanceof SinglePoolElement,
                    VERSION + " trial contains a non-template pool element");
            String name = ((SinglePoolElement) pooled.getElement())
                    .getTemplateLocation().getPath();
            require(EXPECTED.contains(name), "Unexpected " + VERSION + " trial piece " + name);
            require(pieces.putIfAbsent(name, new Piece(pooled, name)) == null,
                    "Duplicate " + VERSION + " trial piece " + name);
        }
        require(pieces.keySet().equals(EXPECTED),
                "Incomplete " + VERSION + " campus: " + pieces.keySet());
        for (Piece piece : pieces.values()) {
            BoundingBox box = piece.box();
            for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
                for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                    level.getChunk(cx, cz);
                }
            }
        }
        BoundingBox campus = start.getBoundingBox();
        require(level.getEntitiesOfClass(PaintedVerdictEntity.class,
                new AABB(campus.minX(), campus.minY(), campus.minZ(),
                        campus.maxX() + 1, campus.maxY() + 1, campus.maxZ() + 1)).size() == 1,
                "Loaded " + VERSION + " campus has no unique Painted Verdict in its saved bounds");
        BlockPos arrival = pieces.get("framefall_promenade_" + VERSION).at(15, 11, 0);
        Map<String, BlockPos> goals = Map.of(
                "boss-court", pieces.get("framefall_gallery_" + VERSION).at(23, 5, 23),
                "garden-lens", pieces.get("framefall_garden_" + VERSION).at(9, 5, 37),
                "observatory-hall", pieces.get("framefall_observatory_" + VERSION).at(24, 5, 8));
        if (VERSION.equals("v6")) {
            goals = new HashMap<>(goals);
            goals.put("gallery-facade-pad", pieces.get("framefall_gallery_v6").at(23, 5, 55));
            goals.put("atelier-kiln-pad", pieces.get("framefall_atelier_v6").at(38, 5, 41));
            goals.put("garden-arcade-pad", pieces.get("framefall_garden_v6").at(9, 5, 35));
            goals.put("observatory-view-bay", pieces.get("framefall_promenade_v6").at(7, 7, 24));
        }
        Map<String, List<BlockPos>> routes = new HashMap<>();
        for (var goal : goals.entrySet()) {
            List<BlockPos> route = route(level, pieces.values(), arrival, goal.getValue());
            require(!route.isEmpty(), "Loaded " + VERSION + " route blocked at " + goal.getKey()
                    + " from " + arrival + " to " + goal.getValue());
            routes.put(goal.getKey(), route);
        }
        return new Site(start, Map.copyOf(pieces), arrival, Map.copyOf(routes));
    }

    private static List<BlockPos> route(ServerLevel level, java.util.Collection<Piece> pieces,
                                        BlockPos start, BlockPos goal) {
        require(walkable(level, pieces, start), "Unsafe v5 arrival " + start);
        require(walkable(level, pieces, goal), "Unsafe v5 goal " + goal);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Map<BlockPos, BlockPos> previous = new HashMap<>();
        queue.add(start);
        previous.put(start, start);
        while (!queue.isEmpty() && previous.size() < MAX_VISITED) {
            BlockPos current = queue.removeFirst();
            if (current.equals(goal)) break;
            for (int[] delta : CARDINAL) {
                for (int rise = -1; rise <= 1; rise++) {
                    BlockPos next = current.offset(delta[0], rise, delta[1]);
                    if (previous.containsKey(next) || !walkable(level, pieces, next)) continue;
                    previous.put(next, current);
                    queue.addLast(next);
                }
            }
        }
        if (!previous.containsKey(goal)) return List.of();
        ArrayList<BlockPos> reverse = new ArrayList<>();
        for (BlockPos at = goal; !at.equals(start); at = previous.get(at)) reverse.add(at);
        reverse.add(start);
        return reverse.reversed();
    }

    private static boolean walkable(ServerLevel level,
                                    java.util.Collection<Piece> pieces, BlockPos feet) {
        if (!level.getWorldBorder().isWithinBounds(feet)
                || level.getChunkSource().getChunkNow(feet.getX() >> 4,
                        feet.getZ() >> 4) == null) return false;
        boolean inPiece = false;
        for (Piece piece : pieces) {
            BoundingBox box = piece.box();
            if (feet.getX() >= box.minX() && feet.getX() <= box.maxX()
                    && feet.getZ() >= box.minZ() && feet.getZ() <= box.maxZ()
                    && feet.getY() >= box.minY() + 1
                    && feet.getY() <= box.maxY() - 2) {
                inPiece = true;
                break;
            }
        }
        if (!inPiece) return false;
        BlockPos floor = feet.below();
        var support = level.getBlockState(floor);
        if (support.is(BlockTags.LEAVES) || support.is(BlockTags.LOGS)
                || support.getCollisionShape(level, floor).isEmpty()
                || !support.getFluidState().isEmpty()) return false;
        for (int rise = 0; rise <= 1; rise++) {
            BlockPos at = feet.above(rise);
            var state = level.getBlockState(at);
            if (!state.getCollisionShape(level, at).isEmpty()
                    || !state.getFluidState().isEmpty()) return false;
        }
        return true;
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalStateException(message);
    }
}
