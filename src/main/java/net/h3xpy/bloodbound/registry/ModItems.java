package net.h3xpy.bloodbound.registry;

import java.util.List;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.offering.Offering;
import net.h3xpy.bloodbound.offering.OfferingItem;
import net.h3xpy.bloodbound.perk.AddonRarity;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BloodBound.MODID);

    /** The currency mobs drop and the perk table spends. */
    public static final DeferredItem<Item> SOUL_SHARD =
            ITEMS.registerSimpleItem("soul_shard", new Item.Properties().stacksTo(64));

    public static final DeferredItem<?> PERK_TABLE_ITEM =
            ITEMS.registerSimpleBlockItem("perk_table", ModBlocks.PERK_TABLE);

    // --- Offerings: laid on the Perk Table, they shape the next soulweb level ---

    // Discounts on every price of the next level.
    public static final DeferredItem<OfferingItem> DRIED_FLOWER =
            offering("dried_flower", Offering.discount(AddonRarity.COMMON, 5));
    public static final DeferredItem<OfferingItem> FRESH_GRASS =
            offering("fresh_grass", Offering.discount(AddonRarity.UNCOMMON, 10));
    public static final DeferredItem<OfferingItem> WILD_TWEEDIA =
            offering("wild_tweedia", Offering.discount(AddonRarity.RARE, 20));
    public static final DeferredItem<OfferingItem> CYANANTHUS_BOUQUET =
            offering("cyananthus_bouquet", Offering.discount(AddonRarity.EPIC, 40));
    public static final DeferredItem<OfferingItem> POISONOUS_BERRIES =
            offering("poisonous_berries", Offering.discount(AddonRarity.UNSTABLE, 90));

    // Marked with perks or addons, some of which the next level is sure to offer.
    public static final DeferredItem<OfferingItem> BLOOMING_VIOLET =
            offering("blooming_violet", Offering.markedPerks(AddonRarity.UNCOMMON, 3, 1, 50));
    public static final DeferredItem<OfferingItem> FROZEN_AMARANTH =
            offering("frozen_amaranth", Offering.markedAddons(AddonRarity.COMMON, 6, 3, AddonRarity.COMMON, 25));
    public static final DeferredItem<OfferingItem> FRESH_AMARANTH =
            offering("fresh_amaranth", Offering.markedAddons(AddonRarity.UNCOMMON, 3, 1, AddonRarity.RARE, 75));

    // Extra perk nodes on the next level.
    public static final DeferredItem<OfferingItem> DRIED_PEPPERMINT =
            offering("dried_peppermint", Offering.extraPerks(AddonRarity.UNCOMMON, 1));
    public static final DeferredItem<OfferingItem> TIED_PEPPERMINT =
            offering("tied_peppermint", Offering.extraPerks(AddonRarity.RARE, 2));
    public static final DeferredItem<OfferingItem> FRESH_PEPPERMINT =
            offering("fresh_peppermint", Offering.extraPerks(AddonRarity.EPIC, 3));

    // Strawberries: the next level leans on the perks already owned.
    public static final DeferredItem<OfferingItem> STRAWBERRY_LEAVES =
            offering("strawberry_leaves", Offering.upgradesOnly(AddonRarity.RARE));
    public static final DeferredItem<OfferingItem> STRAWBERRY_FLOWER =
            offering("strawberry_flower", Offering.upgradeDiscount(AddonRarity.COMMON, 50));

    // The rest.
    public static final DeferredItem<OfferingItem> YELLOW_CHERRY_BLOSSOM =
            offering("yellow_cherry_blossom", Offering.refund(AddonRarity.RARE, 50));
    public static final DeferredItem<OfferingItem> BLACK_LEAF =
            offering("black_leaf", Offering.preserveNext(AddonRarity.EPIC));

    /** Every offering, in the order the creative tab shows them. */
    public static final List<DeferredItem<OfferingItem>> OFFERINGS = List.of(
            DRIED_FLOWER, FRESH_GRASS, WILD_TWEEDIA, CYANANTHUS_BOUQUET, POISONOUS_BERRIES,
            BLOOMING_VIOLET, FROZEN_AMARANTH, FRESH_AMARANTH,
            DRIED_PEPPERMINT, TIED_PEPPERMINT, FRESH_PEPPERMINT,
            STRAWBERRY_LEAVES, STRAWBERRY_FLOWER,
            YELLOW_CHERRY_BLOSSOM, BLACK_LEAF);

    private ModItems() {}

    private static DeferredItem<OfferingItem> offering(String name, Offering offering) {
        return ITEMS.register(name, () -> new OfferingItem(offering, new Item.Properties().stacksTo(16)));
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
