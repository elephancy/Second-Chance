package com.feel.gems.gametest.adventure;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.impl.client.gametest.world.TestWorldSaveImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.phys.Vec3;

/** Disposable production-jar probe for Hamed's fixed natural seed and copied saves. */
@SuppressWarnings("UnstableApiUsage")
public final class HamedPackagedNaturalClientGameTest implements FabricClientGameTest {
    private static final String SEED = "SecondChanceBiomeSmoke";
    private static final ResourceKey<Structure> PUBLIC = ResourceKey.create(
            Registries.STRUCTURE, Identifier.fromNamespaceAndPath("gems", "inkbound_workshop"));
    private static final BlockPos CAMERA = new BlockPos(1276, 68, 1312);
    private static final Map<String, String> NEW_PIECES = Map.of(
            "hamed_workshop_cherry_v7", "1154,67,1282,1200,107,1328",
            "hamed_print_hall_cherry_v6", "1107,60,1282,1153,103,1336",
            "hamed_cherry_lane_v5", "1154,61,1247,1200,85,1281",
            "hamed_paper_garden_cherry_v4", "1201,67,1282,1247,101,1328");
    private static final Map<String, String> OLD_PIECES = Map.of(
            "hamed_workshop_cherry_v6", NEW_PIECES.get("hamed_workshop_cherry_v7"),
            "hamed_print_hall_cherry_v6", NEW_PIECES.get("hamed_print_hall_cherry_v6"),
            "hamed_cherry_lane_v5", NEW_PIECES.get("hamed_cherry_lane_v5"),
            "hamed_paper_garden_cherry_v3", NEW_PIECES.get("hamed_paper_garden_cherry_v4"));

    private record Site(List<BoundingBox> buildings, List<BoundingBox> allPieces,
                        Rotation rotation) {}

    @Override
    public void runTest(ClientGameTestContext context) {
        String phase = System.getProperty("gems.hamedPackagedPhase", "");
        if (phase.isEmpty()) return;
        require(List.of("fresh", "reopen", "old-v6").contains(phase),
                "Unknown Hamed packaged phase: " + phase);
        int routeNodes;
        if (phase.equals("fresh")) {
            try (TestSingleplayerContext world = context.worldBuilder()
                    .setUseConsistentSettings(false)
                    .adjustSettings(settings -> {
                        settings.setSeed(SEED);
                        settings.setGenerateStructures(true);
                    }).create()) {
                routeNodes = inspectAndCapture(context, world, phase);
            }
        } else {
            Path save = context.computeOnClient(client -> client.gameDirectory.toPath()
                    .resolve("saves").resolve("New World"));
            require(Files.isRegularFile(save.resolve("level.dat")),
                    "Copied Hamed save missing: " + save);
            try (TestSingleplayerContext world = new TestWorldSaveImpl(context, save).open()) {
                routeNodes = inspectAndCapture(context, world, phase);
            }
        }
        System.out.println("HAMED_PACKAGED_PASS phase=" + phase
                + " seed=" + SEED + " start=75,83 pieces=4 routeNodes=" + routeNodes);
    }

    private static int inspectAndCapture(ClientGameTestContext context,
                                         TestSingleplayerContext world, String phase) {
        world.getConnection().waitForChunksRender();
        Site site = world.getServer().computeOnServer(server ->
                inspect(server.overworld(), phase.equals("old-v6")));
        List<NaturalDistrictRouteProbe.Gate> gates = NaturalDistrictRouteProbe.gatesFor(
                "inkbound_workshop", site.rotation());
        require(gates.size() == 1, "Hamed must have one natural arrival gate");
        var walk = NaturalDistrictRouteProbe.walk(context, world, "inkbound_workshop",
                site.buildings(), site.allPieces(), gates.getFirst());
        captureGarden(context, world, phase);
        return walk.steps().size();
    }

