package net.h3xpy.bloodbound.event;

import java.util.Optional;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * Full Extraction: an ore comes out of the ground already smelted, with the rock it sat in, and now
 * and then a soul shard or two.
 * <p>
 * Worked on the drops rather than on the break, so Fortune has already had its say by the time the
 * ore is smelted — a fortunate break smelts into more ingots, not the same number. Silk Touch
 * switches the whole perk off: that pick is asking for the ore block, and gets it.
 */
public final class FullExtractionHandler {

    private FullExtractionHandler() {}

    @SubscribeEvent
    public static void onDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) {
            return;
        }
        int tier = PerkDataManager.get(player).getActiveTier(ModPerks.FULL_EXTRACTION);
        BlockState state = event.getState();
        if (tier <= 0 || !state.is(Tags.Blocks.ORES)) {
            return;
        }

        ServerLevel level = event.getLevel();
        Holder<Enchantment> silkTouch = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.SILK_TOUCH);
        if (EnchantmentHelper.getItemEnchantmentLevel(silkTouch, event.getTool()) > 0) {
            return;
        }

        for (ItemEntity drop : event.getDrops()) {
            ItemStack smelted = smelt(level, drop.getItem());
            if (smelted != null) {
                drop.setItem(smelted);
            }
        }

        BlockPos pos = event.getPos();
        Item rock = rockOf(state);
        if (rock != null) {
            event.getDrops().add(itemAt(level, pos, new ItemStack(rock)));
        }

        // Rolled again after every success: a lucky break can pay out more than once.
        double chance = ModPerks.FULL_EXTRACTION.value(ModPerks.EXTRACTION_SHARD_CHANCE, tier) / 100.0D;
        int shards = 0;
        while (player.getRandom().nextDouble() < chance && shards < ModPerks.EXTRACTION_MAX_SHARDS) {
            shards++;
        }
        if (shards > 0) {
            event.getDrops().add(itemAt(level, pos, new ItemStack(ModItems.SOUL_SHARD.get(), shards)));
        }

        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                8, 0.25D, 0.25D, 0.25D, 0.01D);
        level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.3F, 1.6F);
    }

    /** What a stack smelts into, as many of it as the stack was, or null if it does not smelt. */
    @Nullable
    private static ItemStack smelt(ServerLevel level, ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Optional<RecipeHolder<SmeltingRecipe>> recipe = level.getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), level);
        if (recipe.isEmpty()) {
            return null;
        }
        ItemStack result = recipe.get().value().getResultItem(level.registryAccess());
        return result.isEmpty() ? null : result.copyWithCount(result.getCount() * stack.getCount());
    }

    /** The rock an ore was embedded in, as the plain block, or null for an ore in anything else. */
    @Nullable
    private static Item rockOf(BlockState state) {
        if (state.is(Tags.Blocks.ORES_IN_GROUND_DEEPSLATE)) {
            return Items.DEEPSLATE;
        }
        if (state.is(Tags.Blocks.ORES_IN_GROUND_NETHERRACK)) {
            return Items.NETHERRACK;
        }
        if (state.is(Tags.Blocks.ORES_IN_GROUND_STONE)) {
            return Items.STONE;
        }
        return null;
    }

    private static ItemEntity itemAt(ServerLevel level, BlockPos pos, ItemStack stack) {
        ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack);
        entity.setDefaultPickUpDelay();
        return entity;
    }
}
