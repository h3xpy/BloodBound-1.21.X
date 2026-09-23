package net.h3xpy.bloodbound.advancement;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.perk.Addon;
import net.h3xpy.bloodbound.perk.AddonRarity;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The mod's advancements, and the moments that earn them.
 * <p>
 * Each one is a datapack advancement whose only criterion is {@code impossible}, so nothing grants
 * them by accident — they are handed out from here, when the soulweb pays out.
 */
public final class ModAdvancements {

    /** The criterion every one of them is built on. */
    private static final String CRITERION = "granted";

    private static final ResourceLocation ROOT = id("root");
    private static final ResourceLocation TIER1_PERK = id("tier1_perk");
    private static final ResourceLocation TIER2_PERK = id("tier2_perk");
    private static final ResourceLocation TIER3_PERK = id("tier3_perk");
    private static final ResourceLocation FIRST_ADDON = id("first_addon");
    private static final ResourceLocation UNSTABLE_ADDON = id("unstable_addon");

    private ModAdvancements() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, path);
    }

    /** A perk bought off the web, at the tier it was bought at. */
    public static void onPerkBought(ServerPlayer player, int tier) {
        award(player, ROOT);
        award(player, switch (tier) {
            case 1 -> TIER1_PERK;
            case 2 -> TIER2_PERK;
            default -> TIER3_PERK;
        });
    }

    /** An addon bought off the web. An unstable one is worth a second line of its own. */
    public static void onAddonBought(ServerPlayer player, Addon addon) {
        award(player, ROOT);
        award(player, FIRST_ADDON);
        if (addon.rarity() == AddonRarity.UNSTABLE) {
            award(player, UNSTABLE_ADDON);
        }
    }

    /** Hands one over. Already-earned advancements ignore this, so it is safe to call every time. */
    private static void award(ServerPlayer player, ResourceLocation id) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        AdvancementHolder advancement = server.getAdvancements().get(id);
        if (advancement != null) {
            player.getAdvancements().award(advancement, CRITERION);
        }
    }
}
