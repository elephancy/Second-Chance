package com.feel.gems.adventure;

import com.feel.gems.sound.ModSounds;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/** Rebuild a found pattern, then mend a worn tool at the public townhall bench. */
public final class SoryalRepairRuntime {
    private static final String DOMAIN = "soryal";

    private SoryalRepairRuntime() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            Block block = world.getBlockState(hit.getBlockPos()).getBlock();
            if (block != DomainBlocks.SORYAL_SCRAP_CLUE
                    && block != DomainBlocks.SORYAL_LOFT_CLUE
                    && block != DomainBlocks.SORYAL_COPPER_MARK
                    && block != DomainBlocks.SORYAL_GLASS_MARK
                    && block != DomainBlocks.SORYAL_BRICK_MARK
                    && block != DomainBlocks.TOWNHALL_REPAIR_BENCH) {
                return InteractionResult.PASS;
            }
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            if (block == DomainBlocks.TOWNHALL_REPAIR_BENCH) return bench(level, serverPlayer);
            SoryalRepairState state = SoryalRepairState.get(level.getServer());
            if (block == DomainBlocks.SORYAL_SCRAP_CLUE || block == DomainBlocks.SORYAL_LOFT_CLUE) {
                boolean scrap = block == DomainBlocks.SORYAL_SCRAP_CLUE;
                state.record(scrap ? SoryalRepairState.SCRAP : SoryalRepairState.LOFT);
                tell(serverPlayer, scrap
                        ? "Scrap fragment: a copper rivet held the first fracture; glass dust rests in the next seam."
                        : "Loft fragment: glass catches the light beside a later patch of old brick.");
                if (state.ready() && !state.solved()) {
                    tell(serverPlayer, "Both fragments fit. The yard floor carries the same fractures.");
                }
                return InteractionResult.SUCCESS_SERVER;
            }
            int mark = block == DomainBlocks.SORYAL_COPPER_MARK ? SoryalRepairState.COPPER
                    : block == DomainBlocks.SORYAL_GLASS_MARK ? SoryalRepairState.GLASS
                    : SoryalRepairState.BRICK;
            switch (state.mark(mark)) {
                case WRONG -> tell(serverPlayer, "The patchwork slips apart. The floor marks are ready for another attempt.");
                case PROGRESS -> tell(serverPlayer, "The patch holds. " + state.step() + " of 3 seams joined.");
                case ALREADY_SOLVED -> tell(serverPlayer,
                        "The floor is repaired. The Repair Bench is open at the townhall.");
                case SOLVED -> {
                    FriendDomainUnlockState.get(level.getServer()).clear(DOMAIN);
                    level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                            "Soryal's repair is complete. Everyone can use the townhall Repair Bench."), false);
                    level.playSound(null, serverPlayer.blockPosition(), ModSounds.ARCANE_CHIME,
                            SoundSource.BLOCKS, 0.7F, 1.05F);
                }
            }
            return InteractionResult.SUCCESS_SERVER;
        });
    }

    private static InteractionResult bench(ServerLevel level, ServerPlayer player) {
        if (SoryalRepairState.get(level.getServer()).solved()) {
            FriendDomainUnlockState.get(level.getServer()).clear(DOMAIN);
        }
        if (!FriendDomainUnlockState.get(level.getServer()).cleared(DOMAIN)) {
            tell(player, "Soryal's repair is still unfinished. Explore the yard.");
            return InteractionResult.SUCCESS_SERVER;
        }
        ItemStack held = player.getMainHandItem();
        if (!held.isDamageableItem() || !held.isDamaged()) {
            tell(player, "Hold a worn tool in your main hand. One iron and one copper mend a quarter of it.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!player.getAbilities().instabuild
                && (count(player, Items.IRON_INGOT) == 0 || count(player, Items.COPPER_INGOT) == 0)) {
            tell(player, "This repair needs one iron ingot and one copper ingot.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!player.getAbilities().instabuild) {
            consume(player, Items.IRON_INGOT);
            consume(player, Items.COPPER_INGOT);
        }
        int repaired = Math.min(held.getDamageValue(), Math.max(1, held.getMaxDamage() / 4));
        held.setDamageValue(held.getDamageValue() - repaired);
        player.getInventory().setChanged();
        tell(player, "Repaired " + repaired + " durability. The original tool and its enchantments stay with you.");
        level.playSound(null, player.blockPosition(), ModSounds.ARCANE_CHIME,
                SoundSource.BLOCKS, 0.45F, 1.3F);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static int count(ServerPlayer player, Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static void consume(ServerPlayer player, Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                stack.shrink(1);
                return;
            }
        }
        throw new IllegalStateException("Checked repair material disappeared");
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), false);
    }
}
