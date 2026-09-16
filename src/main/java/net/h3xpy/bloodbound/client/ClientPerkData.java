package net.h3xpy.bloodbound.client;

import java.util.HashMap;
import java.util.Map;

import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * The client's read-only mirror of the player's BloodBound state, refreshed by
 * {@link ClientPayloadHandler}. Screens and the HUD render from this; nothing here is authoritative.
 */
public final class ClientPerkData {
    private static PlayerPerkData data = new PlayerPerkData();

    /** Game time a Broken Movement Device return point runs out; 0 when there is none. */
    private static long recallExpiresAt;

    private ClientPerkData() {}

    public static PlayerPerkData get() {
        return data;
    }

    static void set(PlayerPerkData fresh) {
        data = fresh;
    }

    static void setRecallExpiry(long expiresAt) {
        recallExpiresAt = expiresAt;
    }

    /** Ticks left on the return point, or 0 when none is up. */
    public static int recallTicksRemaining() {
        long remaining = recallExpiresAt - gameTime();
        return remaining > 0L ? (int) remaining : 0;
    }

    /** What each charge-holding perk has left, as last reported by the server. */
    private record Charges(int charges, int max, long rechargeAt) {}

    private static final Map<ResourceLocation, Charges> CHARGES = new HashMap<>();

    static void setCharges(ResourceLocation perkId, int charges, int max, long rechargeAt) {
        CHARGES.put(perkId, new Charges(charges, max, rechargeAt));
    }

    /** Charges left, or -1 when the server has not said anything about this perk. */
    public static int charges(ResourceLocation perkId) {
        Charges held = CHARGES.get(perkId);
        return held == null ? -1 : held.charges();
    }

    public static int maxCharges(ResourceLocation perkId) {
        Charges held = CHARGES.get(perkId);
        return held == null ? 0 : held.max();
    }

    /** Ticks until this perk's next charge lands, or 0 when it is full. */
    public static int rechargeTicks(ResourceLocation perkId) {
        Charges held = CHARGES.get(perkId);
        if (held == null || held.rechargeAt() <= 0L) {
            return 0;
        }
        long remaining = held.rechargeAt() - gameTime();
        return remaining > 0L ? (int) remaining : 0;
    }

    /** Nasty Blade's streak, as last reported by the server. */
    private static int bladeTokens;
    private static long bladeExpiresAt;

    static void setBladeStreak(int tokens, long expiresAt) {
        bladeTokens = tokens;
        bladeExpiresAt = expiresAt;
    }

    public static int bladeTokens() {
        return bladeTokens;
    }

    /** Ticks left on the streak, or 0 when there is none. */
    public static int bladeTicksRemaining() {
        if (bladeExpiresAt <= 0L) {
            return 0;
        }
        long remaining = bladeExpiresAt - gameTime();
        return remaining > 0L ? (int) remaining : 0;
    }

    /** Cleared on disconnect so a second world does not inherit the first one's perks. */
    public static void reset() {
        data = new PlayerPerkData();
        recallExpiresAt = 0L;
        CHARGES.clear();
        bladeTokens = 0;
        bladeExpiresAt = 0L;
    }

    /** Current game time, used to work out how much of a cooldown is left. */
    public static long gameTime() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null ? minecraft.level.getGameTime() : 0L;
    }
}
