package net.h3xpy.bloodbound.registry;

import net.h3xpy.bloodbound.BloodBound;
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

    private ModItems() {}

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
