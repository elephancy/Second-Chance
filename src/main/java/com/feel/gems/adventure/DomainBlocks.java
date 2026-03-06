package com.feel.gems.adventure;

import com.feel.gems.GemsMod;
import net.minecraft.core.Registry;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.StateDefinition;

/** Placeable district building materials and the protected civic ledger. */
public final class DomainBlocks {
    public static final Block HUSHWIRE_SLATE = register("hushwire_slate");
    public static final Block HUSHWIRE_TRACERY = register("hushwire_tracery");
    public static final Block INKBOUND_SCREEN = register("inkbound_screen");
    public static final Block INKBOUND_INKSTONE = register("inkbound_inkstone");
    public static final Block DECO_FAIENCE = register("deco_faience");
    public static final Block DECO_SUNBURST = register("deco_sunburst");
    public static final Block DECO_MARQUEE = register("deco_marquee");
    public static final Block GALLERY_PLANE = register("gallery_plane");
    public static final Block GALLERY_PRISM = register("gallery_prism");
    public static final Block STATION_ASHLAR = register("station_ashlar");
    public static final Block STATION_DIAL = register("station_dial");
    public static final Block STAGE_TIMBER = register("stage_timber");
    public static final Block STAGE_CURTAIN = register("stage_curtain");
    public static final Block FOLIO_STONE = register("folio_stone");
    public static final Block BRANCH_INDEX = register("branch_index");
    public static final Block REFUGE_BRICK = register("refuge_brick");
    public static final Block SHELTER_EMBLEM = register("shelter_emblem");
    public static final Block COURT_PLASTER = register("court_plaster");
    public static final Block MASK_RELIEF = register("mask_relief");
    public static final Block RIVERWARD_LIMESTONE = register("riverward_limestone");
    public static final Block RIVERWARD_GAUGE = register("riverward_gauge");
    public static final Block HARBOR_CHALK = register("harbor_chalk");
    public static final Block HARBOR_WAVE_TILE = register("harbor_wave_tile");
    public static final Block FORUM_PLASTER = register("forum_plaster");
    public static final Block FORUM_MOSAIC = register("forum_mosaic");
    public static final Block SURVEY_STONE = register("survey_stone");
    public static final Block COMPASS_MOSAIC = register("compass_mosaic");
    public static final Block ARCHIVE_LIMESTONE = register("archive_limestone");
    public static final Block ARCHIVE_INLAY = register("archive_inlay");
    public static final Block STORYBOOK_TURF = register("storybook_turf");
    public static final Block STORYBOOK_PATH = register("storybook_path");
    public static final Block LOST_CLAY = register("lost_clay");
    public static final Block LOST_TILE = register("lost_tile");
    public static final Block PATCHWORK_BRICK = register("patchwork_brick");
    public static final Block PATCHWORK_MOSAIC = register("patchwork_mosaic");
    public static final Block SPLITSTONE = register("splitstone");
    public static final Block WAYMARK_TILE = register("waymark_tile");
    public static final Block ALI_BRIDGE_REQUEST = registerDirectionalProtected("ali_bridge_request");
    public static final Block ALI_BRIDGE_SEAL = registerProtected("ali_bridge_seal");
    public static final Block YEHIA_BRIDGE_REQUEST = registerDirectionalProtected("yehia_bridge_request");
    public static final Block YEHIA_BRIDGE_SEAL = registerProtected("yehia_bridge_seal");
    public static final Block ADL_EAST_SIGHT = registerProtected("adl_east_sight");
    public static final Block ADL_WEST_CHART = registerProtected("adl_west_chart");
    public static final Block ADL_MAP_TABLE = registerProtected("adl_map_table");
    public static final Block ADL_CAIRN = registerProtected("adl_cairn");
    public static final Block TOWNHALL_ATLAS_PIN = registerProtected("townhall_atlas_pin");
    public static final Block TOWNHALL_LEDGER = registerProtected("townhall_ledger");
    public static final Block INKBOUND_SPARK = registerProtected("inkbound_spark");
    public static final Block INKBOUND_SHELTER = registerProtected("inkbound_shelter");
    public static final Block INKBOUND_ROUTE = registerProtected("inkbound_route");
    public static final Block INKBOUND_PRESS = registerProtected("inkbound_press");
    public static final Block INKBOUND_BINDING = registerProtected("inkbound_binding");
    public static final Block BRANCH_WITNESS = registerProtected("branch_witness");
    public static final Block LASTLIGHT_WITNESS = registerProtected("lastlight_witness");
    public static final Block OTHMAN_COMPARISON = registerProtected("othman_comparison");
    public static final Block TOWNHALL_INKBOUND_DESK = registerProtected("townhall_inkbound_desk");
    public static final Block KHALED_ROUTE_START = registerProtected("khaled_route_start");
    public static final Block KHALED_WEST_REFUGE = registerProtected("khaled_west_refuge");
    public static final Block KHALED_EAST_REFUGE = registerProtected("khaled_east_refuge");
    public static final Block KHALED_ROUTE_FINISH = registerProtected("khaled_route_finish");
    public static final Block KHALED_WEST_BRACE = registerProtected("khaled_west_brace");
    public static final Block KHALED_EAST_BRACE = registerProtected("khaled_east_brace");
    public static final Block KHALED_WEST_BRACE_RESTORED = registerProtected("khaled_west_brace_restored");
    public static final Block KHALED_EAST_BRACE_RESTORED = registerProtected("khaled_east_brace_restored");
    public static final Block TOWNHALL_SHELTER_DESK = registerProtected("townhall_shelter_desk");
    public static final Block NOUR_BOAT_CLUE = registerProtected("nour_boat_clue");
    public static final Block NOUR_FOG_CLUE = registerProtected("nour_fog_clue");
    public static final Block NOUR_BLUE_LAMP = registerProtected("nour_blue_lamp");
    public static final Block NOUR_AMBER_LAMP = registerProtected("nour_amber_lamp");
    public static final Block NOUR_WHITE_LAMP = registerProtected("nour_white_lamp");
    public static final Block NOUR_RELAY_TOWER = registerProtected("nour_relay_tower");
    public static final Block NOUR_RELAY_BOAT = registerProtected("nour_relay_boat");
    public static final Block NOUR_RELAY_FOG = registerProtected("nour_relay_fog");
    public static final Block TOWNHALL_SIGNAL_TABLE = registerProtected("townhall_signal_table");
    public static final Block FAM_STORY_CLUE = registerProtected("fam_story_clue");
    public static final Block FAM_SCHOOL_CLUE = registerProtected("fam_school_clue");
    public static final Block FAM_FORUM_BOARD = registerProtected("fam_forum_board");
    public static final Block FAM_DISPATCH_STORY = registerProtected("fam_dispatch_story");
    public static final Block FAM_DISPATCH_SCHOOL = registerProtected("fam_dispatch_school");
    public static final Block FAM_CIRCLE_START = registerProtected("fam_circle_start");
    public static final Block FAM_CIRCLE_STORY = registerProtected("fam_circle_story");
    public static final Block FAM_CIRCLE_SCHOOL = registerProtected("fam_circle_school");
    public static final Block TOWNHALL_WELCOME_BOARD = registerProtected("townhall_welcome_board");
    public static final Block OTHMAN_PUBLIC_CATALOG = registerProtected("othman_public_catalog");
    public static final Block OTHMAN_PRIVATE_NOTE = registerProtected("othman_private_note");
    public static final Block OTHMAN_PUBLISH_DESK = registerProtected("othman_publish_desk");
    public static final Block OTHMAN_KEEP_DESK = registerProtected("othman_keep_desk");
    public static final Block TOWNHALL_KNOWLEDGE_SHELF = registerProtected("townhall_knowledge_shelf");
    public static final Block YEHIA_TRUTH_CLUE = registerProtected("yehia_truth_clue");
    public static final Block YEHIA_PROP_CLUE = registerProtected("yehia_prop_clue");
    public static final Block YEHIA_STORY_TABLE = registerProtected("yehia_story_table");
    public static final Block YEHIA_MUSHROOM_MARK = registerProtected("yehia_mushroom_mark");
    public static final Block YEHIA_LANTERN_MARK = registerProtected("yehia_lantern_mark");
    public static final Block YEHIA_TREE_MARK = registerProtected("yehia_tree_mark");
    public static final Block TOWNHALL_STORY_TRAIL = registerProtected("townhall_story_trail");
    public static final Block OMAR_CLOCK_RECEIPT = registerProtected("omar_clock_receipt");
    public static final Block OMAR_BOOK_RECORD = registerProtected("omar_book_record");
    public static final Block OMAR_COMPASS_CHOICE = registerProtected("omar_compass_choice");
    public static final Block OMAR_CLOCK_CHOICE = registerProtected("omar_clock_choice");
    public static final Block OMAR_BOOK_CHOICE = registerProtected("omar_book_choice");
    public static final Block TOWNHALL_LOST_FOUND = registerProtected("townhall_lost_found");
    public static final Block SORYAL_SCRAP_CLUE = registerProtected("soryal_scrap_clue");
    public static final Block SORYAL_LOFT_CLUE = registerProtected("soryal_loft_clue");
    public static final Block SORYAL_COPPER_MARK = registerProtected("soryal_copper_mark");
    public static final Block SORYAL_GLASS_MARK = registerProtected("soryal_glass_mark");
    public static final Block SORYAL_BRICK_MARK = registerProtected("soryal_brick_mark");
    public static final Block SORYAL_YARD_FITTING = registerProtected("soryal_yard_fitting");
    public static final Block SORYAL_BRIDGE_FITTING = registerProtected("soryal_bridge_fitting");
    public static final Block SORYAL_LOFT_FITTING = registerProtected("soryal_loft_fitting");
    public static final Block SORYAL_YARD_RESTORED = registerProtected("soryal_yard_restored");
    public static final Block SORYAL_BRIDGE_RESTORED = registerProtected("soryal_bridge_restored");
    public static final Block SORYAL_LOFT_RESTORED = registerProtected("soryal_loft_restored");
    public static final Block REDLINE_AID_START = registerProtected("redline_aid_start");
    public static final Block REDLINE_AID_FINISH = registerProtected("redline_aid_finish");
    public static final Block TOWNHALL_REPAIR_BENCH = registerProtected("townhall_repair_bench");
    public static final Block ALI_HIGH_MAP = registerProtected("ali_high_map");
    public static final Block ALI_GATE_RUBBING = registerProtected("ali_gate_rubbing");
    public static final Block ALI_MAP_TABLE = registerProtected("ali_map_table");
    public static final Block ALI_NORTH_DOOR = registerProtected("ali_north_door");
    public static final Block ALI_EAST_DOOR = registerProtected("ali_east_door");
    public static final Block ALI_WEST_DOOR = registerProtected("ali_west_door");
    public static final Block ALI_RELEASE_HIGH = registerProtected("ali_release_high");
    public static final Block ALI_RELEASE_LOW = registerProtected("ali_release_low");
    public static final Block TOWNHALL_SHORTCUT_BOARD = registerProtected("townhall_shortcut_board");
    public static final Block TOWNHALL_ANGLE_DESK = registerProtected("townhall_angle_desk");
    public static final Block TOWNHALL_RETURN_BOARD = registerProtected("townhall_return_board");
    public static final Block TOWNHALL_PRACTICE_STAGE = registerProtected("townhall_practice_stage");
    public static final Block TOWNHALL_ROUTE_DESK = registerProtected("townhall_route_desk");
    public static final Block TOWNHALL_RESCUE_DISPATCH = registerProtected("townhall_rescue_dispatch");
    public static final Block TOWNHALL_STAGE_DESK = registerProtected("townhall_stage_desk");
    public static final Block TOWNHALL_TOURNAMENT_BOARD = registerProtected("townhall_tournament_board");
    public static final Block TOWNHALL_STORY_COUNCIL = registerProtected("townhall_story_council");
    public static final Block TOWNHALL_OPEN_ARCHIVE = registerProtected("townhall_open_archive");
    public static final Block TOWNHALL_RELIEF_DEPOT = registerProtected("townhall_relief_depot");
    public static final Block TOWNHALL_CROSSROADS = registerProtected("townhall_crossroads");
    public static final Block COURT_DAIS = registerProtected("court_dais");
    public static final Block COURT_WEST_STATION = registerProtected("court_west_station");
    public static final Block COURT_EAST_STATION = registerProtected("court_east_station");
    public static final Block COURT_WEST_MARK = registerProtected("court_west_mark");
    public static final Block COURT_EAST_MARK = registerProtected("court_east_mark");
    public static final Block COURT_REMEMBER_ENDING = registerProtected("court_remember_ending");
    public static final Block COURT_REPAIR_ENDING = registerProtected("court_repair_ending");
    public static final Block COURT_RELEASE_ENDING = registerProtected("court_release_ending");

