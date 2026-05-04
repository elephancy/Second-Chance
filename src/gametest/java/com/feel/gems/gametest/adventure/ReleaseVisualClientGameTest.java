package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.AdventureItems;
import com.feel.gems.adventure.CampaignPersistentState;
import com.feel.gems.adventure.DomainBlocks;
import com.feel.gems.adventure.LandmarkKey;
import com.feel.gems.adventure.boss.StageOpeningState;
import com.feel.gems.adventure.worldgen.AdventureBiomes;
import com.feel.gems.casino.CasinoViewPayload;
import com.feel.gems.casino.CasinoGame;
import com.feel.gems.casino.CasinoRound;
import com.feel.gems.client.casino.CasinoScreen;
import com.feel.gems.client.modmenu.GemsConfigScreen;
import com.feel.gems.client.screen.GuidebookScreen;
import com.feel.gems.friend.FriendEntity;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Focused 26.3 client captures for release polish; screenshots require human review. */
@SuppressWarnings("UnstableApiUsage")
public final class ReleaseVisualClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        if (Boolean.getBoolean("gems.focusedNaturalClientGate")) return;
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            clearOverlays(context);
            context.takeScreenshot("release-compact-hud");

            context.setScreen(GuidebookScreen::new);
            context.waitTicks(3);
            context.clickScreenButton("Find a place");
            context.waitTicks(3);
            context.takeScreenshot("release-guidebook-path");
            clickGuideButtonStarting(context, "Jump: Quick start (");
            context.waitTicks(3);
            context.takeScreenshot("release-guidebook-section-index");
            clickGuideButtonContaining(context, "Lastlight: Return Board");
            context.waitTicks(3);
            context.takeScreenshot("release-guidebook-section-jump");
            context.clickScreenButton("Topics");
            context.waitTicks(1);
            clickGuideTopic(context, "Find your path");
            context.waitTicks(3);
            context.clickScreenButton("Next >");
            context.waitTicks(3);
            context.takeScreenshot("release-guidebook-path-next-page");
            context.clickScreenButton("Topics");
            context.waitTicks(3);
            context.takeScreenshot("release-guidebook-chapters");
            clickGuideTopic(context, "Mastery & rivals");
            context.waitTicks(3);
            context.takeScreenshot("release-guidebook-mastery");
            context.clickScreenButton("Topics");
            context.waitTicks(3);
            clickGuideTopic(context, "Trust & teams");
            context.waitTicks(3);
            context.takeScreenshot("release-guidebook-topic-jump");
            context.clickScreenButton("Search");
            context.runOnClient(client -> client.gui.screen().children().stream()
                    .filter(EditBox.class::isInstance).map(EditBox.class::cast)
                    .findFirst().orElseThrow().setValue("Astral Daggers"));
            context.waitTicks(3);
            context.takeScreenshot("release-guidebook-search-preview");
            context.clickScreenButton("Gems / Astra Gem");
            context.waitTicks(3);
            context.takeScreenshot("release-guidebook-search-jump");
            context.clickScreenButton("Home");
            context.waitTicks(3);
            context.takeScreenshot("release-guidebook-return-home");
            context.setScreen(() -> null);

            context.setScreen(() -> new GemsConfigScreen(null));
            context.waitTicks(3);
            context.takeScreenshot("release-config-general");
            context.getInput().setCursorPos(400, 250);
            context.waitTicks(30);
            context.takeScreenshot("release-config-label-help");
            context.clickScreenButton("Elemental");
            context.waitTicks(3);
            context.takeScreenshot("release-config-gem-controls");
            context.clickScreenButton("More tools");
            context.waitTicks(3);
            context.takeScreenshot("release-config-more-tools");
            context.clickScreenButton("Back");
            context.waitTicks(3);
            context.setScreen(() -> null);

            casinoVisualFixtures(context);
            if (Boolean.getBoolean("gems.focusedUiClientGate")) return;

            BlockPos start = world.getServer().computeOnServer(server ->
                    world.getConnection().getServerPlayer().blockPosition().offset(-15, -5, -15));
            BlockPos hall = start;
            BlockPos inkbound = start.offset(0, 0, 112);
            BlockPos court = start.offset(112, 0, 112);
            BlockPos lostWadi = start.offset(0, 0, 224);
            BlockPos oasis = lostWadi.offset(13, 0, -21);
            world.getServer().runOnServer(server -> {
                place(world, "town_hall", hall);
                place(world, "inkbound_garden", inkbound);
                place(world, "unwritten_court", court);
                place(world, "unwritten_stewards_annex", court.offset(46, 0, 13));
                place(world, "unwritten_mask_archive", court.offset(-20, 0, 13));
                place(world, "omar_lost_quarter", lostWadi);
                place(world, "omar_return_oasis", oasis);
                var player = world.getConnection().getServerPlayer();
                var level = player.level();
                if (!level.getBlockState(lostWadi.offset(23, 4, 0)).is(DomainBlocks.LOST_TILE)
                        || !level.getBlockState(oasis.offset(10, 4, 20)).is(DomainBlocks.LOST_TILE)
                        || !level.getBlockState(oasis.offset(10, 6, 10)).is(Blocks.WATER)) {
                    throw new IllegalStateException("Omar oasis connection or imported basin did not place");
                }
                if (!level.getBlockState(court.offset(23, 5, 32)).is(DomainBlocks.COURT_DAIS)
                        || !level.getBlockState(court.offset(18, 5, 23)).is(DomainBlocks.COURT_WEST_MARK)
                        || !level.getBlockState(court.offset(28, 5, 23)).is(DomainBlocks.COURT_EAST_MARK)) {
                    throw new IllegalStateException("Finale markers are absent from the Court template");
                }
                BlockPos board = hall.offset(20, 6, 9);
                BlockPos council = hall.offset(15, 6, 9);
                BlockPos angleDesk = hall.offset(10, 6, 9);
                BlockPos returnBoard = hall.offset(5, 6, 20);
                BlockPos practiceStage = hall.offset(25, 6, 20);
                BlockPos routeDesk = hall.offset(10, 6, 20);
                BlockPos rescueDispatch = hall.offset(15, 6, 20);
                BlockPos stageDesk = hall.offset(20, 6, 20);
                if (!level.getBlockState(board).is(DomainBlocks.TOWNHALL_TOURNAMENT_BOARD)
                        || !level.getBlockState(council).is(DomainBlocks.TOWNHALL_STORY_COUNCIL)
                        || !level.getBlockState(angleDesk).is(DomainBlocks.TOWNHALL_ANGLE_DESK)
                        || !level.getBlockState(returnBoard).is(DomainBlocks.TOWNHALL_RETURN_BOARD)
                        || !level.getBlockState(practiceStage).is(DomainBlocks.TOWNHALL_PRACTICE_STAGE)
                        || !level.getBlockState(routeDesk).is(DomainBlocks.TOWNHALL_ROUTE_DESK)
                        || !level.getBlockState(rescueDispatch).is(DomainBlocks.TOWNHALL_RESCUE_DISPATCH)
                        || !level.getBlockState(stageDesk).is(DomainBlocks.TOWNHALL_STAGE_DESK)) {
                    throw new IllegalStateException("Townhall board placement does not match the template");
                }
                var callback = UseBlockCallback.EVENT.invoker();
                if (callback.interact(player, level, InteractionHand.MAIN_HAND, hit(board))
                        != InteractionResult.SUCCESS_SERVER
                        || callback.interact(player, level, InteractionHand.MAIN_HAND, hit(council))
                        != InteractionResult.SUCCESS_SERVER) {
                    throw new IllegalStateException("Townhall board callback ignored a use");
                }
                if (callback.interact(player, level, InteractionHand.MAIN_HAND, hit(rescueDispatch))
                        != InteractionResult.SUCCESS_SERVER
                        || callback.interact(player, level, InteractionHand.MAIN_HAND, hit(stageDesk))
                        != InteractionResult.SUCCESS_SERVER
                        || StageOpeningState.get(server).pending() != null) {
                    throw new IllegalStateException("Locked boss townhall services changed state");
                }
                CampaignPersistentState.get(server).recordVictory(
                        List.of(player.getUUID()), LandmarkKey.REDLINE);
                CampaignPersistentState.get(server).recordVictory(
                        List.of(player.getUUID()), LandmarkKey.UNWRITTEN);
                if (callback.interact(player, level, InteractionHand.MAIN_HAND, hit(rescueDispatch))
                        != InteractionResult.SUCCESS_SERVER) {
                    throw new IllegalStateException("Rescue Dispatch ignored the world unlock");
                }
                boolean hasBell = false;
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                    hasBell |= player.getInventory().getItem(slot).is(AdventureItems.DISPATCH_BELL);
                }
                player.setShiftKeyDown(true);
                InteractionResult staged = callback.interact(player, level,
                        InteractionHand.MAIN_HAND, hit(stageDesk));
                player.setShiftKeyDown(false);
                if (!hasBell || staged != InteractionResult.SUCCESS_SERVER
                        || StageOpeningState.get(server).pending() == null) {
                    throw new IllegalStateException("Boss townhall service did not open for the world");
                }
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.setNoGravity(true);
            });
            view(world, context, hall, 13.5, 5, 6.5, 0.0F, 8.0F,
                    "release-townhall-central-aisle");
            view(world, context, hall, 15.5, 8, 19.5, 180.0F, 15.0F,
                    "release-townhall-service-row");
            view(world, context, hall, 5.5, 5, 17.5, 0.0F, 0.0F,
                    "release-townhall-return-board");
            view(world, context, hall, 17.5, 8, 10.5, 0.0F, 15.0F,
                    "release-townhall-practice-route-row");
            view(world, context, hall, 10.5, 5, 17.5, 0.0F, 0.0F,
                    "release-townhall-route-desk");
            view(world, context, hall, 17.5, 5, 23.5, 180.0F, 5.0F,
                    "release-townhall-rescue-stage-row");
            view(world, context, hall, 15.5, 5, 17.5, 0.0F, 0.0F,
                    "release-townhall-rescue-dispatch");
            view(world, context, hall, 20.5, 5, 17.5, 0.0F, 0.0F,
                    "release-townhall-stage-desk");
            view(world, context, hall, 25.5, 5, 17.5, 0.0F, 0.0F,
                    "release-townhall-practice-stage");
            view(world, context, inkbound, 10.5, 9, 39.0, 180.0F, 10.0F,
                    "release-inkbound-garden");
            view(world, context, court, 23.5, 22, 62.0, 180.0F, 22.0F,
                    "release-unwritten-court");
            view(world, context, court, 23.5, 13, 37.0, 180.0F, 32.0F,
                    "release-unwritten-markers");
            view(world, context, lostWadi, 23.5, 12, 14.0, 180.0F, 12.0F,
                    "release-omar-oasis-from-quarter");
            view(world, context, oasis, 10.5, 8, 18.5, 180.0F, 7.0F,
                    "release-omar-oasis-entry");
        }
        naturalOmarOasis(context);
        naturalOthmanLibrary(context);
        naturalOthmanLibrarySecondSeed(context);
        naturalAliSplitstone(context);
    }

    /** Staged renderer captures use production game IDs and solo round state, not live settlement. */
    private static void casinoVisualFixtures(ClientGameTestContext context) {
        List<CasinoViewPayload.Choice> tables = new ArrayList<>();
        for (CasinoGame game : CasinoGame.values()) {
            tables.add(new CasinoViewPayload.Choice("preview_" + game.id(),
                    game.title() + " · " + game.stake() + " E"));
        }
        tables.add(new CasinoViewPayload.Choice("tournament", "Daily free tournament"));
        captureCasino(context, new CasinoViewPayload(UUID.randomUUID(), 1, "LOBBY", "lobby",
                "Ragab's Casino", "Choose a game to see its rules and stake.", "Choose a table.",
                0, 16, List.of(), List.of(), List.of(), List.copyOf(tables)),
                "release-casino-six-table-lobby");

        for (CasinoGame game : CasinoGame.values()) {
            if (game == CasinoGame.COOP) {
                captureCasino(context, new CasinoViewPayload(UUID.randomUUID(), 1, "COOP_PLAY",
                        game.id(), game.title(), game.rules(),
                        "Both players are seated. Share your colors.", game.stake(), 16,
                        List.of("Your color: Ruby",
                                "Tell your partner your color. Choose the one they see."),
                        List.of(), List.of(),
                        List.of(new CasinoViewPayload.Choice("guess_ruby", "Ruby"),
                                new CasinoViewPayload.Choice("guess_jade", "Jade"),
                                new CasinoViewPayload.Choice("guess_azure", "Azure"))),
                        "release-casino-coop-round");
                continue;
            }
            CasinoRound round = activeCasinoRound(game);
            CasinoViewPayload payload = roundPayload(game, round, false);
            captureCasino(context, payload, game == CasinoGame.CARDS
                    ? "release-casino-round-fixture" : "release-casino-" + game.id() + "-round");
            if (game == CasinoGame.CARDS) {
                context.clickScreenButton("Show rules");
                context.waitTicks(3);
                context.takeScreenshot("release-casino-rules-fixture");
            }
        }

        List<CasinoViewPayload.Choice> featured = new ArrayList<>();
        for (CasinoGame game : List.of(CasinoGame.RPS, CasinoGame.CARDS,
                CasinoGame.WHEEL, CasinoGame.COOP)) {
            featured.add(new CasinoViewPayload.Choice("challenge_" + game.id(),
                    game.title() + (game == CasinoGame.RPS ? " · won"
                            : game == CasinoGame.COOP ? " · duo" : " · solo")));
        }
        featured.add(new CasinoViewPayload.Choice("all_games", "Regular games"));
        captureCasino(context, new CasinoViewPayload(UUID.randomUUID(), 1,
                "TOURNAMENT_LOBBY", "lobby", "Daily Tournament",
                "Win three different featured tables together. The Signal Table needs two players; "
                        + "the three solo tables are enough for one player. No emeralds change hands.",
                "Choose a featured table.", 0, 16,
                List.of("Day 1: 1/3 featured tables won for the world."),
                List.of(), List.of(), List.copyOf(featured)),
                "release-casino-tournament-fixture");

        CasinoGame game = CasinoGame.CARDS;
        String freeRules = "Get closer to 21 than the dealer without going over. "
                + "Hit or stand; the dealer draws to 17.";
        captureCasino(context, new CasinoViewPayload(UUID.randomUUID(), 1, "PREVIEW", game.id(),
                "Free: " + game.title(), freeRules,
                "Free round. Meet the table's goal to add it once to today's world board.",
                0, 16, List.of("Free entry; one world point per featured table.",
                        "Idle 10 min: round closes without a score."),
                List.of(), List.of(),
                List.of(new CasinoViewPayload.Choice("commit", "Play free round"),
                        new CasinoViewPayload.Choice("back", "Featured tables"))),
                "release-casino-tournament-entry-fixture");

        CasinoRound freeRound = activeCasinoRound(game);
        captureCasino(context, roundPayload(game, freeRound, true),
                "release-casino-tournament-round-fixture");
        CasinoRound wonRound = winningCasinoRound(game);
        captureCasino(context, new CasinoViewPayload(UUID.randomUUID(), 1, "RESULT", game.id(),
                "Free: " + game.title(), freeRules,
                "Twenty-One added to today's board. 3/3 tables won; no emeralds changed hands.",
                0, 16, wonRound.board(), wonRound.playerCardSprites(), wonRound.dealerCardSprites(),
                List.of(new CasinoViewPayload.Choice("lobby", "Featured tables"))),
                "release-casino-tournament-result-fixture");
        captureLedgerLayouts(context);
        captureCasinoResultReveals(context);
        context.setScreen(() -> null);
    }

    /** Test-only view transitions; the round computes its result, but no wager is settled. */
    private static void captureCasinoResultReveals(ClientGameTestContext context) {
        CasinoRound wheel = new CasinoRound(CasinoGame.WHEEL, new Random() {
            @Override
            public int nextInt(int bound) { return 23; }
        });
        captureRoundToResult(context, CasinoGame.WHEEL, wheel, "red", "wheel");

        CasinoRound reels = new CasinoRound(CasinoGame.REELS, new Random() {
            private final int[] draws = {2, 0, 1, 2, 2};
            private int next;
            @Override
            public int nextInt(int bound) { return draws[next++]; }
        });
        captureRoundToResult(context, CasinoGame.REELS, reels, "hold_0", "reels");

        context.clickScreenButton("Motion: On");
        CasinoRound instant = new CasinoRound(CasinoGame.WHEEL, new Random() {
            @Override
            public int nextInt(int bound) { return 0; }
        });
        UUID instantSession = UUID.randomUUID();
        context.runOnClient(client -> CasinoScreen.show(client,
                fixtureView(instantSession, 1, "ROUND", CasinoGame.WHEEL, instant)));
        context.waitTicks(1);
        if (!instant.play("red")) throw new IllegalStateException("Instant Wheel fixture rejected a visible call");
        context.runOnClient(client -> CasinoScreen.show(client,
                fixtureView(instantSession, 2, "RESULT", CasinoGame.WHEEL, instant)));
        context.waitTicks(1);
        context.takeScreenshot("release-casino-wheel-instant-result");
        context.clickScreenButton("Motion: Off");

        CasinoRound groupQuestion = new CasinoRound(CasinoGame.TRIVIA, new Random() {
            @Override
            public int nextInt(int bound) { return 8; }
        });
        if (!groupQuestion.result().equals("Who asked, \"Do we have a tutorial for this group?\"")) {
            throw new IllegalStateException("The sourced group Trivia question was not selected");
        }
        captureCasino(context, roundPayload(CasinoGame.TRIVIA, groupQuestion, false),
                "release-casino-group-trivia-round");
        System.out.println("CASINO_REVEAL_VISUAL_CAPTURE_ONLY sameSession=wheel,reels"
                + " motion=early,settled instant=wheel groupTrivia=attributed serverSettlement=unverified");
    }

    private static void captureRoundToResult(ClientGameTestContext context, CasinoGame game,
            CasinoRound round, String choice, String suffix) {
        UUID session = UUID.randomUUID();
        context.runOnClient(client -> CasinoScreen.show(client,
                fixtureView(session, 1, "ROUND", game, round)));
        context.waitTicks(1);
        context.takeScreenshot("release-casino-" + suffix + "-before-result");
        if (!round.play(choice) || !round.finished()) {
            throw new IllegalStateException("The " + suffix + " result fixture did not finish");
        }
        context.runOnClient(client -> CasinoScreen.show(client,
                fixtureView(session, 2, "RESULT", game, round)));
        context.waitTicks(1);
        context.takeScreenshot("release-casino-" + suffix + "-revealing");
        context.waitTicks(16);
        context.takeScreenshot("release-casino-" + suffix + "-settled");
    }

    private static CasinoViewPayload fixtureView(UUID session, int revision, String phase,
            CasinoGame game, CasinoRound round) {
        List<CasinoViewPayload.Choice> choices = phase.equals("RESULT")
                ? List.of(new CasinoViewPayload.Choice("lobby", "Another game"))
                : round.choices().stream()
                        .map(choice -> new CasinoViewPayload.Choice(choice.id(), choice.label())).toList();
        return new CasinoViewPayload(session, revision, phase, game.id(), game.title(),
                game.rules(), round.result(), game.stake(), 16, round.board(),
                round.playerCardSprites(), round.dealerCardSprites(), choices);
    }

    /** Visual-only payloads: these do not prove server settlement or a second player's turn. */
    private static void captureLedgerLayouts(ClientGameTestContext context) {
        int[] originalSize = context.computeOnClient(client -> new int[] {
                client.getWindow().getWidth(), client.getWindow().getHeight(),
                client.options.guiScale().get()});
        CasinoGame solo = CasinoGame.CARDS;
        CasinoGame pair = CasinoGame.COOP;
        for (int[] size : List.of(new int[] {854, 480}, new int[] {1280, 720})) {
            context.runOnClient(client -> {
                client.getWindow().setWindowed(size[0], size[1]);
                client.options.guiScale().set(2);
                client.resizeGui();
            });
            context.waitTicks(5);
            String suffix = size[0] + "x" + size[1];
            captureCasino(context, new CasinoViewPayload(UUID.randomUUID(), 1, "PREVIEW", solo.id(),
                    solo.title(), solo.rules(), "Read the rules, then commit 4 emeralds.",
                    solo.stake(), 16, List.of(), List.of(), List.of(),
                    List.of(new CasinoViewPayload.Choice("commit", "Stake 4 emeralds"),
                            new CasinoViewPayload.Choice("back", "All games"))),
                    "release-casino-ledger-preview-staged-" + suffix);
            captureCasino(context, roundPayload(solo, activeCasinoRound(solo), false),
                    "release-casino-ledger-active-staged-" + suffix);
            captureCasino(context, new CasinoViewPayload(UUID.randomUUID(), 1, "COOP_WAIT", pair.id(),
                    pair.title(), pair.rules(), "Waiting for a second player. No stake taken yet.",
                    pair.stake(), 16, List.of("No emeralds have been taken yet."),
                    List.of(), List.of(), List.of(new CasinoViewPayload.Choice("back", "Leave seat"))),
                    "release-casino-ledger-partner-wait-staged-" + suffix);
            captureCasino(context, new CasinoViewPayload(UUID.randomUUID(), 1, "COOP_PLAY", pair.id(),
                    pair.title(), pair.rules(), "Waiting for your partner's guess.",
                    pair.stake(), 14, List.of("Your color: Ruby"), List.of(), List.of(), List.of()),
                    "release-casino-ledger-partner-turn-staged-" + suffix);
        }
        context.runOnClient(client -> {
            client.getWindow().setWindowed(originalSize[0], originalSize[1]);
            client.options.guiScale().set(originalSize[2]);
            client.resizeGui();
        });
        System.out.println("CASINO_LEDGER_VISUAL_CAPTURE_ONLY sizes=854x480,1280x720"
                + " states=PREVIEW,ROUND,COOP_WAIT,COOP_PLAY serverRound=unverified");
    }

    private static CasinoRound activeCasinoRound(CasinoGame game) {
        for (int seed = 1; seed <= 100; seed++) {
            CasinoRound round = new CasinoRound(game, new Random(seed));
            if (!round.finished()) return round;
        }
        throw new IllegalStateException("No active visual round for " + game.id());
    }

    private static CasinoRound winningCasinoRound(CasinoGame game) {
        for (int seed = 1; seed <= 100; seed++) {
            CasinoRound round = new CasinoRound(game, new Random(seed));
            if (!round.finished()) round.play("stand");
            if (round.finished() && round.payout() > round.stake()) return round;
        }
        throw new IllegalStateException("No winning visual round for " + game.id());
    }

    private static CasinoViewPayload roundPayload(CasinoGame game, CasinoRound round, boolean free) {
        List<CasinoViewPayload.Choice> choices = round.choices().stream()
                .map(choice -> new CasinoViewPayload.Choice(choice.id(), choice.label())).toList();
        String rules = free
                ? "Get closer to 21 than the dealer without going over. Hit or stand; the dealer draws to 17."
                : game.rules();
        return new CasinoViewPayload(UUID.randomUUID(), 1, "ROUND", game.id(),
                free ? "Free: " + game.title() : game.title(), rules, round.result(),
                free ? 0 : game.stake(), 16, round.board(), round.playerCardSprites(),
                round.dealerCardSprites(), choices);
    }

    private static void captureCasino(ClientGameTestContext context,
            CasinoViewPayload payload, String name) {
        context.setScreen(() -> new CasinoScreen(payload));
        context.waitTicks(3);
        context.takeScreenshot(name);
    }

    /** Seeded natural terrain capture for the raised oasis edge, separate from flat placements. */
    private static void naturalOmarOasis(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(settings -> {
                    settings.setSeed("SecondChanceBiomeSmoke");
                    settings.setGenerateStructures(true);
                }).create()) {
            world.getConnection().waitForChunksRender();
            OasisSite site = world.getServer().computeOnServer(server -> {
                ServerLevel level = server.overworld();
                var tag = TagKey.<Structure>create(Registries.STRUCTURE,
                        Identifier.fromNamespaceAndPath("gems", "atlas_target/omar_lost_quarter"));
                BlockPos located = level.findNearestMapStructure(tag, new BlockPos(0, 80, 0), 128, false);
                if (located == null) throw new IllegalStateException("No natural Omar quarter in seeded reach");
                List<NaturalPiece> pieces = naturalPieces(level, tag, located);
                BoundingBox root = requirePiece(pieces, "omar_lost_quarter_caravan_v2");
                BoundingBox oasis = requirePiece(pieces, "omar_return_oasis_caravan_v2");
                loadPiece(level, oasis);
                BlockPos sign = null;
                BlockPos chest = null;
                int water = 0;
                int path = 0;
                for (int x = oasis.minX(); x <= oasis.maxX(); x++) {
                    for (int z = oasis.minZ(); z <= oasis.maxZ(); z++) {
                        for (int y = oasis.minY() + 4; y <= oasis.minY() + 7; y++) {
                            BlockPos pos = new BlockPos(x, y, z);
                            var state = level.getBlockState(pos);
                            if (state.is(Blocks.OAK_SIGN)) sign = pos;
                            if (state.is(Blocks.CHEST)) chest = pos;
                            if (state.is(Blocks.WATER)) water++;
                            if (state.is(DomainBlocks.LOST_TILE)) path++;
                        }
                    }
                }
                if (sign == null || chest == null || sign.distSqr(chest) > 15 * 15
                        || water < 20 || path < 30) {
                    throw new IllegalStateException("Natural Omar oasis lacks its public sign, loot, basin or tiled route: "
                            + oasis + " sign=" + sign + " chest=" + chest
                            + " water=" + water + " path=" + path);
                }
                int centerX = (oasis.minX() + oasis.maxX()) / 2;
                int centerZ = (oasis.minZ() + oasis.maxZ()) / 2;
                int rootX = (root.minX() + root.maxX()) / 2;
                int rootZ = (root.minZ() + root.maxZ()) / 2;
                int outwardX = Math.abs(centerX - rootX) > Math.abs(centerZ - rootZ)
                        ? Integer.signum(centerX - rootX) : 0;
                int outwardZ = outwardX == 0 ? Integer.signum(centerZ - rootZ) : 0;
                if (outwardX == 0 && outwardZ == 0) {
                    throw new IllegalStateException("Omar oasis has no exterior side: " + oasis);
                }
                int lowTerrain = 0;
                for (int distance : new int[]{2, 5, 8}) {
                    int x = (outwardX < 0 ? oasis.minX() : outwardX > 0 ? oasis.maxX() : centerX)
                            + outwardX * distance;
                    int z = (outwardZ < 0 ? oasis.minZ() : outwardZ > 0 ? oasis.maxZ() : centerZ)
                            + outwardZ * distance;
                    level.getChunk(x >> 4, z >> 4);
                    if (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
                            < sign.getY()) lowTerrain++;
                }
                if (lowTerrain == 0) {
                    throw new IllegalStateException("Omar's raised oasis edge is buried by natural terrain: " + oasis);
                }
                System.out.println("OMAR_OASIS_NATURAL root=" + root + " oasis=" + oasis
                        + " sign=" + sign + " chest=" + chest + " lowerExterior=" + lowTerrain);
                return new OasisSite(oasis, centerX, centerZ, outwardX, outwardZ);
            });
            int wideX = site.centerX() + site.outwardX() * 34 - site.outwardZ() * 16;
            int wideZ = site.centerZ() + site.outwardZ() * 34 + site.outwardX() * 16;
            naturalSurfaceView(world, context, wideX, wideZ, site.oasis().maxY() + 8, 16,
                    faceYaw(wideX, wideZ, site.centerX(), site.centerZ()), 21.0F,
                    "release-omar-oasis-natural-wide");
            int edgeX = site.centerX() + site.outwardX() * 23;
            int edgeZ = site.centerZ() + site.outwardZ() * 23;
            naturalSurfaceView(world, context, edgeX, edgeZ, site.oasis().minY() + 10, 8,
                    faceYaw(edgeX, edgeZ, site.centerX(), site.centerZ()), 13.0F,
                    "release-omar-oasis-natural-edge");
        }
    }

    /** Two natural seeds exercise the reading hall and its public bank entry. */
    private static void naturalOthmanLibrary(ClientGameTestContext context) {
        naturalOthmanLibrarySeed(context, "SecondChanceBiomeSmokeC", "c");
    }

    private static void naturalOthmanLibrarySecondSeed(ClientGameTestContext context) {
        naturalOthmanLibrarySeed(context, "SecondChanceBiomeSmokeB", "b");
    }

    private static void naturalOthmanLibrarySeed(ClientGameTestContext context,
            String seed, String label) {
        try (TestSingleplayerContext world = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(settings -> {
                    settings.setSeed(seed);
                    settings.setGenerateStructures(true);
                }).create()) {
            world.getConnection().waitForChunksRender();
            ArchiveSite site = world.getServer().computeOnServer(server -> {
                ServerLevel level = server.overworld();
                var tag = TagKey.<Structure>create(Registries.STRUCTURE,
                        Identifier.fromNamespaceAndPath("gems", "themed_target/othman_library"));
                BlockPos located = level.findNearestMapStructure(tag,
                        new BlockPos(0, 80, 0), 128, false);
                if (located == null) {
                    throw new IllegalStateException("No themed Othman library in seeded reach: " + seed);
                }
                List<NaturalPiece> pieces = naturalPieces(level, tag, located);
                BoundingBox root = requirePiece(pieces, "othman_library_dry_arrival_v3");
                BoundingBox wing = requirePiece(pieces, "othman_study_dry_arrival_wing_v3");
                BoundingBox catalog = pieces.stream()
                        .filter(piece -> piece.template().contains("othman_catalog"))
                        .map(NaturalPiece::box).findFirst().orElseThrow(() ->
                                new IllegalStateException("Othman catalog annex did not generate"));
                loadPiece(level, root);
                loadPiece(level, wing);
                loadPiece(level, catalog);
                var residents = level.getEntitiesOfClass(FriendEntity.class,
                        new AABB(root.minX() - 8, root.minY(), root.minZ() - 8,
                                root.maxX() + 8, root.maxY() + 8, root.maxZ() + 8),
                        friend -> "othman".equals(friend.friendId()));
                if (residents.size() != 1
                        || !level.getBlockState(residents.getFirst().blockPosition().below()).isSolid()) {
                    throw new IllegalStateException("Natural Othman reading hall lacks one grounded resident: "
                            + root + " residents=" + residents.size());
                }
                int signs = 0;
                int inlay = 0;
                for (int x = root.minX(); x <= root.maxX(); x++) {
                    for (int z = root.minZ(); z <= root.maxZ(); z++) {
                        BlockPos sign = new BlockPos(x, root.minY() + 5, z);
                        if (level.getBlockState(sign).is(Blocks.OAK_SIGN)) signs++;
                        if (level.getBlockState(sign.below()).is(DomainBlocks.ARCHIVE_INLAY)) inlay++;
                    }
                }
                if (signs < 1 || inlay < 20) {
                    throw new IllegalStateException("Natural Othman hall lacks its sign or inlaid public floor: "
                            + root + " signs=" + signs + " inlay=" + inlay);
                }
                boolean alongX = wing.getXSpan() > wing.getZSpan();
                int rootAxis = alongX ? (root.minX() + root.maxX()) / 2
                        : (root.minZ() + root.maxZ()) / 2;
                int low = alongX ? wing.minX() : wing.minZ();
                int high = alongX ? wing.maxX() : wing.maxZ();
                boolean nearLow = Math.abs(rootAxis - low) < Math.abs(rootAxis - high);
                int towardRoot = nearLow ? -1 : 1;
                int far = nearLow ? high : low;
                int across = alongX ? (wing.minZ() + wing.maxZ()) / 2
                        : (wing.minX() + wing.maxX()) / 2;
                BlockPos bank = null;
                for (int distance = 0; distance <= 6 && bank == null; distance++) {
                    int axis = far + towardRoot * distance;
                    for (int offset = -1; offset <= 1 && bank == null; offset++) {
                        int x = alongX ? axis : across + offset;
                        int z = alongX ? across + offset : axis;
                        for (int y = wing.minY(); y <= wing.minY() + 4; y++) {
                            BlockPos pos = new BlockPos(x, y, z);
                            if (level.getBlockState(pos).is(DomainBlocks.ARCHIVE_INLAY)
                                    && level.getBlockState(pos.above()).getCollisionShape(level,
                                            pos.above()).isEmpty()
                                    && level.getBlockState(pos.above(2)).getCollisionShape(level,
                                            pos.above(2)).isEmpty()) {
                                bank = pos;
                                break;
                            }
                        }
                    }
                }
                if (bank == null) {
                    throw new IllegalStateException("Othman arrival wing has no clear bank landing: " + wing);
                }
                int dx = alongX ? towardRoot : 0;
                int dz = alongX ? 0 : towardRoot;
                BlockPos inner = bank.offset(dx * 12, 0, dz * 12);
                boolean markedInnerDeck = false;
                for (int y = wing.minY(); y <= wing.minY() + 5; y++) {
                    if (level.getBlockState(new BlockPos(inner.getX(), y, inner.getZ()))
                            .is(DomainBlocks.ARCHIVE_INLAY)) markedInnerDeck = true;
                }
                if (!markedInnerDeck) {
                    throw new IllegalStateException("Othman arrival wing does not connect its bank inlay inward: "
                            + wing + " bank=" + bank);
                }
                BlockPos shore = null;
                for (int distance = 1; distance <= 5 && shore == null; distance++) {
                    int x = bank.getX() - dx * distance;
                    int z = bank.getZ() - dz * distance;
                    level.getChunk(x >> 4, z >> 4);
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    BlockPos feet = new BlockPos(x, y, z);
                    if (Math.abs(y - bank.getY() - 1) <= 2
                            && level.getBlockState(feet.below()).isSolid()
                            && level.getBlockState(feet).getFluidState().isEmpty()
                            && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                            && level.getBlockState(feet.above())
                                    .getCollisionShape(level, feet.above()).isEmpty()) {
                        shore = feet;
                    }
                }
                if (shore == null) {
                    throw new IllegalStateException("Othman bank landing has no clear natural shore entry: "
                            + bank + " wing=" + wing);
                }
                BlockPos resident = residents.getFirst().blockPosition();
                System.out.println("OTHMAN_ARRIVAL_" + label.toUpperCase() + " root=" + root
                        + " wing=" + wing + " bank=" + bank + " shore=" + shore);
                return new ArchiveSite(root, wing, catalog, resident, bank, shore, dx, dz);
            });

            int x = site.resident().getX();
            int z = site.resident().getZ();
            int cameraFloor = site.resident().getY() + 20;
            if ("c".equals(label)) {
                naturalSurfaceView(world, context, x, z + 42, cameraFloor, 16,
                        faceYaw(x, z + 42, x, z), 19.0F,
                        "release-othman-library-natural-south");
                naturalSurfaceView(world, context, x + 42, z, cameraFloor, 16,
                        faceYaw(x + 42, z, x, z), 19.0F,
                        "release-othman-library-natural-east");
                naturalSurfaceView(world, context, x, z - 42, cameraFloor, 16,
                        faceYaw(x, z - 42, x, z), 19.0F,
                        "release-othman-library-natural-north");
                naturalSurfaceView(world, context, x - 42, z, cameraFloor, 16,
                        faceYaw(x - 42, z, x, z), 19.0F,
                        "release-othman-library-natural-west");
                naturalSurfaceView(world, context, x, z + 60, 0, 3,
                        faceYaw(x, z + 60, x, z), 1.0F,
                        "release-othman-library-natural-south-ground");
            } else {
                int rootX = (site.root().minX() + site.root().maxX()) / 2;
                int rootZ = (site.root().minZ() + site.root().maxZ()) / 2;
                naturalSurfaceView(world, context, rootX, site.root().maxZ() + 42,
                        site.root().maxY() + 4, 20,
                        faceYaw(rootX, site.root().maxZ() + 42, rootX, rootZ), 19.0F,
                        "release-othman-library-seed-b-approach");
                naturalSurfaceView(world, context, rootX, site.root().maxZ() + 20,
                        site.root().minY() + 10, 8,
                        faceYaw(rootX, site.root().maxZ() + 20, rootX, rootZ), 20.0F,
                        "release-othman-library-seed-b-shore");
                int catalogX = (site.catalog().minX() + site.catalog().maxX()) / 2;
                int catalogZ = (site.catalog().minZ() + site.catalog().maxZ()) / 2;
                int outX = Integer.signum(catalogX - rootX);
                int outZ = Integer.signum(catalogZ - rootZ);
                naturalSurfaceView(world, context, catalogX + outX * 18,
                        catalogZ + outZ * 18, 0, 1,
                        faceYaw(catalogX + outX * 18, catalogZ + outZ * 18,
                                catalogX, catalogZ), 3.0F,
                        "othman-probe-b-east-hill");
                naturalSurfaceView(world, context, catalogX + outX * 8,
                        catalogZ + outZ * 8, 0, 1,
                        faceYaw(catalogX + outX * 8, catalogZ + outZ * 8,
                                catalogX, catalogZ), 3.0F,
                        "othman-probe-b-east-rotunda");
            }
            int bankX = site.bank().getX();
            int bankZ = site.bank().getZ();
            int outwardX = -site.dx();
            int outwardZ = -site.dz();
            String prefix = "c".equals(label) ? "seed-c" : "seed-b";
            naturalSurfaceView(world, context, bankX + outwardX * 12,
                    bankZ + outwardZ * 12, 0, 5,
                    faceYaw(bankX + outwardX * 12, bankZ + outwardZ * 12, x, z),
                    8.0F, "release-othman-library-" + prefix + "-bridge-bank");
            naturalSurfaceView(world, context, bankX + outwardX * 4,
                    bankZ + outwardZ * 4, 0, 2,
                    faceYaw(bankX + outwardX * 4, bankZ + outwardZ * 4, x, z),
                    3.0F, "release-othman-library-" + prefix + "-bridge-approach");
            if ("c".equals(label)) {
                naturalSurfaceView(world, context, bankX, bankZ, 0, 2,
                        faceYaw(bankX, bankZ, x, z), 3.0F,
                        "othman-probe-c-west-deck");
            } else {
                naturalSurfaceView(world, context, bankX + outwardX * 22,
                        bankZ + outwardZ * 22, 0, 4,
                        faceYaw(bankX + outwardX * 22, bankZ + outwardZ * 22, x, z),
                        -16.0F, "release-othman-library-seed-b-bridge-south");
            }
            world.getServer().runOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                player.setNoGravity(false);
                player.teleportTo(player.level(), site.shore().getX() + 0.5,
                        site.shore().getY() + 0.1, site.shore().getZ() + 0.5,
                        EnumSet.noneOf(Relative.class),
                        (float) Math.toDegrees(Math.atan2(-site.dx(), site.dz())),
                        0.0F, false);
            });
            context.waitTicks(10);
            world.getConnection().waitForChunksRender();
            context.getInput().holdKey(options -> options.keyUp);
            context.getInput().holdKey(options -> options.keyJump);
            context.waitTicks("c".equals(label) ? 120 : 110);
            context.getInput().releaseKey(options -> options.keyUp);
            context.getInput().releaseKey(options -> options.keyJump);
            context.waitTicks(10);
            world.getServer().runOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                double progress = (player.getX() - site.shore().getX() - 0.5) * site.dx()
                        + (player.getZ() - site.shore().getZ() - 0.5) * site.dz();
                double lateral = site.dx() == 0
                        ? Math.abs(player.getX() - site.shore().getX() - 0.5)
                        : Math.abs(player.getZ() - site.shore().getZ() - 0.5);
                double minimum = "c".equals(label) ? 5.0 : 15.0;
                System.out.println("OTHMAN_BRIDGE_" + label.toUpperCase()
                        + "_WALK pos=" + player.position() + " progress=" + progress
                        + " lateral=" + lateral + " alive=" + player.isAlive());
                if (!player.isAlive() || progress < minimum || lateral > 5.0
                        || player.getY() < site.bank().getY()) {
                    throw new IllegalStateException("Player did not reach the Archive bridge on "
                            + seed + ": " + player.position());
                }
            });
            clearOverlays(context);
            context.takeScreenshot("release-othman-library-" + prefix + "-bridge-walk");
            if ("b".equals(label)) {
                walkOthmanDryApproach(context, world, site);
            }
        }
    }

    /** Walk the new dry-land gate, ramp, existing perimeter, and east portico in one trip. */
    private static void walkOthmanDryApproach(ClientGameTestContext context,
            TestSingleplayerContext world, ArchiveSite site) {
        DryRoute route = world.getServer().computeOnServer(server -> {
            ServerLevel level = server.overworld();
            BoundingBox root = site.root();
            BoundingBox wing = site.wing();
            int wx = Integer.signum(wing.getCenter().getX() - root.getCenter().getX());
            int wz = Integer.signum(wing.getCenter().getZ() - root.getCenter().getZ());
            if (Math.abs(wx) + Math.abs(wz) != 1) {
                throw new IllegalStateException("Othman dry route has no cardinal study wing: " + wing);
            }
            int px = wz;
            int pz = -wx;
            DryRoute path = new DryRoute(root.getCenter().getX(), root.getCenter().getZ(),
                    root.minY(), px, pz, wx, wz);
            BlockPos natural = path.point(-40, 20, 0);
            level.getChunk(natural.getX() >> 4, natural.getZ() >> 4);
            int naturalY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    natural.getX(), natural.getZ());
            BlockPos feet = new BlockPos(natural.getX(), naturalY, natural.getZ());
            if (!level.getBlockState(feet.below()).isSolid()
                    || !level.getBlockState(feet).getFluidState().isEmpty()
                    || !level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    || !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                    || Math.abs(naturalY - root.minY() - 2) > 1) {
                throw new IllegalStateException("Othman dry route has no standing woodland entry: " + feet);
            }
            path = path.withStart(feet);
            BlockPos previous = feet.below();
            for (int u = -39; u <= -38; u++) {
                BlockPos sample = path.point(u, 20, 0);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        sample.getX(), sample.getZ());
                BlockPos naturalFloor = new BlockPos(sample.getX(), y - 1, sample.getZ());
                if (!level.getBlockState(naturalFloor).isSolid()) {
                    throw new IllegalStateException("Othman woodland approach loses ground at "
                            + naturalFloor);
                }
                assertClearOthmanHeadroom(level, naturalFloor);
                assertOthmanStep(previous, naturalFloor);
                previous = naturalFloor;
            }
            for (int u = -37; u <= -21; u++) {
                int expectedY = root.minY() + (u <= -26 ? 1 : u <= -23 ? 2 : 3);
                for (int v : new int[] {20, 21}) {
                    BlockPos floor = path.point(u, v, expectedY);
                    level.getChunk(floor.getX() >> 4, floor.getZ() >> 4);
                    if (!(level.getBlockState(floor).is(DomainBlocks.ARCHIVE_INLAY)
                            || u == -21 && archiveWalkFloor(level, floor))) {
                        throw new IllegalStateException("Othman dry ramp lost its inlay at " + floor);
                    }
                    assertClearOthmanHeadroom(level, floor);
                    if (v == 20) {
                        assertOthmanStep(previous, floor);
                        previous = floor;
                    }
                }
            }
            for (int side : new int[] {19, 22}) {
                BlockPos gate = path.point(-35, side, root.minY() + 5);
                if (!level.getBlockState(gate).is(Blocks.SEA_LANTERN)) {
                    throw new IllegalStateException("Othman woodland gate is missing its lamp at " + gate);
                }
            }
            for (int u = -20; u <= 21; u++) {
                BlockPos floor = findOthmanWalkFloor(level, path, u, 20);
                assertOthmanStep(previous, floor);
                previous = floor;
            }
            for (int v = 19; v >= 0; v--) {
                BlockPos floor = findOthmanWalkFloor(level, path, 21, v);
                assertOthmanStep(previous, floor);
                previous = floor;
            }
            System.out.println("OTHMAN_DRY_B_ROUTE woodland=" + feet + " gate="
                    + path.point(-35, 20, root.minY() + 1) + " portico=" + previous
                    + " ramp=17x2 perimeter=62 grade<=1 headroom=3");
            return path;
        });

        BlockPos woodland = route.start();
        naturalSurfaceView(world, context, woodland.getX(), woodland.getZ(), 0, 4,
                (float) Math.toDegrees(Math.atan2(-route.px(), route.pz())), 5.0F,
                "release-othman-library-seed-b-dry-woodland");
        world.getServer().runOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            player.setNoGravity(false);
            player.teleportTo(player.level(), woodland.getX() + 0.5, woodland.getY() + 0.1,
                    woodland.getZ() + 0.5, EnumSet.noneOf(Relative.class),
                    (float) Math.toDegrees(Math.atan2(-route.px(), route.pz())), 0.0F, false);
        });
        context.waitTicks(10);
        walkOthmanLeg(context, world, route, -28.5, true, false, 130);
        clearOverlays(context);
        context.takeScreenshot("release-othman-library-seed-b-dry-gate-walk");
        walkOthmanLeg(context, world, route, -21.5, true, true, 100);
        walkOthmanLeg(context, world, route, 20.5, true, false, 320);
        world.getServer().runOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            player.teleportTo(player.level(), player.getX(), player.getY(), player.getZ(),
                    EnumSet.noneOf(Relative.class),
                    (float) Math.toDegrees(Math.atan2(route.wx(), -route.wz())), 0.0F, false);
        });
        context.waitTicks(3);
        walkOthmanLeg(context, world, route, 0.5, false, false, 180);
        context.waitTicks(6);
        world.getServer().runOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            double u = route.u(player.getX(), player.getZ());
            double v = route.v(player.getX(), player.getZ());
            if (!player.isAlive() || !player.onGround() || u < 20.0 || u > 22.5
                    || v > 1.0 || Math.abs(v) > 2.0 || player.getY() < route.baseY() + 3) {
                throw new IllegalStateException("Othman dry woodland walk did not reach the east portico: "
                        + player.position() + " u=" + u + " v=" + v);
            }
            System.out.println("OTHMAN_DRY_B_WALK pos=" + player.position()
                    + " u=" + u + " v=" + v + " fromWoodland=true");
        });
        clearOverlays(context);
        context.takeScreenshot("release-othman-library-seed-b-dry-portico-walk");
    }

    private static void walkOthmanLeg(ClientGameTestContext context,
            TestSingleplayerContext world, DryRoute route, double target,
            boolean alongPortico, boolean jump, int maxTicks) {
        context.getInput().holdKey(options -> options.keyUp);
        if (jump) context.getInput().holdKey(options -> options.keyJump);
        double progress = Double.NEGATIVE_INFINITY;
        try {
            for (int elapsed = 0; elapsed < maxTicks; elapsed += 3) {
                context.waitTicks(3);
                progress = world.getServer().computeOnServer(server -> {
                    var player = world.getConnection().getServerPlayer();
                    double u = route.u(player.getX(), player.getZ());
                    double v = route.v(player.getX(), player.getZ());
                    if (!player.isAlive() || alongPortico && Math.abs(v - 20.0) > 2.0
                            || !alongPortico && Math.abs(u - 21.0) > 2.0) {
                        throw new IllegalStateException("Othman dry walk left the route: "
                                + player.position() + " u=" + u + " v=" + v);
                    }
                    return alongPortico ? u : -v;
                });
                if (progress >= (alongPortico ? target : -target)) break;
            }
        } finally {
            context.getInput().releaseKey(options -> options.keyUp);
            if (jump) context.getInput().releaseKey(options -> options.keyJump);
        }
        if (progress < (alongPortico ? target : -target)) {
            throw new IllegalStateException("Othman dry walk stopped before waypoint: progress="
                    + progress + " target=" + target);
        }
    }

    private static BlockPos findOthmanWalkFloor(ServerLevel level, DryRoute path, int u, int v) {
        for (int y = path.baseY() + 5; y >= path.baseY(); y--) {
            BlockPos floor = path.point(u, v, y);
            if (archiveWalkFloor(level, floor)) {
                assertClearOthmanHeadroom(level, floor);
                return floor;
            }
        }
        throw new IllegalStateException("Othman perimeter floor missing at u=" + u + " v=" + v);
    }

    private static boolean archiveWalkFloor(ServerLevel level, BlockPos floor) {
        var state = level.getBlockState(floor);
        return state.is(DomainBlocks.ARCHIVE_INLAY) || state.is(DomainBlocks.ARCHIVE_LIMESTONE)
                || state.is(Blocks.POLISHED_DIORITE);
    }

    private static void assertClearOthmanHeadroom(ServerLevel level, BlockPos floor) {
        for (int rise = 1; rise <= 3; rise++) {
            BlockPos head = floor.above(rise);
            if (!level.getBlockState(head).getCollisionShape(level, head).isEmpty()
                    || !level.getFluidState(head).isEmpty()) {
                throw new IllegalStateException("Othman public route has blocked headroom at " + head);
            }
        }
    }

    private static void assertOthmanStep(BlockPos previous, BlockPos floor) {
        int step = Math.abs(previous.getX() - floor.getX())
                + Math.abs(previous.getZ() - floor.getZ());
        if (step != 1 || Math.abs(previous.getY() - floor.getY()) > 1) {
            throw new IllegalStateException("Othman dry route breaks its grade between "
                    + previous + " and " + floor);
        }
    }

    private record DryRoute(int centerX, int centerZ, int baseY,
                            int px, int pz, int wx, int wz, BlockPos start) {
        private DryRoute(int centerX, int centerZ, int baseY,
                         int px, int pz, int wx, int wz) {
            this(centerX, centerZ, baseY, px, pz, wx, wz, null);
        }

        private DryRoute withStart(BlockPos feet) {
            return new DryRoute(centerX, centerZ, baseY, px, pz, wx, wz, feet);
        }

        private BlockPos point(int u, int v, int y) {
            return new BlockPos(centerX + px * u + wx * v, y,
                    centerZ + pz * u + wz * v);
        }

        private double u(double x, double z) {
            return (x - centerX - 0.5) * px + (z - centerZ - 0.5) * pz;
        }

        private double v(double x, double z) {
            return (x - centerX - 0.5) * wx + (z - centerZ - 0.5) * wz;
        }
    }

    private record OasisSite(BoundingBox oasis, int centerX, int centerZ,
                             int outwardX, int outwardZ) { }

    private record ArchiveSite(BoundingBox root, BoundingBox wing, BoundingBox catalog,
                               BlockPos resident, BlockPos bank, BlockPos shore,
                               int dx, int dz) { }

    private record NaturalPiece(String template, BoundingBox box) { }

    private static List<NaturalPiece> naturalPieces(ServerLevel level, TagKey<Structure> tag,
                                                    BlockPos located) {
        var registry = level.getServer().registryAccess().lookupOrThrow(Registries.STRUCTURE);
        int chunkX = located.getX() >> 4;
        int chunkZ = located.getZ() >> 4;
        for (int radius = 0; radius <= 5; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                    var chunk = level.getChunk(chunkX + x, chunkZ + z);
                    for (var holder : registry.getTagOrEmpty(tag)) {
                        StructureStart start = level.structureManager()
                                .getStartForStructure(holder.value(), chunk);
                        if (start == null || !start.isValid()) continue;
                        return start.getPieces().stream().map(piece -> {
                            String template = piece instanceof PoolElementStructurePiece pooled
                                    && pooled.getElement() instanceof SinglePoolElement single
                                            ? single.getTemplateLocation().toString() : "";
                            return new NaturalPiece(template, piece.getBoundingBox());
                        }).toList();
                    }
                }
            }
        }
        throw new IllegalStateException("Located natural structure has no saved start near " + located);
    }

    private static BoundingBox requirePiece(List<NaturalPiece> pieces, String name) {
        return pieces.stream().filter(piece -> piece.template().equals("gems:" + name))
                .map(NaturalPiece::box).findFirst().orElseThrow(() ->
                        new IllegalStateException("Natural structure is missing piece " + name
                                + ": " + pieces));
    }

    private static void loadPiece(ServerLevel level, BoundingBox box) {
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
            for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                level.getChunk(x, z);
            }
        }
    }

    /** A fresh Splitstone start, searched for generated fins rather than placed templates. */
    private static void naturalAliSplitstone(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(settings -> {
                    settings.setSeed("SecondChanceAliSplitstoneVisual");
                    settings.setGenerateStructures(true);
                }).create()) {
            world.getConnection().waitForChunksRender();
            int[] site = new int[2];
            int[] needle = new int[] {Integer.MIN_VALUE, Integer.MIN_VALUE, -1};
            int[] cleft = new int[4];
            int[] sampled = new int[3];
            world.getServer().runOnServer(server -> {
                var level = world.getConnection().getServerPlayer().level();
                var target = TagKey.<Structure>create(Registries.STRUCTURE,
                        Identifier.fromNamespaceAndPath("gems", "atlas_target/ali_splitstone_cloister"));
                BlockPos start = level.findNearestMapStructure(target,
                        new BlockPos(0, 80, 0), 128, false);
                if (start == null) throw new IllegalStateException("No natural Ali cloister in seeded reach");
                site[0] = start.getX();
                site[1] = start.getZ();
                int closest = Integer.MAX_VALUE;
                for (int chunkX = (start.getX() >> 4) - 5;
                        chunkX <= (start.getX() >> 4) + 5; chunkX++) {
                    for (int chunkZ = (start.getZ() >> 4) - 5;
                            chunkZ <= (start.getZ() >> 4) + 5; chunkZ++) {
                        level.getChunk(chunkX, chunkZ);
                        for (int x = chunkX << 4; x < (chunkX << 4) + 16; x++) {
                            for (int z = chunkZ << 4; z < (chunkZ << 4) + 16; z++) {
                                int distance = (x - site[0]) * (x - site[0])
                                        + (z - site[1]) * (z - site[1]);
                                if (distance < 40 * 40 || distance >= closest) continue;
                                int top = level.getHeight(
                                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                                BlockPos crown = new BlockPos(x, top, z);
                                if (!level.getBiome(crown).is(AdventureBiomes.SPLITSTONE_HEIGHTS)) {
                                    continue;
                                }
                                sampled[0]++;
                                if (top < level.getSeaLevel() + 12) continue;
                                sampled[1]++;
                                if (!level.getBlockState(crown).is(Blocks.CALCITE)
                                        || !level.getBlockState(crown.below(2)).is(Blocks.CALCITE)) {
                                    continue;
                                }
                                sampled[2]++;
                                for (Direction side : new Direction[] {Direction.NORTH,
                                        Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                                    BlockPos shaft = crown.relative(side);
                                    if (!level.getBlockState(shaft.below(4)).is(Blocks.CALCITE)) {
                                        continue;
                                    }
                                    for (int depth = 6; depth <= 16; depth++) {
                                        if (level.getBlockState(shaft.below(depth))
                                                .is(DomainBlocks.SPLITSTONE)) {
                                            closest = distance;
                                            needle[0] = x;
                                            needle[1] = z;
                                            needle[2] = side.ordinal();
                                            break;
                                        }
                                    }
                                    if (closest == distance) break;
                                }
                            }
                        }
                    }
                }
                System.out.println("ALI_SPLITSTONE_VISUAL site=" + site[0] + "," + site[1]
                        + " needle=" + needle[0] + "," + needle[1]
                        + " distance=" + (closest == Integer.MAX_VALUE ? "none"
                                : Math.round(Math.sqrt(closest)))
                        + " biomeColumns=" + sampled[0] + " highColumns=" + sampled[1]
                        + " calciteCrowns=" + sampled[2]
                        + " siteSurface=" + level.getHeight(
                                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, site[0], site[1]));
                if (needle[2] >= 0) {
                    Direction inward = Direction.values()[needle[2]];
                    int ux = inward.getStepX();
                    int uz = inward.getStepZ();
                    int centerX = needle[0] + ux * 4;
                    int centerZ = needle[1] + uz * 4;
                    int vx = -uz;
                    int vz = ux;
                    if ((site[0] - centerX) * vx + (site[1] - centerZ) * vz < 0) {
                        vx = -vx;
                        vz = -vz;
                    }
                    for (int v = -3; v <= 3; v++) {
                        for (int u = -1; u <= 1; u++) {
                            int x = centerX + ux * u + vx * v;
                            int z = centerZ + uz * u + vz * v;
                            int y = level.getHeight(
                                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                            BlockPos feet = new BlockPos(x, y, z);
                            var floor = level.getBlockState(feet.below());
                            var feetState = level.getBlockState(feet);
                            var headState = level.getBlockState(feet.above());
                            if ((!floor.isSolid() && !floor.is(Blocks.SNOW))
                                    || !feetState.getFluidState().isEmpty()
                                    || !(feetState.isAir() || feetState.canBeReplaced())
                                    || !(headState.isAir() || headState.canBeReplaced())) {
                                throw new IllegalStateException("Ali needle cleft blocked at " + feet);
                            }
                        }
                    }
                    cleft[0] = centerX - vx * 3;
                    cleft[1] = centerZ - vz * 3;
                    cleft[2] = centerX + vx * 3;
                    cleft[3] = centerZ + vz * 3;
                    System.out.println("ALI_SPLITSTONE_CLEFT clear=true start="
                            + cleft[0] + "," + cleft[1] + " end=" + cleft[2] + "," + cleft[3]);
                }
                world.getConnection().getServerPlayer().setNoGravity(true);
            });
            int sx = site[0];
            int sz = site[1];
            context.getInput().pressKey(options -> options.keyToggleGui);
            context.waitTicks(2);
            naturalSurfaceView(world, context, sx, sz + 68, 0, 24,
                    180.0F, 20.0F, "release-ali-natural-cloister");
            if (needle[0] != Integer.MIN_VALUE) {
                int nx = needle[0];
                int nz = needle[1];
                int approachX = nx + Integer.signum(nx - sx) * 22;
                int approachZ = nz + Integer.signum(nz - sz) * 22;
                float approachYaw = faceYaw(approachX, approachZ, sx, sz);
                naturalSurfaceView(world, context, approachX, approachZ, 0, 26,
                        approachYaw, 18.0F, "release-ali-natural-needle-approach");
                naturalSurfaceView(world, context, nx + 10, nz + 10, 0, 16,
                        faceYaw(nx + 10, nz + 10, nx, nz), 12.0F,
                        "release-ali-natural-needle-close");
                naturalSurfaceView(world, context, cleft[0], cleft[1], 0, 2,
                        faceYaw(cleft[0], cleft[1], cleft[2], cleft[3]), -12.0F,
                        "release-ali-natural-cleft-ground");
                naturalSurfaceView(world, context, cleft[2], cleft[3], 0, 2,
                        faceYaw(cleft[2], cleft[3], cleft[0], cleft[1]), -12.0F,
                        "release-ali-natural-cleft-ground-reverse");
                int middleX = (cleft[0] + cleft[2]) / 2;
                int middleZ = (cleft[1] + cleft[3]) / 2;
                naturalSurfaceView(world, context, middleX, middleZ, 0, 7,
                        faceYaw(middleX, middleZ, cleft[2], cleft[3]), 22.0F,
                        "release-ali-natural-cleft-raised");
            }
            context.getInput().pressKey(options -> options.keyToggleGui);
        }
    }

    private static float faceYaw(int fromX, int fromZ, int toX, int toZ) {
        return (float) Math.toDegrees(Math.atan2(fromX - toX, toZ - fromZ));
    }

    private static void naturalSurfaceView(TestSingleplayerContext world,
            ClientGameTestContext context, int x, int z, int minY, int lift,
            float yaw, float pitch, String name) {
        world.getServer().runOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            player.level().getChunk(x >> 4, z >> 4);
            int y = Math.max(minY, player.level().getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + lift);
            player.setNoGravity(true);
            player.teleportTo(player.level(), x + 0.5, y, z + 0.5,
                    EnumSet.noneOf(Relative.class), yaw, pitch, false);
        });
        context.waitTicks(30);
        world.getConnection().waitForChunksRender();
        clearOverlays(context);
        context.takeScreenshot(name);
    }

    private static void place(TestSingleplayerContext world, String name, BlockPos origin) {
        var server = world.getConnection().getServerPlayer().level().getServer();
        var template = server.getStructureTemplateManager()
                .get(Identifier.fromNamespaceAndPath("gems", name)).orElseThrow();
        if (!template.placeInWorld(world.getConnection().getServerPlayer().level(), origin, origin,
                new StructurePlaceSettings(), RandomSource.create(), 2)) {
            throw new IllegalStateException("Template placement failed: " + name);
        }
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                Direction.UP, pos, false);
    }

    private static void view(TestSingleplayerContext world, ClientGameTestContext context,
            BlockPos origin, double x, double y, double z, float yaw, float pitch, String name) {
        world.getServer().runOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            player.teleportTo(player.level(), origin.getX() + x, origin.getY() + y,
                    origin.getZ() + z, EnumSet.noneOf(Relative.class), yaw, pitch, false);
        });
        context.waitTicks(20);
        world.getConnection().waitForChunksRender();
        clearOverlays(context);
        context.takeScreenshot(name);
    }

    private static void clearOverlays(ClientGameTestContext context) {
        context.runOnClient(client -> {
            client.gui.hud.getChat().clearMessages(false);
            client.gui.toastManager().clear();
        });
    }

    private static void clickGuideTopic(ClientGameTestContext context, String title) {
        for (int page = 0; page < 6; page++) {
            if (context.tryClickScreenButton(title)) return;
            if (!context.tryClickScreenButton("Next >")) break;
            context.waitTicks(1);
        }
        throw new IllegalStateException("Guide topic not visible: " + title);
    }

    private static void clickGuideButtonContaining(ClientGameTestContext context, String title) {
        AtomicReference<String> label = new AtomicReference<>();
        context.runOnClient(client -> label.set(client.gui.screen().children().stream()
                .filter(Button.class::isInstance).map(Button.class::cast)
                .map(button -> button.getMessage().getString())
                .filter(text -> text.contains(title))
                .findFirst().orElseThrow(() -> new IllegalStateException("Guide button not visible: " + title))));
        context.clickScreenButton(label.get());
    }
    private static void clickGuideButtonStarting(ClientGameTestContext context, String prefix) {
        AtomicReference<String> label = new AtomicReference<>();
        context.runOnClient(client -> label.set(client.gui.screen().children().stream()
                .filter(Button.class::isInstance).map(Button.class::cast)
                .map(button -> button.getMessage().getString())
                .filter(text -> text.startsWith(prefix))
                .findFirst().orElseThrow(() -> new IllegalStateException("Guide button not visible: " + prefix))));
        context.clickScreenButton(label.get());
    }
}
