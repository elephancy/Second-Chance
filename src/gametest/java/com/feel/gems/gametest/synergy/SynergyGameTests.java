package com.feel.gems.gametest.synergy;

import com.feel.gems.core.GemId;
import com.feel.gems.core.GemRegistry;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.power.registry.PowerIds;
import com.feel.gems.synergy.SynergyDefinition;
import com.feel.gems.synergy.SynergyRegistry;
import com.feel.gems.synergy.SynergyRuntime;
import com.feel.gems.trust.GemTrust;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

public final class SynergyGameTests {
    private static void teleport(ServerPlayer player, ServerLevel world, double x, double y, double z) {
        player.teleportTo(world, x, y, z, EnumSet.noneOf(Relative.class), 0.0F, 0.0F, false);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void synergyTriggersWithMutualTrust(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer p1 = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer p2 = GemsGameTestUtil.createMockCreativeServerPlayer(context);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(p1, world, pos.x, pos.y, pos.z);
        teleport(p2, world, pos.x + 1.0D, pos.y, pos.z + 1.0D);

        context.runAtTickTime(20L, () -> {
            GemTrust.trust(p1, p2.getUUID());
            GemTrust.trust(p2, p1.getUUID());
            SynergyRuntime.onAbilityCast(p1, GemId.SUMMONER, PowerIds.SUMMON_SLOT_1);
            SynergyRuntime.onAbilityCast(p2, GemId.HUNTER, PowerIds.HUNTER_POUNCE);
        });

        context.runAtTickTime(40L, () -> {
            if (!p1.hasEffect(MobEffects.STRENGTH) || !p2.hasEffect(MobEffects.STRENGTH)) {
                context.fail("Expected Pack Alpha synergy to apply Strength to both participants");
                return;
            }
            GemsGameTestUtil.complete(context);
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void synergyBlockedWithoutMutualTrust(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer p1 = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer p2 = GemsGameTestUtil.createMockCreativeServerPlayer(context);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(p1, world, pos.x, pos.y, pos.z);
        teleport(p2, world, pos.x + 1.0D, pos.y, pos.z + 1.0D);

        context.runAtTickTime(20L, () -> {
            SynergyRuntime.onAbilityCast(p1, GemId.SUMMONER, PowerIds.SUMMON_SLOT_1);
            SynergyRuntime.onAbilityCast(p2, GemId.HUNTER, PowerIds.HUNTER_POUNCE);
        });

        context.runAtTickTime(40L, () -> {
            if (p1.hasEffect(MobEffects.STRENGTH) || p2.hasEffect(MobEffects.STRENGTH)) {
                context.fail("Synergy should not trigger without mutual trust");
                return;
            }
            GemsGameTestUtil.complete(context);
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void allSynergyEffectsExecute(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer p1 = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer p2 = GemsGameTestUtil.createMockCreativeServerPlayer(context);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(p1, world, pos.x, pos.y, pos.z);
        teleport(p2, world, pos.x + 1.0D, pos.y, pos.z + 1.0D);

        context.runAtTickTime(20L, () -> {
            long now = world.getGameTime();
            for (SynergyDefinition synergy : SynergyRegistry.getAll()) {
                List<SynergyDefinition.SynergyParticipant> participants = new ArrayList<>();
                int i = 0;
                for (GemId gem : synergy.requiredGems()) {
                    ServerPlayer player = (i++ % 2 == 0) ? p1 : p2;
                    Identifier abilityId = pickAbilityId(synergy, gem);
                    participants.add(new SynergyDefinition.SynergyParticipant(player, gem, abilityId, now));
                }
                synergy.effect().apply(participants);
            }
            GemsGameTestUtil.complete(context);
        });
    }

    private static Identifier pickAbilityId(SynergyDefinition synergy, GemId gem) {
        if (synergy.isAbilitySpecific()) {
            Set<Identifier> required = synergy.requiredAbilities().orElse(Set.of());
            if (!required.isEmpty()) {
                return required.iterator().next();
            }
        }
        List<Identifier> abilities = GemRegistry.definition(gem).abilities();
        if (!abilities.isEmpty()) {
            return abilities.get(0);
        }
        return PowerIds.FIREBALL;
    }
}
