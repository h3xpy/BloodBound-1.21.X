package net.h3xpy.bloodbound.client.screen;

import java.util.ArrayList;
import java.util.List;

import net.h3xpy.bloodbound.soulweb.Soulweb;
import net.h3xpy.bloodbound.soulweb.SoulwebNode;
import net.h3xpy.bloodbound.soulweb.NodeReward;
import net.h3xpy.bloodbound.client.ClientPerkData;
import net.h3xpy.bloodbound.network.PurchaseNodePayload;
import net.h3xpy.bloodbound.perk.Addon;
import net.h3xpy.bloodbound.perk.AddonRegistry;
import net.h3xpy.bloodbound.perk.Perk;
import net.h3xpy.bloodbound.perk.PerkRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Soulweb tab: the spokes of buyable nodes.
 * <p>
 * Nothing is committed until the first purchase; from then on every other branch greys out, and the
 * web rerolls once the chosen branch is fully bought.
 */
public class SoulwebTab {

    private static final int NODE_RADIUS = 12;
    private static final int CENTER_RADIUS = 9;

    /** Drawn under every link so the paths stand off the panel. */
    private static final int COLOR_LINK_OUTLINE = 0xFF0A0507;
    private static final int COLOR_LINK_IDLE = 0xFF5E2C36;
    private static final int COLOR_LINK_TAKEN = 0xFFB4232F;
    private static final int COLOR_LINK_BLOCKED = 0xFF1C1416;
    private static final int COLOR_NODE_FILL = 0xFF241016;
    private static final int COLOR_NODE_BLOCKED = 0xFF140E10;
    private static final int COLOR_NODE_TAKEN = 0xFF3A1018;

    /** How many separation passes to run. Cheap at this node count, and this many settle the layout. */
    private static final int RELAX_PASSES = 40;
    /** Closest two node centres may sit before they get pushed apart. */
    private static final int MIN_SEPARATION = NODE_RADIUS * 2 + 10;

    /** Where the innermost ring sits, as a fraction of the reach towards the edge of the panel. */
    private static final double INNER_RING = 0.38D;
    /** How much of the ellipse and how much of the rectangle {@link #reachTowards} blends. */
    private static final double ELLIPSE_WEIGHT = 0.4D;

    private final PerkTableScreen screen;

    /** Screen position of each node, indexed alongside the web's node list. */
    private int[][] positions = new int[0][];

    public SoulwebTab(PerkTableScreen screen) {
        this.screen = screen;
    }

    public void onScreenResized() {
        // Positions are recomputed from the screen bounds on the next frame.
        positions = new int[0][];
    }

    // --- layout ---

    private int centerX() {
        return screen.contentX() + screen.contentWidth() / 2;
    }

    private int centerY() {
        return screen.contentY() + screen.contentHeight() / 2 - 4;
    }

    /**
     * The web is laid out on an ellipse rather than a circle: the content area is far wider than it
     * is tall, and spreading the spokes sideways buys a lot of room between the paths.
     */
    private double radiusX() {
        return screen.contentWidth() / 2.0D - NODE_RADIUS - 4;
    }

    private double radiusY() {
        // Extra room at the bottom for the price label printed under each node.
        return screen.contentHeight() / 2.0D - NODE_RADIUS - 12;
    }

    /**
     * How far the outermost ring may sit in a given direction: halfway between the ellipse
     * inscribed in the content area and the content area itself. A pure ellipse leaves the four
     * corners of the panel empty and crams everything into a band across the middle, which is
     * exactly what makes a crowded web unreadable.
     */
    private double reachTowards(double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double ellipse = 1.0D / Math.sqrt(square(cos / radiusX()) + square(sin / radiusY()));
        double rectangle = 1.0D / Math.max(Math.abs(cos) / radiusX(), Math.abs(sin) / radiusY());
        return ellipse * ELLIPSE_WEIGHT + rectangle * (1.0D - ELLIPSE_WEIGHT);
    }

    /**
     * Where a node's ring sits along its spoke. Worked out from the depth rather than read from the
     * node so that the rings always use the full height of the panel, however deep the web goes and
     * whenever it was generated.
     */
    private static double ringFraction(int depth, int maxDepth) {
        if (maxDepth <= 1) {
            return 0.66D;
        }
        return INNER_RING + (1.0D - INNER_RING) * (depth - 1) / (maxDepth - 1);
    }

    private static double square(double value) {
        return value * value;
    }

    private int nodeX(int index) {
        return positions[index][0];
    }

    private int nodeY(int index) {
        return positions[index][1];
    }

