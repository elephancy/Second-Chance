package com.feel.gems.adventure.worldgen.site;

import com.feel.gems.GemsMod;
import com.mojang.serialization.Codec;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Actual-state adapter for a server-thread scheduler. It reads only already
 * loaded chunks with this mod's new-generation stamp. Old saves fail closed.
 */
public class GuardedNewChunkSiteAccess implements SiteAccess {
    private static final String STAMP_VERSION = "site-v1";
    private static final AttachmentType<String> NEW_CHUNK = AttachmentRegistry.createPersistent(
            Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "site_new_chunk_v1"), Codec.STRING);
    private static final Map<ServerLevel, String> GENERATION_SESSIONS =
            new ConcurrentHashMap<>();
    private static boolean initialized;

    private final ServerLevel level;
    private final Set<Structure> currentSiteStarts;

    public GuardedNewChunkSiteAccess(ServerLevel level, Set<Structure> currentSiteStarts) {
        this.level = level;
        this.currentSiteStarts = Set.copyOf(currentSiteStarts);
    }

    /** Call during mod initialization, before natural chunks are generated. */
    public static void init() {
        if (initialized) return;
        initialized = true;
        // Fabric fires CHUNK_LOAD before CHUNK_GENERATE when a generated chunk
        // first becomes FULL. Stamp it at the earlier callback so a save or a
        // later listener cannot observe a FULL chunk without its provenance.
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, newlyGenerated) -> {
            if (newlyGenerated && chunk.getAttached(NEW_CHUNK) == null)
                chunk.setAttached(NEW_CHUNK, generationStamp(level));
        });
        // Shutdown can still save or finish already scheduled generation.
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> GENERATION_SESSIONS.clear());
    }

    private static String generationStamp(ServerLevel level) {
        return GENERATION_SESSIONS.computeIfAbsent(level,
                unused -> STAMP_VERSION + ":" + UUID.randomUUID());
    }

    @Override
    public Seam seam() { return Seam.GUARDED_NEW_CHUNKS; }

    @Override
    public ResourceKey<Level> dimension() { return level.dimension(); }

    @Override
    public int minY() { return level.getMinY(); }

    @Override
    public int maxY() { return level.getMaxY(); }

    protected LevelChunk loadedChunk(int x, int z) {
        return level.getChunkSource().getChunkNow(x, z);
    }

    @Override
    public boolean canInspectChunk(ChunkPos chunk) {
        return loadedChunk(chunk.x(), chunk.z()) != null;
    }

    @Override
    public boolean isNewChunk(ChunkPos chunk) {
        LevelChunk loaded = loadedChunk(chunk.x(), chunk.z());
        if (loaded == null) return false;
        String stamp = loaded.getAttached(NEW_CHUNK);
        return stamp != null && (STAMP_VERSION.equals(stamp)
                || stamp.startsWith(STAMP_VERSION + ":"));
    }

    /** True only while this exact chunk is still in the server run that generated it. */
    public boolean generatedThisSession(ChunkPos chunk) {
        LevelChunk loaded = loadedChunk(chunk.x(), chunk.z());
        return loaded != null && generationStamp(level).equals(loaded.getAttached(NEW_CHUNK));
    }

    @Override
    public boolean canWrite(BlockPos pos) {
        return isNewChunk(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4));
    }

    @Override
    public Inspection protectedVolume(BoundingBox bounds) {
        for (int cx = bounds.minX() >> 4; cx <= bounds.maxX() >> 4; cx++) {
            for (int cz = bounds.minZ() >> 4; cz <= bounds.maxZ() >> 4; cz++) {
                LevelChunk chunk = loadedChunk(cx, cz);
                if (chunk == null) {
                    traceUnknown("volume chunk unavailable", new ChunkPos(cx, cz), null);
                    return Inspection.UNKNOWN;
                }
                for (var entry : chunk.getAllReferences().entrySet()) {
                    if (currentSiteStarts.contains(entry.getKey())) continue;
                    for (long reference : entry.getValue()) {
                        LevelChunk startChunk = loadedChunk(ChunkPos.getX(reference),
                                ChunkPos.getZ(reference));
                        if (startChunk == null) {
                            traceUnknown("referenced start chunk unavailable",
                                    new ChunkPos(ChunkPos.getX(reference),
                                            ChunkPos.getZ(reference)), entry.getKey());
                            return Inspection.UNKNOWN;
                        }
                        StructureStart start = startChunk.getStartForStructure(entry.getKey());
                        if (start == null || !start.isValid()) {
                            traceUnknown("referenced start missing",
                                    startChunk.getPos(), entry.getKey());
                            return Inspection.UNKNOWN;
                        }
                        for (StructurePiece piece : start.getPieces()) {
                            if (piece.getBoundingBox().intersects(bounds)) return Inspection.CONFLICT;
                        }
                    }
                }
            }
        }
        return Inspection.CLEAR;
    }

    private static void traceUnknown(String reason, ChunkPos chunk, Structure structure) {
        if (Boolean.getBoolean("gems.fourRoadsEstateGate"))
            System.out.println("FOUR_ROADS_ESTATE_PROTECTION_UNKNOWN reason=" + reason
                    + " chunk=" + chunk + " structure=" + structure);
    }

    @Override
    public boolean biomeMatches(BlockPos pos, Set<ResourceKey<Biome>> allowed) {
        if (loadedChunk(pos.getX() >> 4, pos.getZ() >> 4) == null) return false;
        for (ResourceKey<Biome> biome : allowed) {
            if (level.getBiome(pos).is(biome)) return true;
        }
        return false;
    }

    @Override
    public Column column(int x, int z, int anchorY, SiteDefinition definition) {
        LevelChunk chunk = loadedChunk(x >> 4, z >> 4);
        if (chunk == null) return null;
        int floorY;
        // LevelChunk.getHeight already returns the top block Y in 26.3;
        // ServerLevel.getHeight returns the first free Y instead.
        if (definition.setting() == SiteDefinition.Setting.SURFACE) {
            floorY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    x & 15, z & 15);
        } else if (definition.setting() == SiteDefinition.Setting.OCEAN) {
            floorY = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR,
                    x & 15, z & 15);
        } else {
            floorY = anchorY - 1;
        }
        if (floorY < level.getMinY() || floorY >= level.getMaxY()) return null;
        int support = 0;
        for (int depth = 0; depth < definition.minSupportDepth(); depth++) {
            int y = floorY - depth;
            if (y < level.getMinY()) break;
            BlockState state = chunk.getBlockState(new BlockPos(x, y, z));
            if (!state.isSolid() || !state.getFluidState().isEmpty()) break;
            support++;
        }
        int water = 0;
        if (definition.setting() == SiteDefinition.Setting.OCEAN) {
            for (int rise = 1; rise <= definition.minWaterDepth(); rise++) {
                int y = floorY + rise;
                if (y >= level.getMaxY()
                        || !chunk.getBlockState(new BlockPos(x, y, z)).is(Blocks.WATER)) break;
                water++;
            }
        }
        int roof = 0;
        if (definition.minRoofThickness() > 0) {
            int top = anchorY + definition.volumes().stream()
                    .filter(volume -> volume.use() == SiteVolume.Use.BUILD)
                    .mapToInt(SiteVolume::maxY).max().orElse(0);
            for (int rise = 1; rise <= definition.minRoofThickness(); rise++) {
                int y = top + rise;
                if (y >= level.getMaxY()) break;
                BlockState state = chunk.getBlockState(new BlockPos(x, y, z));
                if (!state.isSolid() || !state.getFluidState().isEmpty()) break;
                roof++;
            }
        }
        // A site's hazard proof must be supplied by a specialist adapter.
        // This generic adapter never assumes deep-dark, lava, or void safety.
        return new Column(floorY, water, support, roof, 0);
    }

    @Override
    public BlockState state(BlockPos pos) {
        LevelChunk chunk = loadedChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) throw new IllegalStateException("Unloaded site cell");
        return chunk.getBlockState(pos);
    }

    @Override
    public boolean hasBlockEntity(BlockPos pos) {
        LevelChunk chunk = loadedChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return true;
        return chunk.getBlockEntity(pos) != null;
    }

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
