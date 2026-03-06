package com.feel.gems.casino;

import com.feel.gems.friend.FriendEntity;
import com.feel.gems.adventure.FriendDomainUnlockState;
import com.feel.gems.adventure.AdventureSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;

/** One server-owned session per player, bound to Ragab and the current dimension. */
public final class CasinoService {
    private static final int MAX_DISTANCE_SQUARED = 36;
    private static final long TIMEOUT_TICKS = 20L * 60 * 10;
    private static final String[] SIGNALS = {"Ruby", "Jade", "Azure"};
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, UUID> WAITING_BY_HOST = new HashMap<>();
    private static final Map<UUID, UUID> WAITING_TOURNAMENT_BY_HOST = new HashMap<>();

    private enum Phase {LOBBY, TOURNAMENT_LOBBY, PREVIEW, ROUND, RESULT, COOP_WAIT, COOP_PLAY}

    private static final class Session {
        final UUID token = UUID.randomUUID();
        final UUID player;
        final UUID host;
        final ResourceKey<Level> dimension;
        BlockPos tableSign;
        int revision;
        long expiresAt;
        Phase phase = Phase.LOBBY;
        CasinoGame game;
        CasinoRound round;
        CoopRound coop;
        boolean settled;
        boolean tournament;
        long tournamentDay = -1;
        boolean forfeitPendingOnJoin;
        String message = "Choose a table. Rules appear before you stake emeralds.";

        Session(ServerPlayer player, FriendEntity host) {
            this.player = player.getUUID();
            this.host = host.getUUID();
            this.dimension = player.level().dimension();
            touch(player);
        }

        void touch(ServerPlayer player) {
            expiresAt = player.level().getServer().getTickCount() + TIMEOUT_TICKS;
        }
    }

    /** Two clues are private; both players must communicate and name the other's signal. */
    private static final class CoopRound {
        final UUID first;
        final UUID second;
        final int firstSignal = ThreadLocalRandom.current().nextInt(SIGNALS.length);
        final int secondSignal = ThreadLocalRandom.current().nextInt(SIGNALS.length);
        int firstGuess = -1;
        int secondGuess = -1;
        boolean settled;

        CoopRound(UUID first, UUID second) {
            this.first = first;
            this.second = second;
        }

        int signalFor(UUID player) {
            return first.equals(player) ? firstSignal : secondSignal;
        }

        boolean hasGuessed(UUID player) {
            return (first.equals(player) ? firstGuess : secondGuess) >= 0;
        }

        boolean guess(UUID player, int signal) {
            if (settled || signal < 0 || signal >= SIGNALS.length || hasGuessed(player)) return false;
            if (first.equals(player)) firstGuess = signal;
            else if (second.equals(player)) secondGuess = signal;
            else return false;
            return true;
        }

        boolean bothGuessed() {
            return firstGuess >= 0 && secondGuess >= 0;
        }

        boolean won() {
            return bothGuessed() && firstGuess == secondSignal && secondGuess == firstSignal;
        }
    }

    private CasinoService() {
    }

    public static void open(ServerPlayer player, FriendEntity host) {
        if (!validHost(player, host)) return;
        EmeraldStakes.deliverClaims(player, CasinoPlayerAccount.of(player));
        Session existing = SESSIONS.get(player.getUUID());
        if (existing != null) {
            if (expired(player, existing)) expire(player.level().getServer(), existing);
            if (SESSIONS.get(player.getUUID()) == existing && existing.host.equals(host.getUUID())) {
                // Moving back to Ragab changes the interaction point, not the round.
                existing.tableSign = null;
                send(player, existing);
                return;
            } else if (existing.phase == Phase.ROUND || existing.phase == Phase.COOP_PLAY) {
                player.sendSystemMessage(Component.literal("Finish your active casino round before changing tables."), true);
                return;
            }
            if (existing.phase == Phase.COOP_WAIT) {
                WAITING_BY_HOST.remove(existing.host, existing.player);
                WAITING_TOURNAMENT_BY_HOST.remove(existing.host, existing.player);
            }
        }
        Session session = new Session(player, host);
        SESSIONS.put(player.getUUID(), session);
        send(player, session);
    }