    private static Site inspect(ServerLevel level, boolean old) {
        Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValue(PUBLIC);
        require(structure != null, "Public Hamed structure missing from production registry");
        StructureStart start = level.structureManager().getStartForStructure(structure,
                level.getChunk(75, 83));
        require(start != null && start.isValid(), "No natural Hamed start at seed-A chunk 75,83");
        require(start.getChunkPos().x() == 75 && start.getChunkPos().z() == 83,
                "Hamed start moved from fixed natural site");
        Map<String, String> actual = new HashMap<>();
        Map<String, BoundingBox> boxes = new HashMap<>();
        Rotation rotation = null;
        for (var raw : start.getPieces()) {
            require(raw instanceof PoolElementStructurePiece, "Unexpected Hamed piece type");
            var piece = (PoolElementStructurePiece) raw;
            require(piece.getElement() instanceof SinglePoolElement,
                    "Unexpected Hamed pool element");
            String name = ((SinglePoolElement) piece.getElement()).getTemplateLocation().getPath();
            require(piece.getRotation() == Rotation.CLOCKWISE_180,
                    "Saved Hamed piece rotation changed: " + name);
            require(actual.putIfAbsent(name, bounds(piece.getBoundingBox())) == null,
                    "Duplicate Hamed piece: " + name);
            boxes.put(name, piece.getBoundingBox());
            rotation = piece.getRotation();
        }
        Map<String, String> expected = old ? OLD_PIECES : NEW_PIECES;
        require(actual.equals(expected), "Hamed pieces or bounds changed: " + actual);
        List<BoundingBox> all = new ArrayList<>(boxes.values());
        for (BoundingBox box : all) {
            for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
                for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                    level.getChunk(cx, cz);
                }
            }
        }
        require(block(level, 1223, 73, 1305).equals("gems:inkbound_binding"),
                "Hamed binding station missing after natural load");
        if (!old) {
            for (int z : new int[]{1316, 1304, 1294}) {
                require(block(level, 1230, 90, z).equals("minecraft:light_blue_stained_glass"),
                        "New upper bindery window missing at z=" + z);
            }
            require(block(level, 1236, 86, 1303).equals("minecraft:cherry_planks"),
                    "New upper gallery floor missing");
            require(block(level, 1236, 101, 1303).equals("minecraft:deepslate_tiles"),
                    "New upper roof ridge missing");
        }
        return new Site(List.of(boxes.get(old ? "hamed_workshop_cherry_v6"
                        : "hamed_workshop_cherry_v7"),
                boxes.get("hamed_print_hall_cherry_v6"),
                boxes.get(old ? "hamed_paper_garden_cherry_v3"
                        : "hamed_paper_garden_cherry_v4")), List.copyOf(all), rotation);
    }

    private static void captureGarden(ClientGameTestContext context,
                                      TestSingleplayerContext world, String phase) {
        Vec3 eye = new Vec3(CAMERA.getX() + 0.5, CAMERA.getY() + 1.62,
                CAMERA.getZ() + 0.5);
        Vec3 target = new Vec3(1242.5, 91.0, 1304.5);
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        world.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            require(!level.getBlockState(CAMERA.below()).is(BlockTags.LEAVES)
                    && !level.getBlockState(CAMERA.below()).is(BlockTags.LOGS)
                    && !level.getBlockState(CAMERA.below()).getCollisionShape(
                            level, CAMERA.below()).isEmpty(),
                    "Hamed side camera lost its natural standing ground");
            require(level.getBlockState(CAMERA).getCollisionShape(level, CAMERA).isEmpty()
                    && level.getBlockState(CAMERA.above()).getCollisionShape(
                            level, CAMERA.above()).isEmpty(),
                    "Hamed side camera is blocked by natural foliage");
            var player = world.getConnection().getServerPlayer();
            player.teleportTo(level, eye.x, CAMERA.getY(), eye.z,
                    EnumSet.noneOf(Relative.class), yaw, pitch, false);
        });
        world.getConnection().waitForChunksRender();
        context.waitTicks(40);
        context.runOnClient(client -> {
            client.gui.hud.getChat().clearMessages(false);
            client.gui.toastManager().clear();
        });
        context.takeScreenshot("hamed-packaged-" + phase + "-garden");
    }

    private static String block(ServerLevel level, int x, int y, int z) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(
                new BlockPos(x, y, z)).getBlock()).toString();
    }

    private static String bounds(BoundingBox box) {
        return box.minX() + "," + box.minY() + "," + box.minZ() + ","
                + box.maxX() + "," + box.maxY() + "," + box.maxZ();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
