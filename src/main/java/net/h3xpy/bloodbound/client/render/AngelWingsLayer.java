package net.h3xpy.bloodbound.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.client.ClientAngelWings;
import net.h3xpy.bloodbound.client.model.AngelWingsModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws Beware The Power Of An Angel's wings on whoever has them out, fixed to their back. They
 * glow a little, lit at full brightness whatever the light around them.
 */
public class AngelWingsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "textures/entity/angel_wings.png");

    private final AngelWingsModel wings;

    public AngelWingsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
            EntityModelSet models) {
        super(parent);
        this.wings = new AngelWingsModel(models.bakeLayer(AngelWingsModel.LAYER_LOCATION));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
            float headPitch) {
        if (player.isInvisible() || !ClientAngelWings.hasWings(player.getId())) {
            return;
        }
        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        wings.setupAnim(ageInTicks, !player.onGround());
        wings.renderToBuffer(poseStack, buffers.getBuffer(wings.renderType(TEXTURE)), LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}
