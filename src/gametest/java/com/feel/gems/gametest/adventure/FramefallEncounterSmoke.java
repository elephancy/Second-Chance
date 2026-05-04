package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.CampaignDeliveryAccount;
import com.feel.gems.adventure.CampaignPersistentState;
import com.feel.gems.adventure.LandmarkKey;
import com.feel.gems.adventure.boss.BossFeatures;
import com.feel.gems.adventure.boss.PaintedVerdictEntity;
import com.feel.gems.adventure.boss.VerdictSequence;
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

/** A bounded scripted survival encounter in a naturally generated Framefall. */
public final class FramefallEncounterSmoke implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("gems-framefall-encounter-smoke");
    private static final UUID PLAYER_ID = UUID.fromString("ec320abf-ea5c-4466-a36e-cbdf3d4537f5");
    private static final UUID TEAMMATE_ID = UUID.fromString("27f608b8-c67d-4c6c-a79e-5dd7592c78d2");
    private static final TagKey<net.minecraft.world.level.levelgen.structure.Structure> FRAMEFALL =
            TagKey.create(Registries.STRUCTURE,
                    Identifier.fromNamespaceAndPath("gems", "atlas_target/framefall_gallery"));
    private static final int TIMEOUT_TICKS = 3200;
    private final boolean coop = Boolean.getBoolean("gems.framefallEncounterSmoke.coop");
    private ServerPlayer player;
    private ServerPlayer teammate;
    private PaintedVerdictEntity boss;
    private BlockPos found;
    private int courtPreparedTick;
    private int startTick;
    private int stopAtTick;
    private int round = 1;
    private int firstVictoryTick;
    private int phaseTicks;
    private int shots;
    private int damagingAttacks;
    private int teammateDamagingAttacks;
    private int firstRoundDamagingAttacks;
    private int firstRoundTeammateDamagingAttacks;
    private int attacks;
    private int teammateAttacks;
    private float lowestPlayerHealth = Float.MAX_VALUE;
    private VerdictSequence.Phase previousPhase = VerdictSequence.Phase.DORMANT;
    private boolean seenTell;
    private boolean seenFire;
    private boolean seenRecovery;
    private boolean tookBossDamage;

    @Override
    public void onInitialize() {
        if (!Boolean.getBoolean("gems.framefallEncounterSmoke")) return;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (stopAtTick > 0) {
                if (server.getTickCount() >= stopAtTick) server.halt(false);
                return;
            }
            try {
                if (player == null) {
                    if (server.getTickCount() < 40) return;
                    if (!setup(server)) return;
                }
                require(server.getTickCount() - startTick < TIMEOUT_TICKS,
                        "The natural encounter exceeded " + TIMEOUT_TICKS + " server ticks");
                require(server.getPlayerList().getPlayer(PLAYER_ID) == player && player.isAlive(),
                        "The connected survival player left or died during the encounter");
                if (coop) {
                    require(server.getPlayerList().getPlayer(TEAMMATE_ID) == teammate
                                    && teammate.isAlive(),
                            "The connected teammate left or died during the encounter");
                }
                if (server.getTickCount() - startTick == 200) {
                    LOGGER.info("FRAMEFALL_TICK_DIAGNOSTIC bossTicks={} phase={} playerPos={} "
                                    + "playerTicks={} nearby={} distanceSq={}",
                            boss.tickCount, boss.phase(), player.position(), player.tickCount,
                            player.level().getEntitiesOfClass(ServerPlayer.class,
                                    new AABB(boss.blockPosition()).inflate(24)).size(),
                            player.distanceToSqr(boss));
                    require(boss.tickCount >= 50, "Natural boss chunk did not entity-tick after approach");
                }
                if (round == 0) {
                    if (server.getTickCount() - firstVictoryTick >= 120) startRematch(server);
                } else {
                    driveCombat(server);
                }
            } catch (Exception failure) {
                LOGGER.error("FRAMEFALL_NATURAL_ENCOUNTER_FAILED", failure);
                stopAtTick = server.getTickCount() + 40;
            }
        });
    }

    private boolean setup(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (found == null) {
            found = level.findNearestMapStructure(FRAMEFALL,
                    new BlockPos(0, 64, 0), 128, false);
            require(found != null, "The seed did not generate a Framefall start within 128 chunks");
            // A locate result can name an ungenerated start. The court may
            // rotate into any quadrant; keep those chunks entity-ticking even
            // before the embedded player has completed a real client handshake.
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int chunkX = (found.getX() >> 4) + dx;
                    int chunkZ = (found.getZ() >> 4) + dz;
                    level.getChunk(chunkX, chunkZ);
                    level.setChunkForced(chunkX, chunkZ, true);
                }
            }
            courtPreparedTick = server.getTickCount();
            LOGGER.info("FRAMEFALL_NATURAL_COURT_PREPARED start={} tick={}",
                    found, courtPreparedTick);
            return false;
        }
        if (server.getTickCount() - courtPreparedTick < 10) return false;
        var bosses = level.getEntitiesOfClass(PaintedVerdictEntity.class,
                new AABB(found.getX() - 80, level.getMinY(), found.getZ() - 80,
                        found.getX() + 80, level.getMaxY(), found.getZ() + 80));
        require(bosses.size() == 1,
                "Expected one naturally placed Painted Verdict; found " + bosses.size()
                        + " at Framefall start " + found);
        boss = bosses.getFirst();
        require(boss.phase() == VerdictSequence.Phase.DORMANT,
                "Naturally placed Painted Verdict was not dormant");

        player = connect(server, level, PLAYER_ID, "FramefallProbe");
        equip(player);
        moveNearBoss();
        if (coop) {
            teammate = connect(server, level, TEAMMATE_ID, "FramefallAlly");
            equip(teammate);
            moveTeammateNearBoss();
        }
        startTick = server.getTickCount();
        lowestPlayerHealth = player.getHealth();
        LOGGER.info("FRAMEFALL_NATURAL_FOUND start={} boss={} phase={} playerHealth={} "
                        + "teammateHealth={} floor={}",
                found, boss.blockPosition(), boss.phase(), player.getHealth(),
                teammate == null ? "solo" : teammate.getHealth(),
                level.getBlockState(boss.blockPosition().east(3).below()).getBlock());
        return true;
    }

    private static ServerPlayer connect(MinecraftServer server, ServerLevel level,
                                        UUID id, String name) {
        GameProfile profile = new GameProfile(id, name);
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
        // Keep the scripted test short while retaining the real survival
        // player attack path, boss armor, hit immunity and damage windows.
        connected.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(16.0);
    }

    private void driveCombat(MinecraftServer server) {
        VerdictSequence.Phase phase = boss.phase();
        if (phase != previousPhase) {
            LOGGER.info("FRAMEFALL_ROUND_{}_PHASE {} -> {} tick={} bossHealth={} playerHealth={}",
                    round, previousPhase, phase, server.getTickCount(),
                    boss.getHealth(), player.getHealth());
            phaseTicks = 0;
            previousPhase = phase;
        } else {
            phaseTicks++;
        }
        if (phase == VerdictSequence.Phase.TELL) {
            seenTell = true;
            if ((round == 1 && shots == 0) || phaseTicks < 34) moveNearBoss();
            else moveOffLockedLane();
            if (coop) {
                if ((round == 1 && shots == 0) || phaseTicks < 34) moveTeammateNearBoss();
                else moveTeammateOffLockedLane();
            }
        } else if (phase == VerdictSequence.Phase.FIRE) {
            seenFire = true;
            if (phaseTicks == 0) shots++;
        } else if (phase == VerdictSequence.Phase.RECOVERY) {
            seenRecovery = true;
            moveWithinMelee();
            if (coop) moveTeammateWithinMelee();
            if (coop && phaseTicks == 19) {
                float before = boss.getHealth();
                teammateAttacks++;
                teammate.attack(boss);
                if (boss.getHealth() < before) teammateDamagingAttacks++;
            } else if (phaseTicks % 15 == 4) {
                float before = boss.getHealth();
                attacks++;
                player.attack(boss);
                if (boss.getHealth() < before) damagingAttacks++;
            }
        }
        if (player.getHealth() < lowestPlayerHealth) {
            LOGGER.info("FRAMEFALL_ROUND_{}_PLAYER_HIT tick={} health={}",
                    round, server.getTickCount(), player.getHealth());
            lowestPlayerHealth = player.getHealth();
            tookBossDamage = true;
        }
        if (phase == VerdictSequence.Phase.DORMANT && seenRecovery) {
            if (round == 1) completeFirstClear(server);
            else completeRematch(server);
        }
    }

    private void completeFirstClear(MinecraftServer server) {
        require(seenTell && seenFire && seenRecovery && tookBossDamage && damagingAttacks > 0
                        && (!coop || teammateDamagingAttacks > 0),
                "First encounter missed a real tell, shot, player hit, recovery or melee damage");
        require(CampaignPersistentState.get(server).model().hasCleared(PLAYER_ID, LandmarkKey.FRAMEFALL),
                "The natural boss kill did not record personal Framefall credit");
        assertRewards(server, 2);
        if (coop) {
            require(CampaignPersistentState.get(server).model()
                            .hasCleared(TEAMMATE_ID, LandmarkKey.FRAMEFALL),
                    "The natural boss kill did not record teammate Framefall credit");
            assertRewards(server, teammate, 2);
            LOGGER.info("FRAMEFALL_COOP_FIRST_CLEAR_PASS primaryDamaging={} teammateDamaging={} "
                            + "primaryShards={} teammateShards={}",
                    damagingAttacks, teammateDamagingAttacks,
                    count(player, Items.AMETHYST_SHARD), count(teammate, Items.AMETHYST_SHARD));
        }
        LOGGER.info("FRAMEFALL_FIRST_CLEAR_PASS attacks={} damaging={} shots={} lowestHealth={} "
                        + "book={} frame={} shards={}", attacks, damagingAttacks, shots,
                lowestPlayerHealth, count(Items.WRITTEN_BOOK), count(BossFeatures.PARALLAX_FRAME),
                count(Items.AMETHYST_SHARD));
        firstRoundDamagingAttacks = damagingAttacks;
        firstRoundTeammateDamagingAttacks = teammateDamagingAttacks;
        firstVictoryTick = server.getTickCount();
        round = 0;
    }

    private void startRematch(MinecraftServer server) {
        moveWithinMelee();
        player.setShiftKeyDown(true);
        InteractionResult result = boss.interact(player, InteractionHand.MAIN_HAND,
                new Vec3(0.0, boss.getBbHeight() * 0.5, 0.0));
        player.setShiftKeyDown(false);
        require(result == InteractionResult.SUCCESS_SERVER
                        && boss.phase() == VerdictSequence.Phase.TELL,
                "Sneak-interacting with the defeated frame did not start a rematch: " + result);
        LOGGER.info("FRAMEFALL_REMATCH_STARTED tick={} playerHealth={}",
                server.getTickCount(), player.getHealth());
        round = 2;
        shots = 0;
        seenTell = false;
        seenFire = false;
        seenRecovery = false;
        previousPhase = VerdictSequence.Phase.DORMANT;
    }

    private void completeRematch(MinecraftServer server) {
        require(seenTell && seenFire && seenRecovery
                        && damagingAttacks > firstRoundDamagingAttacks
                        && (!coop || teammateDamagingAttacks > firstRoundTeammateDamagingAttacks),
                "Rematch missed tell, firing, recovery or player attacks");
        assertRewards(server, 4);
        if (coop) assertRewards(server, teammate, 4);
        LOGGER.info("FRAMEFALL_NATURAL_ENCOUNTER_PASS attacks={} damaging={} rematchShots={} "
                        + "health={} book={} frame={} shards={}", attacks, damagingAttacks, shots,
                player.getHealth(), count(Items.WRITTEN_BOOK),
                count(BossFeatures.PARALLAX_FRAME), count(Items.AMETHYST_SHARD));
        if (coop) {
            LOGGER.info("FRAMEFALL_NATURAL_COOP_PASS primaryAttacks={} primaryDamaging={} "
                            + "teammateAttacks={} teammateDamaging={} "
                            + "primaryBook={} teammateBook={} primaryFrame={} teammateFrame={} "
                            + "primaryShards={} teammateShards={}",
                    attacks, damagingAttacks, teammateAttacks, teammateDamagingAttacks,
                    count(player, Items.WRITTEN_BOOK), count(teammate, Items.WRITTEN_BOOK),
                    count(player, BossFeatures.PARALLAX_FRAME),
                    count(teammate, BossFeatures.PARALLAX_FRAME),
                    count(player, Items.AMETHYST_SHARD), count(teammate, Items.AMETHYST_SHARD));
        }
        stopAtTick = server.getTickCount() + 40;
    }

    private void assertRewards(MinecraftServer server, int expectedShards) {
        assertRewards(server, player, expectedShards);
    }

    private static void assertRewards(MinecraftServer server, ServerPlayer recipient,
                                      int expectedShards) {
        var account = CampaignDeliveryAccount.of(recipient, CampaignPersistentState.get(server));
        require(account.hasLore(LandmarkKey.FRAMEFALL)
                        && account.hasSignature(LandmarkKey.FRAMEFALL)
                        && account.hasSupplies(LandmarkKey.FRAMEFALL)
                        && count(recipient, Items.WRITTEN_BOOK) == 1
                        && count(recipient, BossFeatures.PARALLAX_FRAME) == 1
                        && count(recipient, Items.AMETHYST_SHARD) == expectedShards,
                "Boss reward count or receipts wrong for " + recipient.getGameProfile().name()
                        + ": lore=" + account.hasLore(LandmarkKey.FRAMEFALL)
                        + " signature=" + account.hasSignature(LandmarkKey.FRAMEFALL)
                        + " supplies=" + account.hasSupplies(LandmarkKey.FRAMEFALL)
                        + " books=" + count(recipient, Items.WRITTEN_BOOK)
                        + " frames=" + count(recipient, BossFeatures.PARALLAX_FRAME)
                        + " shards=" + count(recipient, Items.AMETHYST_SHARD));
    }

    private void moveNearBoss() {
        teleport(player, boss.getX() + 3.0, boss.getY(), boss.getZ());
    }

    private void moveOffLockedLane() {
        teleport(player, boss.getX() + 3.0, boss.getY(), boss.getZ() + (coop ? 5.0 : 4.0));
    }

    private void moveWithinMelee() {
        teleport(player, boss.getX() + 1.5, boss.getY(), boss.getZ());
    }

    private void moveTeammateNearBoss() {
        teleport(teammate, boss.getX() + 3.0, boss.getY(), boss.getZ() + 3.0);
    }

    private void moveTeammateOffLockedLane() {
        teleport(teammate, boss.getX() + 3.0, boss.getY(), boss.getZ() + 7.0);
    }

    private void moveTeammateWithinMelee() {
        teleport(teammate, boss.getX() + 1.5, boss.getY(), boss.getZ() + 1.5);
    }

    private static void teleport(ServerPlayer connected, double x, double y, double z) {
        if (connected.position().distanceToSqr(x, y, z) < 0.04) return;
        connected.teleportTo(connected.level(), x, y, z,
                EnumSet.noneOf(net.minecraft.world.entity.Relative.class),
                0.0F, 0.0F, false);
    }

    private int count(Item item) {
        return count(player, item);
    }

    private static int count(ServerPlayer recipient, Item item) {
        int amount = 0;
        for (int slot = 0; slot < recipient.getInventory().getContainerSize(); slot++) {
            ItemStack stack = recipient.getInventory().getItem(slot);
            if (stack.is(item)) amount += stack.getCount();
        }
        return amount;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
