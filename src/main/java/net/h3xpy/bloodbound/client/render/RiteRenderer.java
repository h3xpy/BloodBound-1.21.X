package net.h3xpy.bloodbound.client.render;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.h3xpy.bloodbound.entity.RiteEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Draws the consecration of a ritual being laid, in additive light.
 * <p>
 * Blessed: a shaft of pale gold light drops out of the sky onto the heart of it; when it lands, soft
 * cyan rings bloom out to the edge one after another, twelve rays sweep slowly round, and a golden
 * halo hangs turning above the middle. Everything is soft and wide, and fades gently.
 * <p>
 * Cursed: the ground cracks open from the middle — jagged glowing fissures, each the same for the
 * same ritual, forking as they run out to the edge — while a ragged ring of fire burns in round the
 * rim. At its height the cracks throb hot orange, then cool to a dull red and die.
 */
public class RiteRenderer extends EntityRenderer<RiteEntity> {

    private static final float LIFT = 0.05F;

    public RiteRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(RiteEntity rite, float yaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight) {
        float age = rite.tickCount + partialTick;
        if (age > RiteEntity.LIFE_TICKS) {
            return;
        }
        float fade = Mth.clamp((RiteEntity.LIFE_TICKS - age) / 25.0F, 0.0F, 1.0F);
        poseStack.pushPose();
        poseStack.translate(0.0F, LIFT, 0.0F);
        Glow glow = new Glow(buffers.getBuffer(RenderType.lightning()), poseStack.last().pose());
        if (rite.blessed()) {
            blessing(glow, rite.radius(), age, fade);
        } else {
            curse(glow, rite.radius(), age, fade, rite.getId());
        }
        poseStack.popPose();
        super.render(rite, yaw, partialTick, poseStack, buffers, packedLight);
    }

    // --- blessed ---

    private static void blessing(Glow glow, float radius, float age, float fade) {
        float landed = 14.0F;
        // The shaft of light: dropping from high above, then standing and thinning away.
        float drop = Mth.clamp(age / landed, 0.0F, 1.0F);
        float top = 14.0F;
        float bottom = top * (1.0F - drop);
        float shaftWidth = 0.35F + 0.5F * Mth.clamp((age - landed) / 6.0F, 0.0F, 1.0F) * (1.0F - Mth.clamp((age - landed - 6.0F) / 30.0F, 0.0F, 1.0F));
        float shaftAlpha = fade * (age < landed ? 0.8F : Mth.clamp(1.0F - (age - landed) / 35.0F, 0.0F, 1.0F));
        glow.pillar(bottom, top, shaftWidth * 2.2F, 1.0F, 0.95F, 0.7F, shaftAlpha * 0.25F);
        glow.pillar(bottom, top, shaftWidth, 1.0F, 1.0F, 0.9F, shaftAlpha);

        if (age < landed) {
            return;
        }
        float since = age - landed;
        // Three soft rings blooming out to the edge, one after another.
        for (int i = 0; i < 3; i++) {
            float t = (since - i * 7.0F) / 22.0F;
            if (t <= 0.0F) {
                continue;
            }
            float r = radius * Math.min(1.0F, t);
            float a = fade * (t < 1.0F ? 0.9F : Mth.clamp(1.6F - t * 0.6F, 0.25F, 0.9F));
            glow.ring(r, 0.18F, 0.55F, 0.95F, 1.0F, a * 0.35F, 0.45F);
            glow.ring(r, 0.07F, 0.85F, 1.0F, 1.0F, a, 0.15F);
        }
        // Twelve rays reaching out and turning slowly.
        float reach = radius * Mth.clamp(since / 18.0F, 0.0F, 1.0F);
        float turn = since * 0.8F * Mth.DEG_TO_RAD;
        for (int i = 0; i < 12; i++) {
            float angle = turn + Mth.TWO_PI * i / 12.0F;
            float x = Mth.cos(angle) * reach;
            float z = Mth.sin(angle) * reach;
            glow.line(0.0F, 0.0F, x, z, 0.0F, 0.35F, 0.45F, 0.8F, 1.0F, fade * 0.2F);
            glow.line(0.0F, 0.0F, x, z, 0.0F, 0.08F, 0.85F, 0.97F, 1.0F, fade * 0.7F);
        }
        // The halo, hanging and turning above the heart.
        float halo = Mth.clamp(since / 10.0F, 0.0F, 1.0F);
        glow.tiltedRing(0.75F * halo, 2.6F + 0.15F * Mth.sin(age * 0.15F), 0.1F, 1.0F, 0.85F, 0.35F, fade * 0.9F,
                since * 3.0F * Mth.DEG_TO_RAD);
    }

