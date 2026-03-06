package com.feel.gems.adventure;

import com.feel.gems.adventure.CampaignRouteState.Path;
import com.feel.gems.item.InventorySpace;
import com.feel.gems.sound.ModSounds;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;

/** One physical council chooses a shared route after the first two reveals. */
public final class CampaignRouteRuntime {
    private static final int CONFIRM_TICKS = 20 * 30;
    private static final double VOTE_RADIUS_SQUARED = 16 * 16;
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private record Pending(Path path, long expires, BlockPos board, ResourceKey<Level> dimension) {
        boolean matches(Path vote, long now, BlockPos position, ResourceKey<Level> world) {
            return path == vote && now <= expires && board.equals(position) && dimension.equals(world);
        }
    }

    private CampaignRouteRuntime() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            BlockPos position = hit.getBlockPos();
            Block council = world.getBlockState(position).getBlock();
            if (council != DomainBlocks.TOWNHALL_STORY_COUNCIL
                    && council != DomainBlocks.TOWNHALL_OPEN_ARCHIVE
                    && council != DomainBlocks.TOWNHALL_RELIEF_DEPOT
                    && council != DomainBlocks.TOWNHALL_CROSSROADS) {
                return InteractionResult.PASS;
            }
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            return use(level, serverPlayer, position);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            Path path = CampaignRouteState.get(server).chosen();
            if (path == null) return;
            if (RouteFinaleState.get(server).complete()) {
                tell(handler.getPlayer(), "This world chose " + path.label()
                        + ". The Court ending is complete. Sneak-use the townhall council to read its epilogue.");
                String trail = RequiredChapters.postgameLead(server, path);
                if (trail != null) tell(handler.getPlayer(), trail);
            } else {
                tell(handler.getPlayer(), "This world chose " + path.label() + ". "
                        + nextLead(server, path));
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                PENDING.remove(handler.getPlayer().getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
    }

    private static InteractionResult use(ServerLevel level, ServerPlayer player, BlockPos board) {
        if (level.dimension() != Level.OVERWORLD) {
            tell(player, "The Story Council meets at the Overworld townhall.");
            return InteractionResult.SUCCESS_SERVER;
        }
        MinecraftServer server = level.getServer();
        CampaignRouteState state = CampaignRouteState.get(server);
        if (state.chosen() != null) {
            if (RouteFinaleState.get(server).complete()) {
                return postgame(level, player, board, state.chosen());
            }
            tell(player, "This world chose " + state.chosen().label() + ". "
                    + nextLead(server, state.chosen()));
            return InteractionResult.SUCCESS_SERVER;
        }
        int reveals = Integer.bitCount(CampaignPersistentState.get(server).model()
                .snapshot().worldChangedMask() & LandmarkKey.ALL_MASK);
        if (reveals < 2) {
            tell(player, "Recover two boss identities before the group chooses how to answer the changed world.");
            return InteractionResult.SUCCESS_SERVER;
        }
        UUID id = player.getUUID();
        if (player.isShiftKeyDown()) {
            Path next = Path.next(state.voteOf(id));
            state.vote(id, next);
            PENDING.remove(id);
            tell(player, "Your vote: " + next.label() + ". " + meaning(next)
                    + " Use the council twice to confirm after everyone nearby has voted.");
            return InteractionResult.SUCCESS_SERVER;
        }
        Path vote = state.voteOf(id);
        if (vote == null) {
            tell(player, "Sneak-use the council to choose Remember, Repair or Release. The choice will shape this world.");
            return InteractionResult.SUCCESS_SERVER;
        }
        long now = server.getTickCount();
        Pending pending = PENDING.get(id);
        if (pending == null || !pending.matches(vote, now, board, level.dimension())) {
            PENDING.put(id, new Pending(vote, now + CONFIRM_TICKS, board, level.dimension()));
            tell(player, "Confirm " + vote.label() + " for this world? Use the council again within 30 seconds."
                    + " Everyone within 16 blocks must vote; a tie waits for another choice.");
            return InteractionResult.SUCCESS_SERVER;
        }
        PENDING.remove(id);
        List<UUID> present = level.players().stream()
                .filter(nearby -> !nearby.isSpectator()
                        && nearby.distanceToSqr(board.getX() + 0.5,
                                board.getY() + 0.5, board.getZ() + 0.5) <= VOTE_RADIUS_SQUARED)
                .map(ServerPlayer::getUUID).toList();
        switch (state.confirm(present, id)) {
            case NEED_VOTES -> tell(player, "Someone at the council has not voted yet. They can sneak-use it, or step away.");
            case NO_MAJORITY -> tell(player, "The council is tied. Change a vote, then confirm again.");
            case PROPOSER_MISMATCH -> tell(player, "Most nearby players chose another route. Change your vote to confirm it.");
            case ALREADY_CHOSEN -> tell(player, "This world has already chosen its story.");
            case CHOSEN -> {
                Path chosen = state.chosen();
                PENDING.clear();
                server.getPlayerList().broadcastSystemMessage(Component.literal(
                        "The Story Council chose " + chosen.label() + ". " + nextLead(server, chosen)), false);
                level.playSound(null, board, ModSounds.ARCANE_CHIME,
                        SoundSource.BLOCKS, 0.8F, 0.9F);
            }
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static InteractionResult postgame(ServerLevel level, ServerPlayer player,
                                               BlockPos board, Path path) {
        Block destination = switch (path) {
            case REMEMBER -> DomainBlocks.TOWNHALL_OPEN_ARCHIVE;
            case REPAIR -> DomainBlocks.TOWNHALL_RELIEF_DEPOT;
            case RELEASE -> DomainBlocks.TOWNHALL_CROSSROADS;
        };
        if (!level.getBlockState(board).is(destination)) {
            level.setBlock(board, destination.defaultBlockState(), Block.UPDATE_ALL);
        }
        if (player.isShiftKeyDown()) {
            String newTrail = RequiredChapters.postgameLead(level.getServer(), path);
            if (newTrail != null) tell(player, newTrail);
            RouteEndingNarrative.open(player, path,
                    OthmanArchiveState.get(level.getServer()).published());
            return InteractionResult.SUCCESS_SERVER;
        }
        RouteFinaleState ending = RouteFinaleState.get(level.getServer());
        long day = Math.max(0, Math.floorDiv(level.getOverworldClockTime(), 24_000L));
        int requiredSlots = path == Path.REMEMBER ? 1 : 2;
        if (ending.nextServiceDay(player.getUUID()) <= day
                && !InventorySpace.hasEmptySlots(player, requiredSlots)) {
            tell(player, "Make room for " + requiredSlots
                    + (requiredSlots == 1 ? " stack" : " stacks")
                    + ", then use this desk again for your kit.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!ending.claimService(player.getUUID(), day)) {
            tell(player, "This desk has a fresh kit each Minecraft day. The next one is ready on day "
                    + ending.nextServiceDay(player.getUUID()) + ". Sneak-use to read the ending.");
            return InteractionResult.SUCCESS_SERVER;
        }
        switch (path) {
            case REMEMBER -> {
                give(player, new ItemStack(AdventureItems.RUMOR_ATLAS));
                tell(player, "The open archive lends you an atlas of every expedition. Sneak-use it to choose a page.");
            }
            case REPAIR -> {
                give(player, new ItemStack(DomainBlocks.PATCHWORK_BRICK, 8));
                give(player, new ItemStack(Items.BREAD, 4));
                tell(player, "The relief depot packed patchwork brick and food for the next crossing.");
            }
            case RELEASE -> {
                give(player, new ItemStack(AdventureItems.SHORTCUT_CHALK));
                give(player, new ItemStack(AdventureItems.SIGNAL_BUOY));
                tell(player, "The crossroads has Shortcut Chalk and a Harbor Buoy ready for your next trip.");
            }
        }
        level.playSound(null, board, ModSounds.ARCANE_CHIME, SoundSource.BLOCKS, 0.5F, 1.15F);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        player.getInventory().add(stack);
        if (!stack.isEmpty()) player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
    }

    private static String meaning(Path path) {
        return switch (path) {
            case REMEMBER -> "Hear both sides of the story and make sure nobody is forgotten.";
            case REPAIR -> "Fix what broke and keep your friends safe.";
            case RELEASE -> "Open new paths so everyone can move forward.";
        };
    }

    static String nextLead(MinecraftServer server, Path path) {
        FriendDomainUnlockState domains = FriendDomainUnlockState.get(server);
        switch (path) {
            case REMEMBER -> {
                CampaignRouteState route = CampaignRouteState.get(server);
                if (!route.rememberInkboundBound()) {
                    if (!InkboundPersistentState.get(server).model().unlocked()) {
                        return "Hamed's print house still has unmade impressions. The paper garden keeps this world's Remember account.";
                    }
                    return route.rememberPrintCount() < 2
                            ? "Hamed's old press has more to say; the paper garden waits nearby."
                            : "The paper garden is ready for Hamed's new accounts.";
                }
                if (OthmanArchiveState.get(server).published() == 0) {
                    return "Othman's public catalog and private study disagree. The library is waiting for the town's account.";
                }
                if (!route.rememberArchiveCompared()) {
                    if (route.rememberWitnessCount() < 2) {
                        return "A Branch map and Lastlight timetable tell different parts of the Library's story.";
                    }
                    return "Othman's library table holds the two travel records together.";
                }
                if (!YehiaStoryState.get(server).completed()) {
                    return "Yehia's lodge and garden keep different versions of one journey.";
                }
                if (route.rememberTaleStage() < 5) {
                    return route.rememberTaleStage() == 0
                            ? "Yehia's lodge still holds a second telling of the garden's journey."
                            : route.rememberTaleStage() == 4
                            ? "The Prop House painted door has the tale's closing line."
                            : "Yehia's garden marks remember more than one telling.";
                }
            }
            case REPAIR -> {
                if (!domains.cleared("soryal")) return "Soryal's repair yard still carries an unfinished pattern.";
                RepairRouteState repair = RepairRouteState.get(server);
                if (!repair.soryalComplete()) {
                    return "Soryal's yard, bridge and loft still show different damage from the same break.";
                }
                if (!domains.cleared("khaled")) return "Khaled's floodway is still waiting for a safe crossing.";
                if (!repair.riverwardComplete()) {
                    return "Khaled's flood refuges and braces still need the Repair crew.";
                }
                if (!CampaignPersistentState.get(server).model().worldChanged(LandmarkKey.REDLINE)) {
                    return "Face the guardian of Redline House and secure its refuge route.";
                }
                if (!repair.redlineComplete()) {
                    return "Redline's infirmary and watchpost still need a protected aid route.";
                }
            }
            case RELEASE -> {
                if (!domains.cleared("nour")) return "Nour's harbor logs and lamps remember the same call.";
                if (!ReleaseRouteState.get(server).nourRelayComplete()) {
                    return "Nour's radio hall and side houses still hold the unfinished relay.";
                }
                if (!domains.cleared("ali")) return "Ali's high map omits a worn arch in Splitstone Cloister.";
                if (!ReleaseRouteState.get(server).aliShortcutComplete()) {
                    return "Ali's high map and cut passage still have a distance between them.";
                }
                if (!domains.cleared("fam")) return "Fam's Story Hall and Open School still remember different voices.";
                if (!ReleaseRouteState.get(server).famDispatchComplete()) {
                    return "Fam's shared note is waiting at the forum board. Friends can carry it together.";
                }
            }
        }
        if (Integer.bitCount(CampaignPersistentState.get(server).model().snapshot().worldChangedMask()
                & LandmarkKey.ALL_MASK) < LandmarkKey.values().length) {
            return "Your three leads are gathered. Recover the remaining boss identities.";
        }
        if (RouteFinaleState.get(server).complete()) {
            return "The final chapter is complete. Speak to your friends and visit Unwritten Court to see what changed.";
        }
        String dimensionLead = RequiredChapters.nextLead(server, path);
        return dimensionLead != null ? dimensionLead
                : "The leads are gathered. Begin the final chapter at Unwritten Court's center dais.";
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), false);
    }
}
