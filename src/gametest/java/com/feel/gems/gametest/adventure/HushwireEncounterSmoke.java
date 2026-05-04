package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.CampaignDeliveryAccount;
import com.feel.gems.adventure.CampaignPersistentState;
import com.feel.gems.adventure.LandmarkKey;
import com.feel.gems.adventure.boss.BossFeatures;
import com.feel.gems.adventure.boss.EmptyEncoreEntity;
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
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Test-only scripted survival fight in a naturally generated Hushwire hall. */
public final class HushwireEncounterSmoke implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("gems-hushwire-encounter-smoke");
    private static final UUID PLAYER_ID = UUID.fromString("ac2410eb-6c91-4926-8a0e-24e16427948d");
    private static final TagKey<net.minecraft.world.level.levelgen.structure.Structure> HUSHWIRE =
            TagKey.create(Registries.STRUCTURE,
                    Identifier.fromNamespaceAndPath("gems", "atlas_target/hushwire"));
    private static final int TIMEOUT_TICKS = 6000;
    private static final boolean RESUME_REMATCH =
            Boolean.getBoolean("gems.hushwireEncounterSmoke.resume");
    private static final boolean STOP_AFTER_CLEAR =
            Boolean.getBoolean("gems.hushwireEncounterSmoke.stopAfterClear");
    private static final int[][] RECEIVERS = {{-4, -6}, {4, -6}, {0, 9}};

    private ServerPlayer player;
    private EmptyEncoreEntity boss;
    private BlockPos found;
    private BlockPos bossStart;
    private int receiverRotation;
    private int preparedTick;
    private int startTick;
    private int stopAtTick;
    private int firstVictoryTick;
    private int round = 1;
    private int attacks;
    private int damagingAttacks;
    private int firstRoundDamagingAttacks;
    private int signalHits;
    private int observedCycles;
    private int lastCycleTick;
    private float lowestHealth = Float.MAX_VALUE;

    @Override
    public void onInitialize() {
        if (!Boolean.getBoolean("gems.hushwireEncounterSmoke")) return;
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
                        "Natural Hushwire fight timed out");
                require(server.getPlayerList().getPlayer(PLAYER_ID) == player && player.isAlive(),
                        "Connected survival player left or died");
                int elapsed = server.getTickCount() - startTick;
                if (elapsed == 200 || elapsed == 1000 || elapsed == 2000
                        || elapsed == 3000 || elapsed == 4000 || elapsed == 5000) {
                    String truthProbe = elapsed == 200
                            ? Boolean.toString(boss.showTruth(player)) : "not sampled";
                    LOGGER.info("HUSHWIRE_DIAGNOSTIC elapsed={} bossTicks={} bossHealth={} "
                                    + "bossPos={} playerPos={} playerHealth={} attacks={} damaging={} "
                                    + "truthProbe={}",
                            elapsed, boss.tickCount, boss.getHealth(), boss.blockPosition(),
                            player.blockPosition(), player.getHealth(), attacks, damagingAttacks,
                            truthProbe);
                }
                if (round == 0) {
                    if (server.getTickCount() - firstVictoryTick >= 120) startRematch(server);
                } else {
                    driveCombat(server);
                }
            } catch (Exception failure) {
                LOGGER.error("HUSHWIRE_NATURAL_ENCOUNTER_FAILED", failure);
                stopAtTick = server.getTickCount() + 40;
            }
        });
    }

    private boolean setup(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (found == null) {
            found = level.findNearestMapStructure(HUSHWIRE, new BlockPos(0, 64, 0), 256, false);
            require(found != null, "No natural Hushwire start within 256 chunks");
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int chunkX = (found.getX() >> 4) + dx;
                    int chunkZ = (found.getZ() >> 4) + dz;
                    level.getChunk(chunkX, chunkZ);
                    level.setChunkForced(chunkX, chunkZ, true);
                }
            }
            preparedTick = server.getTickCount();
            LOGGER.info("HUSHWIRE_NATURAL_PREPARED start={} tick={}", found, preparedTick);
            return false;
        }
        if (server.getTickCount() - preparedTick < 10) return false;
        var bosses = level.getEntitiesOfClass(EmptyEncoreEntity.class,
                new AABB(found.getX() - 80, level.getMinY(), found.getZ() - 80,
                        found.getX() + 80, level.getMaxY(), found.getZ() + 80));
        require(bosses.size() == 1,
                "Expected one natural Empty Encore, found " + bosses.size());
        boss = bosses.getFirst();
        bossStart = boss.blockPosition();
        receiverRotation = findReceiverRotation(level);
        player = connect(server, level);
        equip(player);
        teleport(player, boss.getX() + 3, boss.getY(), boss.getZ());
        lowestHealth = player.getHealth();
        startTick = server.getTickCount();
        LOGGER.info("HUSHWIRE_NATURAL_FOUND start={} boss={} floor={} receiverRotation={} health={}",
                found, bossStart, level.getBlockState(bossStart.below()).getBlock(),
                receiverRotation, player.getHealth());
        if (RESUME_REMATCH) {
            require(CampaignPersistentState.get(server).model()
                    .hasCleared(PLAYER_ID, LandmarkKey.HUSHWIRE),
                    "Saved first Hushwire clear missing after server restart");
            assertRewards(server, 8);
            assertReceiversRestored(level);
            teleport(player, boss.getX() + 1.5, boss.getY(), boss.getZ());
            InteractionResult result = boss.interact(player, InteractionHand.MAIN_HAND,
                    new Vec3(0.0, boss.getBbHeight() * 0.5, 0.0));
            require(result == InteractionResult.SUCCESS_SERVER,
                    "Saved defeated receiver did not restart rematch: " + result);
            round = 2;
            signalHits = 1;
            LOGGER.info("HUSHWIRE_SAVED_REMATCH_STARTED tick={} health={} redstone={}",
                    startTick, player.getHealth(), count(Items.REDSTONE));
        }
        return true;
    }

    private static ServerPlayer connect(MinecraftServer server, ServerLevel level) {
        GameProfile profile = new GameProfile(PLAYER_ID, "HushwireProbe");
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer connected = new ServerPlayer(server, level,
                cookie.gameProfile(), cookie.clientInformation());
        server.getPlayerList().loadPlayerData(connected.nameAndId()).ifPresent(data ->
                connected.load(TagValueInput.create(ProblemReporter.DISCARDING,
                        server.registryAccess(), data)));
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
        require(boss.position().distanceToSqr(Vec3.atBottomCenterOf(bossStart)) < 0.05,
                "Melee knockback moved Empty Encore off its authored receiver stage: "
                        + boss.position());
        int cycleTick = (server.getTickCount() - startTick) % 122;
        if (cycleTick < lastCycleTick) observedCycles++;
        lastCycleTick = cycleTick;
        // The back receiver's line rotates through the three true calls. Wait
        // there for one real hit, clear of the boss's physical center.
        if (round == 1 && signalHits == 0) {
            BlockPos back = receiverPad(RECEIVERS[2]);
            teleport(player, bossStart.getX() + 0.5
                            + (back.getX() - bossStart.getX()) / 9.0 * 2.5,
                    bossStart.getY(), bossStart.getZ() + 0.5
                            + (back.getZ() - bossStart.getZ()) / 9.0 * 2.5);
        } else {
            teleport(player, bossStart.getX() + 8.5, bossStart.getY(), bossStart.getZ() + 8.5);
        }
        if (signalHits > 0 && cycleTick >= 72 && cycleTick <= 115
                && server.getTickCount() % 12 == 0) {
            teleport(player, boss.getX() + 1.4, boss.getY(), boss.getZ());
            require(player.distanceToSqr(boss) <= 9.0,
                    "Scripted attack exceeded melee reach");
            float before = boss.getHealth();
            attacks++;
            player.attack(boss);
            if (boss.getHealth() < before) damagingAttacks++;
            teleport(player, bossStart.getX() + 8.5, bossStart.getY(), bossStart.getZ() + 8.5);
        }
        if (player.getHealth() < lowestHealth) {
            lowestHealth = player.getHealth();
            signalHits++;
            LOGGER.info("HUSHWIRE_ROUND_{}_SIGNAL_HIT tick={} health={} bossPos={}",
                    round, server.getTickCount(), lowestHealth, boss.blockPosition());
        }
        if (round == 1 && CampaignPersistentState.get(server).model()
                .hasCleared(PLAYER_ID, LandmarkKey.HUSHWIRE)) {
            require(signalHits > 0 && observedCycles > 0 && damagingAttacks > 0,
                    "First fight missed real signal, multiple cycles, or attacks");
            assertRewards(server, 8);
            assertReceiversRestored(server.overworld());
            LOGGER.info("HUSHWIRE_FIRST_CLEAR_PASS attacks={} damaging={} cycles={} "
                            + "signalHits={} lowestHealth={} book={} dial={} redstone={}",
                    attacks, damagingAttacks, observedCycles, signalHits, lowestHealth,
                    count(Items.WRITTEN_BOOK), count(BossFeatures.SIGNAL_DIAL), count(Items.REDSTONE));
            firstRoundDamagingAttacks = damagingAttacks;
            firstVictoryTick = server.getTickCount();
            round = 0;
            if (STOP_AFTER_CLEAR) {
                LOGGER.info("HUSHWIRE_FIRST_CLEAR_SAVE_PASS tick={} boss={} redstone={}",
                        firstVictoryTick, boss.blockPosition(), count(Items.REDSTONE));
                stopAtTick = server.getTickCount() + 40;
            }
        } else if (round == 2 && count(Items.REDSTONE) == 16) {
            require(damagingAttacks > firstRoundDamagingAttacks,
                    "Rematch had no new player attack damage");
            assertRewards(server, 16);
            assertReceiversRestored(server.overworld());
            LOGGER.info("HUSHWIRE_NATURAL_ENCOUNTER_PASS attacks={} damaging={} cycles={} "
                            + "health={} book={} dial={} redstone={}",
                    attacks, damagingAttacks, observedCycles, player.getHealth(),
                    count(Items.WRITTEN_BOOK), count(BossFeatures.SIGNAL_DIAL), count(Items.REDSTONE));
            stopAtTick = server.getTickCount() + 40;
        }
    }

    private void startRematch(MinecraftServer server) {
        teleport(player, boss.getX() + 1.5, boss.getY(), boss.getZ());
        InteractionResult result = boss.interact(player, InteractionHand.MAIN_HAND,
                new Vec3(0.0, boss.getBbHeight() * 0.5, 0.0));
        require(result == InteractionResult.SUCCESS_SERVER,
                "Interacting with the quiet receiver did not start rematch: " + result);
        player.setHealth(player.getMaxHealth());
        LOGGER.info("HUSHWIRE_REMATCH_STARTED tick={} boss={} health={}",
                server.getTickCount(), boss.blockPosition(), player.getHealth());
        round = 2;
        lastCycleTick = 0;
    }

    private void assertRewards(MinecraftServer server, int expectedRedstone) {
        var account = CampaignDeliveryAccount.of(player, CampaignPersistentState.get(server));
        require(account.hasLore(LandmarkKey.HUSHWIRE)
                        && account.hasSignature(LandmarkKey.HUSHWIRE)
                        && account.hasSupplies(LandmarkKey.HUSHWIRE)
                        && count(Items.WRITTEN_BOOK) == 1
                        && count(BossFeatures.SIGNAL_DIAL) == 1
                        && count(Items.REDSTONE) == expectedRedstone,
                "Hushwire rewards or saved receipts wrong: books=" + count(Items.WRITTEN_BOOK)
                        + " dials=" + count(BossFeatures.SIGNAL_DIAL)
                        + " redstone=" + count(Items.REDSTONE));
    }

    private void assertReceiversRestored(ServerLevel level) {
        for (int[] offset : RECEIVERS) {
            BlockPos pad = receiverPad(offset).below();
            require(level.getBlockState(pad).is(Blocks.GOLD_BLOCK),
                    "Hushwire receiver failed to restore at " + pad);
        }
    }

    private int findReceiverRotation(ServerLevel level) {
        for (int rotation = 0; rotation < 4; rotation++) {
            boolean complete = true;
            for (int[] offset : RECEIVERS) {
                BlockPos pad = receiverPad(offset, rotation).below();
                if (!level.getBlockState(pad).is(Blocks.CONCRETE.blue())
                        && !level.getBlockState(pad).is(Blocks.GOLD_BLOCK)) {
                    complete = false;
                    break;
                }
            }
            if (complete) return rotation;
        }
        throw new IllegalStateException("Natural Hushwire hall has no authored receiver layout");
    }

    private BlockPos receiverPad(int[] offset) {
        return receiverPad(offset, receiverRotation);
    }

    private BlockPos receiverPad(int[] offset, int rotation) {
        return switch (rotation) {
            case 1 -> bossStart.offset(-offset[1], 0, offset[0]);
            case 2 -> bossStart.offset(-offset[0], 0, -offset[1]);
            case 3 -> bossStart.offset(offset[1], 0, -offset[0]);
            default -> bossStart.offset(offset[0], 0, offset[1]);
        };
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
