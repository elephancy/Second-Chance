package com.feel.gems.gametest.adventure;

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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A normal-input route gate for the newly versioned district approaches. */
@SuppressWarnings("UnstableApiUsage")
final class NaturalDistrictRouteProbe {
    record Gate(String name, int buildingIndex, Direction inward, int bankDistance,
                int interiorDepth, int halfWidth, int lateralOffset, int sourceHalfWidth) {
        Gate(String name, int buildingIndex, Direction inward, int bankDistance,
             int interiorDepth, int halfWidth) {
            this(name, buildingIndex, inward, bankDistance, interiorDepth,
                    halfWidth, 0, halfWidth);
        }
    }

    record Step(BlockPos feet, double standingY) {}
    private record Route(List<Step> steps, BoundingBox building) {}
    record WalkApproach(List<Step> steps, Step entry, BoundingBox building, Gate gate) {}
    private record Motion(long serverTick, int groundedStep, int nearStep,
                          String diagnostic) {}
    private record Landing(boolean valid, String diagnostic) {}

    private NaturalDistrictRouteProbe() {}

    static List<Gate> gatesFor(String target, Rotation rootRotation) {
        return gatesFor(target, rootRotation, false);
    }

    static List<Gate> gatesFor(String target, Rotation rootRotation,
                               boolean hushwireProcession) {
        return switch (target) {
            case "second_chance_town" -> List.of(
                    new Gate("market-north", 3, Direction.SOUTH, 2, 6, 4),
                    new Gate("guesthouse-east", 4, Direction.WEST, 2, 6, 4));
            case "lastlight_exchange" -> List.of(
                    new Gate("street-north", 1, Direction.SOUTH, 2, 12, 4),
                    new Gate("market-shore-west", 2, Direction.EAST, 14, 5, 4));
            case "rehearsal_works" -> List.of(
                    new Gate("stage-north", 0, Direction.SOUTH, 12, 6, 4));
            case "branch_vault" -> List.of(
                    new Gate("vault-south-wayfinder", 0, Direction.NORTH, 20, 6,
                            4, -2, 2));
            case "hushwire" -> List.of(
                    new Gate("hall-entry", 0,
                            rootRotation.rotate(Direction.NORTH),
                            hushwireProcession ? 28 : 2, 6, 4));
            case "unwritten_court" -> List.of(
                    new Gate("court-north", 0, Direction.SOUTH, 2, 6, 4));
            case "khaled_floodgate" -> List.of(
                    new Gate("clinic-north", 2, Direction.SOUTH, 15, 5, 4));
            case "nour_signal_harbor" -> List.of();
            case "fam_forum" -> List.of(
                    new Gate("forum-east", 0, Direction.WEST, 2, 6, 4));
            case "adl_survey_house" -> List.of(
                    new Gate("survey-east", 0, Direction.WEST, 2, 6, 4));
            case "othman_library" -> List.of(
                    new Gate("library-south", 0, Direction.NORTH, 2, 13,
                            24, -18, 2));
            case "inkbound_workshop" -> List.of(
                    new Gate("cherry-lane-outward", 0,
                            rootRotation.rotate(Direction.SOUTH).getOpposite(), 36, 9,
                            4, 0, 2));
            case "yehia_story_lodge" -> List.of(
                    new Gate("lodge-west", 0, Direction.EAST, 34, 9, 4));
            case "omar_lost_quarter" -> List.of(
                    new Gate("wadi-entrance", 0,
                            rootRotation.rotate(Direction.NORTH), 14, 5, 5));
            case "soryal_repair_yard" -> List.of(
                    new Gate("yard-entry", 0,
                            rootRotation.rotate(Direction.NORTH), 1, 6, 4));
            case "ali_splitstone_cloister" -> List.of(
                    new Gate("cloister-north", 0,
                            rootRotation.rotate(Direction.SOUTH), 16, 6, 4));
            default -> List.of();
        };
    }

