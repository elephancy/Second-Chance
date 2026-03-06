package com.feel.gems.adventure;

import com.feel.gems.util.GemsTeleport;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Explicit, bounded return from a dimension site to verified Overworld ground. */
public final class DimensionReturnService {
    private DimensionReturnService() { }

    /** Called while a player is standing safely in the Overworld, such as on Atlas use. */
    public static boolean recordOverworldDeparture(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD) return false;
        BlockPos feet = player.blockPosition();
        if (!safe(level, player, feet)) return false;
        DimensionReturnState.get(level.getServer()).remember(player.getUUID(), feet);
        return true;
    }

    /**
     * A deliberate interaction may load one previously visited anchor chunk
     * and the spawn chunk. It never edits either world or moves the player to
     * an unverified cell. Preferred is the real Court dais for an End return.
     */
    public static boolean tryReturn(ServerPlayer player, BlockPos preferredOverworld) {
        if (!(player.level() instanceof ServerLevel from)
                || from.dimension() == Level.OVERWORLD) return false;
        ServerLevel home = from.getServer().getLevel(Level.OVERWORLD);
        if (home == null) {
            tell(player, "The Overworld is not ready. The return remains available.");
            return false;
        }
        BlockPos personal = DimensionReturnState.get(from.getServer())
                .departure(player.getUUID());
        BlockPos spawn = home.getRespawnData().pos();
        Set<Long> attempted = new HashSet<>();
        for (BlockPos anchor : new BlockPos[] {preferredOverworld, personal, spawn}) {
            if (anchor == null || !attempted.add(anchor.asLong())
                    || !home.getWorldBorder().isWithinBounds(anchor)) continue;
            // The saved player and Court anchors have been visited before;
            // the vanilla shared spawn is a bounded last resort.
            if (home.getChunkSource().getChunkNow(anchor.getX() >> 4,
                    anchor.getZ() >> 4) == null) {
                home.getChunk(anchor.getX() >> 4, anchor.getZ() >> 4);
            }
            BlockPos landing = findLanding(home, player, anchor,
                    anchor.equals(spawn) ? 8 : 5,
                    anchor.equals(spawn) ? 12 : 4);
            if (landing == null) continue;
            if (!GemsTeleport.teleport(player, home, landing.getX() + 0.5,
                    landing.getY(), landing.getZ() + 0.5,
                    player.getYRot(), player.getXRot())) {
                tell(player, "The return did not complete. Try the mark again.");
                return false;
            }
            tell(player, anchor.equals(spawn)
                    ? "Returned safely near the Overworld spawn."
                    : "Returned safely to your Overworld route.");
            return true;
        }
        tell(player, "No safe Overworld landing is ready. The return stays open; use your portal or clear the landing and try again.");
        return false;
    }

    private static BlockPos findLanding(ServerLevel level, ServerPlayer player,
                                        BlockPos anchor, int radius, int height) {
        for (int distance = 0; distance <= radius; distance++) {
            for (int dx = -distance; dx <= distance; dx++) {
                for (int dz = -distance; dz <= distance; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != distance) continue;
                    for (int dy = height; dy >= -height; dy--) {
                        BlockPos feet = anchor.offset(dx, dy, dz);
                        if (safe(level, player, feet)) return feet;
                    }
                }
            }
        }
        return null;
    }

    private static boolean safe(ServerLevel level, ServerPlayer player, BlockPos feet) {
        BlockPos below = feet.below();
        BlockPos head = feet.above();
        if (feet.getY() <= level.getMinY() || head.getY() >= level.getMaxY()
                || !loaded(level, below) || !loaded(level, feet) || !loaded(level, head)
                || !level.getWorldBorder().isWithinBounds(feet)) return false;
        var support = level.getBlockState(below);
        var floor = level.getBlockState(feet);
        var ceiling = level.getBlockState(head);
        if (support.is(Blocks.MAGMA_BLOCK) || support.is(Blocks.CAMPFIRE)
                || support.is(Blocks.SOUL_CAMPFIRE) || support.is(Blocks.CACTUS)
                || floor.is(Blocks.FIRE) || floor.is(Blocks.SOUL_FIRE)
                || floor.is(Blocks.COBWEB) || floor.is(Blocks.SWEET_BERRY_BUSH)
                || ceiling.is(Blocks.FIRE) || ceiling.is(Blocks.SOUL_FIRE)
                || support.getCollisionShape(level, below).isEmpty()
                || !floor.getCollisionShape(level, feet).isEmpty()
                || !ceiling.getCollisionShape(level, head).isEmpty()
                || !level.getFluidState(feet).isEmpty()
                || !level.getFluidState(head).isEmpty()) return false;
        Vec3 destination = Vec3.atBottomCenterOf(feet);
        return level.noCollision(player, player.getBoundingBox().move(
                destination.x - player.getX(), destination.y - player.getY(),
                destination.z - player.getZ()));
    }

    private static boolean loaded(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(pos.getX() >> 4,
                pos.getZ() >> 4) != null;
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), true);
    }
}
