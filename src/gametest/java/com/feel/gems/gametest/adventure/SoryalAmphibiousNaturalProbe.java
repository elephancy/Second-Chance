package com.feel.gems.gametest.adventure;

import java.util.EnumSet;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/** Actual client boat/dismount/walk gate for a natural Soryal water start. */
@SuppressWarnings("UnstableApiUsage")
final class SoryalAmphibiousNaturalProbe {
    private SoryalAmphibiousNaturalProbe() {}

    static boolean hasBoatLane(TestSingleplayerContext world,
            BoundingBox root, Rotation rotation) {
        return world.getServer().computeOnServer(server -> {
            ServerLevel level = server.overworld();
            BlockPos edge = edge(root, rotation);
            Direction outward = rotation.rotate(Direction.SOUTH);
            Direction lateral = rotation.rotate(Direction.EAST);
            for (int depth = 1; depth <= 12; depth++) {
                BlockPos center = edge.relative(outward, depth);
                for (int side = -1; side <= 1; side++) {
                    BlockPos water = center.relative(lateral, side);
                    if (!level.getFluidState(water).is(FluidTags.WATER)
                            || !level.getFluidState(water.below()).is(FluidTags.WATER)
                            || !level.getBlockState(water.above()).getCollisionShape(
                                    level, water.above()).isEmpty()
                            || !level.getBlockState(water.above(2)).getCollisionShape(
                                    level, water.above(2)).isEmpty()) return false;
                }
            }
            return true;
        });
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world,
            BoundingBox root, Rotation rotation, String label) {
        BlockPos edge = edge(root, rotation);
        Direction outward = rotation.rotate(Direction.SOUTH);
        BlockPos start = edge.relative(outward, 12).above();
        BlockPos dock = edge.relative(outward).above();
        BlockPos interior = edge.relative(outward.getOpposite(), 7).above(5);
        if (!hasBoatLane(world, root, rotation)) {
            throw new IllegalStateException("Saved Soryal boat lane is not 12 clear water rows");
        }
        world.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = world.getConnection().getServerPlayer();
            Boat boat = EntityTypes.OAK_BOAT.create(level, EntitySpawnReason.EVENT);
            if (boat == null) throw new IllegalStateException("Could not create Soryal test boat");
            boat.setPos(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
            if (!level.addFreshEntity(boat)) {
                throw new IllegalStateException("Could not stage Soryal boat on saved water");
            }
            float yaw = yaw(boat.position(), Vec3.atCenterOf(dock));
            player.teleportTo(level, start.getX() + 0.5, start.getY() + 0.2,
                    start.getZ() + 0.5, EnumSet.noneOf(Relative.class), yaw, 0, false);
            boat.setYRot(yaw);
            if (!player.startRiding(boat)) {
                throw new IllegalStateException("Player could not board Soryal test boat");
            }
            System.out.println("SORYAL_BOAT_STAGE seed=" + label + " start=" + start
                    + " dock=" + dock + " assist=boat-spawn-and-board");
        });
        context.waitTicks(20);
        world.getConnection().waitForChunksRender();
        context.takeScreenshot("soryal-" + label + "-water-arrival");

        double initial = distance(world, dock);
        context.getInput().holdKey(options -> options.keyUp);
        try {
            for (int tick = 0; tick < 160; tick += 2) {
                aimAt(context, world, Vec3.atCenterOf(dock));
                context.waitTicks(2);
                if (distance(world, dock) <= 2.8) break;
            }
        } finally {
            context.getInput().releaseKey(options -> options.keyUp);
        }
        double arrived = distance(world, dock);
        if (arrived > 2.8 || initial - arrived < 6.0) {
            throw new IllegalStateException("Client boat did not reach Soryal dock: initial="
                    + initial + " arrived=" + arrived);
        }
        System.out.println("SORYAL_BOAT_INPUT seed=" + label + " startDistance="
                + initial + " dockDistance=" + arrived + " status=arrived");
        context.takeScreenshot("soryal-" + label + "-dock");

        context.getInput().holdKey(options -> options.keyShift);
        try {
            for (int tick = 0; tick < 24; tick += 2) {
                context.waitTicks(2);
                if (world.getServer().computeOnServer(server ->
                        world.getConnection().getServerPlayer().getVehicle() == null)) break;
            }
        } finally {
            context.getInput().releaseKey(options -> options.keyShift);
        }
        world.getServer().runOnServer(server -> {
            if (world.getConnection().getServerPlayer().getVehicle() != null) {
                throw new IllegalStateException("Client dismount left Soryal player in boat");
            }
        });

