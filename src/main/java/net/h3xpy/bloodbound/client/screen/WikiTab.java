package net.h3xpy.bloodbound.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.advancement.AchievementRewards;
import net.h3xpy.bloodbound.client.ClientPerkData;
import net.h3xpy.bloodbound.client.ClientWiki;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.network.ClaimAchievementPayload;
import net.h3xpy.bloodbound.perk.Addon;
import net.h3xpy.bloodbound.perk.AddonRegistry;
import net.h3xpy.bloodbound.perk.Perk;
import net.h3xpy.bloodbound.perk.PerkRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Wiki tab: every perk, owned or not, on the left; on the right, what the selected one does, its
 * addons, its popularity in this world in stars, and the advancements filed under it — each of which
 * can be claimed for a reward once earned. The first entry gathers the general advancements.
 */
public class WikiTab {

    private static final int ENTRY_HEIGHT = 22;
    private static final int LINE = 10;
    private static final int COLOR_STAR = 0xFFF2C14E;
    private static final int COLOR_STAR_OFF = 0xFF4A3A3E;
    private static final int COLOR_CLAIMED = 0xFF6BD46B;
    private static final String STAR = "★";

    private final PerkTableScreen screen;
    private int listScroll;
    private int detailScroll;
    /** Height the detail panel took last frame, for clamping its scroll. */
    private int detailHeight;
    /** The selected entry: null for the general page, a perk id otherwise. */
    @Nullable
    private ResourceLocation selected;

    /** Where the claim buttons were drawn this frame, for the click that may follow. */
    private record ClaimButton(int x, int y, int width, int height, ResourceLocation id) {}

    private final List<ClaimButton> buttons = new ArrayList<>();

    public WikiTab(PerkTableScreen screen) {
        this.screen = screen;
    }

    // --- layout ---

    private int listWidth() {
        return Math.min(150, screen.contentWidth() * 38 / 100);
    }

    private int listX() {
        return screen.contentX() + 4;
    }

    private int listY() {
        return screen.contentY() + 4;
    }

    private int listHeight() {
        return screen.contentHeight() - 8;
    }

    private int visibleEntries() {
        return Math.max(1, listHeight() / ENTRY_HEIGHT);
    }

    private int detailX() {
        return listX() + listWidth() + 10;
    }

    private int detailY() {
        return screen.contentY() + 4;
    }

    private int detailWidth() {
        return screen.contentX() + screen.contentWidth() - 6 - detailX();
    }

    private int detailBottom() {
        return screen.contentY() + screen.contentHeight() - 4;
    }

    /** The general page first, then every perk by name. */
    private static List<Perk> perks() {
        List<Perk> perks = new ArrayList<>(PerkRegistry.all());
        perks.sort(Comparator.comparing(perk -> perk.displayName().getString(), String.CASE_INSENSITIVE_ORDER));
        return perks;
    }

    private int entryCount() {
        return 1 + PerkRegistry.count();
    }

    // --- rendering ---

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        listScroll = Math.clamp(listScroll, 0, Math.max(0, entryCount() - visibleEntries()));
        List<Perk> perks = perks();

        int shown = Math.min(visibleEntries(), entryCount() - listScroll);
        for (int i = 0; i < shown; i++) {
            int index = listScroll + i;
            int y = listY() + i * ENTRY_HEIGHT;
            if (index == 0) {
                renderGeneralEntry(graphics, y, mouseX, mouseY);
            } else {
                renderPerkEntry(graphics, perks.get(index - 1), y, mouseX, mouseY);
            }
        }
        renderScrollbar(graphics);

