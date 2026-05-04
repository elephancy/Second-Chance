package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.exploration.ExplorationProgress;
import com.feel.gems.adventure.exploration.ExplorationDeliveryAccount;
import com.feel.gems.adventure.exploration.ExplorationState;
import com.feel.gems.adventure.maze.MazeBlocks;
import com.feel.gems.state.GemsPersistentDataHolder;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Opt-in final gate: generated maze, saved checkpoint and single-clear reward across world reloads. */
@SuppressWarnings("UnstableApiUsage")
public final class NinefoldMazeNaturalClientGameTest implements FabricClientGameTest {
    private static final List<Block> ORDER = List.of(
            MazeBlocks.ENTRY, MazeBlocks.SUN, MazeBlocks.MOON, MazeBlocks.STAR,
            MazeBlocks.CHECKPOINT_ONE, MazeBlocks.MIRROR_AMBER, MazeBlocks.MIRROR_BLUE,
            MazeBlocks.CHECKPOINT_TWO, MazeBlocks.WITNESS_ONE, MazeBlocks.WITNESS_TWO,
            MazeBlocks.WITNESS_THREE, MazeBlocks.CHECKPOINT_THREE, MazeBlocks.EXIT);

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!Boolean.getBoolean("gems.ninefoldMazeGate")) return;
        TestWorldSave save;
        Site site;
        try (TestSingleplayerContext world = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(settings -> {
                    settings.setSeed("SecondChanceNinefoldGate");
                    settings.setGenerateStructures(true);
                }).create()) {
            save = world.getWorldSave();
            world.getConnection().waitForChunksRender();
            site = world.getServer().computeOnServer(server -> {
                ServerLevel level = server.overworld();
                TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE,
                        Identifier.fromNamespaceAndPath("gems", "atlas_target/ninefold_maze"));
                BlockPos located = level.findNearestMapStructure(tag, new BlockPos(0, 80, 0), 160, false);
                if (located == null) throw new IllegalStateException("No natural maze within Atlas reach");
                var registry = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
                StructureStart start = null;
                for (int radius = 0; radius <= 5 && start == null; radius++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                            var chunk = level.getChunk((located.getX() >> 4) + dx,
                                    (located.getZ() >> 4) + dz);
                            for (var holder : registry.getTagOrEmpty(tag)) {
                                StructureStart candidate = level.structureManager()
                                        .getStartForStructure(holder.value(), chunk);
                                if (candidate != null && candidate.isValid()) {
                                    start = candidate;
                                    break;
                                }
                            }
                        }
                    }
                }
                if (start == null) throw new IllegalStateException("Located maze has no saved start");
                if (start.getPieces().size() != 9) throw new IllegalStateException(
                        "Maze generated " + start.getPieces().size() + " pieces instead of nine");
                for (var piece : start.getPieces()) {
                    BoundingBox box = piece.getBoundingBox();
                    for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
                        for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                            level.getChunk(cx, cz);
                        }
                    }
                }
                Map<Block, BlockPos> markers = new HashMap<>();
                Set<BlockPos> resetPositions = new HashSet<>();
                for (var piece : start.getPieces()) {
                    BoundingBox box = piece.getBoundingBox();
                    for (int y = box.minY(); y <= box.maxY(); y++) {
                        for (int z = box.minZ(); z <= box.maxZ(); z++) {
                            for (int x = box.minX(); x <= box.maxX(); x++) {
                                BlockPos pos = new BlockPos(x, y, z);
                                Block block = level.getBlockState(pos).getBlock();
                                if (block == MazeBlocks.RESET) resetPositions.add(pos);
                                if (ORDER.contains(block) && markers.putIfAbsent(block, pos) != null) {
                                    throw new IllegalStateException("Duplicate maze marker " + block);
                                }
                            }
                        }
                    }
                }
                for (Block block : ORDER) {
                    if (!markers.containsKey(block)) throw new IllegalStateException("Missing maze marker " + block);
                }
                BlockPos entry = markers.get(MazeBlocks.ENTRY);
                BoundingBox entryPiece = start.getPieces().stream().map(piece -> piece.getBoundingBox())
                        .filter(box -> inside(box, entry)).findFirst()
                        .orElseThrow(() -> new IllegalStateException("Maze entry is outside every piece"));
                assertNaturalRoute(level, start.getBoundingBox(), entryPiece, markers);
                ServerPlayer player = world.getConnection().getServerPlayer();
                var callback = UseBlockCallback.EVENT.invoker();
                for (Block block : ORDER.subList(0, 5)) activate(level, player, callback, markers, block);
                String activity = ExplorationProgress.activityId("maze", entry.getX(), entry.getZ());
                var progress = mazeData(player);
                if (progress.getLongOr("entry", 0L) != entry.asLong()
                        || progress.getIntOr("glyph", 0) != 3
                        || progress.getIntOr("checkpoints", 0) != 1
                        || !progress.contains("return")) {
                    throw new IllegalStateException("First checkpoint was not saved before closing the world");
                }
                if (ExplorationState.get(server).model().earned(player.getUUID(), activity)
                        || ExplorationDeliveryAccount.of(player).has(activity)
                        || count(player, Items.ECHO_SHARD) != 0) {
                    throw new IllegalStateException("Unfinished maze granted a reward");
                }
                BlockPos middleReset = resetPositions.stream()
                        .filter(pos -> pos.getY() == markers.get(MazeBlocks.CHECKPOINT_ONE).getY() + 9)
                        .min(Comparator.comparingDouble(pos -> pos.distSqr(markers.get(MazeBlocks.MIRROR_AMBER))))
                        .orElseThrow(() -> new IllegalStateException("Middle-floor reset lamp did not generate"));
                return new Site(entry, start.getBoundingBox(), Map.copyOf(markers), player.getUUID(),
                        activity, progress.getLongOr("return", 0L), middleReset,
                        start.getChunkPos().toString());
            });
        }
        try (TestSingleplayerContext world = save.open()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runOnServer(server -> {
                ServerLevel level = server.overworld();
                ServerPlayer player = world.getConnection().getServerPlayer();
                assertPlayerAndCheckpoint(player, site, 1);
                if (ExplorationState.get(server).model().earned(player.getUUID(), site.activity())) {
                    throw new IllegalStateException("World reload gave the unfinished maze a clear");
                }
                var callback = UseBlockCallback.EVENT.invoker();
                activate(level, player, callback, Map.of(MazeBlocks.RESET, site.middleReset()),
                        MazeBlocks.RESET);
                if (!player.blockPosition().equals(BlockPos.of(site.checkpointReturn()))) {
                    throw new IllegalStateException("Saved reset lamp failed to return to the first checkpoint");
                }
                for (Block block : ORDER.subList(5, ORDER.size())) {
                    activate(level, player, callback, site.markers(), block);
                }
                assertReward(server, player, site);
                System.out.println("NINEFOLD_MAZE_GATE start=" + site.start()
                        + " pieces=9 markers=" + site.markers().size()
                        + " exteriorAndThreeFloorRoute=true checkpointReopen=true soloReward=true");
            });
        }
        try (TestSingleplayerContext world = save.open()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runOnServer(server -> {
                ServerLevel level = server.overworld();
                ServerPlayer player = world.getConnection().getServerPlayer();
                assertPlayerAndCheckpoint(player, site, 7);
                assertReward(server, player, site);
                activate(level, player, UseBlockCallback.EVENT.invoker(), site.markers(), MazeBlocks.EXIT);
                assertReward(server, player, site);
                System.out.println("NINEFOLD_MAZE_PERSISTENCE_PASS checkpointMask=7"
                        + " savedWorldReward=true repeatExitDuplicate=false");
            });
            world.getServer().runOnServer(server -> {
                ServerLevel level = server.overworld();
                ServerPlayer player = world.getConnection().getServerPlayer();
                BoundingBox bounds = site.bounds();
                BlockPos entry = site.entry();
                int west = entry.getX() - bounds.minX();
                int east = bounds.maxX() - entry.getX();
                int north = entry.getZ() - bounds.minZ();
                int south = bounds.maxZ() - entry.getZ();
                int closest = Math.min(Math.min(west, east), Math.min(north, south));
                int x = entry.getX();
                int z = entry.getZ();
                float yaw;
                if (closest == west) {
                    x = bounds.minX() - 18;
                    yaw = -90.0F;
                } else if (closest == east) {
                    x = bounds.maxX() + 18;
                    yaw = 90.0F;
                } else if (closest == north) {
                    z = bounds.minZ() - 18;
                    yaw = 0.0F;
                } else {
                    z = bounds.maxZ() + 18;
                    yaw = 180.0F;
                }
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                player.teleportTo(level, x + 0.5, y, z + 0.5,
                        EnumSet.noneOf(Relative.class), yaw, 12.0F, false);
            });
            context.waitTicks(210);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("ninefold-maze-natural-arrival");
        }
    }

    private record Site(BlockPos entry, BoundingBox bounds, Map<Block, BlockPos> markers,
                        UUID playerId, String activity, long checkpointReturn,
                        BlockPos middleReset, String start) { }

    private static net.minecraft.nbt.CompoundTag mazeData(ServerPlayer player) {
        return ((GemsPersistentDataHolder) player).gems$getPersistentData()
                .getCompound("NinefoldMaze").orElseThrow();
    }

    private static void assertPlayerAndCheckpoint(ServerPlayer player, Site site, int checkpoints) {
        if (!player.getUUID().equals(site.playerId())) {
            throw new IllegalStateException("World reload changed the maze player's identity");
        }
        var progress = mazeData(player);
        if (progress.getLongOr("entry", 0L) != site.entry().asLong()
                || progress.getLongOr("return", 0L) == 0L
                || progress.getIntOr("glyph", 0) != 3
                || progress.getIntOr("checkpoints", 0) != checkpoints) {
            throw new IllegalStateException("Maze checkpoint or solved glyphs did not survive world reload");
        }
        if (checkpoints == 1 && progress.getLongOr("return", 0L) != site.checkpointReturn()) {
            throw new IllegalStateException("First checkpoint return position changed during world reload");
        }
        if (checkpoints == 7 && (progress.getIntOr("mirror", 0) != 2
                || progress.getIntOr("witnesses", 0) != 7)) {
            throw new IllegalStateException("Completed maze seals did not survive world reload");
        }
    }

    private static void assertReward(net.minecraft.server.MinecraftServer server,
                                     ServerPlayer player, Site site) {
        var state = ExplorationState.get(server).model();
        if (!state.discovered(site.activity()) || !state.earned(player.getUUID(), site.activity())
                || !ExplorationDeliveryAccount.of(player).has(site.activity())
                || count(player, Items.ECHO_SHARD) != 3) {
            throw new IllegalStateException("Saved or repeated maze clear lost or duplicated supplies");
        }
    }

    private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static void activate(ServerLevel level, ServerPlayer player,
                                 UseBlockCallback callback, Map<Block, BlockPos> markers, Block block) {
        BlockPos pos = markers.get(block);
        BlockPos feet = null;
        for (Direction side : new Direction[]{Direction.NORTH, Direction.EAST,
                Direction.SOUTH, Direction.WEST}) {
            BlockPos candidate = pos.relative(side);
            if (level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty()
                    && level.getBlockState(candidate.above())
                            .getCollisionShape(level, candidate.above()).isEmpty()
                    && !level.getBlockState(candidate.below())
                            .getCollisionShape(level, candidate.below()).isEmpty()
                    && level.getFluidState(candidate).isEmpty()) {
                feet = candidate;
                break;
            }
        }
        if (feet == null) throw new IllegalStateException("Marker has no safe standing spot: " + block + " at " + pos);
        player.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5,
                EnumSet.noneOf(Relative.class), player.getYRot(), player.getXRot(), false);
        var result = callback.interact(player, level, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        if (result != InteractionResult.SUCCESS_SERVER) {
            throw new IllegalStateException("Marker callback ignored " + block + " at " + pos);
        }
    }

    private static void assertNaturalRoute(ServerLevel level, BoundingBox all,
                                           BoundingBox entryPiece, Map<Block, BlockPos> markers) {
        int ground = markers.get(MazeBlocks.ENTRY).getY();
        Set<BlockPos> lower = floodFloor(level, all,
                adjacent(level, all, markers.get(MazeBlocks.ENTRY), ground), ground);
        requireMarkers(level, all, lower, markers, ground, MazeBlocks.SUN,
                MazeBlocks.MOON, MazeBlocks.STAR, MazeBlocks.CHECKPOINT_ONE);
        requireExteriorGrade(level, entryPiece, markers.get(MazeBlocks.ENTRY));
        BlockPos firstLadder = connectingLadder(level, all, lower, ground, ground + 9);
        Set<BlockPos> middle = floodFloor(level, all,
                adjacent(level, all, firstLadder.above(9), ground + 9), ground + 9);
        requireMarkers(level, all, middle, markers, ground + 9,
                MazeBlocks.MIRROR_AMBER, MazeBlocks.MIRROR_BLUE, MazeBlocks.CHECKPOINT_TWO);
        BlockPos secondLadder = connectingLadder(level, all, middle, ground + 9, ground + 18);
        Set<BlockPos> upper = floodFloor(level, all,
                adjacent(level, all, secondLadder.above(9), ground + 18), ground + 18);
        requireMarkers(level, all, upper, markers, ground + 18,
                MazeBlocks.WITNESS_ONE, MazeBlocks.WITNESS_TWO, MazeBlocks.WITNESS_THREE,
                MazeBlocks.CHECKPOINT_THREE, MazeBlocks.EXIT);
    }

    private static void requireMarkers(ServerLevel level, BoundingBox box, Set<BlockPos> reached,
                                       Map<Block, BlockPos> markers, int y, Block... targets) {
        for (Block target : targets) {
            BlockPos marker = markers.get(target);
            if (marker.getY() != y || adjacent(level, box, marker, y).stream()
                    .noneMatch(reached::contains)) {
                throw new IllegalStateException("No standing route to " + target + " at " + marker);
            }
        }
    }

    private static BlockPos connectingLadder(ServerLevel level, BoundingBox box,
                                             Set<BlockPos> reached, int lowerY, int upperY) {
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                BlockPos bottom = new BlockPos(x, lowerY, z);
                if (!level.getBlockState(bottom).is(Blocks.LADDER)
                        || adjacent(level, box, bottom, lowerY).stream()
                                .noneMatch(reached::contains)) continue;
                boolean continuous = true;
                for (int y = lowerY + 1; y <= upperY; y++) {
                    if (!level.getBlockState(new BlockPos(x, y, z)).is(Blocks.LADDER)) {
                        continuous = false;
                        break;
                    }
                }
                if (continuous && !adjacent(level, box, new BlockPos(x, upperY, z), upperY).isEmpty()) {
                    return bottom;
                }
            }
        }
        throw new IllegalStateException("No reachable continuous maze ladder from y="
                + lowerY + " to y=" + upperY);
    }

    private static Set<BlockPos> floodFloor(ServerLevel level, BoundingBox box,
                                            Set<BlockPos> starts, int y) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(starts);
        Set<BlockPos> reached = new HashSet<>(starts);
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos next = current.relative(side);
                if (next.getY() == y && !reached.contains(next) && standable(level, box, next)) {
                    reached.add(next);
                    queue.addLast(next);
                }
            }
        }
        return reached;
    }

    private static Set<BlockPos> adjacent(ServerLevel level, BoundingBox box,
                                          BlockPos marker, int y) {
        Set<BlockPos> positions = new HashSet<>();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos pos = marker.relative(side);
            if (pos.getY() == y && standable(level, box, pos)) positions.add(pos);
        }
        return positions;
    }

    private static boolean standable(ServerLevel level, BoundingBox box, BlockPos pos) {
        return inside(box, pos) && level.getFluidState(pos).isEmpty()
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }

    private static boolean inside(BoundingBox box, BlockPos pos) {
        return pos.getX() >= box.minX() && pos.getX() <= box.maxX()
                && pos.getY() >= box.minY() && pos.getY() <= box.maxY()
                && pos.getZ() >= box.minZ() && pos.getZ() <= box.maxZ();
    }

    private static void requireExteriorGrade(ServerLevel level, BoundingBox piece, BlockPos entry) {
        int y = entry.getY();
        Set<BlockPos> starts = new HashSet<>();
        BoundingBox approach = new BoundingBox(piece.minX() - 1, y - 4, piece.minZ() - 1,
                piece.maxX() + 1, y, piece.maxZ() + 1);
        for (int x = approach.minX(); x <= approach.maxX(); x++) {
            for (int z : new int[]{approach.minZ(), approach.maxZ()}) {
                addExterior(level, approach, starts, x, z, y);
            }
        }
        for (int z = approach.minZ(); z <= approach.maxZ(); z++) {
            for (int x : new int[]{approach.minX(), approach.maxX()}) {
                addExterior(level, approach, starts, x, z, y);
            }
        }
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(starts);
        Set<BlockPos> reached = new HashSet<>(starts);
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            if (adjacent(level, approach, entry, y).contains(current)) return;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos next = current.relative(side).above(dy);
                    if (!reached.contains(next) && standable(level, approach, next)) {
                        reached.add(next);
                        queue.addLast(next);
                    }
                }
            }
        }
        throw new IllegalStateException("No natural exterior ascent to maze entry at " + entry);
    }

    private static void addExterior(ServerLevel level, BoundingBox approach,
                                    Set<BlockPos> starts, int x, int z, int entryY) {
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos feet = new BlockPos(x, surface, z);
        if (surface >= entryY - 4 && surface <= entryY - 3
                && standable(level, approach, feet)) starts.add(feet);
    }
}
