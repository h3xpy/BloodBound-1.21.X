package net.h3xpy.bloodbound.event;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.h3xpy.bloodbound.effect.EffectDurations;
import net.h3xpy.bloodbound.effect.MovementTracker;
import net.h3xpy.bloodbound.network.AuraRevealPayload;
import net.h3xpy.bloodbound.registry.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Aura Revealed: keeps track of who revealed whom, so the outline can be sent to that one viewer
 * and taken back the moment the effect ends.
 * <p>
 * The effect on the victim is the source of truth for how long it lasts; this only remembers who is
 * watching, which is not something a status effect can carry.
 */
public final class AuraRevealHandler {

    /** Victim to the player who revealed them. */
    private static final Map<UUID, UUID> WATCHERS = new HashMap<>();

    private AuraRevealHandler() {}

    /** Reveals a target's aura to one player and starts the effect on the target. */
    public static void reveal(ServerPlayer viewer, LivingEntity target, int durationTicks) {
        if (target instanceof ServerPlayer hidden && UnderTheRadarHandler.blocksReveal(hidden)) {
            return;
        }
        target.addEffect(new MobEffectInstance(ModEffects.AURA_REVEALED, durationTicks, 0, false, true, true));
        // Only track it if the effect actually took: something immune would leave a stale watcher.
        if (!target.hasEffect(ModEffects.AURA_REVEALED)) {
            return;
        }
        WATCHERS.put(target.getUUID(), viewer.getUUID());
        PacketDistributor.sendToPlayer(viewer, new AuraRevealPayload(target.getId(), durationTicks));
    }

    /**
     * Bounty Hunter License: a reveal the viewer keeps their eyes on does not run out.
     * <p>
     * Topped up a slice at a time rather than made permanent, so looking away is what ends it —
     * which is the whole trade: the licence costs you your attention.
     */
    public static void topUpWatched(ServerPlayer viewer, double halfAngleDegrees, int ticks) {
        if (WATCHERS.isEmpty()) {
            return;
        }
        double minimumDot = Math.cos(Math.toRadians(halfAngleDegrees));
        Vec3 eyes = viewer.getEyePosition();
        Vec3 look = viewer.getLookAngle();
        Vec3 far = eyes.add(look.scale(128.0D));

        for (Map.Entry<UUID, UUID> entry : WATCHERS.entrySet()) {
            if (!entry.getValue().equals(viewer.getUUID())) {
                continue;
            }
            Entity target = viewer.serverLevel().getEntity(entry.getKey());
            if (!(target instanceof LivingEntity living) || !living.isAlive()) {
                continue;
            }

            // Looking at it means the crosshair is on it, or near enough: either the line of sight
            // passes through its box, or it sits inside a narrow cone round that line. The box test
            // is what makes it work up close, where a cone alone is far too tight.
            Vec3 offset = living.getBoundingBox().getCenter().subtract(eyes);
            boolean onCrosshair = living.getBoundingBox().inflate(0.3D).clip(eyes, far).isPresent();
            boolean inCone = offset.lengthSqr() > 1.0E-6D && offset.normalize().dot(look) >= minimumDot;
            if (!onCrosshair && !inCone) {
                continue;
            }

            MobEffectInstance current = living.getEffect(ModEffects.AURA_REVEALED);
            if (current != null && current.getDuration() < ticks) {
                EffectDurations.set(living, current, ticks);
                // The outline on the viewer's screen runs its own clock off what it was last sent.
                // Topping the effect up without telling it is exactly why the aura still vanished.
                PacketDistributor.sendToPlayer(viewer, new AuraRevealPayload(living.getId(), ticks));
            }
        }
    }

    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        if (event.getEffectInstance() != null && event.getEffectInstance().is(ModEffects.AURA_REVEALED)) {
            conceal(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance != null && instance.is(ModEffects.AURA_REVEALED)) {
            conceal(event.getEntity());
        }
    }

    /** Tells the watcher to drop the outline, whether the effect ran out or was cured. */
    private static void conceal(LivingEntity target) {
        UUID watcherId = WATCHERS.remove(target.getUUID());
        if (watcherId == null || target.level().isClientSide) {
            return;
        }
        Entity watcher = target.level().getServer() == null ? null
                : target.level().getServer().getPlayerList().getPlayer(watcherId);
        if (watcher instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new AuraRevealPayload(target.getId(), 0));
        }
    }
}
