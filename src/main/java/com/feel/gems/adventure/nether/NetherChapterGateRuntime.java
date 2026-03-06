package com.feel.gems.adventure.nether;

import com.feel.gems.adventure.worldgen.site.GuardedSiteScheduler;
import com.feel.gems.adventure.worldgen.site.SiteCandidate;
import com.feel.gems.adventure.worldgen.site.SiteDefinition;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Rediscovers saved Nether roots on chunk load, including after a restart.
 * Only the sealed six-cell entry is changed, after the bounded scheduler has
 * checked new chunks, foreign structures, every authored control and the
 * complete loaded route. No natural Nether terrain is carved after generation.
 */
public final class NetherChapterGateRuntime {
    private static boolean registered;

    private NetherChapterGateRuntime() { }

    public static void register() {
        if (registered) return;
        registered = true;
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, newlyGenerated) -> {
            if (level.dimension() != Level.NETHER) return;
            inspect(level, chunk);
        });
    }

    private static void inspect(ServerLevel level, LevelChunk chunk) {
        // Reject most chunks by palette before checking individual blocks.
        LevelChunkSection[] sections = chunk.getSections();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (!section.maybeHas(state -> state.is(NetherChapterSites.rootBlock(
                    NetherChapterSites.Site.ASH_ANNEX))
                    || state.is(NetherChapterSites.rootBlock(
                            NetherChapterSites.Site.EMBER_BRIDGE)))) continue;
            int baseY = level.getMinY() + index * 16;
            for (int dy = 0; dy < 16; dy++) {
                int y = baseY + dy;
                if (y < 33 || y > 84) continue;
                for (int dz = 0; dz < 16; dz++) {
                    for (int dx = 0; dx < 16; dx++) {
                        var state = section.getBlockState(dx, dy, dz);
                        NetherChapterSites.Site site = state.is(
                                NetherChapterSites.rootBlock(
                                        NetherChapterSites.Site.ASH_ANNEX))
                                ? NetherChapterSites.Site.ASH_ANNEX
                                : state.is(NetherChapterSites.rootBlock(
                                        NetherChapterSites.Site.EMBER_BRIDGE))
                                        ? NetherChapterSites.Site.EMBER_BRIDGE : null;
                        if (site == null) continue;
                        BlockPos root = new BlockPos(minX + dx, y, minZ + dz);
                        submit(level, chunk, root, site, state.getValue(
                                HorizontalDirectionalBlock.FACING));
                    }
                }
            }
        }
    }

    private static void submit(ServerLevel level, LevelChunk chunk, BlockPos root,
                               NetherChapterSites.Site site, net.minecraft.core.Direction facing) {
        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        SiteDefinition definition = NetherChapterSites.definition(site);
        Structure own = structures.getValue(
                ResourceKey.create(Registries.STRUCTURE, definition.id()));
        if (own == null || !chunk.getAllReferences().containsKey(own)) return;
        SiteCandidate candidate = NetherChapterSites.candidate(root, facing);
        NetherChapterSiteAccess access = new NetherChapterSiteAccess(level,
                Set.of(own), site, candidate, definition);
        var plan = NetherChapterSites.gatePlan(site, candidate, definition);
        var gates = NetherChapterSites.sealedGates(site, candidate);
        if (site == NetherChapterSites.Site.EMBER_BRIDGE) {
            // The root marker may be in a different chunk from its shifted
            // saved start. One exact reference identifies its original owner.
            var references = chunk.getAllReferences().get(own);
            if (references == null || references.size() != 1) return;
            long packed = references.iterator().nextLong();
            ChunkPos owner = new ChunkPos(ChunkPos.getX(packed), ChunkPos.getZ(packed));
            GuardedSiteScheduler.submit(level, definition, candidate, plan,
                    access, gates, outcome -> EmberV6Readiness.recordTerminal(
                            level, owner, candidate, own, outcome));
        } else {
            GuardedSiteScheduler.submit(level, definition, candidate, plan,
                    access, gates);
        }
    }
}
