package net.h3xpy.bloodbound.event;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.h3xpy.bloodbound.effect.ExposedEffect;
import net.h3xpy.bloodbound.registry.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * Runtime behaviour of BloodBound's own status effects.
 */
public final class EffectHandler {

    /** Health each Exposed victim had when it was applied, waiting to be handed back. */
    private static final Map<UUID, Float> EXPOSED_HEALTH = new HashMap<>();

    private EffectHandler() {}

    /**
     * Broken blocks healing outright, and Exposed keeps its victim pinned at one point. Every heal
     * in the game — regeneration, food, golden apples, potions, and BloodBound's own perks — goes
     * through this event, so one check covers them all.
     */
    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.hasEffect(ModEffects.BROKEN) || entity.hasEffect(ModEffects.EXPOSED)) {
            event.setCanceled(true);
        }
    }

    /** Anything tough enough simply cannot be Exposed. */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (event.getEffectInstance().is(ModEffects.EXPOSED)
                && event.getEntity().getMaxHealth() > ExposedEffect.MAX_AFFECTED_HEALTH) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    /** Remembers the health Exposed is about to take away, and takes it. */
    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (!event.getEffectInstance().is(ModEffects.EXPOSED)) {
            return;
        }
        LivingEntity entity = event.getEntity();
        // A refresh must not overwrite the health banked by the first application, or a second
        // Exposed while the first is running would hand back a single point of health.
        if (event.getOldEffectInstance() == null) {
            EXPOSED_HEALTH.put(entity.getUUID(), entity.getHealth());
        }
        if (entity.getHealth() > ExposedEffect.PINNED_HEALTH) {
            entity.setHealth(ExposedEffect.PINNED_HEALTH);
        }
    }

    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        if (event.getEffectInstance() != null && event.getEffectInstance().is(ModEffects.EXPOSED)) {
            restore(event.getEntity());
        }
    }

    /** Milk, a command or a death all come through here rather than through expiry. */
    @SubscribeEvent
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance != null && instance.is(ModEffects.EXPOSED)) {
            restore(event.getEntity());
        }
    }

    /**
     * Hands back the health Exposed took. Healing is blocked while the effect is on, so this runs
     * as a direct write rather than a heal — by the time it is called the effect is already gone,
     * but going through {@code heal} would let a Broken victim lose the health for good.
     */
    private static void restore(LivingEntity entity) {
        Float previous = EXPOSED_HEALTH.remove(entity.getUUID());
        if (previous == null || !entity.isAlive()) {
            return;
        }
        entity.setHealth(Math.min(previous, entity.getMaxHealth()));
    }
}
