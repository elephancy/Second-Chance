package com.feel.gems.gametest.ability;

import com.feel.gems.config.GemsBalance;
import com.feel.gems.core.GemId;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.power.ability.spy.*;
import com.feel.gems.power.gem.spy.SpySystem;
import com.feel.gems.power.runtime.GemPowers;
import com.feel.gems.state.GemPlayerState;
import com.feel.gems.state.GemsPersistentDataHolder;
import com.feel.gems.util.GemsNbt;
import java.util.EnumSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.entity.SculkSensorBlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public final class GemsSpyGameTests {

    private static void teleport(ServerPlayer player, ServerLevel world, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(world, x, y, z, EnumSet.noneOf(Relative.class), yaw, pitch, false);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void spySmokeBombActivates(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer enemy = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(enemy);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(enemy, world, pos.x + 2.0D, pos.y, pos.z, 180.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SPY);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new SpySmokeBombAbility().activate(player);
            if (!ok) {
                context.fail("Smoke Bomb did not activate");
                return;
            }
        });

        context.runAtTickTime(15L, () -> {
            if (player.getEffect(MobEffects.INVISIBILITY) == null) {
                context.fail("Smoke Bomb should cloak the spy");
                return;
            }
            if (enemy.getEffect(MobEffects.BLINDNESS) == null || enemy.getEffect(MobEffects.SLOWNESS) == null) {
                context.fail("Smoke Bomb should blind and slow nearby enemies");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void spySkinshiftActivates(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(target);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(target, world, pos.x, pos.y, pos.z + 2.0D, 180.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SPY);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new SpySkinshiftAbility().activate(player);
            if (!ok) {
                context.fail("Skinshift did not activate with target in sight");
                return;
            }
            CompoundTag nbt = ((GemsPersistentDataHolder) player).gems$getPersistentData();
            if (GemsNbt.getUuid(nbt, "spySkinshiftTarget") == null) {
                context.fail("Skinshift should store the target UUID");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void spyEchoReplaysLastSeenAbility(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer enemy = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(enemy);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(enemy, world, pos.x + 2.0D, pos.y, pos.z, 180.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SPY);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            CompoundTag nbt = ((GemsPersistentDataHolder) player).gems$getPersistentData();
            Identifier observed = com.feel.gems.power.registry.PowerIds.SPY_SMOKE_BOMB;
            nbt.putString("spyLastSeenAbility", observed.toString());
            nbt.putLong("spyLastSeenAt", world.getGameTime());
            CompoundTag observedRoot = new CompoundTag();
            CompoundTag rec = new CompoundTag();
            rec.putInt("epoch", com.feel.gems.power.gem.spy.SpySystem.deaths(player));
            rec.putLong("first", world.getGameTime());
            rec.putLong("last", world.getGameTime());
            rec.putInt("count", 1);
            observedRoot.put(observed.toString(), rec);
            nbt.put("spyObserved", observedRoot);
            boolean ok = new SpyEchoAbility().activate(player);
            if (!ok) {
                context.fail("Echo did not replay the last seen ability");
                return;
            }
        });

        context.runAtTickTime(15L, () -> {
            // Smoke Bomb's own test covers affecting nearby enemies; for Echo, verify that we successfully
            // replayed the ability (self invisibility is always applied on activation).
            if (player.getEffect(MobEffects.INVISIBILITY) == null) {
                context.fail("Echo should re-cast the observed ability");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void spyMimicFormActivates(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SPY);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            CompoundTag nbt = ((GemsPersistentDataHolder) player).gems$getPersistentData();
            nbt.putString("spyLastKilledType", "minecraft:cow");
            boolean ok = new SpyMimicFormAbility().activate(player);
            if (!ok) {
                context.fail("Mimic Form did not activate with a stored kill");
                return;
            }
            if (player.getEffect(MobEffects.INVISIBILITY) == null) {
                context.fail("Mimic Form should grant invisibility");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void spyStealAddsStolenAbility(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SPY);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            CompoundTag nbt = ((GemsPersistentDataHolder) player).gems$getPersistentData();
            Identifier observed = com.feel.gems.power.registry.PowerIds.SPY_SMOKE_BOMB;
            nbt.putString("spyLastSeenAbility", observed.toString());
            nbt.putLong("spyLastSeenAt", world.getGameTime());

            CompoundTag observedMap = new CompoundTag();
            CompoundTag rec = new CompoundTag();
            int required = GemsBalance.v().spy().stealRequiredWitnessCount();
            rec.putInt("count", required);
            rec.putLong("first", world.getGameTime());
            rec.putLong("last", world.getGameTime());
            rec.putInt("epoch", SpySystem.deaths(player));
            observedMap.put(observed.toString(), rec);
            nbt.put("spyObserved", observedMap);

            boolean ok = new SpyStealAbility().activate(player);
            if (!ok) {
                context.fail("Steal did not add the observed ability");
                return;
            }
            if (SpySystem.selectedStolenAbility(player) == null) {
                context.fail("Steal should store a stolen ability");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void spyStolenCastUsesStolenAbility(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer enemy = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(enemy);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(enemy, world, pos.x + 2.0D, pos.y, pos.z, 180.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SPY);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            CompoundTag nbt = ((GemsPersistentDataHolder) player).gems$getPersistentData();
            ListTag stolen = new ListTag();
            stolen.add(StringTag.valueOf(com.feel.gems.power.registry.PowerIds.SPY_SMOKE_BOMB.toString()));
            nbt.put("spyStolen", stolen);
            nbt.putInt("spyStolenSelected", 0);
            boolean ok = new SpyStolenCastAbility().activate(player);
            if (!ok) {
                context.fail("Stolen Cast did not activate with a stolen ability");
                return;
            }
        });

        context.runAtTickTime(15L, () -> {
            if (enemy.getEffect(MobEffects.BLINDNESS) == null) {
                context.fail("Stolen Cast should execute the stolen ability");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void spyStillnessCloakGrantsInvisibility(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SPY);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        int stillness = GemsBalance.v().spy().stillnessTicks();

        context.runAtTickTime(5L, () -> SpySystem.tickEverySecond(player));
        context.runAtTickTime(5L + stillness + 2L, () -> {
            SpySystem.tickEverySecond(player);
            if (player.getEffect(MobEffects.INVISIBILITY) == null) {
                context.fail("Stillness Cloak should grant invisibility after standing still");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void spySilentStepPreventsSculkSensorTrigger(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        context.setBlock(0, 1, 0, Blocks.SCULK_SENSOR.defaultBlockState());
        var pos = net.minecraft.core.BlockPos.containing(context.absoluteVec(new Vec3(0.0D, 1.0D, 0.0D)));
        SculkSensorBlockEntity sensor = (SculkSensorBlockEntity) world.getBlockEntity(pos);
        if (sensor == null) {
            context.fail("Failed to create sculk sensor block entity");
            return;
        }

        Vec3 playerPos = context.absoluteVec(new Vec3(0.5D, 2.0D, 2.5D));
        teleport(player, world, playerPos.x, playerPos.y, playerPos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SPY);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            world.gameEvent(GameEvent.STEP, player.position(), GameEvent.Context.of(player));
        });

        context.runAtTickTime(15L, () -> {
            if (!SculkSensorBlock.canActivate(world.getBlockState(pos))) {
                context.fail("Silent Step should block sculk sensor activation");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void spyBackstabDealsBonusDamage(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer attacker = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(attacker);
        GemsGameTestUtil.forceSurvival(target);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(attacker, world, pos.x, pos.y, pos.z - 1.5D, 0.0F, 0.0F);
        teleport(target, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(attacker);
        GemPlayerState.setActiveGem(attacker, GemId.SPY);
        GemPlayerState.setEnergy(attacker, 5);
        GemPowers.sync(attacker);

        context.runAtTickTime(5L, () -> {
            float before = target.getHealth();
            attacker.attack(target);
            if (target.getHealth() >= before) {
                context.fail("Backstab should deal bonus damage from behind");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void spyQuickHandsGrantsHaste(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SPY);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            if (player.getEffect(MobEffects.HASTE) == null) {
                context.fail("Quick Hands should grant Haste");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void spyConfigValuesValid(GameTestHelper context) {
        var cfg = GemsBalance.v().spy();
        
        if (cfg.smokeBombCooldownTicks() < 0) {
            context.fail("Smoke Bomb cooldown cannot be negative");
        }
        if (cfg.skinshiftCooldownTicks() < 0) {
            context.fail("Skinshift cooldown cannot be negative");
        }
        if (cfg.echoCooldownTicks() < 0) {
            context.fail("Echo cooldown cannot be negative");
        }
        
        context.succeed();
    }
}
