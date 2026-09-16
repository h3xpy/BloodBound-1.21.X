package net.h3xpy.bloodbound.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.h3xpy.bloodbound.network.OmnisciencePayload;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The client half of Omniscience: outlines what the server found, through the walls, on this screen
 * alone.
 * <p>
 * Entities borrow the same private glow Aura Revealed uses. Blocks cannot glow, so they are drawn
 * by hand — the depth test is switched off around the draw, which is what puts the outlines in
 * front of the stone rather than behind it. No vanilla render type does that on its own.
 */
public final class ClientOmniscience {

    /** Ore outlines, and container outlines. */
    private static final int COLOR_ORE = 0xFFE0C44C;
    private static final int COLOR_CONTAINER = 0xFF4CD6E0;

    private static final float LINE_WIDTH = 2.0F;
    /** Boxes are grown a hair so they do not fight the block's own faces for pixels. */
    private static final double INFLATE = 0.002D;

    private static List<BlockPos> ores = List.of();
    private static List<BlockPos> containers = List.of();
    private static List<Integer> entities = List.of();

    private ClientOmniscience() {}

    public static void accept(OmnisciencePayload payload) {
        // Whatever was glowing before is not necessarily in the new sweep.
        clearGlow();
        ores = payload.ores();
        containers = payload.containers();
        entities = payload.entities();
        applyGlow();
    }

    public static boolean isRevealed(int entityId) {
        return entities.contains(entityId);
    }

    /**
     * Keeps the entity glow on. An entity that leaves view and comes back is a fresh copy with the
     * flag clear, so it has to be set again rather than only once when the reveal lands.
     */
    public static void tick() {
        if (!entities.isEmpty()) {
            applyGlow();
        }
    }

    public static void reset() {
        clearGlow();
        ores = List.of();
        containers = List.of();
        entities = List.of();
    }

    private static void applyGlow() {
        setGlow(true);
    }

    private static void clearGlow() {
        setGlow(false);
    }

    private static void setGlow(boolean glowing) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        for (int id : entities) {
            Entity entity = minecraft.level.getEntity(id);
            // Aura Revealed may be holding the same entity for its own reasons; leave it alone.
            if (entity != null && (glowing || !ClientAuraReveal.isRevealed(id))) {
                entity.setSharedFlag(6, glowing);
            }
        }
    }

    // --- block outlines ---

    public static void render(PoseStack poseStack, Camera camera) {
        if (ores.isEmpty() && containers.isEmpty()) {
            return;
        }

        Vec3 view = camera.getPosition();
        poseStack.pushPose();
        poseStack.translate(-view.x, -view.y, -view.z);
        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.lineWidth(LINE_WIDTH);

        BufferBuilder builder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        List<BlockPos> all = new ArrayList<>(ores.size() + containers.size());
        all.addAll(ores);
        all.addAll(containers);
        for (int i = 0; i < all.size(); i++) {
            box(builder, matrix, all.get(i), i < ores.size() ? COLOR_ORE : COLOR_CONTAINER);
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());

        RenderSystem.lineWidth(1.0F);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        poseStack.popPose();
    }

    /** The twelve edges of one block, as separate line segments. */
    private static void box(BufferBuilder builder, Matrix4f matrix, BlockPos pos, int color) {
        float x0 = (float) (pos.getX() - INFLATE);
        float y0 = (float) (pos.getY() - INFLATE);
        float z0 = (float) (pos.getZ() - INFLATE);
        float x1 = (float) (pos.getX() + 1 + INFLATE);
        float y1 = (float) (pos.getY() + 1 + INFLATE);
        float z1 = (float) (pos.getZ() + 1 + INFLATE);

        edge(builder, matrix, x0, y0, z0, x1, y0, z0, color);
        edge(builder, matrix, x1, y0, z0, x1, y0, z1, color);
        edge(builder, matrix, x1, y0, z1, x0, y0, z1, color);
        edge(builder, matrix, x0, y0, z1, x0, y0, z0, color);

        edge(builder, matrix, x0, y1, z0, x1, y1, z0, color);
        edge(builder, matrix, x1, y1, z0, x1, y1, z1, color);
        edge(builder, matrix, x1, y1, z1, x0, y1, z1, color);
        edge(builder, matrix, x0, y1, z1, x0, y1, z0, color);

        edge(builder, matrix, x0, y0, z0, x0, y1, z0, color);
        edge(builder, matrix, x1, y0, z0, x1, y1, z0, color);
        edge(builder, matrix, x1, y0, z1, x1, y1, z1, color);
        edge(builder, matrix, x0, y0, z1, x0, y1, z1, color);
    }

    private static void edge(BufferBuilder builder, Matrix4f matrix, float x0, float y0, float z0,
            float x1, float y1, float z1, int color) {
        builder.addVertex(matrix, x0, y0, z0).setColor(color);
        builder.addVertex(matrix, x1, y1, z1).setColor(color);
    }
}
