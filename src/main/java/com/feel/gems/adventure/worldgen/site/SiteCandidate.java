package com.feel.gems.adventure.worldgen.site;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** A fixed orientation and anchor; no subsequent random draw may move its pieces. */
public record SiteCandidate(BlockPos anchor, int quarterTurns) {
    public SiteCandidate {
        if (anchor == null || quarterTurns < 0 || quarterTurns > 3) {
            throw new IllegalArgumentException("Invalid site candidate");
        }
        anchor = anchor.immutable();
    }

    public List<BoundingBox> bounds(SiteDefinition definition) {
        List<BoundingBox> result = new ArrayList<>(definition.volumes().size());
        for (SiteVolume volume : definition.volumes()) {
            result.add(volume.at(anchor, quarterTurns));
        }
        return List.copyOf(result);
    }

    public List<ChunkPos> chunks(SiteDefinition definition) {
        Set<Long> keys = new TreeSet<>();
        for (BoundingBox box : bounds(definition)) {
            for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
                for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                    keys.add(ChunkPos.pack(x, z));
                }
            }
        }
        return keys.stream().map(key -> new ChunkPos(ChunkPos.getX(key), ChunkPos.getZ(key)))
                .toList();
    }
}
