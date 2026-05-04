package com.feel.gems.adventure;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Exercises a saved Atlas page through the real server item action. */
public final class RumorAtlasGameTests {
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void savedOthmanPageHandlesEmptyTestWorld(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        CapturePlayer player = connectedPlayer(context);
        ItemStack atlas = new ItemStack(AdventureItems.RUMOR_ATLAS);
        int othmanPage = LandmarkKey.values().length + 6;
        CustomData.update(DataComponents.CUSTOM_DATA, atlas,
                data -> data.putInt("rumor_atlas_target", othmanPage));
        player.setItemInHand(InteractionHand.MAIN_HAND, atlas);
        player.messages.clear();
        if (RumorAtlasItem.selection(atlas, LandmarkKey.values().length + 15) != othmanPage) {
            context.fail("Saved Othman Atlas page changed its index");
            return;
        }
        if (AdventureItems.RUMOR_ATLAS.use(level, player, InteractionHand.MAIN_HAND)
                != InteractionResult.SUCCESS
                || !player.messages.contains("Looking for Othman's Library...")) {
            context.fail("Saved Othman Atlas page did not begin its lookup: " + player.messages);
            return;
        }
        context.runAtTickTime(150L, () -> {
            if (!player.messages.contains("No trace of Othman's Library appears on this page. Try again farther away.")) {
                context.fail("Saved Othman Atlas page did not return the empty-world clue: "
                        + player.messages);
                return;
            }
            context.succeed();
        });
    }

    private static CapturePlayer connectedPlayer(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        var server = level.getServer();
        String name = "test" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), name), false);
        CapturePlayer player = new CapturePlayer(server, level, cookie);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    private static final class CapturePlayer extends ServerPlayer {
        private final List<String> messages = new ArrayList<>();

        private CapturePlayer(net.minecraft.server.MinecraftServer server, ServerLevel level,
                              CommonListenerCookie cookie) {
            super(server, level, cookie.gameProfile(), cookie.clientInformation());
        }

        @Override
        public void sendSystemMessage(Component message, boolean overlay) {
            if (messages != null) messages.add(message.getString());
            super.sendSystemMessage(message, overlay);
        }
    }
}
