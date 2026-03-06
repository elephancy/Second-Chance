package com.feel.gems.adventure.worldgen;

import com.feel.gems.GemsMod;
import com.feel.gems.adventure.DomainBlocks;
import com.mojang.serialization.MapCodec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;

/** Bounded, rotation-aware tree windows around newly generated district arrivals. */
public final class DistrictCanopyTransitionFeature implements Feature {
    public static final MapCodec<DistrictCanopyTransitionFeature> CODEC =
            MapCodec.unit(new DistrictCanopyTransitionFeature());
    private static final Identifier ID = gems("district_canopy_transition");
    private static final ResourceKey<PlacedFeature> PLACED =
            ResourceKey.create(Registries.PLACED_FEATURE, ID);
    private static final int MAX_HALO = 40;
    // TOP_LAYER_MODIFICATION exposes only the immediate 3x3 reference region.
    // hasChunk can report cached chunks beyond that region, but getChunk still
    // rejects them during generation.
    private static final int REFERENCE_RING = 1;
    private static final int MAX_START_CHUNK_DISTANCE = 8;

    // Local directions rotate with the active root piece. A near-facade apron
    // can be wider than its outward sight corridor; Othman's measured east
    // window starts seven blocks beyond the wall and clears only whole trees.
    static final List<Spec> SPECS = List.of(
            new Spec("branch", structure("branch_vault"), gems("branch_vault_vista_v7"),
                    Direction.SOUTH, Direction.EAST,
                    1, 40, 7, 8, 8, -2, 8,
                    -16, 24, 24, 12, 8),
            new Spec("branch_themed", structure("branch_vault_themed"),
                    gems("branch_vault_vista_v7"), Direction.SOUTH, Direction.EAST,
                    1, 40, 7, 8, 8, -2, 8,
                    -16, 24, 24, 12, 8),
            new Spec("fam", structure("fam_forum"), gems("fam_forum_east_wayfinding_v7"),
                    Direction.SOUTH, Direction.WEST,
                    1, 32, 8, 4, 4, 0, 4,
                    -16, 18, 15, 8, 8),
            // The v10 campus has three separate west-facing facades. Each
            // exact saved template must get its own short sight window; the
            // root window cannot reach the adjacent school and story halls.
            new Spec("fam_v10_court", structure("fam_forum_v10_trial"),
                    gems("fam_forum_arcaded_court_v10"), Direction.SOUTH, Direction.EAST,
                    1, 36, 8, 12, 8, 0, 2,
                    -12, 20, 20, 12, 10),
            new Spec("fam_v10_school", structure("fam_forum_v10_trial"),
                    gems("fam_open_school_workshop_v10"), Direction.SOUTH, Direction.EAST,
                    1, 36, 8, 12, 8, 0, 2,
                    -12, 20, 20, 12, 10),
            new Spec("fam_v10_story", structure("fam_forum_v10_trial"),
                    gems("fam_story_hall_reading_garden_v10"), Direction.SOUTH, Direction.EAST,
                    1, 36, 8, 12, 8, 0, 2,
                    -12, 20, 20, 12, 10),
            new Spec("fam_public_court", structure("fam_forum"),
                    gems("fam_forum_arcaded_court_v10"), Direction.SOUTH, Direction.EAST,
                    1, 36, 8, 12, 8, 0, 2,
                    -12, 20, 20, 12, 10),
            new Spec("fam_public_school", structure("fam_forum"),
                    gems("fam_open_school_workshop_v10"), Direction.SOUTH, Direction.EAST,
                    1, 36, 8, 12, 8, 0, 2,
                    -12, 20, 20, 12, 10),
            new Spec("fam_public_story", structure("fam_forum"),
                    gems("fam_story_hall_reading_garden_v10"), Direction.SOUTH, Direction.EAST,
                    1, 36, 8, 12, 8, 0, 2,
                    -12, 20, 20, 12, 10),
            new Spec("othman", structure("othman_library_themed"),
                    gems("othman_library_archive_v7"),
                    Direction.SOUTH, Direction.WEST,
                    7, 16, 0, 2, 2, 2, -1,
                    2, 5, 15, 4, 4),
            // The public south stair sits left of the hall's centerline.
            // A separate narrow window reveals it from the natural approach
            // without thinning the trees that frame the east garden facade.
            new Spec("othman_south", structure("othman_library_themed"),
                    gems("othman_library_archive_v7"),
                    Direction.WEST, Direction.SOUTH,
                    1, 18, 8, 6, 5, -14, -1,
                    -16, 24, 20, 4, 4));

