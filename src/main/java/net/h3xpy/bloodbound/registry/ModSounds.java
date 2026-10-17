package net.h3xpy.bloodbound.registry;

import java.util.function.Supplier;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's own sounds. The files are in {@code assets/bloodbound/sounds/}, listed in {@code sounds.json}. */
public final class ModSounds {

    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, BloodBound.MODID);

    /** Now and then, a Longshot arrow with Gunpowder goes off like a heavy sniper. */
    public static final Supplier<SoundEvent> HEAVY_SNIPER = SOUNDS.register("heavy_sniper",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "heavy_sniper")));

    private ModSounds() {}

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }
}
