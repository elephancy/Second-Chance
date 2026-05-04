package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.DomainBlocks;
import com.feel.gems.adventure.FamForumPersistentState;
import com.feel.gems.adventure.FriendDomainUnlockState;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Opt-in loaded-world gate for the staged three-piece Fam Forum v9. */
@SuppressWarnings("UnstableApiUsage")
public final class FamForumV9ClientGameTest implements FabricClientGameTest {
    private static final String ROOT = "fam_forum_listening_court_v9";
    private static final String STORY = "fam_story_hall_library_v9";
    private static final String SCHOOL = "fam_open_school_colonnade_v9";

    private record Piece(String id, BlockPos origin, BoundingBox bounds) {}

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!Boolean.getBoolean("gems.famForumV9ClientGate")) return;
        TestWorldSave save;
        BlockPos origin;
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            origin = world.getServer().computeOnServer(server ->
                    world.getConnection().getServerPlayer().blockPosition()
                            .offset(-23, 12, -23));
            world.getServer().runOnServer(server -> {
                ServerLevel level = server.overworld();
                Piece root = place(server, level, ROOT, origin);
                Piece story = place(server, level, STORY, origin.offset(46, 0, 3));
                Piece school = place(server, level, SCHOOL, origin.offset(-40, 0, 3));
                checkLoadedControls(level, root, story, school);
                checkRoutes(level, root, story, school);
                exerciseControls(world, root, story, school);
                System.out.println("FAM_V9_LOADED_PASS templates=" + List.of(ROOT, STORY, SCHOOL)
                        + " sharedUnlock=true circle=true");
            });
            view(world, context, origin.offset(23, 6, 44), 180.0F,
                    "fam-v9-loaded-listening-court");
            view(world, context, origin.offset(76, 6, 36), 180.0F,
                    "fam-v9-loaded-story-hall");
            view(world, context, origin.offset(-12, 6, 36), 180.0F,
                    "fam-v9-loaded-open-school");
            save = world.getWorldSave();
        }
        try (TestSingleplayerContext reopened = save.open()) {
            reopened.getConnection().waitForChunksRender();
            BlockPos retained = origin;
            reopened.getServer().runOnServer(server -> {
                ServerLevel level = server.overworld();
                BlockPos board = retained.offset(23, 6, 25);
                BlockPos story = retained.offset(56, 6, 21);
                BlockPos school = retained.offset(-12, 6, 21);
                level.getChunk(board.getX() >> 4, board.getZ() >> 4);
                level.getChunk(story.getX() >> 4, story.getZ() >> 4);
                level.getChunk(school.getX() >> 4, school.getZ() >> 4);
                if (!level.getBlockState(board).is(DomainBlocks.FAM_FORUM_BOARD)
                        || !level.getBlockState(story).is(DomainBlocks.FAM_STORY_CLUE)
                        || !level.getBlockState(school).is(DomainBlocks.FAM_SCHOOL_CLUE)
                        || !FamForumPersistentState.get(server).ready()
                        || !FriendDomainUnlockState.get(server).cleared("fam")) {
                    throw new IllegalStateException("Fam v9 pieces or shared guide lost on reload");
                }
                System.out.println("FAM_V9_REOPEN_PASS templatesAndSharedGuide=true");
            });
        }
    }

    private static Piece place(net.minecraft.server.MinecraftServer server, ServerLevel level,
                               String id, BlockPos origin) {
        var template = server.getStructureTemplateManager()
                .get(Identifier.fromNamespaceAndPath("gems", id)).orElseThrow();
        if (!template.placeInWorld(level, origin, origin, new StructurePlaceSettings(),
                RandomSource.create(), 2)) {
            throw new IllegalStateException("Fam v9 placement failed: " + id);
        }
        var size = template.getSize();
        return new Piece(id, origin, new BoundingBox(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + size.getX() - 1, origin.getY() + size.getY() - 1,
                origin.getZ() + size.getZ() - 1));
    }

    private static void checkLoadedControls(ServerLevel level, Piece root,
                                             Piece story, Piece school) {
        check(level, root.origin().offset(23, 6, 25), DomainBlocks.FAM_FORUM_BOARD);
        check(level, root.origin().offset(23, 6, 19), DomainBlocks.FAM_CIRCLE_START);
        check(level, story.origin().offset(10, 6, 18), DomainBlocks.FAM_STORY_CLUE);
        check(level, story.origin().offset(12, 6, 18), DomainBlocks.FAM_DISPATCH_STORY);
        check(level, story.origin().offset(30, 5, 30), DomainBlocks.FAM_CIRCLE_STORY);
        check(level, school.origin().offset(28, 6, 18), DomainBlocks.FAM_SCHOOL_CLUE);
        check(level, school.origin().offset(28, 6, 20), DomainBlocks.FAM_DISPATCH_SCHOOL);
        check(level, school.origin().offset(28, 6, 27), DomainBlocks.FAM_CIRCLE_SCHOOL);
    }

    private static void check(ServerLevel level, BlockPos at,
                              net.minecraft.world.level.block.Block expected) {
        if (!level.getBlockState(at).is(expected)) {
            throw new IllegalStateException("Fam v9 loaded control missing at " + at
                    + ": expected " + expected + ", found " + level.getBlockState(at));
        }
    }

    private static void checkRoutes(ServerLevel level, Piece root, Piece story, Piece school) {
        route(level, root, root.origin().offset(23, 5, 42), List.of(
                root.origin().offset(23, 6, 25), root.origin().offset(23, 6, 19),
                root.origin().offset(0, 5, 23), root.origin().offset(46, 5, 23)));
        route(level, story, story.origin().offset(0, 6, 20), List.of(
                story.origin().offset(10, 6, 18), story.origin().offset(12, 6, 18),
                story.origin().offset(30, 5, 30)));
        route(level, school, school.origin().offset(40, 6, 20), List.of(
                school.origin().offset(28, 6, 18), school.origin().offset(28, 6, 20),
                school.origin().offset(28, 6, 27)));
    }

    private static void route(ServerLevel level, Piece piece, BlockPos start,
                              List<BlockPos> controls) {
        if (!walkable(level, piece, start)) {
            throw new IllegalStateException("Fam v9 entrance blocked: " + piece.id()
                    + " at " + start);
        }
        Set<BlockPos> reached = new HashSet<>();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        reached.add(start);
        pending.add(start);
        while (!pending.isEmpty()) {
            BlockPos current = pending.removeFirst();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos flat = current.relative(direction);
                for (int rise = -1; rise <= 1; rise++) {
                    BlockPos next = flat.above(rise);
                    if (!reached.contains(next) && walkable(level, piece, next)) {
                        reached.add(next);
                        pending.addLast(next);
                    }
                }
            }
        }
        for (BlockPos control : controls) {
            boolean available = false;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (int rise = -1; rise <= 1; rise++) {
                    if (reached.contains(control.relative(direction).above(rise))) {
                        available = true;
                    }
                }
            }
            if (!available) {
                throw new IllegalStateException("No loaded walk to " + piece.id()
                        + " control at " + control + "; visited=" + reached.size());
            }
        }
    }

    private static boolean walkable(ServerLevel level, Piece piece, BlockPos feet) {
        BoundingBox box = piece.bounds();
        if (feet.getX() < box.minX() || feet.getX() > box.maxX()
                || feet.getZ() < box.minZ() || feet.getZ() > box.maxZ()
                || feet.getY() < piece.origin().getY() + 4
                || feet.getY() > piece.origin().getY() + 8) return false;
        return !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }

    private static void exerciseControls(TestSingleplayerContext world, Piece root,
                                         Piece story, Piece school) {
        var player = world.getConnection().getServerPlayer();
        var server = player.level().getServer();
        ServerLevel level = server.overworld();
        BlockPos board = root.origin().offset(23, 6, 25);
        if (use(player, level, board) != InteractionResult.SUCCESS_SERVER
                || FriendDomainUnlockState.get(server).cleared("fam")) {
            throw new IllegalStateException("Fam v9 board bypassed its two clues");
        }
        if (use(player, level, story.origin().offset(10, 6, 18))
                    != InteractionResult.SUCCESS_SERVER
                || use(player, level, school.origin().offset(28, 6, 18))
                    != InteractionResult.SUCCESS_SERVER
                || !FamForumPersistentState.get(server).ready()
                || use(player, level, board) != InteractionResult.SUCCESS_SERVER
                || !FriendDomainUnlockState.get(server).cleared("fam")) {
            throw new IllegalStateException("Fam v9 loaded clues did not unlock the guide");
        }
        BlockPos center = root.origin().offset(23, 6, 19);
        player.teleportTo(level, center.getX() + 1.5, center.getY(), center.getZ() + 0.5,
                EnumSet.noneOf(Relative.class), 0.0F, 0.0F, false);
        if (use(player, level, center) != InteractionResult.SUCCESS_SERVER
                || use(player, level, story.origin().offset(30, 5, 30))
                    != InteractionResult.SUCCESS_SERVER
                || use(player, level, school.origin().offset(28, 6, 27))
                    != InteractionResult.SUCCESS_SERVER
                || use(player, level, center) != InteractionResult.SUCCESS_SERVER
                || !player.hasEffect(MobEffects.NIGHT_VISION)
                || !player.hasEffect(MobEffects.SPEED)) {
            throw new IllegalStateException("Fam v9 listening circuit failed on loaded controls");
        }
    }

    private static InteractionResult use(net.minecraft.server.level.ServerPlayer player,
                                         ServerLevel level, BlockPos at) {
        return UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
    }

    private static void view(TestSingleplayerContext world, ClientGameTestContext context,
                             BlockPos at, float yaw, String name) {
        world.getServer().runOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            player.teleportTo(server.overworld(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5,
                    EnumSet.noneOf(Relative.class), yaw, 0.0F, false);
            player.setNoGravity(true);
        });
        context.waitTicks(20);
        world.getConnection().waitForChunksRender();
        context.runOnClient(client -> {
            client.gui.hud.getChat().clearMessages(false);
            client.gui.toastManager().clear();
        });
        context.takeScreenshot(name);
    }
}
