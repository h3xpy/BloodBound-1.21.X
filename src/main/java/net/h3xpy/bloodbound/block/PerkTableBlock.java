package net.h3xpy.bloodbound.block;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.menu.PerkTableMenu;
import net.h3xpy.bloodbound.offering.OfferingItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The perk table: right-clicking opens the loadout and soulweb screens.
 * <p>
 * Right-clicking it with an offering lays that offering down for the next soulweb level instead;
 * one at a time, so laying another hands the first one back. Sneaking with empty hands takes it
 * back. The offering belongs to the player, not to the table: any table shows the same one.
 */
public class PerkTableBlock extends Block {
    private static final Component TITLE = Component.translatable("container.bloodbound.perk_table");

    public PerkTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(stack.getItem() instanceof OfferingItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return ItemInteractionResult.SUCCESS;
        }

        // Marks are settled before it goes down, so the table shows what it will do.
        OfferingItem.ensureMarked(stack, serverPlayer.getRandom());
        PlayerPerkData data = PerkDataManager.get(serverPlayer);
        ItemStack previous = data.placeOffering(stack);
        Component name = stack.getHoverName();
        stack.consume(1, player);
        giveBack(serverPlayer, previous);
        PerkDataManager.sync(serverPlayer);

        serverPlayer.displayClientMessage(Component.translatable("bloodbound.message.offering_placed", name)
                .withStyle(ChatFormatting.GOLD), true);
        level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
        ((ServerLevel) level).sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                pos.getX() + 0.5D, pos.getY() + 1.05D, pos.getZ() + 0.5D, 12, 0.25D, 0.05D, 0.25D, 0.01D);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // Sneaking with empty hands takes the offering back off the table.
        if (player.isSecondaryUseActive() && player instanceof ServerPlayer serverPlayer) {
            PlayerPerkData data = PerkDataManager.get(serverPlayer);
            if (!data.offering().isEmpty()) {
                giveBack(serverPlayer, data.placeOffering(ItemStack.EMPTY));
                PerkDataManager.sync(serverPlayer);
                serverPlayer.displayClientMessage(Component.translatable("bloodbound.message.offering_taken")
                        .withStyle(ChatFormatting.GRAY), true);
                return InteractionResult.CONSUME;
            }
        }

        // Roll the web now if the player has never had one, so the soulweb tab has something to
        // draw the moment the screen opens.
        if (player instanceof ServerPlayer serverPlayer) {
            PlayerPerkData data = PerkDataManager.get(serverPlayer);
            data.getOrCreateSoulweb(serverPlayer.getRandom(), serverPlayer.registryAccess());
            // A web left spent by an older build is rolled over right there, offering and all.
            PerkDataManager.settleOffering(serverPlayer, data);
            PerkDataManager.sendWiki(serverPlayer);
            PerkDataManager.sync(serverPlayer);
        }

        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, owner) -> new PerkTableMenu(containerId, inventory,
                        ContainerLevelAccess.create(level, pos)),
                TITLE), pos);
        return InteractionResult.CONSUME;
    }

    /** Puts an offering back in the player's inventory, or at their feet when it is full. */
    private static void giveBack(ServerPlayer player, ItemStack offering) {
        if (!offering.isEmpty() && !player.getInventory().add(offering)) {
            player.drop(offering, false);
        }
    }
}