    static WalkApproach walk(ClientGameTestContext context, TestSingleplayerContext world,
            String target, List<BoundingBox> buildings, List<BoundingBox> allPieces,
            Gate gate) {
        if (gate.buildingIndex() >= buildings.size()) {
            throw new IllegalStateException("Expected building index " + gate.buildingIndex()
                    + " for " + target + " but only " + buildings.size() + " were found");
        }
        if (target.equals("ali_splitstone_cloister")
                || target.equals("yehia_story_lodge")
                || target.equals("omar_lost_quarter")
                || target.equals("inkbound_workshop")) {
            world.getServer().runOnServer(server ->
                    loadGuardedRouteChunks(server.overworld(), buildings.get(gate.buildingIndex()), gate));
            context.waitTicks(8);
        }
        Route route = world.getServer().computeOnServer(server ->
                findRoute(server.overworld(), buildings.get(gate.buildingIndex()),
                        allPieces, gate));
        List<Step> steps = route.steps();
        Step start = steps.getFirst();
        Step end = steps.getLast();
        System.out.println("NATURAL_DISTRICT_WALK_ROUTE target=" + target + " gate="
                + gate.name() + " exterior=" + start.feet() + " interior="
                + end.feet() + " nodes=" + steps.size() + " rise="
                + String.format(java.util.Locale.ROOT, "%.2f",
                        end.standingY() - start.standingY()) + " building="
                + route.building());

        int entryX = end.feet().getX() - start.feet().getX();
        int entryZ = end.feet().getZ() - start.feet().getZ();
        float entryYaw = (float) Math.toDegrees(Math.atan2(-entryX, entryZ));
        world.getServer().runOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            player.setNoGravity(false);
            player.teleportTo(server.overworld(), start.feet().getX() + 0.5,
                    start.standingY(), start.feet().getZ() + 0.5,
                    EnumSet.noneOf(Relative.class), entryYaw, 0.0F, false);
        });
        context.waitTicks(5);
        world.getConnection().waitForChunksRender();
        context.takeScreenshot("natural-" + target + "-" + gate.name() + "-entry");
        walkPath(context, world, steps, target, gate.name());
        context.waitTicks(4);
        world.getServer().runOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            if (!player.isAlive() || !player.onGround()
                    || !inside(end.feet(), route.building(), gate.interiorDepth())) {
                throw new IllegalStateException("Walk did not finish at the intended interior: "
                        + player.position() + " target=" + end.feet());
            }
            double x = player.getX() - end.feet().getX() - 0.5;
            double z = player.getZ() - end.feet().getZ() - 0.5;
            if (x * x + z * z > 0.5) {
                throw new IllegalStateException("Player drifted from interior gate: "
                        + player.position() + " target=" + end.feet());
            }
            System.out.println("NATURAL_DISTRICT_WALK_PASS target=" + target + " gate="
                    + gate.name() + " player=" + player.position() + " nodes="
                    + steps.size());
        });
        Step entry = steps.stream().filter(step ->
                step.feet().getX() >= route.building().minX()
                        && step.feet().getX() <= route.building().maxX()
                        && step.feet().getZ() >= route.building().minZ()
                        && step.feet().getZ() <= route.building().maxZ())
                .findFirst().orElseThrow();
        return new WalkApproach(steps, entry, route.building(), gate);
    }

    /** Traverse a generated district from the player's current room to another authored piece. */
    static WalkApproach walkBetween(ClientGameTestContext context, TestSingleplayerContext world,
                                    String target, String routeName,
                                    List<BoundingBox> pieces, BoundingBox destination) {
        Route route = world.getServer().computeOnServer(server ->
                findBetween(server.overworld(), world.getConnection().getServerPlayer(),
                        pieces, destination));
        List<Step> steps = route.steps();
        System.out.println("GROUPED_DISTRICT_INTRA_ROUTE target=" + target
                + " route=" + routeName + " from=" + steps.getFirst().feet()
                + " to=" + steps.getLast().feet() + " nodes=" + steps.size());
        walkPath(context, world, steps, target, routeName);
        // The final step was grounded during input traversal. Let the released
        // input settle before judging the landing; an actual gap will not.
        int consecutiveGrounded = 0;
        String lastLanding = "no landing sample";
        for (int poll = 0; poll < 12 && consecutiveGrounded < 2; poll++) {
            context.waitTicks(2);
            Landing landing = world.getServer().computeOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                BlockPos below = BlockPos.containing(player.getX(),
                        player.getY() - 0.6, player.getZ());
                boolean valid = player.isAlive() && player.onGround()
                        && inside(player.blockPosition(), destination, 2);
                return new Landing(valid, "player=" + player.position()
                        + " alive=" + player.isAlive()
                        + " onGround=" + player.onGround()
                        + " below=" + below + ":"
                        + server.overworld().getBlockState(below).getBlock());
            });
            lastLanding = landing.diagnostic();
            consecutiveGrounded = landing.valid() ? consecutiveGrounded + 1 : 0;
        }
        if (consecutiveGrounded < 2) {
            throw new IllegalStateException("Intra-district route missed "
                    + routeName + " after 24 settling ticks: " + lastLanding
                    + " destination=" + destination);
        }
        world.getServer().runOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            if (!player.isAlive()
                    || !inside(player.blockPosition(), destination, 2)) {
                throw new IllegalStateException("Intra-district route missed "
                        + routeName + ": player=" + player.position()
                        + " destination=" + destination);
            }
            System.out.println("GROUPED_DISTRICT_INTRA_PASS target=" + target
                    + " route=" + routeName + " nodes=" + steps.size());
        });
        Gate synthetic = new Gate(routeName, 0, Direction.NORTH, 0, 2, 4);
        return new WalkApproach(steps, steps.getLast(), destination, synthetic);
    }

    private static Route findBetween(ServerLevel level, ServerPlayer player,
                                     List<BoundingBox> pieces, BoundingBox destination) {
        BlockPos playerFeet = player.blockPosition();
        Step start = null;
        for (int delta = -2; delta <= 1 && start == null; delta++) {
            Step candidate = standing(level, playerFeet.below().offset(0, delta, 0));
            if (candidate != null && candidate.feet().getX() == playerFeet.getX()
                    && candidate.feet().getZ() == playerFeet.getZ()
                    && Math.abs(candidate.standingY() - player.getY()) < 1.3) {
                start = candidate;
            }
        }
        if (start == null) throw new IllegalStateException(
                "Player lacks a standing route source at " + player.position());
        int minX = pieces.stream().mapToInt(BoundingBox::minX).min().orElseThrow() - 8;
        int maxX = pieces.stream().mapToInt(BoundingBox::maxX).max().orElseThrow() + 8;
        int minZ = pieces.stream().mapToInt(BoundingBox::minZ).min().orElseThrow() - 8;
        int maxZ = pieces.stream().mapToInt(BoundingBox::maxZ).max().orElseThrow() + 8;
        int minY = pieces.stream().mapToInt(BoundingBox::minY).min().orElseThrow() - 5;
        int maxY = pieces.stream().mapToInt(BoundingBox::maxY).max().orElseThrow() - 2;
        ArrayDeque<Step> frontier = new ArrayDeque<>();
        Map<BlockPos, BlockPos> previous = new HashMap<>();
        Map<BlockPos, Step> visited = new HashMap<>();
        frontier.add(start);
        previous.put(start.feet(), start.feet());
        visited.put(start.feet(), start);
        while (!frontier.isEmpty() && visited.size() < 120_000) {
            Step current = frontier.removeFirst();
            if (inside(current.feet(), destination, 2)
                    && current.standingY() >= destination.minY() + 2.5) {
                List<Step> reverse = new ArrayList<>();
                BlockPos cursor = current.feet();
                while (true) {
                    reverse.add(visited.get(cursor));
                    BlockPos parent = previous.get(cursor);
                    if (parent.equals(cursor)) break;
                    cursor = parent;
                }
                java.util.Collections.reverse(reverse);
                return new Route(List.copyOf(reverse), destination);
            }
            for (Direction direction : new Direction[]{Direction.NORTH, Direction.SOUTH,
                    Direction.EAST, Direction.WEST}) {
                int x = current.feet().getX() + direction.getStepX();
                int z = current.feet().getZ() + direction.getStepZ();
                if (x < minX || x > maxX || z < minZ || z > maxZ) continue;
                level.getChunk(x >> 4, z >> 4);
                for (int floorY = current.feet().getY() - 2;
                        floorY <= current.feet().getY(); floorY++) {
                    if (floorY < minY || floorY > maxY) continue;
                    Step next = standing(level, new BlockPos(x, floorY, z));
                    if (next == null || previous.containsKey(next.feet())
                            || Math.abs(next.standingY() - current.standingY()) > 1.05
                            || !hasJumpHeadroom(level, current, next)) continue;
                    previous.put(next.feet(), current.feet());
                    visited.put(next.feet(), next);
                    frontier.addLast(next);
                }
            }
        }
        throw new IllegalStateException("No continuous player-height route from "
                + start.feet() + " to " + destination
                + " within district pieces; explored=" + visited.size());
    }

    /** Continue a proven player-height route with real client movement input. */
    static void walkPath(ClientGameTestContext context, TestSingleplayerContext world,
            List<Step> steps, String target, String routeName) {
        for (int index = 1; index < steps.size();) {
            int requestedStep = index;
            Step next = steps.get(index);
            Step previous = steps.get(index - 1);
            aimAt(context, world, next);
            boolean jump = next.standingY() > previous.standingY() + 0.55;
            long startedTick = world.getServer().computeOnServer(server ->
                    server.overworld().getGameTime());
            long wallDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
            long lastAimTick = startedTick;
            long settlingSince = -1;
            boolean moving = true;
            int advanced = -1;
            Motion lastMotion = null;
            holdMovement(context, jump);
            try {
                while (System.nanoTime() < wallDeadline) {
                    context.waitTicks(2);
                    lastMotion = world.getServer().computeOnServer(server ->
                            motion(server.overworld(),
                                    world.getConnection().getServerPlayer(), steps, requestedStep));
                    if (lastMotion.groundedStep() >= index) {
                        advanced = lastMotion.groundedStep();
                        break;
                    }
                    if (lastMotion.serverTick() - startedTick >= 80) break;
                    if (moving && lastMotion.nearStep() >= index) {
                        releaseMovement(context, jump);
                        moving = false;
                        settlingSince = lastMotion.serverTick();
                    } else if (!moving && lastMotion.serverTick() - settlingSince >= 16) {
                        aimAt(context, world, next);
                        holdMovement(context, jump);
                        moving = true;
                        lastAimTick = lastMotion.serverTick();
                    } else if (moving && lastMotion.nearStep() < index
                            && lastMotion.serverTick() - lastAimTick >= 8) {
                        aimAt(context, world, next);
                        lastAimTick = lastMotion.serverTick();
                    }
                }
            } finally {
                releaseMovement(context, jump);
            }
            if (advanced < index) {
                int failedIndex = index;
                throw new IllegalStateException("Normal-input walk stopped on " + routeName
                        + " at step "
                        + failedIndex + "/" + (steps.size() - 1) + " toward "
                        + next.feet() + " after "
                        + (lastMotion == null ? 0 : lastMotion.serverTick() - startedTick)
                        + " server ticks; "
                        + (lastMotion == null ? "no movement sample" : lastMotion.diagnostic()));
            }
            if (advanced > index) {
                System.out.println("NATURAL_DISTRICT_WALK_ADVANCE target=" + target
                        + " gate=" + routeName + " from=" + index
                        + " to=" + advanced);
            }
            index = advanced + 1;
        }
    }

    private static void aimAt(ClientGameTestContext context, TestSingleplayerContext world,
            Step target) {
        float yaw = world.getServer().computeOnServer(server -> {
            ServerPlayer player = world.getConnection().getServerPlayer();
            double dx = target.feet().getX() + 0.5 - player.getX();
            double dz = target.feet().getZ() + 0.5 - player.getZ();
            return (float) Math.toDegrees(Math.atan2(-dx, dz));
        });
        context.runOnClient(client -> {
            if (client.player != null) {
                client.player.setYRot(yaw);
                client.player.setXRot(0.0F);
            }
        });
    }

    private static void holdMovement(ClientGameTestContext context, boolean jump) {
        context.getInput().holdKey(options -> options.keyUp);
        if (jump) context.getInput().holdKey(options -> options.keyJump);
    }

    private static void releaseMovement(ClientGameTestContext context, boolean jump) {
        context.getInput().releaseKey(options -> options.keyUp);
        if (jump) context.getInput().releaseKey(options -> options.keyJump);
    }

    private static Motion motion(ServerLevel level, ServerPlayer player,
            List<Step> steps, int requested) {
        int near = -1;
        int grounded = -1;
        if (player.isAlive()) {
            for (int index = Math.min(requested + 2, steps.size() - 1);
                    index >= requested; index--) {
                Step step = steps.get(index);
                var box = player.getBoundingBox();
                double x = step.feet().getX();
                double z = step.feet().getZ();
                // Require a real overlap with the walking tile, not just a
                // nearby center. This also recognizes a half-height landing.
                if (box.maxX <= x + 0.05 || box.minX >= x + 0.95
                        || box.maxZ <= z + 0.05 || box.minZ >= z + 0.95) continue;
                double rise = player.getY() - step.standingY();
                if (rise >= -0.22 && rise <= 1.35 && near < 0) near = index;
                if (player.onGround() && rise >= -0.22 && rise <= 0.55) {
                    grounded = index;
                    break;
                }
            }
        }
        BlockPos below = BlockPos.containing(player.getX(),
                player.getY() - 0.6, player.getZ());
        String diagnostic = "position=" + player.position()
                + " onGround=" + player.onGround()
                + " velocity=" + player.getDeltaMovement()
                + " below=" + below + ":" + level.getBlockState(below).getBlock()
                + " nearStep=" + near + " groundedStep=" + grounded
                + " serverTick=" + level.getGameTime();
        return new Motion(level.getGameTime(), grounded, near, diagnostic);
    }

    /** Load the bounded crossing footprint, then let the server's chunk-load queue finish it. */
    private static void loadGuardedRouteChunks(ServerLevel level, BoundingBox building, Gate gate) {
        int normalX = gate.inward().getStepX();
        int normalZ = gate.inward().getStepZ();
        int crossX = -normalZ;
        int crossZ = normalX;
        int centerX = (building.minX() + building.maxX()) / 2;
        int centerZ = (building.minZ() + building.maxZ()) / 2;
        int edgeX = (normalX > 0 ? building.minX()
                : normalX < 0 ? building.maxX() : centerX)
                + crossX * gate.lateralOffset();
        int edgeZ = (normalZ > 0 ? building.minZ()
                : normalZ < 0 ? building.maxZ() : centerZ)
                + crossZ * gate.lateralOffset();
        Set<ChunkPos> chunks = new HashSet<>();
        for (int depth = -Math.max(gate.bankDistance() + 4, 20);
                depth <= Math.max(gate.interiorDepth() + 4, 14); depth++) {
            for (int cross = -gate.halfWidth(); cross <= gate.halfWidth(); cross++) {
                int x = edgeX + normalX * depth + crossX * cross;
                int z = edgeZ + normalZ * depth + crossZ * cross;
                chunks.add(new ChunkPos(x >> 4, z >> 4));
            }
        }
        for (ChunkPos chunk : chunks) level.getChunk(chunk.x(), chunk.z());
    }

    private static Route findRoute(ServerLevel level, BoundingBox building,
            List<BoundingBox> allPieces, Gate gate) {
        int centerX = (building.minX() + building.maxX()) / 2;
        int centerZ = (building.minZ() + building.maxZ()) / 2;
        int edgeX = gate.inward() == Direction.EAST ? building.minX()
                : gate.inward() == Direction.WEST ? building.maxX() : centerX;
        int edgeZ = gate.inward() == Direction.SOUTH ? building.minZ()
                : gate.inward() == Direction.NORTH ? building.maxZ() : centerZ;
        int normalX = gate.inward().getStepX();
        int normalZ = gate.inward().getStepZ();
        int crossX = -normalZ;
        int crossZ = normalX;
        edgeX += crossX * gate.lateralOffset();
        edgeZ += crossZ * gate.lateralOffset();
        int minDepth = -Math.max(gate.bankDistance() + 4, 20);
        int maxDepth = Math.max(gate.interiorDepth() + 4, 14);
        int radius = gate.halfWidth();
        Map<BlockPos, Step> cells = new HashMap<>();
        List<Step> sources = new ArrayList<>();
        int dryExterior = 0;
        int interior = 0;
        for (int depth = minDepth; depth <= maxDepth; depth++) {
            for (int cross = -radius; cross <= radius; cross++) {
                int x = edgeX + normalX * depth + crossX * cross;
                int z = edgeZ + normalZ * depth + crossZ * cross;
                level.getChunk(x >> 4, z >> 4);
                int lowFloor = gate.name().equals("wadi-entrance")
                        ? building.minY() - 16 : building.minY() - 3;
                for (int floorY = lowFloor;
                        floorY <= building.minY() + 9; floorY++) {
                    Step step = standing(level, new BlockPos(x, floorY, z));
                    if (step == null) continue;
                    cells.put(step.feet(), step);
                    if (Math.abs(cross) <= gate.sourceHalfWidth()
                            && depth <= -gate.bankDistance()
                            && depth >= -gate.bankDistance() - 4
                            && outsidePieces(step.feet(), allPieces)) {
                        sources.add(step);
                        dryExterior++;
                    }
                    if (depth >= gate.interiorDepth()
                            && depth <= gate.interiorDepth() + 3
                            && inside(step.feet(), building, gate.interiorDepth())
                            && step.standingY() >= building.minY() + 2.5) interior++;
                }
            }
        }
        if (sources.isEmpty() || interior == 0) {
            throw new IllegalStateException("No dry gate endpoints: exterior="
                    + dryExterior + " interior=" + interior + " gate=" + gate.name()
                    + " edge=" + edgeX + "," + edgeZ + " box=" + building);
        }
        ArrayDeque<Step> frontier = new ArrayDeque<>(sources);
        Map<BlockPos, BlockPos> previous = new HashMap<>();
        for (Step source : sources) previous.put(source.feet(), source.feet());
        int expanded = 0;
        while (!frontier.isEmpty()) {
            Step current = frontier.removeFirst();
            expanded++;
            int depth = normalX * (current.feet().getX() - edgeX)
                    + normalZ * (current.feet().getZ() - edgeZ);
            if (depth >= gate.interiorDepth()
                    && inside(current.feet(), building, gate.interiorDepth())
                    && current.standingY() >= building.minY() + 2.5) {
                List<Step> reverse = new ArrayList<>();
                BlockPos position = current.feet();
                while (true) {
                    reverse.add(cells.get(position));
                    BlockPos parent = previous.get(position);
                    if (parent.equals(position)) break;
                    position = parent;
                }
                java.util.Collections.reverse(reverse);
                Step firstInside = reverse.stream().filter(step ->
                        step.feet().getX() >= building.minX()
                                && step.feet().getX() <= building.maxX()
                                && step.feet().getZ() >= building.minZ()
                                && step.feet().getZ() <= building.maxZ())
                        .findFirst().orElseThrow();
                int entryCross = crossX * (firstInside.feet().getX() - edgeX)
                        + crossZ * (firstInside.feet().getZ() - edgeZ);
                if (Math.abs(entryCross) > gate.sourceHalfWidth() + 1) {
                    throw new IllegalStateException("Route bypassed the intended gate: "
                            + gate.name() + " firstInside=" + firstInside.feet()
                            + " lateral=" + entryCross);
                }
                return new Route(List.copyOf(reverse), building);
            }
            for (Direction direction : new Direction[]{Direction.NORTH, Direction.SOUTH,
                    Direction.EAST, Direction.WEST}) {
                int x = current.feet().getX() + direction.getStepX();
                int z = current.feet().getZ() + direction.getStepZ();
                for (int deltaY = -1; deltaY <= 1; deltaY++) {
                    BlockPos nextPos = new BlockPos(x, current.feet().getY() + deltaY, z);
                    Step next = cells.get(nextPos);
                    if (next == null || previous.containsKey(nextPos)
                            || Math.abs(next.standingY() - current.standingY()) > 1.05
                            || !hasJumpHeadroom(level, current, next)) {
                        continue;
                    }
                    previous.put(nextPos, current.feet());
                    frontier.addLast(next);
                }
            }
        }
        throw new IllegalStateException("No continuous dry player-height route from bank"
                + " to interior: gate=" + gate.name() + " exterior=" + dryExterior
                + " interior=" + interior + " reachable=" + expanded
                + " edge=" + edgeX + "," + edgeZ + " box=" + building);
    }

    private static Step standing(ServerLevel level, BlockPos floor) {
        var state = level.getBlockState(floor);
        if (state.is(BlockTags.LEAVES) || !state.getFluidState().isEmpty()) return null;
        VoxelShape shape = state.getCollisionShape(level, floor);
        if (shape.isEmpty()) return null;
        double height = shape.max(Direction.Axis.Y);
        if (height < 0.45 || height > 1.05) return null;
        BlockPos feet = floor.above();
        for (int rise = 0; rise <= 1; rise++) {
            BlockPos clearance = feet.above(rise);
            var block = level.getBlockState(clearance);
            if (!block.getFluidState().isEmpty()
                    || !block.getCollisionShape(level, clearance).isEmpty()) return null;
        }
        return new Step(feet, floor.getY() + height);
    }

    private static boolean hasJumpHeadroom(ServerLevel level, Step current, Step next) {
        if (next.standingY() <= current.standingY() + 0.55) return true;
        // A standing cell has room for the player's body, but a one-block
        // ascent also needs space above the takeoff cell during the jump.
        BlockPos aboveTakeoff = current.feet().above(2);
        var state = level.getBlockState(aboveTakeoff);
        return state.getFluidState().isEmpty()
                && state.getCollisionShape(level, aboveTakeoff).isEmpty();
    }

    private static boolean outsidePieces(BlockPos feet, List<BoundingBox> pieces) {
        return pieces.stream().noneMatch(box -> feet.getX() >= box.minX()
                && feet.getX() <= box.maxX() && feet.getZ() >= box.minZ()
                && feet.getZ() <= box.maxZ());
    }

    private static boolean inside(BlockPos feet, BoundingBox box, int depth) {
        return feet.getX() >= box.minX() + depth && feet.getX() <= box.maxX() - depth
                && feet.getZ() >= box.minZ() + depth && feet.getZ() <= box.maxZ() - depth;
    }
}
