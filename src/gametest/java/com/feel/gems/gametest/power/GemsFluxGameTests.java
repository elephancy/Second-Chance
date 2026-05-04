package com.feel.gems.gametest.power;

import net.minecraft.world.entity.EntityTypes;
import com.feel.gems.config.GemsBalance;
import com.feel.gems.core.GemId;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.power.ability.flux.FluxBeamAbility;
import com.feel.gems.power.gem.flux.FluxCharge;
import com.feel.gems.power.runtime.GemPowers;
import com.feel.gems.state.GemPlayerState;
import java.util.EnumSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;




public final class GemsFluxGameTests {
    private static void teleport(ServerPlayer player, ServerLevel world, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(world, x, y, z, EnumSet.noneOf(Relative.class), yaw, pitch, false);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void fluxBeamConsumesCharge(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        Vec3 startPos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, startPos.x, startPos.y, startPos.z, 0.0F, 0.0F);

        // Place a target in front of the player and explicitly aim at it (GameTest runs all tests in one batch,
        // so relying on default rotations or relative spawn helpers can be flaky).
        Vec3 targetPos = context.absoluteVec(new Vec3(0.5D, 2.0D, 1.5D));
        var target = EntityTypes.ARMOR_STAND.create(world, armorStand -> { }, BlockPos.containing(targetPos), EntitySpawnReason.TRIGGERED, false, false);
        if (target == null) {
            context.fail("Failed to create target entity");
            return;
        }
        // Keep the target very close so it stays within the default EMPTY_STRUCTURE bounds (avoids barrier occlusion).
        target.snapTo(targetPos.x, targetPos.y, targetPos.z, 0.0F, 0.0F);
        target.setNoGravity(true);
        world.addFreshEntity(target);

        Vec3 aimAt = new Vec3(target.getX(), target.getEyeY(), target.getZ());
        double dx = aimAt.x - player.getX();
        double dz = aimAt.z - player.getZ();
        double dy = aimAt.y - player.getEyeY();
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        // teleport(...) is the most reliable way to apply yaw/pitch for server-side raycasts.
        teleport(player, world, startPos.x, startPos.y, startPos.z, yaw, pitch);

        FluxCharge.set(player, 100);

        context.runAtTickTime(5L, () -> {
            boolean activated = new FluxBeamAbility().activate(player);
            if (!activated) {
                context.fail("Flux Beam did not activate (target not acquired)");
            }
            if (FluxCharge.get(player) != 0) {
                context.fail("Flux Beam did not reset flux charge back to 0");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void fluxChargeConsumesExactlyOneItem(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        Vec3 startPos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, startPos.x, startPos.y, startPos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.FLUX);
        GemPlayerState.setEnergy(player, 10);
        GemPowers.sync(player);

        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND_BLOCK, 3));
        FluxCharge.set(player, 0);

        context.runAtTickTime(2L, () -> {
            int before = player.getOffhandItem().getCount();
            boolean ok = FluxCharge.tryConsumeChargeItem(player);
            int after = player.getOffhandItem().getCount();

            if (!ok) {
                context.fail("Flux charge did not consume a valid charge item");
            }
            if (after != before - 1) {
                context.fail("Expected charge to consume exactly 1 item, before=" + before + " after=" + after);
            }
            if (FluxCharge.get(player) <= 0) {
                context.fail("Flux charge did not increase after consuming a charge item");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void fluxChargeFuelItemsMatchBalanceConfig(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        Vec3 startPos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, startPos.x, startPos.y, startPos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.FLUX);
        GemPlayerState.setEnergy(player, 10);
        GemPowers.sync(player);

        ItemStack enchantedDiamondTool = new ItemStack(Items.DIAMOND_PICKAXE, 1);
        var enchantmentRegistry = world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var unbreaking = enchantmentRegistry.get(Identifier.fromNamespaceAndPath("minecraft", "unbreaking"));
        if (unbreaking.isEmpty()) {
            context.fail("Missing UNBREAKING enchantment registry entry");
            return;
        }
        EnchantmentHelper.updateEnchantments(enchantedDiamondTool, builder -> builder.set(unbreaking.get(), 1));

        record Fuel(ItemStack stack, int expectedAdd, String label) {
        }
        var fuel = java.util.List.of(
                new Fuel(new ItemStack(Items.DIAMOND_BLOCK, 2), GemsBalance.v().flux().chargeDiamondBlock(), "diamond_block"),
                new Fuel(new ItemStack(Items.GOLD_BLOCK, 2), GemsBalance.v().flux().chargeGoldBlock(), "gold_block"),
                new Fuel(new ItemStack(Items.COPPER_BLOCK.weathering().unaffected(), 2), GemsBalance.v().flux().chargeCopperBlock(), "copper_block"),
                new Fuel(new ItemStack(Items.EMERALD_BLOCK, 2), GemsBalance.v().flux().chargeEmeraldBlock(), "emerald_block"),
                new Fuel(new ItemStack(Items.AMETHYST_BLOCK, 2), GemsBalance.v().flux().chargeAmethystBlock(), "amethyst_block"),
                new Fuel(new ItemStack(Items.NETHERITE_SCRAP, 2), GemsBalance.v().flux().chargeNetheriteScrap(), "netherite_scrap"),
                new Fuel(enchantedDiamondTool.copyWithCount(2), GemsBalance.v().flux().chargeEnchantedDiamondItem(), "enchanted_diamond_tool")
        );

        context.runAtTickTime(2L, () -> {
            for (Fuel f : fuel) {
                player.setItemInHand(InteractionHand.OFF_HAND, f.stack.copy());
                FluxCharge.set(player, 0);
                FluxCharge.clearIfBelow100(player);

                int before = player.getOffhandItem().getCount();
                boolean ok = FluxCharge.tryConsumeChargeItem(player);
                int after = player.getOffhandItem().getCount();

                if (!ok) {
                    context.fail("Fuel did not consume: " + f.label);
                    return;
                }
                if (after != before - 1) {
                    context.fail("Expected fuel to consume exactly 1 item: " + f.label + " before=" + before + " after=" + after);
                    return;
                }

                int expectedCharge = Math.min(100, f.expectedAdd);
                int got = FluxCharge.get(player);
                if (got != expectedCharge) {
                    context.fail("Unexpected charge after consuming fuel: " + f.label + " expected=" + expectedCharge + " got=" + got);
                    return;
                }
            }

            context.succeed();
        });
    }

}

