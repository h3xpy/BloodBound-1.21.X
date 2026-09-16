package net.h3xpy.bloodbound.event;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Echoing Wounds: a blow jumps from whatever you hit to the nearest thing beside it, and on again,
 * losing a slice of its damage every time.
 * <p>
 * The chain never comes back to the player who started it, and never hits the same entity twice.
 * The cooldown is only paid when the echo actually finds somebody: a swing in an empty field costs
 * nothing.
 */
public final class EchoingWoundsHandler {

    /** One queued jump: hit {@code targetId}, then look around it for the next one. */
    private record EchoJump(UUID attackerId, UUID targetId, ResourceKey<Level> dimension, float damage,
            int jumpsLeft, long fireAt, Set<UUID> alreadyHit) {}

    private static final List<EchoJump> PENDING = new ArrayList<>();

    /** Set while an echo is landing, so an echo can never start another echo. */
    private static boolean echoing;

    private EchoingWoundsHandler() {}

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (echoing || !(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }

        PlayerPerkData data = PerkDataManager.get(attacker);
        int tier = data.getActiveTier(ModPerks.ECHOING_WOUNDS);
        if (tier <= 0) {
            return;
        }

        long gameTime = attacker.level().getGameTime();
        if (data.isOnCooldown(ModPerks.ECHOING_WOUNDS.id(), gameTime)) {
            return;
        }

        LivingEntity struck = event.getEntity();
        Set<UUID> hit = new HashSet<>();
        hit.add(struck.getUUID());

        // Looked up now rather than when the jump fires: the perk only goes on cooldown if the echo
        // has somewhere to go, so that has to be known before anything is charged for.
        LivingEntity next = nearestNeighbour(attacker, struck, hit, data);
        if (next == null) {
            return;
        }

        int jumps = ModPerks.ECHOING_WOUNDS.intValue(ModPerks.ECHOING_WOUNDS_BOUNCES, tier);
        if (data.isAddonActive(ModAddons.BLOODIED_LETTER)) {
            jumps += ModAddons.BLOODIED_LETTER_EXTRA_JUMPS;
        }

        PENDING.add(new EchoJump(attacker.getUUID(), next.getUUID(), attacker.level().dimension(),
                openingDamage(event.getAmount(), tier, data), jumps,
                gameTime + ModPerks.ECHOING_WOUNDS_INTERVAL_TICKS, hit));

        int cooldown = ModPerks.ECHOING_WOUNDS.cooldownTicks(tier);
        if (data.isAddonActive(ModAddons.HYSTERIA)) {
            cooldown += ModAddons.HYSTERIA_EXTRA_COOLDOWN_SECONDS * 20;
        }
        data.setCooldown(ModPerks.ECHOING_WOUNDS.id(), gameTime, cooldown);
        PerkDataManager.sync(attacker);
    }

    /**
     * What the first echo carries. Normally a slice off the opening blow; Wrapped Glass starts it
     * lower than that, and then builds it up on every jump instead of letting it fade.
     */
    private static float openingDamage(float amount, int tier, PlayerPerkData data) {
        if (data.isAddonActive(ModAddons.WRAPPED_GLASS)) {
            return amount * ModAddons.WRAPPED_GLASS_FIRST_HIT;
        }
        float falloff = (float) ModPerks.ECHOING_WOUNDS.value(ModPerks.ECHOING_WOUNDS_FALLOFF, tier) / 100.0F;
        return amount * (1.0F - falloff);
    }

    /** What the jump after this one carries. */
    private static float nextDamage(float damage, int tier, PlayerPerkData data) {
        if (data.isAddonActive(ModAddons.WRAPPED_GLASS)) {
            return damage * ModAddons.WRAPPED_GLASS_GROWTH[Math.clamp(tier - 1, 0,
                    ModAddons.WRAPPED_GLASS_GROWTH.length - 1)];
        }
        float falloff = (float) ModPerks.ECHOING_WOUNDS.value(ModPerks.ECHOING_WOUNDS_FALLOFF, tier) / 100.0F;
        return damage * (1.0F - falloff);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        Iterator<EchoJump> iterator = PENDING.iterator();
        List<EchoJump> queued = new ArrayList<>();

        while (iterator.hasNext()) {
            EchoJump jump = iterator.next();
            ServerLevel level = event.getServer().getLevel(jump.dimension());
            if (level == null) {
                iterator.remove();
                continue;
            }
            if (level.getGameTime() < jump.fireAt()) {
                continue;
            }
            iterator.remove();

            EchoJump next = deliver(level, jump);
            if (next != null) {
                queued.add(next);
            }
        }
        PENDING.addAll(queued);
    }

