package net.h3xpy.bloodbound.client.screen;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Small drawing helpers. The perk table draws its own shapes rather than shipping GUI textures, so
 * the layout can change without anyone having to redraw a sprite sheet.
 */
public final class GuiUtil {

    private GuiUtil() {}

    /** Filled disc, drawn scanline by scanline. */
    public static void fillCircle(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int dx = (int) Math.sqrt((double) radius * radius - (double) dy * dy);
            graphics.fill(centerX - dx, centerY + dy, centerX + dx + 1, centerY + dy + 1, color);
        }
    }

    /** Ring between {@code innerRadius} and {@code outerRadius}. */
    public static void drawRing(GuiGraphics graphics, int centerX, int centerY, int outerRadius, int innerRadius,
            int color) {
        for (int dy = -outerRadius; dy <= outerRadius; dy++) {
            int outerDx = (int) Math.sqrt((double) outerRadius * outerRadius - (double) dy * dy);
            if (Math.abs(dy) <= innerRadius) {
                int innerDx = (int) Math.sqrt((double) innerRadius * innerRadius - (double) dy * dy);
                graphics.fill(centerX - outerDx, centerY + dy, centerX - innerDx, centerY + dy + 1, color);
                graphics.fill(centerX + innerDx + 1, centerY + dy, centerX + outerDx + 1, centerY + dy + 1, color);
            } else {
                graphics.fill(centerX - outerDx, centerY + dy, centerX + outerDx + 1, centerY + dy + 1, color);
            }
        }
    }

    /**
     * Arc of a circle, drawn as a run of small squares. Positions are given as fractions of a full
     * turn, with 0 at the top and increasing clockwise.
     */
    public static void drawArc(GuiGraphics graphics, int centerX, int centerY, int radius, int thickness,
            float startFraction, float endFraction, int color) {
        int segments = Math.max(2, (int) Math.ceil((endFraction - startFraction) * 360.0F));
        int half = Math.max(1, thickness) / 2;
        for (int i = 0; i <= segments; i++) {
            float fraction = startFraction + (endFraction - startFraction) * i / segments;
            double angle = -Math.PI / 2.0D + fraction * Math.PI * 2.0D;
            int x = centerX + (int) Math.round(Math.cos(angle) * radius);
            int y = centerY + (int) Math.round(Math.sin(angle) * radius);
            graphics.fill(x - half, y - half, x - half + Math.max(1, thickness),
                    y - half + Math.max(1, thickness), color);
        }
    }

    /** Straight line of the given thickness between two arbitrary points. */
    public static void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int thickness, int color) {
        int steps = (int) Math.ceil(Math.hypot(x2 - x1, y2 - y1));
        int half = Math.max(1, thickness) / 2;
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0.0D : (double) i / steps;
            int px = (int) Math.round(x1 + (x2 - x1) * t);
            int py = (int) Math.round(y1 + (y2 - y1) * t);
            graphics.fill(px - half, py - half, px - half + Math.max(1, thickness),
                    py - half + Math.max(1, thickness), color);
        }
    }

    /** One-pixel outline just outside the given rectangle. */
    public static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    /** Blends {@code color} towards black by {@code factor} (0 keeps it, 1 turns it black). */
    public static int darken(int color, float factor) {
        int alpha = (color >>> 24) & 0xFF;
        int red = (int) (((color >> 16) & 0xFF) * (1.0F - factor));
        int green = (int) (((color >> 8) & 0xFF) * (1.0F - factor));
        int blue = (int) ((color & 0xFF) * (1.0F - factor));
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    /** Replaces the alpha channel of a colour. */
    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    /**
     * Shortens a label with an ellipsis so it stays inside {@code maxWidth}. Perk and addon names
     * are translated, so their length is not something the layout can assume.
     */
    /** How wide a tooltip is allowed to grow before its lines wrap. */
    public static final int TOOLTIP_WIDTH = 220;

    /** Adds one line to a tooltip as-is. */
    public static void addLine(List<FormattedCharSequence> lines, Component text) {
        lines.add(text.getVisualOrderText());
    }

    /**
     * Adds a paragraph, wrapped. Tooltips never wrap on their own, so a perk description would
     * otherwise run clean off the side of the screen.
     */
    public static void addWrapped(List<FormattedCharSequence> lines, Component text) {
        lines.addAll(Minecraft.getInstance().font.split(text, TOOLTIP_WIDTH));
    }

    public static Component fitToWidth(Font font, Component text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int room = Math.max(0, maxWidth - font.width(ellipsis));
        return Component.literal(font.plainSubstrByWidth(text.getString(), room) + ellipsis)
                .setStyle(text.getStyle());
    }

    /** Perk tiers read as I, II and III everywhere in the UI. */
    public static String romanTier(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> Integer.toString(tier);
        };
    }

    /** Formats a tick count as {@code 12s}, or {@code 1m 05s} past a minute. */
    public static String formatTicks(int ticks) {
        int seconds = Math.max(0, ticks) / 20;
        if (seconds < 60) {
            return seconds + "s";
        }
        return (seconds / 60) + "m " + String.format("%02ds", seconds % 60);
    }
}
