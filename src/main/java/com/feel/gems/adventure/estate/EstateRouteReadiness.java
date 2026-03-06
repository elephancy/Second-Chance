package com.feel.gems.adventure.estate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Session-only proof of a player's physical route. It never loads chunks or
 * writes terrain. A completed route is evidence of that traversal, not a
 * persistent claim that unloaded passages remain intact for later players.
 */
final class EstateRouteReadiness {
    private static final Map<UUID, Run> RUNS = new HashMap<>();
    private static MinecraftServer owner;

    private EstateRouteReadiness() { }

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(EstateRouteReadiness::tick);
    }

    static boolean begin(ServerPlayer player, EstateRouteSite.Site site,
                         BlockPos gate, char wing) {
        if (site == null || site.gate(wing) == null
                || !site.gate(wing).marker().equals(gate)) return false;
        MinecraftServer server = player.level().getServer();
        if (owner != server) {
            RUNS.clear();
            owner = server;
        }
        RUNS.put(player.getUUID(), new Run(player, site, gate, wing,
                new EstateRouteProgress(wing)));
        return true;
    }

    static boolean completed(ServerPlayer player, EstateRouteSite.Site site) {
        return completedWing(player, site) != '?';
    }

    /** The wing comes from the live, physically observed run, never saved claim data. */
    static char completedWing(ServerPlayer player, EstateRouteSite.Site site) {
        Run run = RUNS.get(player.getUUID());
        if (run == null || run.player != player || !run.progress.complete()
                || site == null || !run.site.encoded().equals(site.encoded())
                || !run.foyer.equals(site.foyer()) || player.level() != run.level)
            return '?';
        long age = run.level.getGameTime() - run.lastTick;
        return age >= 0 && age <= 1
                && run.last.distSqr(site.foyer()) <= 64
                && player.blockPosition().distSqr(run.last) <= 16
                && player.blockPosition().distSqr(site.foyer()) <= 64
                ? run.wing : '?';
    }

    static void clear(ServerPlayer player) { RUNS.remove(player.getUUID()); }

    private static void tick(MinecraftServer server) {
        if (owner != server) {
            RUNS.clear();
            owner = server;
        }
        if (RUNS.isEmpty()) return;
        RUNS.entrySet().removeIf(entry -> {
            Run run = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            return player != run.player || !run.observe();
        });
    }

    private static final class Run {
        final ServerPlayer player;
        final ServerLevel level;
        final EstateRouteSite.Site site;
        final BlockPos gate;
        final BlockPos foyer;
        final char wing;
        final EstateRouteProgress progress;
        BlockPos last;
        ChunkPos lastChunk;
        long lastTick;

        Run(ServerPlayer player, EstateRouteSite.Site site, BlockPos gate, char wing,
            EstateRouteProgress progress) {
            this.player = player;
            this.level = player.level();
            this.site = site;
            this.gate = gate;
            this.foyer = site.foyer();
            this.wing = wing;
            this.progress = progress;
            this.last = player.blockPosition();
            this.lastTick = level.getGameTime();
        }

        boolean observe() {
            if (!player.isAlive() || player.level() != level
                    || level.dimension() != Level.OVERWORLD)
                return false;
            BlockPos here = player.blockPosition();
            long now = level.getGameTime();
            if (now == lastTick) return true;
            // A teleport, relog, long pause or unsampled leap cannot stand in
            // for a physical walk. Creative movement remains otherwise normal.
            if (now - lastTick != 1 || here.distSqr(last) > 16) return false;
            if (!dryLoadedCell(here)) return false;
            ChunkPos chunk = new ChunkPos(here.getX() >> 4, here.getZ() >> 4);
            if (!chunk.equals(lastChunk)) {
                if (EstateSiteIntegrity.inspectTraversedChunk(level, site, chunk)
                        != EstateSiteIntegrity.Result.READY) return false;
                lastChunk = chunk;
            }
            int module = moduleAt(here);
            if (!advance(module, here)) return false;
            last = here;
            lastTick = now;
            return true;
        }

        private boolean advance(int module, BlockPos here) {
            if (progress.complete()) return module == 12;
            EstateRouteSite.Seam seam = site.seam(progress.current());
            if (seam == null) return false;
            if (!progress.acceptsSeamPosition(seam, here)) return false;
            if (module == progress.current()) {
                if (!progress.seamEntered() && near(here, seam.from(), 25)
                        && EstateRouteProgress.inSeam(seam, here)) {
                    EstateSiteIntegrity.Result result = EstateSiteIntegrity.inspectSeamLoaded(
                            level, site, seam);
                    progress.enterSeam(result == EstateSiteIntegrity.Result.READY);
                }
                return true;
            }
            if (module == progress.next())
                return progress.continueToExit(module, seam, here,
                        near(here, seam.to(), 25));
            if (module == -1) {
                if (progress.seamEntered())
                    return EstateRouteProgress.inSeam(seam, here);
                return progress.current() == progress.first()
                        && near(here, gate, 144);
            }
            return false;
        }

        private int moduleAt(BlockPos here) {
            if (insideModule(progress.current(), here)) return progress.current();
            if (progress.next() >= 0 && insideModule(progress.next(), here))
                return progress.next();
            return -1;
        }

        private boolean insideModule(int id, BlockPos here) {
            BoundingBox box = site.module(id);
            return here.getX() >= box.minX() && here.getX() <= box.maxX()
                    && here.getZ() >= box.minZ() && here.getZ() <= box.maxZ()
                    && here.getY() >= box.minY() + 5
                    && here.getY() <= box.minY() + 8;
        }

        private boolean dryLoadedCell(BlockPos feet) {
            if (!level.getWorldBorder().isWithinBounds(feet)
                    || level.getChunkSource().getChunkNow(feet.getX() >> 4,
                            feet.getZ() >> 4) == null) return false;
            for (int rise = 0; rise <= 1; rise++) {
                BlockPos at = feet.above(rise);
                var state = level.getBlockState(at);
                if (!state.getCollisionShape(level, at).isEmpty()
                        || !state.getFluidState().isEmpty()) return false;
            }
            return true;
        }

        private static boolean near(BlockPos a, BlockPos b, int square) {
            long dx = (long) a.getX() - b.getX();
            long dz = (long) a.getZ() - b.getZ();
            return dx * dx + dz * dz <= square
                    && Math.abs(a.getY() - b.getY()) <= 5;
        }

    }
}
