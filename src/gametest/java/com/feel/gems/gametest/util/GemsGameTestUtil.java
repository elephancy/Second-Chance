package com.feel.gems.gametest.util;

import com.feel.gems.assassin.AssassinState;
import com.feel.gems.core.GemId;
import com.feel.gems.item.GemItem;
import com.feel.gems.power.runtime.GemPowers;
import com.feel.gems.state.GemPlayerState;
import com.mojang.authlib.GameProfile;
import com.feel.gems.state.GemsPersistentDataHolder;
import java.util.List;
import java.util.UUID;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.function.BooleanSupplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;




public final class GemsGameTestUtil {
    private static final int COMPLETE_DELAY_TICKS = Integer.getInteger("gems.gametest.complete-delay", 10);

    private GemsGameTestUtil() {
    }

    public static void complete(GameTestHelper context) {
        completeAfter(context, COMPLETE_DELAY_TICKS);
    }

    public static void completeAfter(GameTestHelper context, long delayTicks) {
        if (delayTicks <= 0L) {
            context.succeed();
            return;
        }
        context.runAtTickTime(delayTicks, context::succeed);
    }

    public static void assertEventually(GameTestHelper context, long startTick, long timeoutTick, long stepTicks, BooleanSupplier condition, String failMessage) {
        long step = Math.max(1L, stepTicks);
        context.runAtTickTime(startTick, () -> pollCondition(context, startTick, timeoutTick, step, condition, failMessage));
    }

    private static void pollCondition(GameTestHelper context, long currentTick, long timeoutTick, long stepTicks, BooleanSupplier condition, String failMessage) {
        if (condition.getAsBoolean()) {
            context.succeed();
            return;
        }
        if (currentTick >= timeoutTick) {
            context.fail(failMessage);
            return;
        }
        context.runAtTickTime(currentTick + stepTicks, () -> pollCondition(context, currentTick + stepTicks, timeoutTick, stepTicks, condition, failMessage));
    }

    public static void assertStaysTrue(GameTestHelper context, long startTick, long endTick, long stepTicks, BooleanSupplier condition, String failMessage) {
        long step = Math.max(1L, stepTicks);
        context.runAtTickTime(startTick, () -> pollStaysTrue(context, startTick, endTick, step, condition, failMessage));
    }

    private static void pollStaysTrue(GameTestHelper context, long currentTick, long endTick, long stepTicks, BooleanSupplier condition, String failMessage) {
        if (!condition.getAsBoolean()) {
            context.fail(failMessage);
            return;
        }
        if (currentTick >= endTick) {
            context.succeed();
            return;
        }
        context.runAtTickTime(currentTick + stepTicks, () -> pollStaysTrue(context, currentTick + stepTicks, endTick, stepTicks, condition, failMessage));
    }

    public static void placeStoneFloor(GameTestHelper context, int radius) {
        placeStoneFloor(context, 1, radius);
    }

