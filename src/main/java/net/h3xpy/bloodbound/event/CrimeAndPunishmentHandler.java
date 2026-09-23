package net.h3xpy.bloodbound.event;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.perk.impl.Flashbang;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

/**
 * Crime And Punishment: a blow turned aside leaves the one who threw it staring at the sun.
 * <p>
 * Hung on the shield-block event rather than on shields themselves, so anything vanilla counts as a
 * parry counts here too. The parry itself always stands — the perk only decides whether the
 * punishment follows it.
 */
public final class CrimeAndPunishmentHandler {

    private CrimeAndPunishmentHandler() {}

    @SubscribeEvent
    public static void onBlock(LivingShieldBlockEvent event) {
        if (!event.getBlocked() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerPerkData data = PerkDataManager.get(player);
        int tier = data.getActiveTier(ModPerks.CRIME_AND_PUNISHMENT);
        if (tier <= 0) {
            return;
        }
        if (!(event.getDamageSource().getEntity() instanceof LivingEntity attacker) || attacker == player) {
            return;
        }

        long gameTime = player.level().getGameTime();
        if (data.isOnCooldown(ModPerks.CRIME_AND_PUNISHMENT.id(), gameTime)) {
            // Torn Page: a parry made while waiting is not wasted, it brings the next one closer.
            if (data.isAddonActive(ModAddons.TORN_PAGE)) {
                cutCooldown(player, data, gameTime);
            }
            return;
        }

        double seconds = ModPerks.CRIME_AND_PUNISHMENT.value(ModPerks.CRIME_FLASH_SECONDS, tier);
        if (data.isAddonActive(ModAddons.DAMNED_SOUL)) {
            // Damned Soul trades a flat second for a share of the blow: a light hit is barely worth
            // parrying, a heavy one blinds for longer than the perk ever would.
            seconds -= ModAddons.DAMNED_SOUL_PENALTY_SECONDS;
            seconds += event.getBlockedDamage() * ModAddons.DAMNED_SOUL_SECONDS_PER_DAMAGE;
        }
        int ticks = (int) Math.round(seconds * 20.0D);
        if (ticks <= 0) {
            return;
        }

        Flashbang.blind(player, attacker, ticks);
        data.setCooldown(ModPerks.CRIME_AND_PUNISHMENT.id(), gameTime,
                ModPerks.CRIME_AND_PUNISHMENT.cooldownTicks(tier));
        PerkDataManager.sync(player);
    }

    /** Torn Page: takes its share off what is left of the wait, not off the whole of it. */
    private static void cutCooldown(ServerPlayer player, PlayerPerkData data, long gameTime) {
        int remaining = data.getCooldownRemaining(ModPerks.CRIME_AND_PUNISHMENT.id(), gameTime);
        if (remaining <= 0) {
            return;
        }
        int shortened = (int) Math.round(remaining * (1.0D - ModAddons.TORN_PAGE_CUT));
        data.setCooldown(ModPerks.CRIME_AND_PUNISHMENT.id(), gameTime, shortened);
        PerkDataManager.sync(player);
    }
}
