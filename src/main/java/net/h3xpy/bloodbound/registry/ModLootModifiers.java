package net.h3xpy.bloodbound.registry;

import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.offering.OfferingLootModifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Global loot modifiers; each is switched on by a file under {@code data/<modid>/loot_modifiers/}. */
public final class ModLootModifiers {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, BloodBound.MODID);

    public static final Supplier<MapCodec<OfferingLootModifier>> OFFERINGS_IN_CHESTS =
            SERIALIZERS.register("offerings_in_chests", () -> OfferingLootModifier.CODEC);

    private ModLootModifiers() {}

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }
}
