package com.feel.gems.adventure.worldgen.site;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * The 26.3 late-feature view. It can validate a center-chunk edit, but cannot
 * place a complete multi-chunk estate: its readable reference ring is only 3x3
 * and {@code ensureCanWrite} limits edits to the current center chunk.
 */
public final class WorldGenRegionSiteAccess implements SiteAccess {
    private final WorldGenRegion region;
    private final Set<Structure> currentSiteStarts;

    public WorldGenRegionSiteAccess(WorldGenRegion region, Set<Structure> currentSiteStarts) {
        this.region = region;
        this.currentSiteStarts = Set.copyOf(currentSiteStarts);
    }

    @Override
    public Seam seam() { return Seam.WORLDGEN_REGION; }

    @Override
    public ResourceKey<Level> dimension() { return region.getLevel().dimension(); }

    @Override
    public int minY() { return region.getMinY(); }

    @Override
    public int maxY() { return region.getMaxY(); }

    @Override
    public boolean canInspectChunk(ChunkPos chunk) {
        ChunkPos center = region.getCenter();
        return Math.abs(chunk.x() - center.x()) <= 1
                && Math.abs(chunk.z() - center.z()) <= 1
                && region.hasChunk(chunk.x(), chunk.z());
    }

    @Override
    public boolean isNewChunk(ChunkPos chunk) {
        return canInspectChunk(chunk) && !region.getChunk(chunk.x(), chunk.z())
                .getPersistedStatus().isOrAfter(ChunkStatus.FULL);
    }

    @Override
    public boolean canWrite(BlockPos pos) {
        ChunkPos center = region.getCenter();
        return (pos.getX() >> 4) == center.x() && (pos.getZ() >> 4) == center.z()
                && region.ensureCanWrite(pos);
    }

    @Override
    public Inspection protectedVolume(BoundingBox bounds) {
        for (int cx = bounds.minX() >> 4; cx <= bounds.maxX() >> 4; cx++) {
            for (int cz = bounds.minZ() >> 4; cz <= bounds.maxZ() >> 4; cz++) {
                ChunkPos chunk = new ChunkPos(cx, cz);
                if (!canInspectChunk(chunk)) return Inspection.UNKNOWN;
                var references = region.getChunk(cx, cz,
                        ChunkStatus.STRUCTURE_REFERENCES, false).getAllReferences();
                for (var entry : references.entrySet()) {
                    if (currentSiteStarts.contains(entry.getKey())) continue;
                    for (long reference : entry.getValue()) {
                        ChunkPos startPos = new ChunkPos(ChunkPos.getX(reference),
                                ChunkPos.getZ(reference));
                        if (!canInspectChunk(startPos)) return Inspection.UNKNOWN;
                        StructureStart start = region.getChunk(startPos.x(), startPos.z(),
                                ChunkStatus.STRUCTURE_STARTS, false)
                                .getStartForStructure(entry.getKey());
                        if (start == null || !start.isValid()) return Inspection.UNKNOWN;
                        for (StructurePiece piece : start.getPieces()) {
                            if (piece.getBoundingBox().intersects(bounds)) return Inspection.CONFLICT;
                        }
                    }
                }
            }
        }
        return Inspection.CLEAR;
    }

    @Override
    public boolean biomeMatches(BlockPos pos, Set<ResourceKey<Biome>> allowed) {
        for (ResourceKey<Biome> biome : allowed) {
            if (region.getBiome(pos).is(biome)) return true;
        }
        return false;
    }

    @Override
    public Column column(int x, int z, int anchorY, SiteDefinition definition) {
        if (definition.setting() != SiteDefinition.Setting.SURFACE
                && definition.setting() != SiteDefinition.Setting.OCEAN) return null;
        int y = definition.setting() == SiteDefinition.Setting.OCEAN
                ? region.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1
                : region.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        if (y < region.getMinY() || y >= region.getMaxY()) return null;
        int support = 0;
        for (int depth = 0; depth < definition.minSupportDepth(); depth++) {
            int probeY = y - depth;
            if (probeY < region.getMinY()) break;
            BlockState state = region.getBlockState(new BlockPos(x, probeY, z));
            if (!state.isSolid() || !state.getFluidState().isEmpty()) break;
            support++;
        }
        int water = 0;
        if (definition.setting() == SiteDefinition.Setting.OCEAN) {
            for (int rise = 1; rise <= definition.minWaterDepth(); rise++) {
                int probeY = y + rise;
                if (probeY >= region.getMaxY()
                        || !region.getBlockState(new BlockPos(x, probeY, z)).is(Blocks.WATER)) break;
                water++;
            }
        }
        // This seam cannot establish a remote hazard exclusion radius. Only
        // policies with zero roof/hazard minima can pass here.
        return new Column(y, water, support, 0, 0);
    }

    @Override
    public BlockState state(BlockPos pos) { return region.getBlockState(pos); }

    @Override
    public boolean hasBlockEntity(BlockPos pos) { return region.getBlockEntity(pos) != null; }

    @Override
    public boolean isNaturalTerrain(BlockState state) {
        return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.TUFF) || state.is(Blocks.DIRT)
                || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL) || state.is(Blocks.ANDESITE)
                || state.is(Blocks.DIORITE) || state.is(Blocks.GRANITE)
                || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)
                || state.is(Blocks.SANDSTONE) || state.is(Blocks.GRAVEL)
                || state.is(Blocks.CLAY) || state.is(Blocks.NETHERRACK)
                || state.is(Blocks.BASALT) || state.is(Blocks.BLACKSTONE)
                || state.is(Blocks.END_STONE);
    }
}
