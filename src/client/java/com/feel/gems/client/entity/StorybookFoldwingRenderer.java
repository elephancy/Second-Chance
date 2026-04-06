package com.feel.gems.client.entity;

import com.feel.gems.entity.StorybookFoldwingEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/** Five authored wing patterns show which part of Yehia's tale comes next. */
public final class StorybookFoldwingRenderer extends MobRenderer<StorybookFoldwingEntity,
        StorybookFoldwingRenderer.FoldwingRenderState, StorybookFoldwingModel> {
    private static final Identifier[] TEXTURES = {
            texture("folded"), texture("mushroom"), texture("lantern"),
            texture("tree"), texture("finished")
    };

    public StorybookFoldwingRenderer(EntityRendererProvider.Context context) {
        super(context, new StorybookFoldwingModel(StorybookFoldwingModel.createLayer().bakeRoot()), 0.25F);
    }

    @Override
    public FoldwingRenderState createRenderState() {
        return new FoldwingRenderState();
    }

    @Override
    public void extractRenderState(StorybookFoldwingEntity entity, FoldwingRenderState state,
                                   float tickDelta) {
        super.extractRenderState(entity, state, tickDelta);
        state.cue = Math.clamp(entity.cuePhase(), 0, TEXTURES.length - 1);
    }

    @Override
    public Identifier getTextureLocation(FoldwingRenderState state) {
        return TEXTURES[state.cue];
    }

    private static Identifier texture(String phase) {
        return Identifier.fromNamespaceAndPath("gems", "textures/entity/storybook_foldwing_" + phase + ".png");
    }

    public static final class FoldwingRenderState extends LivingEntityRenderState {
        private int cue;
    }
}