        context.getInput().holdKey(options -> options.keyUp);
        context.getInput().holdKey(options -> options.keyJump);
        try {
            for (int tick = 0; tick < 180; tick += 2) {
                aimAt(context, world, Vec3.atCenterOf(interior));
                context.waitTicks(2);
                if (world.getServer().computeOnServer(server -> {
                    ServerPlayer player = world.getConnection().getServerPlayer();
                    return player.onGround() && player.getY() >= interior.getY() - 0.5
                            && horizontal(player.position(), Vec3.atCenterOf(interior)) <= 2.0;
                })) break;
            }
        } finally {
            context.getInput().releaseKey(options -> options.keyUp);
            context.getInput().releaseKey(options -> options.keyJump);
        }
        world.getServer().runOnServer(server -> {
            ServerPlayer player = world.getConnection().getServerPlayer();
            if (!player.onGround() || player.getY() < interior.getY() - 0.5
                    || horizontal(player.position(), Vec3.atCenterOf(interior)) > 2.0) {
                throw new IllegalStateException("Client dock-to-door climb failed at "
                        + player.position() + " target=" + interior);
            }
            System.out.println("SORYAL_DOCK_TO_DOOR_PASS seed=" + label
                    + " player=" + player.position());
        });
        context.takeScreenshot("soryal-" + label + "-inside-door");

        // Read the actual generated story controls before framing an interior
        // diagnostic. The camera teleport is only for the screenshot; the
        // water-to-door movement above uses normal player input.
        world.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            for (int[] mark : new int[][] {{20, 36, 0}, {23, 36, 1},
                    {26, 36, 2}, {18, 38, 3}}) {
                String expected = switch (mark[2]) {
                    case 0 -> "gems:soryal_copper_mark";
                    case 1 -> "gems:soryal_glass_mark";
                    case 2 -> "gems:soryal_brick_mark";
                    default -> "gems:soryal_yard_fitting";
                };
                BlockPos position = local(root, rotation, mark[0], 5, mark[1]);
                String actual = BuiltInRegistries.BLOCK.getKey(
                        level.getBlockState(position).getBlock()).toString();
                if (!actual.equals(expected)) {
                    throw new IllegalStateException("Soryal saved control at "
                            + position + " is " + actual + " instead of " + expected);
                }
            }
            BlockPos camera = local(root, rotation, 25, 5, 34);
            BlockPos controls = local(root, rotation, 23, 5, 36);
            ServerPlayer player = world.getConnection().getServerPlayer();
            player.teleportTo(level, camera.getX() + 0.5, camera.getY(),
                    camera.getZ() + 0.5, EnumSet.noneOf(Relative.class),
                    yaw(Vec3.atCenterOf(camera), Vec3.atCenterOf(controls)), 8, false);
            System.out.println("SORYAL_STORY_CONTROLS_PASS seed=" + label
                    + " camera=" + camera + " controls=" + controls);
        });
        context.waitTicks(20);
        world.getConnection().waitForChunksRender();
        context.takeScreenshot("soryal-" + label + "-story-controls");
    }

    private static BlockPos local(BoundingBox root, Rotation rotation,
            int x, int y, int z) {
        Direction outward = rotation.rotate(Direction.SOUTH);
        Direction lateral = rotation.rotate(Direction.EAST);
        return edge(root, rotation).relative(lateral, x - 25)
                .relative(outward.getOpposite(), 46 - z).above(y);
    }

    private static BlockPos edge(BoundingBox root, Rotation rotation) {
        Direction outward = rotation.rotate(Direction.SOUTH);
        Direction lateral = rotation.rotate(Direction.EAST);
        return new BlockPos((root.minX() + root.maxX()) / 2,
                root.minY(), (root.minZ() + root.maxZ()) / 2)
                .relative(outward, 23).relative(lateral, 2);
    }

    private static float yaw(Vec3 source, Vec3 target) {
        return (float) Math.toDegrees(Math.atan2(source.x - target.x,
                target.z - source.z));
    }

    private static double horizontal(Vec3 source, Vec3 target) {
        return Math.hypot(source.x - target.x, source.z - target.z);
    }

    private static double distance(TestSingleplayerContext world, BlockPos target) {
        return world.getServer().computeOnServer(server -> {
            ServerPlayer player = world.getConnection().getServerPlayer();
            Vec3 source = player.getVehicle() != null
                    ? player.getVehicle().position() : player.position();
            return horizontal(source, Vec3.atCenterOf(target));
        });
    }

    private static void aimAt(ClientGameTestContext context,
            TestSingleplayerContext world, Vec3 target) {
        float targetYaw = world.getServer().computeOnServer(server ->
                yaw(world.getConnection().getServerPlayer().position(), target));
        context.runOnClient(client -> {
            if (client.player != null) client.player.setYRot(targetYaw);
        });
    }
}
