package com.feel.gems.adventure;

import com.feel.gems.adventure.AdventureSounds;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;

/** A choice about knowledge that changes the shared, optional townhall lead. */
public final class OthmanArchiveRuntime {
    private static final String DOMAIN = "othman";

    private OthmanArchiveRuntime() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            Block block = world.getBlockState(hit.getBlockPos()).getBlock();
            if (block != DomainBlocks.OTHMAN_PUBLIC_CATALOG
                    && block != DomainBlocks.OTHMAN_PRIVATE_NOTE
                    && block != DomainBlocks.OTHMAN_PUBLISH_DESK
                    && block != DomainBlocks.OTHMAN_KEEP_DESK
                    && block != DomainBlocks.TOWNHALL_KNOWLEDGE_SHELF) {
                return InteractionResult.PASS;
            }
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            if (block == DomainBlocks.TOWNHALL_KNOWLEDGE_SHELF) return shelf(level, serverPlayer);
            if (block == DomainBlocks.OTHMAN_PUBLISH_DESK) {
                return choose(level, serverPlayer, OthmanArchiveState.CATALOG);
            }
            if (block == DomainBlocks.OTHMAN_KEEP_DESK) {
                return choose(level, serverPlayer, OthmanArchiveState.STUDY);
            }
            OthmanArchiveState state = OthmanArchiveState.get(level.getServer());
            if (block == DomainBlocks.OTHMAN_PUBLIC_CATALOG) {
                state.record(OthmanArchiveState.CATALOG);
                tell(serverPlayer, "Catalog lead: Branch Vault's map room has a route above its ground floor.");
            } else {
                state.record(OthmanArchiveState.STUDY);
                tell(serverPlayer, "Study note: Lastlight's signal box has an upper control room.");
            }
            level.playSound(null, hit.getBlockPos(), AdventureSounds.STORY_PAGE_TURN,
                    SoundSource.BLOCKS, 0.5F, 1.0F);
            if (state.ready() && state.published() == 0) {
                tell(serverPlayer, "Both leads are known. Choose one to share at Othman's reading desks.");
            }
            return InteractionResult.SUCCESS_SERVER;
        });
    }

    private static InteractionResult choose(ServerLevel level, ServerPlayer player, int lead) {
        OthmanArchiveState state = OthmanArchiveState.get(level.getServer());
        if (!state.ready()) {
            tell(player, "Read the catalog rotunda and study wing first. Friends can split the search.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (state.publish(lead)) {
            FriendDomainUnlockState.get(level.getServer()).clear(DOMAIN);
            level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                    "Othman's library has shared a lead. The Knowledge Shelf is open at the townhall."), false);
            level.playSound(null, player.blockPosition(), AdventureSounds.STORY_RECORD_SEAL,
                    SoundSource.BLOCKS, 0.65F, 1.0F);
        } else {
            if (state.published() != 0) {
                FriendDomainUnlockState.get(level.getServer()).clear(DOMAIN);
            }
            tell(player, "A lead is already published. The other note remains here to read.");
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static InteractionResult shelf(ServerLevel level, ServerPlayer player) {
        int lead = OthmanArchiveState.get(level.getServer()).published();
        if (lead != 0) {
            FriendDomainUnlockState.get(level.getServer()).clear(DOMAIN);
        }
        if (!FriendDomainUnlockState.get(level.getServer()).cleared(DOMAIN)) {
            tell(player, "Read both library annexes, then choose a lead at Othman's reading desks.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (lead == OthmanArchiveState.CATALOG) {
            tell(player, "Published lead: Branch Vault's map room has a route above its ground floor.");
        } else if (lead == OthmanArchiveState.STUDY) {
            tell(player, "Published lead: Lastlight's signal box has an upper control room.");
        } else {
            tell(player, "The shelf is open, but its note is missing. Return to Othman's desks.");
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), false);
    }
}