    /** Open a signed game bay without forcing players back across the hall. */
    public static void openAtTable(ServerPlayer player, FriendEntity host, CasinoGame game, BlockPos signPos) {
        if (player == null || host == null || game == null || signPos == null
                || !player.isAlive() || player.isSpectator() || !host.isAlive()
                || !"ragab".equals(host.friendId()) || player.level() != host.level()
                || player.distanceToSqr(signPos.getX() + 0.5, signPos.getY() + 0.5,
                        signPos.getZ() + 0.5) > MAX_DISTANCE_SQUARED
                || host.distanceToSqr(signPos.getX() + 0.5, signPos.getY() + 0.5,
                        signPos.getZ() + 0.5) > 24 * 24
                || !(player.level() instanceof ServerLevel level)
                || !CasinoFloorInteraction.matches(level, signPos, game)) return;
        EmeraldStakes.deliverClaims(player, CasinoPlayerAccount.of(player));
        Session existing = SESSIONS.get(player.getUUID());
        if (existing != null && expired(player, existing)) {
            expire(player.level().getServer(), existing);
            existing = SESSIONS.get(player.getUUID());
        }
        if (existing != null && (existing.phase == Phase.ROUND || existing.phase == Phase.COOP_PLAY)) {
            if (!existing.host.equals(host.getUUID()) || existing.game != game) {
                player.sendSystemMessage(Component.literal("Finish your active casino round before changing tables."), true);
                return;
            }
            existing.tableSign = signPos.immutable();
            send(player, existing);
            return;
        }
        if (existing != null && existing.phase == Phase.COOP_WAIT) {
            WAITING_BY_HOST.remove(existing.host, existing.player);
            WAITING_TOURNAMENT_BY_HOST.remove(existing.host, existing.player);
        }
        Session session = new Session(player, host);
        session.tableSign = signPos.immutable();
        preview(session, "preview_" + game.id());
        SESSIONS.put(player.getUUID(), session);
        send(player, session);
    }

