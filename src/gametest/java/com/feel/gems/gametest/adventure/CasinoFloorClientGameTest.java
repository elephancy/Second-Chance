package com.feel.gems.gametest.adventure;

import com.feel.gems.casino.CasinoGame;
import com.feel.gems.casino.CasinoViewPayload;
import com.feel.gems.client.casino.CasinoScreen;
import com.feel.gems.friend.FriendEntity;
import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Opt-in connected-client check for the six physical casino seats and old sign copy. */
@SuppressWarnings("UnstableApiUsage")
public final class CasinoFloorClientGameTest implements FabricClientGameTest {
    private static final boolean CAPTURE = !Boolean.getBoolean("gems.casinoNoCapture");
    private record Seat(CasinoGame game, int x, int z) { }

    private static final List<Seat> SEATS = List.of(
            new Seat(CasinoGame.RPS, 9, 6),
            new Seat(CasinoGame.CARDS, 20, 6),
            new Seat(CasinoGame.WHEEL, 9, 16),
            new Seat(CasinoGame.REELS, 20, 16),
            new Seat(CasinoGame.TRIVIA, 9, 23),
            new Seat(CasinoGame.COOP, 20, 23));

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!Boolean.getBoolean("gems.casinoFloorClientGate")) return;
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            BlockPos modern = world.getServer().computeOnServer(server -> {
                ServerPlayer player = world.getConnection().getServerPlayer();
                ServerLevel level = server.overworld();
                BlockPos origin = player.blockPosition().offset(-15, -5, -15);
                place(server, level, origin, "town_casino_deco_v2");
                place(server, level, origin.offset(64, 0, 0), "town_casino");
                player.getInventory().setItem(0, new ItemStack(Items.EMERALD, 64));
                if (resident(level, origin) == null
                        || resident(level, origin.offset(64, 0, 0)) == null) {
                    throw new IllegalStateException("A placed casino lost Ragab's resident entity");
                }
                return origin;
            });
            world.getServer().runOnServer(server -> {
                ServerPlayer player = world.getConnection().getServerPlayer();
                player.teleportTo(server.overworld(), modern.getX() + 15.5,
                        modern.getY() + 5, modern.getZ() + 15.5,
                        EnumSet.noneOf(Relative.class), 180.0F, 0.0F, false);
            });
            world.getConnection().waitForChunksRender();
            context.waitTicks(20);
            if (CAPTURE) context.takeScreenshot("casino-six-bay-floor-v2");

            int tested = 0;
            for (Seat seat : SEATS) {
                useSign(world, context, modern, seat);
                CasinoViewPayload preview = expect(context, seat.game(), "PREVIEW");
                if (!preview.rules().equals(seat.game().rules())
                        || preview.stake() != seat.game().stake()) {
                    throw new IllegalStateException(seat.game() + " physical sign opened wrong rules/stake");
                }
                if (seat.game() == CasinoGame.CARDS || seat.game() == CasinoGame.COOP) {
                    if (CAPTURE) context.takeScreenshot("casino-floor-" + seat.game().id() + "-preview");
                }
                context.clickScreenButton("Stake " + seat.game().stake() + " emeralds");
                context.waitTicks(5);
                if (seat.game() == CasinoGame.COOP) {
                    expect(context, seat.game(), "COOP_WAIT");
                    context.clickScreenButton("Leave seat");
                    context.waitTicks(5);
                    expect(context, seat.game(), "PREVIEW");
                    context.clickScreenButton("All games");
                    context.waitTicks(5);
                    expect(context, CasinoGame.COOP, "LOBBY");
                } else {
                    finishSolo(context, seat.game());
                }
                context.setScreen(() -> null);
                tested++;
            }

            world.getServer().runOnServer(server -> {
                ServerPlayer player = world.getConnection().getServerPlayer();
                FriendEntity ragab = resident(server.overworld(), modern);
                player.teleportTo(server.overworld(), ragab.getX() + 1.0,
                        ragab.getY(), ragab.getZ(), EnumSet.noneOf(Relative.class),
                        0.0F, 0.0F, false);
            });
            world.getConnection().waitForChunksRender();
            context.waitTicks(20);
            context.runOnClient(client -> {
                FriendEntity ragab = client.level.getEntitiesOfClass(FriendEntity.class,
                        new AABB(modern.offset(15, 5, 24)).inflate(4),
                        friend -> friend.isAlive() && "ragab".equals(friend.friendId()))
                        .stream().findFirst().orElseThrow(() ->
                                new IllegalStateException("Ragab was not visible to the client"));
                if (client.gameMode.interact(client.player, ragab,
                        new EntityHitResult(ragab), InteractionHand.MAIN_HAND)
                        == InteractionResult.PASS) {
                    throw new IllegalStateException("Client interaction with Ragab was not consumed");
                }
            });
            context.waitTicks(5);
            CasinoViewPayload lobby = expect(context, CasinoGame.RPS, "LOBBY");
            if (lobby.choices().stream().filter(choice -> choice.id().startsWith("preview_")).count() != 6) {
                throw new IllegalStateException("Ragab's fallback lobby lost a game table");
            }
            context.setScreen(() -> null);

            BlockPos old = modern.offset(64, 0, 0);
            useSign(world, context, old, SEATS.getFirst());
            expect(context, CasinoGame.RPS, "PREVIEW");
            context.clickScreenButton("Stake 3 emeralds");
            context.waitTicks(5);
            finishSolo(context, CasinoGame.RPS);
            System.out.println("CASINO_FLOOR_CLIENT_PASS v2Seats=" + tested
                    + " paidSolo=5 signalWait=1 ragabLobby=1 oldV1Sign=1");
        }
    }

    private static void place(net.minecraft.server.MinecraftServer server, ServerLevel level,
                              BlockPos origin, String id) {
        var template = server.getStructureTemplateManager()
                .get(Identifier.fromNamespaceAndPath("gems", id)).orElseThrow();
        if (!template.placeInWorld(level, origin, origin, new StructurePlaceSettings(),
                RandomSource.create(), 2)) {
            throw new IllegalStateException("Casino template placement failed: " + id);
        }
    }

    private static FriendEntity resident(ServerLevel level, BlockPos origin) {
        return level.getEntitiesOfClass(FriendEntity.class,
                new AABB(origin.offset(15, 5, 24)).inflate(4),
                friend -> friend.isAlive() && "ragab".equals(friend.friendId()))
                .stream().findFirst().orElse(null);
    }

    private static void useSign(TestSingleplayerContext world, ClientGameTestContext context,
                                BlockPos origin, Seat seat) {
        context.setScreen(() -> null);
        world.getServer().runOnServer(server -> {
            ServerPlayer player = world.getConnection().getServerPlayer();
            ServerLevel level = server.overworld();
            BlockPos sign = origin.offset(seat.x(), 5, seat.z());
            player.teleportTo(level, sign.getX() + 0.5, sign.getY(), sign.getZ() - 1.5,
                    EnumSet.noneOf(Relative.class), 0.0F, 0.0F, false);
        });
        world.getConnection().waitForChunksRender();
        context.waitTicks(20);
        context.runOnClient(client -> {
            BlockPos sign = origin.offset(seat.x(), 5, seat.z());
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(sign), Direction.UP, sign, false);
            if (client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit)
                    == InteractionResult.PASS) {
                throw new IllegalStateException(seat.game() + " client sign use was not consumed");
            }
        });
        context.waitTicks(5);
    }

    private static void finishSolo(ClientGameTestContext context, CasinoGame game) {
        for (int move = 0; move < 30; move++) {
            CasinoViewPayload view = currentView(context);
            if (!view.gameId().equals(game.id())) {
                throw new IllegalStateException("Active table changed from " + game.id()
                        + " to " + view.gameId());
            }
            if (view.phase().equals("RESULT")) return;
            if (!view.phase().equals("ROUND") || view.choices().isEmpty()) {
                throw new IllegalStateException(game + " failed to reach a live round");
            }
            context.clickScreenButton(view.choices().getFirst().label());
            context.waitTicks(5);
        }
        throw new IllegalStateException(game + " did not finish after 30 table actions");
    }

    private static CasinoViewPayload expect(ClientGameTestContext context, CasinoGame game, String phase) {
        CasinoViewPayload view = currentView(context);
        if (!phase.equals(view.phase())
                || !phase.equals("LOBBY") && !game.id().equals(view.gameId())) {
            throw new IllegalStateException("Expected " + game + " " + phase
                    + ", got " + view.gameId() + " " + view.phase());
        }
        return view;
    }

    private static CasinoViewPayload currentView(ClientGameTestContext context) {
        return context.computeOnClient(client -> readView(client));
    }

    private static CasinoViewPayload readView(net.minecraft.client.Minecraft client) {
        if (!(client.gui.screen() instanceof CasinoScreen screen)) {
            throw new IllegalStateException("Casino packet did not open its screen");
        }
        try {
            Field field = CasinoScreen.class.getDeclaredField("view");
            field.setAccessible(true);
            return (CasinoViewPayload) field.get(screen);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not inspect the connected casino view", error);
        }
    }
}