    public static void placeStoneFloor(GameTestHelper context, int y, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                context.setBlock(x, y, z, Blocks.STONE.defaultBlockState());
            }
        }
    }

    public static void forceSurvival(ServerPlayer player) {
        player.setGameMode(GameType.SURVIVAL);
        player.setPermanentlyInvulnerable(false);
        var abilities = player.getAbilities();
        abilities.invulnerable = false;
        abilities.instabuild = false;
        abilities.mayfly = false;
        abilities.flying = false;
        player.onUpdateAbilities();

        // GameTests run with `gamerule pvp` off by default; player-vs-player damage relies on this being true.
        if (player.level() instanceof ServerLevel world) {
            var server = world.getServer();
            if (server != null) {
                world.getGameRules().set(GameRules.PVP, true, server);
            }
        }

        // GameTest players can end up on the same scoreboard team with friendly-fire disabled, which blocks PvP damage.
        var team = player.getTeam();
        if (team != null && player.level().getServer() != null) {
            player.level().getServer().getScoreboard().removePlayerFromTeam(player.getScoreboardName(), team);
        }
    }

    public static boolean hasItem(ServerPlayer player, Item item) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                return true;
            }
        }
        return false;
    }

    public static int countItem(ServerPlayer player, Item item) {
        int count = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public static int countGlint(ServerPlayer player, Item item) {
        int glint = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item) && stack.has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)) {
                glint++;
            }
        }
        return glint;
    }

    public static int countGemItems(List<ItemStack> stacks) {
        int gemItems = 0;
        for (ItemStack stack : stacks) {
            if (stack.getItem() instanceof GemItem) {
                gemItems++;
            }
        }
        return gemItems;
    }

    public static int countGemItems(ServerPlayer player) {
        var inventory = player.getInventory();
        int gemItems = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof GemItem) {
                gemItems++;
            }
        }
        return gemItems;
    }

    public static boolean containsAirMace(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (isAirMace(inventory.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    public static int countAirMaces(ServerPlayer player) {
        int count = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (isAirMace(inventory.getItem(i))) {
                count++;
            }
        }
        return count;
    }

    public static void resetAssassinState(ServerPlayer player) {
        AssassinState.initIfNeeded(player);
        var data = ((GemsPersistentDataHolder) player).gems$getPersistentData();
        data.putBoolean("assassinIsAssassin", false);
        data.putBoolean("assassinEliminated", false);
        data.putInt("assassinHearts", AssassinState.maxHearts());
    }

    public static void resetPlayerForTest(ServerPlayer player) {
        player.removeAllEffects();
        player.setRemainingFireTicks(0);
        player.clearFire();
        player.setDeltaMovement(Vec3.ZERO);
        player.setAbsorptionAmount(0.0F);

        var inst = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (inst != null) {
            inst.setBaseValue(20.0D);
        }
        player.setHealth(player.getMaxHealth());

        var hunger = player.getFoodData();
        hunger.setFoodLevel(20);
        hunger.setSaturation(5.0F);
        hunger.addExhaustion(-10.0F);

        player.getInventory().clearContent();
        player.experienceLevel = 0;
        player.totalExperience = 0;
        player.experienceProgress = 0.0F;
        player.releaseUsingItem();

        if (player.level() instanceof ServerLevel world && world.getServer() != null) {
            com.feel.gems.bonus.BonusClaimsState.get(world.getServer()).releaseAllClaims(player.getUUID());
        }
    }

    public static ServerPlayer createMockCreativeServerPlayer(GameTestHelper context) {
        // TestContext#createMockCreativeServerPlayerInWorld returns a ServerPlayerEntity subclass whose
        // getGameMode() is hard-overridden to CREATIVE, which makes it impossible to test survival PvP damage.
        // We build an actual ServerPlayerEntity + connect it through PlayerManager so game mode is real and mutable.
        ServerLevel world = context.getLevel();
        var server = world.getServer();
        if (server == null) {
            throw new IllegalStateException("GameTest server is null");
        }

        // Game profiles must have a <=16 char name using [A-Za-z0-9_]; otherwise things like player heads fail to serialize.
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        GameProfile profile = new GameProfile(UUID.randomUUID(), "test" + suffix);
        CommonListenerCookie data = CommonListenerCookie.createInitial(profile, false);

        ServerPlayer player = new ServerPlayer(server, world, data.gameProfile(), data.clientInformation());

        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);

        server.getPlayerList().placeNewPlayer(connection, player, data);

        // Newly-connected players are considered "loading" for ~60 ticks and are invulnerable until loaded.
        // GameTests commonly apply damage very early (e.g., tick 5), so fast-forward the loading timer.
        if (player.connection != null) {
            for (int i = 0; i < 80 && !player.connection.hasClientLoaded(); i++) {
                player.connection.tickClientLoadTimeout();
            }
        }

        // Ensure a clean per-player state between GameTests, even if the run directory persisted playerdata.
        ((GemsPersistentDataHolder) player).gems$setPersistentData(new net.minecraft.nbt.CompoundTag());
        resetPlayerForTest(player);

        // GameTests should be deterministic. The production init flow assigns a random gem at first join,
        // which makes tests flaky (e.g., the "enemy" rolling VOID and becoming immune to effects).
        GemPlayerState.resetToNew(player, GemId.ASTRA);
        GemPowers.sync(player);
        return player;
    }

    private static boolean isAirMace(ItemStack stack) {
        if (!stack.is(Items.MACE)) {
            return false;
        }
        var custom = stack.get(DataComponents.CUSTOM_DATA);
        return custom != null && custom.copyTag().getBooleanOr("gemsAirMace", false);
    }
}