    // --- cursed ---

    private static void curse(Glow glow, float radius, float age, float fade, int seed) {
        float spread = Mth.clamp(age / RiteEntity.PEAK_TICKS, 0.0F, 1.0F);
        // Throbbing hot at the height, cooling to a dull red as it dies.
        float heat = Mth.clamp(1.0F - Math.abs(age - RiteEntity.PEAK_TICKS - 8.0F) / 25.0F, 0.0F, 1.0F);
        float throb = 0.75F + 0.25F * Mth.sin(age * 0.9F);
        float r = 0.75F + 0.25F * heat;
        float g = 0.05F + 0.4F * heat * throb;
        float b = 0.02F;
        float a = fade * (0.6F + 0.4F * throb);

        RandomSource random = RandomSource.create(seed * 341873128712L);
        int cracks = 9;
        for (int i = 0; i < cracks; i++) {
            float base = Mth.TWO_PI * i / cracks + (random.nextFloat() - 0.5F) * 0.4F;
            crack(glow, random, 0.0F, 0.0F, base, radius, spread, r, g, b, a, 0.11F, 2);
        }
        // The rim burning in, ragged and flickering, a step behind the cracks.
        float rim = Mth.clamp((age - 12.0F) / 15.0F, 0.0F, 1.0F);
        if (rim > 0.0F) {
            int segments = 72;
            int drawn = Math.round(segments * rim);
            for (int i = 0; i < drawn; i++) {
                float a0 = Mth.TWO_PI * i / segments;
                float a1 = Mth.TWO_PI * (i + 1) / segments;
                float j0 = radius * (1.0F + 0.03F * Mth.sin(i * 2.7F + age * 0.5F));
                float j1 = radius * (1.0F + 0.03F * Mth.sin((i + 1) * 2.7F + age * 0.5F));
                glow.line(Mth.cos(a0) * j0, Mth.sin(a0) * j0, Mth.cos(a1) * j1, Mth.sin(a1) * j1, 0.0F,
                        0.45F, 0.6F, 0.02F, 0.0F, a * 0.35F);
                glow.line(Mth.cos(a0) * j0, Mth.sin(a0) * j0, Mth.cos(a1) * j1, Mth.sin(a1) * j1, 0.0F,
                        0.12F, r, g, b, a);
            }
        }
    }

    /**
     * One fissure, as far as it has got: a run of jagged steps outwards from here, forking now and
     * then. The same seed always draws the same crack, so it does not shimmer from frame to frame.
     */
    private static void crack(Glow glow, RandomSource random, float x, float z, float heading, float length,
            float share, float r, float g, float b, float a, float width, int forks) {
        int steps = 7;
        float step = length / steps;
        float drawn = length * share;
        float travelled = 0.0F;
        for (int i = 0; i < steps; i++) {
            heading += (random.nextFloat() - 0.5F) * 0.9F;
            float nx = x + Mth.cos(heading) * step;
            float nz = z + Mth.sin(heading) * step;
            boolean fork = forks > 0 && i > 1 && random.nextFloat() < 0.3F;
            float forkHeading = heading + (random.nextBoolean() ? 0.7F : -0.7F);
            if (travelled < drawn) {
                float part = Math.min(1.0F, (drawn - travelled) / step);
                float ex = Mth.lerp(part, x, nx);
                float ez = Mth.lerp(part, z, nz);
                float w = width * (1.0F - 0.6F * i / steps);
                glow.line(x, z, ex, ez, 0.0F, w * 3.5F, 0.55F, 0.0F, 0.0F, a * 0.3F);
                glow.line(x, z, ex, ez, 0.0F, w, r, g, b, a);
            }
            if (fork) {
                float remaining = (length - travelled) * 0.45F;
                float forkShare = Mth.clamp((drawn - travelled) / Math.max(remaining, 0.01F), 0.0F, 1.0F);
                if (forkShare > 0.0F) {
                    crack(glow, random, nx, nz, forkHeading, remaining, forkShare, r, g, b, a * 0.8F, width * 0.6F, forks - 1);
                } else {
                    // Keep the random sequence the same whether or not the fork shows yet.
                    crack(glow, random, nx, nz, forkHeading, remaining, 0.0F, r, g, b, 0.0F, width * 0.6F, forks - 1);
                }
            }
            travelled += step;
            x = nx;
            z = nz;
        }
    }

