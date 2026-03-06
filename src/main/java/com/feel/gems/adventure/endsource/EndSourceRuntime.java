package com.feel.gems.adventure.endsource;

import com.feel.gems.adventure.CampaignRouteState;
import com.feel.gems.adventure.CampaignRouteState.Path;
import com.feel.gems.adventure.AdventureSounds;
import com.feel.gems.adventure.DimensionReturnService;
import com.feel.gems.adventure.RouteFinaleRuntime;
import com.feel.gems.adventure.RouteFinaleState;
import com.feel.gems.adventure.nether.NetherChapterState;
import com.feel.gems.sound.ModSounds;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

/** Server-owned, route-specific source chapter; natural starts are staged off. */
public final class EndSourceRuntime {
    private static final int WEST = 1;
    private static final int EAST = 2;
    private static final long IDLE_LIMIT = 20L * 600;
    private static final long REPAIR_LIMIT = 20L * 180;
    private static final long RELEASE_LIMIT = 20L * 150;
    private static final int PAD_HOLD_TICKS = 20 * 7;
    private static final Map<UUID, Attempt> ATTEMPTS = new HashMap<>();
    private static final Map<Block, BlockPos> CONTROLS = Map.of(
            EndSourceBlocks.CORE, new BlockPos(23, 5, 23),
            EndSourceBlocks.WEST_ACCOUNT, new BlockPos(8, 5, 23),
            EndSourceBlocks.EAST_ACCOUNT, new BlockPos(38, 5, 23),
            EndSourceBlocks.WEST_LENS, new BlockPos(18, 5, 23),
            EndSourceBlocks.EAST_LENS, new BlockPos(28, 5, 23),
            EndSourceBlocks.NORTH_SIGNAL, new BlockPos(23, 5, 8),
            EndSourceBlocks.SOUTH_SIGNAL, new BlockPos(23, 5, 38),
            EndSourceBlocks.RETURN_ARCH, new BlockPos(23, 5, 41));
    private static final Block[] SIGNAL_RING = {
            EndSourceBlocks.WEST_ACCOUNT, EndSourceBlocks.NORTH_SIGNAL,
            EndSourceBlocks.EAST_ACCOUNT, EndSourceBlocks.SOUTH_SIGNAL};
    private static boolean registered;

    private EndSourceRuntime() { }

