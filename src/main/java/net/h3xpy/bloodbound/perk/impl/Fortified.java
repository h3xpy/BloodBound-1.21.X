package net.h3xpy.bloodbound.perk.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.network.PerkChargesPayload;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.perk.PerkGlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Fortified: dealing enough damage braces the bearer, and the next few real blows they take land
 * soft. The braces show on the slot as pips, and the slot glows while there are any.
 * <p>
 * Damage only counts towards the next set once the last set is spent, so a long fight does not keep
 * the bearer braced for ever.
 */
public final class Fortified {

    /** Damage dealt towards the next set of braces. */
    private static final Map<UUID, Float> DEALT = new HashMap<>();
    /** Braces left. */
    private static final Map<UUID, Integer> BRACES = new HashMap<>();

    private Fortified() {}

    /** Counts what the bearer deals, after armour: what really landed. */
    @SubscribeEvent
    public static void onDamageDealt(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || event.getEntity() == player
                || event.getNewDamage() <= 0.0F) {
            return;
        }
        PlayerPerkData data = PerkDataManager.get(player);
        int tier = data.getActiveTier(ModPerks.FORTIFIED);
        if (tier <= 0 || BRACES.getOrDefault(player.getUUID(), 0) > 0) {
            return;
        }
        float dealt = DEALT.merge(player.getUUID(), event.getNewDamage(), Float::sum);
        if (dealt >= ModPerks.FORTIFIED.value(ModPerks.FORTIFIED_DAMAGE_DEALT, tier)) {
            DEALT.remove(player.getUUID());
            int braces = ModPerks.FORTIFIED.intValue(ModPerks.FORTIFIED_HITS, tier);
            BRACES.put(player.getUUID(), braces);
            show(player, braces, braces);
            player.playNotifySound(SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 0.8F, 1.0F);
        }
    }

    /** A real blow against a braced bearer is cut, and uses up a brace. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= ModPerks.FORTIFIED_MIN_HIT) {
            return;
        }
        int braces = BRACES.getOrDefault(player.getUUID(), 0);
        if (braces <= 0) {
            return;
        }
        PlayerPerkData data = PerkDataManager.get(player);
        int tier = data.getActiveTier(ModPerks.FORTIFIED);
        if (tier <= 0) {
            return;
        }
        double cut = ModPerks.FORTIFIED.value(ModPerks.FORTIFIED_REDUCTION, tier) / 100.0D;
        event.setAmount((float) (event.getAmount() * (1.0D - cut)));
        braces--;
        if (braces > 0) {
            BRACES.put(player.getUUID(), braces);
        } else {
            BRACES.remove(player.getUUID());
        }
        show(player, braces, ModPerks.FORTIFIED.intValue(ModPerks.FORTIFIED_HITS, tier));
        player.level().playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.7F, 1.2F);
    }

    /** Takes the braces away once the perk is no longer in effect. Called once a tick per player. */
    public static void tick(ServerPlayer player, PlayerPerkData data) {
        if (data.getActiveTier(ModPerks.FORTIFIED) <= 0 && (BRACES.containsKey(player.getUUID()) || DEALT.containsKey(player.getUUID()))) {
            clear(player);
        }
    }

    private static void show(ServerPlayer player, int braces, int max) {
        PerkGlow.set(player, ModPerks.FORTIFIED, braces > 0);
        PacketDistributor.sendToPlayer(player, new PerkChargesPayload(ModPerks.FORTIFIED.id(),
                braces > 0 ? braces : -1, braces > 0 ? max : 0, 0L));
    }

    /** Drops a player's braces and progress, on logout and death. */
    public static void clear(ServerPlayer player) {
        DEALT.remove(player.getUUID());
        if (BRACES.remove(player.getUUID()) != null && player.connection != null) {
            show(player, 0, 0);
        }
    }
}
