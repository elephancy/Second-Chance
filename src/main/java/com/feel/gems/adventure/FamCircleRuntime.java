package com.feel.gems.adventure;

import com.feel.gems.sound.ModSounds;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;

/** Optional shared listening circuit at Fam's versioned forum. */
public final class FamCircleRuntime {
    private static final int STORY = 1;
    private static final int SCHOOL = 2;
    private static final int WINDOW = 20 * 240;
    private static final int COOLDOWN = 20 * 600;
    private static final int SITE_RADIUS_SQUARED = 150 * 150;
    private static final int AUDIENCE_RADIUS_SQUARED = 16 * 16;
    private static final Map<MinecraftServer, Session> SESSIONS = new WeakHashMap<>();

    private FamCircleRuntime() { }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            Block block = world.getBlockState(hit.getBlockPos()).getBlock();
            if (block != DomainBlocks.FAM_CIRCLE_START
                    && block != DomainBlocks.FAM_CIRCLE_STORY
                    && block != DomainBlocks.FAM_CIRCLE_SCHOOL) {
                return InteractionResult.PASS;
            }
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            if (!FriendDomainUnlockState.get(level.getServer()).cleared("fam")) {
                tell(serverPlayer, "Read both forum rooms and finish Fam's guide first.");
                return InteractionResult.SUCCESS_SERVER;
            }
            Session session = SESSIONS.computeIfAbsent(level.getServer(), ignored -> new Session());
            long now = level.getGameTime();
            session.prune(now);
            if (block == DomainBlocks.FAM_CIRCLE_START) {
                return center(level, serverPlayer, session, hit.getBlockPos(), now);
            }
            return room(level, serverPlayer, session, block, hit.getBlockPos(), now);
        });
    }

    private static InteractionResult center(ServerLevel level, ServerPlayer player,
                                            Session session, BlockPos center, long now) {
        UUID id = player.getUUID();
        Round round = session.rounds.get(id);
        if (round != null && round.at(center) && round.valid(now)) {
            if (round.voices == (STORY | SCHOOL)) {
                session.rounds.remove(id);
                session.cooldowns.put(id, now + COOLDOWN);
                int audience = 0;
                for (ServerPlayer listener : level.players()) {
                    if (listener.blockPosition().distSqr(center) > AUDIENCE_RADIUS_SQUARED) {
                        continue;
                    }
                    listener.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 480, 0));
                    listener.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 240, 0));
                    tell(listener, "The forum shares its bearings: clear sight and a quicker journey.");
                    audience++;
                }
                level.playSound(null, center, ModSounds.ARCANE_CHIME,
                        SoundSource.BLOCKS, 0.8F, 1.25F);
                if (audience == 0) {
                    tell(player, "The forum is ready, but nobody was near the speaking circle.");
                }
            } else {
                tell(player, round.voices == 0
                        ? "The Story Hall and Open School have not joined this circle. Friends can carry a voice for you."
                        : round.voices == STORY
                        ? "The Story Hall has spoken. The Open School remains quiet."
                        : "The Open School has spoken. The Story Hall remains quiet.");
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        if (session.cooldowns.getOrDefault(id, 0L) > now) {
            tell(player, "The circle is resting. Come back in a few minutes.");
            return InteractionResult.SUCCESS_SERVER;
        }
        session.rounds.put(id, new Round(level.dimension().identifier().toString(),
                center.immutable(), now, 0));
        tell(player, "The circle is open for four minutes. Both side rooms can lend it a voice; friends may split the walk.");
        return InteractionResult.SUCCESS_SERVER;
    }

    private static InteractionResult room(ServerLevel level, ServerPlayer player,
                                          Session session, Block block, BlockPos marker,
                                          long now) {
        int voice = block == DomainBlocks.FAM_CIRCLE_STORY ? STORY : SCHOOL;
        int stamped = 0;
        String dimension = level.dimension().identifier().toString();
        for (Map.Entry<UUID, Round> entry : session.rounds.entrySet()) {
            Round round = entry.getValue();
            if (!round.valid(now) || !round.dimension.equals(dimension)
                    || marker.distSqr(round.center) > SITE_RADIUS_SQUARED
                    || (round.voices & voice) != 0) {
                continue;
            }
            entry.setValue(new Round(round.dimension, round.center, round.started,
                    round.voices | voice));
            ServerPlayer starter = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (starter != null) {
                tell(starter, voice == STORY
                        ? "A friend heard the Story Hall. The Open School must speak too."
                        : "A friend heard the Open School. The Story Hall must speak too.");
            }
            stamped++;
        }
        tell(player, stamped == 0
                ? "Start a listening circle at Fam's central podium first."
                : "This room's voice reached " + stamped + " open circle"
                        + (stamped == 1 ? "." : "s."));
        return InteractionResult.SUCCESS_SERVER;
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), false);
    }

    private record Round(String dimension, BlockPos center, long started, int voices) {
        private boolean valid(long now) {
            return now >= started && now - started <= WINDOW;
        }

        private boolean at(BlockPos position) {
            return center.equals(position);
        }
    }

    private static final class Session {
        private final Map<UUID, Round> rounds = new HashMap<>();
        private final Map<UUID, Long> cooldowns = new HashMap<>();

        private void prune(long now) {
            rounds.values().removeIf(round -> !round.valid(now));
            cooldowns.values().removeIf(until -> until <= now);
        }
    }
}
