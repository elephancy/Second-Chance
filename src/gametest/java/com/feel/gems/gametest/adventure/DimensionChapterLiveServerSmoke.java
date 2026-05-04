package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.AdventureItems;
import com.feel.gems.adventure.DimensionReturnState;
import com.feel.gems.adventure.endsource.EndSourceBlocks;
import com.feel.gems.adventure.nether.NetherChapterBlocks;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Disposable-save, opt-in network probe. Every staging teleport is logged as setup, never route proof. */
public final class DimensionChapterLiveServerSmoke implements ModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("gems-dimension-live-server");
    private static final BlockPos HOME = new BlockPos(1, 101, 2);
    private static final BlockPos PORTAL = new BlockPos(0, 101, 0);
    private static final BlockPos GATEWAY = new BlockPos(0, 201, 0);
    private final Site[] sites = new Site[3];
    private boolean resume;
    private boolean visualOnly;
    private boolean enabled;
    private boolean complete;
    private Phase phase = Phase.WAIT_JOIN;
    private int phaseTick;
    private String expectedAck;
    private String queuedInstruction;
    private String queuedAck;
    private Phase queuedPhase;
    private ServerPlayer player;
    private final boolean[] emberWalkVisited = new boolean[30];

    @Override
    public void onInitialize() {
        enabled = Boolean.getBoolean("gems.dimensionLive.server");
        if (!enabled) return;
        resume = Boolean.getBoolean("gems.dimensionLive.resume");
        visualOnly = Boolean.getBoolean("gems.dimensionLive.visualOnly");
        if (resume && visualOnly)
            throw new IllegalArgumentException("Visual-only and restart modes are exclusive");
        String[] parts = System.getProperty("gems.dimensionLive.roots", "").split(";");
        if (parts.length != 3) throw new IllegalArgumentException("Three paired-save roots required");
        for (int i = 0; i < parts.length; i++) sites[i] = Site.parse(parts[i]);
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(Commands.literal("dimlive")
                        .then(Commands.literal("ack")
                                .then(Commands.argument("token", StringArgumentType.word())
                                        .executes(context -> {
                                            String token = StringArgumentType.getString(context, "token");
                                            if (context.getSource().getPlayer() != player
                                                    || !token.equals(expectedAck)) return 0;
                                            expectedAck = null;
                                            acknowledged(context.getSource().getServer());
                                            return 1;
                                        })))));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (complete) return;
            player = handler.getPlayer();
            if (!player.getName().getString().equals("DimLiveA")) {
                fail("Unexpected player " + player.getName().getString());
                return;
            }
            LOG.info("DIMLIVE_JOIN phase={} player={} remote={}",
                    visualOnly ? "visual-only" : resume ? "restart" : "initial",
                    player.getUUID(), player.connection.getRemoteAddress());
            if (visualOnly) {
                stageEstablishing(server, 0);
            } else if (resume) {
                BlockPos saved = DimensionReturnState.get(server).departure(player.getUUID());
                if (!HOME.equals(saved) || player.level().dimension() != Level.OVERWORLD) {
                    fail("Restart lost personal departure or Overworld playerdata: " + saved);
                    return;
                }
                LOG.info("DIMLIVE_RESTART_PERSISTED departure={} dimension=overworld", saved);
                queueWhenReady(server, Phase.RESTART_SHOT,
                        "DIMLIVE|SCREENSHOT|restart-home", "restart-home");
            } else {
                ServerLevel world = server.overworld();
                stageHome(world);
                player.getInventory().add(new net.minecraft.world.item.ItemStack(AdventureItems.RUMOR_ATLAS));
                player.teleportTo(world, HOME.getX() + .5, HOME.getY(), HOME.getZ() + .5,
                        Set.of(), 180, 0, true);
                LOG.info("DIMLIVE_SETUP_TELEPORT stage=departure dimension=overworld pos={}", HOME);
                queueWhenReady(server, Phase.ATLAS, "DIMLIVE|ATLAS", "atlas");
            }
            phaseTick = server.getTickCount();
        });
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }

    private void acknowledged(MinecraftServer server) {
        if (player == null || complete) return;
        switch (phase) {
            case ATLAS -> {
                ServerLevel level = server.overworld();
                BlockPos feet = player.blockPosition();
                LOG.info("DIMLIVE_ATLAS_ACK_OBSERVED feet={} dimension={} held={} support={} feetBlock={} head={}",
                        feet, player.level().dimension().identifier(),
                        player.getMainHandItem().getItem(),
                        level.getBlockState(feet.below()).getBlock(),
                        level.getBlockState(feet).getBlock(),
                        level.getBlockState(feet.above()).getBlock());
                BlockPos saved = DimensionReturnState.get(server).departure(player.getUUID());
                if (!HOME.equals(saved)) { fail("Atlas did not record departure: " + saved); return; }
                LOG.info("DIMLIVE_ATLAS_PACKET_PASS departure={}", saved);
                stageNetherPortal(server.overworld());
                player.teleportTo(server.overworld(), PORTAL.getX() + .5, PORTAL.getY(),
                        PORTAL.getZ() + .5, Set.of(), 0, 0, true);
                LOG.info("DIMLIVE_SETUP_TELEPORT stage=test-portal-interior dimension=overworld pos={}", PORTAL);
                phase = Phase.NETHER_PORTAL;
            }
            case NETHER_ARRIVAL_SHOT -> stageEstablishing(server, 0);
            case ASH_ESTABLISHING_SHOT -> {
                if (visualOnly) stageEstablishing(server, 1);
                else stageControl(server, 0, false);
            }
            case ASH_CONTROL -> stageControl(server, 0, true);
            case EMBER_CONTROL -> stageControl(server, 1, true);
            case EMBER_ESTABLISHING_SHOT -> {
                // Do the Nether-to-End camera teleport on the following
                // server tick, after Brigadier finishes this client ACK.
                // Crossing dimensions inside a command callback can leave
                // its source bound to the player's old level.
                if (visualOnly) phase = Phase.VISUAL_END_STAGE;
                else stageControl(server, 1, false);
            }
            case END_ARRIVAL_SHOT -> stageEstablishing(server, 2);
            case END_ESTABLISHING_SHOT -> {
                if (visualOnly) done();
                else stageControl(server, 2, false);
            }
            case END_CONTROL -> stageControl(server, 2, true);
            case RESTART_SHOT -> done();
            default -> fail("Unexpected client acknowledgement in " + phase);
        }
        phaseTick = server.getTickCount();
    }

    private void tick(MinecraftServer server) {
        if (!enabled || complete || player == null) return;
        ServerPlayer current = server.getPlayerList().getPlayer(player.getUUID());
        if (current == null) return;
        player = current;
        int elapsed = server.getTickCount() - phaseTick;
        if (elapsed > 20 * 75) { fail("Stage timeout " + phase); return; }
        switch (phase) {
            case WAIT_READY -> {
                if (!player.connection.hasClientLoaded()) {
                    if (elapsed % 100 == 0)
                        LOG.info("DIMLIVE_WAIT_CLIENT_LOADED stage={} elapsedTicks={}",
                                queuedPhase, elapsed);
                    return;
                }
                phase = queuedPhase;
                expectedAck = queuedAck;
                player.sendSystemMessage(Component.literal(queuedInstruction));
                LOG.info("DIMLIVE_INSTRUCTION {} ack={}", queuedInstruction, queuedAck);
                phaseTick = server.getTickCount();
                queuedInstruction = null;
                queuedAck = null;
                queuedPhase = null;
            }
            case VISUAL_END_STAGE -> stageEstablishing(server, 2);
            case NETHER_PORTAL -> {
                if (player.level().dimension() != Level.NETHER) return;
                LOG.info("DIMLIVE_REAL_NETHER_PORTAL_PASS arrival={} pos={}",
                        player.level().dimension().identifier(), player.blockPosition());
                queueWhenReady(server, Phase.NETHER_ARRIVAL_SHOT,
                        "DIMLIVE|SCREENSHOT|nether-portal-arrival", "nether-portal-arrival");
            }
            case ASH_RETURN -> {
                if (player.level().dimension() != Level.OVERWORLD) return;
                if (!returned(server, "ash")) return;
                stageEmberWalk(server);
            }
            case EMBER_WALK -> tickEmberWalk(server);
            case EMBER_RETURN -> {
                if (player.level().dimension() != Level.OVERWORLD) return;
                if (!returned(server, "ember")) return;
                stageEndGateway(server);
            }
            case END_GATEWAY -> {
                BlockPos target = sites[2].at(23, 4, 0);
                if (player.level().dimension() != Level.END
                        || player.blockPosition().distManhattan(target) > 8) return;
                LOG.info("DIMLIVE_REAL_END_GATEWAY_PASS arrival={} target={}",
                        player.blockPosition(), target);
                queueWhenReady(server, Phase.END_ARRIVAL_SHOT,
                        "DIMLIVE|SCREENSHOT|end-gateway-arrival", "end-gateway-arrival");
            }
            case END_RETURN -> {
                if (player.level().dimension() != Level.OVERWORLD) return;
                if (!returned(server, "end")) return;
                done();
            }
            default -> { }
        }
    }

    private void stageEmberWalk(MinecraftServer server) {
        ServerLevel level = server.getLevel(Level.NETHER);
        Site site = sites[1];
        if (level == null) { fail("Nether unavailable for Ember walk"); return; }
        for (int z = 41; z <= 70; z++) {
            int feetY = z >= 55 ? 4 : 5;
            BlockPos feet = site.at(37, feetY, z);
            level.getChunk(feet.getX() >> 4, feet.getZ() >> 4);
            Block floor = level.getBlockState(feet.below()).getBlock();
            if (!safe(level, feet) || floor == Blocks.MAGMA_BLOCK
                    || (z >= 55 && (!naturalApproachFloor(1, floor)
                        || !passableWarpedGrowth(level.getBlockState(feet).getBlock())
                        || !passableWarpedGrowth(level.getBlockState(feet.above()).getBlock())))
                    || (z < 55 && (!level.getBlockState(feet).isAir()
                        || !level.getBlockState(feet.above()).isAir()))) {
                fail("Saved Ember walk changed or unsafe at " + feet + " localZ=" + z);
                return;
            }
        }
        BlockPos start = site.at(37, 4, 70);
        BlockPos goal = site.at(37, 5, 41);
        BlockPos step = site.at(37, 5, 54);
        float yaw = (float) Math.toDegrees(Math.atan2(start.getX() - goal.getX(),
                goal.getZ() - start.getZ()));
        player.teleportTo(level, start.getX() + .5, start.getY(), start.getZ() + .5,
                Set.of(), yaw, 0, true);
        LOG.info("DIMLIVE_SETUP_TELEPORT stage=ember-walk-start feet={} goal={} step={} "
                + "reason=disposable-route-entry-only", start, goal, step);
        String instruction = "DIMLIVE|WALK|ember-route|" + goal.getX() + "|"
                + goal.getY() + "|" + goal.getZ() + "|" + step.getX() + "|"
                + step.getY() + "|" + step.getZ();
        queueWhenReady(server, Phase.EMBER_WALK, instruction, null);
    }

    private static boolean passableWarpedGrowth(Block block) {
        return block == Blocks.AIR || block == Blocks.WARPED_ROOTS
                || block == Blocks.NETHER_SPROUTS;
    }

    private void tickEmberWalk(MinecraftServer server) {
        if (player.level().dimension() != Level.NETHER || !player.isAlive()) {
            fail("Player left safe Nether walk before Ember entry"); return;
        }
        BlockPos feet = player.blockPosition();
        Site site = sites[1];
        for (int z = 41; z <= 70; z++) {
            BlockPos expected = site.at(37, z >= 55 ? 4 : 5, z);
            if (feet.getX() == expected.getX() && feet.getZ() == expected.getZ()
                    && feet.getY() >= expected.getY()
                    && feet.getY() <= expected.getY() + 2) {
                emberWalkVisited[z - 41] = true;
                break;
            }
        }
        BlockPos goal = site.at(37, 5, 41);
        if (feet.getX() != goal.getX() || feet.getZ() != goal.getZ()
                || feet.getY() < goal.getY()) return;
        for (boolean visited : emberWalkVisited) {
            if (!visited) { fail("Client skipped a saved Ember walk column"); return; }
        }
        LOG.info("DIMLIVE_REAL_WALK_PASS site=ember columns=30 movement=client-key-up-and-jump "
                + "start={} goal={} no-route-teleport=true", site.at(37, 4, 70), goal);
        player.sendSystemMessage(Component.literal("DIMLIVE|WALK_DONE"));
        stageEstablishing(server, 1);
    }

    private void stageControl(MinecraftServer server, int index, boolean returnMark) {
        Site site = sites[index];
        ServerLevel level = server.getLevel(index == 2 ? Level.END : Level.NETHER);
        if (level == null) { fail("Dimension unavailable for " + site.name); return; }
        int x = index == 0 ? (returnMark ? 25 : 6) : index == 1
                ? (returnMark ? 39 : 37) : 23;
        int z = index == 0 ? (returnMark ? 31 : 14) : index == 1 ? 23
                : (returnMark ? 41 : 23);
        BlockPos control = site.at(x, 5, z);
        Block expected = index == 0 ? (returnMark ? NetherChapterBlocks.ANNEX_BOARD_RETURN
                : NetherChapterBlocks.ANNEX_REGISTER) : index == 1
                ? (returnMark ? NetherChapterBlocks.BRIDGE_EAST_SHELTER
                : NetherChapterBlocks.BRIDGE_START) : (returnMark
                ? EndSourceBlocks.RETURN_ARCH : EndSourceBlocks.CORE);
        level.getChunk(control.getX() >> 4, control.getZ() >> 4);
        if (!level.getBlockState(control).is(expected)) {
            fail("Paired natural control changed: " + site.name + " at " + control);
            return;
        }
        BlockPos feet = adjacent(level, control);
        if (feet == null) { fail("No safe authored control stand at " + control); return; }
        float yaw = (float) Math.toDegrees(Math.atan2(feet.getX() - control.getX(),
                control.getZ() - feet.getZ()));
        player.teleportTo(level, feet.getX() + .5, feet.getY(), feet.getZ() + .5,
                Set.of(), yaw, 16, true);
        LOG.info("DIMLIVE_SETUP_TELEPORT stage={}{} dimension={} feet={} control={}",
                site.name, returnMark ? "-return" : "-control", level.dimension().identifier(),
                feet, control);
        String stage = site.name + (returnMark ? "-return" : "-control");
        String instruction = "DIMLIVE|USE|" + stage + "|" + control.getX() + "|"
                + control.getY() + "|" + control.getZ() + "|" + returnMark;
        Phase next = index == 0 ? (returnMark ? Phase.ASH_RETURN : Phase.ASH_CONTROL)
                : index == 1 ? (returnMark ? Phase.EMBER_RETURN : Phase.EMBER_CONTROL)
                : (returnMark ? Phase.END_RETURN : Phase.END_CONTROL);
        queueWhenReady(server, next, instruction, returnMark ? null : stage);
    }

    private void stageEstablishing(MinecraftServer server, int index) {
        Site site = sites[index];
        ServerLevel level = server.getLevel(index == 2 ? Level.END : Level.NETHER);
        if (level == null) { fail("Dimension unavailable for establishing view " + site.name); return; }
        int centerX = index == 1 ? 39 : 23;
        boolean emberGallery = index == 1
                && ((level.getBlockState(site.at(39, 17, 69)).is(Blocks.WARPED_PLANKS)
                        && level.getBlockState(site.at(39, 21, 69)).is(Blocks.SHROOMLIGHT))
                    || (level.getBlockState(site.at(39, 20, 70)).is(Blocks.SHROOMLIGHT)
                        && level.getBlockState(site.at(33, 20, 70)).is(Blocks.SHROOMLIGHT)));
        // The Nether arcade owns Z47..54. A camera at Z54 stands on authored
        // floor and cannot show the silhouette. Look from genuinely natural
        // ground beyond the guarded approach, then reject blocked sightlines.
        int nearZ = index == 2 ? -12 : emberGallery ? 76 : 64;
        int farZ = index == 2 ? -24 : emberGallery ? 86 : index == 1 ? 78 : 82;
        int idealDistance = emberGallery ? 14 : index == 1 ? 12 : 18;
        int facadeZ = index == 2 ? 0 : emberGallery ? 70 : 54;
        int rayCount = emberGallery ? 15 : 3;
        BlockPos camera = null;
        int bestScore = Integer.MIN_VALUE;
        int bestVisible = 0;
        int bestLocalZ = 0;
        for (int z = nearZ; index == 2 ? z >= farZ : z <= farZ;
             z += index == 2 ? -1 : 1) {
            for (int offset = -10; offset <= 10; offset++) {
                for (int y = -4; y <= 13; y++) {
                    BlockPos candidate = site.at(centerX + offset, y, z);
                    level.getChunk(candidate.getX() >> 4, candidate.getZ() >> 4);
                    Block floor = level.getBlockState(candidate.below()).getBlock();
                    if (!naturalApproachFloor(index, floor)
                            || !safe(level, candidate)
                            || !visualAir(level, candidate)
                            || !visualAir(level, candidate.above())) continue;
                    int visible = facadeSightlines(level, site, index,
                            centerX + offset, y, z, facadeZ, emberGallery);
                    if (visible < (emberGallery ? 9 : 1)) continue;
                    int score = visible * 100
                            - Math.abs(Math.abs(z - facadeZ) - idealDistance) * 2
                            - Math.abs(offset) - Math.abs(y - 5);
                    if (score > bestScore) {
                        camera = candidate;
                        bestScore = score;
                        bestVisible = visible;
                        bestLocalZ = z;
                    }
                }
            }
        }
        if (camera == null) {
            LOG.warn("DIMLIVE_VISUAL_SKIP site={} reason=no-safe-visible-natural-camera", site.name);
            if (visualOnly) {
                fail("No safe visible natural camera for " + site.name);
                return;
            }
            stageControl(server, index, false);
            return;
        }
        int faceZ = index == 2 ? 7 : emberGallery ? 70 : index == 1 ? 40 : 43;
        BlockPos face = site.at(centerX, emberGallery ? 12 : 9, faceZ);
        float yaw = (float) Math.toDegrees(Math.atan2(camera.getX() - face.getX(),
                face.getZ() - camera.getZ()));
        player.teleportTo(level, camera.getX() + .5, camera.getY(), camera.getZ() + .5,
                Set.of(), yaw, -10, true);
        LOG.info("DIMLIVE_SETUP_CAMERA site={} feet={} facing={} safeNaturalFloor=true "
                        + "clearFacadeRays={}/{} distance={}",
                site.name, camera, face, bestVisible, rayCount,
                Math.abs(bestLocalZ - facadeZ));
        Phase next = index == 0 ? Phase.ASH_ESTABLISHING_SHOT
                : index == 1 ? Phase.EMBER_ESTABLISHING_SHOT : Phase.END_ESTABLISHING_SHOT;
        String stage = site.name + "-approach-clean";
        queueWhenReady(server, next, "DIMLIVE|SCREENSHOT|" + stage, stage);
    }

    private static boolean naturalApproachFloor(int index, Block floor) {
        if (index == 2) return floor == Blocks.END_STONE;
        return floor == Blocks.NETHERRACK || floor == Blocks.BASALT
                || floor == Blocks.SMOOTH_BASALT || floor == Blocks.BLACKSTONE
                || floor == Blocks.SOUL_SOIL || floor == Blocks.SOUL_SAND
                || floor == Blocks.GRAVEL || floor == Blocks.NETHER_GOLD_ORE
                || floor == Blocks.NETHER_QUARTZ_ORE || floor == Blocks.WARPED_NYLIUM
                || floor == Blocks.CRIMSON_NYLIUM;
    }

    private static boolean visualAir(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        Block block = state.getBlock();
        return state.isAir()
                || block == Blocks.WARPED_ROOTS || block == Blocks.NETHER_SPROUTS;
    }

    private static int facadeSightlines(ServerLevel level, Site site, int index,
                                        int cameraX, int cameraY, int cameraZ,
                                        int facadeZ, boolean emberGallery) {
        int centerX = index == 1 ? 39 : 23;
        if (emberGallery) {
            int visible = 0;
            for (int targetX : new int[] {34, 36, 39, 42, 44}) {
                for (int targetY : new int[] {8, 12, 16}) {
                    if (clearFacadeRay(level, site, cameraX, cameraY, cameraZ,
                            targetX, targetY, facadeZ)) visible++;
                }
            }
            return visible;
        }
        int[] targets = index == 1
                ? new int[] {centerX - 12, centerX, centerX + 6}
                : new int[] {centerX - 12, centerX, centerX + 12};
        int visible = 0;
        for (int targetX : targets) {
            if (clearFacadeRay(level, site, cameraX, cameraY, cameraZ,
                    targetX, 10, facadeZ)) visible++;
        }
        return visible;
    }

    private static boolean clearFacadeRay(ServerLevel level, Site site,
                                          int cameraX, int cameraY, int cameraZ,
                                          int targetX, int targetY, int facadeZ) {
        int steps = (int) Math.ceil(Math.hypot(targetX - cameraX,
                facadeZ - cameraZ) * 2);
        for (int i = 2; i < steps - 2; i++) {
            double fraction = (double) i / steps;
            int x = (int) Math.round(cameraX + (targetX - cameraX) * fraction);
            int y = (int) Math.round(cameraY + 1.62
                    + (targetY - cameraY - 1.62) * fraction);
            int z = (int) Math.round(cameraZ + (facadeZ - cameraZ) * fraction);
            if (!visualAir(level, site.at(x, y, z))) return false;
        }
        return true;
    }

    private void stageEndGateway(MinecraftServer server) {
        ServerLevel end = server.getLevel(Level.END);
        if (end == null) { fail("End unavailable"); return; }
        BlockPos target = sites[2].at(23, 4, 0);
        if (!safe(end, target)) { fail("End threshold no longer safe: " + target); return; }
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            end.setBlock(GATEWAY.offset(dx, -1, dz), Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
            for (int dy = 0; dy <= 3; dy++)
                end.setBlock(GATEWAY.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        end.setBlock(GATEWAY, Blocks.END_GATEWAY.defaultBlockState(), Block.UPDATE_ALL);
        if (!(end.getBlockEntity(GATEWAY) instanceof TheEndGatewayBlockEntity gateway)) {
            fail("Disposable gateway entity missing"); return;
        }
        gateway.setExitPosition(target, true);
        player.teleportTo(end, GATEWAY.getX() + .5, GATEWAY.getY(),
                GATEWAY.getZ() + .5, Set.of(), 0, 0, true);
        LOG.info("DIMLIVE_SETUP_GATEWAY source={} exactTarget={} staging=true", GATEWAY, target);
        phase = Phase.END_GATEWAY;
        phaseTick = server.getTickCount();
    }

    private boolean returned(MinecraftServer server, String site) {
        BlockPos saved = DimensionReturnState.get(server).departure(player.getUUID());
        if (!HOME.equals(saved) || player.blockPosition().distManhattan(HOME) > 12) {
            fail("Return landed away from saved departure after " + site + ": "
                    + player.blockPosition() + " saved=" + saved);
            return false;
        }
        LOG.info("DIMLIVE_REAL_RETURN_PASS site={} landing={} departure={}",
                site, player.blockPosition(), saved);
        return true;
    }

    private static void stageHome(ServerLevel level) {
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 4; z++) {
            level.setBlock(new BlockPos(x, 100, z), Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
            for (int y = 101; y <= 105; y++)
                level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static void stageNetherPortal(ServerLevel level) {
        // Build the complete frame first; portal cells may self-remove if a
        // neighboring frame column has not been placed yet.
        for (int x = -1; x <= 2; x++) for (int y = 100; y <= 104; y++)
            if (x == -1 || x == 2 || y == 100 || y == 104)
                level.setBlock(new BlockPos(x, y, 0), Blocks.OBSIDIAN.defaultBlockState(),
                        Block.UPDATE_ALL);
        for (int x = 0; x <= 1; x++) for (int y = 101; y <= 103; y++)
            level.setBlock(new BlockPos(x, y, 0), Blocks.NETHER_PORTAL.defaultBlockState()
                    .setValue(NetherPortalBlock.AXIS, Direction.Axis.X), Block.UPDATE_ALL);
        LOG.info("DIMLIVE_SETUP_PORTAL frame=obsidian interior=nether_portal staging=true");
    }

    private static BlockPos adjacent(ServerLevel level, BlockPos control) {
        for (Direction dir : new Direction[] {Direction.SOUTH, Direction.NORTH,
                Direction.EAST, Direction.WEST}) {
            BlockPos feet = control.relative(dir);
            if (safe(level, feet)) return feet;
        }
        return null;
    }

    private static boolean safe(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty() == false
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && level.getFluidState(feet).isEmpty()
                && level.getFluidState(feet.above()).isEmpty();
    }

    private void queueWhenReady(MinecraftServer server, Phase next,
                                String instruction, String ack) {
        queuedInstruction = instruction;
        queuedAck = ack;
        queuedPhase = next;
        expectedAck = null;
        phase = Phase.WAIT_READY;
        phaseTick = server.getTickCount();
        LOG.info("DIMLIVE_QUEUED stage={} clientLoaded={}", next,
                player.connection.hasClientLoaded());
    }

    private void done() {
        complete = true;
        LOG.info("DIMLIVE_SERVER_COMPLETE phase={} evidence={}",
                visualOnly ? "visual-only" : resume ? "restart" : "initial",
                visualOnly ? "test-only-camera-teleports" : "test-only-setup-plus-real-network-interactions");
        if (player != null) player.sendSystemMessage(Component.literal("DIMLIVE|COMPLETE"));
    }

    private void fail(String reason) {
        complete = true;
        LOG.error("DIMLIVE_SERVER_FAILED reason={}", reason);
        if (player != null) player.sendSystemMessage(Component.literal("DIMLIVE|FAILED|" + reason));
    }

    private enum Phase { WAIT_JOIN, WAIT_READY, VISUAL_END_STAGE,
        ATLAS, NETHER_PORTAL, NETHER_ARRIVAL_SHOT,
        ASH_ESTABLISHING_SHOT, ASH_CONTROL, ASH_RETURN,
        EMBER_WALK, EMBER_ESTABLISHING_SHOT, EMBER_CONTROL, EMBER_RETURN,
        END_GATEWAY, END_ARRIVAL_SHOT, END_ESTABLISHING_SHOT,
        END_CONTROL, END_RETURN, RESTART_SHOT }

    private record Site(String name, BlockPos root, int turn) {
        static Site parse(String value) {
            String[] fields = value.split(",");
            if (fields.length != 5 || !Set.of("ash_annex", "ember_bridge", "end_source")
                    .contains(fields[0])) throw new IllegalArgumentException("Invalid site root: " + value);
            int turn = Integer.parseInt(fields[4]);
            if (turn < 0 || turn > 3) throw new IllegalArgumentException("Invalid rotation");
            return new Site(fields[0], new BlockPos(Integer.parseInt(fields[1]),
                    Integer.parseInt(fields[2]), Integer.parseInt(fields[3])), turn);
        }

        BlockPos at(int x, int y, int z) {
            int dx = x - 23, dz = z - 23;
            int rx = switch (turn) { case 0 -> dx; case 1 -> -dz; case 2 -> -dx; default -> dz; };
            int rz = switch (turn) { case 0 -> dz; case 1 -> dx; case 2 -> -dz; default -> -dx; };
            return root.offset(rx, y - 4, rz);
        }
    }
}
