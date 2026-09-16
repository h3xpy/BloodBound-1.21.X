package net.h3xpy.bloodbound.perk.impl;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.event.AuraRevealHandler;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * Keep Fighting: pour your own health into a nearby player, then pay for it with Broken.
 * <p>
 * There is no cooldown — being unable to heal for the next 20 to 30 seconds is the cost.
 */
public final class KeepFighting {

    private KeepFighting() {}

    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        if (player.hasEffect(ModEffects.BROKEN)) {
            refuse(player, "bloodbound.message.keep_fighting_broken");
            return false;
        }

        float cost = (float) ModPerks.KEEP_FIGHTING.value(ModPerks.KEEP_FIGHTING_SELF_COST, tier);
        // Strictly greater: the perk is a sacrifice, never a suicide.
        if (player.getHealth() <= cost) {
            refuse(player, "bloodbound.message.keep_fighting_no_health");
            return false;
        }

        ServerPlayer target = findTarget(player);
        if (target == null) {
            refuse(player, "bloodbound.message.keep_fighting_no_target");
            return false;
        }

        float given = (float) ModPerks.KEEP_FIGHTING.value(ModPerks.KEEP_FIGHTING_TARGET_HEAL, tier);
        target.heal(given);
        // Counts as healing somebody, for the perks that pay out on that.
        GreenHerbs.recordHealing(player, target, given);
        TeamSpirit.recordHealing(player, target, given);
        // setHealth rather than hurt: this is a price paid, not damage taken, so it should not
        // trigger hurt animations, knockback or damage-reduction perks.
        player.setHealth(player.getHealth() - cost);

        int broken = brokenTicks(data, tier);
        player.addEffect(new MobEffectInstance(ModEffects.BROKEN, broken, 0, false, true, true));

        // The one you saved runs for as long as you cannot mend yourself.
        if (data.isAddonActive(ModAddons.RUNE_OF_SWIFTNESS)) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, broken, 0,
                    false, true, true));
        }
        if (data.isAddonActive(ModAddons.RUNE_OF_STEALTH)) {
            vanish(player);
        }

        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 0.5F, 1.6F);
        player.serverLevel().sendParticles(ParticleTypes.HEART,
                target.getX(), target.getY() + target.getBbHeight(), target.getZ(),
                6, 0.3D, 0.2D, 0.3D, 0.02D);
        return false;
    }

    /** How long the Broken lasts, once the runes and the cup have had their say. */
    private static int brokenTicks(PlayerPerkData data, int tier) {
        float ticks = ModPerks.KEEP_FIGHTING.ticks(ModPerks.KEEP_FIGHTING_BROKEN_SECONDS, tier);
        if (data.isAddonActive(ModAddons.CRACKED_CUP)) {
            ticks *= ModAddons.CRACKED_CUP_BROKEN;
        }
        if (data.isAddonActive(ModAddons.RUNE_OF_STEALTH)) {
            ticks *= ModAddons.RUNE_OF_STEALTH_BROKEN;
        }
        return Math.max(1, Math.round(ticks));
    }

    /**
     * Rune of Stealth: the player drops out of sight for a moment, and everything still standing
     * nearby is painted on their screen so they know what they are walking away from.
     */
    private static void vanish(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,
                ModAddons.RUNE_OF_STEALTH_INVISIBILITY_TICKS, 0, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                ModAddons.RUNE_OF_STEALTH_RESISTANCE_TICKS, 0, false, true, true));

        AABB around = player.getBoundingBox().inflate(ModAddons.RUNE_OF_STEALTH_REVEAL_RANGE);
        for (LivingEntity entity : player.serverLevel().getEntitiesOfClass(LivingEntity.class, around)) {
            if (entity != player && entity.isAlive()) {
                AuraRevealHandler.reveal(player, entity, ModAddons.RUNE_OF_STEALTH_REVEAL_TICKS);
            }
        }
    }

    /**
     * Nearest player in range, preferring one who is actually hurt so the sacrifice is not wasted
     * on a teammate at full health standing closer.
     */
    @Nullable
    private static ServerPlayer findTarget(ServerPlayer player) {
        double rangeSq = ModPerks.KEEP_FIGHTING_RANGE * ModPerks.KEEP_FIGHTING_RANGE;
        ServerPlayer nearest = null;
        ServerPlayer nearestHurt = null;
        double nearestDistance = Double.MAX_VALUE;
        double nearestHurtDistance = Double.MAX_VALUE;

        for (ServerPlayer other : player.serverLevel().players()) {
            if (other == player || !other.isAlive()) {
                continue;
            }
            double distance = other.distanceToSqr(player);
            if (distance > rangeSq) {
                continue;
            }
            if (distance < nearestDistance) {
                nearest = other;
                nearestDistance = distance;
            }
            if (other.getHealth() < other.getMaxHealth() && distance < nearestHurtDistance) {
                nearestHurt = other;
                nearestHurtDistance = distance;
            }
        }
        return nearestHurt != null ? nearestHurt : nearest;
    }

    private static void refuse(ServerPlayer player, String messageKey) {
        player.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.DARK_GRAY), true);
    }
}
