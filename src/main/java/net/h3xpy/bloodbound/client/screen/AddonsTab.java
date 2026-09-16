package net.h3xpy.bloodbound.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.client.ClientPerkData;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.network.SetAddonPayload;
import net.h3xpy.bloodbound.perk.Addon;
import net.h3xpy.bloodbound.perk.AddonRegistry;
import net.h3xpy.bloodbound.perk.Perk;
import net.h3xpy.bloodbound.perk.PerkRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Addons tab: everything owned on the left, one addon slot per equipped perk on the right.
 * <p>
 * Each addon belongs to exactly one perk and can only go in that perk's slot, so equipping is a
 * single click — there is never a choice of destination.
 */
public class AddonsTab {

    private static final int ENTRY_HEIGHT = 26;
    private static final int SLOT_HEIGHT = 34;

    private final PerkTableScreen screen;
    private int scrollOffset;

    public AddonsTab(PerkTableScreen screen) {
        this.screen = screen;
    }

    // --- layout ---

    /** The list takes a little over half the content area; the equipped slots take the rest. */
    private int listWidth() {
        return Math.min(220, screen.contentWidth() * 52 / 100);
    }

    private int slotWidth() {
        return screen.contentWidth() - listWidth() - 20;
    }

    private int listX() {
        return screen.contentX() + 4;
    }

    private int listY() {
        return screen.contentY() + 16;
    }

    private int listHeight() {
        return screen.contentHeight() - 22;
    }

    private int visibleEntries() {
        return Math.max(1, listHeight() / ENTRY_HEIGHT);
    }

    private int slotsX() {
        return screen.contentX() + listWidth() + 12;
    }

    private int slotY(int slot) {
        return screen.contentY() + 16 + slot * (SLOT_HEIGHT + 2);
    }

    /**
     * Owned addons, with the ones whose perk is equipped first: those are the only ones a click can
     * actually do anything with, so they belong at the top of the list.
     */
    private List<Addon> ownedAddons() {
        PlayerPerkData data = ClientPerkData.get();
        List<Addon> owned = new ArrayList<>();
        for (Addon addon : AddonRegistry.all()) {
            if (data.isAddonUnlocked(addon.id())) {
                owned.add(addon);
            }
        }
        // Equippable first, then all the addons of one perk together, and inside a group cheapest
        // rarity first. An addon's perk is either equipped or not, so the first key can never split
        // a group up, and registry order never gets a say.
        owned.sort(Comparator
                .comparingInt((Addon addon) -> data.getActiveTier(addon.perkId()) > 0 ? 0 : 1)
                .thenComparing(addon -> perkName(addon.perkId()), String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(addon -> addon.rarity().ordinal()));
        return owned;
    }

    /** The parent perk's display name, or the raw id if the perk somehow went missing. */
    private static String perkName(ResourceLocation perkId) {
        Perk perk = PerkRegistry.get(perkId);
        return perk != null ? perk.displayName().getString() : String.valueOf(perkId);
    }

    private int maxScroll() {
        return Math.max(0, ownedAddons().size() - visibleEntries());
    }

    // --- rendering ---

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        PlayerPerkData data = ClientPerkData.get();
        List<Addon> owned = ownedAddons();
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll());

        graphics.drawString(font, Component.translatable("bloodbound.addons.owned", owned.size()),
                listX(), screen.contentY() + 5, PerkTableScreen.COLOR_TEXT_DIM, false);

        if (owned.isEmpty()) {
            graphics.drawString(font, Component.translatable("bloodbound.addons.empty")
                    .withStyle(ChatFormatting.DARK_GRAY), listX(), listY() + 8,
                    PerkTableScreen.COLOR_TEXT_DIM, false);
        }

        int shown = Math.min(visibleEntries(), owned.size() - scrollOffset);
        for (int i = 0; i < shown; i++) {
            renderEntry(graphics, owned.get(scrollOffset + i), data, listX(), listY() + i * ENTRY_HEIGHT,
                    mouseX, mouseY);
        }
        renderScrollbar(graphics, owned.size());

