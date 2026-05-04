package com.feel.gems.gametest.ability;

import net.minecraft.world.entity.EntityTypes;
import com.feel.gems.config.GemsBalance;
import com.feel.gems.core.GemId;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.power.ability.summoner.*;
import com.feel.gems.power.gem.summoner.SummonerSummons;
import com.feel.gems.power.runtime.GemPowers;
import com.feel.gems.state.GemPlayerState;
import java.util.EnumSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

public final class GemsSummonerGameTests {

    private static void teleport(ServerPlayer player, ServerLevel world, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(world, x, y, z, EnumSet.noneOf(Relative.class), yaw, pitch, false);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void summonerSummonSlotActivates(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SUMMONER);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            int before = world.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(12.0D), SummonerSummons::isSummon).size();
            boolean ok = new SummonSlotAbility(1).activate(player);
            if (!ok) {
                context.fail("Summon Slot did not activate");
            }
            int after = world.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(12.0D), SummonerSummons::isSummon).size();
            if (after <= before) {
                context.fail("Summon Slot should spawn summons");
            }
            Mob base = EntityTypes.ZOMBIE.create(world, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
            Mob spawned = world.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(12.0D), SummonerSummons::isSummon)
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (base != null && spawned != null) {
                float bonus = GemsBalance.v().summoner().summonBonusHealth();
                if (bonus > 0.0F && spawned.getMaxHealth() <= base.getMaxHealth()) {
                    context.fail("Familiar's Blessing should increase summon max health");
                }
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void summonerRecallActivates(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SUMMONER);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new SummonSlotAbility(1).activate(player);
            if (!ok) {
                context.fail("Summon Slot did not activate");
            }
        });

        context.runAtTickTime(20L, () -> {
            boolean ok = new SummonRecallAbility().activate(player);
            if (!ok) {
                context.fail("Summon Recall did not activate");
            }
            int remaining = SummonerSummons.pruneAndCount(player);
            if (remaining > 0) {
                context.fail("Summon Recall should despawn summons");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void summonerConfigValuesValid(GameTestHelper context) {
        var cfg = GemsBalance.v().summoner();
        
        if (cfg.summonSlotCooldownTicks() < 0) {
            context.fail("Summon Slot cooldown cannot be negative");
        }
        if (cfg.recallCooldownTicks() < 0) {
            context.fail("Recall cooldown cannot be negative");
        }
        if (cfg.maxActiveSummons() <= 0) {
            context.fail("Max summons must be positive");
        }
        
        context.succeed();
    }
}
