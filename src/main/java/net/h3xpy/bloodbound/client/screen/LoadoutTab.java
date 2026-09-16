package net.h3xpy.bloodbound.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.client.ClientPerkData;
import net.h3xpy.bloodbound.client.ModKeyMappings;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.network.SetLoadoutPayload;
import net.h3xpy.bloodbound.perk.Perk;
import net.h3xpy.bloodbound.perk.PerkRegistry;
import net.h3xpy.bloodbound.perk.PerkType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Loadout tab: learned perks on the left, the four equipped slots on the right.
 * <p>
 * Clicking a learned perk equips it in the first free slot, clicking an equipped one takes it off,
 * and clicking a filled slot empties it.
 */
public class LoadoutTab {

    private static final int ENTRY_HEIGHT = 26;
    private static final int SLOT_HEIGHT = 34;

    private final PerkTableScreen screen;
    private int scrollOffset;

    public LoadoutTab(PerkTableScreen screen) {
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
        return screen.contentY() + 18;
    }

    private int listHeight() {
        return screen.contentHeight() - 24;
    }

    /**
     * Width of the search box. The count sits to its right, so the box gives up exactly as much
     * room as the longest count could need — measured, rather than guessed at.
     */
    int searchWidth() {
        int countWidth = Minecraft.getInstance().font
                .width(Component.translatable("bloodbound.loadout.learned", 999));
        return Math.max(60, listWidth() - countWidth - 10);
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

    /** Perks the player has learned, alphabetical, narrowed to whatever is in the search box. */
    private List<Perk> learnedPerks() {
        PlayerPerkData data = ClientPerkData.get();
        String query = screen.searchQuery();
        List<Perk> perks = new ArrayList<>();
        for (Perk perk : PerkRegistry.all()) {
            if (data.isUnlocked(perk.id()) && matches(perk, query)) {
                perks.add(perk);
            }
        }
        perks.sort(Comparator.comparing(perk -> perk.displayName().getString(), String.CASE_INSENSITIVE_ORDER));
        return perks;
    }

    /** Matches on the name the player actually reads, so the filter follows the game language. */
    private static boolean matches(Perk perk, String query) {
        return query.isEmpty()
                || perk.displayName().getString().toLowerCase(Locale.ROOT).contains(query);
    }

    /** A narrower list can leave the scroll position past the end. */
    void onSearchChanged() {
        scrollOffset = 0;
    }

    private int maxScroll() {
        return Math.max(0, learnedPerks().size() - visibleEntries());
    }

    // --- rendering ---

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        PlayerPerkData data = ClientPerkData.get();
        List<Perk> perks = learnedPerks();
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll());

        // The search box sits where this label used to, so the count moves to the end of the column.
        Component count = Component.translatable("bloodbound.loadout.learned", perks.size());
        graphics.drawString(font, count, listX() + listWidth() - font.width(count),
                screen.contentY() + 6, PerkTableScreen.COLOR_TEXT_DIM, false);

        if (perks.isEmpty()) {
            graphics.drawString(font, Component.translatable(screen.searchQuery().isEmpty()
                            ? "bloodbound.loadout.empty"
                            : "bloodbound.loadout.no_match").withStyle(ChatFormatting.DARK_GRAY),
                    listX(), listY() + 8, PerkTableScreen.COLOR_TEXT_DIM, false);
        }

        int shown = Math.min(visibleEntries(), perks.size() - scrollOffset);
        for (int i = 0; i < shown; i++) {
            Perk perk = perks.get(scrollOffset + i);
            renderEntry(graphics, perk, data, listX(), listY() + i * ENTRY_HEIGHT, mouseX, mouseY);
        }

        renderScrollbar(graphics, perks.size());

        int equipped = 0;
        for (int slot = 0; slot < PlayerPerkData.LOADOUT_SIZE; slot++) {
            if (data.getLoadoutSlot(slot) != null) {
                equipped++;
            }
        }
        graphics.drawString(font,
                Component.translatable("bloodbound.loadout.slots", equipped, PlayerPerkData.LOADOUT_SIZE),
                slotsX(), screen.contentY() + 5, PerkTableScreen.COLOR_TEXT_DIM, false);

