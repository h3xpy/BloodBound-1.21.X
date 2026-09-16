package net.h3xpy.bloodbound.client.hud;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.client.ClientEffectCharges;
import net.h3xpy.bloodbound.client.ModKeyMappings;
import net.h3xpy.bloodbound.effect.BleedingHandler;
import net.h3xpy.bloodbound.effect.ExhaustedHandler;
import net.h3xpy.bloodbound.registry.ModEffects;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;

/**
 * The bars for Bleeding and Exhausted, and the red creeping in at the edges of a bleeding player's
 * screen.
 * <p>
 * Each bar is a framed strip of segments with the effect's own icon at its head, sitting above the
 * hotbar. Bleeding also says how to get rid of it, with the key the player actually has bound, and
 * shows how far along the dressing is — including a paused one, since that progress is kept. A
 * winded Exhausted bar is dimmed, with a notch where the sprint comes back, and stays on screen
 * when empty: an empty bar is exactly the one worth seeing.
 */
public class EffectBarsOverlay implements LayeredDraw.Layer {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "effect_bars");

    private static final int ICON_SIZE = 9;
    private static final int SEGMENT_WIDTH = 9;
    private static final int SEGMENT_HEIGHT = 5;
    private static final int SEGMENT_GAP = 1;
    private static final int PADDING = 1;
    private static final int ICON_GAP = 3;
    /** How far above the bottom of the screen the lowest bar sits, and the space between bars. */
    private static final int BOTTOM_MARGIN = 58;
    private static final int ROW_HEIGHT = 12;

    private static final int COLOR_FRAME = 0xD0100A0C;
    private static final int COLOR_EMPTY = 0xFF2A1E22;

    private static final int BLEEDING_FILL = 0xFFB4232F;
    private static final int BLEEDING_SHINE = 0xFFE0535D;
    private static final int CURE_FILL = 0xFF6FD06F;
    private static final int HINT_COLOR = 0xFFF0B0B0;
    private static final int EXHAUSTED_FILL = 0xFFC23BB5;
    private static final int EXHAUSTED_SHINE = 0xFFE77BDD;
    private static final int WINDED_FILL = 0xFF6A3565;
    private static final int COLOR_NOTCH = 0xFFF0E0A0;

    /** How red the edges get at their worst, and how far in the vignette reaches. */
    private static final float MAX_TINT = 0.55F;
    private static final int VIGNETTE_DEPTH = 70;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        ClientEffectCharges.Bar bleeding = bar(ModEffects.BLEEDING.getKey().location());
        ClientEffectCharges.Bar exhausted = bar(ModEffects.EXHAUSTED.getKey().location());

        if (bleeding != null) {
            vignette(graphics, bleeding.charges(), Math.abs(bleeding.maxCharges()));
        }

        int row = 0;
        if (bleeding != null) {
            int[] frame = renderBar(graphics, minecraft, ModEffects.BLEEDING, bleeding.charges(),
                    bleeding.maxCharges(), false, BLEEDING_FILL, BLEEDING_SHINE, row++);
            renderCure(graphics, minecraft, frame);
        }
        if (exhausted != null) {
            int max = Math.abs(exhausted.maxCharges());
            boolean winded = exhausted.maxCharges() < 0;
            renderBar(graphics, minecraft, ModEffects.EXHAUSTED, exhausted.charges(), max, winded,
                    winded ? WINDED_FILL : EXHAUSTED_FILL, winded ? WINDED_FILL : EXHAUSTED_SHINE, row);
        }
    }

    @Nullable
    private static ClientEffectCharges.Bar bar(ResourceLocation id) {
        ClientEffectCharges.Bar bar = ClientEffectCharges.bar(id);
        return bar != null && bar.maxCharges() != 0 ? bar : null;
    }

    /** @return the frame's left, top, width and height, so what hangs off the bar can find it */
    private int[] renderBar(GuiGraphics graphics, Minecraft minecraft, Holder<MobEffect> effect, int charges,
            int max, boolean winded, int fill, int shine, int row) {
        int segments = SEGMENT_WIDTH * max + SEGMENT_GAP * (max - 1);
        int frameWidth = segments + PADDING * 2;
        int frameHeight = SEGMENT_HEIGHT + PADDING * 2;
        int total = ICON_SIZE + ICON_GAP + frameWidth;

        int left = (graphics.guiWidth() - total) / 2;
        int top = graphics.guiHeight() - BOTTOM_MARGIN - row * ROW_HEIGHT;

        TextureAtlasSprite icon = minecraft.getMobEffectTextures().get(effect);
        graphics.blit(left, top + (frameHeight - ICON_SIZE) / 2, 0, ICON_SIZE, ICON_SIZE, icon);

        int frameX = left + ICON_SIZE + ICON_GAP;
        graphics.fill(frameX, top, frameX + frameWidth, top + frameHeight, COLOR_FRAME);

        for (int i = 0; i < max; i++) {
            int x = frameX + PADDING + i * (SEGMENT_WIDTH + SEGMENT_GAP);
            int y = top + PADDING;
            if (i < charges) {
                graphics.fill(x, y, x + SEGMENT_WIDTH, y + SEGMENT_HEIGHT, fill);
                graphics.fill(x, y, x + SEGMENT_WIDTH, y + 1, shine);
            } else {
                graphics.fill(x, y, x + SEGMENT_WIDTH, y + SEGMENT_HEIGHT, COLOR_EMPTY);
            }
        }

        if (winded) {
            int recover = ExhaustedHandler.recoverCharges(max);
            int notchX = frameX + PADDING + recover * (SEGMENT_WIDTH + SEGMENT_GAP) - SEGMENT_GAP;
            graphics.fill(notchX, top - 1, notchX + 1, top + frameHeight + 1, COLOR_NOTCH);
        }
        return new int[] {frameX, top, frameWidth, frameHeight};
    }

    /**
     * Under the Bleeding bar: how to be rid of it, and how far along that is. The hint names the
     * key as bound, so a player who moved it off G is not sent looking for the wrong one.
     */
    private void renderCure(GuiGraphics graphics, Minecraft minecraft, int[] frame) {
        int frameX = frame[0];
        int top = frame[1];
        int width = frame[2];
        int height = frame[3];

        Component hint = Component.translatable("bloodbound.hud.bleeding_hint",
                ModKeyMappings.HEAL.getTranslatedKeyMessage());
        graphics.drawString(minecraft.font, hint, frameX + width + 5, top - 1, HINT_COLOR, true);

        ClientEffectCharges.Bar cure = ClientEffectCharges.bar(BleedingHandler.CURE_BAR_ID);
        if (cure == null || cure.maxCharges() <= 0 || cure.charges() <= 0) {
            return;
        }
        int filled = Math.round(width * Math.min(1.0F, cure.charges() / (float) cure.maxCharges()));
        int y = top + height + 1;
        graphics.fill(frameX, y, frameX + width, y + 2, COLOR_FRAME);
        graphics.fill(frameX, y, frameX + filled, y + 2, CURE_FILL);
    }

    /**
     * Red at the edges, deepening as the bar empties. A full bar is barely tinted; an empty one is
     * the last warning before running costs blood.
     */
    private void vignette(GuiGraphics graphics, int charges, int max) {
        if (max <= 0) {
            return;
        }
        float emptiness = 1.0F - charges / (float) max;
        float strength = MAX_TINT * (0.25F + 0.75F * emptiness);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        for (int depth = 0; depth < VIGNETTE_DEPTH; depth++) {
            float share = 1.0F - depth / (float) VIGNETTE_DEPTH;
            int alpha = Math.round(strength * share * share * 255.0F / 8.0F);
            if (alpha <= 0) {
                continue;
            }
            int color = (alpha << 24) | 0x00A00C14;
            graphics.fill(depth, depth, width - depth, depth + 1, color);
            graphics.fill(depth, height - depth - 1, width - depth, height - depth, color);
            graphics.fill(depth, depth + 1, depth + 1, height - depth - 1, color);
            graphics.fill(width - depth - 1, depth + 1, width - depth, height - depth - 1, color);
        }
    }
}
