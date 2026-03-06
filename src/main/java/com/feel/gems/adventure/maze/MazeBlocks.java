package com.feel.gems.adventure.maze;

import com.feel.gems.GemsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Indestructible control stones keep the maze's route playable on public servers. */
public final class MazeBlocks {
    public static final Block ENTRY = marker("maze_entry");
    public static final Block EXIT = marker("maze_exit");
    public static final Block RESET = marker("maze_reset");
    public static final Block SUN = marker("maze_sun");
    public static final Block MOON = marker("maze_moon");
    public static final Block STAR = marker("maze_star");
    public static final Block CHECKPOINT_ONE = marker("maze_checkpoint_one");
    public static final Block CHECKPOINT_TWO = marker("maze_checkpoint_two");
    public static final Block CHECKPOINT_THREE = marker("maze_checkpoint_three");
    public static final Block MIRROR_AMBER = marker("maze_mirror_amber");
    public static final Block MIRROR_BLUE = marker("maze_mirror_blue");
    public static final Block WITNESS_ONE = marker("maze_witness_one");
    public static final Block WITNESS_TWO = marker("maze_witness_two");
    public static final Block WITNESS_THREE = marker("maze_witness_three");
    public static final Block TRAP_ECHO = trap("maze_trap_echo", MazeTrap.ECHO);
    public static final Block TRAP_DART = trap("maze_trap_dart", MazeTrap.DART);
    public static final Block TRAP_SNARE = trap("maze_trap_snare", MazeTrap.SNARE);
    public static final Block TRAP_RETURN = trap("maze_trap_return", MazeTrap.RETURN);

    private MazeBlocks() {
    }

    public static void init() {
        // Forces registration before worldgen templates are decoded.
    }

    private static Block marker(String path) {
        Identifier id = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, path);
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        return Registry.register(BuiltInRegistries.BLOCK, id,
                new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.BEDROCK).setId(key)));
    }

    private static Block trap(String path, MazeTrap effect) {
        Identifier id = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, path);
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        return Registry.register(BuiltInRegistries.BLOCK, id,
                new TrapBlock(effect, BlockBehaviour.Properties.ofFullCopy(Blocks.BEDROCK).setId(key)));
    }

    enum MazeTrap { ECHO, DART, SNARE, RETURN }

    private static final class TrapBlock extends Block {
        private final MazeTrap effect;

        private TrapBlock(MazeTrap effect, Properties properties) {
            super(properties);
            this.effect = effect;
        }

        @Override
        public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
            super.stepOn(level, pos, state, entity);
            if (entity instanceof ServerPlayer player) {
                MazeRuntime.triggerTrap(player, pos, effect);
            }
        }
    }
}
