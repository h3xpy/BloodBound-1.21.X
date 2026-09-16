package net.h3xpy.bloodbound.soulweb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import net.h3xpy.bloodbound.Config;
import net.h3xpy.bloodbound.perk.Addon;
import net.h3xpy.bloodbound.perk.AddonRarity;
import net.h3xpy.bloodbound.perk.AddonRegistry;
import net.h3xpy.bloodbound.perk.Perk;
import net.h3xpy.bloodbound.perk.PerkRegistry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * Builds a fresh soulweb: branches radiating from the centre, each a short chain of nodes holding
 * the next tier of a perk, an addon, loot or experience.
 * <p>
 * Perk tiers are always offered in order, no perk appears twice on the same web, and high grade
 * loot stays locked away until the web has levelled up.
 */
public final class SoulwebGenerator {

    /** Radius of the first ring, as a fraction of the drawing area. */
    private static final float BASE_RADIUS = 0.30F;
    /** Radius added per extra ring. */
    private static final float RADIUS_STEP = 0.22F;

    /** A perk, or the next tier of one, always starts from this price. */
    private static final int PERK_BASE_COST = 8;
    /** And ends up at this one, once the level curve has topped out. */
    private static final int PERK_FINAL_COST = 200;

    /** Fewest perk nodes a web may hold, when there are that many perks left to offer. */
    private static final int MIN_PERK_NODES = 2;
    /** Fewest addon nodes a web may hold, when the player owns a perk with an addon left to buy. */
    private static final int MIN_ADDON_NODES = 1;

    /** How far a price may swing either way, once swings start at all. */
    private static final int PRICE_VARIATION = 7;
    /** Below this web level every price is exactly what the curve says, with no swing. */
    private static final int VARIATION_MIN_LEVEL = 5;

    /**
     * Level price curve: {@code P = B + (F - B) / (1 + e^(-0.3 * (L - 12)))}, where B is the
     * reward's base price, F what it should end up costing and L the web level. A logistic ramp
     * from one to the other, climbing hardest around level 12.
     */
    private static final double LEVEL_CURVE_STEEPNESS = 0.3D;
    private static final double LEVEL_CURVE_MIDPOINT = 12.0D;

    /**
     * What loot ends up costing, as a multiple of its base price. Perks and addons name their own
     * final price; every one of them lands on this same ratio, so loot follows it too.
     */
    private static final int LOOT_FINAL_MULTIPLIER = 25;

    /** Web level below which the rare pool never appears. */
    private static final int RARE_POOL_MIN_LEVEL = 4;
    /** Rare pool odds gain this much every {@link #RARE_CHANCE_STEP_LEVELS} levels. */
    private static final int RARE_CHANCE_STEP_PERCENT = 5;
    private static final int RARE_CHANCE_STEP_LEVELS = 3;
    private static final int RARE_CHANCE_CAP_PERCENT = 35;

    private SoulwebGenerator() {}