    /** Additive glowing shapes, flat on the ground or standing up. */
    private record Glow(VertexConsumer buffer, Matrix4f pose) {

        void line(float x0, float z0, float x1, float z1, float y, float width, float r, float g, float b, float a) {
            if (a <= 0.0F) {
                return;
            }
            float dx = x1 - x0;
            float dz = z1 - z0;
            float length = Mth.sqrt(dx * dx + dz * dz);
            if (length < 1.0E-4F) {
                return;
            }
            float nx = -dz / length * width * 0.5F;
            float nz = dx / length * width * 0.5F;
            quad(x0 + nx, y, z0 + nz, x1 + nx, y, z1 + nz, x1 - nx, y, z1 - nz, x0 - nx, y, z0 - nz, r, g, b, a);
        }

        void ring(float radius, float width, float r, float g, float b, float a, float softness) {
            int segments = Math.max(24, Math.round(radius * 8.0F));
            for (int i = 0; i < segments; i++) {
                float a0 = Mth.TWO_PI * i / segments;
                float a1 = Mth.TWO_PI * (i + 1) / segments;
                line(Mth.cos(a0) * radius, Mth.sin(a0) * radius, Mth.cos(a1) * radius, Mth.sin(a1) * radius,
                        0.0F, width, r, g, b, a);
            }
        }

        /** A ring held up in the air, turning, tilted a little: the halo. */
        void tiltedRing(float radius, float y, float width, float r, float g, float b, float a, float turn) {
            if (radius <= 0.01F) {
                return;
            }
            int segments = 32;
            for (int i = 0; i < segments; i++) {
                float a0 = turn + Mth.TWO_PI * i / segments;
                float a1 = turn + Mth.TWO_PI * (i + 1) / segments;
                float y0 = y + Mth.sin(a0) * 0.12F;
                float y1 = y + Mth.sin(a1) * 0.12F;
                float x0 = Mth.cos(a0) * radius;
                float z0 = Mth.sin(a0) * radius;
                float x1 = Mth.cos(a1) * radius;
                float z1 = Mth.sin(a1) * radius;
                // Drawn as a band standing up, so it reads from the side as well as from above.
                quad(x0, y0 - width, z0, x1, y1 - width, z1, x1, y1 + width, z1, x0, y0 + width, z0, r, g, b, a);
            }
        }

        /** A column of light, as two crossed upright planes. */
        void pillar(float bottom, float top, float width, float r, float g, float b, float a) {
            if (a <= 0.0F || top <= bottom) {
                return;
            }
            float h = width * 0.5F;
            quad(-h, bottom, 0.0F, h, bottom, 0.0F, h, top, 0.0F, -h, top, 0.0F, r, g, b, a);
            quad(0.0F, bottom, -h, 0.0F, bottom, h, 0.0F, top, h, 0.0F, top, -h, r, g, b, a);
        }

        private void quad(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                float dx, float dy, float dz, float r, float g, float b, float a) {
            // Both windings, so it shows from either side.
            vertex(ax, ay, az, r, g, b, a);
            vertex(bx, by, bz, r, g, b, a);
            vertex(cx, cy, cz, r, g, b, a);
            vertex(dx, dy, dz, r, g, b, a);
            vertex(dx, dy, dz, r, g, b, a);
            vertex(cx, cy, cz, r, g, b, a);
            vertex(bx, by, bz, r, g, b, a);
            vertex(ax, ay, az, r, g, b, a);
        }

        private void vertex(float x, float y, float z, float r, float g, float b, float a) {
            buffer.addVertex(pose, x, y, z).setColor(r, g, b, a);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(RiteEntity rite) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
