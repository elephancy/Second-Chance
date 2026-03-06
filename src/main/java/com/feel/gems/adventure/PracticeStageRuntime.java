package com.feel.gems.adventure;

import com.feel.gems.sound.ModSounds;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A bounded, harmless copy of the Unbroken Punchline's spotlight tell. */
public final class PracticeStageRuntime {
    private static final DustParticleOptions FOLLOW = new DustParticleOptions(0xF3D975, 1.15F);
    private static final DustParticleOptions LOCK = new DustParticleOptions(0xD76491, 1.25F);
    private static final Map<BlockPos, Session> SESSIONS = new HashMap<>();
    private static final Map<BlockPos, Long> READY_AT = new HashMap<>();
    private static final int RECHARGE_TICKS = 20 * 10;
    private static final int MAX_SESSIONS = 8;

    private static final class Session {
        final UUID volunteer;
        final long startTick;
        Vec3 spot;

        Session(UUID volunteer, long startTick, Vec3 spot) {
            this.volunteer = volunteer;
            this.startTick = startTick;
            this.spot = spot;
        }
    }

    private PracticeStageRuntime() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            BlockPos board = hit.getBlockPos();
            if (!world.getBlockState(board).is(DomainBlocks.TOWNHALL_PRACTICE_STAGE)) {
                return InteractionResult.PASS;
            }
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer volunteer)) {
                return InteractionResult.SUCCESS;
            }
            if (level.dimension() != Level.OVERWORLD || volunteer.isSpectator()) {
                return InteractionResult.SUCCESS_SERVER;
            }
            if (!CampaignPersistentState.get(level.getServer()).model()
                    .worldChanged(LandmarkKey.REHEARSAL)) {
                tell(volunteer, "Clear Rehearsal to open this Practice Stage.");
                return InteractionResult.SUCCESS_SERVER;
            }
            if (SESSIONS.containsKey(board)) {
                tell(volunteer, "The spotlight is already running. Watch its color, then take your turn.");
                return InteractionResult.SUCCESS_SERVER;
            }
            long now = level.getServer().getTickCount();
            long remaining = READY_AT.getOrDefault(board, 0L) - now;
            if (remaining > 0) {
                tell(volunteer, "Stage ready in " + ((remaining + 19) / 20) + " seconds.");
                return InteractionResult.SUCCESS_SERVER;
            }
            if (SESSIONS.size() >= MAX_SESSIONS) {
                tell(volunteer, "The practice lights are busy. Try again in a moment.");
                return InteractionResult.SUCCESS_SERVER;
            }
            SESSIONS.put(board.immutable(), new Session(volunteer.getUUID(), now, volunteer.position()));
            tellNearby(level, board, "Gold light follows the volunteer. Pink locks it; step outside before it falls.");
            return InteractionResult.SUCCESS_SERVER;
        });
        ServerTickEvents.END_SERVER_TICK.register(PracticeStageRuntime::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SESSIONS.clear();
            READY_AT.clear();
        });
    }

    private static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null) return;
        Iterator<Map.Entry<BlockPos, Session>> iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Session> entry = iterator.next();
            BlockPos board = entry.getKey();
            Session session = entry.getValue();
            ServerPlayer volunteer = server.getPlayerList().getPlayer(session.volunteer);
            if (volunteer == null || volunteer.level() != level || !volunteer.isAlive()
                    || volunteer.isSpectator() || !level.hasChunkAt(board)
                    || !level.getBlockState(board).is(DomainBlocks.TOWNHALL_PRACTICE_STAGE)) {
                iterator.remove();
                continue;
            }
            int elapsed = (int) (server.getTickCount() - session.startTick);
            PracticeSpotlightPlan.Phase phase = PracticeSpotlightPlan.phase(elapsed);
            if (phase == PracticeSpotlightPlan.Phase.TRACKING) {
                session.spot = volunteer.position();
            }
            if (elapsed == PracticeSpotlightPlan.LOCK_TICK) {
                session.spot = volunteer.position();
                tellWitnesses(level, board, volunteer, "Pink light locked. Step outside the circle now.");
                level.playSound(null, board, ModSounds.ARCANE_CHIME, SoundSource.BLOCKS, 0.55F, 1.25F);
            }
            if (elapsed <= PracticeSpotlightPlan.STRIKE_TICK
                    && (elapsed % 4 == 0 || elapsed == PracticeSpotlightPlan.STRIKE_TICK)) {
                ring(level, session.spot, elapsed < PracticeSpotlightPlan.LOCK_TICK ? FOLLOW : LOCK);
            }
            if (elapsed == PracticeSpotlightPlan.STRIKE_TICK) {
                boolean clear = PracticeSpotlightPlan.escaped(volunteer.position(), session.spot);
                tellWitnesses(level, board, volunteer, clear
                        ? "The volunteer cleared the light. That is your opening to strike."
                        : "The volunteer stayed in the light. In the fight, this hit would hurt.");
                level.playSound(null, board, ModSounds.SURGE_PULSE, SoundSource.BLOCKS, 0.4F, 1.3F);
            }
            if (phase == PracticeSpotlightPlan.Phase.FINISHED) {
                READY_AT.put(board, (long) server.getTickCount() + RECHARGE_TICKS);
                iterator.remove();
            }
        }
    }

    private static void ring(ServerLevel level, Vec3 center, DustParticleOptions dust) {
        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2 * i / 16;
            level.sendParticles(dust, center.x + Math.cos(angle) * PracticeSpotlightPlan.RADIUS,
                    center.y + 0.15, center.z + Math.sin(angle) * PracticeSpotlightPlan.RADIUS,
                    1, 0, 0, 0, 0);
        }
    }

    private static void tellNearby(ServerLevel level, BlockPos board, String message) {
        Vec3 center = Vec3.atCenterOf(board);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level() == level && !player.isSpectator()
                    && player.distanceToSqr(center) <= 16 * 16) {
                tell(player, message);
            }
        }
    }

    private static void tellWitnesses(ServerLevel level, BlockPos board,
                                      ServerPlayer volunteer, String message) {
        tellNearby(level, board, message);
        if (volunteer.distanceToSqr(Vec3.atCenterOf(board)) > 16 * 16) {
            tell(volunteer, message);
        }
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), true);
    }
}
