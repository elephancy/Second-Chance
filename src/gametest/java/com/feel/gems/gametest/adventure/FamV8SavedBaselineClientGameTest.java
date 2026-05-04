package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.DomainBlocks;
import com.feel.gems.adventure.FamForumPersistentState;
import com.feel.gems.adventure.FriendDomainUnlockState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.impl.client.gametest.world.TestWorldSaveImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** A closed public-v8 Fam save to reopen after the public pool advances. */
@SuppressWarnings("UnstableApiUsage")
public final class FamV8SavedBaselineClientGameTest implements FabricClientGameTest {
    private static final String SEED = "SecondChanceBiomeSmokeB";
    private static final String ROOT = "gems:fam_forum_civic_arcades_v8";
    private static final String STORY = "gems:fam_story_hall_assembly_v6";
    private static final String SCHOOL = "gems:fam_open_school_assembly_v6";
    private static final String REFERENCE = "gems:fam_east_reference_v1";
    private static final String HEADER = "FAM_V8_SAVED_BASELINE_V1";
    private static final ResourceKey<Structure> PUBLIC = ResourceKey.create(
            Registries.STRUCTURE, Identifier.fromNamespaceAndPath("gems", "fam_forum"));

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!Boolean.getBoolean("gems.famV8SavedBaselineGate")) return;
        String phase = System.getProperty("gems.famV8SavedBaselinePhase", "");
        require(phase.equals("create") || phase.equals("reopen"),
                "Fam v8 baseline phase must be create or reopen");
        Path gameDir = context.computeOnClient(client -> client.gameDirectory.toPath());
        Path savePath = gameDir.resolve("saves").resolve("New World");
        Path receiptPath = gameDir.resolve("fam-v8-saved-baseline.txt");
        if (phase.equals("reopen")) {
            require(Files.isRegularFile(savePath.resolve("level.dat")),
                    "Reopen needs a copy of the closed v8 save: " + savePath);
            String expected = read(receiptPath);
            ChunkPos start = startChunk(expected);
            try (TestSingleplayerContext world = new TestWorldSaveImpl(context, savePath).open()) {
                world.getConnection().waitForChunksRender();
                String actual = world.getServer().computeOnServer(server ->
                        snapshot(server.overworld(), world.getConnection().getServerPlayer(),
                                start, false));
                require(actual.equals(expected), "Saved public v8 Fam blocks or start changed:\n"
                        + firstDifference(expected, actual));
            }
            System.out.println("FAM_V8_SAVED_REOPEN_PASS start=" + start
                    + " receipt=" + receiptPath);
            return;
        }

        require(!Files.exists(savePath) && !Files.exists(receiptPath),
                "Create requires a fresh disposable Fam v8 QA directory");
        TestWorldSave save;
        String expected;
        ChunkPos start;
        try (TestSingleplayerContext world = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(settings -> {
                    settings.setSeed(SEED);
                    settings.setGenerateStructures(true);
                }).create()) {
            world.getConnection().waitForChunksRender();
            start = world.getServer().computeOnServer(server ->
                    locate(server.overworld()));
            ChunkPos retained = start;
            expected = world.getServer().computeOnServer(server ->
                    snapshot(server.overworld(),
                            world.getConnection().getServerPlayer(), retained, true));
            save = world.getWorldSave();
        }
        require(Files.isRegularFile(savePath.resolve("level.dat")),
                "Fam v8 save did not close to disk");
        // Prove the receipt describes the closed save rather than just live memory.
        try (TestSingleplayerContext reopened = save.open()) {
            reopened.getConnection().waitForChunksRender();
            String persisted = reopened.getServer().computeOnServer(server ->
                    snapshot(server.overworld(),
                            reopened.getConnection().getServerPlayer(), start, false));
            require(expected.equals(persisted), "Fresh v8 save changed on its first reopen:\n"
                    + firstDifference(expected, persisted));
        }
        try {
            Files.writeString(receiptPath, expected, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
        } catch (IOException error) {
            throw new IllegalStateException("Could not write Fam v8 receipt", error);
        }
        System.out.println("FAM_V8_SAVED_BASELINE_PASS start=" + start
                + " receipt=" + receiptPath + " bytes=" + expected.length());
    }

    private static ChunkPos locate(ServerLevel level) {
        var tag = net.minecraft.tags.TagKey.create(Registries.STRUCTURE,
                Identifier.fromNamespaceAndPath("gems", "atlas_target/fam_forum"));
        BlockPos found = level.findNearestMapStructure(tag,
                new BlockPos(0, 80, 0), 128, false);
        require(found != null, "No public v8 Fam start within the Atlas reach on " + SEED);
        Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValue(PUBLIC);
        require(structure != null, "Public Fam structure is missing");
        for (int radius = 0; radius <= 5; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int cx = (found.getX() >> 4) + dx;
                    int cz = (found.getZ() >> 4) + dz;
                    StructureStart start = level.structureManager()
                            .getStartForStructure(structure, level.getChunk(cx, cz));
                    if (start != null && start.isValid()) return start.getChunkPos();
                }
            }
        }
        throw new IllegalStateException("Located public v8 Fam has no saved start near " + found);
    }

    private static String snapshot(ServerLevel level, ServerPlayer player,
                                   ChunkPos at, boolean activateProgress) {
        Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValue(PUBLIC);
        require(structure != null, "Public Fam structure disappeared");
        StructureStart start = level.structureManager().getStartForStructure(structure,
                level.getChunk(at.x(), at.z()));
        require(start != null && start.isValid() && start.getChunkPos().equals(at),
                "Saved public Fam start disappeared at " + at);
        Map<String, PoolElementStructurePiece> pieces = new HashMap<>();
        Set<String> allowed = Set.of(ROOT, STORY, SCHOOL, REFERENCE);
        for (StructurePiece raw : start.getPieces()) {
            require(raw instanceof PoolElementStructurePiece pooled
                            && pooled.getElement() instanceof SinglePoolElement,
                    "Unexpected saved public Fam piece " + raw.getClass());
            PoolElementStructurePiece pooled = (PoolElementStructurePiece) raw;
            String name = ((SinglePoolElement) pooled.getElement())
                    .getTemplateLocation().toString();
            require(allowed.contains(name) && pieces.putIfAbsent(name, pooled) == null,
                    "Unknown or duplicate public v8 Fam piece " + name);
        }
        require(pieces.keySet().containsAll(Set.of(ROOT, STORY, SCHOOL)),
                "Public Fam start is not the complete v8 campus: " + pieces.keySet());
        Map<String, BlockPos> controls = Map.of(
                "board", at(pieces.get(ROOT), 23, 6, 25),
                "story", at(pieces.get(STORY), 10, 6, 18),
                "school", at(pieces.get(SCHOOL), 28, 6, 18));
        Set<ChunkPos> footprint = new HashSet<>();
        for (PoolElementStructurePiece piece : pieces.values()) {
            BoundingBox box = piece.getBoundingBox();
            for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++)
                for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++)
                    footprint.add(new ChunkPos(cx, cz));
        }
        for (ChunkPos chunk : footprint) level.getChunk(chunk.x(), chunk.z());
        require(level.getBlockState(controls.get("board")).is(DomainBlocks.FAM_FORUM_BOARD),
                "Saved v8 board is missing");
        require(level.getBlockState(controls.get("story")).is(DomainBlocks.FAM_STORY_CLUE),
                "Saved v8 story note is missing");
        require(level.getBlockState(controls.get("school")).is(DomainBlocks.FAM_SCHOOL_CLUE),
                "Saved v8 school note is missing");
        if (activateProgress) {
            require(!FamForumPersistentState.get(level.getServer()).ready()
                            && !FriendDomainUnlockState.get(level.getServer()).cleared("fam"),
                    "Fresh v8 baseline already has Fam progress");
            use(player, level, controls.get("story"));
            use(player, level, controls.get("school"));
            use(player, level, controls.get("board"));
        }
        require(FamForumPersistentState.get(level.getServer()).ready()
                        && FriendDomainUnlockState.get(level.getServer()).cleared("fam"),
                "Saved Fam clues or server-wide forum unlock are missing");

        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        lines.add("seed=" + SEED);
        lines.add("public=gems:fam_forum");
        lines.add("startChunk=" + at.x() + "," + at.z());
        lines.add("worldgenBounds=" + bounds(start.getBoundingBox()));
        pieces.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> lines.add("piece=" + entry.getKey() + "|"
                        + entry.getValue().getRotation() + "|"
                        + bounds(entry.getValue().getBoundingBox())));
        controls.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> lines.add("control=" + entry.getKey() + "|"
                        + entry.getValue().getX() + "," + entry.getValue().getY()
                        + "," + entry.getValue().getZ() + "|"
                        + level.getBlockState(entry.getValue())));
        footprint.stream().sorted(Comparator.comparingInt(ChunkPos::x)
                        .thenComparingInt(ChunkPos::z))
                .forEach(chunk -> lines.add(hashChunk(level, chunk)));
        lines.add("famProgress=both-notes-and-shared-unlock");
        return String.join("\n", lines) + "\n";
    }

    private static String hashChunk(ServerLevel level, ChunkPos at) {
        var chunk = level.getChunk(at.x(), at.z());
        require(chunk.getPersistedStatus().isOrAfter(ChunkStatus.FULL),
                "Fam footprint chunk is not FULL: " + at);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int populated = 0;
            for (int y = level.getMinY(); y < level.getMaxY(); y++) {
                for (int z = at.getMinBlockZ(); z < at.getMinBlockZ() + 16; z++) {
                    for (int x = at.getMinBlockX(); x < at.getMinBlockX() + 16; x++) {
                        var state = chunk.getBlockState(pos.set(x, y, z));
                        if (state.isAir()) continue;
                        digest.update((x + "," + y + "," + z + "=" + state + "\n")
                                .getBytes(StandardCharsets.UTF_8));
                        populated++;
                    }
                }
            }
            return "chunk=" + at.x() + "," + at.z() + "|FULL|"
                    + populated + "|" + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static BlockPos at(PoolElementStructurePiece piece, int x, int y, int z) {
        BoundingBox box = piece.getBoundingBox();
        return switch (piece.getRotation()) {
            case NONE -> new BlockPos(box.minX() + x, box.minY() + y, box.minZ() + z);
            case CLOCKWISE_90 -> new BlockPos(box.maxX() - z, box.minY() + y, box.minZ() + x);
            case CLOCKWISE_180 -> new BlockPos(box.maxX() - x, box.minY() + y, box.maxZ() - z);
            case COUNTERCLOCKWISE_90 -> new BlockPos(box.minX() + z, box.minY() + y, box.maxZ() - x);
        };
    }

    private static void use(ServerPlayer player, ServerLevel level, BlockPos control) {
        InteractionResult result = UseBlockCallback.EVENT.invoker().interact(player,
                level, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(control), Direction.UP, control, false));
        require(result == InteractionResult.SUCCESS_SERVER,
                "Saved v8 Fam control rejected at " + control + ": " + result);
    }

    private static ChunkPos startChunk(String receipt) {
        require(receipt.startsWith(HEADER + "\nseed=" + SEED + "\npublic=gems:fam_forum\n"),
                "Fam v8 receipt header/seed is wrong");
        for (String line : receipt.split("\n")) {
            if (!line.startsWith("startChunk=")) continue;
            String[] parts = line.substring("startChunk=".length()).split(",", -1);
            require(parts.length == 2, "Bad Fam start chunk receipt");
            return new ChunkPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }
        throw new IllegalStateException("Fam v8 receipt lacks start chunk");
    }

    private static String firstDifference(String expected, String actual) {
        String[] before = expected.split("\n", -1), after = actual.split("\n", -1);
        for (int i = 0; i < Math.max(before.length, after.length); i++) {
            String left = i < before.length ? before[i] : "<missing>";
            String right = i < after.length ? after[i] : "<missing>";
            if (!left.equals(right)) return "line " + (i + 1) + " expected=" + left
                    + " actual=" + right;
        }
        return "unknown mismatch";
    }

    private static String read(Path receipt) {
        try {
            require(Files.isRegularFile(receipt),
                    "Reopen needs the matching copied Fam v8 receipt: " + receipt);
            return Files.readString(receipt, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Could not read Fam v8 receipt", error);
        }
    }

    private static String bounds(BoundingBox box) {
        return box.minX() + "," + box.minY() + "," + box.minZ() + ","
                + box.maxX() + "," + box.maxY() + "," + box.maxZ();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
