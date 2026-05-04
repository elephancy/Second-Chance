package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.DomainBlocks;
import com.feel.gems.adventure.FriendDomainUnlockState;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/** An opt-in, saved-world Riverward walk and ordinary flood-timer receipt. */
@SuppressWarnings("UnstableApiUsage")
final class KhaledRiverwardNaturalProbe {
    private static final Set<String> EXPECTED = Set.of(
            "gems:khaled_riverward_floodgate_v8",
            "gems:khaled_tide_engine_v6",
            "gems:khaled_refuge_clinic_v6");

    private record Marks(BlockPos start, BlockPos west, BlockPos east,
                         BlockPos finish) {}

    private KhaledRiverwardNaturalProbe() {}

    static void inspectPieces(StructureStart start) {
        var found = new java.util.HashSet<String>();
        for (var piece : start.getPieces()) {
            if (!(piece instanceof PoolElementStructurePiece pooled)
                    || !(pooled.getElement() instanceof SinglePoolElement single)) continue;
            String id = single.getTemplateLocation().toString();
            found.add(id);
            System.out.println("KHALED_NATURAL_PIECE template=" + id
                    + " rotation=" + pooled.getRotation()
                    + " box=" + piece.getBoundingBox());
        }
        if (!found.equals(EXPECTED)) {
            throw new IllegalStateException("Natural Riverward selected " + found
                    + " instead of the three staged versioned pieces");
        }
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world,
            List<BoundingBox> buildings,
            NaturalDistrictRouteProbe.WalkApproach bankApproach) {
        Marks marks = world.getServer().computeOnServer(server ->
                scanMarks(server.overworld(), buildings));
        BlockPos bankInterior = bankApproach.steps().getLast().feet();
        Map<String, List<NaturalDistrictRouteProbe.Step>> routes =
                world.getServer().computeOnServer(server -> {
                    ServerLevel level = server.overworld();
                    Map<BlockPos, NaturalDistrictRouteProbe.Step> floor =
                            standingCells(level, buildings);
                    Map<String, List<NaturalDistrictRouteProbe.Step>> paths = Map.of(
                            "clinic-start", route(floor, bankInterior, marks.start().above()),
                            "start-west", route(floor, marks.start().above(), marks.west().above()),
                            "west-east", route(floor, marks.west().above(), marks.east().above()),
                            "east-engine", route(floor, marks.east().above(), marks.finish().above()));
                    paths.forEach((name, path) -> System.out.println(
                            "KHALED_NATURAL_ROUTE segment=" + name + " nodes=" + path.size()
                                    + " start=" + path.getFirst().feet()
                                    + " end=" + path.getLast().feet()));
                    return paths;
                });

        // This is the same interior tile reached by the earlier normal-input
        // bank walk. The visual survey moved the camera, so restore that
        // proved position before walking the actual clinic and flood route.
        world.getServer().runOnServer(server -> {
            ServerPlayer player = world.getConnection().getServerPlayer();
            var step = routes.get("clinic-start").getFirst();
            player.teleportTo(server.overworld(), step.feet().getX() + 0.5,
                    step.standingY(), step.feet().getZ() + 0.5,
                    EnumSet.noneOf(Relative.class), 0, 0, false);
        });
        context.waitTicks(5);
        NaturalDistrictRouteProbe.walkPath(context, world, routes.get("clinic-start"),
                "khaled_floodgate", "clinic-start");
        standOnMark(context, world, marks.start());
        long started = world.getServer().computeOnServer(server -> {
            ServerPlayer player = world.getConnection().getServerPlayer();
            ServerLevel level = server.overworld();
            if (FriendDomainUnlockState.get(server).cleared("khaled")) {
                throw new IllegalStateException("Riverward was already clear before natural start");
            }
            if (use(player, level, marks.start()) != InteractionResult.SUCCESS_SERVER) {
                throw new IllegalStateException("Natural clinic start mark ignored the player");
            }
            return (long) server.getTickCount();
        });
        System.out.println("KHALED_NATURAL_TIMER startTick=" + started);

        NaturalDistrictRouteProbe.walkPath(context, world, routes.get("start-west"),
                "khaled_floodgate", "start-west");
        standOnMark(context, world, marks.west());
        recordArrival(world, "west", marks.west(), started);
        waitUntil(context, world, started + 250);
        assertOnMark(world, marks.west());
        context.takeScreenshot("khaled-natural-west-shelter-pulse");

        NaturalDistrictRouteProbe.walkPath(context, world, routes.get("west-east"),
                "khaled_floodgate", "west-east");
        standOnMark(context, world, marks.east());
        recordArrival(world, "east", marks.east(), started);
        waitUntil(context, world, started + 410);
        assertOnMark(world, marks.east());
        context.takeScreenshot("khaled-natural-east-shelter-pulse");

        NaturalDistrictRouteProbe.walkPath(context, world, routes.get("east-engine"),
                "khaled_floodgate", "east-engine");
        standOnMark(context, world, marks.finish());
        recordArrival(world, "finish", marks.finish(), started);
        world.getServer().runOnServer(server -> {
            ServerPlayer player = world.getConnection().getServerPlayer();
            if (use(player, server.overworld(), marks.finish())
                    != InteractionResult.SUCCESS_SERVER
                    || !FriendDomainUnlockState.get(server).cleared("khaled")) {
                throw new IllegalStateException("Natural timed crossing failed to open the shared Shelter Desk"
                        + " at tick+" + (server.getTickCount() - started));
            }
            System.out.println("KHALED_NATURAL_CLEAR start=" + marks.start()
                    + " west=" + marks.west() + " east=" + marks.east()
                    + " finish=" + marks.finish()
                    + " elapsedTicks=" + (server.getTickCount() - started)
                    + " sharedUnlock=true");
        });
    }

