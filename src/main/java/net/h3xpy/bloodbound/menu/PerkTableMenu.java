package net.h3xpy.bloodbound.menu;

import net.h3xpy.bloodbound.registry.ModBlocks;
import net.h3xpy.bloodbound.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

/**
 * A slot-less menu. It exists so the screen closes on its own when the player walks away from the
 * table, and so the server can verify a player is actually standing at a table before spending
 * their shards.
 */
public class PerkTableMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;

    /** Client-side constructor; the block position is written by the server when the menu opens. */
    public PerkTableMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, ContainerLevelAccess.create(inventory.player.level(), data.readBlockPos()));
    }

    public PerkTableMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(ModMenus.PERK_TABLE.get(), containerId);
        this.access = access;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.PERK_TABLE.get());
    }
}
