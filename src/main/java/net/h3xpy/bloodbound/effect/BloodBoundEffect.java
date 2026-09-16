package net.h3xpy.bloodbound.effect;

import java.util.Set;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.EffectCure;
import net.neoforged.neoforge.common.EffectCures;

/**
 * Shared base for BloodBound's own status effects.
 * <p>
 * The one thing it settles is what can cure them: not milk. Every one of these effects is the price
 * of a perk — Broken pays for Keep Fighting, Exposed for a shot across the field — and a bucket
 * should not be able to undo that. A totem of undying still clears them, the way it clears
 * everything else.
 */
public abstract class BloodBoundEffect extends MobEffect {

    protected BloodBoundEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance effectInstance) {
        cures.add(EffectCures.PROTECTED_BY_TOTEM);
    }
}
