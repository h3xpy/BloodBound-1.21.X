package net.h3xpy.bloodbound.perk.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.ritual.RitualManager;
import net.h3xpy.bloodbound.ritual.RitualSetup;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Sin Of Obliviousness: a circle where being hurt costs you your eyes.
 * <p>
 * Anything but the one who laid it that takes so much as a scratch inside the ritual is blinded,
 * and stays blinded for as long as the ritual stands — walking out of it changes nothing. Breaking
 * the ritual is the only cure, which is what its growing aura is for.
 */
public final class SinOfObliviousness {

    /** What this perk has blinded, and whose ritual is holding its eyes. */
    private static final Map<UUID, UUID> BLINDED = new HashMap<>();

    private SinOfObliviousness() {}

    /** The press does nothing: the work is the hold. */
    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        return false;
    }

    public static void setHolding(ServerPlayer player, boolean holding) {
        PlayerPerkData data = PerkDataManager.get(player);
        int tier = data.getActiveTier(ModPerks.SIN_OF_OBLIVIOUSNESS);
        if (tier <= 0
                || data.isOnCooldown(ModPerks.SIN_OF_OBLIVIOUSNESS.id(), player.level().getGameTime())) {
            RitualSetup.stop(player, ModPerks.SIN_OF_OBLIVIOUSNESS.id(), false);
            return;
        }
        RitualSetup.setHolding(player, ModPerks.SIN_OF_OBLIVIOUSNESS.id(), tier, holding);
    }

    /** Advances the laying. Called once a tick per player. */
    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        int tier = data.getActiveTier(ModPerks.SIN_OF_OBLIVIOUSNESS);
        if (tier <= 0) {
            // Only this perk's ritual. Wiping the player's whole setup here is what stopped every
            // other ritual from ever being laid: this runs every tick, for everybody.
            RitualManager.remove(player.getUUID(), ModPerks.SIN_OF_OBLIVIOUSNESS.id());
            return;
        }

        int needed = ModPerks.SIN_SETUP_TICKS;
        int held = RitualSetup.advance(player, ModPerks.SIN_OF_OBLIVIOUSNESS.id(), needed);
        if (held < needed) {
            return;
        }

        RitualSetup.stop(player, ModPerks.SIN_OF_OBLIVIOUSNESS.id(), false);
        RitualManager.place(player, ModPerks.SIN_OF_OBLIVIOUSNESS.id(), RitualManager.Mood.HARMFUL, tier,
                ModPerks.SIN_OF_OBLIVIOUSNESS.value(ModPerks.SIN_RADIUS, tier), 0.0F);
        data.setCooldown(ModPerks.SIN_OF_OBLIVIOUSNESS.id(), gameTime,
                ModPerks.SIN_OF_OBLIVIOUSNESS.cooldownTicks(tier));
        PerkDataManager.sync(player);
        player.displayClientMessage(Component.translatable("bloodbound.message.sin_placed")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    /**
     * Something took damage: if it was inside somebody's circle, and it was not theirs, it loses
     * its eyes.
     */
    public static void onDamaged(LivingEntity victim) {
        if (!(victim.level() instanceof ServerLevel level) || !victim.isAlive()) {
            return;
        }
        for (ServerPlayer owner : level.getServer().getPlayerList().getPlayers()) {
            RitualManager.Ritual ritual =
                    RitualManager.get(owner.getUUID(), ModPerks.SIN_OF_OBLIVIOUSNESS.id());
            if (ritual == null || !ritual.isAlive() || ritual.dimension() != level.dimension()) {
                continue;
            }
            if (victim.getUUID().equals(ritual.ownerId()) || !ritual.covers(victim.position())) {
                continue;
            }
            BLINDED.put(victim.getUUID(), owner.getUUID());
            blind(victim);
            return;
        }
    }

    /**
     * Keeps the blindness topped up wherever the blinded have wandered off to, and gives the eyes
     * back the moment the ritual that took them is gone.
     */
    public static void tickRituals(MinecraftServer server, long gameTime) {
        if (BLINDED.isEmpty() || gameTime % 20L != 0L) {
            return;
        }
        for (Map.Entry<UUID, UUID> entry : Map.copyOf(BLINDED).entrySet()) {
            LivingEntity victim = find(server, entry.getKey());
            RitualManager.Ritual ritual =
                    RitualManager.get(entry.getValue(), ModPerks.SIN_OF_OBLIVIOUSNESS.id());

            if (ritual == null || !ritual.isAlive()) {
                BLINDED.remove(entry.getKey());
                if (victim != null) {
                    victim.removeEffect(MobEffects.BLINDNESS);
                }
                continue;
            }
            if (victim == null || !victim.isAlive()) {
                // Gone, or unloaded: forgetting it is the only thing left to do.
                BLINDED.remove(entry.getKey());
                continue;
            }
            // Leaving the circle is no cure: the ritual has to be broken.
            blind(victim);
        }
    }

    /** Looks one blinded thing up, wherever in the worlds it has got to. */
    @Nullable
    private static LivingEntity find(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }

    private static void blind(LivingEntity victim) {
        victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ModPerks.SIN_BLINDNESS_TICKS,
                0, false, true, true));
    }

    public static void clear(ServerPlayer player) {
        RitualSetup.stop(player, ModPerks.SIN_OF_OBLIVIOUSNESS.id(), false);
        BLINDED.remove(player.getUUID());
        RitualManager.remove(player.getUUID(), ModPerks.SIN_OF_OBLIVIOUSNESS.id());
    }
}
