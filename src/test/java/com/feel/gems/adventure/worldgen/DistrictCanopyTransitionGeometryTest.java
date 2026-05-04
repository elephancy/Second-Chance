package com.feel.gems.adventure.worldgen;

import com.feel.gems.testutil.MinecraftBootstrap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DistrictCanopyTransitionGeometryTest {
    private static DistrictCanopyTransitionFeature.Spec spec(String name) {
        MinecraftBootstrap.ensure();
        return DistrictCanopyTransitionFeature.SPECS.stream()
                .filter(candidate -> candidate.name().equals(name))
                .findFirst().orElseThrow();
    }

    @Test
    void activeRootsCannotAdoptLegacyStarts() {
        var branch = spec("branch");
        var fam = spec("fam");
        var othman = spec("othman");
        assertEquals(Identifier.fromNamespaceAndPath("gems", "branch_vault_vista_v7"),
                branch.root());
        assertEquals(Identifier.fromNamespaceAndPath("gems", "fam_forum_east_wayfinding_v7"),
                fam.root());
        assertEquals(Identifier.fromNamespaceAndPath("gems", "othman_library_archive_v7"),
                othman.root());
        assertFalse(DistrictCanopyTransitionFeature.isActiveRoot(
                Identifier.fromNamespaceAndPath("gems", "othman_library_colonnade_v6"),
                othman.root()));
        assertFalse(DistrictCanopyTransitionFeature.isActiveRoot(
                Identifier.fromNamespaceAndPath("gems", "branch_vault_wayfinding_v6"),
                branch.root()));
        for (var active : DistrictCanopyTransitionFeature.SPECS) {
            assertTrue(DistrictCanopyTransitionFeature.isActiveRoot(active.root(), active.root()));
            assertFalse(DistrictCanopyTransitionFeature.isActiveRoot(
                    Identifier.fromNamespaceAndPath("gems", active.name() + "_legacy"),
                    active.root()));
        }
    }

    @Test
    void branchSouthCorridorCoversTheDoorWithinItsBoundedWindow() {
        var branch = spec("branch");
        var root = new BoundingBox(0, 60, 0, 46, 90, 46);
        assertNotNull(DistrictCanopyTransitionFeature.maskPoint(branch, root,
                Direction.SOUTH, Direction.EAST, 13, 47));
        assertNotNull(DistrictCanopyTransitionFeature.maskPoint(branch, root,
                Direction.SOUTH, Direction.EAST, 29, 66));
        assertNotNull(DistrictCanopyTransitionFeature.maskPoint(branch, root,
                Direction.SOUTH, Direction.EAST, 29, 82));
        for (int doorX = 20; doorX <= 26; doorX++) {
            assertNotNull(DistrictCanopyTransitionFeature.maskPoint(branch, root,
                    Direction.SOUTH, Direction.EAST, doorX, 67));
        }
        assertNull(DistrictCanopyTransitionFeature.maskPoint(branch, root,
                Direction.SOUTH, Direction.EAST, 29, 87));
        assertNull(DistrictCanopyTransitionFeature.maskPoint(branch, root,
                Direction.SOUTH, Direction.EAST, 30, 66));
        assertNull(DistrictCanopyTransitionFeature.maskPoint(branch, root,
                Direction.SOUTH, Direction.EAST, 12, 67));
        assertNull(DistrictCanopyTransitionFeature.maskPoint(branch, root,
                Direction.SOUTH, Direction.EAST, 21, 46));
    }

    @Test
    void rotatedEastMasksStayInsideMeasuredWindows() {
        var root = new BoundingBox(0, 60, 0, 46, 90, 46);
        Direction east = Rotation.COUNTERCLOCKWISE_90.rotate(Direction.SOUTH);
        Direction south = Rotation.COUNTERCLOCKWISE_90.rotate(Direction.WEST);
        assertEquals(Direction.EAST, east);
        assertEquals(Direction.SOUTH, south);

        var fam = spec("fam");
        assertEquals(0, fam.lateralOffset());
        assertNotNull(DistrictCanopyTransitionFeature.maskPoint(fam, root,
                east, south, 78, 25));
        assertNotNull(DistrictCanopyTransitionFeature.maskPoint(fam, root,
                east, south, 78, 19));
        assertNotNull(DistrictCanopyTransitionFeature.maskPoint(fam, root,
                east, south, 78, 27));
        assertNull(DistrictCanopyTransitionFeature.maskPoint(fam, root,
                east, south, 79, 25));
        assertNull(DistrictCanopyTransitionFeature.maskPoint(fam, root,
                east, south, 78, 28));

        var othman = spec("othman");
        assertNotNull(DistrictCanopyTransitionFeature.maskPoint(othman, root,
                east, south, 53, 25));
        assertNotNull(DistrictCanopyTransitionFeature.maskPoint(othman, root,
                east, south, 62, 27));
        assertNull(DistrictCanopyTransitionFeature.maskPoint(othman, root,
                east, south, 52, 25));
        assertNull(DistrictCanopyTransitionFeature.maskPoint(othman, root,
                east, south, 63, 25));
        assertNull(DistrictCanopyTransitionFeature.maskPoint(othman, root,
                east, south, 53, 28));
    }

    @Test
    void othmanPreservesTreesThatCrossTheWritableChunkOrTouchBlockEntities() {
        spec("othman");
        BlockPos trunk = new BlockPos(0, 64, 0);
        BlockPos crown = new BlockPos(1, 65, 0);
        BlockPos outside = new BlockPos(2, 65, 0);
        Set<BlockPos> wholeTree = Set.of(trunk, crown);
        Map<BlockPos, BlockState> states = Map.of(
                trunk, Blocks.OAK_LOG.defaultBlockState(),
                crown, Blocks.OAK_LEAVES.defaultBlockState(),
                outside, Blocks.OAK_LEAVES.defaultBlockState());

        assertFalse(DistrictCanopyTransitionFeature.touchesProtectedTree(wholeTree,
                wholeTree, pos -> pos.equals(outside) ? Blocks.AIR.defaultBlockState()
                        : states.getOrDefault(pos, Blocks.AIR.defaultBlockState()),
                pos -> false, 0, 320));
        assertTrue(DistrictCanopyTransitionFeature.touchesProtectedTree(wholeTree,
                wholeTree, pos -> states.getOrDefault(pos, Blocks.AIR.defaultBlockState()),
                pos -> false, 0, 320));
        assertTrue(DistrictCanopyTransitionFeature.touchesProtectedTree(wholeTree,
                wholeTree, pos -> Blocks.AIR.defaultBlockState(),
                pos -> pos.equals(trunk.south()), 0, 320));
        assertTrue(DistrictCanopyTransitionFeature.isNaturalTreeCell(
                Blocks.OAK_LEAVES.defaultBlockState()));
        assertFalse(DistrictCanopyTransitionFeature.isNaturalTreeCell(
                Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true)));
    }
}
