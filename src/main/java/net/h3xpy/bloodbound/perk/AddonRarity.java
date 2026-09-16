package net.h3xpy.bloodbound.perk;

import net.minecraft.ChatFormatting;

/**
 * Rarity of an addon. Sets its base price on the soulweb and how often it turns up when a web is
 * rolled.
 */
public enum AddonRarity {
    COMMON("common", 1, 25, 40, ChatFormatting.GRAY, 0xFFB0B0B0),
    UNCOMMON("uncommon", 3, 75, 30, ChatFormatting.YELLOW, 0xFFE7C74A),
    RARE("rare", 5, 125, 18, ChatFormatting.GREEN, 0xFF4CC14C),
    EPIC("epic", 8, 200, 9, ChatFormatting.LIGHT_PURPLE, 0xFFB44CE0),
    UNSTABLE("unstable", 10, 250, 3, ChatFormatting.RED, 0xFFE04C4C);

    private final String name;
    private final int baseCost;
    private final int finalCost;
    private final int weight;
    private final ChatFormatting textColor;
    private final int ringColor;

    AddonRarity(String name, int baseCost, int finalCost, int weight, ChatFormatting textColor, int ringColor) {
        this.name = name;
        this.baseCost = baseCost;
        this.finalCost = finalCost;
        this.weight = weight;
        this.textColor = textColor;
        this.ringColor = ringColor;
    }

    public String getSerializedName() {
        return name;
    }

    /** Base price of an addon of this rarity, before the web level curve. */
    public int baseCost() {
        return baseCost;
    }

    /** What an addon of this rarity costs once the web level curve has topped out. */
    public int finalCost() {
        return finalCost;
    }

    /** Relative odds of this rarity being picked when an addon node is rolled. */
    public int weight() {
        return weight;
    }

    public ChatFormatting textColor() {
        return textColor;
    }

    public int ringColor() {
        return ringColor;
    }

    public String translationKey() {
        return "bloodbound.addon_rarity." + name;
    }

    public static AddonRarity byName(String name) {
        for (AddonRarity rarity : values()) {
            if (rarity.name.equals(name)) {
                return rarity;
            }
        }
        return COMMON;
    }
}
