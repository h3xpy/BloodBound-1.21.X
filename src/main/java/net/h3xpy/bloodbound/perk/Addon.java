package net.h3xpy.bloodbound.perk;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A bonus bolted onto one specific perk. Addons are bought on the soulweb and kept for good, but
 * only one can be equipped at a time across the whole loadout, and it only does anything while its
 * parent perk is equipped.
 */
public class Addon {
    private final ResourceLocation id;
    private final ResourceLocation perkId;
    private final AddonRarity rarity;
    private final ResourceLocation icon;

    private Addon(String path, Perk perk, AddonRarity rarity) {
        this.id = ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, path);
        this.perkId = perk.id();
        this.rarity = rarity;
        // GUI atlas sprite id; the PNG lives at assets/<ns>/textures/gui/sprites/addon/<id>.png
        this.icon = ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "addon/" + path);
    }

    public static Addon of(String path, Perk perk, AddonRarity rarity) {
        return new Addon(path, perk, rarity);
    }

    public ResourceLocation id() {
        return id;
    }

    /** The one perk this addon attaches to. */
    public ResourceLocation perkId() {
        return perkId;
    }

    public AddonRarity rarity() {
        return rarity;
    }

    /** GUI atlas sprite id, for {@code GuiGraphics.blitSprite}. */
    public ResourceLocation icon() {
        return icon;
    }

    public String translationKey() {
        return "addon." + id.getNamespace() + "." + id.getPath();
    }

    public Component displayName() {
        return Component.translatable(translationKey());
    }

    public Component description() {
        return Component.translatable(translationKey() + ".desc");
    }

    @Override
    public String toString() {
        return "Addon[" + id + "]";
    }
}
