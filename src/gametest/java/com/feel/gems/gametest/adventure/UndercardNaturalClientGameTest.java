package com.feel.gems.gametest.adventure;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.feel.gems.adventure.undercard.UndercardBlocks;
import com.feel.gems.adventure.undercard.UndercardConstructEntity;
import com.feel.gems.adventure.undercard.UndercardSiteIntegrity;
import com.feel.gems.adventure.undercard.UndercardState;
import com.feel.gems.adventure.undercard.UndercardStructure;
import com.feel.gems.adventure.worldgen.site.GuardedNewChunkSiteAccess;
import com.feel.gems.adventure.worldgen.site.SiteAccess;
import com.feel.gems.state.GemsPersistentDataHolder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import net.fabricmc.fabric.impl.client.gametest.world.TestWorldSaveImpl;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Test-only natural Undercard starts. Shipping structure set remains absent. */
@SuppressWarnings("UnstableApiUsage")
public final class UndercardNaturalClientGameTest implements FabricClientGameTest {
    private static final String[] SEEDS = {
            "Undercard-Badlands-1", "Undercard-Badlands-2", "Undercard-Badlands-3"
    };
    private static final boolean V5_TRIAL = Boolean.getBoolean("gems.undercardV5Trial");
    private static final boolean V5_ACCEPTANCE = Boolean.getBoolean(
            "gems.undercardV5Acceptance");
    private static final String V5_SEED_A = "Undercard-V5-Badlands-A-20261003";
    private static final String V5_SEED_B = "Undercard-V5-Badlands-B-20261003";
    private static final ResourceKey<Structure> SITE = ResourceKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath("gems",
                    V5_TRIAL && !V5_ACCEPTANCE ? "undercard_v5_trial" : "undercard"));
    private static final TagKey<Structure> TARGET = TagKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath("gems",
                    V5_TRIAL && !V5_ACCEPTANCE ? "undercard_v5_probe" : "undercard_probe"));
    private static final TagKey<Biome> V5_BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath("gems", "has_structure/undercard_v5_trial"));

    private record Survey(String seed, String result, BoundingBox bounds,
                          BlockPos root, BlockPos gate, BlockPos bell,
                          BlockPos post, BlockPos exit, int startX, int startZ) {
        boolean ready() { return "READY".equals(result); }
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!Boolean.getBoolean("gems.undercardNaturalGate")) return;
        if (V5_ACCEPTANCE) {
            runV5Acceptance(context);
            return;
        }
        boolean diagnostic = Boolean.getBoolean("gems.undercardNaturalDiagnostic");
        // The V5 trial is required to be realistically discoverable. In a
        // 12-chunk spread grid, 42 locate rings cover roughly eight km.
        int searchChunks = V5_TRIAL ? Math.min(searchChunks(diagnostic), 42)
                : searchChunks(diagnostic);
        List<String> outcomes = new ArrayList<>();
        int ready = 0;
        int exercised = 0;
        TestWorldSave reopenedSave = null;
        Survey reopenedSite = null;
        for (String seed : SEEDS) {
            try (TestSingleplayerContext world = context.worldBuilder()
                    .setUseConsistentSettings(false)
                    .adjustSettings(settings -> {
                        settings.setSeed(seed);
                        settings.setGenerateStructures(true);
                    }).create()) {
                world.getConnection().waitForChunksRender();
                Survey site = world.getServer().computeOnServer(server ->
                        survey(server.overworld(), seed, searchChunks));
                outcomes.add(seed + ":" + site.result());
                if (diagnostic) {
                    System.out.println("UNDERCARD_NATURAL_DIAGNOSTIC seed=" + seed
                            + " searchChunks=" + searchChunks
                            + " result=" + site.result()
                            + " cumulative=" + UndercardStructure.diagnosticCounts());
                }
                if (!site.ready()) continue;
                ready++;
                if (exercised == 0) {
                    reopenedSave = world.getWorldSave();
                    reopenedSite = site;
                    world.getServer().runOnServer(server ->
                            arriveAtExterior(server.overworld(),
                                    world.getConnection().getServerPlayer(), site));
                    world.getConnection().waitForChunksRender();
                    context.waitTicks(80);
                    context.takeScreenshot("undercard-natural-quarry-exterior");
                    world.getServer().runOnServer(server ->
                            arriveAtSurface(server.overworld(),
                                    world.getConnection().getServerPlayer(), site));
                    world.getConnection().waitForChunksRender();
                    context.waitTicks(100);
                    context.takeScreenshot("undercard-natural-quarry-arrival");
                    world.getServer().runOnServer(server ->
                            arriveAtBell(server.overworld(),
                                    world.getConnection().getServerPlayer(), site));
                    world.getConnection().waitForChunksRender();
                    context.waitTicks(40);
                    world.getServer().runOnServer(server ->
                            startSolo(server.overworld(),
                                    world.getConnection().getServerPlayer(), site));
                    context.waitTicks(10);
                    context.takeScreenshot("undercard-natural-solo-ring");
                    context.waitTicks(34);
                    world.getServer().runOnServer(server ->
                            scoreSoloCounter(server.overworld(),
                                    world.getConnection().getServerPlayer(), site, 0));
                    context.waitTicks(52);
                    world.getServer().runOnServer(server ->
                            scoreSoloCounter(server.overworld(),
                                    world.getConnection().getServerPlayer(), site, 1));
                    context.waitTicks(52);
                    world.getServer().runOnServer(server ->
                            scoreSoloCounter(server.overworld(),
                                    world.getConnection().getServerPlayer(), site, 2));
                    world.getServer().runOnServer(server ->
                            assertSoloAndReturn(server.overworld(),
                                    world.getConnection().getServerPlayer(), site));
                    exercised++;
                }
            }
        }
        if (reopenedSave != null) {
            Survey persisted = reopenedSite;
            try (TestSingleplayerContext reopened = reopenedSave.open()) {
                reopened.getConnection().waitForChunksRender();
                reopened.getServer().runOnServer(server -> {
                    ServerLevel level = server.overworld();
                    level.getChunk(persisted.gate().getX() >> 4,
                            persisted.gate().getZ() >> 4);
                    if (!level.getBlockState(persisted.root()).is(UndercardBlocks.ROOT)
                            || !level.getBlockState(persisted.gate()).is(Blocks.LADDER)) {
                        throw new IllegalStateException("Undercard saved root or opened gate lost on reload");
                    }
                    if (!UndercardState.get(server).hasEarned(
                            reopened.getConnection().getServerPlayer().getUUID(),
                            persisted.bell().asLong())) {
                        throw new IllegalStateException("Natural solo first-clear receipt lost on reload");
                    }
                });
            }
        }
        System.out.println("UNDERCARD_TEST_ONLY_NATURAL mode="
                + (diagnostic ? "diagnostic" : "acceptance")
                + " template=" + (V5_TRIAL ? "v5_trial" : "v4")
                + " searchChunks=" + searchChunks + " seeds=" + SEEDS.length
                + " ready=" + ready + " soloAndReturn=" + exercised
                + " outcomes=" + outcomes);
        if (!diagnostic && (ready == 0 || exercised != 1)) {
            throw new IllegalStateException("No safe test-only Undercard start: " + outcomes);
        }
    }

    /** One seed per JVM so the driver can copy a closed save into a fresh run. */
    private static void runV5Acceptance(ClientGameTestContext context) {
        if (!V5_TRIAL) throw new IllegalStateException("V5 acceptance requires trial placement");
        String seed = System.getProperty("gems.undercardV5Seed", "");
        if (!seed.equals(V5_SEED_A) && !seed.equals(V5_SEED_B)) {
            throw new IllegalArgumentException("Undercard v5 seed is not predeclared: " + seed);
        }
        boolean restart = Boolean.getBoolean("gems.undercardV5Restart");
        boolean control = Boolean.getBoolean("gems.undercardV5Control");
        if (restart && control) throw new IllegalArgumentException("Choose one V5 phase");
        if (control) {
            String expected = System.getProperty("gems.undercardV5Expected", "");
            BoundingBox box = boxFromReceipt(expected);
            try (TestSingleplayerContext world = context.worldBuilder()
                    .setUseConsistentSettings(false)
                    .adjustSettings(settings -> {
                        settings.setSeed(seed);
                        settings.setGenerateStructures(false);
                    }).create()) {
                String hash = world.getServer().computeOnServer(server ->
                        outsideHash(server.overworld(), box));
                System.out.println("UNDERCARD_V5_OUTSIDE_CONTROL seed=" + seed
                        + " sha256=" + hash);
            }
            return;
        }
        if (restart) {
            Path save = context.computeOnClient(client -> client.gameDirectory.toPath()
                    .resolve("saves").resolve("New World"));
            if (!Files.isRegularFile(save.resolve("level.dat"))) {
                throw new IllegalStateException("Restart requires a copied closed world save");
            }
            try (TestSingleplayerContext world = new TestWorldSaveImpl(context, save).open()) {
                world.getConnection().waitForChunksRender();
                String expected = System.getProperty("gems.undercardV5Expected", "");
                world.getServer().runOnServer(server -> verifyV5Restart(
                        server.overworld(), world.getConnection().getServerPlayer(), expected));
                System.out.println("UNDERCARD_V5_PASS phase=restart seed=" + seed
                        + " receipt=" + expected);
            }
            return;
        }

        Survey site;
        String outside;
        UUID playerId;
        try (TestSingleplayerContext world = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(settings -> {
                    settings.setSeed(seed);
                    settings.setGenerateStructures(true);
                }).create()) {
            world.getConnection().waitForChunksRender();
            site = world.getServer().computeOnServer(server ->
                    survey(server.overworld(), seed, 42));
            if (!site.ready()) throw new IllegalStateException("V5 natural site " + seed
                    + " rejected: " + site.result() + " diagnostics="
                    + UndercardStructure.diagnosticCounts());
            world.getServer().runOnServer(server -> {
                ServerLevel level = server.overworld();
                int dx = Integer.compare(site.gate().getX(), site.root().getX());
                int dz = Integer.compare(site.gate().getZ(), site.root().getZ());
                BlockPos porch = site.gate().offset(-6 * dx, 0, -6 * dz);
                int topAtPorch = level.getHeight(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        porch.getX(), porch.getZ());
                int floor = site.post().getY() - 1;
                // The quarry headhouse roofs this cell. Heightmap reads that
                // authored roof, while the reachable entrance is at porch feet.
                int depth = porch.getY() - floor;
                if (!level.getBiome(porch).is(V5_BIOMES) || depth < 24 || depth > 36) {
                    throw new IllegalStateException("V5 site missed dryland biome or buried"
                            + " hall depth: porch=" + porch + " depth=" + depth
                            + " topAtPorch=" + topAtPorch
                            + " biome=" + level.getBiome(porch));
                }
                System.out.println("UNDERCARD_V5_LOCATION seed=" + seed + " porch="
                        + porch + " floor=" + floor + " depth=" + depth
                        + " topAtPorch=" + topAtPorch
                        + " biome=" + level.getBiome(porch));
            });
            playerId = world.getServer().computeOnServer(server ->
                    world.getConnection().getServerPlayer().getUUID());
            world.getServer().runOnServer(server -> arriveAtExterior(server.overworld(),
                    world.getConnection().getServerPlayer(), site));
            world.getConnection().waitForChunksRender();
            context.waitTicks(40);
            context.takeScreenshot("undercard-v5-" + seed + "-hidden-exterior");
            walkToPorch(context, world, site);
            context.takeScreenshot("undercard-v5-" + seed + "-arrival");
            world.getServer().runOnServer(server -> arriveAtBell(server.overworld(),
                    world.getConnection().getServerPlayer(), site));
            world.getConnection().waitForChunksRender();
            context.waitTicks(20);
            world.getServer().runOnServer(server -> startSolo(server.overworld(),
                    world.getConnection().getServerPlayer(), site));
            context.waitTicks(10);
            context.takeScreenshot("undercard-v5-" + seed + "-lit-ring");
            context.waitTicks(34);
            for (int round = 0; round < 3; round++) {
                int current = round;
                world.getServer().runOnServer(server -> scoreSoloCounter(
                        server.overworld(), world.getConnection().getServerPlayer(),
                        site, current));
                context.waitTicks(52);
            }
            world.getServer().runOnServer(server -> assertSoloAndReturn(
                    server.overworld(), world.getConnection().getServerPlayer(), site));
            context.waitTicks(8);
            world.getServer().runOnServer(server -> {
                ServerPlayer player = world.getConnection().getServerPlayer();
                if (!player.isAlive() || player.level() != server.overworld()
                        || player.blockPosition().distSqr(site.gate()) > 32L * 32L) {
                    throw new IllegalStateException("Service exit did not return player safely");
                }
            });
            // This is the closed-world baseline for the copied fresh-JVM
            // reopen, after every gameplay step and natural tick in the run.
            outside = world.getServer().computeOnServer(server ->
                    outsideHash(server.overworld(), site.bounds()));
        }

        String receipt = receipt(site, playerId);
        System.out.println("UNDERCARD_V5_OUTSIDE seed=" + seed + " sha256=" + outside);
        System.out.println("UNDERCARD_V5_PASS phase=initial seed=" + seed
                + " receipt=" + receipt);
    }

    private static void walkToPorch(ClientGameTestContext context,
                                   TestSingleplayerContext world, Survey site) {
        int dx = Integer.compare(site.gate().getX(), site.root().getX());
        int dz = Integer.compare(site.gate().getZ(), site.root().getZ());
        BlockPos porch = site.gate().offset(-6 * dx, 0, -6 * dz);
        List<NaturalDistrictRouteProbe.Step> steps = new ArrayList<>();
        for (int distance = 10; distance >= 0; distance--) {
            int x = porch.getX() - distance * dx;
            int z = porch.getZ() - distance * dz;
            int feetY = distance == 0 ? porch.getY()
                    : world.getServer().computeOnServer(server ->
                            server.overworld().getHeight(
                                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
            steps.add(new NaturalDistrictRouteProbe.Step(new BlockPos(x, feetY, z), feetY));
        }
        var first = steps.getFirst();
        world.getServer().runOnServer(server -> {
            ServerPlayer player = world.getConnection().getServerPlayer();
            player.teleportTo(server.overworld(), first.feet().getX() + 0.5,
                    first.standingY(), first.feet().getZ() + 0.5,
                    EnumSet.noneOf(Relative.class), yawToward(first.feet(), porch), 0, false);
        });
        context.waitTicks(5);
        NaturalDistrictRouteProbe.walkPath(context, world, steps, "undercard-v5",
                "concealed-quarry-porch");
        world.getServer().runOnServer(server -> {
            ServerPlayer player = world.getConnection().getServerPlayer();
            if (!player.isAlive() || player.blockPosition().distSqr(porch) > 4) {
                throw new IllegalStateException("Normal-input quarry arrival missed porch");
            }
        });
    }

    private static String receipt(Survey site, UUID player) {
        BoundingBox box = site.bounds();
        return site.startX() + "," + site.startZ() + ";"
                + box.minX() + "," + box.minY() + "," + box.minZ() + ","
                + box.maxX() + "," + box.maxY() + "," + box.maxZ() + ";"
                + site.root().asLong() + ";" + site.gate().asLong() + ";"
                + site.bell().asLong() + ";" + site.post().asLong() + ";"
                + site.exit().asLong() + ";" + player;
    }

    private static void verifyV5Restart(ServerLevel level, ServerPlayer player,
                                        String expected) {
        String[] fields = expected.split(";");
        if (fields.length != 8) throw new IllegalArgumentException("Invalid saved V5 receipt");
        String[] start = fields[0].split(",");
        if (start.length != 2) {
            throw new IllegalArgumentException("Invalid saved V5 coordinates");
        }
        int cx = Integer.parseInt(start[0]);
        int cz = Integer.parseInt(start[1]);
        BoundingBox expectedBox = boxFromReceipt(expected);
        Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValue(SITE);
        if (structure == null) throw new IllegalStateException("V5 trial registry disappeared");
        StructureStart saved = level.structureManager().getStartForStructure(structure,
                level.getChunk(cx, cz));
        if (saved == null || !saved.isValid()
                || !UndercardStructure.oneAuthoredPiece(saved.getPieces())
                || !saved.getPieces().getFirst().getBoundingBox().equals(expectedBox)) {
            throw new IllegalStateException("V5 saved start or exact bounds changed on restart");
        }
        BlockPos root = BlockPos.of(Long.parseLong(fields[2]));
        BlockPos gate = BlockPos.of(Long.parseLong(fields[3]));
        BlockPos bell = BlockPos.of(Long.parseLong(fields[4]));
        BlockPos post = BlockPos.of(Long.parseLong(fields[5]));
        BlockPos exit = BlockPos.of(Long.parseLong(fields[6]));
        UUID winner = UUID.fromString(fields[7]);
        level.getChunk(root.getX() >> 4, root.getZ() >> 4);
        level.getChunk(gate.getX() >> 4, gate.getZ() >> 4);
        if (!level.getBlockState(root).is(UndercardBlocks.ROOT)
                || !level.getBlockState(gate).is(Blocks.LADDER)
                || !level.getBlockState(bell).is(UndercardBlocks.BELL)
                || !level.getBlockState(post).is(UndercardBlocks.SPARRING_POST)
                || !level.getBlockState(exit).is(UndercardBlocks.EXIT)
                || !ladderContinuous(level, gate, true)
                || !litAndEmpty(level, post, expectedBox)
                || !UndercardState.get(level.getServer()).hasEarned(winner, bell.asLong())) {
            throw new IllegalStateException("V5 saved venue, safe entry, or first-clear reward lost");
        }
        if (!player.getUUID().equals(winner)) {
            throw new IllegalStateException("Restart opened under a different winning player");
        }
        assertWinnerReceipt(player, bell);
    }

    private static BoundingBox boxFromReceipt(String expected) {
        String[] fields = expected.split(";");
        if (fields.length != 8) throw new IllegalArgumentException("Invalid V5 receipt");
        String[] extents = fields[1].split(",");
        if (extents.length != 6) throw new IllegalArgumentException("Invalid V5 box");
        return new BoundingBox(Integer.parseInt(extents[0]),
                Integer.parseInt(extents[1]), Integer.parseInt(extents[2]),
                Integer.parseInt(extents[3]), Integer.parseInt(extents[4]),
                Integer.parseInt(extents[5]));
    }

    private static String outsideHash(ServerLevel level, BoundingBox box) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int x = box.minX() - 2; x <= box.maxX() + 2; x++) {
                for (int z = box.minZ() - 2; z <= box.maxZ() + 2; z++) {
                    if (x >= box.minX() && x <= box.maxX()
                            && z >= box.minZ() && z <= box.maxZ()) continue;
                    level.getChunk(x >> 4, z >> 4);
                    for (int y = box.minY() - 4; y <= box.maxY() + 4; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        digest.update((x + "," + y + "," + z + ":"
                                + level.getBlockState(pos) + ":"
                                + level.getFluidState(pos) + "\n")
                                .getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static int searchChunks(boolean diagnostic) {
        String configured = System.getProperty("gems.undercardNaturalSearchChunks", "512");
        int radius;
        try {
            radius = Integer.parseInt(configured);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Invalid Undercard search radius: "
                    + configured, invalid);
        }
        if (radius < 1 || radius > 512 || (!diagnostic && radius != 512)) {
            throw new IllegalArgumentException("Undercard search radius must be 1..512"
                    + " and only diagnostic mode may use less than 512: " + radius);
        }
        return radius;
    }

    private static Survey survey(ServerLevel level, String seed, int searchChunks) {
        if (V5_ACCEPTANCE) assertEffectiveV5TrialPack(level);
        if (Boolean.getBoolean("gems.undercardNaturalDiagnostic")) {
            var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            Structure registered = structures.getValue(SITE);
            int tagged = 0;
            for (var holder : structures.getTagOrEmpty(TARGET)) {
                if (holder.value() == registered) tagged++;
            }
            System.out.println("UNDERCARD_REGISTRY seed=" + seed
                    + " structure=" + (registered == null ? "missing" : registered.getClass().getSimpleName())
                    + " targetMatches=" + tagged);
        }
        BlockPos located = level.findNearestMapStructure(TARGET, new BlockPos(0, 80, 0),
                searchChunks, false);
        if (located == null) {
            if (V5_ACCEPTANCE) v5CandidateDiagnostics(level, searchChunks);
            return empty(seed, "NO_START");
        }
        Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValue(SITE);
        if (structure == null) return empty(seed, "NO_REGISTRY");
        StructureStart start = null;
        int foundX = 0;
        int foundZ = 0;
        int chunkX = located.getX() >> 4;
        int chunkZ = located.getZ() >> 4;
        for (int radius = 0; radius <= 5 && start == null; radius++) {
            for (int dx = -radius; dx <= radius && start == null; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    var chunk = level.getChunk(chunkX + dx, chunkZ + dz);
                    StructureStart candidate = level.structureManager()
                            .getStartForStructure(structure, chunk);
                    if (candidate != null && candidate.isValid()) {
                        start = candidate;
                        foundX = chunkX + dx;
                        foundZ = chunkZ + dz;
                        break;
                    }
                }
            }
        }
        if (start == null) return empty(seed, "MISSING_SAVED_START");
        if (!UndercardStructure.oneAuthoredPiece(start.getPieces())) {
            return empty(seed, "WRONG_PIECE");
        }
        BoundingBox box = start.getPieces().getFirst().getBoundingBox();
        if (box.getXSpan() != 63 || box.getYSpan() != 48 || box.getZSpan() != 63) {
            return empty(seed, "WRONG_VENUE_SIZE");
        }
        if (V5_TRIAL && Math.hypot(box.getCenter().getX(),
                box.getCenter().getZ()) > 8_000) {
            return empty(seed, "BEYOND_8KM_TRAVEL_GATE");
        }
        for (int cx = (box.minX() - 128) >> 4; cx <= (box.maxX() + 128) >> 4; cx++) {
            for (int cz = (box.minZ() - 128) >> 4; cz <= (box.maxZ() + 128) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        GuardedNewChunkSiteAccess access = new GuardedNewChunkSiteAccess(level, Set.of(structure));
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                if (!access.isNewChunk(new net.minecraft.world.level.ChunkPos(cx, cz))) {
                    return empty(seed, "OLD_CHUNK");
                }
            }
        }
        if (access.protectedVolume(box) != SiteAccess.Inspection.CLEAR) {
            return empty(seed, "PROTECTED_OR_UNKNOWN");
        }
        BlockPos root = null;
        BlockPos gate = null;
        BlockPos bell = null;
        BlockPos post = null;
        BlockPos exit = null;
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Block block = level.getBlockState(pos).getBlock();
                    if (block == UndercardBlocks.ROOT) root = unique(root, pos);
                    else if (block == UndercardBlocks.GATE) gate = unique(gate, pos);
                    else if (block == UndercardBlocks.BELL) bell = unique(bell, pos);
                    else if (block == UndercardBlocks.SPARRING_POST) post = unique(post, pos);
                    else if (block == UndercardBlocks.EXIT) exit = unique(exit, pos);
                }
            }
        }
        if (root == null || gate == null || bell == null || post == null || exit == null) {
            return empty(seed, "MISSING_MARKER");
        }
        if (!safeSurfaceApproach(level, root, gate)) {
            if (V5_ACCEPTANCE) System.out.println("UNDERCARD_V5_BLOCKED_APPROACH"
                    + " root=" + root + " gate=" + gate + " box=" + box);
            return empty(seed, "BLOCKED_APPROACH");
        }
        if (!ladderContinuous(level, gate, false)) {
            return empty(seed, "BLOCKED_LADDER");
        }
        if (!noDeepHazard(level, root, box)) return empty(seed, "DEEP_HAZARD");
        if (!litAndEmpty(level, post, box)) return empty(seed, "DARK_OR_OCCUPIED_RING");
        UndercardSiteIntegrity.Inspection production =
                UndercardSiteIntegrity.inspectLoaded(level, start, structure);
        if (production.status() != UndercardSiteIntegrity.Status.READY) {
            return empty(seed, "GATE_AUDIT_" + production.reason());
        }
        return new Survey(seed, "READY", box, root, gate, bell, post, exit,
                foundX, foundZ);
    }

    /** Resolve loaded registries before locate; overlay files alone do not prove pack priority. */
    private static void assertEffectiveV5TrialPack(ServerLevel level) {
        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var sets = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        StructureSet stable = sets.getValue(ResourceKey.create(Registries.STRUCTURE_SET,
                Identifier.fromNamespaceAndPath("gems", "undercard")));
        if (stable == null || !(stable.placement() instanceof RandomSpreadStructurePlacement spread)
                || spread.spacing() != 12 || spread.separation() != 8
                || !hasExpectedSalt(spread, 26030591) || stable.structures().size() != 1
                || !stable.structures().getFirst().structure().is(SITE)
                || stable.structures().getFirst().weight() != 1) {
            throw new IllegalStateException("Undercard V5 overlay lost pack priority: "
                    + "effective stable set is not the 12/8/26030591 candidate");
        }
        ResourceKey<Structure> oldTrial = ResourceKey.create(Registries.STRUCTURE,
                Identifier.fromNamespaceAndPath("gems", "undercard_v5_trial"));
        StructureSet displaced = sets.getValue(ResourceKey.create(Registries.STRUCTURE_SET,
                Identifier.fromNamespaceAndPath("gems", "undercard_v5_trial")));
        if (displaced == null
                || !(displaced.placement() instanceof RandomSpreadStructurePlacement oldSpread)
                || oldSpread.spacing() != 4096 || oldSpread.separation() != 4095
                || !hasExpectedSalt(oldSpread, 26030592) || displaced.structures().size() != 1
                || !displaced.structures().getFirst().structure().is(oldTrial)
                || displaced.structures().getFirst().weight() != 1) {
            throw new IllegalStateException("Undercard V5 overlay lost pack priority: "
                    + "old trial set is not displaced");
        }
        int targetCount = 0;
        boolean targetsStable = false;
        for (var holder : structures.getTagOrEmpty(TARGET)) {
            targetCount++;
            targetsStable |= holder.is(SITE);
        }
        if (structures.getValue(SITE) == null || targetCount != 1 || !targetsStable) {
            throw new IllegalStateException("Undercard V5 overlay lost pack priority: "
                    + "probe tag must target only the stable structure");
        }
        System.out.println("UNDERCARD_V5_EFFECTIVE_PACK_PASS stable=12/8/26030591"
                + " target=undercard oldTrial=4096/4095/26030592");
    }

    private static boolean hasExpectedSalt(RandomSpreadStructurePlacement placement, int expected) {
        JsonElement encoded = StructurePlacement.CODEC.encodeStart(JsonOps.INSTANCE, placement)
                .result().orElse(null);
        if (!(encoded instanceof JsonObject object)) return false;
        JsonElement salt = object.get("salt");
        return salt != null && salt.isJsonPrimitive() && salt.getAsJsonPrimitive().isNumber()
                && salt.getAsInt() == expected;
    }

    private static BlockPos unique(BlockPos existing, BlockPos found) {
        if (existing != null) throw new IllegalStateException("Duplicate Undercard marker");
        return found;
    }

    /** Bounded, noise-only evidence when the acceptance locate finds no start. */
    private static void v5CandidateDiagnostics(ServerLevel level, int radius) {
        var sets = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        StructureSet set = sets.getValue(ResourceKey.create(Registries.STRUCTURE_SET,
                Identifier.fromNamespaceAndPath("gems", "undercard")));
        Holder<Structure> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getOrThrow(SITE);
        if (set == null || !(set.placement() instanceof RandomSpreadStructurePlacement spread)) {
            System.out.println("UNDERCARD_V5_NO_START_DIAGNOSTIC set=missing_or_nonspread");
            return;
        }
        var source = level.getChunkSource();
        var state = source.getGeneratorState();
        int spacing = spread.spacing();
        List<ChunkPos> candidates = new ArrayList<>();
        Set<ChunkPos> seen = new HashSet<>();
        // Vanilla locate interprets radius as spread regions, not chunks.
        // Mirror its 42-ring candidate area before sampling failed starts.
        for (int rx = -radius; rx <= radius; rx++) {
            for (int rz = -radius; rz <= radius; rz++) {
                ChunkPos chunk = spread.getPotentialStructureChunk(level.getSeed(),
                        rx * spacing, rz * spacing);
                if (spread.isStructureChunk(state, chunk.x(), chunk.z())
                        && seen.add(chunk)) candidates.add(chunk);
            }
        }
        candidates.sort(Comparator.comparingInt((ChunkPos chunk) ->
                Math.max(Math.abs(chunk.x()), Math.abs(chunk.z())))
                .thenComparingInt(ChunkPos::x).thenComparingInt(ChunkPos::z));
        System.out.println("UNDERCARD_V5_NO_START_DIAGNOSTIC candidates="
                + candidates.size() + " rings=" + radius + " spacing=" + spacing
                + " structure="
                + holder.value().getClass().getName() + " countersBefore="
                + UndercardStructure.diagnosticCounts());
        int sampled = Math.min(12, candidates.size());
        var generator = source.getGenerator();
        var resolver = generator.getBiomeSource().createUncachedResolver(
                source.randomState());
        for (int index = 0; index < sampled; index++) {
            ChunkPos chunk = candidates.get(index);
            boolean loadedBefore = source.hasChunk(chunk.x(), chunk.z());
            var randomState = source.randomState();
            int centerX = chunk.getMinBlockX() + 8;
            int centerZ = chunk.getMinBlockZ() + 8;
            int surface = generator.getBaseHeight(centerX, centerZ,
                    Heightmap.Types.WORLD_SURFACE_WG, level, randomState) - 1;
            boolean surfaceBiome = holder.value().biomes().contains(resolver.getNoiseBiome(
                    centerX >> 2, surface >> 2, centerZ >> 2));
            boolean hallBiome = holder.value().biomes().contains(resolver.getNoiseBiome(
                    centerX >> 2, (surface - 32) >> 2, centerZ >> 2));
            String countersBefore = UndercardStructure.diagnosticCounts();
            StructureStart start = holder.value().generate(holder, level.dimension(),
                    level.registryAccess(), generator, generator.getBiomeSource(),
                    randomState.createClimateSampler(SamplerContext.EMPTY_UNCACHED),
                    randomState, level.getServer().getStructureTemplateManager(),
                    level.getSeed(), chunk, 0, level, holder.value().biomes()::contains);
            System.out.println("UNDERCARD_V5_NO_START_CANDIDATE rank=" + index
                    + " chunk=" + chunk + " valid=" + start.isValid()
                    + " pieces=" + start.getPieces().size()
                    + " surfaceY=" + surface + " surfaceBiome=" + surfaceBiome
                    + " hallBiome=" + hallBiome
                    + " loadedBefore=" + loadedBefore
                    + " loadedAfter=" + source.hasChunk(chunk.x(), chunk.z())
                    + " countersBefore=" + countersBefore
                    + " counters=" + UndercardStructure.diagnosticCounts());
        }
    }

    private static boolean safeSurfaceApproach(ServerLevel level, BlockPos root, BlockPos gate) {
        int dx = Integer.compare(gate.getX(), root.getX());
        int dz = Integer.compare(gate.getZ(), root.getZ());
        if (Math.abs(dx) + Math.abs(dz) != 1) return false;
        BlockPos doorway = gate.offset(-6 * dx, 0, -6 * dz);
        if (!walkableAt(level, doorway.below())) {
            entryDiagnostic("doorway", doorway.below(), gate.getY() - 1, level);
            return false;
        }
        int previous = gate.getY() - 1;
        for (int step = 2; step <= 10; step++) {
            int x = doorway.getX() - step * dx;
            int z = doorway.getZ() - step * dz;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            if (Math.abs(y - previous) > 1
                    || !walkableAt(level, new BlockPos(x, y, z))) {
                BlockPos floor = new BlockPos(x, y, z);
                entryDiagnostic("step=" + step, floor, previous, level);
                return false;
            }
            previous = y;
        }
        return true;
    }

    private static void entryDiagnostic(String stage, BlockPos floor, int previous,
                                        ServerLevel level) {
        if (Boolean.getBoolean("gems.undercardNaturalDiagnostic") || V5_ACCEPTANCE) {
            System.out.println("UNDERCARD_ENTRY_FAIL " + stage + " floor=" + floor
                    + " previousY=" + previous
                    + " floorState=" + level.getBlockState(floor)
                    + " feetState=" + level.getBlockState(floor.above())
                    + " headState=" + level.getBlockState(floor.above(2))
                    + " floorFluid=" + level.getFluidState(floor)
                    + " feetFluid=" + level.getFluidState(floor.above()));
        }
    }

    private static boolean walkableAt(ServerLevel level, BlockPos floor) {
        return level.getBlockState(floor).isSolid()
                && level.getFluidState(floor).isEmpty()
                && level.getBlockState(floor.above()).getCollisionShape(level,
                        floor.above()).isEmpty()
                && level.getBlockState(floor.above(2)).getCollisionShape(level,
                        floor.above(2)).isEmpty()
                && level.getFluidState(floor.above()).isEmpty();
    }

    private static boolean ladderContinuous(ServerLevel level, BlockPos gate,
                                             boolean gateOpened) {
        for (int y = gate.getY() - 28; y <= gate.getY() + 1; y++) {
            BlockPos rung = new BlockPos(gate.getX(), y, gate.getZ());
            if (rung.equals(gate) && !gateOpened) {
                if (!level.getBlockState(rung).is(UndercardBlocks.GATE)) return false;
            } else if (!level.getBlockState(rung).is(Blocks.LADDER)) return false;
            if (!level.getFluidState(rung).isEmpty()) return false;
        }
        return true;
    }

    private static boolean noDeepHazard(ServerLevel level, BlockPos root, BoundingBox box) {
        Structure city = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValue(ResourceKey.create(Registries.STRUCTURE,
                        Identifier.fromNamespaceAndPath("minecraft", "ancient_city")));
        for (int cx = (box.minX() - 128) >> 4; cx <= (box.maxX() + 128) >> 4; cx++) {
            for (int cz = (box.minZ() - 128) >> 4; cz <= (box.maxZ() + 128) >> 4; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) return false;
                if (city != null) {
                    var references = chunk.getAllReferences().get(city);
                    if (references != null && !references.isEmpty()) return false;
                }
                var sections = chunk.getSections();
                for (int index = 0; index < sections.length; index++) {
                    var section = sections[index];
                    if (!section.maybeHas(state -> state.is(Blocks.SCULK_SENSOR)
                            || state.is(Blocks.SCULK_SHRIEKER))) continue;
                    int y0 = level.getSectionYFromSectionIndex(index) << 4;
                    for (int x = 0; x < 16; x++) {
                        for (int y = 0; y < 16; y++) {
                            for (int z = 0; z < 16; z++) {
                                var state = section.getBlockState(x, y, z);
                                if (!state.is(Blocks.SCULK_SENSOR)
                                        && !state.is(Blocks.SCULK_SHRIEKER)) continue;
                                BlockPos pos = new BlockPos((cx << 4) + x, y0 + y,
                                        (cz << 4) + z);
                                if (pos.distSqr(root) <= 128L * 128L) return false;
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    private static boolean litAndEmpty(ServerLevel level, BlockPos post, BoundingBox box) {
        int floorY = post.getY() - 1;
        for (int x = post.getX() - 12; x <= post.getX() + 12; x++) {
            for (int z = post.getZ() - 12; z <= post.getZ() + 12; z++) {
                BlockPos floor = new BlockPos(x, floorY, z);
                BlockPos feet = floor.above();
                if (!level.getBlockState(floor).isSolid()
                        || !level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                        || !level.getBlockState(feet.above()).getCollisionShape(level,
                                feet.above()).isEmpty()) continue;
                if (level.getBrightness(LightLayer.BLOCK, feet) < 1) return false;
            }
        }
        AABB chamber = new AABB(box.minX() + 4, floorY, box.minZ() + 4,
                box.maxX() - 3, floorY + 18, box.maxZ() - 3);
        return level.getEntitiesOfClass(Mob.class, chamber, mob ->
                mob.getType().getCategory() == MobCategory.MONSTER).isEmpty();
    }

    private static void arriveAtSurface(ServerLevel level, ServerPlayer player, Survey site) {
        int dx = Integer.compare(site.gate().getX(), site.root().getX());
        int dz = Integer.compare(site.gate().getZ(), site.root().getZ());
        BlockPos arrival = site.gate().offset(-6 * dx, 0, -6 * dz);
        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(level, arrival.getX() + 0.5, arrival.getY(),
                arrival.getZ() + 0.5, EnumSet.noneOf(Relative.class),
                yawToward(arrival, site.gate()), 0, false);
    }

    private static void arriveAtExterior(ServerLevel level, ServerPlayer player, Survey site) {
        int dx = Integer.compare(site.gate().getX(), site.root().getX());
        int dz = Integer.compare(site.gate().getZ(), site.root().getZ());
        BlockPos arrival = site.gate().offset(-15 * dx, 0, -15 * dz);
        int feetY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                arrival.getX(), arrival.getZ());
        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(level, arrival.getX() + 0.5, feetY,
                arrival.getZ() + 0.5, EnumSet.noneOf(Relative.class),
                yawToward(arrival, site.gate()), -22, false);
    }

    private static void arriveAtBell(ServerLevel level, ServerPlayer player, Survey site) {
        if (!level.getBlockState(site.gate()).is(Blocks.LADDER)) {
            throw new IllegalStateException("Natural Undercard gate did not open before arena arrival");
        }
        player.teleportTo(level, site.bell().getX() + 0.5, site.bell().getY() + 1,
                site.bell().getZ() + 0.5, EnumSet.noneOf(Relative.class),
                yawToward(site.bell(), site.post()), 0, false);
    }

    private static float yawToward(BlockPos from, BlockPos target) {
        return (float) Math.toDegrees(Math.atan2(
                from.getX() - target.getX(), target.getZ() - from.getZ()));
    }

    private static void startSolo(ServerLevel level, ServerPlayer player, Survey site) {
        InteractionResult result = UseBlockCallback.EVENT.invoker().interact(player, level,
                InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(site.bell()),
                        Direction.UP, site.bell(), false));
        if (result != InteractionResult.SUCCESS_SERVER) {
            throw new IllegalStateException("Natural Undercard bell ignored the solo request");
        }
        AABB room = new AABB(site.bounds().minX(), site.bounds().minY(),
                site.bounds().minZ(), site.bounds().maxX() + 1,
                site.bounds().maxY() + 1, site.bounds().maxZ() + 1);
        if (level.getEntitiesOfClass(UndercardConstructEntity.class, room).size() != 1) {
            throw new IllegalStateException("Natural Undercard bell did not spawn its solo construct"
                    + " (player=" + player.blockPosition() + ", post=" + site.post() + ")");
        }
    }

    private static void assertSoloAndReturn(ServerLevel level, ServerPlayer player, Survey site) {
        AABB room = new AABB(site.bounds().minX(), site.bounds().minY(),
                site.bounds().minZ(), site.bounds().maxX() + 1,
                site.bounds().maxY() + 1, site.bounds().maxZ() + 1);
        if (!level.getBlockState(site.gate()).is(Blocks.LADDER)) {
            throw new IllegalStateException("Guarded gate stayed sealed during natural solo test");
        }
        if (!level.getEntitiesOfClass(UndercardConstructEntity.class, room).isEmpty()
                || !UndercardState.get(level.getServer()).hasEarned(
                        player.getUUID(), site.bell().asLong())) {
            throw new IllegalStateException("Natural solo bout did not finish all three counters");
        }
        assertWinnerReceipt(player, site.bell());
        if (!ladderContinuous(level, site.gate(), true)) {
            throw new IllegalStateException("Guarded return gate did not open after site audit");
        }
        BlockPos serviceStand = null;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = site.exit().relative(direction);
            if (walkableAt(level, candidate.below())) {
                serviceStand = candidate;
                break;
            }
        }
        if (serviceStand == null) {
            throw new IllegalStateException("Service exit has no dry authored standing spot");
        }
        player.teleportTo(level, serviceStand.getX() + 0.5,
                serviceStand.getY(), serviceStand.getZ() + 0.5,
                EnumSet.noneOf(Relative.class),
                yawToward(serviceStand, site.exit()), 0, false);
        InteractionResult result = UseBlockCallback.EVENT.invoker().interact(player, level,
                InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(site.exit()),
                        Direction.UP, site.exit(), false));
        if (result != InteractionResult.SUCCESS_SERVER) {
            throw new IllegalStateException("Service exit control ignored the nearby player");
        }
        System.out.println("UNDERCARD_NATURAL_SITE box=" + site.bounds()
                + " noDeepHazard=true litRing=true noHostileMobs=true"
                + " soloThreeCounters=true guardedReturnLadder=true");
    }

    private static void scoreSoloCounter(ServerLevel level, ServerPlayer player,
                                         Survey site, int round) {
        AABB room = new AABB(site.bounds().minX(), site.bounds().minY(),
                site.bounds().minZ(), site.bounds().maxX() + 1,
                site.bounds().maxY() + 1, site.bounds().maxZ() + 1);
        List<UndercardConstructEntity> constructs =
                level.getEntitiesOfClass(UndercardConstructEntity.class, room);
        if (constructs.size() != 1) {
            throw new IllegalStateException("Natural solo round " + round
                    + " has " + constructs.size() + " constructs");
        }
        UndercardConstructEntity construct = constructs.getFirst();
        player.teleportTo(level, construct.getX() + 1.5, construct.getY(),
                construct.getZ(), EnumSet.noneOf(Relative.class), 0, 0, false);
        InteractionResult result = AttackEntityCallback.EVENT.invoker().interact(
                player, level, InteractionHand.MAIN_HAND, construct,
                new EntityHitResult(construct));
        if (result != InteractionResult.FAIL) {
            throw new IllegalStateException("Natural solo round " + round
                    + " attack bypassed the nonlethal counter callback");
        }
        if (round < 2 && construct.blockPosition().equals(
                round == 0 ? site.post().offset(-5, 0, 0)
                        : site.post().offset(5, 0, 0))) {
            throw new IllegalStateException("Natural solo round " + round
                    + " did not advance its lane");
        }
    }

    private static void assertWinnerReceipt(ServerPlayer player, BlockPos bell) {
        CompoundTag saved = ((GemsPersistentDataHolder) player).gems$getPersistentData();
        CompoundTag receipts = saved.getCompound("UndercardReceipts").orElseThrow(() ->
                new IllegalStateException("Winner's Brass delivery receipt is missing"));
        if (!receipts.getBooleanOr(Long.toString(bell.asLong()), false)) {
            throw new IllegalStateException("Winner's Brass delivery was not recorded");
        }
        int trophies = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            var name = stack.get(DataComponents.CUSTOM_NAME);
            if (stack.is(Items.COPPER_INGOT) && name != null
                    && name.getString().equals("Undercard Winner's Brass")) {
                trophies += stack.getCount();
            }
        }
        if (trophies != 1) {
            throw new IllegalStateException("Expected one saved Winner's Brass, found "
                    + trophies);
        }
    }

    private static Survey empty(String seed, String reason) {
        return new Survey(seed, reason, null, null, null, null, null, null, 0, 0);
    }
}
