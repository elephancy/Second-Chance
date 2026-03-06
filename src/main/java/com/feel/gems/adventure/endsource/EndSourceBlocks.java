package com.feel.gems.adventure.endsource;

import com.feel.gems.GemsMod;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** Protected End Source geometry and controls, registered before NBT loading. */
public final class EndSourceBlocks {
    public static final Block LIMESTONE = block("source_limestone");
    public static final Block INLAY = block("source_inlay");
    public static final Block CHORUS_RELIEF = block("source_chorus_relief");
    public static final Block LIGHT_PATH = glowing("source_light_path", 10);
    public static final Block ROOT = directional("source_root");
    public static final Block CORE = glowing("source_core", 14);
    public static final Block WEST_ACCOUNT = block("source_west_account");
    public static final Block EAST_ACCOUNT = block("source_east_account");
    public static final Block WEST_LENS = block("source_west_lens");
    public static final Block EAST_LENS = block("source_east_lens");
    public static final Block NORTH_SIGNAL = block("source_north_signal");
    public static final Block SOUTH_SIGNAL = block("source_south_signal");
    public static final Block RETURN_ARCH = glowing("source_return_arch", 14);

    private EndSourceBlocks() { }

    public static void init() { }

    private static Block block(String name) {
        Identifier id = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, name);
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        return Registry.register(BuiltInRegistries.BLOCK, id,
                new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.BEDROCK).setId(key)));
    }

    private static Block glowing(String name, int light) {
        Identifier id = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, name);
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        return Registry.register(BuiltInRegistries.BLOCK, id,
                new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.BEDROCK)
                        .lightLevel(state -> light).setId(key)));
    }

    private static Block directional(String name) {
        Identifier id = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, name);
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        return Registry.register(BuiltInRegistries.BLOCK, id,
                new RootBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BEDROCK).setId(key)));
    }

    private static final class RootBlock extends HorizontalDirectionalBlock {
        private RootBlock(BlockBehaviour.Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }
    }
}
