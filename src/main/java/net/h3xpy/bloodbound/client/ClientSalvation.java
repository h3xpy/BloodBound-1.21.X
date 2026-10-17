package net.h3xpy.bloodbound.client;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.h3xpy.bloodbound.network.SalvationBindPayload;
import net.h3xpy.bloodbound.perk.impl.Salvation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * Salvation, on the client: who is bound and until when, the chains drawn round them, and, for a
 * bound local player, the attack and use keys refused before anything is sent.
 * <p>
 * The chains are rings of vanilla chain links wrapped round the body at a slant, turning slowly
 * against each other. They snap tight when the bind lands and slip loose in its last second.
 */
public final class ClientSalvation {

    private static final ResourceLocation CHAIN = ResourceLocation.withDefaultNamespace("textures/block/chain.png");
    /** Width of a chain link strip, as the chain texture lays it out: three pixels of sixteen. */
    private static final float LINK_U = 3.0F / 16.0F;
    private static final float LINK_WIDTH = 0.24F;
    private static final int SEGMENTS = 28;

    /** Bound entities, by id, to the game time they are let go at. */
    private static final Map<Integer, Long> BOUND_UNTIL = new HashMap<>();
    /** When each bind landed, for the chains snapping tight. */
    private static final Map<Integer, Long> BOUND_AT = new HashMap<>();

    private ClientSalvation() {}

    public static void handle(SalvationBindPayload payload) {
        long now = gameTime();
        if (payload.ticks() <= 0) {
            BOUND_UNTIL.remove(payload.entityId());
            BOUND_AT.remove(payload.entityId());
        } else {
            BOUND_UNTIL.put(payload.entityId(), now + payload.ticks());
            BOUND_AT.putIfAbsent(payload.entityId(), now);
        }
    }

    public static boolean isBound(int entityId) {
        Long until = BOUND_UNTIL.get(entityId);
        if (until == null) {
            return false;
        }
        if (gameTime() >= until) {
            BOUND_UNTIL.remove(entityId);
            BOUND_AT.remove(entityId);
            return false;
        }
        return true;
    }

    public static void reset() {
        BOUND_UNTIL.clear();
        BOUND_AT.clear();
    }

    /** A bound player's hands are tied: no using anything, and no swinging a weapon. Fists still work. */
    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && isBound(minecraft.player.getId()) && (event.isUseItem()
                || (event.isAttack() && Salvation.isWeapon(minecraft.player.getMainHandItem())))) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (BOUND_UNTIL.isEmpty() || !isBound(entity.getId())) {
            return;
        }
        float partial = event.getPartialTick();
        long now = gameTime();
        float age = (float) (now - BOUND_AT.getOrDefault(entity.getId(), now)) + partial;
        float left = (float) (BOUND_UNTIL.get(entity.getId()) - now) - partial;
        // Snapping tight from further out as the bind lands, and slipping loose at the very end.
        float tight = Mth.clamp(age / 6.0F, 0.0F, 1.0F);
        float loose = Mth.clamp(1.0F - left / 20.0F, 0.0F, 1.0F);
        float body = Math.max(entity.getBbWidth() * 0.6F, 0.35F) + 0.06F;
        float radius = body * (1.0F + 0.8F * (1.0F - tight) + 0.25F * loose);
        float height = entity.getBbHeight();

        PoseStack pose = event.getPoseStack();
        VertexConsumer buffer = event.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(CHAIN));
        int light = event.getPackedLight();
        float spin = (entity.tickCount + partial) * 2.0F;
        ring(pose, buffer, light, radius, height * 0.62F, 18.0F, spin);
        ring(pose, buffer, light, radius, height * 0.35F, -14.0F, -spin * 0.8F + 40.0F);
        if (height > 1.4F) {
            ring(pose, buffer, light, radius * 0.92F, height * 0.85F, -8.0F, spin * 0.6F + 90.0F);
        }
    }

    /**
     * One ring of chain round the body, at this height, tilted by this much, turned this far. Drawn
     * as alternate links standing up and lying flat, as a real chain's links do.
     */
    private static void ring(PoseStack pose, VertexConsumer buffer, int light, float radius, float y, float tilt,
            float turn) {
        pose.pushPose();
        pose.translate(0.0F, y, 0.0F);
        pose.mulPose(Axis.YP.rotationDegrees(turn));
        pose.mulPose(Axis.XP.rotationDegrees(tilt));
        PoseStack.Pose last = pose.last();
        Matrix4f matrix = last.pose();
        float half = LINK_WIDTH / 2.0F;
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / SEGMENTS;
            float a1 = Mth.TWO_PI * (i + 1) / SEGMENTS;
            float x0 = Mth.cos(a0) * radius;
            float z0 = Mth.sin(a0) * radius;
            float x1 = Mth.cos(a1) * radius;
            float z1 = Mth.sin(a1) * radius;
            // The texture's links run down a strip; each segment shows one length of it.
            float v1 = Math.min(1.0F, Mth.sqrt((x1 - x0) * (x1 - x0) + (z1 - z0) * (z1 - z0)) * 2.0F);
            if (i % 2 == 0) {
                // Standing: facing outwards, from below the line to above it.
                quad(buffer, matrix, last, light, x0, -half, z0, x1, -half, z1, x1, half, z1, x0, half, z0, 0.0F, LINK_U, v1);
            } else {
                // Lying: flat, from inside the line to outside it.
                float in = (radius - half) / radius;
                float out = (radius + half) / radius;
                quad(buffer, matrix, last, light, x0 * in, 0.0F, z0 * in, x1 * in, 0.0F, z1 * in,
                        x1 * out, 0.0F, z1 * out, x0 * out, 0.0F, z0 * out, LINK_U, LINK_U * 2.0F, v1);
            }
        }
        pose.popPose();
    }

    private static void quad(VertexConsumer buffer, Matrix4f matrix, PoseStack.Pose last, int light,
            float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
            float dx, float dy, float dz, float u0, float u1, float v1) {
        vertex(buffer, matrix, last, light, ax, ay, az, u0, 0.0F);
        vertex(buffer, matrix, last, light, bx, by, bz, u0, v1);
        vertex(buffer, matrix, last, light, cx, cy, cz, u1, v1);
        vertex(buffer, matrix, last, light, dx, dy, dz, u1, 0.0F);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, PoseStack.Pose last, int light,
            float x, float y, float z, float u, float v) {
        buffer.addVertex(matrix, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(last, 0.0F, 1.0F, 0.0F);
    }

    private static long gameTime() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0L : minecraft.level.getGameTime();
    }
}
