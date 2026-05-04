package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.CampaignDeliveryAccount;
import com.feel.gems.adventure.CampaignPersistentState;
import com.feel.gems.adventure.LandmarkKey;
import com.feel.gems.adventure.boss.BossFeatures;
import com.feel.gems.adventure.boss.StrayCadenzaEntity;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
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

/** Test-only scripted survival fight at a naturally generated Unwritten Court. */
public final class UnwrittenEncounterSmoke implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("gems-unwritten-encounter-smoke");
    private static final UUID PLAYER_ID = UUID.fromString("f7f90958-49bb-42ac-9f39-02438863cde2");
    private static final TagKey<net.minecraft.world.level.levelgen.structure.Structure> COURT =
            TagKey.create(Registries.STRUCTURE,
                    Identifier.fromNamespaceAndPath("gems", "atlas_target/unwritten_court"));
    private static final int TIMEOUT_TICKS = 7200;
    private static final Field CYCLE_TICK = field("cycleTick");
    private static final Field ACTIVE = field("active");

    private ServerPlayer player;
    private StrayCadenzaEntity boss;
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
    private int lockedBeats;
    private int escapedBeats;
    private float lowestHealth = Float.MAX_VALUE;
    private float healthAtLock;
    private boolean firstStrikeHit;

    @Override
    public void onInitialize() {
        if (!Boolean.getBoolean("gems.unwrittenEncounterSmoke")) return;
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
                require(elapsed < TIMEOUT_TICKS, "Natural Unwritten fight timed out");
                require(server.getPlayerList().getPlayer(PLAYER_ID) == player && player.isAlive(),
                        "Connected survival player left or died");
                if (elapsed == 200) {
                    require(boss.tickCount >= 50, "Natural Cadenza was not entity-ticking");
                    require(boss.position().distanceToSqr(Vec3.atBottomCenterOf(center)) < 0.05,
                            "Melee displaced Cadenza from its authored center stage");
                    LOGGER.info("UNWRITTEN_TICK_DIAGNOSTIC bossTicks={} playerTicks={} "
                                    + "bossPos={} center={} drift={}",
                            boss.tickCount, player.tickCount, boss.position(), center,
                            boss.position().distanceToSqr(Vec3.atBottomCenterOf(center)));
                }
                if (round == 0) {
                    if (server.getTickCount() - firstVictoryTick >= 120) startRematch(server);
                } else {
                    driveCombat(server);
                }
            } catch (Exception failure) {
                LOGGER.error("UNWRITTEN_NATURAL_ENCOUNTER_FAILED", failure);
                stopAtTick = server.getTickCount() + 40;
            }
        });
    }

    private boolean setup(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (found == null) {
            found = level.findNearestMapStructure(COURT, new BlockPos(0, 64, 0), 128, false);
            require(found != null, "No natural Unwritten Court within 128 chunks");
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    int chunkX = (found.getX() >> 4) + dx;
                    int chunkZ = (found.getZ() >> 4) + dz;
                    level.getChunk(chunkX, chunkZ);
                    level.setChunkForced(chunkX, chunkZ, true);
                }
            }
            preparedTick = server.getTickCount();
            LOGGER.info("UNWRITTEN_NATURAL_PREPARED start={} tick={}", found, preparedTick);
            return false;
        }
        if (server.getTickCount() - preparedTick < 10) return false;
        var bosses = level.getEntitiesOfClass(StrayCadenzaEntity.class,
                new AABB(found.getX() - 80, level.getMinY(), found.getZ() - 80,
                        found.getX() + 80, level.getMaxY(), found.getZ() + 80));
        require(bosses.size() == 1,
                "Expected one natural Stray Cadenza, found " + bosses.size());
        boss = bosses.getFirst();
        center = boss.blockPosition();
        require(level.getBlockState(center.below()).is(Blocks.LODESTONE),
                "Cadenza is not on the authored center lodestone");
        require(cueCount(level, Blocks.CONCRETE.purple()) == 4,
                "Generated Court is missing intact purple stage cues");
        player = connect(server, level);
        equip(player);
        teleport(player, center.getX() + 2.5, center.getY(), center.getZ() + 0.5);
        lowestHealth = player.getHealth();
        startTick = server.getTickCount();
        LOGGER.info("UNWRITTEN_NATURAL_FOUND start={} center={} playerHealth={} cues={}",
                found, center, player.getHealth(), cueCount(level, Blocks.CONCRETE.purple()));
        return true;
    }

    private void driveCombat(MinecraftServer server) throws IllegalAccessException {
        int cycle = CYCLE_TICK.getInt(boss);
        boolean active = ACTIVE.getBoolean(boss);
        if (active && cycle == 47) {
            lockedBeats++;
            healthAtLock = player.getHealth();
            LOGGER.info("UNWRITTEN_ROUND_{}_LOCK tick={} bossHealth={} playerHealth={}",
                    round, server.getTickCount(), boss.getHealth(), player.getHealth());
        }
        if (round == 1 && !firstStrikeHit) {
            // Stay in the first marked spotlight to prove its real strike lands.
            teleport(player, center.getX() + 2.5, center.getY(), center.getZ() + 0.5);
        } else if (cycle >= 47 && cycle <= 73) {
            // The target locks at tick 46. Leave that circle before tick 72.
            teleport(player, center.getX() + 8.5, center.getY(), center.getZ() + 8.5);
        } else {
            teleport(player, center.getX() + 2.0, center.getY(), center.getZ() + 0.5);
        }
        if (active && firstStrikeHit && cycle == 74
                && player.getHealth() >= healthAtLock) escapedBeats++;
        if (active && firstStrikeHit && cycle > 74 && cycle < 130
                && server.getTickCount() % 12 == 0 && boss.isAlive()) {
            float before = boss.getHealth();
            attacks++;
            player.attack(boss);
            if (boss.getHealth() < before) damagingAttacks++;
        }
        if (player.getHealth() < lowestHealth) {
            require(active && lockedBeats > 0 && cycle >= 73,
                    "Player was hurt before the first marked stage strike");
            lowestHealth = player.getHealth();
            firstStrikeHit = true;
            LOGGER.info("UNWRITTEN_ROUND_{}_PLAYER_HIT tick={} cycle={} health={}",
                    round, server.getTickCount(), cycle, lowestHealth);
        }
        if (round == 1 && CampaignPersistentState.get(server).model()
                .hasCleared(PLAYER_ID, LandmarkKey.UNWRITTEN)) {
            completeFirstClear(server);
        } else if (round == 2 && count(Items.AMETHYST_SHARD) == 16) {
            completeRematch(server);
        }
    }

    private void completeFirstClear(MinecraftServer server) {
        require(firstStrikeHit && damagingAttacks > 0 && lockedBeats > 0,
                "First fight missed spotlight damage, player attacks, or a lock");
        require(cueCount(server.overworld(), Blocks.SEA_LANTERN) == 4,
                "Cadenza did not light the four authored stage cues");
        assertRewards(server, 8);
        LOGGER.info("UNWRITTEN_FIRST_CLEAR_PASS attacks={} damaging={} locked={} escaped={} "
                        + "lowestHealth={} book={} mark={} shards={} litCues={}",
                attacks, damagingAttacks, lockedBeats, escapedBeats, lowestHealth,
                count(Items.WRITTEN_BOOK), count(BossFeatures.STAGE_MARK),
                count(Items.AMETHYST_SHARD), cueCount(server.overworld(), Blocks.SEA_LANTERN));
        firstRoundHits = damagingAttacks;
        firstVictoryTick = server.getTickCount();
        round = 0;
    }

    private void startRematch(MinecraftServer server) {
        teleport(player, center.getX() + 1.5, center.getY(), center.getZ() + 0.5);
        InteractionResult result = boss.interact(player, InteractionHand.MAIN_HAND,
                new Vec3(0.0, boss.getBbHeight() * 0.5, 0.0));
        require(result == InteractionResult.SUCCESS_SERVER,
                "Interacting with defeated Cadenza did not start a rematch: " + result);
        player.setHealth(player.getMaxHealth());
        lowestHealth = player.getHealth();
        round = 2;
        LOGGER.info("UNWRITTEN_REMATCH_STARTED tick={} health={} center={}",
                server.getTickCount(), player.getHealth(), boss.blockPosition());
    }

    private void completeRematch(MinecraftServer server) {
        require(damagingAttacks > firstRoundHits && escapedBeats > 0,
                "Rematch missed real player attack damage or a locked-beat escape");
        require(cueCount(server.overworld(), Blocks.SEA_LANTERN) == 4,
                "Rematch did not preserve restored stage cues");
        assertRewards(server, 16);
        LOGGER.info("UNWRITTEN_NATURAL_ENCOUNTER_PASS attacks={} damaging={} locked={} "
                        + "escaped={} book={} mark={} shards={} litCues={}",
                attacks, damagingAttacks, lockedBeats, escapedBeats,
                count(Items.WRITTEN_BOOK), count(BossFeatures.STAGE_MARK),
                count(Items.AMETHYST_SHARD), cueCount(server.overworld(), Blocks.SEA_LANTERN));
        stopAtTick = server.getTickCount() + 40;
    }

    private void assertRewards(MinecraftServer server, int expectedShards) {
        var account = CampaignDeliveryAccount.of(player, CampaignPersistentState.get(server));
        require(account.hasLore(LandmarkKey.UNWRITTEN)
                        && account.hasSignature(LandmarkKey.UNWRITTEN)
                        && account.hasSupplies(LandmarkKey.UNWRITTEN)
                        && count(Items.WRITTEN_BOOK) == 1
                        && count(BossFeatures.STAGE_MARK) == 1
                        && count(Items.AMETHYST_SHARD) == expectedShards,
                "Unwritten rewards or saved receipts wrong: books=" + count(Items.WRITTEN_BOOK)
                        + " marks=" + count(BossFeatures.STAGE_MARK)
                        + " shards=" + count(Items.AMETHYST_SHARD));
    }

    private int cueCount(ServerLevel level, net.minecraft.world.level.block.Block block) {
        int count = 0;
        for (int dx : new int[] {-5, 5}) {
            for (int dz : new int[] {-5, 5}) {
                if (level.getBlockState(center.offset(dx, 0, dz)).is(block)) count++;
            }
        }
        return count;
    }

    private int count(Item item) {
        int amount = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) amount += stack.getCount();
        }
        return amount;
    }

    private static ServerPlayer connect(MinecraftServer server, ServerLevel level) {
        GameProfile profile = new GameProfile(PLAYER_ID, "UnwrittenProbe");
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

    private static void teleport(ServerPlayer connected, double x, double y, double z) {
        if (connected.position().distanceToSqr(x, y, z) < 0.04) return;
        connected.teleportTo(connected.level(), x, y, z,
                EnumSet.noneOf(net.minecraft.world.entity.Relative.class),
                0.0F, 0.0F, false);
    }

    private static Field field(String name) {
        try {
            Field result = StrayCadenzaEntity.class.getDeclaredField(name);
            result.setAccessible(true);
            return result;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cadenza test probe cannot read " + name, failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
