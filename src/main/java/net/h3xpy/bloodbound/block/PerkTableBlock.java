package net.h3xpy.bloodbound.block;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.menu.PerkTableMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The perk table: right-clicking opens the loadout and soulweb screens.
 */
public class PerkTableBlock extends Block {
    private static final Component TITLE = Component.translatable("container.bloodbound.perk_table");

    public PerkTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // Roll the web now if the player has never had one, so the soulweb tab has something to
        // draw the moment the screen opens.
        if (player instanceof ServerPlayer serverPlayer) {
            PerkDataManager.get(serverPlayer).getOrCreateSoulweb(serverPlayer.getRandom(), serverPlayer.registryAccess());
            PerkDataManager.sync(serverPlayer);
        }

        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, owner) -> new PerkTableMenu(containerId, inventory,
                        ContainerLevelAccess.create(level, pos)),
                TITLE), pos);
        return InteractionResult.CONSUME;
    }
}
