package net.h3xpy.bloodbound.soulweb;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;

/**
 * How the offering burnt for a level shapes that level as it is rolled.
 *
 * @param offering               item id of that offering, kept on the web so the screen can show it
 * @param discountPercent        cut off every price
 * @param upgradeDiscountPercent cut off Tier II and III perks on top of that
 * @param extraPerks             perk nodes on top of what the web would have rolled
 * @param upgradesOnly           perk nodes only offer the next tier of perks the player owns
 * @param refundPercent          share of the level's last purchase paid back when it is finished
 * @param forcedPerks            perks the level is sure to offer the next tier of
 * @param forcedAddons           addons the level is sure to offer
 * @param compensationShards     paid because none of the offering's marks could be offered
 */
public record WebModifiers(@Nullable ResourceLocation offering, int discountPercent, int upgradeDiscountPercent,
        int extraPerks, boolean upgradesOnly, int refundPercent, List<ResourceLocation> forcedPerks,
        List<ResourceLocation> forcedAddons, int compensationShards) {

    public static final WebModifiers NONE = new WebModifiers(null, 0, 0, 0, false, 0, List.of(), List.of(), 0);
}