    /**
     * Places every node, then pushes overlapping ones apart.
     * <p>
     * The ellipse squashes the web vertically, so two spokes that are comfortably apart by angle can
     * still land on top of each other on screen. A few relaxation passes fix that far more reliably
     * than trying to pick angles that never collide.
     */
    private void resolvePositions(Soulweb web) {
        int count = web.nodes().size();
        positions = new int[count][2];

        int maxDepth = 1;
        for (SoulwebNode node : web.nodes()) {
            maxDepth = Math.max(maxDepth, node.depth());
        }

        double[] x = new double[count];
        double[] y = new double[count];
        for (int i = 0; i < count; i++) {
            SoulwebNode node = web.nodes().get(i);
            double reach = reachTowards(node.angle()) * ringFraction(node.depth(), maxDepth);
            x[i] = centerX() + Math.cos(node.angle()) * reach;
            y[i] = centerY() + Math.sin(node.angle()) * reach;
        }

        int minX = screen.contentX() + NODE_RADIUS + 2;
        int maxX = screen.contentX() + screen.contentWidth() - NODE_RADIUS - 2;
        // Clear of the web's level label at the top.
        int minY = screen.contentY() + NODE_RADIUS + 14;
        // Room under the lowest node for its price label, and for the hint line below that.
        int maxY = screen.contentY() + screen.contentHeight() - NODE_RADIUS - 18;

        // Nothing may creep onto the hub in the middle.
        double hubClearance = CENTER_RADIUS + NODE_RADIUS + 4;

        for (int pass = 0; pass < RELAX_PASSES; pass++) {
            for (int a = 0; a < count; a++) {
                for (int b = a + 1; b < count; b++) {
                    double dx = x[b] - x[a];
                    double dy = y[b] - y[a];
                    double distance = Math.sqrt(dx * dx + dy * dy);

                    if (distance >= MIN_SEPARATION) {
                        continue;
                    }
                    // Perfectly coincident nodes have no direction to separate along; nudge sideways.
                    if (distance < 0.001D) {
                        dx = 1.0D;
                        dy = 0.0D;
                        distance = 1.0D;
                    }
                    double push = (MIN_SEPARATION - distance) / 2.0D;
                    double nx = dx / distance * push;
                    double ny = dy / distance * push;
                    x[a] -= nx;
                    y[a] -= ny;
                    x[b] += nx;
                    y[b] += ny;
                }
            }
            for (int i = 0; i < count; i++) {
                double dx = x[i] - centerX();
                double dy = y[i] - centerY();
                double distance = Math.sqrt(dx * dx + dy * dy);
                if (distance < hubClearance) {
                    if (distance < 0.001D) {
                        dx = 0.0D;
                        dy = -1.0D;
                        distance = 1.0D;
                    }
                    x[i] = centerX() + dx / distance * hubClearance;
                    y[i] = centerY() + dy / distance * hubClearance;
                }
                x[i] = Math.clamp(x[i], minX, maxX);
                y[i] = Math.clamp(y[i], minY, maxY);
            }
        }

        for (int i = 0; i < count; i++) {
            positions[i][0] = (int) Math.round(x[i]);
            positions[i][1] = (int) Math.round(y[i]);
        }
    }

    /** True once positions have been worked out for the web currently on screen. */
    private boolean hasPositionsFor(Soulweb web) {
        return positions.length == web.nodes().size();
    }

    // --- rendering ---

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        Soulweb web = ClientPerkData.get().soulweb();

