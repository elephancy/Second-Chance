package com.feel.gems.casino;

import com.feel.gems.friend.FriendEntity;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Disposable dedicated-server gate for player-owned Signal Table money. */
public final class CasinoNetworkServerSmoke implements ModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("casino-network-server");
    private static final BlockPos ORIGIN = new BlockPos(0, 145, 0);
    private static final BlockPos SIGN = ORIGIN.offset(20, 5, 23);
    private boolean ready;
    private boolean sawWin;
    private boolean sawPending;
    private boolean sawResume;
    private boolean sawSavedHost;
    private final Set<java.util.UUID> refunded = new HashSet<>();
    private int startTick;
    private final Set<java.util.UUID> identities = new HashSet<>();

    @Override
    public void onInitialize() {
        if (!Boolean.getBoolean("gems.casinoNetwork.server")) return;
        boolean resume = Boolean.getBoolean("gems.casinoNetwork.resume");
        ServerTickEvents.END_SERVER_TICK.register(server -> tick(server, resume));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!ready) fail("player joined before casino fixture was ready");
            ServerPlayer player = handler.getPlayer();
            String name = player.getName().getString();
            if (!name.equals("CasinoA") && !name.equals("CasinoB"))
                fail("unexpected player " + name);
            if (!identities.add(player.getUUID())) fail("duplicate fixture identity " + name);
            if (!resume) player.getInventory().setItem(0, new ItemStack(Items.EMERALD, 64));
            player.teleportTo(server.overworld(), SIGN.getX() + (name.equals("CasinoA") ? 0.5 : -0.5),
                    SIGN.getY(), SIGN.getZ() - 1.5,
                    java.util.EnumSet.noneOf(Relative.class), 0.0F, 0.0F, false);
            LOG.info("CASINO_NETWORK_JOIN phase={} player={} uuid={} remote={} wallet={} pending={} claim={}",
                    resume ? "resume" : "initial", name, player.getUUID(),
                    player.connection.getRemoteAddress(), EmeraldStakes.count(player),
                    CasinoPlayerAccount.of(player).pending(),
                    CasinoPlayerAccount.of(player).claimBalance());
        });
    }

    private void tick(MinecraftServer server, boolean resume) {
        if (!ready) {
            if (server.getTickCount() < 80) return;
            ServerLevel level = server.overworld();
            if (!resume) {
                var template = server.getStructureTemplateManager()
                        .get(Identifier.fromNamespaceAndPath("gems", "town_casino_deco_v2"))
                        .orElseThrow();
                if (!template.placeInWorld(level, ORIGIN, ORIGIN, new StructurePlaceSettings(),
                        RandomSource.create(), 2)) fail("casino v2 template placement failed");
            }
            // A restarted dedicated server has no player tickets yet. Load
            // only the two fixture chunks before checking saved blocks/entities.
            level.getChunkAt(SIGN);
            level.getChunkAt(ORIGIN.offset(15, 5, 24));
            if (!CasinoFloorInteraction.matches(level, SIGN, CasinoGame.COOP))
                fail("saved Signal Table sign missing");
            if (!resume && !hostPresent(level)) fail("placed Ragab host missing");
            ready = true;
            startTick = server.getTickCount();
            LOG.info("CASINO_NETWORK_READY phase={} sign={} host=present",
                    resume ? "resume" : "initial", SIGN);
        }
        if (server.getTickCount() - startTick > 20 * 240)
            fail("four-minute fixture timeout");
        var players = server.getPlayerList().getPlayers();
        if (resume && !players.isEmpty() && !sawSavedHost) {
            if (hostPresent(server.overworld())) {
                sawSavedHost = true;
                LOG.info("CASINO_NETWORK_SAVED_HOST_PRESENT ragab=loaded sign=loaded");
            }
        }
        if (resume) {
            for (ServerPlayer player : players) {
                int emeralds = EmeraldStakes.count(player);
                int pending = CasinoPlayerAccount.of(player).pending();
                long claim = CasinoPlayerAccount.of(player).claimBalance();
                if (emeralds == 65 && pending == 0 && claim == 0
                        && refunded.add(player.getUUID())) {
                    LOG.info("CASINO_NETWORK_REFUND_PLAYER player={} uuid={} wallet=65 pending=0 claim=0",
                            player.getName().getString(), player.getUUID());
                } else if (emeralds + pending != 65 || claim != 0) {
                    fail("restart money conservation failed for " + player.getName().getString()
                            + ": " + emeralds + "/" + pending + "/" + claim);
                }
            }
            if (!sawResume && refunded.size() == 2 && sawSavedHost) {
                sawResume = true;
                LOG.info("CASINO_NETWORK_REFUND_AFTER_RESTART wallets=65,65 pending=0,0 claim=0,0");
            }
            return;
        }
        if (players.size() != 2) return;
        if (players.get(0).getUUID().equals(players.get(1).getUUID()))
            fail("two connections share one identity");
        for (ServerPlayer player : players) {
            int emeralds = EmeraldStakes.count(player);
            int pending = CasinoPlayerAccount.of(player).pending();
            long claim = CasinoPlayerAccount.of(player).claimBalance();
            if (claim != 0 || pending != 0 && pending != 2)
                fail("unexpected money ledger for " + player.getName().getString()
                        + ": " + emeralds + "/" + pending + "/" + claim);
            int total = emeralds + pending;
            // During sequential settlement, one client can be a tick ahead.
            if (resume && total != 65 || !resume && total != 64 && total != 65
                    || !resume && sawWin && total != 65)
                fail("money conservation failed for " + player.getName().getString()
                        + ": " + emeralds + "/" + pending + "/" + claim);
        }
        if (!sawWin && players.stream().allMatch(p -> EmeraldStakes.count(p) == 65
                && CasinoPlayerAccount.of(p).pending() == 0)) {
            sawWin = true;
            LOG.info("CASINO_NETWORK_SERVER_WIN wallets=65,65 pending=0,0 claim=0,0");
        }
        if (sawWin && !sawPending && players.stream().allMatch(p -> EmeraldStakes.count(p) == 63
                && CasinoPlayerAccount.of(p).pending() == 2)) {
            sawPending = true;
            LOG.info("CASINO_NETWORK_SERVER_PENDING wallets=63,63 pending=2,2 claim=0,0");
        }
    }

    private static void fail(String reason) {
        LOG.error("CASINO_NETWORK_FAILED reason={}", reason);
        throw new IllegalStateException("Casino network fixture: " + reason);
    }

    private static boolean hostPresent(ServerLevel level) {
        return !level.getEntitiesOfClass(FriendEntity.class,
                new AABB(ORIGIN.offset(15, 5, 24)).inflate(4),
                friend -> friend.isAlive() && "ragab".equals(friend.friendId())).isEmpty();
    }
}
