package com.feel.gems.adventure.undercard;

import com.feel.gems.GemsMod;
import com.feel.gems.adventure.worldgen.site.GuardedSiteScheduler;
import com.feel.gems.adventure.worldgen.site.SiteCandidate;
import com.feel.gems.adventure.worldgen.site.SiteDefinition;
import com.feel.gems.adventure.worldgen.site.SitePlacementPlan;
import com.feel.gems.adventure.worldgen.site.SiteVolume;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;

/** Inactive v1 placement contract, anchored at the saved center roof marker. */
public final class UndercardSite {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "undercard");
    public static final Identifier TEMPLATE = Identifier.fromNamespaceAndPath(
            GemsMod.MOD_ID, "undercard_ring_v1");
    public static final int VERSION = 1;
    public static final int HAZARD_HALO = 128;

    private UndercardSite() { }

    /** The root marker is local (23,22,23), invariant under quarter turns. */
    public static SiteDefinition definition() {
        return new SiteDefinition(ID, VERSION, Level.OVERWORLD, Set.of(Biomes.BADLANDS),
                SiteDefinition.Setting.UNDERGROUND, 96, 64, 48, 86, 4,
                0, 4, 8, HAZARD_HALO, List.of(
                    new SiteVolume(SiteVolume.Use.BUILD, -21, -22, -21, 21, 1, 14),
                    new SiteVolume(SiteVolume.Use.BUILD, -4, -19, 11, 4, 22, 23),
                    new SiteVolume(SiteVolume.Use.APPROACH, -2, 10, 14, 2, 14, 17),
                    new SiteVolume(SiteVolume.Use.CLEARANCE, -23, -24, -23, 23, 4, 16)));
    }

    /** One requirement verifies the immutable instance marker before opening. */
    public static SitePlacementPlan gatePlan(SiteCandidate candidate) {
        return new SitePlacementPlan(ID, VERSION, Map.of(), List.of(
                new SitePlacementPlan.Requirement(candidate.anchor(),
                        SitePlacementPlan.Require.EXACT, UndercardBlocks.ROOT.defaultBlockState())));
    }

    /** The scheduler may replace this one sealed cell only after full preflight. */
    public static GuardedSiteScheduler.Gate gate(SiteCandidate candidate) {
        int turns = candidate.quarterTurns();
        int x = switch (turns) { case 1 -> -20; case 3 -> 20; default -> 0; };
        int z = switch (turns) { case 0 -> 20; case 2 -> -20; default -> 0; };
        Direction facing = switch (turns) {
            case 0 -> Direction.NORTH;
            case 1 -> Direction.EAST;
            case 2 -> Direction.SOUTH;
            default -> Direction.WEST;
        };
        return new GuardedSiteScheduler.Gate(candidate.anchor().offset(x, 11, z),
                UndercardBlocks.GATE.defaultBlockState(),
                Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing));
    }
}
