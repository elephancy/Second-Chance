package com.feel.gems.adventure.undercard;

import com.feel.gems.GemsMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Original stone-and-brass venue materials and non-craftable controls. */
public final class UndercardBlocks {
    public static final Block SCORE_TILE = decorative("undercard_score_tile", false);
    public static final Block LAMP = decorative("undercard_lamp", true);
    public static final Block BELL = control("undercard_bell");
    public static final Block SPARRING_POST = control("undercard_sparring_post");
    public static final Block LEDGER = control("undercard_ledger");
    public static final Block EXIT = control("undercard_exit");
    public static final Block ROOT = control("undercard_root");
    public static final Block GATE = control("undercard_gate");

    private UndercardBlocks() { }

    public static void init() {
        // Force registrations before structure templates are decoded.
    }

    private static Block control(String path) {
        Identifier id = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, path);
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        return Registry.register(BuiltInRegistries.BLOCK, id,
                new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.BEDROCK).setId(key)));
    }

    private static Block decorative(String path, boolean light) {
        Identifier id = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, path);
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        BlockBehaviour.Properties properties = BlockBehaviour.Properties
                .ofFullCopy(light ? Blocks.SEA_LANTERN : Blocks.IRON_BLOCK).setId(key);
        return Registry.register(BuiltInRegistries.BLOCK, id, new Block(properties));
    }
}
