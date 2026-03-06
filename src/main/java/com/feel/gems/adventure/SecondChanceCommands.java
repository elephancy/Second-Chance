package com.feel.gems.adventure;

import com.feel.gems.adventure.endsource.EndSourceState;
import com.feel.gems.adventure.boss.CampaignSignatures;
import com.feel.gems.adventure.nether.NetherChapterState;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Small, server-owned shortcuts for staging and inspecting campaign playtests. */
public final class SecondChanceCommands {
    private static final List<String> NETHER_SITES = List.of(
            "lastlight_ash_annex", "redline_ember_bridge");
    private static final List<String> END_SITES = List.of("unwritten_end_source");
    private static final List<Item> TRAVEL_KIT = List.of(
            AdventureItems.RUMOR_ATLAS, AdventureItems.ANGLE_LENS,
            AdventureItems.RETURN_STUB);
    private static final List<String> SITES = List.of(
            "second_chance_town", "framefall_gallery", "lastlight_exchange",
            "rehearsal_works", "branch_vault", "redline_house", "hushwire",
            "unwritten_court", "inkbound_workshop", "khaled_floodgate",
            "nour_signal_harbor", "fam_forum", "adl_survey_house",
            "othman_library", "yehia_story_lodge", "omar_lost_quarter",
            "soryal_repair_yard", "ali_splitstone_cloister", "lost_trail",
            "star_cipher", "skywatch_lookout", "ninefold_maze",
            "four_roads_estate", "lastlight_ash_annex", "redline_ember_bridge",
            "unwritten_end_source", "undercard", "branch_underarchive",
            "nour_sunken_relay");

