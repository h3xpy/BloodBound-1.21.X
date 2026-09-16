package net.h3xpy.bloodbound.effect;

import net.minecraft.world.effect.MobEffectCategory;

/**
 * Bleeding: a wound that opens every time you run.
 * <p>
 * The effect itself does nothing — it is a flag, and all of the behaviour lives in
 * {@code BleedingHandler}: the charges, the cost of sprinting on an empty bar, the cure, and the
 * trail of blood. Keeping it out of {@code applyEffectTick} is deliberate: the charges are held
 * outside the effect, and re-adding an effect from inside its own tick is not something to build on.
 */
public class BleedingEffect extends BloodBoundEffect {

    public BleedingEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B0000);
    }
}
