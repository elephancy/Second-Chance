package com.feel.gems.client.entity;

import com.feel.gems.friend.FriendEntity;
import com.feel.gems.friend.FriendRegistry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/** Fictional town outfits give residents distinct looks without implying real likenesses. */
public final class FriendEntityRenderer extends MobRenderer<FriendEntity, FriendEntityRenderer.FriendRenderState, HumanoidModel<FriendEntityRenderer.FriendRenderState>> {
    private static final Identifier FALLBACK_SKIN = Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");

    public FriendEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
    }

    @Override
    public FriendRenderState createRenderState() {
        return new FriendRenderState();
    }

    @Override
    public void extractRenderState(FriendEntity entity, FriendRenderState state, float tickDelta) {
        super.extractRenderState(entity, state, tickDelta);
        state.friendId = entity.friendId();
    }

    @Override
    public Identifier getTextureLocation(FriendRenderState state) {
        return FriendRegistry.isTownFriendId(state.friendId)
                ? Identifier.fromNamespaceAndPath("gems", "textures/entity/friend/" + state.friendId + ".png")
                : FALLBACK_SKIN;
    }

    public static final class FriendRenderState extends HumanoidRenderState {
        private String friendId = "";
    }
}
