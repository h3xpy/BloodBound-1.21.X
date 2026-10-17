package net.h3xpy.bloodbound.perk;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.network.PerkGlowPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Lights a passive perk's HUD slot gold while it is in effect, the way Vigilance's does.
 * <p>
 * Two kinds: a state that holds ({@link #set}, sent only when it changes, so it can be called every
 * tick), and a one-off moment the perk went off ({@link #flash}).
 */
public final class PerkGlow {

    /** How long a one-off flash lasts by default. */
    public static final int FLASH_TICKS = 40;

    /** Which perks each player's client has been told are lit. */
    private static final Map<UUID, Set<ResourceLocation>> LIT = new HashMap<>();

    private PerkGlow() {}

    /** Lit for as long as {@code on} stays true. Cheap to call every tick: only a change is sent. */
    public static void set(ServerPlayer player, Perk perk, boolean on) {
        Set<ResourceLocation> lit = LIT.computeIfAbsent(player.getUUID(), id -> new HashSet<>());
        boolean changed = on ? lit.add(perk.id()) : lit.remove(perk.id());
        if (changed && player.connection != null) {
            PacketDistributor.sendToPlayer(player, new PerkGlowPayload(perk.id(), on ? PerkGlowPayload.ON : PerkGlowPayload.OFF));
        }
    }

    /** The perk just went off: lit for a moment. */
    public static void flash(ServerPlayer player, Perk perk) {
        flash(player, perk, FLASH_TICKS);
    }

    public static void flash(ServerPlayer player, Perk perk, int ticks) {
        if (player.connection != null) {
            PacketDistributor.sendToPlayer(player, new PerkGlowPayload(perk.id(), ticks));
        }
    }

    /** Puts out whatever is lit for a perk no longer in effect: taken off, or turned off. Called once a tick per player. */
    public static void prune(ServerPlayer player, PlayerPerkData data) {
        Set<ResourceLocation> lit = LIT.get(player.getUUID());
        if (lit == null || lit.isEmpty()) {
            return;
        }
        lit.removeIf(id -> {
            if (data.getActiveTier(id) > 0) {
                return false;
            }
            PacketDistributor.sendToPlayer(player, new PerkGlowPayload(id, PerkGlowPayload.OFF));
            return true;
        });
    }

    /** Forgets a player; their client clears its own copy on logout. */
    public static void clear(UUID playerId) {
        LIT.remove(playerId);
    }

    /** A death: everything goes out, on both sides. */
    public static void reset(ServerPlayer player) {
        Set<ResourceLocation> lit = LIT.remove(player.getUUID());
        if (lit != null && player.connection != null) {
            for (ResourceLocation id : lit) {
                PacketDistributor.sendToPlayer(player, new PerkGlowPayload(id, PerkGlowPayload.OFF));
            }
        }
    }
}
