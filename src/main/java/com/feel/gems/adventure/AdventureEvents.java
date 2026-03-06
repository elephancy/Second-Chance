package com.feel.gems.adventure;

import com.feel.gems.adventure.exploration.ExplorationRuntime;
import com.feel.gems.adventure.estate.EstateRuntime;
import com.feel.gems.adventure.endsource.EndSourceRuntime;
import com.feel.gems.adventure.maze.MazeRuntime;
import com.feel.gems.adventure.nether.NetherChapterRuntime;
import com.feel.gems.adventure.nether.NetherChapterGateRuntime;
import com.feel.gems.adventure.satellite.SatelliteRuntime;
import com.feel.gems.adventure.undercard.UndercardRuntime;
import com.feel.gems.casino.CasinoTournamentBoard;
import com.feel.gems.event.GemsPlayerDeath;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/** Retry undelivered personal lore without scanning inventories every tick. */
public final class AdventureEvents {
    private AdventureEvents() {
    }

    public static void register() {
        GemsPlayerDeath.registerDeathHeadCallback(victim ->
                OmarRecoveryState.get(victim.level().getServer()).record(victim.getUUID(),
                        victim.level().dimension().identifier(), victim.blockPosition()));
        RumorAtlasLocator.register();
        ExplorationRuntime.register();
        MazeRuntime.register();
        EstateRuntime.register();
        UndercardRuntime.register();
        DimensionTravelRuntime.register();
        NetherChapterGateRuntime.register();
        NetherChapterRuntime.register();
        EndSourceRuntime.register();
        SatelliteRuntime.register();
        TownHallServices.register();
        BossTownhallServices.register();
        PracticeStageRuntime.register();
        RouteDeskRuntime.register();
        CasinoTournamentBoard.register();
        InkboundWorkshop.register();
        KhaledCrossingRuntime.register();
        NourSignalRuntime.register();
        NourRelayRuntime.register();
        FamForumRuntime.register();
        FamDispatchRuntime.register();
        FamCircleRuntime.register();
        AdlSurveyRuntime.register();
        OthmanArchiveRuntime.register();
        YehiaStoryRuntime.register();
        OmarLostRuntime.register();
        SoryalRepairRuntime.register();
        AliRouteRuntime.register();
        CampaignRouteRuntime.register();
        RememberWitnessRuntime.register();
        RepairRouteRuntime.register();
        RedlineAidRuntime.register();
        RouteFinaleRuntime.register();
        SignalBuoyRuntime.register();
        RescueDispatchRuntime.register();
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> RumorAtlasItem.clearSearchCache());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                CampaignRewards.deliverPendingLore(handler.getPlayer()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 100 != 0) {
                return;
            }
            for (var player : server.getPlayerList().getPlayers()) {
                CampaignRewards.deliverPendingLore(player);
            }
        });
    }
}