    /**
     * @param registries     needed to roll enchantments onto equipment rewards
     * @param unlockedPerks  the tier the player already owns per perk; perks absent are untrained
     * @param unlockedAddons addons the player already owns, which are never offered again
     * @param level          the level of the web, which drives prices and loot quality
     */
    public static Soulweb generate(RandomSource random, RegistryAccess registries,
            Map<ResourceLocation, Integer> unlockedPerks, Set<ResourceLocation> unlockedAddons, int level) {
        int branchCount = Config.SOULWEB_BRANCHES.getAsInt();
        int minDepth = Config.SOULWEB_MIN_DEPTH.getAsInt();
        int maxDepth = Math.max(minDepth, Config.SOULWEB_MAX_DEPTH.getAsInt());

        List<int[]> slots = new ArrayList<>();
        for (int branch = 0; branch < branchCount; branch++) {
            int depth = minDepth + random.nextInt(maxDepth - minDepth + 1);
            for (int d = 1; d <= depth; d++) {
                slots.add(new int[] { branch, d });
            }
        }

        // Perks first. Only the next tier of each perk is ever offered, so tiers can only be taken
        // in order and the same perk can never show up twice on one web.
        List<NodeReward> perkRewards = availablePerkRewards(unlockedPerks);
        Collections.shuffle(perkRewards, new Random(random.nextLong()));
        // Every web owes the player at least MIN_PERK_NODES perks, as far as there are perks left
        // to offer at all.
        int perkCount = perkRewards.isEmpty() ? 0
                : Math.min(perkRewards.size(),
                        Math.max(MIN_PERK_NODES, 1 + random.nextInt(Config.SOULWEB_MAX_PERK_NODES.getAsInt())));
        List<Integer> perkSlots = pickSpreadSlots(random, slots, branchCount, perkCount, List.of());

        List<NodeReward> addonRewards = pickAddonRewards(random, unlockedPerks, unlockedAddons);
        List<Integer> addonSlots = pickSpreadSlots(random, slots, branchCount, addonRewards.size(), perkSlots);

        int rareChance = rareChancePercent(level);

        List<SoulwebNode> nodes = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            int branch = slots.get(i)[0];
            int depth = slots.get(i)[1];

            NodeReward reward;
            int baseCost;
            int finalCost;

            int perkIndex = perkSlots.indexOf(i);
            int addonIndex = addonSlots.indexOf(i);
            if (perkIndex >= 0 && perkIndex < perkRewards.size()) {
                reward = perkRewards.get(perkIndex);
                baseCost = PERK_BASE_COST;
                finalCost = PERK_FINAL_COST;
            } else if (addonIndex >= 0 && addonIndex < addonRewards.size()) {
                reward = addonRewards.get(addonIndex);
                AddonRarity rarity = addonRarity(reward);
                baseCost = rarity.baseCost();
                finalCost = rarity.finalCost();
            } else {
                boolean rare = random.nextInt(100) < rareChance;
                LootPools.LootEntry entry = pickLootEntry(random, rare);
                reward = buildLootReward(random, registries, entry, rare);
                baseCost = entry.cost();
                finalCost = entry.cost() * LOOT_FINAL_MULTIPLIER;
            }

            int price = priceWithVariation(random, scaleToLevel(baseCost, finalCost, level), level);
            nodes.add(new SoulwebNode(branch, depth, price, reward,
                    layoutAngle(random, branch, branchCount, depth),
                    BASE_RADIUS + (depth - 1) * RADIUS_STEP));
        }

