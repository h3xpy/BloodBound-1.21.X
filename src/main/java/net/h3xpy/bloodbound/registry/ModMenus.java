package net.h3xpy.bloodbound.registry;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.menu.PerkTableMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(BuiltInRegistries.MENU, BloodBound.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<PerkTableMenu>> PERK_TABLE =
            MENUS.register("perk_table", () -> IMenuTypeExtension.create(PerkTableMenu::new));

    private ModMenus() {}

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
