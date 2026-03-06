package com.feel.gems.adventure;

import com.feel.gems.sound.ModSounds;
import com.feel.gems.item.InventorySpace;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.ItemStack;

/** Read two records, recover the missing object, then retrieve a useful lead. */
public final class OmarLostRuntime {
    private static final String DOMAIN = "omar";

    private OmarLostRuntime() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            Block block = world.getBlockState(hit.getBlockPos()).getBlock();
            if (block != DomainBlocks.OMAR_CLOCK_RECEIPT
                    && block != DomainBlocks.OMAR_BOOK_RECORD
                    && block != DomainBlocks.OMAR_COMPASS_CHOICE
                    && block != DomainBlocks.OMAR_CLOCK_CHOICE
                    && block != DomainBlocks.OMAR_BOOK_CHOICE
                    && block != DomainBlocks.TOWNHALL_LOST_FOUND) {
                return InteractionResult.PASS;
            }
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            if (block == DomainBlocks.TOWNHALL_LOST_FOUND) return townhall(level, serverPlayer);
            OmarLostState state = OmarLostState.get(level.getServer());
            if (block == DomainBlocks.OMAR_CLOCK_RECEIPT) {
                state.record(OmarLostState.RECEIPT);
                tell(serverPlayer, "Exchange receipt: the clock was collected before dusk.");
            } else if (block == DomainBlocks.OMAR_BOOK_RECORD) {
                state.record(OmarLostState.LEDGER);
                tell(serverPlayer, "Loft notebook: the book came back at dawn.");
            } else {
                int object = block == DomainBlocks.OMAR_COMPASS_CHOICE ? OmarLostState.COMPASS
                        : block == DomainBlocks.OMAR_CLOCK_CHOICE ? OmarLostState.CLOCK
                        : OmarLostState.BOOK;
                return choose(level, serverPlayer, state, object);
            }
            if (state.ready() && !state.solved()) {
                tell(serverPlayer, "You have both clues. Return to Omar's lane and choose what went missing.");
            }
            return InteractionResult.SUCCESS_SERVER;
        });
    }

    private static InteractionResult choose(ServerLevel level, ServerPlayer player,
                                            OmarLostState state, int object) {
        switch (state.choose(object)) {
            case WRONG -> tell(player, "That object has a return recorded elsewhere in the quarter. The other choices remain.");
            case ALREADY_SOLVED -> tell(player,
                    "The missing compass is found. The Lost-and-Found board is open at the townhall.");
            case SOLVED -> {
                FriendDomainUnlockState.get(level.getServer()).clear(DOMAIN);
                level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                        "Omar's lost compass is found. Everyone can use Lost-and-Found at the townhall."), false);
                level.playSound(null, player.blockPosition(), ModSounds.ARCANE_CHIME,
                        SoundSource.BLOCKS, 0.65F, 0.95F);
            }
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static InteractionResult townhall(ServerLevel level, ServerPlayer player) {
        if (OmarLostState.get(level.getServer()).solved()) {
            FriendDomainUnlockState.get(level.getServer()).clear(DOMAIN);
        }
        if (!FriendDomainUnlockState.get(level.getServer()).cleared(DOMAIN)) {
            tell(player, "Find the missing object in Omar's Lost Quarter to open this board.");
            return InteractionResult.SUCCESS_SERVER;
        }
        boolean ownsSlip = false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(AdventureItems.RECOVERY_SLIP)
                    && RecoverySlipItem.belongsTo(stack, player.getUUID())) {
                ownsSlip = true;
                break;
            }
        }
        if (!ownsSlip) {
            ItemStack slip = new ItemStack(AdventureItems.RECOVERY_SLIP);
            RecoverySlipItem.bind(slip, player.getUUID());
            slip.set(DataComponents.CUSTOM_NAME,
                    Component.literal(player.getName().getString() + "'s Recovery Slip"));
            if (!InventorySpace.insertWhole(player, slip)) {
                tell(player, "Make room to claim a Recovery Slip. It remembers your last fall and can be handed to a friend.");
                return InteractionResult.SUCCESS_SERVER;
            }
            tell(player, "Recovery Slip claimed. Use it to find your last fall, or hand it to a friend to search for you.");
        }
        CampaignProgress.PlayerProgress progress = CampaignPersistentState.get(level.getServer())
                .model().progress(player.getUUID());
        tell(player, clue(progress));
        return InteractionResult.SUCCESS_SERVER;
    }

    static String clue(CampaignProgress.PlayerProgress progress) {
        int uncompleted = progress.visited() & ~progress.cleared() & LandmarkKey.ALL_MASK;
        for (LandmarkKey place : LandmarkKey.values()) {
            if ((uncompleted & place.bit()) == 0) continue;
            return switch (place) {
                case FRAMEFALL -> "Lost lead: Framefall's observatory looks over the paths beside the gallery.";
                case LASTLIGHT -> "Try the upstairs control room over Lastlight's tracks.";
                case REHEARSAL -> "Lost lead: find the costume house beside Rehearsal's main stage.";
                case BRANCH -> "Lost lead: a linked map room sits beside Branch Vault.";
                case REDLINE -> "Lost lead: check the infirmary linked to Redline House.";
                case HUSHWIRE -> "Lost lead: the relay bunker has a quiet receiver beside its sentry.";
                case UNWRITTEN -> "Lost lead: the mask archive stands beside Unwritten Court.";
            };
        }
        if (progress.cleared() == LandmarkKey.ALL_MASK) {
            return "No unfinished expedition remains. Help a friend recover one of theirs.";
        }
        return "Visit a landmark, then return here if you missed one of its smaller rooms.";
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), false);
    }
}
