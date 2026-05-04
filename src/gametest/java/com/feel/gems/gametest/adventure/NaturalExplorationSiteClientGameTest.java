package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.exploration.ExplorationBlocks;
import com.feel.gems.adventure.exploration.ExplorationDeliveryAccount;
import com.feel.gems.adventure.exploration.ExplorationProgress;
import com.feel.gems.adventure.exploration.ExplorationState;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Opt-in final gate for naturally generated, playable independent exploration sites. */
@SuppressWarnings("UnstableApiUsage")
public final class NaturalExplorationSiteClientGameTest implements FabricClientGameTest {
    private static final String[] SITES = {"lost_trail", "star_cipher", "skywatch_lookout"};

    private record Site(BoundingBox box, Map<Block, BlockPos> markers) {
        BlockPos marker(Block block) {
            BlockPos pos = markers.get(block);
            if (pos == null) throw new IllegalStateException("Missing " + block + " in " + box);
            return pos;
        }
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!Boolean.getBoolean("gems.naturalExplorationSweep")) return;
        try (TestSingleplayerContext world = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(settings -> {
                    settings.setSeed("SecondChanceExplorationSmoke");
                    settings.setGenerateStructures(true);
                }).create()) {
            world.getConnection().waitForChunksRender();
            for (String name : SITES) {
                Site site = world.getServer().computeOnServer(server -> locate(server.overworld(), name));
                screenshot(context, world, site, name);
                world.getServer().runOnServer(server -> {
                    ServerLevel level = server.overworld();
                    switch (name) {
                        case "lost_trail" -> trail(world, level, site);
                        case "star_cipher" -> cipher(world, level, site);
                        case "skywatch_lookout" -> lookout(world, level, site);
                        default -> throw new IllegalArgumentException(name);
                    }
                });
                System.out.println("NATURAL_EXPLORATION_PASS site=" + name + " box=" + site.box());
            }
        }
    }

    private static Site locate(ServerLevel level, String name) {
        var registry = level.getServer().registryAccess().lookupOrThrow(Registries.STRUCTURE);
        TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE,
                Identifier.fromNamespaceAndPath("gems", "themed_target/" + name));
        BlockPos located = level.findNearestMapStructure(tag, new BlockPos(0, 80, 0), 96, false);
        if (located == null) throw new IllegalStateException("No natural start for " + name);
        int centerX = located.getX() >> 4;
        int centerZ = located.getZ() >> 4;
        StructureStart start = null;
        for (int radius = 0; radius <= 5 && start == null; radius++) {
            for (int dx = -radius; dx <= radius && start == null; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    var chunk = level.getChunk(centerX + dx, centerZ + dz);
                    for (var holder : registry.getTagOrEmpty(tag)) {
                        StructureStart candidate = level.structureManager()
                                .getStartForStructure(holder.value(), chunk);
                        if (candidate != null && candidate.isValid()) {
                            start = candidate;
                            break;
                        }
                    }
                    if (start != null) break;
                }
            }
        }
        if (start == null) throw new IllegalStateException("No saved start for " + name + " near " + located);
        // The start box includes the 12-block beard terrain adaptation on
        // every side, including below the template. Site markers and grade
        // must be scanned against the placed single-piece template itself.
        if (start.getPieces().size() != 1) {
            throw new IllegalStateException(name + " generated " + start.getPieces().size()
                    + " pieces instead of one");
        }
        BoundingBox box = start.getPieces().getFirst().getBoundingBox();
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
            for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) level.getChunk(x, z);
        }
        Map<Block, BlockPos> markers = new HashMap<>();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Block block = level.getBlockState(pos).getBlock();
                    if (!isMarker(block)) continue;
                    if (markers.put(block, pos) != null) {
                        throw new IllegalStateException(name + " has duplicate " + block);
                    }
                }
            }
        }
        int expected = switch (name) {
            case "lost_trail" -> 3;
            case "star_cipher" -> 5;
            case "skywatch_lookout" -> 1;
            default -> throw new IllegalArgumentException(name);
        };
        if (markers.size() != expected) {
            throw new IllegalStateException(name + " expected " + expected
                    + " markers, found " + markers);
        }
        System.out.println("NATURAL_EXPLORATION_SITE site=" + name + " located=" + located
                + " start=" + start.getChunkPos() + " box=" + box + " markers=" + markers);
        return new Site(box, Map.copyOf(markers));
    }

    private static boolean isMarker(Block block) {
        return block == ExplorationBlocks.TRAIL_ORIGIN || block == ExplorationBlocks.TRAIL_TURN
                || block == ExplorationBlocks.TRAIL_FINISH || block == ExplorationBlocks.CIPHER_PLAQUE
                || block == ExplorationBlocks.CIPHER_SUN || block == ExplorationBlocks.CIPHER_STAR
                || block == ExplorationBlocks.CIPHER_MOON || block == ExplorationBlocks.CIPHER_SEAL
                || block == ExplorationBlocks.VISTA_DIAL;
    }

    private static void trail(TestSingleplayerContext world, ServerLevel level, Site site) {
        BlockPos origin = site.marker(ExplorationBlocks.TRAIL_ORIGIN);
        BlockPos turn = site.marker(ExplorationBlocks.TRAIL_TURN);
        BlockPos finish = site.marker(ExplorationBlocks.TRAIL_FINISH);
        exteriorRoute(level, site.box(), origin);
        reachable(level, site.box(), origin, turn);
        reachable(level, site.box(), turn, finish);
        var player = world.getConnection().getServerPlayer();
        useNear(level, player, site.box(), origin);
        useNear(level, player, site.box(), turn);
        useNear(level, player, site.box(), finish);
        assertEarned(level, player, "trail", origin);
    }

    private static void cipher(TestSingleplayerContext world, ServerLevel level, Site site) {
        BlockPos plaque = site.marker(ExplorationBlocks.CIPHER_PLAQUE);
        BlockPos sun = site.marker(ExplorationBlocks.CIPHER_SUN);
        BlockPos star = site.marker(ExplorationBlocks.CIPHER_STAR);
        BlockPos moon = site.marker(ExplorationBlocks.CIPHER_MOON);
        BlockPos seal = site.marker(ExplorationBlocks.CIPHER_SEAL);
        exteriorRoute(level, site.box(), plaque);
        for (BlockPos pos : List.of(sun, star, moon, seal)) reachable(level, site.box(), plaque, pos);
        var player = world.getConnection().getServerPlayer();
        useNear(level, player, site.box(), plaque);
        useNear(level, player, site.box(), sun);
        useNear(level, player, site.box(), star);
        useNear(level, player, site.box(), moon);
        useNear(level, player, site.box(), seal);
        assertEarned(level, player, "cipher", plaque);
    }

    private static void lookout(TestSingleplayerContext world, ServerLevel level, Site site) {
        BlockPos dial = site.marker(ExplorationBlocks.VISTA_DIAL);
        if (!level.canSeeSky(dial.above())) throw new IllegalStateException("Skywatch dial is roofed over");
        int ladder = 0;
        int ladderBottom = Integer.MAX_VALUE;
        int ladderTop = Integer.MIN_VALUE;
        BlockPos ladderBase = null;
        for (int x = site.box().minX(); x <= site.box().maxX(); x++) {
            for (int z = site.box().minZ(); z <= site.box().maxZ(); z++) {
                int count = 0;
                int runBottom = site.box().minY();
                for (int y = site.box().minY(); y <= site.box().maxY(); y++) {
                    if (level.getBlockState(new BlockPos(x, y, z)).is(Blocks.LADDER)) {
                        if (count == 0) runBottom = y;
                        count++;
                        if (count > ladder) {
                            ladder = count;
                            ladderBottom = runBottom;
                            ladderTop = y;
                            ladderBase = new BlockPos(x, runBottom, z);
                        }
                    } else {
                        count = 0;
                    }
                }
            }
        }
        if (ladder < 20 || ladderBottom > site.box().minY() + 5
                || ladderTop < dial.getY() - 1) {
            throw new IllegalStateException("Skywatch has no ground-to-roof climb: "
                    + ladder + " from " + ladderBottom + " to " + ladderTop);
        }
        exteriorRoute(level, site.box(), ladderBase);
        BlockPos roofFeet = adjacentStanding(level, site.box(), dial);
        var player = world.getConnection().getServerPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SPYGLASS));
        player.teleportTo(level, roofFeet.getX() + 0.5, roofFeet.getY(),
                roofFeet.getZ() + 0.5, EnumSet.noneOf(Relative.class), -90F, 0F, false);
        use(level, player, dial);
        assertEarned(level, player, "vista", dial);
    }

    private static void assertEarned(ServerLevel level, net.minecraft.server.level.ServerPlayer player,
                                     String kind, BlockPos origin) {
        String id = ExplorationProgress.activityId(kind, origin.getX(), origin.getZ());
        if (!ExplorationState.get(level.getServer()).model().earned(player.getUUID(), id)
                || !ExplorationDeliveryAccount.of(player).has(id)) {
            throw new IllegalStateException(kind + " completion/receipt missing for " + id);
        }
    }

    private static void use(ServerLevel level, net.minecraft.server.level.ServerPlayer player, BlockPos pos) {
        InteractionResult result = UseBlockCallback.EVENT.invoker().interact(player, level,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        if (result != InteractionResult.SUCCESS_SERVER) {
            throw new IllegalStateException("Field marker ignored use at " + pos + ": " + result);
        }
    }

    private static void useNear(ServerLevel level, net.minecraft.server.level.ServerPlayer player,
                                BoundingBox box, BlockPos pos) {
        BlockPos feet = adjacentStanding(level, box, pos);
        player.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5,
                EnumSet.noneOf(Relative.class), 0F, 0F, false);
        use(level, player, pos);
    }

    private static void reachable(ServerLevel level, BoundingBox box, BlockPos from, BlockPos to) {
        int y = from.getY();
        if (to.getY() != y) throw new IllegalStateException("Marker floors differ");
        Set<BlockPos> starts = adjacentPositions(level, box, from);
        Set<BlockPos> goals = adjacentPositions(level, box, to);
        if (starts.isEmpty() || goals.isEmpty()) {
            throw new IllegalStateException("No standing position at a route endpoint");
        }
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.addAll(starts);
        seen.addAll(starts);
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            if (goals.contains(current)) return;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos next = current.relative(direction);
                if (seen.add(next) && canStand(level, box, next)) queue.add(next);
            }
        }
        throw new IllegalStateException("No standing route from " + from + " to " + to);
    }

    /** Prove the placed grade joins natural ground to the first usable marker or tower ladder. */
    private static void exteriorRoute(ServerLevel level, BoundingBox box, BlockPos target) {
        Map<Integer, List<BlockPos>> stairs = new HashMap<>();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int y = box.minY(); y <= Math.min(box.maxY(), box.minY() + 8); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).getBlock() instanceof StairBlock) {
                        stairs.computeIfAbsent(y, ignored -> new java.util.ArrayList<>()).add(pos);
                    }
                }
            }
        }
        if (stairs.isEmpty()) throw new IllegalStateException("No authored exterior grade in " + box);
        int bottom = stairs.keySet().stream().mapToInt(Integer::intValue).min().orElseThrow();
        List<BlockPos> low = stairs.getOrDefault(bottom, List.of());
        List<BlockPos> middle = stairs.getOrDefault(bottom + 1, List.of());
        List<BlockPos> high = stairs.getOrDefault(bottom + 2, List.of());
        if (low.size() < 3 || middle.size() < 3 || high.size() < 3) {
            throw new IllegalStateException("Exterior grade is missing a full three-wide rise");
        }
        int lowX = (int) Math.round(low.stream().mapToInt(BlockPos::getX).average().orElseThrow());
        int lowZ = (int) Math.round(low.stream().mapToInt(BlockPos::getZ).average().orElseThrow());
        int midX = (int) Math.round(middle.stream().mapToInt(BlockPos::getX).average().orElseThrow());
        int midZ = (int) Math.round(middle.stream().mapToInt(BlockPos::getZ).average().orElseThrow());
        int dx = midX - lowX;
        int dz = midZ - lowZ;
        if (Math.abs(dx) + Math.abs(dz) != 1) {
            throw new IllegalStateException("Exterior grade has no rising direction");
        }
        int outsideX = lowX - dx * 2;
        int outsideZ = lowZ - dz * 2;
        int outsideY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                outsideX, outsideZ);
        BlockPos outside = new BlockPos(outsideX, outsideY, outsideZ);
        int minY = Math.min(box.minY() - 4, outsideY - 2);
        int maxY = Math.max(target.getY() + 3, outsideY + 2);
        if (!canStandGrade(level, box, outside, minY, maxY)) {
            throw new IllegalStateException("No natural standing approach to grade at " + outside);
        }
        Set<BlockPos> goals = adjacentGradePositions(level, box, target, minY, maxY);
        if (goals.isEmpty()) throw new IllegalStateException("No usable side of entry marker " + target);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(outside);
        seen.add(outside);
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            if (goals.contains(current)) return;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (int rise : new int[]{0, 1, -1}) {
                    BlockPos next = current.relative(direction).offset(0, rise, 0);
                    if (seen.add(next) && canStandGrade(level, box, next, minY, maxY)) {
                        queue.add(next);
                    }
                }
            }
        }
        throw new IllegalStateException("No survival standing route from " + outside
                + " over the grade to " + target);
    }

    private static Set<BlockPos> adjacentGradePositions(ServerLevel level, BoundingBox box,
                                                         BlockPos marker, int minY, int maxY) {
        Set<BlockPos> result = new HashSet<>();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos feet = marker.relative(direction);
            if (canStandGrade(level, box, feet, minY, maxY)) result.add(feet);
        }
        return result;
    }

    private static boolean canStandGrade(ServerLevel level, BoundingBox box,
                                         BlockPos feet, int minY, int maxY) {
        if (feet.getX() < box.minX() - 6 || feet.getX() > box.maxX() + 6
                || feet.getZ() < box.minZ() - 6 || feet.getZ() > box.maxZ() + 6
                || feet.getY() < minY || feet.getY() > maxY) return false;
        var floor = level.getBlockState(feet.below());
        return (floor.isSolid() || floor.getBlock() instanceof StairBlock
                || floor.getBlock() instanceof SlabBlock)
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }

    private static BlockPos adjacentStanding(ServerLevel level, BoundingBox box, BlockPos marker) {
        return adjacentPositions(level, box, marker).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No standing position by " + marker));
    }

    private static Set<BlockPos> adjacentPositions(ServerLevel level, BoundingBox box, BlockPos marker) {
        Set<BlockPos> positions = new HashSet<>();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos pos = marker.relative(direction);
            if (canStand(level, box, pos)) positions.add(pos);
        }
        return positions;
    }

    private static boolean canStand(ServerLevel level, BoundingBox box, BlockPos pos) {
        return pos.getX() >= box.minX() && pos.getX() <= box.maxX()
                && pos.getZ() >= box.minZ() && pos.getZ() <= box.maxZ()
                && level.getBlockState(pos.below()).isSolid()
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }

    private static void screenshot(ClientGameTestContext context, TestSingleplayerContext world,
                                   Site site, String name) {
        world.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            var player = world.getConnection().getServerPlayer();
            BoundingBox box = site.box();
            BlockPos marker = switch (name) {
                case "lost_trail" -> site.marker(ExplorationBlocks.TRAIL_ORIGIN);
                case "star_cipher" -> site.marker(ExplorationBlocks.CIPHER_PLAQUE);
                case "skywatch_lookout" -> site.marker(ExplorationBlocks.VISTA_DIAL);
                default -> throw new IllegalArgumentException(name);
            };
            BlockPos camera = safeCamera(level, box, marker);
            player.teleportTo(level, camera.getX() + 0.5, camera.getY(), camera.getZ() + 0.5,
                    EnumSet.noneOf(Relative.class), 180F, 12F, false);
            System.out.println("NATURAL_EXPLORATION_CAMERA site=" + name + " feet=" + camera);
        });
        context.waitTicks(25);
        world.getConnection().waitForChunksRender();
        world.getServer().runOnServer(server -> {
            if (!world.getConnection().getServerPlayer().isAlive()) {
                throw new IllegalStateException(name + " camera position did not keep the player alive");
            }
        });
        context.takeScreenshot("natural-exploration-" + name);
    }

    /** Prefer a solid natural viewpoint; fall back to the authored site floor by its first marker. */
    private static BlockPos safeCamera(ServerLevel level, BoundingBox box, BlockPos marker) {
        int centerX = (box.minX() + box.maxX()) / 2;
        for (int distance = 18; distance >= 4; distance -= 2) {
            int z = box.maxZ() + distance;
            for (int offset : new int[]{0, -4, 4, -8, 8}) {
                int x = centerX + offset;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos feet = new BlockPos(x, y, z);
                if (Math.abs(y - marker.getY()) <= 12
                        && level.getBlockState(feet.below()).isSolid()
                        && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                        && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                    return feet;
                }
            }
        }
        return adjacentStanding(level, box, marker);
    }
}
