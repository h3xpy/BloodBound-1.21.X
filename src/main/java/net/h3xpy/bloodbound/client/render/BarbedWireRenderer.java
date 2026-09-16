package net.h3xpy.bloodbound.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.client.model.BarbedWireModel;
import net.h3xpy.bloodbound.entity.BarbedWireEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a coil of barbed wire with its Blockbench model, faded to the tier's opacity.
 * <p>
 * Translucent rather than cut out, since a trap drawn at a fifth of its opacity is the whole point
 * of it. The model is flipped and dropped the way every entity model is, which is what puts a part
 * modelled at y = 24 flat on the ground.
 */
public class BarbedWireRenderer extends EntityRenderer<BarbedWireEntity> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "textures/entity/barbed_wire.png");

    private final BarbedWireModel model;

    public BarbedWireRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new BarbedWireModel(context.bakeLayer(BarbedWireModel.LAYER_LOCATION));
    }

    @Override
    public void render(BarbedWireEntity wire, float yaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);

        int alpha = Math.round(wire.opacity() * 255.0F);
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        model.renderToBuffer(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, (alpha << 24) | 0xFFFFFF);

        poseStack.popPose();
        super.render(wire, yaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(BarbedWireEntity wire) {
        return TEXTURE;
    }
}
