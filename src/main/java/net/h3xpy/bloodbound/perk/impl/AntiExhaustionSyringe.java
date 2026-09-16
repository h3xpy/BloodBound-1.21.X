package net.h3xpy.bloodbound.perk.impl;

import java.util.ArrayList;
import java.util.List;

import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.network.PerkChargesPayload;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Anti-Exhaustion Syringe: drags every other perk's cooldown most of the way to ready, then pays
 * for it with a long cooldown of its own.
 */
public final class AntiExhaustionSyringe {

    private AntiExhaustionSyringe() {}

    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        float fraction = (float) ModPerks.ANTI_EXHAUSTION_SYRINGE.value(ModPerks.SYRINGE_REDUCTION, tier) / 100.0F;
        fraction *= potency(data);

        // Its own cooldown is set below, so skipping it here keeps the shot from paying for itself.
        int cleared = data.skipCooldowns(fraction, gameTime, ModPerks.ANTI_EXHAUSTION_SYRINGE.id());
        boolean didSomething = cleared > 0;

        // Metal Syringe: the charge pools are filled too, which the cooldown sweep never touches.
        if (data.isAddonActive(ModAddons.METAL_SYRINGE)) {
            data.refillAllCharges();
            didSomething = true;
        }
        // Inhalator: barely a dose, but it clears everything that was on you.
        if (data.isAddonActive(ModAddons.INHALATOR) && stripHarmful(player)) {
            didSomething = true;
        }

        if (!didSomething) {
            player.displayClientMessage(
                    Component.translatable("bloodbound.message.syringe_nothing").withStyle(ChatFormatting.DARK_GRAY),
                    true);
            return false;
        }

        chargeCooldown(player, data, tier, gameTime);
        sendDoses(player, data, gameTime);

        player.level().playSound(null, player.blockPosition(), SoundEvents.HONEY_DRINK,
                SoundSource.PLAYERS, 0.7F, 1.4F);
        player.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER,
                player.getX(), player.getY() + 1.0D, player.getZ(), 12, 0.3D, 0.5D, 0.3D, 0.02D);
        player.displayClientMessage(
                Component.translatable("bloodbound.message.syringe_used", cleared).withStyle(ChatFormatting.AQUA),
                true);
        return true;
    }

    /** Keeps Diluted Serum's dose count on the HUD. Called once a tick per player. */
    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        if (data.getActiveTier(ModPerks.ANTI_EXHAUSTION_SYRINGE) > 0 && gameTime % 10L == 0L) {
            sendDoses(player, data, gameTime);
        }
    }

    private static void sendDoses(ServerPlayer player, PlayerPerkData data, long gameTime) {
        if (!data.isAddonActive(ModAddons.DILUTED_SERUM)) {
            PacketDistributor.sendToPlayer(player,
                    new PerkChargesPayload(ModPerks.ANTI_EXHAUSTION_SYRINGE.id(), -1, 0, 0L));
            return;
        }
        int left = data.isOnCooldown(ModPerks.ANTI_EXHAUSTION_SYRINGE.id(), gameTime)
                ? 0
                : Math.round(data.perkCharges(ModPerks.ANTI_EXHAUSTION_SYRINGE.id(),
                        ModAddons.DILUTED_SERUM_CHARGES));
        PacketDistributor.sendToPlayer(player, new PerkChargesPayload(ModPerks.ANTI_EXHAUSTION_SYRINGE.id(),
                left, ModAddons.DILUTED_SERUM_CHARGES, 0L));
    }

    /** What is left of the dose once the addons have watered it down. */
    private static float potency(PlayerPerkData data) {
        if (data.isAddonActive(ModAddons.INHALATOR)) {
            return ModAddons.INHALATOR_POTENCY;
        }
        if (data.isAddonActive(ModAddons.DILUTED_SERUM)) {
            return ModAddons.DILUTED_SERUM_POTENCY;
        }
        return 1.0F;
    }

    /**
     * Puts the syringe on cooldown, unless Diluted Serum still has a dose left in it. Whichever way
     * it lands, the length is the addons' business too.
     */
    private static void chargeCooldown(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        int cooldown = ModPerks.ANTI_EXHAUSTION_SYRINGE.cooldownTicks(tier);
        if (data.isAddonActive(ModAddons.METAL_SYRINGE)) {
            cooldown = Math.round(cooldown * ModAddons.METAL_SYRINGE_COOLDOWN);
        }
        if (data.isAddonActive(ModAddons.INHALATOR)) {
            cooldown = Math.round(cooldown * ModAddons.INHALATOR_COOLDOWN);
        }

        if (!data.isAddonActive(ModAddons.DILUTED_SERUM)) {
            data.setCooldown(ModPerks.ANTI_EXHAUSTION_SYRINGE.id(), gameTime, cooldown);
            return;
        }

        float left = data.perkCharges(ModPerks.ANTI_EXHAUSTION_SYRINGE.id(),
                ModAddons.DILUTED_SERUM_CHARGES) - 1.0F;
        if (left > 0.0F) {
            data.setPerkCharges(ModPerks.ANTI_EXHAUSTION_SYRINGE.id(), left);
            return;
        }
        data.setPerkCharges(ModPerks.ANTI_EXHAUSTION_SYRINGE.id(), ModAddons.DILUTED_SERUM_CHARGES);
        data.setCooldown(ModPerks.ANTI_EXHAUSTION_SYRINGE.id(), gameTime, cooldown);
    }

    /** Takes every harmful effect off the player. @return true if there was anything to take. */
    private static boolean stripHarmful(ServerPlayer player) {
        List<Holder<MobEffect>> harmful = new ArrayList<>();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                harmful.add(effect.getEffect());
            }
        }
        harmful.forEach(player::removeEffect);
        return !harmful.isEmpty();
    }
}
