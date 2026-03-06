package com.feel.gems.adventure;

import com.feel.gems.adventure.AdventureSounds;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Hamed's two-note workshop and world-unlocked group field plans. Server-thread only. */
public final class InkboundWorkshop {
    private static final String[] NAMES = {"Spark", "Shelter", "Route"};
    private static final String[] RECIPES = {"Lantern", "Trail", "Haven"};
    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();
    private static final Map<UUID, Long> DESK_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Integer> DESK_PLANS = new HashMap<>();
    private static final int FIELD_PLAN_TICKS = 20 * 180;
    private static final int DESK_RECHARGE_TICKS = 20 * 300;
    private static final double BRIEFING_RADIUS_SQUARED = 12 * 12;
    private static final int MAX_BRIEFED_PLAYERS = 8;

    private record Selection(int first, int second) {
    }

    private InkboundWorkshop() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            Block block = world.getBlockState(hit.getBlockPos()).getBlock();
            int note = note(block);
            if (note < 0 && block != DomainBlocks.INKBOUND_PRESS
                    && block != DomainBlocks.INKBOUND_BINDING
                    && block != DomainBlocks.TOWNHALL_INKBOUND_DESK) {
                return InteractionResult.PASS;
            }
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            if (note >= 0) return select(serverPlayer, note);
            if (block == DomainBlocks.INKBOUND_PRESS) return press(level, serverPlayer);
            if (block == DomainBlocks.INKBOUND_BINDING) return bind(level, serverPlayer);
            return useDesk(level, serverPlayer);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            SELECTIONS.remove(handler.getPlayer().getUUID());
            DESK_PLANS.remove(handler.getPlayer().getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SELECTIONS.clear();
            DESK_COOLDOWNS.clear();
            DESK_PLANS.clear();
        });
    }

    private static int note(Block block) {
        if (block == DomainBlocks.INKBOUND_SPARK) return 0;
        if (block == DomainBlocks.INKBOUND_SHELTER) return 1;
        if (block == DomainBlocks.INKBOUND_ROUTE) return 2;
        return -1;
    }

    private static InteractionResult select(ServerPlayer player, int note) {
        Selection old = SELECTIONS.get(player.getUUID());
        if (old == null || old.second() >= 0) {
            SELECTIONS.put(player.getUUID(), new Selection(note, -1));
            tell(player, NAMES[note] + " selected. One more note can join it.");
        } else if (old.first() == note) {
            tell(player, "That is the same note. The press needs two different impressions.");
        } else {
            SELECTIONS.put(player.getUUID(), new Selection(old.first(), note));
            tell(player, NAMES[old.first()] + " + " + NAMES[note] + ". The press is ready.");
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static InteractionResult press(ServerLevel level, ServerPlayer player) {
        Selection choice = SELECTIONS.remove(player.getUUID());
        if (choice == null || choice.second() < 0) {
            tell(player, "The press needs two different notes.");
            return InteractionResult.SUCCESS_SERVER;
        }
        int pair = InkboundProgress.pair(choice.first(), choice.second());
        if (pair < 0) return InteractionResult.FAIL;
        applyPair(player, pair);
        level.playSound(null, player.blockPosition(), AdventureSounds.STORY_INK_WRITE,
                SoundSource.BLOCKS, 0.65F, 1.0F);
        InkboundPersistentState state = InkboundPersistentState.get(level.getServer());
        boolean newRecipe = state.record(pair);
        CampaignRouteState route = CampaignRouteState.get(level.getServer());
        if (route.recordRememberPrint(pair)) {
            tell(player, "Remember: " + route.rememberPrintCount()
                    + "/2 different prints kept.");
        }
        tell(player, RECIPES[pair] + " drafted. " + (newRecipe
                ? Integer.bitCount(state.model().triedMask()) + "/3 recipes found."
                : "You know this recipe."));
        if (newRecipe && state.model().unlocked()) {
            level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                    "All Inkbound recipes are found. Hamed's townhall desk is open to everyone."), false);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static InteractionResult bind(ServerLevel level, ServerPlayer player) {
        CampaignRouteState route = CampaignRouteState.get(level.getServer());
        if (route.chosen() != CampaignRouteState.Path.REMEMBER) {
            tell(player, "The binding table waits for a group following Remember.");
        } else if (!InkboundPersistentState.get(level.getServer()).model().unlocked()) {
            tell(player, "The old press still holds an unmade impression.");
        } else if (route.rememberInkboundBound()) {
            tell(player, "Two different accounts are bound side by side. Neither was erased.");
        } else if (route.rememberPrintCount() < 2) {
            tell(player, "The paper garden is still waiting for another account from the press.");
        } else if (route.bindRememberInkbound()) {
            level.playSound(null, player.blockPosition(), AdventureSounds.STORY_RECORD_SEAL,
                    SoundSource.BLOCKS, 0.7F, 0.9F);
            level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                    "Hamed bound two different accounts. The Remember route continues at Othman's Library."), false);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static InteractionResult useDesk(ServerLevel level, ServerPlayer player) {
        if (level.dimension() != Level.OVERWORLD || player.isSpectator()) {
            tell(player, "Prepare field plans at the Overworld townhall.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!InkboundPersistentState.get(level.getServer()).model().unlocked()) {
            tell(player, "Inkbound's old press still has something left to print.");
            return InteractionResult.SUCCESS_SERVER;
        }
        int plan = DESK_PLANS.getOrDefault(player.getUUID(), 0);
        if (player.isShiftKeyDown()) {
            plan = (plan + 1) % RECIPES.length;
            DESK_PLANS.put(player.getUUID(), plan);
            tellDesk(player, RECIPES[plan] + " plan selected. Use normally to brief up to eight players within 12 blocks.");
            return InteractionResult.SUCCESS_SERVER;
        }
        long now = level.getServer().getTickCount();
        long remaining = DESK_COOLDOWNS.getOrDefault(player.getUUID(), 0L) - now;
        if (remaining > 0) {
            tell(player, "Your next field briefing is ready in " + ((remaining + 19) / 20) + " seconds.");
            return InteractionResult.SUCCESS_SERVER;
        }
        var group = level.players().stream()
                .filter(nearby -> !nearby.isSpectator()
                        && nearby.distanceToSqr(player) <= BRIEFING_RADIUS_SQUARED)
                .sorted(Comparator.comparingDouble(nearby -> nearby.distanceToSqr(player)))
                .limit(MAX_BRIEFED_PLAYERS)
                .toList();
        for (ServerPlayer nearby : group) {
            applyPlan(nearby, plan, FIELD_PLAN_TICKS);
            tellDesk(nearby, RECIPES[plan] + " field plan: " + planEffect(plan)
                    + " for three minutes. " + player.getName().getString() + " briefed the group.");
        }
        DESK_COOLDOWNS.put(player.getUUID(), now + DESK_RECHARGE_TICKS);
        level.playSound(null, player.blockPosition(), AdventureSounds.STORY_RECORD_SEAL,
                SoundSource.BLOCKS, 0.65F, 1.1F);
        tellDesk(player, "Briefed " + group.size() + (group.size() == 1 ? " traveler" : " travelers")
                + ". Sneak-use to choose another plan after your five-minute recharge.");
        return InteractionResult.SUCCESS_SERVER;
    }

    private static String planEffect(int plan) {
        return switch (plan) {
            case 0 -> "see in the dark and resist fire";
            case 1 -> "see in the dark and move faster";
            case 2 -> "endure hits and move faster";
            default -> throw new IllegalArgumentException("Invalid plan");
        };
    }

    private static void applyPlan(ServerPlayer player, int plan, int duration) {
        switch (plan) {
            case 0 -> {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, duration, 0));
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, duration, 0));
            }
            case 1 -> {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, duration, 0));
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, duration, 0));
            }
            case 2 -> {
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, duration, 0));
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, duration, 0));
            }
            default -> throw new IllegalArgumentException("Invalid plan");
        }
    }

    private static void applyPair(ServerPlayer player, int pair) {
        applyPlan(player, pair, 20 * 45);
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), true);
    }

    private static void tellDesk(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), false);
    }
}
