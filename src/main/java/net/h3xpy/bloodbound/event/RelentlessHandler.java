package net.h3xpy.bloodbound.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.h3xpy.bloodbound.damage.PerkDamageSource;
import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.network.PerkChargesPayload;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Relentless: dropping under the threshold opens a window of a few seconds in which every blow is
 * followed by a short chain of weaker ones. The cooldown starts once the window closes.
 */
public final class RelentlessHandler {

    /** One queued follow-up blow. */
    private record ChainHit(UUID attackerId, UUID targetId, ResourceKey<Level> dimension, float damage,
            long fireAt) {}

    private static final List<ChainHit> PENDING = new ArrayList<>();
    /** Players whose window is open, and until when. */
    private static final Map<UUID, Long> OPEN_UNTIL = new HashMap<>();

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
        // Wooden Sword drops the health condition: the first blow landed opens the window.
        if (data.isAddonActive(ModAddons.WOODEN_SWORD)) {
            tryOpen(attacker, data, tier, gameTime);
        }
        if (!isOpen(attacker.getUUID(), gameTime)) {
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
    }

    /** Taking a blow that leaves the bearer under the threshold opens the window. */
    @SubscribeEvent
    public static void onHurt(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive()) {
            return;
        }
        PlayerPerkData data = PerkDataManager.get(player);
        int tier = data.getActiveTier(ModPerks.RELENTLESS);
        if (tier > 0 && !data.isAddonActive(ModAddons.WOODEN_SWORD) && player.getHealth() <= threshold(data, tier)) {
            tryOpen(player, data, tier, player.level().getGameTime());
        }
    }

    /**
     * Still under the threshold when the cooldown runs out: it goes off again straight away, without
     * waiting for another blow. Called once a tick per player.
     */
    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        int tier = data.getActiveTier(ModPerks.RELENTLESS);
        if (tier > 0 && player.isAlive() && !data.isAddonActive(ModAddons.WOODEN_SWORD)
                && player.getHealth() <= threshold(data, tier)) {
            tryOpen(player, data, tier, gameTime);
        }
    }

    /** Opens the window, unless it is open already or the perk is cooling down. */
    private static void tryOpen(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        if (isOpen(player.getUUID(), gameTime) || data.isOnCooldown(ModPerks.RELENTLESS.id(), gameTime)) {
            return;
        }
        long until = gameTime + ModPerks.RELENTLESS.ticks(ModPerks.RELENTLESS_WINDOW, tier);
        OPEN_UNTIL.put(player.getUUID(), until);
        player.level().playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS,
                0.5F, 1.4F);
        // Shown on the slot as a single pip running down with the window.
        PacketDistributor.sendToPlayer(player, new PerkChargesPayload(ModPerks.RELENTLESS.id(), 1, 1, until));
    }

    private static boolean isOpen(UUID id, long gameTime) {
        Long until = OPEN_UNTIL.get(id);
        return until != null && gameTime < until;
    }

    /** Closes the windows that have run out, and starts the cooldown each one leaves behind. */
    private static void closeWindows(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Long>> iterator = OPEN_UNTIL.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }
            long gameTime = player.level().getGameTime();
            if (gameTime < entry.getValue()) {
                continue;
            }
            iterator.remove();
            PlayerPerkData data = PerkDataManager.get(player);
            int tier = Math.max(1, data.getUnlockedTier(ModPerks.RELENTLESS.id()));
            int cooldown = ModPerks.RELENTLESS.cooldownTicks(tier);
            if (data.isAddonActive(ModAddons.WOODEN_SWORD)) {
                cooldown = Math.round(cooldown * ModAddons.WOODEN_SWORD_COOLDOWN);
            }
            data.setCooldown(ModPerks.RELENTLESS.id(), gameTime, cooldown);
            PerkDataManager.sync(player);
            PacketDistributor.sendToPlayer(player, new PerkChargesPayload(ModPerks.RELENTLESS.id(), -1, 0, 0L));
        }
    }

    /** The health, in half-hearts, at or under which Relentless is in play, addons included. */
    public static float threshold(PlayerPerkData data, int tier) {
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
        if (!OPEN_UNTIL.isEmpty()) {
            closeWindows(event.getServer());
        }
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
        OPEN_UNTIL.remove(playerId);
        PENDING.removeIf(hit -> hit.attackerId().equals(playerId) || hit.targetId().equals(playerId));
    }
}
