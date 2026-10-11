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
import net.minecraft.util.Mth;

/**
 * Beware The Power Of An Angel's wings, after the perk's icon: on each side a fan of long blade-like
 * feathers from the shoulder blades — two sweeping up, three spread level, one hanging low — each
 * a little shorter than the last.
 * <p>
 * Built in the player body's own space, so the layer only has to move it to the body and draw it.
 */
public class AngelWingsModel extends Model {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "angel_wings"), "main");

    /** The feathers of one wing: length in pixels, and the angle off level, negative going up. */
    private static final float[][] FEATHERS = {
        { 16.0F, -62.0F },
        { 15.0F, -40.0F },
        { 18.0F, -12.0F },
        { 16.0F, 8.0F },
        { 13.0F, 27.0F },
        { 10.0F, 48.0F },
    };
    /** How thick a feather is, and how deep. */
    private static final float FEATHER_HEIGHT = 3.0F;
    private static final float FEATHER_DEPTH = 1.0F;
    /** Where the wings leave the back: either side of the spine, up between the shoulder blades. */
    private static final float ROOT_X = 1.5F;
    private static final float ROOT_Y = 3.0F;
    private static final float ROOT_Z = 2.5F;

    private final ModelPart leftWing;
    private final ModelPart rightWing;

    public AngelWingsModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.leftWing = root.getChild("left_wing");
        this.rightWing = root.getChild("right_wing");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition left = root.addOrReplaceChild("left_wing", CubeListBuilder.create(),
                PartPose.offset(ROOT_X, ROOT_Y, ROOT_Z));
        PartDefinition right = root.addOrReplaceChild("right_wing", CubeListBuilder.create(),
                PartPose.offset(-ROOT_X, ROOT_Y, ROOT_Z));

        for (int i = 0; i < FEATHERS.length; i++) {
            float length = FEATHERS[i][0];
            float angle = FEATHERS[i][1] * Mth.DEG_TO_RAD;
            // Each feather takes its own strip of the sheet, four pixels high.
            int v = i * 4;
            left.addOrReplaceChild("feather_" + i, CubeListBuilder.create().texOffs(0, v)
                            .addBox(0.0F, -FEATHER_HEIGHT / 2.0F, -FEATHER_DEPTH / 2.0F, length, FEATHER_HEIGHT, FEATHER_DEPTH),
                    PartPose.offsetAndRotation(0.0F, i * 0.6F, 0.0F, 0.0F, 0.0F, angle));
            right.addOrReplaceChild("feather_" + i, CubeListBuilder.create().texOffs(0, v).mirror()
                            .addBox(-length, -FEATHER_HEIGHT / 2.0F, -FEATHER_DEPTH / 2.0F, length, FEATHER_HEIGHT, FEATHER_DEPTH),
                    PartPose.offsetAndRotation(0.0F, i * 0.6F, 0.0F, 0.0F, 0.0F, -angle));
        }
        return LayerDefinition.create(mesh, 64, 32);
    }

    /**
     * Sets the wings for this moment: swept back by default, beating while flying.
     *
     * @param ageInTicks the player's age, for the beat
     * @param flying     whether the wings carry the player right now
     */
    public void setupAnim(float ageInTicks, boolean flying) {
        float beat = flying ? Mth.sin(ageInTicks * 0.35F) : Mth.sin(ageInTicks * 0.08F) * 0.2F;
        float sweep = (30.0F + 22.0F * beat) * Mth.DEG_TO_RAD;
        float lift = 10.0F * beat * Mth.DEG_TO_RAD;
        leftWing.yRot = -sweep;
        leftWing.zRot = -lift;
        rightWing.yRot = sweep;
        rightWing.zRot = lift;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        leftWing.render(poseStack, buffer, packedLight, packedOverlay, color);
        rightWing.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
