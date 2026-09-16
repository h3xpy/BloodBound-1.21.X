package net.h3xpy.bloodbound.soulweb;

import net.h3xpy.bloodbound.perk.Addon;
import net.h3xpy.bloodbound.perk.AddonRarity;
import net.h3xpy.bloodbound.perk.AddonRegistry;
import net.h3xpy.bloodbound.perk.Perk;
import net.h3xpy.bloodbound.perk.PerkRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * What a soulweb node hands over when bought: a perk tier, an addon, a stack of items, or raw
 * experience.
 * <p>
 * Rewards carry their own presentation because perks and loot are graded on the loot scale while
 * addons run on their own. Prices live on the node, since they are rolled at generation time.
 */
public sealed interface NodeReward {

    Component displayName();

    /** Colour of the ring drawn around the node. */
    int ringColor();

    /** Rarity label shown in the node tooltip. */
    Component rarityName();

    CompoundTag save(HolderLookup.Provider provider);

    static NodeReward load(HolderLookup.Provider provider, CompoundTag tag) {
        if (tag.contains("addon")) {
            return new AddonReward(ResourceLocation.tryParse(tag.getString("addon")));
        }
        NodeRarity rarity = NodeRarity.byName(tag.getString("rarity"));
        if (tag.contains("perk")) {
            return new PerkReward(ResourceLocation.tryParse(tag.getString("perk")), tag.getInt("tier"), rarity);
        }
        if (tag.contains("xp")) {
            return new XpReward(tag.getInt("xp"), rarity);
        }
        return new ItemReward(ItemStack.parseOptional(provider, tag.getCompound("item")), rarity);
    }

    /** Teaches {@code perkId} at {@code tier}. */
    record PerkReward(ResourceLocation perkId, int tier, NodeRarity rarity) implements NodeReward {
        @Override
        public Component displayName() {
            Perk perk = PerkRegistry.get(perkId);
            Component name = perk != null ? perk.displayName() : Component.literal(String.valueOf(perkId));
            return Component.translatable("bloodbound.soulweb.perk_node", name, romanNumeral(tier));
        }

        @Override
        public int ringColor() {
            return rarity.ringColor();
        }

        @Override
        public Component rarityName() {
            return Component.translatable(rarity.translationKey()).withStyle(rarity.textColor());
        }

        @Override
        public CompoundTag save(HolderLookup.Provider provider) {
            CompoundTag tag = new CompoundTag();
            tag.putString("perk", perkId.toString());
            tag.putInt("tier", tier);
            tag.putString("rarity", rarity.getSerializedName());
            return tag;
        }

        private static String romanNumeral(int tier) {
            return switch (tier) {
                case 1 -> "I";
                case 2 -> "II";
                case 3 -> "III";
                default -> Integer.toString(tier);
            };
        }
    }

    /** Hands the stack straight to the player's inventory. */
    record ItemReward(ItemStack stack, NodeRarity rarity) implements NodeReward {
        @Override
        public Component displayName() {
            if (stack.getCount() > 1) {
                return Component.literal(stack.getCount() + "x ").append(stack.getHoverName());
            }
            return stack.getHoverName();
        }

        @Override
        public int ringColor() {
            return rarity.ringColor();
        }

        @Override
        public Component rarityName() {
            return Component.translatable(rarity.translationKey()).withStyle(rarity.textColor());
        }

        @Override
        public CompoundTag save(HolderLookup.Provider provider) {
            CompoundTag tag = new CompoundTag();
            tag.put("item", stack.saveOptional(provider));
            tag.putString("rarity", rarity.getSerializedName());
            return tag;
        }
    }

    /** Awards raw experience points. */
    record XpReward(int amount, NodeRarity rarity) implements NodeReward {
        @Override
        public Component displayName() {
            return Component.translatable("bloodbound.soulweb.xp_node", amount);
        }

        @Override
        public int ringColor() {
            return rarity.ringColor();
        }

        @Override
        public Component rarityName() {
            return Component.translatable(rarity.translationKey()).withStyle(rarity.textColor());
        }

        @Override
        public CompoundTag save(HolderLookup.Provider provider) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("xp", amount);
            tag.putString("rarity", rarity.getSerializedName());
            return tag;
        }
    }

    /** Unlocks an addon permanently. */
    record AddonReward(ResourceLocation addonId) implements NodeReward {
        private AddonRarity rarity() {
            Addon addon = AddonRegistry.get(addonId);
            return addon != null ? addon.rarity() : AddonRarity.COMMON;
        }

        @Override
        public Component displayName() {
            Addon addon = AddonRegistry.get(addonId);
            return addon != null ? addon.displayName() : Component.literal(String.valueOf(addonId));
        }

        @Override
        public int ringColor() {
            return rarity().ringColor();
        }

        @Override
        public Component rarityName() {
            AddonRarity addonRarity = rarity();
            return Component.translatable("bloodbound.soulweb.addon_rarity",
                    Component.translatable(addonRarity.translationKey())).withStyle(addonRarity.textColor());
        }

        @Override
        public CompoundTag save(HolderLookup.Provider provider) {
            CompoundTag tag = new CompoundTag();
            tag.putString("addon", addonId.toString());
            return tag;
        }
    }
}
