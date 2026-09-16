package net.h3xpy.bloodbound.client.screen;

import java.util.Locale;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import net.h3xpy.bloodbound.menu.PerkTableMenu;
import net.h3xpy.bloodbound.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The perk table. Three tabs: the loadout of equipped perks, the addons attached to them, and the
 * soulweb where soul shards are spent.
 */
public class PerkTableScreen extends AbstractContainerScreen<PerkTableMenu> {

    // Shared palette, so every tab looks like one screen.
    static final int COLOR_PANEL = 0xF00D0709;
    static final int COLOR_PANEL_INNER = 0xFF16090C;
    static final int COLOR_BORDER = 0xFF5A1220;
    static final int COLOR_ACCENT = 0xFFB4232F;
    static final int COLOR_TEXT = 0xFFE8DCDC;
    static final int COLOR_TEXT_DIM = 0xFF8A7A7A;
    static final int COLOR_SLOT = 0xFF1F0F14;

    private static final int MIN_WIDTH = 316;
    private static final int MIN_HEIGHT = 236;
    /** Past this the soulweb is comfortable and a wider panel would just look empty. */
    private static final int MAX_WIDTH = 520;
    private static final int MAX_HEIGHT = 380;

    private static final int TAB_HEIGHT = 18;
    private static final int HEADER_HEIGHT = 22;

    private final LoadoutTab loadoutTab = new LoadoutTab(this);
    private final AddonsTab addonsTab = new AddonsTab(this);
    private final SoulwebTab soulwebTab = new SoulwebTab(this);

    private Tab activeTab = Tab.LOADOUT;

    /** Filters the learned-perk list. Only shown on the loadout tab. */
    @Nullable
    private EditBox searchBox;

    public PerkTableScreen(PerkTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        // 320x240 is the smallest viewport Minecraft ever hands a GUI; init() grows the panel from
        // there to whatever the window allows, because the soulweb needs every pixel it can get.
        this.imageWidth = MIN_WIDTH;
        this.imageHeight = MIN_HEIGHT;
    }

