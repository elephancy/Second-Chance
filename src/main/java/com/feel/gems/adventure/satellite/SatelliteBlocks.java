package com.feel.gems.adventure.satellite;

import com.feel.gems.GemsMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Materials and protected controls unique to the two friend satellites. */
public final class SatelliteBlocks {
    public static final Block SUNKEN_DECK_TILE = material("sunken_deck_tile", Blocks.PRISMARINE_BRICKS);
    public static final Block SUNKEN_WAVE_INLAY = material("sunken_wave_inlay", Blocks.SEA_LANTERN);
    public static final Block SUNKEN_ROOT = control("sunken_root");
    public static final Block SUNKEN_BUOY = control("sunken_buoy");
    public static final Block SUNKEN_WEST_HYDROPHONE = control("sunken_west_hydrophone");
    public static final Block SUNKEN_EAST_HYDROPHONE = control("sunken_east_hydrophone");
    public static final Block SUNKEN_TUNING_CONSOLE = control("sunken_tuning_console");
    public static final Block SUNKEN_RETURN = control("sunken_return");

    public static final Block UNDERARCHIVE_INDEX_TILE = material("underarchive_index_tile", Blocks.TUFF_BRICKS);
    public static final Block UNDERARCHIVE_ROOT = control("underarchive_root");
    public static final Block UNDERARCHIVE_CLUE = control("underarchive_clue");
    public static final Block UNDERARCHIVE_FIRST_INDEX = control("underarchive_first_index");
    public static final Block UNDERARCHIVE_SECOND_INDEX = control("underarchive_second_index");
    public static final Block UNDERARCHIVE_THIRD_INDEX = control("underarchive_third_index");
    public static final Block UNDERARCHIVE_DESK = control("underarchive_desk");
    public static final Block UNDERARCHIVE_RETURN = control("underarchive_return");

    private SatelliteBlocks() { }

    public static void init() {
        // Class initialization owns registration before templates are decoded.
    }

    public static boolean isControl(Block block) {
        return block == SUNKEN_BUOY || block == SUNKEN_WEST_HYDROPHONE
                || block == SUNKEN_EAST_HYDROPHONE || block == SUNKEN_TUNING_CONSOLE
                || block == SUNKEN_RETURN || block == UNDERARCHIVE_CLUE
                || block == UNDERARCHIVE_FIRST_INDEX || block == UNDERARCHIVE_SECOND_INDEX
                || block == UNDERARCHIVE_THIRD_INDEX || block == UNDERARCHIVE_DESK
                || block == UNDERARCHIVE_RETURN;
    }

    private static Block material(String path, Block source) {
        Identifier id = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, path);
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        return Registry.register(BuiltInRegistries.BLOCK, id,
                new Block(BlockBehaviour.Properties.ofFullCopy(source).setId(key)));
    }

    private static Block control(String path) {
        Identifier id = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, path);
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        return Registry.register(BuiltInRegistries.BLOCK, id,
                new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.BEDROCK).setId(key)));
    }
}
