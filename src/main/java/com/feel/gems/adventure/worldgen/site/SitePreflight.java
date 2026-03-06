package com.feel.gems.adventure.worldgen.site;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Fail-closed validation before any site write. Preflight itself never mutates a world. */
public final class SitePreflight {
    private static final long MAX_SYNCHRONOUS_CELLS = 200_000;
    public enum Status { READY, REJECTED, NEEDS_SCHEDULER }
    public enum Reason {
        NONE, WRONG_PLAN, WRONG_DIMENSION, ANCHOR_HEIGHT, WORLD_BOUNDS, ENGINE_SEAM,
        CHUNK_UNAVAILABLE, EXISTING_CHUNK, PROTECTED_AREA, PROTECTION_UNPROVEN,
        BIOME, TERRAIN_UNPROVEN,
        TERRAIN, PLAN_OUTSIDE_VOLUME, CLEARANCE, BLOCK_ENTITY, BLOCK_CONFLICT
    }

    public record Result(Status status, Reason reason, BlockPos at) {
        static Result ready() { return new Result(Status.READY, Reason.NONE, null); }
        static Result reject(Reason reason, BlockPos at) {
            return new Result(Status.REJECTED, reason, at == null ? null : at.immutable());
        }
        static Result scheduler() {
            return new Result(Status.NEEDS_SCHEDULER, Reason.ENGINE_SEAM, null);
        }
    }

    private SitePreflight() { }

    public static Result check(SiteDefinition definition, SiteCandidate candidate,
                               SitePlacementPlan plan, SiteAccess access) {
        return check(definition, candidate, plan, access, false);
    }

