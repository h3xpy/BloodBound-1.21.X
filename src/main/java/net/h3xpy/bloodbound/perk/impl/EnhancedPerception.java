package net.h3xpy.bloodbound.perk.impl;

import java.util.UUID;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.event.AuraRevealHandler;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.registry.ModEffects;
import net.h3xpy.bloodbound.ritual.TrapRoster;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Enhanced Perception: everything the player reads auras with reaches further.
 * <p>
 * Nothing here runs on its own. Perks that sweep a radius ask {@link #radius} for theirs, and
 * {@code AuraRevealHandler} asks for the rest when a reveal actually lands, so a new perk that
 * reveals an aura gets Spyware and Ransomware for free.
 */
public final class EnhancedPerception {

    private EnhancedPerception() {}

    /** The reach of an aura sweep for this viewer: its own radius, plus the perk's and Adware's. */
    public static double radius(ServerPlayer viewer, double base) {
        PlayerPerkData data = PerkDataManager.get(viewer);
        int tier = data.getActiveTier(ModPerks.ENHANCED_PERCEPTION);
        if (tier <= 0) {
            return base;
        }
        double bonus = ModPerks.ENHANCED_PERCEPTION.value(ModPerks.PERCEPTION_BONUS_BLOCKS, tier);
        if (data.isAddonActive(ModAddons.ADWARE)) {
            bonus += ModAddons.ADWARE_BONUS_BLOCKS;
        }
        return base + bonus;
    }

    /**
     * Rootkit: a sweep for auras finds traps too — barbed wire, tripwires, Target Found wires and spring
     * pads that are not the viewer's own — and lights them up for the same time. Rituals, Eavesdrop
     * included, are not traps.
     * Called by every perk that sweeps an area for auras, with that area.
     */
    public static void revealTraps(ServerPlayer viewer, Vec3 centre, double radius, int ticks) {
        if (!PerkDataManager.get(viewer).isAddonActive(ModAddons.ROOTKIT)) {
            return;
        }
        AABB area = AABB.ofSize(centre, radius * 2.0D, radius * 2.0D, radius * 2.0D);
        for (Entity entity : viewer.serverLevel().getEntitiesOfClass(Entity.class, area, EnhancedPerception::isTrap)) {
            if (entity.distanceToSqr(centre) <= radius * radius && !viewer.getUUID().equals(ownerOf(entity))) {
                AuraRevealHandler.revealMarker(viewer, entity, ticks);
            }
        }
    }

    private static boolean isTrap(Entity entity) {
        return entity instanceof TrapRoster.Trap;
    }

    @Nullable
    private static UUID ownerOf(Entity entity) {
        return entity instanceof TrapRoster.Trap trap ? trap.trapOwnerId() : null;
    }

    /** Spyware: an aura this viewer read stays lit a little longer. */
    public static int revealTicks(ServerPlayer viewer, int base) {
        PlayerPerkData data = PerkDataManager.get(viewer);
        return data.isAddonActive(ModAddons.SPYWARE) ? base + ModAddons.SPYWARE_EXTRA_TICKS : base;
    }

    /** Ransomware: being seen by this viewer is tiring. Called once a reveal has taken. */
    public static void onRevealed(ServerPlayer viewer, LivingEntity target) {
        if (!PerkDataManager.get(viewer).isAddonActive(ModAddons.RANSOMWARE)) {
            return;
        }
        target.addEffect(new MobEffectInstance(ModEffects.EXHAUSTED, ModAddons.RANSOMWARE_EXHAUST_TICKS,
                ModAddons.RANSOMWARE_EXHAUST_LEVEL - 1, false, true, true));
    }
}
