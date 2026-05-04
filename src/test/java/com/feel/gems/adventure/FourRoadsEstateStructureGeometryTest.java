package com.feel.gems.adventure.estate;

import com.feel.gems.testutil.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FourRoadsEstateStructureGeometryTest {
    @BeforeAll
    static void initBootstrap() {
        MinecraftBootstrap.ensure();
    }

    @Test
    void currentCampusFormsCompleteGridAtEveryRotation() {
        for (int turn = 0; turn < 4; turn++) {
            assertTrue(FourRoadsEstateStructure.completeGridGeometry(currentGrid(turn)),
                    "turn " + turn);
        }
    }

    @Test
    void oldCenterOrMissingAxialCannotMasqueradeAsCurrentCampus() {
        List<FourRoadsEstateStructure.TemplateCell> oldCenter = currentGrid(0);
        FourRoadsEstateStructure.TemplateCell center = oldCenter.get(12);
        oldCenter.set(12, new FourRoadsEstateStructure.TemplateCell(
                Identifier.fromNamespaceAndPath("gems", "four_roads_estate_v4_2_2"),
                center.bounds()));
        assertFalse(FourRoadsEstateStructure.completeGridGeometry(oldCenter));

        List<FourRoadsEstateStructure.TemplateCell> missingAxial = currentGrid(0);
        missingAxial.set(11, new FourRoadsEstateStructure.TemplateCell(
                Identifier.fromNamespaceAndPath("gems", "four_roads_estate_v1_1_2"),
                missingAxial.get(11).bounds()));
        assertFalse(FourRoadsEstateStructure.completeGridGeometry(missingAxial));
    }

    @Test
    void reflectionAndMissingPieceAreNotCompleteGrid() {
        List<FourRoadsEstateStructure.TemplateCell> reflected = new ArrayList<>(25);
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                reflected.add(cell(x, z, z, x));
            }
        }
        assertFalse(FourRoadsEstateStructure.completeGridGeometry(reflected));

        List<FourRoadsEstateStructure.TemplateCell> incomplete = currentGrid(0);
        incomplete.removeLast();
        assertFalse(FourRoadsEstateStructure.completeGridGeometry(incomplete));
    }

    private static List<FourRoadsEstateStructure.TemplateCell> currentGrid(int turn) {
        List<FourRoadsEstateStructure.TemplateCell> cells = new ArrayList<>(25);
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                int placedX = switch (turn) {
                    case 1 -> 4 - z;
                    case 2 -> 4 - x;
                    case 3 -> z;
                    default -> x;
                };
                int placedZ = switch (turn) {
                    case 1 -> x;
                    case 2 -> 4 - z;
                    case 3 -> 4 - x;
                    default -> z;
                };
                cells.add(cell(x, z, placedX, placedZ));
            }
        }
        return cells;
    }

    private static FourRoadsEstateStructure.TemplateCell cell(int x, int z,
                                                               int placedX, int placedZ) {
        int minX = 1000 + placedX * 47;
        int minZ = -2000 + placedZ * 47;
        String version = x == 2 && z == 2 ? "v5"
                : (x == 2 && (z == 1 || z == 3))
                || (z == 2 && (x == 1 || x == 3)) ? "v4" : "v1";
        int height = x == 2 && z == 2 ? 64
                : z == 1 && x == 2 ? 30
                : z == 2 && x == 1 ? 32
                : z == 2 && x == 3 ? 28
                : z == 3 && x == 2 ? 27 : 18;
        return new FourRoadsEstateStructure.TemplateCell(
                Identifier.fromNamespaceAndPath("gems", "four_roads_estate_"
                        + version + "_" + z + "_" + x),
                new BoundingBox(minX, 70, minZ, minX + 46, 70 + height - 1, minZ + 46));
    }
}
