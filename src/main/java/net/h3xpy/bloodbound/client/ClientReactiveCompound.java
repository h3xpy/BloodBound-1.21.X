package net.h3xpy.bloodbound.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.h3xpy.bloodbound.network.ReactiveCompoundPayload;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * Reactive Compound in first person, after the sketch it was asked for: both arms show, the weapon
 * in one hand and a vial in the other. The vial comes up over the blade, the blade swings flat to
 * take it, the vial tips and pours — the drops falling from its mouth onto the blade — then both go
 * back. Once coated, the blade drips now and then.
 * <p>
 * Each arm is drawn the way Minecraft draws an empty hand in first person, and its item is put in
 * that hand the way a third person view holds one, so hand and item stay together however the arm
 * moves. The drops live in the same space as the hands, so they follow the vial.
 */
public final class ClientReactiveCompound {

    private static final int PURPLE = 0xA040E0;
    /** The vial: a potion bottle in the compound's purple. */
    private static final ItemStack VIAL = makeVial();

    /** Where in the three seconds the arms come up, the vial tips, the pouring ends. */
    private static final float RAISE_END = 0.25F;
    private static final float TIP_END = 0.35F;
    private static final float POUR_END = 0.80F;
    /** How far the vial is tipped in the hand to pour, in degrees. */
    private static final float VIAL_TILT = 110.0F;
    /** How far down the arm model the palm is, and where the vial sits from it, and how big. */
    private static final float PALM_DOWN_THE_ARM = 0.6F;
    private static final float VIAL_ABOVE_PALM = 0.10F;
    private static final float VIAL_TOWARDS_EYES = 0.15F;
    private static final float VIAL_SCALE = 0.38F;

    /** A drop between the vial and the blade, in the hands' own space. */
    private static final class Drop {
        private final Vector3f position;
        private final Vector3f previous;
        private final Vector3f velocity;
        private int age;
        /** How bright its purple is, as the portal particles vary. */
        private final float shade;

        private Drop(Vector3f position, Vector3f velocity) {
            this.position = new Vector3f(position);
            this.previous = new Vector3f(position);
            this.velocity = velocity;
            this.shade = 0.6F + (float) Math.random() * 0.4F;
        }
    }

    private static final List<Drop> DROPS = new ArrayList<>();
    /** How a drop falls, per tick, and how long it lasts at most. */
    private static final float DROP_GRAVITY = 0.012F;
    private static final int DROP_LIFE = 16;
    /** Half the width of a drop, in the hands' space. */
    private static final float DROP_SIZE = 0.035F;

    private static int phase = ReactiveCompoundPayload.NONE;
    /** Client tick the pouring started on. */
    private static long startedAt;
    /** Where the vial's mouth was drawn last frame, in the hands' space; null when it was not. */
    private static Vector3f mouth;
    /** Undoes the pose the hands start from, to bring a point drawn on an arm back into hand space. */
    private static Matrix4f handSpace = new Matrix4f();

    private ClientReactiveCompound() {}

