package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.CampaignPersistentState;
import com.feel.gems.adventure.InkboundPersistentState;
import com.feel.gems.adventure.AdventureItems;
import com.feel.gems.adventure.FriendDomainUnlockState;
import com.feel.gems.adventure.FamForumPersistentState;
import com.feel.gems.adventure.AdlSurveyRuntime;
import com.feel.gems.adventure.AdlSurveyState;
import com.feel.gems.adventure.OthmanArchiveState;
import com.feel.gems.adventure.YehiaStoryState;
import com.feel.gems.adventure.DomainBlocks;
import com.feel.gems.adventure.LandmarkKey;
import com.feel.gems.adventure.boss.SignalEchoEntity;
import com.feel.gems.friend.FriendRegistry;
import com.feel.gems.client.screen.GuidebookScreen;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/** Development-only renderer smoke test with screenshots for visual review. */
@SuppressWarnings("UnstableApiUsage")
public final class AdventureClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        if (Boolean.getBoolean("gems.focusedNaturalClientGate")
                || Boolean.getBoolean("gems.focusedUiClientGate")) return;
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            if (FriendRegistry.find("nour").isEmpty()) {
                throw new IllegalStateException("Friend roster did not load Nour's identity");
            }
            context.takeScreenshot("second-chance-singleplayer");
            context.setScreen(GuidebookScreen::new);
            context.waitTicks(80);
            context.takeScreenshot("second-chance-guidebook-start");
            context.clickScreenButton("Find a place");
            context.waitTicks(2);
            context.takeScreenshot("second-chance-guidebook-adventure");
            context.setScreen(() -> null);
            BlockPos townOrigin = world.getServer().computeOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                var level = player.level();
                BlockPos origin = player.blockPosition().offset(-31, -5, -31);
                for (var piece : Map.of(
                        "second_chance_town_deco_v2", origin,
                        "town_casino_deco_v2", origin.offset(63, 0, 16),
                        "town_arcade_deco_v2", origin.offset(63, 0, 0),
                        "town_lounge_deco_v2", origin.offset(63, 0, 48),
                        "town_market", origin.offset(-21, 0, 21),
                        "town_guesthouse", origin.offset(21, 0, -21),
                        "town_hall_alcoves_v2", origin.offset(16, 0, 62)).entrySet()) {
                    var template = server.getStructureTemplateManager()
                            .get(Identifier.fromNamespaceAndPath("gems", piece.getKey()))
                            .orElseThrow();
                    if (!template.placeInWorld(level, piece.getValue(), piece.getValue(),
                            new StructurePlaceSettings(), RandomSource.create(), 2)) {
                        throw new IllegalStateException("Town piece placement failed: " + piece.getKey());
                    }
                }
                player.teleportTo(level, origin.getX() + 31.5, origin.getY() + 5,
                        origin.getZ() + 15.5, EnumSet.noneOf(Relative.class),
                        0.0F, 0.0F, false);
                return origin;
            });
            context.waitTicks(60);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("second-chance-town-approach");
            townView(world, context, townOrigin, 49.5, 5, 31.5, -90.0F,
                    "second-chance-casino-exterior");
            townView(world, context, townOrigin, 56.5, 5, 7.5, -90.0F,
                    "ragab-arcade-exterior");
            townView(world, context, townOrigin, 56.5, 5, 55.5, -90.0F,
                    "ragab-lounge-exterior");
            townView(world, context, townOrigin, 12.5, 5, 31.5, 90.0F,
                    "second-chance-market-exterior");
            townView(world, context, townOrigin, 31.5, 5, 12.5, 180.0F,
                    "second-chance-guesthouse-exterior");
            townView(world, context, townOrigin, 31.5, 5, 47.5, 0.0F,
                    "second-chance-townhall-exterior");
            townView(world, context, townOrigin, 31.5, 5, 71.5, 0.0F,
                    "second-chance-townhall-interior");
            world.getServer().runOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                var level = player.level();
                player.teleportTo(level, townOrigin.getX() + 31.5, townOrigin.getY() + 5,
                        townOrigin.getZ() + 74.5, EnumSet.noneOf(Relative.class),
                        0.0F, 0.0F, false);
                BlockPos ledger = townOrigin.offset(31, 6, 77);
                BlockHitResult hit = new BlockHitResult(
                        new Vec3(ledger.getX() + 0.5, ledger.getY() + 0.5,
                                ledger.getZ() + 0.5), Direction.UP, ledger, false);
                var callback = UseBlockCallback.EVENT.invoker();
                BlockPos inkboundDesk = townOrigin.offset(36, 6, 77);
                if (callback.interact(player, level, InteractionHand.MAIN_HAND,
                        hit(inkboundDesk)) != InteractionResult.SUCCESS_SERVER
                        || InkboundPersistentState.get(server).model().unlocked()) {
                    throw new IllegalStateException("Inkbound desk did not stay locked");
                }
                if (callback.interact(player, level, InteractionHand.MAIN_HAND, hit)
                        != InteractionResult.SUCCESS_SERVER) {
                    throw new IllegalStateException("Townhall ledger did not handle a locked use");
                }
                AABB hall = new AABB(ledger).inflate(12);
                if (!level.getEntitiesOfClass(SignalEchoEntity.class, hall,
                        SignalEchoEntity::isAlive).isEmpty()) {
                    throw new IllegalStateException("Locked ledger spawned an echo");
                }
                CampaignPersistentState.get(server).recordVictory(
                        List.of(player.getUUID()), LandmarkKey.HUSHWIRE);
                if (callback.interact(player, level, InteractionHand.MAIN_HAND, hit)
                        != InteractionResult.SUCCESS_SERVER
                        || level.getEntitiesOfClass(SignalEchoEntity.class, hall,
                                SignalEchoEntity::isAlive).size() != 1) {
                    throw new IllegalStateException("World-shared Hushwire service failed");
                }
            });
            context.waitTicks(2);
            context.takeScreenshot("second-chance-townhall-signal-scout");
            BlockPos inkboundOrigin = world.getServer().computeOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                var level = player.level();
                BlockPos origin = townOrigin.offset(160, 0, 160);
                for (var piece : Map.of(
                        "inkbound_workshop", origin,
                        "inkbound_print_house", origin.offset(46, 0, 13),
                        "inkbound_garden", origin.offset(-20, 0, 13)).entrySet()) {
                    var template = server.getStructureTemplateManager()
                            .get(Identifier.fromNamespaceAndPath("gems", piece.getKey()))
                            .orElseThrow();
                    if (!template.placeInWorld(level, piece.getValue(), piece.getValue(),
                            new StructurePlaceSettings(), RandomSource.create(), 2)) {
                        throw new IllegalStateException("Inkbound piece placement failed: "
                                + piece.getKey());
                    }
                }
                return origin;
            });
            townView(world, context, inkboundOrigin, 23.5, 5, 53.5, 180.0F,
                    "inkbound-workshop-exterior");
            townView(world, context, inkboundOrigin, 23.5, 5, 32.5, 180.0F,
                    "inkbound-workshop-interior");
            townView(world, context, inkboundOrigin, 58.0, 5, 47.5, 180.0F,
                    "inkbound-print-house-exterior");
            townView(world, context, inkboundOrigin, 62.5, 5, 27.5, 135.0F,
                    "inkbound-print-house-stations");
            world.getServer().runOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                var level = player.level();
                BlockPos print = inkboundOrigin.offset(46, 0, 13);
                BlockPos spark = print.offset(7, 7, 8);
                BlockPos shelter = print.offset(11, 7, 8);
                BlockPos route = print.offset(15, 7, 8);
                BlockPos press = print.offset(11, 6, 13);
                var callback = UseBlockCallback.EVENT.invoker();
                for (BlockPos[] pair : new BlockPos[][]{{spark, shelter},
                        {spark, route}, {shelter, route}}) {
                    for (BlockPos station : new BlockPos[]{pair[0], pair[1], press}) {
                        if (callback.interact(player, level, InteractionHand.MAIN_HAND,
                                hit(station)) != InteractionResult.SUCCESS_SERVER) {
                            throw new IllegalStateException("Inkbound station ignored use: " + station);
                        }
                    }
                }
                if (!InkboundPersistentState.get(server).model().unlocked()) {
                    throw new IllegalStateException("Inkbound pairs did not unlock the desk");
                }
                player.teleportTo(level, townOrigin.getX() + 35.5, townOrigin.getY() + 5,
                        townOrigin.getZ() + 77.5, EnumSet.noneOf(Relative.class),
                        0.0F, 0.0F, false);
                BlockPos desk = townOrigin.offset(36, 6, 77);
                if (callback.interact(player, level, InteractionHand.MAIN_HAND,
                        hit(desk)) != InteractionResult.SUCCESS_SERVER
                        || !player.hasEffect(MobEffects.NIGHT_VISION)
                        || !player.hasEffect(MobEffects.RESISTANCE)
                        || !player.hasEffect(MobEffects.SPEED)) {
                    throw new IllegalStateException("Inkbound public desk did not grant a field plan");
                }
            });
            townView(world, context, townOrigin, 33.0, 5, 83.0, 210.0F,
                    "second-chance-townhall-inkbound-desk");
            townView(world, context, inkboundOrigin, -10.0, 5, 47.5, 180.0F,
                    "inkbound-paper-garden-exterior");
            BlockPos khaledOrigin = world.getServer().computeOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                var level = player.level();
                BlockPos origin = townOrigin.offset(-160, 0, 160);
                for (var piece : Map.of(
                        "khaled_floodgate", origin,
                        "khaled_shieldworks", origin.offset(46, 0, 13),
                        "khaled_shelter_walk", origin.offset(-20, 0, 13)).entrySet()) {
                    var template = server.getStructureTemplateManager()
                            .get(Identifier.fromNamespaceAndPath("gems", piece.getKey()))
                            .orElseThrow();
                    if (!template.placeInWorld(level, piece.getValue(), piece.getValue(),
                            new StructurePlaceSettings(), RandomSource.create(), 2)) {
                        throw new IllegalStateException("Khaled piece placement failed: "
                                + piece.getKey());
                    }
                }
                return origin;
            });
            townView(world, context, khaledOrigin, 23.5, 5, 60.0, 180.0F,
                    "khaled-riverward-approach");
            townView(world, context, khaledOrigin, 23.5, 5, 35.0, 180.0F,
                    "khaled-floodgate-bridge");
            townView(world, context, khaledOrigin, 40.0, 6, 33.0, 135.0F,
                    "khaled-bridge-water-side");
            townView(world, context, khaledOrigin, 56.0, 5, 47.0, 180.0F,
                    "khaled-shieldworks-exterior");
            townView(world, context, khaledOrigin, 56.0, 5, 35.0, 180.0F,
                    "khaled-shieldworks-approach");
            townView(world, context, khaledOrigin, -10.0, 5, 47.0, 180.0F,
                    "khaled-shelter-walk-exterior");
            world.getServer().runOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                var level = player.level();
                var callback = UseBlockCallback.EVENT.invoker();
                BlockPos desk = townOrigin.offset(21, 6, 77);
                if (callback.interact(player, level, InteractionHand.MAIN_HAND,
                        hit(desk)) != InteractionResult.SUCCESS_SERVER
                        || FriendDomainUnlockState.get(server).cleared("khaled")) {
                    throw new IllegalStateException("Khaled desk did not stay locked");
                }
                BlockPos start = khaledOrigin.offset(-10, 4, 23);
                player.teleportTo(level, start.getX() + 0.5, start.getY() + 1,
                        start.getZ() + 0.5, EnumSet.noneOf(Relative.class),
                        0.0F, 0.0F, false);
                if (callback.interact(player, level, InteractionHand.MAIN_HAND,
                        hit(start)) != InteractionResult.SUCCESS_SERVER) {
                    throw new IllegalStateException("Khaled route did not start");
                }
                player.teleportTo(level, khaledOrigin.getX() + 17.5,
                        khaledOrigin.getY() + 6, khaledOrigin.getZ() + 23.5,
                        EnumSet.noneOf(Relative.class), 0.0F, 0.0F, false);
            });
            context.waitTicks(12);
            world.getServer().runOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                player.teleportTo(player.level(), khaledOrigin.getX() + 29.5,
                        khaledOrigin.getY() + 6, khaledOrigin.getZ() + 23.5,
                        EnumSet.noneOf(Relative.class), 0.0F, 0.0F, false);
            });
            context.waitTicks(12);
            world.getServer().runOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                var level = player.level();
                player.teleportTo(level, khaledOrigin.getX() + 57.5,
                        khaledOrigin.getY() + 5, khaledOrigin.getZ() + 27.5,
                        EnumSet.noneOf(Relative.class), 0.0F, 0.0F, false);
                var callback = UseBlockCallback.EVENT.invoker();
                BlockPos finish = khaledOrigin.offset(57, 4, 27);
                if (callback.interact(player, level, InteractionHand.MAIN_HAND,
                        hit(finish)) != InteractionResult.SUCCESS_SERVER
                        || !FriendDomainUnlockState.get(server).cleared("khaled")) {
                    throw new IllegalStateException("Khaled route did not unlock the desk");
                }
                player.teleportTo(level, townOrigin.getX() + 21.5,
                        townOrigin.getY() + 5, townOrigin.getZ() + 77.5,
                        EnumSet.noneOf(Relative.class), 0.0F, 0.0F, false);
                BlockPos desk = townOrigin.offset(21, 6, 77);
                if (callback.interact(player, level, InteractionHand.MAIN_HAND,
                        hit(desk)) != InteractionResult.SUCCESS_SERVER
                        || !player.hasEffect(MobEffects.ABSORPTION)) {
                    throw new IllegalStateException("Shelter Desk did not grant a brief shield");
                }
            });
            townView(world, context, townOrigin, 22.0, 5, 83.0, 150.0F,
                    "second-chance-townhall-shelter-desk");
            BlockPos nourOrigin = world.getServer().computeOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                var level = player.level();
                BlockPos origin = townOrigin.offset(160, 0, -160);
                for (var piece : Map.of(
                        "nour_signal_harbor", origin,
                        "nour_boathouse", origin.offset(46, 0, 13),
                        "nour_foghouse", origin.offset(-20, 0, 13)).entrySet()) {
                    var template = server.getStructureTemplateManager()
                            .get(Identifier.fromNamespaceAndPath("gems", piece.getKey()))
                            .orElseThrow();
                    if (!template.placeInWorld(level, piece.getValue(), piece.getValue(),
                            new StructurePlaceSettings(), RandomSource.create(), 2)) {
                        throw new IllegalStateException("Nour piece placement failed: "
                                + piece.getKey());
                    }
                }
                return origin;
            });
            townView(world, context, nourOrigin, 23.5, 5, 60.0, 180.0F,
                    "nour-harbor-pier-approach");
            townView(world, context, nourOrigin, 23.5, 5, 35.0, 180.0F,
                    "nour-radio-hall-front");
            townView(world, context, nourOrigin, 56.0, 5, 47.0, 180.0F,
                    "nour-boathouse-exterior");
            townView(world, context, nourOrigin, -10.0, 5, 47.0, 180.0F,
                    "nour-foghouse-exterior");
            world.getServer().runOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                var level = player.level();
                ItemStack buoy = new ItemStack(AdventureItems.SIGNAL_BUOY);
                player.setItemInHand(InteractionHand.MAIN_HAND, buoy);
                player.teleportTo(level, townOrigin.getX() + 31.5, townOrigin.getY() + 5,
                        townOrigin.getZ() + 15.5, EnumSet.noneOf(Relative.class),
                        0.0F, 0.0F, false);
                AdventureItems.SIGNAL_BUOY.use(level, player, InteractionHand.MAIN_HAND);
                if (player.getCooldowns().isOnCooldown(buoy)) {
                    throw new IllegalStateException("Signal Buoy launched without nearby water");
                }
                player.teleportTo(level, nourOrigin.getX() + 19.5, nourOrigin.getY() + 5,
                        nourOrigin.getZ() + 30.5, EnumSet.noneOf(Relative.class),
                        0.0F, 0.0F, false);
                if (AdventureItems.SIGNAL_BUOY.use(level, player, InteractionHand.MAIN_HAND)
                        != InteractionResult.SUCCESS_SERVER
                        || !player.getCooldowns().isOnCooldown(buoy)) {
                    throw new IllegalStateException("Signal Buoy did not launch beside harbor water");
                }
                player.teleportTo(level, nourOrigin.getX() + 23.5, nourOrigin.getY() + 6,
                        nourOrigin.getZ() + 29.5, EnumSet.noneOf(Relative.class),
                        45.0F, 35.0F, false);
            });
            context.waitTicks(20);
            context.takeScreenshot("nour-signal-buoy");
            world.getServer().runOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                var level = player.level();
                var callback = UseBlockCallback.EVENT.invoker();
                BlockPos table = townOrigin.offset(41, 6, 77);
                if (callback.interact(player, level, InteractionHand.MAIN_HAND, hit(table))
                        != InteractionResult.SUCCESS_SERVER
                        || FriendDomainUnlockState.get(server).cleared("nour")) {
                    throw new IllegalStateException("Signal Table did not stay locked");
                }
                BlockPos boat = nourOrigin.offset(56, 5, 23);
                BlockPos fog = nourOrigin.offset(-10, 5, 23);
                for (BlockPos clue : new BlockPos[]{boat, fog}) {
                    if (callback.interact(player, level, InteractionHand.MAIN_HAND, hit(clue))
                            != InteractionResult.SUCCESS_SERVER) {
                        throw new IllegalStateException("Harbor clue ignored use: " + clue);
                    }
                }
                for (BlockPos lamp : new BlockPos[]{nourOrigin.offset(18, 6, 24),
                        nourOrigin.offset(23, 6, 24), nourOrigin.offset(28, 6, 24)}) {
                    if (callback.interact(player, level, InteractionHand.MAIN_HAND, hit(lamp))
                            != InteractionResult.SUCCESS_SERVER) {
                        throw new IllegalStateException("Harbor lamp ignored use: " + lamp);
                    }
                }
                if (!FriendDomainUnlockState.get(server).cleared("nour")) {
                    throw new IllegalStateException("Harbor call did not unlock the table");
                }
            });
            townView(world, context, townOrigin, 40.0, 5, 83.0, 210.0F,
                    "second-chance-townhall-signal-table");
            BlockPos famOrigin = world.getServer().computeOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                var level = player.level();
                BlockPos origin = townOrigin.offset(-320, 0, 0);
                for (var piece : Map.of(
                        "fam_forum", origin,
                        "fam_story_hall", origin.offset(46, 0, 13),
                        "fam_open_school", origin.offset(-20, 0, 13)).entrySet()) {
                    var template = server.getStructureTemplateManager()
                            .get(Identifier.fromNamespaceAndPath("gems", piece.getKey()))
                            .orElseThrow();
                    if (!template.placeInWorld(level, piece.getValue(), piece.getValue(),
                            new StructurePlaceSettings(), RandomSource.create(), 2)) {
                        throw new IllegalStateException("Fam piece placement failed: "
                                + piece.getKey());
                    }
                }
                return origin;
            });
            townView(world, context, famOrigin, 23.5, 5, 61.0, 180.0F,
                    "fam-forum-approach");
            townView(world, context, famOrigin, 23.5, 6, 36.0, 180.0F,
                    "fam-forum-interior");
            townView(world, context, famOrigin, 56.0, 5, 47.0, 180.0F,
                    "fam-story-hall-exterior");
            townView(world, context, famOrigin, -10.0, 5, 47.0, 180.0F,
                    "fam-open-school-exterior");
            world.getServer().runOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                var level = player.level();
                var callback = UseBlockCallback.EVENT.invoker();
                BlockPos welcome = townOrigin.offset(36, 6, 86);
                if (callback.interact(player, level, InteractionHand.MAIN_HAND, hit(welcome))
                        != InteractionResult.SUCCESS_SERVER
                        || FriendDomainUnlockState.get(server).cleared("fam")) {
                    throw new IllegalStateException("Fam Welcome Board did not stay locked");
                }
                BlockPos board = famOrigin.offset(23, 6, 25);
                if (callback.interact(player, level, InteractionHand.MAIN_HAND, hit(board))
                        != InteractionResult.SUCCESS_SERVER
                        || FriendDomainUnlockState.get(server).cleared("fam")) {
                    throw new IllegalStateException("Fam board unlocked before both clues");
                }
                for (BlockPos clue : new BlockPos[]{famOrigin.offset(56, 6, 21),
                        famOrigin.offset(-12, 6, 21)}) {
                    if (callback.interact(player, level, InteractionHand.MAIN_HAND, hit(clue))
                            != InteractionResult.SUCCESS_SERVER) {
                        throw new IllegalStateException("Fam clue ignored use: " + clue);
                    }
                }
                if (!FamForumPersistentState.get(server).ready()) {
                    throw new IllegalStateException("Fam's shared clues were not saved");
                }
                if (callback.interact(player, level, InteractionHand.MAIN_HAND, hit(board))
                        != InteractionResult.SUCCESS_SERVER
                        || !FriendDomainUnlockState.get(server).cleared("fam")
                        || callback.interact(player, level, InteractionHand.MAIN_HAND, hit(welcome))
                        != InteractionResult.SUCCESS_SERVER) {
                    throw new IllegalStateException("Fam's shared Welcome Board did not unlock");
                }
            });
            BlockPos adlOrigin = world.getServer().computeOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                var level = player.level();
                BlockPos origin = townOrigin.offset(-440, 0, 0);
                for (var piece : Map.of(
                        "adl_survey_house", origin,
                        "adl_sightpost", origin.offset(46, 0, 13),
                        "adl_chart_shelter", origin.offset(-20, 0, 13)).entrySet()) {
                    var template = server.getStructureTemplateManager()
                            .get(Identifier.fromNamespaceAndPath("gems", piece.getKey()))
                            .orElseThrow();
                    if (!template.placeInWorld(level, piece.getValue(), piece.getValue(),
                            new StructurePlaceSettings(), RandomSource.create(), 2)) {
                        throw new IllegalStateException("Adl piece placement failed: "
                                + piece.getKey());
                    }
                }
                return origin;
            });
            townView(world, context, adlOrigin, 23.5, 5, 61.0, 180.0F,
                    "adl-survey-house-approach");
            townView(world, context, adlOrigin, 61.0, 6, 38.0, 180.0F,
                    "adl-sightpost-exterior");
            townView(world, context, adlOrigin, -10.0, 5, 38.0, 180.0F,
                    "adl-chart-shelter-exterior");
            world.getServer().runOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                var level = player.level();
                var callback = UseBlockCallback.EVENT.invoker();
                BlockPos board = townOrigin.offset(26, 6, 86);
                if (callback.interact(player, level, InteractionHand.MAIN_HAND, hit(board))
                        != InteractionResult.SUCCESS_SERVER
                        || FriendDomainUnlockState.get(server).cleared("adl")) {
                    throw new IllegalStateException("Adl's Atlas Pin board did not stay locked");
                }
                BlockPos map = adlOrigin.offset(23, 5, 23);
                if (callback.interact(player, level, InteractionHand.MAIN_HAND, hit(map))
                        != InteractionResult.SUCCESS_SERVER
                        || AdlSurveyState.get(server).mapped()) {
                    throw new IllegalStateException("Adl's map worked before both bearings");
                }
                for (BlockPos sight : new BlockPos[]{adlOrigin.offset(56, 16, 23),
                        adlOrigin.offset(-10, 6, 22)}) {
                    if (callback.interact(player, level, InteractionHand.MAIN_HAND, hit(sight))
                            != InteractionResult.SUCCESS_SERVER) {
                        throw new IllegalStateException("Adl sight ignored use: " + sight);
                    }
                }
                if (!AdlSurveyState.get(server).bothObserved()
                        || callback.interact(player, level, InteractionHand.MAIN_HAND, hit(map))
                        != InteractionResult.SUCCESS_SERVER
                        || !AdlSurveyState.get(server).mapped()) {
                    throw new IllegalStateException("Adl's shared map did not align");
                }
                BlockPos cairn = adlOrigin.offset(23, 5, 4);
                if (callback.interact(player, level, InteractionHand.MAIN_HAND, hit(cairn))
                        != InteractionResult.SUCCESS_SERVER
                        || !FriendDomainUnlockState.get(server).cleared("adl")
                        || callback.interact(player, level, InteractionHand.MAIN_HAND, hit(board))
                        != InteractionResult.SUCCESS_SERVER) {
                    throw new IllegalStateException("Adl's route did not open the Atlas Pin");
                }
                boolean hasPin = false;
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                    hasPin |= player.getInventory().getItem(slot).is(AdventureItems.ATLAS_PIN);
                }
                if (!hasPin) throw new IllegalStateException("Atlas Pin was not delivered");
                player.teleportTo(level, adlOrigin.getX() + 23.5,
                        adlOrigin.getY() + 5, adlOrigin.getZ() + 39.5,
                        EnumSet.noneOf(Relative.class), 0.0F, 0.0F, false);
                player.setShiftKeyDown(true);
                if (AdlSurveyRuntime.usePin(level, player) != InteractionResult.SUCCESS_SERVER
                        || !player.blockPosition().equals(AdlSurveyState.get(server).pin())) {
                    throw new IllegalStateException("Adl's shared pin was not saved");
                }
                player.setShiftKeyDown(false);
            });
            BlockPos othmanOrigin = world.getServer().computeOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                var level = player.level();
                BlockPos origin = townOrigin.offset(-560, 0, 0);
                for (var piece : Map.of(
                        "othman_library", origin,
                        "othman_catalog_rotunda", origin.offset(46, 0, 13),
                        "othman_study_wing", origin.offset(-20, 0, 13)).entrySet()) {
                    var template = server.getStructureTemplateManager()
                            .get(Identifier.fromNamespaceAndPath("gems", piece.getKey()))
                            .orElseThrow();
                    if (!template.placeInWorld(level, piece.getValue(), piece.getValue(),
                            new StructurePlaceSettings(), RandomSource.create(), 2)) {
                        throw new IllegalStateException("Othman piece placement failed: "
                                + piece.getKey());
                    }
                }
                return origin;
            });
            townView(world, context, othmanOrigin, 23.5, 5, 62.0, 180.0F,
                    "othman-library-approach");
            townView(world, context, othmanOrigin, 23.5, 6, 37.0, 180.0F,
                    "othman-library-interior");
            townView(world, context, othmanOrigin, 57.0, 5, 38.0, 180.0F,
                    "othman-catalog-rotunda");
            townView(world, context, othmanOrigin, -10.0, 5, 38.0, 180.0F,
                    "othman-study-wing");
            world.getServer().runOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                var level = player.level();
                var callback = UseBlockCallback.EVENT.invoker();
                BlockPos shelf = townOrigin.offset(31, 6, 86);
                if (callback.interact(player, level, InteractionHand.MAIN_HAND, hit(shelf))
                        != InteractionResult.SUCCESS_SERVER
                        || FriendDomainUnlockState.get(server).cleared("othman")) {
                    throw new IllegalStateException("Othman's Knowledge Shelf did not stay locked");
                }
                BlockPos choice = othmanOrigin.offset(23, 5, 16);
                if (callback.interact(player, level, InteractionHand.MAIN_HAND, hit(choice))
                        != InteractionResult.SUCCESS_SERVER
                        || OthmanArchiveState.get(server).published() != 0) {
                    throw new IllegalStateException("Othman's desk accepted a choice before both notes");
                }
                for (BlockPos note : new BlockPos[]{othmanOrigin.offset(56, 5, 23),
                        othmanOrigin.offset(-10, 6, 26)}) {
                    if (callback.interact(player, level, InteractionHand.MAIN_HAND, hit(note))
                            != InteractionResult.SUCCESS_SERVER) {
                        throw new IllegalStateException("Othman note ignored use: " + note);
                    }
                }
                if (!OthmanArchiveState.get(server).ready()
                        || callback.interact(player, level, InteractionHand.MAIN_HAND, hit(choice))
                        != InteractionResult.SUCCESS_SERVER
                        || OthmanArchiveState.get(server).published() != OthmanArchiveState.STUDY
                        || !FriendDomainUnlockState.get(server).cleared("othman")
                        || callback.interact(player, level, InteractionHand.MAIN_HAND, hit(shelf))
                        != InteractionResult.SUCCESS_SERVER) {
                    throw new IllegalStateException("Othman's shared lead did not reach the townhall");
                }
            });
            BlockPos yehiaOrigin = world.getServer().computeOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                var level = player.level();
                BlockPos origin = townOrigin.offset(-640, 0, 0);
                for (var piece : Map.of(
                        "yehia_story_lodge", origin,
                        "yehia_truth_garden", origin.offset(46, 0, 13),
                        "yehia_prop_house", origin.offset(-20, 0, 13)).entrySet()) {
                    var template = server.getStructureTemplateManager()
                            .get(Identifier.fromNamespaceAndPath("gems", piece.getKey()))
                            .orElseThrow();
                    if (!template.placeInWorld(level, piece.getValue(), piece.getValue(),
                            new StructurePlaceSettings(), RandomSource.create(), 2)) {
                        throw new IllegalStateException("Yehia piece placement failed: "
                                + piece.getKey());
                    }
                }
                return origin;
            });
            townView(world, context, yehiaOrigin, 23.5, 5, 62.0, 180.0F,
                    "yehia-story-lodge-approach");
            townView(world, context, yehiaOrigin, 56.0, 5, 40.0, 180.0F,
                    "yehia-truth-garden");
            townView(world, context, yehiaOrigin, -10.0, 5, 40.0, 180.0F,
                    "yehia-prop-house");
            world.getServer().runOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                var level = player.level();
                var callback = UseBlockCallback.EVENT.invoker();
                BlockPos board = townOrigin.offset(21, 6, 86);
                if (callback.interact(player, level, InteractionHand.MAIN_HAND, hit(board))
                        != InteractionResult.SUCCESS_SERVER
                        || FriendDomainUnlockState.get(server).cleared("yehia")) {
                    throw new IllegalStateException("Yehia's Story Trail did not stay locked");
                }
                BlockPos table = yehiaOrigin.offset(23, 6, 16);
                if (callback.interact(player, level, InteractionHand.MAIN_HAND, hit(table))
                        != InteractionResult.SUCCESS_SERVER || YehiaStoryState.get(server).started()) {
                    throw new IllegalStateException("Yehia's route started before both side stories");
                }
                for (BlockPos clue : new BlockPos[]{yehiaOrigin.offset(56, 5, 23),
                        yehiaOrigin.offset(-10, 6, 25)}) {
                    if (callback.interact(player, level, InteractionHand.MAIN_HAND, hit(clue))
                            != InteractionResult.SUCCESS_SERVER) {
                        throw new IllegalStateException("Yehia clue ignored use: " + clue);
                    }
                }
                var story = YehiaStoryState.get(server);
                if (!story.ready() || callback.interact(player, level, InteractionHand.MAIN_HAND,
                        hit(table)) != InteractionResult.SUCCESS_SERVER || !story.started()) {
                    throw new IllegalStateException("Yehia's story did not start after both clues");
                }
                BlockPos mushroom = yehiaOrigin.offset(52, 5, 23);
                BlockPos lantern = yehiaOrigin.offset(56, 5, 19);
                BlockPos tree = yehiaOrigin.offset(60, 5, 23);
                callback.interact(player, level, InteractionHand.MAIN_HAND, hit(tree));
                if (story.step() != 0) throw new IllegalStateException("Wrong story mark did not reset");
                for (BlockPos mark : new BlockPos[]{mushroom, lantern, tree}) {
                    if (callback.interact(player, level, InteractionHand.MAIN_HAND, hit(mark))
                            != InteractionResult.SUCCESS_SERVER) {
                        throw new IllegalStateException("Yehia route mark ignored use: " + mark);
                    }
                }
                if (!story.completed() || !FriendDomainUnlockState.get(server).cleared("yehia")
                        || callback.interact(player, level, InteractionHand.MAIN_HAND, hit(board))
                        != InteractionResult.SUCCESS_SERVER) {
                    throw new IllegalStateException("Yehia's Story Trail did not unlock");
                }
                boolean hasThread = false;
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                    hasThread |= player.getInventory().getItem(slot).is(AdventureItems.STORY_THREAD);
                }
                if (!hasThread) throw new IllegalStateException("Story Thread was not issued");
            });
        }
        naturalSignal(context);
    }

    /** Checks the public signal against generated 26.3 structures, not the flat render world. */
    private static void naturalSignal(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(settings -> {
                    settings.setSeed("SecondChanceBiomeSmoke");
                    settings.setGenerateStructures(true);
                }).create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                var level = player.level();
                BlockPos table = player.blockPosition().offset(1, 0, 0);
                level.setBlockAndUpdate(table, DomainBlocks.TOWNHALL_SIGNAL_TABLE.defaultBlockState());
                FriendDomainUnlockState.get(server).clear("nour");
                var callback = UseBlockCallback.EVENT.invoker();
                LandmarkKey[] targets = LandmarkKey.values();
                int selected = -1;
                for (int i = 0; i < targets.length; i++) {
                    var tag = TagKey.create(Registries.STRUCTURE,
                            Identifier.fromNamespaceAndPath("gems", "atlas_target/" + targets[i].id()));
                    BlockPos found = level.findNearestMapStructure(tag, table, 128, false);
                    if (found != null) {
                        selected = i;
                        break;
                    }
                }
                if (selected < 0) {
                    throw new IllegalStateException("No authored expedition generated within Signal Table range");
                }
                for (int i = 0; i < selected; i++) {
                    player.setShiftKeyDown(true);
                    if (callback.interact(player, level, InteractionHand.MAIN_HAND, hit(table))
                            != InteractionResult.SUCCESS_SERVER) {
                        throw new IllegalStateException("Signal Table did not select a target");
                    }
                }
                player.setShiftKeyDown(false);
                if (callback.interact(player, level, InteractionHand.MAIN_HAND, hit(table))
                        != InteractionResult.SUCCESS_SERVER) {
                    throw new IllegalStateException("Signal Table failed on a generated world");
                }
            });
            context.waitTicks(2);
            context.takeScreenshot("second-chance-natural-signal");
            FramefallRoute route = world.getServer().computeOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                var tag = TagKey.create(Registries.STRUCTURE,
                        Identifier.fromNamespaceAndPath("gems", "atlas_target/framefall_gallery"));
                BlockPos found = player.level().findNearestMapStructure(tag,
                        player.blockPosition(), 128, false);
                if (found == null) {
                    throw new IllegalStateException("Framefall has no natural structure near this seed");
                }
                FramefallRoute natural = findFramefallRoute(player.level(), tag, found);
                // The atlas repeats a location actionbar while held; leave
                // the architectural review captures unobstructed.
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                int viewX = natural.entry().getX() - natural.dx() * 20;
                int viewZ = natural.entry().getZ() - natural.dz() * 20;
                player.level().getChunk(viewX >> 4, viewZ >> 4);
                int viewY = Math.max(natural.gallery().maxY() + 8,
                        player.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                viewX, viewZ) + 18);
                player.setNoGravity(true);
                player.teleportTo(player.level(), viewX + 0.5, viewY,
                        viewZ + 0.5, EnumSet.noneOf(Relative.class), natural.yaw(), 24.0F, false);
                return natural;
            });
            context.waitTicks(100);
            world.getConnection().waitForChunksRender();
            context.runOnClient(client -> {
                client.gui.hud.getChat().clearMessages(false);
                client.gui.toastManager().clear();
            });
            context.takeScreenshot("second-chance-natural-framefall");
            world.getServer().runOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                player.teleportTo(player.level(), route.entry().getX() + 0.5,
                        route.entry().getY() + 0.1, route.entry().getZ() + 0.5,
                        EnumSet.noneOf(Relative.class), route.yaw(), -4.0F, false);
            });
            context.waitTicks(20);
            world.getConnection().waitForChunksRender();
            context.runOnClient(client -> {
                client.gui.hud.getChat().clearMessages(false);
                client.gui.toastManager().clear();
            });
            context.takeScreenshot("second-chance-natural-framefall-approach");
            context.waitTicks(10);
            context.takeScreenshot("second-chance-natural-framefall-walk-axis");
            world.getServer().runOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                player.setNoGravity(false);
                player.teleportTo(player.level(), route.entry().getX() + 0.5,
                        route.entry().getY() + 0.1, route.entry().getZ() + 0.5,
                        EnumSet.noneOf(Relative.class), route.yaw(), 0.0F, false);
            });
            context.waitTicks(10);
            world.getConnection().waitForChunksRender();
            context.getInput().holdKey(options -> options.keyUp);
            context.getInput().holdKey(options -> options.keyJump);
            context.waitTicks(220);
            context.getInput().releaseKey(options -> options.keyUp);
            context.getInput().releaseKey(options -> options.keyJump);
            context.waitTicks(10);
            world.getServer().runOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                double progress = (player.getX() - route.entry().getX() - 0.5) * route.dx()
                        + (player.getZ() - route.entry().getZ() - 0.5) * route.dz();
                double offset = route.dx() == 0
                        ? Math.abs(player.getX() - route.entry().getX() - 0.5)
                        : Math.abs(player.getZ() - route.entry().getZ() - 0.5);
                if (!player.isAlive() || progress < 30.0 || offset > 6.0
                        || player.getY() < route.promenade().minY() + 4) {
                    throw new IllegalStateException("Framefall promenade could not reach the Atelier door: "
                            + player.position() + " progress=" + progress + " offset=" + offset);
                }
            });
            context.takeScreenshot("second-chance-natural-framefall-walk-end");
        }
    }

    private record FramefallRoute(BoundingBox gallery, BoundingBox promenade,
                                  BlockPos entry, int dx, int dz, float yaw) { }

    /** The locate result may be beside the actual start chunk; resolve the generated pieces. */
    private static FramefallRoute findFramefallRoute(ServerLevel level, TagKey<Structure> tag,
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
                        List<BoundingBox> pieces = start.getPieces().stream()
                                .map(piece -> piece.getBoundingBox()).toList();
                        BoundingBox gallery = framefallPiece(pieces, 47, 47);
                        BoundingBox atelier = framefallPiece(pieces, 31, 31);
                        BoundingBox promenade = framefallPiece(pieces, 31, 47);
                        if (gallery == null || atelier == null || promenade == null) {
                            throw new IllegalStateException("Natural Framefall is missing its gallery, Atelier or promenade: "
                                    + start.getChunkPos());
                        }
                        boolean alongX = promenade.getXSpan() > promenade.getZSpan();
                        int atelierAxis = alongX ? (atelier.minX() + atelier.maxX()) / 2
                                : (atelier.minZ() + atelier.maxZ()) / 2;
                        int low = alongX ? promenade.minX() : promenade.minZ();
                        int high = alongX ? promenade.maxX() : promenade.maxZ();
                        boolean nearLow = Math.abs(atelierAxis - low) < Math.abs(atelierAxis - high);
                        int towardAtelier = nearLow ? -1 : 1;
                        int far = nearLow ? high : low;
                        int across = alongX ? (promenade.minZ() + promenade.maxZ()) / 2
                                : (promenade.minX() + promenade.maxX()) / 2;
                        BlockPos entry = null;
                        int firstY = Integer.MIN_VALUE;
                        int previousY = Integer.MIN_VALUE;
                        for (int distance : new int[]{3, 12, 23, 34, 43}) {
                            int axis = far + towardAtelier * distance;
                            BlockPos floor = framefallWalkTile(level, promenade,
                                    alongX ? axis : across, alongX ? across : axis);
                            if (distance == 3) {
                                entry = floor.above();
                                firstY = floor.getY();
                            } else if (previousY < floor.getY()
                                    || previousY - floor.getY() > 2) {
                                throw new IllegalStateException("Framefall promenade has a broken descent toward the Atelier");
                            }
                            previousY = floor.getY();
                        }
                        int descent = firstY - previousY;
                        if (descent < 4 || descent > 7) {
                            throw new IllegalStateException("Framefall promenade grade changed: " + descent);
                        }
                        int dx = alongX ? towardAtelier : 0;
                        int dz = alongX ? 0 : towardAtelier;
                        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                        return new FramefallRoute(gallery, promenade, entry, dx, dz, yaw);
                    }
                }
            }
        }
        throw new IllegalStateException("Located Framefall has no saved start near " + located);
    }

    private static BoundingBox framefallPiece(List<BoundingBox> pieces, int width, int depth) {
        return pieces.stream().filter(box -> box.getXSpan() == width && box.getZSpan() == depth
                || box.getXSpan() == depth && box.getZSpan() == width).findFirst().orElse(null);
    }

    private static BlockPos framefallWalkTile(ServerLevel level, BoundingBox promenade, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        for (int y = promenade.minY() + 12; y >= promenade.minY() + 4; y--) {
            BlockPos floor = new BlockPos(x, y, z);
            var state = level.getBlockState(floor);
            String blockPath = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            boolean paved = state.is(Blocks.POLISHED_ANDESITE)
                    || blockPath.equals("cut_copper") || blockPath.equals("oxidized_cut_copper")
                    || state.is(Blocks.STONE_BRICK_STAIRS)
                    || state.is(DomainBlocks.GALLERY_PLANE);
            if (paved && level.getBlockState(floor.above()).getCollisionShape(level, floor.above()).isEmpty()
                    && level.getBlockState(floor.above(2))
                            .getCollisionShape(level, floor.above(2)).isEmpty()) {
                return floor;
            }
        }
        throw new IllegalStateException("Framefall has no clear authored promenade tile at " + x + "," + z);
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5,
                pos.getZ() + 0.5), Direction.UP, pos, false);
    }

    private static void townView(TestSingleplayerContext world, ClientGameTestContext context,
            BlockPos origin, double x, double y, double z, float yaw, String screenshot) {
        world.getServer().runOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            player.teleportTo(player.level(), origin.getX() + x, origin.getY() + y,
                    origin.getZ() + z, EnumSet.noneOf(Relative.class), yaw, 0.0F, false);
        });
        context.waitTicks(20);
        world.getConnection().waitForChunksRender();
        context.runOnClient(client -> {
            client.gui.hud.getChat().clearMessages(false);
            client.gui.toastManager().clear();
        });
        context.takeScreenshot(screenshot);
    }
}
