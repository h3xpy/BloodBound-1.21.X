package net.h3xpy.bloodbound.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.client.model.KnifeModel;
import net.h3xpy.bloodbound.entity.KnifeEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the Sacrificial Knife: along its flight while it flies, and, once stuck, on the thing it is
 * stuck in — worked out from that thing's own smoothed position and body turn every frame, so the
 * knife moves with it exactly rather than trailing a step behind.
 */
public class KnifeRenderer extends EntityRenderer<KnifeEntity> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "textures/entity/knife.png");
    /** How far out of the wound the guard sits, so a little of the blade shows. */
    private static final double SHOWING = 0.3D;
    /** The model is drawn a little larger than life, so it reads at a distance. */
    private static final float SCALE = 1.4F;

    private final KnifeModel model;

    public KnifeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new KnifeModel(context.bakeLayer(KnifeModel.LAYER_LOCATION));
    }

    @Override
    public void render(KnifeEntity knife, float yaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();
        Vec3 direction;
        Entity host = knife.stuckTo() >= 0 ? knife.level().getEntity(knife.stuckTo()) : null;
        if (host instanceof LivingEntity target) {
            float bodyYaw = Mth.rotLerp(partialTick, target.yBodyRotO, target.yBodyRot) * Mth.DEG_TO_RAD;
            Vec3 at = target.getPosition(partialTick).add(knife.localOffset().yRot(-bodyYaw));
            direction = knife.direction().yRot(-bodyYaw);
            at = at.subtract(direction.normalize().scale(SHOWING));
            Vec3 drawnAt = knife.getPosition(partialTick);
            poseStack.translate(at.x - drawnAt.x, at.y - drawnAt.y, at.z - drawnAt.z);
        } else {
            direction = knife.direction();
        }

        Vec3 d = direction.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : direction.normalize();
        poseStack.mulPose(Axis.YP.rotation((float) Mth.atan2(d.x, d.z)));
        poseStack.mulPose(Axis.XP.rotation((float) -Math.asin(Mth.clamp(d.y, -1.0D, 1.0D))));
        poseStack.scale(SCALE, SCALE, SCALE);
        model.renderToBuffer(poseStack, buffers.getBuffer(model.renderType(TEXTURE)), packedLight,
                OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        poseStack.popPose();
        super.render(knife, yaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(KnifeEntity knife) {
        return TEXTURE;
    }
}
