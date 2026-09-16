package net.h3xpy.bloodbound.perk;

import java.util.ArrayList;
import java.util.List;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * A single perk definition. Every perk has {@link #MAX_TIER} tiers; each tier is a strictly better
 * version of the previous one. Values that change between tiers are declared through
 * {@link Builder#scaling(double...)} and are rendered in the description as {@code T1/T2/T3},
 * with the tier the player owns highlighted.
 */
public class Perk {
    public static final int MAX_TIER = 3;

    private final ResourceLocation id;
    private final PerkType type;
    private final List<double[]> scalingValues;
    /** One sprite per tier, indexed by tier - 1. Precomputed since the UI asks for these every frame. */
    private final ResourceLocation[] icons;
    private final int cooldownIndex;
    private final int flatCooldownSeconds;
    private final boolean grantsHealing;
    private final boolean grantsMarkVision;

    private Perk(Builder builder) {
        this.id = builder.id;
        this.type = builder.type;
        this.scalingValues = List.copyOf(builder.scalingValues);
        this.cooldownIndex = builder.cooldownIndex;
        this.flatCooldownSeconds = builder.flatCooldownSeconds;
        this.grantsHealing = builder.grantsHealing;
        this.grantsMarkVision = builder.grantsMarkVision;

        // GUI atlas sprite ids, not file paths: the PNGs live at
        // assets/<ns>/textures/gui/sprites/perk/<id>_tier<n>.png, at any square resolution.
        this.icons = new ResourceLocation[MAX_TIER];
        for (int tier = 1; tier <= MAX_TIER; tier++) {
            icons[tier - 1] = ResourceLocation.fromNamespaceAndPath(id.getNamespace(),
                    "perk/" + id.getPath() + "_tier" + tier);
        }
    }

    public static Builder builder(String path) {
        return new Builder(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, path));
    }

    public ResourceLocation id() {
        return id;
    }

    public PerkType type() {
        return type;
    }

    /**
     * GUI atlas sprite id for this perk's icon at the given tier, for {@code GuiGraphics.blitSprite}.
     * Each tier has its own artwork; out-of-range tiers clamp to the nearest real one.
     */
    public ResourceLocation icon(int tier) {
        return icons[Math.clamp(tier, 1, MAX_TIER) - 1];
    }

    /** Raw value of the {@code index}-th scaling entry for the given tier (1-based). */
    public double value(int index, int tier) {
        return scalingValues.get(index)[Math.clamp(tier, 1, MAX_TIER) - 1];
    }

    public int intValue(int index, int tier) {
        return (int) Math.round(value(index, tier));
    }

    /** Convenience: a duration declared in seconds, converted to ticks. */
    public int ticks(int index, int tier) {
        return (int) Math.round(value(index, tier) * 20.0D);
    }

    /** Whether this perk goes on cooldown at all. */
    public boolean hasCooldown() {
        return cooldownIndex >= 0 || flatCooldownSeconds > 0;
    }

    /** Cooldown length in ticks at the given tier, or 0 if the perk has no cooldown. */
    public int cooldownTicks(int tier) {
        if (cooldownIndex >= 0) {
            return ticks(cooldownIndex, tier);
        }
        return flatCooldownSeconds * 20;
    }

    /** Whether equipping this perk unlocks the co-op healing ability. */
    public boolean grantsHealing() {
        return grantsHealing;
    }

    /** Whether equipping this perk lets the player read the marks left on the ground. */
    public boolean grantsMarkVision() {
        return grantsMarkVision;
    }

    public String translationKey() {
        return "perk." + id.getNamespace() + "." + id.getPath();
    }

    public Component displayName() {
        return Component.translatable(translationKey());
    }

    /**
     * The perk description, with every scaling value rendered as {@code T1/T2/T3}.
     *
     * @param highlightTier the tier to emphasise, or 0 to render every tier neutrally
     */
    public Component description(int highlightTier) {
        Object[] args = new Object[scalingValues.size()];
        for (int i = 0; i < scalingValues.size(); i++) {
            args[i] = formatScaling(i, highlightTier);
        }
        return Component.translatable(translationKey() + ".desc", args);
    }

    private Component formatScaling(int index, int highlightTier) {
        MutableComponent out = Component.empty();
        double[] values = scalingValues.get(index);
        for (int tier = 1; tier <= values.length; tier++) {
            if (tier > 1) {
                out.append(Component.literal("/").withStyle(ChatFormatting.DARK_GRAY));
            }
            MutableComponent part = Component.literal(formatNumber(values[tier - 1]));
            out.append(tier == highlightTier
                    ? part.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    : part.withStyle(ChatFormatting.GRAY));
        }
        return out;
    }

    private static String formatNumber(double value) {
        if (value == Math.rint(value)) {
            return Integer.toString((int) value);
        }
        return String.valueOf(value);
    }

    @Override
    public String toString() {
        return "Perk[" + id + "]";
    }

    public static class Builder {
        private final ResourceLocation id;
        private final List<double[]> scalingValues = new ArrayList<>();
        private PerkType type = PerkType.PASSIVE;
        private int cooldownIndex = -1;
        private int flatCooldownSeconds;
        private boolean grantsHealing;
        private boolean grantsMarkVision;

        private Builder(ResourceLocation id) {
            this.id = id;
        }

        public Builder type(PerkType type) {
            this.type = type;
            return this;
        }

        /** Marks which scaling entry holds the cooldown, in seconds. */
        public Builder cooldown(int scalingIndex) {
            this.cooldownIndex = scalingIndex;
            return this;
        }

        /** A cooldown that is the same at every tier, in seconds. */
        public Builder flatCooldown(int seconds) {
            this.flatCooldownSeconds = seconds;
            return this;
        }

        /** Equipping this perk unlocks the co-op healing ability. */
        public Builder grantsHealing() {
            this.grantsHealing = true;
            return this;
        }

        /** Equipping this perk lets the player see the marks left on the ground. */
        public Builder grantsMarkVision() {
            this.grantsMarkVision = true;
            return this;
        }

        /** Declares one value that scales with the tier. Must provide exactly {@link #MAX_TIER} values. */
        public Builder scaling(double... perTier) {
            if (perTier.length != MAX_TIER) {
                throw new IllegalArgumentException(
                        "Perk " + id + " must declare exactly " + MAX_TIER + " values per scaling entry");
            }
            scalingValues.add(perTier);
            return this;
        }

        public Perk build() {
            return new Perk(this);
        }
    }
}