    private DistrictCanopyTransitionFeature() { }

    public static void init() {
        Registry.register(BuiltInRegistries.FEATURE_TYPE, ID, CODEC);
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld()
                        .or(BiomeSelectors.includeByKey(AdventureBiomes.BRANCH_TAIGA,
                                AdventureBiomes.FORUM_GLADE,
                                AdventureBiomes.ARCHIVE_COMMONS)),
                GenerationStep.Decoration.TOP_LAYER_MODIFICATION, PLACED);
    }

    @Override
    public MapCodec<DistrictCanopyTransitionFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random,
                         BlockPos origin) {
        if (!(level instanceof WorldGenRegion region)) return false;
        ChunkPos center = region.getCenter();
        // An old FULL chunk can be present in the generation region while a
        // neighbour is new. Never rework it, even if its structure is active.
        if (region.getChunk(center.x(), center.z()).getPersistedStatus()
                .isOrAfter(ChunkStatus.FULL)) return false;

        var structures = region.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        List<ActiveSpec> active = new ArrayList<>();
        for (Spec spec : SPECS) {
            Structure structure = structures.getValue(spec.structure());
            if (structure != null) active.add(new ActiveSpec(spec, structure));
        }
        if (active.isEmpty()) return false;
        // A v10 arrival deck is a local, saved marker. Finish its tree window
        // even if this chunk cannot read the four-chunk-distant start owner.
        boolean changed = FamV10ArrivalTerrace.clearCanopyOverMarker(region,
                center, structures.getValue(structure("fam_forum_v10_trial")));
        changed |= FamV10ArrivalTerrace.clearCanopyOverMarker(region,
                center, structures.getValue(structure("fam_forum")));

        // Every structure piece that intersects the writable center chunk is
        // referenced there. Resolve only cached start chunks; an unresolved
        // reference vetoes the whole center chunk rather than risking a cut
        // through another structure's trees or decorations.
        List<BoundingBox> protectedPieces = protectedPieces(region, center);
        if (protectedPieces == null) return changed;

        Set<StartReference> seen = new HashSet<>();
        // Read only the generation stage's immediate STRUCTURE_REFERENCES ring,
        // then only cached STRUCTURE_STARTS. A mask point without a discoverable
        // structure reference is left untouched. Never ask StructureManager
        // for a remote start or load missing chunks.
        for (int cz = center.z() - REFERENCE_RING; cz <= center.z() + REFERENCE_RING; cz++) {
            for (int cx = center.x() - REFERENCE_RING; cx <= center.x() + REFERENCE_RING; cx++) {
                if (!region.hasChunk(cx, cz)) continue;
                var referenceChunk = region.getChunk(cx, cz,
                        ChunkStatus.STRUCTURE_REFERENCES, false);
                for (int index = 0; index < active.size(); index++) {
                    ActiveSpec item = active.get(index);
                    for (long reference : referenceChunk.getReferencesForStructure(item.structure())) {
                        int startX = ChunkPos.getX(reference);
                        int startZ = ChunkPos.getZ(reference);
                        if (Math.abs(startX - center.x()) > MAX_START_CHUNK_DISTANCE
                                || Math.abs(startZ - center.z()) > MAX_START_CHUNK_DISTANCE
                                || !region.hasChunk(startX, startZ)
                                || !seen.add(new StartReference(index, startX, startZ))) continue;
                        StructureStart start = region.getChunk(startX, startZ,
                                ChunkStatus.STRUCTURE_STARTS, false)
                                .getStartForStructure(item.structure());
                        if (start == null || !start.isValid()) continue;
                        PoolElementStructurePiece root = activeRoot(start, item.spec().root());
                        if (root != null) {
                            changed |= trimCenterChunk(region, center,
                                    start, root, item.spec(), protectedPieces);
                            if (item.spec().name().equals("fam")) {
                                changed |= FamArrivalGrade.applyCenterChunk(region,
                                        center, start, root, protectedPieces);
                            }
                            if (item.spec().name().startsWith("branch")
                                    && branchGradeClearOfProtectedPieces(center, root,
                                            protectedPieces)) {
                                changed |= BranchArrivalGrade.applyCenterChunk(region,
                                        center, start, root);
                            }
                        }
                    }
                }
            }
        }
        return changed;
    }

    private static PoolElementStructurePiece activeRoot(StructureStart start, Identifier root) {
        // A legacy start with the same structure ID cannot acquire this halo.
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof PoolElementStructurePiece pooled
                    && pooled.getElement() instanceof SinglePoolElement single
                    && isActiveRoot(single.getTemplateLocation(), root)) return pooled;
        }
        return null;
    }

    static boolean isActiveRoot(Identifier saved, Identifier expected) {
        return expected.equals(saved);
    }

    private static List<BoundingBox> protectedPieces(WorldGenRegion region, ChunkPos center) {
        var references = region.getChunk(center.x(), center.z(),
                ChunkStatus.STRUCTURE_REFERENCES, false).getAllReferences();
        List<BoundingBox> boxes = new ArrayList<>();
        Set<ReferencedStart> seen = new HashSet<>();
        for (var entry : references.entrySet()) {
            Structure structure = entry.getKey();
            for (long reference : entry.getValue()) {
                int startX = ChunkPos.getX(reference);
                int startZ = ChunkPos.getZ(reference);
                if (Math.abs(startX - center.x()) > MAX_START_CHUNK_DISTANCE
                        || Math.abs(startZ - center.z()) > MAX_START_CHUNK_DISTANCE
                        || !region.hasChunk(startX, startZ)) return null;
                if (!seen.add(new ReferencedStart(structure, startX, startZ))) continue;
                StructureStart start = region.getChunk(startX, startZ,
                        ChunkStatus.STRUCTURE_STARTS, false)
                        .getStartForStructure(structure);
                if (start == null || !start.isValid()) return null;
                for (StructurePiece piece : start.getPieces()) {
                    boxes.add(piece.getBoundingBox());
                }
            }
        }
        return boxes;
    }

    private static boolean trimCenterChunk(WorldGenRegion region, ChunkPos center,
            StructureStart start, PoolElementStructurePiece root, Spec spec,
            List<BoundingBox> protectedPieces) {
        BoundingBox box = root.getBoundingBox();
        Direction outward = root.getRotation().rotate(spec.localOutward());
        Direction lateral = root.getRotation().rotate(spec.localLateral());
        if (spec.name().startsWith("othman") || spec.name().startsWith("branch")) {
            return clearContainedTrees(region, center, start, spec, box,
                    outward, lateral, protectedPieces);
        }
        boolean changed = false;
        for (int x = center.getMinBlockX(); x < center.getMinBlockX() + 16; x++) {
            for (int z = center.getMinBlockZ(); z < center.getMinBlockZ() + 16; z++) {
                if (insideAnyPiece(start, x, z)
                        || insideProtectedPiece(protectedPieces, x, z)) continue;
                MaskPoint mask = maskPoint(spec, box, outward, lateral, x, z);
                if (mask == null) continue;
                int top = region.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                int ground = naturalGround(region, x, top, z);
                if (ground < box.minY() - spec.maxGroundDrop()
                        || ground > box.minY() + spec.maxGroundRise()) continue;
                int from = Math.max(ground + 1, box.minY() + spec.minRootY());
                int through = Math.min(Math.min(ground + spec.maxTreeHeight(),
                        box.minY() + spec.maxRootY()), region.getMaxY() - 1);
                for (int y = from; y <= through; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = region.getBlockState(pos);
                    boolean vanilla = BuiltInRegistries.BLOCK.getKey(state.getBlock())
                            .getNamespace().equals("minecraft");
                    boolean leaf = vanilla && state.is(BlockTags.LEAVES)
                            && state.getBlock() instanceof LeavesBlock
                            && !state.getValue(LeavesBlock.PERSISTENT);
                    boolean trunk = spec.logHalfWidth() >= 0
                            && Math.abs(mask.lateral()) <= spec.logHalfWidth()
                            && vanilla && state.is(BlockTags.LOGS);
                    if ((leaf || trunk) && region.getBlockEntity(pos) == null
                            && region.ensureCanWrite(pos)) {
                        region.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    /** A mask hit may clear a whole tree, but never a partial crown or trunk. */
    private static boolean clearContainedTrees(WorldGenRegion region, ChunkPos center,
            StructureStart start, Spec spec, BoundingBox box, Direction outward,
            Direction lateral, List<BoundingBox> protectedPieces) {
        Map<BlockPos, BlockState> cells = new HashMap<>();
        Set<BlockPos> maskHits = new HashSet<>();
        for (int x = center.getMinBlockX(); x < center.getMinBlockX() + 16; x++) {
            for (int z = center.getMinBlockZ(); z < center.getMinBlockZ() + 16; z++) {
                if (insideAnyPiece(start, x, z)
                        || insideProtectedPiece(protectedPieces, x, z)) continue;
                boolean inMask = maskPoint(spec, box, outward, lateral, x, z) != null;
                int top = region.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                int ground = naturalGround(region, x, top, z);
                if (ground < box.minY() - spec.maxGroundDrop()
                        || ground > box.minY() + spec.maxGroundRise()) continue;
                // Scan from the natural ground through the complete tree height.
                // The narrow sight mask is only a trigger; its old root Y cap
                // stopped at y=74 in seed B and left the y=75 crown above it.
                int from = ground + 1;
                int through = Math.min(ground + spec.maxTreeHeight(), region.getMaxY() - 1);
                for (int y = from; y <= through; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = region.getBlockState(pos);
                    if (isTreeMaterial(state)) {
                        cells.put(pos, state);
                        if (inMask) maskHits.add(pos);
                    }
                }
            }
        }

        boolean changed = false;
        Set<BlockPos> visited = new HashSet<>();
        for (BlockPos first : cells.keySet()) {
            if (!visited.add(first)) continue;
            Set<BlockPos> component = new HashSet<>();
            ArrayDeque<BlockPos> pending = new ArrayDeque<>();
            pending.add(first);
            boolean hasLog = false;
            boolean safe = true;
            while (!pending.isEmpty()) {
                BlockPos pos = pending.removeFirst();
                component.add(pos);
                BlockState state = cells.get(pos);
                hasLog |= state.is(BlockTags.LOGS);
                safe &= isNaturalTreeCell(state) && region.getBlockEntity(pos) == null
                        && region.ensureCanWrite(pos);
                // Include diagonals: an offset crown must not be separated from its trunk.
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            BlockPos neighbor = pos.offset(dx, dy, dz);
                            if (cells.containsKey(neighbor) && visited.add(neighbor)) {
                                pending.add(neighbor);
                            }
                        }
                    }
                }
            }
            if (!safe || !hasLog || component.stream().noneMatch(maskHits::contains)
                    || touchesProtectedTree(component, cells.keySet(),
                    region::getBlockState, pos -> region.getBlockEntity(pos) != null,
                    region.getMinY(), region.getMaxY())) continue;
            for (BlockPos pos : component) {
                region.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                changed = true;
            }
        }
        return changed;
    }

    static boolean touchesProtectedTree(Set<BlockPos> component, Set<BlockPos> candidates,
            Function<BlockPos, BlockState> stateAt, Predicate<BlockPos> hasBlockEntity,
            int minY, int maxY) {
        for (BlockPos pos : component) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos neighbor = pos.offset(dx, dy, dz);
                        if (neighbor.getY() < minY || neighbor.getY() >= maxY
                                || candidates.contains(neighbor)) continue;
                        // A continuation outside the mask/chunk, a persistent crown,
                        // or an adjacent nest vetoes the entire component.
                        if (isTreeMaterial(stateAt.apply(neighbor)) || hasBlockEntity.test(neighbor)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean isTreeMaterial(BlockState state) {
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)
                || state.getBlock() instanceof LeavesBlock;
    }

    static boolean isNaturalTreeCell(BlockState state) {
        if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("minecraft")) {
            return false;
        }
        if (state.is(BlockTags.LOGS)) return true;
        return state.getBlock() instanceof LeavesBlock
                && !state.getValue(LeavesBlock.PERSISTENT);
    }

    private static int naturalGround(WorldGenLevel level, int x, int top, int z) {
        for (int y = top - 1; y >= Math.max(level.getMinY(), top - 32); y--) {
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                    || state.isAir() || state.canBeReplaced()) continue;
            return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)
                    || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL)
                    || state.is(Blocks.STONE) || state.is(Blocks.ANDESITE)
                    || state.is(Blocks.GRAVEL) || state.is(Blocks.MOSS_BLOCK)
                    || state.is(Blocks.TUFF)
                    || state.is(DomainBlocks.FORUM_PLASTER) ? y : Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }

    private static boolean insideAnyPiece(StructureStart start, int x, int z) {
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox box = piece.getBoundingBox();
            if (x >= box.minX() && x <= box.maxX()
                    && z >= box.minZ() && z <= box.maxZ()) return true;
        }
        return false;
    }

    private static boolean insideProtectedPiece(List<BoundingBox> pieces, int x, int z) {
        for (BoundingBox box : pieces) {
            if (x >= box.minX() && x <= box.maxX()
                    && z >= box.minZ() && z <= box.maxZ()) return true;
        }
        return false;
    }

    private static boolean branchGradeClearOfProtectedPieces(ChunkPos center,
            PoolElementStructurePiece root, List<BoundingBox> pieces) {
        boolean hasLocalColumn = false;
        for (BranchArrivalGrade.Column column : BranchArrivalGrade.plan(root.getBoundingBox(),
                root.getRotation().rotate(Direction.SOUTH),
                root.getRotation().rotate(Direction.EAST))) {
            BlockPos pos = column.top();
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            // The grade preflight reads every lane column, including other
            // chunks. Keep every such read in this generation region's 3x3.
            if (Math.abs(chunkX - center.x()) > 1
                    || Math.abs(chunkZ - center.z()) > 1) return false;
            if (chunkX == center.x() && chunkZ == center.z()) {
                hasLocalColumn = true;
                if (insideProtectedPiece(pieces, pos.getX(), pos.getZ())) return false;
            }
        }
        return hasLocalColumn;
    }

    static MaskPoint maskPoint(Spec spec, BoundingBox box, Direction outward,
            Direction lateral, int x, int z) {
        int depth = switch (outward) {
            case SOUTH -> z - box.maxZ();
            case NORTH -> box.minZ() - z;
            case EAST -> x - box.maxX();
            case WEST -> box.minX() - x;
            default -> Integer.MIN_VALUE;
        };
        if (depth < spec.minDepth() || depth > spec.maxDepth()) return null;
        int centerX = (box.minX() + box.maxX()) / 2;
        int centerZ = (box.minZ() + box.maxZ()) / 2;
        int offset = (x - centerX) * lateral.getStepX()
                + (z - centerZ) * lateral.getStepZ() - spec.lateralOffset();
        int halfWidth = depth <= spec.apronDepth()
                ? spec.apronHalfWidth() : spec.corridorHalfWidth();
        return Math.abs(offset) <= halfWidth ? new MaskPoint(depth, offset) : null;
    }

    private static Identifier gems(String path) {
        return Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, path);
    }

    private static ResourceKey<Structure> structure(String path) {
        return ResourceKey.create(Registries.STRUCTURE, gems(path));
    }

    record Spec(String name, ResourceKey<Structure> structure, Identifier root,
                Direction localOutward, Direction localLateral,
                int minDepth, int maxDepth, int apronDepth,
                int apronHalfWidth, int corridorHalfWidth, int lateralOffset,
                int logHalfWidth, int minRootY, int maxRootY,
                int maxTreeHeight, int maxGroundDrop, int maxGroundRise) {
        Spec {
            if (minDepth < 1 || maxDepth < minDepth || maxDepth > MAX_HALO
                    || apronDepth < 0 || apronDepth > maxDepth
                    || apronHalfWidth < 0 || corridorHalfWidth < 0
                    || apronHalfWidth > MAX_HALO || corridorHalfWidth > MAX_HALO
                    || maxTreeHeight < 1 || maxTreeHeight > 26
                    || minRootY < -16 || minRootY > maxRootY || maxRootY > 26
                    || maxGroundDrop < 0 || maxGroundRise < 0
                    || localOutward.getAxis() == localLateral.getAxis()) {
                throw new IllegalArgumentException("Unbounded district canopy mask: " + name);
            }
        }
    }

    record MaskPoint(int depth, int lateral) {}
    private record ActiveSpec(Spec spec, Structure structure) {}
    private record StartReference(int specIndex, int x, int z) {}
    private record ReferencedStart(Structure structure, int x, int z) {}
}
