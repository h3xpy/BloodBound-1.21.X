package net.h3xpy.bloodbound.effect;

import net.minecraft.world.effect.MobEffectCategory;

/**
 * Aura Revealed: the victim is outlined through walls, but only on the screen of whoever revealed
 * them. Everyone else sees nothing at all.
 * <p>
 * The effect itself is only a timer and a marker the victim can see in their own effect list. Who
 * is watching, and the glow on their client, is tracked by the aura reveal handler.
 */
public class AuraRevealedEffect extends BloodBoundEffect {

    public AuraRevealedEffect() {
        super(MobEffectCategory.HARMFUL, 0xE0C44C);
    }
}
