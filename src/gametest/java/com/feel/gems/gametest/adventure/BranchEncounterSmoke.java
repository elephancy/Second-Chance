package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.CampaignDeliveryAccount;
import com.feel.gems.adventure.CampaignPersistentState;
import com.feel.gems.adventure.LandmarkKey;
import com.feel.gems.adventure.boss.BossFeatures;
import com.feel.gems.adventure.boss.SecondMoveSolverEntity;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.EnumSet;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Test-only scripted survival fight at a naturally generated Branch Vault. */
public final class BranchEncounterSmoke implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("gems-branch-encounter-smoke");
    private static final UUID PLAYER_ID = UUID.fromString("ac65b9c6-3d0b-4229-ac87-f990bfdb62ab");
    private static final TagKey<net.minecraft.world.level.levelgen.structure.Structure> BRANCH =
            TagKey.create(Registries.STRUCTURE,
                    Identifier.fromNamespaceAndPath("gems", "atlas_target/branch_vault"));
    private static final int TIMEOUT_TICKS = 4200;

    private ServerPlayer player;
    private SecondMoveSolverEntity boss;
    private BlockPos found;
    private BlockPos center;
    private int preparedTick;
    private int startTick;
    private int stopAtTick;
    private int firstVictoryTick;
    private int round = 1;
    private int attacks;
    private int damagingAttacks;
    private int firstRoundHits;
    private float lowestHealth = Float.MAX_VALUE;
    private float minHit = Float.MAX_VALUE;
    private float maxHit;
    private boolean laneHit;

    @Override
    public void onInitialize() {
        if (!Boolean.getBoolean("gems.branchEncounterSmoke")) return;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (stopAtTick > 0) {
                if (server.getTickCount() >= stopAtTick) server.halt(false);
                return;
            }
            try {
                if (player == null) {
                    if (server.getTickCount() < 40 || !setup(server)) return;
                }
                int elapsed = server.getTickCount() - startTick;
                require(elapsed < TIMEOUT_TICKS, "Natural Branch fight timed out");
                require(server.getPlayerList().getPlayer(PLAYER_ID) == player && player.isAlive(),
                        "Connected survival player left or died");
                if (elapsed == 200) {
                    require(boss.tickCount >= 50, "Natural Solver was not entity-ticking");
                    require(boss.position().distanceToSqr(Vec3.atBottomCenterOf(center)) < 0.05,
                            "Melee displaced the stationary Solver from its authored junction");
                    LOGGER.info("BRANCH_TICK_DIAGNOSTIC bossTicks={} playerTicks={} bossPos={}",
                            boss.tickCount, player.tickCount, boss.blockPosition());
                }
                if (round == 0) {
                    if (server.getTickCount() - firstVictoryTick >= 120) startRematch(server);
                } else {
                    driveCombat(server);
                }
            } catch (Exception failure) {
                LOGGER.error("BRANCH_NATURAL_ENCOUNTER_FAILED", failure);
                stopAtTick = server.getTickCount() + 40;
            }
        });
    }

    private boolean setup(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (found == null) {
            found = level.findNearestMapStructure(BRANCH, new BlockPos(0, 64, 0), 320, false);
            require(found != null, "No natural Branch Vault start within 320 chunks");
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int chunkX = (found.getX() >> 4) + dx;
                    int chunkZ = (found.getZ() >> 4) + dz;
                    level.getChunk(chunkX, chunkZ);
                    level.setChunkForced(chunkX, chunkZ, true);
                }
            }
            preparedTick = server.getTickCount();
            LOGGER.info("BRANCH_NATURAL_PREPARED start={} tick={}", found, preparedTick);
            return false;
        }
        if (server.getTickCount() - preparedTick < 10) return false;
        var bosses = level.getEntitiesOfClass(SecondMoveSolverEntity.class,
                new AABB(found.getX() - 80, level.getMinY(), found.getZ() - 80,
                        found.getX() + 80, level.getMaxY(), found.getZ() + 80));
        require(bosses.size() == 1,
                "Expected one natural Second-Move Solver, found " + bosses.size());
        boss = bosses.getFirst();
        center = boss.blockPosition();
        require(level.getBlockState(center.below()).is(Blocks.LODESTONE),
                "Solver is not on the authored junction lodestone");
        player = connect(server, level);
        equip(player);
        teleport(player, center.getX() + 5.5, center.getY(), center.getZ() + 0.5);
        lowestHealth = player.getHealth();
        startTick = server.getTickCount();
        LOGGER.info("BRANCH_NATURAL_FOUND start={} boss={} playerHealth={} floor={}",
                found, center, player.getHealth(), level.getBlockState(center.below()).getBlock());
        return true;
    }

    private static ServerPlayer connect(MinecraftServer server, ServerLevel level) {
        GameProfile profile = new GameProfile(PLAYER_ID, "BranchProbe");
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer connected = new ServerPlayer(server, level,
                cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, connected, cookie);
        if (connected.connection != null) {
            for (int tick = 0; tick < 80 && !connected.connection.hasClientLoaded(); tick++) {
                connected.connection.tickClientLoadTimeout();
            }
        }
        return connected;
    }

    private static void equip(ServerPlayer connected) {
        connected.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
        connected.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        connected.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        connected.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
        connected.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
        connected.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(16.0);
    }

    private void driveCombat(MinecraftServer server) {
        int elapsed = server.getTickCount() - startTick;
        // Let the first predicted east lane actually hit. Subsequent approaches
        // strike through ServerPlayer.attack and retreat from the junction.
        if (round == 1 && !laneHit) {
            teleport(player, center.getX() + 5.5, center.getY(), center.getZ() + 0.5);
        } else {
            teleport(player, center.getX() + 4.5, center.getY(), center.getZ() + 4.5);
            if (server.getTickCount() % 15 == 0 && boss.isAlive()) {
                teleport(player, boss.getX() + 1.5, boss.getY(), boss.getZ() + 1.5);
                float before = boss.getHealth();
                attacks++;
                player.attack(boss);
                if (boss.getHealth() < before) {
                    damagingAttacks++;
                    float hit = before - boss.getHealth();
                    minHit = Math.min(minHit, hit);
                    maxHit = Math.max(maxHit, hit);
                }
                teleport(player, center.getX() + 4.5, center.getY(), center.getZ() + 4.5);
            }
        }
        if (player.getHealth() < lowestHealth) {
            lowestHealth = player.getHealth();
            if (round == 1 && !laneHit) laneHit = true;
            LOGGER.info("BRANCH_ROUND_{}_PLAYER_HIT tick={} health={} bossHealth={}",
                    round, server.getTickCount(), lowestHealth, boss.getHealth());
        }
        if (round == 1 && CampaignPersistentState.get(server).model()
                .hasCleared(PLAYER_ID, LandmarkKey.BRANCH)) {
            require(elapsed > 60, "First clear completed before attack-driven fight");
            completeFirstClear(server);
        } else if (round == 2 && count(Items.MAP) == 2) {
            completeRematch(server);
        }
    }

    private void completeFirstClear(MinecraftServer server) {
        LOGGER.info("BRANCH_FIRST_CLEAR_DIAGNOSTIC minHit={} maxHit={} damaging={} center={} boss={}",
                minHit, maxHit, damagingAttacks, center, boss.blockPosition());
        require(laneHit && damagingAttacks > 0 && maxHit >= minHit * 1.8F,
                "First fight missed lane damage, attack damage, or recovery window");
        assertRewards(server, 1);
        assertRoutesRevealed(server.overworld());
        LOGGER.info("BRANCH_FIRST_CLEAR_PASS attacks={} damaging={} lowestHealth={} book={} lens={} maps={}",
                attacks, damagingAttacks, lowestHealth,
                count(Items.WRITTEN_BOOK), count(BossFeatures.ROUTE_LENS), count(Items.MAP));
        firstRoundHits = damagingAttacks;
        firstVictoryTick = server.getTickCount();
        round = 0;
    }

    private void startRematch(MinecraftServer server) {
        teleport(player, boss.getX() + 1.5, boss.getY(), boss.getZ());
        InteractionResult result = boss.interact(player, InteractionHand.MAIN_HAND,
                new Vec3(0.0, boss.getBbHeight() * 0.5, 0.0));
        require(result == InteractionResult.SUCCESS_SERVER,
                "Interacting with defeated Solver did not start rematch: " + result);
        player.setHealth(player.getMaxHealth());
        LOGGER.info("BRANCH_REMATCH_STARTED tick={} health={}",
                server.getTickCount(), player.getHealth());
        round = 2;
    }

    private void completeRematch(MinecraftServer server) {
        require(damagingAttacks > firstRoundHits, "Rematch had no new player attack damage");
        assertRewards(server, 2);
        assertRoutesRevealed(server.overworld());
        LOGGER.info("BRANCH_NATURAL_ENCOUNTER_PASS attacks={} damaging={} health={} book={} lens={} maps={}",
                attacks, damagingAttacks, player.getHealth(),
                count(Items.WRITTEN_BOOK), count(BossFeatures.ROUTE_LENS), count(Items.MAP));
        stopAtTick = server.getTickCount() + 40;
    }

    private void assertRoutesRevealed(ServerLevel level) {
        for (int[] offset : new int[][]{{-7, 0}, {7, 0}, {0, -7}, {0, 7}}) {
            require(level.getBlockState(center.below().offset(offset[0], 0, offset[1]))
                            .is(Blocks.GOLD_BLOCK),
                    "Restored route end absent at offset " + offset[0] + "," + offset[1]);
        }
    }

    private void assertRewards(MinecraftServer server, int expectedMaps) {
        var account = CampaignDeliveryAccount.of(player, CampaignPersistentState.get(server));
        require(account.hasLore(LandmarkKey.BRANCH)
                        && account.hasSignature(LandmarkKey.BRANCH)
                        && account.hasSupplies(LandmarkKey.BRANCH)
                        && count(Items.WRITTEN_BOOK) == 1
                        && count(BossFeatures.ROUTE_LENS) == 1
                        && count(Items.MAP) == expectedMaps,
                "Branch rewards or saved receipts wrong: books=" + count(Items.WRITTEN_BOOK)
                        + " lenses=" + count(BossFeatures.ROUTE_LENS)
                        + " maps=" + count(Items.MAP));
    }

    private int count(Item item) {
        int amount = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) amount += stack.getCount();
        }
        return amount;
    }

    private static void teleport(ServerPlayer connected, double x, double y, double z) {
        if (connected.position().distanceToSqr(x, y, z) < 0.04) return;
        connected.teleportTo(connected.level(), x, y, z,
                EnumSet.noneOf(net.minecraft.world.entity.Relative.class),
                0.0F, 0.0F, false);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
