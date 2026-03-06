package com.feel.gems.adventure.worldgen.site;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Read-only proof from the actual world. Implementations must never load chunks. */
public interface SiteAccess {
    enum Inspection { CLEAR, CONFLICT, UNKNOWN }
    enum Seam { WORLDGEN_REGION, GUARDED_NEW_CHUNKS }

    /** One column covering the exact planned floor, roof, and nearby hazard envelope. */
    record Column(int floorY, int waterDepth, int solidSupportDepth,
                  int roofThickness, int hazardDistance) {
        public Column {
            if (waterDepth < 0 || solidSupportDepth < 0 || roofThickness < 0
                    || hazardDistance < 0) {
                throw new IllegalArgumentException("Unproven terrain column");
            }
        }
    }

    Seam seam();
    ResourceKey<Level> dimension();
    int minY();
    int maxY();
    boolean canInspectChunk(ChunkPos chunk);
    boolean isNewChunk(ChunkPos chunk);
    boolean canWrite(BlockPos pos);
    Inspection protectedVolume(BoundingBox bounds);
    boolean biomeMatches(BlockPos pos, Set<ResourceKey<Biome>> allowed);
    /** Return null when a complete column, including its hazards, cannot be proven. */
    Column column(int x, int z, int anchorY, SiteDefinition definition);
    BlockState state(BlockPos pos);
    boolean hasBlockEntity(BlockPos pos);
    boolean isNaturalTerrain(BlockState state);
}
