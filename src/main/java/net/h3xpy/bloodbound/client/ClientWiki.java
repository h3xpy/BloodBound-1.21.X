package net.h3xpy.bloodbound.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.h3xpy.bloodbound.network.WikiPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The client's copy of what the wiki tab needs from the server: stars for every perk and addon, and
 * the advancements the player has earned. Refreshed whenever a Perk Table is opened or a reward is
 * claimed.
 */
public final class ClientWiki {

    private static final Map<ResourceLocation, Integer> PERK_STARS = new HashMap<>();
    private static final Map<ResourceLocation, Integer> ADDON_STARS = new HashMap<>();
    private static final Set<ResourceLocation> EARNED = new HashSet<>();

    private ClientWiki() {}

    static void set(WikiPayload payload) {
        PERK_STARS.clear();
        PERK_STARS.putAll(payload.perkStars());
        ADDON_STARS.clear();
        ADDON_STARS.putAll(payload.addonStars());
        EARNED.clear();
        EARNED.addAll(payload.earned());
    }

    public static int perkStars(ResourceLocation perkId) {
        return PERK_STARS.getOrDefault(perkId, 1);
    }

    public static int addonStars(ResourceLocation addonId) {
        return ADDON_STARS.getOrDefault(addonId, 1);
    }

    public static boolean isEarned(ResourceLocation advancementId) {
        return EARNED.contains(advancementId);
    }

    public static void reset() {
        PERK_STARS.clear();
        ADDON_STARS.clear();
        EARNED.clear();
    }
}