        if (web == null || web.nodes().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("bloodbound.soulweb.loading"),
                    centerX(), centerY(), PerkTableScreen.COLOR_TEXT_DIM);
            return;
        }

        resolvePositions(web);

        graphics.drawString(font, Component.translatable("bloodbound.soulweb.level", web.level()),
                screen.contentX() + 4, screen.contentY() + 5, PerkTableScreen.COLOR_TEXT_DIM, false);

        renderLinks(graphics, web);
        renderCenter(graphics, web);

        for (int i = 0; i < web.nodes().size(); i++) {
            renderNode(graphics, web, i, mouseX, mouseY);
        }

        graphics.drawCenteredString(font,
                Component.translatable(web.chosenBranch() == Soulweb.NO_BRANCH
                        ? "bloodbound.soulweb.hint_open"
                        : "bloodbound.soulweb.hint_committed").withStyle(ChatFormatting.DARK_GRAY),
                screen.contentX() + screen.contentWidth() / 2,
                screen.contentY() + screen.contentHeight() - 11,
                PerkTableScreen.COLOR_TEXT_DIM);
    }

    private void renderLinks(GuiGraphics graphics, Soulweb web) {
        for (int i = 0; i < web.nodes().size(); i++) {
            SoulwebNode node = web.nodes().get(i);
            int color = linkColor(web, i, node);

            int fromX = centerX();
            int fromY = centerY();
            if (node.depth() > 1) {
                int parent = parentIndexOf(web, node);
                if (parent < 0) {
                    continue;
                }
                fromX = nodeX(parent);
                fromY = nodeY(parent);
            }

            // A dark stroke under a brighter one, so the path reads clearly against the panel.
            GuiUtil.drawLine(graphics, fromX, fromY, nodeX(i), nodeY(i), 5, COLOR_LINK_OUTLINE);
            GuiUtil.drawLine(graphics, fromX, fromY, nodeX(i), nodeY(i), 3, color);
        }
    }

    private int linkColor(Soulweb web, int index, SoulwebNode node) {
        if (web.isBlocked(index)) {
            return COLOR_LINK_BLOCKED;
        }
        return node.isPurchased() ? COLOR_LINK_TAKEN : COLOR_LINK_IDLE;
    }

    /** Index of the node one step closer to the centre on the same branch, or -1. */
    private int parentIndexOf(Soulweb web, SoulwebNode node) {
        for (int i = 0; i < web.nodes().size(); i++) {
            SoulwebNode candidate = web.nodes().get(i);
            if (candidate.branch() == node.branch() && candidate.depth() == node.depth() - 1) {
                return i;
            }
        }
        return -1;
    }

    private void renderCenter(GuiGraphics graphics, Soulweb web) {
        GuiUtil.fillCircle(graphics, centerX(), centerY(), CENTER_RADIUS, COLOR_NODE_TAKEN);
        GuiUtil.drawRing(graphics, centerX(), centerY(), CENTER_RADIUS, CENTER_RADIUS - 2,
                PerkTableScreen.COLOR_ACCENT);
    }

    private void renderNode(GuiGraphics graphics, Soulweb web, int index, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        SoulwebNode node = web.nodes().get(index);
        int x = nodeX(index);
        int y = nodeY(index);

        boolean purchased = node.isPurchased();
        boolean blocked = web.isBlocked(index);
        boolean unlockable = web.isUnlockable(index);
        boolean affordable = screen.shardCount() >= node.cost();
        boolean hovered = isOverNode(index, mouseX, mouseY);

        int fill = purchased ? COLOR_NODE_TAKEN : (blocked ? COLOR_NODE_BLOCKED : COLOR_NODE_FILL);
        int ring = node.reward().ringColor();
        if (blocked) {
            ring = 0xFF2A2426;
        } else if (!unlockable && !purchased) {
            ring = GuiUtil.darken(ring, 0.55F);
        }

        GuiUtil.fillCircle(graphics, x, y, NODE_RADIUS, fill);

        // A slow pulse marks the nodes that can actually be bought right now.
        if (unlockable && affordable) {
            // Wrap the clock first: milliseconds since the epoch lose too much precision as a float.
            float pulse = (Mth.sin((System.currentTimeMillis() % 100_000L) / 220.0F) + 1.0F) * 0.5F;
            GuiUtil.drawRing(graphics, x, y, NODE_RADIUS + 2, NODE_RADIUS,
                    GuiUtil.withAlpha(ring, 60 + (int) (pulse * 120)));
        }
        GuiUtil.drawRing(graphics, x, y, NODE_RADIUS, NODE_RADIUS - 2, hovered ? 0xFFFFFFFF : ring);

        renderNodeContent(graphics, node, x, y, blocked || (!unlockable && !purchased));

        if (purchased) {
            graphics.drawCenteredString(font, "✓", x + 9, y + 6, 0xFF6BD46B);
        } else if (!blocked) {
            int costColor = affordable ? 0xFFE8DCDC : 0xFFCC5A5A;
            graphics.drawCenteredString(font, Integer.toString(node.cost()), x, y + NODE_RADIUS + 2, costColor);
        }
    }

    private void renderNodeContent(GuiGraphics graphics, SoulwebNode node, int x, int y, boolean dimmed) {
        if (node.reward() instanceof NodeReward.ItemReward itemReward) {
            graphics.renderItem(itemReward.stack(), x - 8, y - 8);
            dim(graphics, x, y, dimmed);
            return;
        }
        if (node.reward() instanceof NodeReward.XpReward) {
            // Borrowing the bottle o' enchanting icon keeps experience nodes readable at a glance
            // without shipping another sprite.
            graphics.renderItem(new ItemStack(Items.EXPERIENCE_BOTTLE), x - 8, y - 8);
            dim(graphics, x, y, dimmed);
            return;
        }
        if (node.reward() instanceof NodeReward.PerkReward perkReward) {
            Perk perk = PerkRegistry.get(perkReward.perkId());
            if (perk != null) {
                graphics.blitSprite(perk.icon(perkReward.tier()), x - 8, y - 8, 16, 16);
                dim(graphics, x, y, dimmed);
            }
            return;
        }
        if (node.reward() instanceof NodeReward.AddonReward addonReward) {
            Addon addon = AddonRegistry.get(addonReward.addonId());
            if (addon != null) {
                graphics.blitSprite(addon.icon(), x - 8, y - 8, 16, 16);
                dim(graphics, x, y, dimmed);
            }
        }
    }

    /** Greys out a node's contents when it cannot be bought. */
    private static void dim(GuiGraphics graphics, int x, int y, boolean dimmed) {
        if (dimmed) {
            graphics.fill(x - 8, y - 8, x + 8, y + 8, 0x80000000);
        }
    }

    // --- tooltips ---

    public void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        Soulweb web = ClientPerkData.get().soulweb();
        if (web == null) {
            return;
        }
        int index = nodeAt(mouseX, mouseY);
        if (index < 0) {
            return;
        }
        SoulwebNode node = web.nodes().get(index);
        List<FormattedCharSequence> lines = new ArrayList<>();

        GuiUtil.addLine(lines, node.reward().displayName().copy().withStyle(ChatFormatting.WHITE));
        GuiUtil.addLine(lines, node.reward().rarityName());

        if (node.reward() instanceof NodeReward.PerkReward perkReward) {
            Perk perk = PerkRegistry.get(perkReward.perkId());
            if (perk != null) {
                GuiUtil.addLine(lines, Component.empty());
                GuiUtil.addWrapped(lines, perk.description(perkReward.tier()).copy().withStyle(ChatFormatting.GRAY));
            }
        } else if (node.reward() instanceof NodeReward.AddonReward addonReward) {
            Addon addon = AddonRegistry.get(addonReward.addonId());
            if (addon != null) {
                Perk parent = PerkRegistry.get(addon.perkId());
                if (parent != null) {
                    GuiUtil.addLine(lines, Component.translatable("bloodbound.addon.for_perk", parent.displayName())
                            .withStyle(ChatFormatting.DARK_AQUA));
                }
                GuiUtil.addLine(lines, Component.empty());
                GuiUtil.addWrapped(lines, addon.description().copy().withStyle(ChatFormatting.GRAY));
            }
        }

        GuiUtil.addLine(lines, Component.empty());
        if (node.isPurchased()) {
            GuiUtil.addLine(lines, Component.translatable("bloodbound.soulweb.purchased").withStyle(ChatFormatting.GREEN));
        } else if (web.isBlocked(index)) {
            GuiUtil.addLine(lines, Component.translatable("bloodbound.soulweb.blocked").withStyle(ChatFormatting.DARK_GRAY));
        } else if (!web.isUnlockable(index)) {
            GuiUtil.addLine(lines, Component.translatable("bloodbound.soulweb.locked").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            boolean affordable = screen.shardCount() >= node.cost();
            GuiUtil.addLine(lines, Component.translatable("bloodbound.soulweb.cost", node.cost())
                    .withStyle(affordable ? ChatFormatting.YELLOW : ChatFormatting.RED));
            if (affordable) {
                GuiUtil.addLine(lines, Component.translatable("bloodbound.soulweb.hint_buy").withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        graphics.renderTooltip(Minecraft.getInstance().font, lines, mouseX, mouseY);
    }

    // --- input ---

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Soulweb web = ClientPerkData.get().soulweb();
        if (web == null || button != 0) {
            return false;
        }
        int index = nodeAt((int) mouseX, (int) mouseY);
        if (index < 0 || !web.isUnlockable(index)) {
            return false;
        }
        PacketDistributor.sendToServer(new PurchaseNodePayload(index));
        return true;
    }

    // --- hit testing ---

    private int nodeAt(int mouseX, int mouseY) {
        Soulweb web = ClientPerkData.get().soulweb();
        // Positions come from the last frame drawn, so a click before the first render finds nothing.
        if (web == null || !hasPositionsFor(web)) {
            return -1;
        }
        for (int i = 0; i < web.nodes().size(); i++) {
            if (isOverNode(i, mouseX, mouseY)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isOverNode(int index, int mouseX, int mouseY) {
        int dx = mouseX - nodeX(index);
        int dy = mouseY - nodeY(index);
        return dx * dx + dy * dy <= NODE_RADIUS * NODE_RADIUS;
    }
}
