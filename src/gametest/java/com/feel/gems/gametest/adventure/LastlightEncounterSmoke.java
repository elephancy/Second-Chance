package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.CampaignDeliveryAccount;
import com.feel.gems.adventure.CampaignPersistentState;
import com.feel.gems.adventure.LandmarkKey;
import com.feel.gems.adventure.boss.BossFeatures;
import com.feel.gems.adventure.boss.LastArrivalEntity;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Test-only scripted survival fight in a naturally generated Lastlight Exchange. */
public final class LastlightEncounterSmoke implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("gems-lastlight-encounter-smoke");
    private static final UUID PLAYER_ID = UUID.fromString("cf29b597-0492-4665-8ca0-6718778301c3");
    private static final TagKey<net.minecraft.world.level.levelgen.structure.Structure> LASTLIGHT =
            TagKey.create(Registries.STRUCTURE,
                    Identifier.fromNamespaceAndPath("gems", "atlas_target/lastlight_exchange"));
    private static final int TIMEOUT_TICKS = 3600;

    private ServerPlayer player;
    private LastArrivalEntity boss;
    private BlockPos found;
    private BlockPos startingBossPos;
    private int preparedTick;
    private int startTick;
    private int stopAtTick;
    private int firstVictoryTick;
    private int round = 1;
    private int attacks;
    private int damagingAttacks;
    private int firstRoundDamagingAttacks;
    private int lateMoves;
    private float lowestHealth = Float.MAX_VALUE;
    private boolean firstTrackHit;
    private boolean seenAnnexThisRound;

    @Override
    public void onInitialize() {
        if (!Boolean.getBoolean("gems.lastlightEncounterSmoke")) return;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (stopAtTick > 0) {
                if (server.getTickCount() >= stopAtTick) server.halt(false);
                return;
            }
            try {
                if (player == null) {
                    if (server.getTickCount() < 40 || !setup(server)) return;
                }
                require(server.getTickCount() - startTick < TIMEOUT_TICKS,
                        "Natural Lastlight fight timed out");
                require(server.getPlayerList().getPlayer(PLAYER_ID) == player && player.isAlive(),
                        "Connected survival player left or died");
                if (server.getTickCount() - startTick == 200) {
                    require(boss.tickCount >= 50, "Natural Lastlight boss was not entity-ticking");
                    LOGGER.info("LASTLIGHT_TICK_DIAGNOSTIC bossTicks={} playerTicks={} bossPos={}",
                            boss.tickCount, player.tickCount, boss.blockPosition());
                }
                if (round == 0) {
                    if (server.getTickCount() - firstVictoryTick >= 120) startRematch(server);
                } else {
                    driveCombat(server);
                }
            } catch (Exception failure) {
                LOGGER.error("LASTLIGHT_NATURAL_ENCOUNTER_FAILED", failure);
                stopAtTick = server.getTickCount() + 40;
            }
        });
    }

    private boolean setup(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (found == null) {
            found = level.findNearestMapStructure(LASTLIGHT, new BlockPos(0, 64, 0), 320, false);
            require(found != null, "No natural Lastlight Exchange start within 320 chunks");
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int chunkX = (found.getX() >> 4) + dx;
                    int chunkZ = (found.getZ() >> 4) + dz;
                    level.getChunk(chunkX, chunkZ);
                    level.setChunkForced(chunkX, chunkZ, true);
                }
            }
            preparedTick = server.getTickCount();
            LOGGER.info("LASTLIGHT_NATURAL_PREPARED start={} tick={}", found, preparedTick);
            return false;
        }
        if (server.getTickCount() - preparedTick < 10) return false;
        var bosses = level.getEntitiesOfClass(LastArrivalEntity.class,
                new AABB(found.getX() - 80, level.getMinY(), found.getZ() - 80,
                        found.getX() + 80, level.getMaxY(), found.getZ() + 80));
        require(bosses.size() == 1,
                "Expected one natural Last Arrival, found " + bosses.size());
        boss = bosses.getFirst();
        startingBossPos = boss.blockPosition();
        player = connect(server, level);
        equip(player);
        teleport(player, boss.getX() + 3, boss.getY(), boss.getZ());
        lowestHealth = player.getHealth();
        startTick = server.getTickCount();
        LOGGER.info("LASTLIGHT_NATURAL_FOUND start={} boss={} playerHealth={} floor={}",
                found, startingBossPos, player.getHealth(),
                level.getBlockState(startingBossPos.below()).getBlock());
        return true;
    }

    private static ServerPlayer connect(MinecraftServer server, ServerLevel level) {
        GameProfile profile = new GameProfile(PLAYER_ID, "LastlightProbe");
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
        boolean victoryThisRound = round == 1
                ? CampaignPersistentState.get(server).model()
                        .hasCleared(PLAYER_ID, LandmarkKey.LASTLIGHT)
                : count(Items.CLOCK) == 2;
        if (!seenAnnexThisRound && boss.getHealth() > boss.getMaxHealth() * 0.5F
                && !victoryThisRound) {
            require(boss.position().distanceToSqr(Vec3.atBottomCenterOf(startingBossPos)) < 0.05,
                    "Melee knockback moved stationary Last Arrival before annex phase: "
                            + boss.position());
        }
        if (boss.level().getBlockState(boss.blockPosition().below()).is(Blocks.LODESTONE)
                && !seenAnnexThisRound) {
            seenAnnexThisRound = true;
            lateMoves++;
            LOGGER.info("LASTLIGHT_ROUND_{}_ANNEX_MOVE tick={} to={} bossHealth={}",
                    round, server.getTickCount(), boss.blockPosition(), boss.getHealth());
        }
        // Stay on the first lit lane until one real strike lands. Then wait
        // between both track sets, stepping in only to make a real melee hit.
        if (round == 1 && !firstTrackHit) {
            teleport(player, startingBossPos.getX() + 0.5, boss.getY(),
                    startingBossPos.getZ() + 3.5);
        } else {
            moveBetweenTracks();
        }
        if ((round != 1 || firstTrackHit) && server.getTickCount() % 15 == 0
                && boss.isAlive()) {
            teleport(player, boss.getX() + 1.5, boss.getY(), boss.getZ() + 1.5);
            float before = boss.getHealth();
            attacks++;
            player.attack(boss);
            if (boss.getHealth() < before) damagingAttacks++;
            moveBetweenTracks();
        }
        if (player.getHealth() < lowestHealth) {
            lowestHealth = player.getHealth();
            firstTrackHit = true;
            LOGGER.info("LASTLIGHT_ROUND_{}_PLAYER_HIT tick={} health={}",
                    round, server.getTickCount(), lowestHealth);
        }
        if (round == 1 && CampaignPersistentState.get(server).model()
                .hasCleared(PLAYER_ID, LandmarkKey.LASTLIGHT)) {
            completeFirstClear(server);
        } else if (round == 2 && count(Items.CLOCK) == 2) {
            completeRematch(server);
        }
    }

    private void completeFirstClear(MinecraftServer server) {
        require(firstTrackHit && damagingAttacks > 0 && lateMoves > 0,
                "First fight missed track damage, player attacks or annex move");
        assertRewards(server, 1);
        LOGGER.info("LASTLIGHT_FIRST_CLEAR_PASS attacks={} damaging={} annexMoves={} "
                        + "lowestHealth={} book={} ticket={} clocks={}",
                attacks, damagingAttacks, lateMoves, lowestHealth,
                count(Items.WRITTEN_BOOK), count(BossFeatures.RETURN_TICKET), count(Items.CLOCK));
        firstRoundDamagingAttacks = damagingAttacks;
        firstVictoryTick = server.getTickCount();
        round = 0;
    }

    private void startRematch(MinecraftServer server) {
        teleport(player, boss.getX() + 1.5, boss.getY(), boss.getZ());
        ServerLevel level = server.overworld();
        BlockState original = level.getBlockState(startingBossPos);
        require(original.isAir(), "Original boss platform is unexpectedly occupied");
        level.setBlock(startingBossPos, Blocks.STONE.defaultBlockState(), 3);
        InteractionResult blocked;
        try {
            blocked = boss.interact(player, InteractionHand.MAIN_HAND,
                    new Vec3(0.0, boss.getBbHeight() * 0.5, 0.0));
        } finally {
            level.setBlock(startingBossPos, original, 3);
        }
        require(blocked == InteractionResult.SUCCESS_SERVER,
                "Blocked clock platform did not defer the rematch: " + blocked);
        LOGGER.info("LASTLIGHT_BLOCKED_PLATFORM_DEFERRED tick={} center={}",
                server.getTickCount(), startingBossPos);
        InteractionResult result = boss.interact(player, InteractionHand.MAIN_HAND,
                new Vec3(0.0, boss.getBbHeight() * 0.5, 0.0));
        require(result == InteractionResult.SUCCESS_SERVER,
                "Interacting with defeated clock did not start a rematch: " + result);
        player.setHealth(player.getMaxHealth());
        LOGGER.info("LASTLIGHT_REMATCH_STARTED tick={} health={}",
                server.getTickCount(), player.getHealth());
        seenAnnexThisRound = false;
        round = 2;
    }

    private void completeRematch(MinecraftServer server) {
        require(damagingAttacks > firstRoundDamagingAttacks,
                "Rematch had no new player attack damage");
        require(seenAnnexThisRound && lateMoves == 2,
                "Rematch did not reach the physical annex stage");
        assertRewards(server, 2);
        LOGGER.info("LASTLIGHT_NATURAL_ENCOUNTER_PASS attacks={} damaging={} annexMoves={} "
                        + "health={} book={} ticket={} clocks={}",
                attacks, damagingAttacks, lateMoves, player.getHealth(),
                count(Items.WRITTEN_BOOK), count(BossFeatures.RETURN_TICKET), count(Items.CLOCK));
        stopAtTick = server.getTickCount() + 40;
    }

    private void assertRewards(MinecraftServer server, int expectedClocks) {
        var account = CampaignDeliveryAccount.of(player, CampaignPersistentState.get(server));
        require(account.hasLore(LandmarkKey.LASTLIGHT)
                        && account.hasSignature(LandmarkKey.LASTLIGHT)
                        && account.hasSupplies(LandmarkKey.LASTLIGHT)
                        && count(Items.WRITTEN_BOOK) == 1
                        && count(BossFeatures.RETURN_TICKET) == 1
                        && count(Items.CLOCK) == expectedClocks,
                "Lastlight rewards or saved receipts wrong: books=" + count(Items.WRITTEN_BOOK)
                        + " tickets=" + count(BossFeatures.RETURN_TICKET)
                        + " clocks=" + count(Items.CLOCK));
    }

    private int count(Item item) {
        int amount = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) amount += stack.getCount();
        }
        return amount;
    }

    private void moveBetweenTracks() {
        teleport(player, startingBossPos.getX() + 4.5, startingBossPos.getY(),
                startingBossPos.getZ() + 4.5);
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
