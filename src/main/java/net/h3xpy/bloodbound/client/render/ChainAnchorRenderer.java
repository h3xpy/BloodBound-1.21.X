package net.h3xpy.bloodbound.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.h3xpy.bloodbound.entity.ChainAnchorEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

/**
 * Draws Chained Up's anchor as a small lodestone, until it has a model of its own. The chain itself
 * is drawn by the server in particles, from the anchor to whatever it holds.
 */
public class ChainAnchorRenderer extends EntityRenderer<ChainAnchorEntity> {

    private static final float SCALE = 0.5F;

    public ChainAnchorRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ChainAnchorEntity anchor, float yaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();
        poseStack.scale(SCALE, SCALE, SCALE);
        // Block models are drawn from their corner; the anchor stands in the middle.
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.LODESTONE.defaultBlockState(),
                poseStack, buffers, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(anchor, yaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(ChainAnchorEntity anchor) {
        return ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png");
    }
}
