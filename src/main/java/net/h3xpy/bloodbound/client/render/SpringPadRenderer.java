package net.h3xpy.bloodbound.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.h3xpy.bloodbound.entity.SpringPadEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;

/**
 * Draws a Spring Pad as a flattened slime block on a thin piston base, until it has a model of its
 * own. The piston faces up, the way the pad throws.
 */
public class SpringPadRenderer extends EntityRenderer<SpringPadEntity> {

    public SpringPadRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SpringPadEntity pad, float yaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight) {
        var blocks = Minecraft.getInstance().getBlockRenderer();
        poseStack.pushPose();

        // The base: a thin slab of piston, the plate it springs from.
        poseStack.pushPose();
        poseStack.scale(0.9F, 0.08F, 0.9F);
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        blocks.renderSingleBlock(Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.UP),
                poseStack, buffers, packedLight,
                OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        // The spring itself, slightly smaller, sat on the base.
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.08D, 0.0D);
        poseStack.scale(0.75F, 0.12F, 0.75F);
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        blocks.renderSingleBlock(Blocks.SLIME_BLOCK.defaultBlockState(), poseStack, buffers, packedLight,
                OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        poseStack.popPose();
        super.render(pad, yaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(SpringPadEntity pad) {
        return ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png");
    }
}