        graphics.drawString(font, Component.translatable("bloodbound.addons.equipped_header"),
                slotsX(), screen.contentY() + 5, PerkTableScreen.COLOR_TEXT_DIM, false);
        for (int slot = 0; slot < PlayerPerkData.LOADOUT_SIZE; slot++) {
            renderSlot(graphics, data, slot, mouseX, mouseY);
        }
    }

    private void renderEntry(GuiGraphics graphics, Addon addon, PlayerPerkData data, int x, int y, int mouseX,
            int mouseY) {
        var font = Minecraft.getInstance().font;
        boolean equipped = data.isAddonEquipped(addon);
        boolean perkEquipped = data.getActiveTier(addon.perkId()) > 0;
        boolean hovered = isInside(mouseX, mouseY, x, y, listWidth(), ENTRY_HEIGHT - 2);

        // Only an addon that is really in effect is drawn as fitted: one left over in the slot of a
        // perk since taken out of the loadout is doing nothing, and should not look like it is.
        boolean fitted = equipped && perkEquipped;
        graphics.fill(x, y, x + listWidth(), y + ENTRY_HEIGHT - 2,
                fitted ? 0xFF3A1018 : (hovered ? 0xFF241016 : PerkTableScreen.COLOR_SLOT));
        if (fitted) {
            graphics.fill(x, y, x + 2, y + ENTRY_HEIGHT - 2, PerkTableScreen.COLOR_ACCENT);
        }

        graphics.blitSprite(addon.icon(), x + 5, y + 3, 18, 18);
        graphics.drawString(font, GuiUtil.fitToWidth(font, addon.displayName(), listWidth() - 31), x + 27, y + 4,
                perkEquipped ? PerkTableScreen.COLOR_TEXT : 0xFF6A5A5E, false);

        // The parent perk matters more than the rarity here: it decides where the addon can go.
        Perk parent = PerkRegistry.get(addon.perkId());
        Component subtitle = parent != null ? parent.displayName() : addon.displayName();
        graphics.drawString(font, GuiUtil.fitToWidth(font, subtitle, listWidth() - 31), x + 27, y + 14,
                perkEquipped ? 0xFF9A8A8A : 0xFF5A4A4E, false);
    }

    private void renderSlot(GuiGraphics graphics, PlayerPerkData data, int slot, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        int x = slotsX();
        int y = slotY(slot);
        boolean hovered = isInside(mouseX, mouseY, x, y, slotWidth(), SLOT_HEIGHT);

        graphics.fill(x, y, x + slotWidth(), y + SLOT_HEIGHT, hovered ? 0xFF241016 : PerkTableScreen.COLOR_SLOT);
        GuiUtil.drawBorder(graphics, x, y, slotWidth(), SLOT_HEIGHT, PerkTableScreen.COLOR_BORDER);

        Perk perk = PerkRegistry.get(data.getLoadoutSlot(slot));
        if (perk == null) {
            graphics.drawString(font, Component.translatable("bloodbound.addons.no_perk")
                    .withStyle(ChatFormatting.DARK_GRAY), x + 8, y + 13,
                    PerkTableScreen.COLOR_TEXT_DIM, false);
            return;
        }

        graphics.drawString(font, GuiUtil.fitToWidth(font, perk.displayName(), slotWidth() - 8),
                x + 4, y + 4, PerkTableScreen.COLOR_TEXT_DIM, false);

        Addon addon = AddonRegistry.get(data.equippedAddon(perk.id()));
        if (addon == null) {
            graphics.drawString(font, Component.translatable("bloodbound.addons.empty_slot")
                    .withStyle(ChatFormatting.DARK_GRAY), x + 4, y + 18,
                    PerkTableScreen.COLOR_TEXT_DIM, false);
            return;
        }

        graphics.blitSprite(addon.icon(), x + 4, y + 15, 16, 16);
        // ringColor already carries full alpha; ChatFormatting.getColor() does not.
        graphics.drawString(font, GuiUtil.fitToWidth(font, addon.displayName(), slotWidth() - 28),
                x + 24, y + 19, addon.rarity().ringColor(), false);
    }

    private void renderScrollbar(GuiGraphics graphics, int total) {
        int max = maxScroll();
        if (max <= 0) {
            return;
        }
        int trackX = listX() + listWidth() + 2;
        int trackHeight = listHeight();
        graphics.fill(trackX, listY(), trackX + 3, listY() + trackHeight, 0xFF1A0A0E);

        int thumbHeight = Math.max(12, trackHeight * visibleEntries() / total);
        int thumbY = listY() + (trackHeight - thumbHeight) * scrollOffset / max;
        graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, PerkTableScreen.COLOR_ACCENT);
    }

    // --- tooltips ---

    public void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        PlayerPerkData data = ClientPerkData.get();

        Addon hovered = addonAt(mouseX, mouseY);
        if (hovered == null) {
            int slot = slotAt(mouseX, mouseY);
            if (slot >= 0) {
                hovered = AddonRegistry.get(data.equippedAddon(data.getLoadoutSlot(slot)));
            }
        }
        if (hovered == null) {
            return;
        }

        List<FormattedCharSequence> lines = new ArrayList<>();
        GuiUtil.addLine(lines, hovered.displayName().copy().withStyle(ChatFormatting.WHITE));
        GuiUtil.addLine(lines, Component.translatable(hovered.rarity().translationKey())
                .withStyle(hovered.rarity().textColor()));

        Perk parent = PerkRegistry.get(hovered.perkId());
        if (parent != null) {
            GuiUtil.addLine(lines, Component.translatable("bloodbound.addon.for_perk", parent.displayName())
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        GuiUtil.addLine(lines, Component.empty());
        GuiUtil.addWrapped(lines, hovered.description().copy().withStyle(ChatFormatting.GRAY));
        GuiUtil.addLine(lines, Component.empty());

        if (data.getActiveTier(hovered.perkId()) <= 0) {
            GuiUtil.addLine(lines, Component.translatable("bloodbound.addons.needs_perk").withStyle(ChatFormatting.RED));
        } else {
            GuiUtil.addLine(lines, Component.translatable(data.isAddonEquipped(hovered)
                    ? "bloodbound.addons.hint_unequip"
                    : "bloodbound.addons.hint_equip").withStyle(ChatFormatting.DARK_GRAY));
        }

        graphics.renderTooltip(Minecraft.getInstance().font, lines, mouseX, mouseY);
    }

    // --- input ---

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        PlayerPerkData data = ClientPerkData.get();

        Addon addon = addonAt((int) mouseX, (int) mouseY);
        if (addon != null) {
            // Clicking one whose perk is not equipped does nothing at all — the tooltip already says
            // why. The click is still swallowed so it cannot fall through to whatever is behind.
            if (data.getActiveTier(addon.perkId()) <= 0) {
                return true;
            }
            PacketDistributor.sendToServer(data.isAddonEquipped(addon)
                    ? SetAddonPayload.clear(addon.perkId())
                    : new SetAddonPayload(addon.perkId().toString(), addon.id().toString()));
            return true;
        }

        int slot = slotAt((int) mouseX, (int) mouseY);
        if (slot >= 0) {
            ResourceLocation perkId = data.getLoadoutSlot(slot);
            if (perkId != null && data.equippedAddon(perkId) != null) {
                PacketDistributor.sendToServer(SetAddonPayload.clear(perkId));
            }
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!isInside((int) mouseX, (int) mouseY, listX(), listY(), listWidth() + 6, listHeight())) {
            return false;
        }
        scrollOffset = Math.clamp(scrollOffset - (int) Math.signum(scrollY), 0, maxScroll());
        return true;
    }

    // --- hit testing ---

    @Nullable
    private Addon addonAt(int mouseX, int mouseY) {
        List<Addon> owned = ownedAddons();
        int shown = Math.min(visibleEntries(), Math.max(0, owned.size() - scrollOffset));
        for (int i = 0; i < shown; i++) {
            if (isInside(mouseX, mouseY, listX(), listY() + i * ENTRY_HEIGHT, listWidth(), ENTRY_HEIGHT - 2)) {
                return owned.get(scrollOffset + i);
            }
        }
        return null;
    }

    private int slotAt(int mouseX, int mouseY) {
        for (int slot = 0; slot < PlayerPerkData.LOADOUT_SIZE; slot++) {
            if (isInside(mouseX, mouseY, slotsX(), slotY(slot), slotWidth(), SLOT_HEIGHT)) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean isInside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