        graphics.fill(detailX() - 5, screen.contentY() + 2, detailX() - 4, detailBottom(), PerkTableScreen.COLOR_BORDER);
        renderDetail(graphics, mouseX, mouseY);
    }

    private void renderGeneralEntry(GuiGraphics graphics, int y, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        boolean active = selected == null;
        drawEntryBackground(graphics, y, active, mouseX, mouseY);
        graphics.renderItem(new ItemStack(Items.WRITABLE_BOOK), listX() + 3, y + 3);
        graphics.drawString(font, Component.translatable("bloodbound.wiki.general"), listX() + 23, y + 7,
                PerkTableScreen.COLOR_TEXT, false);
        if (hasClaimable(null)) {
            drawClaimableMark(graphics, y);
        }
    }

    private void renderPerkEntry(GuiGraphics graphics, Perk perk, int y, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        PlayerPerkData data = ClientPerkData.get();
        int tier = data.getUnlockedTier(perk);
        drawEntryBackground(graphics, y, perk.id().equals(selected), mouseX, mouseY);

        // A perk not learned yet is drawn dimmed, so what is owned stands out at a glance.
        if (tier <= 0) {
            graphics.setColor(0.35F, 0.35F, 0.35F, 1.0F);
        }
        graphics.blitSprite(perk.icon(Math.max(1, tier)), listX() + 3, y + 3, 16, 16);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        graphics.drawString(font, GuiUtil.fitToWidth(font, perk.displayName(), listWidth() - 34), listX() + 23,
                y + 2, tier > 0 ? PerkTableScreen.COLOR_TEXT : 0xFF6A5A5E, false);
        Component status = tier > 0
                ? Component.translatable("bloodbound.wiki.tier", GuiUtil.romanTier(tier))
                : Component.translatable("bloodbound.wiki.locked");
        graphics.drawString(font, status, listX() + 23, y + 12, tier > 0 ? 0xFF9A8A8A : 0xFF5A4A4E, false);
        drawStars(graphics, listX() + 23 + font.width(status) + 4, y + 12, ClientWiki.perkStars(perk.id()));

        if (hasClaimable(perk.id())) {
            drawClaimableMark(graphics, y);
        }
    }

    private void drawEntryBackground(GuiGraphics graphics, int y, boolean active, int mouseX, int mouseY) {
        boolean hovered = isInside(mouseX, mouseY, listX(), y, listWidth(), ENTRY_HEIGHT - 2);
        graphics.fill(listX(), y, listX() + listWidth(), y + ENTRY_HEIGHT - 2,
                active ? 0xFF3A1018 : (hovered ? 0xFF241016 : PerkTableScreen.COLOR_SLOT));
        if (active) {
            graphics.fill(listX(), y, listX() + 2, y + ENTRY_HEIGHT - 2, PerkTableScreen.COLOR_ACCENT);
        }
    }

    /** A gold "!" on an entry with a reward waiting to be claimed. */
    private void drawClaimableMark(GuiGraphics graphics, int y) {
        graphics.drawString(Minecraft.getInstance().font, "!", listX() + listWidth() - 7, y + 6, COLOR_STAR, false);
    }

    private void drawStars(GuiGraphics graphics, int x, int y, int stars) {
        Font font = Minecraft.getInstance().font;
        for (int i = 0; i < 5; i++) {
            graphics.drawString(font, STAR, x + i * 7, y, i < stars ? COLOR_STAR : COLOR_STAR_OFF, false);
        }
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int max = entryCount() - visibleEntries();
        if (max <= 0) {
            return;
        }
        int trackX = listX() + listWidth() + 2;
        graphics.fill(trackX, listY(), trackX + 3, listY() + listHeight(), 0xFF1A0A0E);
        int thumbHeight = Math.max(12, listHeight() * visibleEntries() / entryCount());
        int thumbY = listY() + (listHeight() - thumbHeight) * listScroll / max;
        graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, PerkTableScreen.COLOR_ACCENT);
    }

    // --- the detail panel ---

    private void renderDetail(GuiGraphics graphics, int mouseX, int mouseY) {
        buttons.clear();
        int maxScroll = Math.max(0, detailHeight - (detailBottom() - detailY()));
        detailScroll = Math.clamp(detailScroll, 0, maxScroll);

        graphics.enableScissor(detailX(), detailY(), detailX() + detailWidth(), detailBottom());
        int top = detailY() - detailScroll;
        int y = selected == null ? renderGeneral(graphics, top) : renderPerk(graphics, top, mouseX, mouseY);
        if (selected == null || PerkRegistry.get(selected) != null) {
            y = renderAchievements(graphics, y + 6, mouseX, mouseY);
        }
        graphics.disableScissor();
        detailHeight = y - top;

        if (maxScroll > 0) {
            int trackX = detailX() + detailWidth() + 2;
            int trackHeight = detailBottom() - detailY();
            graphics.fill(trackX, detailY(), trackX + 2, detailBottom(), 0xFF1A0A0E);
            int thumbHeight = Math.max(12, trackHeight * trackHeight / Math.max(1, detailHeight));
            int thumbY = detailY() + (trackHeight - thumbHeight) * detailScroll / maxScroll;
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, PerkTableScreen.COLOR_ACCENT);
        }
    }

    private int renderGeneral(GuiGraphics graphics, int y) {
        Font font = Minecraft.getInstance().font;
        graphics.drawString(font, Component.translatable("bloodbound.wiki.general").withStyle(ChatFormatting.BOLD),
                detailX(), y, PerkTableScreen.COLOR_TEXT, false);
        y += LINE + 4;
        return paragraph(graphics, Component.translatable("bloodbound.wiki.general_desc"), y,
                PerkTableScreen.COLOR_TEXT_DIM);
    }

    private int renderPerk(GuiGraphics graphics, int y, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        Perk perk = PerkRegistry.get(selected);
        if (perk == null) {
            return y;
        }
        PlayerPerkData data = ClientPerkData.get();
        int tier = data.getUnlockedTier(perk);

        graphics.blitSprite(perk.icon(Math.max(1, tier)), detailX(), y, 20, 20);
        graphics.drawString(font, perk.displayName().copy().withStyle(ChatFormatting.BOLD), detailX() + 24, y + 1,
                PerkTableScreen.COLOR_TEXT, false);
        Component status = tier > 0
                ? Component.translatable("bloodbound.wiki.owned_tier", GuiUtil.romanTier(tier))
                : Component.translatable("bloodbound.wiki.locked");
        graphics.drawString(font, status, detailX() + 24, y + 11, tier > 0 ? COLOR_CLAIMED : 0xFF8A5A5E, false);
        drawStars(graphics, detailX() + detailWidth() - 36, y + 1, ClientWiki.perkStars(perk.id()));
        y += 24;

        y = paragraph(graphics, perk.description(tier), y, PerkTableScreen.COLOR_TEXT_DIM);

        y = heading(graphics, Component.translatable("bloodbound.wiki.addons"), y + 6);
        List<Addon> addons = new ArrayList<>(AddonRegistry.forPerk(perk.id()));
        addons.sort(Comparator.comparingInt(addon -> addon.rarity().ordinal()));
        if (addons.isEmpty()) {
            y = paragraph(graphics, Component.translatable("bloodbound.wiki.no_addons"), y, 0xFF6A5A5E);
        }
        for (Addon addon : addons) {
            boolean owned = data.isAddonUnlocked(addon.id());
            if (!owned) {
                graphics.setColor(0.35F, 0.35F, 0.35F, 1.0F);
            }
            graphics.blitSprite(addon.icon(), detailX(), y, 12, 12);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            MutableComponent name = addon.displayName().copy().withStyle(addon.rarity().textColor());
            name.append(Component.literal("  "))
                    .append(Component.translatable(owned ? "bloodbound.wiki.owned" : "bloodbound.wiki.not_owned")
                            .withStyle(owned ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
            graphics.drawString(font, GuiUtil.fitToWidth(font, name, detailWidth() - 54), detailX() + 15, y + 2,
                    PerkTableScreen.COLOR_TEXT, false);
            drawStars(graphics, detailX() + detailWidth() - 36, y + 2, ClientWiki.addonStars(addon.id()));
            y += 14;
            y = paragraph(graphics, addon.description(), y, 0xFF7A6A6E);
            y += 3;
        }
        return y;
    }

    private int renderAchievements(GuiGraphics graphics, int y, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        y = heading(graphics, Component.translatable("bloodbound.wiki.achievements"), y);
        List<AchievementRewards.Entry> entries = AchievementRewards.forPerk(selected);
        if (entries.isEmpty()) {
            return paragraph(graphics, Component.translatable("bloodbound.wiki.no_achievements"), y, 0xFF6A5A5E);
        }
        PlayerPerkData data = ClientPerkData.get();
        for (AchievementRewards.Entry entry : entries) {
            boolean earned = ClientWiki.isEarned(entry.id());
            boolean claimed = data.isAchievementClaimed(entry.id());
            AchievementRewards.Difficulty difficulty = entry.difficulty();

            graphics.drawString(font, GuiUtil.fitToWidth(font, Component.translatable(entry.titleKey()), detailWidth()),
                    detailX(), y, earned ? PerkTableScreen.COLOR_TEXT : 0xFF8A7A7A, false);
            y += LINE;
            y = paragraph(graphics, Component.translatable(entry.descriptionKey()), y, 0xFF7A6A6E);

            Component reward = Component.translatable(difficulty.translationKey()).withStyle(difficulty.color())
                    .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable(difficulty.givesOffering()
                            ? "bloodbound.wiki.reward_both" : "bloodbound.wiki.reward_shards", difficulty.shards())
                            .withStyle(ChatFormatting.GRAY));
            graphics.drawString(font, GuiUtil.fitToWidth(font, reward, detailWidth() - 60), detailX(), y + 2,
                    PerkTableScreen.COLOR_TEXT, false);

            int buttonWidth = 52;
            int buttonX = detailX() + detailWidth() - buttonWidth;
            if (claimed) {
                Component mark = Component.translatable("bloodbound.wiki.claimed_mark");
                graphics.drawString(font, mark, detailX() + detailWidth() - font.width(mark), y + 2, COLOR_CLAIMED, false);
            } else if (earned) {
                boolean hovered = isInside(mouseX, mouseY, buttonX, y, buttonWidth, 12)
                        && mouseY >= detailY() && mouseY < detailBottom();
                graphics.fill(buttonX, y, buttonX + buttonWidth, y + 12, hovered ? 0xFFD03A46 : PerkTableScreen.COLOR_ACCENT);
                GuiUtil.drawBorder(graphics, buttonX, y, buttonWidth, 12, 0xFFF2C14E);
                graphics.drawCenteredString(font, Component.translatable("bloodbound.wiki.claim"),
                        buttonX + buttonWidth / 2, y + 2, 0xFFFFFFFF);
                buttons.add(new ClaimButton(buttonX, y, buttonWidth, 12, entry.id()));
            } else {
                Component mark = Component.translatable("bloodbound.wiki.not_earned");
                graphics.drawString(font, mark, detailX() + detailWidth() - font.width(mark), y + 2, 0xFF6A5A5E, false);
            }
            y += 18;
        }
        return y;
    }

    private int heading(GuiGraphics graphics, Component text, int y) {
        Font font = Minecraft.getInstance().font;
        graphics.drawString(font, text.copy().withStyle(ChatFormatting.BOLD), detailX(), y, PerkTableScreen.COLOR_ACCENT, false);
        graphics.fill(detailX(), y + LINE, detailX() + detailWidth(), y + LINE + 1, PerkTableScreen.COLOR_BORDER);
        return y + LINE + 4;
    }

    /** Draws a paragraph wrapped to the panel, and returns where the next thing goes. */
    private int paragraph(GuiGraphics graphics, Component text, int y, int color) {
        Font font = Minecraft.getInstance().font;
        for (FormattedCharSequence line : font.split(text, detailWidth())) {
            graphics.drawString(font, line, detailX(), y, color, false);
            y += LINE;
        }
        return y;
    }

    // --- claims waiting ---

    /** Whether any advancement filed under this perk (or the general ones, for null) waits to be claimed. */
    private static boolean hasClaimable(@Nullable ResourceLocation perkId) {
        PlayerPerkData data = ClientPerkData.get();
        for (AchievementRewards.Entry entry : AchievementRewards.forPerk(perkId)) {
            if (ClientWiki.isEarned(entry.id()) && !data.isAchievementClaimed(entry.id())) {
                return true;
            }
        }
        return false;
    }

    // --- tooltips ---

    public void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (isInside(mouseX, mouseY, detailX() + detailWidth() - 36, detailY() - detailScroll, 36, 10)
                && selected != null && mouseY >= detailY()) {
            graphics.renderTooltip(Minecraft.getInstance().font,
                    Component.translatable("bloodbound.wiki.stars_hint"), mouseX, mouseY);
        }
    }

    // --- input ---

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (int) mouseX;
        int y = (int) mouseY;
        for (ClaimButton claim : buttons) {
            if (isInside(x, y, claim.x(), claim.y(), claim.width(), claim.height())
                    && y >= detailY() && y < detailBottom()) {
                PacketDistributor.sendToServer(new ClaimAchievementPayload(claim.id()));
                return true;
            }
        }

        if (!isInside(x, y, listX(), listY(), listWidth(), listHeight())) {
            return false;
        }
        int index = listScroll + (y - listY()) / ENTRY_HEIGHT;
        if (index < 0 || index >= entryCount()) {
            return true;
        }
        ResourceLocation picked = index == 0 ? null : perks().get(index - 1).id();
        if (picked == null ? selected != null : !picked.equals(selected)) {
            selected = picked;
            detailScroll = 0;
        }
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        int x = (int) mouseX;
        int y = (int) mouseY;
        if (isInside(x, y, listX(), listY(), listWidth() + 6, listHeight())) {
            listScroll = Math.clamp(listScroll - (int) Math.signum(scrollY), 0,
                    Math.max(0, entryCount() - visibleEntries()));
            return true;
        }
        if (isInside(x, y, detailX(), detailY(), detailWidth() + 6, detailBottom() - detailY())) {
            detailScroll = Math.max(0, detailScroll - (int) Math.signum(scrollY) * 3 * LINE);
            return true;
        }
        return false;
    }

    private static boolean isInside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
