package com.feel.gems.adventure.estate;

import com.feel.gems.util.GemsTeleport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/** Bounded, server-thread return from the mansion to a verified outer road. */
final class EstateReturnService {
    private static final int MAX_WAITING = 8;
    private static final int MAX_GATE_CHUNKS = 9;
    private static final int MAX_GATE_TICKS = 20 * 6;
    private static final int MAX_TOTAL_TICKS = 20 * 32;
    private static final Map<UUID, Attempt> WAITING = new LinkedHashMap<>();
    private static Attempt active;
    private static MinecraftServer owner;

    private EstateReturnService() { }

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(EstateReturnService::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUUID();
            WAITING.remove(id);
            if (active != null && active.player.getUUID().equals(id)) active.cancel = true;
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (owner != server) return;
            if (active != null) active.releaseAll();
            active = null;
            WAITING.clear();
            owner = null;
        });
    }

    static void request(ServerPlayer player, ServerLevel level, EstateRouteSite.Site site,
                        BlockPos returnStone, BlockPos preferred) {
        MinecraftServer server = level.getServer();
        if (owner != server) {
            if (active != null) active.releaseAll();
            active = null;
            WAITING.clear();
            owner = server;
        }
        if (active != null && active.player == player || WAITING.containsKey(player.getUUID())) {
            tell(player, "The return is opening. Stay by the stone.");
            return;
        }
        if (WAITING.size() >= MAX_WAITING) {
            tell(player, "The return stone is busy. Try again shortly.");
            return;
        }
        List<EstateRouteSite.Gate> gates = orderedGates(site, preferred);
        if (level.dimension() != Level.OVERWORLD || gates.size() != 4
                || site.foyer().distSqr(returnStone) > 8L * 8L) {
            tell(player, "The return stone cannot find this estate's roads.");
            return;
        }
        WAITING.put(player.getUUID(), new Attempt(player, level, site,
                returnStone, gates));
        tell(player, "The return stone is finding a safe outer road. Stay nearby.");
    }

    private static List<EstateRouteSite.Gate> orderedGates(EstateRouteSite.Site site,
                                                            BlockPos preferred) {
        List<EstateRouteSite.Gate> gates = new ArrayList<>(Arrays.asList(site.gates()));
        gates.removeIf(gate -> gate == null);
        if (preferred != null) {
            for (int i = 0; i < gates.size(); i++) {
                if (gates.get(i).marker().equals(preferred)) {
                    EstateRouteSite.Gate first = gates.remove(i);
                    gates.addFirst(first);
                    break;
                }
            }
        }
        return gates;
    }

    private static void tick(MinecraftServer server) {
        if (owner != server) return;
        if (active == null) {
            if (WAITING.isEmpty()) return;
            UUID first = WAITING.keySet().iterator().next();
            active = WAITING.remove(first);
            active.started = server.getTickCount();
        }
        Attempt attempt = active;
        if (attempt.cancel || !attempt.stillWaiting(server)) {
            attempt.cancel = true;
            attempt.cleanup();
        } else if (server.getTickCount() - attempt.started > MAX_TOTAL_TICKS) {
            attempt.expired = true;
            attempt.cancel = true;
            attempt.cleanup();
        } else {
            attempt.tick();
        }
        if (attempt.done) active = null;
    }

    /** Only the selected gate and its three-lane exterior approach are loaded. */
    static List<ChunkPos> gateChunks(EstateRouteSite.Gate gate) {
        BlockPos marker = gate.marker();
        BlockPos left = gate.at(gate.distance() + 8, -1);
        BlockPos right = gate.at(gate.distance() + 8, 1);
        int minX = Math.min(marker.getX(), Math.min(left.getX(), right.getX()));
        int maxX = Math.max(marker.getX(), Math.max(left.getX(), right.getX()));
        int minZ = Math.min(marker.getZ(), Math.min(left.getZ(), right.getZ()));
        int maxZ = Math.max(marker.getZ(), Math.max(left.getZ(), right.getZ()));
        List<ChunkPos> chunks = new ArrayList<>();
        for (int x = minX >> 4; x <= maxX >> 4; x++)
            for (int z = minZ >> 4; z <= maxZ >> 4; z++)
                chunks.add(new ChunkPos(x, z));
        return List.copyOf(chunks);
    }

    private static final class Attempt {
        private final ServerPlayer player;
        private final ServerLevel level;
        private final EstateRouteSite.Site site;
        private final BlockPos returnStone;
        private final List<EstateRouteSite.Gate> gates;
        private long started;
        private final Set<Long> tickets = new HashSet<>();
        private List<ChunkPos> needed = List.of();
        private int gateIndex;
        private int issued;
        private long gateStarted;
        private boolean cleaning;
        private boolean succeeded;
        private boolean cancel;
        private boolean expired;
        private boolean done;

        private Attempt(ServerPlayer player, ServerLevel level, EstateRouteSite.Site site,
                        BlockPos returnStone, List<EstateRouteSite.Gate> gates) {
            this.player = player;
            this.level = level;
            this.site = site;
            this.returnStone = returnStone;
            this.gates = gates;
        }

        private boolean stillWaiting(MinecraftServer server) {
            if (server.getPlayerList().getPlayer(player.getUUID()) != player
                    || !player.isAlive() || player.level() != level
                    || player.blockPosition().distSqr(returnStone) > 8L * 8L
                    || level.getBlockState(returnStone).getBlock() != EstateBlocks.RETURN)
                return false;
            EstateRouteSite.Site current = EstateRouteSite.readLoaded(level, returnStone);
            return current != null && current.encoded().equals(site.encoded())
                    && EstateSiteIntegrity.clearLanding(level, returnStone);
        }

        private void tick() {
            if (cleaning) { cleanup(); return; }
            if (gateIndex == gates.size()) { finish(false); return; }
            if (needed.isEmpty()) {
                needed = gateChunks(gates.get(gateIndex));
                gateStarted = level.getGameTime();
                if (needed.size() > MAX_GATE_CHUNKS) { cleaning = true; return; }
            }
            if (level.getGameTime() - gateStarted > MAX_GATE_TICKS) {
                cleaning = true;
                return;
            }
            if (issued < needed.size()) {
                ChunkPos next = needed.get(issued++);
                if (level.getChunkSource().getChunkNow(next.x(), next.z()) == null) {
                    tickets.add(next.pack());
                    level.setChunkForced(next.x(), next.z(), true);
                }
                return;
            }
            for (ChunkPos chunk : needed)
                if (level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) == null) return;
            EstateRouteSite.Gate gate = gates.get(gateIndex);
            BlockPos marker = gate.marker();
            EstateRouteSite.Site atGate = EstateRouteSite.readLoaded(level, marker);
            if (atGate != null && atGate.encoded().equals(site.encoded())
                    && EstateSiteIntegrity.inspectEntryLoaded(level, site, marker,
                            gate.wing()) == EstateSiteIntegrity.Result.READY
                    && safeLanding(marker)) {
                BlockPos feet = marker.above();
                if (GemsTeleport.teleport(player, level, feet.getX() + 0.5,
                        feet.getY(), feet.getZ() + 0.5,
                        player.getYRot(), player.getXRot())) {
                    EstateRouteReadiness.clear(player);
                    tell(player, "Outside again. The other roads are still open.");
                    succeeded = true;
                }
            }
            cleaning = true;
        }

        private boolean safeLanding(BlockPos marker) {
            BlockPos feet = marker.above();
            if (!level.getWorldBorder().isWithinBounds(feet)
                    || !EstateSiteIntegrity.clearLanding(level, marker)) return false;
            for (BlockPos pos : List.of(feet, feet.above()))
                if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                        || !level.getFluidState(pos).isEmpty()) return false;
            return !level.getBlockState(marker).getCollisionShape(level, marker).isEmpty();
        }

        private void cleanup() {
            int removed = 0;
            while (!tickets.isEmpty() && removed < 2) {
                long packed = tickets.iterator().next();
                tickets.remove(packed);
                level.setChunkForced(ChunkPos.getX(packed), ChunkPos.getZ(packed), false);
                removed++;
            }
            if (!tickets.isEmpty()) return;
            if (cancel || succeeded) { finish(succeeded); return; }
            gateIndex++;
            needed = List.of();
            issued = 0;
            cleaning = false;
        }

        private void finish(boolean success) {
            if (!success && expired)
                tell(player, "The return stone could not open a safe road in time. Try again.");
            else if (!success && !cancel)
                tell(player, "No verified outer road is safe right now. Try the stone later.");
            done = true;
        }

        private void releaseAll() {
            for (long packed : tickets)
                level.setChunkForced(ChunkPos.getX(packed), ChunkPos.getZ(packed), false);
            tickets.clear();
        }
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), false);
    }
}
