package net.h3xpy.bloodbound.effect;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.h3xpy.bloodbound.network.EffectChargesPayload;
import net.h3xpy.bloodbound.registry.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Exhausted: lungs that only hold so much.
 * <p>
 * A stamina bar rather than a plain debuff. Running spends it a charge a second, easing off fills it
 * back at half that, and running it to nothing costs the sprint and brings Slowness III until three
 * charges are back. Nobody exhausted can be given Speed either: the bar is the pace. A mob has no
 * sprint, so simply moving is what spends it.
 */
public final class ExhaustedHandler {

    /** Charges the bar holds at level one; every second level takes one off. */
    private static final int BASE_CHARGES = 5;
    /** Ticks of work a charge costs, and ticks of rest a charge takes to come back. */
    private static final int SPEND_TICKS = 20;
    private static final int REFILL_TICKS = 40;
    /** Charges that have to be back before a winded entity may run again. */
    private static final int RECOVER_CHARGES = 3;
    /** Slowness level while winded (III). */
    private static final int SLOWNESS_LEVEL = 3;
    /** Speed under which a mob counts as standing still. */
    private static final double MOB_MOVE_EPSILON = 0.01D;

    /** One entity's lungs: charges left, whether it is winded, and the two clocks. */
    private static final class Stamina {
        private int charges;
        private boolean winded;
        private int spendTicks;
        private int refillTicks;

        private Stamina(int charges) {
            this.charges = charges;
        }
    }

    private static final Map<UUID, Stamina> STAMINA = new HashMap<>();

    private ExhaustedHandler() {}

    /** How big the bar is at a given effect level: five, less one for every second level. */
    public static int maxCharges(int amplifier) {
        int level = amplifier + 1;
        return Math.max(1, BASE_CHARGES - (level - 1) / 2);
    }

    /** Charges needed back before the sprint returns, never more than the bar holds. */
    public static int recoverCharges(int max) {
        return Math.min(RECOVER_CHARGES, max);
    }

    // --- no Speed while exhausted ---

    @SubscribeEvent
    public static void onApplicable(MobEffectEvent.Applicable event) {
        if (event.getEffectInstance().is(MobEffects.MOVEMENT_SPEED)
                && event.getEntity().hasEffect(ModEffects.EXHAUSTED)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    /** Speed already running when the exhaustion lands goes with it. */
    @SubscribeEvent
    public static void onAdded(MobEffectEvent.Added event) {
        if (event.getEffectInstance().is(ModEffects.EXHAUSTED) && !event.getEntity().level().isClientSide) {
            event.getEntity().removeEffect(MobEffects.MOVEMENT_SPEED);
        }
    }

    // --- the effect ending ---

    @SubscribeEvent
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        if (event.getEffectInstance() != null && event.getEffectInstance().is(ModEffects.EXHAUSTED)) {
            recover(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        if (event.getEffectInstance() != null && event.getEffectInstance().is(ModEffects.EXHAUSTED)) {
            recover(event.getEntity());
        }
    }

    /** The effect ending hands the sprint back and takes the Slowness off. */
    private static void recover(LivingEntity entity) {
        Stamina stamina = STAMINA.remove(entity.getUUID());
        if (stamina != null && stamina.winded) {
            entity.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
        send(entity, null, 0);
    }

    // --- the bar ---

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity.level().isClientSide) {
            return;
        }
        MobEffectInstance effect = entity.getEffect(ModEffects.EXHAUSTED);
        if (effect == null) {
            if (STAMINA.containsKey(entity.getUUID())) {
                recover(entity);
            }
            return;
        }

        int max = maxCharges(effect.getAmplifier());
        Stamina stamina = STAMINA.computeIfAbsent(entity.getUUID(), id -> new Stamina(max));
        // A stronger dose landing on top shrinks the bar under it.
        stamina.charges = Math.min(stamina.charges, max);
        int before = stamina.charges;
        boolean wasWinded = stamina.winded;

        if (!stamina.winded && isWorking(entity)) {
            stamina.refillTicks = 0;
            if (++stamina.spendTicks >= SPEND_TICKS) {
                stamina.spendTicks = 0;
                stamina.charges = Math.max(0, stamina.charges - 1);
            }
            if (stamina.charges <= 0) {
                stamina.winded = true;
            }
        } else {
            // The spend clock is kept, not reset: a run broken up by jumps is billed for all of it.
            if (stamina.charges < max && ++stamina.refillTicks >= REFILL_TICKS) {
                stamina.refillTicks = 0;
                stamina.charges++;
            }
            if (stamina.winded && stamina.charges >= recoverCharges(max)) {
                stamina.winded = false;
                entity.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            }
        }

        if (stamina.winded) {
            keepWinded(entity);
        }

        if (before != stamina.charges || wasWinded != stamina.winded || entity.tickCount % 20 == 0) {
            send(entity, stamina, max);
        }
    }

    /** A player has to be running to be working at it; a mob only has to be going somewhere. */
    private static boolean isWorking(LivingEntity entity) {
        if (entity instanceof Player) {
            return MovementTracker.isRunning(entity);
        }
        return entity.getDeltaMovement().horizontalDistanceSqr() > MOB_MOVE_EPSILON * MOB_MOVE_EPSILON;
    }

    /** Slowness in short slices, refreshed while winded, so it never outlives the breather. */
    private static void keepWinded(LivingEntity entity) {
        MobEffectInstance current = entity.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (current == null || current.getDuration() < 30) {
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40,
                    SLOWNESS_LEVEL - 1, false, false, true));
        }
        entity.setSprinting(false);
    }

    /**
     * The bar, for the player carrying it. A winded bar is reported with its maximum negated, which
     * the overlay reads back apart; a maximum of zero means the effect is gone.
     */
    private static void send(LivingEntity entity, Stamina stamina, int max) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        int charges = stamina == null ? 0 : stamina.charges;
        int reportedMax = stamina == null ? 0 : (stamina.winded ? -max : max);
        PacketDistributor.sendToPlayer(player, new EffectChargesPayload(
                ModEffects.EXHAUSTED.getKey().location(), charges, reportedMax));
    }

    /** A death ends the exhaustion, and takes its bar off the screen with it. */
    public static void onDeath(LivingEntity entity) {
        clear(entity.getUUID());
        send(entity, null, 0);
    }

    /** Forgets an entity entirely, on logout or death. */
    public static void clear(UUID entityId) {
        STAMINA.remove(entityId);
    }
}
