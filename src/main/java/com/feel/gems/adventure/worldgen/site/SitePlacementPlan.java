package com.feel.gems.adventure.worldgen.site;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

/** Exact proposed writes and reads; no dynamic choice is allowed after preflight. */
public record SitePlacementPlan(Identifier siteId, int version,
                                Map<BlockPos, Write> writes, List<Requirement> requirements) {
    public enum Replace { AIR, WATER, LAVA, NATURAL, EXACT }
    public enum Require { AIR, WATER, LAVA, DRY_SOLID, EXACT }

    public record Write(BlockState state, Replace replace, BlockState expected) {
        public Write {
            if (state == null || replace == null
                    || (replace == Replace.EXACT) != (expected != null)) {
                throw new IllegalArgumentException("Invalid site write");
            }
        }
    }

    public record Requirement(BlockPos pos, Require condition, BlockState expected) {
        public Requirement {
            if (pos == null || condition == null
                    || (condition == Require.EXACT) != (expected != null)) {
                throw new IllegalArgumentException("Invalid site requirement");
            }
            pos = pos.immutable();
        }
    }

    public SitePlacementPlan {
        if (siteId == null || version < 1 || writes == null || requirements == null) {
            throw new IllegalArgumentException("Invalid site write plan");
        }
        Map<BlockPos, Write> copy = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, Write> entry : writes.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("Null site write");
            }
            Write previous = copy.putIfAbsent(entry.getKey().immutable(), entry.getValue());
            if (previous != null && !previous.equals(entry.getValue())) {
                throw new IllegalArgumentException("Conflicting site write at " + entry.getKey());
            }
        }
        writes = Map.copyOf(copy);
        requirements = List.copyOf(requirements);
        for (Requirement requirement : requirements) {
            if (writes.containsKey(requirement.pos())) {
                throw new IllegalArgumentException("Read requirement overlaps a site write at "
                        + requirement.pos());
            }
        }
    }
}