    private static ItemStack makeVial() {
        ItemStack vial = new ItemStack(Items.POTION);
        vial.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(PURPLE), List.of()));
        return vial;
    }

    static void setPhase(int next) {
        if (next == ReactiveCompoundPayload.COATING && phase != ReactiveCompoundPayload.COATING) {
            startedAt = ClientPerkData.gameTime();
            DROPS.clear();
        }
        phase = next;
    }

    public static boolean isCoating() {
        return phase == ReactiveCompoundPayload.COATING;
    }

    /** How far through the coating the animation is, 0 to 1. */
    private static float progress(float partialTick) {
        float ticks = ClientPerkData.gameTime() - startedAt + partialTick;
        return Mth.clamp(ticks / ModPerks.REACTIVE_COAT_TICKS, 0.0F, 1.0F);
    }

    // --- the timeline ---

    /** How far the arms have come up: 0 down out of sight, 1 in place over the blade. */
    private static float reach(float t) {
        if (t < RAISE_END) {
            return ease(t / RAISE_END);
        }
        if (t < POUR_END) {
            return 1.0F;
        }
        return 1.0F - ease((t - POUR_END) / (1.0F - POUR_END));
    }

    /** How far the vial is tipped: 0 upright, 1 pouring. */
    private static float tip(float t) {
        if (t < RAISE_END) {
            return 0.0F;
        }
        if (t < TIP_END) {
            return ease((t - RAISE_END) / (TIP_END - RAISE_END));
        }
        if (t < POUR_END) {
            // A small shake while it pours, so the hand does not look frozen.
            return 1.0F + 0.05F * Mth.sin((t - TIP_END) * 55.0F);
        }
        return 1.0F - ease((t - POUR_END) / (1.0F - POUR_END));
    }

    private static boolean isPouring(float t) {
        return t > TIP_END && t < POUR_END;
    }

    /** Smooth start and stop. */
    private static float ease(float x) {
        float c = Mth.clamp(x, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }

    // --- the drops ---

    /** Moves the drops on and lets new ones fall from the vial. Called once a client tick. */
    public static void tick(Minecraft minecraft) {
        // Dying puts the vial away at once: nothing of the animation survives a death.
        if (phase != ReactiveCompoundPayload.NONE && (minecraft.player == null || minecraft.player.isDeadOrDying())) {
            reset();
            return;
        }
        Iterator<Drop> iterator = DROPS.iterator();
        while (iterator.hasNext()) {
            Drop drop = iterator.next();
            drop.previous.set(drop.position);
            drop.velocity.y -= DROP_GRAVITY;
            drop.position.add(drop.velocity);
            if (++drop.age > DROP_LIFE) {
                iterator.remove();
            }
        }
        if (isCoating() && mouth != null && isPouring(progress(0.0F))) {
            DROPS.add(new Drop(mouth, new Vector3f(0.0F, -0.01F, 0.0F)));
        }
    }

    // --- drawing ---

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !isCoating()) {
            return;
        }
        event.setCanceled(true);
        float t = progress(event.getPartialTick());
        boolean main = event.getHand() == InteractionHand.MAIN_HAND;
        HumanoidArm arm = main ? player.getMainArm() : player.getMainArm().getOpposite();
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        PoseStack pose = event.getPoseStack();
        // The hands' own space, before any arm is moved: the drops are kept in it.
        handSpace = new Matrix4f(pose.last().pose()).invert();
        float reach = reach(t);
        float tip = tip(t);
        Vector3f palm = null;

        pose.pushPose();
        if (main) {
            // The weapon: lifted a little towards the middle and swung flat, under the vial.
            pose.translate(side * -0.12F * reach, 0.10F * reach, 0.0F);
            pivot(pose, side * 0.64F, -0.6F, -0.72F, Axis.ZP.rotationDegrees(side * 40.0F * reach));
            renderArmWithItem(pose, event.getMultiBufferSource(), event.getPackedLight(), player, arm,
                    player.getMainHandItem());
        } else {
            // The vial: in from above the screen to over the blade, then tipped.
            pose.translate(side * Mth.lerp(reach, -0.20F, -0.36F), Mth.lerp(reach, 1.70F, 0.63F), -0.28F);
            // The forearm reaches in from the top corner, as in the sketch, rather than up from below.
            pivot(pose, side * 0.64F, -0.6F, -0.72F, Axis.ZP.rotationDegrees(side * (90.0F - 20.0F * tip)));
            palm = renderArmWithItem(pose, event.getMultiBufferSource(), event.getPackedLight(), player, arm,
                    ItemStack.EMPTY);
        }
        pose.popPose();
        if (!main) {
            renderVial(pose, event.getMultiBufferSource(), event.getPackedLight(), player, palm, side * VIAL_TILT * tip);
        }

        // The drops, in the same space as the hands; drawn once, after the vial, so they show over it.
        if (!main) {
            renderDrops(pose, event.getMultiBufferSource(), event.getPackedLight(), player, event.getPartialTick());
        }
    }

    /** Turns everything after it about a point rather than about the camera. */
    private static void pivot(PoseStack pose, float x, float y, float z, org.joml.Quaternionf rotation) {
        pose.translate(x, y, z);
        pose.mulPose(rotation);
        pose.translate(-x, -y, -z);
    }

    /**
     * An arm as Minecraft draws an empty hand in first person, with an item in that hand as a third
     * person view holds one.
     *
     * @return where the palm ended up, in the hands' own space
     */
    private static Vector3f renderArmWithItem(PoseStack pose, MultiBufferSource buffers, int light,
            AbstractClientPlayer player, HumanoidArm arm, ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean right = arm == HumanoidArm.RIGHT;
        float f = right ? 1.0F : -1.0F;

        pose.pushPose();
        // ItemInHandRenderer.renderPlayerArm, with no swing and fully raised.
        pose.translate(f * 0.64000005F, -0.6F, -0.71999997F);
        pose.mulPose(Axis.YP.rotationDegrees(f * 45.0F));
        pose.translate(f * -1.0F, 3.6F, 3.5F);
        pose.mulPose(Axis.ZP.rotationDegrees(f * 120.0F));
        pose.mulPose(Axis.XP.rotationDegrees(200.0F));
        pose.mulPose(Axis.YP.rotationDegrees(f * -135.0F));
        pose.translate(f * 5.6F, 0.0F, 0.0F);

        PlayerRenderer renderer = (PlayerRenderer) minecraft.getEntityRenderDispatcher().getRenderer(player);
        if (right) {
            renderer.renderRightHand(pose, buffers, light, player);
        } else {
            renderer.renderLeftHand(pose, buffers, light, player);
        }

        // ItemInHandLayer: from the arm's pivot to the palm.
        (right ? renderer.getModel().rightArm : renderer.getModel().leftArm).translateAndRotate(pose);
        Vector3f palm = handSpace.transformPosition(pose.last().pose().transformPosition(
                new Vector3f(f * -1.0F / 16.0F, PALM_DOWN_THE_ARM, 0.0F)));
        if (!stack.isEmpty()) {
            pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            pose.translate(f / 16.0F, 0.125F, -0.625F);
            minecraft.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, stack,
                    right ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                    !right, pose, buffers, light);
        }
        pose.popPose();
        return palm;
    }

    /**
     * The vial, drawn facing the camera at the palm rather than as a third person hand holds it —
     * held that way it pointed away from the eyes and hid behind the fist. Tipped in the plane of the
     * screen, so the pouring reads plainly; its mouth is noted for the drops.
     */
    private static void renderVial(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
            Vector3f palm, float tilt) {
        pose.pushPose();
        // Just in front of the fist, so the arm does not swallow it.
        pose.translate(palm.x, palm.y, palm.z + VIAL_TOWARDS_EYES);
        pose.mulPose(Axis.ZP.rotationDegrees(tilt));
        pose.translate(0.0F, VIAL_ABOVE_PALM, 0.0F);
        pose.scale(VIAL_SCALE, VIAL_SCALE, VIAL_SCALE);
        mouth = handSpace.transformPosition(pose.last().pose().transformPosition(new Vector3f(0.0F, 0.42F, 0.0F)));
        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, VIAL,
                ItemDisplayContext.FIXED, false, pose, buffers, light);
        pose.popPose();
    }

    private static void renderDrops(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
            float partialTick) {
        if (DROPS.isEmpty()
                || !(Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES) instanceof TextureAtlas atlas)) {
            return;
        }
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_PARTICLES));
        for (Drop drop : DROPS) {
            float life = (drop.age + partialTick) / DROP_LIFE;
            // The portal particle's own look: its sprite shrinks frame by frame, in purple.
            TextureAtlasSprite sprite = atlas.getSprite(ResourceLocation.withDefaultNamespace(
                    "generic_" + Mth.clamp(7 - (int) (life * 8.0F), 0, 7)));
            float size = DROP_SIZE * (1.0F - 0.5F * life);
            float x = Mth.lerp(partialTick, drop.previous.x, drop.position.x);
            float y = Mth.lerp(partialTick, drop.previous.y, drop.position.y);
            float z = Mth.lerp(partialTick, drop.previous.z, drop.position.z);
            PoseStack.Pose last = pose.last();
            int r = (int) (drop.shade * 0.9F * 255.0F);
            int g = (int) (drop.shade * 0.3F * 255.0F);
            int b = (int) (drop.shade * 255.0F);
            // Glowing, as the portal particles do: full brightness whatever the light around.
            vertex(buffer, last, x - size, y - size, z, sprite.getU0(), sprite.getV1(), r, g, b);
            vertex(buffer, last, x + size, y - size, z, sprite.getU1(), sprite.getV1(), r, g, b);
            vertex(buffer, last, x + size, y + size, z, sprite.getU1(), sprite.getV0(), r, g, b);
            vertex(buffer, last, x - size, y + size, z, sprite.getU0(), sprite.getV0(), r, g, b);
        }
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v,
            int r, int g, int b) {
        buffer.addVertex(pose, x, y, z).setColor(r, g, b, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.0F, 0.0F, 1.0F);
    }

    /** No swinging while the vial is out: the server would refuse the blow anyway. */
    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack() && isCoating()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    public static void reset() {
        phase = ReactiveCompoundPayload.NONE;
        DROPS.clear();
        mouth = null;
    }
}
