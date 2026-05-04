package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.CampaignPersistentState;
import com.feel.gems.adventure.CampaignRouteState;
import com.feel.gems.adventure.DomainBlocks;
import com.feel.gems.adventure.FriendDomainUnlockState;
import com.feel.gems.adventure.InkboundPersistentState;
import com.feel.gems.adventure.LandmarkKey;
import com.feel.gems.adventure.OthmanArchiveState;
import com.feel.gems.adventure.ReleaseRouteState;
import com.feel.gems.adventure.RepairRouteState;
import com.feel.gems.adventure.RequiredChapters;
import com.feel.gems.adventure.RouteFinaleRuntime;
import com.feel.gems.adventure.RouteFinaleState;
import com.feel.gems.adventure.YehiaStoryState;
import com.feel.gems.adventure.boss.StrayCadenzaEntity;
import com.feel.gems.adventure.endsource.EndSourceBlocks;
import com.feel.gems.adventure.endsource.EndSourceRuntime;
import com.feel.gems.adventure.endsource.EndSourceState;
import com.feel.gems.adventure.nether.NetherChapterBlocks;
import com.feel.gems.adventure.nether.NetherChapterState;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opt-in connected-client chapter sequence on a disposable saved world. */
public final class RequiredChapterAcceptanceServerSmoke implements ModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("gems-required-chapter-acceptance");
    private static final TagKey<Structure> COURT = TagKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath("gems", "atlas_target/unwritten_court"));
    private final Site[] sites = new Site[3];
    private String phase;
    private CampaignRouteState.Path route;
    private List<Step> steps;
    private ServerPlayer a;
    private ServerPlayer b;
    private Court court;
    private Step pending;
    private int index;
    private int readyAt;
    private int sentAt;
    private int stopAt;
    private boolean finished;
    private boolean openingChecked;
    private boolean waitingPlayersStaged;
    private int joinedBy;
    private Target pendingTarget;

    @Override
    public void onInitialize() {
        if (!Boolean.getBoolean("gems.requiredChapterAcceptance.server")) return;
        phase = System.getProperty("gems.requiredChapterAcceptance.phase", "");
        String routeName = System.getProperty("gems.requiredChapterAcceptance.route", "remember");
        route = CampaignRouteState.Path.from(routeName);
        if (route == null || !Set.of("before", "after", "court").contains(phase))
            throw new IllegalArgumentException("Required chapter route/phase mismatch");
        String[] parts = System.getProperty("gems.requiredChapterAcceptance.roots", "").split(";");
        if (parts.length != 3) throw new IllegalArgumentException("Three recorded saved roots required");
        for (int i = 0; i < parts.length; i++) sites[i] = Site.parse(parts[i]);
        if (!sites[0].name.equals("ash_annex") || !sites[1].name.equals("ember_bridge")
                || !sites[2].name.equals("end_source"))
            throw new IllegalArgumentException("Roots must be Ash, Ember, End in that order");
        steps = plan(phase, route);
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(Commands.literal("reqchapter")
                        .then(Commands.literal("ack")
                                .then(Commands.argument("stage", StringArgumentType.word())
                                        .executes(context -> acknowledge(context.getSource().getServer(),
                                                context.getSource().getPlayer(),
                                                StringArgumentType.getString(context, "stage")))))));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            String name = player.getName().getString();
            if (name.equals("ReqA")) a = player;
            else if (name.equals("ReqB")) b = player;
            else { fail(server, "Unexpected player " + name); return; }
            LOG.info("REQCH_JOIN phase={} role={} uuid={} remote={}", phase, name,
                    player.getUUID(), player.connection.getRemoteAddress());
            if (phase.equals("before") && name.equals("ReqA")) prepareRoute(server, player);
            if (name.equals("ReqA")) LOG.info("REQCH_READY_B phase={}", phase);
        });
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }

    private static List<Step> plan(String phase, CampaignRouteState.Path route) {
        return switch (phase) {
            case "before" -> List.of(
                    new Step("A", "end_blocked", "Restore the Ash Annex", 0),
                    new Step("A", "ash_register", "Ash register:", 0),
                    new Step("B", "ash_signal", "Signal copy:", 0),
                    new Step("A", "ember_east", "One shelter stands.", 0));
            case "after" -> {
                List<Step> shared = List.of(
                    new Step("B", "ash_platform", "Platform token:", 0),
                    new Step("B", "ash_board_wrong", "The board resets.", 0),
                    new Step("B", "ash_board_shelter", "Shelter route set.", 0),
                    new Step("A", "ash_board_transfer", "Transfer set.", 0),
                    new Step("B", "ash_board_return", "The Ash Annex timetable is restored.", 0),
                    new Step("A", "ember_west", "Both shelters stand.", 0),
                    new Step("B", "ember_start", "Evacuation started.", 0),
                    new Step("A", "ember_start_again", "An evacuation is underway.", 0),
                    new Step("B", "ember_finish_early", "Hold the center watch line", 0),
                    new Step("A", "ember_midpoint", "Hold this watch line", 70),
                    new Step("B", "ember_finish", "The Ember Bridge evacuation succeeded.", 0),
                    new Step("A", "court_blocked", "Use an End gateway", 0));
                List<Step> routeSteps = switch (route) {
                    case REMEMBER -> List.of(
                            new Step("A", "end_start", "Remember:", 0),
                            new Step("A", "end_west_account", "The west account says", 0),
                            new Step("A", "end_west_lens", "One account is safe.", 0),
                            new Step("A", "end_east_account", "The east account says", 0),
                            new Step("A", "end_east_lens", "Both conflicting accounts", 0));
                    case REPAIR -> List.of(
                            new Step("A", "end_start", "Repair:", 0),
                            new Step("A", "end_repair_west_lens", "This work point held.", 0, true),
                            new Step("A", "end_repair_east_lens", "Both work points held.", 0, true));
                    case RELEASE -> List.of(
                            new Step("A", "end_start", "Release:", 0),
                            new Step("A", "end_release_signal_1", "The route shifted.", 0),
                            new Step("A", "end_release_signal_2", "The route shifted.", 0),
                            new Step("A", "end_release_signal_3", "The route shifted.", 0),
                            new Step("A", "end_release_signal_4", "The signal crossed every gallery.", 0));
                };
                java.util.ArrayList<Step> all = new java.util.ArrayList<>(shared);
                all.addAll(routeSteps);
                all.add(new Step("A", "end_finish", "The End Source is understood.", 0));
                all.add(new Step("A", "end_return", "Returned safely", 0));
                yield List.copyOf(all);
            }
            case "court" -> switch (route) {
                case REMEMBER -> List.of(
                    new Step("B", "court_start", "Remember: the source has secured the west account.", 0),
                    new Step("B", "court_east_room", "The steward's account", 0),
                    new Step("B", "court_east_mark", "Both voices are kept", 0),
                    new Step("B", "court_finish", "Remember is complete.", 0));
                case REPAIR -> List.of(
                    new Step("B", "court_start", "Repair: the source braces the west work site", 0),
                    new Step("B", "court_west_room", "The End Source brace already supplies", 0),
                    new Step("B", "court_east_room", "Materials are in place.", 0),
                    new Step("A", "court_west_mark", "Stand by this mark", 0),
                    new Step("B", "court_east_mark", "Stand by this mark", 250),
                    new Step("B", "court_finish", "Repair is complete.", 0));
                case RELEASE -> List.of(
                    new Step("B", "court_start", "Release: the source has carried the first signal.", 0),
                    new Step("B", "court_release_signal_2", "Signal carried.", 0),
                    new Step("B", "court_release_signal_3", "Signal carried.", 0),
                    new Step("B", "court_release_signal_4", "The changing route is open.", 0),
                    new Step("B", "court_finish", "Release is complete.", 0));
            };
            default -> throw new IllegalArgumentException(phase);
        };
    }

    private void prepareRoute(MinecraftServer server, ServerPlayer player) {
        if (!RequiredChapters.active(server)) {
            fail(server, "Three test-only structure sets are not active in disposable save");
            return;
        }
        CampaignRouteState route = CampaignRouteState.get(server);
        if (route.chosen() != null || NetherChapterState.get(server).recordCount() != 0
                || NetherChapterState.get(server).shelterCount() != 0
                || EndSourceState.get(server).clearedPath() != null
                || RouteFinaleState.get(server).complete()) {
            fail(server, "Before phase must start from an uncompleted pre-Council copy");
            return;
        }
        // Test setup: old boss and field-route prerequisites are deliberately
        // saved through their state APIs; this does not claim they were earned.
        for (LandmarkKey key : LandmarkKey.values())
            CampaignPersistentState.get(server).recordVictory(List.of(player.getUUID()), key);
        boolean prepared = route.vote(player.getUUID(), this.route)
                && route.confirm(List.of(player.getUUID()), player.getUUID())
                    == CampaignRouteState.Resolution.CHOSEN;
        prepared &= prepareEarlierStory(server);
        prepared &= switch (this.route) {
            case REMEMBER -> route.recordRememberPrint(0) && route.recordRememberPrint(1)
                && route.bindRememberInkbound()
                && route.recordRememberWitness(1) && route.recordRememberWitness(2)
                && route.compareRememberArchive()
                && route.startRememberTale()
                && route.markRememberTale(YehiaStoryState.TREE) == 2
                && route.markRememberTale(YehiaStoryState.LANTERN) == 3
                && route.markRememberTale(YehiaStoryState.MUSHROOM) == 4
                && route.finishRememberTale();
            case REPAIR -> {
                RepairRouteState repair = RepairRouteState.get(server);
                yield repair.recordRepair(RepairRouteState.YARD)
                        && repair.recordRepair(RepairRouteState.BRIDGE)
                        && repair.recordRepair(RepairRouteState.LOFT)
                        && repair.recordRiverwardBrace(RepairRouteState.WEST_BRACE)
                        && repair.recordRiverwardBrace(RepairRouteState.EAST_BRACE)
                        && repair.finishRiverward() && repair.finishRedline();
            }
            case RELEASE -> {
                ReleaseRouteState release = ReleaseRouteState.get(server);
                boolean anchors = release.finishNourRelay()
                        && release.recordAliAnchor(ReleaseRouteState.HIGH_MAP,
                                new BlockPos(0, 80, 0))
                        && release.recordAliAnchor(ReleaseRouteState.CUT_PASSAGE,
                                new BlockPos(32, 80, 0))
                        && release.finishAliShortcut();
                int serial = release.startFamDispatch(server.overworld().getGameTime());
                yield anchors && serial > 0
                        && release.stampFamDispatch(serial, ReleaseRouteState.FAM_STORY,
                                server.overworld().getGameTime())
                        && release.stampFamDispatch(serial, ReleaseRouteState.FAM_SCHOOL,
                                server.overworld().getGameTime())
                        && release.finishFamDispatch(serial, server.overworld().getGameTime());
            }
        };
        if (!prepared || !earlierStoryReady(server) || !RouteFinaleRuntime.ready(server, this.route)) {
            fail(server, "Test-only " + this.route + " prerequisites failed to prepare");
            return;
        }
        LOG.info("REQCH_SETUP_ONLY route={} bossBits=7 earlierStory=true fieldLeads=3 player={}",
                this.route, player.getUUID());
    }

    private boolean prepareEarlierStory(MinecraftServer server) {
        FriendDomainUnlockState domains = FriendDomainUnlockState.get(server);
        return switch (route) {
            case REMEMBER -> {
                InkboundPersistentState inkbound = InkboundPersistentState.get(server);
                boolean prints = inkbound.record(0) && inkbound.record(1) && inkbound.record(2);
                OthmanArchiveState archive = OthmanArchiveState.get(server);
                boolean published = archive.record(OthmanArchiveState.CATALOG)
                        && archive.record(OthmanArchiveState.STUDY)
                        && archive.publish(OthmanArchiveState.CATALOG);
                YehiaStoryState story = YehiaStoryState.get(server);
                story.record(YehiaStoryState.TRUTH);
                story.record(YehiaStoryState.PROP);
                boolean tale = story.start()
                        && !story.mark(YehiaStoryState.MUSHROOM)
                        && !story.mark(YehiaStoryState.LANTERN)
                        && story.mark(YehiaStoryState.TREE);
                yield prints && published && tale
                        && domains.clear("othman") && domains.clear("yehia");
            }
            case REPAIR -> domains.clear("soryal") && domains.clear("khaled");
            case RELEASE -> domains.clear("nour") && domains.clear("ali")
                    && domains.clear("fam");
        };
    }

    private boolean earlierStoryReady(MinecraftServer server) {
        FriendDomainUnlockState domains = FriendDomainUnlockState.get(server);
        return switch (route) {
            case REMEMBER -> InkboundPersistentState.get(server).model().unlocked()
                    && OthmanArchiveState.get(server).published() == OthmanArchiveState.CATALOG
                    && YehiaStoryState.get(server).completed()
                    && domains.cleared("othman") && domains.cleared("yehia");
            case REPAIR -> domains.cleared("soryal") && domains.cleared("khaled");
            case RELEASE -> domains.cleared("nour") && domains.cleared("ali")
                    && domains.cleared("fam");
        };
    }

    private void tick(MinecraftServer server) {
        if (finished) {
            if (stopAt > 0 && server.getTickCount() >= stopAt) server.halt(false);
            return;
        }
        if (joinedBy == 0) joinedBy = server.getTickCount() + 20 * 600;
        if (a == null || b == null || !a.connection.hasClientLoaded()
                || !b.connection.hasClientLoaded()) {
            if (server.getTickCount() > joinedBy) fail(server, "Two-client join timeout");
            return;
        }
        if (phase.equals("court") && !waitingPlayersStaged) {
            // JOIN can run before vanilla finishes registering the player's
            // chunk tracker. Move both fixture teleports to a later server tick.
            stageWaitingPlayer(server, a);
            if (finished) return;
            stageWaitingPlayer(server, b);
            if (finished) return;
            waitingPlayersStaged = true;
            return;
        }
        if (!openingChecked) {
            if (!checkOpening(server)) return;
            openingChecked = true;
            LOG.info("REQCH_PHASE_READY phase={} bothConnected=true", phase);
            readyAt = server.getTickCount() + 10;
        }
        if (pending != null) {
            if (sentAt == 0 && server.getTickCount() >= readyAt) {
                ServerPlayer actor = pending.role.equals("A") ? a : b;
                actor.sendSystemMessage(Component.literal(instruction(pending, pendingTarget)));
                sentAt = server.getTickCount();
                LOG.info("REQCH_INSTRUCTION phase={} stage={} actor={}", phase,
                        pending.stage, actor.getName().getString());
            } else if (sentAt > 0 && server.getTickCount() - sentAt > 20 * 45) {
                fail(server, "No real packet response for " + pending.stage);
            }
            return;
        }
        if (server.getTickCount() < readyAt) return;
        if (index == steps.size()) { pass(server); return; }
        pending = steps.get(index);
        sentAt = 0;
        pendingTarget = stage(server, pending);
        if (pendingTarget == null) return;
        readyAt = server.getTickCount() + 20;
    }

    private boolean checkOpening(MinecraftServer server) {
        NetherChapterState nether = NetherChapterState.get(server);
        EndSourceState source = EndSourceState.get(server);
        boolean valid = RequiredChapters.active(server)
                && CampaignRouteState.get(server).chosen() == route
                && earlierStoryReady(server)
                && RouteFinaleRuntime.ready(server, route)
                && !RouteFinaleState.get(server).complete();
        if (phase.equals("before")) valid &= nether.recordCount() == 0
                && nether.shelterCount() == 0 && source.clearedPath() == null;
        if (phase.equals("after")) valid &= nether.recordCount() == 2
                && nether.shelterCount() == 1 && !nether.annexCleared()
                && !nether.bridgeCleared() && source.clearedPath() == null;
        if (phase.equals("court")) valid &= nether.requiredChaptersCleared()
                && source.cleared(route);
        if (!valid) fail(server, "Saved opening flags do not match " + phase);
        return valid;
    }

    private Target stage(MinecraftServer server, Step step) {
        ServerPlayer actor = step.role.equals("A") ? a : b;
        Target target = target(server, step.stage);
        if (target == null) return null;
        ServerLevel level = server.getLevel(target.dimension);
        if (level == null) { fail(server, "Missing dimension for " + step.stage); return null; }
        level.getChunk(target.pos.getX() >> 4, target.pos.getZ() >> 4);
        if (!level.getBlockState(target.pos).is(target.block)) {
            fail(server, "Saved control changed at " + step.stage + " " + target.pos);
            return null;
        }
        if ((step.stage.equals("court_blocked") || step.stage.equals("court_start"))
                && !clearSyntheticCourtBoss(server, level, target.pos, step.stage)) return null;
        BlockPos feet = adjacent(level, target.pos);
        if (feet == null) { fail(server, "No safe stand for " + step.stage); return null; }
        if (phase.equals("court") && actor.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
            fail(server, "Court actor is not in survival mode at " + step.stage);
            return null;
        }
        if (step.stage.equals("ember_east") || step.stage.equals("ember_west")) {
            actor.getInventory().add(new ItemStack(Items.BRICK, 4));
            actor.getInventory().add(new ItemStack(Items.COPPER_INGOT, 2));
            LOG.info("REQCH_SETUP_ONLY materials=4_bricks_2_copper actor={} stage={}",
                    step.role, step.stage);
        }
        if (step.stage.equals("court_east_room") && route == CampaignRouteState.Path.REPAIR) {
            actor.getInventory().add(new ItemStack(Items.GLASS_PANE, 1));
            LOG.info("REQCH_SETUP_ONLY materials=1_glass_pane actor={} stage={}",
                    step.role, step.stage);
        }
        actor.teleportTo(level, feet.getX() + .5, feet.getY(), feet.getZ() + .5,
                Set.of(), 0, 0, true);
        LOG.info("REQCH_SETUP_ONLY teleport stage={} actor={} dimension={} feet={} control={}",
                step.stage, step.role, level.dimension().identifier(), feet, target.pos);
        return target;
    }

    private boolean clearSyntheticCourtBoss(MinecraftServer server, ServerLevel level,
                                            BlockPos dais, String stage) {
        if (!CampaignPersistentState.get(server).model().worldChanged(LandmarkKey.UNWRITTEN)) {
            fail(server, "Court boss cleanup lacks a saved victory at " + stage);
            return false;
        }
        // The fixture grants boss victories through saved state. A naturally
        // spawned Court boss can still be alive in that synthetic post-boss
        // copy; discard only the one belonging to this recorded Court.
        List<StrayCadenzaEntity> nearby = level.getEntitiesOfClass(StrayCadenzaEntity.class,
                new AABB(dais).inflate(20), StrayCadenzaEntity::isAlive);
        if (nearby.size() > 1) {
            fail(server, "Multiple live Cadenza near recorded Court at " + stage);
            return false;
        }
        if (!nearby.isEmpty()) nearby.getFirst().discard(); // No loot or second victory.
        LOG.info("REQCH_SETUP_ONLY clear_post_victory_cadenza stage={} dais={} removed={} noDrops=true",
                stage, dais, nearby.size());
        return true;
    }

    private void stageWaitingPlayer(MinecraftServer server, ServerPlayer player) {
        if (player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
            fail(server, "Court fixture player did not join in survival mode");
            return;
        }
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getRespawnData().pos();
        BlockPos feet = null;
        for (int radius = 0; radius <= 8 && feet == null; radius++) {
            for (int dx = -radius; dx <= radius && feet == null; dx++) {
                for (int dz = -radius; dz <= radius && feet == null; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    level.getChunk((spawn.getX() + dx) >> 4, (spawn.getZ() + dz) >> 4);
                    for (int dy = 8; dy >= -12; dy--) {
                        BlockPos candidate = spawn.offset(dx, dy, dz);
                        if (safeWaitingStand(level, player, candidate)) {
                            feet = candidate;
                            break;
                        }
                    }
                }
            }
        }
        if (feet == null) {
            fail(server, "No safe Overworld waiting stand near spawn");
            return;
        }
        player.teleportTo(level, feet.getX() + .5, feet.getY(), feet.getZ() + .5,
                Set.of(), 0, 0, true);
        LOG.info("REQCH_SETUP_ONLY waiting_at_spawn role={} feet={} survival=true",
                player.getName().getString(), feet);
    }

    private static boolean safeWaitingStand(ServerLevel level, ServerPlayer player, BlockPos feet) {
        BlockPos below = feet.below(), head = feet.above();
        if (feet.getY() <= level.getMinY() || head.getY() >= level.getMaxY()
                || !level.getWorldBorder().isWithinBounds(feet)) return false;
        var support = level.getBlockState(below);
        var floor = level.getBlockState(feet);
        var ceiling = level.getBlockState(head);
        if (support.is(Blocks.MAGMA_BLOCK) || support.is(Blocks.CAMPFIRE)
                || support.is(Blocks.SOUL_CAMPFIRE) || support.is(Blocks.CACTUS)
                || floor.is(Blocks.FIRE) || floor.is(Blocks.SOUL_FIRE)
                || floor.is(Blocks.COBWEB) || floor.is(Blocks.SWEET_BERRY_BUSH)
                || ceiling.is(Blocks.FIRE) || ceiling.is(Blocks.SOUL_FIRE)
                || support.getCollisionShape(level, below).isEmpty()
                || !floor.getCollisionShape(level, feet).isEmpty()
                || !ceiling.getCollisionShape(level, head).isEmpty()
                || !level.getFluidState(feet).isEmpty()
                || !level.getFluidState(head).isEmpty()) return false;
        Vec3 destination = Vec3.atBottomCenterOf(feet);
        return level.noCollision(player, player.getBoundingBox().move(
                destination.x - player.getX(), destination.y - player.getY(),
                destination.z - player.getZ()));
    }

    private Target target(MinecraftServer server, String stage) {
        Site ash = sites[0], ember = sites[1], end = sites[2];
        return switch (stage) {
            case "ash_register" -> at(Level.NETHER, ash.at(6, 5, 14), NetherChapterBlocks.ANNEX_REGISTER);
            case "ash_signal" -> at(Level.NETHER, ash.at(39, 5, 14), NetherChapterBlocks.ANNEX_SIGNAL);
            case "ash_platform" -> at(Level.NETHER, ash.at(23, 5, 8), NetherChapterBlocks.ANNEX_PLATFORM);
            case "ash_board_shelter" -> at(Level.NETHER, ash.at(21, 5, 31), NetherChapterBlocks.ANNEX_BOARD_SHELTER);
            case "ash_board_transfer" -> at(Level.NETHER, ash.at(23, 5, 31), NetherChapterBlocks.ANNEX_BOARD_TRANSFER);
            case "ash_board_wrong", "ash_board_return" -> at(Level.NETHER, ash.at(25, 5, 31), NetherChapterBlocks.ANNEX_BOARD_RETURN);
            case "ember_east" -> at(Level.NETHER, ember.at(39, 5, 23), NetherChapterBlocks.BRIDGE_EAST_SHELTER);
            case "ember_west" -> at(Level.NETHER, ember.at(7, 5, 23), NetherChapterBlocks.BRIDGE_WEST_SHELTER);
            case "ember_start", "ember_start_again" -> at(Level.NETHER, ember.at(37, 5, 23), NetherChapterBlocks.BRIDGE_START);
            case "ember_midpoint" -> at(Level.NETHER, ember.at(23, 5, 23), NetherChapterBlocks.BRIDGE_MIDPOINT);
            case "ember_finish_early", "ember_finish" -> at(Level.NETHER, ember.at(9, 5, 23), NetherChapterBlocks.BRIDGE_FINISH);
            case "end_blocked", "end_start", "end_finish" -> at(Level.END, end.at(23, 5, 23), EndSourceBlocks.CORE);
            case "end_west_account" -> at(Level.END, end.at(8, 5, 23), EndSourceBlocks.WEST_ACCOUNT);
            case "end_west_lens" -> at(Level.END, end.at(18, 5, 23), EndSourceBlocks.WEST_LENS);
            case "end_east_account" -> at(Level.END, end.at(38, 5, 23), EndSourceBlocks.EAST_ACCOUNT);
            case "end_east_lens" -> at(Level.END, end.at(28, 5, 23), EndSourceBlocks.EAST_LENS);
            case "end_repair_west_lens" -> at(Level.END, end.at(18, 5, 23), EndSourceBlocks.WEST_LENS);
            case "end_repair_east_lens" -> at(Level.END, end.at(28, 5, 23), EndSourceBlocks.EAST_LENS);
            case "end_release_signal_1", "end_release_signal_2", "end_release_signal_3",
                    "end_release_signal_4" -> endSignalTarget(server, end);
            case "end_return" -> at(Level.END, end.at(23, 5, 41), EndSourceBlocks.RETURN_ARCH);
            case "court_blocked", "court_start", "court_finish" -> courtTarget(server, "dais");
            case "court_west_room" -> courtTarget(server, "westRoom");
            case "court_west_mark" -> courtTarget(server, "westMark");
            case "court_east_room" -> courtTarget(server, "eastRoom");
            case "court_east_mark" -> courtTarget(server, "eastMark");
            case "court_release_signal_2", "court_release_signal_3",
                    "court_release_signal_4" -> courtSignalTarget(server);
            default -> { fail(server, "Unknown stage " + stage); yield null; }
        };
    }

    private Target courtTarget(MinecraftServer server, String name) {
        if (court == null) court = findCourt(server);
        if (court == null) { fail(server, "No generated Court found"); return null; }
        return switch (name) {
            case "dais" -> at(Level.OVERWORLD, court.dais, DomainBlocks.COURT_DAIS);
            case "westRoom" -> at(Level.OVERWORLD, court.westRoom, DomainBlocks.COURT_WEST_STATION);
            case "westMark" -> at(Level.OVERWORLD, court.westMark, DomainBlocks.COURT_WEST_MARK);
            case "eastRoom" -> at(Level.OVERWORLD, court.eastRoom, DomainBlocks.COURT_EAST_STATION);
            default -> at(Level.OVERWORLD, court.eastMark, DomainBlocks.COURT_EAST_MARK);
        };
    }

    private Target endSignalTarget(MinecraftServer server, Site end) {
        try {
            Field attempts = EndSourceRuntime.class.getDeclaredField("ATTEMPTS");
            attempts.setAccessible(true);
            Object attempt = ((Map<?, ?>) attempts.get(null)).get(a.getUUID());
            if (attempt == null) throw new IllegalStateException("No active End signal attempt");
            Block next = (Block) invoke(attempt, "nextSignal");
            BlockPos local = next == EndSourceBlocks.WEST_ACCOUNT ? new BlockPos(8, 5, 23)
                    : next == EndSourceBlocks.NORTH_SIGNAL ? new BlockPos(23, 5, 8)
                    : next == EndSourceBlocks.EAST_ACCOUNT ? new BlockPos(38, 5, 23)
                    : next == EndSourceBlocks.SOUTH_SIGNAL ? new BlockPos(23, 5, 38) : null;
            if (local == null) throw new IllegalStateException("Unexpected End signal control");
            LOG.info("REQCH_END_NEXT route=release step={} control={}", index, next);
            return at(Level.END, end.at(local.getX(), local.getY(), local.getZ()), next);
        } catch (ReflectiveOperationException | IllegalStateException error) {
            fail(server, "Cannot inspect active End signal: " + error);
            return null;
        }
    }

    private Target courtSignalTarget(MinecraftServer server) {
        try {
            Object attempt = courtAttempt(server);
            if (attempt == null) throw new IllegalStateException("No active Court signal attempt");
            String next = invoke(attempt, "nextRelease").toString();
            LOG.info("REQCH_COURT_NEXT route=release station={}", next);
            return switch (next) {
                case "WEST_ROOM" -> courtTarget(server, "westRoom");
                case "EAST_ROOM" -> courtTarget(server, "eastRoom");
                case "WEST_MARK" -> courtTarget(server, "westMark");
                case "EAST_MARK" -> courtTarget(server, "eastMark");
                default -> throw new IllegalStateException("Unexpected next Court station " + next);
            };
        } catch (ReflectiveOperationException | IllegalStateException error) {
            fail(server, "Cannot inspect active Court signal: " + error);
            return null;
        }
    }

    private static Object courtAttempt(MinecraftServer server) throws ReflectiveOperationException {
        Field active = RouteFinaleRuntime.class.getDeclaredField("ACTIVE");
        active.setAccessible(true);
        return ((Map<?, ?>) active.get(null)).get(server);
    }

    private static Object field(Object value, String name) throws ReflectiveOperationException {
        Field member = value.getClass().getDeclaredField(name);
        member.setAccessible(true);
        return member.get(value);
    }

    private static Object invoke(Object value, String name) throws ReflectiveOperationException {
        Method method = value.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(value);
    }

    private static Court findCourt(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos start = level.findNearestMapStructure(COURT, BlockPos.ZERO, 128, false);
        if (start == null) return null;
        BlockPos dais = find(level, start, DomainBlocks.COURT_DAIS, 48);
        if (dais == null) return null;
        BlockPos west = find(level, dais, DomainBlocks.COURT_WEST_MARK, 15);
        BlockPos east = find(level, dais, DomainBlocks.COURT_EAST_MARK, 15);
        if (west == null || east == null) return null;
        int eastDx = east.getX() - west.getX(), eastDz = east.getZ() - west.getZ();
        int northDx = west.getX() + east.getX() - 2 * dais.getX();
        int northDz = west.getZ() + east.getZ() - 2 * dais.getZ();
        if (Math.abs(eastDx) + Math.abs(eastDz) != 10
                || Math.abs(northDx) + Math.abs(northDz) != 18) return null;
        BlockPos eastRoom = dais.offset(35 * eastDx / 10 + 7 * northDx / 18, 0,
                35 * eastDz / 10 + 7 * northDz / 18);
        BlockPos westRoom = dais.offset(-34 * eastDx / 10 + 7 * northDx / 18, 0,
                -34 * eastDz / 10 + 7 * northDz / 18);
        if (!level.getBlockState(eastRoom).is(DomainBlocks.COURT_EAST_STATION)
                || !level.getBlockState(westRoom).is(DomainBlocks.COURT_WEST_STATION)) return null;
        LOG.info("REQCH_COURT_FOUND naturalStart={} dais={} westRoom={} eastRoom={} westMark={} eastMark={}",
                start, dais, westRoom, eastRoom, west, east);
        return new Court(dais, westRoom, eastRoom, west, east);
    }

    private static BlockPos find(ServerLevel level, BlockPos center, Block block, int radius) {
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            int x = center.getX() + dx, z = center.getZ() + dz;
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
            for (int scan = y; scan >= Math.max(level.getMinY(), y - 40); scan--) {
                BlockPos pos = new BlockPos(x, scan, z);
                if (level.getBlockState(pos).is(block)) return pos;
            }
        }
        return null;
    }

    private static BlockPos adjacent(ServerLevel level, BlockPos control) {
        // Some controls sit level with a solid stage floor. Try the control's
        // level first, then the walkable surface one block above that floor.
        for (int rise = 0; rise <= 1; rise++) {
            for (Direction direction : new Direction[] {Direction.SOUTH, Direction.NORTH,
                    Direction.EAST, Direction.WEST}) {
                BlockPos feet = control.relative(direction).above(rise);
                if (!level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()
                        && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                        && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                        && level.getFluidState(feet).isEmpty()
                        && level.getFluidState(feet.above()).isEmpty()) return feet;
            }
        }
        return null;
    }

    private static Target at(ResourceKey<Level> dimension, BlockPos pos, Block block) {
        return new Target(dimension, pos, block);
    }

    private static String instruction(Step step, Target target) {
        return "REQCH|USE|" + step.stage + "|" + target.pos.getX() + "|"
                + target.pos.getY() + "|" + target.pos.getZ() + "|" + step.expected
                + "|" + step.deferred;
    }

    private int acknowledge(MinecraftServer server, ServerPlayer actor, String stage) {
        if (pending == null || sentAt == 0 || !pending.stage.equals(stage)
                || actor != (pending.role.equals("A") ? a : b)) return 0;
        if (!checkProgress(server, stage)) return 0;
        LOG.info("REQCH_PACKET_PASS phase={} stage={} actor={}", phase, stage, pending.role);
        readyAt = server.getTickCount() + Math.max(12, pending.delayAfter);
        pending = null;
        pendingTarget = null;
        sentAt = 0;
        index++;
        return 1;
    }

    private boolean checkProgress(MinecraftServer server, String stage) {
        NetherChapterState nether = NetherChapterState.get(server);
        EndSourceState source = EndSourceState.get(server);
        boolean valid = switch (stage) {
            case "end_blocked", "court_blocked" -> !source.cleared(route)
                    && !RouteFinaleState.get(server).complete();
            case "ash_register" -> nether.recordCount() == 1;
            case "ash_signal" -> nether.recordCount() == 2;
            case "ember_east" -> nether.shelterCount() == 1;
            case "ash_platform" -> nether.recordCount() == 3;
            case "ash_board_wrong" -> !nether.annexCleared();
            case "ash_board_return" -> nether.annexCleared();
            case "ember_west" -> nether.shelterCount() == 2;
            case "ember_start_again", "ember_finish_early" -> !nether.bridgeCleared();
            case "ember_finish" -> nether.bridgeCleared();
            case "end_finish" -> source.cleared(route);
            case "end_return" -> a.level().dimension() == Level.OVERWORLD;
            case "court_start" -> courtOpening(server);
            case "court_east_room" -> route != CampaignRouteState.Path.REPAIR
                    || RouteFinaleState.get(server).repairSupplies() == 2;
            case "court_finish" -> RouteFinaleState.get(server).complete()
                    && (route != CampaignRouteState.Path.REPAIR
                    || RouteFinaleState.get(server).repairSupplies() == 2);
            default -> true;
        };
        if (valid && stage.startsWith("end_release_signal_")) {
            try {
                Field attempts = EndSourceRuntime.class.getDeclaredField("ATTEMPTS");
                attempts.setAccessible(true);
                Object attempt = ((Map<?, ?>) attempts.get(null)).get(a.getUUID());
                valid = attempt != null && (int) field(attempt, "releaseStep")
                        == Integer.parseInt(stage.substring(stage.length() - 1));
            } catch (ReflectiveOperationException error) {
                valid = false;
            }
        }
        if (valid && stage.startsWith("court_release_signal_")) {
            try {
                Object attempt = courtAttempt(server);
                valid = attempt != null && (int) field(attempt, "releaseStep")
                        == Integer.parseInt(stage.substring(stage.length() - 1));
            } catch (ReflectiveOperationException error) {
                valid = false;
            }
        }
        if (!valid) fail(server, "State did not advance after real packet " + stage);
        return valid;
    }

    private boolean courtOpening(MinecraftServer server) {
        if (RouteFinaleState.get(server).complete()) return false;
        try {
            Object attempt = courtAttempt(server);
            if (attempt == null || !(boolean) field(attempt, "sourceAid")
                    || field(attempt, "path") != route) return false;
            return switch (route) {
                case REMEMBER -> (int) field(attempt, "bound") == 1;
                case REPAIR -> RouteFinaleState.get(server).repairSupplies() == 0
                        && (int) invoke(attempt, "effectiveRepairSupplies",
                                int.class, 0) == 1;
                case RELEASE -> (int) field(attempt, "releaseStep") == 1
                        && invoke(attempt, "nextRelease").toString().endsWith("MARK");
            };
        } catch (ReflectiveOperationException error) {
            LOG.error("REQCH_COURT_OPENING_INSPECTION_FAILED", error);
            return false;
        }
    }

    private static Object invoke(Object value, String name, Class<?> type, Object argument)
            throws ReflectiveOperationException {
        Method method = value.getClass().getDeclaredMethod(name, type);
        method.setAccessible(true);
        return method.invoke(value, argument);
    }

    private void pass(MinecraftServer server) {
        boolean valid = switch (phase) {
            case "before" -> NetherChapterState.get(server).recordCount() == 2
                    && NetherChapterState.get(server).shelterCount() == 1;
            case "after" -> NetherChapterState.get(server).requiredChaptersCleared()
                    && EndSourceState.get(server).cleared(route)
                    && !RouteFinaleState.get(server).complete();
            case "court" -> RouteFinaleState.get(server).complete();
            default -> RouteFinaleState.get(server).complete()
                    && EndSourceState.get(server).clearedPath() == null;
        };
        if (!valid) { fail(server, "Final saved flags missing for " + phase); return; }
        LOG.info("REQCH_PHASE_PASS phase={} route={} ashRecords={} annex={} shelters={} bridge={} "
                        + "source={} court={} setupTeleports=true naturalTravel=false",
                phase, route, NetherChapterState.get(server).recordCount(),
                NetherChapterState.get(server).annexCleared(),
                NetherChapterState.get(server).shelterCount(),
                NetherChapterState.get(server).bridgeCleared(),
                EndSourceState.get(server).clearedPath(), RouteFinaleState.get(server).complete());
        finished = true;
        stopAt = server.getTickCount() + 80;
        for (ServerPlayer player : List.of(a, b))
            player.sendSystemMessage(Component.literal("REQCH|COMPLETE"));
    }

    private void fail(MinecraftServer server, String reason) {
        if (finished) return;
        LOG.error("REQCH_PHASE_FAILED phase={} reason={}", phase, reason);
        finished = true;
        stopAt = server.getTickCount() + 40;
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            player.sendSystemMessage(Component.literal("REQCH|FAILED|" + reason));
    }

    private record Step(String role, String stage, String expected, int delayAfter,
                        boolean deferred) {
        Step(String role, String stage, String expected, int delayAfter) {
            this(role, stage, expected, delayAfter, false);
        }
    }
    private record Target(ResourceKey<Level> dimension, BlockPos pos, Block block) { }
    private record Court(BlockPos dais, BlockPos westRoom, BlockPos eastRoom,
                         BlockPos westMark, BlockPos eastMark) { }
    private record Site(String name, BlockPos root, int turn) {
        static Site parse(String raw) {
            String[] fields = raw.split(",");
            if (fields.length != 5 || !Set.of("ash_annex", "ember_bridge", "end_source")
                    .contains(fields[0])) throw new IllegalArgumentException("Bad saved site " + raw);
            int turn = Integer.parseInt(fields[4]);
            if (turn < 0 || turn > 3) throw new IllegalArgumentException("Bad rotation");
            return new Site(fields[0], new BlockPos(Integer.parseInt(fields[1]),
                    Integer.parseInt(fields[2]), Integer.parseInt(fields[3])), turn);
        }
        BlockPos at(int x, int y, int z) {
            int dx = x - 23, dz = z - 23;
            int rx = switch (turn) { case 0 -> dx; case 1 -> -dz; case 2 -> -dx; default -> dz; };
            int rz = switch (turn) { case 0 -> dz; case 1 -> dx; case 2 -> -dz; default -> -dx; };
            return root.offset(rx, y - 4, rz);
        }
    }
}