    /** Resume only after a matching persistent PREPARED receipt has been read. */
    public static Result check(SiteDefinition definition, SiteCandidate candidate,
                               SitePlacementPlan plan, SiteAccess access,
                               boolean allowCompletedWrites) {
        if (!definition.id().equals(plan.siteId()) || definition.version() != plan.version()) {
            return Result.reject(Reason.WRONG_PLAN, null);
        }
        if (!definition.dimension().equals(access.dimension())) {
            return Result.reject(Reason.WRONG_DIMENSION, candidate.anchor());
        }
        int anchorY = candidate.anchor().getY();
        if (anchorY < definition.minAnchorY() || anchorY > definition.maxAnchorY()) {
            return Result.reject(Reason.ANCHOR_HEIGHT, candidate.anchor());
        }
        List<BoundingBox> volumes;
        try {
            volumes = candidate.bounds(definition);
        } catch (ArithmeticException overflow) {
            return Result.reject(Reason.WORLD_BOUNDS, candidate.anchor());
        }
        for (BoundingBox volume : volumes) {
            if (volume.minX() < -30_000_000 || volume.maxX() > 30_000_000
                    || volume.minZ() < -30_000_000 || volume.maxZ() > 30_000_000) {
                return Result.reject(Reason.WORLD_BOUNDS,
                        new BlockPos(volume.minX(), volume.minY(), volume.minZ()));
            }
        }
        long scanCells = 0;
        for (BoundingBox volume : volumes) {
            scanCells += (long) volume.getXSpan() * volume.getYSpan()
                    * volume.getZSpan();
            if (scanCells > MAX_SYNCHRONOUS_CELLS) return Result.scheduler();
        }
        List<ChunkPos> chunks = candidate.chunks(definition);
        for (BoundingBox volume : volumes) {
            if (volume.minY() < access.minY() || volume.maxY() >= access.maxY()) {
                return Result.reject(Reason.ANCHOR_HEIGHT,
                        new BlockPos(volume.minX(), volume.minY(), volume.minZ()));
            }
        }
        if (access.seam() == SiteAccess.Seam.WORLDGEN_REGION && chunks.size() > 1) {
            return Result.scheduler();
        }
        for (ChunkPos chunk : chunks) {
            if (!access.canInspectChunk(chunk)) {
                return Result.reject(Reason.CHUNK_UNAVAILABLE,
                        new BlockPos(chunk.getMinBlockX(), anchorY, chunk.getMinBlockZ()));
            }
            if (!access.isNewChunk(chunk)) {
                return Result.reject(Reason.EXISTING_CHUNK,
                        new BlockPos(chunk.getMinBlockX(), anchorY, chunk.getMinBlockZ()));
            }
        }
        for (BoundingBox volume : volumes) {
            SiteAccess.Inspection inspection = access.protectedVolume(volume);
            if (inspection != SiteAccess.Inspection.CLEAR) {
                return Result.reject(inspection == SiteAccess.Inspection.UNKNOWN
                                ? Reason.PROTECTION_UNPROVEN : Reason.PROTECTED_AREA,
                        new BlockPos(volume.minX(), volume.minY(), volume.minZ()));
            }
        }
        Set<Long> surveyed = new HashSet<>();
        int lowestFloor = Integer.MAX_VALUE;
        int highestFloor = Integer.MIN_VALUE;
        for (BoundingBox volume : volumes) {
            for (int x = volume.minX(); x <= volume.maxX(); x++) {
                for (int z = volume.minZ(); z <= volume.maxZ(); z++) {
                    long columnKey = ((long) x << 32) ^ (z & 0xffffffffL);
                    if (!surveyed.add(columnKey)) continue;
                    BlockPos sample = new BlockPos(x, anchorY, z);
                    if (!access.biomeMatches(sample, definition.allowedBiomes())) {
                        return Result.reject(Reason.BIOME, sample);
                    }
                    SiteAccess.Column column = access.column(x, z, anchorY, definition);
                    if (column == null) return Result.reject(Reason.TERRAIN_UNPROVEN, sample);
                    lowestFloor = Math.min(lowestFloor, column.floorY());
                    highestFloor = Math.max(highestFloor, column.floorY());
                    if (column.solidSupportDepth() < definition.minSupportDepth()
                            || column.roofThickness() < definition.minRoofThickness()
                            || column.hazardDistance() < definition.minHazardDistance()
                            || (definition.setting() == SiteDefinition.Setting.OCEAN
                                    && column.waterDepth() < definition.minWaterDepth())) {
                        return Result.reject(Reason.TERRAIN, sample);
                    }
                }
            }
        }
        if ((definition.setting() == SiteDefinition.Setting.SURFACE
                || definition.setting() == SiteDefinition.Setting.OCEAN)
                && highestFloor - lowestFloor > definition.maxSlope()) {
            return Result.reject(Reason.TERRAIN, candidate.anchor());
        }
        for (SitePlacementPlan.Requirement requirement : plan.requirements()) {
            BlockPos pos = requirement.pos();
            if (!insideAny(volumes, pos)) return Result.reject(Reason.PLAN_OUTSIDE_VOLUME, pos);
            BlockState current = access.state(pos);
            if (access.hasBlockEntity(pos)) return Result.reject(Reason.BLOCK_ENTITY, pos);
            boolean matches = switch (requirement.condition()) {
                case AIR -> current.isAir();
                case WATER -> current.is(Blocks.WATER)
                        && current.getFluidState().isSource();
                case LAVA -> current.is(Blocks.LAVA)
                        && current.getFluidState().isSource();
                case DRY_SOLID -> current.isSolid() && current.getFluidState().isEmpty();
                case EXACT -> current.equals(requirement.expected());
            };
            if (!matches) return Result.reject(Reason.CLEARANCE, pos);
        }
        for (int index = 0; index < volumes.size(); index++) {
            if (definition.volumes().get(index).use() != SiteVolume.Use.CLEARANCE) continue;
            BoundingBox volume = volumes.get(index);
            for (int x = volume.minX(); x <= volume.maxX(); x++) {
                for (int z = volume.minZ(); z <= volume.maxZ(); z++) {
                    for (int y = volume.minY(); y <= volume.maxY(); y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (access.hasBlockEntity(pos)) return Result.reject(Reason.BLOCK_ENTITY, pos);
                        BlockState current = access.state(pos);
                        if (!current.isAir()
                                && !(definition.setting() == SiteDefinition.Setting.OCEAN
                                        && current.is(Blocks.WATER))) {
                            return Result.reject(Reason.CLEARANCE, pos);
                        }
                    }
                }
            }
        }
        for (Map.Entry<BlockPos, SitePlacementPlan.Write> entry : plan.writes().entrySet()) {
            BlockPos pos = entry.getKey();
            if (!insideWritable(volumes, definition, pos) || !access.canWrite(pos)) {
                return Result.reject(Reason.PLAN_OUTSIDE_VOLUME, pos);
            }
            if (access.hasBlockEntity(pos)) return Result.reject(Reason.BLOCK_ENTITY, pos);
            BlockState current = access.state(pos);
            SitePlacementPlan.Write write = entry.getValue();
            boolean replaceable = switch (write.replace()) {
                case AIR -> current.isAir();
                case WATER -> current.is(Blocks.WATER)
                        && current.getFluidState().isSource();
                case LAVA -> current.is(Blocks.LAVA)
                        && current.getFluidState().isSource();
                case NATURAL -> access.isNaturalTerrain(current);
                case EXACT -> current.equals(write.expected());
            };
            if (!replaceable && !(allowCompletedWrites && current.equals(write.state()))) {
                return Result.reject(Reason.BLOCK_CONFLICT, pos);
            }
        }
        return Result.ready();
    }

    private static boolean insideAny(List<BoundingBox> volumes, BlockPos pos) {
        return volumes.stream().anyMatch(box -> box.isInside(pos));
    }

    private static boolean insideWritable(List<BoundingBox> volumes,
                                          SiteDefinition definition, BlockPos pos) {
        for (int index = 0; index < volumes.size(); index++) {
            if (definition.volumes().get(index).use() != SiteVolume.Use.CLEARANCE
                    && volumes.get(index).isInside(pos)) return true;
        }
        return false;
    }
}
