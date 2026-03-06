package com.feel.gems.adventure;

import com.feel.gems.sound.ModSounds;
import com.feel.gems.item.InventorySpace;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Two sightlines meet at a real cairn; the reward is a shared field waypoint. */
public final class AdlSurveyRuntime {
    private static final String DOMAIN = "adl";

    private AdlSurveyRuntime() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            Block block = world.getBlockState(hit.getBlockPos()).getBlock();
            if (block != DomainBlocks.ADL_EAST_SIGHT
                    && block != DomainBlocks.ADL_WEST_CHART
                    && block != DomainBlocks.ADL_MAP_TABLE
                    && block != DomainBlocks.ADL_CAIRN
                    && block != DomainBlocks.TOWNHALL_ATLAS_PIN) {
                return InteractionResult.PASS;
            }
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            if (block == DomainBlocks.TOWNHALL_ATLAS_PIN) return townhall(level, serverPlayer);
            if (block == DomainBlocks.ADL_MAP_TABLE) return map(level, serverPlayer);
            if (block == DomainBlocks.ADL_CAIRN) return cairn(level, serverPlayer);
            AdlSurveyState state = AdlSurveyState.get(level.getServer());
            if (block == DomainBlocks.ADL_EAST_SIGHT) {
                state.observe(AdlSurveyState.EAST);
                tell(serverPlayer, "High sightpost: the cut in the ridge frames the observatory and a second, lower line.");
            } else {
                state.observe(AdlSurveyState.WEST);
                tell(serverPlayer, "Chart shelter: a scratched bearing runs across the map house toward the ridge's cut.");
            }
            if (state.bothObserved() && !state.mapped()) {
                tell(serverPlayer, "Both bearings are known. Adl's map table has room for them.");
            }
            return InteractionResult.SUCCESS_SERVER;
        });
    }

    private static InteractionResult map(ServerLevel level, ServerPlayer player) {
        AdlSurveyState state = AdlSurveyState.get(level.getServer());
        if (!state.bothObserved()) {
            tell(player, "The map is missing bearings from the high sightpost and chart shelter. Friends can split the search.");
        } else {
            state.align();
            tell(player, "The two scratches cross beyond the map house. The survey is marked there in stone.");
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static InteractionResult cairn(ServerLevel level, ServerPlayer player) {
        if (!AdlSurveyState.get(level.getServer()).mapped()) {
            tell(player, "Two sightlines meet here. The map table still has an empty tracing.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (FriendDomainUnlockState.get(level.getServer()).clear(DOMAIN)) {
            level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                    "Adl's route is mapped. Everyone can claim an Atlas Pin at the townhall."), false);
            level.playSound(null, player.blockPosition(), ModSounds.ARCANE_CHIME,
                    SoundSource.BLOCKS, 0.65F, 1.1F);
        } else {
            tell(player, "The cairn is mapped. The Atlas Pin is open at the townhall.");
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static InteractionResult townhall(ServerLevel level, ServerPlayer player) {
        if (!FriendDomainUnlockState.get(level.getServer()).cleared(DOMAIN)) {
            tell(player, "Adl's survey is still unfinished. Explore the highlands.");
            return InteractionResult.SUCCESS_SERVER;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(AdventureItems.ATLAS_PIN)) {
                tell(player, "Your Atlas Pin is ready. Sneak-use it to move the shared marker; use it to find that marker.");
                return InteractionResult.SUCCESS_SERVER;
            }
        }
        if (InventorySpace.insertWhole(player, new ItemStack(AdventureItems.ATLAS_PIN))) {
            tell(player, "Atlas Pin claimed. Sneak-use it anywhere in the Overworld to mark a route for everyone.");
        } else {
            tell(player, "Make room in your inventory, then use the board again.");
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    public static InteractionResult usePin(ServerLevel level, ServerPlayer player) {
        if (level.dimension() != Level.OVERWORLD) {
            tell(player, "The Atlas Pin marks places in the Overworld.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!FriendDomainUnlockState.get(level.getServer()).cleared(DOMAIN)) {
            tell(player, "Map Adl's survey route before using the Atlas Pin.");
            return InteractionResult.SUCCESS_SERVER;
        }
        AdlSurveyState state = AdlSurveyState.get(level.getServer());
        BlockPos current = state.pin();
        if (player.isShiftKeyDown()) {
            BlockPos here = player.blockPosition();
            if (current != null && near(current, here)) {
                state.clearPin();
                level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                        player.getName().getString() + " cleared the shared Atlas Pin."), false);
            } else {
                state.setPin(here);
                level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                        player.getName().getString() + " moved the shared Atlas Pin."
                                + " Use yours to find it in the Overworld."), false);
                level.playSound(null, here, ModSounds.ARCANE_CHIME,
                        SoundSource.PLAYERS, 0.55F, 1.2F);
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        if (current == null) {
            tell(player, "No shared route is marked. Sneak-use your pin where friends should meet.");
            return InteractionResult.SUCCESS_SERVER;
        }
        long dx = (long) current.getX() - player.getBlockX();
        long dz = (long) current.getZ() - player.getBlockZ();
        int distance = (int) Math.round(Math.hypot(dx, dz) / 10.0) * 10;
        tell(player, "Shared Atlas Pin: " + RumorAtlasItem.direction(dx, dz)
                + ", about " + distance + " blocks away.");
        return InteractionResult.SUCCESS_SERVER;
    }

    private static boolean near(BlockPos a, BlockPos b) {
        return Math.abs((long) a.getX() - b.getX()) <= 4
                && Math.abs((long) a.getY() - b.getY()) <= 4
                && Math.abs((long) a.getZ() - b.getZ()) <= 4;
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), false);
    }
}
