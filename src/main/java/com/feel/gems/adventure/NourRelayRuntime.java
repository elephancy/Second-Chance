package com.feel.gems.adventure;

import com.feel.gems.adventure.CampaignRouteState.Path;
import com.feel.gems.sound.ModSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** A public timed relay across Nour's three connected harbor buildings. */
public final class NourRelayRuntime {
    private static final int MAX_LINK_DISTANCE = 128;
    private static Attempt active;

    private record Attempt(ResourceKey<Level> dimension, BlockPos origin, NourRelayPlan plan) { }

    private NourRelayRuntime() { }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            Block block = world.getBlockState(hit.getBlockPos()).getBlock();
            if (block != DomainBlocks.NOUR_RELAY_TOWER
                    && block != DomainBlocks.NOUR_RELAY_BOAT
                    && block != DomainBlocks.NOUR_RELAY_FOG) return InteractionResult.PASS;
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            MinecraftServer server = level.getServer();
            if (CampaignRouteState.get(server).chosen() != Path.RELEASE
                    || !FriendDomainUnlockState.get(server).cleared("nour")) {
                tell(serverPlayer, "Choose Release and tune Nour's original harbor call first.");
                return InteractionResult.SUCCESS_SERVER;
            }
            if (ReleaseRouteState.get(server).nourRelayComplete()) {
                tell(serverPlayer, "The harbor relay is open for everyone.");
                return InteractionResult.SUCCESS_SERVER;
            }
            BlockPos pos = hit.getBlockPos();
            return block == DomainBlocks.NOUR_RELAY_TOWER
                    ? tower(level, serverPlayer, pos)
                    : call(level, serverPlayer, pos,
                            block == DomainBlocks.NOUR_RELAY_BOAT
                                    ? NourRelayPlan.BOATHOUSE : NourRelayPlan.FOGHOUSE);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> active = null);
    }

    private static InteractionResult tower(ServerLevel level, ServerPlayer player, BlockPos pos) {
        long now = level.getServer().getTickCount();
        if (active != null && active.plan().expired(now)) active = null;
        if (active == null) {
            active = new Attempt(level.dimension(), pos, new NourRelayPlan(now));
            announce(level, "Nour's relay is live for three minutes. The two side houses are still quiet.");
            level.playSound(null, pos, ModSounds.ARCANE_CHIME, SoundSource.BLOCKS, 0.7F, 1.2F);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!active.dimension().equals(level.dimension()) || !active.origin().equals(pos)) {
            tell(player, "Another harbor relay is active. Finish it or wait for its window to close.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!active.plan().ready(now)) {
            tell(player, active.plan().count() + "/2 side-house calls heard. "
                    + active.plan().secondsLeft(now) + " seconds left.");
            return InteractionResult.SUCCESS_SERVER;
        }
        active = null;
        if (ReleaseRouteState.get(level.getServer()).finishNourRelay()) {
            announce(level, "The harbor relay reached both houses. The Release route continues at Splitstone Heights.");
        }
        level.playSound(null, pos, ModSounds.ARCANE_CHIME, SoundSource.BLOCKS, 0.85F, 1.55F);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static InteractionResult call(ServerLevel level, ServerPlayer player, BlockPos pos, int site) {
        long now = level.getServer().getTickCount();
        if (active == null || !active.dimension().equals(level.dimension())
                || !near(active.origin(), pos)) {
            tell(player, "The side house cannot reach the tower while the harbor relay is quiet.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (active.plan().expired(now)) {
            active = null;
            tell(player, "The harbor relay went quiet. The tower can open another three-minute window.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!active.plan().record(site, now)) {
            tell(player, "This house has already answered. Another call is still missing.");
            return InteractionResult.SUCCESS_SERVER;
        }
        String house = site == NourRelayPlan.BOATHOUSE ? "boathouse" : "foghouse";
        announce(level, player.getName().getString() + " sent the " + house + " call ("
                + active.plan().count() + "/2). "
                + (active.plan().count() == 2 ? "The radio hall is receiving both replies."
                : "The other house remains quiet."));
        level.playSound(null, pos, ModSounds.ARCANE_CHIME, SoundSource.BLOCKS, 0.55F, 1.3F);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static boolean near(BlockPos origin, BlockPos pos) {
        return Math.abs(origin.getX() - pos.getX()) <= MAX_LINK_DISTANCE
                && Math.abs(origin.getY() - pos.getY()) <= 48
                && Math.abs(origin.getZ() - pos.getZ()) <= MAX_LINK_DISTANCE;
    }

    private static void announce(ServerLevel level, String message) {
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(message), false);
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), true);
    }
}
