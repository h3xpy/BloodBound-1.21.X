package net.h3xpy.bloodbound.menu;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.registry.ModBlocks;
import net.h3xpy.bloodbound.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

/**
 * A slot-less menu. It exists so the screen closes on its own when the player walks away from the
 * table, and so the server can verify a player is actually standing at a table before spending
 * their shards.
 */
public class PerkTableMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final Player owner;

    /** What the client was last told the player is carrying. */
    private int shards;

    /** Client-side constructor; the block position is written by the server when the menu opens. */
    public PerkTableMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, ContainerLevelAccess.create(inventory.player.level(), data.readBlockPos()));
    }

    public PerkTableMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(ModMenus.PERK_TABLE.get(), containerId);
        this.access = access;
        this.owner = inventory.player;

        // The shard count travels with the menu rather than being counted from the client's own
        // inventory. While a menu is open the server stops sending inventory slot updates, so a
        // client-side count went stale the moment a shard was picked up or spent — which is why it
        // only ever corrected itself when something forced the slot to be sent again.
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                // Menu data travels as a short, and an inventory tops out well under that anyway.
                return owner.level().isClientSide ? shards
                        : Math.min(Short.MAX_VALUE, PerkDataManager.countShards(owner));
            }

            @Override
            public void set(int value) {
                shards = value;
            }
        });
    }

    /** How many soul shards the player is carrying, as the server last counted them. */
    public int shardCount() {
        return owner.level().isClientSide ? shards : PerkDataManager.countShards(owner);
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
