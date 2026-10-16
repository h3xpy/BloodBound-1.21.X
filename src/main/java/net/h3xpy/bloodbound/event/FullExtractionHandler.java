package net.h3xpy.bloodbound.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.advancement.ModAdvancements;
import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.perk.PerkGlow;
import net.h3xpy.bloodbound.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
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
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Full Extraction: an ore comes out of the ground already smelted, with the rock it sat in, and now
 * and then a soul shard or two.
 * <p>
 * Worked on the drops rather than on the break, so Fortune has already had its say by the time the
 * ore is smelted — a fortunate break smelts into more ingots, not the same number. Silk Touch
 * switches the whole perk off: that pick is asking for the ore block, and gets it.
 */
public final class FullExtractionHandler {

    /** Perfect Extraction: the shards one ore has to give up. */
    private static final int PERFECT_EXTRACTION_SHARDS = 3;

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
        PlayerPerkData data = PerkDataManager.get(player);
        double chance = ModPerks.FULL_EXTRACTION.value(ModPerks.EXTRACTION_SHARD_CHANCE, tier) / 100.0D;
        if (data.isAddonActive(ModAddons.GOLD_NUGGET)) {
            chance += ModAddons.GOLD_NUGGET_CHANCE;
        }
        int shards = 0;
        while (player.getRandom().nextDouble() < chance && shards < ModPerks.EXTRACTION_MAX_SHARDS) {
            shards++;
        }
        if (shards > 0) {
            // Metal Plate: every shard brings one more of the ore along with it.
            if (data.isAddonActive(ModAddons.METAL_PLATE) && !event.getDrops().isEmpty()) {
                ItemStack ore = event.getDrops().get(0).getItem();
                event.getDrops().add(itemAt(level, pos, ore.copyWithCount(shards)));
            }
            event.getDrops().add(itemAt(level, pos, new ItemStack(ModItems.SOUL_SHARD.get(), shards)));
            PerkGlow.flash(player, ModPerks.FULL_EXTRACTION);
        }
        if (data.isAddonActive(ModAddons.GILDED_PICKAXE)) {
            gildedPickaxe(player, data, level, pos, event);
        }
        if (shards >= PERFECT_EXTRACTION_SHARDS) {
            ModAdvancements.grant(player, ModAdvancements.PERFECT_EXTRACTION);
        }

        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                8, 0.25D, 0.25D, 0.25D, 0.01D);
        level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.3F, 1.6F);
    }

    /**
     * Gilded Pickaxe: a long shot at a jackpot. Every miss makes the next try likelier and the prize
     * a shard smaller; a hit pays out and starts it all again. The misses are saved with the player.
     */
    private static void gildedPickaxe(ServerPlayer player, PlayerPerkData data, ServerLevel level, BlockPos pos,
            BlockDropsEvent event) {
        int misses = (int) data.questProgress(GILDED_MISSES);
        double chance = ModAddons.GILDED_PICKAXE_CHANCE + ModAddons.GILDED_PICKAXE_CHANCE_PER_MISS * misses;
        if (player.getRandom().nextDouble() >= chance) {
            data.addQuestProgress(GILDED_MISSES, 1.0F);
            return;
        }
        int prize = Math.max(1, ModAddons.GILDED_PICKAXE_PRIZE - misses);
        data.addQuestProgress(GILDED_MISSES, -misses);
        // Handed out in full stacks, so a big prize is not one stack past its limit.
        for (int left = prize; left > 0; left -= 64) {
            event.getDrops().add(itemAt(level, pos, new ItemStack(ModItems.SOUL_SHARD.get(), Math.min(64, left))));
        }
        PerkGlow.flash(player, ModPerks.FULL_EXTRACTION, 100);
        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.2F);
        player.displayClientMessage(Component.translatable("bloodbound.message.gilded_pickaxe", prize)
                .withStyle(ChatFormatting.GOLD), false);
    }

    /**
     * Drill Head: a block broken takes the blocks like it with it, in the 3x3x3 it opens onto — the
     * one it sits at the near face of, going the way the player looks. Each comes out as if mined by
     * hand, the tool paying for it, so a worn tool stops the drill when it breaks.
     */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (drilling || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        PlayerPerkData data = PerkDataManager.get(player);
        if (data.getActiveTier(ModPerks.FULL_EXTRACTION) <= 0 || !data.isAddonActive(ModAddons.DRILL_HEAD)) {
            return;
        }
        BlockState state = event.getState();
        BlockPos origin = event.getPos();
        Direction forward = Direction.getNearest(player.getLookAngle());
        Direction.Axis axis = forward.getAxis();

        List<BlockPos> targets = new ArrayList<>();
        for (int depth = 0; depth < 3; depth++) {
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    BlockPos pos = origin.relative(forward, depth).offset(
                            axis == Direction.Axis.X ? 0 : a,
                            axis == Direction.Axis.Y ? 0 : (axis == Direction.Axis.X ? a : b),
                            axis == Direction.Axis.Z ? 0 : b);
                    if (!pos.equals(origin) && player.level().getBlockState(pos).is(state.getBlock())) {
                        targets.add(pos);
                    }
                }
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        boolean hadTool = !player.getMainHandItem().isEmpty();
        // After the block itself has gone, so the drill never runs ahead of the break that set it off.
        player.getServer().execute(() -> {
            drilling = true;
            try {
                for (BlockPos pos : targets) {
                    // The tool gave out: the drill stops with it.
                    if (hadTool && player.getMainHandItem().isEmpty()) {
                        break;
                    }
                    if (player.level().getBlockState(pos).is(state.getBlock())) {
                        player.gameMode.destroyBlock(pos);
                    }
                }
            } finally {
                drilling = false;
            }
        });
    }

    /** Set while the drill breaks its own blocks, so they do not set off drills of their own. */
    private static boolean drilling;

    /** Saved count of Gilded Pickaxe misses since its last jackpot. */
    private static final String GILDED_MISSES = "gilded_pickaxe_misses";

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