    private static Marks scanMarks(ServerLevel level, List<BoundingBox> buildings) {
        Map<Block, BlockPos> found = new HashMap<>();
        Set<Block> wanted = Set.of(DomainBlocks.KHALED_ROUTE_START,
                DomainBlocks.KHALED_WEST_REFUGE, DomainBlocks.KHALED_EAST_REFUGE,
                DomainBlocks.KHALED_ROUTE_FINISH);
        for (BoundingBox box : buildings) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    for (int y = box.minY() + 3; y <= box.minY() + 8; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        Block block = level.getBlockState(pos).getBlock();
                        if (wanted.contains(block) && found.putIfAbsent(block, pos) != null) {
                            throw new IllegalStateException("Duplicate Riverward mark " + block);
                        }
                    }
                }
            }
        }
        if (!found.keySet().equals(wanted)) {
            throw new IllegalStateException("Missing natural Riverward marks: " + found);
        }
        return new Marks(found.get(DomainBlocks.KHALED_ROUTE_START),
                found.get(DomainBlocks.KHALED_WEST_REFUGE),
                found.get(DomainBlocks.KHALED_EAST_REFUGE),
                found.get(DomainBlocks.KHALED_ROUTE_FINISH));
    }

    private static Map<BlockPos, NaturalDistrictRouteProbe.Step> standingCells(
            ServerLevel level, List<BoundingBox> buildings) {
        Map<BlockPos, NaturalDistrictRouteProbe.Step> cells = new HashMap<>();
        for (BoundingBox box : buildings) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    for (int y = box.minY() + 2; y <= box.minY() + 8; y++) {
                        BlockPos floor = new BlockPos(x, y, z);
                        var state = level.getBlockState(floor);
                        if (!state.getFluidState().isEmpty()) continue;
                        VoxelShape shape = state.getCollisionShape(level, floor);
                        if (shape.isEmpty()) continue;
                        double height = shape.max(Direction.Axis.Y);
                        if (height < 0.45 || height > 1.05) continue;
                        BlockPos feet = floor.above();
                        if (!clear(level, feet) || !clear(level, feet.above())) continue;
                        cells.put(feet, new NaturalDistrictRouteProbe.Step(feet,
                                floor.getY() + height));
                    }
                }
            }
        }
        return cells;
    }

    private static boolean clear(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.getFluidState().isEmpty()
                && state.getCollisionShape(level, pos).isEmpty();
    }

    private static List<NaturalDistrictRouteProbe.Step> route(
            Map<BlockPos, NaturalDistrictRouteProbe.Step> cells,
            BlockPos source, BlockPos target) {
        if (!cells.containsKey(source) || !cells.containsKey(target)) {
            throw new IllegalStateException("Natural Riverward endpoint has no dry player-height"
                    + " standing cell: " + source + " to " + target);
        }
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Map<BlockPos, BlockPos> previous = new HashMap<>();
        queue.add(source);
        previous.put(source, source);
        while (!queue.isEmpty() && !previous.containsKey(target)) {
            BlockPos current = queue.removeFirst();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos next = current.relative(direction).above(dy);
                    if (previous.containsKey(next) || !cells.containsKey(next)
                            || Math.abs(cells.get(next).standingY()
                                    - cells.get(current).standingY()) > 1.05) continue;
                    previous.put(next, current);
                    queue.addLast(next);
                }
            }
        }
        if (!previous.containsKey(target)) {
            throw new IllegalStateException("No connected dry Riverward route "
                    + source + " to " + target + " across the saved three-piece site");
        }
        List<NaturalDistrictRouteProbe.Step> backwards = new ArrayList<>();
        BlockPos current = target;
        while (true) {
            backwards.add(cells.get(current));
            BlockPos parent = previous.get(current);
            if (parent.equals(current)) break;
            current = parent;
        }
        java.util.Collections.reverse(backwards);
        return List.copyOf(backwards);
    }

    private static void standOnMark(ClientGameTestContext context,
            TestSingleplayerContext world, BlockPos mark) {
        for (int tick = 0; tick < 24; tick += 2) {
            boolean onMark = world.getServer().computeOnServer(server ->
                    world.getConnection().getServerPlayer().blockPosition().below().equals(mark));
            if (onMark) return;
            float yaw = world.getServer().computeOnServer(server -> {
                ServerPlayer player = world.getConnection().getServerPlayer();
                return (float) Math.toDegrees(Math.atan2(
                        player.getX() - mark.getX() - 0.5,
                        mark.getZ() + 0.5 - player.getZ()));
            });
            context.runOnClient(client -> {
                if (client.player != null) client.player.setYRot(yaw);
            });
            context.getInput().holdKey(options -> options.keyUp);
            try {
                context.waitTicks(2);
            } finally {
                context.getInput().releaseKey(options -> options.keyUp);
            }
        }
        assertOnMark(world, mark);
    }

    private static void assertOnMark(TestSingleplayerContext world, BlockPos mark) {
        world.getServer().runOnServer(server -> {
            ServerPlayer player = world.getConnection().getServerPlayer();
            if (!player.onGround() || !player.blockPosition().below().equals(mark)) {
                throw new IllegalStateException("Player did not stand on natural Riverward mark "
                        + mark + ": " + player.position());
            }
        });
    }

    private static void recordArrival(TestSingleplayerContext world,
            String stage, BlockPos mark, long started) {
        assertOnMark(world, mark);
        world.getServer().runOnServer(server -> System.out.println(
                "KHALED_NATURAL_ARRIVAL stage=" + stage + " mark=" + mark
                        + " tick+" + (server.getTickCount() - started)));
    }

    private static void waitUntil(ClientGameTestContext context,
            TestSingleplayerContext world, long tick) {
        while (world.getServer().computeOnServer(server ->
                (long) server.getTickCount()) < tick) context.waitTicks(5);
    }

    private static InteractionResult use(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return UseBlockCallback.EVENT.invoker().interact(player, level,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }
}
