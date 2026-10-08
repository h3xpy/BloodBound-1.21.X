package net.h3xpy.bloodbound.registry;

import java.util.List;
import java.util.function.Supplier;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Data carried by BloodBound's own item stacks. */
public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, BloodBound.MODID);

    /** The perks or addons an offering is marked with, rolled once when it is first made. */
    public static final Supplier<DataComponentType<List<ResourceLocation>>> OFFERING_MARKS =
            COMPONENTS.registerComponentType("offering_marks", builder -> builder
                    .persistent(ResourceLocation.CODEC.listOf())
                    .networkSynchronized(ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list())));

    private ModDataComponents() {}

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
