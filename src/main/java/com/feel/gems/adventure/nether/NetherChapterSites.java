package com.feel.gems.adventure.nether;

import com.feel.gems.GemsMod;
import com.feel.gems.adventure.worldgen.site.GuardedSiteScheduler;
import com.feel.gems.adventure.worldgen.site.SiteAccess;
import com.feel.gems.adventure.worldgen.site.SiteCandidate;
import com.feel.gems.adventure.worldgen.site.SiteDefinition;
import com.feel.gems.adventure.worldgen.site.SitePlacementPlan;
import com.feel.gems.adventure.worldgen.site.SitePreflight;
import com.feel.gems.adventure.worldgen.site.SiteVolume;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

/** Complete rotated bounds and read-only safety plan for the Nether sites. */
public final class NetherChapterSites {
    public enum Site { ASH_ANNEX, EMBER_BRIDGE }

    // V3 owns eight more south-facing blocks for a built, vegetation-free
    // arrival arcade. The natural walk continues outside it at Z55..62.
    private static final SiteVolume FULL_BODY_V3 = new SiteVolume(
            SiteVolume.Use.BUILD, -23, -4, -23, 23, 26, 31);
    private static final SiteVolume ANNEX_APPROACH_V3 = new SiteVolume(
            SiteVolume.Use.APPROACH, -7, -3, 24, 7, 5, 39);
    // V5 owns the whole fifteen-wide gallery through local Z70. Natural
    // terrain beyond its threshold remains read-only and must support a walk.
    private static final SiteVolume FULL_BODY_BRIDGE_V5 = new SiteVolume(
            SiteVolume.Use.BUILD, -23, -4, -23, 23, 26, 47);
    private static final SiteVolume BRIDGE_APPROACH_V5 = new SiteVolume(
            SiteVolume.Use.APPROACH, 9, -3, 24, 23, 5, 63);

    public static final SiteDefinition ASH_ANNEX_V5 = new SiteDefinition(
            id("lastlight_ash_annex_v5"), 5, Level.NETHER,
            Set.of(biome("nether_wastes")), SiteDefinition.Setting.NETHER,
            36, 20, 44, 84, 0, 0, 0, 0, 1,
            List.of(FULL_BODY_V3, ANNEX_APPROACH_V3));
    public static final SiteDefinition EMBER_BRIDGE_V6 = new SiteDefinition(
            id("redline_ember_bridge_v6"), 6, Level.NETHER,
            Set.of(biome("warped_forest"), biome("nether_wastes"),
                    biome("crimson_forest"), biome("soul_sand_valley"),
                    biome("basalt_deltas")), SiteDefinition.Setting.NETHER,
            24, 12, 33, 84, 0, 0, 0, 0, 1,
            List.of(FULL_BODY_BRIDGE_V5, BRIDGE_APPROACH_V5));

    private NetherChapterSites() { }

    public static SiteDefinition definition(Site site) {
        return site == Site.ASH_ANNEX ? ASH_ANNEX_V5 : EMBER_BRIDGE_V6;
    }

    public static Block rootBlock(Site site) {
        return site == Site.ASH_ANNEX ? NetherChapterBlocks.ANNEX_ROOT
                : NetherChapterBlocks.BRIDGE_ROOT;
    }

    public static Block gateBlock(Site site) {
        return site == Site.ASH_ANNEX ? NetherChapterBlocks.ANNEX_GATE
                : NetherChapterBlocks.BRIDGE_GATE;
    }

