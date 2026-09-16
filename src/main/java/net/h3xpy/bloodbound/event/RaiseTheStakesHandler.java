package net.h3xpy.bloodbound.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * Raise The Stakes and its four coins: ores pay out more than once.
 * <p>
 * Every success rolls again, so the payout is open ended — it just gets steadily less likely. Silk
 * Touch turns the whole thing off: duplicating the ore block itself would be free diamonds.
 * <p>
 * The extra soul shard the perk also grants lives in {@link SoulShardDropHandler}, where the shard
 * roll happens.
 */
public final class RaiseTheStakesHandler {

    /** What Tarnished Coin hands over instead of the ore's own drop. */
    private static final Map<Item, Item> STORAGE_BLOCKS = Map.ofEntries(
            Map.entry(Items.RAW_IRON, Items.RAW_IRON_BLOCK),
            Map.entry(Items.RAW_COPPER, Items.RAW_COPPER_BLOCK),
            Map.entry(Items.RAW_GOLD, Items.RAW_GOLD_BLOCK),
            Map.entry(Items.COAL, Items.COAL_BLOCK),
            Map.entry(Items.DIAMOND, Items.DIAMOND_BLOCK),
            Map.entry(Items.EMERALD, Items.EMERALD_BLOCK),
            Map.entry(Items.LAPIS_LAZULI, Items.LAPIS_BLOCK),
            Map.entry(Items.REDSTONE, Items.REDSTONE_BLOCK),
            Map.entry(Items.QUARTZ, Items.QUARTZ_BLOCK),
            Map.entry(Items.AMETHYST_SHARD, Items.AMETHYST_BLOCK));

    private RaiseTheStakesHandler() {}

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) {
            return;
        }
        if (!event.getState().is(Tags.Blocks.ORES) || event.getDrops().isEmpty()) {
            return;
        }

        PlayerPerkData data = PerkDataManager.get(player);
        int tier = data.getActiveTier(ModPerks.RAISE_THE_STAKES);
        if (tier <= 0 || hasSilkTouch(event.getLevel(), event.getTool())) {
            return;
        }

        // Tarnished Coin's whole block is rolled separately, and pays whatever else happens.
        if (data.isAddonActive(ModAddons.TARNISHED_COIN)
                && player.getRandom().nextFloat() < ModAddons.TARNISHED_COIN_CHANCE) {
            dropStorageBlock(event);
        }

        float chance = rerollChance(data, tier);
        boolean allOrNothing = data.isAddonActive(ModAddons.SHATTERED_COIN);

        int extra = 0;
        while (extra < ModPerks.RAISE_THE_STAKES_MAX_EXTRA_DROPS && player.getRandom().nextFloat() < chance) {
            extra++;
        }

        // Shattered Coin gambles the ore itself on the opening roll.
        if (allOrNothing && extra == 0) {
            event.getDrops().clear();
            return;
        }
        if (extra <= 0) {
            return;
        }

        // Copied from what the block actually dropped, so fortune and the loot table have already
        // had their say and every extra payout is a real one.
        List<ItemEntity> originals = new ArrayList<>(event.getDrops());
        for (int i = 0; i < extra; i++) {
            for (ItemEntity original : originals) {
                event.getDrops().add(new ItemEntity(event.getLevel(),
                        original.getX(), original.getY(), original.getZ(), original.getItem().copy()));
            }
        }
    }

    /** The odds of one more payout, once every coin has had its say. */
    private static float rerollChance(PlayerPerkData data, int tier) {
        if (data.isAddonActive(ModAddons.SHATTERED_COIN)) {
            return ModAddons.SHATTERED_COIN_CHANCE[Math.clamp(tier - 1, 0,
                    ModAddons.SHATTERED_COIN_CHANCE.length - 1)];
        }

        float chance = (float) ModPerks.RAISE_THE_STAKES.value(ModPerks.RAISE_THE_STAKES_CHANCE, tier) / 100.0F;
        if (data.isAddonActive(ModAddons.SHINY_COIN)) {
            chance += ModAddons.SHINY_COIN_BONUS;
        }
        if (data.isAddonActive(ModAddons.SCRATCHED_COIN)) {
            chance -= ModAddons.SCRATCHED_COIN_PENALTY;
        }
        if (data.isAddonActive(ModAddons.TARNISHED_COIN)) {
            chance -= ModAddons.TARNISHED_COIN_PENALTY;
        }
        return Math.max(0.0F, chance);
    }

    /** Adds one storage block of whatever the ore dropped, when there is such a block. */
    private static void dropStorageBlock(BlockDropsEvent event) {
        for (ItemEntity drop : new ArrayList<>(event.getDrops())) {
            Item block = STORAGE_BLOCKS.get(drop.getItem().getItem());
            if (block != null) {
                event.getDrops().add(new ItemEntity(event.getLevel(),
                        drop.getX(), drop.getY(), drop.getZ(), new ItemStack(block)));
                return;
            }
        }
    }

    private static boolean hasSilkTouch(ServerLevel level, ItemStack tool) {
        if (tool.isEmpty()) {
            return false;
        }
        Holder<Enchantment> silkTouch = level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SILK_TOUCH);
        return EnchantmentHelper.getItemEnchantmentLevel(silkTouch, tool) > 0;
    }
}
