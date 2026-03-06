package com.feel.gems.adventure.nether;

import com.feel.gems.adventure.worldgen.site.GuardedNewChunkSiteAccess;
import com.feel.gems.adventure.worldgen.site.SiteCandidate;
import com.feel.gems.adventure.worldgen.site.SiteDefinition;
import com.feel.gems.adventure.worldgen.site.SiteAccess;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Loaded, new-chunk route proof for a generated Nether chapter body. */
public final class NetherChapterSiteAccess extends GuardedNewChunkSiteAccess {
    private final ServerLevel level;
    private final NetherChapterSites.Site site;
    private final SiteCandidate candidate;
    private final SiteDefinition definition;
    private long checkedTick = Long.MIN_VALUE;
    private boolean routeSafe;

    public NetherChapterSiteAccess(ServerLevel level, Set<Structure> currentStarts,
                                   NetherChapterSites.Site site, SiteCandidate candidate,
                                   SiteDefinition definition) {
        super(level, currentStarts);
        this.level = level;
        this.site = site;
        this.candidate = candidate;
        this.definition = definition;
    }

    @Override
    public Column column(int x, int z, int anchorY, SiteDefinition definition) {
        Column generic = super.column(x, z, anchorY, definition);
        if (generic == null) return null;
        // Recheck at execution time, not just at chunk-load submission. A
        // player can alter a route while the scheduler waits for more chunks.
        return new Column(generic.floorY(), generic.waterDepth(),
                generic.solidSupportDepth(), generic.roofThickness(),
                routeSafeThisTick() ? 1 : 0);
    }

    private boolean routeSafeThisTick() {
        long tick = level.getGameTime();
        if (checkedTick == tick) return routeSafe;
        checkedTick = tick;
        routeSafe = false;
        var fullBody = candidate.bounds(definition).getFirst();
        for (int cx = fullBody.minX() >> 4; cx <= fullBody.maxX() >> 4; cx++) {
            for (int cz = fullBody.minZ() >> 4; cz <= fullBody.maxZ() >> 4; cz++) {
                ChunkPos chunk = new ChunkPos(cx, cz);
                if (!canInspectChunk(chunk) || !isNewChunk(chunk)) return false;
            }
        }
        if (protectedVolume(fullBody) != SiteAccess.Inspection.CLEAR) return false;
        if (site == NetherChapterSites.Site.EMBER_BRIDGE) {
            for (int z : new int[]{18, 23, 28}) {
                if (!level.getBiome(NetherChapterSites.at(candidate, 39, 5, z))
                        .is(Biomes.WARPED_FOREST)) return false;
            }
        }
        routeSafe = NetherChapterSites.mandatoryPathDry(site, candidate, this);
        return routeSafe;
    }

    /** Event-driven recheck; never rely on a prior tick's route cache. */
    public boolean recheckLoadedRoute() {
        checkedTick = Long.MIN_VALUE;
        return routeSafeThisTick();
    }
}
