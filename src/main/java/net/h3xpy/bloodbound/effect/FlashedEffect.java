package net.h3xpy.bloodbound.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Flashed: blinded outright.
 * <p>
 * A player sees nothing but white until the last second, when it fades. A mob loses whatever it was
 * chasing and cannot pick a target up again while it lasts. The white screen is drawn by the client
 * overlay; everything else is here.
 */
public class FlashedEffect extends BloodBoundEffect {

    /** How long the white takes to clear at the end, in ticks. */
    public static final int FADE_TICKS = 20;

    private static final int PARTICLE_INTERVAL = 3;
    private static final int PARTICLE_COUNT = 4;

    public FlashedEffect() {
        super(MobEffectCategory.HARMFUL, 0xFFFFFF);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        // Dropped every tick, not just when it lands: a mob that finds something new while blind
        // should lose it again immediately.
        if (entity instanceof Mob mob) {
            mob.setTarget(null);
            mob.setLastHurtByMob(null);
        }

        if (entity.level() instanceof ServerLevel level && entity.tickCount % PARTICLE_INTERVAL == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    entity.getX(), entity.getEyeY(), entity.getZ(), PARTICLE_COUNT,
                    entity.getBbWidth() * 0.5D, entity.getBbHeight() * 0.25D, entity.getBbWidth() * 0.5D,
                    0.02D);
        }
        return true;
    }
}
