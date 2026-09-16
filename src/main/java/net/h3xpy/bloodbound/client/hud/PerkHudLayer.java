package net.h3xpy.bloodbound.client.hud;

import java.util.Locale;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.ClientConfig;
import net.h3xpy.bloodbound.client.ClientEventHandler;
import net.h3xpy.bloodbound.client.ClientPerkData;
import net.h3xpy.bloodbound.client.ModKeyMappings;
import net.h3xpy.bloodbound.client.screen.GuiUtil;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.perk.Addon;
import net.h3xpy.bloodbound.perk.AddonRegistry;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.perk.Perk;
import net.h3xpy.bloodbound.perk.PerkRegistry;
import net.h3xpy.bloodbound.perk.PerkType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;

/**
 * Shows the equipped perks stacked in the bottom-left corner, dimmed and counting down while on
 * cooldown, so the player can see at a glance what is ready.
 */
public class PerkHudLayer implements LayeredDraw.Layer {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "perk_hud");

    private static final int SLOT_SIZE = 20;
    private static final int SLOT_SPACING = 2;
    private static final int MARGIN = 4;
    /**
     * Extra room under the stack. Vanilla draws the bottom line of chat 40 px off the floor, so
     * this sits the lowest slot right on that line rather than under it.
     */
    private static final int BOTTOM_MARGIN = 36;

    /** Perk icons are 16 px; addons ride at 70% of that. */
    private static final int ADDON_ICON_SIZE = Math.round(16 * 0.7F);

    /** Used for a live window such as a Broken Movement Device return point, never a cooldown. */
    private static final int COLOR_WINDOW = 0xFF4CD6E0;

    /** Used for a streak count, such as Nasty Blade's tokens. */
    private static final int COLOR_TOKENS = 0xFFE7C74A;

    /** Size of one charge pip on a slot that tracks charges. */
    private static final int PIP_WIDTH = 3;
    private static final int PIP_HEIGHT = 2;
    /**
     * Above this many charges the row of pips stops being readable, so the count is written out
     * instead. A reserve of six hundred is a number, not a row of lights.
     */
    private static final int MAX_PIPS = 6;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        PlayerPerkData data = ClientPerkData.get();
        long gameTime = ClientPerkData.gameTime();
        int bottom = graphics.guiHeight() - MARGIN - BOTTOM_MARGIN;

        // Scaled about the bottom-left corner, so the stack keeps hugging it at any size.
        float scale = ClientConfig.hudScale();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, graphics.guiHeight() * (1.0F - scale), 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);

        for (int slot = 0; slot < PlayerPerkData.LOADOUT_SIZE; slot++) {
            ResourceLocation perkId = data.getLoadoutSlot(slot);
            Perk perk = PerkRegistry.get(perkId);
            if (perk == null) {
                continue;
            }

            int x = MARGIN;
            int y = bottom - (PlayerPerkData.LOADOUT_SIZE - slot) * (SLOT_SIZE + SLOT_SPACING);
            int tier = data.getUnlockedTier(perkId);
            int remaining = data.getCooldownRemaining(perkId, gameTime);
            int total = perk.cooldownTicks(tier);

            // Only an addon that is actually in effect earns a place on screen.
            Addon addon = AddonRegistry.get(data.equippedAddon(perkId));
            if (addon != null && !data.isAddonActive(addon)) {
                addon = null;
            }

            // Some perks run their own clock, which the slot shows instead of the cooldown.
            int window = 0;
            int tokens = 0;
            // Any perk that holds charges reports them; the slot shows them as pips, and counts
            // down to the next one while it still has some to spend.
            int charges = ClientPerkData.charges(perk.id());
            int maxCharges = ClientPerkData.maxCharges(perk.id());
            if (charges > 0) {
                window = ClientPerkData.rechargeTicks(perk.id());
            }

            if (perk.id().equals(ModPerks.BROKEN_MOVEMENT_DEVICE.id())) {
                window = ClientPerkData.recallTicksRemaining();
            } else if (perk.id().equals(ModPerks.NASTY_BLADE.id())) {
                tokens = ClientPerkData.bladeTokens();
                window = ClientPerkData.bladeTicksRemaining();
            }

            renderSlot(graphics, minecraft, perk, addon, slot, tier, x, y, remaining, total, window, charges,
                    maxCharges, tokens);
        }

        graphics.pose().popPose();
    }

    private void renderSlot(GuiGraphics graphics, Minecraft minecraft, Perk perk, @Nullable Addon addon, int slot,
            int tier, int x, int y, int remaining, int total, int window, int charges, int maxCharges,
            int tokens) {
        boolean ready = remaining <= 0;
        boolean counting = window > 0;

        graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0xA0000000);
        GuiUtil.drawBorder(graphics, x, y, SLOT_SIZE, SLOT_SIZE,
                counting ? COLOR_WINDOW : (ready ? 0xFFB4232F : 0xFF3A2226));

        graphics.blitSprite(perk.icon(tier), x + 2, y + 2, 16, 16);

        // The addon rides alongside its perk, smaller so the perk stays the thing you read first.
        int textX = x + SLOT_SIZE + 3;
        if (addon != null) {
            int addonY = y + (SLOT_SIZE - ADDON_ICON_SIZE) / 2;
            graphics.blitSprite(addon.icon(), textX, addonY, ADDON_ICON_SIZE, ADDON_ICON_SIZE);
            textX += ADDON_ICON_SIZE + 3;
        }

        // Only active perks respond to a key, so only they advertise one.
        if (perk.type() == PerkType.ACTIVE) {
            String key = ModKeyMappings.boundKeyLabel(slot);
            if (key != null) {
                graphics.drawString(minecraft.font, key, textX, y + 6,
                        ready ? 0xFFB4232F : 0xFF6A5A5E, true);
            }
        }

        // A live window outranks the cooldown: whichever clock is actually running is the only
        // number worth reading.
        if (counting) {
            // Scrim so the count reads over the icon without hiding which perk it belongs to.
            graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0x80000000);
            String seconds = Integer.toString((window + 19) / 20);
            graphics.drawString(minecraft.font, seconds,
                    x + (SLOT_SIZE - minecraft.font.width(seconds)) / 2, y + 7, COLOR_WINDOW, true);
        } else if (!ready) {
            // Dark overlay that drains from the top as the cooldown runs out.
            int covered = total > 0 ? Math.round(SLOT_SIZE * (remaining / (float) total)) : SLOT_SIZE;
            covered = Math.clamp(covered, 0, SLOT_SIZE);
            graphics.fill(x, y + SLOT_SIZE - covered, x + SLOT_SIZE, y + SLOT_SIZE, 0xB0000000);

            String label = Integer.toString(Math.max(1, remaining / 20));
            graphics.drawString(minecraft.font, label,
                    x + (SLOT_SIZE - minecraft.font.width(label)) / 2, y + 7, 0xFFFFFFFF, true);
        }

        if (charges >= 0) {
            renderCharges(graphics, minecraft, x, y, charges, maxCharges);
        }
        renderWindUp(graphics, minecraft, perk, slot, x, y, ready);
        if (tokens > 0) {
            // The count rides in the corner of the slot, so the seconds keep the middle.
            String label = Integer.toString(tokens);
            graphics.drawString(minecraft.font, label,
                    x + SLOT_SIZE - minecraft.font.width(label) - 1, y + SLOT_SIZE - 8, COLOR_TOKENS, true);
        }
    }

    /**
     * Pulled Pin: how hard the throw is wound up, while the key is down. A gold bar rising up the
     * side of the slot, and the bonus it is worth written beside it.
     */
    private void renderWindUp(GuiGraphics graphics, Minecraft minecraft, Perk perk, int slot, int x, int y,
            boolean ready) {
        if (ready && perk.id().equals(ModPerks.FRAGNADE.id())) {
            renderFragHold(graphics, minecraft, slot, x, y);
            return;
        }
        if (!ready || !perk.id().equals(ModPerks.FLASHBANG.id())) {
            return;
        }
        PlayerPerkData data = ClientPerkData.get();
        if (!data.isAddonActive(ModAddons.PULLED_PIN)) {
            return;
        }
        int held = ClientEventHandler.slotHeldTicks(slot);
        if (held <= 0) {
            return;
        }

        double share = Math.min(1.0D, held / (double) ModAddons.PULLED_PIN_FULL_HOLD_TICKS);
        int filled = (int) Math.round(SLOT_SIZE * share);
        graphics.fill(x - 3, y, x - 1, y + SLOT_SIZE, 0xC0201014);
        graphics.fill(x - 3, y + SLOT_SIZE - filled, x - 1, y + SLOT_SIZE,
                share >= 1.0D ? 0xFFFFE066 : COLOR_TOKENS);

        String label = "+" + Math.round(share * ModAddons.PULLED_PIN_MAX_BONUS * 100.0D) + "%";
        graphics.drawString(minecraft.font, label, x + (SLOT_SIZE - minecraft.font.width(label)) / 2,
                y - 9, share >= 1.0D ? 0xFFFFE066 : COLOR_TOKENS, true);
    }

    /**
     * Frag' Nade: how long the throw has been wound, written out in seconds against the three it
     * tops out at, with a bar up the side of the slot that turns magenta at full strength.
     */
    private void renderFragHold(GuiGraphics graphics, Minecraft minecraft, int slot, int x, int y) {
        int held = ClientEventHandler.slotHeldTicks(slot);
        if (held <= 0) {
            return;
        }
        int capped = Math.min(held, ModPerks.FRAG_HOLD_MAX_TICKS);
        double share = capped / (double) ModPerks.FRAG_HOLD_MAX_TICKS;
        int filled = (int) Math.round(SLOT_SIZE * share);
        int color = share >= 1.0D ? 0xFFFF55FF : 0xFF55FFFF;

        graphics.fill(x - 3, y, x - 1, y + SLOT_SIZE, 0xC0201014);
        graphics.fill(x - 3, y + SLOT_SIZE - filled, x - 1, y + SLOT_SIZE, color);

        String label = String.format(Locale.ROOT, "%.1fs / %ds", capped / 20.0D, ModPerks.FRAG_HOLD_MAX_TICKS / 20);
        graphics.drawString(minecraft.font, label, x, y - 9, color, true);
    }

    /**
     * What the device has left, along the bottom of the slot. Drawn last, over the cooldown and the
     * countdown alike, so a spent device still says how much of it is back.
     * <p>
     * A handful of charges reads best as a row of pips. A reserve in the hundreds does not, so past
     * a few it is written out as a number instead.
     */
    private void renderCharges(GuiGraphics graphics, Minecraft minecraft, int x, int y, int charges,
            int max) {
        if (max <= 0) {
            return;
        }
        if (max > MAX_PIPS) {
            String label = Integer.toString(charges);
            int width = minecraft.font.width(label);
            graphics.fill(x + SLOT_SIZE - width - 3, y + SLOT_SIZE - 9, x + SLOT_SIZE, y + SLOT_SIZE,
                    0xB0000000);
            graphics.drawString(minecraft.font, label, x + SLOT_SIZE - width - 1, y + SLOT_SIZE - 8,
                    COLOR_WINDOW, true);
            return;
        }

        int pitch = PIP_WIDTH + 1;
        int left = x + (SLOT_SIZE - (max * pitch - 1)) / 2;
        int top = y + SLOT_SIZE - PIP_HEIGHT - 1;
        for (int i = 0; i < max; i++) {
            int pipX = left + i * pitch;
            graphics.fill(pipX, top, pipX + PIP_WIDTH, top + PIP_HEIGHT,
                    i < charges ? COLOR_WINDOW : 0xC0201014);
        }
    }
}
