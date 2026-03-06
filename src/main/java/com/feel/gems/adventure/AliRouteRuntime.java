package com.feel.gems.adventure;

import com.feel.gems.sound.ModSounds;
import com.feel.gems.adventure.CampaignRouteState.Path;
import com.feel.gems.item.InventorySpace;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** Compare a high map with a worn gate rubbing, then claim portable shortcuts. */
public final class AliRouteRuntime {
    private static final String DOMAIN = "ali";

    private AliRouteRuntime() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            Block block = world.getBlockState(hit.getBlockPos()).getBlock();
            if (block != DomainBlocks.ALI_HIGH_MAP
                    && block != DomainBlocks.ALI_GATE_RUBBING
                    && block != DomainBlocks.ALI_MAP_TABLE
                    && block != DomainBlocks.ALI_NORTH_DOOR
                    && block != DomainBlocks.ALI_EAST_DOOR
                    && block != DomainBlocks.ALI_WEST_DOOR
                    && block != DomainBlocks.ALI_RELEASE_HIGH
                    && block != DomainBlocks.ALI_RELEASE_LOW
                    && block != DomainBlocks.TOWNHALL_SHORTCUT_BOARD) return InteractionResult.PASS;
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            if (block == DomainBlocks.TOWNHALL_SHORTCUT_BOARD) return townhall(level, serverPlayer);
            if (block == DomainBlocks.ALI_RELEASE_HIGH || block == DomainBlocks.ALI_RELEASE_LOW) {
                return releaseAnchor(level, serverPlayer, hit.getBlockPos(), block);
            }
            AliRouteState state = AliRouteState.get(level.getServer());
            if (block == DomainBlocks.ALI_HIGH_MAP) {
                state.read(AliRouteState.HIGH_MAP);
                tell(serverPlayer, "The high map draws north and east doors. The third arch is missing.");
            } else if (block == DomainBlocks.ALI_GATE_RUBBING) {
                state.read(AliRouteState.GATE_RUBBING);
                tell(serverPlayer, "The gate rubbing is worn smooth at one arch; its outline survives where the high map has blank stone.");
            } else if (block == DomainBlocks.ALI_MAP_TABLE) {
                tell(serverPlayer, "The table holds the high map beside an older gate rubbing. One traveled arch has no inked route.");
            } else {
                switch (state.choose(block == DomainBlocks.ALI_WEST_DOOR)) {
                    case WRONG -> tell(serverPlayer, "The arch closes. You can try another.");
                    case ALREADY_SOLVED -> tell(serverPlayer,
                            "The west route is known. Claim Shortcut Chalk at the townhall.");
                    case SOLVED -> {
                        FriendDomainUnlockState.get(level.getServer()).clear(DOMAIN);
                        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                                "Ali's west route is found. Everyone can claim Shortcut Chalk at the townhall."), false);
                        level.playSound(null, serverPlayer.blockPosition(), ModSounds.ARCANE_CHIME,
                                SoundSource.BLOCKS, 0.7F, 1.2F);
                    }
                }
            }
            return InteractionResult.SUCCESS_SERVER;
        });
    }

    private static InteractionResult townhall(ServerLevel level, ServerPlayer player) {
        if (AliRouteState.get(level.getServer()).solved()) {
            FriendDomainUnlockState.get(level.getServer()).clear(DOMAIN);
        }
        if (!FriendDomainUnlockState.get(level.getServer()).cleared(DOMAIN)) {
            tell(player, "Ali's route is still closed. Explore Splitstone Heights.");
            return InteractionResult.SUCCESS_SERVER;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(AdventureItems.SHORTCUT_CHALK)) {
                tell(player, "Sneak-use your chalk at two safe places nearby; use it beside either mark to travel.");
                return InteractionResult.SUCCESS_SERVER;
            }
        }
        if (InventorySpace.insertWhole(player, new ItemStack(AdventureItems.SHORTCUT_CHALK))) {
            tell(player, "Shortcut Chalk claimed. Sneak-use at two safe Overworld spots within 128 blocks.");
        } else {
            tell(player, "Make room in your inventory, then use the board again.");
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static InteractionResult releaseAnchor(ServerLevel level, ServerPlayer player,
                                                    BlockPos pos, Block block) {
        var server = level.getServer();
        ReleaseRouteState release = ReleaseRouteState.get(server);
        if (CampaignRouteState.get(server).chosen() != Path.RELEASE
                || !release.nourRelayComplete()
                || !FriendDomainUnlockState.get(server).cleared(DOMAIN)) {
            tell(player, "Finish Nour's relay and find Ali's missing door before linking these anchors.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (release.aliShortcutComplete()) {
            tell(player, "The high map and cut passage already share an open route.");
            return InteractionResult.SUCCESS_SERVER;
        }
        int site = block == DomainBlocks.ALI_RELEASE_HIGH
                ? ReleaseRouteState.HIGH_MAP : ReleaseRouteState.CUT_PASSAGE;
        if (release.recordAliAnchor(site, pos)) {
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    (site == ReleaseRouteState.HIGH_MAP ? "High map" : "Cut passage")
                            + " anchor recorded. " + (release.aliAnchorsReady()
                            ? "Both anchors answer the same chalk line."
                            : "The other side building still holds an anchor.")), false);
            level.playSound(null, pos, ModSounds.ARCANE_CHIME, SoundSource.BLOCKS, 0.55F, 1.25F);
        } else {
            tell(player, release.aliAnchorsReady()
                    ? "Both anchors are ready. The chalk line between them is open."
                    : "This anchor is already recorded, or the two sites are too far apart.");
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Called only after Shortcut Chalk has successfully teleported a player. */
    static void onShortcutTravel(ServerLevel level, ServerPlayer player,
                                 BlockPos from, BlockPos to) {
        var server = level.getServer();
        if (CampaignRouteState.get(server).chosen() != Path.RELEASE) return;
        ReleaseRouteState release = ReleaseRouteState.get(server);
        if (!release.matchesAliTrip(from, to)) return;
        BlockPos high = release.aliHighAnchor();
        BlockPos low = release.aliLowAnchor();
        if (high == null || low == null || !level.hasChunkAt(high) || !level.hasChunkAt(low)
                || !level.getBlockState(high).is(DomainBlocks.ALI_RELEASE_HIGH)
                || !level.getBlockState(low).is(DomainBlocks.ALI_RELEASE_LOW)) return;
        if (release.finishAliShortcut()) {
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    player.getName().getString() + " opened Ali's high-to-low shortcut for the group."
                            + " The Release route continues at Fam's Forum."), false);
        }
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), false);
    }
}
