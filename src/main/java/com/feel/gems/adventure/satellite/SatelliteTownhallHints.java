package com.feel.gems.adventure.satellite;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

/** Shared locations for late joiners at the townhall. */
public final class SatelliteTownhallHints {
    private SatelliteTownhallHints() { }

    /** Add the ocean location to the Harbor Map response once found. */
    public static String signalTable(MinecraftServer server) {
        return SatelliteState.get(server).sharedOceanBearing()
                .map(pos -> "The ocean reply came from " + coordinates(pos)
                        + " in the Overworld ocean. Bring a boat to the lit buoy.")
                .orElse("");
    }

    /** Append to the existing Route Desk response when unlocked. */
    public static String routeDesk(MinecraftServer server) {
        return SatelliteState.get(server).sharedArchiveBearing()
                .map(pos -> "Branch's lower index lies beneath the taiga near "
                        + coordinates(pos) + " in the Overworld. Look for the low timber clue.")
                .orElse("");
    }

    private static String coordinates(BlockPos pos) {
        return "X " + pos.getX() + ", Z " + pos.getZ();
    }
}
