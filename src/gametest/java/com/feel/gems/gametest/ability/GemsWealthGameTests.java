package com.feel.gems.gametest.ability;

import com.feel.gems.config.GemsBalance;
import com.feel.gems.core.GemId;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.power.ability.wealth.AmplificationAbility;
import com.feel.gems.power.ability.wealth.FumbleAbility;
import com.feel.gems.power.ability.wealth.HotbarLockAbility;
import com.feel.gems.power.ability.wealth.PocketsAbility;
import com.feel.gems.power.ability.wealth.RichRushAbility;
import com.feel.gems.power.gem.wealth.HotbarLock;
import com.feel.gems.power.gem.wealth.WealthFumble;
import com.feel.gems.power.runtime.AbilityRuntime;
import com.feel.gems.power.runtime.GemPowers;
import com.feel.gems.state.GemPlayerState;
import java.util.EnumSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import com.feel.gems.screen.PocketsScreenHandler;
import com.feel.gems.trust.GemTrust;

public final class GemsWealthGameTests {

    private static void teleport(ServerPlayer player, ServerLevel world, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(world, x, y, z, EnumSet.noneOf(Relative.class), yaw, pitch, false);
    }

    private static int countItem(ServerPlayer player, Item item) {
        int count = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void aimAt(ServerPlayer player, ServerLevel world, Vec3 target) {
        Vec3 pos = player.position();
        double dx = target.x - pos.x;
        double dz = target.z - pos.z;
        double dy = target.y - player.getEyeY();
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        teleport(player, world, pos.x, pos.y, pos.z, yaw, pitch);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void fumbleDisorientatesEnemies(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer enemy = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer ally = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(enemy);
        GemsGameTestUtil.forceSurvival(ally);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(enemy, world, pos.x + 3.0D, pos.y, pos.z, 0.0F, 0.0F);
        teleport(ally, world, pos.x + 2.0D, pos.y, pos.z + 2.0D, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.WEALTH);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        GemTrust.trust(player, ally.getUUID());

        context.runAtTickTime(5L, () -> {
            boolean ok = new FumbleAbility().activate(player);
            if (!ok) {
                context.fail("Fumble did not activate");
            }
        });

        context.runAtTickTime(20L, () -> {
            if (!WealthFumble.isActive(enemy)) {
                context.fail("Fumble should affect enemies in range");
            }
            if (WealthFumble.isActive(ally)) {
                context.fail("Fumble should not affect trusted allies");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void hotbarLockLocksTargetHotbar(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(target);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(target, world, pos.x, pos.y, pos.z + 3.0D, 180.0F, 0.0F);
        aimAt(player, world, target.position().add(0.0D, 1.2D, 0.0D));

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.WEALTH);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new HotbarLockAbility().activate(player);
            if (!ok) {
                context.fail("Hotbar Lock did not activate");
            }
            if (!HotbarLock.isLocked(target)) {
                context.fail("Hotbar Lock should lock the target hotbar");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void amplificationIncreasesEnchantmentPower(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.WEALTH);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        var fortune = world.registryAccess().get(Enchantments.FORTUNE).orElseThrow();
        EnchantmentHelper.updateEnchantments(pickaxe, builder -> builder.set(fortune, 1));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, pickaxe);

        context.runAtTickTime(5L, () -> {
            boolean ok = new AmplificationAbility().activate(player);
            if (!ok) {
                context.fail("Amplification did not activate");
            }
            int level = EnchantmentHelper.getItemEnchantmentLevel(fortune, pickaxe);
            if (level <= 1) {
                context.fail("Amplification should boost enchantment levels");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void richRushGrantsLuckEffects(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.WEALTH);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new RichRushAbility().activate(player);
            if (!ok) {
                context.fail("Rich Rush did not activate");
            }
            if (!AbilityRuntime.isRichRushActive(player)) {
                context.fail("Rich Rush should mark the runtime as active");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void pocketsExpandsInventory(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.WEALTH);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            boolean ok = new PocketsAbility().activate(player);
            if (!ok) {
                context.fail("Pockets did not activate");
            }
            AbstractContainerMenu handler = player.containerMenu;
            if (!(handler instanceof PocketsScreenHandler)) {
                context.fail("Pockets should open the pockets screen");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void wealthPassivesApply(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        GemsGameTestUtil.forceSurvival(target);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        teleport(target, world, pos.x + 2.0D, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.WEALTH);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, sword);
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, pickaxe);

        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
        helmet.setDamageValue(helmet.getMaxDamage() / 2);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, helmet);
        ItemStack targetChest = new ItemStack(Items.DIAMOND_CHESTPLATE);
        target.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, targetChest);

        context.runAtTickTime(5L, () -> {
            GemPowers.maintain(player);
            if (!player.hasEffect(MobEffects.LUCK) || !player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)) {
                context.fail("Wealth status effect passives should apply");
            }
            var mending = world.registryAccess().get(Enchantments.MENDING).orElseThrow();
            var looting = world.registryAccess().get(Enchantments.LOOTING).orElseThrow();
            var fortune = world.registryAccess().get(Enchantments.FORTUNE).orElseThrow();
            int mendingLevel = EnchantmentHelper.getItemEnchantmentLevel(mending, sword);
            int lootingLevel = EnchantmentHelper.getItemEnchantmentLevel(looting, sword);
            int fortuneLevel = EnchantmentHelper.getItemEnchantmentLevel(fortune, pickaxe);
            if (mendingLevel <= 0 || lootingLevel < 3 || fortuneLevel < 3) {
                context.fail("Wealth auto-enchants should apply");
            }

            int beforeMend = helmet.getDamageValue();
            int beforeTargetDamage = targetChest.getDamageValue();
            player.attack(target);
            if (helmet.getDamageValue() >= beforeMend) {
                context.fail("Armor Mend on Hit should repair armor");
            }
            if (targetChest.getDamageValue() <= beforeTargetDamage) {
                context.fail("Durability Chip should damage enemy armor");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void doubleDebrisDuplicatesScrap(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setActiveGem(player, GemId.WEALTH);
        GemPlayerState.setEnergy(player, 5);
        GemPowers.sync(player);

        context.runAtTickTime(5L, () -> {
            player.getInventory().clearContent();
            int before = countItem(player, Items.NETHERITE_SCRAP);
            ItemStack taken = new ItemStack(Items.NETHERITE_SCRAP, 2);
            player.getInventory().add(taken.copy());

            FurnaceResultSlot slot = new FurnaceResultSlot(player, new SimpleContainer(1), 0, 0, 0);
            slot.onTake(player, taken);

            int after = countItem(player, Items.NETHERITE_SCRAP);
            if (after - before < 4) {
                context.fail("Double Debris should duplicate netherite scrap output");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void fumbleHasCooldownConfigured(GameTestHelper context) {
        // Direct activate() bypasses cooldown system (handled by GemAbilities.activateByIndex)
        // Just verify the cooldown config is valid
        var cfg = GemsBalance.v().wealth();
        if (cfg.fumbleCooldownTicks() <= 0) {
            context.fail("Fumble cooldown must be positive");
        }
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void wealthConfigValuesValid(GameTestHelper context) {
        var cfg = GemsBalance.v().wealth();
        
        if (cfg.fumbleCooldownTicks() < 0) {
            context.fail("Fumble cooldown cannot be negative");
        }
        if (cfg.fumbleDurationTicks() < 0) {
            context.fail("Fumble duration cannot be negative");
        }
        if (cfg.fumbleRadiusBlocks() < 0) {
            context.fail("Fumble radius cannot be negative");
        }
        if (cfg.hotbarLockCooldownTicks() < 0) {
            context.fail("Hotbar Lock cooldown cannot be negative");
        }
        if (cfg.amplificationCooldownTicks() < 0) {
            context.fail("Amplification cooldown cannot be negative");
        }
        if (cfg.richRushCooldownTicks() < 0) {
            context.fail("Rich Rush cooldown cannot be negative");
        }
        if (cfg.pocketsRows() < 0) {
            context.fail("Pockets rows cannot be negative");
        }
        
        context.succeed();
    }
}
