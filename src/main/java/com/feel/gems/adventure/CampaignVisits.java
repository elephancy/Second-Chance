package com.feel.gems.adventure;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;

/** Record a discovery from an already-bounded encounter player list. */
public final class CampaignVisits {
    private CampaignVisits() {
    }

    public static void markNearby(ServerLevel world, List<ServerPlayer> players,
                                  LandmarkKey landmark) {
        if (players.isEmpty()) return;
        CampaignPersistentState state = CampaignPersistentState.get(world.getServer());
        for (ServerPlayer player : players) {
            state.visit(player.getUUID(), landmark);
        }
    }
}
