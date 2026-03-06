package com.feel.gems.adventure;

import com.feel.gems.sound.ModSounds;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;

/** Shared two-voice forum guide and a personal, progress-aware welcome hint. */
public final class FamForumRuntime {
    private static final String DOMAIN = "fam";

    private FamForumRuntime() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            Block block = world.getBlockState(hit.getBlockPos()).getBlock();
            if (block != DomainBlocks.FAM_STORY_CLUE
                    && block != DomainBlocks.FAM_SCHOOL_CLUE
                    && block != DomainBlocks.FAM_FORUM_BOARD
                    && block != DomainBlocks.TOWNHALL_WELCOME_BOARD) {
                return InteractionResult.PASS;
            }
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            if (block == DomainBlocks.TOWNHALL_WELCOME_BOARD) {
                return welcome(level, serverPlayer);
            }
            if (block == DomainBlocks.FAM_FORUM_BOARD) {
                return assemble(level, serverPlayer);
            }
            FamForumPersistentState state = FamForumPersistentState.get(level.getServer());
            if (block == DomainBlocks.FAM_STORY_CLUE) {
                state.record(FamForumPersistentState.STORY);
                tell(serverPlayer, "Story Hall: the atlas gives a direction; smaller rooms explain the place.");
            } else {
                state.record(FamForumPersistentState.SCHOOL);
                tell(serverPlayer, "Open School: watch the warning, move after it locks, and return later if needed.");
            }
            if (state.ready() && !FriendDomainUnlockState.get(level.getServer()).cleared(DOMAIN)) {
                tell(serverPlayer, "Both voices are heard. The board beside Fam has changed.");
            }
            return InteractionResult.SUCCESS_SERVER;
        });
    }

    private static InteractionResult assemble(ServerLevel level, ServerPlayer player) {
        if (FriendDomainUnlockState.get(level.getServer()).cleared(DOMAIN)) {
            return FamDispatchRuntime.forum(level, player);
        }
        if (!FamForumPersistentState.get(level.getServer()).ready()) {
            tell(player, "The Story Hall and Open School have not both been heard. One player can visit both.");
            return InteractionResult.SUCCESS_SERVER;
        }
        FriendDomainUnlockState.get(level.getServer()).clear(DOMAIN);
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                "Fam's forum has a shared guide. Everyone can use the Welcome Board at the townhall."), false);
        tell(player, "Follow an atlas lead, read the side rooms, watch the fight, and return when ready.");
        level.playSound(null, player.blockPosition(), ModSounds.ARCANE_CHIME,
                SoundSource.BLOCKS, 0.65F, 1.15F);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static InteractionResult welcome(ServerLevel level, ServerPlayer player) {
        if (!FriendDomainUnlockState.get(level.getServer()).cleared(DOMAIN)) {
            tell(player, "Fam's board is waiting for both sides of the forum's story.");
            return InteractionResult.SUCCESS_SERVER;
        }
        CampaignProgress.PlayerProgress progress = CampaignPersistentState.get(level.getServer())
                .model().progress(player.getUUID());
        tell(player, hint(progress));
        return InteractionResult.SUCCESS_SERVER;
    }

    static String hint(CampaignProgress.PlayerProgress progress) {
        int cleared = progress.cleared() & LandmarkKey.ALL_MASK;
        if (cleared == LandmarkKey.ALL_MASK) {
            return "You have finished all seven stories. Rematch a boss or help a friend find one they missed.";
        }
        for (LandmarkKey place : LandmarkKey.values()) {
            if ((progress.visited() & place.bit()) != 0 && (cleared & place.bit()) == 0) {
                return "You reached " + place.label()
                        + ". Search its side rooms for clues and supplies before the main fight.";
            }
        }
        if (cleared != 0) {
            return "You cleared " + Integer.bitCount(cleared)
                    + " of seven stories. Pick a new atlas page; townhall services can help the whole server.";
        }
        return "Make a Rumor Atlas with a compass and paper. Sneak-use to choose a place; use it for a direction.";
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), false);
    }
}
