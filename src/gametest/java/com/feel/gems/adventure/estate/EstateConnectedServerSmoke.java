package com.feel.gems.adventure.estate;

import com.feel.gems.adventure.exploration.ExplorationProgress;
import com.feel.gems.adventure.exploration.ExplorationDeliveryAccount;
import com.feel.gems.adventure.exploration.ExplorationState;
import com.feel.gems.state.GemsPersistentDataHolder;
import com.feel.gems.util.GemsTeleport;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

/** Disposable connected-player proof; setup loads one route corridor at a time. */
public final class EstateConnectedServerSmoke implements ModInitializer {
    private static final ResourceKey<Structure> ESTATE = ResourceKey.create(
            Registries.STRUCTURE, Identifier.fromNamespaceAndPath("gems", "four_roads_estate_v1"));
    private static final int CORRIDOR_CAP = 112;
    private static final int IN_FLIGHT_CAP = 8;
    private enum Phase { ROOT, LOAD, PRE_FOYER, GATE, BYPASS, REENTRY, UNLOAD,
                         WALK, FOYER, VIEW_CENTRAL, RETURN, RELEASE, COMPLETE }

    private final boolean enabled = Boolean.getBoolean("gems.estateConnected.server");
    private final ChunkPos recorded = new ChunkPos(
            Integer.getInteger("gems.estateConnected.chunkX", Integer.MIN_VALUE),
            Integer.getInteger("gems.estateConnected.chunkZ", Integer.MIN_VALUE));
    private final String seed = System.getProperty("gems.estateConnected.seed", "FourRoadsEstate-Common-1");
    private final String requestedWing = System.getProperty("gems.estateConnected.wing", "N");
    private final boolean allRoads = requestedWing.equals("all");
    private final boolean restartOnly = Boolean.getBoolean("gems.estateConnected.restart");
    private char wing = allRoads ? 'N' : requestedWing.charAt(0);
    private int wingIndex;
    private final Set<Long> forced = new HashSet<>();
    private final Set<Long> inFlight = new HashSet<>();
    private final List<Long> tickNanos = new ArrayList<>();
    private Phase phase = Phase.ROOT;
    private ServerPlayer actor;
    private EstateRouteSite.Site site;
    private EstateRouteSite.Gate gate;
    private BlockPos foyer;
    private BlockPos returnStone;
    private BlockPos centralView;
    private BlockPos centralLook;
    private BlockPos gateViewStand;
    private List<ChunkPos> corridor = List.of();
    private List<BlockPos> route = List.of();
    private int loading;
    private int started;
    private int phaseAt;
    private int useSent;
    private int foyerUseObservedAt;
    private long tickBegan;
    private String failure;
    private int pearlsBefore;

