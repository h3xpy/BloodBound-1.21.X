package net.h3xpy.bloodbound.perk.impl;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * From The Dark: the dark is where this one lives. They always see through it, and moving through it
 * carries them.
 * <p>
 * The price is paid elsewhere: eyes that used to the dark take twice as long to recover from a
 * flash, which {@link Flashbang} applies.
 */
public final class FromTheDark {

    /** Night vision is granted in long slices and refreshed early, so it never starts flashing. */
    private static final int NIGHT_VISION_DURATION = 400;
    private static final int NIGHT_VISION_REFRESH_BELOW = 300;

    private FromTheDark() {}

    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        int tier = data.getActiveTier(ModPerks.FROM_THE_DARK);
        if (tier <= 0 || !player.isAlive()) {
            return;
        }

        seeInTheDark(player);

        if (!player.isSprinting() || !inTheDark(player)
                || data.isOnCooldown(ModPerks.FROM_THE_DARK.id(), gameTime)) {
            return;
        }

        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ModPerks.FROM_THE_DARK_SPEED_TICKS,
                ModPerks.FROM_THE_DARK_SPEED_LEVEL - 1, false, true, true));

        // Counted from the end of the burst rather than the start, so the numbers read as the gap
        // between one sprint and the next.
        data.setCooldown(ModPerks.FROM_THE_DARK.id(), gameTime,
                ModPerks.FROM_THE_DARK_SPEED_TICKS + ModPerks.FROM_THE_DARK.cooldownTicks(tier));
        PerkDataManager.sync(player);

        player.level().playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(),
                SoundSource.PLAYERS, 0.5F, 1.3F);
    }

    /** Light where the player is standing, sky and blocks both, at or under what counts as dark. */
    private static boolean inTheDark(ServerPlayer player) {
        return player.level().getMaxLocalRawBrightness(player.blockPosition())
                <= ModPerks.FROM_THE_DARK_MAX_LIGHT;
    }

    /**
     * Keeps night vision topped up. Refreshed well before it would start flashing, which vanilla
     * does in the last ten seconds.
     */
    private static void seeInTheDark(ServerPlayer player) {
        MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
        if (current == null || current.getDuration() < NIGHT_VISION_REFRESH_BELOW) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_VISION_DURATION, 0,
                    false, false, true));
        }
    }
}
