package net.h3xpy.bloodbound.event;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Hunter's Instinct: anything bleeding nearby gives itself away.
 * <p>
 * Only damage the hunter had no hand in counts. A hit of their own already tells them where the
 * thing is; the point of the perk is everything else — a fall, a fight somebody else started, a mob
 * burning in the sun.
 */
public final class HuntersInstinctHandler {

    private HuntersInstinctHandler() {}

    /** Bounty Hunter License. Called once a tick per player. */
    public static void tick(ServerPlayer hunter, PlayerPerkData data) {
        if (data.getActiveTier(ModPerks.HUNTERS_INSTINCT) <= 0
                || !data.isAddonActive(ModAddons.BOUNTY_HUNTER_LICENSE)) {
            return;
        }
        AuraRevealHandler.topUpWatched(hunter, ModAddons.BOUNTY_LICENSE_VIEW_DEGREES,
                ModAddons.BOUNTY_LICENSE_TOP_UP_TICKS);
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || event.getNewDamage() <= 0.0F) {
            return;
        }

        for (ServerPlayer hunter : victim.getServer().getPlayerList().getPlayers()) {
            if (hunter.level() != victim.level() || hunter == victim) {
                continue;
            }
            int tier = PerkDataManager.get(hunter).getActiveTier(ModPerks.HUNTERS_INSTINCT);
            if (tier <= 0) {
                continue;
            }
            // Nothing the hunter did themselves: their own hits are not news.
            if (event.getSource().getEntity() == hunter) {
                continue;
            }

            double radius = ModPerks.HUNTERS_INSTINCT.value(ModPerks.HUNTERS_INSTINCT_RADIUS, tier);
            if (PerkDataManager.get(hunter).isAddonActive(ModAddons.EMPTY_SHOTGUN_SHELL)) {
                radius *= ModAddons.EMPTY_SHELL_RANGE;
            }
            if (hunter.distanceToSqr(victim) > radius * radius) {
                continue;
            }
            AuraRevealHandler.reveal(hunter, victim, ModPerks.HUNTERS_INSTINCT_REVEAL_TICKS);
        }
    }
}
