package com.feel.gems.adventure.estate;

import com.feel.gems.GemsMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** The versioned attachment tree also defines the only walkable module seams. */
final class EstateModuleGraph {
    private static final String PREFIX = "four_roads_estate_v1_";
    private static final String V5_CENTER = "four_roads_estate_v5_2_2";
    private static final int[] V4_AXIAL = {7, 11, 13, 17};
    private static final int[] PARENT = {
            1, 6, 1, 2, 9,
            10, 7, 12, 13, 8,
            15, 12, -1, 12, 9,
            16, 11, 12, 17, 14,
            15, 22, 23, 18, 23
    };

    record Module(int id, PoolElementStructurePiece piece) {
        int baseY() { return piece.getBoundingBox().minY(); }
        int floorY() { return baseY() + 4; }
        int gridX() { return id % 5; }
        int gridZ() { return id / 5; }
    }

    record Seam(Module child, Module parent, BlockPos from, BlockPos to,
                Direction direction) {
        int floorAt(int step) {
            return from.getY() + Math.round((to.getY() - from.getY())
                    * step / 12.0f);
        }

        BlockPos at(int step, int lane) {
            return from.relative(direction, step)
                    .relative(direction.getClockWise(), lane)
                    .atY(floorAt(step));
        }
    }

    private EstateModuleGraph() { }

    static int parent(int id) { return PARENT[id]; }

    static Module[] modules(List<StructurePiece> pieces) {
        Module[] result = new Module[25];
        if (pieces.size() != 25) return null;
        for (StructurePiece piece : pieces) {
            if (!(piece instanceof PoolElementStructurePiece pooled)
                    || !(pooled.getElement() instanceof SinglePoolElement single)) return null;
            Identifier template = single.getTemplateLocation();
            if (!GemsMod.MOD_ID.equals(template.getNamespace())) return null;
            int id = cellId(template);
            if (id < 0 || result[id] != null) return null;
            result[id] = new Module(id, pooled);
        }
        for (Module module : result) if (module == null) return null;
        Identifier[] templates = new Identifier[25];
        for (Module module : result) {
            templates[module.id()] = ((SinglePoolElement) module.piece().getElement())
                    .getTemplateLocation();
        }
        if (!coherentVersion(templates)) return null;
        return result;
    }

    /** IDs and heights belong to the saved template, not the current start pool. */
    static int cellId(Identifier template) {
        if (!GemsMod.MOD_ID.equals(template.getNamespace())) return -1;
        String path = template.getPath();
        if (path.equals(V5_CENTER)) return 12;
        if (path.equals("four_roads_estate_v4_1_2")) return 7;
        if (path.equals("four_roads_estate_v4_2_1")) return 11;
        if (path.equals("four_roads_estate_v4_2_3")) return 13;
        if (path.equals("four_roads_estate_v4_3_2")) return 17;
        if (!path.startsWith(PREFIX)) return -1;
        String name = path.substring(PREFIX.length());
        if (name.length() != 3 || name.charAt(1) != '_'
                || name.charAt(0) < '0' || name.charAt(0) > '4'
                || name.charAt(2) < '0' || name.charAt(2) > '4') return -1;
        return (name.charAt(0) - '0') * 5 + name.charAt(2) - '0';
    }

    static int expectedHeight(Identifier template) {
        return switch (template.getPath()) {
            case V5_CENTER -> 64;
            case "four_roads_estate_v4_1_2" -> 30;
            case "four_roads_estate_v4_2_1" -> 32;
            case "four_roads_estate_v4_2_3" -> 28;
            case "four_roads_estate_v4_3_2" -> 27;
            default -> cellId(template) >= 0 ? 18 : -1;
        };
    }

    /** Current campus center and axial modules must form one complete layout. */
    static boolean coherentVersion(Identifier[] templates) {
        if (templates.length != 25 || templates[12] == null
                || !V5_CENTER.equals(templates[12].getPath())) return false;
        for (int id : V4_AXIAL) {
            if (templates[id] == null
                    || !templates[id].getPath().startsWith("four_roads_estate_v4_"))
                return false;
        }
        return true;
    }

    static List<Seam> seams(Module[] modules) {
        if (modules == null) return List.of();
        List<Seam> result = new ArrayList<>(24);
        for (int id = 0; id < modules.length; id++) {
            int parentId = PARENT[id];
            if (parentId < 0) continue;
            Module child = modules[id];
            Module parent = modules[parentId];
            Direction local = localDirection(child, parent);
            if (local == null) return List.of();
            BlockPos from = room(child, local);
            BlockPos to = room(parent, local.getOpposite());
            Direction world = child.piece().getRotation().rotate(local);
            if (Math.abs(from.getY() - to.getY()) > 10
                    || Math.abs(from.getX() - to.getX())
                    + Math.abs(from.getZ() - to.getZ()) != 12
                    || from.getX() + world.getStepX() * 12 != to.getX()
                    || from.getZ() + world.getStepZ() * 12 != to.getZ()) return List.of();
            result.add(new Seam(child, parent, from, to, world));
        }
        return List.copyOf(result);
    }

    private static Direction localDirection(Module first, Module second) {
        int dx = second.gridX() - first.gridX();
        int dz = second.gridZ() - first.gridZ();
        if (dx == 1 && dz == 0) return Direction.EAST;
        if (dx == -1 && dz == 0) return Direction.WEST;
        if (dx == 0 && dz == 1) return Direction.SOUTH;
        if (dx == 0 && dz == -1) return Direction.NORTH;
        return null;
    }

    private static BlockPos room(Module module, Direction side) {
        int x = side == Direction.WEST ? 5 : side == Direction.EAST ? 40 : 19;
        int z = side == Direction.NORTH ? 5 : side == Direction.SOUTH ? 40 : 19;
        BlockPos transformed = StructureTemplate.transform(new BlockPos(x, 4, z),
                Mirror.NONE, module.piece().getRotation(), BlockPos.ZERO)
                .offset(module.piece().getPosition());
        return transformed.atY(module.floorY());
    }
}
