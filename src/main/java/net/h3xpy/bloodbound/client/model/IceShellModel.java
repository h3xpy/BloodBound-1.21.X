package net.h3xpy.bloodbound.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.entity.IceShellEntity;
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
 * The block of ice, as one cube a shade larger than the player it holds.
 * <p>
 * Drawn on a translucent, unculled render type, so its faces show from the outside and from the
 * inside alike — the whole point being that the player can still see what is happening to them.
 */
public class IceShellModel extends EntityModel<IceShellEntity> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "ice_shell"), "main");

    /** Width and height of the cube, in model units: comfortably round a standing player. */
    private static final float WIDTH = 20.0F;
    private static final float HEIGHT = 34.0F;

    private final ModelPart cube;

    public IceShellModel(ModelPart root) {
        this.cube = root.getChild("cube");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("cube", CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-WIDTH / 2.0F, -HEIGHT, -WIDTH / 2.0F, WIDTH, HEIGHT, WIDTH,
                        new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        // A 16x16 sheet, which is what a block texture is: every face takes the whole of it.
        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public void setupAnim(IceShellEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch) {
        // Ice does not move.
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
            int color) {
        cube.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
