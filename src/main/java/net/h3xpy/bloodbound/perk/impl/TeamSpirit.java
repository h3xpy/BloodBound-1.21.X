package net.h3xpy.bloodbound.perk.impl;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Team Spirit: what the medic gives out is worth something for as long as you stay with them.
 * <p>
 * A single point of health earns the boons; staying in range is what keeps them. Walk out and there
 * are three seconds to think better of it, after which they are gone for good and have to be earned
 * again. The effects are granted in short slices rather than for their whole length, which is what
 * makes leaving actually take them away.
 */
public final class TeamSpirit {

    /** Someone carrying the boons: who gave them, when they run out, and the grace clock. */
    private static final class Boon {
        private final UUID sourceId;
        private final int tier;
        private long expiresAt;
        private long graceUntil;

        private Boon(UUID sourceId, int tier, long expiresAt) {
            this.sourceId = sourceId;
            this.tier = tier;
            this.expiresAt = expiresAt;
        }
    }

    /** Effects are handed out in slices this long and topped up while the holder stays close. */
    private static final int SLICE_TICKS = 40;

    private static final Map<UUID, Boon> BOONS = new HashMap<>();
    /** Health each medic has put into each patient since the last payout. */
    private static final Map<UUID, Map<UUID, Float>> DELIVERED = new HashMap<>();

    private TeamSpirit() {}

    /** Health the bearer put into somebody else. A point of it is all the boons cost. */
    public static void recordHealing(ServerPlayer healer, ServerPlayer target, float amount) {
        if (healer == target || amount <= 0.0F) {
            return;
        }
        int tier = PerkDataManager.get(healer).getActiveTier(ModPerks.TEAM_SPIRIT);
        if (tier <= 0) {
            return;
        }

        Map<UUID, Float> patients = DELIVERED.computeIfAbsent(healer.getUUID(), id -> new HashMap<>());
        float given = patients.getOrDefault(target.getUUID(), 0.0F) + amount;
        if (given < ModPerks.SPIRIT_MIN_HEAL) {
            patients.put(target.getUUID(), given);
            return;
        }
        patients.put(target.getUUID(), 0.0F);

        long expiresAt = target.level().getGameTime()
                + ModPerks.TEAM_SPIRIT.ticks(ModPerks.SPIRIT_DURATION, tier);
        Boon existing = BOONS.get(target.getUUID());
        if (existing != null && existing.sourceId.equals(healer.getUUID())) {
            // Healing the same player again only ever pushes the clock out.
            existing.expiresAt = Math.max(existing.expiresAt, expiresAt);
            existing.graceUntil = 0L;
            return;
        }

        BOONS.put(target.getUUID(), new Boon(healer.getUUID(), tier, expiresAt));
        target.displayClientMessage(Component.translatable("bloodbound.message.team_spirit_gained")
                .withStyle(ChatFormatting.GOLD), true);
        target.level().playSound(null, target.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 0.5F, 1.6F);
    }

    /** Keeps the boons topped up, and takes them away from anybody who wandered off. */
    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        Boon boon = BOONS.get(player.getUUID());
        if (boon == null) {
            return;
        }
        if (gameTime >= boon.expiresAt || !player.isAlive()) {
            drop(player, "bloodbound.message.team_spirit_faded");
            return;
        }

        ServerPlayer source = player.serverLevel().getServer().getPlayerList().getPlayer(boon.sourceId);
        int tier = source == null ? 0 : PerkDataManager.get(source).getActiveTier(ModPerks.TEAM_SPIRIT);
        boolean close = false;
        if (source != null && tier > 0 && source.level() == player.level()) {
            double radius = ModPerks.TEAM_SPIRIT.value(ModPerks.SPIRIT_RADIUS, tier);
            close = source.distanceToSqr(player) <= radius * radius;
        }

        if (close) {
            boon.graceUntil = 0L;
            grant(player, tier);
            return;
        }

        // Out of range: a few seconds to come back, then it is gone and has to be earned again.
        if (boon.graceUntil == 0L) {
            boon.graceUntil = gameTime + ModPerks.SPIRIT_GRACE_TICKS;
            player.displayClientMessage(Component.translatable("bloodbound.message.team_spirit_leaving")
                    .withStyle(ChatFormatting.YELLOW), true);
        }
        if (gameTime >= boon.graceUntil) {
            drop(player, "bloodbound.message.team_spirit_lost");
        } else {
            grant(player, boon.tier);
        }
    }

    private static void grant(ServerPlayer player, int tier) {
        int haste = ModPerks.TEAM_SPIRIT.intValue(ModPerks.SPIRIT_HASTE, tier);
        top(player, MobEffects.DIG_SPEED, haste - 1);
        top(player, MobEffects.DAMAGE_BOOST, ModPerks.SPIRIT_STRENGTH_LEVEL - 1);
    }

    /** Refreshes one effect, leaving a stronger one the player already had alone. */
    private static void top(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
            int amplifier) {
        MobEffectInstance current = player.getEffect(effect);
        if (current != null && current.getAmplifier() > amplifier) {
            return;
        }
        if (current == null || current.getDuration() < SLICE_TICKS / 2) {
            player.addEffect(new MobEffectInstance(effect, SLICE_TICKS, amplifier, false, true, true));
        }
    }

    private static void drop(ServerPlayer player, String messageKey) {
        Boon boon = BOONS.remove(player.getUUID());
        if (boon == null) {
            return;
        }
        // Only what we granted comes off: a short slice is ours, a potion is not.
        strip(player, MobEffects.DIG_SPEED);
        strip(player, MobEffects.DAMAGE_BOOST);
        player.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.DARK_GRAY), true);
    }

    private static void strip(ServerPlayer player,
            net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        MobEffectInstance current = player.getEffect(effect);
        if (current != null && current.getDuration() <= SLICE_TICKS) {
            player.removeEffect(effect);
        }
    }

    /** Forgets a player entirely, on logout or death. */
    public static void clear(UUID playerId) {
        BOONS.remove(playerId);
        DELIVERED.remove(playerId);
        DELIVERED.values().forEach(patients -> patients.remove(playerId));

        Iterator<Map.Entry<UUID, Boon>> iterator = BOONS.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().sourceId.equals(playerId)) {
                iterator.remove();
            }
        }
    }
}
