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

    /**
     * Addons that were renamed after release, old id to new. Saves still carry the old ids; reading
     * them through {@link #migrate} keeps what players had bought.
     */
    private static final Map<ResourceLocation, ResourceLocation> RENAMED = Map.of(
            ResourceLocation.fromNamespaceAndPath("bloodbound", "peekaboo"),
            ResourceLocation.fromNamespaceAndPath("bloodbound", "soul_in_a_jar"),
            ResourceLocation.fromNamespaceAndPath("bloodbound", "now_you_see_me"),
            ResourceLocation.fromNamespaceAndPath("bloodbound", "firefly"));

    /** The current id for an addon id read from a save, which may be one from before a rename. */
    @Nullable
    public static ResourceLocation migrate(@Nullable ResourceLocation id) {
        return id == null ? null : RENAMED.getOrDefault(id, id);
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
