package net.h3xpy.bloodbound.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.entity.TargetFoundEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * The Target Found tripwire, as modelled in Blockbench: three flat strands lying on the ground.
 * <p>
 * The geometry is the export unchanged; only the layer id and the render call were brought up to
 * date for 1.21, as for {@link BarbedWireModel}.
 */
public class TargetFoundModel extends EntityModel<TargetFoundEntity> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "target_found"), "main");

    private final ModelPart main;

    public TargetFoundModel(ModelPart root) {
        this.main = root.getChild("bb_main");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition main = root.addOrReplaceChild("bb_main", CubeListBuilder.create(),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        main.addOrReplaceChild("cube_r1", CubeListBuilder.create()
                .texOffs(0, 2).addBox(-2.0F, 0.0F, -1.0F, 14.0F, 0.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(3.0F, 0.0F, -5.0F, 0.0F, -1.4835F, 0.0F));
        main.addOrReplaceChild("cube_r2", CubeListBuilder.create()
                .texOffs(0, 1).addBox(-2.0F, 0.0F, 0.0F, 14.0F, 0.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-5.0F, 0.0F, 0.0F, 0.0F, -0.5236F, 0.0F));
        main.addOrReplaceChild("cube_r3", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2.0F, 0.0F, -1.0F, 14.0F, 0.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-5.0F, 0.0F, 0.0F, 0.0F, 0.5236F, 0.0F));

        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void setupAnim(TargetFoundEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch) {
        // A tripwire does not move.
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
            int color) {
        main.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