    public static void register() {
        if (registered) return;
        registered = true;
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            BlockPos position = hit.getBlockPos();
            Block block = world.getBlockState(position).getBlock();
            BlockPos local = CONTROLS.get(block);
            if (local == null) return InteractionResult.PASS;
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            if (level.dimension() != Level.END) {
                tell(serverPlayer, "The End Source is on an outer End island.");
                return InteractionResult.SUCCESS_SERVER;
            }
            // A visitor must be able to leave even if the root or another
            // control failed to arrive. Sneak-use works at any surviving mark.
            if (block == EndSourceBlocks.RETURN_ARCH || player.isShiftKeyDown()) {
                return returnToOverworld(level, serverPlayer);
            }
            Site site = locate(level, position, local);
            if (site == null) {
                tell(serverPlayer, "This observatory is incomplete. Its source controls are sealed.");
                return InteractionResult.SUCCESS_SERVER;
            }
            return interact(level, serverPlayer, block, site);
        });
        ServerTickEvents.END_SERVER_TICK.register(EndSourceRuntime::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                ATTEMPTS.remove(handler.getPlayer().getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ATTEMPTS.clear());
    }

    private static InteractionResult interact(ServerLevel level, ServerPlayer player,
                                              Block block, Site site) {
        MinecraftServer server = level.getServer();
        Path path = CampaignRouteState.get(server).chosen();
        if (path == null) {
            tell(player, "Choose a route at the townhall Story Council before reading this source.");
            return InteractionResult.SUCCESS_SERVER;
        }
        EndSourceState state = EndSourceState.get(server);
        if (state.cleared(path)) {
            tell(player, switch (path) {
                case REMEMBER -> "You kept both stories. The west story is already safe at the Court.";
                case REPAIR -> "The source braces hold. The Court's west work site will already be supplied.";
                case RELEASE -> "The path is open. The first call at the Court is already done.";
            });
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!NetherChapterState.get(server).requiredChaptersCleared()) {
            tell(player, "Restore the Ash Annex and Ember Bridge before using this lens.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!RouteFinaleState.get(server).complete() && !RouteFinaleRuntime.ready(server, path)) {
            tell(player, "Beat all seven bosses and finish your three story tasks before using this lens.");
            return InteractionResult.SUCCESS_SERVER;
        }
        UUID id = player.getUUID();
        long now = server.getTickCount();
        Attempt attempt = ATTEMPTS.get(id);
        if (block == EndSourceBlocks.CORE
                && (attempt == null || !attempt.matches(site, path, now))) {
            attempt = new Attempt(path, site, now);
            ATTEMPTS.put(id, attempt);
            tell(player, start(path));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (attempt == null || !attempt.matches(site, path, now)) {
            ATTEMPTS.remove(id);
            tell(player, "Begin at the source lens in the center chamber. Use the return arch, or sneak-use any source mark to leave.");
            return InteractionResult.SUCCESS_SERVER;
        }
        attempt.lastAction = now;
        if (block == EndSourceBlocks.CORE) {
            if (!attempt.complete()) {
                tell(player, progress(attempt));
                return InteractionResult.SUCCESS_SERVER;
            }
            ATTEMPTS.remove(id);
            if (state.clear(path)) {
                server.getPlayerList().broadcastSystemMessage(Component.literal(
                        "The End Source is understood. " + aid(path)), false);
                level.playSound(null, player.blockPosition(), ModSounds.ARCANE_CHIME,
                        SoundSource.BLOCKS, 1.0F, 1.2F);
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        switch (path) {
            case REMEMBER -> remember(player, block, attempt);
            case REPAIR -> repair(player, block, attempt, now);
            case RELEASE -> release(player, block, attempt, now);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static void remember(ServerPlayer player, Block block, Attempt attempt) {
        if (block == EndSourceBlocks.WEST_ACCOUNT || block == EndSourceBlocks.EAST_ACCOUNT) {
            int side = block == EndSourceBlocks.WEST_ACCOUNT ? WEST : EAST;
            attempt.accounts |= side;
            tell(player, side == WEST
                    ? "The west account says the route was drawn before anyone arrived. Its border matches a lens nearby."
                    : "The east account says the route changed after everyone arrived. Its border matches a lens nearby.");
        } else if (block == EndSourceBlocks.WEST_LENS || block == EndSourceBlocks.EAST_LENS) {
            int side = block == EndSourceBlocks.WEST_LENS ? WEST : EAST;
            if ((attempt.accounts & side) == 0) {
                tell(player, "This lens has a place for an account that is still missing.");
                return;
            }
            attempt.bound |= side;
            tell(player, attempt.bound == (WEST | EAST)
                    ? "Both sides of the story are safe. The center lens brightens."
                    : "One story is safe. The other gallery is still quiet.");
        } else {
            tell(player, "The west and east light paths lead away from the center.");
        }
    }

    private static void repair(ServerPlayer player, Block block, Attempt attempt, long now) {
        if (now - attempt.started > REPAIR_LIMIT) {
            ATTEMPTS.remove(player.getUUID());
            tell(player, "The braces faded. Begin again at the center lens; the return arch remains open.");
            return;
        }
        if (block == EndSourceBlocks.WEST_LENS || block == EndSourceBlocks.EAST_LENS) {
            int side = block == EndSourceBlocks.WEST_LENS ? WEST : EAST;
            if ((attempt.defended & side) != 0) {
                tell(player, "This brace is holding. Another work point still pulses.");
            } else {
                attempt.braces |= side;
                tell(player, "Brace placed. The light pulse is building around this work point.");
            }
        } else {
            tell(player, "Both side lenses pulse against the broken frame.");
        }
    }

    private static void release(ServerPlayer player, Block block, Attempt attempt, long now) {
        if (now - attempt.started > RELEASE_LIMIT) {
            ATTEMPTS.remove(player.getUUID());
            tell(player, "Time ran out. Start again at the center lens.");
            return;
        }
        Block expected = attempt.nextSignal();
        if (block != expected) {
            tell(player, "The lit path bends away from this mark.");
            return;
        }
        attempt.releaseStep++;
        tell(player, attempt.releaseStep == SIGNAL_RING.length
                ? "The signal crossed every gallery. The center lens shines again."
                : "The route shifted. The next light path is visible.");
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 5 != 0 || ATTEMPTS.isEmpty()) return;
        var iterator = ATTEMPTS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Attempt attempt = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            long now = server.getTickCount();
            if (player == null || !player.isAlive() || player.isSpectator()
                    || player.level().dimension() != Level.END
                    || now - attempt.lastAction > IDLE_LIMIT
                    || (attempt.path == Path.REPAIR && now - attempt.started > REPAIR_LIMIT)
                    || (attempt.path == Path.RELEASE && now - attempt.started > RELEASE_LIMIT)
                    || !near(player.blockPosition(), attempt.site.root, 43)) {
                iterator.remove();
                continue;
            }
            if (attempt.path != Path.REPAIR) continue;
            ServerLevel level = player.level();
            for (int side : new int[] {WEST, EAST}) {
                if ((attempt.braces & side) == 0 || (attempt.defended & side) != 0) continue;
                BlockPos marker = attempt.site.world(side == WEST ? new BlockPos(18, 5, 23)
                        : new BlockPos(28, 5, 23));
                if (level.getChunkSource().getChunkNow(marker.getX() >> 4,
                        marker.getZ() >> 4) == null
                        || !level.getBlockState(marker).is(side == WEST
                        ? EndSourceBlocks.WEST_LENS : EndSourceBlocks.EAST_LENS)) {
                    attempt.padTicks[side == WEST ? 0 : 1] = 0;
                    continue;
                }
                int index = side == WEST ? 0 : 1;
                if (player.distanceToSqr(marker.getX() + 0.5, marker.getY() + 0.5,
                        marker.getZ() + 0.5) > 3.5 * 3.5) {
                    attempt.padTicks[index] = 0;
                    continue;
                }
                attempt.padTicks[index] = Math.min(PAD_HOLD_TICKS, attempt.padTicks[index] + 5);
                if (attempt.padTicks[index] == 20 * 4) {
                    tell(player, "The light pulse is rising. Keep protecting this work point.");
                    level.playSound(null, marker, AdventureSounds.CADENZA_CALL,
                            SoundSource.BLOCKS, 0.6F, 1.4F);
                }
                if (attempt.padTicks[index] == PAD_HOLD_TICKS) {
                    attempt.defended |= side;
                    tell(player, attempt.defended == (WEST | EAST)
                            ? "Both work points held. The center lens shines again."
                            : "This work point held. The other side still flickers.");
                    level.playSound(null, marker, ModSounds.ARCANE_CHIME,
                            SoundSource.BLOCKS, 0.7F, 1.1F);
                }
            }
        }
    }

    private static InteractionResult returnToOverworld(ServerLevel end, ServerPlayer player) {
        BlockPos court = EndSourceState.get(end.getServer()).courtReturn(player.getUUID());
        DimensionReturnService.tryReturn(player, court);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static Site locate(ServerLevel level, BlockPos clicked, BlockPos local) {
        for (int turns = 0; turns < 4; turns++) {
            int[] offset = rotate(local.getX() - 23, local.getZ() - 23, turns);
            BlockPos root = clicked.offset(-offset[0], 4 - local.getY(), -offset[1]);
            if (level.getChunkSource().getChunkNow(root.getX() >> 4,
                    root.getZ() >> 4) == null) continue;
            var stamp = level.getBlockState(root);
            if (!stamp.is(EndSourceBlocks.ROOT)
                    || stamp.getValue(HorizontalDirectionalBlock.FACING)
                            != Direction.from2DDataValue((turns + 2) % 4)
                    || !level.getBlockState(root.above()).is(EndSourceBlocks.CORE)) continue;
            return new Site(root, turns);
        }
        return null;
    }

    private static int[] rotate(int x, int z, int turns) {
        return switch (turns) {
            case 0 -> new int[] {x, z};
            case 1 -> new int[] {-z, x};
            case 2 -> new int[] {-x, -z};
            default -> new int[] {z, -x};
        };
    }

    private static boolean near(BlockPos a, BlockPos b, int horizontal) {
        return Math.abs((long) a.getX() - b.getX()) <= horizontal
                && Math.abs((long) a.getY() - b.getY()) <= 12
                && Math.abs((long) a.getZ() - b.getZ()) <= horizontal;
    }

    private static String start(Path path) {
        return switch (path) {
            case REMEMBER -> "Remember: two accounts lie beyond the west and east light paths.";
            case REPAIR -> "Repair: the side lenses are failing. The frame gives you three minutes.";
            case RELEASE -> "Release: the gallery lights change with each call. The signal lasts two and a half minutes.";
        };
    }

    private static String progress(Attempt attempt) {
        return switch (attempt.path) {
            case REMEMBER -> "Stories saved: " + Integer.bitCount(attempt.bound)
                    + "/2. The remaining gallery still carries a voice.";
            case REPAIR -> "Work points defended: " + Integer.bitCount(attempt.defended)
                    + "/2. A side lens still pulses against the frame.";
            case RELEASE -> "Calls followed: " + attempt.releaseStep + "/4. The lit path has shifted.";
        };
    }

    private static String aid(Path path) {
        return switch (path) {
            case REMEMBER -> "The west story is safe at the Court; the east gallery is still quiet.";
            case REPAIR -> "The Court's west brace is supplied; the east work site still needs help.";
            case RELEASE -> "The first call at the Court is done; follow the remaining calls.";
        };
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), false);
    }

    private record Site(BlockPos root, int turns) {
        BlockPos world(BlockPos local) {
            int[] offset = rotate(local.getX() - 23, local.getZ() - 23, turns);
            return root.offset(offset[0], local.getY() - 4, offset[1]);
        }
    }

    private static final class Attempt {
        private final Path path;
        private final Site site;
        private final long started;
        private long lastAction;
        private int accounts;
        private int bound;
        private int braces;
        private int defended;
        private final int[] padTicks = new int[2];
        private final int signalOffset;
        private int releaseStep;

        private Attempt(Path path, Site site, long now) {
            this.path = path;
            this.site = site;
            this.started = now;
            this.lastAction = now;
            this.signalOffset = Math.floorMod((int) (site.root.asLong() ^ now),
                    SIGNAL_RING.length);
        }

        private boolean matches(Site other, Path selected, long now) {
            return path == selected && site.equals(other) && now - lastAction <= IDLE_LIMIT
                    && (path != Path.REPAIR || now - started <= REPAIR_LIMIT)
                    && (path != Path.RELEASE || now - started <= RELEASE_LIMIT);
        }

        private boolean complete() {
            return switch (path) {
                case REMEMBER -> bound == (WEST | EAST);
                case REPAIR -> defended == (WEST | EAST);
                case RELEASE -> releaseStep == SIGNAL_RING.length;
            };
        }

        private Block nextSignal() {
            return SIGNAL_RING[(signalOffset + Math.min(releaseStep,
                    SIGNAL_RING.length - 1)) % SIGNAL_RING.length];
        }
    }
}
