package net.h3xpy.bloodbound.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.entity.BarbedWireEntity;
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
 * The coil of barbed wire, as modelled in Blockbench.
 * <p>
 * The geometry is the export unchanged; only the parts 1.21 no longer accepts were brought up to
 * date — the layer id, and a render call that now takes one packed colour instead of four floats.
 */
public class BarbedWireModel extends EntityModel<BarbedWireEntity> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "barbed_wire"), "main");

    private final ModelPart main;

    public BarbedWireModel(ModelPart root) {
        this.main = root.getChild("bb_main");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition main = root.addOrReplaceChild("bb_main", CubeListBuilder.create()
                .texOffs(0, 17).addBox(-1.0F, -1.0F, -8.0F, 2.0F, 1.0F, 16.0F, new CubeDeformation(0.0F))
                .texOffs(1, 1).addBox(5.0F, -1.0F, 1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(1, 1).addBox(1.0F, -1.0F, -6.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(1, 1).addBox(-6.0F, -1.0F, -2.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(1, 1).addBox(-3.0F, -1.0F, 1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(1, 1).addBox(2.0F, -1.0F, -2.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(1, 1).addBox(1.0F, -1.0F, 5.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(1, 1).addBox(-2.0F, -1.0F, 3.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(1, 1).addBox(-2.0F, -1.0F, -4.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        main.addOrReplaceChild("cube_r1", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-1.0F, -1.0F, -8.0F, 2.0F, 1.0F, 16.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 1.5708F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(BarbedWireEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch) {
        // A coil of wire does not move.
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
            int color) {
        main.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
