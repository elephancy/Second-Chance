package com.feel.gems.adventure;

import com.feel.gems.adventure.CampaignRouteState.Path;
import com.feel.gems.sound.ModSounds;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Server-owned Redline supply carry; only active runs are checked every five ticks. */
public final class RedlineAidRuntime {
    private static final Map<UUID, Attempt> ATTEMPTS = new HashMap<>();

    private record Attempt(ResourceKey<Level> dimension, BlockPos start, RedlineAidPlan plan) { }

    private RedlineAidRuntime() { }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            Block block = world.getBlockState(hit.getBlockPos()).getBlock();
            if (block != DomainBlocks.REDLINE_AID_START && block != DomainBlocks.REDLINE_AID_FINISH) {
                return InteractionResult.PASS;
            }
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            return block == DomainBlocks.REDLINE_AID_START
                    ? start(level, serverPlayer, hit.getBlockPos())
                    : finish(level, serverPlayer, hit.getBlockPos());
        });
        ServerTickEvents.END_SERVER_TICK.register(RedlineAidRuntime::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                ATTEMPTS.remove(handler.getPlayer().getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ATTEMPTS.clear());
    }

    private static InteractionResult start(ServerLevel level, ServerPlayer player, BlockPos pos) {
        MinecraftServer server = level.getServer();
        RepairRouteState repair = RepairRouteState.get(server);
        if (CampaignRouteState.get(server).chosen() != Path.REPAIR
                || !repair.riverwardComplete()
                || !CampaignPersistentState.get(server).model().worldChanged(LandmarkKey.REDLINE)) {
            tell(player, "Choose Repair, hold Riverward's crossing, then clear Redline House first.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (repair.redlineComplete()) {
            tell(player, "The infirmary and watchpost already share an open aid route.");
            return InteractionResult.SUCCESS_SERVER;
        }
        ATTEMPTS.put(player.getUUID(), new Attempt(level.dimension(), pos,
                new RedlineAidPlan(server.getTickCount())));
        tell(player, "Aid run started. The gold shelters mark the line between infirmary and watchpost. You have two minutes.");
        return InteractionResult.SUCCESS_SERVER;
    }

    private static InteractionResult finish(ServerLevel level, ServerPlayer player, BlockPos pos) {
        Attempt attempt = ATTEMPTS.get(player.getUUID());
        if (attempt == null || !attempt.dimension().equals(level.dimension())
                || !near(attempt.start(), pos)) {
            tell(player, "Start the aid run at the infirmary mark beside its gold shelter.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!attempt.plan().ready(level.getServer().getTickCount())) {
            tell(player, "The aid line between the infirmary and watchpost shelters is still broken.");
            return InteractionResult.SUCCESS_SERVER;
        }
        ATTEMPTS.remove(player.getUUID());
        if (RepairRouteState.get(level.getServer()).finishRedline()) {
            level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                    "Aid reached Redline's watchpost. The three Repair field routes are ready."), false);
        } else {
            tell(player, "The aid route is already open for everyone.");
        }
        level.playSound(null, pos, ModSounds.ARCANE_CHIME, SoundSource.BLOCKS, 0.7F, 1.1F);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        if (now % 5 != 0) return;
        var iterator = ATTEMPTS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Attempt attempt = entry.getValue();
            if (player == null || !player.isAlive()
                    || !player.level().dimension().equals(attempt.dimension())
                    || !near(attempt.start(), player.blockPosition())) {
                iterator.remove();
                continue;
            }
            BlockPos floor = player.blockPosition().below();
            int shelter = shelterSide(player, floor, attempt.start());
            RedlineAidPlan.Event event = attempt.plan().tick(now, shelter == 1, shelter == -1);
            if (event == RedlineAidPlan.Event.EXPIRED) {
                iterator.remove();
                tell(player, "The aid run timed out. Return to the infirmary mark and try again.");
            } else if (event == RedlineAidPlan.Event.EAST_HELD) {
                tell(player, "The infirmary-side shelter is steady. The far shelter still flickers.");
                player.level().playSound(null, floor, ModSounds.ARCANE_CHIME,
                        SoundSource.BLOCKS, 0.4F, 1.0F);
            } else if (event == RedlineAidPlan.Event.WEST_HELD) {
                tell(player, "Both shelters held. The watchpost mark glows.");
                player.level().playSound(null, floor, ModSounds.ARCANE_CHIME,
                        SoundSource.BLOCKS, 0.5F, 1.2F);
            }
        }
    }

    /** The infirmary defines east even when the jigsaw house rotates in the world. */
    private static int shelterSide(ServerPlayer player, BlockPos floor, BlockPos start) {
        if (!player.level().getBlockState(floor).is(Blocks.CONCRETE.lime())) return 0;
        for (Direction towardCenter : Direction.Plane.HORIZONTAL) {
            BlockPos center = floor.relative(towardCenter, 9);
            if (!player.level().getBlockState(center).is(Blocks.LODESTONE)) continue;
            int dx = start.getX() - center.getX();
            int dz = start.getZ() - center.getZ();
            Direction infirmary = Math.abs(dx) >= Math.abs(dz)
                    ? (dx >= 0 ? Direction.EAST : Direction.WEST)
                    : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
            if (floor.equals(center.relative(infirmary, 9))) return 1;
            if (floor.equals(center.relative(infirmary.getOpposite(), 9))) return -1;
        }
        return 0;
    }

    private static boolean near(BlockPos start, BlockPos pos) {
        return Math.abs(start.getX() - pos.getX()) <= 160
                && Math.abs(start.getY() - pos.getY()) <= 48
                && Math.abs(start.getZ() - pos.getZ()) <= 160;
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), true);
    }
}
