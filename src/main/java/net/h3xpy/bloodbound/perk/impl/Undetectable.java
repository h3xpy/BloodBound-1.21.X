package net.h3xpy.bloodbound.perk.impl;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.ritual.RitualManager;
import net.h3xpy.bloodbound.ritual.RitualSetup;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Undetectable: five seconds of standing still lays a ritual that hides everything in it.
 * <p>
 * Inside it, nobody but its owner reads auras at all, and nothing in it — creature, player or trap —
 * can have its aura read by anybody but its owner. Every way the mod shows an aura asks
 * {@link #shields} first, so the ritual covers them all.
 */
public final class Undetectable {

    private Undetectable() {}

    /** The press does nothing: the work is the hold. */
    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        return false;
    }

    public static void setHolding(ServerPlayer player, boolean holding) {
        PlayerPerkData data = PerkDataManager.get(player);
        int tier = data.getActiveTier(ModPerks.UNDETECTABLE);
        if (tier <= 0 || data.isOnCooldown(ModPerks.UNDETECTABLE.id(), player.level().getGameTime())) {
            RitualSetup.stop(player, ModPerks.UNDETECTABLE.id(), false);
            return;
        }
        RitualSetup.setHolding(player, ModPerks.UNDETECTABLE.id(), tier, holding);
    }

    /** Advances the laying. Called once a tick per player. */
    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        int tier = data.getActiveTier(ModPerks.UNDETECTABLE);
        if (tier <= 0) {
            if (!data.hasEquipped(ModPerks.UNDETECTABLE)) {
                // Out of the loadout, and the ritual goes with it.
                RitualManager.remove(player.getUUID(), ModPerks.UNDETECTABLE.id());
            }
            return;
        }
        int needed = ModPerks.UNDETECTABLE_SETUP_TICKS;
        if (RitualSetup.advance(player, ModPerks.UNDETECTABLE.id(), needed) < needed) {
            return;
        }
        RitualSetup.stop(player, ModPerks.UNDETECTABLE.id(), false);
        RitualManager.place(player, ModPerks.UNDETECTABLE.id(), RitualManager.Mood.HELPFUL, tier,
                ModPerks.UNDETECTABLE.value(ModPerks.UNDETECTABLE_RADIUS, tier), 0.0F);
        data.setCooldown(ModPerks.UNDETECTABLE.id(), gameTime, ModPerks.UNDETECTABLE.cooldownTicks(tier));
        PerkDataManager.sync(player);
        player.displayClientMessage(Component.translatable("bloodbound.message.undetectable_placed")
                .withStyle(ChatFormatting.AQUA), true);
    }

    /**
     * Whether an Undetectable keeps this viewer from reading this thing's aura: the viewer stands in
     * one that is not theirs, or the thing does.
     */
    public static boolean shields(ServerPlayer viewer, Entity target) {
        for (RitualManager.Ritual ritual : RitualManager.all(ModPerks.UNDETECTABLE.id())) {
            if (!ritual.isAlive() || ritual.ownerId().equals(viewer.getUUID())) {
                continue;
            }
            if ((ritual.dimension() == viewer.level().dimension() && ritual.covers(viewer.position()))
                    || (ritual.dimension() == target.level().dimension() && ritual.covers(target.position()))) {
                return true;
            }
        }
        return false;
    }

    /** A logout: the laying in hand is dropped, the ritual already down stays where it is. */
    public static void logout(ServerPlayer player) {
        RitualSetup.stop(player, ModPerks.UNDETECTABLE.id(), false);
    }

    public static void clear(ServerPlayer player) {
        RitualSetup.stop(player, ModPerks.UNDETECTABLE.id(), false);
        RitualManager.remove(player.getUUID(), ModPerks.UNDETECTABLE.id());
    }
}
