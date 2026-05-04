package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.DomainBlocks;
import com.feel.gems.adventure.FriendDomainUnlockState;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One disposable saved-start inspection of the three newly selected harbor pieces. */
@SuppressWarnings("UnstableApiUsage")
final class NourHarborV5NaturalProbe {
    private static final Set<String> EXPECTED = Set.of(
            "gems:nour_signal_harbor_station_v5",
            "gems:nour_boathouse_station_v5",
            "gems:nour_foghouse_station_v5");

    private record Marks(BlockPos boat, BlockPos fog, BlockPos blue,
                         BlockPos amber, BlockPos white, BlockPos barrel,
                         BlockPos ladderTop) { }

    private NourHarborV5NaturalProbe() { }

    static void inspectPieces(StructureStart start) {
        Set<String> found = new HashSet<>();
        for (var piece : start.getPieces()) {
            if (!(piece instanceof PoolElementStructurePiece pooled)
                    || !(pooled.getElement() instanceof SinglePoolElement single)) continue;
            String id = single.getTemplateLocation().toString();
            found.add(id);
            System.out.println("NOUR_V5_NATURAL_PIECE template=" + id
                    + " rotation=" + pooled.getRotation()
                    + " box=" + piece.getBoundingBox());
        }
        if (!found.equals(EXPECTED)) {
            throw new IllegalStateException("Natural harbor selected " + found
                    + " instead of the three v5 pieces");
        }
    }

    /** Inspect saved blocks after carvers, placement and neighbor-chunk loading. */
    static void inspectSavedFoundation(ServerLevel level, StructureStart start) {
        int anchors = 0;
        PoolElementStructurePiece root = null;
        List<BoundingBox> savedBoxes = new ArrayList<>();
        for (var piece : start.getPieces()) {
            if (!(piece instanceof PoolElementStructurePiece pooled)
                    || !(pooled.getElement() instanceof SinglePoolElement single)
                    || !EXPECTED.contains(single.getTemplateLocation().toString())) continue;
            savedBoxes.add(piece.getBoundingBox());
            if (single.getTemplateLocation().toString().equals(
                    "gems:nour_signal_harbor_station_v5")) root = pooled;
            BoundingBox box = piece.getBoundingBox();
            int[] xs = {box.minX() + 4, (box.minX() + box.maxX()) / 2,
                    box.maxX() - 4};
            int[] zs = {box.minZ() + 4, (box.minZ() + box.maxZ()) / 2,
                    box.maxZ() - 4};
            for (int x : xs) for (int z : zs) {
                BlockPos top = new BlockPos(x, box.minY() - 1, z);
                int timbers = 0;
                BlockPos probe = top;
                while (timbers <= 25 && (level.getBlockState(probe).is(
                        Blocks.STRIPPED_SPRUCE_LOG)
                        || level.getBlockState(probe).is(Blocks.DARK_OAK_PLANKS))) {
                    timbers++;
                    probe = probe.below();
                }
                var anchor = level.getBlockState(probe);
                if (timbers > 25 || !anchor.isSolid()
                        || !anchor.getFluidState().isEmpty()) {
                    throw new IllegalStateException("Natural harbor trestle is ungrounded: "
                            + top + " timbers=" + timbers + " bottom=" + anchor);
                }
                anchors++;
            }
        }
        if (anchors != 27 || root == null) {
            throw new IllegalStateException("Expected 27 v5 foundation anchors, found "
                    + anchors + " root=" + root);
        }
        int deckCells = 0;
        for (int z = 38; z <= 46; z++) for (int x = 22; x <= 24; x++) {
            BlockPos floor = StructureTemplate.transform(new BlockPos(x, 4, z),
                    Mirror.NONE, root.getRotation(), BlockPos.ZERO)
                    .offset(root.getPosition());
            if (!level.getBlockState(floor).isSolid()
                    || !level.getFluidState(floor).isEmpty()
                    || !level.getBlockState(floor.above()).getCollisionShape(
                        level, floor.above()).isEmpty()
                    || !level.getFluidState(floor.above()).isEmpty()
                    || !level.getBlockState(floor.above(2)).getCollisionShape(
                        level, floor.above(2)).isEmpty()) {
                throw new IllegalStateException("Natural harbor public deck is blocked: "
                        + floor);
            }
            deckCells++;
        }
        Marks marks = scan(level, savedBoxes);
        int roomFloors = 0;
        for (BlockPos target : List.of(marks.boat(), marks.fog(),
                marks.blue(), marks.amber(), marks.white(),
                marks.barrel(), marks.ladderTop())) {
            adjacentStand(level, target);
            roomFloors++;
        }
        int boatWaterCells = inspectBoatApproach(level, root);
        List<Integer> dryRoutes = connectedDockRoutes(level, root, savedBoxes, marks);
        System.out.println("NOUR_V5_SAVED_SUPPORT anchors=" + anchors
                + " publicDeckCells=" + deckCells
                + " dryRoomStandingFloors=" + roomFloors
                + " boatWaterCells=" + boatWaterCells
                + " dockToControls=" + dryRoutes
                + " pieces=3 status=grounded-after-carvers");
    }

