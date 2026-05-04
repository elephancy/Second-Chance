package com.feel.gems.gametest.item;

import com.feel.gems.assassin.AssassinState;
import com.feel.gems.config.GemsBalance;
import com.feel.gems.core.GemId;
import com.feel.gems.event.GemsPlayerDeath;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.item.ModItems;
import com.feel.gems.state.GemPlayerState;
import java.util.EnumSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;




public final class GemsHeartAndAssassinGameTests {
    private static void teleport(ServerPlayer player, ServerLevel world, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(world, x, y, z, EnumSet.noneOf(Relative.class), yaw, pitch, false);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void assassinConversionAndHeartsApplied(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        Vec3 startPos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, startPos.x, startPos.y, startPos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        AssassinState.initIfNeeded(player);

        // Force conversion using the same persistent path the death mixin uses.
        AssassinState.becomeAssassin(player);
        AssassinState.addAssassinHearts(player, -2); // 8 hearts
        GemPlayerState.applyMaxHearts(player);

        context.runAtTickTime(2L, () -> {
            if (!AssassinState.isAssassin(player)) {
                context.fail("Expected player to be assassin after becomeAssassin");
            }
            if (AssassinState.getAssassinHearts(player) != 8) {
                context.fail("Expected assassin hearts to be 8, got " + AssassinState.getAssassinHearts(player));
            }
            double maxHealth = player.getAttributeValue(Attributes.MAX_HEALTH);
            if (Math.abs(maxHealth - 16.0D) > 0.01D) {
                context.fail("Expected max health 16.0 for 8 hearts, got " + maxHealth);
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 160)
    public void heartDropsAboveFloorAndStopsAtFive(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer killer = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer victim = GemsGameTestUtil.createMockCreativeServerPlayer(context);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(killer, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(victim, world, pos.x + 1.0D, pos.y, pos.z, 180.0F, 0.0F);

        context.runAtTickTime(1L, () -> {
            GemsGameTestUtil.forceSurvival(killer);
            GemsGameTestUtil.forceSurvival(victim);
            teleport(killer, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
            teleport(victim, world, pos.x + 1.0D, pos.y, pos.z, 180.0F, 0.0F);

            GemPlayerState.initIfNeeded(killer);
            GemPlayerState.initIfNeeded(victim);
            GemPlayerState.setActiveGem(killer, GemId.FIRE);
            GemPlayerState.setActiveGem(victim, GemId.LIFE);
            GemsGameTestUtil.resetAssassinState(victim);
            int minHearts = GemPlayerState.minMaxHearts();
            GemPlayerState.setMaxHearts(victim, minHearts + 1);
            GemPlayerState.applyMaxHearts(victim);
        });

        context.runAtTickTime(5L, () -> GemsPlayerDeath.onDeathTail(victim, victim.damageSources().playerAttack(killer)));

        context.runAtTickTime(40L, () -> {
            int heartsDropped = world.getEntitiesOfClass(ItemEntity.class, new AABB(pos, pos.add(1.0D, 1.0D, 1.0D)).inflate(3.0D), e -> e.getItem().is(ModItems.HEART)).size();
	            int invHearts = GemsGameTestUtil.countItem(killer, ModItems.HEART);
	            var victimDataAfter = ((com.feel.gems.state.GemsPersistentDataHolder) victim).gems$getPersistentData();
	            if (heartsDropped + invHearts != 1) {
	                context.fail("Expected exactly one heart gained (dropped or picked up), found dropped=" + heartsDropped + " inv=" + invHearts + " assassin=" + victimDataAfter.getBoolean("assassinIsAssassin").orElse(false) + " storedMax=" + victimDataAfter.getInt("maxHearts").orElse(0) + " assassinHearts=" + victimDataAfter.getInt("assassinHearts").orElse(0));
	                return;
	            }
            int storedHearts = victimDataAfter.getInt("maxHearts").orElse(0);
            if (storedHearts != GemPlayerState.minMaxHearts()) {
                context.fail("Victim hearts should clamp to floor after drop: " + storedHearts);
                return;
            }
            boolean becameAssassin = victimDataAfter.getBoolean("assassinIsAssassin").orElse(false);
            if (becameAssassin) {
                context.fail("Victim above floor should not convert to assassin");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 160)
    public void heartAtFloorDoesNotDropAndTurnsAssassin(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer killer = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer victim = GemsGameTestUtil.createMockCreativeServerPlayer(context);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(killer, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(victim, world, pos.x + 1.0D, pos.y, pos.z, 180.0F, 0.0F);

        context.runAtTickTime(1L, () -> {
            GemsGameTestUtil.forceSurvival(killer);
            GemsGameTestUtil.forceSurvival(victim);
            teleport(killer, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
            teleport(victim, world, pos.x + 1.0D, pos.y, pos.z, 180.0F, 0.0F);

            GemPlayerState.initIfNeeded(killer);
            GemPlayerState.initIfNeeded(victim);
            GemPlayerState.setActiveGem(killer, GemId.FIRE);
            GemPlayerState.setActiveGem(victim, GemId.LIFE);
            GemsGameTestUtil.resetAssassinState(victim);
            int minHearts = GemPlayerState.minMaxHearts();
            GemPlayerState.setMaxHearts(victim, minHearts);
            GemPlayerState.applyMaxHearts(victim);
        });

        context.runAtTickTime(5L, () -> GemsPlayerDeath.onDeathTail(victim, victim.damageSources().playerAttack(killer)));

        context.runAtTickTime(40L, () -> {
            int heartsDropped = world.getEntitiesOfClass(ItemEntity.class, new AABB(pos, pos.add(1.0D, 1.0D, 1.0D)).inflate(3.0D), e -> e.getItem().is(ModItems.HEART)).size();
            var victimDataAfter = ((com.feel.gems.state.GemsPersistentDataHolder) victim).gems$getPersistentData();
            if (heartsDropped != 0) {
                context.fail("No hearts should drop at the floor (" + GemPlayerState.minMaxHearts() + " hearts)");
                return;
            }
            int invHearts = GemsGameTestUtil.countItem(killer, ModItems.HEART);
            if (invHearts != 0) {
                context.fail("Killer should not receive a heart when victim at floor");
                return;
            }
            boolean becameAssassin = victimDataAfter.getBoolean("assassinIsAssassin").orElse(false);
	            int triggerHearts = Math.max(GemPlayerState.minMaxHearts(), GemsBalance.v().systems().assassinTriggerHearts());
	            if (!becameAssassin) {
	                context.fail("Victim at " + triggerHearts + " hearts should be converted to assassin on death (storedMax=" + victimDataAfter.getInt("maxHearts").orElse(0) + " assassinHearts=" + victimDataAfter.getInt("assassinHearts").orElse(0) + ")");
	                return;
	            }
            context.succeed();
        });
    }

}

