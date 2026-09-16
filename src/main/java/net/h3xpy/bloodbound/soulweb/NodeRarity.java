package net.h3xpy.bloodbound.soulweb;

import net.minecraft.ChatFormatting;

/**
 * Display grade of a soulweb node: the colour of its ring and the label in its tooltip. Prices
 * come from the reward itself, not from here.
 */
public enum NodeRarity {
    COMMON("common", ChatFormatting.GRAY, 0xFFB0B0B0),
    UNCOMMON("uncommon", ChatFormatting.YELLOW, 0xFFE7C74A),
    RARE("rare", ChatFormatting.GREEN, 0xFF4CC14C),
    VERY_RARE("very_rare", ChatFormatting.LIGHT_PURPLE, 0xFFB44CE0),
    ULTRA_RARE("ultra_rare", ChatFormatting.RED, 0xFFE04C4C);

    private final String name;
    private final ChatFormatting textColor;
    private final int ringColor;

    NodeRarity(String name, ChatFormatting textColor, int ringColor) {
        this.name = name;
        this.textColor = textColor;
        this.ringColor = ringColor;
    }

    public String getSerializedName() {
        return name;
    }

    public ChatFormatting textColor() {
        return textColor;
    }

    public int ringColor() {
        return ringColor;
    }

    public String translationKey() {
        return "bloodbound.rarity." + name;
    }

    public static NodeRarity byName(String name) {
        for (NodeRarity rarity : values()) {
            if (rarity.name.equals(name)) {
                return rarity;
            }
        }
        return COMMON;
    }
}
