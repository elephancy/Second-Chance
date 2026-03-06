package com.feel.gems.adventure;

import com.feel.gems.adventure.satellite.SatelliteTownhallHints;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;

/** Branch victory opens one concrete side-route clue for each Atlas expedition. */
public final class RouteDeskRuntime {
    private static final Map<UUID, Integer> SELECTED = new HashMap<>();

    private RouteDeskRuntime() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.getBlockState(hit.getBlockPos()).is(DomainBlocks.TOWNHALL_ROUTE_DESK)) {
                return InteractionResult.PASS;
            }
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            if (level.dimension() != Level.OVERWORLD) {
                tell(serverPlayer, "The Route Desk belongs in the Overworld townhall.");
                return InteractionResult.SUCCESS_SERVER;
            }
            if (!CampaignPersistentState.get(level.getServer()).model()
                    .worldChanged(LandmarkKey.BRANCH)) {
                tell(serverPlayer, "Clear Branch to open this Route Desk.");
                return InteractionResult.SUCCESS_SERVER;
            }
            LandmarkKey[] targets = LandmarkKey.values();
            int index = SELECTED.getOrDefault(serverPlayer.getUUID(), 0);
            if (serverPlayer.isShiftKeyDown()) {
                index = (index + 1) % targets.length;
                SELECTED.put(serverPlayer.getUUID(), index);
                tell(serverPlayer, "Alternate route: " + targets[index].label()
                        + ". Use normally to read it.");
            } else {
                LandmarkKey target = targets[index];
                tell(serverPlayer, target.label() + ": " + RouteDeskClues.alternate(target));
                String archiveLead = SatelliteTownhallHints.routeDesk(level.getServer());
                if (!archiveLead.isEmpty()) tell(serverPlayer, archiveLead);
            }
            return InteractionResult.SUCCESS_SERVER;
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                SELECTED.remove(handler.getPlayer().getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SELECTED.clear());
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), false);
    }
}
