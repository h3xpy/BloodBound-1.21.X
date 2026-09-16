package net.h3xpy.bloodbound.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Exposed: the victim is pinned at a single point of health for the duration, and gets the health
 * they had back the moment it ends. One hit finishes them while it lasts.
 * <p>
 * Anything with more than {@link #MAX_AFFECTED_HEALTH} health shrugs it off entirely — bosses are
 * not meant to be deleted by a status effect.
 * <p>
 * Pinning, restoring and the health cap all live in the effect event handler; this class only
 * holds the effect down to one point of health each tick and decides how it looks.
 */
public class ExposedEffect extends BloodBoundEffect {

    /** Above this maximum health an entity cannot be Exposed at all. */
    public static final float MAX_AFFECTED_HEALTH = 100.0F;

    /** What the victim is held at. */
    public static final float PINNED_HEALTH = 1.0F;

    private static final int PARTICLE_INTERVAL = 4;
    private static final int PARTICLE_COUNT = 3;

    public ExposedEffect() {
        super(MobEffectCategory.HARMFUL, 0xE04C4C);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        // Held down every tick rather than only on application: regeneration, absorption running
        // out, a max-health change — anything that moves the bar has to be pushed straight back.
        if (entity.getHealth() > PINNED_HEALTH) {
            entity.setHealth(PINNED_HEALTH);
        }

        if (entity.level() instanceof ServerLevel level && entity.tickCount % PARTICLE_INTERVAL == 0) {
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.6D, entity.getZ(),
                    PARTICLE_COUNT,
                    entity.getBbWidth() * 0.4D, entity.getBbHeight() * 0.3D, entity.getBbWidth() * 0.4D,
                    0.0D);
        }
        return true;
    }
}