        for (int slot = 0; slot < PlayerPerkData.LOADOUT_SIZE; slot++) {
            renderSlot(graphics, data, slot, mouseX, mouseY);
        }
    }

    private void renderEntry(GuiGraphics graphics, Perk perk, PlayerPerkData data, int x, int y, int mouseX,
            int mouseY) {
        var font = Minecraft.getInstance().font;
        boolean equipped = data.isEquipped(perk.id());
        boolean hovered = isInside(mouseX, mouseY, x, y, listWidth(), ENTRY_HEIGHT - 2);

        int background = equipped ? 0xFF3A1018 : (hovered ? 0xFF241016 : PerkTableScreen.COLOR_SLOT);
        graphics.fill(x, y, x + listWidth(), y + ENTRY_HEIGHT - 2, background);
        if (equipped) {
            graphics.fill(x, y, x + 2, y + ENTRY_HEIGHT - 2, PerkTableScreen.COLOR_ACCENT);
        }

        int tier = data.getUnlockedTier(perk.id());

        graphics.blitSprite(perk.icon(tier), x + 5, y + 3, 18, 18);
        graphics.drawString(font, GuiUtil.fitToWidth(font, perk.displayName(), listWidth() - 31),
                x + 27, y + 4, PerkTableScreen.COLOR_TEXT, false);

        graphics.drawString(font, Component.translatable("bloodbound.tier", GuiUtil.romanTier(tier))
                .withStyle(ChatFormatting.GOLD), x + 27, y + 14, 0xFFD9A441, false);
    }

    private void renderSlot(GuiGraphics graphics, PlayerPerkData data, int slot, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        int x = slotsX();
        int y = slotY(slot);
        boolean hovered = isInside(mouseX, mouseY, x, y, slotWidth(), SLOT_HEIGHT);

        graphics.fill(x, y, x + slotWidth(), y + SLOT_HEIGHT, hovered ? 0xFF241016 : PerkTableScreen.COLOR_SLOT);
        GuiUtil.drawBorder(graphics, x, y, slotWidth(), SLOT_HEIGHT, PerkTableScreen.COLOR_BORDER);

        ResourceLocation perkId = data.getLoadoutSlot(slot);
        Perk perk = PerkRegistry.get(perkId);
        if (perk == null) {
            graphics.drawString(font, Component.translatable("bloodbound.loadout.empty_slot", slot + 1)
                    .withStyle(ChatFormatting.DARK_GRAY), x + 8, y + 13, PerkTableScreen.COLOR_TEXT_DIM, false);
            return;
        }

        int tier = data.getUnlockedTier(perkId);

        graphics.blitSprite(perk.icon(tier), x + 3, y + 3, 28, 28);
        graphics.drawString(font, GuiUtil.fitToWidth(font, perk.displayName(), slotWidth() - 39),
                x + 35, y + 8, PerkTableScreen.COLOR_TEXT, false);
        graphics.drawString(font, Component.translatable("bloodbound.tier", GuiUtil.romanTier(tier)),
                x + 35, y + 19, 0xFFD9A441, false);

        // Each slot has its own activation key, but only an active perk does anything with it.
        if (perk.type() == PerkType.ACTIVE) {
            String key = ModKeyMappings.boundKeyLabel(slot);
            if (key != null) {
                graphics.drawString(font, key, x + slotWidth() - 4 - font.width(key), y + 19,
                        PerkTableScreen.COLOR_ACCENT, false);
            }
        }
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

        Perk hovered = perkAt(mouseX, mouseY);
        if (hovered != null) {
            graphics.renderTooltip(Minecraft.getInstance().font,
                    tooltipFor(hovered, data, data.isEquipped(hovered.id())), mouseX, mouseY);
            return;
        }

        int slot = slotAt(mouseX, mouseY);
        if (slot >= 0) {
            Perk perk = PerkRegistry.get(data.getLoadoutSlot(slot));
            if (perk != null) {
                graphics.renderTooltip(Minecraft.getInstance().font, tooltipFor(perk, data, true),
                        mouseX, mouseY);
            }
        }
    }

    private List<FormattedCharSequence> tooltipFor(Perk perk, PlayerPerkData data, boolean equipped) {
        int tier = data.getUnlockedTier(perk.id());
        List<FormattedCharSequence> lines = new ArrayList<>();
        GuiUtil.addLine(lines, perk.displayName().copy().withStyle(ChatFormatting.WHITE));
        GuiUtil.addLine(lines,
                Component.translatable("bloodbound.tier", GuiUtil.romanTier(tier)).withStyle(ChatFormatting.GOLD));
        GuiUtil.addLine(lines, Component.empty());
        GuiUtil.addWrapped(lines, perk.description(tier).copy().withStyle(ChatFormatting.GRAY));
        GuiUtil.addLine(lines, Component.empty());
        GuiUtil.addLine(lines, Component.translatable(equipped
                ? "bloodbound.loadout.hint_unequip"
                : "bloodbound.loadout.hint_equip").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    // --- input ---

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        PlayerPerkData data = ClientPerkData.get();

        Perk perk = perkAt((int) mouseX, (int) mouseY);
        if (perk != null) {
            if (data.isEquipped(perk.id())) {
                unequip(data, perk.id());
            } else {
                int free = data.firstFreeSlot();
                if (free >= 0) {
                    PacketDistributor.sendToServer(new SetLoadoutPayload(free, perk.id().toString()));
                }
            }
            return true;
        }

        int slot = slotAt((int) mouseX, (int) mouseY);
        if (slot >= 0) {
            if (data.getLoadoutSlot(slot) != null) {
                PacketDistributor.sendToServer(SetLoadoutPayload.clear(slot));
            }
            return true;
        }
        return false;
    }

    private void unequip(PlayerPerkData data, ResourceLocation perkId) {
        for (int slot = 0; slot < PlayerPerkData.LOADOUT_SIZE; slot++) {
            if (perkId.equals(data.getLoadoutSlot(slot))) {
                PacketDistributor.sendToServer(SetLoadoutPayload.clear(slot));
                return;
            }
        }
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
    private Perk perkAt(int mouseX, int mouseY) {
        List<Perk> perks = learnedPerks();
        int shown = Math.min(visibleEntries(), Math.max(0, perks.size() - scrollOffset));
        for (int i = 0; i < shown; i++) {
            if (isInside(mouseX, mouseY, listX(), listY() + i * ENTRY_HEIGHT, listWidth(), ENTRY_HEIGHT - 2)) {
                return perks.get(scrollOffset + i);
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
