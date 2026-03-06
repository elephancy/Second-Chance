package com.feel.gems.adventure.nether;

import com.feel.gems.adventure.CampaignPersistentState;
import com.feel.gems.adventure.LandmarkKey;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

/** Server-owned chapter interactions. Both clears belong to the world, not a player. */
public final class NetherChapterRuntime {
    private static final long EVACUATION_LIMIT = 20L * 90;
    private static final long BOARD_LIMIT = 20L * 120;
    private static final int MIDPOINT_HOLD_TICKS = 60;
    private static final Map<BlockPos, BoardStage> BOARD_STAGE = new HashMap<>();
    private static final Map<BlockPos, Evacuation> EVACUATIONS = new HashMap<>();
    private static boolean registered;

    private static final Map<Block, BlockPos> ANNEX_CONTROLS = Map.of(
            NetherChapterBlocks.ANNEX_REGISTER, new BlockPos(6, 5, 14),
            NetherChapterBlocks.ANNEX_SIGNAL, new BlockPos(39, 5, 14),
            NetherChapterBlocks.ANNEX_PLATFORM, new BlockPos(23, 5, 8),
            NetherChapterBlocks.ANNEX_BOARD_SHELTER, new BlockPos(21, 5, 31),
            NetherChapterBlocks.ANNEX_BOARD_TRANSFER, new BlockPos(23, 5, 31),
            NetherChapterBlocks.ANNEX_BOARD_RETURN, new BlockPos(25, 5, 31));
    private static final Map<Block, BlockPos> BRIDGE_CONTROLS = Map.of(
            NetherChapterBlocks.BRIDGE_EAST_SHELTER, new BlockPos(39, 5, 23),
            NetherChapterBlocks.BRIDGE_WEST_SHELTER, new BlockPos(7, 5, 23),
            NetherChapterBlocks.BRIDGE_START, new BlockPos(37, 5, 23),
            NetherChapterBlocks.BRIDGE_MIDPOINT, new BlockPos(23, 5, 23),
            NetherChapterBlocks.BRIDGE_FINISH, new BlockPos(9, 5, 23));

    private NetherChapterRuntime() { }

