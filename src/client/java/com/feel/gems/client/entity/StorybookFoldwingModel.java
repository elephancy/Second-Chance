package com.feel.gems.client.entity;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

/** An original low-poly paper moth: folded body, broad hinged wings and page-tip antennae. */
public final class StorybookFoldwingModel extends EntityModel<StorybookFoldwingRenderer.FoldwingRenderState> {
    private final ModelPart leftWing;
    private final ModelPart rightWing;

    public StorybookFoldwingModel(ModelPart root) {
        super(root);
        leftWing = root.getChild("left_wing");
        rightWing = root.getChild("right_wing");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        var root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-2.0F, -2.0F, -3.0F, 4.0F, 4.0F, 6.0F),
                PartPose.offset(0.0F, 17.0F, 0.0F));
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 12)
                .addBox(-2.0F, -2.0F, -2.0F, 4.0F, 3.0F, 3.0F),
                PartPose.offset(0.0F, 16.0F, -3.0F));
        root.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(24, 0)
                .addBox(-10.0F, -1.0F, -4.0F, 10.0F, 2.0F, 8.0F),
                PartPose.offset(-1.0F, 16.0F, 0.0F));
        root.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(24, 10)
                .addBox(0.0F, -1.0F, -4.0F, 10.0F, 2.0F, 8.0F),
                PartPose.offset(1.0F, 16.0F, 0.0F));
        root.addOrReplaceChild("antennae", CubeListBuilder.create().texOffs(0, 21)
                .addBox(-2.0F, -4.0F, -1.0F, 1.0F, 4.0F, 1.0F)
                .texOffs(4, 21).addBox(1.0F, -4.0F, -1.0F, 1.0F, 4.0F, 1.0F),
                PartPose.offset(0.0F, 15.0F, -4.0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void setupAnim(StorybookFoldwingRenderer.FoldwingRenderState state) {
        super.setupAnim(state);
        float flutter = (float) Math.sin(state.ageInTicks * 0.85F) * 0.18F;
        leftWing.zRot = 0.43F + flutter;
        rightWing.zRot = -0.43F - flutter;
    }
}
