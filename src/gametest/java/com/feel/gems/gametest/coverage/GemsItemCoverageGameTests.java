package com.feel.gems.gametest.coverage;

import com.feel.gems.gametest.util.GemsGameTestUtil;
import java.util.EnumSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class GemsItemCoverageGameTests {
    private static void teleport(ServerPlayer player, ServerLevel world, double x, double y, double z) {
        player.teleportTo(world, x, y, z, EnumSet.noneOf(Relative.class), 0.0F, 0.0F, false);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void allModItemsUseWithoutCrashing(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);

        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z);

        context.runAtTickTime(5L, () -> {
            for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
                if (!"gems".equals(id.getNamespace())) {
                    continue;
                }
                Item item = BuiltInRegistries.ITEM.getValue(id);
                if (item == null) {
                    continue;
                }
                try {
                    ItemStack stack = new ItemStack(item);
                    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                    item.use(world, player, InteractionHand.MAIN_HAND);
                } catch (Throwable t) {
                    context.fail("Item crashed: " + id + " -> " + t.getMessage());
                    return;
                }
            }
            context.succeed();
        });
    }
}
