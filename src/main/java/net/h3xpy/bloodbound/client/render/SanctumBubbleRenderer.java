package net.h3xpy.bloodbound.client.render;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.entity.SanctumBubbleEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws a Holy Sanctum as a shield bubble: a sphere of glowing hexagons, seen from inside and out.
 * <p>
 * Two layers. A translucent shell, gold while the bubble is whole and redder the more it has taken,
 * flickering once it is close to breaking. And over it an additive glow that drifts across the
 * hexagons. A hit makes the side it came from flare white and the whole shell pulse, fading over
 * half a second; the bubble swells into being when it goes up.
 */
public class SanctumBubbleRenderer extends EntityRenderer<SanctumBubbleEntity> {

    private static final ResourceLocation SHELL =
            ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "textures/entity/sanctum_shell.png");

    private static final int RINGS = 18;
    private static final int SEGMENTS = 36;
    /** How many times the hexagons repeat around and from pole to pole. */
    private static final float TILES_AROUND = 6.0F;
    private static final float TILES_DOWN = 3.0F;

    private static final float GROW_TICKS = 6.0F;
    private static final float FLASH_TICKS = 10.0F;

    public SanctumBubbleRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SanctumBubbleEntity bubble, float yaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight) {
        float age = bubble.tickCount + partialTick;
        float grow = Math.min(1.0F, age / GROW_TICKS);
        float radius = bubble.radius() * (1.0F - (1.0F - grow) * (1.0F - grow));

        float health = bubble.healthShare();
        float flash = Math.max(0.0F, 1.0F - bubble.ticksSinceHit(partialTick) / FLASH_TICKS);
        // Gold when whole, through orange to red as it wears down.
        float red = 1.0F;
        float green = Mth.lerp(health, 0.25F, 0.85F);
        float blue = Mth.lerp(health, 0.15F, 0.35F);
        float flicker = health < 0.3F ? 0.75F + 0.25F * Mth.sin(age * 1.7F) : 1.0F;

        poseStack.pushPose();
        Vector3f hit = bubble.hitDirection();

        VertexConsumer shell = buffers.getBuffer(RenderType.entityTranslucent(SHELL));
        sphere(poseStack, shell, radius, red, green, blue, 0.32F * flicker, flash, hit, 0.0F, 0.0F);

        // The glow runs a hair outside the shell, so the two never fight over the same pixels.
        float drift = age * 0.004F;
        VertexConsumer glow = buffers.getBuffer(RenderType.energySwirl(SHELL, drift, drift * 0.6F));
        sphere(poseStack, glow, radius * 1.01F, red * 0.45F, green * 0.45F, blue * 0.45F, 1.0F * flicker,
                flash, hit, 0.0F, 0.0F);

        poseStack.popPose();
        super.render(bubble, yaw, partialTick, poseStack, buffers, packedLight);
    }

    /** One UV sphere, brighter towards the side the last hit came from while the flash lasts. */
    private static void sphere(PoseStack poseStack, VertexConsumer consumer, float radius, float r, float g, float b,
            float alpha, float flash, Vector3f hit, float uOffset, float vOffset) {
        PoseStack.Pose pose = poseStack.last();
        for (int ring = 0; ring < RINGS; ring++) {
            float theta0 = Mth.PI * ring / RINGS;
            float theta1 = Mth.PI * (ring + 1) / RINGS;
            for (int segment = 0; segment < SEGMENTS; segment++) {
                float phi0 = Mth.TWO_PI * segment / SEGMENTS;
                float phi1 = Mth.TWO_PI * (segment + 1) / SEGMENTS;
                vertex(pose, consumer, radius, theta0, phi0, ring, segment, r, g, b, alpha, flash, hit, uOffset, vOffset);
                vertex(pose, consumer, radius, theta1, phi0, ring + 1, segment, r, g, b, alpha, flash, hit, uOffset, vOffset);
                vertex(pose, consumer, radius, theta1, phi1, ring + 1, segment + 1, r, g, b, alpha, flash, hit, uOffset, vOffset);
                vertex(pose, consumer, radius, theta0, phi1, ring, segment + 1, r, g, b, alpha, flash, hit, uOffset, vOffset);
            }
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, float radius, float theta, float phi,
            int ring, int segment, float r, float g, float b, float alpha, float flash, Vector3f hit,
            float uOffset, float vOffset) {
        float nx = Mth.sin(theta) * Mth.cos(phi);
        float ny = Mth.cos(theta);
        float nz = Mth.sin(theta) * Mth.sin(phi);

        // Where the hit landed flares nearly white; the rest of the shell pulses a little with it.
        float facing = Math.max(0.0F, nx * hit.x() + ny * hit.y() + nz * hit.z());
        float spot = flash * facing * facing * facing * facing;
        float lift = Math.min(1.0F, spot + flash * 0.2F);
        float red = Mth.lerp(lift, r, 1.0F);
        float green = Mth.lerp(lift, g, 1.0F);
        float blue = Mth.lerp(lift, b, 1.0F);
        float a = Math.min(1.0F, alpha + spot * 0.6F + flash * 0.1F);

        float u = segment * TILES_AROUND / SEGMENTS + uOffset;
        float v = ring * TILES_DOWN / RINGS + vOffset;
        consumer.addVertex(pose, nx * radius, ny * radius, nz * radius)
                .setColor(red, green, blue, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
    }

    @Override
    public ResourceLocation getTextureLocation(SanctumBubbleEntity bubble) {
        return SHELL;
    }
}
