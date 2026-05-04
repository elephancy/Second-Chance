package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.AdventureItems;
import com.feel.gems.adventure.CampaignPersistentState;
import com.feel.gems.adventure.DomainBlocks;
import com.feel.gems.adventure.LandmarkKey;
import com.feel.gems.adventure.boss.PaintedVerdictEntity;
import com.feel.gems.adventure.boss.VerdictSequence;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opt-in connected first chapter slice on one disposable natural world.
 * Transport to the natural boss and town is fixture setup. The boss clear,
 * reward, and townhall service must come from real client attack/use packets.
 */
public final class ConnectedJourneyServerSmoke implements ModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("gems-connected-journey");
    private static final TagKey<Structure> FRAMEFALL = TagKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath("gems", "atlas_target/framefall_gallery"));
    private static final TagKey<Structure> TOWN = TagKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath("gems", "themed_target/second_chance_town"));
    private static final BlockPos ORIGIN = new BlockPos(0, 64, 0);
    private String phase;
    private boolean twoClient;
    private boolean ready;
    private boolean failed;
    private boolean passed;
    private BlockPos framefall;
    private BlockPos angleDesk;
    private PaintedVerdictEntity boss;
    private int preparedAt;
    private ServerPlayer first;
    private ServerPlayer second;

    @Override
    public void onInitialize() {
        if (!Boolean.getBoolean("gems.connectedJourney.server")) return;
        phase = System.getProperty("gems.connectedJourney.phase", "");
        twoClient = Boolean.getBoolean("gems.connectedJourney.twoClient");
        if (!Set.of("initial", "resume").contains(phase))
            throw new IllegalArgumentException("Connected journey phase must be initial or resume");
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(Commands.literal("connectedjourney")
                        .then(Commands.literal("ack")
                                .then(Commands.argument("stage", StringArgumentType.word())
                                        .executes(context -> acknowledge(context.getSource().getServer(),
                                                context.getSource().getPlayer(),
                                                StringArgumentType.getString(context, "stage")))))));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                join(server, handler.getPlayer()));
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }

    private void tick(MinecraftServer server) {
        if (failed || passed) return;
        if (!ready && server.getTickCount() >= 80) {
            try {
                if (prepare(server)) {
                    ready = true;
                    LOG.info("CJ_READY_A phase={} twoClient={} framefall={} angleDesk={}",
                            phase, twoClient, framefall, angleDesk);
                }
            } catch (Exception error) {
                fail(server, "natural preparation: " + error.getMessage());
            }
        }
        if (ready && phase.equals("initial") && first != null && angleDesk == null
                && cleared(server, first)) {
            if (boss == null || boss.phase() != VerdictSequence.Phase.DORMANT) {
                fail(server, "victory state appeared without a completed natural boss encounter");
                return;
            }
            try {
                prepareTown(server.overworld());
                stageDesk(server.overworld(), first);
                LOG.info("CJ_BOSS_CLEAR realClient=true personal=true worldShared=true boss={}",
                        boss.blockPosition());
                first.sendSystemMessage(Component.literal("CJ|USE|angle|" + coordinates(angleDesk)));
            } catch (Exception error) {
                fail(server, "town transition: " + error.getMessage());
            }
        }
        if (ready && server.getTickCount() > 20 * 900)
            fail(server, "fifteen-minute connected slice timeout");
    }

    private boolean prepare(MinecraftServer server) {
        boolean saved = CampaignPersistentState.get(server).model()
                .worldChanged(LandmarkKey.FRAMEFALL);
        require(saved == phase.equals("resume"), "saved first clear disagrees with phase");
        ServerLevel level = server.overworld();
        if (framefall == null)
            framefall = level.findNearestMapStructure(FRAMEFALL, ORIGIN, 128, false);
        require(framefall != null, "no natural Framefall within the bounded search");
        if (phase.equals("initial")) {
            if (preparedAt == 0) {
                for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                    int cx = (framefall.getX() >> 4) + dx;
                    int cz = (framefall.getZ() >> 4) + dz;
                    level.getChunk(cx, cz);
                    level.setChunkForced(cx, cz, true);
                }
                preparedAt = server.getTickCount();
                return false;
            }
            if (server.getTickCount() - preparedAt < 10) return false;
            var bosses = level.getEntitiesOfClass(PaintedVerdictEntity.class,
                    new AABB(framefall.getX() - 80, level.getMinY(), framefall.getZ() - 80,
                            framefall.getX() + 80, level.getMaxY(), framefall.getZ() + 80));
            require(bosses.size() == 1, "expected one natural Painted Verdict, found " + bosses.size());
            boss = bosses.getFirst();
            require(boss.phase() == VerdictSequence.Phase.DORMANT,
                    "natural boss was already engaged");
        } else {
            prepareTown(level);
        }
        return true;
    }

    private void prepareTown(ServerLevel level) {
        BlockPos town = level.findNearestMapStructure(TOWN, ORIGIN, 128, false);
        require(town != null, "no natural townhall within the bounded search");
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++)
            level.getChunk((town.getX() >> 4) + dx, (town.getZ() >> 4) + dz);
        angleDesk = find(level, town, 96);
        require(angleDesk != null, "natural townhall lacks its Angle Desk");
    }

    private static BlockPos find(ServerLevel level, BlockPos center, int radius) {
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            int x = center.getX() + dx;
            int z = center.getZ() + dz;
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            for (int y = top; y >= Math.max(level.getMinY(), top - 40); y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (level.getBlockState(pos).is(DomainBlocks.TOWNHALL_ANGLE_DESK)) return pos;
            }
        }
        return null;
    }

    private void join(MinecraftServer server, ServerPlayer player) {
        if (!ready || failed || passed) {
            fail(server, "early or unexpected join");
            return;
        }
        String name = player.getName().getString();
        if (name.equals("JourneyA") && first == null) {
            first = player;
            if (phase.equals("initial")) {
                require(!cleared(server, player), "first player already has Framefall credit");
                equip(player);
                player.teleportTo(server.overworld(), boss.getX() + 3.0, boss.getY(),
                        boss.getZ(), Set.of(), 0, 0, true);
                player.sendSystemMessage(Component.literal("CJ|FIGHT"));
            } else {
                require(cleared(server, player), "first player's clear did not survive restart");
                stageDesk(server.overworld(), player);
                player.sendSystemMessage(Component.literal("CJ|USE|angle|" + coordinates(angleDesk)));
            }
            LOG.info("CJ_JOIN role=A phase={} dedicated=true personalClear={}", phase,
                    cleared(server, player));
        } else if (name.equals("JourneyB") && twoClient && second == null && first != null
                && angleDesk != null && count(first, AdventureItems.ANGLE_LENS) == 1) {
            second = player;
            require(!cleared(server, player), "late joiner acquired an unearned personal clear");
            stageDesk(server.overworld(), player);
            player.sendSystemMessage(Component.literal("CJ|USE|angle|" + coordinates(angleDesk)));
            LOG.info("CJ_JOIN role=B phase={} dedicated=true personalClear=false sharedClear=true", phase);
        } else {
            fail(server, "out-of-order player join " + name);
        }
    }

    private int acknowledge(MinecraftServer server, ServerPlayer player, String stage) {
        if (!stage.equals("angle") || angleDesk == null || failed || passed
                || (player != first && player != second)) {
            fail(server, "unexpected service acknowledgement");
            return 0;
        }
        require(CampaignPersistentState.get(server).model().worldChanged(LandmarkKey.FRAMEFALL),
                "service used without a real world clear");
        require(count(player, AdventureItems.ANGLE_LENS) == 1,
                "Angle Desk did not deliver exactly one production lens");
        if (player == first && twoClient && second == null) {
            LOG.info("CJ_READY_B phase={} firstLens=1", phase);
            return 1;
        }
        if (twoClient && player != second) {
            fail(server, "first client repeated service before late joiner");
            return 0;
        }
        if (twoClient) require(!cleared(server, second), "late joiner acquired personal boss credit");
        passed = true;
        LOG.info("CJ_PHASE_PASS phase={} realBossClear={} realUsePackets=true sharedService={} "
                        + "savedWorld=true transportSetup=siteAndTown",
                phase, phase.equals("initial"), twoClient);
        for (ServerPlayer peer : server.getPlayerList().getPlayers())
            peer.sendSystemMessage(Component.literal("CJ|COMPLETE|" + phase));
        return 1;
    }

    private static void equip(ServerPlayer player) {
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
    }

    private void stageDesk(ServerLevel level, ServerPlayer player) {
        BlockPos target = null;
        for (int yOffset : new int[] {-1, 0})
            for (int distance = 1; distance <= 4 && target == null; distance++)
                for (int dx = -distance; dx <= distance && target == null; dx++)
                    for (int dz = -distance; dz <= distance; dz++) {
                        BlockPos feet = angleDesk.offset(dx, yOffset, dz);
                        if (level.getBlockState(feet).isAir()
                                && level.getBlockState(feet.above()).isAir()
                                && !level.getBlockState(feet.below())
                                        .getCollisionShape(level, feet.below()).isEmpty()) {
                            target = feet;
                            break;
                        }
                    }
        require(target != null, "no dry standing cell by natural Angle Desk");
        LOG.info("CJ_DESK_STAGE phase={} desk={} feet={}", phase, angleDesk, target);
        player.teleportTo(level, target.getX() + .5, target.getY(), target.getZ() + .5,
                Set.of(), 0, 0, true);
    }

    private static String coordinates(BlockPos pos) {
        return pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
    }

    private static boolean cleared(MinecraftServer server, ServerPlayer player) {
        return CampaignPersistentState.get(server).model()
                .hasCleared(player.getUUID(), LandmarkKey.FRAMEFALL);
    }

    private static int count(ServerPlayer player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalStateException(reason);
    }

    private void fail(MinecraftServer server, String reason) {
        if (failed) return;
        failed = true;
        LOG.error("CJ_PHASE_FAILED phase={} reason={}", phase, reason);
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            player.sendSystemMessage(Component.literal("CJ|FAILED|" + reason));
    }
}
