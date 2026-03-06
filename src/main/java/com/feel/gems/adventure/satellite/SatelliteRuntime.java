package com.feel.gems.adventure.satellite;

import com.feel.gems.GemsMod;
import com.feel.gems.adventure.CampaignPersistentState;
import com.feel.gems.adventure.FriendDomainUnlockState;
import com.feel.gems.adventure.LandmarkKey;
import com.feel.gems.util.GemsTeleport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;

/** Server-authoritative, co-op-friendly puzzles in valid saved structure starts. */
public final class SatelliteRuntime {
    private static final ResourceKey<Structure> OCEAN = ResourceKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "nour_sunken_relay_v1"));
    private static final Identifier OCEAN_V2_TEMPLATE = Identifier.fromNamespaceAndPath(
            GemsMod.MOD_ID, "nour_sunken_relay_v2");
    private static final Identifier OCEAN_V3_TEMPLATE = Identifier.fromNamespaceAndPath(
            GemsMod.MOD_ID, "nour_sunken_relay_v3");
    private static final Identifier OCEAN_V4_TEMPLATE = Identifier.fromNamespaceAndPath(
            GemsMod.MOD_ID, "nour_sunken_relay_v4");
    private static final ResourceKey<Structure> ARCHIVE = ResourceKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "branch_underarchive_v1"));
    private static final Identifier ARCHIVE_V2_TEMPLATE = Identifier.fromNamespaceAndPath(
            GemsMod.MOD_ID, "branch_underarchive_v2");
    private static final long OCEAN_WINDOW = 3 * 60 * 20;
    private static final long ARCHIVE_WINDOW = 5 * 60 * 20;
    private static final int MAX_ATTEMPTS = 32;
    private static final int MAX_AUDITS = 128;
    private static final long AUDIT_RETRY_TICKS = 30 * 20;
    private static final long UNAVAILABLE_RETRY_TICKS = 5 * 20;
    private static final Map<Long, Attempt> OCEAN_ATTEMPTS = new HashMap<>();
    private static final Map<Long, Attempt> ARCHIVE_ATTEMPTS = new HashMap<>();
    private static final Map<Long, AuditResult> OCEAN_AUDITS =
            new LinkedHashMap<>();
    private static final Map<Long, AuditResult> ARCHIVE_AUDITS =
            new LinkedHashMap<>();

    private record AuditResult(SatelliteLoadedAudit.Verdict verdict, long checkedAt) { }

    private static final class Attempt {
        final long started;
        int progress;

        Attempt(long started) {
            this.started = started;
        }

        boolean expired(long now, long window) {
            return now < started || now - started > window;
        }
    }

    /** Loads only the saved archive's hazard halo, then releases every ticket. */
    private static final class ArchiveSurvey {
        private static final int MAX_CHUNKS = 324;
        private static final int MAX_IN_FLIGHT = 8;
        private static final int MAX_TICKS = 20 * 90;
        private static final TicketType TICKET = new TicketType(20L * 120 + 17,
                TicketType.FLAG_LOADING);
        private static ArchiveSurvey active;

        private final ServerLevel level;
        private final ServerPlayer player;
        private final BlockPos control;
        private final BlockPos root;
        private final Block block;
        private final List<ChunkPos> needed;
        private final Map<Long, CompletableFuture<?>> inFlight = new HashMap<>();
        private final Set<Long> tickets = new HashSet<>();
        private final int started;
        private int issued;
        private int peakInFlight;
        private boolean closed;
        private String outcome = "cancelled";

        private ArchiveSurvey(ServerLevel level, ServerPlayer player, BlockPos control,
                              BlockPos root, Block block) {
            this.level = level;
            this.player = player;
            this.control = control;
            this.root = root;
            this.block = block;
            this.started = level.getServer().getTickCount();
            List<ChunkPos> chunks = new ArrayList<>();
            for (int cx = (root.getX() - 128) >> 4;
                 cx <= (root.getX() + 128) >> 4; cx++) {
                for (int cz = (root.getZ() - 128) >> 4;
                     cz <= (root.getZ() + 128) >> 4; cz++) {
                    chunks.add(new ChunkPos(cx, cz));
                }
            }
            this.needed = List.copyOf(chunks);
        }

        private static void request(ServerLevel level, ServerPlayer player,
                                    BlockPos control, BlockPos root, Block block) {
            if (active != null) {
                tell(player, active.player == player && active.root.equals(root)
                        ? "The archive is still checking its surroundings. Stay by the index."
                        : "The archive is checking another entrance. Try again shortly.");
                return;
            }
            ArchiveSurvey survey = new ArchiveSurvey(level, player, control, root, block);
            if (survey.needed.size() > MAX_CHUNKS) {
                tell(player, "The archive's safety survey exceeded its boundary. The entrance stays closed.");
                return;
            }
            active = survey;
            tell(player, "The archive is checking the ground below. Stay by the index.");
        }

        private static void tick(MinecraftServer server) {
            ArchiveSurvey survey = active;
            if (survey == null || survey.level.getServer() != server) return;
            try {
                survey.advance(server);
            } catch (RuntimeException error) {
                survey.outcome = "error";
                survey.close();
                GemsMod.LOGGER.error("Underarchive safety survey closed after an error", error);
                if (server.getPlayerList().getPlayer(survey.player.getUUID()) == survey.player)
                    tell(survey.player, "The archive could not finish its safety check. Try again later.");
            }
        }

        private void advance(MinecraftServer server) {
            if (server.getPlayerList().getPlayer(player.getUUID()) != player
                    || !player.isAlive() || player.level() != level
                    || player.blockPosition().distSqr(control) > 64
                    || level.getBlockState(control).getBlock() != block) {
                close();
                return;
            }
            if (server.getTickCount() - started > MAX_TICKS) {
                tell(player, "The archive could not finish its safety check. Try again later.");
                outcome = "timeout";
                close();
                return;
            }
            inFlight.entrySet().removeIf(entry -> entry.getValue().isDone());
            int issuedNow = 0;
            while (issued < needed.size() && issuedNow < 2
                    && inFlight.size() < MAX_IN_FLIGHT) {
                ChunkPos chunk = needed.get(issued++);
                long packed = chunk.pack();
                tickets.add(packed);
                inFlight.put(packed, level.getChunkSource()
                        .addTicketAndLoadWithRadius(TICKET, chunk, 0));
                issuedNow++;
            }
            peakInFlight = Math.max(peakInFlight, inFlight.size());
            if (issued < needed.size() || !inFlight.isEmpty()) return;
            for (ChunkPos chunk : needed) {
                if (level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) == null)
                    return;
            }
            StructureStart start = siteAt(level, control, ARCHIVE, 58);
            if (start == null || !level.getBlockState(root).is(SatelliteBlocks.UNDERARCHIVE_ROOT)
                    || start.getBoundingBox().minX() + 23 != root.getX()
                    || start.getBoundingBox().minY() + 28 != root.getY()
                    || start.getBoundingBox().minZ() + 23 != root.getZ()) {
                tell(player, "The archive's saved entrance changed. The index stays closed.");
                outcome = "saved_start_changed";
                close();
                return;
            }
            SatelliteLoadedAudit.Verdict verdict = SatelliteLoadedAudit.inspectLoaded(
                    level, start, SatelliteLoadedAudit.Site.ARCHIVE);
            if (!ARCHIVE_AUDITS.containsKey(root.asLong())
                    && ARCHIVE_AUDITS.size() >= MAX_AUDITS) {
                ARCHIVE_AUDITS.remove(ARCHIVE_AUDITS.keySet().iterator().next());
            }
            ARCHIVE_AUDITS.put(root.asLong(), new AuditResult(verdict, level.getGameTime()));
            if (verdict != SatelliteLoadedAudit.Verdict.READY) {
                tell(player, "The archive's safety check found " + verdict
                        + ". The index stays closed.");
                outcome = "audit_" + verdict;
                close();
                return;
            }
            try {
                archive(level, player, control, root, block);
                outcome = "ready";
            } finally {
                close();
            }
        }

        private void close() {
            if (closed) return;
            closed = true;
            int held = tickets.size();
            int released = 0;
            for (long packed : tickets) {
                level.getChunkSource().removeTicketWithRadius(TICKET,
                        new ChunkPos(ChunkPos.getX(packed), ChunkPos.getZ(packed)), 0);
                released++;
            }
            tickets.clear();
            inFlight.clear();
            if (active == this) active = null;
            GemsMod.LOGGER.info("Underarchive safety survey {} at {}: elapsedTicks={} "
                    + "plannedChunks={} visitedChunks={} peakInFlight={} ticketsHeld={} "
                    + "ticketsReleased={} ticketsRemaining={}", outcome, root,
                    level.getServer().getTickCount() - started, needed.size(), issued,
                    peakInFlight, held, released, tickets.size());
        }

        private static void stop(MinecraftServer server) {
            if (active != null && active.level.getServer() == server) {
                active.outcome = "server_stopping";
                active.close();
            }
        }
    }

    /** Test-readable proof that the temporary archive survey released its tickets. */
    public static boolean archiveSurveyIdle() {
        return ArchiveSurvey.active == null;
    }

    /** The loaded hazard audit completed before any archive control was used. */
    public static boolean archiveSafetyReady(BlockPos root) {
        AuditResult result = ARCHIVE_AUDITS.get(root.asLong());
        return result != null && result.verdict() == SatelliteLoadedAudit.Verdict.READY;
    }

    private SatelliteRuntime() { }

    /** Register once after SatelliteBlocks.init(). */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ArchiveSurvey::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(ArchiveSurvey::stop);
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            Block block = world.getBlockState(pos).getBlock();
            if (!SatelliteBlocks.isControl(block)) return InteractionResult.PASS;
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer actor)) {
                return InteractionResult.SUCCESS;
            }
            if (level.dimension() != Level.OVERWORLD || actor.isSpectator()) {
                return InteractionResult.SUCCESS_SERVER;
            }
            boolean ocean = block == SatelliteBlocks.SUNKEN_BUOY
                    || block == SatelliteBlocks.SUNKEN_WEST_HYDROPHONE
                    || block == SatelliteBlocks.SUNKEN_EAST_HYDROPHONE
                    || block == SatelliteBlocks.SUNKEN_TUNING_CONSOLE
                    || block == SatelliteBlocks.SUNKEN_RETURN;
            StructureStart start = siteAt(level, pos, ocean ? OCEAN : ARCHIVE,
                    ocean ? 43 : 58);
            if (start == null) {
                tell(actor, "This station has no intact site record. The controls stay closed.");
                return InteractionResult.SUCCESS_SERVER;
            }
            BoundingBox box = start.getBoundingBox();
            BlockPos root = new BlockPos(box.minX() + 23,
                    box.minY() + (ocean ? 19 : 28), box.minZ() + 23);
            if (!loaded(level, root) || level.getBlockState(root).getBlock()
                    != (ocean ? SatelliteBlocks.SUNKEN_ROOT : SatelliteBlocks.UNDERARCHIVE_ROOT)) {
                tell(actor, "The site's center seal is missing. The controls stay closed.");
                return InteractionResult.SUCCESS_SERVER;
            }
            SatelliteLoadedAudit.Verdict safety = audited(level, start, root, ocean);
            if (safety != SatelliteLoadedAudit.Verdict.READY) {
                if (!ocean && (safety == SatelliteLoadedAudit.Verdict.CHUNK_UNAVAILABLE
                        || safety == SatelliteLoadedAudit.Verdict.PROTECTION_UNPROVEN)) {
                    ArchiveSurvey.request(level, actor, pos, root, block);
                    return InteractionResult.SUCCESS_SERVER;
                }
                tell(actor, safety == SatelliteLoadedAudit.Verdict.CHUNK_UNAVAILABLE
                        || safety == SatelliteLoadedAudit.Verdict.PROTECTION_UNPROVEN
                        ? "Nearby terrain is not fully loaded for the safety survey. The controls stay closed."
                        : "This site's surroundings failed the safety survey. The controls stay closed; try again after repairs.");
                return InteractionResult.SUCCESS_SERVER;
            }
            if (ocean) ocean(level, actor, pos, root, block);
            else archive(level, actor, pos, root, block);
            return InteractionResult.SUCCESS_SERVER;
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ArchiveSurvey.stop(server);
            OCEAN_ATTEMPTS.clear();
            ARCHIVE_ATTEMPTS.clear();
            OCEAN_AUDITS.clear();
            ARCHIVE_AUDITS.clear();
        });
    }

    private static SatelliteLoadedAudit.Verdict audited(ServerLevel level,
                                                         StructureStart start, BlockPos root,
                                                         boolean ocean) {
        Map<Long, AuditResult> audits = ocean ? OCEAN_AUDITS : ARCHIVE_AUDITS;
        long key = root.asLong();
        long now = level.getGameTime();
        AuditResult prior = audits.get(key);
        if (prior != null) {
            if (prior.verdict() == SatelliteLoadedAudit.Verdict.READY) return prior.verdict();
            long retry = prior.verdict() == SatelliteLoadedAudit.Verdict.CHUNK_UNAVAILABLE
                    || prior.verdict() == SatelliteLoadedAudit.Verdict.PROTECTION_UNPROVEN
                    ? UNAVAILABLE_RETRY_TICKS : AUDIT_RETRY_TICKS;
            if (now >= prior.checkedAt() && now - prior.checkedAt() < retry) {
                return prior.verdict();
            }
        }
        SatelliteLoadedAudit.Verdict verdict = SatelliteLoadedAudit.inspectLoaded(level,
                start, ocean ? SatelliteLoadedAudit.Site.OCEAN
                        : SatelliteLoadedAudit.Site.ARCHIVE);
        if (prior == null && audits.size() >= MAX_AUDITS) {
            audits.remove(audits.keySet().iterator().next());
        }
        audits.put(key, new AuditResult(verdict, now));
        if (verdict != SatelliteLoadedAudit.Verdict.READY && (prior == null
                || prior.verdict() != verdict)) {
            GemsMod.LOGGER.warn("Satellite safety survey rejected {} at {}: {}",
                    ocean ? OCEAN.identifier() : ARCHIVE.identifier(), root, verdict);
        }
        return verdict;
    }

    private static void ocean(ServerLevel level, ServerPlayer actor, BlockPos pos,
                              BlockPos root, Block block) {
        if (block == SatelliteBlocks.SUNKEN_BUOY) {
            travel(level, actor, root.below(13), "Inside Nour's dry relay chamber.");
            return;
        }
        if (block == SatelliteBlocks.SUNKEN_RETURN) {
            travel(level, actor, root.above(19), "Back on the boat dock.");
            return;
        }
        SatelliteState state = SatelliteState.get(level.getServer());
        long key = root.asLong();
        if (state.oceanComplete(key)) {
            tell(actor, "Both calls reached the shore. Everyone can find this place now.");
            return;
        }
        if (!FriendDomainUnlockState.get(level.getServer()).cleared("nour")) {
            tell(actor, "Nour's harbor call has not been tuned yet. This relay is its missing reply.");
            return;
        }
        long now = level.getGameTime();
        Attempt attempt = OCEAN_ATTEMPTS.get(key);
        if (attempt != null && attempt.expired(now, OCEAN_WINDOW)) {
            OCEAN_ATTEMPTS.remove(key);
            attempt = null;
        }
        if (block == SatelliteBlocks.SUNKEN_TUNING_CONSOLE) {
            if (attempt == null) {
                OCEAN_ATTEMPTS.values().removeIf(entry -> entry.expired(now, OCEAN_WINDOW));
                if (OCEAN_ATTEMPTS.size() >= MAX_ATTEMPTS) {
                    tell(actor, "The relay is busy. Return in a moment.");
                    return;
                }
                OCEAN_ATTEMPTS.put(key, new Attempt(now));
                tell(actor, "The shore is listening for three minutes. Blue and amber hydrophones hum on opposite sides.");
            } else if (attempt.progress == 3) {
                OCEAN_ATTEMPTS.remove(key);
                if (state.completeOcean(key)) {
                    announce(level, "Nour's ocean reply reached the harbor. The Harbor Map now shows where it came from.");
                }
                chime(level, pos);
            } else {
                tell(actor, Integer.bitCount(attempt.progress) + "/2 calls heard. The table hums beneath the replies.");
            }
            return;
        }
        if (attempt == null) {
            tell(actor, "The hydrophone is silent. The center table has not been tuned.");
            return;
        }
        int side = block == SatelliteBlocks.SUNKEN_WEST_HYDROPHONE ? 1 : 2;
        if ((attempt.progress & side) != 0) {
            tell(actor, "This hydrophone has already answered. A second voice is still missing.");
            return;
        }
        attempt.progress |= side;
        tell(actor, side == 1 ? "Blue heard a boat that never returned."
                : "Amber heard the shore reply after the storm.");
        if (attempt.progress == 3) {
            announce(level, "Both calls now echo through the middle chamber.");
        }
        chime(level, pos);
    }

    private static void archive(ServerLevel level, ServerPlayer actor, BlockPos pos,
                                BlockPos root, Block block) {
        if (block == SatelliteBlocks.UNDERARCHIVE_CLUE) {
            tell(actor, "A rubbed-out arrow points beneath Branch. The shelves below keep the changed route.");
            travel(level, actor, root.below(22), "You reached the lit Underarchive.");
            return;
        }
        if (block == SatelliteBlocks.UNDERARCHIVE_RETURN) {
            travel(level, actor, root.above(22), "Back at the taiga index stone.");
            return;
        }
        SatelliteState state = SatelliteState.get(level.getServer());
        long key = root.asLong();
        if (state.archiveComplete(key)) {
            tell(actor, "The changed route is already in the townhall Route Desk.");
            return;
        }
        if (!CampaignPersistentState.get(level.getServer()).model()
                .worldChanged(LandmarkKey.BRANCH)) {
            tell(actor, "Branch's archive is still sealed. This lower index belongs to it.");
            return;
        }
        long now = level.getGameTime();
        Attempt attempt = ARCHIVE_ATTEMPTS.get(key);
        if (attempt != null && attempt.expired(now, ARCHIVE_WINDOW)) {
            ARCHIVE_ATTEMPTS.remove(key);
            attempt = null;
        }
        if (block == SatelliteBlocks.UNDERARCHIVE_DESK) {
            if (attempt == null) {
                ARCHIVE_ATTEMPTS.values().removeIf(entry -> entry.expired(now, ARCHIVE_WINDOW));
                if (ARCHIVE_ATTEMPTS.size() >= MAX_ATTEMPTS) {
                    tell(actor, "The index is busy. Return in a moment.");
                    return;
                }
                ARCHIVE_ATTEMPTS.put(key, new Attempt(now));
                tell(actor, "The old map shows a crossed-out road. Three side shelves hold newer notes. The ink fades in five minutes.");
            } else if (attempt.progress == 3) {
                ARCHIVE_ATTEMPTS.remove(key);
                if (state.completeArchive(key)) {
                    announce(level, "Branch's lower index opened a safe side route. The townhall Route Desk can share it.");
                }
                chime(level, pos);
            } else {
                tell(actor, attempt.progress + "/3 shelves read. The map is still unfinished.");
            }
            return;
        }
        if (attempt == null) {
            tell(actor, "The shelf's note has no place yet. The center map is still closed.");
            return;
        }
        Block expected = switch (attempt.progress) {
            case 0 -> SatelliteBlocks.UNDERARCHIVE_FIRST_INDEX;
            case 1 -> SatelliteBlocks.UNDERARCHIVE_THIRD_INDEX;
            default -> SatelliteBlocks.UNDERARCHIVE_SECOND_INDEX;
        };
        if (block != expected) {
            tell(actor, "The changed route does not pass through this shelf.");
            return;
        }
        attempt.progress++;
        tell(actor, switch (attempt.progress) {
            case 1 -> "Ochre shelf: the first road was erased, not lost.";
            case 2 -> "Blue shelf: a witness kept the turn above the old stairs.";
            default -> "Green shelf: the safe passage circles back toward the public archive.";
        });
        chime(level, pos);
    }

    private static StructureStart siteAt(ServerLevel level, BlockPos pos,
                                         ResourceKey<Structure> key, int height) {
        if (!loaded(level, pos)) return null;
        Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValue(key);
        if (structure == null) return null;
        StructureStart start = level.structureManager().getStructureWithPieceAt(
                pos.getX(), pos.getY(), pos.getZ(), structure);
        if (start == null || !start.isValid() || start.getPieces().size() != 1
                || !(start.getPieces().getFirst() instanceof PoolElementStructurePiece pooled)
                || !(pooled.getElement() instanceof SinglePoolElement single)
                || !(single.getTemplateLocation().equals(key.identifier())
                        || key.equals(OCEAN)
                        && (single.getTemplateLocation().equals(OCEAN_V2_TEMPLATE)
                                || single.getTemplateLocation().equals(OCEAN_V3_TEMPLATE)
                                || single.getTemplateLocation().equals(OCEAN_V4_TEMPLATE))
                        || key.equals(ARCHIVE)
                        && single.getTemplateLocation().equals(ARCHIVE_V2_TEMPLATE))) return null;
        BoundingBox box = start.getBoundingBox();
        int expectedHeight = single.getTemplateLocation().equals(OCEAN_V4_TEMPLATE)
                ? 50 : height;
        return box.getXSpan() == 47 && box.getYSpan() == expectedHeight
                && box.getZSpan() == 47
                ? start : null;
    }

    private static void travel(ServerLevel level, ServerPlayer actor, BlockPos feet,
                               String success) {
        if (!safe(level, feet)) {
            tell(actor, "The landing is blocked or flooded. Clear it, then try again.");
            return;
        }
        if (GemsTeleport.teleport(actor, level, feet.getX() + 0.5, feet.getY(),
                feet.getZ() + 0.5, actor.getYRot(), actor.getXRot())) {
            tell(actor, success);
        } else {
            tell(actor, "The passage did not open. Try the marker again.");
        }
    }

    private static boolean loaded(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }

    private static boolean safe(ServerLevel level, BlockPos feet) {
        if (!level.getWorldBorder().isWithinBounds(feet) || !loaded(level, feet)
                || !loaded(level, feet.below()) || !loaded(level, feet.above())) return false;
        return !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && level.getFluidState(feet).isEmpty()
                && level.getFluidState(feet.above()).isEmpty();
    }

    private static void chime(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS, 0.8F, 1.2F);
    }

    private static void tell(ServerPlayer actor, String message) {
        actor.sendSystemMessage(Component.literal(message), false);
    }

    private static void announce(ServerLevel level, String message) {
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(message), false);
    }
}
