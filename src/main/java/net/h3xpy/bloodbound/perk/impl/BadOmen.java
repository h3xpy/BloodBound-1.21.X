package net.h3xpy.bloodbound.perk.impl;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.ritual.RitualManager;
import net.h3xpy.bloodbound.ritual.RitualSetup;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Bad Omen: a ritual laid over your other rituals.
 * <p>
 * It does nothing to anyone itself. Everything it does, it does to the owner's rituals standing
 * within its reach, and all of that lives in {@code RitualManager}, since every ritual has to answer
 * to it: they are wider, each works inside all of their circles, they cannot be broken while it
 * stands — whoever tries is shown the omen instead — and the ones with charges hold more.
 * <p>
 * The omen itself is an ordinary ritual, and breaking it is how all of that is undone.
 */
public final class BadOmen {

    private BadOmen() {}

    /** The press does nothing: the work is the hold. */
    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        return false;
    }

    public static void setHolding(ServerPlayer player, boolean holding) {
        PlayerPerkData data = PerkDataManager.get(player);
        int tier = data.getActiveTier(ModPerks.BAD_OMEN);
        if (tier <= 0 || data.isOnCooldown(ModPerks.BAD_OMEN.id(), player.level().getGameTime())) {
            RitualSetup.stop(player, ModPerks.BAD_OMEN.id(), false);
            return;
        }
        RitualSetup.setHolding(player, ModPerks.BAD_OMEN.id(), tier, holding);
    }

    /** Advances the laying. Called once a tick per player. */
    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        int tier = data.getActiveTier(ModPerks.BAD_OMEN);
        if (tier <= 0) {
            // The perk coming out of the loadout takes its ritual with it.
            RitualManager.remove(player.getUUID(), ModPerks.BAD_OMEN.id());
            return;
        }

        int held = RitualSetup.advance(player, ModPerks.BAD_OMEN.id(), ModPerks.OMEN_SETUP_TICKS);
        if (held < ModPerks.OMEN_SETUP_TICKS) {
            return;
        }

        RitualSetup.stop(player, ModPerks.BAD_OMEN.id(), false);
        // Soul flame: everything it does, it does for the one who laid it.
        RitualManager.place(player, ModPerks.BAD_OMEN.id(), RitualManager.Mood.HELPFUL, tier,
                ModPerks.BAD_OMEN.value(ModPerks.OMEN_RADIUS, tier), 0.0F);
        data.setCooldown(ModPerks.BAD_OMEN.id(), gameTime, ModPerks.BAD_OMEN.cooldownTicks(tier));
        PerkDataManager.sync(player);
        player.displayClientMessage(Component.translatable("bloodbound.message.bad_omen_placed")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    /** A logout: the laying in hand is dropped, the omen already down stays where it is. */
    public static void logout(ServerPlayer player) {
        RitualSetup.stop(player, ModPerks.BAD_OMEN.id(), false);
    }

    public static void clear(ServerPlayer player) {
        RitualSetup.stop(player, ModPerks.BAD_OMEN.id(), false);
        RitualManager.remove(player.getUUID(), ModPerks.BAD_OMEN.id());
    }
}
