package com.feel.gems.adventure;

import com.feel.gems.sound.ModSounds;
import com.feel.gems.entity.StorybookFoldwingEntity;
import com.feel.gems.item.InventorySpace;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** A group-solvable garden tale and a portable clue to nearby stashes. */
public final class YehiaStoryRuntime {
    private static final String DOMAIN = "yehia";

    private YehiaStoryRuntime() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> StorybookFoldwingEntity.clearTableReservations());
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            Block block = world.getBlockState(hit.getBlockPos()).getBlock();
            if (block != DomainBlocks.YEHIA_TRUTH_CLUE
                    && block != DomainBlocks.YEHIA_PROP_CLUE
                    && block != DomainBlocks.YEHIA_STORY_TABLE
                    && block != DomainBlocks.YEHIA_MUSHROOM_MARK
                    && block != DomainBlocks.YEHIA_LANTERN_MARK
                    && block != DomainBlocks.YEHIA_TREE_MARK
                    && block != DomainBlocks.TOWNHALL_STORY_TRAIL) {
                return InteractionResult.PASS;
            }
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            if (block == DomainBlocks.TOWNHALL_STORY_TRAIL) return trail(level, serverPlayer);
            YehiaStoryState state = YehiaStoryState.get(level.getServer());
            if (block == DomainBlocks.YEHIA_STORY_TABLE) {
                StorybookFoldwingEntity.ensureAtStoryTable(level, hit.getBlockPos());
            }
            CampaignRouteState route = CampaignRouteState.get(level.getServer());
            if (state.completed() && route.chosen() == CampaignRouteState.Path.REMEMBER
                    && route.rememberArchiveCompared()) {
                InteractionResult remember = remember(level, serverPlayer, block, route);
                if (remember != InteractionResult.PASS) return remember;
            }
            if (block == DomainBlocks.YEHIA_TRUTH_CLUE || block == DomainBlocks.YEHIA_PROP_CLUE) {
                boolean truth = block == DomainBlocks.YEHIA_TRUTH_CLUE;
                state.record(truth ? YehiaStoryState.TRUTH : YehiaStoryState.PROP);
                tell(serverPlayer, truth
                        ? "Garden tale: the cap sheltered a borrowed flame. By morning, its light had reached the roots."
                        : "Prop house: the red door opens only in the painted version. The garden kept the other account." );
                if (state.ready() && !state.completed()) {
                    tell(serverPlayer, "Both stories are known. Yehia's story table holds their opening lines.");
                }
                return InteractionResult.SUCCESS_SERVER;
            }
            if (block == DomainBlocks.YEHIA_STORY_TABLE) {
                if (state.completed()) {
                    tell(serverPlayer, "The real garden path is found. The Story Trail is open at the townhall.");
                } else if (state.start()) {
                    tell(serverPlayer, "The table holds both accounts. The garden has left its own marks.");
                } else {
                    tell(serverPlayer, "The garden tale can begin here. Its two accounts are in the side houses.");
                }
                return InteractionResult.SUCCESS_SERVER;
            }
            int marker = block == DomainBlocks.YEHIA_MUSHROOM_MARK ? YehiaStoryState.MUSHROOM
                    : block == DomainBlocks.YEHIA_LANTERN_MARK ? YehiaStoryState.LANTERN
                    : YehiaStoryState.TREE;
            if (!state.started()) {
                tell(serverPlayer, "Start the tale at the story table inside Yehia's lodge.");
            } else if (state.completed()) {
                tell(serverPlayer, "This trail has already led the group to its ending.");
            } else if (state.mark(marker)) {
                FriendDomainUnlockState.get(level.getServer()).clear(DOMAIN);
                level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                        "Yehia's real trail is found. Everyone can claim a Story Thread at the townhall."), false);
                level.playSound(null, serverPlayer.blockPosition(), ModSounds.ARCANE_CHIME,
                        SoundSource.BLOCKS, 0.7F, 1.25F);
            } else if (state.step() == 0) {
                tell(serverPlayer, "The tale loses its thread and returns to its beginning. Nothing is lost.");
            } else {
                tell(serverPlayer, "The tale continues. " + state.step() + " of 3 moments remembered.");
            }
            return InteractionResult.SUCCESS_SERVER;
        });
    }

    private static InteractionResult remember(ServerLevel level, ServerPlayer player,
                                               Block block, CampaignRouteState route) {
        if (block == DomainBlocks.YEHIA_STORY_TABLE) {
            if (route.startRememberTale()) {
                tell(player, "The garden kept one telling. Remember asks what changes when its ending is told first.");
            } else if (route.rememberTaleStage() == 5) {
                tell(player, "Both versions of Yehia's tale are kept. The Story Council has the next lead.");
            } else if (route.rememberTaleStage() == 4) {
                tell(player, "The reverse trail is complete. Read the painted door in the Prop House.");
            } else {
                tell(player, "The Truth Garden's old marks look different in this telling. The painted door keeps its closing line.");
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        if (block == DomainBlocks.YEHIA_PROP_CLUE) {
            if (route.finishRememberTale()) {
                level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                        "Yehia kept the real path and the made-up door. Both stories have a place in the record."), false);
                level.playSound(null, player.blockPosition(), ModSounds.ARCANE_CHIME,
                        SoundSource.BLOCKS, 0.7F, 1.2F);
            } else if (route.rememberTaleStage() == 5) {
                tell(player, "The real path and the painted door are both in the record.");
            } else if (route.rememberTaleStage() == 0) {
                tell(player, "Start the reverse tale at Yehia's lodge table first.");
            } else {
                tell(player, "The painted door waits for the garden's reverse telling.");
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        int marker = block == DomainBlocks.YEHIA_TREE_MARK ? YehiaStoryState.TREE
                : block == DomainBlocks.YEHIA_LANTERN_MARK ? YehiaStoryState.LANTERN
                : block == DomainBlocks.YEHIA_MUSHROOM_MARK ? YehiaStoryState.MUSHROOM : -1;
        if (marker < 0) return InteractionResult.PASS;
        int before = route.rememberTaleStage();
        if (before == 0) {
            tell(player, "Start the reverse tale at Yehia's lodge table first.");
        } else if (before == 5) {
            tell(player, "Both versions of the tale are already in the record.");
        } else if (before == 4) {
            tell(player, "The reverse trail is complete. Read the painted door in the Prop House.");
        } else {
            int after = route.markRememberTale(marker);
            if (after == 1 && before != 1) {
                tell(player, "That order changed the tale. Its reverse telling returns to the beginning.");
            } else if (after == before) {
                tell(player, "That part does not follow yet. The reverse telling still waits.");
            } else {
                tell(player, after == 4 ? "The reverse trail is complete. Visit the Prop House."
                        : (after - 1) + " of 3 moments retold.");
            }
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static InteractionResult trail(ServerLevel level, ServerPlayer player) {
        if (YehiaStoryState.get(level.getServer()).completed()) {
            FriendDomainUnlockState.get(level.getServer()).clear(DOMAIN);
        }
        if (!FriendDomainUnlockState.get(level.getServer()).cleared(DOMAIN)) {
            tell(player, "Yehia's garden tale is still unfinished. Explore the vale.");
            return InteractionResult.SUCCESS_SERVER;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(AdventureItems.STORY_THREAD)) {
                tell(player, "Use your Story Thread near a landmark to find an unopened stash.");
                return InteractionResult.SUCCESS_SERVER;
            }
        }
        if (InventorySpace.insertWhole(player, new ItemStack(AdventureItems.STORY_THREAD))) {
            tell(player, "Story Thread claimed. Use it near a landmark to find unopened loot.");
        } else {
            tell(player, "Make room in your inventory, then use the board again.");
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), false);
    }
}
