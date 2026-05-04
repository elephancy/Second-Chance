package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.CampaignPersistentState;
import com.feel.gems.adventure.CampaignRouteState;
import com.feel.gems.adventure.FriendDomainUnlockState;
import com.feel.gems.adventure.LandmarkKey;
import com.feel.gems.adventure.ReleaseRouteState;
import com.feel.gems.adventure.RepairRouteState;
import com.feel.gems.adventure.RequiredChapters;
import com.feel.gems.adventure.RouteFinaleRuntime;
import com.feel.gems.adventure.RouteFinaleState;
import com.feel.gems.adventure.YehiaStoryState;
import com.feel.gems.adventure.endsource.EndSourceState;
import com.feel.gems.adventure.nether.NetherChapterState;
import java.util.List;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Test-only state probe loaded from a separate jar on a packaged dedicated server. */
public final class CampaignStateAcceptanceSmoke implements ModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("gems-campaign-state-gate");
    private static final UUID FIRST = UUID.fromString("719c2bdf-8572-4af9-8217-0ea5d59dc49a");
    private static final UUID SECOND = UUID.fromString("36fa74b0-c0a0-4f30-a3a8-ab532c55f224");
    private static final String[] FRIENDS = {
        "adl", "ali", "fam", "hamed", "khaled", "nour", "omar", "othman",
        "ragab", "soryal", "yehia"
    };
    private boolean ran;

    @Override
    public void onInitialize() {
        if (!Boolean.getBoolean("gems.campaignState.enabled")) return;
        String phase = System.getProperty("gems.campaignState.phase", "");
        CampaignRouteState.Path path = CampaignRouteState.Path.from(
                System.getProperty("gems.campaignState.route", ""));
        if (!(phase.equals("seed") || phase.equals("route")
                || phase.equals("chapter") || phase.equals("restart"))
                || (!phase.equals("seed") && path == null)) {
            throw new IllegalArgumentException("Campaign state probe needs a valid phase and route");
        }
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ran || server.getTickCount() < 20) return;
            ran = true;
            try {
                switch (phase) {
                    case "seed" -> seed(server);
                    case "route" -> route(server, path);
                    case "chapter" -> chapter(server, path);
                    case "restart" -> restart(server, path);
                    default -> throw new IllegalStateException(phase);
                }
                LOG.info("CAMPAIGN_STATE_PASS phase={} route={} bossBits=7 friendDomains=11 "
                        + "scriptedTransitions=true", phase, path == null ? "none" : path.id());
            } catch (Throwable failure) {
                LOG.error("CAMPAIGN_STATE_FAIL phase={} route={}", phase, path, failure);
            }
        });
    }

    private static void seed(MinecraftServer server) {
        require(RequiredChapters.active(server), "Packaged chapter structure sets are missing");
        require(CampaignRouteState.get(server).chosen() == null, "Seed world already chose a route");
        require(CampaignPersistentState.get(server).model().snapshot().worldChangedMask() == 0,
                "Seed world already has boss progress");
        require(!RouteFinaleState.get(server).complete(), "Seed world already has an ending");
        for (LandmarkKey key : LandmarkKey.values()) {
            var victory = CampaignPersistentState.get(server).recordVictory(List.of(FIRST), key);
            require(victory.firstWorldChange() && victory.firstClears().get(FIRST),
                    "First boss progress failed for " + key.id());
        }
        // A late joiner sees world changes without inheriting first-clear identity.
        require(!CampaignPersistentState.get(server).model().hasCleared(SECOND, LandmarkKey.FRAMEFALL),
                "Late joiner inherited a first clear");
        FriendDomainUnlockState friends = FriendDomainUnlockState.get(server);
        for (String friend : FRIENDS) require(friends.clear(friend), "Duplicate friend clear " + friend);
        checkPrefix(server);
    }

    private static void route(MinecraftServer server, CampaignRouteState.Path path) {
        checkPrefix(server);
        CampaignRouteState choice = CampaignRouteState.get(server);
        require(choice.vote(FIRST, path) && choice.vote(SECOND, path), "Both votes were not recorded");
        require(choice.confirm(List.of(FIRST, SECOND), FIRST)
                        == CampaignRouteState.Resolution.CHOSEN,
                "The two-player council choice failed");
        require(choice.chosen() == path && choice.voteOf(FIRST) == null
                        && choice.voteOf(SECOND) == null,
                "Council did not lock one shared choice and clear votes");
        require(choice.confirm(List.of(FIRST, SECOND), SECOND)
                        == CampaignRouteState.Resolution.ALREADY_CHOSEN,
                "A second choice changed the council result");
        progressFieldLeads(server, path);
        require(RouteFinaleRuntime.ready(server, path), "Route field leads did not unlock finale");
        NetherChapterState nether = NetherChapterState.get(server);
        require(nether.readRecord(NetherChapterState.REGISTER)
                        && nether.readRecord(NetherChapterState.SIGNAL)
                        && nether.readRecord(NetherChapterState.PLATFORM)
                        && nether.clearAnnex()
                        && nether.restoreShelter(NetherChapterState.EAST_SHELTER)
                        && nether.restoreShelter(NetherChapterState.WEST_SHELTER)
                        && nether.clearBridge(), "Nether chapter sequence failed");
        checkBeforeEnd(server, path);
    }

    private static void chapter(MinecraftServer server, CampaignRouteState.Path path) {
        checkBeforeEnd(server, path);
        EndSourceState source = EndSourceState.get(server);
        require(source.clear(path), "End Source did not record selected route");
        require(RequiredChapters.readyForCourt(server, path), "Court chapter gate remained closed");
        RouteFinaleState ending = RouteFinaleState.get(server);
        require(ending.finish(), "Ending did not become complete");
        long day = Math.max(0, Math.floorDiv(server.overworld().getOverworldClockTime(), 24_000L));
        require(ending.claimService(FIRST, day) && !ending.claimService(FIRST, day),
                "Daily service duplicated or failed");
        checkComplete(server, path, day);
    }

    private static void restart(MinecraftServer server, CampaignRouteState.Path path) {
        checkComplete(server, path, -1);
        CampaignRouteState choice = CampaignRouteState.get(server);
        require(!choice.vote(SECOND, CampaignRouteState.Path.next(path)),
                "Saved route accepted a different vote");
        require(choice.confirm(List.of(FIRST, SECOND), SECOND)
                        == CampaignRouteState.Resolution.ALREADY_CHOSEN,
                "Saved route was not locked");
        require(!EndSourceState.get(server).clear(CampaignRouteState.Path.next(path)),
                "A second End Source path was accepted");
        require(!RouteFinaleState.get(server).finish(), "Ending replay created a new completion");
        require(!CampaignPersistentState.get(server).recordVictory(List.of(FIRST), LandmarkKey.FRAMEFALL)
                        .firstClears().get(FIRST), "Saved first clear became claimable again");
        require(!FriendDomainUnlockState.get(server).clear("adl"), "Saved friend clear repeated");
    }

    private static void checkPrefix(MinecraftServer server) {
        var model = CampaignPersistentState.get(server).model();
        require(model.snapshot().worldChangedMask() == LandmarkKey.ALL_MASK,
                "Seven world boss changes did not persist");
        for (LandmarkKey key : LandmarkKey.values()) {
            require(model.hasCleared(FIRST, key) && !model.hasCleared(SECOND, key),
                    "Personal boss identities crossed players at " + key.id());
        }
        for (String friend : FRIENDS) {
            require(FriendDomainUnlockState.get(server).cleared(friend),
                    "Friend domain did not persist: " + friend);
        }
        require(CampaignRouteState.get(server).chosen() == null
                        && EndSourceState.get(server).clearedPath() == null
                        && !RouteFinaleState.get(server).complete(),
                "Pre-choice prefix was changed before branching");
    }

    private static void checkBeforeEnd(MinecraftServer server, CampaignRouteState.Path path) {
        var model = CampaignPersistentState.get(server).model();
        require(model.snapshot().worldChangedMask() == LandmarkKey.ALL_MASK,
                "Boss changes were lost before the End chapter");
        for (LandmarkKey key : LandmarkKey.values()) {
            require(model.hasCleared(FIRST, key) && !model.hasCleared(SECOND, key),
                    "Personal first clear changed before the End chapter: " + key.id());
        }
        for (String friend : FRIENDS) require(FriendDomainUnlockState.get(server).cleared(friend),
                "Friend unlock was lost before the End chapter: " + friend);
        require(CampaignRouteState.get(server).chosen() == path
                        && RouteFinaleRuntime.ready(server, path)
                        && NetherChapterState.get(server).requiredChaptersCleared()
                        && EndSourceState.get(server).clearedPath() == null
                        && !RouteFinaleState.get(server).complete(),
                "Route, Nether chapters or uncompleted End boundary did not persist");
        checkRouteDetails(server, path);
    }

    private static void progressFieldLeads(MinecraftServer server, CampaignRouteState.Path path) {
        switch (path) {
            case REMEMBER -> {
                CampaignRouteState state = CampaignRouteState.get(server);
                require(state.recordRememberPrint(0) && state.recordRememberPrint(1)
                                && state.bindRememberInkbound()
                                && state.recordRememberWitness(1) && state.recordRememberWitness(2)
                                && state.compareRememberArchive() && state.startRememberTale()
                                && state.markRememberTale(YehiaStoryState.TREE) == 2
                                && state.markRememberTale(YehiaStoryState.LANTERN) == 3
                                && state.markRememberTale(YehiaStoryState.MUSHROOM) == 4
                                && state.finishRememberTale(), "Remember leads failed");
            }
            case REPAIR -> {
                RepairRouteState state = RepairRouteState.get(server);
                require(state.recordRepair(RepairRouteState.YARD)
                                && state.recordRepair(RepairRouteState.BRIDGE)
                                && state.recordRepair(RepairRouteState.LOFT)
                                && state.recordRiverwardBrace(RepairRouteState.WEST_BRACE)
                                && state.recordRiverwardBrace(RepairRouteState.EAST_BRACE)
                                && state.finishRiverward() && state.finishRedline(),
                        "Repair leads failed");
            }
            case RELEASE -> {
                ReleaseRouteState state = ReleaseRouteState.get(server);
                require(state.finishNourRelay()
                                && state.recordAliAnchor(ReleaseRouteState.HIGH_MAP,
                                        new BlockPos(0, 80, 0))
                                && state.recordAliAnchor(ReleaseRouteState.CUT_PASSAGE,
                                        new BlockPos(32, 80, 0))
                                && state.finishAliShortcut(), "Release anchors failed");
                long now = server.overworld().getGameTime();
                int serial = state.startFamDispatch(now);
                require(serial > 0 && state.stampFamDispatch(serial, ReleaseRouteState.FAM_STORY, now)
                                && state.stampFamDispatch(serial, ReleaseRouteState.FAM_SCHOOL, now)
                                && state.finishFamDispatch(serial, now), "Release dispatch failed");
            }
        }
    }

    private static void checkComplete(MinecraftServer server, CampaignRouteState.Path path,
                                      long claimedDay) {
        var model = CampaignPersistentState.get(server).model();
        require(model.snapshot().worldChangedMask() == LandmarkKey.ALL_MASK,
                "Seven boss world bits changed after branching");
        for (LandmarkKey key : LandmarkKey.values()) {
            require(model.hasCleared(FIRST, key) && !model.hasCleared(SECOND, key),
                    "Personal first clear changed after branching: " + key.id());
        }
        for (String friend : FRIENDS) require(FriendDomainUnlockState.get(server).cleared(friend),
                "Friend domain missing after ending: " + friend);
        require(CampaignRouteState.get(server).chosen() == path
                        && RouteFinaleRuntime.ready(server, path), "Route or field leads did not persist");
        checkRouteDetails(server, path);
        require(NetherChapterState.get(server).requiredChaptersCleared()
                        && EndSourceState.get(server).cleared(path)
                        && RequiredChapters.readyForCourt(server, path)
                        && RouteFinaleState.get(server).complete(),
                "Required chapters or ending did not persist");
        long nextDay = RouteFinaleState.get(server).nextServiceDay(FIRST);
        require(nextDay > 0 && (claimedDay < 0 || nextDay == claimedDay + 1),
                "Daily service claim did not persist");
        require(RouteFinaleState.get(server).nextServiceDay(SECOND) == 0,
                "Daily service claim leaked to another player");
    }

    private static void checkRouteDetails(MinecraftServer server, CampaignRouteState.Path path) {
        CampaignRouteState remember = CampaignRouteState.get(server);
        RepairRouteState repair = RepairRouteState.get(server);
        ReleaseRouteState release = ReleaseRouteState.get(server);
        switch (path) {
            case REMEMBER -> require(remember.rememberPrintCount() == 2
                            && remember.rememberInkboundBound()
                            && remember.rememberWitnessCount() == 2
                            && remember.rememberArchiveCompared()
                            && remember.rememberTaleStage() == 5
                            && repair.repairedCount() == 0
                            && !release.nourRelayComplete(),
                    "Remember details or route isolation changed");
            case REPAIR -> require(repair.repairedCount() == 3
                            && repair.riverwardBraceCount() == 2
                            && repair.riverwardComplete() && repair.redlineComplete()
                            && remember.rememberPrintCount() == 0
                            && !release.nourRelayComplete(),
                    "Repair details or route isolation changed");
            case RELEASE -> require(release.nourRelayComplete()
                            && release.aliAnchorsReady() && release.aliShortcutComplete()
                            && release.famDispatchStops() == (ReleaseRouteState.FAM_STORY
                                | ReleaseRouteState.FAM_SCHOOL)
                            && release.famDispatchComplete()
                            && remember.rememberPrintCount() == 0
                            && repair.repairedCount() == 0,
                    "Release details or route isolation changed");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