    @Override
    protected void init() {
        // Sized before super.init(), which is what turns imageWidth/imageHeight into leftPos/topPos.
        this.imageWidth = Math.clamp(this.width - 20, MIN_WIDTH, MAX_WIDTH);
        this.imageHeight = Math.clamp(this.height - 20, MIN_HEIGHT, MAX_HEIGHT);
        super.init();
        soulwebTab.onScreenResized();

        // init() runs again on every resize, so carry the text across rather than losing the filter.
        String previous = searchBox != null ? searchBox.getValue() : "";
        searchBox = new EditBox(font, contentX() + 5, contentY() + 3, loadoutTab.searchWidth(), 12,
                Component.translatable("bloodbound.loadout.search"));
        searchBox.setHint(Component.translatable("bloodbound.loadout.search").withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setMaxLength(48);
        searchBox.setBordered(true);
        searchBox.setValue(previous);
        searchBox.setResponder(value -> loadoutTab.onSearchChanged());
        addRenderableWidget(searchBox);
    }

    /** What the player has typed into the search box, lower-cased and trimmed. */
    String searchQuery() {
        return searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
    }

    // --- layout ---

    int contentX() {
        return leftPos + 8;
    }

    int contentY() {
        return topPos + HEADER_HEIGHT + TAB_HEIGHT + 6;
    }

    int contentWidth() {
        return imageWidth - 16;
    }

    int contentHeight() {
        return imageHeight - HEADER_HEIGHT - TAB_HEIGHT - 14;
    }

    /** How many soul shards the player is carrying, read straight from the client inventory. */
    int shardCount() {
        if (minecraft == null || minecraft.player == null) {
            return 0;
        }
        int total = 0;
        Inventory inventory = minecraft.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(ModItems.SOUL_SHARD.get())) {
                total += stack.getCount();
            }
        }
        return total;
    }

    // --- rendering ---

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, COLOR_PANEL);
        GuiUtil.drawBorder(graphics, leftPos, topPos, imageWidth, imageHeight, COLOR_BORDER);
        graphics.fill(contentX(), contentY(), contentX() + contentWidth(), contentY() + contentHeight(),
                COLOR_PANEL_INNER);
    }

    /** The vanilla title and "Inventory" labels would sit in the wrong places here. */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The box belongs to the loadout tab alone, and a hidden widget takes no clicks or keys.
        if (searchBox != null) {
            searchBox.visible = activeTab == Tab.LOADOUT;
            if (!searchBox.visible) {
                searchBox.setFocused(false);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);

        renderHeader(graphics);
        renderTabs(graphics, mouseX, mouseY);

        switch (activeTab) {
            case LOADOUT -> loadoutTab.render(graphics, mouseX, mouseY, partialTick);
            case ADDONS -> addonsTab.render(graphics, mouseX, mouseY, partialTick);
            case SOULWEB -> soulwebTab.render(graphics, mouseX, mouseY, partialTick);
        }

        // Tooltips last so nothing draws over them.
        switch (activeTab) {
            case LOADOUT -> loadoutTab.renderTooltip(graphics, mouseX, mouseY);
            case ADDONS -> addonsTab.renderTooltip(graphics, mouseX, mouseY);
            case SOULWEB -> soulwebTab.renderTooltip(graphics, mouseX, mouseY);
        }
    }

    private void renderHeader(GuiGraphics graphics) {
        graphics.drawString(font, title, leftPos + 9, topPos + 8, COLOR_TEXT, false);

        ItemStack shard = new ItemStack(ModItems.SOUL_SHARD.get());
        String count = Integer.toString(shardCount());
        int countWidth = font.width(count);
        int shardX = leftPos + imageWidth - 12 - countWidth - 18;

        graphics.renderItem(shard, shardX, topPos + 4);
        graphics.drawString(font, count, shardX + 20, topPos + 8, COLOR_TEXT, false);
    }

    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        for (Tab tab : Tab.values()) {
            int x = tabX(tab);
            int y = topPos + HEADER_HEIGHT;
            boolean selected = tab == activeTab;
            boolean hovered = isOverTab(tab, mouseX, mouseY);

            int background = selected ? COLOR_ACCENT : (hovered ? 0xFF3A1018 : COLOR_SLOT);
            graphics.fill(x, y, x + tabWidth(), y + TAB_HEIGHT, background);
            GuiUtil.drawBorder(graphics, x, y, tabWidth(), TAB_HEIGHT, COLOR_BORDER);
            graphics.drawCenteredString(font, Component.translatable(tab.translationKey),
                    x + tabWidth() / 2, y + 5, selected ? 0xFFFFFFFF : COLOR_TEXT_DIM);
        }
    }

    private int tabWidth() {
        return (imageWidth - 16 - 4) / Tab.values().length;
    }

    private int tabX(Tab tab) {
        return leftPos + 8 + tab.ordinal() * (tabWidth() + 4);
    }

    private boolean isOverTab(Tab tab, int mouseX, int mouseY) {
        int x = tabX(tab);
        int y = topPos + HEADER_HEIGHT;
        return mouseX >= x && mouseX < x + tabWidth() && mouseY >= y && mouseY < y + TAB_HEIGHT;
    }

    // --- input ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Tab tab : Tab.values()) {
            if (isOverTab(tab, (int) mouseX, (int) mouseY)) {
                if (activeTab != tab) {
                    activeTab = tab;
                    if (minecraft != null) {
                        minecraft.getSoundManager().play(
                                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    }
                }
                return true;
            }
        }

        boolean handled = switch (activeTab) {
            case LOADOUT -> loadoutTab.mouseClicked(mouseX, mouseY, button);
            case ADDONS -> addonsTab.mouseClicked(mouseX, mouseY, button);
            case SOULWEB -> soulwebTab.mouseClicked(mouseX, mouseY, button);
        };

        return handled || super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * While the search box has focus it swallows every key but Escape, so typing an "e" filters the
     * list instead of closing the table.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox != null && searchBox.visible && searchBox.isFocused()) {
            if (searchBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (activeTab == Tab.LOADOUT && loadoutTab.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (activeTab == Tab.ADDONS && addonsTab.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private enum Tab {
        LOADOUT("bloodbound.tab.loadout"),
        ADDONS("bloodbound.tab.addons"),
        SOULWEB("bloodbound.tab.soulweb");

        private final String translationKey;

        Tab(String translationKey) {
            this.translationKey = translationKey;
        }
    }
}
