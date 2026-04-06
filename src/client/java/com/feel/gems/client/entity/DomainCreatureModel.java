package com.feel.gems.client.entity;

import com.feel.gems.entity.DomainCreatureEntity.Kind;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

/** Shared small-animal rig; each domain exposes a different set of authored appendages. */
public final class DomainCreatureModel extends EntityModel<DomainCreatureRenderer.CreatureRenderState> {
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart feet;
    private final ModelPart[] features;
    private final ModelPart gullLeftWing;
    private final ModelPart gullRightWing;

    public DomainCreatureModel(ModelPart root) {
        super(root);
        body = root.getChild("body");
        head = root.getChild("head");
        feet = root.getChild("feet");
        features = new ModelPart[Kind.values().length];
        for (Kind kind : Kind.values()) features[kind.ordinal()] = root.getChild(kind.id);
        gullLeftWing = features[Kind.BEACON_GULL.ordinal()].getChild("left_wing");
        gullRightWing = features[Kind.BEACON_GULL.ordinal()].getChild("right_wing");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        var root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4, -5, -5, 8, 6, 10), PartPose.offset(0, 20, 0));
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 17)
                .addBox(-3, -4, -3, 6, 5, 5), PartPose.offset(0, 18, -5));
        root.addOrReplaceChild("feet", CubeListBuilder.create().texOffs(30, 19)
                .addBox(-4, 0, -4, 2, 2, 3).addBox(2, 0, -4, 2, 2, 3)
                .addBox(-4, 0, 2, 2, 2, 3).addBox(2, 0, 2, 2, 2, 3),
                PartPose.offset(0, 22, 0));

        root.addOrReplaceChild("inkling", CubeListBuilder.create().texOffs(0, 29)
                .addBox(-5, -7, -1, 10, 1, 8).texOffs(0, 38)
                .addBox(-1, -11, 2, 2, 4, 2).texOffs(42, 0)
                .addBox(-5, -3, 4, 10, 1, 4), PartPose.offset(0, 18, 0));
        root.addOrReplaceChild("reedrunner", CubeListBuilder.create().texOffs(0, 30)
                .addBox(-2, -10, -7, 4, 9, 2).texOffs(12, 30)
                .addBox(-1, -13, -7, 2, 4, 1).texOffs(38, 20)
                .addBox(-2, -2, 4, 4, 2, 5), PartPose.offset(0, 18, 0));
        var gull = root.addOrReplaceChild("beacon_gull", CubeListBuilder.create().texOffs(0, 17)
                .addBox(-2, -11, -7, 4, 4, 4).texOffs(42, 0)
                .addBox(-1, -8, -5, 2, 5, 2).texOffs(42, 12)
                .addBox(-2, -8, -11, 4, 2, 4).texOffs(38, 20)
                .addBox(-2, -4, 4, 4, 2, 6), PartPose.offset(0, 18, 0));
        gull.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(0, 30)
                .addBox(-9, -1, -4, 9, 2, 8).texOffs(42, 0)
                .addBox(-11, -2, -3, 3, 2, 6), PartPose.offset(-3, -4, 0));
        gull.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(0, 30)
                .addBox(0, -1, -4, 9, 2, 8).texOffs(42, 0)
                .addBox(8, -2, -3, 3, 2, 6), PartPose.offset(3, -4, 0));
        root.addOrReplaceChild("chorus_hare", CubeListBuilder.create().texOffs(0, 30)
                .addBox(-3, -12, -7, 2, 9, 2).texOffs(8, 30)
                .addBox(1, -12, -7, 2, 9, 2).texOffs(42, 0)
                .addBox(-2, -4, 5, 4, 4, 4), PartPose.offset(0, 18, 0));
        root.addOrReplaceChild("cairn_beetle", CubeListBuilder.create().texOffs(0, 30)
                .addBox(-6, -7, -6, 12, 3, 12).texOffs(0, 30)
                .addBox(-4, -10, -4, 8, 3, 8).texOffs(42, 0)
                .addBox(-1, -11, -1, 2, 3, 2).texOffs(42, 12)
                .addBox(-4, -10, -8, 2, 6, 3).addBox(2, -10, -8, 2, 6, 3)
                .texOffs(38, 20)
                .addBox(-7, -3, -2, 2, 2, 5).addBox(5, -3, -2, 2, 2, 5),
                PartPose.offset(0, 18, 0));
        root.addOrReplaceChild("catalog_owl", CubeListBuilder.create().texOffs(0, 30)
                .addBox(-6, -7, -3, 3, 8, 7).texOffs(20, 30)
                .addBox(3, -7, -3, 3, 8, 7).texOffs(42, 0)
                .addBox(-3, -9, -8, 2, 5, 2).addBox(1, -9, -8, 2, 5, 2),
                PartPose.offset(0, 18, 0));
        root.addOrReplaceChild("dust_skipper", CubeListBuilder.create().texOffs(0, 30)
                .addBox(-5, -12, -5, 2, 10, 2).texOffs(8, 30)
                .addBox(3, -12, -5, 2, 10, 2).texOffs(0, 0)
                .addBox(-5, -6, 3, 10, 2, 6).texOffs(42, 12)
                .addBox(-2, -10, 7, 4, 5, 2), PartPose.offset(0, 18, 0));
        root.addOrReplaceChild("rivet_crab", CubeListBuilder.create().texOffs(0, 30)
                .addBox(-6, -7, -5, 12, 2, 10).texOffs(42, 0)
                .addBox(-11, -4, -5, 5, 4, 5).addBox(6, -4, -5, 5, 4, 5)
                .texOffs(38, 20).addBox(-3, -10, -7, 1, 5, 1)
                .addBox(2, -10, -7, 1, 5, 1), PartPose.offset(0, 18, 0));
        root.addOrReplaceChild("splitstone_ram", CubeListBuilder.create().texOffs(0, 30)
                .addBox(-7, -10, -8, 4, 7, 5).texOffs(20, 30)
                .addBox(3, -10, -8, 4, 7, 5).texOffs(42, 0)
                .addBox(-3, -3, -11, 6, 3, 4).texOffs(38, 20)
                .addBox(-3, -7, 5, 6, 4, 4), PartPose.offset(0, 18, 0));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(DomainCreatureRenderer.CreatureRenderState state) {
        super.setupAnim(state);
        // Birds and arthropods are not four-footed mammals under a colored shell.
        // Body scale is reset every frame because one renderer serves every kind.
        Kind kind = state.kind;
        head.visible = switch (kind) {
            case BEACON_GULL, CAIRN_BEETLE, RIVET_CRAB -> false;
            default -> true;
        };
        feet.visible = switch (kind) {
            case INKLING, BEACON_GULL, CAIRN_BEETLE, CATALOG_OWL, RIVET_CRAB -> false;
            default -> true;
        };
        body.xScale = switch (kind) {
            case BEACON_GULL -> 0.82F;
            case DUST_SKIPPER -> 0.9F;
            case REEDRUNNER, CHORUS_HARE, CATALOG_OWL -> 0.82F;
            case CAIRN_BEETLE, RIVET_CRAB, SPLITSTONE_RAM -> 1.13F;
            default -> 1.0F;
        };
        body.yScale = switch (kind) {
            case INKLING, CAIRN_BEETLE, RIVET_CRAB -> 0.68F;
            case BEACON_GULL -> 0.85F;
            case SPLITSTONE_RAM -> 1.15F;
            default -> 1.0F;
        };
        body.zScale = kind == Kind.CATALOG_OWL ? 0.76F : 1.0F;
        head.xScale = kind == Kind.CATALOG_OWL ? 1.1F : 1.0F;
        head.yScale = kind == Kind.DUST_SKIPPER ? 0.82F : 1.0F;
        for (int i = 0; i < features.length; i++) features[i].visible = i == state.kind.ordinal();
        float bob = (float) Math.sin(state.ageInTicks * 0.14F) * 0.06F;
        float wingbeat = (float) Math.sin(state.ageInTicks * 0.35F) * 0.11F;
        gullLeftWing.zRot = 0.34F + wingbeat;
        gullRightWing.zRot = -0.34F - wingbeat;
        ModelPart active = features[state.kind.ordinal()];
        active.yRot = switch (state.kind) {
            case RIVET_CRAB -> (float) Math.sin(state.ageInTicks * 0.3F) * 0.08F;
            default -> 0.0F;
        };
        active.zRot = switch (state.kind) {
            case CATALOG_OWL -> (float) Math.sin(state.ageInTicks * 0.55F) * 0.12F;
            case DUST_SKIPPER -> (float) Math.sin(state.ageInTicks * 0.24F) * 0.06F;
            case REEDRUNNER -> state.reedReady
                    ? (float) Math.sin(state.ageInTicks * 0.45F) * 0.16F : 0.04F;
            case INKLING -> bob;
            default -> 0.0F;
        };
    }
}
