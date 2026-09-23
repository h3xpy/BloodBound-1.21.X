package net.h3xpy.bloodbound.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.h3xpy.bloodbound.entity.RitualEntity;
import net.h3xpy.bloodbound.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws a ritual with the block model Maxime built for it.
 * <p>
 * The model belongs to a registered block that is never placed, which is what gets it loaded and
 * baked by vanilla; here it is simply drawn where the marker entity stands, centred on it.
 */
public class RitualRenderer extends EntityRenderer<RitualEntity> {

    public RitualRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(RitualEntity ritual, float yaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight) {
        BlockState state = ModBlocks.RITUAL.get().defaultBlockState();

        poseStack.pushPose();
        // Block models are drawn from their corner; the entity stands in the middle of the ritual.
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        Minecraft.getInstance().getBlockRenderer()
                .renderSingleBlock(state, poseStack, buffers, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        super.render(ritual, yaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(RitualEntity ritual) {
        return ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png");
    }
}
