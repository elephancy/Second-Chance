package com.feel.gems.adventure.nether;

import com.feel.gems.GemsMod;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** Protected, itemless controls and root stamps for the two Nether chapters. */
public final class NetherChapterBlocks {
    public static final Block ANNEX_ROOT = directional("ash_annex_root");
    public static final Block ANNEX_GATE = protectedBlock("ash_annex_gate");
    public static final Block ANNEX_REGISTER = protectedBlock("ash_annex_register");
    public static final Block ANNEX_SIGNAL = protectedBlock("ash_annex_signal");
    public static final Block ANNEX_PLATFORM = protectedBlock("ash_annex_platform");
    public static final Block ANNEX_BOARD_SHELTER = protectedBlock("ash_annex_board_shelter");
    public static final Block ANNEX_BOARD_TRANSFER = protectedBlock("ash_annex_board_transfer");
    public static final Block ANNEX_BOARD_RETURN = protectedBlock("ash_annex_board_return");

    public static final Block BRIDGE_ROOT = directional("ember_bridge_root");
    public static final Block BRIDGE_GATE = protectedBlock("ember_bridge_gate");
    public static final Block BRIDGE_EAST_SHELTER = protectedBlock("ember_bridge_east_shelter");
    public static final Block BRIDGE_WEST_SHELTER = protectedBlock("ember_bridge_west_shelter");
    public static final Block BRIDGE_START = protectedBlock("ember_bridge_start");
    public static final Block BRIDGE_MIDPOINT = protectedBlock("ember_bridge_midpoint");
    public static final Block BRIDGE_FINISH = protectedBlock("ember_bridge_finish");

    private NetherChapterBlocks() { }

    /** Force registrations before a structure palette is decoded. */
    public static void init() { }

    private static Block protectedBlock(String name) {
        Identifier id = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, name);
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        return Registry.register(BuiltInRegistries.BLOCK, id,
                new Block(BlockBehaviour.Properties.ofFullCopy(
                        net.minecraft.world.level.block.Blocks.BEDROCK).setId(key)));
    }

    private static Block directional(String name) {
        Identifier id = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, name);
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        return Registry.register(BuiltInRegistries.BLOCK, id,
                new RootBlock(BlockBehaviour.Properties.ofFullCopy(
                        net.minecraft.world.level.block.Blocks.BEDROCK).setId(key)));
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
