package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.CampaignDeliveryAccount;
import com.feel.gems.adventure.CampaignPersistentState;
import com.feel.gems.adventure.LandmarkKey;
import com.feel.gems.adventure.boss.BossFeatures;
import com.feel.gems.adventure.boss.RedLineBetweenEntity;
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
import net.minecraft.world.effect.MobEffects;
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

/** Opt-in test-only scripted survival encounter at a natural Redline House. */
public final class RedlineEncounterSmoke implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("gems-redline-encounter-smoke");
    private static final UUID PLAYER_ID = UUID.fromString("61e99547-556d-4e82-88f4-f80936829a36");
    private static final TagKey<net.minecraft.world.level.levelgen.structure.Structure> REDLINE =
            TagKey.create(Registries.STRUCTURE,
                    Identifier.fromNamespaceAndPath("gems", "atlas_target/redline_house"));
    private static final int TIMEOUT_TICKS = 5200;

    private ServerPlayer player;
    private RedLineBetweenEntity boss;
    private BlockPos found;
    private BlockPos center;
    private int preparedTick;
    private int startTick;
    private int stopAtTick;
    private int shelterTick;
    private int firstVictoryTick;
    private int firstRoundDamageHits;
    private int round = 1;
    private int attacks;
    private int damagingAttacks;
    private float lowestHealth = Float.MAX_VALUE;
    private boolean sawLineStrike;

    @Override
    public void onInitialize() {
        if (!Boolean.getBoolean("gems.redlineEncounterSmoke")) return;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (stopAtTick > 0) {
                if (server.getTickCount() >= stopAtTick) server.halt(false);
                return;
            }
            try {
                if (player == null) {
                    if (server.getTickCount() < 40 || !setup(server)) return;
                }
                int now = server.getTickCount();
                require(now - startTick < TIMEOUT_TICKS, "Natural Redline fight timed out");
                require(server.getPlayerList().getPlayer(PLAYER_ID) == player && player.isAlive(),
                        "Connected survival player left or died");
                if (now - startTick == 200) {
                    require(boss.tickCount >= 50, "Natural boss was not entity-ticking");
                    LOGGER.info("REDLINE_TICK_DIAGNOSTIC bossTicks={} playerTicks={} bossPos={}",
                            boss.tickCount, player.tickCount, boss.blockPosition());
                }
                if (round == 0) {
                    if (now - firstVictoryTick >= 120) startRematch(server);
                } else {
                    driveCombat(server);
                }
            } catch (Exception failure) {
                LOGGER.error("REDLINE_NATURAL_ENCOUNTER_FAILED", failure);
                stopAtTick = server.getTickCount() + 40;
            }
        });
    }

    private boolean setup(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (found == null) {
            found = level.findNearestMapStructure(REDLINE, new BlockPos(0, 64, 0), 128, false);
            require(found != null, "No natural Redline House start within 128 chunks");
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int chunkX = (found.getX() >> 4) + dx;
                    int chunkZ = (found.getZ() >> 4) + dz;
                    level.getChunk(chunkX, chunkZ);
                    level.setChunkForced(chunkX, chunkZ, true);
                }
            }
            preparedTick = server.getTickCount();
            LOGGER.info("REDLINE_NATURAL_PREPARED start={} tick={}", found, preparedTick);
            return false;
        }
        if (server.getTickCount() - preparedTick < 10) return false;
        var bosses = level.getEntitiesOfClass(RedLineBetweenEntity.class,
                new AABB(found.getX() - 80, level.getMinY(), found.getZ() - 80,
                        found.getX() + 80, level.getMaxY(), found.getZ() + 80));
        require(bosses.size() == 1,
                "Expected one natural Red Line Between, found " + bosses.size());
        boss = bosses.getFirst();
        center = boss.blockPosition();
        require(level.getBlockState(center.below()).is(Blocks.LODESTONE),
                "Boss is not on the authored arena anchor");
        for (BlockPos pad : pads()) {
            require(level.getBlockState(pad).is(Blocks.CONCRETE.yellow()),
                    "An authored gold shelter pad is missing: " + pad);
        }
        player = connect(server, level);
        equip(player);
        teleport(center.getX() + 3.5, center.getY(), center.getZ() + 0.5);
        startTick = server.getTickCount();
        lowestHealth = player.getHealth();
        LOGGER.info("REDLINE_NATURAL_FOUND start={} boss={} health={} shelterPads=4",
                found, center, player.getHealth());
        return true;
    }

    private static ServerPlayer connect(MinecraftServer server, ServerLevel level) {
        GameProfile profile = new GameProfile(PLAYER_ID, "RedlineProbe");
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
        int now = server.getTickCount();
        if ((now - startTick) % 200 == 0) {
            LOGGER.info("REDLINE_ROUND_{}_DIAGNOSTIC tick={} bossPos={} bossHealth={} "
                            + "playerHealth={} attacks={} damaging={}",
                    round, now, boss.blockPosition(), boss.getHealth(),
                    player.getHealth(), attacks, damagingAttacks);
        }
        if (round == 1 && shelterTick == 0) {
            // An east approach chooses the east shelter. Stay on its actual pad
            // until the boss grants the shelter effect before its lock.
            teleport(center.getX() + 9.5, center.getY(), center.getZ() + 0.5);
            if (player.hasEffect(MobEffects.ABSORPTION)) {
                shelterTick = now;
                LOGGER.info("REDLINE_SHELTER_RESCUE tick={} pad={} effect=absorption",
                        now, center.east(9).below());
            }
            return;
        }
        if (round == 1 && !sawLineStrike) {
            // After the rescued cycle, the plan moves from east to south.
            // Miss that shelter and stand on its visible line for a real hit.
            if (now - shelterTick >= 141) {
                teleport(center.getX() + 0.5, center.getY(), center.getZ() + 3.5);
            } else {
                teleport(center.getX() + 9.5, center.getY(), center.getZ() + 0.5);
            }
            if (player.getHealth() < lowestHealth) {
                lowestHealth = player.getHealth();
                sawLineStrike = true;
                LOGGER.info("REDLINE_LINE_STRIKE tick={} health={} delayAfterRescue={}",
                        now, lowestHealth, now - shelterTick);
            }
            return;
        }
        // Fight with the player's normal attack path and the boss's guarded
        // health, hit immunity, and rotating shelter cycles in force.
        // Two blocks diagonally from the anchor remains in melee range while
        // clearing all four cardinal line corridors.
        teleport(center.getX() + 2.0, center.getY(), center.getZ() + 2.0);
        if (now % 15 == 0) {
            require(player.distanceToSqr(boss) <= 9.0,
                    "Scripted player is beyond melee reach of the natural guard: "
                            + player.distanceToSqr(boss));
            require(boss.position().distanceToSqr(Vec3.atBottomCenterOf(center)) < 1.0,
                    "Natural guard drifted off its authored shelter anchor: " + boss.blockPosition());
            float before = boss.getHealth();
            attacks++;
            player.attack(boss);
            if (boss.getHealth() < before) damagingAttacks++;
        }
        if (round == 1 && CampaignPersistentState.get(server).model()
                .hasCleared(PLAYER_ID, LandmarkKey.REDLINE)) {
            completeFirstClear(server);
        } else if (round == 2 && count(Items.GOLDEN_CARROT) == 6) {
            completeRematch(server);
        }
    }

    private void completeFirstClear(MinecraftServer server) {
        require(shelterTick > 0 && sawLineStrike && damagingAttacks > 0,
                "First encounter missed shelter rescue, line strike, or player attack damage");
        for (BlockPos pad : pads()) {
            require(server.overworld().getBlockState(pad).is(Blocks.CONCRETE.lime()),
                    "First victory did not restore a shelter pad: " + pad);
        }
        assertRewards(server, 3);
        LOGGER.info("REDLINE_FIRST_CLEAR_PASS attacks={} damaging={} shelterTick={} "
                        + "lowestHealth={} book={} line={} carrots={} padsRestored=4",
                attacks, damagingAttacks, shelterTick, lowestHealth,
                count(Items.WRITTEN_BOOK), count(BossFeatures.RESCUE_LINE),
                count(Items.GOLDEN_CARROT));
        firstRoundDamageHits = damagingAttacks;
        firstVictoryTick = server.getTickCount();
        round = 0;
    }

    private void startRematch(MinecraftServer server) {
        teleport(boss.getX() + 1.5, boss.getY(), boss.getZ());
        InteractionResult result = boss.interact(player, InteractionHand.MAIN_HAND,
                new Vec3(0.0, boss.getBbHeight() * 0.5, 0.0));
        require(result == InteractionResult.SUCCESS_SERVER,
                "Interacting with the defeated guard did not start a rematch: " + result);
        player.setHealth(player.getMaxHealth());
        LOGGER.info("REDLINE_REMATCH_STARTED tick={} health={}",
                server.getTickCount(), player.getHealth());
        round = 2;
    }

    private void completeRematch(MinecraftServer server) {
        require(damagingAttacks > firstRoundDamageHits,
                "Rematch had no new player attack damage");
        assertRewards(server, 6);
        LOGGER.info("REDLINE_NATURAL_ENCOUNTER_PASS attacks={} damaging={} health={} "
                        + "book={} line={} carrots={}", attacks, damagingAttacks,
                player.getHealth(), count(Items.WRITTEN_BOOK),
                count(BossFeatures.RESCUE_LINE), count(Items.GOLDEN_CARROT));
        stopAtTick = server.getTickCount() + 40;
    }

    private void assertRewards(MinecraftServer server, int expectedCarrots) {
        var account = CampaignDeliveryAccount.of(player, CampaignPersistentState.get(server));
        require(account.hasLore(LandmarkKey.REDLINE)
                        && account.hasSignature(LandmarkKey.REDLINE)
                        && account.hasSupplies(LandmarkKey.REDLINE)
                        && count(Items.WRITTEN_BOOK) == 1
                        && count(BossFeatures.RESCUE_LINE) == 1
                        && count(Items.GOLDEN_CARROT) == expectedCarrots,
                "Redline rewards or saved receipts wrong: lore=" + account.hasLore(LandmarkKey.REDLINE)
                        + " signature=" + account.hasSignature(LandmarkKey.REDLINE)
                        + " supplies=" + account.hasSupplies(LandmarkKey.REDLINE)
                        + " books=" + count(Items.WRITTEN_BOOK)
                        + " lines=" + count(BossFeatures.RESCUE_LINE)
                        + " carrots=" + count(Items.GOLDEN_CARROT));
    }

    private BlockPos[] pads() {
        return new BlockPos[] {center.north(9).below(), center.east(9).below(),
                center.south(9).below(), center.west(9).below()};
    }

    private int count(Item item) {
        int amount = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) amount += stack.getCount();
        }
        return amount;
    }

    private void teleport(double x, double y, double z) {
        if (player.position().distanceToSqr(x, y, z) < 0.04) return;
        player.teleportTo(player.level(), x, y, z,
                EnumSet.noneOf(net.minecraft.world.entity.Relative.class),
                0.0F, 0.0F, false);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