    /** Lands one jump and works out the next, or null when the chain ends here. */
    @Nullable
    private static EchoJump deliver(ServerLevel level, EchoJump jump) {
        Entity attacker = level.getEntity(jump.attackerId());
        Entity target = level.getEntity(jump.targetId());
        if (!(attacker instanceof ServerPlayer player) || !(target instanceof LivingEntity victim)
                || !victim.isAlive()) {
            return null;
        }

        PlayerPerkData data = PerkDataManager.get(player);
        int tier = data.getActiveTier(ModPerks.ECHOING_WOUNDS);

        echoing = true;
        try {
            // The echo lands inside the invulnerability window the original blow opened, so without
            // clearing the timer it would be swallowed whole.
            victim.invulnerableTime = 0;
            victim.hurt(player.damageSources().playerAttack(player), jump.damage());
        } finally {
            echoing = false;
        }

        if (data.isAddonActive(ModAddons.HYSTERIA)) {
            victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,
                    ModAddons.HYSTERIA_EFFECT_TICKS, 0, false, true, true));
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    ModAddons.HYSTERIA_EFFECT_TICKS, 0, false, true, true));
        }

        // Blood Thirsty Skull only pays once the chain has actually carried, so a single jump is
        // worth nothing and a long one is worth having.
        if (data.isAddonActive(ModAddons.BLOOD_THIRSTY_SKULL) && jump.alreadyHit().size() > 1) {
            player.heal(ModAddons.BLOOD_THIRSTY_SKULL_HEAL);
        }

        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                victim.getX(), victim.getY() + victim.getBbHeight() / 2.0D, victim.getZ(),
                8, 0.25D, 0.35D, 0.25D, 0.02D);

        int jumpsLeft = jump.jumpsLeft() - 1;
        if (jumpsLeft <= 0) {
            return null;
        }

        Set<UUID> hit = new HashSet<>(jump.alreadyHit());
        hit.add(victim.getUUID());
        LivingEntity next = nearestNeighbour(player, victim, hit, data);
        if (next == null) {
            return null;
        }

        return new EchoJump(jump.attackerId(), next.getUUID(), jump.dimension(),
                nextDamage(jump.damage(), tier, data), jumpsLeft,
                level.getGameTime() + ModPerks.ECHOING_WOUNDS_INTERVAL_TICKS, hit);
    }

    /** Nearest living thing to {@code around} the echo has not already been through. */
    @Nullable
    private static LivingEntity nearestNeighbour(ServerPlayer attacker, LivingEntity around, Set<UUID> alreadyHit,
            PlayerPerkData data) {
        double radius = ModPerks.ECHOING_WOUNDS_RADIUS;
        if (data.isAddonActive(ModAddons.ROTTING_ROPE)) {
            radius *= ModAddons.ROTTING_ROPE_RANGE;
        }
        AABB box = around.getBoundingBox().inflate(radius);

        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : around.level().getEntitiesOfClass(LivingEntity.class, box)) {
            // Never the player who threw the punch, and never the same victim twice.
            if (candidate == attacker || candidate == around || !candidate.isAlive()
                    || alreadyHit.contains(candidate.getUUID())) {
                continue;
            }
            double distance = candidate.distanceToSqr(around);
            if (distance <= radius * radius && distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Drops anything queued for a player who has left, so nothing fires into a stale world. */
    public static void clear(UUID playerId) {
        PENDING.removeIf(jump -> jump.attackerId().equals(playerId) || jump.targetId().equals(playerId));
    }
}
