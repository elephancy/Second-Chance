package com.feel.gems.client.entity;

import com.feel.gems.entity.DomainCreatureEntity;
import com.feel.gems.entity.DomainCreatureEntity.Kind;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/** Uses the shared rig but an authored silhouette and painted texture for every local resident. */
public final class DomainCreatureRenderer extends MobRenderer<DomainCreatureEntity,
        DomainCreatureRenderer.CreatureRenderState, DomainCreatureModel> {
    private static final Identifier[] TEXTURES = new Identifier[Kind.values().length];
    private static final Identifier REED_READY_TEXTURE = Identifier.fromNamespaceAndPath(
            "gems", "textures/entity/domain_reedrunner_ready.png");

    static {
        for (Kind kind : Kind.values()) TEXTURES[kind.ordinal()] = Identifier.fromNamespaceAndPath(
                "gems", "textures/entity/domain_" + kind.id + ".png");
    }

    public DomainCreatureRenderer(EntityRendererProvider.Context context) {
        super(context, new DomainCreatureModel(DomainCreatureModel.createLayer().bakeRoot()), 0.3F);
    }

    @Override
    public CreatureRenderState createRenderState() {
        return new CreatureRenderState();
    }

    @Override
    public void extractRenderState(DomainCreatureEntity entity, CreatureRenderState state, float tickDelta) {
        super.extractRenderState(entity, state, tickDelta);
        state.kind = entity.kind();
        state.reedReady = entity.reedReady();
    }

    @Override
    public Identifier getTextureLocation(CreatureRenderState state) {
        return state.reedReady && state.kind == Kind.REEDRUNNER
                ? REED_READY_TEXTURE : TEXTURES[state.kind.ordinal()];
    }

    public static final class CreatureRenderState extends LivingEntityRenderState {
        Kind kind = Kind.INKLING;
        boolean reedReady;
    }
}
