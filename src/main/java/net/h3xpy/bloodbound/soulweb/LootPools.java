package net.h3xpy.bloodbound.soulweb;

import java.util.List;
import java.util.stream.Stream;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

/**
 * The two loot pools a soulweb draws from. Each entry carries its own price, so a node costs what
 * its contents are worth rather than a flat rate.
 */
public final class LootPools {

    /** Most enchantments an entry from the equipment pool may roll. */
    private static final int MAX_ENCHANTMENTS = 3;
    /** Enchanting power used when rolling equipment, roughly a mid-to-high table. */
    private static final int MIN_ENCHANT_POWER = 15;
    private static final int MAX_ENCHANT_POWER = 30;

    /** What an entry turns into when the node is bought. */
    public enum Kind {
        /** A plain stack of the item. */
        ITEM,
        /** Raw experience points rather than an item. */
        XP,
        /** One piece of equipment, rolled with up to three compatible enchantments. */
        ENCHANTED
    }

    /**
     * @param item the item to hand over, or the base equipment to enchant; ignored for {@link Kind#XP}
     * @param min  smallest amount, or fewest experience points
     * @param max  largest amount, or most experience points
     * @param cost price of the node in soul shards, before the level's random variation
     */
    public record LootEntry(Item item, int min, int max, int cost, Kind kind) {
        public static LootEntry of(Item item, int min, int max, int cost) {
            return new LootEntry(item, min, max, cost, Kind.ITEM);
        }

        public static LootEntry xp(int min, int max, int cost) {
            return new LootEntry(Items.AIR, min, max, cost, Kind.XP);
        }

        public static LootEntry enchanted(Item item, int cost) {
            return new LootEntry(item, 1, 1, cost, Kind.ENCHANTED);
        }

        public int rollAmount(RandomSource random) {
            return min + random.nextInt(max - min + 1);
        }
    }

    public static final List<LootEntry> COMMON = List.of(
            LootEntry.of(Items.ARROW, 8, 16, 2),
            LootEntry.of(Items.TORCH, 8, 16, 2),
            LootEntry.of(Items.COAL, 6, 12, 2),
            LootEntry.of(Items.IRON_INGOT, 2, 5, 3),
            LootEntry.of(Items.COPPER_INGOT, 3, 7, 2),
            LootEntry.of(Items.REDSTONE, 4, 8, 3),
            LootEntry.of(Items.LAPIS_LAZULI, 4, 8, 3),
            LootEntry.of(Items.GOLD_INGOT, 1, 3, 4),
            LootEntry.of(Items.COOKED_BEEF, 4, 8, 3),
            LootEntry.of(Items.BREAD, 4, 8, 2),
            LootEntry.of(Items.LEATHER, 1, 3, 2),
            LootEntry.of(Items.STRING, 2, 5, 2),
            LootEntry.of(Items.GUNPOWDER, 1, 2, 3),
            LootEntry.xp(24, 48, 3));

    public static final List<LootEntry> RARE = List.of(
            LootEntry.of(Items.DIAMOND, 1, 2, 10),
            LootEntry.of(Items.EMERALD, 3, 7, 8),
            LootEntry.of(Items.GOLD_INGOT, 4, 8, 7),
            LootEntry.of(Items.IRON_INGOT, 6, 10, 6),
            LootEntry.of(Items.GOLDEN_APPLE, 1, 1, 9),
            LootEntry.of(Items.ENDER_PEARL, 1, 2, 7),
            LootEntry.of(Items.BLAZE_ROD, 1, 2, 8),
            LootEntry.of(Items.GHAST_TEAR, 1, 1, 9),
            LootEntry.of(Items.EXPERIENCE_BOTTLE, 2, 4, 7),
            LootEntry.of(Items.DIAMOND_SWORD, 1, 1, 12),
            LootEntry.of(Items.DIAMOND_PICKAXE, 1, 1, 12),
            LootEntry.xp(80, 140, 8),

            // Equipment, rolled with enchantments. A plain book becomes an enchanted book.
            LootEntry.enchanted(Items.IRON_SWORD, 9),
            LootEntry.enchanted(Items.IRON_PICKAXE, 9),
            LootEntry.enchanted(Items.DIAMOND_AXE, 12),
            LootEntry.enchanted(Items.DIAMOND_SHOVEL, 11),
            LootEntry.enchanted(Items.BOW, 9),
            LootEntry.enchanted(Items.CROSSBOW, 10),
            LootEntry.enchanted(Items.IRON_CHESTPLATE, 9),
            LootEntry.enchanted(Items.DIAMOND_HELMET, 12),
            LootEntry.enchanted(Items.DIAMOND_BOOTS, 12),
            LootEntry.enchanted(Items.BOOK, 10));

    private LootPools() {}

    /**
     * Builds the stack an entry hands over. Equipment entries come back enchanted with up to
     * {@link #MAX_ENCHANTMENTS} compatible enchantments.
     */
    public static ItemStack buildStack(RandomSource random, RegistryAccess registries, LootEntry entry) {
        if (entry.kind() == Kind.ENCHANTED) {
            return enchant(random, registries, new ItemStack(entry.item()));
        }
        return new ItemStack(entry.item(), entry.rollAmount(random));
    }

    private static ItemStack enchant(RandomSource random, RegistryAccess registries, ItemStack stack) {
        int power = MIN_ENCHANT_POWER + random.nextInt(MAX_ENCHANT_POWER - MIN_ENCHANT_POWER + 1);
        Stream<Holder<Enchantment>> candidates = registries.registryOrThrow(Registries.ENCHANTMENT)
                .holders()
                .map(holder -> (Holder<Enchantment>) holder);

        List<EnchantmentInstance> rolled = EnchantmentHelper.selectEnchantment(random, stack, power, candidates);

        // Vanilla swaps the plain book for an enchanted one; the enchantments then land in the
        // stored-enchantments component rather than the active one.
        ItemStack result = stack.is(Items.BOOK) ? new ItemStack(Items.ENCHANTED_BOOK) : stack;
        for (int i = 0; i < Math.min(MAX_ENCHANTMENTS, rolled.size()); i++) {
            EnchantmentInstance instance = rolled.get(i);
            result.enchant(instance.enchantment, instance.level);
        }
        return result;
    }
}
