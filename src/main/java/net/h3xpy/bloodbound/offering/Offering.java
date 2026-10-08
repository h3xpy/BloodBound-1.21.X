package net.h3xpy.bloodbound.offering;

import net.h3xpy.bloodbound.perk.AddonRarity;

/**
 * What one kind of offering does when it burns, which happens when the player finishes the soulweb
 * level it was laid for; everything here applies to the level that follows, and only that one.
 * Offerings share the addons' five rarities.
 *
 * @param discountPercent        every price on the next level is cut by this much
 * @param upgradeDiscountPercent and every Tier II or III perk by this much more
 * @param extraPerks             this many more perk nodes than the web would have rolled
 * @param upgradesOnly           every perk node offers the next tier of a perk the player owns
 * @param refundPercent          finishing the next level pays back this share of its last purchase
 * @param preservesNext          the next offering laid on the table is not used up when it burns
 * @param marks                  what the offering is marked with: perks, addons, or nothing
 * @param markCount              how many it is marked with
 * @param guaranteed             how many of those the next level is sure to offer
 * @param minAddonRarity         the lowest rarity an addon mark can have
 * @param compensationShards     paid instead when none of the marks can be offered any more
 */
public record Offering(AddonRarity rarity, int discountPercent, int upgradeDiscountPercent, int extraPerks,
        boolean upgradesOnly, int refundPercent, boolean preservesNext, Marks marks, int markCount,
        int guaranteed, AddonRarity minAddonRarity, int compensationShards) {

    /** What an offering is marked with, if anything. */
    public enum Marks {
        NONE,
        PERKS,
        ADDONS
    }

    public static Offering discount(AddonRarity rarity, int percent) {
        return new Offering(rarity, percent, 0, 0, false, 0, false, Marks.NONE, 0, 0, AddonRarity.COMMON, 0);
    }

    public static Offering upgradeDiscount(AddonRarity rarity, int percent) {
        return new Offering(rarity, 0, percent, 0, false, 0, false, Marks.NONE, 0, 0, AddonRarity.COMMON, 0);
    }

    public static Offering extraPerks(AddonRarity rarity, int count) {
        return new Offering(rarity, 0, 0, count, false, 0, false, Marks.NONE, 0, 0, AddonRarity.COMMON, 0);
    }

    public static Offering upgradesOnly(AddonRarity rarity) {
        return new Offering(rarity, 0, 0, 0, true, 0, false, Marks.NONE, 0, 0, AddonRarity.COMMON, 0);
    }

    public static Offering refund(AddonRarity rarity, int percent) {
        return new Offering(rarity, 0, 0, 0, false, percent, false, Marks.NONE, 0, 0, AddonRarity.COMMON, 0);
    }

    public static Offering preserveNext(AddonRarity rarity) {
        return new Offering(rarity, 0, 0, 0, false, 0, true, Marks.NONE, 0, 0, AddonRarity.COMMON, 0);
    }

    public static Offering markedPerks(AddonRarity rarity, int marks, int guaranteed, int compensation) {
        return new Offering(rarity, 0, 0, 0, false, 0, false, Marks.PERKS, marks, guaranteed, AddonRarity.COMMON,
                compensation);
    }

    public static Offering markedAddons(AddonRarity rarity, int marks, int guaranteed, AddonRarity minRarity,
            int compensation) {
        return new Offering(rarity, 0, 0, 0, false, 0, false, Marks.ADDONS, marks, guaranteed, minRarity,
                compensation);
    }
}
