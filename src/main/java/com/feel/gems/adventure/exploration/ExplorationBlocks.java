package com.feel.gems.adventure.exploration;

import com.feel.gems.GemsMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Non-craftable marker stones placed only in authored exploration sites. */
public final class ExplorationBlocks {
    public static final Block TRAIL_ORIGIN = marker("trail_origin");
    public static final Block TRAIL_TURN = marker("trail_turn");
    public static final Block TRAIL_FINISH = marker("trail_finish");
    public static final Block CIPHER_PLAQUE = marker("cipher_plaque");
    public static final Block CIPHER_SUN = marker("cipher_sun");
    public static final Block CIPHER_STAR = marker("cipher_star");
    public static final Block CIPHER_MOON = marker("cipher_moon");
    public static final Block CIPHER_SEAL = marker("cipher_seal");
    public static final Block VISTA_DIAL = marker("vista_dial");

    private ExplorationBlocks() { }

    public static void init() {
        // Force static registration before worldgen data is decoded.
    }

    private static Block marker(String path) {
        Identifier id = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, path);
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        return Registry.register(BuiltInRegistries.BLOCK, id,
                new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.BEDROCK).setId(key)));
    }
}
