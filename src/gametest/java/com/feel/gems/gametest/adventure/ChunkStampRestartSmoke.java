package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.DomainBlocks;
import com.feel.gems.adventure.worldgen.GuardedDistrictBridges;
import com.feel.gems.adventure.worldgen.ScopedChunkSave;
import com.mojang.serialization.Codec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opt-in, disposable-save check of 26.3 chunk attachment and flush ordering. */
public final class ChunkStampRestartSmoke implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("gems-chunk-stamp-restart-smoke");
    private static final ChunkPos OLD = new ChunkPos(0, 0);
    private static final ChunkPos NEW = new ChunkPos(32, 0);
    private static final ChunkPos TRANSACTION_ROOT = new ChunkPos(40, 0);
    private static final ChunkPos TRANSACTION_END = new ChunkPos(41, 0);
    private static final ChunkPos UNLOADED = new ChunkPos(1024, 0);
    private static final String VALUE = "new-generation-v4";
    private static final Path ABORT_MARK = Path.of("chunk-stamp-intentional-abort.txt");
    private static final Path TRANSACTION_ABORT_MARK = Path.of("chunk-transaction-intentional-abort.txt");

    private AttachmentType<String> stamp;
    private String phase;
    private boolean generated;
    private boolean ran;
    private int stopAt;

    @Override
    public void onInitialize() {
        phase = System.getProperty("gems.chunkStampRestartSmoke.phase", "");
        if (phase.isEmpty()) return;
        if (!phase.equals("old") && !phase.equals("new") && !phase.equals("reopen")
                && !phase.equals("transaction") && !phase.equals("transaction-reopen")
                && !phase.equals("complete-reopen")) {
            throw new IllegalArgumentException("Unknown chunk stamp phase: " + phase);
        }
        stamp = AttachmentRegistry.createPersistent(
                Identifier.fromNamespaceAndPath("gems-gametest", "new_chunk_probe"), Codec.STRING);
        ServerChunkEvents.CHUNK_GENERATE.register((level, chunk) -> {
            if (phase.equals("new") && level.dimension() == Level.OVERWORLD
                    && chunk.getPos().equals(NEW)) {
                chunk.setAttached(stamp, VALUE);
                generated = true;
                LOGGER.info("CHUNK_STAMP_GENERATED chunk={}", NEW);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (stopAt > 0) {
                if (server.getTickCount() >= stopAt) server.halt(false);
                return;
            }
            if (ran || server.getTickCount() < 40) return;
            ran = true;
            try {
                switch (phase) {
                    case "old" -> old(server);
                    case "new" -> newer(server);
                    case "reopen" -> reopen(server);
                    case "transaction" -> transaction(server);
                    case "transaction-reopen" -> transactionReopen(server);
                    case "complete-reopen" -> completeReopen(server);
                    default -> throw new AssertionError(phase);
                }
                LOGGER.info("CHUNK_STAMP_{}_PASS", phase.toUpperCase());
            } catch (Exception failure) {
                LOGGER.error("CHUNK_STAMP_{}_FAILED", phase.toUpperCase(), failure);
            }
            stopAt = server.getTickCount() + 20;
        });
    }

    private void old(MinecraftServer server) {
        ServerLevel level = server.overworld();
        LevelChunk old = level.getChunk(OLD.x(), OLD.z());
        require(old.getAttached(stamp) == null, "Old chunk unexpectedly stamped");
        level.getChunkSource().save(true);
        LOGGER.info("CHUNK_STAMP_OLD_SAVED chunk={}", OLD);
    }

    private void newer(MinecraftServer server) throws Exception {
        ServerLevel level = server.overworld();
        LevelChunk old = level.getChunk(OLD.x(), OLD.z());
        require(old.getAttached(stamp) == null, "Copied old chunk has a new-generation stamp");
        LevelChunk fresh = level.getChunk(NEW.x(), NEW.z());
        require(generated && VALUE.equals(fresh.getAttached(stamp)),
                "CHUNK_GENERATE did not stamp the new chunk");
        BlockPos deck = probe(level, NEW);
        BlockPos gate = probe(level, OLD);
        require(level.getBlockState(deck).isAir() && level.getBlockState(gate).isAir(),
                "Probe cells were not empty in the copied save");
        require(level.setBlockAndUpdate(deck, Blocks.POLISHED_TUFF.defaultBlockState()),
                "Could not stage a deck cell");
        long flushStarted = System.nanoTime();
        level.getChunkSource().save(true);
        LOGGER.info("CHUNK_STAMP_DECK_FLUSHED chunk={} block={} durationMs={}",
                NEW, deck, (System.nanoTime() - flushStarted) / 1_000_000);
        require(level.setBlockAndUpdate(gate, Blocks.POLISHED_TUFF.defaultBlockState()),
                "Could not stage a post-flush gate cell");
        Files.writeString(ABORT_MARK, "Deck and stamp flushed before post-flush gate write.\n");
        LOGGER.info("CHUNK_STAMP_INTENTIONAL_ABORT_AFTER_FLUSH gate={}", gate);
        // This process owns only the copied fixture world. Abrupt exit tests the
        // save boundary: the gate must not reach disk ahead of the deck.
        Runtime.getRuntime().halt(0);
    }

    private void reopen(MinecraftServer server) {
        require(Files.exists(ABORT_MARK), "The copied-save abort phase did not run");
        ServerLevel level = server.overworld();
        LevelChunk old = level.getChunk(OLD.x(), OLD.z());
        LevelChunk fresh = level.getChunk(NEW.x(), NEW.z());
        require(old.getAttached(stamp) == null, "Old chunk gained a generation stamp");
        require(VALUE.equals(fresh.getAttached(stamp)), "New chunk lost its persisted stamp");
        BlockPos deck = probe(level, NEW);
        BlockPos gate = probe(level, OLD);
        require(level.getBlockState(deck).is(Blocks.POLISHED_TUFF),
                "Flushed deck block did not survive the abrupt exit");
        require(level.getBlockState(gate).isAir(),
                "Post-flush gate block reached disk before the deck boundary");
        LOGGER.info("CHUNK_STAMP_REOPEN_VERIFIED old={} new={} deck={} gate={}",
                OLD, NEW, deck, gate);
    }

    private void transaction(MinecraftServer server) throws Exception {
        ServerLevel level = server.overworld();
        require(!GuardedDistrictBridges.hasNewChunkStamp(level.getChunk(OLD.x(), OLD.z())),
                "Old copied chunk was incorrectly stamped by the production service");
        BlockPos oldMarker = probe(level, OLD);
        BlockPos oldDeck = oldMarker.east(2);
        require(level.getBlockState(oldDeck).isAir(),
                "Unstamped footprint probe is not empty");
        GuardedDistrictBridges.Plan oldPlan = new GuardedDistrictBridges.Plan(
                "Unstamped fixture", oldMarker)
                .finish(oldMarker, transactionRequest(), Blocks.POLISHED_TUFF.defaultBlockState())
                .gate(oldMarker.east(), DomainBlocks.ALI_BRIDGE_SEAL.defaultBlockState())
                .exterior(oldDeck, Blocks.POLISHED_TUFF.defaultBlockState());
        require(GuardedDistrictBridges.place(level, oldPlan)
                        == GuardedDistrictBridges.PlaceResult.BLOCKED
                        && level.getBlockState(oldDeck).isAir(),
                "Unstamped old chunk unexpectedly accepted an exterior write");
        LOGGER.info("CHUNK_TRANSACTION_OLD_CHUNK_ZERO_WRITE_PASS chunk={}", OLD);
        require(GuardedDistrictBridges.hasNewChunkStamp(
                        level.getChunk(TRANSACTION_ROOT.x(), TRANSACTION_ROOT.z()))
                        && GuardedDistrictBridges.hasNewChunkStamp(
                                level.getChunk(TRANSACTION_END.x(), TRANSACTION_END.z())),
                "Fresh transaction footprint is missing a production generation stamp");
        BlockPos marker = transactionMarker(level);
        BlockPos secondary = transactionSecondaryMarker(marker);
        BlockPos obstruction = marker.offset(5, 1, 0);
        BlockState request = transactionRequest();
        BlockState seal = DomainBlocks.ALI_BRIDGE_SEAL.defaultBlockState();
        require(level.getBlockState(marker).isAir(), "Transaction marker cell was occupied");
        require(level.setBlockAndUpdate(marker, request), "Could not place transaction marker");
        require(level.getBlockState(secondary).isAir(), "Secondary marker cell was occupied");
        require(level.setBlockAndUpdate(secondary, Blocks.COPPER_BLOCK.weathering().unaffected().defaultBlockState()),
                "Could not place secondary transaction marker");
        for (BlockPos gate : transactionGates(marker)) {
            require(level.getBlockState(gate).isAir(), "Transaction gate cell was occupied");
            require(level.setBlockAndUpdate(gate, seal), "Could not seal transaction gate");
        }
        require(level.getBlockState(obstruction).isAir(), "Obstruction cell was occupied");
        require(level.setBlockAndUpdate(obstruction, Blocks.COBBLESTONE.defaultBlockState()),
                "Could not place transaction obstruction");
        level.getChunkSource().save(true);

        require(level.getChunkSource().getChunkNow(UNLOADED.x(), UNLOADED.z()) == null,
                "Missing-footprint probe unexpectedly loaded a distant chunk");
        GuardedDistrictBridges.Plan missingPlan = transactionPlan(marker)
                .requireNewChunk(new BlockPos(UNLOADED.getMinBlockX() + 8,
                        marker.getY(), UNLOADED.getMinBlockZ() + 8));
        require(GuardedDistrictBridges.place(level, missingPlan)
                        == GuardedDistrictBridges.PlaceResult.WAIT
                        && level.getBlockState(marker.offset(2, 1, 0)).isAir(),
                "An unloaded footprint did not defer before any deck write");
        LOGGER.info("CHUNK_TRANSACTION_UNLOADED_ZERO_WRITE_PASS chunk={}", UNLOADED);

        require(GuardedDistrictBridges.place(level, transactionPlan(marker))
                        == GuardedDistrictBridges.PlaceResult.BLOCKED,
                "Obstructed full-footprint preflight did not block");
        for (int distance = 2; distance <= 9; distance++) {
            BlockPos deck = marker.offset(distance, 1, 0);
            require(distance == 5 ? level.getBlockState(deck).is(Blocks.COBBLESTONE)
                            : level.getBlockState(deck).isAir(),
                    "Blocked preflight changed a deck cell at " + deck);
        }
        for (BlockPos gate : transactionGates(marker)) {
            require(level.getBlockState(gate).equals(seal),
                    "Blocked preflight opened a gate cell at " + gate);
        }
        require(level.getBlockState(marker).equals(request),
                "Blocked preflight completed the marker");
        require(level.getBlockState(secondary).is(Blocks.COPPER_BLOCK.weathering().unaffected()),
                "Blocked preflight completed the secondary marker");
        LOGGER.info("CHUNK_TRANSACTION_OBSTRUCTION_ZERO_WRITE_PASS marker={}", marker);

        require(level.setBlockAndUpdate(obstruction, Blocks.AIR.defaultBlockState()),
                "Could not remove fixture obstruction");
        for (int distance = 2; distance <= 3; distance++) {
            require(level.setBlockAndUpdate(marker.offset(distance, 1, 0),
                            Blocks.POLISHED_TUFF.defaultBlockState()),
                    "Could not stage the partial transaction deck");
        }
        long flushStarted = System.nanoTime();
        int saved = ScopedChunkSave.flushLoaded(level,
                Set.of(TRANSACTION_ROOT, TRANSACTION_END));
        require(saved == 2, "Scoped deck barrier did not save both dirty footprint chunks");
        LOGGER.info("CHUNK_TRANSACTION_PARTIAL_SCOPED_FLUSH durationMs={} saved={} loaded={}",
                (System.nanoTime() - flushStarted) / 1_000_000, saved,
                level.getChunkSource().getLoadedChunksCount());
        // Model an interruption after a durable deck but before an open gate
        // is saved. Reopen must recover the original sealed state.
        require(level.setBlockAndUpdate(transactionGates(marker)[0],
                        Blocks.AIR.defaultBlockState()),
                "Could not stage an unsaved gate opening");
        Files.writeString(TRANSACTION_ABORT_MARK,
                "Two footprint chunks were flushed, then one gate cell was opened without a save.\n");
        LOGGER.info("CHUNK_TRANSACTION_INTENTIONAL_ABORT_AFTER_PARTIAL_DECK marker={}", marker);
        Runtime.getRuntime().halt(0);
    }

    private void transactionReopen(MinecraftServer server) {
        require(Files.exists(TRANSACTION_ABORT_MARK),
                "The copied-save partial transaction abort did not run");
        ServerLevel level = server.overworld();
        BlockPos marker = transactionMarker(level);
        requireTransactionStamps(level);
        require(level.getBlockState(marker).equals(transactionRequest()),
                "Pending marker did not survive the transaction abort");
        require(level.getBlockState(transactionSecondaryMarker(marker)).is(Blocks.COPPER_BLOCK.weathering().unaffected()),
                "Secondary pending marker did not survive the transaction abort");
        for (int distance = 2; distance <= 9; distance++) {
            BlockPos deck = marker.offset(distance, 1, 0);
            require(distance <= 3 ? level.getBlockState(deck).is(Blocks.POLISHED_TUFF)
                            : level.getBlockState(deck).isAir(),
                    "Saved partial deck differs at " + deck);
        }
        for (BlockPos gate : transactionGates(marker)) {
            require(level.getBlockState(gate).is(DomainBlocks.ALI_BRIDGE_SEAL),
                    "Gate opened before transaction recovery");
        }
        require(GuardedDistrictBridges.place(level, transactionPlan(marker))
                        == GuardedDistrictBridges.PlaceResult.COMPLETE,
                "Partial transaction was not recovered");
        require(level.getBlockState(marker).is(Blocks.POLISHED_TUFF),
                "Recovered transaction did not retire the marker");
        require(level.getBlockState(transactionSecondaryMarker(marker)).is(Blocks.POLISHED_TUFF),
                "Recovered transaction did not retire the secondary marker");
        LOGGER.info("CHUNK_TRANSACTION_RECOVERY_PASS marker={}", marker);
    }

    private void completeReopen(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos marker = transactionMarker(level);
        requireTransactionStamps(level);
        require(level.getBlockState(marker).is(Blocks.POLISHED_TUFF),
                "Completion marker was not durable across restart");
        require(level.getBlockState(transactionSecondaryMarker(marker)).is(Blocks.POLISHED_TUFF),
                "Secondary completion marker was not durable across restart");
        for (int distance = 2; distance <= 9; distance++) {
            require(level.getBlockState(marker.offset(distance, 1, 0)).is(Blocks.POLISHED_TUFF),
                    "Recovered deck was not durable across restart");
        }
        for (BlockPos gate : transactionGates(marker)) {
            require(level.getBlockState(gate).isAir(),
                    "Recovered gate was not durable across restart");
        }
        BlockPos playerEdit = marker.offset(4, 1, 0);
        require(level.setBlockAndUpdate(playerEdit, Blocks.AIR.defaultBlockState()),
                "Could not model a post-completion player edit");
        require(GuardedDistrictBridges.place(level, transactionPlan(marker))
                        == GuardedDistrictBridges.PlaceResult.COMPLETE
                        && level.getBlockState(playerEdit).isAir(),
                "Completed crossing unexpectedly repaired a post-completion edit");
        LOGGER.info("CHUNK_TRANSACTION_COMPLETION_REOPEN_PASS marker={} preservedEdit={}",
                marker, playerEdit);
    }

    private static GuardedDistrictBridges.Plan transactionPlan(BlockPos marker) {
        GuardedDistrictBridges.Plan plan = new GuardedDistrictBridges.Plan("Fixture", marker)
                .finish(marker, transactionRequest(), Blocks.POLISHED_TUFF.defaultBlockState())
                .finish(transactionSecondaryMarker(marker), Blocks.COPPER_BLOCK.weathering().unaffected().defaultBlockState(),
                        Blocks.POLISHED_TUFF.defaultBlockState());
        for (int distance = 2; distance <= 9; distance++) {
            BlockPos deck = marker.offset(distance, 1, 0);
            plan.exterior(deck, Blocks.POLISHED_TUFF.defaultBlockState())
                    .requireAir(deck.above()).requireAir(deck.above(2));
        }
        for (BlockPos gate : transactionGates(marker)) {
            plan.gate(gate, DomainBlocks.ALI_BRIDGE_SEAL.defaultBlockState());
        }
        return plan;
    }

    private static BlockPos transactionMarker(ServerLevel level) {
        return new BlockPos(TRANSACTION_ROOT.getMinBlockX() + 13,
                level.getMaxY() - 7, TRANSACTION_ROOT.getMinBlockZ() + 8);
    }

    private static BlockPos transactionSecondaryMarker(BlockPos marker) {
        return marker.offset(4, 0, 1);
    }

    private static void requireTransactionStamps(ServerLevel level) {
        require(!GuardedDistrictBridges.hasNewChunkStamp(level.getChunk(OLD.x(), OLD.z())),
                "Old copied chunk gained a production generation stamp");
        for (ChunkPos pos : Set.of(TRANSACTION_ROOT, TRANSACTION_END)) {
            require(GuardedDistrictBridges.hasNewChunkStamp(level.getChunk(pos.x(), pos.z())),
                    "Scoped footprint chunk lost its generation stamp: " + pos);
        }
    }

    private static BlockPos[] transactionGates(BlockPos marker) {
        return new BlockPos[] {marker.offset(1, 1, 0), marker.offset(1, 2, 0)};
    }

    private static BlockState transactionRequest() {
        return DomainBlocks.ALI_BRIDGE_REQUEST.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST);
    }

    private static BlockPos probe(ServerLevel level, ChunkPos chunk) {
        return new BlockPos(chunk.getMinBlockX() + 8, level.getMaxY() - 5,
                chunk.getMinBlockZ() + 8);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
