package com.feel.gems.adventure.worldgen;

import com.feel.gems.GemsMod;
import com.mojang.serialization.MapCodec;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.BlockPos;
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

/** A narrow, late tree line around newly selected Framefall buildings. */
public final class FramefallCanopyTransitionFeature implements Feature {
    public static final MapCodec<FramefallCanopyTransitionFeature> CODEC =
            MapCodec.unit(new FramefallCanopyTransitionFeature());
    private static final Identifier ID = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID,
            "framefall_canopy_transition");
    private static final ResourceKey<Structure> GALLERY = ResourceKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "framefall_gallery"));
    private static final ResourceKey<PlacedFeature> PLACED = ResourceKey.create(Registries.PLACED_FEATURE, ID);
    private static final Identifier GALLERY_ROOT = Identifier.fromNamespaceAndPath(
            GemsMod.MOD_ID, "framefall_gallery_v6");
    private static final Set<Identifier> NEW_PIECES = Set.of(
            Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "framefall_gallery_v6"),
            Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "framefall_atelier_v6"),
            Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "framefall_promenade_v6"));
    private static final int RADIUS = 10;
    private static final int MAX_TREE_HEIGHT = 26;
    private static final int MAX_START_CHUNK_DISTANCE = 8;

    private FramefallCanopyTransitionFeature() { }

    public static void init() {
        Registry.register(BuiltInRegistries.FEATURE_TYPE, ID, CODEC);
        // Runs after trees in every Overworld biome, including a forest beside
        // Framefall Ravine. No feature is added to old, already full chunks.
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld()
                        .or(BiomeSelectors.includeByKey(AdventureBiomes.FRAMEFALL_RAVINE)),
                GenerationStep.Decoration.TOP_LAYER_MODIFICATION, PLACED);
    }

    @Override
    public MapCodec<FramefallCanopyTransitionFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random,
                         BlockPos origin) {
        if (!(level instanceof WorldGenRegion region)) return false;
        Structure gallery = region.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(GALLERY);
        if (gallery == null) return false;

        ChunkPos center = region.getCenter();
        Set<ChunkPos> seen = new HashSet<>();
        boolean changed = false;
        // A piece at most ten blocks from this chunk intersects this chunk or
        // one of its eight immediate neighbours. Read references at the earlier
        // structure status. References may name starts well outside that ring:
        // StructureManager.startsForStructure would fetch those remote chunks.
        // FEATURES explicitly depends on STRUCTURE_STARTS out to eight chunks
        // in 26.3. Read only that cached metadata, never remote terrain or a
        // ServerLevel chunk; the 3x3 reference ring covers the ten-block halo.
        for (int cz = center.z() - 1; cz <= center.z() + 1; cz++) {
            for (int cx = center.x() - 1; cx <= center.x() + 1; cx++) {
                if (!region.hasChunk(cx, cz)) continue;
                var references = region.getChunk(cx, cz, ChunkStatus.STRUCTURE_REFERENCES, false)
                        .getReferencesForStructure(gallery);
                for (long reference : references) {
                    int startX = ChunkPos.getX(reference);
                    int startZ = ChunkPos.getZ(reference);
                    if (Math.abs(startX - center.x()) > MAX_START_CHUNK_DISTANCE
                            || Math.abs(startZ - center.z()) > MAX_START_CHUNK_DISTANCE
                            || !region.hasChunk(startX, startZ)) continue;
                    ChunkPos startPos = new ChunkPos(startX, startZ);
                    if (!seen.add(startPos)) continue;
                    StructureStart start = region.getChunk(startX, startZ, ChunkStatus.STRUCTURE_STARTS, false)
                            .getStartForStructure(gallery);
                    if (start == null || !start.isValid() || !usesNewGallery(start)) continue;
                    changed |= trimChunk(level, center, start);
                }
            }
        }
        return changed;
    }

    private static boolean usesNewGallery(StructureStart start) {
        // Require the current root, not just the public structure ID.
        return start.getPieces().stream().anyMatch(piece -> GALLERY_ROOT.equals(template(piece)));
    }

    private static Identifier template(StructurePiece piece) {
        if (piece instanceof PoolElementStructurePiece pooled
                && pooled.getElement() instanceof SinglePoolElement single) {
            return single.getTemplateLocation();
        }
        return null;
    }

    private static boolean trimChunk(WorldGenLevel level, ChunkPos chunk, StructureStart start) {
        boolean changed = false;
        for (int x = chunk.getMinBlockX(); x < chunk.getMinBlockX() + 16; x++) {
            for (int z = chunk.getMinBlockZ(); z < chunk.getMinBlockZ() + 16; z++) {
                if (insideAnyPiece(start, x, z)) continue;
                int distance = RADIUS + 1;
                BoundingBox nearest = null;
                for (StructurePiece piece : start.getPieces()) {
                    Identifier id = template(piece);
                    if (id == null || !NEW_PIECES.contains(id)) continue;
                    int candidate = distance(piece.getBoundingBox(), x, z);
                    if (candidate < distance) {
                        distance = candidate;
                        nearest = piece.getBoundingBox();
                    }
                }
                if (nearest == null || distance < 1 || distance > RADIUS) continue;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                int ground = naturalGround(level, x, top, z);
                if (ground < nearest.minY() - 12 || ground > nearest.maxY() + 8) continue;
                // An inner clear line opens the wall and approaches. The outer
                // six blocks thin by coordinate hash so the edge is not a box.
                if (distance > 4 && Math.floorMod(hash(x, z), RADIUS - 3) < distance - 4) continue;
                int ceiling = Math.min(Math.min(top, ground + MAX_TREE_HEIGHT),
                        Math.min(nearest.maxY() + 8, level.getMaxY() - 1));
                for (int y = ground + 1; y <= ceiling; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if ((state.is(BlockTags.LEAVES) || distance <= 4 && state.is(BlockTags.LOGS))
                            && level.ensureCanWrite(pos)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                        changed = true;
                    }
                }
                // A few original pale chips soften the foot of the new wall.
                if (distance <= 5 && Math.floorMod(hash(z, x), 7) == 0) {
                    BlockPos floor = new BlockPos(x, ground, z);
                    if (level.ensureCanWrite(floor)) {
                        level.setBlock(floor, Blocks.CALCITE.defaultBlockState(), 2);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private static int naturalGround(WorldGenLevel level, int x, int top, int z) {
        for (int y = top - 1; y >= Math.max(level.getMinY(), top - 32); y--) {
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                    || state.isAir() || state.canBeReplaced()) continue;
            return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)
                    || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL)
                    || state.is(Blocks.STONE) || state.is(Blocks.ANDESITE)
                    || state.is(Blocks.GRAVEL) || state.is(Blocks.MOSS_BLOCK) ? y : Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }

    private static boolean insideAnyPiece(StructureStart start, int x, int z) {
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox box = piece.getBoundingBox();
            if (x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ()) return true;
        }
        return false;
    }

    private static int distance(BoundingBox box, int x, int z) {
        int dx = Math.max(0, Math.max(box.minX() - x, x - box.maxX()));
        int dz = Math.max(0, Math.max(box.minZ() - z, z - box.maxZ()));
        return Math.max(dx, dz);
    }

    private static int hash(int x, int z) {
        int value = x * 0x9E3779B9 ^ z * 0x85EBCA6B;
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        return value ^ value >>> 15;
    }
}
