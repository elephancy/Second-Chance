package com.feel.gems.adventure.nether;

import com.feel.gems.adventure.worldgen.site.GuardedSiteScheduler;
import com.feel.gems.adventure.worldgen.site.SiteCandidate;
import com.feel.gems.adventure.worldgen.site.SiteDefinition;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.levelgen.structure.Structure;

/** V6 discovery reads existing FULL chunks only; it never generates a remote route. */
public final class EmberV6Readiness {
    private EmberV6Readiness() { }

    public static BlockPos outerArch(SiteCandidate candidate) {
        return NetherChapterSites.at(candidate, 39, 5, 70);
    }

    public static void recordTerminal(ServerLevel level, ChunkPos owner,
                                      SiteCandidate candidate, Structure own,
                                      GuardedSiteScheduler.Outcome outcome) {
        if (level.dimension() != Level.NETHER) return;
        BlockPos arch = outerArch(candidate);
        boolean ready = outcome == GuardedSiteScheduler.Outcome.COMPLETE
                && loadedCheck(level, candidate.anchor(), own).orElse(false);
        EmberV6ReadinessState.get(level).observe(owner, candidate.anchor(), arch,
                ready ? EmberV6ReadinessState.Verdict.READY
                        : EmberV6ReadinessState.Verdict.SEALED);
    }

    /** A recorded READY is demoted if its already loaded passage changed. */
    public static EmberV6ReadinessState.Verdict verdict(ServerLevel level,
            ChunkPos owner, BlockPos root, BlockPos arch, Structure own) {
        EmberV6ReadinessState state = EmberV6ReadinessState.get(level);
        EmberV6ReadinessState.Verdict saved = state.verdict(owner, root, arch);
        if (saved != EmberV6ReadinessState.Verdict.READY) return saved;
        Optional<Boolean> current = loadedCheck(level, root, own);
        if (current.isPresent() && !current.get()) {
            state.observe(owner, root, arch, EmberV6ReadinessState.Verdict.SEALED);
            return EmberV6ReadinessState.Verdict.SEALED;
        }
        return saved;
    }

    private static Optional<Boolean> loadedCheck(ServerLevel level, BlockPos root,
                                                  Structure own) {
        if (own == null) return Optional.of(false);
        ChunkPos markerChunk = new ChunkPos(root.getX() >> 4, root.getZ() >> 4);
        if (level.getChunkSource().getChunkNow(markerChunk.x(), markerChunk.z()) == null) {
            return Optional.empty();
        }
        var state = level.getBlockState(root);
        if (!state.is(NetherChapterSites.rootBlock(NetherChapterSites.Site.EMBER_BRIDGE))) {
            return Optional.of(false);
        }
        SiteCandidate candidate = NetherChapterSites.candidate(root,
                state.getValue(HorizontalDirectionalBlock.FACING));
        SiteDefinition definition = NetherChapterSites.definition(
                NetherChapterSites.Site.EMBER_BRIDGE);
        for (ChunkPos chunk : candidate.chunks(definition)) {
            if (level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) == null) {
                return Optional.empty();
            }
        }
        boolean gatesOpen = NetherChapterSites.sealedGates(
                NetherChapterSites.Site.EMBER_BRIDGE, candidate).stream()
                .allMatch(gate -> level.getBlockState(gate.pos()).equals(gate.open()));
        if (!gatesOpen) return Optional.of(false);
        NetherChapterSiteAccess access = new NetherChapterSiteAccess(level,
                Set.of(own), NetherChapterSites.Site.EMBER_BRIDGE, candidate, definition);
        return Optional.of(access.recheckLoadedRoute());
    }
}
