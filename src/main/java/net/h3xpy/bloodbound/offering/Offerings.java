package net.h3xpy.bloodbound.offering;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.perk.AddonRarity;
import net.h3xpy.bloodbound.registry.ModItems;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.event.village.WandererTradesEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Where offerings come from, and how often. Every chance lives in this one file so tuning them never
 * means hunting through the code.
 * <p>
 * Sources roll a <em>rarity</em> first, then one offering of that rarity, all of them equally
 * likely; a new offering joins the sources simply by having a rarity. Tables are indexed by rarity:
 * common, uncommon, rare, epic, unstable.
 * <ul>
 *   <li>structure chests, through {@link OfferingLootModifier};</li>
 *   <li>hostile mobs of more than {@link #MOB_MIN_HEALTH} health, killed by a player;</li>
 *   <li>wandering traders, every rarity;</li>
 *   <li>clerics, Dried Flower and Fresh Grass only, and dearer.</li>
 * </ul>
 */
public final class Offerings {

    /** Chance, per structure chest opened, of an offering of each rarity, in percent. One at most. */
    public static final double[] CHEST_CHANCES = { 1.5D, 0.75D, 0.3D, 0.1D, 0.02D };
    /** Chance, per eligible mob killed, of an offering of each rarity, in percent. One at most. */
    public static final double[] MOB_CHANCES = { 3.0D, 1.5D, 0.6D, 0.2D, 0.04D };
    /** Health a hostile mob needs, strictly above, to be able to drop an offering. */
    public static final float MOB_MIN_HEALTH = 50.0F;

    /**
     * Wandering traders. Each trader draws five of its trades out of the common list; two entries
     * are added to it, one for common and uncommon offerings and one for the rest, and each picks
     * what it sells by these weights, in percent of the times it is drawn. Weights adding up to less
     * than 100 mean it often sells nothing, and the trader simply draws another trade instead.
     */
    public static final double[] TRADER_SMALL_WEIGHTS = { 60.0D, 40.0D, 0.0D, 0.0D, 0.0D };
    public static final double[] TRADER_LARGE_WEIGHTS = { 0.0D, 0.0D, 30.0D, 10.0D, 2.0D };
    /** How many of each of those two entries go into the list: three, so roughly three times the odds. */
    public static final int TRADER_LISTING_COPIES = 3;
    /** Emerald prices at the wandering trader, by rarity. */
    public static final int[] TRADER_PRICES = { 6, 12, 24, 40, 64 };
    /** How many times a trader sells the offering before running out. */
    public static final int TRADER_SMALL_USES = 2;
    public static final int TRADER_LARGE_USES = 1;

    /** Clerics sell these two from journeyman level on, at twice the trader's price. */
    public static final int CLERIC_LEVEL = 3;
    public static final double DRIED_FLOWER_CLERIC_WEIGHT = 35.0D;
    public static final double FRESH_GRASS_CLERIC_WEIGHT = 15.0D;
    public static final int DRIED_FLOWER_CLERIC_PRICE = 12;
    public static final int FRESH_GRASS_CLERIC_PRICE = 24;
    public static final int CLERIC_USES = 3;
    public static final int CLERIC_XP = 10;

    private Offerings() {}

    /** Every offering of this rarity. */
    public static List<Item> ofRarity(AddonRarity rarity) {
        List<Item> found = new ArrayList<>();
        for (DeferredItem<OfferingItem> offering : ModItems.OFFERINGS) {
            if (offering.get().offering().rarity() == rarity) {
                found.add(offering.get());
            }
        }
        return found;
    }

    /** One offering of this rarity, every one equally likely, its marks already rolled. */
    @Nullable
    public static ItemStack pick(RandomSource random, AddonRarity rarity) {
        List<Item> candidates = ofRarity(rarity);
        if (candidates.isEmpty()) {
            return null;
        }
        return make(candidates.get(random.nextInt(candidates.size())), random);
    }

    /** A fresh offering stack, marks rolled. */
    public static ItemStack make(Item item, RandomSource random) {
        ItemStack stack = new ItemStack(item);
        OfferingItem.ensureMarked(stack, random);
        return stack;
    }

    /**
     * Rolls a rarity against per-rarity chances in percent, then an offering of it, or nothing. One
     * roll, so two offerings never come out of the same chest or kill.
     */
    @Nullable
    public static ItemStack roll(RandomSource random, double[] chances) {
        AddonRarity rarity = rollRarity(random, chances);
        return rarity == null ? null : pick(random, rarity);
    }

    @Nullable
    private static AddonRarity rollRarity(RandomSource random, double[] weights) {
        double roll = random.nextDouble() * 100.0D;
        AddonRarity[] rarities = AddonRarity.values();
        for (int i = 0; i < weights.length && i < rarities.length; i++) {
            roll -= weights[i];
            if (roll < 0.0D) {
                return rarities[i];
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity mob = event.getEntity();
        Entity killer = event.getSource().getEntity();
        if (!(mob instanceof Enemy) || mob.getMaxHealth() <= MOB_MIN_HEALTH || !(killer instanceof Player)) {
            return;
        }
        ItemStack offering = roll(mob.getRandom(), MOB_CHANCES);
        if (offering != null) {
            event.getDrops().add(new ItemEntity(mob.level(), mob.getX(), mob.getY(), mob.getZ(), offering));
        }
    }

    @SubscribeEvent
    public static void onWandererTrades(WandererTradesEvent event) {
        // Each entry is one more draw at an offering among the trader's five trades, which is what
        // the trader's odds hang on; a trader can end up selling more than one.
        for (int i = 0; i < TRADER_LISTING_COPIES; i++) {
            event.getGenericTrades().add((trader, random) -> {
                AddonRarity rarity = rollRarity(random, TRADER_SMALL_WEIGHTS);
                return rarity == null ? null : offer(pick(random, rarity), TRADER_PRICES[rarity.ordinal()],
                        TRADER_SMALL_USES, 1);
            });
            event.getGenericTrades().add((trader, random) -> {
                AddonRarity rarity = rollRarity(random, TRADER_LARGE_WEIGHTS);
                return rarity == null ? null : offer(pick(random, rarity), TRADER_PRICES[rarity.ordinal()],
                        TRADER_LARGE_USES, 1);
            });
        }
    }

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.CLERIC) {
            return;
        }
        event.getTrades().get(CLERIC_LEVEL).add((VillagerTrades.ItemListing) (villager, random) -> {
            double roll = random.nextDouble() * 100.0D;
            if (roll < DRIED_FLOWER_CLERIC_WEIGHT) {
                return offer(make(ModItems.DRIED_FLOWER.get(), random), DRIED_FLOWER_CLERIC_PRICE,
                        CLERIC_USES, CLERIC_XP);
            }
            if (roll < DRIED_FLOWER_CLERIC_WEIGHT + FRESH_GRASS_CLERIC_WEIGHT) {
                return offer(make(ModItems.FRESH_GRASS.get(), random), FRESH_GRASS_CLERIC_PRICE,
                        CLERIC_USES, CLERIC_XP);
            }
            return null;
        });
    }

    @Nullable
    private static MerchantOffer offer(@Nullable ItemStack offering, int emeralds, int maxUses, int villagerXp) {
        return offering == null ? null
                : new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), offering, maxUses, villagerXp, 0.05F);
    }
}
