package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.CampaignDeliveryAccount;
import com.feel.gems.adventure.CampaignPersistentState;
import com.feel.gems.adventure.LandmarkKey;
import com.feel.gems.adventure.boss.BossFeatures;
import com.feel.gems.adventure.boss.UnbrokenPunchlineEntity;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Test-only scripted survival fight in a naturally generated Rehearsal Works. */
public final class RehearsalEncounterSmoke implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("gems-rehearsal-encounter-smoke");
    private static final UUID PLAYER_ID = UUID.fromString("948d84eb-0bb1-49d8-9e33-c4ca3242c765");
    private static final TagKey<net.minecraft.world.level.levelgen.structure.Structure> REHEARSAL =
            TagKey.create(Registries.STRUCTURE,
                    Identifier.fromNamespaceAndPath("gems", "atlas_target/rehearsal_works"));
    private static final int TIMEOUT_TICKS = 4000;

    private ServerPlayer player;
    private UnbrokenPunchlineEntity boss;
    private BlockPos found;
    private int preparedTick;
    private int startTick;
    private int stopAtTick;
    private int firstVictoryTick;
    private int firstRoundHits;
    private int round = 1;
    private int attacks;
    private int damagingAttacks;
    private int stageMoves;
    private int cycleStartTick;
    private BlockPos previousBossPos;
    private float lowestHealth = Float.MAX_VALUE;

    @Override
    public void onInitialize() {
        if (!Boolean.getBoolean("gems.rehearsalEncounterSmoke")) return;
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
                require(elapsed < TIMEOUT_TICKS, "Natural Rehearsal fight timed out");
                require(server.getPlayerList().getPlayer(PLAYER_ID) == player && player.isAlive(),
                        "Connected survival player left or died");
                if (elapsed == 200) {
                    require(boss.tickCount >= 50, "Natural boss was not entity-ticking");
                    LOGGER.info("REHEARSAL_TICK_DIAGNOSTIC bossTicks={} playerTicks={} bossPos={}",
                            boss.tickCount, player.tickCount, boss.blockPosition());
                }
                if (round == 0) {
                    if (server.getTickCount() - firstVictoryTick >= 120) startRematch(server);
                } else {
                    driveCombat(server);
                }
            } catch (Exception failure) {
                LOGGER.error("REHEARSAL_NATURAL_ENCOUNTER_FAILED", failure);
                stopAtTick = server.getTickCount() + 40;
            }
        });
    }

    private boolean setup(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (found == null) {
            found = level.findNearestMapStructure(REHEARSAL, new BlockPos(0, 64, 0), 128, false);
            require(found != null, "No natural Rehearsal Works start within 128 chunks");
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int chunkX = (found.getX() >> 4) + dx;
                    int chunkZ = (found.getZ() >> 4) + dz;
                    level.getChunk(chunkX, chunkZ);
                    level.setChunkForced(chunkX, chunkZ, true);
                }
            }
            preparedTick = server.getTickCount();
            LOGGER.info("REHEARSAL_NATURAL_PREPARED start={} tick={}", found, preparedTick);
            return false;
        }
        if (server.getTickCount() - preparedTick < 10) return false;
        var bosses = level.getEntitiesOfClass(UnbrokenPunchlineEntity.class,
                new AABB(found.getX() - 80, level.getMinY(), found.getZ() - 80,
                        found.getX() + 80, level.getMaxY(), found.getZ() + 80));
        require(bosses.size() == 1,
                "Expected one natural Unbroken Punchline, found " + bosses.size());
        boss = bosses.getFirst();
        player = connect(server, level);
        equip(player);
        teleport(player, boss.getX() + 3, boss.getY(), boss.getZ());
        previousBossPos = boss.blockPosition();
        lowestHealth = player.getHealth();
        startTick = server.getTickCount();
        LOGGER.info("REHEARSAL_NATURAL_FOUND start={} boss={} playerHealth={} floor={}",
                found, boss.blockPosition(), player.getHealth(),
                level.getBlockState(boss.blockPosition().below()).getBlock());
        return true;
    }

    private static ServerPlayer connect(MinecraftServer server, ServerLevel level) {
        GameProfile profile = new GameProfile(PLAYER_ID, "RehearsalProbe");
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
        if (boss.blockPosition().distSqr(previousBossPos) > 9) {
            stageMoves++;
            cycleStartTick = server.getTickCount();
            previousBossPos = boss.blockPosition();
            LOGGER.info("REHEARSAL_ROUND_{}_STAGE_MOVE tick={} to={} health={}",
                    round, server.getTickCount(), previousBossPos, boss.getHealth());
        }
        // The first spotlight lands. Thereafter, move after its lock and
        // return for the recovery window. Stage teleport resets the cycle.
        int cycleElapsed = server.getTickCount() - cycleStartTick;
        boolean dodging = cycleStartTick != 0 && cycleElapsed >= 36 && cycleElapsed <= 58;
        teleport(player, boss.getX() + (dodging ? 5.0 : 1.5),
                boss.getY(), boss.getZ() + (dodging ? 4.0 : 0.0));
        if (!dodging && server.getTickCount() % 15 == 0
                && player.distanceToSqr(boss) <= 9.0) {
            float before = boss.getHealth();
            attacks++;
            player.attack(boss);
            if (boss.getHealth() < before) damagingAttacks++;
        }
        if (player.getHealth() < lowestHealth) {
            lowestHealth = player.getHealth();
            LOGGER.info("REHEARSAL_ROUND_{}_PLAYER_HIT tick={} health={}",
                    round, server.getTickCount(), lowestHealth);
            if (cycleStartTick == 0) cycleStartTick = server.getTickCount() - 58;
        }
        if (round == 1 && CampaignPersistentState.get(server).model()
                .hasCleared(PLAYER_ID, LandmarkKey.REHEARSAL)) {
            completeFirstClear(server);
        } else if (round == 2 && count(Items.GLOW_INK_SAC) == 4) {
            completeRematch(server);
        }
    }

    private void completeFirstClear(MinecraftServer server) {
        require(damagingAttacks > 0 && stageMoves > 0 && lowestHealth < 20,
                "First fight missed player damage, stage movement, or spotlight damage");
        assertRewards(server, 2);
        LOGGER.info("REHEARSAL_FIRST_CLEAR_PASS attacks={} damaging={} stageMoves={} "
                        + "lowestHealth={} book={} mark={} ink={}",
                attacks, damagingAttacks, stageMoves, lowestHealth,
                count(Items.WRITTEN_BOOK), count(BossFeatures.REHEARSAL_MARK),
                count(Items.GLOW_INK_SAC));
        firstRoundHits = damagingAttacks;
        firstVictoryTick = server.getTickCount();
        round = 0;
    }

    private void startRematch(MinecraftServer server) {
        teleport(player, boss.getX() + 1.5, boss.getY(), boss.getZ());
        InteractionResult result = boss.interact(player, InteractionHand.MAIN_HAND,
                new Vec3(0.0, boss.getBbHeight() * 0.5, 0.0));
        require(result == InteractionResult.SUCCESS_SERVER,
                "Interacting with the defeated mask did not start a rematch: " + result);
        LOGGER.info("REHEARSAL_REMATCH_STARTED tick={} health={}",
                server.getTickCount(), player.getHealth());
        // The scripted player rests between independent encounters.
        player.setHealth(player.getMaxHealth());
        cycleStartTick = 0;
        round = 2;
    }

    private void completeRematch(MinecraftServer server) {
        require(damagingAttacks > firstRoundHits,
                "Rematch had no new player attack damage");
        assertRewards(server, 4);
        LOGGER.info("REHEARSAL_NATURAL_ENCOUNTER_PASS attacks={} damaging={} stageMoves={} "
                        + "health={} book={} mark={} ink={}",
                attacks, damagingAttacks, stageMoves, player.getHealth(),
                count(Items.WRITTEN_BOOK), count(BossFeatures.REHEARSAL_MARK),
                count(Items.GLOW_INK_SAC));
        stopAtTick = server.getTickCount() + 40;
    }

    private void assertRewards(MinecraftServer server, int expectedInk) {
        var account = CampaignDeliveryAccount.of(player, CampaignPersistentState.get(server));
        require(account.hasLore(LandmarkKey.REHEARSAL)
                        && account.hasSignature(LandmarkKey.REHEARSAL)
                        && account.hasSupplies(LandmarkKey.REHEARSAL)
                        && count(Items.WRITTEN_BOOK) == 1
                        && count(BossFeatures.REHEARSAL_MARK) == 1
                        && count(Items.GLOW_INK_SAC) == expectedInk,
                "Rehearsal rewards or saved receipts wrong: books=" + count(Items.WRITTEN_BOOK)
                        + " marks=" + count(BossFeatures.REHEARSAL_MARK)
                        + " ink=" + count(Items.GLOW_INK_SAC));
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
