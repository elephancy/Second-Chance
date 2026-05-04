package com.feel.gems.gametest.item;

import com.feel.gems.core.GemId;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.item.GemItemGlint;
import com.feel.gems.item.ModItems;
import com.feel.gems.legendary.LegendaryDuels;
import com.feel.gems.state.GemPlayerState;
import java.util.EnumSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;




public final class GemsItemGameTests {
    private static void teleport(ServerPlayer player, ServerLevel world, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(world, x, y, z, EnumSet.noneOf(Relative.class), yaw, pitch, false);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void challengersGauntletStartsAndCancelsDuel(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        var server = world.getServer();
        if (server == null) {
            context.fail("No server instance");
            return;
        }

        ServerPlayer challenger = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        ServerPlayer target = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        challenger.setGameMode(GameType.SURVIVAL);
        target.setGameMode(GameType.SURVIVAL);

        Vec3 pos1 = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        Vec3 pos2 = context.absoluteVec(new Vec3(1.5D, 2.0D, 0.5D));
        teleport(challenger, world, pos1.x, pos1.y, pos1.z, 0.0F, 0.0F);
        teleport(target, world, pos2.x, pos2.y, pos2.z, 0.0F, 0.0F);

        var beforeWorld = challenger.level();
        var beforePos = challenger.blockPosition();

        context.runAtTickTime(5L, () -> {
            boolean ok = LegendaryDuels.startGauntletDuel(challenger, target);
            if (!ok) {
                context.fail("Expected gauntlet duel to start");
            }
        });

        context.runAtTickTime(20L, () -> {
            if (!LegendaryDuels.isInGauntletDuel(server, challenger.getUUID())) {
                context.fail("Expected challenger to be in a gauntlet duel");
                return;
            }
            if (challenger.level() == beforeWorld && challenger.blockPosition().equals(beforePos)) {
                context.fail("Expected challenger to be teleported into an arena");
                return;
            }
            LegendaryDuels.onDisconnect(server, challenger);
        });

        context.runAtTickTime(40L, () -> {
            if (LegendaryDuels.isInGauntletDuel(server, challenger.getUUID())) {
                context.fail("Expected gauntlet duel to be cleared after cancel");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void recipesAreRegistered(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        var server = world.getServer();
        if (server == null) {
            context.fail("No server instance");
            return;
        }
        var manager = server.getRecipeManager();
        for (String path : new String[]{"heart", "energy_upgrade", "gem_trader", "gem_purchase"}) {
            Identifier id = Identifier.fromNamespaceAndPath("gems", path);
            ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, id);
            if (manager.byKey(key).isEmpty()) {
                context.fail("Missing recipe: " + id);
                return;
            }
        }
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 120)
    public void glintAppliesOnlyToActiveGemAtCap(GameTestHelper context) {
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setEnergy(player, GemPlayerState.MAX_ENERGY);
        GemPlayerState.setActiveGem(player, GemId.ASTRA);

        player.getInventory().clearContent();
        player.addItem(new ItemStack(ModItems.ASTRA_GEM));
        player.addItem(new ItemStack(ModItems.FIRE_GEM));

        context.runAtTickTime(2L, () -> {
            GemItemGlint.sync(player);
            if (GemsGameTestUtil.countGlint(player, ModItems.ASTRA_GEM) != 1) {
                context.fail("Active gem should glint when at energy cap");
                return;
            }
            if (GemsGameTestUtil.countGlint(player, ModItems.FIRE_GEM) != 0) {
                context.fail("Inactive gems should not glint when another gem is active");
                return;
            }

            GemPlayerState.setEnergy(player, GemPlayerState.MIN_ENERGY);
            GemItemGlint.sync(player);
            if (GemsGameTestUtil.countGlint(player, ModItems.ASTRA_GEM) != 0) {
                context.fail("Glint should clear when energy drops below cap");
                return;
            }

            GemPlayerState.setActiveGem(player, GemId.FIRE);
            GemPlayerState.setEnergy(player, GemPlayerState.MAX_ENERGY);
            GemItemGlint.sync(player);
            if (GemsGameTestUtil.countGlint(player, ModItems.ASTRA_GEM) != 0) {
                context.fail("Old active gem should lose glint after switching");
                return;
            }
            if (GemsGameTestUtil.countGlint(player, ModItems.FIRE_GEM) != 1) {
                context.fail("New active gem should gain glint at energy cap");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 120)
    public void energyUpgradeIncreasesEnergyUntilCap(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setEnergy(player, GemPlayerState.MAX_ENERGY - 1);

        ItemStack upgrades = new ItemStack(ModItems.ENERGY_UPGRADE, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, upgrades);

        context.runAtTickTime(2L, () -> {
            ModItems.ENERGY_UPGRADE.use(world, player, InteractionHand.MAIN_HAND);
            if (GemPlayerState.getEnergy(player) != GemPlayerState.MAX_ENERGY) {
                context.fail("Energy upgrade did not raise energy to cap");
                return;
            }
            if (player.getMainHandItem().getCount() != 1) {
                context.fail("Energy upgrade should consume exactly one item when applied");
                return;
            }

            ModItems.ENERGY_UPGRADE.use(world, player, InteractionHand.MAIN_HAND);
            if (GemPlayerState.getEnergy(player) != GemPlayerState.MAX_ENERGY) {
                context.fail("Energy should stay capped after extra upgrade use");
                return;
            }
            if (player.getMainHandItem().getCount() != 1) {
                context.fail("Energy upgrade should not consume when at cap");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 120)
    public void heartItemRespectsMaxCap(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        GemPlayerState.initIfNeeded(player);
        GemPlayerState.setMaxHearts(player, GemPlayerState.MAX_MAX_HEARTS - 1);
        GemPlayerState.applyMaxHearts(player);

        ItemStack hearts = new ItemStack(ModItems.HEART, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, hearts);

        context.runAtTickTime(2L, () -> {
            ModItems.HEART.use(world, player, InteractionHand.MAIN_HAND);
            if (GemPlayerState.getMaxHearts(player) != GemPlayerState.MAX_MAX_HEARTS) {
                context.fail("Heart did not increase max hearts to the cap");
                return;
            }
            if (player.getMainHandItem().getCount() != 1) {
                context.fail("Heart should consume exactly one item when applied");
                return;
            }

            ModItems.HEART.use(world, player, InteractionHand.MAIN_HAND);
            if (GemPlayerState.getMaxHearts(player) != GemPlayerState.MAX_MAX_HEARTS) {
                context.fail("Hearts should stay at cap after extra use");
                return;
            }
            if (player.getMainHandItem().getCount() != 1) {
                context.fail("Heart should not consume when already capped");
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 120)
    public void pocketsInventoryPersistsAcrossSaves(GameTestHelper context) {
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.setGameMode(GameType.SURVIVAL);

        context.runAtTickTime(2L, () -> {
            var pockets = com.feel.gems.screen.PocketsStorage.load(player);
            pockets.setItem(0, new ItemStack(Items.DIAMOND, 3));
            com.feel.gems.screen.PocketsStorage.save(player, pockets);

            var reloaded = com.feel.gems.screen.PocketsStorage.load(player);
            if (!ItemStack.matches(reloaded.getItem(0), new ItemStack(Items.DIAMOND, 3))) {
                context.fail("Pockets inventory did not persist items through save/load");
                return;
            }
            context.succeed();
        });
    }

}
