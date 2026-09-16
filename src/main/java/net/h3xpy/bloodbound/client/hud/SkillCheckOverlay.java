package net.h3xpy.bloodbound.client.hud;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.client.ClientSkillCheck;
import net.h3xpy.bloodbound.client.ModKeyMappings;
import net.h3xpy.bloodbound.client.screen.GuiUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The skill check dial: a needle sweeping a ring once, with a success zone to hit.
 */
public class SkillCheckOverlay implements LayeredDraw.Layer {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "skill_check");

    private static final int RADIUS = 34;
    /** Pushed below the crosshair so it never sits under it. */
    private static final int VERTICAL_OFFSET = 46;

    private static final int COLOR_BACKING = 0x70000000;
    private static final int COLOR_TRACK = 0x66FFFFFF;
    private static final int COLOR_ZONE = 0xFFE8DCDC;
    private static final int COLOR_NEEDLE = 0xFFB4232F;
    private static final int COLOR_HIT = 0xFF6BD46B;
    private static final int COLOR_MISS = 0xFFCC4444;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        boolean active = ClientSkillCheck.isActive();
        if (!active && !ClientSkillCheck.isFlashing()) {
            return;
        }

        int centerX = graphics.guiWidth() / 2;
        int centerY = graphics.guiHeight() / 2 + VERTICAL_OFFSET;

        GuiUtil.fillCircle(graphics, centerX, centerY, RADIUS + 6, COLOR_BACKING);
        GuiUtil.drawRing(graphics, centerX, centerY, RADIUS + 2, RADIUS - 2, COLOR_TRACK);

        // Once resolved, the whole ring flashes the verdict for a few ticks.
        if (!active) {
            GuiUtil.drawRing(graphics, centerX, centerY, RADIUS + 3, RADIUS - 3,
                    ClientSkillCheck.flashSuccess() ? COLOR_HIT : COLOR_MISS);
            return;
        }

        float zoneStart = ClientSkillCheck.zoneStart();
        GuiUtil.drawArc(graphics, centerX, centerY, RADIUS, 6,
                zoneStart, zoneStart + ClientSkillCheck.zoneWidth(), COLOR_ZONE);

        float progress = ClientSkillCheck.progress(deltaTracker.getGameTimeDeltaPartialTick(false));
        double angle = -Math.PI / 2.0D + progress * Math.PI * 2.0D;
        int needleX = centerX + (int) Math.round(Math.cos(angle) * (RADIUS + 3));
        int needleY = centerY + (int) Math.round(Math.sin(angle) * (RADIUS + 3));
        GuiUtil.drawLine(graphics, centerX, centerY, needleX, needleY, 3, COLOR_NEEDLE);

        String key = ModKeyMappings.SKILL_CHECK.getTranslatedKeyMessage().getString();
        graphics.drawCenteredString(minecraft.font, Component.literal(key),
                centerX, centerY + RADIUS + 12, 0xFFE8DCDC);
    }
}
