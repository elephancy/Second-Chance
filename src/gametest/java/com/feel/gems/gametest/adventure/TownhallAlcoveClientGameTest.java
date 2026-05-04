package com.feel.gems.gametest.adventure;

import java.util.EnumSet;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/** End-to-end visual gate for the current new-start townhall gallery. */
@SuppressWarnings("UnstableApiUsage")
public final class TownhallAlcoveClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        if (Boolean.getBoolean("gems.focusedNaturalClientGate")
                || Boolean.getBoolean("gems.focusedUiClientGate")) return;
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            BlockPos hall = world.getServer().computeOnServer(server ->
                    world.getConnection().getServerPlayer().blockPosition().offset(-15, -5, -15));
            world.getServer().runOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                var template = server.getStructureTemplateManager()
                        .get(Identifier.fromNamespaceAndPath("gems", "town_hall_gallery_v3"))
                        .orElseThrow();
                if (!template.placeInWorld(player.level(), hall, hall,
                        new StructurePlaceSettings(), RandomSource.create(), 2)) {
                    throw new IllegalStateException("Gallery template placement failed");
                }
                player.setNoGravity(true);
            });
            view(world, context, hall, 15.5, 5, 1.5, 0.0F, 2.0F, "townhall-gallery-v3-entrance");
            view(world, context, hall, 13.5, 5, 6.5, 0.0F, 8.0F, "townhall-gallery-v3-central-aisle");
            view(world, context, hall, 13.5, 5, 12.5, 90.0F, 3.0F, "townhall-gallery-v3-prep");
            view(world, context, hall, 17.5, 5, 12.5, -90.0F, 3.0F, "townhall-gallery-v3-tactics");
            view(world, context, hall, 13.5, 5, 22.5, 90.0F, 3.0F, "townhall-gallery-v3-routes");
            view(world, context, hall, 17.5, 5, 22.5, -90.0F, 3.0F, "townhall-gallery-v3-stories");
        }
    }

    private static void view(TestSingleplayerContext world, ClientGameTestContext context,
            BlockPos origin, double x, double y, double z, float yaw, float pitch, String name) {
        world.getServer().runOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            player.teleportTo(player.level(), origin.getX() + x, origin.getY() + y,
                    origin.getZ() + z, EnumSet.noneOf(Relative.class), yaw, pitch, false);
        });
        context.waitTicks(20);
        world.getConnection().waitForChunksRender();
        context.runOnClient(client -> {
            client.gui.hud.getChat().clearMessages(false);
            client.gui.toastManager().clear();
        });
        context.takeScreenshot(name);
    }
}
