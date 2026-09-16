package net.h3xpy.bloodbound.event;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.h3xpy.bloodbound.damage.PerkDamageSource;
import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Relentless: while wounded, every blow is followed by a short chain of weaker ones.
 */
public final class RelentlessHandler {

    /** One queued follow-up blow. */
    private record ChainHit(UUID attackerId, UUID targetId, ResourceKey<Level> dimension, float damage,
            long fireAt) {}

    private static final List<ChainHit> PENDING = new ArrayList<>();

    /** Set while a chain hit is being dealt, so a chain can never start another chain. */
    private static boolean firingChain;

    private RelentlessHandler() {}

    @SubscribeEvent
    public static void onAttack(LivingIncomingDamageEvent event) {
        if (firingChain || !(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        // Melee only: this is about swinging a weapon, not arrows or fire.
        if (!event.getSource().is(DamageTypes.PLAYER_ATTACK)) {
            return;
        }

        PlayerPerkData data = PerkDataManager.get(attacker);
        int tier = data.getActiveTier(ModPerks.RELENTLESS);
        if (tier <= 0) {
            return;
        }

        long gameTime = attacker.level().getGameTime();
        if (data.isOnCooldown(ModPerks.RELENTLESS.id(), gameTime)) {
            return;
        }
        // Wooden Sword drops the condition entirely; the other two move where it sits.
        if (!data.isAddonActive(ModAddons.WOODEN_SWORD) && attacker.getHealth() > threshold(data, tier)) {
            return;
        }

        int hits = Math.min(ModPerks.RELENTLESS.intValue(ModPerks.RELENTLESS_HITS, tier),
                ModPerks.RELENTLESS_CHAIN_FRACTIONS.length);
        float opening = event.getAmount();
        LivingEntity target = event.getEntity();

        for (int i = 0; i < hits; i++) {
            queueHit(attacker, target, opening * ModPerks.RELENTLESS_CHAIN_FRACTIONS[i], gameTime, i);
        }
        // Bloodied Matchete waits longer to fire and lands one more blow for it.
        if (data.isAddonActive(ModAddons.BLOODIED_MATCHETE)) {
            float share = ModAddons.BLOODIED_MATCHETE_EXTRA_HIT[Math.clamp(tier - 1, 0,
                    ModAddons.BLOODIED_MATCHETE_EXTRA_HIT.length - 1)];
            queueHit(attacker, target, opening * share, gameTime, hits);
        }

        int cooldown = ModPerks.RELENTLESS.cooldownTicks(tier);
        if (data.isAddonActive(ModAddons.WOODEN_SWORD)) {
            cooldown = Math.round(cooldown * ModAddons.WOODEN_SWORD_COOLDOWN);
        }
        data.setCooldown(ModPerks.RELENTLESS.id(), gameTime, cooldown);
        PerkDataManager.sync(attacker);
    }

    /** The health the chain starts at, in half-hearts. The perk writes its own in hearts. */
    private static float threshold(PlayerPerkData data, int tier) {
        int index = Math.clamp(tier - 1, 0, 2);
        if (data.isAddonActive(ModAddons.WILD_ROSE)) {
            return (float) (ModAddons.WILD_ROSE_HEARTS[index] * 2.0D);
        }
        if (data.isAddonActive(ModAddons.BLOODIED_MATCHETE)) {
            return (float) (ModAddons.BLOODIED_MATCHETE_HEARTS[index] * 2.0D);
        }
        return (float) (ModPerks.RELENTLESS.value(ModPerks.RELENTLESS_HEARTS, tier) * 2.0D);
    }

    /** Queues the {@code index}-th follow-up, spaced out by the chain's own interval. */
    private static void queueHit(ServerPlayer attacker, LivingEntity target, float damage, long gameTime,
            int index) {
        long delay = Math.round(ModPerks.RELENTLESS_INTERVAL_SECONDS * 20.0D * (index + 1));
        PENDING.add(new ChainHit(attacker.getUUID(), target.getUUID(), attacker.level().dimension(),
                damage, gameTime + delay));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        Iterator<ChainHit> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            ChainHit hit = iterator.next();
            ServerLevel level = event.getServer().getLevel(hit.dimension());
            if (level == null) {
                iterator.remove();
                continue;
            }
            if (level.getGameTime() < hit.fireAt()) {
                continue;
            }
            iterator.remove();
            deliver(level, hit);
        }
    }

    private static void deliver(ServerLevel level, ChainHit hit) {
        Entity attacker = level.getEntity(hit.attackerId());
        Entity target = level.getEntity(hit.targetId());
        if (!(attacker instanceof ServerPlayer player) || !(target instanceof LivingEntity victim)
                || !victim.isAlive()) {
            return;
        }

        firingChain = true;
        try {
            // Follow-ups land inside the invulnerability window the opening blow opened, and they
            // are weaker than it, so without clearing the timer they would be swallowed whole.
            victim.invulnerableTime = 0;
            victim.hurt(PerkDamageSource.of(player.damageSources().playerAttack(player), "relentless"), hit.damage());
        } finally {
            firingChain = false;
        }
    }

    /** Drops anything queued for a player who has left, so nothing fires into a stale world. */
    public static void clear(UUID playerId) {
        PENDING.removeIf(hit -> hit.attackerId().equals(playerId) || hit.targetId().equals(playerId));
    }
}
