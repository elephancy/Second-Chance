package com.feel.gems.adventure;

import com.feel.gems.adventure.CampaignRouteState.Path;
import com.feel.gems.sound.ModSounds;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/** Rebuild three visible fittings after Soryal's original yard puzzle. */
public final class RepairRouteRuntime {
    private record Fitting(int site, Item material, Block damaged, Block restored,
                           String materialName, String name) {
    }

    private RepairRouteRuntime() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            BlockPos pos = hit.getBlockPos();
            Fitting fitting = fitting(world.getBlockState(pos).getBlock());
            if (fitting == null) return InteractionResult.PASS;
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            return use(level, serverPlayer, hand, pos, fitting);
        });
    }

    private static Fitting fitting(Block block) {
        if (block == DomainBlocks.SORYAL_YARD_FITTING || block == DomainBlocks.SORYAL_YARD_RESTORED) {
            return new Fitting(RepairRouteState.YARD, DomainBlocks.PATCHWORK_BRICK.asItem(),
                    DomainBlocks.SORYAL_YARD_FITTING, DomainBlocks.SORYAL_YARD_RESTORED,
                    "patchwork brick", "yard brickwork");
        }
        if (block == DomainBlocks.SORYAL_BRIDGE_FITTING || block == DomainBlocks.SORYAL_BRIDGE_RESTORED) {
            return new Fitting(RepairRouteState.BRIDGE, Items.COPPER_INGOT,
                    DomainBlocks.SORYAL_BRIDGE_FITTING, DomainBlocks.SORYAL_BRIDGE_RESTORED,
                    "copper ingot", "scrap bridge joint");
        }
        if (block == DomainBlocks.SORYAL_LOFT_FITTING || block == DomainBlocks.SORYAL_LOFT_RESTORED) {
            return new Fitting(RepairRouteState.LOFT, Items.GLASS_PANE,
                    DomainBlocks.SORYAL_LOFT_FITTING, DomainBlocks.SORYAL_LOFT_RESTORED,
                    "glass pane", "loft window");
        }
        return null;
    }

    private static InteractionResult use(ServerLevel level, ServerPlayer player,
                                          InteractionHand hand, BlockPos pos, Fitting fitting) {
        if (CampaignRouteState.get(level.getServer()).chosen() != Path.REPAIR) {
            tell(player, "This fitting belongs to the Repair route. The Story Council shows this world's choice.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!SoryalRepairState.get(level.getServer()).solved()) {
            tell(player, "Read both fragments and mend the three floor marks first.");
            return InteractionResult.SUCCESS_SERVER;
        }
        RepairRouteState state = RepairRouteState.get(level.getServer());
        Block current = level.getBlockState(pos).getBlock();
        // A chunk and the shared save may survive a crash in either order.
        if (current == fitting.restored) {
            boolean recovered = state.recordRepair(fitting.site);
            if (recovered && state.soryalComplete()) announceComplete(level);
            tell(player, "The " + fitting.name + " is already mended.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (state.repaired(fitting.site)) {
            if (!level.setBlock(pos, fitting.restored.defaultBlockState(), Block.UPDATE_ALL)) {
                tell(player, "The fitting did not take. Try again.");
                return InteractionResult.SUCCESS_SERVER;
            }
            tell(player, "The " + fitting.name + " is already mended.");
            return InteractionResult.SUCCESS_SERVER;
        }
        ItemStack held = player.getItemInHand(hand);
        if (!player.getAbilities().instabuild && !held.is(fitting.material)) {
            tell(player, "Hold one " + fitting.materialName
                    + " and use it on the " + fitting.name + ".");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!level.setBlock(pos, fitting.restored.defaultBlockState(), Block.UPDATE_ALL)) {
            tell(player, "The fitting did not take. Try again.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
            player.getInventory().setChanged();
        }
        state.recordRepair(fitting.site);
        level.playSound(null, pos, ModSounds.ARCANE_CHIME, SoundSource.BLOCKS, 0.55F, 0.85F);
        if (state.soryalComplete()) {
            announceComplete(level);
        } else {
            tell(player, "The " + fitting.name + " is mended. " + state.repairedCount()
                    + "/3 fittings restored for the group.");
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static void announceComplete(ServerLevel level) {
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                "Soryal's yard, bridge and loft now connect. The Repair route continues at Riverward."), false);
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), false);
    }
}