    private static int inspectBoatApproach(ServerLevel level,
                                            PoolElementStructurePiece root) {
        int cells = 0;
        int waterline = Integer.MIN_VALUE;
        for (int z = 47; z <= 58; z++) for (int x = 22; x <= 24; x++) {
            BlockPos column = local(root, x, 0, z);
            int top = Integer.MIN_VALUE;
            for (int y = 64; y >= 61; y--) {
                BlockPos sample = new BlockPos(column.getX(), y, column.getZ());
                if (level.getFluidState(sample).is(FluidTags.WATER)) {
                    top = y;
                    if (!level.getBlockState(sample.above()).getCollisionShape(
                            level, sample.above()).isEmpty()
                            || !level.getFluidState(sample.above()).isEmpty()) {
                        throw new IllegalStateException("Blocked boat headroom at " + sample);
                    }
                    break;
                }
            }
            if (top == Integer.MIN_VALUE || (waterline != Integer.MIN_VALUE
                    && Math.abs(top - waterline) > 1)) {
                throw new IllegalStateException("Boat cannot reach saved south dock at "
                        + column + " topWater=" + top + " firstWater=" + waterline);
            }
            if (waterline == Integer.MIN_VALUE) waterline = top;
            cells++;
        }
        for (int x : new int[]{21, 25}) {
            for (int y = 1; y <= 4; y++) {
                BlockPos ladder = local(root, x, y, 46);
                if (!level.getBlockState(ladder).is(Blocks.LADDER)) {
                    throw new IllegalStateException("Saved dock climb missing at " + ladder);
                }
            }
            BlockPos top = local(root, x, 5, 46);
            BlockPos exit = local(root, x == 21 ? 22 : 24, 4, 46);
            if (!level.getBlockState(top).getCollisionShape(level, top).isEmpty()
                    || !level.getBlockState(exit).isSolid()) {
                throw new IllegalStateException("Saved dock ladder has no dry top exit: "
                        + top + " via " + exit);
            }
        }
        return cells;
    }

