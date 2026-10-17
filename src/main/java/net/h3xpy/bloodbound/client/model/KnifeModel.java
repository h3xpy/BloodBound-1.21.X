package net.h3xpy.bloodbound.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Sacrificial Knife: a ritual dagger. The blade runs forward along +Z from the guard, which sits at
 * the origin; the handle runs back behind it. Stuck in something, the blade is the part that goes in
 * and the handle is what is left showing.
 */
public class KnifeModel extends Model {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "knife"), "main");

    private final ModelPart root;

    public KnifeModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // Blade: thin and flat, with a narrower tip.
        root.addOrReplaceChild("blade", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-0.5F, -1.0F, 0.5F, 1.0F, 2.0F, 6.0F), PartPose.ZERO);
        root.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-0.5F, -0.5F, 6.5F, 1.0F, 1.0F, 1.5F), PartPose.ZERO);
        // Guard: a short crossbar.
        root.addOrReplaceChild("guard", CubeListBuilder.create().texOffs(0, 8)
                .addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F), PartPose.ZERO);
        // Handle, ending in a pommel.
        root.addOrReplaceChild("handle", CubeListBuilder.create().texOffs(0, 13)
                .addBox(-0.5F, -0.5F, -4.5F, 1.0F, 1.0F, 4.0F), PartPose.ZERO);
        root.addOrReplaceChild("pommel", CubeListBuilder.create().texOffs(10, 8)
                .addBox(-0.75F, -0.75F, -5.5F, 1.5F, 1.5F, 1.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
