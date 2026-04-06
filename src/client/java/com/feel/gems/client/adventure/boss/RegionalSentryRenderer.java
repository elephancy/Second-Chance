package com.feel.gems.client.adventure.boss;

import com.feel.gems.adventure.boss.RegionalSentryEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/** One biped model with a distinct texture for each region's sentry type. */
public final class RegionalSentryRenderer extends MobRenderer<RegionalSentryEntity,
        HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
    private final Identifier texture;

    public RegionalSentryRenderer(EntityRendererProvider.Context context, String name) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.4F);
        texture = Identifier.fromNamespaceAndPath("gems", "textures/entity/" + name + ".png");
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        return texture;
    }
}
