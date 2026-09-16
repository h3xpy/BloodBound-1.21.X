package net.h3xpy.bloodbound.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.client.model.TargetFoundModel;
import net.h3xpy.bloodbound.entity.TargetFoundEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a Target Found tripwire with its Blockbench model, faded to the tier's opacity.
 * <p>
 * Same set-up as {@link BarbedWireRenderer}: translucent, flipped and dropped like every entity
 * model. The strands are flat planes, so they sit a hair above the ground to avoid z-fighting.
 */
public class TargetFoundRenderer extends EntityRenderer<TargetFoundEntity> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "textures/entity/target_found.png");

    private final TargetFoundModel model;

    public TargetFoundRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new TargetFoundModel(context.bakeLayer(TargetFoundModel.LAYER_LOCATION));
    }

    @Override
    public void render(TargetFoundEntity wire, float yaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.51F, 0.0F);

        int alpha = Math.round(wire.opacity() * 255.0F);
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        model.renderToBuffer(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, (alpha << 24) | 0xFFFFFF);

        poseStack.popPose();
        super.render(wire, yaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(TargetFoundEntity wire) {
        return TEXTURE;
    }
}
