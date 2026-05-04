package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.AdventureItems;
import com.feel.gems.adventure.LandmarkKey;
import com.feel.gems.adventure.RequiredChapters;
import com.feel.gems.adventure.nether.EmberV6ReadinessState;
import com.mojang.brigadier.Command;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Test-only connected Atlas check on a copied natural two-start Nether save. */
public final class DimensionReadyAtlasServerSmoke implements ModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("gems-dimension-ready-atlas-server");
    private static final BlockPos FEET = new BlockPos(0, 112, 512);
    private static final ChunkPos SEALED_OWNER = new ChunkPos(-45, 8);
    private static final BlockPos SEALED_ROOT = new BlockPos(-697, 44, 105);
    private static final BlockPos SEALED_ARCH = new BlockPos(-713, 45, 58);
    private static final ChunkPos READY_OWNER = new ChunkPos(-16, -42);
    private static final BlockPos READY_ROOT = new BlockPos(-233, 67, -695);
    private static final BlockPos READY_ARCH = new BlockPos(-217, 68, -648);
    private boolean enabled;
    private boolean complete;
    private int joinedAt;
    private boolean prompted;
    private ServerPlayer player;

    @Override
    public void onInitialize() {
        enabled = Boolean.getBoolean("gems.dimensionReadyAtlas.server");
        if (!enabled) return;
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(Commands.literal("dimready").then(Commands.literal("ack")
                        .executes(context -> {
                            if (context.getSource().getPlayer() != player || complete) return 0;
                            complete = true;
                            LOG.info("DIMREADY_SERVER_PASS connected_client_atlas_ack=true "
                                    + "near_sealed={} far_ready={}", SEALED_OWNER, READY_OWNER);
                            player.sendSystemMessage(Component.literal("DIMREADY|COMPLETE"));
                            return Command.SINGLE_SUCCESS;
                        }))));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> join(server,
                handler.getPlayer()));
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }

    private void join(MinecraftServer server, ServerPlayer joined) {
        if (complete || !enabled) return;
        player = joined;
        if (!joined.getName().getString().equals("DimReadyB")) {
            fail("Unexpected connected player");
            return;
        }
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether == null || !RequiredChapters.active(server)) {
            fail("Staged required chapter sets are not active in disposable world");
            return;
        }
        EmberV6ReadinessState state = EmberV6ReadinessState.get(nether);
        if (state.verdict(SEALED_OWNER, SEALED_ROOT, SEALED_ARCH)
                != EmberV6ReadinessState.Verdict.SEALED
                || state.verdict(READY_OWNER, READY_ROOT, READY_ARCH)
                != EmberV6ReadinessState.Verdict.READY) {
            fail("Saved two-start readiness identities did not survive copy/restart");
            return;
        }
        LOG.info("DIMREADY_INDEX_PASS sealedOwner={} readyOwner={} save=true",
                SEALED_OWNER, READY_OWNER);
        // This staging pad is only for a safe connected Atlas packet. It is
        // outside both site footprints and is not a natural-travel claim.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos floor = FEET.offset(dx, -1, dz);
                nether.setBlock(floor, Blocks.OBSIDIAN.defaultBlockState(), 3);
                for (int dy = 0; dy <= 2; dy++)
                    nether.setBlock(FEET.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        ItemStack atlas = new ItemStack(AdventureItems.RUMOR_ATLAS);
        CustomData.update(DataComponents.CUSTOM_DATA, atlas,
                data -> data.putInt("rumor_atlas_target", LandmarkKey.values().length + 16));
        joined.getInventory().setItem(0, atlas);
        joined.getInventory().setSelectedSlot(0);
        joined.teleportTo(nether, FEET.getX() + .5, FEET.getY(), FEET.getZ() + .5,
                Set.of(), 0, 0, true);
        LOG.info("DIMREADY_SETUP_ONLY dimension=nether feet={} atlasPage=Ember", FEET);
        joinedAt = server.getTickCount();
    }

    private void tick(MinecraftServer server) {
        if (!enabled || complete || player == null) return;
        int elapsed = server.getTickCount() - joinedAt;
        if (elapsed > 20 * 150) {
            fail("Connected Atlas response timed out");
            return;
        }
        if (!prompted && elapsed >= 30 && player.connection.hasClientLoaded()) {
            prompted = true;
            player.sendSystemMessage(Component.literal("DIMREADY|USE_ATLAS"));
            LOG.info("DIMREADY_PROMPT sent loadedClient=true");
        }
    }

    private void fail(String reason) {
        complete = true;
        LOG.error("DIMREADY_SERVER_FAILED reason={}", reason);
        if (player != null) player.sendSystemMessage(Component.literal("DIMREADY|FAILED|" + reason));
    }
}