    private static List<Integer> connectedDockRoutes(ServerLevel level,
            PoolElementStructurePiece root, List<BoundingBox> boxes, Marks marks) {
        Map<BlockPos, Double> dry = new HashMap<>();
        for (BoundingBox box : boxes) {
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
                        if (clear(level, feet) && clear(level, feet.above())) {
                            dry.put(feet, y + height);
                        }
                    }
                }
            }
        }
        BlockPos dock = local(root, 23, 5, 45);
        if (!dry.containsKey(dock)) {
            throw new IllegalStateException("No dry player-height dock entry at " + dock);
        }
        Map<BlockPos, Integer> steps = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        steps.put(dock, 0);
        queue.add(dock);
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos next = current.relative(direction).above(dy);
                    if (!dry.containsKey(next) || steps.containsKey(next)
                            || Math.abs(dry.get(next) - dry.get(current)) > 1.05) continue;
                    steps.put(next, steps.get(current) + 1);
                    queue.addLast(next);
                }
            }
        }
        List<Integer> lengths = new ArrayList<>();
        for (BlockPos target : List.of(marks.boat(), marks.fog(), marks.blue(),
                marks.amber(), marks.white(), marks.barrel())) {
            int shortest = Integer.MAX_VALUE;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                Integer length = steps.get(target.relative(direction));
                if (length != null) shortest = Math.min(shortest, length);
            }
            if (shortest == Integer.MAX_VALUE) {
                throw new IllegalStateException("No dry connected dock route to " + target);
            }
            lengths.add(shortest);
        }
        return List.copyOf(lengths);
    }

    private static boolean clear(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.getFluidState().isEmpty()
                && state.getCollisionShape(level, pos).isEmpty();
    }

    private static BlockPos local(PoolElementStructurePiece root, int x, int y, int z) {
        return StructureTemplate.transform(new BlockPos(x, y, z), Mirror.NONE,
                root.getRotation(), BlockPos.ZERO).offset(root.getPosition());
    }

    private static BlockPos local(BlockPos root, Rotation rotation, int x, int y, int z) {
        return StructureTemplate.transform(new BlockPos(x, y, z), Mirror.NONE,
                rotation, BlockPos.ZERO).offset(root);
    }

    /** One deliberately staged boat start, followed by actual client movement input. */
    static void runPlayerBoatArrival(ClientGameTestContext context,
            TestSingleplayerContext world, BlockPos root, Rotation rotation,
            List<BoundingBox> buildings) {
        BlockPos startColumn = local(root, rotation, 23, 0, 58);
        BlockPos dockColumn = local(root, rotation, 23, 0, 47);
        BlockPos start = world.getServer().computeOnServer(server ->
                aboveSavedWater(server.overworld(), startColumn));
        BlockPos dockWater = world.getServer().computeOnServer(server ->
                aboveSavedWater(server.overworld(), dockColumn));
        BlockPos ladder = local(root, rotation, 25, 3, 46);
        BlockPos deck = local(root, rotation, 23, 5, 45);
        BlockPos sideExit = local(root, rotation, 24, 5, 46);
        Marks marks = world.getServer().computeOnServer(server ->
                scan(server.overworld(), buildings));
        world.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = world.getConnection().getServerPlayer();
            if (!level.getFluidState(start.below()).is(FluidTags.WATER)
                    || !level.getBlockState(start).getCollisionShape(level, start).isEmpty()) {
                throw new IllegalStateException("Staged boat is not at saved water surface: "
                        + start + " waterBelow=" + level.getFluidState(start.below()));
            }
            Boat boat = EntityTypes.OAK_BOAT.create(level, EntitySpawnReason.EVENT);
            if (boat == null) throw new IllegalStateException("Could not create test boat");
            boat.setPos(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
            if (!level.addFreshEntity(boat)) {
                throw new IllegalStateException("Could not stage boat in natural approach");
            }
            float yaw = yaw(boat.position(), Vec3.atCenterOf(dockWater));
            player.teleportTo(level, start.getX() + 0.5, start.getY() + 0.2,
                    start.getZ() + 0.5, EnumSet.noneOf(Relative.class), yaw, 0, false);
            boat.setYRot(yaw);
            if (!player.startRiding(boat)) {
                throw new IllegalStateException("Player could not board staged boat");
            }
            System.out.println("NOUR_V5_BOAT_STAGE seed=seed-a source=" + start
                    + " dock=" + dockWater + " assist=boat-spawn-and-board");
        });
        context.waitTicks(20);
        world.getConnection().waitForChunksRender();
        world.getServer().runOnServer(server -> {
            ServerPlayer player = world.getConnection().getServerPlayer();
            if (!(player.getVehicle() instanceof Boat boat)
                    || Math.abs(boat.getY() - start.getY()) > 1.0) {
                throw new IllegalStateException("Boat was not floating at staged waterline: "
                        + (player.getVehicle() == null ? "unmounted"
                                : player.getVehicle().position()) + " target=" + start);
            }
        });
        context.takeScreenshot("nour-v5-offshore-player-eye-water-arrival");

        double firstDistance = horizontalDistance(world, dockWater);
        context.getInput().holdKey(options -> options.keyUp);
        try {
            for (int tick = 0; tick < 140; tick += 2) {
                aimAt(context, world, Vec3.atCenterOf(dockWater));
                context.waitTicks(2);
                if (horizontalDistance(world, dockWater) <= 3.5) break;
            }
        } finally {
            context.getInput().releaseKey(options -> options.keyUp);
        }
        double arrivedDistance = horizontalDistance(world, dockWater);
        if (arrivedDistance > 3.5 || firstDistance - arrivedDistance < 5.0) {
            throw new IllegalStateException("Normal-input boat failed to reach dock: start="
                    + firstDistance + " finish=" + arrivedDistance);
        }
        System.out.println("NOUR_V5_BOAT_INPUT startDistance=" + firstDistance
                + " finishDistance=" + arrivedDistance + " status=arrived");
        context.takeScreenshot("nour-v5-offshore-player-eye-dock");

        context.getInput().holdKey(options -> options.keyShift);
        try {
            for (int tick = 0; tick < 20; tick += 2) {
                context.waitTicks(2);
                if (world.getServer().computeOnServer(server ->
                        world.getConnection().getServerPlayer().getVehicle() == null)) break;
            }
        } finally {
            context.getInput().releaseKey(options -> options.keyShift);
        }
        world.getServer().runOnServer(server -> {
            if (world.getConnection().getServerPlayer().getVehicle() != null) {
                throw new IllegalStateException("Normal-input dismount left player in boat");
            }
        });
        System.out.println("NOUR_V5_DOCK_INPUT dismount=client-shift ladder=" + ladder);

        context.getInput().holdKey(options -> options.keyUp);
        context.getInput().holdKey(options -> options.keyJump);
        try {
            for (int tick = 0; tick < 120; tick += 2) {
                aimAt(context, world, Vec3.atCenterOf(ladder));
                context.waitTicks(2);
                boolean atTop = world.getServer().computeOnServer(server -> {
                    ServerPlayer player = world.getConnection().getServerPlayer();
                    return player.getY() >= deck.getY() - 0.25
                            && Math.hypot(player.getX() - ladder.getX() - 0.5,
                                    player.getZ() - ladder.getZ() - 0.5) <= 1.5;
                });
                if (atTop) break;
            }
        } finally {
            context.getInput().releaseKey(options -> options.keyUp);
            context.getInput().releaseKey(options -> options.keyJump);
        }
        world.getServer().runOnServer(server -> {
            ServerPlayer player = world.getConnection().getServerPlayer();
            if (player.getY() < deck.getY() - 0.25) {
                throw new IllegalStateException("Normal-input dock ladder did not climb to exit: "
                        + player.position());
            }
            System.out.println("NOUR_V5_DOCK_INPUT climb=client-forward-jump top="
                    + player.position() + " sideExit=" + sideExit);
        });

        context.getInput().holdKey(options -> options.keyUp);
        try {
            for (int tick = 0; tick < 50; tick += 2) {
                aimAt(context, world, Vec3.atCenterOf(sideExit));
                context.waitTicks(2);
                boolean landedOnDeck = world.getServer().computeOnServer(server -> {
                    ServerPlayer player = world.getConnection().getServerPlayer();
                    return player.onGround() && player.getY() >= deck.getY() - 0.2
                            && player.blockPosition().getX() == sideExit.getX()
                            && player.blockPosition().getZ() == sideExit.getZ();
                });
                if (landedOnDeck) break;
            }
        } finally {
            context.getInput().releaseKey(options -> options.keyUp);
        }
        context.waitTicks(5);
        BlockPos landed = world.getServer().computeOnServer(server -> {
            ServerPlayer player = world.getConnection().getServerPlayer();
            if (!player.onGround() || player.getY() < deck.getY() - 0.2
                    || !player.blockPosition().equals(sideExit)) {
                throw new IllegalStateException("Normal-input dock side exit did not land: "
                        + player.position() + " ground=" + player.onGround());
            }
            System.out.println("NOUR_V5_DOCK_INPUT sideStep=client-forward landed="
                    + player.position());
            return player.blockPosition();
        });
        context.takeScreenshot("nour-v5-offshore-player-eye-deck");

        List<NaturalDistrictRouteProbe.Step> kitRoute = world.getServer().computeOnServer(
                server -> dryRoute(server.overworld(), buildings, landed, marks.barrel()));
        NaturalDistrictRouteProbe.walkPath(context, world, kitRoute,
                "nour_signal_harbor", "dock-to-boat-kit");
        context.waitTicks(5);
        world.getServer().runOnServer(server -> {
            ServerPlayer player = world.getConnection().getServerPlayer();
            if (!player.onGround() || player.blockPosition().distManhattan(marks.barrel()) > 1) {
                throw new IllegalStateException("Normal-input kit walk ended away from barrel: "
                        + player.position());
            }
            System.out.println("NOUR_V5_KIT_INPUT nodes=" + kitRoute.size()
                    + " arrived=" + player.position() + " status=client-walk");
        });
        context.takeScreenshot("nour-v5-offshore-player-eye-boat-kit");

        // Separate staged water-side portraits make each wing legible at player scale.
        // They are visual samples, not a claim that the player piloted to these points.
        waterSidePortrait(context, world, buildings.get(1), rotation,
                "nour-v5-offshore-water-side-boathouse");
        waterSidePortrait(context, world, buildings.get(2), rotation,
                "nour-v5-offshore-water-side-foghouse");
    }

    private static void waterSidePortrait(ClientGameTestContext context,
            TestSingleplayerContext world, BoundingBox building, Rotation rotation,
            String name) {
        world.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = world.getConnection().getServerPlayer();
            Direction sea = rotation.rotate(Direction.SOUTH);
            BlockPos center = building.getCenter();
            BlockPos view = new BlockPos(center.getX() + sea.getStepX() * 30,
                    64, center.getZ() + sea.getStepZ() * 30);
            BlockPos water = view.below();
            if (!level.getFluidState(water).is(FluidTags.WATER)
                    || !level.getBlockState(view).getCollisionShape(level, view).isEmpty()) {
                throw new IllegalStateException("No player-eye offshore water portrait for "
                        + name + " at " + view);
            }
            Boat boat = EntityTypes.OAK_BOAT.create(level, EntitySpawnReason.EVENT);
            if (boat == null) throw new IllegalStateException("Could not stage portrait boat");
            boat.setPos(view.getX() + 0.5, view.getY(), view.getZ() + 0.5);
            if (!level.addFreshEntity(boat)) {
                throw new IllegalStateException("Could not place portrait boat at " + view);
            }
            player.stopRiding();
            Vec3 target = new Vec3(center.getX() + 0.5, building.minY() + 19,
                    center.getZ() + 0.5);
            Vec3 source = boat.position();
            float yaw = yaw(source, target);
            double range = Math.hypot(target.x - source.x, target.z - source.z);
            float pitch = (float) Math.toDegrees(Math.atan2(source.y + 1.6 - target.y,
                    range));
            player.teleportTo(level, view.getX() + 0.5, view.getY() + 0.2,
                    view.getZ() + 0.5, EnumSet.noneOf(Relative.class), yaw, pitch, false);
            boat.setYRot(yaw);
            if (!player.startRiding(boat)) {
                throw new IllegalStateException("Could not board portrait boat at " + view);
            }
            System.out.println("NOUR_V5_WATER_VIEW name=" + name + " camera=" + view
                    + " building=" + building + " assist=boat-spawn-and-board-camera");
        });
        context.waitTicks(25);
        world.getConnection().waitForChunksRender();
        context.takeScreenshot(name);
    }

    private static float yaw(Vec3 source, Vec3 target) {
        return (float) Math.toDegrees(Math.atan2(source.x - target.x,
                target.z - source.z));
    }

    private static BlockPos aboveSavedWater(ServerLevel level, BlockPos column) {
        for (int y = 64; y >= 61; y--) {
            BlockPos water = new BlockPos(column.getX(), y, column.getZ());
            if (!level.getFluidState(water).is(FluidTags.WATER)) continue;
            BlockPos air = water.above();
            if (!level.getBlockState(air).getCollisionShape(level, air).isEmpty()
                    || !level.getFluidState(air).isEmpty()) {
                throw new IllegalStateException("Boat lane has blocked headroom at " + air);
            }
            return air;
        }
        throw new IllegalStateException("No saved boat water at " + column);
    }

    private static void aimAt(ClientGameTestContext context, TestSingleplayerContext world,
            Vec3 target) {
        float targetYaw = world.getServer().computeOnServer(server ->
                yaw(world.getConnection().getServerPlayer().position(), target));
        context.runOnClient(client -> {
            if (client.player != null) client.player.setYRot(targetYaw);
        });
    }

    private static double horizontalDistance(TestSingleplayerContext world, BlockPos target) {
        return world.getServer().computeOnServer(server -> {
            ServerPlayer player = world.getConnection().getServerPlayer();
            Vec3 source = player.getVehicle() != null
                    ? player.getVehicle().position() : player.position();
            return Math.hypot(source.x - target.getX() - 0.5,
                    source.z - target.getZ() - 0.5);
        });
    }

    private static List<NaturalDistrictRouteProbe.Step> dryRoute(ServerLevel level,
            List<BoundingBox> boxes, BlockPos from, BlockPos barrel) {
        Map<BlockPos, Double> dry = new HashMap<>();
        for (BoundingBox box : boxes) {
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
                        if (clear(level, feet) && clear(level, feet.above())) {
                            dry.put(feet, y + height);
                        }
                    }
                }
            }
        }
        if (!dry.containsKey(from)) {
            throw new IllegalStateException("Ladder landed outside dry route cells: " + from);
        }
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        parent.put(from, from);
        queue.add(from);
        BlockPos destination = null;
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            if (current.distManhattan(barrel) == 1) {
                destination = current;
                break;
            }
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos next = current.relative(direction).above(dy);
                    if (!dry.containsKey(next) || parent.containsKey(next)
                            || Math.abs(dry.get(next) - dry.get(current)) > 1.05) continue;
                    parent.put(next, current);
                    queue.addLast(next);
                }
            }
        }
        if (destination == null) {
            throw new IllegalStateException("No dry route from input-climbed deck "
                    + from + " to boat kit " + barrel);
        }
        List<NaturalDistrictRouteProbe.Step> backwards = new ArrayList<>();
        for (BlockPos node = destination; ; node = parent.get(node)) {
            backwards.add(new NaturalDistrictRouteProbe.Step(node, dry.get(node)));
            if (node.equals(from)) break;
        }
        java.util.Collections.reverse(backwards);
        System.out.println("NOUR_V5_KIT_ROUTE start=" + from + " target=" + destination
                + " nodes=" + backwards.size());
        return List.copyOf(backwards);
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world,
                    List<BoundingBox> buildings) {
        Marks marks = world.getServer().computeOnServer(server ->
                scan(server.overworld(), buildings));
        // Screenshots are player-eye captures at checked dry standing cells;
        // reaching these camera tiles is staged. The normal-input shore walk
        // is recorded separately by NaturalDistrictRouteProbe.
        BlockPos gallery = world.getServer().computeOnServer(server ->
                adjacentStand(server.overworld(), marks.ladderTop()));
        camera(context, world, gallery, "nour-v5-lantern-gallery");
        BlockPos kit = world.getServer().computeOnServer(server ->
                adjacentStand(server.overworld(), marks.barrel()));
        camera(context, world, kit, "nour-v5-boat-kit");

        // These are server-side automated uses with the player standing next
        // to each saved control, not unassisted human clicks.
        world.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = world.getConnection().getServerPlayer();
            if (FriendDomainUnlockState.get(server).cleared("nour")) {
                throw new IllegalStateException("Nour was already clear before the natural controls");
            }
            for (BlockPos pos : List.of(marks.boat(), marks.fog(), marks.blue(),
                    marks.amber(), marks.white())) {
                BlockPos stand = adjacentStand(level, pos);
                player.teleportTo(level, stand.getX() + 0.5, stand.getY(),
                        stand.getZ() + 0.5, EnumSet.noneOf(Relative.class), 0, 0, false);
                InteractionResult result = UseBlockCallback.EVENT.invoker().interact(
                        player, level, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                if (result != InteractionResult.SUCCESS_SERVER) {
                    throw new IllegalStateException("Harbor control ignored standing player at "
                            + pos + ": " + result);
                }
            }
            if (!FriendDomainUnlockState.get(server).cleared("nour")) {
                throw new IllegalStateException("Natural boathouse/foghouse/lamp sequence did not clear Nour");
            }
            System.out.println("NOUR_V5_NATURAL_CONTROLS boat=" + marks.boat()
                    + " fog=" + marks.fog() + " blue=" + marks.blue()
                    + " amber=" + marks.amber() + " white=" + marks.white()
                    + " sharedUnlock=true interaction=server-automated-standing");
        });
    }

    private static Marks scan(ServerLevel level, List<BoundingBox> buildings) {
        Map<Block, BlockPos> controls = new HashMap<>();
        Set<Block> wanted = Set.of(DomainBlocks.NOUR_BOAT_CLUE,
                DomainBlocks.NOUR_FOG_CLUE, DomainBlocks.NOUR_BLUE_LAMP,
                DomainBlocks.NOUR_AMBER_LAMP, DomainBlocks.NOUR_WHITE_LAMP);
        BlockPos kit = null;
        Map<Long, Integer> ladderColumns = new HashMap<>();
        Map<Long, BlockPos> ladderTops = new HashMap<>();
        for (BoundingBox box : buildings) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    for (int y = box.minY() + 4; y <= box.maxY(); y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        Block block = level.getBlockState(pos).getBlock();
                        if (wanted.contains(block) && controls.putIfAbsent(block, pos) != null) {
                            throw new IllegalStateException("Duplicate Nour control " + block);
                        }
                        if (block == Blocks.BARREL
                                && level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity stash
                                && stash.getLootTable() != null
                                && stash.getLootTable().identifier().equals(
                                    Identifier.fromNamespaceAndPath("gems", "chests/nour_launch_kit"))) {
                            if (kit != null) throw new IllegalStateException("Duplicate Nour launch kit");
                            kit = pos;
                        }
                        if (block == Blocks.LADDER) {
                            long column = BlockPos.asLong(x, 0, z);
                            ladderColumns.merge(column, 1, Integer::sum);
                            if (!ladderTops.containsKey(column)
                                    || ladderTops.get(column).getY() < y) ladderTops.put(column, pos);
                        }
                    }
                }
            }
        }
        if (!controls.keySet().equals(wanted) || kit == null) {
            throw new IllegalStateException("Natural Nour controls or launch kit missing: "
                    + controls + " kit=" + kit);
        }
        BlockPos ladder = ladderColumns.entrySet().stream()
                .filter(entry -> entry.getValue() >= 28)
                .map(entry -> ladderTops.get(entry.getKey()))
                .findFirst().orElseThrow(() ->
                        new IllegalStateException("No full-height lighthouse ladder loaded"));
        System.out.println("NOUR_V5_NATURAL_ROUTE kit=" + kit + " ladderTop=" + ladder
                + " barrelLoot=nour_launch_kit threePieceFootprints=" + buildings);
        return new Marks(controls.get(DomainBlocks.NOUR_BOAT_CLUE),
                controls.get(DomainBlocks.NOUR_FOG_CLUE),
                controls.get(DomainBlocks.NOUR_BLUE_LAMP),
                controls.get(DomainBlocks.NOUR_AMBER_LAMP),
                controls.get(DomainBlocks.NOUR_WHITE_LAMP), kit, ladder);
    }

    private static BlockPos adjacentStand(ServerLevel level, BlockPos target) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos feet = target.relative(direction);
            if (level.getBlockState(feet.below()).isSolid()
                    && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                    && level.getFluidState(feet).isEmpty()
                    && level.getFluidState(feet.above()).isEmpty()) return feet;
        }
        throw new IllegalStateException("No dry player-height standing cell beside " + target);
    }

    private static void camera(ClientGameTestContext context, TestSingleplayerContext world,
                               BlockPos feet, String name) {
        world.getServer().runOnServer(server -> {
            ServerPlayer player = world.getConnection().getServerPlayer();
            player.teleportTo(server.overworld(), feet.getX() + 0.5, feet.getY(),
                    feet.getZ() + 0.5, EnumSet.noneOf(Relative.class), 0, 0, false);
        });
        context.waitTicks(8);
        world.getConnection().waitForChunksRender();
        context.takeScreenshot(name);
    }
}
