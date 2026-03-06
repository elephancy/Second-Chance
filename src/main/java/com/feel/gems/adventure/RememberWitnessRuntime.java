package com.feel.gems.adventure;

import com.feel.gems.adventure.CampaignRouteState.Path;
import com.feel.gems.adventure.AdventureSounds;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;

/** Physical evidence from two expeditions, compared at Othman's library. */
public final class RememberWitnessRuntime {
    private RememberWitnessRuntime() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            Block block = world.getBlockState(hit.getBlockPos()).getBlock();
            if (block != DomainBlocks.BRANCH_WITNESS
                    && block != DomainBlocks.LASTLIGHT_WITNESS
                    && block != DomainBlocks.OTHMAN_COMPARISON) return InteractionResult.PASS;
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            CampaignRouteState route = CampaignRouteState.get(level.getServer());
            if (route.chosen() != Path.REMEMBER) {
                tell(serverPlayer, "The Story Council has not sent this world to compare accounts.");
            } else if (!route.rememberInkboundBound()) {
                tell(serverPlayer, "Bind two different prints in Hamed's paper garden first.");
            } else if (block == DomainBlocks.OTHMAN_COMPARISON) {
                compare(level, serverPlayer, route);
            } else {
                witness(level, serverPlayer, route, block == DomainBlocks.BRANCH_WITNESS);
            }
            return InteractionResult.SUCCESS_SERVER;
        });
    }

    private static void witness(ServerLevel level, ServerPlayer player, CampaignRouteState route, boolean branch) {
        int mark = branch ? 1 : 2;
        String account = branch
                ? "Branch's map has a route that was left off the public drawing."
                : "Lastlight's upper signal room has a timetable with a missing arrival.";
        if (route.rememberArchiveCompared()) {
            tell(player, account + " Both accounts are kept at Othman's library.");
        } else if (route.recordRememberWitness(mark)) {
            level.playSound(null, player.blockPosition(), AdventureSounds.STORY_PAGE_TURN,
                    SoundSource.BLOCKS, 0.5F, 1.0F);
            tell(player, account + " Witness " + route.rememberWitnessCount()
                    + "/2 saved for everyone. Compare both at Othman's library.");
        } else {
            tell(player, account + " This witness is already recorded.");
        }
    }

    private static void compare(ServerLevel level, ServerPlayer player, CampaignRouteState route) {
        OthmanArchiveState archive = OthmanArchiveState.get(level.getServer());
        if (route.rememberArchiveCompared()) {
            tell(player, "Both witnesses remain on the record. The shared lead is still on the Knowledge Shelf.");
        } else if (!archive.ready() || archive.published() == 0) {
            tell(player, "Read both library notes and share one lead at Othman's desks first.");
        } else if (route.rememberWitnessCount() < 2) {
            tell(player, "Branch's map room and Lastlight's upper signal room still hold traces. "
                    + route.rememberWitnessCount() + "/2 witnesses are recorded.");
        } else if (route.compareRememberArchive()) {
            String publicLead = archive.published() == OthmanArchiveState.CATALOG
                    ? "Branch's public map" : "Lastlight's public timetable";
            level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                    "Othman kept both witnesses beside " + publicLead
                            + ". Remember continues at Yehia's Storybook Vale."), false);
            level.playSound(null, player.blockPosition(), AdventureSounds.STORY_RECORD_SEAL,
                    SoundSource.BLOCKS, 0.7F, 1.0F);
        }
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), false);
    }
}
