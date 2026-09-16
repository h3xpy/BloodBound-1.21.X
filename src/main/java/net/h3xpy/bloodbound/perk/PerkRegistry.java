package net.h3xpy.bloodbound.perk;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;

/**
 * Static registry of every perk in the mod. Insertion order is stable so the perk table always
 * lists perks the same way.
 */
public final class PerkRegistry {
    private static final Map<ResourceLocation, Perk> PERKS = new LinkedHashMap<>();

    private PerkRegistry() {}

    static Perk register(Perk perk) {
        if (PERKS.putIfAbsent(perk.id(), perk) != null) {
            throw new IllegalStateException("Duplicate perk id: " + perk.id());
        }
        return perk;
    }

    @Nullable
    public static Perk get(@Nullable ResourceLocation id) {
        return id == null ? null : PERKS.get(id);
    }

    public static Collection<Perk> all() {
        return Collections.unmodifiableCollection(PERKS.values());
    }

    public static int count() {
        return PERKS.size();
    }
}
