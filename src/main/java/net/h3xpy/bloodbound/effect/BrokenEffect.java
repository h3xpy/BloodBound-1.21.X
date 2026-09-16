package net.h3xpy.bloodbound.effect;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Broken: the entity cannot heal by any means for the duration.
 * <p>
 * The block itself lives in the heal event handler, since every form of healing in the game funnels
 * through {@code LivingEntity#heal}. This class only handles how the effect looks.
 */
public class BrokenEffect extends BloodBoundEffect {

    /** Blue fading to red, the colours the effect is described by. */
    private static final Vector3f FROM_COLOR = new Vector3f(0.0F, 0.0F, 1.0F);
    private static final Vector3f TO_COLOR = new Vector3f(1.0F, 0.0F, 0.0F);
    private static final DustColorTransitionOptions PARTICLE =
            new DustColorTransitionOptions(FROM_COLOR, TO_COLOR, 1.0F);

    /** Ticks between particle puffs; every tick would be a blizzard. */
    private static final int PARTICLE_INTERVAL = 5;
    private static final int PARTICLE_COUNT = 4;

    public BrokenEffect() {
        // The bar colour sits between the two particle colours.
        super(MobEffectCategory.HARMFUL, 0x8000C0);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % PARTICLE_INTERVAL == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(PARTICLE,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.6D, entity.getZ(),
                    PARTICLE_COUNT,
                    entity.getBbWidth() * 0.4D, entity.getBbHeight() * 0.3D, entity.getBbWidth() * 0.4D,
                    0.0D);
        }
        return true;
    }
}
