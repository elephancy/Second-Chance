package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.CampaignPersistentState;
import com.feel.gems.adventure.CampaignRouteState;
import com.feel.gems.adventure.AdventureItems;
import com.feel.gems.adventure.DomainBlocks;
import com.feel.gems.adventure.LandmarkKey;
import com.feel.gems.adventure.ReleaseRouteState;
import com.feel.gems.adventure.RepairRouteState;
import com.feel.gems.adventure.RouteFinaleRuntime;
import com.feel.gems.adventure.RouteFinaleState;
import com.feel.gems.adventure.YehiaStoryState;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opt-in dedicated-server check against two actually generated Courts. */
public final class CourtIsolationSmoke implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("gems-court-isolation-smoke");
    private static final UUID PLAYER_ID = UUID.fromString("14e331d2-ea9d-4cd8-a75c-a47a842d2a0a");
    private static final UUID DEAD_PROBE_ID = UUID.fromString("ac39dfe5-9d42-467d-8d0c-af3dd029b60e");
    private static final TagKey<Structure> COURT = TagKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath("gems", "atlas_target/unwritten_court"));
    private static final TagKey<Structure> TOWN = TagKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath("gems", "themed_target/second_chance_town"));
    private static final BlockPos ORIGIN = new BlockPos(0, 80, 0);
    private final CampaignRouteState.Path path = CampaignRouteState.Path.from(
            System.getProperty("gems.courtIsolationSmoke.path", "remember"));
    private final String restartPhase = System.getProperty("gems.courtIsolationSmoke.restartPhase", "");
    private BlockPos firstStart;
    private BlockPos secondStart;
    private int preparedAt;
    private int stopAt;
    private boolean finished;
    private Court repairCourt;
    private ServerPlayer repairPlayer;
    private ServerPlayer deadProbe;
    private Object repairAttempt;
    private int repairStartedAt;
    private int deadProbeStartedAt;
    private int repairPhase;
    private boolean sawWestHold;
    private boolean sawWarning;
    private boolean sawGuarded;
    private boolean sawEastHold;

    private record Court(BlockPos start, BlockPos dais, BlockPos westMark, BlockPos eastMark,
                         BlockPos westRoom, BlockPos eastRoom) { }

    @Override
    public void onInitialize() {
        if (!Boolean.getBoolean("gems.courtIsolationSmoke")) return;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (stopAt > 0) {
                if (server.getTickCount() >= stopAt) server.halt(false);
                return;
            }
            if (server.getTickCount() < 40 || finished) return;
            try {
                require(path != null, "Unknown finale path argument");
                if (firstStart == null) {
                    prepare(server);
                    return;
                }
                if (server.getTickCount() - preparedAt < 20) return;
                if (repairAttempt != null) {
                    advanceRepair(server);
                    return;
                }
                if (path != CampaignRouteState.Path.REPAIR && !restartPhase.isEmpty()) {
                    switch (restartPhase) {
                        case "before" -> beforeOtherRoute(server);
                        case "resume" -> resumeOtherRoute(server);
                        case "completed" -> verifyCompletedOtherRoute(server);
                        default -> throw new IllegalArgumentException("Unknown Court restart phase: " + restartPhase);
                    }
                    return;
                }
                if ("resume".equals(restartPhase)) {
                    resumeRepair(server);
                    return;
                }
                if ("completed".equals(restartPhase)) {
                    verifyCompletedRepair(server);
                    return;
                }
                if ("deadguard".equals(restartPhase)) {
                    verifyDeadGuard(server);
                    return;
                }
                run(server);
                if (path != CampaignRouteState.Path.REPAIR) {
                    LOGGER.info("COURT_ISOLATION_{}_PASS firstStart={} secondStart={}",
                            path, firstStart, secondStart);
                    finished = true;
                    stopAt = server.getTickCount() + 40;
                }
            } catch (Exception failure) {
                LOGGER.error("COURT_ISOLATION_{}_FAILED", path, failure);
                finished = true;
                stopAt = server.getTickCount() + 40;
            }
        });
    }

    private void prepare(MinecraftServer server) {
        ServerLevel level = server.overworld();
        firstStart = level.findNearestMapStructure(COURT, ORIGIN, 128, false);
        require(firstStart != null, "No naturally generated Unwritten Court found");
        long best = Long.MAX_VALUE;
        for (int dx : new int[]{-512, -256, 0, 256, 512}) {
            for (int dz : new int[]{-512, -256, 0, 256, 512}) {
                if (dx == 0 && dz == 0) continue;
                BlockPos candidate = level.findNearestMapStructure(COURT,
                        firstStart.offset(dx, 0, dz), 32, false);
                if (candidate == null || candidate.equals(firstStart)) continue;
                long gapX = (long) firstStart.getX() - candidate.getX();
                long gapZ = (long) firstStart.getZ() - candidate.getZ();
                long distance = gapX * gapX + gapZ * gapZ;
                if (distance < best) {
                    best = distance;
                    secondStart = candidate;
                }
            }
        }
        require(secondStart != null && best <= 1024L * 1024L,
                "No second generated Court within 1024 blocks of first");
        for (BlockPos start : List.of(firstStart, secondStart)) {
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    int x = (start.getX() >> 4) + dx;
                    int z = (start.getZ() >> 4) + dz;
                    level.getChunk(x, z);
                    level.setChunkForced(x, z, true);
                }
            }
        }
        preparedAt = server.getTickCount();
        LOGGER.info("COURT_ISOLATION_PREPARED path={} first={} second={} separation={}",
                path, firstStart, secondStart, Math.sqrt(best));
    }

    private void run(MinecraftServer server) throws ReflectiveOperationException {
        ServerLevel level = server.overworld();
        Court first = inspect(level, firstStart);
        Court second = inspect(level, secondStart);
        require(!first.dais.equals(second.dais), "The two locates found one dais");
        LOGGER.info("COURT_ISOLATION_GENERATED path={} first={} second={}", path, first, second);
        ServerPlayer player = connect(server, level);
        prepareRoute(server);
        require(!RouteFinaleState.get(server).complete(), "Fresh route is already complete");

        use(level, player, first.dais);
        Object attempt = attempt(server);
        require(attempt != null && first.dais.equals(field(attempt, "dais")),
                "First Court did not start an attempt with its own dais");
        use(level, player, second.dais);
        require(attempt(server) == attempt && first.dais.equals(field(attempt, "dais")),
                "Second Court replaced the live attempt");

        switch (path) {
            case REMEMBER -> remember(server, level, player, first, second, attempt);
            case REPAIR -> {
                repair(server, level, player, first, second, attempt);
                repairCourt = first;
                repairPlayer = player;
                repairAttempt = attempt;
                repairStartedAt = server.getTickCount();
            }
            case RELEASE -> release(server, level, player, first, second, attempt);
        }
        require(level.getBlockState(second.dais).is(DomainBlocks.COURT_DAIS),
                "The other Court's dais changed");
    }

    private static void remember(MinecraftServer server, ServerLevel level, ServerPlayer player,
                                 Court first, Court second, Object attempt)
            throws ReflectiveOperationException {
        use(level, player, second.westRoom);
        use(level, player, second.westMark);
        use(level, player, second.eastRoom);
        use(level, player, second.eastMark);
        require((int) field(attempt, "bound") == 0
                        && (long) field(attempt, "westWitnessAt") == -1
                        && (long) field(attempt, "eastWitnessAt") == -1,
                "Other Court advanced Remember witnesses or marks");
        use(level, player, first.dais);
        require(!RouteFinaleState.get(server).complete(), "Other Court completed Remember");
        use(level, player, first.westRoom);
        use(level, player, first.westMark);
        require((int) field(attempt, "bound") == 1, "Own west witness did not bind");
        use(level, player, second.eastRoom);
        use(level, player, first.eastMark);
        require((int) field(attempt, "bound") == 1,
                "Other Court's east room supplied the active Court's east mark");
        use(level, player, first.eastRoom);
        use(level, player, first.eastMark);
        use(level, player, first.dais);
        require(RouteFinaleState.get(server).complete()
                        && level.getBlockState(first.dais).is(DomainBlocks.COURT_REMEMBER_ENDING),
                "Own Remember path did not complete at its dais");
        LOGGER.info("COURT_ISOLATION_REMEMBER bound=2 ownEnding=true otherDaisIntact=true");
    }

    private static void repair(MinecraftServer server, ServerLevel level, ServerPlayer player,
                               Court first, Court second, Object attempt)
            throws ReflectiveOperationException {
        RouteFinaleState state = RouteFinaleState.get(server);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COPPER_INGOT, 2));
        use(level, player, second.westRoom);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_PANE, 2));
        use(level, player, second.eastRoom);
        use(level, player, second.westMark);
        use(level, player, second.eastMark);
        require(state.repairSupplies() == 0 && (int) field(attempt, "held") == 0,
                "Other Court advanced Repair supplies or holds");
        use(level, player, first.dais);
        require(!state.complete(), "Other Court completed Repair");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COPPER_INGOT, 2));
        use(level, player, first.westRoom);
        require(state.repairSupplies() == 1, "Own west room did not accept copper");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_PANE, 2));
        use(level, player, second.eastRoom);
        require(state.repairSupplies() == 1, "Other Court supplied active east room");
        use(level, player, first.eastRoom);
        require(state.repairSupplies() == 3, "Own east room did not accept glass");
        use(level, player, second.westMark);
        use(level, player, second.eastMark);
        require((int) field(attempt, "held") == 0 && !state.complete(),
                "Other Court advanced Repair after supplies were delivered");
        LOGGER.info("COURT_ISOLATION_REPAIR ownSupplies=2 otherSupplies=0 held=0 ending=false");
    }

    private void advanceRepair(MinecraftServer server) throws ReflectiveOperationException {
        ServerLevel level = server.overworld();
        RouteFinaleState state = RouteFinaleState.get(server);
        require(attempt(server) == repairAttempt, "Repair attempt disappeared before the ending");
        require(server.getTickCount() - repairStartedAt < 500,
                "Repair standing/strike sequence exceeded 500 server ticks");
        require(repairPlayer.isAlive() && !repairPlayer.isSpectator(),
                "Repair probe must remain alive and standing through every hold and strike");
        if (deadProbeStartedAt > 0) {
            require(deadProbe != null && !deadProbe.isAlive()
                            && level.players().contains(deadProbe),
                    "Dead negative probe left the loaded Court before hold exclusion was checked");
            if (server.getTickCount() - deadProbeStartedAt < 100) return;
            require((int) field(repairAttempt, "held") == 0
                            && (int) field(repairAttempt, "westHoldTicks") == 0
                            && !(boolean) field(repairAttempt, "defendedStrike"),
                    "Dead player credited a Repair hold or guarded strike");
            LOGGER.info("COURT_RESTART_DEAD_EXCLUDED_PASS liveObserver=true deadAtWestMark=true "
                    + "held=0 westHoldTicks=0 guardedStrike=false elapsedTicks={}",
                    server.getTickCount() - deadProbeStartedAt);
            deadProbeStartedAt = 0;
        }

        if (repairPhase == 0) {
            stand(level, repairPlayer, repairCourt.westMark);
            repairPhase = 1;
            LOGGER.info("COURT_REPAIR_STAND_WEST mark={} tick={}",
                    repairCourt.westMark, level.getGameTime());
            return;
        }
        int held = (int) field(repairAttempt, "held");
        if ("before".equals(restartPhase) && (held & 1) != 0) {
            require(state.repairSupplies() == 3 && !state.complete(),
                    "Restart baseline has wrong persistent Repair state");
            LOGGER.info("COURT_RESTART_BEFORE_PASS route={} supplies={} westHeld={} complete={} dais={} "
                            + "gameTime={}", CampaignRouteState.get(server).chosen(), state.repairSupplies(),
                    held, state.complete(), repairCourt.dais, level.getGameTime());
            finished = true;
            stopAt = server.getTickCount() + 10;
            return;
        }
        if (repairPhase == 1) {
            if ((held & 1) != 0 && !sawWestHold) {
                sawWestHold = true;
                LOGGER.info("COURT_REPAIR_WEST_HELD tick={} firstHoldAt={}",
                        level.getGameTime(), field(repairAttempt, "firstRepairHoldAt"));
            }
            if ((boolean) field(repairAttempt, "repairWarned") && !sawWarning) {
                sawWarning = true;
                LOGGER.info("COURT_REPAIR_WARNING tick={} target={}",
                        level.getGameTime(), field(repairAttempt, "repairTarget"));
            }
            if ((boolean) field(repairAttempt, "defendedStrike")) {
                require(sawWestHold && sawWarning && (held & 1) != 0,
                        "A guarded strike did not follow the west hold and warning");
                sawGuarded = true;
                LOGGER.info("COURT_REPAIR_GUARDED tick={} held={} target={}",
                        level.getGameTime(), held, field(repairAttempt, "repairTarget"));
                stand(level, repairPlayer, repairCourt.eastMark);
                repairPhase = 2;
                LOGGER.info("COURT_REPAIR_STAND_EAST mark={} tick={}",
                        repairCourt.eastMark, level.getGameTime());
            }
            return;
        }
        if (repairPhase == 2) {
            if ((held & 2) == 0) return;
            sawEastHold = true;
            require((held & 1) != 0 && (boolean) field(repairAttempt, "defendedStrike"),
                    "East line held but west line or strike guard was lost");
            LOGGER.info("COURT_REPAIR_EAST_HELD tick={} held={} firstHoldAt={} age={}",
                    level.getGameTime(), held, field(repairAttempt, "firstRepairHoldAt"),
                    level.getGameTime() - (long) field(repairAttempt, "firstRepairHoldAt"));
            use(level, repairPlayer, repairCourt.dais);
            require(state.complete() && level.getBlockState(repairCourt.dais)
                            .is(DomainBlocks.COURT_REPAIR_ENDING),
                    "First Court Repair ending did not complete at its own dais");
            require(sawWestHold && sawWarning && sawGuarded && sawEastHold,
                    "Repair hold, warning, guard, and second hold were not all witnessed");
            require(level.getBlockState(inspect(level, secondStart).dais)
                            .is(DomainBlocks.COURT_DAIS),
                    "The second Court dais changed during Repair completion");
            LOGGER.info("COURT_ISOLATION_REPAIR_PASS westHold=true warning=true "
                    + "guardedStrike=true eastHold=true probeAlive=true ownEnding=true otherDaisIntact=true "
                    + "firstStart={} secondStart={}", firstStart, secondStart);
            if ("resume".equals(restartPhase)) {
                LOGGER.info("COURT_RESTART_RESUME_PASS supplies={} complete={} dais={} gameTime={}",
                        state.repairSupplies(), state.complete(), repairCourt.dais, level.getGameTime());
            }
            finished = true;
            stopAt = server.getTickCount() + 40;
        }
    }

    private void resumeRepair(MinecraftServer server) throws ReflectiveOperationException {
        ServerLevel level = server.overworld();
        RouteFinaleState state = RouteFinaleState.get(server);
        require(path == CampaignRouteState.Path.REPAIR
                        && CampaignRouteState.get(server).chosen() == path
                        && state.repairSupplies() == 3 && !state.complete(),
                "Saved route or Repair supplies did not survive restart");
        require(attempt(server) == null, "In-flight attempt unexpectedly survived process restart");
        Court first = inspect(level, firstStart);
        ServerPlayer player = connect(server, level);
        use(level, player, first.dais);
        Object attempt = attempt(server);
        require(attempt != null && first.dais.equals(field(attempt, "dais"))
                        && (int) field(attempt, "held") == 0
                        && (long) field(attempt, "firstRepairHoldAt") == -1L,
                "Dais did not begin a fresh Repair attempt after restart");
        LOGGER.info("COURT_RESTART_RESUMED route={} supplies={} oldHoldReset=true dais={} gameTime={}",
                CampaignRouteState.get(server).chosen(), state.repairSupplies(), first.dais,
                level.getGameTime());
        stand(level, player, first.dais);
        deadProbe = connect(server, level, DEAD_PROBE_ID, "DeadCourtProbe");
        stand(level, deadProbe, first.westMark);
        deadProbe.setHealth(0.0F);
        require(!deadProbe.isAlive() && level.players().contains(deadProbe),
                "Negative probe is not dead and present at the west mark");
        require(!guarded(level, first.westMark),
                "A dead player at the mark could still guard a Repair strike");
        LOGGER.info("COURT_RESTART_DEAD_GUARD_EXCLUDED_PASS deadAtWestMark=true guarded=false");
        deadProbeStartedAt = server.getTickCount();
        repairCourt = first;
        repairPlayer = player;
        repairAttempt = attempt;
        repairStartedAt = server.getTickCount();
    }

    private void verifyCompletedRepair(MinecraftServer server) throws ReflectiveOperationException {
        ServerLevel level = server.overworld();
        RouteFinaleState state = RouteFinaleState.get(server);
        require(path == CampaignRouteState.Path.REPAIR
                        && CampaignRouteState.get(server).chosen() == path
                        && state.repairSupplies() == 3 && state.complete(),
                "Completed Repair route did not survive restart");
        require(attempt(server) == null, "Completed route retained an in-memory attempt");
        BlockPos ending = find(level, firstStart, DomainBlocks.COURT_REPAIR_ENDING, 48);
        require(ending != null, "Completed Court ending block did not survive restart");
        LOGGER.info("COURT_RESTART_COMPLETED_PASS route={} supplies={} complete={} ending={} gameTime={}",
                CampaignRouteState.get(server).chosen(), state.repairSupplies(), state.complete(),
                ending, level.getGameTime());
        finished = true;
        stopAt = server.getTickCount() + 10;
    }

    private void verifyDeadGuard(MinecraftServer server) throws ReflectiveOperationException {
        ServerLevel level = server.overworld();
        BlockPos mark = find(level, firstStart, DomainBlocks.COURT_WEST_MARK, 48);
        BlockPos ending = find(level, firstStart, DomainBlocks.COURT_REPAIR_ENDING, 48);
        require(mark != null && ending != null, "Saved natural Court lacks Repair ending or west mark");
        ServerPlayer observer = connect(server, level);
        stand(level, observer, ending);
        ServerPlayer dead = connect(server, level, DEAD_PROBE_ID, "DeadCourtProbe");
        stand(level, dead, mark);
        dead.setHealth(0.0F);
        require(observer.isAlive() && !dead.isAlive() && level.players().contains(dead)
                        && !guarded(level, mark),
                "Dead player on the mark counted as a Repair strike guard");
        LOGGER.info("COURT_RESTART_DEAD_GUARD_PASS deadAtWestMark=true "
                        + "liveObserverAtDais=true guarded=false mark={} ending={}", mark, ending);
        finished = true;
        stopAt = server.getTickCount() + 10;
    }

    private void beforeOtherRoute(MinecraftServer server) throws ReflectiveOperationException {
        ServerLevel level = server.overworld();
        Court first = inspect(level, firstStart);
        Court second = inspect(level, secondStart);
        ServerPlayer player = connect(server, level);
        require(player.isAlive() && !player.isSpectator(), "Finale probe is not living");
        prepareRoute(server);
        require(!RouteFinaleState.get(server).complete(), "Fresh route already completed");
        use(level, player, first.dais);
        Object live = attempt(server);
        require(live != null && first.dais.equals(field(live, "dais")),
                "Natural Court did not start the requested route");
        if (path == CampaignRouteState.Path.REMEMBER) {
            use(level, player, first.westRoom);
            use(level, player, first.westMark);
            require((int) field(live, "bound") == 1
                            && (long) field(live, "eastWitnessAt") == -1L,
                    "Remember partial witness did not remain in flight");
        } else {
            use(level, player, nextReleaseStation(first, live));
            require((int) field(live, "releaseStep") == 1,
                    "Release partial signal did not remain in flight");
        }
        require(level.getBlockState(second.dais).is(DomainBlocks.COURT_DAIS),
                "Other natural Court changed before restart");
        LOGGER.info("COURT_RESTART_BEFORE_PASS path={} route={} progress={} complete={} "
                        + "living=true dais={} otherDaisIntact=true gameTime={}",
                path, CampaignRouteState.get(server).chosen(),
                path == CampaignRouteState.Path.REMEMBER ? field(live, "bound")
                        : field(live, "releaseStep"),
                RouteFinaleState.get(server).complete(), first.dais, level.getGameTime());
        finished = true;
        stopAt = server.getTickCount() + 10;
    }

    private void resumeOtherRoute(MinecraftServer server) throws ReflectiveOperationException {
        ServerLevel level = server.overworld();
        RouteFinaleState ending = RouteFinaleState.get(server);
        require(CampaignRouteState.get(server).chosen() == path && !ending.complete(),
                "Chosen route or unfinished state did not survive restart");
        require(attempt(server) == null, "In-flight attempt unexpectedly survived restart");
        Court first = inspect(level, firstStart);
        Court second = inspect(level, secondStart);
        ServerPlayer player = connect(server, level);
        require(player.isAlive() && !player.isSpectator(), "Resumed finale probe is not living");
        use(level, player, first.dais);
        Object live = attempt(server);
        require(live != null && first.dais.equals(field(live, "dais")),
                "Saved prerequisites did not allow a new natural Court attempt");
        require((int) field(live, path == CampaignRouteState.Path.REMEMBER
                        ? "bound" : "releaseStep") == 0,
                "In-flight witness or signal was incorrectly persisted");
        if (path == CampaignRouteState.Path.REMEMBER) {
            require((long) field(live, "westWitnessAt") == -1L
                            && (long) field(live, "eastWitnessAt") == -1L,
                    "Remember witness timer survived process restart");
            use(level, player, first.westRoom);
            use(level, player, first.westMark);
            use(level, player, first.eastRoom);
            use(level, player, first.eastMark);
            require((int) field(live, "bound") == 3,
                    "Resumed Remember did not bind both witnesses");
        } else {
            for (int step = 1; step <= 4; step++) {
                require(player.isAlive() && !player.isSpectator(),
                        "Release probe died during signals");
                use(level, player, nextReleaseStation(first, live));
                require((int) field(live, "releaseStep") == step,
                        "Resumed Release missed signal " + step);
            }
        }
        require(player.isAlive() && !player.isSpectator(),
                "Finale probe died before the ending");
        use(level, player, first.dais);
        Block expectedEnding = path == CampaignRouteState.Path.REMEMBER
                ? DomainBlocks.COURT_REMEMBER_ENDING : DomainBlocks.COURT_RELEASE_ENDING;
        require(ending.complete() && level.getBlockState(first.dais).is(expectedEnding)
                        && level.getBlockState(second.dais).is(DomainBlocks.COURT_DAIS),
                "Resumed route did not complete only at its own natural Court");
        BlockPos board = townBoard(level, DomainBlocks.TOWNHALL_STORY_COUNCIL);
        int[] before = rewardCounts(player);
        use(level, player, board);
        int[] firstClaim = rewardCounts(player);
        require(rewardGained(before, firstClaim), "First route kit was not granted at natural townhall");
        Block serviceBlock = path == CampaignRouteState.Path.REMEMBER
                ? DomainBlocks.TOWNHALL_OPEN_ARCHIVE : DomainBlocks.TOWNHALL_CROSSROADS;
        require(level.getBlockState(board).is(serviceBlock),
                "Natural townhall desk did not become the chosen route service");
        use(level, player, board);
        require(java.util.Arrays.equals(firstClaim, rewardCounts(player)),
                "Same-day repeat use granted another route kit");
        long day = Math.max(0, Math.floorDiv(level.getOverworldClockTime(), 24_000L));
        require(ending.nextServiceDay(PLAYER_ID) == day + 1,
                "Daily route kit receipt was not recorded");
        LOGGER.info("COURT_RESTART_RESUME_PASS path={} complete=true living=true ending={} "
                        + "otherDaisIntact=true townhall={} day={} rewards={} nextServiceDay={}",
                path, first.dais, board, day, java.util.Arrays.toString(firstClaim),
                ending.nextServiceDay(PLAYER_ID));
        finished = true;
        stopAt = server.getTickCount() + 40;
    }

    private void verifyCompletedOtherRoute(MinecraftServer server)
            throws ReflectiveOperationException {
        ServerLevel level = server.overworld();
        RouteFinaleState ending = RouteFinaleState.get(server);
        require(CampaignRouteState.get(server).chosen() == path && ending.complete(),
                "Completed route did not survive the second restart");
        require(attempt(server) == null, "Completed route retained an in-memory attempt");
        Block expectedEnding = path == CampaignRouteState.Path.REMEMBER
                ? DomainBlocks.COURT_REMEMBER_ENDING : DomainBlocks.COURT_RELEASE_ENDING;
        BlockPos dais = find(level, firstStart, expectedEnding, 48);
        Court other = inspect(level, secondStart);
        require(dais != null && level.getBlockState(other.dais).is(DomainBlocks.COURT_DAIS),
                "Saved route ending or other natural Court changed");
        ServerPlayer player = connect(server, level);
        require(player.isAlive() && !player.isSpectator(), "Completed route probe is not living");
        Block serviceBlock = path == CampaignRouteState.Path.REMEMBER
                ? DomainBlocks.TOWNHALL_OPEN_ARCHIVE : DomainBlocks.TOWNHALL_CROSSROADS;
        BlockPos board = townBoard(level, serviceBlock);
        int[] saved = rewardCounts(player);
        require(saved.length == 1 ? saved[0] == 1 : saved[0] == 1 && saved[1] == 1,
                "Claimed route kit inventory did not survive second restart");
        long day = Math.max(0, Math.floorDiv(level.getOverworldClockTime(), 24_000L));
        require(ending.nextServiceDay(PLAYER_ID) == day + 1,
                "Saved daily claim receipt did not survive second restart");
        use(level, player, board);
        require(java.util.Arrays.equals(saved, rewardCounts(player))
                        && ending.nextServiceDay(PLAYER_ID) == day + 1,
                "Second restart allowed a duplicate same-day route kit");
        LOGGER.info("COURT_RESTART_COMPLETED_PASS path={} complete=true ending={} "
                        + "townhall={} day={} savedRewards={} sameDayDuplicate=false "
                        + "nextServiceDay={}", path, dais, board, day,
                java.util.Arrays.toString(saved), ending.nextServiceDay(PLAYER_ID));
        finished = true;
        stopAt = server.getTickCount() + 10;
    }

    private static BlockPos nextReleaseStation(Court court, Object attempt)
            throws ReflectiveOperationException {
        int step = (int) field(attempt, "releaseStep");
        boolean westFirst = (boolean) field(attempt, "westFirst");
        return switch (step) {
            case 0 -> westFirst ? court.westRoom : court.eastRoom;
            case 1 -> westFirst ? court.eastMark : court.westMark;
            case 2 -> westFirst ? court.eastRoom : court.westRoom;
            case 3 -> westFirst ? court.westMark : court.eastMark;
            default -> throw new IllegalStateException("No Release signal after step " + step);
        };
    }

    private BlockPos townBoard(ServerLevel level, Block block) {
        BlockPos town = level.findNearestMapStructure(TOWN, ORIGIN, 128, false);
        require(town != null, "No naturally generated townhall found for route service");
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                level.getChunk((town.getX() >> 4) + dx, (town.getZ() >> 4) + dz);
            }
        }
        BlockPos board = find(level, town, block, 96);
        require(board != null, "Natural townhall lacks expected route desk " + block);
        return board;
    }

    private int[] rewardCounts(ServerPlayer player) {
        return path == CampaignRouteState.Path.REMEMBER
                ? new int[]{count(player, AdventureItems.RUMOR_ATLAS)}
                : new int[]{count(player, AdventureItems.SHORTCUT_CHALK),
                        count(player, AdventureItems.SIGNAL_BUOY)};
    }

    private boolean rewardGained(int[] before, int[] after) {
        for (int i = 0; i < before.length; i++) {
            if (after[i] != before[i] + 1) return false;
        }
        return true;
    }

    private static int count(ServerPlayer player, Item item) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static void release(MinecraftServer server, ServerLevel level, ServerPlayer player,
                                Court first, Court second, Object attempt)
            throws ReflectiveOperationException {
        for (BlockPos station : List.of(second.westRoom, second.eastRoom,
                second.westMark, second.eastMark)) use(level, player, station);
        require((int) field(attempt, "releaseStep") == 0,
                "Other Court advanced Release signals");
        use(level, player, first.dais);
        require(!RouteFinaleState.get(server).complete(), "Other Court completed Release");
        for (BlockPos station : List.of(first.westRoom, first.eastRoom)) use(level, player, station);
        require((int) field(attempt, "releaseStep") == 1, "Own first Release signal failed");
        for (BlockPos station : List.of(second.westMark, second.eastMark)) use(level, player, station);
        require((int) field(attempt, "releaseStep") == 1,
                "Other Court supplied active second signal");
        for (BlockPos station : List.of(first.westMark, first.eastMark)) use(level, player, station);
        require((int) field(attempt, "releaseStep") == 2, "Own second Release signal failed");
        for (BlockPos station : List.of(first.westRoom, first.eastRoom)) use(level, player, station);
        require((int) field(attempt, "releaseStep") == 3, "Own third Release signal failed");
        for (BlockPos station : List.of(first.westMark, first.eastMark)) use(level, player, station);
        require((int) field(attempt, "releaseStep") == 4, "Own fourth Release signal failed");
        use(level, player, first.dais);
        require(RouteFinaleState.get(server).complete()
                        && level.getBlockState(first.dais).is(DomainBlocks.COURT_RELEASE_ENDING),
                "Own Release path did not complete at its dais");
        LOGGER.info("COURT_ISOLATION_RELEASE ownSignals=4 otherSignals=0 ownEnding=true");
    }

    private static Court inspect(ServerLevel level, BlockPos start) {
        BlockPos dais = find(level, start, DomainBlocks.COURT_DAIS, 48);
        require(dais != null, "Generated Court has no dais near " + start);
        BlockPos west = find(level, dais, DomainBlocks.COURT_WEST_MARK, 15);
        BlockPos east = find(level, dais, DomainBlocks.COURT_EAST_MARK, 15);
        require(west != null && east != null && west.getY() == dais.getY()
                        && east.getY() == dais.getY(),
                "Generated Court is missing stage marks at dais height " + dais);
        int eastDx = east.getX() - west.getX();
        int eastDz = east.getZ() - west.getZ();
        int northDx = west.getX() + east.getX() - 2 * dais.getX();
        int northDz = west.getZ() + east.getZ() - 2 * dais.getZ();
        require(Math.abs(eastDx) + Math.abs(eastDz) == 10
                        && Math.abs(northDx) + Math.abs(northDz) == 18,
                "Generated Court mark orientation is wrong at " + dais);
        BlockPos westRoom = dais.offset(-34 * eastDx / 10 + 7 * northDx / 18, 0,
                -34 * eastDz / 10 + 7 * northDz / 18);
        BlockPos eastRoom = dais.offset(35 * eastDx / 10 + 7 * northDx / 18, 0,
                35 * eastDz / 10 + 7 * northDz / 18);
        require(level.getBlockState(westRoom).is(DomainBlocks.COURT_WEST_STATION)
                        && level.getBlockState(eastRoom).is(DomainBlocks.COURT_EAST_STATION),
                "Generated Court lacks one of its real side stations at " + dais
                        + ": west=" + level.getBlockState(westRoom).getBlock()
                        + " east=" + level.getBlockState(eastRoom).getBlock());
        return new Court(start, dais, west, east, westRoom, eastRoom);
    }

    private static BlockPos find(ServerLevel level, BlockPos center, Block block, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                for (int y = top; y >= Math.max(level.getMinY(), top - 40); y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).is(block)) return pos;
                }
            }
        }
        return null;
    }

    private static void prepareRoute(MinecraftServer server) {
        CampaignRouteState route = CampaignRouteState.get(server);
        require(route.vote(PLAYER_ID, CampaignRouteState.Path.from(
                        System.getProperty("gems.courtIsolationSmoke.path", "remember")))
                        && route.confirm(List.of(PLAYER_ID), PLAYER_ID)
                        == CampaignRouteState.Resolution.CHOSEN,
                "Could not choose the fresh world route");
        for (LandmarkKey key : LandmarkKey.values()) {
            CampaignPersistentState.get(server).recordVictory(List.of(PLAYER_ID), key);
        }
        switch (route.chosen()) {
            case REMEMBER -> {
                require(route.recordRememberPrint(0) && route.recordRememberPrint(1)
                                && route.bindRememberInkbound()
                                && route.recordRememberWitness(1)
                                && route.recordRememberWitness(2)
                                && route.compareRememberArchive()
                                && route.startRememberTale()
                                && route.markRememberTale(YehiaStoryState.TREE) == 2
                                && route.markRememberTale(YehiaStoryState.LANTERN) == 3
                                && route.markRememberTale(YehiaStoryState.MUSHROOM) == 4
                                && route.finishRememberTale(),
                        "Could not prepare Remember leads");
            }
            case REPAIR -> {
                RepairRouteState repair = RepairRouteState.get(server);
                require(repair.recordRepair(RepairRouteState.YARD)
                                && repair.recordRepair(RepairRouteState.BRIDGE)
                                && repair.recordRepair(RepairRouteState.LOFT)
                                && repair.recordRiverwardBrace(RepairRouteState.WEST_BRACE)
                                && repair.recordRiverwardBrace(RepairRouteState.EAST_BRACE)
                                && repair.finishRiverward() && repair.finishRedline(),
                        "Could not prepare Repair leads");
            }
            case RELEASE -> {
                ReleaseRouteState release = ReleaseRouteState.get(server);
                require(release.finishNourRelay()
                                && release.recordAliAnchor(ReleaseRouteState.HIGH_MAP,
                                new BlockPos(0, 80, 0))
                                && release.recordAliAnchor(ReleaseRouteState.CUT_PASSAGE,
                                new BlockPos(32, 80, 0))
                                && release.finishAliShortcut(),
                        "Could not prepare Release anchors");
                int serial = release.startFamDispatch(server.overworld().getGameTime());
                require(serial > 0
                                && release.stampFamDispatch(serial, ReleaseRouteState.FAM_STORY,
                                server.overworld().getGameTime())
                                && release.stampFamDispatch(serial, ReleaseRouteState.FAM_SCHOOL,
                                server.overworld().getGameTime())
                                && release.finishFamDispatch(serial, server.overworld().getGameTime()),
                        "Could not prepare Release dispatch");
            }
        }
    }

    private static ServerPlayer connect(MinecraftServer server, ServerLevel level) {
        return connect(server, level, PLAYER_ID, "CourtProbe");
    }

    private static ServerPlayer connect(MinecraftServer server, ServerLevel level,
                                        UUID id, String name) {
        GameProfile profile = new GameProfile(id, name);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(server, level,
                cookie.gameProfile(), cookie.clientInformation());
        boolean loaded = server.getPlayerList().loadPlayerData(player.nameAndId())
                .map(saved -> {
                    player.load(TagValueInput.create(ProblemReporter.DISCARDING,
                            level.registryAccess(), saved));
                    return true;
                }).orElse(false);
        player.getAbilities().invulnerable = true;
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        ServerPlayer registered = server.getPlayerList().getPlayer(id);
        require(registered != null, "Embedded Court player was not registered");
        LOGGER.info("COURT_EMBEDDED_PLAYER registeredSameObject={} loadedSavedData={} id={} pos={}",
                registered == player, loaded, id, registered.position());
        registered.getAbilities().invulnerable = true;
        if (registered.connection != null) {
            for (int tick = 0; tick < 80 && !registered.connection.hasClientLoaded(); tick++) {
                registered.connection.tickClientLoadTimeout();
            }
        }
        return registered;
    }

    private static void use(ServerLevel level, ServerPlayer player, BlockPos station) {
        stand(level, player, station);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(station),
                Direction.UP, station, false);
        require(UseBlockCallback.EVENT.invoker().interact(player, level,
                        InteractionHand.MAIN_HAND, hit) == InteractionResult.SUCCESS_SERVER,
                "Finale station did not handle use at " + station);
    }

    private static void stand(ServerLevel level, ServerPlayer player, BlockPos station) {
        player.teleportTo(level, station.getX() + 0.5, station.getY() + 1.0,
                station.getZ() + 0.5, EnumSet.noneOf(Relative.class), 0.0F, 0.0F, false);
    }

    private static Object attempt(MinecraftServer server) throws ReflectiveOperationException {
        Field field = RouteFinaleRuntime.class.getDeclaredField("ACTIVE");
        field.setAccessible(true);
        return ((Map<?, ?>) field.get(null)).get(server);
    }

    private static Object field(Object value, String name) throws ReflectiveOperationException {
        Field field = value.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(value);
    }

    private static boolean guarded(ServerLevel level, BlockPos mark)
            throws ReflectiveOperationException {
        Method method = RouteFinaleRuntime.class.getDeclaredMethod("guarded", ServerLevel.class,
                BlockPos.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, level, mark);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
