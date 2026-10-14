package net.h3xpy.bloodbound.client.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.entity.TripwireEntity;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a Tripwires wire: two thin wooden posts and a cord between their tops, all faded to the
 * tier's opacity. No model: the cord's length and slope change with every wire, so it is built as a
 * long thin box along the line between the posts.
 * <p>
 * The texture is split in two: the left half is the post's wood, the right half the cord.
 */
public class TripwireRenderer extends EntityRenderer<TripwireEntity> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "textures/entity/tripwire.png");

    /** Half the post's width, and half the cord's thickness, in blocks. */
    private static final float POST_HALF = 1.0F / 16.0F;
    private static final float CORD_HALF = 0.5F / 16.0F;

    public TripwireRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(TripwireEntity wire, float yaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight) {
        int alpha = Math.round(wire.opacity() * 255.0F);
        int color = (alpha << 24) | 0xFFFFFF;
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        Matrix4f pose = poseStack.last().pose();
        float height = (float) ModPerks.TRIPWIRE_POST_HEIGHT;

        post(buffer, pose, Vec3.ZERO, height, color, packedLight);
        if (wire.hasEnd()) {
            Vec3 end = wire.endOffset();
            // The far post is lit by its own spot, not the first one's.
            BlockPos farPos = BlockPos.containing(wire.position().add(end).add(0.0D, 0.5D, 0.0D));
            int farLight = LightTexture.pack(wire.level().getBrightness(LightLayer.BLOCK, farPos),
                    wire.level().getBrightness(LightLayer.SKY, farPos));
            post(buffer, pose, end, height, color, farLight);
            cord(buffer, pose, new Vec3(0, height, 0), end.add(0, height, 0), color, packedLight);
        }
        super.render(wire, yaw, partialTick, poseStack, buffers, packedLight);
    }

    private static void post(VertexConsumer buffer, Matrix4f pose, Vec3 foot, float height, int color, int light) {
        float x = (float) foot.x;
        float y = (float) foot.y;
        float z = (float) foot.z;
        Vector3f[] corners = {
            new Vector3f(x - POST_HALF, y, z - POST_HALF), new Vector3f(x + POST_HALF, y, z - POST_HALF),
            new Vector3f(x + POST_HALF, y, z + POST_HALF), new Vector3f(x - POST_HALF, y, z + POST_HALF),
            new Vector3f(x - POST_HALF, y + height, z - POST_HALF), new Vector3f(x + POST_HALF, y + height, z - POST_HALF),
            new Vector3f(x + POST_HALF, y + height, z + POST_HALF), new Vector3f(x - POST_HALF, y + height, z + POST_HALF),
        };
        box(buffer, pose, corners, 0.0F, 0.5F, color, light);
    }

    /** A thin box along the line between the two post tops. */
    private static void cord(VertexConsumer buffer, Matrix4f pose, Vec3 from, Vec3 to, int color, int light) {
        Vec3 along = to.subtract(from);
        if (along.lengthSqr() < 1.0E-6D) {
            return;
        }
        Vec3 dir = along.normalize();
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-6D) {
            side = new Vec3(1, 0, 0);
        }
        side = side.normalize().scale(CORD_HALF);
        Vec3 up = side.cross(dir).normalize().scale(CORD_HALF);
        Vec3[] ends = { from, to };
        Vector3f[] corners = new Vector3f[8];
        for (int e = 0; e < 2; e++) {
            Vec3 c = ends[e];
            corners[e * 4] = toVec(c.subtract(side).subtract(up));
            corners[e * 4 + 1] = toVec(c.add(side).subtract(up));
            corners[e * 4 + 2] = toVec(c.add(side).add(up));
            corners[e * 4 + 3] = toVec(c.subtract(side).add(up));
        }
        box(buffer, pose, corners, 0.5F, 1.0F, color, light);
    }

    private static Vector3f toVec(Vec3 v) {
        return new Vector3f((float) v.x, (float) v.y, (float) v.z);
    }

    /**
     * Six faces from eight corners: 0-3 one ring, 4-7 the other, in the same winding. The faces are
     * drawn from both sides, so the winding never hides one.
     */
    private static void box(VertexConsumer buffer, Matrix4f pose, Vector3f[] c, float u0, float u1, int color, int light) {
        int[][] faces = {
            {0, 1, 2, 3}, {4, 5, 6, 7}, {0, 1, 5, 4}, {1, 2, 6, 5}, {2, 3, 7, 6}, {3, 0, 4, 7},
        };
        for (int[] face : faces) {
            quad(buffer, pose, c[face[0]], c[face[1]], c[face[2]], c[face[3]], u0, u1, color, light);
            quad(buffer, pose, c[face[3]], c[face[2]], c[face[1]], c[face[0]], u0, u1, color, light);
        }
    }

    private static void quad(VertexConsumer buffer, Matrix4f pose, Vector3f a, Vector3f b, Vector3f c, Vector3f d,
            float u0, float u1, int color, int light) {
        vertex(buffer, pose, a, u0, 0.0F, color, light);
        vertex(buffer, pose, b, u1, 0.0F, color, light);
        vertex(buffer, pose, c, u1, 1.0F, color, light);
        vertex(buffer, pose, d, u0, 1.0F, color, light);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f pose, Vector3f p, float u, float v, int color, int light) {
        buffer.addVertex(pose, p.x(), p.y(), p.z()).setColor(color).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(TripwireEntity wire) {
        return TEXTURE;
    }
}