    public static void register() {
        if (registered) return;
        registered = true;
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            BlockPos pos = hit.getBlockPos();
            Block block = world.getBlockState(pos).getBlock();
            if (!ANNEX_CONTROLS.containsKey(block) && !BRIDGE_CONTROLS.containsKey(block)) {
                return InteractionResult.PASS;
            }
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            if (level.dimension() != Level.NETHER) {
                tell(serverPlayer, "This chapter belongs to the Nether site.");
                return InteractionResult.SUCCESS_SERVER;
            }
            boolean annex = ANNEX_CONTROLS.containsKey(block);
            BlockPos local = annex ? ANNEX_CONTROLS.get(block) : BRIDGE_CONTROLS.get(block);
            Site site = locate(level, pos, local, annex);
            if (site == null) {
                tell(serverPlayer, "This site is sealed until its route is safe.");
                return InteractionResult.SUCCESS_SERVER;
            }
            if (annex) return annex(level, serverPlayer, block, site);
            return bridge(level, serverPlayer, block, site);
        });
        ServerTickEvents.END_SERVER_TICK.register(NetherChapterRuntime::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUUID();
            EVACUATIONS.replaceAll((root, run) -> id.equals(run.watcher)
                    && run.midpointTicks < MIDPOINT_HOLD_TICKS
                    ? new Evacuation(run.root, run.facing, run.startedTick, 0, null)
                    : run);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            BOARD_STAGE.clear();
            EVACUATIONS.clear();
        });
    }

    private static InteractionResult annex(ServerLevel level, ServerPlayer player,
                                           Block block, Site site) {
        MinecraftServer server = level.getServer();
        if (!CampaignPersistentState.get(server).model().worldChanged(LandmarkKey.LASTLIGHT)) {
            tell(player, "Find the Lastlight Exchange first. Its missing route points here.");
            return InteractionResult.SUCCESS_SERVER;
        }
        NetherChapterState state = NetherChapterState.get(server);
        if (state.annexCleared()) {
            tell(player, "The Ash Annex board now shows the route for everyone.");
            return InteractionResult.SUCCESS_SERVER;
        }
        int record = block == NetherChapterBlocks.ANNEX_REGISTER ? NetherChapterState.REGISTER
                : block == NetherChapterBlocks.ANNEX_SIGNAL ? NetherChapterState.SIGNAL
                : block == NetherChapterBlocks.ANNEX_PLATFORM ? NetherChapterState.PLATFORM : 0;
        if (record != 0) {
            state.readRecord(record);
            tell(player, switch (record) {
                case NetherChapterState.REGISTER -> "Ash register: the shelter train left first.";
                case NetherChapterState.SIGNAL -> "Signal copy: the transfer left after the shelter train.";
                default -> "Platform token: the return service was last.";
            });
            if (state.allRecordsRead()) {
                tell(player, "All three records are kept. The departure board is ready to be restored.");
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!state.allRecordsRead()) {
            tell(player, "The board needs three records. The depot still carries traces of its interrupted departure.");
            return InteractionResult.SUCCESS_SERVER;
        }
        int chosen = block == NetherChapterBlocks.ANNEX_BOARD_SHELTER ? 1
                : block == NetherChapterBlocks.ANNEX_BOARD_TRANSFER ? 2 : 3;
        long now = server.getTickCount();
        BoardStage previous = BOARD_STAGE.get(site.root());
        int expected = previous == null || now - previous.lastTick > BOARD_LIMIT
                ? 1 : previous.stage + 1;
        if (chosen != expected) {
            BOARD_STAGE.remove(site.root());
            tell(player, "The departure board resets. Read the records and try again.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (chosen < 3) {
            BOARD_STAGE.put(site.root(), new BoardStage(chosen, now));
            tell(player, chosen == 1 ? "The shelter route is set. Another timetable line remains."
                    : "The transfer is set. One timetable line remains.");
            return InteractionResult.SUCCESS_SERVER;
        }
        BOARD_STAGE.remove(site.root());
        if (state.clearAnnex()) {
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    "The Ash Annex timetable is restored. Lastlight's missing route is known to everyone."), false);
            level.playSound(null, player.blockPosition(), ModSounds.ARCANE_CHIME,
                    SoundSource.BLOCKS, 0.8F, 1.05F);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static InteractionResult bridge(ServerLevel level, ServerPlayer player,
                                            Block block, Site site) {
        MinecraftServer server = level.getServer();
        if (!CampaignPersistentState.get(server).model().worldChanged(LandmarkKey.REDLINE)) {
            tell(player, "Clear Redline House first. Its refuge plan points to this bridge.");
            return InteractionResult.SUCCESS_SERVER;
        }
        NetherChapterState state = NetherChapterState.get(server);
        if (state.bridgeCleared()) {
            tell(player, "The Ember Bridge is open. Both shelters now serve everyone.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (block == NetherChapterBlocks.BRIDGE_EAST_SHELTER
                || block == NetherChapterBlocks.BRIDGE_WEST_SHELTER) {
            int side = block == NetherChapterBlocks.BRIDGE_EAST_SHELTER
                    ? NetherChapterState.EAST_SHELTER : NetherChapterState.WEST_SHELTER;
            if (state.shelterRestored(side)) {
                tell(player, state.bothSheltersRestored()
                        ? "Both shelters are ready. The east watch line is lit."
                        : "This shelter is ready. Repair the other shelter before the evacuation.");
                return InteractionResult.SUCCESS_SERVER;
            }
            if (!player.getAbilities().instabuild
                    && (!has(player, Items.BRICK, 4) || !has(player, Items.COPPER_INGOT, 2))) {
                tell(player, "This shelter needs four bricks and two copper ingots.");
                return InteractionResult.SUCCESS_SERVER;
            }
            take(player, Items.BRICK, 4);
            take(player, Items.COPPER_INGOT, 2);
            state.restoreShelter(side);
            tell(player, state.bothSheltersRestored()
                    ? "Both shelters stand. The east watch line is lit."
                    : "One shelter stands. Repair the other side before the evacuation.");
            level.playSound(null, player.blockPosition(), ModSounds.ARCANE_CHIME,
                    SoundSource.BLOCKS, 0.5F, 0.9F);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (block == NetherChapterBlocks.BRIDGE_START) {
            if (!state.bothSheltersRestored()) {
                tell(player, "Restore both shelters first. Each needs four bricks and two copper ingots.");
                return InteractionResult.SUCCESS_SERVER;
            }
            Evacuation active = EVACUATIONS.get(site.root());
            if (active != null
                    && server.getTickCount() - active.startedTick <= EVACUATION_LIMIT) {
                tell(player, active.midpointTicks >= MIDPOINT_HOLD_TICKS
                        ? "The watch line held. The west mark glows."
                        : "An evacuation is underway. The center watch line is still unsteady.");
                return InteractionResult.SUCCESS_SERVER;
            }
            EVACUATIONS.put(site.root(), new Evacuation(site.root(),
                    site.facing(), server.getTickCount(), 0, null));
            tell(player, "Evacuation started. The center watch line and far mark glow along the crossing. You have 90 seconds.");
            return InteractionResult.SUCCESS_SERVER;
        }
        Evacuation run = EVACUATIONS.get(site.root());
        if (run == null || server.getTickCount() - run.startedTick > EVACUATION_LIMIT) {
            EVACUATIONS.remove(site.root());
            tell(player, "Begin again at the east watch line.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (block == NetherChapterBlocks.BRIDGE_MIDPOINT) {
            if (run.midpointTicks < MIDPOINT_HOLD_TICKS
                    && !player.getUUID().equals(run.watcher)) {
                run = new Evacuation(run.root, run.facing, run.startedTick,
                        0, player.getUUID());
                EVACUATIONS.put(site.root(), run);
            }
            tell(player, run.midpointTicks >= MIDPOINT_HOLD_TICKS
                    ? "The watch line is steady. The west mark glows."
                    : "Hold this watch line for three seconds while the crossing stays open.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (run.midpointTicks < MIDPOINT_HOLD_TICKS) {
            tell(player, "Hold the center watch line for three seconds before ending the evacuation.");
            return InteractionResult.SUCCESS_SERVER;
        }
        EVACUATIONS.remove(site.root());
        if (state.clearBridge()) {
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    "The Ember Bridge evacuation succeeded. Both refuge shelters now serve everyone."), false);
            level.playSound(null, player.blockPosition(), ModSounds.ARCANE_CHIME,
                    SoundSource.BLOCKS, 0.85F, 1.15F);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % (20 * 30) == 0 && !BOARD_STAGE.isEmpty()) {
            long now = server.getTickCount();
            BOARD_STAGE.values().removeIf(stage -> now - stage.lastTick > BOARD_LIMIT);
        }
        if (server.getTickCount() % 5 != 0 || EVACUATIONS.isEmpty()) return;
        var iterator = EVACUATIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Evacuation run = entry.getValue();
            if (server.getTickCount() - run.startedTick > EVACUATION_LIMIT) {
                iterator.remove();
                continue;
            }
            if (run.midpointTicks >= MIDPOINT_HOLD_TICKS || run.watcher == null) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(run.watcher);
            if (player != null && player.isAlive() && !player.isSpectator()
                    && player.level().dimension() == Level.NETHER
                    && near(player.blockPosition(), run.root, 48)
                    && atWatchLine(player, run.root, run.facing)) {
                int ticks = Math.min(MIDPOINT_HOLD_TICKS, run.midpointTicks + 5);
                entry.setValue(new Evacuation(run.root, run.facing,
                        run.startedTick, ticks, run.watcher));
                if (ticks == MIDPOINT_HOLD_TICKS && run.midpointTicks < MIDPOINT_HOLD_TICKS) {
                    tell(player, "The center watch line held. The west mark glows before time runs out.");
                }
            } else if (run.midpointTicks > 0) {
                entry.setValue(new Evacuation(run.root, run.facing,
                        run.startedTick, 0, null));
            }
        }
    }

    /** Only a correctly rotated, opened versioned site can serve its controls. */
    private static Site locate(ServerLevel level, BlockPos clicked, BlockPos local, boolean annex) {
        Block rootType = annex ? NetherChapterBlocks.ANNEX_ROOT : NetherChapterBlocks.BRIDGE_ROOT;
        Block gateType = annex ? NetherChapterBlocks.ANNEX_GATE : NetherChapterBlocks.BRIDGE_GATE;
        int gateX = annex ? 23 : 39;
        for (int turns = 0; turns < 4; turns++) {
            int[] offset = rotate(local.getX() - 23, local.getZ() - 23, turns);
            BlockPos root = clicked.offset(-offset[0], 4 - local.getY(), -offset[1]);
            if (level.getChunkSource().getChunkNow(root.getX() >> 4, root.getZ() >> 4) == null) {
                continue;
            }
            var state = level.getBlockState(root);
            if (!state.is(rootType)
                    || state.getValue(HorizontalDirectionalBlock.FACING)
                            != Direction.from2DDataValue((turns + 2) % 4)) {
                continue;
            }
            boolean gateOpen = true;
            for (int x = gateX - 1; x <= gateX + 1; x++) {
                for (int y = 5; y <= 6; y++) {
                    BlockPos gate = world(root, x, y, annex ? 43 : 40, turns);
                    if (level.getChunkSource().getChunkNow(gate.getX() >> 4,
                            gate.getZ() >> 4) == null) {
                        gateOpen = false;
                        continue;
                    }
                    Block block = level.getBlockState(gate).getBlock();
                    if (block == gateType || !level.getBlockState(gate).isAir()) gateOpen = false;
                }
            }
            if (gateOpen) return new Site(root,
                    Direction.from2DDataValue((turns + 2) % 4));
        }
        return null;
    }

    private static BlockPos world(BlockPos root, int x, int y, int z, int turns) {
        int[] rotated = rotate(x - 23, z - 23, turns);
        return root.offset(rotated[0], y - 4, rotated[1]);
    }

    private static int[] rotate(int x, int z, int turns) {
        return switch (turns) {
            case 0 -> new int[] {x, z};
            case 1 -> new int[] {-z, x};
            case 2 -> new int[] {-x, -z};
            default -> new int[] {z, -x};
        };
    }

    private static boolean near(BlockPos a, BlockPos b, int radius) {
        return Math.abs((long) a.getX() - b.getX()) <= radius
                && Math.abs((long) a.getY() - b.getY()) <= 8
                && Math.abs((long) a.getZ() - b.getZ()) <= radius;
    }

    private static boolean atWatchLine(ServerPlayer player, BlockPos root, Direction facing) {
        BlockPos feet = player.blockPosition();
        if (feet.getY() != root.getY() + 1
                || Math.abs((long) feet.getX() - root.getX()) > 2
                || Math.abs((long) feet.getZ() - root.getZ()) > 2) return false;
        ServerLevel level = player.level();
        if (level.getChunkSource().getChunkNow(root.getX() >> 4, root.getZ() >> 4) == null
                || !level.getBlockState(root).is(NetherChapterBlocks.BRIDGE_ROOT)
                || level.getBlockState(root).getValue(HorizontalDirectionalBlock.FACING) != facing
                || !level.getBlockState(root.above()).is(NetherChapterBlocks.BRIDGE_MIDPOINT)) {
            return false;
        }
        var floor = level.getBlockState(feet.below());
        return floor.isSolid() && floor.getFluidState().isEmpty()
                && level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir();
    }

    private static boolean has(ServerPlayer player, Item item, int count) {
        int found = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) found += stack.getCount();
        }
        return found >= count;
    }

    private static void take(ServerPlayer player, Item item, int count) {
        if (player.getAbilities().instabuild) return;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && count > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(item)) continue;
            int used = Math.min(count, stack.getCount());
            stack.shrink(used);
            count -= used;
        }
    }

    private static void tell(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text), true);
    }

    private record Site(BlockPos root, Direction facing) { }
    private record BoardStage(int stage, long lastTick) { }
    private record Evacuation(BlockPos root, Direction facing,
                              long startedTick, int midpointTicks, UUID watcher) { }
}
