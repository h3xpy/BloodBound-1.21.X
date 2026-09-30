package net.h3xpy.bloodbound.perk.impl;

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
 * Blessing Of Life: five seconds of standing still buys a ritual that mends everything inside it.
 * <p>
 * Everything alive in the circle regenerates; a player standing in it can heal others whether or
 * not they own a perk that unlocks healing, and does it far faster. The ritual keeps its own secret
 * for the first twenty seconds — stay in it longer than that and you are shown what is holding you up.
 */
public final class BlessingOfLife {

    private BlessingOfLife() {}

    /** The press does nothing: the work is the hold. */
    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        return false;
    }

    public static void setHolding(ServerPlayer player, boolean holding) {
        PlayerPerkData data = PerkDataManager.get(player);
        int tier = data.getActiveTier(ModPerks.BLESSING_OF_LIFE);
        if (tier <= 0 || data.isOnCooldown(ModPerks.BLESSING_OF_LIFE.id(), player.level().getGameTime())) {
            RitualSetup.stop(player, ModPerks.BLESSING_OF_LIFE.id(), false);
            return;
        }
        RitualSetup.setHolding(player, ModPerks.BLESSING_OF_LIFE.id(), tier, holding);
    }

    /** Advances the laying. Called once a tick per player. */
    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        int tier = data.getActiveTier(ModPerks.BLESSING_OF_LIFE);
        if (tier <= 0) {
            // The perk coming out of the loadout takes its ritual with it.
            RitualManager.remove(player.getUUID(), ModPerks.BLESSING_OF_LIFE.id());
            return;
        }

        int needed = ModPerks.BLESSING_SETUP_TICKS;
        int held = RitualSetup.advance(player, ModPerks.BLESSING_OF_LIFE.id(), needed);
        if (held < needed) {
            return;
        }

        RitualSetup.stop(player, ModPerks.BLESSING_OF_LIFE.id(), false);
        RitualManager.place(player, ModPerks.BLESSING_OF_LIFE.id(), RitualManager.Mood.HELPFUL, tier,
                ModPerks.BLESSING_OF_LIFE.value(ModPerks.BLESSING_RADIUS, tier), 0.0F);
        data.setCooldown(ModPerks.BLESSING_OF_LIFE.id(), gameTime,
                ModPerks.BLESSING_OF_LIFE.cooldownTicks(tier));
        PerkDataManager.sync(player);
        player.displayClientMessage(Component.translatable("bloodbound.message.blessing_placed")
                .withStyle(ChatFormatting.AQUA), true);
    }

    /** Works every Blessing on the server. Called once a tick. */
    public static void tickRituals(MinecraftServer server, long gameTime) {
        // Every Blessing on the ground, its owner online or not: it heals whoever is standing in it.
        for (RitualManager.Ritual ritual : RitualManager.all(ModPerks.BLESSING_OF_LIFE.id())) {
            if (!ritual.isAlive()) {
                continue;
            }
            ServerLevel level = server.getLevel(ritual.dimension());
            if (level == null) {
                continue;
            }

            for (LivingEntity occupant : ritual.occupants(level)) {
                if (gameTime % 20L == 0L) {
                    occupant.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                            ModPerks.BLESSING_REGEN_TICKS, ModPerks.BLESSING_REGEN_LEVEL - 1,
                            false, true, true));
                }
            }

            // Counted per player and reset the moment they step out: the reward is for staying.
            for (ServerPlayer nearby : level.players()) {
                boolean inside = ritual.covers(nearby.position());
                int ticks = ritual.trackInside(nearby.getUUID(), inside, true);
                if (inside && ticks >= ModPerks.BLESSING_REVEAL_AFTER_TICKS) {
                    RitualManager.revealTo(nearby, ritual, ModPerks.BLESSING_REVEAL_TICKS);
                }
            }
        }
    }

    /**
     * Whether this player is standing in somebody's Blessing. Read by the healing code, which is
     * what the ritual actually changes: who may heal, and how fast.
     */
    public static boolean isBlessed(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        for (RitualManager.Ritual ritual : RitualManager.all(ModPerks.BLESSING_OF_LIFE.id())) {
            if (ritual.isAlive() && ritual.dimension() == player.level().dimension()
                    && ritual.covers(player.position())) {
                return true;
            }
        }
        return false;
    }

    /** A logout: the laying in hand is dropped, the Blessing already down stays where it is. */
    public static void logout(ServerPlayer player) {
        RitualSetup.stop(player, ModPerks.BLESSING_OF_LIFE.id(), false);
    }

    public static void clear(ServerPlayer player) {
        RitualSetup.stop(player, ModPerks.BLESSING_OF_LIFE.id(), false);
        RitualManager.remove(player.getUUID(), ModPerks.BLESSING_OF_LIFE.id());
    }
}
