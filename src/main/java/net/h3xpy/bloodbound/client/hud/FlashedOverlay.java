package net.h3xpy.bloodbound.client.hud;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.effect.FlashedEffect;
import net.h3xpy.bloodbound.registry.ModEffects;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * The white a Flashed player is left staring at. Solid for as long as the effect has more than its
 * fade left, then thinning out over the last second so sight comes back rather than snapping on.
 */
public class FlashedOverlay implements LayeredDraw.Layer {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "flashed");

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        MobEffectInstance flashed = minecraft.player.getEffect(ModEffects.FLASHED);
        if (flashed == null) {
            return;
        }

        int remaining = flashed.getDuration();
        float alpha = remaining >= FlashedEffect.FADE_TICKS
                ? 1.0F
                : remaining / (float) FlashedEffect.FADE_TICKS;
        if (alpha <= 0.0F) {
            return;
        }

        int white = ((int) (alpha * 255.0F) << 24) | 0x00FFFFFF;
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), white);
    }
}
