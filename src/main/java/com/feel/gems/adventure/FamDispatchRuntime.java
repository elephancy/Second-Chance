package com.feel.gems.adventure;

import com.feel.gems.adventure.CampaignRouteState.Path;
import com.feel.gems.sound.ModSounds;
import com.feel.gems.item.InventorySpace;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;

/** A physical message that can be handed between players on Fam's Release route. */
public final class FamDispatchRuntime {
    private static final String SERIAL = "second_chance_forum_dispatch";

    private FamDispatchRuntime() { }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            Block block = world.getBlockState(hit.getBlockPos()).getBlock();
            if (block != DomainBlocks.FAM_DISPATCH_STORY
                    && block != DomainBlocks.FAM_DISPATCH_SCHOOL) return InteractionResult.PASS;
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            var server = level.getServer();
            ReleaseRouteState state = ReleaseRouteState.get(server);
            if (CampaignRouteState.get(server).chosen() != Path.RELEASE
                    || !FriendDomainUnlockState.get(server).cleared("fam")
                    || !state.aliShortcutComplete()) {
                tell(serverPlayer, "First open Nour's relay, Ali's shortcut and Fam's shared guide.");
                return InteractionResult.SUCCESS_SERVER;
            }
            if (state.famDispatchComplete()) {
                tell(serverPlayer, "Fam's Note reached both rooms and came back.");
                return InteractionResult.SUCCESS_SERVER;
            }
            long now = level.getGameTime();
            if (!state.famDispatchActive(now)) {
                tell(serverPlayer, "The five minutes are up. Get a new note from Fam's board.");
                return InteractionResult.SUCCESS_SERVER;
            }
            ItemStack dispatch = heldDispatch(serverPlayer, state.famDispatchSerial());
            if (dispatch.isEmpty()) {
                tell(serverPlayer, "Hold Fam's Note. Another player can hand it to you.");
                return InteractionResult.SUCCESS_SERVER;
            }
            int stop = block == DomainBlocks.FAM_DISPATCH_STORY
                    ? ReleaseRouteState.FAM_STORY : ReleaseRouteState.FAM_SCHOOL;
            if (!state.stampFamDispatch(serial(dispatch), stop, now)) {
                tell(serverPlayer, "This room has already heard the note. Another voice is still missing.");
                return InteractionResult.SUCCESS_SERVER;
            }
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    serverPlayer.getName().getString() + " brought Fam's Note to the "
                            + (stop == ReleaseRouteState.FAM_STORY ? "Story Hall" : "Open School")
                            + ". " + (state.famDispatchStops() == (ReleaseRouteState.FAM_STORY | ReleaseRouteState.FAM_SCHOOL)
                            ? "Fam's board can hear it again." : "The other room remains quiet.")), false);
            level.playSound(null, hit.getBlockPos(), ModSounds.ARCANE_CHIME,
                    SoundSource.BLOCKS, 0.6F, 1.3F);
            return InteractionResult.SUCCESS_SERVER;
        });
    }

    /** The original forum board remains the dispatch start and handoff point. */
    static InteractionResult forum(ServerLevel level, ServerPlayer player) {
        var server = level.getServer();
        ReleaseRouteState state = ReleaseRouteState.get(server);
        if (CampaignRouteState.get(server).chosen() != Path.RELEASE) {
            tell(player, "The forum's shared guide is complete. Use the Welcome Board at the townhall.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!state.aliShortcutComplete()) {
            tell(player, "Open Nour's harbor relay and Ali's high-to-low shortcut first.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (state.famDispatchComplete()) {
            tell(player, "Fam's Note reached both rooms. The forum is ready for the final chapter.");
            return InteractionResult.SUCCESS_SERVER;
        }
        long now = level.getGameTime();
        if (!state.famDispatchActive(now)) {
            int serial = state.startFamDispatch(now);
            ItemStack dispatch = new ItemStack(AdventureItems.FORUM_DISPATCH);
            CustomData.update(DataComponents.CUSTOM_DATA, dispatch, tag -> tag.putInt(SERIAL, serial));
            if (!InventorySpace.insertWhole(player, dispatch)) {
                player.drop(dispatch, false, net.minecraft.util.Prediction.SERVER_ONLY);
                tell(player, "Your inventory is full. Pick up Fam's Note beside you.");
            } else {
                tell(player, "This note carries Fam's two voices for five minutes. You can hand it to a friend.");
            }
            level.playSound(null, player.blockPosition(), ModSounds.ARCANE_CHIME,
                    SoundSource.BLOCKS, 0.7F, 1.0F);
            return InteractionResult.SUCCESS_SERVER;
        }
        ItemStack dispatch = heldDispatch(player, state.famDispatchSerial());
        if (!dispatch.isEmpty() && state.finishFamDispatch(serial(dispatch), now)) {
            dispatch.shrink(1);
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    player.getName().getString() + " brought Fam's Note back. The group has a new way forward."), false);
            level.playSound(null, player.blockPosition(), ModSounds.ARCANE_CHIME,
                    SoundSource.BLOCKS, 0.8F, 1.6F);
            return InteractionResult.SUCCESS_SERVER;
        }
        int stops = state.famDispatchStops();
        tell(player, dispatch.isEmpty()
                ? "The note is with another player or on the ground. If lost, get a new one after five minutes."
                : stops == 0 ? "The Story Hall and Open School have not heard this note."
                : stops == ReleaseRouteState.FAM_STORY ? "The Story Hall heard it. The Open School remains quiet."
                : stops == ReleaseRouteState.FAM_SCHOOL ? "The Open School heard it. The Story Hall remains quiet."
                : "Both rooms have heard it. The board looks ready.");
        return InteractionResult.SUCCESS_SERVER;
    }

    private static ItemStack heldDispatch(ServerPlayer player, int expectedSerial) {
        ItemStack main = player.getMainHandItem();
        if (serial(main) == expectedSerial) return main;
        ItemStack off = player.getOffhandItem();
        return serial(off) == expectedSerial ? off : ItemStack.EMPTY;
    }

    private static int serial(ItemStack stack) {
        return stack.is(AdventureItems.FORUM_DISPATCH)
                ? stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                        .copyTag().getIntOr(SERIAL, 0) : 0;
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), false);
    }
}
