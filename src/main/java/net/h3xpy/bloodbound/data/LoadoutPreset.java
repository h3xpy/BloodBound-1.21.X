package net.h3xpy.bloodbound.data;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * A saved loadout: which perk was in each slot, and the addon each of them had fitted at the time.
 * Loading it puts both back, as far as the player still owns them.
 */
public final class LoadoutPreset {

    private final ResourceLocation[] perks;
    private final Map<ResourceLocation, ResourceLocation> addons;

    private LoadoutPreset(ResourceLocation[] perks, Map<ResourceLocation, ResourceLocation> addons) {
        this.perks = perks;
        this.addons = addons;
    }

    /** A snapshot of the player's loadout as it stands. */
    public static LoadoutPreset of(PlayerPerkData data) {
        ResourceLocation[] perks = Arrays.copyOf(data.loadout(), PlayerPerkData.LOADOUT_SIZE);
        Map<ResourceLocation, ResourceLocation> addons = new LinkedHashMap<>();
        for (ResourceLocation perkId : perks) {
            ResourceLocation addonId = data.equippedAddon(perkId);
            if (perkId != null && addonId != null) {
                addons.put(perkId, addonId);
            }
        }
        return new LoadoutPreset(perks, addons);
    }

    @Nullable
    public ResourceLocation perk(int slot) {
        return slot >= 0 && slot < perks.length ? perks[slot] : null;
    }

    /** The addon that was fitted to this perk when the preset was saved, if any. */
    @Nullable
    public ResourceLocation addon(@Nullable ResourceLocation perkId) {
        return perkId == null ? null : addons.get(perkId);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag slots = new CompoundTag();
        for (int i = 0; i < perks.length; i++) {
            if (perks[i] != null) {
                slots.putString(String.valueOf(i), perks[i].toString());
            }
        }
        tag.put("slots", slots);
        CompoundTag addonTag = new CompoundTag();
        addons.forEach((perkId, addonId) -> addonTag.putString(perkId.toString(), addonId.toString()));
        tag.put("addons", addonTag);
        return tag;
    }

    public static LoadoutPreset load(CompoundTag tag) {
        ResourceLocation[] perks = new ResourceLocation[PlayerPerkData.LOADOUT_SIZE];
        CompoundTag slots = tag.getCompound("slots");
        for (int i = 0; i < perks.length; i++) {
            String key = String.valueOf(i);
            perks[i] = slots.contains(key) ? ResourceLocation.tryParse(slots.getString(key)) : null;
        }
        Map<ResourceLocation, ResourceLocation> addons = new LinkedHashMap<>();
        CompoundTag addonTag = tag.getCompound("addons");
        for (String key : addonTag.getAllKeys()) {
            ResourceLocation perkId = ResourceLocation.tryParse(key);
            ResourceLocation addonId = ResourceLocation.tryParse(addonTag.getString(key));
            if (perkId != null && addonId != null) {
                addons.put(perkId, addonId);
            }
        }
        return new LoadoutPreset(perks, addons);
    }
}