        return new Soulweb(nodes, branchCount, level);
    }

    /**
     * The odds of drawing from the rare pool. Nothing high grade shows up on the first few webs,
     * then it climbs steadily to a ceiling.
     */
    public static int rareChancePercent(int level) {
        if (level < RARE_POOL_MIN_LEVEL) {
            return 0;
        }
        int steps = 1 + (level - RARE_POOL_MIN_LEVEL) / RARE_CHANCE_STEP_LEVELS;
        return Math.min(RARE_CHANCE_CAP_PERCENT, RARE_CHANCE_STEP_PERCENT * steps);
    }

    /**
     * Walks a reward from its base price to its final one along the level curve
     * {@code B + (F - B) / (1 + e^(-0.3 * (L - 12)))}. Every kind of node goes through this, so
     * perks, addons and loot keep their relative worth as the web levels up.
     */
    public static int scaleToLevel(int baseCost, int finalCost, int level) {
        double climb = (finalCost - baseCost)
                / (1.0D + Math.exp(-LEVEL_CURVE_STEEPNESS * (level - LEVEL_CURVE_MIDPOINT)));
        return Math.max(1, (int) Math.round(baseCost + climb));
    }

    /**
     * Applies the price swing. The first few webs are priced exactly on the curve; from
     * {@link #VARIATION_MIN_LEVEL} on, every node swings up to {@link #PRICE_VARIATION} either way.
     */
    private static int priceWithVariation(RandomSource random, int baseCost, int level) {
        if (level < VARIATION_MIN_LEVEL) {
            return Math.max(1, baseCost);
        }
        int variation = random.nextInt(PRICE_VARIATION * 2 + 1) - PRICE_VARIATION;
        return Math.max(1, baseCost + variation);
    }

    private static LootPools.LootEntry pickLootEntry(RandomSource random, boolean rare) {
        List<LootPools.LootEntry> pool = rare ? LootPools.RARE : LootPools.COMMON;
        return pool.get(random.nextInt(pool.size()));
    }

    private static NodeReward buildLootReward(RandomSource random, RegistryAccess registries,
            LootPools.LootEntry entry, boolean rare) {
        NodeRarity grade = rare ? NodeRarity.RARE : NodeRarity.COMMON;
        if (entry.kind() == LootPools.Kind.XP) {
            return new NodeReward.XpReward(entry.rollAmount(random), grade);
        }
        ItemStack stack = LootPools.buildStack(random, registries, entry);
        return new NodeReward.ItemReward(stack, grade);
    }

    private static AddonRarity addonRarity(NodeReward reward) {
        if (reward instanceof NodeReward.AddonReward addonReward) {
            Addon addon = AddonRegistry.get(addonReward.addonId());
            if (addon != null) {
                return addon.rarity();
            }
        }
        return AddonRarity.COMMON;
    }

    /**
     * Picks {@code count} slots, preferring one per branch so the player has a real choice, and
     * never reusing a slot already taken.
     */
    private static List<Integer> pickSpreadSlots(RandomSource random, List<int[]> slots, int branchCount, int count,
            List<Integer> taken) {
        List<Integer> chosen = new ArrayList<>();
        if (count <= 0) {
            return chosen;
        }
        List<Integer> branchOrder = new ArrayList<>();
        for (int i = 0; i < branchCount; i++) {
            branchOrder.add(i);
        }
        Collections.shuffle(branchOrder, new Random(random.nextLong()));

        // Two passes, so more rewards than branches still get placed.
        for (int pass = 0; pass < 2 && chosen.size() < count; pass++) {
            for (int branch : branchOrder) {
                if (chosen.size() >= count) {
                    break;
                }
                List<Integer> candidates = new ArrayList<>();
                for (int i = 0; i < slots.size(); i++) {
                    if (slots.get(i)[0] == branch && !chosen.contains(i) && !taken.contains(i)) {
                        candidates.add(i);
                    }
                }
                if (!candidates.isEmpty()) {
                    chosen.add(candidates.get(random.nextInt(candidates.size())));
                }
            }
        }
        return chosen;
    }

    private static List<NodeReward> availablePerkRewards(Map<ResourceLocation, Integer> unlockedPerks) {
        List<NodeReward> rewards = new ArrayList<>();
        for (Perk perk : PerkRegistry.all()) {
            int nextTier = unlockedPerks.getOrDefault(perk.id(), 0) + 1;
            if (nextTier <= Perk.MAX_TIER) {
                rewards.add(new NodeReward.PerkReward(perk.id(), nextTier, rarityForTier(nextTier)));
            }
        }
        return rewards;
    }

    /**
     * Rolls how many addons this web offers and which ones, weighted so the rarer addons show up
     * less often. Only addons for perks the player owns, and does not already have, are eligible.
     */
    private static List<NodeReward> pickAddonRewards(RandomSource random,
            Map<ResourceLocation, Integer> unlockedPerks, Set<ResourceLocation> unlockedAddons) {
        List<Addon> pool = new ArrayList<>();
        for (Addon addon : AddonRegistry.all()) {
            if (unlockedPerks.getOrDefault(addon.perkId(), 0) > 0 && !unlockedAddons.contains(addon.id())) {
                pool.add(addon);
            }
        }

        List<NodeReward> rewards = new ArrayList<>();
        // At least one, so a web always has an addon on it whenever one is eligible at all.
        int wanted = Math.min(pool.size(),
                Math.max(MIN_ADDON_NODES, random.nextInt(Config.SOULWEB_MAX_ADDON_NODES.getAsInt() + 1)));
        for (int i = 0; i < wanted; i++) {
            Addon picked = weightedPick(random, pool);
            if (picked == null) {
                break;
            }
            pool.remove(picked);
            rewards.add(new NodeReward.AddonReward(picked.id()));
        }
        return rewards;
    }

    private static Addon weightedPick(RandomSource random, List<Addon> pool) {
        int total = 0;
        for (Addon addon : pool) {
            total += addon.rarity().weight();
        }
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (Addon addon : pool) {
            roll -= addon.rarity().weight();
            if (roll < 0) {
                return addon;
            }
        }
        return null;
    }

    private static NodeRarity rarityForTier(int tier) {
        return switch (tier) {
            case 1 -> NodeRarity.RARE;
            case 2 -> NodeRarity.VERY_RARE;
            default -> NodeRarity.ULTRA_RARE;
        };
    }

    /** Evenly spaced spokes, starting at the top, with a little jitter so the web looks organic. */
    private static float layoutAngle(RandomSource random, int branch, int branchCount, int depth) {
        double step = (Math.PI * 2.0D) / branchCount;
        double jitter = step * 0.18D * (depth - 1) * (random.nextDouble() * 2.0D - 1.0D);
        return (float) (-Math.PI / 2.0D + branch * step + jitter);
    }
}
