package com.feel.gems.adventure;

import com.feel.gems.adventure.boss.StageOpeningState;
import com.feel.gems.adventure.nether.NetherChapterState;
import com.feel.gems.item.InventorySpace;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Public boss rewards opened by world victories. */
public final class BossTownhallServices {
    private static final Map<UUID, Integer> ANGLE_SELECTION = new HashMap<>();

    private BossTownhallServices() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            Block block = world.getBlockState(hit.getBlockPos()).getBlock();
            if (block != DomainBlocks.TOWNHALL_ANGLE_DESK
                    && block != DomainBlocks.TOWNHALL_RETURN_BOARD
                    && block != DomainBlocks.TOWNHALL_RESCUE_DISPATCH
                    && block != DomainBlocks.TOWNHALL_STAGE_DESK) return InteractionResult.PASS;
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            if (block == DomainBlocks.TOWNHALL_ANGLE_DESK) return angle(level, serverPlayer);
            if (block == DomainBlocks.TOWNHALL_RETURN_BOARD) return returning(level, serverPlayer);
            if (block == DomainBlocks.TOWNHALL_RESCUE_DISPATCH) return dispatch(level, serverPlayer);
            return stage(level, serverPlayer);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                ANGLE_SELECTION.remove(handler.getPlayer().getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ANGLE_SELECTION.clear());
    }

    private static InteractionResult angle(ServerLevel level, ServerPlayer player) {
        if (level.dimension() != Level.OVERWORLD) {
            tell(player, "The Angle Desk belongs in the Overworld townhall.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!CampaignPersistentState.get(level.getServer()).model().worldChanged(LandmarkKey.FRAMEFALL)) {
            tell(player, "Clear Framefall to open its Angle Desk.");
            return InteractionResult.SUCCESS_SERVER;
        }
        LandmarkKey[] targets = LandmarkKey.values();
        int selected = ANGLE_SELECTION.getOrDefault(player.getUUID(), 0);
        if (player.isShiftKeyDown()) {
            selected = (selected + 1) % targets.length;
            ANGLE_SELECTION.put(player.getUUID(), selected);
            tell(player, "Angle Desk: " + targets[selected].label() + ". Use to set your lens.");
            return InteractionResult.SUCCESS_SERVER;
        }
        LandmarkKey target = targets[selected];
        ItemStack lens = find(player, AdventureItems.ANGLE_LENS);
        if (lens == null) {
            lens = new ItemStack(AdventureItems.ANGLE_LENS);
            AngleLensItem.setTarget(lens, target);
            if (!InventorySpace.insertWhole(player, lens)) {
                tell(player, "Make room in your inventory, then use the desk again.");
                return InteractionResult.SUCCESS_SERVER;
            }
            tell(player, "Angle Lens ready for " + target.label()
                    + ". Sneak-use near the site to mark a group approach.");
            return InteractionResult.SUCCESS_SERVER;
        }
        AngleLensItem.setTarget(lens, target);
        BlockPos point = AngleApproachState.get(level.getServer()).approach(target);
        if (point == null) {
            tell(player, "Lens: " + target.label() + ". No group approach yet. Mark one on site.");
        } else {
            long dx = (long) point.getX() - player.getBlockX();
            long dz = (long) point.getZ() - player.getBlockZ();
            int distance = (int) Math.round(Math.hypot(dx, dz) / 10.0) * 10;
            tell(player, target.label() + " approach: "
                    + RumorAtlasItem.direction(dx, dz) + ", about " + distance + " blocks.");
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static InteractionResult returning(ServerLevel level, ServerPlayer player) {
        if (level.dimension() != Level.OVERWORLD) {
            tell(player, "The Return Board belongs in the Overworld townhall.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!CampaignPersistentState.get(level.getServer()).model().worldChanged(LandmarkKey.LASTLIGHT)) {
            tell(player, "Clear Lastlight to open its Return Board.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (find(player, AdventureItems.RETURN_STUB) == null) {
            if (!InventorySpace.insertWhole(player, new ItemStack(AdventureItems.RETURN_STUB))) {
                tell(player, "Make room in your inventory, then use the board again.");
                return InteractionResult.SUCCESS_SERVER;
            }
            boolean annex = NetherChapterState.get(level.getServer()).annexCleared();
            tell(player, annex
                    ? "Return Stub ready. The restored Ash Annex gives the group 120 seconds and 256 blocks."
                    : RequiredChapters.active(level.getServer())
                    ? "Return Stub ready: sneak-use to set a 60-second point. For an upgrade, follow the Ash Annex Atlas page in the Nether wastes."
                    : "Return Stub ready. Sneak-use to set a 60-second group point; use within 128 blocks to return.");
            return InteractionResult.SUCCESS_SERVER;
        }
        LastlightReturnState state = LastlightReturnState.get(level.getServer());
        long now = level.getGameTime();
        BlockPos point = state.activePoint(now);
        if (point == null) {
            tell(player, "No return point. Sneak-use your stub on safe ground to set one.");
        } else {
            long dx = (long) point.getX() - player.getBlockX();
            long dz = (long) point.getZ() - player.getBlockZ();
            tell(player, "Return: " + RumorAtlasItem.direction(dx, dz) + ", about "
                    + (int) Math.round(Math.hypot(dx, dz) / 10.0) * 10 + " blocks; "
                    + ((state.remaining(now) + 19) / 20) + " seconds left.");
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static InteractionResult dispatch(ServerLevel level, ServerPlayer player) {
        if (level.dimension() != Level.OVERWORLD || player.isSpectator()) {
            tell(player, "Use the Meeting Point in the Overworld townhall.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!CampaignPersistentState.get(level.getServer()).model().worldChanged(LandmarkKey.REDLINE)) {
            tell(player, "Clear Redline to open the Meeting Point.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (find(player, AdventureItems.DISPATCH_BELL) == null) {
            if (!InventorySpace.insertWhole(player, new ItemStack(AdventureItems.DISPATCH_BELL))) {
                tell(player, "Make room in your inventory, then use this desk again.");
                return InteractionResult.SUCCESS_SERVER;
            }
            boolean bridge = NetherChapterState.get(level.getServer()).bridgeCleared();
            tell(player, bridge
                    ? "Meeting Bell ready. Ember Bridge keeps the meeting point open for five minutes and calls farther."
                    : RequiredChapters.active(level.getServer())
                    ? "Meeting Bell ready: sneak-use to mark a spot for three minutes. To improve it, find Ember Bridge in a Nether warped forest."
                    : "Meeting Bell ready. Sneak-use on clear ground to mark a spot for three minutes.");
            return InteractionResult.SUCCESS_SERVER;
        }
        RescueDispatchState state = RescueDispatchState.get(level.getServer());
        BlockPos point = state.activePoint(level.getGameTime());
        if (point == null) {
            tell(player, "No rescue rally. Sneak-use your bell on clear ground to call one.");
        } else {
            long dx = (long) point.getX() - player.getBlockX();
            long dz = (long) point.getZ() - player.getBlockZ();
            tell(player, "Rally: " + RumorAtlasItem.direction(dx, dz) + ", about "
                    + (int) Math.round(Math.hypot(dx, dz) / 10.0) * 10 + " blocks; "
                    + ((state.remaining(level.getGameTime()) + 19) / 20) + " seconds left.");
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static InteractionResult stage(ServerLevel level, ServerPlayer player) {
        if (level.dimension() != Level.OVERWORLD || player.isSpectator()) {
            tell(player, "Use the Stage Desk in the Overworld townhall.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!CampaignPersistentState.get(level.getServer()).model().worldChanged(LandmarkKey.UNWRITTEN)) {
            tell(player, "Clear Unwritten to open its Stage Desk.");
            return InteractionResult.SUCCESS_SERVER;
        }
        StageOpeningState state = StageOpeningState.get(level.getServer());
        StageOpeningState.Selection selection = player.isShiftKeyDown()
                ? state.cycle() : state.pending();
        if (selection == null) {
            tell(player, "No opening queued. Sneak-use to choose one.");
        } else {
            tell(player, "Next rematch: " + selection.label() + ". Sneak-use to change it.");
            player.sendSystemMessage(Component.literal(selection.hint()), false);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static ItemStack find(ServerPlayer player, Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) return stack;
        }
        return null;
    }

    private static void tell(ServerPlayer player, String message) {
        // Chat wraps to the player's screen width and retains the instruction.
        // Action-bar notices cannot wrap and clipped the longer service text.
        player.sendSystemMessage(Component.literal(message), false);
    }
}
