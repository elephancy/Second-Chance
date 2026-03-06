package com.feel.gems.adventure.estate;

import com.feel.gems.GemsMod;
import com.feel.gems.adventure.worldgen.site.GuardedNewChunkSiteAccess;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;

/**
 * Immutable geometry copied into each newly authored body chunk. A gate can
 * read this chunk alone; vanilla's structure lookup would synchronously load
 * the distant start chunk. This descriptor proves geometry identity, while
 * the loaded audit remains the independent foreign-start/site oracle.
 */
final class EstateRouteSite {
    private static final int VERSION = 1;
    private static final int MODULES = 25;
    private static final int SEAMS = 24;
    private static final int GATES = 4;
    private static final int HEADER = 9;
    private static final int MODULE_INTS = 6;
    private static final int SEAM_INTS = 8;
    private static final int GATE_INTS = 6;
    private static final int LENGTH = HEADER + MODULES * MODULE_INTS
            + SEAMS * SEAM_INTS + GATES * GATE_INTS + 1;
    private static final AttachmentType<List<Integer>> DESCRIPTOR =
            AttachmentRegistry.createPersistent(Identifier.fromNamespaceAndPath(
                    GemsMod.MOD_ID, "estate_route_site_v1"), Codec.INT.listOf());

    record Seam(int child, int parent, BlockPos from, BlockPos to,
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

    record Gate(char wing, BlockPos marker, Direction outward, int distance) {
        BlockPos at(int step, int lane) {
            return marker.relative(outward, step)
                    .relative(outward.getClockWise(), lane);
        }
    }

    record Site(List<Integer> encoded, BoundingBox bounds, BoundingBox[] modules,
                Seam[] seams, Gate[] gates, BlockPos foyer) {
        BoundingBox module(int id) { return modules[id]; }

        int roofAt(int x, int z) {
            for (BoundingBox box : modules) {
                if (x >= box.minX() && x <= box.maxX()
                        && z >= box.minZ() && z <= box.maxZ())
                    return box.maxY();
            }
            return Integer.MIN_VALUE;
        }

        Seam seam(int child) {
            return child >= 0 && child < seams.length ? seams[child] : null;
        }

        Gate gate(char wing) {
            for (Gate gate : gates) if (gate.wing() == wing) return gate;
            return null;
        }

        boolean bodyChunk(ChunkPos chunk) {
            return chunk.getMaxBlockX() >= bounds.minX()
                    && chunk.getMinBlockX() <= bounds.maxX()
                    && chunk.getMaxBlockZ() >= bounds.minZ()
                    && chunk.getMinBlockZ() <= bounds.maxZ();
        }

        boolean sameLoadedChunk(ServerLevel level, ChunkPos pos) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
            return chunk != null && (!bodyChunk(pos)
                    || encoded.equals(chunk.getAttached(DESCRIPTOR)));
        }
    }

    private EstateRouteSite() { }

    static void init() { /* Register persistent attachment before world load. */ }

    static void capture(WorldGenRegion region, ChunkPos center,
                        PiecesContainer container) {
        EstateModuleGraph.Module[] modules = EstateModuleGraph.modules(container.pieces());
        if (modules == null || EstateModuleGraph.seams(modules).size() != SEAMS
                || !FourRoadsEstateStructure.completeGrid(container.pieces())) return;
        BoundingBox body = body(modules);
        if (body.getXSpan() != 235 || body.getZSpan() != 235
                || (body.minX() + 117 & 15) != 0
                || (body.minZ() + 117 & 15) != 0
                || center.getMaxBlockX() < body.minX()
                || center.getMinBlockX() > body.maxX()
                || center.getMaxBlockZ() < body.minZ()
                || center.getMinBlockZ() > body.maxZ()) return;
        var rotation = modules[12].piece().getRotation();
        List<Integer> values = new ArrayList<>(LENGTH);
        values.add(VERSION);
        values.add((body.minX() + 117) >> 4);
        values.add((body.minZ() + 117) >> 4);
        values.add(rotation.ordinal());
        values.add(body.minX());
        values.add(body.minY());
        values.add(body.minZ());
        values.add(body.maxY());
        values.add(235);
        for (EstateModuleGraph.Module module : modules) {
            if (module.piece().getRotation() != rotation) return;
            BoundingBox box = module.piece().getBoundingBox();
            appendBox(values, box);
        }
        for (EstateModuleGraph.Seam seam : EstateModuleGraph.seams(modules)) {
            values.add(seam.child().id());
            values.add(seam.parent().id());
            appendPos(values, seam.from());
            appendPos(values, seam.to());
        }
        List<EstateTerrainPlan.Gate> gates = EstateTerrainPlan.gates(container.pieces());
        if (gates.size() != GATES) return;
        for (EstateTerrainPlan.Gate gate : gates) {
            char wing = wing(modules, gate.piece());
            if (wing == '?') return;
            values.add((int) wing);
            appendPos(values, gate.marker());
            values.add(gate.outward().ordinal());
            values.add(gate.distance());
        }
        values.add(values.hashCode());
        List<Integer> encoded = List.copyOf(values);
        if (decode(encoded) == null) return;
        region.getChunk(center.x(), center.z()).setAttached(DESCRIPTOR, encoded);
    }

