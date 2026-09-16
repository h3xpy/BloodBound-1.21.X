package net.h3xpy.bloodbound.registry;

import java.util.function.Supplier;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BloodBound.MODID);

    public static final Supplier<CreativeModeTab> BLOODBOUND = TABS.register("bloodbound",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.bloodbound"))
                    .icon(() -> new ItemStack(ModItems.SOUL_SHARD.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.SOUL_SHARD.get());
                        output.accept(ModBlocks.PERK_TABLE.get());
                    })
                    .build());

    private ModCreativeTabs() {}

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
