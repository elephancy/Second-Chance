package com.feel.gems.adventure.estate;

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

/** Versioned control stones used only by the Four Roads Estate. */
public final class EstateBlocks {
    public static final Block ENTRY_N = register("estate_entry_n", new ContactBlock(Contact.ENTRY_N));
    public static final Block ENTRY_E = register("estate_entry_e", new ContactBlock(Contact.ENTRY_E));
    public static final Block ENTRY_S = register("estate_entry_s", new ContactBlock(Contact.ENTRY_S));
    public static final Block ENTRY_W = register("estate_entry_w", new ContactBlock(Contact.ENTRY_W));
    public static final Block FOYER = register("estate_foyer", new ContactBlock(Contact.FOYER));
    public static final Block RETURN = register("estate_return", new Block(properties("estate_return")));
    public static final Block RESET = register("estate_reset", new Block(properties("estate_reset")));
    public static final Block TRAP_N = register("estate_trap_n", new ContactBlock(Contact.TRAP_N));
    public static final Block TRAP_E = register("estate_trap_e", new ContactBlock(Contact.TRAP_E));
    public static final Block TRAP_S = register("estate_trap_s", new ContactBlock(Contact.TRAP_S));
    public static final Block TRAP_W = register("estate_trap_w", new ContactBlock(Contact.TRAP_W));

    private EstateBlocks() { }

    public static void init() {
        // Forces registration before worldgen templates are decoded.
    }

    static char wing(Block block) {
        if (block == ENTRY_N) return 'N';
        if (block == ENTRY_E) return 'E';
        if (block == ENTRY_S) return 'S';
        if (block == ENTRY_W) return 'W';
        return '?';
    }

    static boolean isControl(Block block) {
        return wing(block) != '?' || block == FOYER || block == RETURN || block == RESET;
    }

    private static BlockBehaviour.Properties properties(String path) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.ofFullCopy(Blocks.BEDROCK)
                .setId(ResourceKey.create(Registries.BLOCK,
                        Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, path)));
        // Saved estate controls emit light as soon as their chunks relight.
        return path.equals("estate_foyer") || path.equals("estate_return")
                ? properties.lightLevel(state -> 15) : properties;
    }

    private static Block register(String path, Block block) {
        return Registry.register(BuiltInRegistries.BLOCK,
                Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, path), block);
    }

    private enum Contact {
        ENTRY_N, ENTRY_E, ENTRY_S, ENTRY_W, FOYER,
        TRAP_N, TRAP_E, TRAP_S, TRAP_W
    }

    private static final class ContactBlock extends Block {
        private final Contact contact;

        private ContactBlock(Contact contact) {
            super(EstateBlocks.properties("estate_" + contact.name().toLowerCase()));
            this.contact = contact;
        }

        @Override
        public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
            super.stepOn(level, pos, state, entity);
            if (!(entity instanceof ServerPlayer player)) return;
            switch (contact) {
                case ENTRY_N -> EstateRuntime.enterOnStep(player, pos, 'N');
                case ENTRY_E -> EstateRuntime.enterOnStep(player, pos, 'E');
                case ENTRY_S -> EstateRuntime.enterOnStep(player, pos, 'S');
                case ENTRY_W -> EstateRuntime.enterOnStep(player, pos, 'W');
                case FOYER -> EstateRuntime.reachFoyer(player, pos);
                case TRAP_N -> EstateRuntime.triggerTrap(player, pos, 'N');
                case TRAP_E -> EstateRuntime.triggerTrap(player, pos, 'E');
                case TRAP_S -> EstateRuntime.triggerTrap(player, pos, 'S');
                case TRAP_W -> EstateRuntime.triggerTrap(player, pos, 'W');
            }
        }
    }
}