    static Site readLoaded(ServerLevel level, BlockPos at) {
        ChunkPos pos = new ChunkPos(at.getX() >> 4, at.getZ() >> 4);
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
        if (chunk == null || !new GuardedNewChunkSiteAccess(level, Set.of())
                .isNewChunk(pos)) return null;
        Site site = decode(chunk.getAttached(DESCRIPTOR));
        return site != null && at.getX() >= site.bounds().minX()
                && at.getX() <= site.bounds().maxX()
                && at.getZ() >= site.bounds().minZ()
                && at.getZ() <= site.bounds().maxZ() ? site : null;
    }

    /** The saved per-chunk descriptor is available before final vegetation. */
    static Site readGenerating(WorldGenRegion region, ChunkPos center) {
        return decode(region.getChunk(center.x(), center.z()).getAttached(DESCRIPTOR));
    }

    /** Full loaded audits also bind the copied route to the saved structure. */
    static boolean matchesStart(Site site, StructureStart start) {
        if (site == null || start == null || !start.isValid()) return false;
        EstateModuleGraph.Module[] modules = EstateModuleGraph.modules(start.getPieces());
        if (modules == null || !sameBox(site.bounds(), start.getBoundingBox())) return false;
        for (int id = 0; id < MODULES; id++) {
            if (!sameBox(site.module(id), modules[id].piece().getBoundingBox())) return false;
        }
        List<EstateModuleGraph.Seam> seams = EstateModuleGraph.seams(modules);
        if (seams.size() != SEAMS) return false;
        for (EstateModuleGraph.Seam seam : seams) {
            Seam saved = site.seam(seam.child().id());
            if (saved == null || saved.parent() != seam.parent().id()
                    || !saved.from().equals(seam.from())
                    || !saved.to().equals(seam.to())
                    || saved.direction() != seam.direction()) return false;
        }
        List<EstateTerrainPlan.Gate> gates = EstateTerrainPlan.gates(start.getPieces());
        if (gates.size() != GATES) return false;
        for (EstateTerrainPlan.Gate gate : gates) {
            boolean matched = false;
            for (Gate saved : site.gates()) {
                if (saved.marker().equals(gate.marker())
                        && saved.outward() == gate.outward()
                        && saved.distance() == gate.distance()) matched = true;
            }
            if (!matched) return false;
        }
        return true;
    }

    private static boolean sameBox(BoundingBox a, BoundingBox b) {
        return a.minX() == b.minX() && a.minY() == b.minY()
                && a.minZ() == b.minZ() && a.maxX() == b.maxX()
                && a.maxY() == b.maxY() && a.maxZ() == b.maxZ();
    }

