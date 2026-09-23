package net.h3xpy.bloodbound.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.h3xpy.bloodbound.client.model.IceShellModel;
import net.h3xpy.bloodbound.entity.IceShellEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the block of ice: vanilla's own ice texture, at two thirds opacity so the player inside can
 * still make out the world, and lit brightly enough to read in a cave.
 */
public class IceShellRenderer extends EntityRenderer<IceShellEntity> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/block/ice.png");

    /** How solid the ice looks. Enough to be unmistakable, not enough to blind whoever is in it. */
    private static final int ALPHA = 170;

    private final IceShellModel model;

    public IceShellRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new IceShellModel(context.bakeLayer(IceShellModel.LAYER_LOCATION));
    }

    @Override
    public void render(IceShellEntity shell, float yaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0D, -1.501D, 0.0D);

        VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        model.renderToBuffer(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY,
                (ALPHA << 24) | 0xFFFFFF);

        poseStack.popPose();
        super.render(shell, yaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(IceShellEntity shell) {
        return TEXTURE;
    }
}