    /** Root marker faces north in the NBT and rotates with the structure. */
    public static SiteCandidate candidate(BlockPos root, Direction facing) {
        int turns = switch (facing) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> throw new IllegalArgumentException("Horizontal root facing required");
        };
        return new SiteCandidate(root, turns);
    }

    public static BlockPos at(SiteCandidate candidate, int x, int y, int z) {
        int dx = x - 23;
        int dz = z - 23;
        int rx;
        int rz;
        switch (candidate.quarterTurns()) {
            case 0 -> { rx = dx; rz = dz; }
            case 1 -> { rx = -dz; rz = dx; }
            case 2 -> { rx = -dx; rz = -dz; }
            default -> { rx = dz; rz = -dx; }
        }
        return candidate.anchor().offset(rx, y - 4, rz);
    }

    /** Air-only gate plan; the authored body is placed by structure generation. */
    public static SitePlacementPlan gatePlan(Site site, SiteCandidate candidate) {
        return gatePlan(site, candidate, definition(site));
    }

    public static SitePlacementPlan gatePlan(Site site, SiteCandidate candidate,
                                             SiteDefinition definition) {
        List<SitePlacementPlan.Requirement> required = new ArrayList<>();
        Direction facing = switch (candidate.quarterTurns()) {
            case 0 -> Direction.NORTH;
            case 1 -> Direction.EAST;
            case 2 -> Direction.SOUTH;
            default -> Direction.WEST;
        };
        required.add(new SitePlacementPlan.Requirement(candidate.anchor(),
                SitePlacementPlan.Require.EXACT,
                rootBlock(site).defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,
                        facing)));
        if (site == Site.ASH_ANNEX) {
            require(required, candidate, 6, 5, 14, NetherChapterBlocks.ANNEX_REGISTER);
            require(required, candidate, 39, 5, 14, NetherChapterBlocks.ANNEX_SIGNAL);
            require(required, candidate, 23, 5, 8, NetherChapterBlocks.ANNEX_PLATFORM);
            require(required, candidate, 21, 5, 31, NetherChapterBlocks.ANNEX_BOARD_SHELTER);
            require(required, candidate, 23, 5, 31, NetherChapterBlocks.ANNEX_BOARD_TRANSFER);
            require(required, candidate, 25, 5, 31, NetherChapterBlocks.ANNEX_BOARD_RETURN);
        } else {
            require(required, candidate, 39, 5, 23, NetherChapterBlocks.BRIDGE_EAST_SHELTER);
            require(required, candidate, 7, 5, 23, NetherChapterBlocks.BRIDGE_WEST_SHELTER);
            require(required, candidate, 37, 5, 23, NetherChapterBlocks.BRIDGE_START);
            require(required, candidate, 23, 5, 23, NetherChapterBlocks.BRIDGE_MIDPOINT);
            require(required, candidate, 9, 5, 23, NetherChapterBlocks.BRIDGE_FINISH);
            require(required, candidate, 39, 20, 70, Blocks.SHROOMLIGHT);
            require(required, candidate, 33, 20, 70, Blocks.SHROOMLIGHT);
        }
        return new SitePlacementPlan(definition.id(), definition.version(),
                Map.of(), required);
    }

    public static List<GuardedSiteScheduler.Gate> sealedGates(Site site,
                                                               SiteCandidate candidate) {
        List<GuardedSiteScheduler.Gate> gates = new ArrayList<>(6);
        int x = site == Site.ASH_ANNEX ? 23 : 39;
        int z = site == Site.ASH_ANNEX ? 43 : 40;
        for (int dx = -1; dx <= 1; dx++) {
            for (int y = 5; y <= 6; y++) {
                gates.add(new GuardedSiteScheduler.Gate(at(candidate, x + dx, y, z),
                        gateBlock(site).defaultBlockState(), Blocks.AIR.defaultBlockState()));
            }
        }
        return List.copyOf(gates);
    }

    /**
     * The shared preflight checks all declared chunks, biome columns, other
     * structure references, root/controls and version. The specialist route
     * check below verifies the known Nether hazards. This method never writes.
     * Natural activation also needs generation-time collision and safe return
     * proofs; callers must not open the gates on this result alone.
     */
    public static SitePreflight.Result preflight(Site site, SiteCandidate candidate,
                                                SiteAccess access) {
        return SitePreflight.check(definition(site), candidate,
                gatePlan(site, candidate), access);
    }

    public static boolean mandatoryPathDry(Site site, SiteCandidate candidate,
                                           SiteAccess access) {
        if (access.dimension() != Level.NETHER) return false;
        if (site == Site.ASH_ANNEX) {
            for (int z = 38; z <= 46; z++) {
                if (z == 43) continue; // Six authored gate cells remain sealed.
                for (int x = 21; x <= 25; x++) {
                    if (!dryWalk(access, at(candidate, x, 4, z))) return false;
                }
            }
            if (!connectedV3Arrival(access, candidate, 23)
                    || !ashPlatformPassageIntact(access, candidate)) return false;
            for (int z = 40; z <= 46; z += 2) {
                if (!solidKnown(access, at(candidate, 23, 12, z))) return false;
            }
            return true;
        }
        for (int x = 10; x <= 36; x++) {
            for (int z = 21; z <= 25; z++) {
                BlockPos floor = at(candidate, x, 4, z);
                if (x == 23 && z == 23) {
                    if (!solidKnown(access, floor) || !known(access, floor.above())
                            || !access.state(floor.above()).is(NetherChapterBlocks.BRIDGE_MIDPOINT)
                            || !known(access, floor.above(2))
                            || !access.state(floor.above(2)).isAir()) return false;
                } else if (!dryWalk(access, floor)) return false;
            }
            if (x >= 11 && x <= 35
                    && (!solidKnown(access, at(candidate, x, 5, 20))
                    || !solidKnown(access, at(candidate, x, 5, 26)))) return false;
        }
        for (int z = 34; z <= 46; z++) {
            if (z == 40) continue;
            if (!dryWalk(access, at(candidate, 39, 4, z))) return false;
        }
        if (!v6AnchoredGalleryIntact(access, candidate)
                || !connectedArrival(access, candidate, 39, 40, 3, true)) return false;
        // The bridge must really cross its contained lava ravine; each bank
        // must support the public floor and the span must keep solid railings.
        for (int x : new int[] {6, 8, 38, 40}) {
            if (!solidKnown(access, at(candidate, x, 3, 23))) return false;
            if (!solidKnown(access, at(candidate, x, 2, 23))) return false;
        }
        return sealedV2Crucible(access, candidate);
    }

    /** V5 opens the formerly buried record at the end of a lit, level passage. */
    private static boolean ashPlatformPassageIntact(SiteAccess access,
                                                    SiteCandidate site) {
        BlockPos control = at(site, 23, 5, 8);
        if (!known(access, control)
                || !access.state(control).is(NetherChapterBlocks.ANNEX_PLATFORM)) return false;
        for (int z = 8; z <= 17; z++) {
            for (int x = 22; x <= 24; x++) {
                if (z == 8 && x == 23) continue;
                if (!dryWalk(access, at(site, x, 4, z))) return false;
                BlockPos roofSpace = at(site, x, 7, z);
                if (!known(access, roofSpace) || !access.state(roofSpace).isAir()) {
                    return false;
                }
            }
        }
        return dryWalk(access, at(site, 23, 4, 18));
    }

    /** V6's trussed span may cross a cave, but four core pads must touch rock. */
    private static boolean v6AnchoredGalleryIntact(SiteAccess access,
                                                    SiteCandidate site) {
        for (int[] center : new int[][] {{6, 14}, {6, 32}, {40, 14}, {40, 32}}) {
            int contacts = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pier = at(site, center[0] + dx, 0,
                            center[1] + dz);
                    if (!known(access, pier)
                            || !access.state(pier).is(Blocks.POLISHED_BASALT)) {
                        return false;
                    }
                    if (solidKnown(access, pier.below())
                            && solidKnown(access, pier.below(2))) {
                        contacts++;
                    }
                }
            }
            if (contacts < 3) return false;
        }
        for (int z = 55; z <= 70; z++) {
            for (int x = 37; x <= 41; x++) {
                BlockPos floor = at(site, x, 4, z);
                if (!known(access, floor)
                        || !access.state(floor).is(Blocks.WARPED_PLANKS)
                        || !dryWalk(access, floor)) return false;
            }
            for (int x : new int[] {34, 44}) {
                BlockPos beam = at(site, x, 2, z);
                if (!known(access, beam)
                        || !access.state(beam).is(
                                Blocks.POLISHED_BLACKSTONE_BRICKS)) return false;
            }
            for (int x : new int[] {35, 43}) {
                BlockPos wall = at(site, x, 6, z);
                if (!known(access, wall)
                        || !access.state(wall).is(Blocks.POLISHED_BASALT)) {
                    return false;
                }
            }
            BlockPos roof = at(site, 39, 12, z);
            if (!known(access, roof)
                    || !(access.state(roof).is(Blocks.WARPED_PLANKS)
                            || access.state(roof).is(Blocks.SHROOMLIGHT))) {
                return false;
            }
        }
        for (int z : new int[] {55, 59, 63, 67, 70}) {
            BlockPos rib = at(site, 39, 1, z);
            if (!known(access, rib)
                    || !access.state(rib).is(Blocks.POLISHED_BLACKSTONE_BRICKS)) {
                return false;
            }
        }
        return true;
    }

    /** The v2 basin is authored lava inside a continuous blackstone shell. */
    private static boolean sealedV2Crucible(SiteAccess access, SiteCandidate site) {
        final int authoredLava = 1437;
        int retainedLava = 0;
        for (int x = 13; x <= 33; x++) {
            for (int z = 12; z <= 34; z++) {
                for (int y = 1; y <= 3; y++) {
                    // Arcade piers replace twelve otherwise regular lava cells.
                    if ((z == 19 || z == 27)
                            && ((y == 2 && (x == 13 || x == 33))
                            || (y == 3 && (x == 14 || x == 15
                                    || x == 31 || x == 32)))) continue;
                    BlockPos pos = at(site, x, y, z);
                    if (!known(access, pos)) return false;
                    var state = access.state(pos);
                    if (state.is(Blocks.LAVA)) retainedLava++;
                    else if (!state.is(Blocks.WARPED_WART_BLOCK)
                            && !state.is(Blocks.SHROOMLIGHT)) return false;
                }
            }
        }
        if (retainedLava * 100 < authoredLava * 95) return false;
        for (int y = 1; y <= 3; y++) {
            BlockPos center = at(site, 23, y, 23);
            if (!known(access, center) || !access.state(center).is(Blocks.LAVA)) return false;
        }
        for (int edge = 11; edge <= 35; edge++) {
            for (int y = 0; y <= 3; y++) {
                if (!sealedCrucibleWall(access, at(site, edge, y, 11))
                        || !sealedCrucibleWall(access, at(site, edge, y, 35))
                        || !sealedCrucibleWall(access, at(site, 11, y, edge))
                        || !sealedCrucibleWall(access, at(site, 35, y, edge))) return false;
            }
        }
        return true;
    }

    private static boolean sealedCrucibleWall(SiteAccess access, BlockPos pos) {
        return known(access, pos) && access.state(pos).is(Blocks.POLISHED_BLACKSTONE_BRICKS);
    }

    /** A v3 visitor may walk around real warped growth, within the declared halo. */
    private static boolean connectedV3Arrival(SiteAccess access,
                                              SiteCandidate site, int centerX) {
        return connectedArrival(access, site, centerX, 16, 1, false);
    }

    private static boolean connectedArrival(SiteAccess access,
                                            SiteCandidate site, int centerX,
                                            int depth, int requiredOuterCells,
                                            boolean passWarpedGrowth) {
        final int width = 15;
        final int heights = 7;
        boolean[] walkable = new boolean[width * depth * heights];
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < heights; y++) {
                    BlockPos floor = at(site, centerX + x - 7, 1 + y, z + 47);
                    walkable[(z * width + x) * heights + y] =
                            passWarpedGrowth && z + 47 >= 55
                                    ? dryWalkThroughWarpedGrowth(access, floor)
                                    : dryWalk(access, floor);
                }
            }
        }
        boolean[] seen = new boolean[walkable.length];
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        for (int x = 5; x <= 9; x++) {
            int index = x * heights + 3; // The authored Y4 floor at Z47.
            if (walkable[index]) {
                seen[index] = true;
                queue.add(index);
            }
        }
        int[] stepX = {1, -1, 0, 0};
        int[] stepZ = {0, 0, 1, -1};
        int outerCells = 0;
        while (!queue.isEmpty()) {
            int index = queue.removeFirst();
            int y = index % heights;
            int flat = index / heights;
            int x = flat % width;
            int z = flat / width;
            if (z == depth - 1 && ++outerCells >= requiredOuterCells) return true;
            for (int direction = 0; direction < stepX.length; direction++) {
                int nx = x + stepX[direction];
                int nz = z + stepZ[direction];
                if (nx < 0 || nx >= width || nz < 0 || nz >= depth) continue;
                for (int dy = -1; dy <= 1; dy++) {
                    int ny = y + dy;
                    if (ny < 0 || ny >= heights) continue;
                    int next = (nz * width + nx) * heights + ny;
                    if (walkable[next] && !seen[next]) {
                        seen[next] = true;
                        queue.addLast(next);
                    }
                }
            }
        }
        return false;
    }

    private static boolean dryWalk(SiteAccess access, BlockPos floor) {
        BlockPos feet = floor.above();
        BlockPos head = floor.above(2);
        return solidKnown(access, floor) && known(access, feet) && known(access, head)
                && access.state(feet).isAir() && access.state(head).isAir();
    }

    /** V4's natural runway may contain 26.3's noncolliding warped ground cover. */
    private static boolean dryWalkThroughWarpedGrowth(SiteAccess access, BlockPos floor) {
        BlockPos feet = floor.above();
        BlockPos head = floor.above(2);
        return solidKnown(access, floor) && known(access, feet) && known(access, head)
                && passableWarpedGrowth(access.state(feet))
                && passableWarpedGrowth(access.state(head));
    }

    private static boolean passableWarpedGrowth(net.minecraft.world.level.block.state.BlockState state) {
        return state.getFluidState().isEmpty()
                && (state.isAir() || state.is(Blocks.WARPED_ROOTS)
                        || state.is(Blocks.NETHER_SPROUTS));
    }

    private static boolean solidKnown(SiteAccess access, BlockPos pos) {
        return known(access, pos) && access.state(pos).isSolid()
                && access.state(pos).getFluidState().isEmpty();
    }

    private static boolean known(SiteAccess access, BlockPos pos) {
        var chunk = new net.minecraft.world.level.ChunkPos(
                pos.getX() >> 4, pos.getZ() >> 4);
        return access.canInspectChunk(chunk) && access.isNewChunk(chunk);
    }

    private static void require(List<SitePlacementPlan.Requirement> requirements,
                                SiteCandidate candidate, int x, int y, int z, Block block) {
        requirements.add(new SitePlacementPlan.Requirement(at(candidate, x, y, z),
                SitePlacementPlan.Require.EXACT, block.defaultBlockState()));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, path);
    }

    private static ResourceKey<Biome> biome(String path) {
        return ResourceKey.create(Registries.BIOME,
                Identifier.fromNamespaceAndPath("minecraft", path));
    }
}