    private SecondChanceCommands() { }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("secondchance")
                .executes(ctx -> playerHelp(ctx.getSource()))
                .then(Commands.literal("help")
                        .executes(ctx -> playerHelp(ctx.getSource())))
                .then(Commands.literal("admin")
                        .requires(SecondChanceCommands::isOperator)
                        .executes(ctx -> help(ctx.getSource()))
                        .then(Commands.literal("help")
                                .executes(ctx -> help(ctx.getSource())))
                        .then(Commands.literal("sites")
                                .executes(ctx -> sites(ctx.getSource()))
                                .then(Commands.argument("dimension", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestDimensions(builder))
                                        .executes(ctx -> sites(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "dimension")))))
                        .then(Commands.literal("bosses")
                                .executes(ctx -> bosses(ctx.getSource())))
                        .then(Commands.literal("progress")
                                .executes(ctx -> usage(ctx.getSource(), "/secondchance admin progress <player>"))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> progress(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("find")
                                .requires(SecondChanceCommands::isCreativeOperatorOrConsole)
                                .executes(ctx -> usage(ctx.getSource(), "/secondchance admin find <site>"))
                                .then(Commands.argument("site", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (String site : SITES) {
                                                if (site.startsWith(builder.getRemainingLowerCase())) {
                                                    builder.suggest(site);
                                                }
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> find(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "site")))))
                        .then(Commands.literal("kit")
                                .requires(SecondChanceCommands::isCreativeOperatorOrConsole)
                                .executes(ctx -> usage(ctx.getSource(), "/secondchance admin kit <player>"))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> kit(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("grantClear")
                                .requires(SecondChanceCommands::isCreativeOperatorOrConsole)
                                .executes(ctx -> usage(ctx.getSource(), "/secondchance admin grantClear <player> <boss>"))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> usage(ctx.getSource(), "/secondchance admin grantClear <player> <boss>"))
                                        .then(Commands.argument("boss", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    for (LandmarkKey landmark : LandmarkKey.values()) {
                                                        if (landmark.id().startsWith(builder.getRemainingLowerCase())) {
                                                            builder.suggest(landmark.id());
                                                        }
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> grantClear(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "boss"))))))));
    }

    private static boolean isOperator(CommandSourceStack source) {
        return source.permissions().hasPermission(
                new Permission.HasCommandLevel(PermissionLevel.byId(2)));
    }

    private static boolean isCreativeOperatorOrConsole(CommandSourceStack source) {
        return isOperator(source) && (source.getEntity() == null
                || source.getEntity() instanceof ServerPlayer player && player.isCreative());
    }

    private static int help(CommandSourceStack source) {
        tell(source, "Second Chance admin commands:");
        tell(source, "Explore: sites [overworld|nether|end], find <site>");
        tell(source, "Story: bosses, progress <player>, grantClear <player> <boss>");
        tell(source, "Supplies: kit <player> (gives missing travel tools)");
        tell(source, "Prefix each with /secondchance admin. Tab completes site and boss IDs.");
        tell(source, "Find, kit and grantClear require Creative mode for in-game operators. Kit and grantClear also work at the console.");
        return 1;
    }

    private static int playerHelp(CommandSourceStack source) {
        tell(source, "Second Chance: use the Rumor Atlas to find places to explore. Your guidebook tracks the journey and rewards.");
        if (isOperator(source)) {
            tell(source, "Admin tools: /secondchance admin help");
        }
        return 1;
    }

    private static int sites(CommandSourceStack source) {
        tell(source, "Build IDs for /secondchance admin find <site>:");
        tell(source, "Filter this list with /secondchance admin sites <overworld|nether|end>.");
        tellSites(source, "Overworld", sitesIn("overworld"));
        tellSites(source, "Nether", NETHER_SITES);
        tellSites(source, "End", END_SITES);
        tell(source, "Stand in the listed dimension. Find searches for a naturally generated build; it does not place one.");
        return 1;
    }

    private static int sites(CommandSourceStack source, String dimension) {
        String normalized = dimension.toLowerCase(Locale.ROOT);
        if (!List.of("overworld", "nether", "end").contains(normalized)) {
            source.sendFailure(Component.literal("Unknown dimension '" + dimension
                    + "'. Choose overworld, nether or end."));
            return 0;
        }
        tell(source, "Build IDs for /secondchance admin find <site>:");
        tellSites(source, switch (normalized) {
            case "nether" -> "Nether";
            case "end" -> "End";
            default -> "Overworld";
        }, sitesIn(normalized));
        tell(source, "Stand in this dimension before searching for a build.");
        return 1;
    }

    private static List<String> sitesIn(String dimension) {
        return switch (dimension) {
            case "nether" -> NETHER_SITES;
            case "end" -> END_SITES;
            default -> SITES.stream().filter(site -> !NETHER_SITES.contains(site)
                    && !END_SITES.contains(site)).toList();
        };
    }

    private static CompletableFuture<Suggestions> suggestDimensions(SuggestionsBuilder builder) {
        for (String dimension : List.of("overworld", "nether", "end")) {
            if (dimension.startsWith(builder.getRemainingLowerCase())) builder.suggest(dimension);
        }
        return builder.buildFuture();
    }

    private static void tellSites(CommandSourceStack source, String dimension, List<String> sites) {
        tell(source, dimension + ":");
        for (int i = 0; i < sites.size(); i += 4) {
            tell(source, "  " + String.join(", ", sites.subList(i, Math.min(i + 4, sites.size()))));
        }
    }

    private static int bosses(CommandSourceStack source) {
        tell(source, "Boss IDs for /secondchance admin grantClear <player> <boss>:");
        for (LandmarkKey landmark : LandmarkKey.values()) {
            tell(source, "  " + landmark.label() + ": " + landmark.id());
        }
        tell(source, "This awards a clear without testing the fight.");
        return 1;
    }

    private static int find(CommandSourceStack source, String site) {
        if (!SITES.contains(site)) {
            source.sendFailure(Component.literal("Unknown build '" + site
                    + "'. Browse IDs with /secondchance admin sites <overworld|nether|end>."));
            return 0;
        }
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Run find as a Creative operator in the site's dimension."));
            return 0;
        }
        ServerLevel level = player.level();
        var dimension = switch (site) {
            case "lastlight_ash_annex", "redline_ember_bridge" -> Level.NETHER;
            case "unwritten_end_source" -> Level.END;
            default -> Level.OVERWORLD;
        };
        if (level.dimension() != dimension) {
            source.sendFailure(Component.literal("Go to " + (dimension == Level.NETHER
                    ? "the Nether" : dimension == Level.END ? "the End" : "the Overworld")
                    + " before searching for " + site.replace('_', ' ') + "."));
            return 0;
        }
        String tagId = switch (site) {
            case "four_roads_estate" -> "four_roads_estate_v1";
            case "branch_underarchive" -> "branch_underarchive_v1";
            case "nour_sunken_relay" -> "nour_sunken_relay_v1";
            default -> site;
        };
        var tag = TagKey.create(Registries.STRUCTURE,
                Identifier.fromNamespaceAndPath("gems", "atlas_target/" + tagId));
        if (!level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getTagOrEmpty(tag).iterator().hasNext()) {
            source.sendFailure(Component.literal("No natural build is registered for "
                    + site.replace('_', ' ') + " in this world."));
            return 0;
        }
        BlockPos origin = player.blockPosition().immutable();
        boolean accepted = RumorAtlasLocator.submit(level, player.getUUID(), origin,
                tagId, 0, 128, false, null, result -> {
                    if (level.getServer().getPlayerList().getPlayer(player.getUUID()) != player) return;
                    if (result.error() != null) {
                        player.sendSystemMessage(Component.literal(result.error()), false);
                    } else if (result.found() == null) {
                        player.sendSystemMessage(Component.literal("No " + site.replace('_', ' ')
                                + " found in this search area. Move farther and try again."), false);
                    } else {
                        BlockPos pos = result.found();
                        player.sendSystemMessage(Component.literal((result.provisional() ? "Possible " : "Nearest ")
                                + site.replace('_', ' ')
                                + " start: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                                + (result.provisional() ? ". Check that this is the open entrance."
                                : ". Inspect the landing before teleporting.")), false);
                    }
                });
        if (!accepted) {
            source.sendFailure(Component.literal("The site search is busy. Try again shortly."));
            return 0;
        }
        tell(source, "Searching for " + site.replace('_', ' ') + " nearby...");
        return 1;
    }

    private static int progress(CommandSourceStack source, ServerPlayer player) {
        var server = source.getServer();
        var model = CampaignPersistentState.get(server).model();
        var saved = model.progress(player.getUUID());
        tell(source, "Story progress for " + player.getName().getString() + ":");
        for (LandmarkKey landmark : LandmarkKey.values()) {
            boolean cleared = (saved.cleared() & landmark.bit()) != 0;
            boolean visited = (saved.visited() & landmark.bit()) != 0;
            tell(source, "  " + landmark.label() + ": "
                    + (cleared ? "cleared" : visited ? "visited" : "not found")
                    + (model.worldChanged(landmark) ? " (world reward open)" : ""));
        }
        var nether = NetherChapterState.get(server);
        tell(source, "Nether: Ash Annex " + (nether.annexCleared() ? "cleared" : "not cleared")
                + "; Ember Bridge " + (nether.bridgeCleared() ? "cleared" : "not cleared"));
        var route = CampaignRouteState.get(server).chosen();
        tell(source, "Shared story choice: " + (route == null ? "not chosen" : route.label())
                + "; End Source " + (EndSourceState.get(server).clearedPath() == null
                ? "not cleared" : "cleared"));
        return 1;
    }

    private static int kit(CommandSourceStack source, ServerPlayer player) {
        List<Item> missing = new ArrayList<>();
        for (Item item : TRAVEL_KIT) {
            if (!hasItem(player, item)) missing.add(item);
        }
        int free = 0;
        var slots = player.getInventory().getNonEquipmentItems();
        for (ItemStack stack : slots) if (stack.isEmpty()) free++;
        if (free < missing.size()) {
            source.sendFailure(Component.literal("Make " + (missing.size() - free)
                    + " empty inventory slots before giving the travel kit."));
            return 0;
        }
        for (Item item : missing) {
            for (int slot = 0; slot < slots.size(); slot++) {
                if (slots.get(slot).isEmpty()) {
                    player.getInventory().setItem(slot, new ItemStack(item));
                    break;
                }
            }
        }
        tell(source, missing.isEmpty() ? player.getName().getString() + " already has the travel kit."
                : "Gave " + player.getName().getString() + " " + missing.size()
                + " missing travel tools (Atlas, Angle Lens, Return Stub).");
        return 1;
    }

    private static boolean hasItem(ServerPlayer player, Item item) {
        if (player.getOffhandItem().is(item)) return true;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(item)) return true;
        }
        return false;
    }

    private static int grantClear(CommandSourceStack source, ServerPlayer player, String bossId) {
        LandmarkKey chosen = null;
        for (LandmarkKey landmark : LandmarkKey.values()) {
            if (landmark.id().equals(bossId)) {
                chosen = landmark;
                break;
            }
        }
        if (chosen == null) {
            source.sendFailure(Component.literal("Unknown boss '" + bossId
                    + "'. Browse IDs with /secondchance admin bosses."));
            return 0;
        }
        if (CampaignPersistentState.get(source.getServer()).model()
                .hasCleared(player.getUUID(), chosen)) {
            source.sendFailure(Component.literal(player.getName().getString()
                    + " already cleared " + chosen.label() + "."));
            return 0;
        }
        CampaignRewards.recordVictoryForIds(source.getServer(), List.of(player.getUUID()), chosen);
        CampaignSignatures.deliver(player);
        tell(source, "Granted " + chosen.label() + " to " + player.getName().getString()
                + ". This skips the fight and does not count as playtest evidence.");
        return 1;
    }

    private static void tell(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }

    private static int usage(CommandSourceStack source, String command) {
        source.sendFailure(Component.literal("Use " + command));
        return 0;
    }
}
