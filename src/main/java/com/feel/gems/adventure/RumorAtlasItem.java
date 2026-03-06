package com.feel.gems.adventure;

import com.feel.gems.adventure.AdventureSounds;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/** A reusable, survival-craftable clue to one authored expedition at a time. */
public final class RumorAtlasItem extends Item {
    private static final String TARGET_KEY = "rumor_atlas_target";
    private record Target(String id, String label, ResourceKey<Level> dimension) {
        Target(String id, String label) {
            this(id, label, Level.OVERWORLD);
        }
    }
    // Keep pages in a stable order within this release so selection remains predictable.
    private static final List<Target> EXTRA_TARGETS = List.of(
            new Target("second_chance_town", "The Square"),
            new Target("inkbound_workshop", "Inkbound Workshop"),
            new Target("khaled_floodgate", "Khaled's Riverward"),
            new Target("nour_signal_harbor", "Nour's Harbor"),
            new Target("fam_forum", "Fam's Forum"),
            new Target("adl_survey_house", "Adl's Survey House"),
            new Target("othman_library", "Othman's Library"),
            new Target("yehia_story_lodge", "Yehia's Story Lodge"),
            new Target("omar_lost_quarter", "Omar's Lost Quarter"),
            new Target("soryal_repair_yard", "Soryal's Repair Yard"),
            new Target("ali_splitstone_cloister", "Ali's Splitstone Cloister"),
            new Target("lost_trail", "Lost Trail"),
            new Target("star_cipher", "Star Cipher"),
            new Target("skywatch_lookout", "Skywatch Lookout"),
            new Target("ninefold_maze", "Ninefold Maze"),
            new Target("lastlight_ash_annex", "Ash Annex", Level.NETHER),
            new Target("redline_ember_bridge", "Ember Bridge", Level.NETHER),
            new Target("unwritten_end_source", "End Source", Level.END));
    private static final int DEFAULT_SEARCH_RINGS = 128;
    private static final int NEARBY_CANONICAL_RADIUS = 256;
    private static final int CACHE_REUSE_RADIUS = 256;
    private static final long CACHE_TICKS = 1_200;
    private static final Map<SearchKey, SearchResult> SEARCH_CACHE = new LinkedHashMap<>(128, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<SearchKey, SearchResult> eldest) {
            return size() > 128;
        }
    };

    private record SearchKey(UUID player, String target) { }

    private record SearchResult(ResourceKey<Level> dimension, BlockPos origin, BlockPos found,
                                long tick, boolean nearbyCanonical) {
        boolean reusable(ServerLevel world, BlockPos current, long now) {
            long dx = (long) origin.getX() - current.getX();
            long dz = (long) origin.getZ() - current.getZ();
            return dimension.equals(world.dimension()) && now >= tick && now - tick < CACHE_TICKS
                    && dx * dx + dz * dz <= CACHE_REUSE_RADIUS * CACHE_REUSE_RADIUS
                    && (!nearbyCanonical || withinNearbyRadius(found, current));
        }
    }

    private static boolean withinNearbyRadius(BlockPos found, BlockPos origin) {
        long dx = (long) found.getX() - origin.getX();
        long dz = (long) found.getZ() - origin.getZ();
        return dx * dx + dz * dz <= NEARBY_CANONICAL_RADIUS * NEARBY_CANONICAL_RADIUS;
    }


    public RumorAtlasItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        if (!(world instanceof ServerLevel serverWorld)
                || !(user instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = player.getItemInHand(hand);
        LandmarkKey[] landmarks = LandmarkKey.values();
        int selected = selection(stack, landmarks.length + EXTRA_TARGETS.size());
        if (player.isShiftKeyDown()) {
            int next = (selected + 1) % (landmarks.length + EXTRA_TARGETS.size());
            CustomData.update(DataComponents.CUSTOM_DATA, stack,
                    data -> data.putInt(TARGET_KEY, next));
            player.sendSystemMessage(Component.literal("Atlas page: " + label(landmarks, next))
                    .withStyle(ChatFormatting.GOLD), true);
            serverWorld.playSound(null, player.blockPosition(), AdventureSounds.STORY_PAGE_TURN,
                    SoundSource.PLAYERS, 0.45F, 1.15F);
            return InteractionResult.SUCCESS;
        }
        Target target = selected < landmarks.length
                ? new Target(landmarks[selected].id(), landmarks[selected].label())
                : EXTRA_TARGETS.get(selected - landmarks.length);
        if (serverWorld.dimension() == Level.OVERWORLD) {
            DimensionReturnService.recordOverworldDeparture(player);
        }
        if (target.dimension() != Level.OVERWORLD
                && !RequiredChapters.active(serverWorld.getServer())) {
            player.sendSystemMessage(Component.literal("This trail has not opened in this world yet.")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.SUCCESS;
        }
        if (!serverWorld.dimension().equals(target.dimension())) {
            player.sendSystemMessage(Component.literal(travelHint(target))
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.SUCCESS;
        }
        String label = target.label();
        String targetId = target.id();
        BlockPos origin = player.blockPosition().immutable();
        SearchKey key = new SearchKey(player.getUUID(), targetId);
        SearchResult cached = SEARCH_CACHE.get(key);
        boolean reusable = cached != null && cached.reusable(serverWorld, origin,
                serverWorld.getServer().getTickCount());
        boolean accepted = RumorAtlasLocator.submit(serverWorld, player.getUUID(), origin, targetId,
                hasThemedStart(targetId) ? themedSearchRings(targetId) : 0,
                fallbackSearchRings(targetId), reusable,
                reusable ? cached.found() : null, result -> {
                    if (serverWorld.getServer().getPlayerList().getPlayer(player.getUUID()) != player) return;
                    if (result.error() != null) {
                        player.sendSystemMessage(Component.literal(result.error())
                                .withStyle(ChatFormatting.GRAY), false);
                        return;
                    }
                    BlockPos found = result.found();
                    // A missing or provisional site must be checked again. In
                    // particular, reusing an unverified Ember Bridge as a
                    // normal hit would make its warning disappear.
                    if (found != null && !result.provisional() && !result.reusedCache()) {
                        SEARCH_CACHE.put(key, new SearchResult(serverWorld.dimension(), origin, found,
                                serverWorld.getServer().getTickCount(), result.nearbyCanonical()));
                    }
                    if (player.level() != serverWorld) {
                        player.sendSystemMessage(Component.literal(
                                "The Atlas finished its " + dimensionName(target.dimension())
                                        + " search. Open it there for directions.")
                                .withStyle(ChatFormatting.GRAY), false);
                        return;
                    }
                    if (found == null) {
                        player.sendSystemMessage(Component.literal(noTrace(target))
                                .withStyle(ChatFormatting.GRAY), false);
                    } else {
                        BlockPos current = player.blockPosition();
                        long dx = (long) found.getX() - current.getX();
                        long dz = (long) found.getZ() - current.getZ();
                        int distance = (int) Math.round(Math.hypot(dx, dz) / 100.0) * 100;
                        String bearing = label + " lies " + direction(dx, dz)
                                + ", about " + distance + " blocks away.";
                        if (result.provisional()) {
                            bearing = "A possible Ember Bridge lies " + direction(dx, dz)
                                    + ", about " + distance + " blocks away. Its entrance has not been checked yet; open the Atlas again if it is sealed.";
                        }
                        player.sendSystemMessage(Component.literal(bearing)
                                .withStyle(result.provisional()
                                        ? ChatFormatting.GRAY : ChatFormatting.AQUA), false);
                    }
                });
        if (!accepted) {
            player.sendSystemMessage(Component.literal("The Atlas is busy. Try again in a moment.")
                    .withStyle(ChatFormatting.GRAY), false);
            return InteractionResult.SUCCESS;
        }
        player.sendSystemMessage(Component.literal("Looking for " + label + "...")
                .withStyle(ChatFormatting.GOLD), true);
        player.getCooldowns().addCooldown(stack, 100);
        return InteractionResult.SUCCESS;
    }

    private static boolean hasThemedStart(String targetId) {
        return switch (targetId) {
            case "rehearsal_works", "branch_vault", "redline_house",
                    "second_chance_town", "othman_library", "lost_trail",
                    "star_cipher", "skywatch_lookout", "ninefold_maze" -> true;
            default -> false;
        };
    }

    private static int themedSearchRings(String targetId) {
        // Minecraft counts random-spread placement rings here, not chunks.
        // The late boss starts use 12-chunk spacing. Wider Rehearsal
        // and Redline searches stop at 16 rings.
        return switch (targetId) {
            case "rehearsal_works", "redline_house", "othman_library",
                    "ninefold_maze" -> 16;
            default -> 8;
        };
    }

    private static int fallbackSearchRings(String targetId) {
        // Minecraft counts structure-set rings here, not chunks. This runs
        // only on use and reuses the result for a minute near the same origin.
        return switch (targetId) {
            case "lastlight_exchange", "branch_vault", "unwritten_court" -> 192;
            case "lastlight_ash_annex", "redline_ember_bridge",
                    "unwritten_end_source" -> 24;
            default -> DEFAULT_SEARCH_RINGS;
        };
    }

    public static void clearSearchCache() {
        SEARCH_CACHE.clear();
    }

    static int selection(ItemStack stack, int count) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA,
                CustomData.EMPTY);
        CompoundTag nbt = data.copyTag();
        return Math.floorMod(nbt.getIntOr(TARGET_KEY, 0), count);
    }


    private static String label(LandmarkKey[] landmarks, int index) {
        return index < landmarks.length ? landmarks[index].label()
                : EXTRA_TARGETS.get(index - landmarks.length).label();
    }

    private static String dimensionName(ResourceKey<Level> dimension) {
        if (dimension.equals(Level.NETHER)) return "Nether";
        if (dimension.equals(Level.END)) return "End";
        return "Overworld";
    }

    private static String travelHint(Target target) {
        if (target.dimension() == Level.NETHER) {
            return "Use a Nether portal, then open the " + target.label()
                    + " page in the Nether for directions.";
        }
        if (target.dimension() == Level.END) {
            return "Use an End gateway to reach the outer islands, then open the End Source page there for directions.";
        }
        return "Open the " + target.label() + " page in the Overworld for directions.";
    }

    private static String noTrace(Target target) {
        return switch (target.id()) {
            case "lastlight_ash_annex" -> "No Ash Annex found nearby. Search farther into unexplored Nether wastes and try this page again.";
            case "redline_ember_bridge" -> "No Ember Bridge found nearby. Search farther into unexplored Nether warped forests and try this page again.";
            case "unwritten_end_source" -> "No End Source found nearby. Cross another End gateway to unexplored outer islands and try this page again.";
            default -> "No trace of " + target.label() + " appears on this page. Try again farther away.";
        };
    }

    static String direction(long dx, long dz) {
        String northSouth = dz < -100 ? "north" : dz > 100 ? "south" : "";
        String eastWest = dx < -100 ? "west" : dx > 100 ? "east" : "";
        if (northSouth.isEmpty() && eastWest.isEmpty()) {
            return "nearby";
        }
        if (northSouth.isEmpty()) {
            return eastWest;
        }
        if (eastWest.isEmpty()) {
            return northSouth;
        }
        return northSouth + eastWest;
    }
}