    @Override
    public void onInitialize() {
        if (!enabled) return;
        if ("NESW".indexOf(wing) < 0 || (restartOnly && !allRoads)
                || recorded.x() == Integer.MIN_VALUE
                || recorded.z() == Integer.MIN_VALUE)
            throw new IllegalArgumentException("Estate fixture requires a wing and recorded chunk");
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(Commands.literal("estateqa")
                        .then(Commands.literal("ack").executes(context ->
                                acknowledge(context.getSource().getServer(), context.getSource().getPlayer())))
                        .then(Commands.literal("gateview").executes(context ->
                                gateView(context.getSource().getServer(), context.getSource().getPlayer())))
                        .then(Commands.literal("walked").executes(context ->
                                walked(context.getSource().getServer(), context.getSource().getPlayer())))));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!handler.getPlayer().getName().getString().equals("EstateQA")) {
                fail("UNEXPECTED_PLAYER");
                return;
            }
            actor = handler.getPlayer();
            System.out.println("ESTATE_CONNECTED_JOIN dedicated=true player=EstateQA");
        });
        EstateRuntime.setFoyerUseObserver((player, pos) -> {
            if (phase == Phase.FOYER && player == actor && pos.equals(foyer)
                    && useSent != 0)
                foyerUseObservedAt = player.level().getServer().getTickCount();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                EstateRuntime.setFoyerUseObserver(null));
        ServerTickEvents.START_SERVER_TICK.register(server -> tickBegan = System.nanoTime());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                tick(server);
            } finally {
                tickNanos.add(System.nanoTime() - tickBegan);
            }
        });
    }

    private void tick(MinecraftServer server) {
        if (phase == Phase.COMPLETE) return;
        if (started == 0) started = server.getTickCount();
        if (server.getTickCount() - started > 20 * (allRoads ? 3600 : 900))
            fail("OVERALL_TIMEOUT");
        if (failure != null) phase = Phase.RELEASE;
        ServerLevel level = server.overworld();
        try {
            switch (phase) {
                case ROOT -> root(server, level);
                case LOAD -> load(server, level);
                case PRE_FOYER, GATE, BYPASS, REENTRY, FOYER, RETURN -> use(server, level);
                case VIEW_CENTRAL -> view(server, level);
                case UNLOAD -> unload(server, level);
                case WALK -> {
                    if (actor == null || !actor.isAlive()) fail("PLAYER_LOST_DURING_ROUTE");
                    if (server.getTickCount() - phaseAt > 20 * 420) fail("WALK_TIMEOUT");
                }
                case RELEASE -> release(level);
                case COMPLETE -> { }
            }
        } catch (RuntimeException error) {
            error.printStackTrace();
            fail("EXCEPTION_" + error.getClass().getSimpleName());
        }
    }

    private void root(MinecraftServer server, ServerLevel level) {
        if (level.getSeed() != (long) seed.hashCode()) {
            fail("SEED_MISMATCH"); return;
        }
        if (actor == null || !actor.connection.hasClientLoaded()) return;
        force(level, recorded);
        LevelChunk root = level.getChunkSource().getChunkNow(recorded.x(), recorded.z());
        if (root == null) return;
        Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getOrThrow(ESTATE).value();
        StructureStart start = root.getStartForStructure(structure);
        if (start == null || !start.isValid()
                || !FourRoadsEstateStructure.completeGrid(start.getPieces())) {
            fail("RECORDED_START_NOT_SAVED"); return;
        }
        if (!v5Pieces(start)) { fail("EXPECTED_V5_PIECES_MISSING"); return; }
        BoundingBox bounds = start.getBoundingBox();
        site = EstateRouteSite.readLoaded(level, bounds.getCenter());
        if (site == null) { fail("FRESH_ROUTE_DESCRIPTOR_MISSING"); return; }
        if (restartOnly) {
            verifyRestart(server, level);
            return;
        }
        gate = site.gate(wing);
        foyer = site.foyer();
        if (gate == null || site.bounds().minX() != bounds.minX()
                || site.bounds().minZ() != bounds.minZ()) {
            fail("SAVED_SITE_GEOMETRY_CHANGED"); return;
        }
        Set<ChunkPos> area = new HashSet<>();
        int module = firstModule();
        for (int i = 0; i < 5; i++) {
            BoundingBox box = site.module(module);
            addChunks(area, box.minX() - 2, box.maxX() + 2,
                    box.minZ() - 2, box.maxZ() + 2);
            module = EstateModuleGraph.parent(module);
        }
        BlockPos outside = gate.at(gate.distance() + 8, 0);
        addChunks(area, Math.min(gate.marker().getX(), outside.getX()) - 2,
                Math.max(gate.marker().getX(), outside.getX()) + 2,
                Math.min(gate.marker().getZ(), outside.getZ()) - 2,
                Math.max(gate.marker().getZ(), outside.getZ()) + 2);
        addChunks(area, foyer.getX() - 8, foyer.getX() + 8,
                foyer.getZ() - 8, foyer.getZ() + 8);
        if (area.size() > CORRIDOR_CAP) { fail("CORRIDOR_CHUNK_CAP"); return; }
        corridor = area.stream().sorted(Comparator
                .comparingInt((ChunkPos pos) -> Math.abs(pos.x() - recorded.x())
                        + Math.abs(pos.z() - recorded.z()))
                .thenComparingInt(ChunkPos::x).thenComparingInt(ChunkPos::z)).toList();
        phase = Phase.LOAD;
        phaseAt = server.getTickCount();
        pearlsBefore = countPearls(actor);
        actor.sendSystemMessage(Component.literal("ESTATEQA|WING|" + wing
                + "|" + wingIndex + "|" + (allRoads ? 4 : 1)));
        System.out.println("ESTATE_CONNECTED_FRESH_SITE descriptor=true chunk="
                + recorded.x() + "," + recorded.z() + " wing=" + wing
                + " version=v5 corridorChunks=" + corridor.size());
    }

    private int firstModule() {
        return switch (wing) {
            case 'N' -> 2; case 'E' -> 14; case 'S' -> 22; case 'W' -> 10;
            default -> throw new IllegalStateException("Invalid Estate wing");
        };
    }

    private static boolean v5Pieces(StructureStart start) {
        EstateModuleGraph.Module[] modules = EstateModuleGraph.modules(start.getPieces());
        if (modules == null) return false;
        for (int id = 0; id < modules.length; id++) {
            if (!(modules[id].piece().getElement() instanceof SinglePoolElement single)) return false;
            String expected = switch (id) {
                case 12 -> "four_roads_estate_v5_2_2";
                case 7, 11, 13, 17 -> "four_roads_estate_v4_" + (id / 5) + "_" + (id % 5);
                default -> "four_roads_estate_v1_" + (id / 5) + "_" + (id % 5);
            };
            if (!single.getTemplateLocation().getNamespace().equals("gems")
                    || !single.getTemplateLocation().getPath().equals(expected)) return false;
        }
        return true;
    }

    private static int countPearls(ServerPlayer player) {
        return countItems(player, Items.ENDER_PEARL);
    }

    private static int countItems(ServerPlayer player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static Item cacheItem(char road) {
        return switch (road) {
            case 'N' -> Items.MOSS_BLOCK;
            case 'E' -> Items.AMETHYST_SHARD;
            case 'S' -> Items.PRISMARINE_SHARD;
            case 'W' -> Items.COPPER_INGOT;
            default -> throw new IllegalArgumentException("Unknown road");
        };
    }

    private static String wingName(char road) {
        return switch (road) {
            case 'N' -> "Garden Road";
            case 'E' -> "Mirror Road";
            case 'S' -> "Sluice Road";
            case 'W' -> "Clock Road";
            default -> throw new IllegalArgumentException("Unknown road");
        };
    }

    private static boolean expectedRoadSupplies(ServerPlayer player, char road,
                                                boolean mastery) {
        return countItems(player, cacheItem(road)) == 12
                && countItems(player, Items.SPYGLASS) == (mastery ? 1 : 0);
    }

    private void verifyRestart(MinecraftServer server, ServerLevel level) {
        foyer = site.foyer();
        ExplorationProgress progress = ExplorationState.get(server).model();
        ExplorationDeliveryAccount receipts = ExplorationDeliveryAccount.of(actor);
        String first = ExplorationProgress.activityId("estate", foyer.getX(), foyer.getZ());
        String mastery = ExplorationProgress.activityId("estate_mastery",
                foyer.getX(), foyer.getZ());
        if (!data().getBooleanOr("won", false) || !progress.earned(actor.getUUID(), first)
                || !receipts.has(first) || countPearls(actor) != 4
                || progress.estateRoadCount(actor.getUUID(), foyer.getX(), foyer.getZ()) != 4
                || !progress.earned(actor.getUUID(), mastery) || !receipts.has(mastery)
                || !progress.hasEstateMastery(actor.getUUID())
                || countItems(actor, Items.SPYGLASS) != 1) {
            fail("RESTART_MASTERY_OR_FIRST_CLEAR_LOST");
            return;
        }
        for (char road : "NESW".toCharArray()) {
            String id = ExplorationProgress.estateRoadId(road, foyer.getX(), foyer.getZ());
            if (!progress.earned(actor.getUUID(), id) || !receipts.has(id)
                    || countItems(actor, cacheItem(road)) != 12) {
                fail("RESTART_ROAD_OR_CACHE_LOST_" + road);
                return;
            }
        }
        System.out.println("ESTATE_CONNECTED_RESTART savedSite=true roads=4/4"
                + " firstClear=1 distinctCaches=4 masterySpyglass=1 receipts=6"
                + " playerUuid=" + actor.getUUID());
        advance(server, Phase.RELEASE);
    }

    private static void addChunks(Set<ChunkPos> out, int minX, int maxX, int minZ, int maxZ) {
        for (int x = minX >> 4; x <= maxX >> 4; x++)
            for (int z = minZ >> 4; z <= maxZ >> 4; z++) out.add(new ChunkPos(x, z));
    }

    private void load(MinecraftServer server, ServerLevel level) {
        inFlight.removeIf(packed -> level.getChunkSource().getChunkNow(
                ChunkPos.getX(packed), ChunkPos.getZ(packed)) != null);
        int issued = 0;
        while (loading < corridor.size() && issued < 2 && inFlight.size() < IN_FLIGHT_CAP) {
            ChunkPos chunk = corridor.get(loading++);
            force(level, chunk);
            if (level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) == null)
                inFlight.add(chunk.pack());
            issued++;
        }
        if (loading < corridor.size() || !inFlight.isEmpty()) {
            if (server.getTickCount() - phaseAt > 20 * 240) fail("CORRIDOR_LOAD_TIMEOUT");
            return;
        }
        if (EstateSiteIntegrity.inspectEntryLoaded(level, site, gate.marker(), wing)
                    != EstateSiteIntegrity.Result.READY
                || EstateSiteIntegrity.inspectFoyerLoaded(level, site, foyer)
                    != EstateSiteIntegrity.Result.READY) {
            fail("LOCAL_CONTROLS_NOT_READY"); return;
        }
        for (int x = foyer.getX() - 8; x <= foyer.getX() + 8; x++)
            for (int z = foyer.getZ() - 8; z <= foyer.getZ() + 8; z++) {
                BlockPos at = new BlockPos(x, foyer.getY(), z);
                if (level.getBlockState(at).getBlock() == EstateBlocks.RETURN)
                    returnStone = at;
            }
        if (returnStone == null) { fail("RETURN_MARKER_MISSING"); return; }
        route = EstateConnectedPath.forWing(level, site, wing);
        if (route.isEmpty()) { fail("WING_ROUTE_NOT_WALKABLE"); return; }
        if (!checkCenterLight(level)) return;
        int lastRoad = firstModule();
        for (int i = 0; i < 3; i++) lastRoad = EstateModuleGraph.parent(lastRoad);
        EstateRouteSite.Seam entrance = site.seam(lastRoad);
        if (entrance == null || entrance.parent() != 12
                || !route.contains(entrance.from())) {
            fail("CHATEAU_VIEW_NOT_ON_ROUTE"); return;
        }
        centralLook = foyer.above(37);
        centralView = roofView(level, site.module(lastRoad), site.module(12),
                entrance.from(), gate.outward(), centralLook);
        if (centralView == null) {
            fail("CHATEAU_ROOF_VIEW_UNAVAILABLE"); return;
        }
        System.out.println("ESTATE_CONNECTED_CORRIDOR wing=" + wing + " chunks=" + corridor.size()
                + " steps=" + (route.size() - 1) + " seams=4 setupOnly=true");
        var overworldClock = level.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK)
                .getOrThrow(WorldClocks.OVERWORLD);
        level.clockManager().setTotalTicks(overworldClock,
                allRoads && wingIndex >= 2 ? 13000L : 1000L);
        System.out.println("ESTATE_CONNECTED_TIME wing=" + wing + " dayTime="
                + level.clockManager().getInstance(overworldClock).totalTicks()
                + " survival=" + !actor.isCreative());
        advance(server, wingIndex == 0 ? Phase.PRE_FOYER : Phase.GATE);
    }

    private boolean checkCenterLight(ServerLevel level) {
        BoundingBox center = site.module(12);
        int floorY = center.minY() + 4;
        int returnX = returnStone.getX() - foyer.getX();
        int returnZ = returnStone.getZ() - foyer.getZ();
        if (Math.abs(returnX) + Math.abs(returnZ) != 5) {
            fail("RETURN_ORIENTATION_UNEXPECTED");
            return false;
        }
        // Authored +z points from the foyer to the return stone. The saved
        // mansion can face any cardinal direction within its square box.
        int zStepX = returnX / 5;
        int zStepZ = returnZ / 5;
        int xStepX = zStepZ;
        int xStepZ = -zStepX;
        int minCenter = 15;
        int minCore = 15;
        int darkCenter = 0;
        int centerCells = 0;
        BlockPos minCenterAt = null;
        BlockPos minCoreAt = null;
        for (int x = 0; x < 47; x++) {
            for (int z = 0; z < 47; z++) {
                if (x < 18 || x > 23) {
                    if (z < 18 || z > 23) continue;
                }
                BlockPos floor = new BlockPos(
                        foyer.getX() + (x - 23) * xStepX + (z - 23) * zStepX,
                        floorY,
                        foyer.getZ() + (x - 23) * xStepZ + (z - 23) * zStepZ);
                if (!EstateConnectedPath.walkable(level, floor)) continue;
                BlockPos feet = floor.above();
                int light = level.getBrightness(LightLayer.BLOCK, feet);
                if (light < minCenter) {
                    minCenter = light;
                    minCenterAt = feet;
                }
                if (x >= 18 && x <= 28 && z >= 18 && z <= 28 && light < minCore) {
                    minCore = light;
                    minCoreAt = feet;
                }
                if (light == 0) darkCenter++;
                centerCells++;
            }
        }
        int minRoute = 15;
        int darkRoute = 0;
        for (BlockPos floor : route) {
            int light = level.getBrightness(LightLayer.BLOCK, floor.above());
            minRoute = Math.min(minRoute, light);
            if (light == 0) darkRoute++;
        }
        int foyerLight = level.getBrightness(LightLayer.BLOCK, foyer.above());
        System.out.println("ESTATE_CONNECTED_LIGHT wing=" + wing
                + " foyer=" + foyerLight + " coreMin=" + minCore
                + " coreMinAt=" + minCoreAt
                + " centerMin=" + minCenter + " centerMinAt=" + minCenterAt
                + " centerMinFloor=" + (minCenterAt == null ? "none"
                    : level.getBlockState(minCenterAt.below()))
                + " centerDark=" + darkCenter
                + "/" + centerCells + " routeMin=" + minRoute
                + " routeDark=" + darkRoute + "/" + route.size()
                + " mobSpawnDark=blockLight0");
        if (centerCells == 0 || foyerLight < 8 || minCore == 0 || minCenter == 0) {
            fail("CENTER_APPROACH_UNLIT");
            return false;
        }
        return true;
    }

    /** Select a nearby existing roof, including the side strips of axial galleries. */
    private static BlockPos roofView(ServerLevel level, BoundingBox wing,
                                     BoundingBox center, BlockPos seamRoad,
                                     net.minecraft.core.Direction outward,
                                     BlockPos look) {
        // The four axial galleries have different roof heights and the v5
        // chateau rises farther above them. Search their actual saved bounds
        // instead of assuming one narrow height and camera distance band.
        int highest = Math.min(wing.maxY(), seamRoad.getY() + 31);
        int lowest = Math.max(wing.minY() + 12, seamRoad.getY() + 12);
        int inWing = 0, inRange = 0, safeStance = 0;
        for (int distance = 1; distance <= wing.getXSpan(); distance++) {
            for (int lateral = 0; lateral <= wing.getZSpan() / 2; lateral++) {
                for (int side : lateral == 0 ? new int[] {0} : new int[] {-lateral, lateral}) {
                    for (int y = highest; y >= lowest; y--) {
                        BlockPos floor = seamRoad.relative(outward, distance)
                                .relative(outward.getClockWise(), side).atY(y);
                        if (floor.getX() < wing.minX() || floor.getX() > wing.maxX()
                                || floor.getZ() < wing.minZ() || floor.getZ() > wing.maxZ()) continue;
                        inWing++;
                        // The outer gallery roof can sit roughly 60 blocks
                        // from the chateau focal point in a saved v5 estate.
                        if (floor.distSqr(look) < 24 * 24
                                || floor.distSqr(look) > 72 * 72) continue;
                        inRange++;
                        if (!clearRoofStance(level, floor)) continue;
                        safeStance++;
                        if (!openSightline(level, floor, look, center)) continue;
                        return floor;
                    }
                }
            }
        }
        System.out.println("ESTATE_CONNECTED_ROOF_VIEW_MISS wing=" + wing
                + " center=" + center + " seam=" + seamRoad
                + " inWing=" + inWing + " inRange=" + inRange
                + " safeStance=" + safeStance);
        return null;
    }

    private static boolean clearRoofStance(ServerLevel level, BlockPos floor) {
        if (!level.getWorldBorder().isWithinBounds(floor)
                || level.getChunkSource().getChunkNow(floor.getX() >> 4,
                        floor.getZ() >> 4) == null
                || level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()
                || !level.getFluidState(floor).isEmpty()) return false;
        for (int height = 1; height <= 2; height++) {
            BlockPos head = floor.above(height);
            if (!level.getBlockState(head).getCollisionShape(level, head).isEmpty()
                    || !level.getFluidState(head).isEmpty()) return false;
        }
        return true;
    }

    private static boolean openSightline(ServerLevel level, BlockPos floor,
                                         BlockPos look, BoundingBox center) {
        Vec3 eye = new Vec3(floor.getX() + 0.5, floor.getY() + 2.62,
                floor.getZ() + 0.5);
        Vec3 delta = Vec3.atCenterOf(look).subtract(eye);
        int samples = (int) Math.ceil(delta.length() * 3);
        for (int i = 1; i < samples; i++) {
            Vec3 point = eye.add(delta.scale(i / (double) samples));
            BlockPos pos = BlockPos.containing(point);
            if (pos.getX() >= center.minX() && pos.getX() <= center.maxX()
                    && pos.getZ() >= center.minZ() && pos.getZ() <= center.maxZ())
                return true;
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    || !level.getFluidState(pos).isEmpty()) return false;
        }
        return false;
    }

    private static BlockPos exteriorGateView(ServerLevel level, EstateRouteSite.Gate gate,
                                             BoundingBox body) {
        BlockPos column = gate.at(gate.distance() + 8, 0);
        // The graded outer path can sit four or more blocks below its gate.
        for (int y = gate.marker().getY() + 3; y >= gate.marker().getY() - 8; y--) {
            BlockPos floor = column.atY(y);
            if (!clearRoofStance(level, floor)) continue;
            Vec3 eye = Vec3.atBottomCenterOf(floor.above()).add(0, 1.62, 0);
            Vec3 target = Vec3.atCenterOf(gate.marker().above(3));
            Vec3 delta = target.subtract(eye);
            int samples = (int) Math.ceil(delta.length() * 3);
            boolean open = false;
            for (int i = 1; i < samples; i++) {
                BlockPos at = BlockPos.containing(eye.add(delta.scale(i / (double) samples)));
                if (body.isInside(at)) { open = true; break; }
                if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()
                        || !level.getFluidState(at).isEmpty()) break;
            }
            if (open) return floor;
        }
        return null;
    }

    private int gateView(MinecraftServer server, ServerPlayer player) {
        if (phase != Phase.RETURN || player != actor || useSent == 0
                || gateViewStand != null || !data().getBooleanOr("won", false)
                || player.blockPosition().distSqr(gate.marker().above()) > 64)
            return fail("GATE_VIEW_BEFORE_SAFE_RETURN");
        ServerLevel level = server.overworld();
        BlockPos stand = exteriorGateView(level, gate, site.bounds());
        if (stand == null || !GemsTeleport.teleport(player, level,
                stand.getX() + 0.5, stand.getY() + 1, stand.getZ() + 0.5, 0, 0))
            return fail("GATE_VIEW_STANCE_UNAVAILABLE");
        gateViewStand = stand;
        BlockPos look = gate.marker().above(3);
        player.sendSystemMessage(Component.literal("ESTATEQA|GATEVIEW|"
                + stand.getX() + "|" + stand.getY() + "|" + stand.getZ() + "|"
                + look.getX() + "|" + look.getY() + "|" + look.getZ()));
        System.out.println("ESTATE_CONNECTED_GATE_VIEW_SETUP wing=" + wing
                + " point=" + stand + " target=" + look
                + " returnVerified=true naturalApproach=true terrainEdits=false");
        return 1;
    }

    private void use(MinecraftServer server, ServerLevel level) {
        if (actor == null || !actor.connection.hasClientLoaded()) return;
        if (useSent == 0) {
            if (phase == Phase.FOYER && wingIndex == 0 && data().getBooleanOr("won", false)) {
                fail("FOYER_CLAIMED_BEFORE_USE_PACKET"); return;
            }
            BlockPos target = switch (phase) {
                case PRE_FOYER, BYPASS, FOYER -> foyer;
                case GATE, REENTRY -> gate.marker();
                case RETURN -> returnStone;
                default -> throw new IllegalStateException();
            };
            // A direct teleport onto a contact block consumes its first-entry
            // message through stepOn before the client can send the use packet.
            BlockPos stand = phase == Phase.PRE_FOYER ? route.getLast()
                    : phase == Phase.GATE || phase == Phase.REENTRY
                    ? route.get(1) : target;
            if (phase != Phase.FOYER
                    && !GemsTeleport.teleport(actor, level, stand.getX() + 0.5,
                            stand.getY() + 1, stand.getZ() + 0.5, 0, 0)) {
                fail("SETUP_TELEPORT_FAILED_" + phase); return;
            }
            String expected = switch (phase) {
                case PRE_FOYER -> "Use one of the four outer road gates first";
                case GATE -> "You found the " + switch (wing) {
                    case 'N' -> "Garden Road"; case 'E' -> "Mirror Road";
                    case 'S' -> "Sluice Road"; case 'W' -> "Clock Road";
                    default -> throw new IllegalStateException();
                };
                case BYPASS -> "Follow your chosen road through each join";
                case REENTRY -> "Follow this road to the mansion";
                case FOYER -> wingIndex == 0 ? "You reached the house at the middle"
                        : wingName(wing) + " surveyed:";
                case RETURN -> "Outside again";
                default -> throw new IllegalStateException();
            };
            actor.sendSystemMessage(Component.literal("ESTATEQA|USE|" + phase
                    + "|" + target.getX() + "|" + target.getY() + "|" + target.getZ()
                    + "|" + expected));
            useSent = server.getTickCount();
            System.out.println("ESTATE_CONNECTED_SETUP_ONLY control=" + phase);
        } else if (server.getTickCount() - useSent > 20 * (phase == Phase.RETURN ? 55 : 45)) {
            fail("CONTROL_TIMEOUT_" + phase);
        }
    }

    private void view(MinecraftServer server, ServerLevel level) {
        if (actor == null || !actor.connection.hasClientLoaded()) return;
        if (useSent == 0) {
            if (!data().getBooleanOr("won", false)
                    || !GemsTeleport.teleport(actor, level, centralView.getX() + 0.5,
                            centralView.getY() + 1, centralView.getZ() + 0.5, 0, 0)) {
                fail("CHATEAU_VIEW_STANCE_UNAVAILABLE"); return;
            }
            actor.sendSystemMessage(Component.literal("ESTATEQA|VIEW|CHATEAU|"
                    + centralView.getX() + "|" + centralView.getY() + "|"
                    + centralView.getZ() + "|" + centralLook.getX() + "|"
                    + centralLook.getY() + "|" + centralLook.getZ()));
            useSent = server.getTickCount();
            System.out.println("ESTATE_CONNECTED_VIEW_SETUP wing=" + wing
                    + " point=" + centralView + " target=" + centralLook
                    + " teleportAfterClaim=true terrainEdits=false");
        } else if (server.getTickCount() - useSent > 20 * 30) {
            fail("CHATEAU_VIEW_TIMEOUT");
        }
    }

    private int acknowledge(MinecraftServer server, ServerPlayer player) {
        if (player != actor || useSent == 0) return 0;
        boolean won = data().getBooleanOr("won", false);
        switch (phase) {
            case PRE_FOYER -> {
                if (won || data().contains("entry")) return fail("PRE_GATE_BYPASS");
                advance(server, Phase.GATE);
            }
            case GATE -> {
                if (data().getLongOr("entry", 0) != gate.marker().asLong()
                        || won != (wingIndex > 0))
                    return fail("GATE_DID_NOT_REGISTER");
                advance(server, Phase.BYPASS);
            }
            case BYPASS -> {
                if (won != (wingIndex > 0) || EstateRouteReadiness.completed(player, site))
                    return fail("TELEPORT_BYPASS");
                advance(server, Phase.REENTRY);
            }
            case REENTRY -> {
                if (data().getLongOr("entry", 0) != gate.marker().asLong()
                        || won != (wingIndex > 0))
                    return fail("REENTRY_DID_NOT_REGISTER");
                advance(server, Phase.UNLOAD);
            }
            case FOYER -> {
                if (foyerUseObservedAt < useSent)
                    return fail("FOYER_USE_CALLBACK_NOT_OBSERVED");
                String id = ExplorationProgress.activityId("estate", foyer.getX(), foyer.getZ());
                String road = ExplorationProgress.estateRoadId(wing, foyer.getX(), foyer.getZ());
                ExplorationProgress progress = ExplorationState.get(server).model();
                if (!won || !progress.earned(player.getUUID(), id)
                        || !ExplorationDeliveryAccount.of(player).has(id)
                        || countPearls(player) != pearlsBefore + (wingIndex == 0 ? 4 : 0))
                    return fail("FOYER_PROGRESS_MISSING");
                if (!progress.earned(player.getUUID(), road)
                        || !ExplorationDeliveryAccount.of(player).has(road)
                        || progress.estateRoadCount(player.getUUID(), foyer.getX(), foyer.getZ()) != wingIndex + 1
                        || progress.hasEstateMastery(player.getUUID()) != (allRoads && wingIndex == 3)
                        || !expectedRoadSupplies(player, wing, wingIndex == 3 && allRoads))
                    return fail("ROAD_SURVEY_REWARD_MISSING_OR_CROSSED");
                for (int index = 0; index <= wingIndex; index++) {
                    char previousWing = allRoads ? "NESW".charAt(index) : wing;
                    String previous = ExplorationProgress.estateRoadId(previousWing,
                            foyer.getX(), foyer.getZ());
                    if (!progress.earned(player.getUUID(), previous)
                            || !ExplorationDeliveryAccount.of(player).has(previous))
                        return fail("PREVIOUS_ROAD_PROGRESS_LOST");
                }
                System.out.println("ESTATE_CONNECTED_PROGRESS won=true sharedDiscovery="
                        + progress.discovered(id) + " surveyedWing=" + wing
                        + " roads=" + (wingIndex + 1) + "/4 uniqueCaches=true");
                advance(server, Phase.VIEW_CENTRAL);
            }
            case VIEW_CENTRAL -> {
                if (!won || player.blockPosition().distSqr(centralView.above()) > 16)
                    return fail("CHATEAU_VIEW_ACK_INVALID");
                advance(server, Phase.RETURN);
            }
            case RETURN -> {
                if (gateViewStand == null
                        || player.blockPosition().distSqr(gateViewStand.above()) > 16
                        || !data().getBooleanOr("won", false))
                    return fail("RETURN_NOT_SAFE_OR_PROGRESS_LOST");
                System.out.println("ESTATE_CONNECTED_RETURN wing=" + wing
                        + " outside=true progressRetained=true");
                if (allRoads && wingIndex < 3) {
                    wing = "NESW".charAt(++wingIndex);
                    loading = 0;
                    inFlight.clear();
                    advance(server, Phase.ROOT);
                } else {
                    advance(server, Phase.RELEASE);
                }
            }
            default -> { return fail("UNEXPECTED_ACK_" + phase); }
        }
        return 1;
    }

    private int walked(MinecraftServer server, ServerPlayer player) {
        if (phase != Phase.WALK || player != actor
                || EstateRouteReadiness.completedWing(player, site) != wing
                || player.blockPosition().distSqr(foyer.above()) > 64
                || data().getBooleanOr("won", false) != (wingIndex > 0))
            return fail("PHYSICAL_ROUTE_INCOMPLETE");
        ServerLevel level = server.overworld();
        var clock = level.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK)
                .getOrThrow(WorldClocks.OVERWORLD);
        long dayTime = Math.floorMod(level.clockManager()
                .getInstance(clock).totalTicks(), 24_000L);
        boolean night = dayTime >= 13_000 && dayTime < 23_000;
        if (night != (wingIndex >= 2)) return fail("WALK_TIME_OF_DAY_MISMATCH");
        System.out.println("ESTATE_CONNECTED_WALK wing=" + wing
                + " modules=5 seams=4 completed=true night=" + night
                + " dayTime=" + dayTime
                + " alive=" + player.isAlive());
        advance(server, Phase.FOYER);
        return 1;
    }

    private void unload(MinecraftServer server, ServerLevel level) {
        int removed = 0;
        while (!forced.isEmpty() && removed < 2) {
            long packed = forced.iterator().next();
            forced.remove(packed);
            level.setChunkForced(ChunkPos.getX(packed), ChunkPos.getZ(packed), false);
            removed++;
        }
        if (!forced.isEmpty()) return;
        phase = Phase.WALK;
        phaseAt = server.getTickCount();
        actor.sendSystemMessage(Component.literal(EstateConnectedPath.encode(route)));
        System.out.println("ESTATE_CONNECTED_WALK_START wing=" + wing
                + " forcedTickets=0 viewDistance=2");
    }

    private CompoundTag data() {
        return ((GemsPersistentDataHolder) actor).gems$getPersistentData()
                .getCompound("FourRoadsEstate").orElseGet(CompoundTag::new);
    }

    private void advance(MinecraftServer server, Phase next) {
        phase = next;
        phaseAt = server.getTickCount();
        useSent = 0;
        foyerUseObservedAt = 0;
        if (next == Phase.RETURN) gateViewStand = null;
    }

    private int fail(String reason) {
        if (failure == null) {
            failure = reason;
            EstateRuntime.setFoyerUseObserver(null);
            System.out.println("ESTATE_CONNECTED_FAILURE reason=" + reason);
        }
        phase = Phase.RELEASE;
        return 0;
    }

    private void force(ServerLevel level, ChunkPos chunk) {
        if (forced.add(chunk.pack())) level.setChunkForced(chunk.x(), chunk.z(), true);
    }

    private void release(ServerLevel level) {
        int removed = 0;
        while (!forced.isEmpty() && removed < 2) {
            long packed = forced.iterator().next();
            forced.remove(packed);
            level.setChunkForced(ChunkPos.getX(packed), ChunkPos.getZ(packed), false);
            removed++;
        }
        if (!forced.isEmpty()) return;
        EstateRuntime.setFoyerUseObserver(null);
        List<Long> sorted = tickNanos.stream().sorted().toList();
        double p95 = sorted.isEmpty() ? 0
                : sorted.get((int) Math.floor((sorted.size() - 1) * 0.95)) / 1_000_000.0;
        double max = sorted.isEmpty() ? 0 : sorted.getLast() / 1_000_000.0;
        long over50 = sorted.stream().filter(value -> value > 50_000_000L).count();
        System.out.println("ESTATE_CONNECTED_RESULT status=" + (failure == null ? "PASS" : "FAIL")
                + " wing=" + wing + " allRoads=" + allRoads
                + " restartOnly=" + restartOnly + " version=v5"
                + " reason=" + (failure == null ? "NONE" : failure)
                + " samples=" + sorted.size() + " p95TickMs=" + p95
                + " maxTickMs=" + max + " ticksOver50Ms=" + over50
                + " forcedTickets=0 metricScope=start-through-fixture"
                + " performanceGate=false");
        if (actor != null) actor.sendSystemMessage(Component.literal("ESTATEQA|COMPLETE|"
                + (failure == null ? "PASS" : "FAIL")));
        phase = Phase.COMPLETE;
    }
}
