package net.h3xpy.bloodbound.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.mark.MarkManager;
import net.h3xpy.bloodbound.network.MarksPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The client half of the marks, and of the blood Bleeding leaves: a scuff on the ground for each.
 * <p>
 * Each one arrives once and runs its own clock from there, so nothing is sent per tick. Size,
 * offset and rotation come out of the block's own coordinates, so a trail looks scuffed and uneven
 * while staying exactly the same from one frame to the next.
 */
public final class ClientMarks {

    private static final ResourceLocation TEXTURE_PLAYER =
            ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "textures/marks/marks.png");
    private static final ResourceLocation TEXTURE_MOB =
            ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "textures/marks/marks_red.png");

    /** How strong a mark gets at its fullest. */
    private static final float MAX_ALPHA = 0.85F;
    /** How far above the block's face the quad sits, to stay clear of the ground's own pixels. */
    private static final double LIFT = 0.02D;
    /** Smallest and largest share of the block one scuff covers. */
    private static final double MIN_SIZE = 0.45D;
    private static final double MAX_SIZE = 0.95D;
    /** Blood is the red mark texture darkened, and drawn a little smaller. */
    private static final int BLOOD_TINT = 0x7A1010;
    private static final double BLOOD_SCALE = 0.7D;

    /** A block and a kind: blood and a trail can lie on the same block without replacing each other. */
    private record Key(BlockPos pos, byte kind) {}

    private static final Map<Key, Long> MARKS = new HashMap<>();

    private ClientMarks() {}

    public static void accept(MarksPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        long now = minecraft.level.getGameTime();
        for (MarksPayload.Mark mark : payload.marks()) {
            MARKS.put(new Key(mark.pos(), mark.kind()), now);
        }
    }

    /** Drops the marks that have run their course. */
    public static void tick() {
        if (MARKS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            MARKS.clear();
            return;
        }
        long now = minecraft.level.getGameTime();
        Iterator<Map.Entry<Key, Long>> iterator = MARKS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Key, Long> entry = iterator.next();
            if (now - entry.getValue() >= lifetime(entry.getKey().kind())) {
                iterator.remove();
            }
        }
    }

    public static void reset() {
        MARKS.clear();
    }

    /** Takes trail marks off these blocks at once. Blood is left alone: it is nobody's to take back. */
    public static void remove(List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            MARKS.remove(new Key(pos, MarksPayload.KIND_PLAYER));
            MARKS.remove(new Key(pos, MarksPayload.KIND_MOB));
        }
    }

    // --- rendering ---

    public static void render(PoseStack poseStack, Camera camera) {
        if (MARKS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        long now = minecraft.level.getGameTime();

        Vec3 view = camera.getPosition();
        poseStack.pushPose();
        poseStack.translate(-view.x, -view.y, -view.z);
        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // Set outright rather than assumed: whatever drew before this stage may have left the depth
        // test off, which is what let marks show through walls now and then.
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        // One pass per texture: the sampler is bound globally, so they cannot share a draw call.
        pass(matrix, now, MarksPayload.KIND_PLAYER, TEXTURE_PLAYER);
        pass(matrix, now, MarksPayload.KIND_MOB, TEXTURE_MOB);
        pass(matrix, now, MarksPayload.KIND_BLOOD, TEXTURE_MOB);

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    /** Draws every mark of one kind, or nothing at all if there are none worth drawing. */
    private static void pass(Matrix4f matrix, long now, byte kind, ResourceLocation texture) {
        BufferBuilder builder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        boolean any = false;
        for (Map.Entry<Key, Long> entry : MARKS.entrySet()) {
            if (entry.getKey().kind() == kind) {
                any |= quad(builder, matrix, entry.getKey(), now - entry.getValue());
            }
        }

        // An empty buffer is not an empty draw, it is a crash: buildOrThrow throws on one, and the
        // newest marks are still at zero opacity. build() hands back null instead and closes it.
        MeshData mesh = builder.build();
        if (!any || mesh == null) {
            if (mesh != null) {
                mesh.close();
            }
            return;
        }
        RenderSystem.setShaderTexture(0, texture);
        BufferUploader.drawWithShader(mesh);
    }

    /** @return true when this mark actually put vertices in the buffer */
    private static boolean quad(BufferBuilder builder, Matrix4f matrix, Key key, long age) {
        float alpha = alpha(key.kind(), age);
        if (alpha <= 0.0F) {
            return false;
        }
        int rgb = key.kind() == MarksPayload.KIND_BLOOD ? BLOOD_TINT : 0xFFFFFF;
        int color = (Math.round(alpha * 255.0F) << 24) | rgb;

        BlockPos pos = key.pos();
        int noise = scramble(pos, key.kind());
        double size = MIN_SIZE + (MAX_SIZE - MIN_SIZE) * ((noise & 0xFF) / 255.0D);
        if (key.kind() == MarksPayload.KIND_BLOOD) {
            size *= BLOOD_SCALE;
        }
        double centreX = pos.getX() + 0.5D + (1.0D - size) * (((noise >> 8) & 0xFF) / 255.0D - 0.5D);
        double centreZ = pos.getZ() + 0.5D + (1.0D - size) * (((noise >> 16) & 0xFF) / 255.0D - 0.5D);
        double angle = ((noise >>> 24) / 255.0D) * Math.PI * 2.0D;

        float y = (float) (pos.getY() + 1 + LIFT);
        double half = size / 2.0D;
        double cos = Math.cos(angle) * half;
        double sin = Math.sin(angle) * half;

        corner(builder, matrix, centreX - cos + sin, y, centreZ - sin - cos, 0.0F, 0.0F, color);
        corner(builder, matrix, centreX - cos - sin, y, centreZ - sin + cos, 0.0F, 1.0F, color);
        corner(builder, matrix, centreX + cos - sin, y, centreZ + sin + cos, 1.0F, 1.0F, color);
        corner(builder, matrix, centreX + cos + sin, y, centreZ + sin - cos, 1.0F, 0.0F, color);
        return true;
    }

    private static void corner(BufferBuilder builder, Matrix4f matrix, double x, float y, double z,
            float u, float v, int color) {
        builder.addVertex(matrix, (float) x, y, (float) z).setUv(u, v).setColor(color);
    }

    private static long lifetime(byte kind) {
        return kind == MarksPayload.KIND_BLOOD ? MarkManager.BLOOD_LIFETIME_TICKS : MarkManager.LIFETIME_TICKS;
    }

    private static float alpha(byte kind, long age) {
        boolean blood = kind == MarksPayload.KIND_BLOOD;
        int in = blood ? MarkManager.BLOOD_FADE_IN_TICKS : MarkManager.FADE_IN_TICKS;
        int hold = blood ? MarkManager.BLOOD_HOLD_TICKS : MarkManager.HOLD_TICKS;
        int out = blood ? MarkManager.BLOOD_FADE_OUT_TICKS : MarkManager.FADE_OUT_TICKS;

        if (age < 0L) {
            return 0.0F;
        }
        if (age < in) {
            return MAX_ALPHA * age / in;
        }
        long held = age - in;
        if (held < hold) {
            return MAX_ALPHA;
        }
        long fading = held - hold;
        if (fading >= out) {
            return 0.0F;
        }
        return MAX_ALPHA * (1.0F - fading / (float) out);
    }

    /** A stable spread of bits from a block position, for the per-mark jitter. */
    private static int scramble(BlockPos pos, byte kind) {
        int hash = pos.getX() * 73856093 ^ pos.getY() * 19349663 ^ pos.getZ() * 83492791 ^ kind * 2654435;
        hash ^= hash >>> 13;
        hash *= 0x5BD1E995;
        return hash ^ (hash >>> 15);
    }
}
