package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.satellite.SatelliteLoadedAudit;
import com.feel.gems.adventure.satellite.SatelliteRuntime;
import java.util.EnumSet;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Focused normal-input entry and exit in disposable natural satellite worlds. */
@SuppressWarnings("UnstableApiUsage")
final class SatellitePlayerRouteProbe {
    private SatellitePlayerRouteProbe() { }

    static void run(ClientGameTestContext context, TestSingleplayerContext world,
                    BoundingBox box, BlockPos root, BlockPos entry, BlockPos exit,
                    boolean ocean) {
        String name = ocean ? "ocean" : "archive";
        world.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            level.clockManager().setTotalTicks(level.registryAccess()
                    .lookupOrThrow(Registries.WORLD_CLOCK)
                    .getOrThrow(WorldClocks.OVERWORLD), 1000L);
            inspectRoute(level, root, entry, exit, ocean, "day");
            world.getConnection().getServerPlayer().setGameMode(GameType.SURVIVAL);
        });
        if (ocean) approachByBoat(context, world, box, root, entry);
        else approachOnFoot(context, world, root, entry, false);
        waitForDryLanding(context, world);
        requireDryPlayer(world, name + " entry approach");
        context.takeScreenshot("satellite-" + name + "-player-approach-day");
        useFromClient(context, world, entry);
        BlockPos chamber = ocean ? root.below(13) : root.below(22);
        if (!ocean) waitForArchiveEntry(context, world, chamber);
        expectAt(world, chamber, name + " entry");
        context.takeScreenshot("satellite-" + name + "-dry-room-day");
        moveToward(context, world, exit.above(), 3.4, 120, false);
        context.waitTicks(5);
        requireDryPlayer(world, name + " return approach");
        useFromClient(context, world, exit);
        BlockPos surface = root.above(ocean ? 19 : 22);
        expectAt(world, surface, name + " return");
        world.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            level.clockManager().setTotalTicks(level.registryAccess()
                    .lookupOrThrow(Registries.WORLD_CLOCK)
                    .getOrThrow(WorldClocks.OVERWORLD), 13000L);
        });
        context.waitTicks(8);
        world.getServer().runOnServer(server ->
                inspectRoute(server.overworld(), root, entry, exit, ocean, "night"));
        context.takeScreenshot("satellite-" + name + "-player-return-night");
        System.out.println("SATELLITE_PLAYER_ROUTE_PASS site=" + name + " root=" + root
                + " entry=" + entry + " exit=" + exit
                + " movement=client-forward use=client-packet dayNight=dry-and-lit");
    }

    /** A copied-save arrival probe with ordinary chunk tickets and no halo preload. */
    static void runArchiveLowView(ClientGameTestContext context,
                                  TestSingleplayerContext world, BoundingBox box,
                                  BlockPos root, BlockPos clue) {
        world.getServer().runOnServer(server ->
                world.getConnection().getServerPlayer().setGameMode(GameType.SURVIVAL));
        approachOnFoot(context, world, root, clue, true);
        waitForDryLanding(context, world);
        requireDryPlayer(world, "archive low-view approach");
        context.takeScreenshot("satellite-archive-low-view-approach");
        SatelliteLoadedAudit.Verdict before = archiveLowViewVerdict(world, root);
        require(before == SatelliteLoadedAudit.Verdict.CHUNK_UNAVAILABLE,
                "Low-view archive did not reproduce the missing-halo condition: " + before);
        useFromClient(context, world, clue);
        waitForArchiveEntry(context, world, root.below(22));
        boolean readyAndIdle = world.getServer().computeOnServer(server ->
                SatelliteRuntime.archiveSafetyReady(root)
                        && SatelliteRuntime.archiveSurveyIdle());
        require(readyAndIdle, "Archive entered without a READY audit and released survey");
        SatelliteLoadedAudit.Verdict after = archiveLowViewVerdict(world, root);
        int[] halo = world.getServer().computeOnServer(server -> {
            ServerLevel level = server.overworld();
            require(server.getPlayerList().getViewDistance() == 6,
                    "Archive arrival did not retain six-chunk server view distance");
            int loaded = 0;
            int total = 0;
            for (int cx = (root.getX() - 128) >> 4;
                 cx <= (root.getX() + 128) >> 4; cx++) {
                for (int cz = (root.getZ() - 128) >> 4;
                     cz <= (root.getZ() + 128) >> 4; cz++) {
                    total++;
                    if (level.getChunkSource().getChunkNow(cx, cz) != null) loaded++;
                }
            }
            return new int[]{loaded, total};
        });
        System.out.println("SATELLITE_ARCHIVE_LOW_VIEW_READY root=" + root + " box=" + box
                + " before=" + before + " after=" + after
                + " loadedHaloChunks=" + halo[0] + "/" + halo[1]
                + " viewDistance=6 clientWalkAndUse=true surveyReleased=true");
    }

    private static void waitForArchiveEntry(ClientGameTestContext context,
                                            TestSingleplayerContext world, BlockPos chamber) {
        for (int elapsed = 0; elapsed < 20 * 90; elapsed += 10) {
            context.waitTicks(10);
            boolean entered = world.getServer().computeOnServer(server ->
                    world.getConnection().getServerPlayer().blockPosition().equals(chamber));
            if (entered) return;
        }
        throw new IllegalStateException("Archive clue never completed its safe survey and entry");
    }

    private static SatelliteLoadedAudit.Verdict archiveLowViewVerdict(
            TestSingleplayerContext world, BlockPos root) {
        return world.getServer().computeOnServer(server -> {
            ServerLevel level = server.overworld();
            Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                    .getValue(ResourceKey.create(Registries.STRUCTURE,
                            Identifier.fromNamespaceAndPath("gems", "branch_underarchive_v1")));
            require(structure != null, "Saved archive structure is missing");
            StructureStart start = level.structureManager().getStructureWithPieceAt(
                    root.getX(), root.getY(), root.getZ(), structure);
            require(start != null && start.isValid(), "Saved archive start is unavailable");
            return SatelliteLoadedAudit.inspectLoaded(level, start,
                    SatelliteLoadedAudit.Site.ARCHIVE);
        });
    }

    private static void approachByBoat(ClientGameTestContext context,
                                       TestSingleplayerContext world,
                                       BoundingBox box, BlockPos root, BlockPos entry) {
        int stepX = Integer.signum(entry.getX() - root.getX());
        int stepZ = Integer.signum(entry.getZ() - root.getZ());
        require(Math.abs(stepX) + Math.abs(stepZ) == 1,
                "Ocean buoy is not on a cardinal boat lane: " + entry);
        // The original disposable boat remains at the center berth in a
        // copied save. Its neighbor is another authored boarding lane, two
        // blocks across the same five-wide dock entrance.
        int side = Boolean.getBoolean("gems.satelliteRestartOnly") ? 2 : 0;
        int sideX = stepZ * side;
        int sideZ = -stepX * side;
        // The surveyed sea surface is water through Y62 and air at Y63,
        // independent of whether the relay foot rests at Y26 or Y27.
        BlockPos offshore = new BlockPos(root.getX() + stepX * 30 + sideX,
                63, root.getZ() + stepZ * 30 + sideZ);
        BlockPos berth = new BlockPos(root.getX() + stepX * 14 + sideX,
                offshore.getY(), root.getZ() + stepZ * 14 + sideZ);
        world.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            level.getChunk(offshore.getX() >> 4, offshore.getZ() >> 4);
            require(level.getFluidState(offshore.below()).is(FluidTags.WATER)
                            && level.getBlockState(offshore).getCollisionShape(level, offshore).isEmpty()
                            && level.getFluidState(offshore).isEmpty(),
                    "Natural offshore boat staging column is obstructed: " + offshore);
            Boat boat = EntityTypes.OAK_BOAT.create(level, EntitySpawnReason.EVENT);
            require(boat != null, "Could not create disposable test boat");
            boat.setPos(offshore.getX() + 0.5, offshore.getY(), offshore.getZ() + 0.5);
            // B approaches from the south at yaw 180. Set the boat's facing
            // before its spawn packet; a later server-only turn can leave
            // the client's mounted boat facing the opposite way.
            float yaw = yaw(offshore.getX() + 0.5, offshore.getZ() + 0.5, berth);
            boat.setYRot(yaw);
            require(level.addFreshEntity(boat), "Could not stage boat in saved ocean");
            ServerPlayer player = world.getConnection().getServerPlayer();
            player.teleportTo(level, offshore.getX() + 0.5, offshore.getY() + 0.2,
                    offshore.getZ() + 0.5, EnumSet.noneOf(Relative.class), yaw, 0F, false);
            require(player.startRiding(boat), "Player could not board offshore test boat");
        });
        context.waitTicks(15);
        world.getConnection().waitForChunksRender();
        // Stop near the low boarding step. On a copied restart, the first
        // run's boat can remain moored at the berth, one boat-width ahead.
        moveToward(context, world, berth, 1.7, 160, false);
        world.getServer().runOnServer(server ->
                require(world.getConnection().getServerPlayer().getVehicle() instanceof Boat,
                        "Boat approach lost its vehicle before reaching the dock"));
        context.getInput().holdKey(options -> options.keyShift);
        try {
            for (int tick = 0; tick < 30; tick += 2) {
                context.waitTicks(2);
                if (world.getServer().computeOnServer(server ->
                        world.getConnection().getServerPlayer().getVehicle() == null)) break;
            }
        } finally {
            context.getInput().releaseKey(options -> options.keyShift);
        }
        world.getServer().runOnServer(server ->
                require(world.getConnection().getServerPlayer().getVehicle() == null,
                        "Normal-input boat dismount failed"));
        // A broad horizontal reach can stop the swimmer underneath the high
        // dock, before the lower boarding step has been climbed.
        moveToward(context, world, entry.above(), 0.9, 160, true);
    }

    private static void approachOnFoot(ClientGameTestContext context,
                                       TestSingleplayerContext world,
                                       BlockPos root, BlockPos entry,
                                       boolean localChunksOnly) {
        int stepX = Integer.signum(entry.getX() - root.getX());
        int stepZ = Integer.signum(entry.getZ() - root.getZ());
        require(Math.abs(stepX) + Math.abs(stepZ) == 1,
                "Archive clue is not on a cardinal surface approach: " + entry);
        ArchiveApproach route = world.getServer().computeOnServer(server -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = world.getConnection().getServerPlayer();
            for (int lateral : new int[]{0, -4, 4}) {
                ArchiveApproach candidate = archiveApproach(level, root, entry,
                        stepX, stepZ, lateral);
                if (candidate == null) continue;
                BlockPos start = candidate.start();
                player.teleportTo(level, start.getX() + 0.5, start.getY(),
                        start.getZ() + 0.5, EnumSet.noneOf(Relative.class),
                        yaw(start.getX() + 0.5, start.getZ() + 0.5, candidate.turn()),
                        0F, false);
                return candidate;
            }
            throw new IllegalStateException("No dry, walkable natural taiga approach to "
                    + entry + " from outward 14");
        });
        context.waitTicks(8);
        if (localChunksOnly) {
            // The missing-halo probe must not wait for the entire view ring to
            // render: doing so generates the halo it is meant to test.
            boolean ready = false;
            for (int ticks = 0; ticks < 600; ticks += 2) {
                ready = context.computeOnClient(client -> {
                    if (client.level == null || client.player == null) return false;
                    BlockPos feet = client.player.blockPosition();
                    return feet.getX() == route.start().getX()
                            && feet.getZ() == route.start().getZ()
                            && client.level.hasChunkAt(feet)
                            && client.level.hasChunkAt(entry);
                });
                if (ready) break;
                context.waitTicks(2);
            }
            require(ready, "Low-view archive local approach chunks never reached the client");
        } else {
            world.getConnection().waitForChunksRender();
        }
        moveArchiveToward(context, world, route.turn(), 160);
        if (route.lateral() != 0) {
            moveArchiveToward(context, world, route.platform(), 80);
            moveArchiveToward(context, world, route.center(), 80);
        }
        System.out.println("SATELLITE_ARCHIVE_APPROACH lane=" + route.lateral()
                + " start=" + route.start() + " turn=" + route.turn()
                + " center=" + route.center() + " normalInput=true");
    }

    private record ArchiveApproach(int lateral, BlockPos start, BlockPos turn,
                                   BlockPos platform, BlockPos center) { }

    private static ArchiveApproach archiveApproach(ServerLevel level, BlockPos root,
                                                    BlockPos entry, int stepX,
                                                    int stepZ, int lateral) {
        int platformY = entry.getY() + 1;
        BlockPos center = archivePoint(root, stepX, stepZ, 0, 7, platformY);
        if (!dryStanding(level, entry.above())) return null;
        for (int outward = 7; outward <= 11; outward++) {
            if (!dryStanding(level, archivePoint(root, stepX, stepZ,
                    0, outward, platformY))) return null;
        }
        if (lateral == 0) {
            int previousY = platformY;
            BlockPos start = null;
            for (int outward = 12; outward <= 14; outward++) {
                BlockPos feet = naturalArchiveStanding(level,
                        archivePoint(root, stepX, stepZ, 0, outward, platformY));
                if (feet == null || Math.abs(feet.getY() - previousY) > 1
                        || outward == 14 && Math.abs(feet.getY() - platformY) > 2)
                    return null;
                previousY = feet.getY();
                start = feet;
            }
            return new ArchiveApproach(0, start, center, center, center);
        }
        BlockPos start = null;
        BlockPos turn = null;
        int previousY = Integer.MIN_VALUE;
        for (int outward = 14; outward >= 7; outward--) {
            BlockPos feet = naturalArchiveStanding(level,
                    archivePoint(root, stepX, stepZ, lateral, outward, platformY));
            if (feet == null || previousY != Integer.MIN_VALUE
                    && Math.abs(feet.getY() - previousY) > 1) return null;
            if (outward == 14) start = feet;
            if (outward == 7) turn = feet;
            previousY = feet.getY();
        }
        int sign = Integer.signum(lateral);
        BlockPos shoulder = naturalArchiveStanding(level,
                archivePoint(root, stepX, stepZ, sign * 3, 7, platformY));
        BlockPos platform = archivePoint(root, stepX, stepZ,
                sign * 2, 7, platformY);
        if (shoulder == null || Math.abs(shoulder.getY() - previousY) > 1
                || Math.abs(shoulder.getY() - platformY) > 1) return null;
        for (int side = sign * 2; side != 0; side -= sign) {
            if (!dryStanding(level, archivePoint(root, stepX, stepZ,
                    side, 7, platformY))) return null;
        }
        return new ArchiveApproach(lateral, start, turn, platform, center);
    }

    private static BlockPos archivePoint(BlockPos root, int stepX, int stepZ,
                                         int lateral, int outward, int y) {
        return new BlockPos(root.getX() + stepX * outward + stepZ * lateral,
                y, root.getZ() + stepZ * outward - stepX * lateral);
    }

    private static BlockPos naturalArchiveStanding(ServerLevel level, BlockPos column) {
        int x = column.getX();
        int z = column.getZ();
        level.getChunk(x >> 4, z >> 4);
        BlockPos feet = new BlockPos(x,
                level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        return level.getWorldBorder().isWithinBounds(feet) && dryStanding(level, feet)
                ? feet : null;
    }

    private static void moveArchiveToward(ClientGameTestContext context,
                                          TestSingleplayerContext world,
                                          BlockPos target, int maxTicks) {
        moveToward(context, world, target, 0.65, maxTicks, true, 0.25);
    }

    private static void moveToward(ClientGameTestContext context,
                                   TestSingleplayerContext world, BlockPos target,
                                   double reach, int maxTicks, boolean jump) {
        moveToward(context, world, target, reach, maxTicks, jump, 1.0);
    }

    private static void moveToward(ClientGameTestContext context,
                                   TestSingleplayerContext world, BlockPos target,
                                   double reach, int maxTicks, boolean jump,
                                   double minimumProgress) {
        double before = distance(world, target);
        require(before > reach, "Movement target was already within reach: " + target);
        context.getInput().holdKey(options -> options.keyUp);
        if (jump) context.getInput().holdKey(options -> options.keyJump);
        try {
            for (int tick = 0; tick < maxTicks; tick += 2) {
                aimAt(context, world, target);
                context.waitTicks(2);
                if (distance(world, target) <= reach) break;
            }
        } finally {
            context.getInput().releaseKey(options -> options.keyUp);
            if (jump) context.getInput().releaseKey(options -> options.keyJump);
        }
        double after = distance(world, target);
        require(after <= reach && before - after > minimumProgress,
                "Normal-input movement missed " + target + ": before=" + before
                        + " after=" + after);
        System.out.println("SATELLITE_PLAYER_INPUT target=" + target + " before="
                + before + " after=" + after + " jump=" + jump);
    }

    private static double distance(TestSingleplayerContext world, BlockPos target) {
        return world.getServer().computeOnServer(server -> {
            ServerPlayer player = world.getConnection().getServerPlayer();
            Vec3 source = player.getVehicle() == null
                    ? player.position() : player.getVehicle().position();
            return Math.hypot(source.x - target.getX() - 0.5,
                    source.z - target.getZ() - 0.5);
        });
    }

    private static void aimAt(ClientGameTestContext context, TestSingleplayerContext world,
                              BlockPos target) {
        float angle = world.getServer().computeOnServer(server -> {
            ServerPlayer player = world.getConnection().getServerPlayer();
            Vec3 source = player.getVehicle() == null
                    ? player.position() : player.getVehicle().position();
            return yaw(source.x, source.z, target);
        });
        context.runOnClient(client -> {
            if (client.player != null) client.player.setYRot(angle);
        });
    }

    private static float yaw(double x, double z, BlockPos target) {
        return (float) Math.toDegrees(Math.atan2(x - target.getX() - 0.5,
                target.getZ() + 0.5 - z));
    }

    private static void useFromClient(ClientGameTestContext context,
                                      TestSingleplayerContext world, BlockPos control) {
        context.runOnClient(client -> {
            require(client.player != null && client.gameMode != null
                            && client.player.distanceToSqr(Vec3.atCenterOf(control)) < 25,
                    "Client cannot reach satellite control " + control);
            client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(control), Direction.UP, control, false));
        });
        context.waitTicks(12);
    }

    private static void expectAt(TestSingleplayerContext world, BlockPos expected,
                                 String stage) {
        world.getServer().runOnServer(server -> {
            ServerPlayer player = world.getConnection().getServerPlayer();
            require(player.blockPosition().equals(expected)
                            && player.isAlive() && player.getAirSupply() > 0,
                    "Client-use " + stage + " did not reach safe landing: "
                            + player.position() + " expected=" + expected);
        });
    }

    private static void requireDryPlayer(TestSingleplayerContext world, String stage) {
        world.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = world.getConnection().getServerPlayer();
            require(player.isAlive() && player.onGround()
                            && dryStanding(level, player.blockPosition())
                            && player.getAirSupply() > 0,
                    "Normal-input " + stage + " did not reach dry standing room: "
                            + player.position());
        });
    }

    private static void waitForDryLanding(ClientGameTestContext context,
                                          TestSingleplayerContext world) {
        // The final client jump can finish at a different tick in each saved
        // world. Let the player land before asserting the dry route.
        for (int tick = 0; tick < 40; tick += 2) {
            if (world.getServer().computeOnServer(server -> {
                ServerLevel level = server.overworld();
                ServerPlayer player = world.getConnection().getServerPlayer();
                return player.isAlive() && player.onGround()
                        && dryStanding(level, player.blockPosition());
            })) return;
            context.waitTicks(2);
        }
    }

    private static void inspectRoute(ServerLevel level, BlockPos root, BlockPos entry,
                                     BlockPos exit, boolean ocean, String phase) {
        BlockPos chamber = root.below(ocean ? 13 : 22);
        BlockPos surface = root.above(ocean ? 19 : 22);
        int minimum = 15;
        for (BlockPos feet : new BlockPos[]{surface, entry.above(), chamber, exit.above()}) {
            require(dryStanding(level, feet),
                    phase + " satellite route is flooded or blocked at " + feet);
            int blockLight = level.getBrightness(LightLayer.BLOCK, feet);
            minimum = Math.min(minimum, blockLight);
            require(blockLight > 0,
                    phase + " satellite route has a zero-block-light hostile spawn cell at "
                            + feet);
        }
        System.out.println("SATELLITE_ROUTE_SAFETY site=" + (ocean ? "ocean" : "archive")
                + " phase=" + phase + " minBlockLight=" + minimum
                + " dryAir=true hostileSurfaceLightZero=false");
    }

    private static boolean dryStanding(ServerLevel level, BlockPos feet) {
        return !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && level.getFluidState(feet).isEmpty()
                && level.getFluidState(feet.above()).isEmpty();
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
    }
}