    private DomainBlocks() {
    }

    public static void init() {
        // Load the static registrations before structure templates are resolved.
    }

    private static Block register(String path) {
        Identifier id = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, path);
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
        Block block = Registry.register(BuiltInRegistries.BLOCK, id,
                new Block(BlockBehaviour.Properties.of()
                        .setId(blockKey).strength(3.5F, 6.0F).sound(SoundType.DEEPSLATE_BRICKS)));
        Registry.register(BuiltInRegistries.ITEM, id,
                new BlockItem(block, new Item.Properties()
                        .setId(itemKey).useBlockDescriptionPrefix()));
        return block;
    }

    private static Block registerProtected(String path) {
        Identifier id = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, path);
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        return Registry.register(BuiltInRegistries.BLOCK, id,
                new Block(BlockBehaviour.Properties.ofFullCopy(
                        net.minecraft.world.level.block.Blocks.BEDROCK).setId(key)));
    }

    private static Block registerDirectionalProtected(String path) {
        Identifier id = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, path);
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        return Registry.register(BuiltInRegistries.BLOCK, id,
                new BridgeRequestBlock(BlockBehaviour.Properties.ofFullCopy(
                        net.minecraft.world.level.block.Blocks.BEDROCK).setId(key)));
    }

    private static final class BridgeRequestBlock extends HorizontalDirectionalBlock {
        private BridgeRequestBlock(BlockBehaviour.Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }
    }
}
