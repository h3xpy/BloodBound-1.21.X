package net.h3xpy.bloodbound.event;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.network.NastyBladePayload;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.registry.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Nasty Blade: a blade that starts blunt and sharpens itself on a run of hits.
 * <p>
 * Every swing lands short of what it should. Each one that connects banks a token worth a slice of
 * that back, and resets the clock the streak runs on; let the clock run out and the whole streak
 * goes with it.
 */
public final class NastyBladeHandler {

    private NastyBladeHandler() {}

    @SubscribeEvent
    public static void onAttack(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        // Melee only: this is about the blade, not arrows or fire.
        if (!event.getSource().is(DamageTypes.PLAYER_ATTACK)) {
            return;
        }

        PlayerPerkData data = PerkDataManager.get(attacker);
        int tier = data.getActiveTier(ModPerks.NASTY_BLADE);
        if (tier <= 0) {
            return;
        }

        // The tokens counted here are the ones already banked: this swing earns its own, and the
        // next blow is the one that spends it.
        float penalty = data.isAddonActive(ModAddons.ANTICOAGULANT)
                ? ModAddons.ANTICOAGULANT_PENALTY
                : ModPerks.NASTY_BLADE_PENALTY;
        float perToken = data.isAddonActive(ModAddons.RAZOR_BLADE)
                ? ModAddons.RAZOR_BLADE_PER_TOKEN
                : ModPerks.NASTY_BLADE_PER_TOKEN;

        // Never below nothing: a blunt blade still counts as a hit, it just does not pay.
        event.setAmount(Math.max(0.0F, event.getAmount() - penalty + data.bladeTokens() * perToken));

        int streak = streakTicks(data, tier);
        long gameTime = attacker.level().getGameTime();

        if (data.isAddonActive(ModAddons.ANTICOAGULANT)) {
            // Refreshed on every hit, so it always outlives the streak by the same tail.
            event.getEntity().addEffect(new MobEffectInstance(ModEffects.BROKEN,
                    streak + ModAddons.ANTICOAGULANT_BROKEN_TAIL_TICKS, 0, false, true, true));
        }

        // A hit that lands on top of the last one keeps the streak alive but earns nothing: the
        // blade sharpens on a run of blows, not on how fast the button can be pressed.
        if (!data.canBankBladeToken(gameTime)) {
            data.refreshBladeStreak(gameTime + streak);
            sendTokens(attacker, data, gameTime + streak);
            return;
        }
        data.addBladeToken(gameTime + streak, gameTime + ModPerks.NASTY_BLADE_TOKEN_COOLDOWN);

        // A short rising note per token, so the streak is something you hear rather than count. The
        // same pling the skill checks use: it carries over a fight, where a chime does not.
        float pitch = Math.min(2.0F, 0.8F + data.bladeTokens() * 0.08F);
        attacker.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.8F, pitch);
        sendTokens(attacker, data, gameTime + streak);
    }

    /** How long a streak survives without a fresh hit. */
    private static int streakTicks(PlayerPerkData data, int tier) {
        int ticks = ModPerks.NASTY_BLADE.ticks(ModPerks.NASTY_BLADE_TIMER, tier);
        if (data.isAddonActive(ModAddons.KNIFE_BELT)) {
            ticks += ModAddons.KNIFE_BELT_EXTRA_SECONDS * 20;
        }
        return ticks;
    }

    /** Drops the streak once its clock runs out. Called once a tick per player. */
    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        if (data.consumeLapsedBladeStreak(gameTime)) {
            player.playNotifySound(SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.7F, 0.7F);
            sendTokens(player, data, 0L);
        }
    }

    /** Tells the client what the streak is worth. Tokens never travel on the usual state sync. */
    private static void sendTokens(ServerPlayer player, PlayerPerkData data, long expiresAt) {
        PacketDistributor.sendToPlayer(player, new NastyBladePayload(data.bladeTokens(), expiresAt));
    }
}
