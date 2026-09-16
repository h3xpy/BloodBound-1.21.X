package net.h3xpy.bloodbound.effect;

import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Changes how long an effect has left without taking it off.
 * <p>
 * Removing an effect and adding a shorter copy looks equivalent but is not: it fires the removal
 * event, and everything listening for that — the bars behind Bleeding and Exhausted, the watcher
 * behind an aura reveal — takes it as the effect ending. Writing the duration directly avoids that,
 * at the cost of telling the client by hand, which never hears about a duration changing otherwise.
 */
public final class EffectDurations {

    private EffectDurations() {}

    public static void set(LivingEntity entity, MobEffectInstance instance, int duration) {
        instance.duration = Math.max(1, duration);
        if (entity instanceof ServerPlayer player && player.hasEffect(instance.getEffect())) {
            player.connection.send(new ClientboundUpdateMobEffectPacket(player.getId(), instance, false));
        }
    }
}
