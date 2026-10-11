package net.h3xpy.bloodbound.event;

import net.h3xpy.bloodbound.effect.BleedingHandler;
import net.h3xpy.bloodbound.effect.EffectDurations;
import net.h3xpy.bloodbound.effect.ExhaustedHandler;
import net.h3xpy.bloodbound.registry.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Any hostile creature can leave a mark: a player it hurts — by hand or by a shot it fired — may come
 * away Exhausted, Broken or Bleeding. Each is rolled on its own, so one blow can, rarely, do more
 * than one. Only a blow that actually hurt counts.
 */
public final class HostileWoundsHandler {

    /** Exhausted: the chance, then the bar it leaves and for how long, each rolled between the two. */
    private static final float EXHAUSTED_CHANCE = 0.04F;
    private static final int EXHAUSTED_MIN_CHARGES = 2;
    private static final int EXHAUSTED_MAX_CHARGES = 10;
    private static final int EXHAUSTED_MIN_TICKS = 5 * 20;
    private static final int EXHAUSTED_MAX_TICKS = 40 * 20;

    /** Broken: the chance, and how long, rolled between the two. */
    private static final float BROKEN_CHANCE = 0.03F;
    private static final int BROKEN_MIN_TICKS = 5 * 20;
    private static final int BROKEN_MAX_TICKS = 25 * 20;

    /** Bleeding: the chance, and how deep, rolled between the two. */
    private static final float BLEEDING_CHANCE = 0.02F;
    private static final int BLEEDING_MIN_CHARGES = 6;
    private static final int BLEEDING_MAX_CHARGES = 20;

    private HostileWoundsHandler() {}

    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getNewDamage() <= 0.0F
                || !player.isAlive() || !(event.getSource().getEntity() instanceof Enemy)) {
            return;
        }
        RandomSource random = player.getRandom();

        if (random.nextFloat() < EXHAUSTED_CHANCE) {
            ExhaustedHandler.applyCharges(player,
                    between(random, EXHAUSTED_MIN_CHARGES, EXHAUSTED_MAX_CHARGES),
                    between(random, EXHAUSTED_MIN_TICKS, EXHAUSTED_MAX_TICKS));
        }
        if (random.nextFloat() < BROKEN_CHANCE) {
            int ticks = between(random, BROKEN_MIN_TICKS, BROKEN_MAX_TICKS);
            MobEffectInstance current = player.getEffect(ModEffects.BROKEN);
            // Never shortened, and lengthened in place rather than removed and put back.
            if (current == null) {
                player.addEffect(new MobEffectInstance(ModEffects.BROKEN, ticks, 0, false, true, true));
            } else if (current.getDuration() < ticks) {
                EffectDurations.set(player, current, ticks);
            }
        }
        if (random.nextFloat() < BLEEDING_CHANCE) {
            BleedingHandler.apply(player, between(random, BLEEDING_MIN_CHARGES, BLEEDING_MAX_CHARGES));
        }
    }

    /** A whole number from min to max, both included. */
    private static int between(RandomSource random, int min, int max) {
        return min + random.nextInt(max - min + 1);
    }
}
