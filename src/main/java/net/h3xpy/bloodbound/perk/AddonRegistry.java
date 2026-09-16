package net.h3xpy.bloodbound.perk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;

/**
 * Static registry of every addon, in declaration order so the perk table lists them consistently.
 */
public final class AddonRegistry {
    private static final Map<ResourceLocation, Addon> ADDONS = new LinkedHashMap<>();

    private AddonRegistry() {}

    static Addon register(Addon addon) {
        if (ADDONS.putIfAbsent(addon.id(), addon) != null) {
            throw new IllegalStateException("Duplicate addon id: " + addon.id());
        }
        return addon;
    }

    @Nullable
    public static Addon get(@Nullable ResourceLocation id) {
        return id == null ? null : ADDONS.get(id);
    }

    public static Collection<Addon> all() {
        return Collections.unmodifiableCollection(ADDONS.values());
    }

    /** Every addon attached to the given perk. */
    public static List<Addon> forPerk(ResourceLocation perkId) {
        List<Addon> matches = new ArrayList<>();
        for (Addon addon : ADDONS.values()) {
            if (addon.perkId().equals(perkId)) {
                matches.add(addon);
            }
        }
        return matches;
    }
}
