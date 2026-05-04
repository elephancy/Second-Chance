package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.AdventureItems;
import com.feel.gems.adventure.CampaignDeliveryAccount;
import com.feel.gems.adventure.CampaignPersistentState;
import com.feel.gems.adventure.CampaignRewards;
import com.feel.gems.adventure.CampaignRouteState;
import com.feel.gems.adventure.DomainBlocks;
import com.feel.gems.adventure.LandmarkKey;
import com.feel.gems.adventure.RescueDispatchState;
import com.feel.gems.adventure.RouteFinaleState;
import com.feel.gems.adventure.boss.BossFeatures;
import com.feel.gems.adventure.boss.CampaignSignatures;
import com.feel.gems.adventure.boss.StageOpeningState;
import com.feel.gems.casino.CasinoGame;
import com.feel.gems.casino.TournamentState;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.item.InventorySpace;
import java.util.EnumSet;
import java.util.List;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Exercises townhall claims and boss services with connected server-side players. */
public final class BossTownhallCoopGameTests {
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 80)
    public void firstClearSuppliesWaitForInventorySpaceAndArriveOnce(GameTestHelper context) {
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        teleport(player, context.getLevel(), context.absolutePos(new BlockPos(1, 2, 1)));
        var inventory = player.getInventory();
        int slots = inventory.getNonEquipmentItems().size();
        for (int slot = 0; slot < slots; slot++) {
            inventory.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        inventory.setItem(0, new ItemStack(Items.AMETHYST_SHARD, 63));
        var server = context.getLevel().getServer();
        var state = CampaignPersistentState.get(server);
        CampaignRewards.recordVictory(server, List.of(player), LandmarkKey.FRAMEFALL);
        CampaignSignatures.deliver(player);
        CampaignDeliveryAccount account = CampaignDeliveryAccount.of(player, state);
        if (!state.model().hasCleared(player.getUUID(), LandmarkKey.FRAMEFALL)
                || account.hasSupplies(LandmarkKey.FRAMEFALL)
                || account.hasLore(LandmarkKey.FRAMEFALL)
                || account.hasSignature(LandmarkKey.FRAMEFALL)
                || GemsGameTestUtil.countItem(player, Items.COBBLESTONE) != (slots - 1) * 64
                || GemsGameTestUtil.countItem(player, Items.AMETHYST_SHARD) != 63
                || GemsGameTestUtil.hasItem(player, BossFeatures.PARALLAX_FRAME)) {
            context.fail("A full inventory lost a reward entitlement or received a partial stack");
            return;
        }

        inventory.setItem(1, ItemStack.EMPTY);
        inventory.setItem(2, ItemStack.EMPTY);
        inventory.setItem(3, ItemStack.EMPTY);
        CampaignRewards.deliverPendingLore(player);
        CampaignSignatures.deliver(player);
        if (!account.hasSupplies(LandmarkKey.FRAMEFALL)
                || !account.hasLore(LandmarkKey.FRAMEFALL)
                || !account.hasSignature(LandmarkKey.FRAMEFALL)
                || GemsGameTestUtil.countItem(player, Items.WRITTEN_BOOK) != 1
                || GemsGameTestUtil.countItem(player, Items.AMETHYST_SHARD) != 65
                || GemsGameTestUtil.countItem(player, BossFeatures.PARALLAX_FRAME) != 1) {
            context.fail("Lore, signature and supplies after freeing slots: receipt="
                    + account.hasSupplies(LandmarkKey.FRAMEFALL)
                    + ", loreReceipt=" + account.hasLore(LandmarkKey.FRAMEFALL)
                    + ", signatureReceipt=" + account.hasSignature(LandmarkKey.FRAMEFALL)
                    + ", books=" + GemsGameTestUtil.countItem(player, Items.WRITTEN_BOOK)
                    + ", amethyst=" + GemsGameTestUtil.countItem(player, Items.AMETHYST_SHARD)
                    + ", frames=" + GemsGameTestUtil.countItem(player, BossFeatures.PARALLAX_FRAME));
            return;
        }
        CampaignRewards.deliverPendingLore(player);
        CampaignSignatures.deliver(player);
        var rematch = CampaignRewards.recordVictory(server, List.of(player), LandmarkKey.FRAMEFALL);
        if (rematch.firstClears().get(player.getUUID())
                || GemsGameTestUtil.countItem(player, Items.AMETHYST_SHARD) != 65) {
            context.fail("Retry or rematch repeated the first-clear supply grant");
            return;
        }
        // The boss entity invokes this only for replays; the commodity loot
        // from a rematch remains available independently of the one-time grant.
        CampaignRewards.deliverRematchSupplies(List.of(player), LandmarkKey.FRAMEFALL, rematch);
        int dropped = context.getLevel().getEntitiesOfClass(ItemEntity.class,
                player.getBoundingBox().inflate(4.0), entity -> entity.getItem().is(Items.AMETHYST_SHARD))
                .stream().mapToInt(entity -> entity.getItem().getCount()).sum();
        if (GemsGameTestUtil.countItem(player, Items.AMETHYST_SHARD) != 65 || dropped != 2) {
            context.fail("A full creative inventory lost the rematch drop: " + dropped);
            return;
        }
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 80)
    public void allSevenFirstClearSupplyBundlesKeepTheirAmounts(GameTestHelper context) {
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        var server = context.getLevel().getServer();
        for (LandmarkKey landmark : LandmarkKey.values()) {
            CampaignRewards.recordVictory(server, List.of(player), landmark);
        }
        var account = CampaignDeliveryAccount.of(player, CampaignPersistentState.get(server));
        for (LandmarkKey landmark : LandmarkKey.values()) {
            if (!account.hasSupplies(landmark)) {
                context.fail("Missing first-clear receipt for " + landmark.id());
                return;
            }
        }
        if (GemsGameTestUtil.countItem(player, Items.AMETHYST_SHARD) != 10
                || GemsGameTestUtil.countItem(player, Items.CLOCK) != 1
                || GemsGameTestUtil.countItem(player, Items.GLOW_INK_SAC) != 2
                || GemsGameTestUtil.countItem(player, Items.MAP) != 1
                || GemsGameTestUtil.countItem(player, Items.GOLDEN_CARROT) != 3
                || GemsGameTestUtil.countItem(player, Items.REDSTONE) != 8) {
            context.fail("One of the seven original boss supply bundles changed or disappeared");
            return;
        }
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 80)
    public void fullInventoryKeepsPostgameKitClaimable(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        CampaignRouteState route = CampaignRouteState.get(level.getServer());
        if (route.chosen() == null) {
            route.vote(player.getUUID(), CampaignRouteState.Path.REPAIR);
            if (route.confirm(List.of(player.getUUID()), player.getUUID())
                    != CampaignRouteState.Resolution.CHOSEN) {
                context.fail("Could not set a route for the postgame service fixture");
                return;
            }
        }
        RouteFinaleState ending = RouteFinaleState.get(level.getServer());
        ending.finish();
        CampaignRouteState.Path path = route.chosen();
        BlockPos desk = context.absolutePos(new BlockPos(2, 2, 1));
        context.setBlock(2, 2, 1, DomainBlocks.TOWNHALL_STORY_COUNCIL);
        int requiredSlots = path == CampaignRouteState.Path.REMEMBER ? 1 : 2;
        long day = Math.max(0, Math.floorDiv(level.getOverworldClockTime(), 24_000L));
        long nextDay = ending.nextServiceDay(player.getUUID());
        if (nextDay > day) {
            context.fail("The fixture player already used the postgame desk today");
            return;
        }

        var inventory = player.getInventory();
        int carriedSlots = inventory.getNonEquipmentItems().size();
        for (int slot = 0; slot < carriedSlots; slot++) {
            inventory.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        if (InventorySpace.hasEmptySlots(player, requiredSlots)
                || useBlock(player, level, desk) != InteractionResult.SUCCESS_SERVER
                || ending.nextServiceDay(player.getUUID()) != nextDay
                || GemsGameTestUtil.countItem(player, Items.COBBLESTONE) != carriedSlots * 64
                || hasPostgameKit(player, path)) {
            context.fail("A full inventory consumed the postgame service claim or changed carried items");
            return;
        }

        for (int slot = 0; slot < requiredSlots; slot++) inventory.setItem(slot, ItemStack.EMPTY);
        if (useBlock(player, level, desk) != InteractionResult.SUCCESS_SERVER
                || ending.nextServiceDay(player.getUUID()) != day + 1
                || !hasPostgameKit(player, path)) {
            context.fail("The postgame kit was not delivered after room was made");
            return;
        }
        if (useBlock(player, level, desk) != InteractionResult.SUCCESS_SERVER
                || ending.nextServiceDay(player.getUUID()) != day + 1
                || !hasPostgameKit(player, path)) {
            context.fail("Repeated use changed a completed postgame claim");
            return;
        }
        context.succeed();
    }

    private static boolean hasPostgameKit(ServerPlayer player, CampaignRouteState.Path path) {
        return switch (path) {
            case REMEMBER -> GemsGameTestUtil.countItem(player, AdventureItems.RUMOR_ATLAS) == 1;
            case REPAIR -> GemsGameTestUtil.countItem(player, DomainBlocks.PATCHWORK_BRICK.asItem()) == 8
                    && GemsGameTestUtil.countItem(player, Items.BREAD) == 4;
            case RELEASE -> GemsGameTestUtil.countItem(player, AdventureItems.SHORTCUT_CHALK) == 1
                    && GemsGameTestUtil.countItem(player, AdventureItems.SIGNAL_BUOY) == 1;
        };
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 80)
    public void fullInventoryKeepsDailyCasinoProvisionsClaimable(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        BlockPos board = context.absolutePos(new BlockPos(2, 2, 1));
        context.setBlock(2, 2, 1, DomainBlocks.TOWNHALL_TOURNAMENT_BOARD);

        TournamentState tournament = TournamentState.get(level.getServer());
        long day = TournamentState.worldDay(level.getServer());
        // Record every table so the currently featured three are complete on
        // any calendar day, including a reused GameTest world.
        for (CasinoGame game : CasinoGame.values()) tournament.record(day, game, true);
        if (!tournament.completed(day)) {
            context.fail("The tournament setup did not unlock the daily board reward");
            return;
        }

        var inventory = player.getInventory();
        int carriedSlots = inventory.getNonEquipmentItems().size();
        for (int slot = 0; slot < carriedSlots; slot++) {
            inventory.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        if (InventorySpace.hasEmptySlots(player, 2)) {
            context.fail("The test player still has room for the two reward stacks");
            return;
        }
        if (useBlock(player, level, board) != InteractionResult.SUCCESS_SERVER
                || GemsGameTestUtil.countItem(player, Items.COBBLESTONE) != carriedSlots * 64
                || GemsGameTestUtil.countItem(player, Items.BREAD) != 0
                || GemsGameTestUtil.countItem(player, Items.TORCH) != 0) {
            context.fail("A full inventory consumed the daily claim or delivered partial provisions");
            return;
        }

        inventory.setItem(0, ItemStack.EMPTY);
        inventory.setItem(1, ItemStack.EMPTY);
        if (useBlock(player, level, board) != InteractionResult.SUCCESS_SERVER
                || GemsGameTestUtil.countItem(player, Items.BREAD) != 2
                || GemsGameTestUtil.countItem(player, Items.TORCH) != 4) {
            context.fail("Freeing two slots did not deliver the previously blocked provisions exactly once");
            return;
        }
        if (useBlock(player, level, board) != InteractionResult.SUCCESS_SERVER
                || GemsGameTestUtil.countItem(player, Items.BREAD) != 2
                || GemsGameTestUtil.countItem(player, Items.TORCH) != 4) {
            context.fail("Repeating a successful daily claim duplicated provisions");
            return;
        }
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 80)
    public void rescueDispatchClaimAndRallyAreShared(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        ServerPlayer caller = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer teammate = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        BlockPos desk = context.absolutePos(new BlockPos(2, 2, 1));
        BlockPos callerFeet = context.absolutePos(new BlockPos(1, 2, 3));
        BlockPos teammateFeet = context.absolutePos(new BlockPos(3, 2, 3));
        context.setBlock(2, 2, 1, DomainBlocks.TOWNHALL_RESCUE_DISPATCH);
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) context.setBlock(x, 1, z, Blocks.STONE);
        }
        teleport(caller, level, callerFeet);
        teleport(teammate, level, teammateFeet);
        CampaignPersistentState.get(level.getServer()).recordVictory(
                List.of(caller.getUUID()), LandmarkKey.REDLINE);

        if (useBlock(caller, level, desk) != InteractionResult.SUCCESS_SERVER
                || useBlock(teammate, level, desk) != InteractionResult.SUCCESS_SERVER
                || !GemsGameTestUtil.hasItem(caller, AdventureItems.DISPATCH_BELL)
                || !GemsGameTestUtil.hasItem(teammate, AdventureItems.DISPATCH_BELL)) {
            context.fail("The unlocked Rescue Dispatch did not give each player a bell");
            return;
        }
        if (GemsGameTestUtil.countItem(caller, AdventureItems.DISPATCH_BELL) != 1
                || useBlock(caller, level, desk) != InteractionResult.SUCCESS_SERVER
                || GemsGameTestUtil.countItem(caller, AdventureItems.DISPATCH_BELL) != 1) {
            context.fail("Repeated desk use duplicated the dispatch bell");
            return;
        }

        caller.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AdventureItems.DISPATCH_BELL));
        teammate.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AdventureItems.DISPATCH_BELL));
        // Let the connected fake players finish their first world tick before
        // checking collision and using an item at their teleported positions.
        context.runAtTickTime(5, () -> {
            RescueDispatchState rally = RescueDispatchState.get(level.getServer());
            long now = level.getGameTime();
            BlockPos earlier = rally.activePoint(now);
            caller.setShiftKeyDown(true);
            InteractionResult called = AdventureItems.DISPATCH_BELL.use(
                    level, caller, InteractionHand.MAIN_HAND);
            caller.setShiftKeyDown(false);
            BlockPos expected = earlier == null ? callerFeet : earlier;
            if (called != InteractionResult.SUCCESS_SERVER
                    || !expected.equals(rally.activePoint(now))
                    || (earlier == null && rally.remaining(now) != RescueDispatchState.WINDOW_TICKS)) {
                context.fail("Bell call mismatch: caller=" + caller.blockPosition()
                        + ", previous=" + earlier + ", current=" + rally.activePoint(now)
                        + ", remaining=" + rally.remaining(now));
                return;
            }
            if (AdventureItems.DISPATCH_BELL.use(level, teammate, InteractionHand.MAIN_HAND)
                    != InteractionResult.SUCCESS_SERVER
                    || !expected.equals(rally.activePoint(now))) {
                context.fail("The second player could not find the active rally");
                return;
            }
            // The lookup applies a one-second cooldown. At tick 30 a surviving
            // rally must reject a second call; if a saved rally expired, the
            // caller can place a fresh one without changing the server clock.
            context.runAtTickTime(30, () -> {
                long later = level.getGameTime();
                BlockPos active = rally.activePoint(later);
                ServerPlayer user = active == null ? caller : teammate;
                user.setShiftKeyDown(true);
                InteractionResult result = AdventureItems.DISPATCH_BELL.use(
                        level, user, InteractionHand.MAIN_HAND);
                user.setShiftKeyDown(false);
                BlockPos finalPoint = active == null ? callerFeet : active;
                if (result != InteractionResult.SUCCESS_SERVER
                        || !finalPoint.equals(rally.activePoint(later))
                        || rally.activePoint(later + rally.remaining(later)) != null) {
                    context.fail("Rally replacement or expiration failed: before=" + active
                            + ", after=" + rally.activePoint(later));
                    return;
                }
                context.succeed();
            });
        });
    }

    @GameTest(structure = "gems:unwritten_court", maxTicks = 240)
    public void stageDeskChoiceIsSharedAndConsumedByRealRematch(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        var bosses = context.getEntities(BossFeatures.STRAY_CADENZA);
        if (bosses.size() != 1) {
            context.fail("The packaged Unwritten Court did not spawn one Cadenza");
            return;
        }
        var boss = bosses.getFirst();
        ServerPlayer first = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer second = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        BlockPos center = boss.blockPosition();
        teleport(first, level, center.offset(3, 1, 0));
        teleport(second, level, center.offset(-3, 1, 0));
        BlockPos desk = context.absolutePos(new BlockPos(2, 6, 2));
        level.setBlockAndUpdate(desk, DomainBlocks.TOWNHALL_STAGE_DESK.defaultBlockState());

        // Let the authored arena start and count both players' presence before its first clear.
        context.runAtTickTime(85, () -> {
            boss.setHealth(1.0F);
            if (!boss.hurtServer(level, level.damageSources().playerAttack(first), 18.0F)
                    || !CampaignPersistentState.get(level.getServer()).model()
                            .worldChanged(LandmarkKey.UNWRITTEN)) {
                context.fail("An active Cadenza clear did not unlock the world Stage Desk");
                return;
            }
            var progress = CampaignPersistentState.get(level.getServer()).model();
            if (!progress.hasCleared(first.getUUID(), LandmarkKey.UNWRITTEN)
                    || !progress.hasCleared(second.getUUID(), LandmarkKey.UNWRITTEN)) {
                context.fail("Both present players were not credited for the first clear");
                return;
            }
            StageOpeningState selection = StageOpeningState.get(level.getServer());
            first.setShiftKeyDown(true);
            InteractionResult chosen = useBlock(first, level, desk);
            first.setShiftKeyDown(false);
            if (chosen != InteractionResult.SUCCESS_SERVER || selection.pending() == null) {
                context.fail("The Stage Desk did not queue a rematch opening");
                return;
            }
            String firstChoice = selection.pending().label();
            if (useBlock(second, level, desk) != InteractionResult.SUCCESS_SERVER
                    || !firstChoice.equals(selection.pending().label())) {
                context.fail("The second player could not see the shared opening");
                return;
            }
            second.setShiftKeyDown(true);
            InteractionResult changed = useBlock(second, level, desk);
            second.setShiftKeyDown(false);
            if (changed != InteractionResult.SUCCESS_SERVER || selection.pending() == null
                    || firstChoice.equals(selection.pending().label())) {
                context.fail("The second player could not change the world-shared opening");
            }
        });
        context.runAtTickTime(200, () -> {
            StageOpeningState selection = StageOpeningState.get(level.getServer());
            if (selection.pending() == null) {
                context.fail("The opening disappeared before the rematch interaction");
                return;
            }
            teleport(first, level, boss.blockPosition().offset(2, 1, 0));
            Vec3 bodyHit = new Vec3(0.0, boss.getBbHeight() * 0.5, 0.0);
            if (boss.interact(first, InteractionHand.MAIN_HAND, bodyHit)
                    != InteractionResult.SUCCESS_SERVER
                    || selection.pending() != null) {
                context.fail("The Cadenza interaction did not consume the selected rematch opening");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "gems:unwritten_court", maxTicks = 130)
    public void downedParticipantKeepsClearAndReceivesRewardsAfterRecovery(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        var bosses = context.getEntities(BossFeatures.STRAY_CADENZA);
        if (bosses.size() != 1) {
            context.fail("The packaged Unwritten Court did not spawn one Cadenza");
            return;
        }
        var boss = bosses.getFirst();
        ServerPlayer finisher = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer downed = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        BlockPos center = boss.blockPosition();
        teleport(finisher, level, center.offset(3, 1, 0));
        teleport(downed, level, center.offset(-3, 1, 0));

        // Both players spend more than three seconds in the active encounter.
        // The downed player cannot receive inventory delivery at the final hit.
        context.runAtTickTime(85, () -> {
            downed.setHealth(0.0F);
            if (downed.isAlive()) {
                context.fail("The downed participant is still eligible for immediate inventory delivery");
                return;
            }
            boss.setHealth(1.0F);
            if (!boss.hurtServer(level, level.damageSources().playerAttack(finisher), 18.0F)) {
                context.fail("The active Cadenza did not accept the finishing hit");
                return;
            }
            var progress = CampaignPersistentState.get(level.getServer()).model();
            if (!progress.hasCleared(downed.getUUID(), LandmarkKey.UNWRITTEN)
                    || !progress.needsLore(downed.getUUID(), LandmarkKey.UNWRITTEN)
                    || !progress.needsSignature(downed.getUUID(), LandmarkKey.UNWRITTEN)
                    || CampaignDeliveryAccount.of(downed,
                            CampaignPersistentState.get(level.getServer()))
                            .hasSupplies(LandmarkKey.UNWRITTEN)
                    || GemsGameTestUtil.hasItem(downed, BossFeatures.STAGE_MARK)) {
                context.fail("The downed participant lost clear credit or received an item while downed");
                return;
            }

            // This revives the same connected mock player. Real death, respawn,
            // disconnect and playerdata reload still need their own live checks.
            downed.setHealth(downed.getMaxHealth());
            CampaignRewards.deliverPendingLore(downed);
            CampaignSignatures.deliver(downed);
            if (GemsGameTestUtil.countItem(downed, Items.WRITTEN_BOOK) != 1
                    || GemsGameTestUtil.countItem(downed, BossFeatures.STAGE_MARK) != 1
                    || GemsGameTestUtil.countItem(downed, Items.AMETHYST_SHARD) != 8
                    || progress.needsLore(downed.getUUID(), LandmarkKey.UNWRITTEN)
                    || progress.needsSignature(downed.getUUID(), LandmarkKey.UNWRITTEN)) {
                context.fail("Pending Cadenza rewards were not delivered once after recovery");
                return;
            }
            CampaignRewards.deliverPendingLore(downed);
            CampaignSignatures.deliver(downed);
            if (GemsGameTestUtil.countItem(downed, Items.WRITTEN_BOOK) != 1
                    || GemsGameTestUtil.countItem(downed, BossFeatures.STAGE_MARK) != 1
                    || GemsGameTestUtil.countItem(downed, Items.AMETHYST_SHARD) != 8) {
                context.fail("Retry duplicated a recovered participant's rewards");
                return;
            }
            context.succeed();
        });
    }

    private static InteractionResult useBlock(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit);
    }

    private static void teleport(ServerPlayer player, ServerLevel level, BlockPos feet) {
        player.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5,
                EnumSet.noneOf(Relative.class), 0.0F, 0.0F, false);
    }
}