    public static void action(ServerPlayer player, CasinoActionPayload payload) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.token.equals(payload.sessionId())) return;
        if (expired(player, session)) {
            expire(player.level().getServer(), session);
            if (SESSIONS.get(player.getUUID()) == session) {
                send(player, session);
            } else {
                player.sendSystemMessage(Component.literal("Casino session expired. Speak to Ragab again."), true);
            }
            return;
        }
        if (!validBinding(player, session)) {
            player.sendSystemMessage(Component.literal("Return to Ragab's table to continue."), true);
            return;
        }
        if (payload.revision() != session.revision) {
            send(player, session); // Replayed or delayed packets only resync the view.
            return;
        }
        boolean accepted = switch (session.phase) {
            case LOBBY -> payload.action().equals("tournament")
                    ? openTournament(player, session) : preview(session, payload.action());
            case TOURNAMENT_LOBBY -> tournamentChoice(player, session, payload.action());
            case PREVIEW -> previewAction(player, session, payload.action());
            case ROUND -> roundAction(player, session, payload.action());
            case COOP_WAIT -> waitingAction(session, payload.action());
            case COOP_PLAY -> coopAction(player, session, payload.action());
            case RESULT -> resultAction(session, payload.action());
        };
        if (accepted) {
            session.revision++;
            session.touch(player);
        }
        send(player, session);
    }

    public static void onJoin(ServerPlayer player) {
        recoverOnJoin(player);
    }

    private static void recoverOnJoin(ServerPlayer player) {
        CasinoPlayerAccount account = CasinoPlayerAccount.of(player);
        // A restart loses sessions; an orphaned stake becomes a player-owned refund.
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && expired(player, session)) {
            expire(player.level().getServer(), session);
        }
        if (session != null && session.forfeitPendingOnJoin) {
            account.forfeit();
            session.forfeitPendingOnJoin = false;
        } else if (session == null || session.phase == Phase.RESULT && session.settled) {
            account.refund();
        }
        EmeraldStakes.deliverClaims(player, account);
        if (account.claimBalance() > 0) {
            player.sendSystemMessage(Component.literal(account.claimBalance()
                    + " casino emeralds are waiting for inventory space."), false);
        }
    }

    public static void onRespawn(MinecraftServer server, ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && session.phase == Phase.COOP_PLAY && session.coop != null) {
            CoopRound round = session.coop;
            abortCoop(server, round, session.tournament
                    ? "A player left the free Signal Table. No score or emeralds changed."
                    : "A player left the table. Both stakes returned.", player.getUUID());
            UUID partnerId = round.first.equals(player.getUUID()) ? round.second : round.first;
            ServerPlayer partner = server.getPlayerList().getPlayer(partnerId);
            Session partnerSession = SESSIONS.get(partnerId);
            if (partner != null && partnerSession != null) {
                partnerSession.revision++;
                send(partner, partnerSession);
            }
        } else if (session != null && session.phase == Phase.ROUND && !session.settled) {
            // Death ends a live solo round on the same terms as its timeout.
            session.settled = true;
            session.phase = Phase.RESULT;
            session.message = session.tournament
                    ? "Free challenge round ended when you died. No score or emeralds changed."
                    : "Round ended when you died. The stake was lost.";
            if (!session.tournament) CasinoPlayerAccount.of(player).forfeit();
        }
        CasinoPlayerAccount account = CasinoPlayerAccount.of(player);
        if (session == null || session.phase == Phase.RESULT && session.settled) account.refund();
        EmeraldStakes.deliverClaims(player, account);
    }

    public static void onDisconnect(MinecraftServer server, ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        if (session.phase == Phase.COOP_WAIT) {
            WAITING_BY_HOST.remove(session.host, session.player);
            WAITING_TOURNAMENT_BY_HOST.remove(session.host, session.player);
            SESSIONS.remove(session.player);
        } else if (session.phase == Phase.COOP_PLAY && session.coop != null) {
            CoopRound round = session.coop;
            abortCoop(server, round, session.tournament
                    ? "A player left the free Signal Table. No score or emeralds changed."
                    : "A player left the table. Both stakes returned.", player.getUUID());
            UUID partnerId = round.first.equals(player.getUUID()) ? round.second : round.first;
            ServerPlayer partner = server.getPlayerList().getPlayer(partnerId);
            Session partnerSession = SESSIONS.get(partnerId);
            if (partner != null && partnerSession != null) {
                partnerSession.revision++;
                send(partner, partnerSession);
            }
        } else if (session.phase != Phase.ROUND) {
            SESSIONS.remove(session.player);
        }
    }

    public static void onServerTick(MinecraftServer server) {
        if (server.getTickCount() % 200 != 0) return;
        for (Session session : new ArrayList<>(SESSIONS.values())) {
            if (server.getTickCount() < session.expiresAt) continue;
            // Idle views may have been closed locally. Keep their server session until
            // the next interaction so a timeout cannot reopen a dismissed screen.
            // Disconnect already removes these sessions.
            if (session.phase == Phase.LOBBY || session.phase == Phase.TOURNAMENT_LOBBY
                    || session.phase == Phase.PREVIEW || session.phase == Phase.RESULT) continue;
            Phase before = session.phase;
            expire(server, session);
            if (before == Phase.COOP_PLAY && session.coop != null) {
                for (UUID id : List.of(session.coop.first, session.coop.second)) {
                    refreshExpiredView(server, SESSIONS.get(id));
                }
            } else {
                refreshExpiredView(server, SESSIONS.get(session.player));
            }
        }
    }

    private static void refreshExpiredView(MinecraftServer server, Session session) {
        if (session == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(session.player);
        if (player == null) return;
        session.revision++;
        session.touch(player);
        send(player, session);
    }

    public static void onServerStopped() {
        SESSIONS.clear();
        WAITING_BY_HOST.clear();
        WAITING_TOURNAMENT_BY_HOST.clear();
    }

    private static boolean preview(Session session, String action) {
        if (!action.startsWith("preview_")) return false;
        CasinoGame game = CasinoGame.byId(action.substring("preview_".length())).orElse(null);
        if (game == null) return false;
        session.game = game;
        session.tournament = false;
        session.tournamentDay = -1;
        session.phase = Phase.PREVIEW;
        session.message = "Read the rules before paying " + game.stake() + " emeralds to play.";
        return true;
    }

    private static boolean openTournament(ServerPlayer player, Session session) {
        session.tournament = true;
        session.tournamentDay = TournamentState.worldDay(player.level().getServer());
        session.game = null;
        session.phase = Phase.TOURNAMENT_LOBBY;
        session.message = "Today's challenge is free. Win three different featured tables as a group.";
        return true;
    }

    private static boolean tournamentChoice(ServerPlayer player, Session session, String action) {
        long day = TournamentState.worldDay(player.level().getServer());
        if (action.equals("all_games")) {
            session.tournament = false;
            session.game = null;
            session.phase = Phase.LOBBY;
            session.message = "Choose a table.";
            return true;
        }
        if (session.tournamentDay != day) {
            session.tournamentDay = day;
            session.message = "A new Minecraft day began. Today's featured tables have changed.";
            return true;
        }
        if (!action.startsWith("challenge_")) return false;
        CasinoGame game = CasinoGame.byId(action.substring("challenge_".length())).orElse(null);
        if (game == null || !TournamentProgress.featured(day, game)) return false;
        session.game = game;
        session.phase = Phase.PREVIEW;
        session.message = "Free round. Win this table to count it once toward today's group goal.";
        return true;
    }

    private static boolean previewAction(ServerPlayer player, Session session, String action) {
        if (action.equals("back")) {
            session.game = null;
            session.phase = session.tournament ? Phase.TOURNAMENT_LOBBY : Phase.LOBBY;
            session.message = session.tournament ? "Choose a featured table." : "Choose a table.";
            return true;
        }
        if (!action.equals("commit") || session.game == null) return false;
        if (session.tournament) {
            long day = TournamentState.worldDay(player.level().getServer());
            if (session.tournamentDay != day || !TournamentProgress.featured(day, session.game)) {
                session.game = null;
                session.phase = Phase.TOURNAMENT_LOBBY;
                session.tournamentDay = day;
                session.message = "A new Minecraft day began. Pick one of today's featured tables.";
                return true;
            }
            if (session.game == CasinoGame.COOP) return joinCoop(player, session);
            session.round = new CasinoRound(session.game, new java.util.Random(player.getRandom().nextLong()));
            session.phase = Phase.ROUND;
            session.message = session.round.result();
            if (session.round.finished()) settleSolo(player, session);
            return true;
        }
        if (session.game == CasinoGame.COOP) return joinCoop(player, session);
        int stake = session.game.stake();
        if (CasinoPlayerAccount.of(player).pending() != 0) {
            session.message = "Your previous stake is still being settled. Try again.";
            return true;
        }
        if (!EmeraldStakes.debit(player, stake)) {
            session.message = "You need " + stake + " emeralds in your inventory.";
            return true;
        }
        try {
            CasinoPlayerAccount.of(player).hold(stake);
        } catch (RuntimeException error) {
            CasinoPlayerAccount account = CasinoPlayerAccount.of(player);
            account.credit(stake);
            EmeraldStakes.deliverClaims(player, account);
            session.message = "Could not reserve this stake. Try again.";
            return true;
        }
        session.round = new CasinoRound(session.game, new java.util.Random(player.getRandom().nextLong()));
        session.phase = Phase.ROUND;
        session.message = session.round.result();
        play(player, AdventureSounds.CASINO_CHIP_LAY, 0.7F, 1.0F);
        if (session.game == CasinoGame.CARDS) {
            play(player, AdventureSounds.CASINO_CARD_SHUFFLE, 0.55F, 1.0F);
        }
        if (session.round.finished()) settleSolo(player, session);
        return true;
    }

    private static boolean roundAction(ServerPlayer player, Session session, String action) {
        if (session.round == null || !session.round.play(action)) return false;
        if (session.game == CasinoGame.CARDS) {
            play(player, AdventureSounds.CASINO_CARD_PLACE, 0.65F, 1.0F);
        } else if (session.game == CasinoGame.WHEEL) {
            play(player, AdventureSounds.CASINO_WHEEL_TICK, 0.5F, 1.0F);
        } else if (session.game == CasinoGame.REELS) {
            play(player, AdventureSounds.CASINO_REELS_SPIN, 0.38F, 1.0F);
        } else if (session.game == CasinoGame.RPS) {
            play(player, AdventureSounds.CASINO_RPS_THROW, 0.4F, 1.0F);
        } else if (session.game == CasinoGame.TRIVIA) {
            play(player, AdventureSounds.CASINO_TRIVIA_CHOICE, 0.42F, 1.0F);
        }
        session.message = session.round.result();
        if (session.round.finished()) settleSolo(player, session);
        return true;
    }

    private static void settleSolo(ServerPlayer player, Session session) {
        if (session.settled) return;
        session.settled = true;
        session.phase = Phase.RESULT;
        if (session.tournament) {
            boolean win = TournamentProgress.qualifies(session.round);
            session.message = tournamentResult(player.level().getServer(), session.game,
                    session.tournamentDay, win);
            play(player, win ? AdventureSounds.CASINO_CHIPS_STACK : AdventureSounds.CASINO_LOSS,
                    0.65F, 1.0F);
            return;
        }
        CasinoPlayerAccount account = CasinoPlayerAccount.of(player);
        int payout = session.round.payout();
        if (session.game == CasinoGame.TRIVIA && payout > session.game.stake()
                && !account.claimTriviaBonus(TournamentState.worldDay(player.level().getServer()))) {
            payout = session.game.stake();
            session.message = "Correct. Your bonus is already claimed; your stake comes back.";
        }
        account.settle(payout);
        EmeraldStakes.deliverClaims(player, account);
        if (account.claimBalance() > 0) {
            session.message += " Inventory full: your emeralds are saved for your next visit.";
        }
        if (payout > session.game.stake()) {
            play(player, AdventureSounds.CASINO_CHIPS_STACK, 0.7F, 1.05F);
        } else if (payout == session.game.stake()) {
            play(player, AdventureSounds.CASINO_CHIP_LAY, 0.55F, 1.15F);
        } else {
            play(player, AdventureSounds.CASINO_LOSS, 0.6F, 1.0F);
        }
    }

    private static boolean resultAction(Session session, String action) {
        if (!action.equals("lobby")) return false;
        session.phase = session.tournament ? Phase.TOURNAMENT_LOBBY : Phase.LOBBY;
        session.game = null;
        session.round = null;
        session.coop = null;
        session.settled = false;
        session.forfeitPendingOnJoin = false;
        session.message = session.tournament ? "Choose another featured table." : "Choose another table.";
        return true;
    }

    private static String tournamentResult(MinecraftServer server, CasinoGame game,
                                           long sessionDay, boolean won) {
        long day = TournamentState.worldDay(server);
        if (sessionDay != day) return "A new Minecraft day began. This free round did not score.";
        if (!won) return "No tournament point this round. You can try this free table again.";
        TournamentState board = TournamentState.get(server);
        boolean newlyScored = board.record(day, game, true);
        if (!newlyScored) return game.title() + " is already on today's board. No emeralds changed hands.";
        if (board.completed(day)) {
            boolean firstClear = FriendDomainUnlockState.get(server).clear("ragab");
            server.getPlayerList().broadcastSystemMessage(Component.literal(firstClear
                    ? "Ragab's free tournament is complete. The Tournament Board is open to everyone at the townhall."
                    : "Today's tournament is complete. The townhall board has a free group provision for everyone."), false);
        }
        return game.title() + " added to today's board. "
                + Integer.bitCount(board.wonTables(day)) + "/3 tables won; no emeralds changed hands.";
    }

    private static boolean joinCoop(ServerPlayer player, Session session) {
        MinecraftServer server = player.level().getServer();
        Map<UUID, UUID> queue = session.tournament ? WAITING_TOURNAMENT_BY_HOST : WAITING_BY_HOST;
        UUID waitingId = queue.get(session.host);
        Session partnerSession = waitingId == null ? null : SESSIONS.get(waitingId);
        ServerPlayer partner = waitingId == null ? null : server.getPlayerList().getPlayer(waitingId);
        if (partnerSession == null || partner == null || !validBinding(partner, partnerSession)
                || partnerSession.phase != Phase.COOP_WAIT || expired(partner, partnerSession)
                || partnerSession.tournament != session.tournament
                || session.tournament && partnerSession.tournamentDay != session.tournamentDay) {
            if (partnerSession != null && partnerSession.phase == Phase.COOP_WAIT
                    && !waitingId.equals(player.getUUID())) {
                partnerSession.phase = Phase.PREVIEW;
                partnerSession.message = "Your co-op seat closed. Choose it again to wait for a partner.";
                if (partner != null) send(partner, partnerSession);
            }
            queue.put(session.host, player.getUUID());
            session.phase = Phase.COOP_WAIT;
            session.message = session.tournament
                    ? "Waiting for a partner at the free Signal Table. No emeralds needed."
                    : "Waiting for a second player at Ragab's table. No stake taken yet.";
            return true;
        }
        if (waitingId.equals(player.getUUID())) return false;
        int stake = CasinoGame.COOP.stake();
        if (!session.tournament && (CasinoPlayerAccount.of(player).pending() != 0
                || CasinoPlayerAccount.of(partner).pending() != 0)) {
            session.message = "A previous stake is still being settled. Try again.";
            return true;
        }
        if (!session.tournament && (EmeraldStakes.count(player) < stake || EmeraldStakes.count(partner) < stake)) {
            session.message = "Both players need " + stake + " emeralds before the round starts.";
            partnerSession.message = session.message;
            send(partner, partnerSession);
            return true;
        }
        if (!session.tournament && !EmeraldStakes.debit(partner, stake)) return false;
        if (!session.tournament && !EmeraldStakes.debit(player, stake)) {
            CasinoPlayerAccount partnerAccount = CasinoPlayerAccount.of(partner);
            partnerAccount.credit(stake);
            EmeraldStakes.deliverClaims(partner, partnerAccount);
            return false;
        }
        if (!session.tournament) {
            CasinoPlayerAccount partnerAccount = CasinoPlayerAccount.of(partner);
            CasinoPlayerAccount playerAccount = CasinoPlayerAccount.of(player);
            try {
                partnerAccount.hold(stake);
                playerAccount.hold(stake);
            } catch (RuntimeException error) {
                if (partnerAccount.pending() == stake) partnerAccount.refund();
                else partnerAccount.credit(stake);
                if (playerAccount.pending() == stake) playerAccount.refund();
                else playerAccount.credit(stake);
                EmeraldStakes.deliverClaims(partner, partnerAccount);
                EmeraldStakes.deliverClaims(player, playerAccount);
                session.message = "Could not reserve both stakes. Try again.";
                return true;
            }
        }
        queue.remove(session.host);
        CoopRound round = new CoopRound(partner.getUUID(), player.getUUID());
        partnerSession.coop = round;
        session.coop = round;
        partnerSession.phase = Phase.COOP_PLAY;
        session.phase = Phase.COOP_PLAY;
        if (!session.tournament) {
            play(partner, AdventureSounds.CASINO_CHIP_LAY, 0.7F, 1.0F);
            play(player, AdventureSounds.CASINO_CHIP_LAY, 0.7F, 1.0F);
        }
        partnerSession.message = "Tell your partner the color you see, then choose theirs.";
        session.message = partnerSession.message;
        partnerSession.revision++;
        partnerSession.touch(partner);
        send(partner, partnerSession);
        return true;
    }

    private static boolean waitingAction(Session session, String action) {
        if (!action.equals("back")) return false;
        (session.tournament ? WAITING_TOURNAMENT_BY_HOST : WAITING_BY_HOST)
                .remove(session.host, session.player);
        session.phase = Phase.PREVIEW;
        session.message = "Co-op seat left. No stake was taken.";
        return true;
    }

    private static boolean coopAction(ServerPlayer player, Session session, String action) {
        CoopRound round = session.coop;
        if (round == null || !action.startsWith("guess_")) return false;
        int signal = switch (action) {
            case "guess_ruby" -> 0;
            case "guess_jade" -> 1;
            case "guess_azure" -> 2;
            default -> -1;
        };
        if (!round.guess(player.getUUID(), signal)) return false;
        play(player, AdventureSounds.CASINO_CARD_PLACE, 0.5F, 1.15F);
        session.message = "Guess locked. Waiting for your partner.";
        if (!round.bothGuessed()) return true;
        MinecraftServer server = player.level().getServer();
        Session otherSession = SESSIONS.get(round.first.equals(player.getUUID()) ? round.second : round.first);
        ServerPlayer other = otherSession == null ? null : server.getPlayerList().getPlayer(otherSession.player);
        if (other == null || !validBinding(other, otherSession)) {
            abortCoop(server, round, session.tournament
                    ? "Partner left the free Signal Table. No score or emeralds changed."
                    : "Partner left the table. Both stakes returned.", null);
            return true;
        }
        settleCoop(server, round, round.won());
        otherSession.revision++;
        otherSession.touch(other);
        send(other, otherSession);
        return true;
    }

    private static void settleCoop(MinecraftServer server, CoopRound round, boolean won) {
        if (round.settled) return;
        round.settled = true;
        long worldDay = TournamentState.worldDay(server);
        Session firstSession = SESSIONS.get(round.first);
        String freeResult = firstSession != null && firstSession.tournament
                ? tournamentResult(server, CasinoGame.COOP, firstSession.tournamentDay, won) : null;
        for (UUID id : List.of(round.first, round.second)) {
            Session session = SESSIONS.get(id);
            if (session == null || session.settled) continue;
            session.settled = true;
            session.phase = Phase.RESULT;
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (session.tournament) {
                session.message = freeResult;
                if (player != null) {
                    play(player, won ? AdventureSounds.CASINO_CHIPS_STACK
                            : AdventureSounds.CASINO_LOSS, 0.65F, 1.0F);
                }
                continue;
            }
            if (won && player != null) {
                CasinoPlayerAccount account = CasinoPlayerAccount.of(player);
                int payout = account.claimCoopBonus(worldDay) ? 3 : 2;
                session.message = payout == 3
                        ? "Both guesses were right. Your 2 emeralds are back, plus 1 more."
                        : "Both guesses were right. Your 2 emeralds are back; today's bonus was already paid.";
                account.settle(payout);
                EmeraldStakes.deliverClaims(player, account);
                if (account.claimBalance() > 0) {
                    session.message += " Inventory full: your emeralds are saved for your next visit.";
                }
                if (payout > CasinoGame.COOP.stake()) {
                    play(player, AdventureSounds.CASINO_CHIPS_STACK, 0.7F, 1.05F);
                } else {
                    play(player, AdventureSounds.CASINO_CHIP_LAY, 0.55F, 1.15F);
                }
            } else if (!won) {
                session.message = "A guess was wrong. You each lost 2 emeralds.";
                if (player != null) {
                    CasinoPlayerAccount.of(player).forfeit();
                    play(player, AdventureSounds.CASINO_LOSS, 0.6F, 1.0F);
                }
            } else {
                // The player left between guesses and settlement; retain the
                // player-owned stake for a login refund.
                session.message = "You left before the result; your stake will be returned.";
            }
        }
    }

    private static void abortCoop(MinecraftServer server, CoopRound round, String reason, UUID deferredRefund) {
        if (round.settled) return;
        round.settled = true;
        for (UUID id : List.of(round.first, round.second)) {
            Session session = SESSIONS.get(id);
            if (session != null) {
                session.settled = true;
                session.phase = Phase.RESULT;
                session.message = reason;
                session.expiresAt = server.getTickCount() + TIMEOUT_TICKS;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if ((session == null || !session.tournament) && player != null && !id.equals(deferredRefund)) {
                CasinoPlayerAccount account = CasinoPlayerAccount.of(player);
                account.refund();
                EmeraldStakes.deliverClaims(player, account);
            }
            // Offline players keep their pending stake for the next login.
        }
    }

    private static void expire(MinecraftServer server, Session session) {
        if (session.phase == Phase.COOP_PLAY && session.coop != null && !session.coop.settled) {
            abortCoop(server, session.coop, session.tournament
                    ? "The free Signal Table timed out. No score or emeralds changed."
                    : "The co-op round timed out. Both stakes returned.", null);
        } else if (session.phase == Phase.ROUND && !session.settled) {
            session.settled = true;
            session.phase = Phase.RESULT;
            session.message = session.tournament
                    ? "Free challenge round timed out. No score or emeralds changed."
                    : "Round expired after ten minutes. The stake was lost.";
            session.expiresAt = server.getTickCount() + TIMEOUT_TICKS;
            ServerPlayer player = server.getPlayerList().getPlayer(session.player);
            if (!session.tournament) {
                if (player != null) CasinoPlayerAccount.of(player).forfeit();
                else session.forfeitPendingOnJoin = true;
            }
        } else if (session.phase == Phase.COOP_WAIT) {
            WAITING_BY_HOST.remove(session.host, session.player);
            WAITING_TOURNAMENT_BY_HOST.remove(session.host, session.player);
            session.phase = Phase.PREVIEW;
            session.message = "Your co-op seat timed out. No stake was taken.";
            session.expiresAt = server.getTickCount() + TIMEOUT_TICKS;
        } else if (session.phase == Phase.LOBBY || session.phase == Phase.TOURNAMENT_LOBBY
                || session.phase == Phase.PREVIEW || session.phase == Phase.RESULT) {
            // The client can still have this view open. Replace its expired choices
            // with a new revision instead of leaving a screen whose token is dead.
            session.revision++;
            session.tournament = false;
            session.tournamentDay = -1;
            session.round = null;
            session.coop = null;
            session.settled = false;
            session.forfeitPendingOnJoin = false;
            if (session.tableSign != null && session.game != null) {
                session.phase = Phase.PREVIEW;
                session.message = "This table timed out. Read its rules before you play again.";
            } else {
                session.phase = Phase.LOBBY;
                session.game = null;
                session.message = "This visit timed out. Choose a table to play again.";
            }
            session.expiresAt = server.getTickCount() + TIMEOUT_TICKS;
        }
    }

    private static boolean expired(ServerPlayer player, Session session) {
        return player.level().getServer().getTickCount() >= session.expiresAt;
    }

    private static boolean validHost(ServerPlayer player, FriendEntity host) {
        return host != null && host.isAlive() && "ragab".equals(host.friendId())
                && player.isAlive() && !player.isSpectator()
                && player.level() == host.level()
                && player.distanceToSqr(host) <= MAX_DISTANCE_SQUARED;
    }

    private static boolean validBinding(ServerPlayer player, Session session) {
        if (player == null || !player.getUUID().equals(session.player)
                || !player.level().dimension().equals(session.dimension)) return false;
        ServerLevel world = player.level();
        Entity entity = world.getEntity(session.host);
        if (!(entity instanceof FriendEntity friend)) return false;
        if (session.tableSign == null) return validHost(player, friend);
        BlockPos sign = session.tableSign;
        return friend.isAlive() && "ragab".equals(friend.friendId())
                && player.isAlive() && !player.isSpectator()
                && friend.distanceToSqr(sign.getX() + 0.5, sign.getY() + 0.5,
                        sign.getZ() + 0.5) <= 24 * 24
                && player.distanceToSqr(sign.getX() + 0.5, sign.getY() + 0.5,
                        sign.getZ() + 0.5) <= MAX_DISTANCE_SQUARED
                && CasinoFloorInteraction.isTable(world, sign);
    }

    private static void send(ServerPlayer player, Session session) {
        if (player == null) return;
        CasinoGame game = session.game;
        String title = session.tournament ? game == null ? "Daily Tournament" : "Free: " + game.title()
                : game == null ? "Ragab's Casino" : game.title();
        String rules = game == null ? session.tournament
                ? "Win three different featured tables together. The Signal Table needs two players; the three solo tables are enough for one player. No emeralds change hands."
                : "Choose a game to see its rules and stake."
                : session.tournament ? tournamentRules(game) : game.rules();
        List<String> board = new ArrayList<>();
        List<CasinoViewPayload.Choice> choices = new ArrayList<>();
        switch (session.phase) {
            case LOBBY -> {
                for (CasinoGame option : CasinoGame.values()) {
                    board.add(option.title() + " · " + option.stake() + " emeralds");
                    choices.add(new CasinoViewPayload.Choice("preview_" + option.id(),
                            option.title() + " · " + option.stake() + " E"));
                }
                choices.add(new CasinoViewPayload.Choice("tournament", "Daily free tournament"));
            }
            case TOURNAMENT_LOBBY -> {
                long day = TournamentState.worldDay(player.level().getServer());
                TournamentState state = TournamentState.get(player.level().getServer());
                int wonTables = state.wonTables(day);
                board.add("Day " + (day + 1) + ": " + Math.min(3, Integer.bitCount(wonTables))
                        + "/3 featured tables won for the world.");
                if (session.tournamentDay != day) {
                    session.tournamentDay = day;
                    session.message = "A new Minecraft day began. Choose one of today's featured tables.";
                }
                for (CasinoGame option : TournamentProgress.featured(day)) {
                    choices.add(new CasinoViewPayload.Choice("challenge_" + option.id(),
                            option.title() + ((wonTables & TournamentProgress.bit(option)) != 0
                                    ? " · won" : option == CasinoGame.COOP ? " · duo" : " · solo")));
                }
                choices.add(new CasinoViewPayload.Choice("all_games", "Regular games"));
            }
            case PREVIEW -> {
                board.add(session.tournament ? "Free to play. A win at this table counts once today."
                        : "Cost to play: " + game.stake() + " emeralds");
                board.add(session.tournament ? "A free round idle for 10 minutes ends without a win."
                        : "A round idle for 10 minutes loses a solo stake; Signal Table refunds both.");
                choices.add(new CasinoViewPayload.Choice("commit", session.tournament
                        ? "Play free round" : "Pay " + game.stake() + " emeralds"));
                choices.add(new CasinoViewPayload.Choice("back", session.tournament
                        ? "Featured tables" : "All games"));
            }
            case ROUND -> {
                board.addAll(session.round.board());
                for (CasinoRound.Choice choice : session.round.choices()) {
                    choices.add(new CasinoViewPayload.Choice(choice.id(), choice.label()));
                }
            }
            case COOP_WAIT -> {
                board.add("Seat reserved. Another player must choose this table.");
                board.add("No emeralds have been taken yet.");
                choices.add(new CasinoViewPayload.Choice("back", "Leave seat"));
            }
            case COOP_PLAY -> {
                CoopRound round = session.coop;
                board.add("Your color: " + SIGNALS[round.signalFor(session.player)]);
                board.add("Tell your partner your color. Choose the one they see.");
                if (!round.hasGuessed(session.player)) {
                    choices.add(new CasinoViewPayload.Choice("guess_ruby", "Ruby"));
                    choices.add(new CasinoViewPayload.Choice("guess_jade", "Jade"));
                    choices.add(new CasinoViewPayload.Choice("guess_azure", "Azure"));
                }
            }
            case RESULT -> {
                if (session.round != null) board.addAll(session.round.board());
                choices.add(new CasinoViewPayload.Choice("lobby", session.tournament
                        ? "Featured tables" : "Another game"));
            }
        }
        ServerPlayNetworking.send(player, new CasinoViewPayload(session.token, session.revision,
                session.phase.name(),
                game == null ? "lobby" : game.id(), title, rules, session.message,
                game == null || session.tournament ? 0 : game.stake(), EmeraldStakes.count(player), List.copyOf(board),
                session.round == null ? List.of() : session.round.playerCardSprites(),
                session.round == null ? List.of() : session.round.dealerCardSprites(),
                List.copyOf(choices)));
    }

    private static void play(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.PLAYERS,
                player.getX(), player.getY(), player.getZ(), volume, pitch,
                player.getRandom().nextLong()));
    }

    private static String tournamentRules(CasinoGame game) {
        String rules = switch (game) {
            case RPS -> "Win two rock-paper-scissors throws before the dealer. Ties replay.";
            case CARDS -> "Get closer to 21 than the dealer without going over. Hit or stand; the dealer draws to 17.";
            case WHEEL -> "Call red, black, low, or high. Zero beats every call.";
            case REELS -> "Keep one symbol or spin all three again. Match at least two symbols.";
            case TRIVIA -> "Answer one Minecraft or group question correctly.";
            case COOP -> "Each player sees a color. Tell your partner yours, then choose theirs. Both guesses must be right.";
        };
        return rules + " Meet that goal to score this table once today. No emeralds change hands.";
    }
}
