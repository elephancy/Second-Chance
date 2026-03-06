package com.feel.gems.adventure;

import com.feel.gems.adventure.boss.BossFeatures;
import com.feel.gems.adventure.endsource.EndSourceBlocks;
import com.feel.gems.adventure.estate.EstateBlocks;
import com.feel.gems.adventure.estate.FourRoadsEstateStructure;
import com.feel.gems.adventure.exploration.ExplorationBlocks;
import com.feel.gems.adventure.maze.MazeBlocks;
import com.feel.gems.adventure.nether.NetherChapterBlocks;
import com.feel.gems.adventure.satellite.SatelliteBlocks;
import com.feel.gems.adventure.satellite.SatelliteStructure;
import com.feel.gems.adventure.undercard.UndercardBlocks;
import com.feel.gems.adventure.undercard.UndercardGateController;
import com.feel.gems.adventure.undercard.UndercardEntities;
import com.feel.gems.adventure.undercard.UndercardStructure;
import com.feel.gems.adventure.worldgen.BranchFolioFeature;
import com.feel.gems.adventure.worldgen.AliDryArrivalStructure;
import com.feel.gems.adventure.worldgen.DistrictArrivalAccessFeature;
import com.feel.gems.adventure.worldgen.DistrictCanopyTransitionFeature;
import com.feel.gems.adventure.estate.EstateArrivalClearanceFeature;
import com.feel.gems.adventure.worldgen.FramefallCanopyTransitionFeature;
import com.feel.gems.adventure.worldgen.FamV10TrialStructure;
import com.feel.gems.adventure.worldgen.GuardedDistrictBridges;
import com.feel.gems.adventure.worldgen.HushwireApproachStructure;
import com.feel.gems.adventure.worldgen.LastlightMarketBridgeFeature;
import com.feel.gems.adventure.worldgen.NourCoastalStructure;
import com.feel.gems.adventure.worldgen.OmarWadiAccess;
import com.feel.gems.adventure.worldgen.OthmanBankApproachFeature;
import com.feel.gems.adventure.worldgen.OthmanThemedStructure;
import com.feel.gems.adventure.worldgen.SplitstoneNeedleFeature;
import com.feel.gems.adventure.worldgen.SoryalShoreStructure;
import com.feel.gems.adventure.worldgen.site.GuardedSiteScheduler;
import com.feel.gems.adventure.worldgen.site.DimensionChapterStructure;
import com.feel.gems.casino.CasinoNetworking;
import com.feel.gems.friend.FriendFeatures;
import net.fabricmc.api.ModInitializer;

/** Second Chance add-on initializer, loaded after the Gems dependency. */
public final class SecondChanceMod implements ModInitializer {
    @Override
    public void onInitialize() {
        AdventureSounds.init();
        AdventureParticles.init();
        AdventureEntities.init();
        UndercardEntities.init();
        DimensionChapterStructure.init();
        OthmanThemedStructure.init();
        SoryalShoreStructure.init();
        HushwireApproachStructure.init();
        AliDryArrivalStructure.init();
        FamV10TrialStructure.init();
        FourRoadsEstateStructure.init();
        UndercardStructure.init();
        SatelliteStructure.init();
        FriendFeatures.init();
        CasinoNetworking.register();
        DomainBlocks.init();
        GuardedSiteScheduler.init();
        GuardedDistrictBridges.init();
        OmarWadiAccess.init();
        ExplorationBlocks.init();
        MazeBlocks.init();
        EstateBlocks.init();
        EstateArrivalClearanceFeature.init();
        UndercardBlocks.init();
        NetherChapterBlocks.init();
        EndSourceBlocks.init();
        SatelliteBlocks.init();
        UndercardGateController.init();
        SplitstoneNeedleFeature.init();
        BranchFolioFeature.init();
        FramefallCanopyTransitionFeature.init();
        LastlightMarketBridgeFeature.init();
        NourCoastalStructure.init();
        OthmanBankApproachFeature.init();
        DistrictArrivalAccessFeature.init();
        DistrictCanopyTransitionFeature.init();
        AdventureItems.init();
        BossFeatures.init();
        AdventureCreativeItems.register();
        AdventureEvents.register();
        SecondChanceCommands.register();
    }
}
