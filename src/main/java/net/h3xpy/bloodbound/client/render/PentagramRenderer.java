package net.h3xpy.bloodbound.client.render;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.h3xpy.bloodbound.entity.PentagramEntity;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws Salvation's pentagram as it is drawn: glowing lines laid on the ground, the way a pen would.
 * <p>
 * The outer circle sweeps round first, then the inner one the other way, then the five strokes of
 * the star one after another, each with a white-hot tip where the "pen" is. Once whole it charges,
 * brightening and pulsing, flares white the moment it binds, throws a ring outwards and fades. The
 * figure turns slowly all the while.
 * <p>
 * Everything is additive light ({@link RenderType#lightning()}), drawn twice: a wide dim band for the
 * glow and a narrow bright core for the line itself.
 */
public class PentagramRenderer extends EntityRenderer<PentagramEntity> {

    /** When each part is drawn, in ticks of the pentagram's life. */
    private static final float OUTER_START = 0.0F;
    private static final float OUTER_END = 14.0F;
    private static final float INNER_START = 8.0F;
    private static final float INNER_END = 22.0F;
    private static final float STAR_START = 20.0F;
    private static final float STAR_STROKE = 5.0F;
    private static final float CHARGE_START = 45.0F;

    private static final int CIRCLE_SEGMENTS = 96;
    /** Inner circle, and the star's points, as a share of the radius. */
    private static final float INNER = 0.82F;
    /** Drawn a hair above the ground so it never fights the blocks for pixels. */
    private static final float LIFT = 0.04F;

    public PentagramRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(PentagramEntity pentagram, float yaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight) {
        float age = pentagram.tickCount + partialTick;
        float radius = pentagram.radius();
        float bind = ModPerks.SALVATION_DRAW_TICKS;
        float end = bind + ModPerks.SALVATION_FADE_TICKS;
        if (age > end) {
            return;
        }

        // Colour: deep crimson while drawn, brightening to orange as it charges, white when it binds.
        float charge = Mth.clamp((age - CHARGE_START) / (bind - CHARGE_START), 0.0F, 1.0F);
        float flare = age >= bind ? Mth.clamp(1.0F - (age - bind) / 8.0F, 0.0F, 1.0F) : 0.0F;
        float fade = age > bind ? Mth.clamp(1.0F - (age - bind) / ModPerks.SALVATION_FADE_TICKS, 0.0F, 1.0F) : 1.0F;
        float pulse = 0.85F + 0.15F * Mth.sin(age * 0.6F) + 0.35F * charge * Mth.sin(age * 1.7F);
        float r = Mth.lerp(flare, Mth.lerp(charge, 0.85F, 1.0F), 1.0F);
        float g = Mth.lerp(flare, Mth.lerp(charge, 0.06F, 0.45F), 0.95F);
        float b = Mth.lerp(flare, Mth.lerp(charge, 0.08F, 0.1F), 0.85F);
        float alpha = Mth.clamp(pulse * fade, 0.0F, 1.0F);

        float width = 0.08F + radius * 0.004F;
        float turn = age * 0.3F * Mth.DEG_TO_RAD;

        poseStack.pushPose();
        poseStack.translate(0.0F, LIFT, 0.0F);
        Matrix4f pose = poseStack.last().pose();
        VertexConsumer buffer = buffers.getBuffer(RenderType.lightning());
        Line line = new Line(buffer, pose, r, g, b, alpha, width);

        // Outer circle, sweeping clockwise; inner circle, the other way.
        line.arc(radius, turn, progress(age, OUTER_START, OUTER_END), 1.0F);
        line.arc(radius * INNER, turn, progress(age, INNER_START, INNER_END), -1.0F);

        // The star: five strokes joining every second point on the inner circle.
        float[][] points = new float[5][2];
        for (int i = 0; i < 5; i++) {
            float angle = turn - Mth.HALF_PI + i * Mth.TWO_PI * 2.0F / 5.0F;
            points[i][0] = Mth.cos(angle) * radius * INNER;
            points[i][1] = Mth.sin(angle) * radius * INNER;
        }
        for (int i = 0; i < 5; i++) {
            float start = STAR_START + i * STAR_STROKE;
            float share = progress(age, start, start + STAR_STROKE);
            if (share <= 0.0F) {
                break;
            }
            float[] from = points[i];
            float[] to = points[(i + 1) % 5];
            float x = Mth.lerp(share, from[0], to[0]);
            float z = Mth.lerp(share, from[1], to[1]);
            line.segment(from[0], from[1], x, z);
            if (share < 1.0F) {
                line.tip(x, z);
            }
        }

        // Once whole, a small ring at each point, and while it charges, they swell.
        if (age >= STAR_START + 5 * STAR_STROKE) {
            float ring = radius * (0.07F + 0.03F * charge);
            for (float[] point : points) {
                line.ring(point[0], point[1], ring);
            }
        }

        // The bind: a ring thrown outwards from the edge as it flares.
        if (age >= bind) {
            float wave = radius * (1.0F + 0.35F * (age - bind) / ModPerks.SALVATION_FADE_TICKS);
            Line shock = new Line(buffer, pose, 1.0F, 0.9F, 0.85F, fade * 0.8F, width * 2.0F);
            shock.arc(wave, turn, 1.0F, 1.0F);
        }
        poseStack.popPose();
        super.render(pentagram, yaw, partialTick, poseStack, buffers, packedLight);
    }

    private static float progress(float age, float start, float end) {
        return Mth.clamp((age - start) / (end - start), 0.0F, 1.0F);
    }

    /** Lays glowing lines flat on the ground: a dim wide band for the glow, a bright narrow core. */
    private record Line(VertexConsumer buffer, Matrix4f pose, float r, float g, float b, float alpha, float width) {

        void arc(float radius, float start, float share, float direction) {
            int segments = Math.round(CIRCLE_SEGMENTS * share);
            for (int i = 0; i < segments; i++) {
                float a0 = start + direction * Mth.TWO_PI * i / CIRCLE_SEGMENTS;
                float a1 = start + direction * Mth.TWO_PI * (i + 1) / CIRCLE_SEGMENTS;
                segment(Mth.cos(a0) * radius, Mth.sin(a0) * radius, Mth.cos(a1) * radius, Mth.sin(a1) * radius);
            }
            if (share > 0.0F && share < 1.0F) {
                float a = start + direction * Mth.TWO_PI * share;
                tip(Mth.cos(a) * radius, Mth.sin(a) * radius);
            }
        }

        void ring(float x, float z, float radius) {
            for (int i = 0; i < 24; i++) {
                float a0 = Mth.TWO_PI * i / 24.0F;
                float a1 = Mth.TWO_PI * (i + 1) / 24.0F;
                segment(x + Mth.cos(a0) * radius, z + Mth.sin(a0) * radius,
                        x + Mth.cos(a1) * radius, z + Mth.sin(a1) * radius);
            }
        }

        void segment(float x0, float z0, float x1, float z1) {
            band(x0, z0, x1, z1, width * 3.0F, alpha * 0.25F);
            band(x0, z0, x1, z1, width, alpha);
        }

        /** The pen: a white-hot blot where the line is being drawn. */
        void tip(float x, float z) {
            float s = width * 3.5F;
            quad(x - s, z - s, x + s, z - s, x + s, z + s, x - s, z + s, 1.0F, 0.95F, 0.85F, alpha);
        }

        private void band(float x0, float z0, float x1, float z1, float w, float a) {
            float dx = x1 - x0;
            float dz = z1 - z0;
            float length = Mth.sqrt(dx * dx + dz * dz);
            if (length < 1.0E-4F) {
                return;
            }
            float nx = -dz / length * w * 0.5F;
            float nz = dx / length * w * 0.5F;
            quad(x0 + nx, z0 + nz, x1 + nx, z1 + nz, x1 - nx, z1 - nz, x0 - nx, z0 - nz, r, g, b, a);
        }

        private void quad(float ax, float az, float bx, float bz, float cx, float cz, float dx, float dz,
                float cr, float cg, float cb, float ca) {
            // Both windings, so it shows from above and from below alike.
            vertex(ax, az, cr, cg, cb, ca);
            vertex(bx, bz, cr, cg, cb, ca);
            vertex(cx, cz, cr, cg, cb, ca);
            vertex(dx, dz, cr, cg, cb, ca);
            vertex(dx, dz, cr, cg, cb, ca);
            vertex(cx, cz, cr, cg, cb, ca);
            vertex(bx, bz, cr, cg, cb, ca);
            vertex(ax, az, cr, cg, cb, ca);
        }

        private void vertex(float x, float z, float cr, float cg, float cb, float ca) {
            buffer.addVertex(pose, x, 0.0F, z).setColor(cr, cg, cb, ca);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(PentagramEntity pentagram) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
