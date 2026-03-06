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
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Server-owned flood pulse, refuge checkpoints and world-shared shelter service. */
public final class KhaledCrossingRuntime {
    private static final String DOMAIN = "khaled";
    private static final DustParticleOptions WATER_MARK =
            new DustParticleOptions(0x83CBD5, 1.0F);
    private static final Map<UUID, Attempt> ATTEMPTS = new HashMap<>();
    private static final Map<UUID, Long> DESK_COOLDOWNS = new HashMap<>();

    private record Attempt(ResourceKey<Level> dimension, BlockPos startPos, KhaledCrossingPlan plan,
                           boolean repairMode, RiverwardBraceWindow windows) { }

    private KhaledCrossingRuntime() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            Block block = world.getBlockState(hit.getBlockPos()).getBlock();
            if (block != DomainBlocks.KHALED_ROUTE_START
                    && block != DomainBlocks.KHALED_ROUTE_FINISH
                    && block != DomainBlocks.KHALED_WEST_BRACE
                    && block != DomainBlocks.KHALED_EAST_BRACE
                    && block != DomainBlocks.KHALED_WEST_BRACE_RESTORED
                    && block != DomainBlocks.KHALED_EAST_BRACE_RESTORED
                    && block != DomainBlocks.TOWNHALL_SHELTER_DESK) {
                return InteractionResult.PASS;
            }
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            if (block == DomainBlocks.KHALED_ROUTE_START) return start(level, serverPlayer, hit.getBlockPos());
            if (block == DomainBlocks.KHALED_ROUTE_FINISH) return finish(level, serverPlayer, hit.getBlockPos());
            if (block != DomainBlocks.TOWNHALL_SHELTER_DESK) {
                return brace(level, serverPlayer, hit.getBlockPos(), block);
            }
            return desk(level, serverPlayer);
        });
        ServerTickEvents.END_SERVER_TICK.register(KhaledCrossingRuntime::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                ATTEMPTS.remove(handler.getPlayer().getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ATTEMPTS.clear();
            DESK_COOLDOWNS.clear();
        });
    }

    private static InteractionResult start(ServerLevel level, ServerPlayer player, BlockPos startPos) {
        MinecraftServer server = level.getServer();
        boolean repairMode = CampaignRouteState.get(server).chosen() == Path.REPAIR
                && RepairRouteState.get(server).soryalComplete()
                && FriendDomainUnlockState.get(server).cleared(DOMAIN)
                && !RepairRouteState.get(server).riverwardComplete();
        ATTEMPTS.put(player.getUUID(), new Attempt(level.dimension(), startPos,
                new KhaledCrossingPlan(server.getTickCount()), repairMode, new RiverwardBraceWindow()));
        tell(player, repairMode
                ? "First surge in 12s. The refuges and their braces show the old route."
                : "First surge in 12s. The roofed refuges still mark the crossing.");
        return InteractionResult.SUCCESS_SERVER;
    }

    private static InteractionResult finish(ServerLevel level, ServerPlayer player, BlockPos finishPos) {
        Attempt attempt = ATTEMPTS.get(player.getUUID());
        if (attempt == null || !attempt.dimension().equals(level.dimension())) {
            tell(player, "Start at the marked stone in Khaled's shelter walk.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (attempt.repairMode() && !nearStart(attempt, finishPos)) {
            tell(player, "Finish at the shieldworks linked to the shelter walk where you started.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!attempt.plan().finish(level.getServer().getTickCount())) {
            tell(player, "The shieldworks mark is waiting for both roofed refuges.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (attempt.repairMode()) {
            RepairRouteState repair = RepairRouteState.get(level.getServer());
            if (!repair.riverwardBracesReady()) {
                tell(player, "Both refuge braces still carry damage from the flood.");
                return InteractionResult.SUCCESS_SERVER;
            }
            ATTEMPTS.remove(player.getUUID());
            if (repair.finishRiverward()) {
                level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                        "Riverward's flood route is held open. The Repair route continues at Redline House."), false);
            } else {
                tell(player, "The flood route is already held open for the group.");
            }
            level.playSound(null, player.blockPosition(), ModSounds.ARCANE_CHIME,
                    SoundSource.BLOCKS, 0.7F, 1.05F);
            return InteractionResult.SUCCESS_SERVER;
        }
        ATTEMPTS.remove(player.getUUID());
        if (FriendDomainUnlockState.get(level.getServer()).clear(DOMAIN)) {
            level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                    "Khaled's crossing is clear. Everyone can use the Shelter Desk at the townhall."), false);
        } else {
            tell(player, "Crossing complete. The Shelter Desk is already open to everyone.");
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.ARCANE_CHIME, SoundSource.BLOCKS, 0.6F, 0.95F);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static InteractionResult brace(ServerLevel level, ServerPlayer player,
                                           BlockPos pos, Block block) {
        MinecraftServer server = level.getServer();
        if (CampaignRouteState.get(server).chosen() != Path.REPAIR
                || !RepairRouteState.get(server).soryalComplete()
                || !FriendDomainUnlockState.get(server).cleared(DOMAIN)) {
            tell(player, "Clear Soryal's repairs and Khaled's first crossing before mending these braces.");
            return InteractionResult.SUCCESS_SERVER;
        }
        int site = block == DomainBlocks.KHALED_WEST_BRACE
                || block == DomainBlocks.KHALED_WEST_BRACE_RESTORED
                ? RepairRouteState.WEST_BRACE : RepairRouteState.EAST_BRACE;
        Block restored = site == RepairRouteState.WEST_BRACE
                ? DomainBlocks.KHALED_WEST_BRACE_RESTORED : DomainBlocks.KHALED_EAST_BRACE_RESTORED;
        RepairRouteState repair = RepairRouteState.get(server);
        if (block == restored) {
            repair.recordRiverwardBrace(site);
            tell(player, "This refuge brace is already mended.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (repair.riverwardBraceRepaired(site)) {
            if (level.setBlock(pos, restored.defaultBlockState(), Block.UPDATE_ALL)) {
                tell(player, "This refuge brace is already mended.");
            } else {
                tell(player, "The brace did not take. Try again.");
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        Attempt attempt = ATTEMPTS.get(player.getUUID());
        Block floor = level.getBlockState(player.blockPosition().below()).getBlock();
        Block refuge = site == RepairRouteState.WEST_BRACE
                ? DomainBlocks.KHALED_WEST_REFUGE : DomainBlocks.KHALED_EAST_REFUGE;
        if (attempt == null || !attempt.repairMode() || !attempt.dimension().equals(level.dimension())
                || !nearStart(attempt, pos) || attempt.plan().expired(server.getTickCount())) {
            tell(player, "Start a repair crossing at Khaled's shelter walk first.");
        } else if (floor != refuge || player.distanceToSqr(pos.getX() + 0.5,
                pos.getY() + 0.5, pos.getZ() + 0.5) > 25) {
            tell(player, "Stand on this refuge's marked stone to reach its brace.");
        } else if (!attempt.windows().canRepair(site, server.getTickCount())) {
            tell(player, "Wait here for the water to pass beneath the shelter, then mend the brace.");
        } else if (!level.setBlock(pos, restored.defaultBlockState(), Block.UPDATE_ALL)) {
            tell(player, "The brace did not take. Try again.");
        } else {
            repair.recordRiverwardBrace(site);
            level.playSound(null, pos, ModSounds.ARCANE_CHIME, SoundSource.BLOCKS, 0.5F, 1.15F);
            tell(player, repair.riverwardBracesReady()
                    ? "Both braces hold. The shieldworks mark glows before the crossing fades."
                    : "Brace mended. The other refuge still pulses.");
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static boolean nearStart(Attempt attempt, BlockPos pos) {
        return Math.abs(attempt.startPos().getX() - pos.getX()) <= 128
                && Math.abs(attempt.startPos().getY() - pos.getY()) <= 48
                && Math.abs(attempt.startPos().getZ() - pos.getZ()) <= 128;
    }

    private static InteractionResult desk(ServerLevel level, ServerPlayer player) {
        if (!FriendDomainUnlockState.get(level.getServer()).cleared(DOMAIN)) {
            tell(player, "Cross Khaled's roofed floodway to open this desk.");
            return InteractionResult.SUCCESS_SERVER;
        }
        long now = level.getServer().getTickCount();
        long left = DESK_COOLDOWNS.getOrDefault(player.getUUID(), 0L) - now;
        if (left > 0) {
            tell(player, "Shelter ready in " + ((left + 19) / 20) + " seconds.");
            return InteractionResult.SUCCESS_SERVER;
        }
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 30, 0));
        DESK_COOLDOWNS.put(player.getUUID(), now + 20 * 90);
        tell(player, "You have 30 seconds of shelter. The desk recharges in 90 seconds.");
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.ARCANE_CHIME, SoundSource.BLOCKS, 0.5F, 0.75F);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        if (now % 5 != 0) return;
        if (now % 200 == 0) {
            DESK_COOLDOWNS.entrySet().removeIf(entry -> entry.getValue() <= now);
        }
        var iterator = ATTEMPTS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Attempt attempt = entry.getValue();
            if (player == null || !player.level().dimension().equals(attempt.dimension())) {
                iterator.remove();
                continue;
            }
            Block floor = player.level().getBlockState(player.blockPosition().below()).getBlock();
            KhaledCrossingPlan.Event progress = attempt.plan().visit(
                    floor == DomainBlocks.KHALED_WEST_REFUGE,
                    floor == DomainBlocks.KHALED_EAST_REFUGE);
            if (progress == KhaledCrossingPlan.Event.WEST_REACHED) {
                tell(player, "First refuge reached. The far shelter's marker still glows.");
            } else if (progress == KhaledCrossingPlan.Event.EAST_REACHED) {
                tell(player, "Both refuges reached. A mark inside the shieldworks lights up.");
            }
            boolean sheltered = floor == DomainBlocks.KHALED_ROUTE_START
                    || floor == DomainBlocks.KHALED_WEST_REFUGE
                    || floor == DomainBlocks.KHALED_EAST_REFUGE
                    || floor == DomainBlocks.KHALED_ROUTE_FINISH;
            KhaledCrossingPlan.Event pulse = attempt.plan().tick(now, sheltered);
            if (pulse == KhaledCrossingPlan.Event.EXPIRED
                    || pulse == KhaledCrossingPlan.Event.FLOODED) {
                iterator.remove();
                tell(player, pulse == KhaledCrossingPlan.Event.FLOODED
                        ? "The water caught the open path. Return to the start mark and try again."
                        : "The crossing faded. Return to the start mark and try again.");
                player.level().sendParticles(WATER_MARK, player.getX(), player.getY() + 0.6,
                        player.getZ(), 12, 0.6, 0.3, 0.6, 0.02);
            } else if (pulse == KhaledCrossingPlan.Event.WARNING) {
                tell(player, "Water rising in two seconds. Stand on a marked shelter stone.");
                player.level().sendParticles(WATER_MARK, player.getX(), player.getY() + 0.3,
                        player.getZ(), 6, 0.7, 0.1, 0.7, 0.01);
            } else if (pulse == KhaledCrossingPlan.Event.SHELTERED) {
                if (attempt.repairMode() && (floor == DomainBlocks.KHALED_WEST_REFUGE
                        || floor == DomainBlocks.KHALED_EAST_REFUGE)) {
                    int site = floor == DomainBlocks.KHALED_WEST_REFUGE
                            ? RepairRouteState.WEST_BRACE : RepairRouteState.EAST_BRACE;
                    attempt.windows().shelteredPulse(site, now);
                    tell(player, "The water passed. Mend this refuge's brace now.");
                } else {
                    tell(player, "The water passed under your shelter. Keep going.");
                }
            }
        }
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), true);
    }
}
