package net.h3xpy.bloodbound.perk.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Green Herbs: the bearer knits back together faster than anyone should, and hands that on to
 * whoever they patch up.
 * <p>
 * The speed-up is added on top of vanilla's own healing rather than replacing it: the extra a tier
 * is worth is banked a fraction of a point at a time and paid out whole. Everything here is kept for
 * the session only — a loan is worth two minutes at most, and one carried across a logout would be
 * nonsense.
 */
public final class GreenHerbs {

    /** Green Herbs borrowed from someone else, and when it runs out. */
    private record Loan(int tier, long expiresAt) {}

    /** Fractions of a health point banked but not yet paid out. */
    private static final Map<UUID, Float> PROGRESS = new HashMap<>();
    /** Players running on someone else's perk. */
    private static final Map<UUID, Loan> LOANS = new HashMap<>();
    /** Healer to how much they have put into each patient since the last loan. */
    private static final Map<UUID, Map<UUID, Float>> DELIVERED = new HashMap<>();

    private GreenHerbs() {}

    /** Runs the faster healing, for both the bearer and anyone carrying a loan. */
    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        int tier = effectiveTier(player, data, gameTime);
        if (tier <= 0) {
            PROGRESS.remove(player.getUUID());
            return;
        }

        // Vanilla's own conditions, so the perk speeds healing up rather than inventing it: hungry
        // players still do not mend, and neither do whole ones.
        if (!player.isAlive() || player.getHealth() >= player.getMaxHealth()
                || player.getFoodData().getFoodLevel() < ModPerks.GREEN_HERBS_MIN_FOOD) {
            return;
        }

        // Only the part vanilla is not already giving: a 2x tier adds one extra point per natural
        // one, a 1.5x tier half of one.
        double extra = ModPerks.GREEN_HERBS.value(ModPerks.GREEN_HERBS_REGEN, tier) - 1.0D;
        if (extra <= 0.0D) {
            return;
        }

        float banked = PROGRESS.getOrDefault(player.getUUID(), 0.0F)
                + (float) (extra / ModPerks.GREEN_HERBS_NATURAL_REGEN_TICKS);
        while (banked >= 1.0F) {
            banked -= 1.0F;
            player.heal(1.0F);
        }
        PROGRESS.put(player.getUUID(), banked);
    }

    /**
     * The tier the player heals at: their own if they carry the perk, otherwise whatever they were
     * lent. A loan that has run out is dropped here, which is also what tells the player it is over.
     */
    private static int effectiveTier(ServerPlayer player, PlayerPerkData data, long gameTime) {
        int own = data.getActiveTier(ModPerks.GREEN_HERBS);
        if (own > 0) {
            return own;
        }

        Loan loan = LOANS.get(player.getUUID());
        if (loan == null) {
            return 0;
        }
        if (gameTime >= loan.expiresAt()) {
            LOANS.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("bloodbound.message.green_herbs_faded")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return 0;
        }
        return loan.tier();
    }

    /**
     * Health the bearer put into someone else. Once enough of it has gone in, the patient walks away
     * healing like the bearer does.
     */
    public static void recordHealing(ServerPlayer healer, ServerPlayer target, float amount) {
        if (healer == target || amount <= 0.0F) {
            return;
        }

        PlayerPerkData healerData = PerkDataManager.get(healer);
        int tier = healerData.getActiveTier(ModPerks.GREEN_HERBS);
        if (tier <= 0) {
            return;
        }
        // Someone who already carries the perk has nothing to be given.
        if (PerkDataManager.get(target).getActiveTier(ModPerks.GREEN_HERBS) > 0) {
            return;
        }

        Map<UUID, Float> patients = DELIVERED.computeIfAbsent(healer.getUUID(), id -> new HashMap<>());
        float given = patients.getOrDefault(target.getUUID(), 0.0F) + amount;

        float threshold = (float) ModPerks.GREEN_HERBS.value(ModPerks.GREEN_HERBS_SHARE_HEAL, tier);
        if (given < threshold) {
            patients.put(target.getUUID(), given);
            return;
        }

        // The count starts over, so a long heal can pass the perk on more than once — each time
        // simply pushing the same loan further out.
        patients.put(target.getUUID(), given - threshold);
        lend(healer, target, tier);
    }

    /** Hands the perk to a patient for a while. */
    private static void lend(ServerPlayer healer, ServerPlayer target, int tier) {
        long expiresAt = target.level().getGameTime()
                + ModPerks.GREEN_HERBS.ticks(ModPerks.GREEN_HERBS_SHARE_SECONDS, tier);
        LOANS.put(target.getUUID(), new Loan(tier, expiresAt));

        int seconds = ModPerks.GREEN_HERBS.intValue(ModPerks.GREEN_HERBS_SHARE_SECONDS, tier);
        target.displayClientMessage(Component.translatable("bloodbound.message.green_herbs_gained", seconds)
                .withStyle(ChatFormatting.GREEN), true);
        healer.displayClientMessage(Component.translatable("bloodbound.message.green_herbs_shared",
                target.getName()).withStyle(ChatFormatting.GREEN), true);

        target.level().playSound(null, target.blockPosition(), SoundEvents.BREWING_STAND_BREW,
                SoundSource.PLAYERS, 0.7F, 1.4F);
        target.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER,
                target.getX(), target.getY() + 1.0D, target.getZ(), 12, 0.4D, 0.6D, 0.4D, 0.0D);
    }

    /** Forgets a player entirely, on logout or death. */
    public static void clear(UUID playerId) {
        PROGRESS.remove(playerId);
        LOANS.remove(playerId);
        DELIVERED.remove(playerId);
        DELIVERED.values().forEach(patients -> patients.remove(playerId));
    }
}