    private static Site decode(List<Integer> encoded) {
        if (encoded == null || encoded.size() != LENGTH
                || encoded.get(0) != VERSION
                || encoded.get(LENGTH - 1) != encoded.subList(0, LENGTH - 1).hashCode()
                || encoded.get(8) != 235) return null;
        int minX = encoded.get(4);
        int minY = encoded.get(5);
        int minZ = encoded.get(6);
        int maxY = encoded.get(7);
        if (minY < -64 || maxY > 320 || minY > maxY
                || ((minX + 117) >> 4) != encoded.get(1)
                || ((minZ + 117) >> 4) != encoded.get(2)
                || ((minX + 117) & 15) != 0
                || ((minZ + 117) & 15) != 0
                || encoded.get(3) < 0 || encoded.get(3) > 3) return null;
        BoundingBox body = new BoundingBox(minX, minY, minZ,
                minX + 234, maxY, minZ + 234);
        int offset = HEADER;
        BoundingBox[] modules = new BoundingBox[MODULES];
        for (int i = 0; i < MODULES; i++) {
            BoundingBox box = new BoundingBox(encoded.get(offset), encoded.get(offset + 1),
                    encoded.get(offset + 2), encoded.get(offset + 3),
                    encoded.get(offset + 4), encoded.get(offset + 5));
            if (box.getXSpan() != 47 || box.getZSpan() != 47
                    || box.minX() < body.minX() || box.maxX() > body.maxX()
                    || box.minZ() < body.minZ() || box.maxZ() > body.maxZ()
                    || box.minY() < body.minY() || box.maxY() > body.maxY()) return null;
            modules[i] = box;
            offset += MODULE_INTS;
        }
        boolean[] grid = new boolean[MODULES];
        for (BoundingBox box : modules) {
            if ((box.minX() - body.minX()) % 47 != 0
                    || (box.minZ() - body.minZ()) % 47 != 0) return null;
            int cell = (box.minX() - body.minX()) / 47
                    + 5 * ((box.minZ() - body.minZ()) / 47);
            if (cell < 0 || cell >= MODULES || grid[cell]) return null;
            grid[cell] = true;
        }
        for (boolean occupied : grid) if (!occupied) return null;
        Seam[] seams = new Seam[MODULES];
        boolean[] childSeen = new boolean[MODULES];
        for (int i = 0; i < SEAMS; i++) {
            int child = encoded.get(offset++);
            int parent = encoded.get(offset++);
            BlockPos from = pos(encoded, offset);
            offset += 3;
            BlockPos to = pos(encoded, offset);
            offset += 3;
            if (child < 0 || child >= MODULES || parent < 0 || parent >= MODULES
                    || parent != EstateModuleGraph.parent(child)
                    || childSeen[child] || !contains(modules[child], from)
                    || !contains(modules[parent], to)
                    || Math.abs(from.getY() - to.getY()) > 10) return null;
            Direction direction = direction(from, to);
            if (direction == null) return null;
            seams[child] = new Seam(child, parent, from, to, direction);
            childSeen[child] = true;
        }
        for (int i = 0; i < MODULES; i++) {
            if (i != 12 && !childSeen[i]) return null;
        }
        Gate[] gates = new Gate[GATES];
        boolean[] wings = new boolean[GATES];
        for (int i = 0; i < GATES; i++) {
            char wing = (char) encoded.get(offset++).intValue();
            BlockPos marker = pos(encoded, offset);
            offset += 3;
            int ordinal = encoded.get(offset++);
            int distance = encoded.get(offset++);
            int slot = switch (wing) {
                case 'N' -> 0; case 'E' -> 1; case 'S' -> 2; case 'W' -> 3;
                default -> -1;
            };
            if (slot < 0 || wings[slot] || ordinal < 0
                    || ordinal >= Direction.values().length
                    || distance < 5 || distance > 6
                    || !contains(modules[switch (wing) {
                        case 'N' -> 2; case 'E' -> 14;
                        case 'S' -> 22; default -> 10;
                    }], marker)) return null;
            Direction outward = Direction.values()[ordinal];
            int edgeDistance = switch (outward) {
                case NORTH -> marker.getZ() - body.minZ();
                case EAST -> body.maxX() - marker.getX();
                case SOUTH -> body.maxZ() - marker.getZ();
                case WEST -> marker.getX() - body.minX();
                default -> -1;
            };
            if (edgeDistance != distance) return null;
            wings[slot] = true;
            gates[i] = new Gate(wing, marker, outward, distance);
        }
        if (offset != LENGTH - 1) return null;
        BoundingBox mansion = modules[12];
        BlockPos foyer = new BlockPos(mansion.getCenter().getX(),
                mansion.minY() + 4, mansion.getCenter().getZ());
        return new Site(encoded, body, modules, seams, gates, foyer);
    }

    private static boolean contains(BoundingBox box, BlockPos pos) {
        return box.isInside(pos);
    }

    private static Direction direction(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (dx == 12 && dz == 0) return Direction.EAST;
        if (dx == -12 && dz == 0) return Direction.WEST;
        if (dx == 0 && dz == 12) return Direction.SOUTH;
        if (dx == 0 && dz == -12) return Direction.NORTH;
        return null;
    }

    private static BoundingBox body(EstateModuleGraph.Module[] modules) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (EstateModuleGraph.Module module : modules) {
            BoundingBox box = module.piece().getBoundingBox();
            minX = Math.min(minX, box.minX());
            minY = Math.min(minY, box.minY());
            minZ = Math.min(minZ, box.minZ());
            maxX = Math.max(maxX, box.maxX());
            maxY = Math.max(maxY, box.maxY());
            maxZ = Math.max(maxZ, box.maxZ());
        }
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static char wing(EstateModuleGraph.Module[] modules,
                             net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece piece) {
        for (EstateModuleGraph.Module module : modules) {
            if (module.piece() != piece) continue;
            return switch (module.id()) {
                case 2 -> 'N'; case 14 -> 'E';
                case 22 -> 'S'; case 10 -> 'W';
                default -> '?';
            };
        }
        return '?';
    }

    private static void appendBox(List<Integer> values, BoundingBox box) {
        values.add(box.minX()); values.add(box.minY()); values.add(box.minZ());
        values.add(box.maxX()); values.add(box.maxY()); values.add(box.maxZ());
    }

    private static void appendPos(List<Integer> values, BlockPos pos) {
        values.add(pos.getX()); values.add(pos.getY()); values.add(pos.getZ());
    }

    private static BlockPos pos(List<Integer> values, int at) {
        return new BlockPos(values.get(at), values.get(at + 1), values.get(at + 2));
    }
}
