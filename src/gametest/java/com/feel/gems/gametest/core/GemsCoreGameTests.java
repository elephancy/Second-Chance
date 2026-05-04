package com.feel.gems.gametest.core;

import com.feel.gems.core.GemDefinition;
import com.feel.gems.core.GemEnergyState;
import com.feel.gems.core.GemId;
import com.feel.gems.core.GemRegistry;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.item.GemKeepOnDeath;
import com.feel.gems.item.GemOwnership;
import com.feel.gems.item.ModItems;
import com.feel.gems.net.CooldownSnapshotPayload;
import com.feel.gems.power.registry.PowerIds;
import com.feel.gems.power.runtime.AbilityDisables;
import com.feel.gems.power.runtime.GemAbilities;
import com.feel.gems.power.runtime.GemAbilityCooldowns;
import com.feel.gems.power.runtime.GemPowers;
import com.feel.gems.state.GemPlayerState;
import com.feel.gems.util.GemsTime;
import io.netty.buffer.Unpooled;
import java.util.EnumSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import com.feel.gems.event.GemsPlayerDeath;




public final class GemsCoreGameTests {
    private static void teleport(ServerPlayer player, ServerLevel world, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(world, x, y, z, EnumSet.noneOf(Relative.class), yaw, pitch, false);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void deathKeepsActiveGemOnly(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        Vec3 startPos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, startPos.x, startPos.y, startPos.z, 0.0F, 0.0F);

        // GameTest creates "embedded" players and runs normal join callbacks; to avoid racey interactions with
        // JOIN/COPY_FROM logic (e.g. ensureActiveGemItem), perform test setup after the player is fully connected.
        context.runAtTickTime(20L, () -> {
            player.getInventory().clearContent();
            GemPlayerState.initIfNeeded(player);
            GemPlayerState.setActiveGem(player, GemId.ASTRA);
            player.addItem(new ItemStack(ModItems.ASTRA_GEM));
            player.addItem(new ItemStack(ModItems.FIRE_GEM));
        });

        // GameTest servers often run with gamerules that can affect drops (e.g. keepInventory).
        // Validate the core stash/restore behavior directly:
        // - stash removes exactly one active gem item (kept across death)
        // - stash does not remove non-active gems
        // - restore returns the active gem item
        context.runAtTickTime(40L, () -> {
            if (!GemsGameTestUtil.hasItem(player, ModItems.ASTRA_GEM)) {
                context.fail("Setup error: missing active Astra gem item");
            }
            if (!GemsGameTestUtil.hasItem(player, ModItems.FIRE_GEM)) {
                context.fail("Setup error: missing non-active Fire gem item");
            }

            int astraBefore = GemsGameTestUtil.countItem(player, ModItems.ASTRA_GEM);
            int fireBefore = GemsGameTestUtil.countItem(player, ModItems.FIRE_GEM);

            GemKeepOnDeath.stash(player);

            int astraAfterStash = GemsGameTestUtil.countItem(player, ModItems.ASTRA_GEM);
            int fireAfterStash = GemsGameTestUtil.countItem(player, ModItems.FIRE_GEM);

            if (astraAfterStash != Math.max(0, astraBefore - 1)) {
                context.fail("Active gem item should decrement by 1 after stash, before=" + astraBefore + " after=" + astraAfterStash);
            }
            if (fireAfterStash != fireBefore) {
                context.fail("Non-active gem item should not change after stash, before=" + fireBefore + " after=" + fireAfterStash);
            }

            GemKeepOnDeath.restore(player);
            int astraAfterRestore = GemsGameTestUtil.countItem(player, ModItems.ASTRA_GEM);
            if (astraAfterRestore != astraBefore) {
                context.fail("Active gem item should be restored after stash+restore, before=" + astraBefore + " after=" + astraAfterRestore);
            }

            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 120)
    public void killingPlayerConsumesTheirActiveGemItem(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer killer = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer victim = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        killer.setGameMode(GameType.SURVIVAL);
        victim.setGameMode(GameType.SURVIVAL);

        Vec3 base = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(killer, world, base.x, base.y, base.z, 0.0F, 0.0F);
        teleport(victim, world, base.x + 2.0D, base.y, base.z, 0.0F, 0.0F);

        context.runAtTickTime(20L, () -> {
            killer.getInventory().clearContent();
            victim.getInventory().clearContent();
            GemPlayerState.initIfNeeded(killer);
            GemPlayerState.initIfNeeded(victim);
            GemPlayerState.setActiveGem(victim, GemId.FIRE);

            ItemStack stolen = new ItemStack(ModItems.FIRE_GEM);
            GemOwnership.tagOwned(stolen, victim.getUUID(), GemPlayerState.getGemEpoch(victim));
            killer.addItem(stolen);

            if (!GemsGameTestUtil.hasItem(killer, ModItems.FIRE_GEM)) {
                context.fail("Setup error: missing victim active gem item on killer");
            }

            GemsPlayerDeath.onDeathTail(victim, victim.damageSources().playerAttack(killer));

            if (GemsGameTestUtil.hasItem(killer, ModItems.FIRE_GEM)) {
                context.fail("Victim active gem item should be removed after kill");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 120)
    public void killingZeroEnergyPlayerDoesNotGrantEnergy(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer killer = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer victim = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        killer.setGameMode(GameType.SURVIVAL);
        victim.setGameMode(GameType.SURVIVAL);

        Vec3 base = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(killer, world, base.x, base.y, base.z, 0.0F, 0.0F);
        teleport(victim, world, base.x + 2.0D, base.y, base.z, 0.0F, 0.0F);

        context.runAtTickTime(20L, () -> {
            GemsGameTestUtil.resetPlayerForTest(killer);
            GemsGameTestUtil.resetPlayerForTest(victim);
            GemPlayerState.initIfNeeded(killer);
            GemPlayerState.initIfNeeded(victim);
            GemPlayerState.setEnergy(killer, GemPlayerState.MAX_ENERGY);
            GemPlayerState.setEnergy(victim, 0);

            GemsPlayerDeath.onDeathTail(victim, victim.damageSources().playerAttack(killer));

            if (GemPlayerState.getEnergy(killer) != GemPlayerState.MAX_ENERGY) {
                context.fail("Killing a 0-energy player should not grant energy");
                return;
            }
            int upgradesDropped = world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(base, base.add(1.0D, 1.0D, 1.0D)).inflate(3.0D),
                    e -> e.getItem().is(ModItems.ENERGY_UPGRADE)).size();
            int upgradesInInv = GemsGameTestUtil.countItem(killer, ModItems.ENERGY_UPGRADE);
            if (upgradesDropped + upgradesInInv > 0) {
                context.fail("Killing a 0-energy player should not drop energy upgrades");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void abilityDisablesClearAndCooldownSnapshotPersists(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.AIR);
        GemPlayerState.setEnergy(player, 8);

        var abilities = GemRegistry.definition(GemId.AIR).abilities();
        int dashIndex = abilities.indexOf(PowerIds.AIR_DASH);
        if (dashIndex < 0) {
            context.fail("AIR gem missing dash ability registration");
            return;
        }

        AbilityDisables.disable(player, PowerIds.AIR_DASH);
        GemPlayerState.setActiveGem(player, GemId.FIRE);
        GemPlayerState.setActiveGem(player, GemId.AIR);
        if (!AbilityDisables.isDisabled(player, PowerIds.AIR_DASH)) {
            context.fail("Ability disables should persist on gem switch (stolen abilities stay disabled until recovered)");
            return;
        }

        // Simulate the victim recovering the stolen ability (e.g. thief switches gems / victim kills thief).
        AbilityDisables.enable(player, PowerIds.AIR_DASH);
        if (AbilityDisables.isDisabled(player, PowerIds.AIR_DASH)) {
            context.fail("Ability should be re-enabled after recovery");
            return;
        }

        context.runAtTickTime(2L, () -> {
            long now = GemsTime.now(player);
            GemAbilities.activateByIndex(player, dashIndex);

            long nextAllowed = GemAbilityCooldowns.nextAllowedTick(player, PowerIds.AIR_DASH);
            if (nextAllowed <= now) {
                context.fail("Dash ability did not start cooldown after activation");
                return;
            }

            int remaining = GemAbilityCooldowns.remainingTicks(player, PowerIds.AIR_DASH, now);
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), world.registryAccess());
            CooldownSnapshotPayload payload = new CooldownSnapshotPayload(GemId.AIR.ordinal(), java.util.List.of(remaining), java.util.List.of(remaining));
            CooldownSnapshotPayload.CODEC.encode(buf, payload);
            buf.readerIndex(0);
            CooldownSnapshotPayload decoded = CooldownSnapshotPayload.CODEC.decode(buf);

            if (decoded.remainingAbilityCooldownTicks().isEmpty()) {
                context.fail("Cooldown snapshot payload lost cooldown entries");
                return;
            }
            if (!decoded.remainingAbilityCooldownTicks().get(0).equals(remaining)) {
                context.fail("Cooldown snapshot payload did not preserve remaining ticks");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 120)
    public void unlockOrderFollowsSpec(GameTestHelper context) {
        // Use Fire as a representative 4-ability gem and also validate all gems respect the energy spec.
        GemDefinition fire = GemRegistry.definition(GemId.FIRE);
        int fireAbilities = fire.abilities().size();
        if (fireAbilities < 3) {
            context.fail("Fire gem should have at least 3 abilities for unlock progression test");
            return;
        }

        int at2 = fire.availableAbilities(new GemEnergyState(2)).size();
        int at3 = fire.availableAbilities(new GemEnergyState(3)).size();
        int at4 = fire.availableAbilities(new GemEnergyState(4)).size();
        int at5 = fire.availableAbilities(new GemEnergyState(5)).size();

        if (at2 != 1 || at3 != Math.min(2, fireAbilities) || at4 != Math.min(3, fireAbilities) || at5 != fireAbilities) {
            context.fail("Fire unlock counts did not follow spec: " + at2 + "," + at3 + "," + at4 + "," + at5);
            return;
        }

        // All gems should follow the same energy unlock curve.
        for (GemId id : GemId.values()) {
            GemDefinition def = GemRegistry.definition(id);
            if (def == null) {
                continue;
            }
            int total = def.abilities().size();
            int e2 = def.availableAbilities(new GemEnergyState(2)).size();
            int e3 = def.availableAbilities(new GemEnergyState(3)).size();
            int e4 = def.availableAbilities(new GemEnergyState(4)).size();
            int e5 = def.availableAbilities(new GemEnergyState(5)).size();

            if (e2 != Math.min(1, total) || e3 != Math.min(2, total) || e4 != Math.min(3, total) || e5 != total) {
                context.fail("Unlock curve mismatch for gem " + id + " counts=" + e2 + "," + e3 + "," + e4 + "," + e5 + " total=" + total);
                return;
            }
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 120)
    public void energyLadderGatesAbilitiesAndLosesOnDeath(GameTestHelper context) {
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.FIRE);
        GemDefinition def = GemRegistry.definition(GemId.FIRE);
        int total = def.abilities().size();

        int e0 = def.availableAbilities(new GemEnergyState(0)).size();
        int e1 = def.availableAbilities(new GemEnergyState(1)).size();
        int e2 = def.availableAbilities(new GemEnergyState(2)).size();
        int e4 = def.availableAbilities(new GemEnergyState(4)).size();
        int e5 = def.availableAbilities(new GemEnergyState(5)).size();

        if (e0 != 0 || e1 != 0 || e2 != Math.min(1, total) || e4 != Math.min(3, total) || e5 != total) {
            context.fail("Ability gating mismatch for Fire gem");
            return;
        }

        if (GemPlayerState.getEnergy(player) != GemPlayerState.DEFAULT_ENERGY) {
            context.fail("Default energy should start at 3");
            return;
        }

        GemPlayerState.addEnergy(player, 1);
        if (GemPlayerState.getEnergy(player) != 4) {
            context.fail("Kill-style energy gain should increase energy by 1 to 4");
            return;
        }

        GemPlayerState.addEnergy(player, -1); // simulate death loss
        if (GemPlayerState.getEnergy(player) != 3) {
            context.fail("Death should reduce energy by 1 to 3");
            return;
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 160)
    public void passivesApplyAndStopWhenDisabled(GameTestHelper context) {
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.SPEED);
        GemPlayerState.setEnergy(player, 4);

        GemPowers.sync(player);
        if (!player.hasEffect(MobEffects.SPEED)) {
            context.fail("Speed passive should apply when energy is above zero");
            return;
        }

        GemPlayerState.setEnergy(player, 0);
        GemPowers.sync(player);
        if (player.hasEffect(MobEffects.SPEED)) {
            context.fail("Speed passive should clear when energy hits zero");
            return;
        }

        GemPlayerState.setActiveGem(player, GemId.AIR);
        GemPlayerState.setEnergy(player, 4);
        GemPowers.maintain(player);
        if (!GemsGameTestUtil.containsAirMace(player)) {
            context.fail("Maintained passive should grant air mace when enabled");
            return;
        }

        player.getInventory().clearContent();
        GemPlayerState.setEnergy(player, 0);
        GemPowers.maintain(player);
        if (GemsGameTestUtil.containsAirMace(player)) {
            context.fail("Maintained passives should stop ticking when disabled");
            return;
        }

        context.succeed();
    }

}

