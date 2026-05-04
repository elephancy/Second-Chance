package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.CampaignPersistentState;
import com.feel.gems.adventure.CampaignRewards;
import com.feel.gems.adventure.DomainBlocks;
import com.feel.gems.adventure.LandmarkKey;
import com.feel.gems.adventure.boss.SignalEchoEntity;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opt-in two-client receipt for shared Hushwire conquest and the town ledger. */
public final class SharedConquestLoopbackServerSmoke implements ModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("gems-shared-conquest-loopback");
    private static final int Y = 150;
    private static final BlockPos LEDGER = new BlockPos(0, Y, -1);
    private String phase;
    private ServerPlayer a;
    private ServerPlayer b;
    private String expectedRole;
    private String expectedStage;
    private boolean ready;
    private boolean failed;
    private int stopAt;

    @Override
    public void onInitialize() {
        if (!Boolean.getBoolean("gems.sharedConquestLoopback.server")) return;
        phase = System.getProperty("gems.sharedConquestLoopback.phase", "");
        if (!Set.of("initial", "resume").contains(phase))
            throw new IllegalArgumentException("Shared conquest phase must be initial or resume");
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(Commands.literal("scmp")
                        .then(Commands.literal("ack")
                                .then(Commands.argument("stage", StringArgumentType.word())
                                        .executes(context -> acknowledge(
                                                context.getSource().getServer(),
                                                context.getSource().getPlayer(),
                                                StringArgumentType.getString(context, "stage")))))));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> join(server, handler.getPlayer()));
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }

    private void tick(MinecraftServer server) {
        if (!ready && server.getTickCount() >= 80) {
            ServerLevel level = server.overworld();
            stage(level);
            boolean saved = state(server).model().worldChanged(LandmarkKey.HUSHWIRE);
            if (saved != phase.equals("resume")) {
                fail(server, "saved world conquest disagrees with phase=" + phase);
                return;
            }
            ready = true;
            LOG.info("SCMP_READY_A phase={} worldChanged={}", phase, saved);
        }
        if (stopAt > 0 && server.getTickCount() >= stopAt) server.halt(false);
        if (ready && stopAt == 0 && server.getTickCount() > 20 * 300)
            fail(server, "five-minute two-client timeout");
    }

    private void join(MinecraftServer server, ServerPlayer player) {
        String role = player.getName().getString();
        if (!ready || failed || (!role.equals("CoopA") && !role.equals("CoopB"))) {
            fail(server, "unexpected or early join " + role);
            return;
        }
        player.teleportTo(server.overworld(), .5, Y, .5, Set.of(), 0.0F, 0.0F, true);
        LOG.info("SCMP_JOIN phase={} role={} uuid={} remote={}", phase, role,
                player.getUUID(), player.connection.getRemoteAddress());
        if (role.equals("CoopA")) {
            if (a != null || b != null) { fail(server, "duplicate first client"); return; }
            a = player;
            boolean personal = state(server).model().hasCleared(player.getUUID(), LandmarkKey.HUSHWIRE);
            if (personal != phase.equals("resume")) {
                fail(server, "first player's saved conquest disagrees with phase");
                return;
            }
            instruct(player, phase.equals("resume") ? "persisted" : "locked");
        } else {
            if (b != null || a == null || !"CoopB".equals(expectedRole)) {
                fail(server, "late client joined before first client's ledger receipt");
                return;
            }
            b = player;
            if (!state(server).model().worldChanged(LandmarkKey.HUSHWIRE)
                    || state(server).model().hasCleared(player.getUUID(), LandmarkKey.HUSHWIRE)) {
                fail(server, "late client should see world conquest without personal victory");
                return;
            }
            instruct(player, "late");
        }
    }

    private int acknowledge(MinecraftServer server, ServerPlayer player, String stage) {
        String role = player.getName().getString();
        if (failed || !stage.equals(expectedStage) || !role.equals(expectedRole)) {
            fail(server, "out-of-order ledger acknowledgement " + role + "/" + stage);
            return 0;
        }
        boolean changed = state(server).model().worldChanged(LandmarkKey.HUSHWIRE);
        if (stage.equals("locked")) {
            if (changed || echoes(server.overworld(), player) != 0) {
                fail(server, "locked ledger granted a scout or world conquest");
                return 0;
            }
            CampaignRewards.recordVictory(server, List.of(player), LandmarkKey.HUSHWIRE);
            if (!state(server).model().worldChanged(LandmarkKey.HUSHWIRE)
                    || !state(server).model().hasCleared(player.getUUID(), LandmarkKey.HUSHWIRE)) {
                fail(server, "recorded victory did not open the shared world service");
                return 0;
            }
            LOG.info("SCMP_CONQUEST actor=CoopA worldChanged=true personalA=true setup=recordVictory");
            instruct(player, "unlocked");
            return 1;
        }
        if (!changed || echoes(server.overworld(), player) < 1) {
            fail(server, "ledger message had no owned Signal Echo for " + role);
            return 0;
        }
        LOG.info("SCMP_LEDGER phase={} role={} stage={} ownedEcho=true", phase, role, stage);
        if (role.equals("CoopA")) {
            expectedRole = "CoopB";
            expectedStage = "late";
            LOG.info("SCMP_READY_B phase={}", phase);
        } else {
            if (state(server).model().hasCleared(player.getUUID(), LandmarkKey.HUSHWIRE)) {
                fail(server, "late joiner acquired an unearned personal first clear");
                return 0;
            }
            LOG.info("SCMP_PHASE_PASS phase={} sharedConquest=true lateJoin=true eachLedgerEcho=true", phase);
            for (ServerPlayer peer : List.of(a, b))
                peer.sendSystemMessage(Component.literal("SCMP|COMPLETE|" + phase));
            expectedRole = "";
            expectedStage = "";
            stopAt = server.getTickCount() + 20 * 60;
        }
        return 1;
    }

    private void instruct(ServerPlayer player, String stage) {
        expectedRole = player.getName().getString();
        expectedStage = stage;
        player.sendSystemMessage(Component.literal("SCMP|USE|" + stage));
        LOG.info("SCMP_INSTRUCT phase={} role={} stage={}", phase, expectedRole, stage);
    }

    private static CampaignPersistentState state(MinecraftServer server) {
        return CampaignPersistentState.get(server);
    }

    private static int echoes(ServerLevel level, ServerPlayer player) {
        return level.getEntitiesOfClass(SignalEchoEntity.class,
                new AABB(LEDGER).inflate(12), echo -> echo.belongsTo(player)).size();
    }

    private static void stage(ServerLevel level) {
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            level.setBlock(new BlockPos(x, Y - 1, z), Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
            for (int dy = 0; dy <= 3; dy++)
                level.setBlock(new BlockPos(x, Y + dy, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(LEDGER, DomainBlocks.TOWNHALL_LEDGER.defaultBlockState(), Block.UPDATE_ALL);
    }

    private void fail(MinecraftServer server, String reason) {
        if (failed) return;
        failed = true;
        LOG.error("SCMP_PHASE_FAILED phase={} reason={}", phase, reason);
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            player.sendSystemMessage(Component.literal("SCMP|FAILED|" + reason));
        stopAt = server.getTickCount() + 20;
    }
}
