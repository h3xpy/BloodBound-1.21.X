package net.h3xpy.bloodbound.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.h3xpy.bloodbound.entity.TargetFoundEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Draws a Target Found tripwire as a faint sight on the ground: a ring with a cross through it.
 * <p>
 * Lines rather than a model, since there is no texture for it yet — thin, faded to the tier's
 * opacity, and in a colour of its own so it is never mistaken for barbed wire.
 */
public class TargetFoundRenderer extends EntityRenderer<TargetFoundEntity> {

    private static final float RADIUS = 0.45F;
    private static final float LIFT = 0.04F;
    private static final int SEGMENTS = 16;
    private static final int COLOR = 0xE0C44C;

    public TargetFoundRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(TargetFoundEntity wire, float yaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight) {
        int color = (Math.round(wire.opacity() * 255.0F) << 24) | COLOR;
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        Matrix4f matrix = poseStack.last().pose();

        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2.0D * i / SEGMENTS;
            double a1 = Math.PI * 2.0D * (i + 1) / SEGMENTS;
            line(lines, matrix, (float) Math.cos(a0) * RADIUS, (float) Math.sin(a0) * RADIUS,
                    (float) Math.cos(a1) * RADIUS, (float) Math.sin(a1) * RADIUS, color);
        }
        float arm = RADIUS * 1.25F;
        line(lines, matrix, -arm, 0.0F, arm, 0.0F, color);
        line(lines, matrix, 0.0F, -arm, 0.0F, arm, color);

        super.render(wire, yaw, partialTick, poseStack, buffers, packedLight);
    }

    private static void line(VertexConsumer lines, Matrix4f matrix, float x0, float z0, float x1, float z1,
            int color) {
        lines.addVertex(matrix, x0, LIFT, z0).setColor(color).setNormal(0.0F, 1.0F, 0.0F);
        lines.addVertex(matrix, x1, LIFT, z1).setColor(color).setNormal(0.0F, 1.0F, 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(TargetFoundEntity wire) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }
}
