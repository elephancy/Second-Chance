package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.DomainBlocks;
import com.feel.gems.adventure.FriendDomainUnlockState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opt-in loopback multiplayer fixture; never included in the release jar. */
public final class NetworkedTownhallServerSmoke implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("gems-network-townhall-smoke");
    private static final int Y = 150;
    private boolean ready;
    private boolean unlocked;
    private boolean lateJoined;
    private boolean failed;
    private int stopAt;

    @Override
    public void onInitialize() {
        if (!Boolean.getBoolean("gems.networkTownhallSmoke")) return;
        boolean resume = Boolean.getBoolean("gems.networkTownhallSmoke.resume");
        ServerTickEvents.END_SERVER_TICK.register(server -> tick(server, resume));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!ready || failed) return;
            ServerPlayer player = handler.getPlayer();
            String name = player.getName().getString();
            if (!name.equals("SmokeA") && !name.equals("SmokeB")) {
                fail(server, "unexpected player " + name);
                return;
            }
            player.teleportTo(server.overworld(), 0.5, Y, 0.5,
                    java.util.Set.of(), 0.0F, 0.0F, true);
            LOGGER.info("NETWORK_PLAYER_JOIN name={} remote={}", name,
                    player.connection.getRemoteAddress());
            if (name.equals("SmokeB")) {
                if (!FriendDomainUnlockState.get(server).cleared("fam")) {
                    fail(server, "SmokeB joined before the shared guide opened");
                    return;
                }
                lateJoined = true;
                LOGGER.info("NETWORK_LATE_JOIN resume={} sharedGuide=true", resume);
                for (ServerPlayer peer : server.getPlayerList().getPlayers()) {
                    if (peer != player && peer.getName().getString().equals("SmokeA")) {
                        peer.sendSystemMessage(Component.literal("NETWORK_SMOKE_B_JOINED"));
                    }
                }
                stopAt = server.getTickCount() + 180;
            }
        });
    }

    private void tick(MinecraftServer server, boolean resume) {
        int tick = server.getTickCount();
        if (!ready && tick >= 80) {
            ServerLevel level = server.overworld();
            if (level == null) return;
            stage(level);
            ready = true;
            boolean savedUnlock = FriendDomainUnlockState.get(server).cleared("fam");
            if (savedUnlock != resume) {
                fail(server, "saved shared guide state did not match resume=" + resume);
                return;
            }
            LOGGER.info("NETWORK_STAGE_READY resume={} sharedGuide={}", resume, savedUnlock);
        }
        if (!ready) return;
        if (failed) {
            if (stopAt > 0 && tick >= stopAt) server.halt(false);
            return;
        }
        if (!resume && !unlocked && FriendDomainUnlockState.get(server).cleared("fam")) {
            boolean firstClientPresent = server.getPlayerList().getPlayers().stream()
                    .anyMatch(player -> player.getName().getString().equals("SmokeA"));
            if (!firstClientPresent || lateJoined) {
                fail(server, "shared guide opened without SmokeA alone");
                return;
            }
            unlocked = true;
            LOGGER.info("NETWORK_SHARED_UNLOCK actor=SmokeA sharedGuide=true");
        }
        if (stopAt > 0 && tick >= stopAt) {
            if (resume || unlocked) {
                LOGGER.info("NETWORK_SERVER_COMPLETE resume={} lateJoin={} sharedGuide={}",
                        resume, lateJoined, FriendDomainUnlockState.get(server).cleared("fam"));
            } else {
                LOGGER.error("NETWORK_SERVER_INCOMPLETE resume={} lateJoin={}", resume, lateJoined);
            }
            server.halt(false);
        }
        if (tick > 20 * 240 && stopAt == 0) fail(server, "four-minute fixture timeout");
    }

    private static void stage(ServerLevel level) {
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                level.setBlock(new BlockPos(x, Y - 1, z), Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
                for (int dy = 0; dy < 4; dy++) {
                    level.setBlock(new BlockPos(x, Y + dy, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        level.setBlock(new BlockPos(1, Y, 0), DomainBlocks.FAM_STORY_CLUE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(new BlockPos(0, Y, 1), DomainBlocks.FAM_SCHOOL_CLUE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(new BlockPos(-1, Y, 0), DomainBlocks.FAM_FORUM_BOARD.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(new BlockPos(0, Y, -1), DomainBlocks.TOWNHALL_WELCOME_BOARD.defaultBlockState(), Block.UPDATE_ALL);
    }

    private void fail(MinecraftServer server, String reason) {
        failed = true;
        LOGGER.error("NETWORK_SERVER_FAILED reason={}", reason);
        stopAt = server.getTickCount() + 20;
    }
}
