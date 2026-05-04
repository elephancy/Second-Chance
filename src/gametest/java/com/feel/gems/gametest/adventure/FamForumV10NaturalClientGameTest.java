package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.DomainBlocks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.impl.client.gametest.world.TestWorldSaveImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Fresh-world, test-only v10 Fam campus gate; public worldgen continues to select v8. */
@SuppressWarnings("UnstableApiUsage")
public final class FamForumV10NaturalClientGameTest implements FabricClientGameTest {
    private static final String ROOT = "fam_forum_arcaded_court_v10";
    private static final String STORY = "fam_story_hall_reading_garden_v10";
    private static final String SCHOOL = "fam_open_school_workshop_v10";
    private static final String REFERENCE = "fam_east_reference_v1";
    private static final String ARRIVAL_REFERENCE = "fam_arrival_reference_v10";
    private static final Set<String> EXPECTED = Set.of(ROOT, STORY, SCHOOL);
    private static final ResourceKey<Structure> TRIAL = ResourceKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath("gems", "fam_forum_v10_trial"));
    private static final ResourceKey<Structure> PUBLIC = ResourceKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath("gems", "fam_forum"));
    private static final TagKey<Structure> TRIAL_TARGET = TagKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath("gems", "fam_v10_trial"));
    private static final TagKey<Structure> PUBLIC_TARGET = TagKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath("gems", "atlas_target/fam_forum"));
    private static final int[][] CARDINAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private record Piece(PoolElementStructurePiece saved, String name) {
        BoundingBox box() { return saved.getBoundingBox(); }

        BlockPos at(int x, int y, int z) {
            BoundingBox box = box();
            return switch (saved.getRotation()) {
                case NONE -> new BlockPos(box.minX() + x, box.minY() + y, box.minZ() + z);
                case CLOCKWISE_90 -> new BlockPos(box.maxX() - z, box.minY() + y,
                        box.minZ() + x);
                case CLOCKWISE_180 -> new BlockPos(box.maxX() - x, box.minY() + y,
                        box.maxZ() - z);
                case COUNTERCLOCKWISE_90 -> new BlockPos(box.minX() + z,
                        box.minY() + y, box.maxZ() - x);
            };
        }
    }

    private record Camera(BlockPos feet, Vec3 target, int clearRays,
                          int foregroundTreeCells) {}
    private record Prepared(StructureStart start, Map<String, Piece> pieces) {}
    private record Site(BlockPos rootStart, Map<String, Piece> pieces,
                        Map<String, Integer> routeSteps, Map<String, Camera> cameras) {}

    @Override
    public void runTest(ClientGameTestContext context) {
        boolean publicGate = Boolean.getBoolean("gems.famForumV10PublicGate");
        if (!publicGate && !Boolean.getBoolean("gems.famForumV10NaturalGate")) return;
        String seed = System.getProperty("gems.famV10Seed", "Fam-V10-Natural-A");
        String recapture = System.getProperty("gems.famV10RecaptureChunk", "");
        if (!recapture.isEmpty()) {
            Path saved = context.computeOnClient(client -> client.gameDirectory.toPath()
                    .resolve("saves").resolve("New World"));
            require(Files.isRegularFile(saved.resolve("level.dat")),
                    "Fam v10 recapture requires a copied, closed save at " + saved);
            try (TestSingleplayerContext world = new TestWorldSaveImpl(context, saved).open()) {
                world.getConnection().waitForChunksRender();
                Prepared prepared = world.getServer().computeOnServer(server -> prepare(
                        server.overworld(), world.getConnection().getServerPlayer(),
                        seed, recapture, publicGate));
                context.waitTicks(40);
                Site site = world.getServer().computeOnServer(server -> inspect(
                        server.overworld(), world.getConnection().getServerPlayer(), prepared));
                for (String name : List.of(ROOT, STORY, SCHOOL)) {
                    capture(context, world, site.cameras().get(name),
                            "fam-v10-recapture-" + name);
                }
                System.out.println((publicGate ? "FAM_V10_PUBLIC_RECAPTURE_PASS seed="
                        : "FAM_V10_RECAPTURE_PASS seed=") + seed
                        + " start=" + site.rootStart() + " pieces="
                        + site.pieces().keySet() + " routeSteps=" + site.routeSteps()
                        + " cameras=" + site.cameras().entrySet().stream()
                                .map(entry -> entry.getKey() + ":"
                                        + entry.getValue().feet() + "/"
                                        + entry.getValue().clearRays()
                                        + "/foreground="
                                        + entry.getValue().foregroundTreeCells()).toList());
            }
            return;
        }
        TestWorldSave save;
        Site site;
        try (TestSingleplayerContext world = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(settings -> {
                    settings.setSeed(seed);
                    settings.setGenerateStructures(true);
                }).create()) {
            world.getConnection().waitForChunksRender();
            Prepared prepared = world.getServer().computeOnServer(server -> prepare(
                    server.overworld(), world.getConnection().getServerPlayer(), seed, "",
                    publicGate));
            // The last neighbor's load can finish after its CHUNK_LOAD callback.
            // Let the production first-generation finish queue commit, then
            // assert the same strict player walk and facade view.
            context.waitTicks(40);
            site = world.getServer().computeOnServer(server -> inspect(
                    server.overworld(), world.getConnection().getServerPlayer(), prepared));
            for (String name : List.of(ROOT, STORY, SCHOOL)) {
                Camera camera = site.cameras().get(name);
                capture(context, world, camera, "fam-v10-natural-" + name);
            }
            System.out.println((publicGate ? "FAM_V10_PUBLIC_PASS seed="
                    : "FAM_V10_NATURAL_PASS seed=") + seed + " start="
                    + site.rootStart() + " pieces=" + site.pieces().keySet()
                    + " routeSteps=" + site.routeSteps() + " cameras="
                    + site.cameras().entrySet().stream()
                            .map(entry -> entry.getKey() + ":" + entry.getValue().feet()
                                    + "/" + entry.getValue().clearRays()
                                    + "/foreground="
                                    + entry.getValue().foregroundTreeCells())
                            .toList());
            save = world.getWorldSave();
        }
        try (TestSingleplayerContext reopened = save.open()) {
            reopened.getConnection().waitForChunksRender();
            reopened.getServer().runOnServer(server -> {
                ServerLevel level = server.overworld();
                BlockPos at = site.rootStart();
                Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                        .getValue(publicGate ? PUBLIC : TRIAL);
                require(structure != null, "Fam v10 trial structure disappeared on reopen");
                var chunk = level.getChunk(at.getX() >> 4, at.getZ() >> 4);
                StructureStart start = level.structureManager()
                        .getStartForStructure(structure, chunk);
                require(start != null && start.isValid(),
                        "Saved Fam v10 trial start disappeared on reopen");
                Map<String, Piece> pieces = pieces(start);
                require(pieces.keySet().equals(EXPECTED),
                        "Saved Fam v10 pieces changed on reopen: " + pieces.keySet());
                load(level, pieces.values());
                checkControls(level, pieces);
                System.out.println((publicGate ? "FAM_V10_PUBLIC_REOPEN_PASS seed="
                        : "FAM_V10_REOPEN_PASS seed=") + seed
                        + " start=" + start.getChunkPos() + " pieces=" + pieces.keySet());
            });
        }
    }

    private static Prepared prepare(ServerLevel level, ServerPlayer player, String seed,
                                    String recaptureChunk, boolean publicGate) {
        int rings = Integer.getInteger("gems.famV10SearchChunks", 256);
        require(rings >= 1 && rings <= 512, "Fam v10 search must be 1..512 chunks");
        BlockPos located;
        if (recaptureChunk.isEmpty()) {
            located = level.findNearestMapStructure(publicGate ? PUBLIC_TARGET : TRIAL_TARGET,
                    new BlockPos(0, 80, 0), rings, false);
        } else {
            String[] parts = recaptureChunk.split(",", -1);
            require(parts.length == 2, "Fam v10 recapture start must be chunkX,chunkZ");
            int cx = Integer.parseInt(parts[0]);
            int cz = Integer.parseInt(parts[1]);
            located = new BlockPos(cx * 16, 0, cz * 16);
        }
        require(located != null, "No natural v10 Fam start within " + rings
                + " chunks on " + seed);
        Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValue(publicGate ? PUBLIC : TRIAL);
        require(structure != null, "Fam v10 target structure is missing");
        StructureStart start = null;
        int maxRadius = recaptureChunk.isEmpty() ? 5 : 0;
        for (int radius = 0; radius <= maxRadius && start == null; radius++) {
            for (int dx = -radius; dx <= radius && start == null; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    var chunk = level.getChunk((located.getX() >> 4) + dx,
                            (located.getZ() >> 4) + dz);
                    StructureStart candidate = level.structureManager()
                            .getStartForStructure(structure, chunk);
                    if (candidate != null && candidate.isValid()) {
                        start = candidate;
                        break;
                    }
                }
            }
        }
        require(start != null, "Located Fam v10 trial has no saved start");
        Map<String, Piece> pieces = pieces(start);
        require(pieces.keySet().equals(EXPECTED),
                "Incomplete v10 Fam campus: " + pieces.keySet());
        load(level, pieces.values());
        loadArrivalRing(level, pieces);
        // Natural discovery searched far from the spawn player. Keep this
        // campus loaded by the same player proximity that production uses,
        // so the guarded post-load finish can run during the event window.
        BlockPos arrival = pieces.get(ROOT).at(23, 5, 42);
        player.teleportTo(level, arrival.getX() + 0.5, arrival.getY(),
                arrival.getZ() + 0.5, EnumSet.noneOf(Relative.class), 0, 0, false);
        return new Prepared(start, Map.copyOf(pieces));
    }

    private static Site inspect(ServerLevel level, ServerPlayer player,
                                Prepared prepared) {
        StructureStart start = prepared.start();
        Map<String, Piece> pieces = prepared.pieces();
        // The production finish gets a short event window above. Reacquire
        // these distant QA chunks before checking loaded-only route walks;
        // getChunkNow is allowed to drop them while the test player is away.
        load(level, pieces.values());
        loadArrivalRing(level, pieces);
        checkControls(level, pieces);
        Piece root = pieces.get(ROOT);
        BlockPos arrival = root.at(23, 5, 42);
        Map<String, Integer> routes = new HashMap<>();
        for (String seam : List.of("primary", "secondary")) {
            Set<BlockPos> blocked = blockedSeam(root, seam.equals("primary")
                    ? 32 : 21, seam.equals("primary") ? 35 : 25);
            Map<BlockPos, BlockPos> reached = traverse(level, pieces.values(), arrival, blocked);
            routes.put(seam + "-story", stepsToControl(reached,
                    pieces.get(STORY).at(10, 6, 18), STORY));
            routes.put(seam + "-school", stepsToControl(reached,
                    pieces.get(SCHOOL).at(28, 6, 18), SCHOOL));
            routes.put(seam + "-board", stepsToControl(reached,
                    root.at(23, 6, 25), ROOT));
            routes.put(seam + "-north-grove", stepsToControl(reached,
                    root.at(23, 6, 5), ROOT));
        }
        Map<String, Camera> cameras = new HashMap<>();
        for (String name : List.of(ROOT, STORY, SCHOOL)) {
            Piece piece = pieces.get(name);
            checkExteriorWalk(level, piece, name.equals(ROOT) ? 47 : 41);
            cameras.put(name, exteriorCamera(level, player, pieces.values(), piece,
                    name.equals(ROOT) ? 47 : 41));
        }
        BlockPos rootStart = new BlockPos(start.getChunkPos().x() * 16, 0,
                start.getChunkPos().z() * 16);
        return new Site(rootStart, Map.copyOf(pieces), Map.copyOf(routes),
                Map.copyOf(cameras));
    }

    private static Map<String, Piece> pieces(StructureStart start) {
        Map<String, Piece> pieces = new HashMap<>();
        for (var raw : start.getPieces()) {
            require(raw instanceof PoolElementStructurePiece,
                    "Fam v10 trial has a non-pool piece");
            PoolElementStructurePiece pooled = (PoolElementStructurePiece) raw;
            require(pooled.getElement() instanceof SinglePoolElement,
                    "Fam v10 trial has a non-template piece");
            String name = ((SinglePoolElement) pooled.getElement())
                    .getTemplateLocation().getPath();
            require(EXPECTED.contains(name) || REFERENCE.equals(name)
                    || ARRIVAL_REFERENCE.equals(name),
                    "Unexpected Fam v10 trial piece: " + name);
            if (EXPECTED.contains(name)) {
                require(pieces.putIfAbsent(name, new Piece(pooled, name)) == null,
                        "Duplicate Fam v10 piece: " + name);
            }
        }
        return pieces;
    }

    private static void load(ServerLevel level, java.util.Collection<Piece> pieces) {
        for (Piece piece : pieces) {
            BoundingBox box = piece.box();
            for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
                for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                    level.getChunk(cx, cz);
                }
            }
        }
    }

    private static void loadArrivalRing(ServerLevel level, Map<String, Piece> pieces) {
        for (String name : List.of(ROOT, STORY, SCHOOL)) {
            Piece piece = pieces.get(name);
            int width = name.equals(ROOT) ? 47 : 41;
            Direction outward = piece.saved().getRotation().rotate(Direction.SOUTH);
            Direction lateral = piece.saved().getRotation().rotate(Direction.EAST);
            BlockPos facade = piece.at(width / 2, 8, width - 1);
            BlockPos a = facade.relative(outward, 1).relative(lateral, -4);
            BlockPos b = facade.relative(outward, 24).relative(lateral, 4);
            for (int cx = (Math.min(a.getX(), b.getX()) >> 4) - 1;
                    cx <= (Math.max(a.getX(), b.getX()) >> 4) + 1; cx++) {
                for (int cz = (Math.min(a.getZ(), b.getZ()) >> 4) - 1;
                        cz <= (Math.max(a.getZ(), b.getZ()) >> 4) + 1; cz++) {
                    level.getChunk(cx, cz);
                }
            }
        }
    }

    private static void checkControls(ServerLevel level, Map<String, Piece> pieces) {
        Piece root = pieces.get(ROOT), story = pieces.get(STORY), school = pieces.get(SCHOOL);
        check(level, root.at(23, 6, 25), DomainBlocks.FAM_FORUM_BOARD);
        check(level, root.at(23, 6, 19), DomainBlocks.FAM_CIRCLE_START);
        check(level, story.at(10, 6, 18), DomainBlocks.FAM_STORY_CLUE);
        check(level, story.at(12, 6, 18), DomainBlocks.FAM_DISPATCH_STORY);
        check(level, story.at(30, 5, 30), DomainBlocks.FAM_CIRCLE_STORY);
        check(level, school.at(28, 6, 18), DomainBlocks.FAM_SCHOOL_CLUE);
        check(level, school.at(28, 6, 20), DomainBlocks.FAM_DISPATCH_SCHOOL);
        check(level, school.at(28, 6, 27), DomainBlocks.FAM_CIRCLE_SCHOOL);
    }

    private static void check(ServerLevel level, BlockPos at, Block expected) {
        require(level.getBlockState(at).is(expected),
                "Fam v10 control missing at " + at + ": expected " + expected
                        + ", found " + level.getBlockState(at));
    }

    private static Set<BlockPos> blockedSeam(Piece root, int lowZ, int highZ) {
        Set<BlockPos> blocked = new HashSet<>();
        for (int x : new int[] {0, 46}) {
            for (int z = lowZ; z <= highZ; z++) {
                for (int y = 4; y <= 8; y++) blocked.add(root.at(x, y, z));
            }
        }
        return blocked;
    }

    private static Map<BlockPos, BlockPos> traverse(ServerLevel level,
            java.util.Collection<Piece> pieces, BlockPos arrival, Set<BlockPos> blocked) {
        require(walkable(level, pieces, arrival), "Fam v10 arrival unsafe: " + arrival);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Map<BlockPos, BlockPos> previous = new HashMap<>();
        queue.add(arrival);
        previous.put(arrival, arrival);
        while (!queue.isEmpty() && previous.size() < 40_000) {
            BlockPos current = queue.removeFirst();
            for (int[] delta : CARDINAL) {
                for (int rise = -1; rise <= 1; rise++) {
                    BlockPos next = current.offset(delta[0], rise, delta[1]);
                    if (blocked.contains(next) || previous.containsKey(next)
                            || !walkable(level, pieces, next)) continue;
                    previous.put(next, current);
                    queue.addLast(next);
                }
            }
        }
        return previous;
    }

    private static int stepsToControl(Map<BlockPos, BlockPos> previous,
                                      BlockPos control, String name) {
        int shortest = Integer.MAX_VALUE;
        for (int[] delta : CARDINAL) {
            for (int rise = -1; rise <= 1; rise++) {
                BlockPos next = control.offset(delta[0], rise, delta[1]);
                if (!previous.containsKey(next)) continue;
                int steps = 0;
                for (BlockPos at = next; !previous.get(at).equals(at);
                        at = previous.get(at)) steps++;
                shortest = Math.min(shortest, steps);
            }
        }
        require(shortest != Integer.MAX_VALUE,
                "No loaded v10 arrival/arcade route to " + name + " control " + control);
        return shortest;
    }

    private static boolean walkable(ServerLevel level,
            java.util.Collection<Piece> pieces, BlockPos feet) {
        if (level.getChunkSource().getChunkNow(feet.getX() >> 4,
                feet.getZ() >> 4) == null) return false;
        boolean inside = false;
        for (Piece piece : pieces) {
            BoundingBox box = piece.box();
            if (feet.getX() >= box.minX() && feet.getX() <= box.maxX()
                    && feet.getZ() >= box.minZ() && feet.getZ() <= box.maxZ()
                    && feet.getY() >= box.minY() + 4 && feet.getY() <= box.minY() + 8) {
                inside = true;
                break;
            }
        }
        if (!inside) return false;
        BlockPos below = feet.below();
        var support = level.getBlockState(below);
        if (support.is(BlockTags.LEAVES) || support.is(BlockTags.LOGS)
                || support.getCollisionShape(level, below).isEmpty()
                || !support.getFluidState().isEmpty()) return false;
        for (int rise = 0; rise <= 1; rise++) {
            BlockPos at = feet.above(rise);
            var state = level.getBlockState(at);
            if (!state.getCollisionShape(level, at).isEmpty()
                    || !state.getFluidState().isEmpty()) return false;
        }
        return true;
    }

    private static void checkExteriorWalk(ServerLevel level, Piece piece, int width) {
        Direction facing = piece.saved().getRotation().rotate(Direction.SOUTH);
        BlockPos facade = piece.at(width / 2, 8, width - 1);
        int landingFeetY = piece.box().minY() + 5;
        for (int distance = 12; distance >= 1; distance--) {
            BlockPos column = facade.relative(facing, distance);
            BlockPos feet = new BlockPos(column.getX(), landingFeetY, column.getZ());
            level.getChunk(feet.getX() >> 4, feet.getZ() >> 4);
            require(level.getBlockState(feet.below()).is(Blocks.SMOOTH_RED_SANDSTONE)
                            && safeGround(level, feet) && !nearTree(level, feet),
                    "Broken top-landing walk to " + piece.name() + " at " + feet);
        }
    }

    private static Camera exteriorCamera(ServerLevel level, ServerPlayer player,
            java.util.Collection<Piece> all, Piece piece, int width) {
        Direction facing = piece.saved().getRotation().rotate(Direction.SOUTH);
        Direction lateral = piece.saved().getRotation().rotate(Direction.EAST);
        BlockPos facadeCenter = piece.at(width / 2, 8, width - 1);
        Camera best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int distance : new int[] {12, 8}) {
            for (int side : new int[] {0, -2, 2}) {
                BlockPos column = facadeCenter.relative(facing, distance)
                        .relative(lateral, side);
                level.getChunk(column.getX() >> 4, column.getZ() >> 4);
                BlockPos feet = new BlockPos(column.getX(), piece.box().minY() + 5,
                        column.getZ());
                if (!level.getBlockState(feet.below()).is(Blocks.SMOOTH_RED_SANDSTONE)
                        || !outsideAll(feet, all) || !safeGround(level, feet)
                        || nearTree(level, feet)) continue;
                int clear = clearFacadeRays(level, player, piece, feet, width);
                if (clear < 2) continue;
                int foreground = foregroundTreeCells(level, feet, facing.getOpposite(),
                        lateral);
                if (foreground > 16) continue;
                int score = clear * 100 - Math.abs(distance - 12)
                        - Math.abs(side) - foreground * 4;
                if (score > bestScore) {
                    Vec3 target = Vec3.atCenterOf(facadeCenter);
                    best = new Camera(feet, target, clear, foreground);
                    bestScore = score;
                }
            }
        }
        require(best != null, "No clear v10 top-landing player view for " + piece.name());
        return best;
    }

    private static int foregroundTreeCells(ServerLevel level, BlockPos feet,
                                           Direction forward, Direction lateral) {
        int count = 0;
        for (int depth = 0; depth <= 10; depth++) {
            for (int side = -5; side <= 5; side++) {
                BlockPos column = feet.relative(forward, depth)
                        .relative(lateral, side);
                for (int rise = 1; rise <= 12; rise++) {
                    var state = level.getBlockState(column.above(rise));
                    if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) count++;
                }
            }
        }
        return count;
    }

    private static boolean outsideAll(BlockPos feet, java.util.Collection<Piece> pieces) {
        for (Piece piece : pieces) {
            BoundingBox box = piece.box();
            if (feet.getX() >= box.minX() - 2 && feet.getX() <= box.maxX() + 2
                    && feet.getZ() >= box.minZ() - 2 && feet.getZ() <= box.maxZ() + 2)
                return false;
        }
        return true;
    }

    private static boolean safeGround(ServerLevel level, BlockPos feet) {
        BlockPos below = feet.below();
        var support = level.getBlockState(below);
        if (support.is(BlockTags.LEAVES) || support.is(BlockTags.LOGS)
                || support.getCollisionShape(level, below).isEmpty()
                || !support.getFluidState().isEmpty()) return false;
        for (int rise = 0; rise <= 1; rise++) {
            BlockPos at = feet.above(rise);
            var state = level.getBlockState(at);
            if (!state.getCollisionShape(level, at).isEmpty()
                    || !state.getFluidState().isEmpty()) return false;
        }
        return true;
    }

    private static boolean nearTree(ServerLevel level, BlockPos feet) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 3; dy++) {
                    var state = level.getBlockState(feet.offset(dx, dy, dz));
                    if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) return true;
                }
            }
        }
        return false;
    }

    private static int clearFacadeRays(ServerLevel level, ServerPlayer player,
                                        Piece piece, BlockPos feet, int width) {
        Vec3 eye = new Vec3(feet.getX() + 0.5, feet.getY() + 1.62,
                feet.getZ() + 0.5);
        int clear = 0;
        for (int x : new int[] {width / 4, width / 2, width * 3 / 4}) {
            Vec3 target = Vec3.atCenterOf(piece.at(x, 8, width - 1));
            var hit = level.clip(new ClipContext(eye, target,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.MISS
                    || inside(hit.getBlockPos(), piece.box())) clear++;
        }
        return clear;
    }

    private static boolean inside(BlockPos at, BoundingBox box) {
        return at.getX() >= box.minX() && at.getX() <= box.maxX()
                && at.getY() >= box.minY() && at.getY() <= box.maxY()
                && at.getZ() >= box.minZ() && at.getZ() <= box.maxZ();
    }

    private static void capture(ClientGameTestContext context,
                                TestSingleplayerContext world, Camera camera,
                                String name) {
        Vec3 eye = new Vec3(camera.feet().getX() + 0.5,
                camera.feet().getY() + 1.62, camera.feet().getZ() + 0.5);
        double dx = camera.target().x - eye.x;
        double dy = camera.target().y - eye.y;
        double dz = camera.target().z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        world.getServer().runOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            player.teleportTo(server.overworld(), camera.feet().getX() + 0.5,
                    camera.feet().getY(), camera.feet().getZ() + 0.5,
                    EnumSet.noneOf(Relative.class), yaw, pitch, false);
        });
        world.getConnection().waitForChunksRender();
        context.waitTicks(40);
        context.runOnClient(client -> {
            client.gui.hud.getChat().clearMessages(false);
            client.gui.toastManager().clear();
        });
        context.takeScreenshot(name);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
