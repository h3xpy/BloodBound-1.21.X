package net.h3xpy.bloodbound.registry;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.effect.AuraRevealedEffect;
import net.h3xpy.bloodbound.effect.BleedingEffect;
import net.h3xpy.bloodbound.effect.BrokenEffect;
import net.h3xpy.bloodbound.effect.ExhaustedEffect;
import net.h3xpy.bloodbound.effect.ExposedEffect;
import net.h3xpy.bloodbound.effect.FlashedEffect;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, BloodBound.MODID);

    /** Stops the entity healing by any means. */
    public static final Holder<MobEffect> BROKEN = EFFECTS.register("broken", BrokenEffect::new);

    /** Pins the entity at one point of health, then hands it back when the effect ends. */
    public static final Holder<MobEffect> EXPOSED = EFFECTS.register("exposed", ExposedEffect::new);

    /** Outlines the entity through walls, for one viewer only. */
    public static final Holder<MobEffect> AURA_REVEALED =
            EFFECTS.register("aura_revealed", AuraRevealedEffect::new);

    /** Blinds the entity outright: a white screen for a player, no target at all for a mob. */
    public static final Holder<MobEffect> FLASHED = EFFECTS.register("flashed", FlashedEffect::new);

    /** A wound on a bar of charges: running costs them, and running on none costs blood. */
    public static final Holder<MobEffect> BLEEDING = EFFECTS.register("bleeding", BleedingEffect::new);

    /** A stamina bar: running spends it, standing still fills it, and empty means Slowness. */
    public static final Holder<MobEffect> EXHAUSTED = EFFECTS.register("exhausted", ExhaustedEffect::new);

    private ModEffects() {}

    public static void register(IEventBus modEventBus) {
        EFFECTS.register(modEventBus);
    }
}
