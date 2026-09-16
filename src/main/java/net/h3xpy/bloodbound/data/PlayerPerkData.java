package net.h3xpy.bloodbound.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.soulweb.Soulweb;
import net.h3xpy.bloodbound.soulweb.SoulwebGenerator;
import net.h3xpy.bloodbound.perk.Addon;
import net.h3xpy.bloodbound.perk.Perk;
import net.h3xpy.bloodbound.perk.PerkRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * Everything BloodBound remembers about a player: which perks they have learned and at which tier,
 * the four perks they currently have equipped, per-perk cooldowns, and their current soulweb.
 * <p>
 * Learned perks are permanent — the attachment is copied on death, so dying never costs progress.
 */
public class PlayerPerkData implements INBTSerializable<CompoundTag> {
    /** How many perks a player can have equipped at once. */
    public static final int LOADOUT_SIZE = 4;

    private final Map<ResourceLocation, Integer> unlockedPerks = new LinkedHashMap<>();
    private final ResourceLocation[] loadout = new ResourceLocation[LOADOUT_SIZE];
    /** Perk id to the game time at which its cooldown expires. */
    private final Map<ResourceLocation, Long> cooldowns = new HashMap<>();

    /** Addons bought on the soulweb. Owning one is permanent, like a perk. */
    private final Set<ResourceLocation> unlockedAddons = new LinkedHashSet<>();
    /** One addon slot per perk: perk id to the addon fitted to it. */
    private final Map<ResourceLocation, ResourceLocation> equippedAddons = new LinkedHashMap<>();
    /** Absorption HP currently granted by the Gel Dressing addon, tracked so it can be taken back. */
    private float grantedAbsorption;

    @Nullable
    private Soulweb soulweb;
    private int webLevel = 1;

    // --- learned perks ---

    /** The tier the player has learned for this perk, or 0 if they have never unlocked it. */
    public int getUnlockedTier(@Nullable ResourceLocation perkId) {
        return perkId == null ? 0 : unlockedPerks.getOrDefault(perkId, 0);
    }

    public int getUnlockedTier(Perk perk) {
        return getUnlockedTier(perk.id());
    }

    public boolean isUnlocked(ResourceLocation perkId) {
        return getUnlockedTier(perkId) > 0;
    }

    /** Learns the perk at the given tier. Tiers never go down. */
    public void unlock(ResourceLocation perkId, int tier) {
        unlockedPerks.merge(perkId, Math.clamp(tier, 1, Perk.MAX_TIER), Math::max);
    }

    /** Sets the tier outright, which unlike {@link #unlock} can also lower it. For commands. */
    public void setPerkTier(ResourceLocation perkId, int tier) {
        unlockedPerks.put(perkId, Math.clamp(tier, 1, Perk.MAX_TIER));
    }

    public Map<ResourceLocation, Integer> unlockedPerks() {
        return unlockedPerks;
    }

    /** Forgets a perk entirely, taking it out of the loadout and dropping its fitted addon. */
    public void revokePerk(ResourceLocation perkId) {
        unlockedPerks.remove(perkId);
        equippedAddons.remove(perkId);
        for (int i = 0; i < LOADOUT_SIZE; i++) {
            if (perkId.equals(loadout[i])) {
                loadout[i] = null;
            }
        }
        cooldowns.remove(perkId);
    }

    // --- loadout ---

    public ResourceLocation[] loadout() {
        return loadout;
    }

    @Nullable
    public ResourceLocation getLoadoutSlot(int slot) {
        return slot >= 0 && slot < LOADOUT_SIZE ? loadout[slot] : null;
    }

    /** Puts a perk in a slot, clearing any other slot already holding it. Pass null to empty it. */
    public void setLoadoutSlot(int slot, @Nullable ResourceLocation perkId) {
        if (slot < 0 || slot >= LOADOUT_SIZE) {
            return;
        }
        if (perkId != null) {
            for (int i = 0; i < LOADOUT_SIZE; i++) {
                if (perkId.equals(loadout[i])) {
                    loadout[i] = null;
                }
            }
        }
        loadout[slot] = perkId;
    }

    public boolean isEquipped(ResourceLocation perkId) {
        for (ResourceLocation equipped : loadout) {
            if (perkId.equals(equipped)) {
                return true;
            }
        }
        return false;
    }

    /** First free loadout slot, or -1 when the loadout is full. */
    public int firstFreeSlot() {
        for (int i = 0; i < LOADOUT_SIZE; i++) {
            if (loadout[i] == null) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The tier at which a perk is currently active: its learned tier if it is equipped, else 0.
     * This is the single check perk logic should use.
     */
    public int getActiveTier(Perk perk) {
        return getActiveTier(perk.id());
    }

    /** As above, by id. */
    public int getActiveTier(@Nullable ResourceLocation perkId) {
        return perkId != null && isEquipped(perkId) ? getUnlockedTier(perkId) : 0;
    }

    /** Whether any equipped perk unlocks the co-op healing ability. */
    public boolean hasHealingAbility() {
        return anyEquipped(Perk::grantsHealing);
    }

    /** Whether any equipped perk lets the player read the marks left on the ground. */
    public boolean canSeeMarks() {
        return anyEquipped(Perk::grantsMarkVision);
    }

    /** True when some perk in the loadout, at a tier the player owns, answers to the test. */
    private boolean anyEquipped(Predicate<Perk> test) {
        for (ResourceLocation perkId : loadout) {
            if (perkId == null || getUnlockedTier(perkId) <= 0) {
                continue;
            }
            Perk perk = PerkRegistry.get(perkId);
            if (perk != null && test.test(perk)) {
                return true;
            }
        }
        return false;
    }

    // --- addons ---

    public Set<ResourceLocation> unlockedAddons() {
        return unlockedAddons;
    }

    public boolean isAddonUnlocked(@Nullable ResourceLocation addonId) {
        return addonId != null && unlockedAddons.contains(addonId);
    }

    public void unlockAddon(ResourceLocation addonId) {
        unlockedAddons.add(addonId);
    }

    /** Forgets an addon, pulling it out of whichever perk slot it was fitted to. */
    public void revokeAddon(ResourceLocation addonId) {
        unlockedAddons.remove(addonId);
        equippedAddons.values().removeIf(addonId::equals);
    }

    /** Wipes every trace of BloodBound progress, for the reset command. */
    public void resetAll() {
        unlockedPerks.clear();
        unlockedAddons.clear();
        equippedAddons.clear();
        cooldowns.clear();
        java.util.Arrays.fill(loadout, null);
        grantedAbsorption = 0.0F;
        soulweb = null;
        webLevel = 1;
    }

    /** Sets the web level directly, and drops the current web so the next one uses it. */
    public void setWebLevel(int level) {
        this.webLevel = Math.max(1, level);
        this.soulweb = null;
    }

    /** The addon fitted to a perk, or null if that perk's addon slot is empty. */
    @Nullable
    public ResourceLocation equippedAddon(@Nullable ResourceLocation perkId) {
        return perkId == null ? null : equippedAddons.get(perkId);
    }

    /** Fits an addon to its perk's slot, replacing whatever was there. Pass null to clear it. */
    public void setEquippedAddon(ResourceLocation perkId, @Nullable ResourceLocation addonId) {
        if (addonId == null) {
            equippedAddons.remove(perkId);
        } else {
            equippedAddons.put(perkId, addonId);
        }
    }

    /** Whether this addon occupies its perk's slot, regardless of whether the perk is equipped. */
    public boolean isAddonEquipped(Addon addon) {
        return addon.id().equals(equippedAddons.get(addon.perkId()));
    }

    /**
     * Whether an addon is not just fitted but actually in effect, which also requires its parent
     * perk to be in the loadout.
     */
    public boolean isAddonActive(Addon addon) {
        return isAddonEquipped(addon) && getActiveTier(addon.perkId()) > 0;
    }

    public float grantedAbsorption() {
        return grantedAbsorption;
    }

    public void setGrantedAbsorption(float amount) {
        this.grantedAbsorption = Math.max(0.0F, amount);
    }

    // --- cooldowns ---

    public void setCooldown(ResourceLocation perkId, long gameTime, int durationTicks) {
        if (durationTicks <= 0) {
            cooldowns.remove(perkId);
        } else {
            cooldowns.put(perkId, gameTime + durationTicks);
        }
    }

    /** Remaining cooldown in ticks, or 0 when the perk is ready. */
    public int getCooldownRemaining(ResourceLocation perkId, long gameTime) {
        Long expiry = cooldowns.get(perkId);
        if (expiry == null) {
            return 0;
        }
        long remaining = expiry - gameTime;
        return remaining <= 0 ? 0 : (int) Math.min(remaining, Integer.MAX_VALUE);
    }

    public boolean isOnCooldown(ResourceLocation perkId, long gameTime) {
        return getCooldownRemaining(perkId, gameTime) > 0;
    }

    /** Drops expired entries so the map does not grow forever. */
    public void pruneCooldowns(long gameTime) {
        cooldowns.entrySet().removeIf(entry -> entry.getValue() <= gameTime);
    }

    /** Wipes every cooldown, so respawning starts the player fresh. */
    public void clearCooldowns() {
        cooldowns.clear();
    }

    /**
     * Skips a share of every running cooldown, for the Anti-Exhaustion Syringe.
     *
     * @param fraction how much of each remaining cooldown to skip, from 0 to 1
     * @param skip     a perk to leave alone, normally the one paying for this
     * @return how many perks were actually brought forward
     */
    public int skipCooldowns(float fraction, long gameTime, @Nullable ResourceLocation skip) {
        int affected = 0;
        for (Map.Entry<ResourceLocation, Long> entry : cooldowns.entrySet()) {
            if (entry.getKey().equals(skip)) {
                continue;
            }
            long remaining = entry.getValue() - gameTime;
            if (remaining <= 0L) {
                continue;
            }
            entry.setValue(entry.getValue() - Math.round(remaining * (double) fraction));
            affected++;
        }
        return affected;
    }

    /** Brings every running cooldown forward, for Lightbringer's recovery aura. */
    public void accelerateCooldowns(int ticks) {
        if (ticks > 0) {
            cooldowns.replaceAll((perkId, expiry) -> expiry - ticks);
        }
    }

    /**
     * The same, for the perks that hand charges back on a timer of their own rather than through a
     * cooldown. Those timers never reach the cooldown map, so the aura would otherwise pass them by.
     */
    public void accelerateRecharges(int ticks) {
        if (ticks <= 0) {
            return;
        }
        if (bankRechargeAt > 0L) {
            bankRechargeAt -= ticks;
        }
        if (deviceRechargeAt > 0L) {
            deviceRechargeAt -= ticks;
        }
        perkRechargeAt.replaceAll((perkId, at) -> at > 0L ? at - ticks : at);
    }

    /**
     * Accumulated fraction of a tick owed by Lightbringer's aura. Recovery is a percentage, so it
     * has to be banked until it adds up to whole ticks. Transient: not worth persisting.
     */
    private float recoveryDebt;

    /** Banks recovery progress and returns however many whole ticks are now due. */
    public int addRecoveryProgress(float amount) {
        recoveryDebt += amount;
        int whole = (int) recoveryDebt;
        recoveryDebt -= whole;
        return whole;
    }

    /**
     * Ticks of recovery owed to harmful effects. Shortening an effect means replacing it, so the
     * debt is banked and paid off in one batch rather than every tick.
     */
    private int pendingEffectTicks;

    public void addPendingEffectTicks(int ticks) {
        pendingEffectTicks += ticks;
    }

    /** Hands over the banked ticks and clears the tally. */
    public int takePendingEffectTicks() {
        int owed = pendingEffectTicks;
        pendingEffectTicks = 0;
        return owed;
    }

    /** Whether any perk is currently on cooldown, so the HUD is worth resyncing. */
    public boolean hasAnyCooldown(long gameTime) {
        for (Long expiry : cooldowns.values()) {
            if (expiry > gameTime) {
                return true;
            }
        }
        return false;
    }

    // --- soulweb ---

    @Nullable
    public Soulweb soulweb() {
        return soulweb;
    }

    public int webLevel() {
        return webLevel;
    }

    public Soulweb getOrCreateSoulweb(RandomSource random, RegistryAccess registries) {
        if (soulweb == null) {
            soulweb = SoulwebGenerator.generate(random, registries, unlockedPerks, unlockedAddons, webLevel);
        } else if (soulweb.isExhausted()) {
            // Covers a web left spent by an older build or a config change.
            rerollSoulweb(random, registries);
        }
        return soulweb;
    }

    /** Rolls a brand new web and bumps the level counter. */
    public Soulweb rerollSoulweb(RandomSource random, RegistryAccess registries) {
        webLevel++;
        soulweb = SoulwebGenerator.generate(random, registries, unlockedPerks, unlockedAddons, webLevel);
        return soulweb;
    }

    // --- transient dash state ---
    // Server-side only and deliberately not serialised: a dash never survives a relog.

    private int dashTicksRemaining;
    private double dashX;
    private double dashZ;
    private long damageImmuneUntil;

    /** Starts a dash along the given horizontal direction. */
    public void startDash(int ticks, double dirX, double dirZ, long gameTime, int immunityTicks) {
        this.dashTicksRemaining = ticks;
        this.dashX = dirX;
        this.dashZ = dirZ;
        this.damageImmuneUntil = gameTime + immunityTicks;
    }

    public boolean isDashing() {
        return dashTicksRemaining > 0;
    }

    public int dashTicksRemaining() {
        return dashTicksRemaining;
    }

    public double dashX() {
        return dashX;
    }

    public double dashZ() {
        return dashZ;
    }

    public void decrementDash() {
        if (dashTicksRemaining > 0) {
            dashTicksRemaining--;
        }
    }

    public boolean isDamageImmune(long gameTime) {
        return gameTime < damageImmuneUntil;
    }

    /** Grants damage immunity without a dash, for Adrenaline. */
    public void setDamageImmuneUntil(long gameTime) {
        this.damageImmuneUntil = gameTime;
    }

    /** Dashes still owed by Fingerless Glove, and how long the player has to call for one. */
    private int dashesLeft;
    private long redashUntil;

    public int dashesLeft() {
        return dashesLeft;
    }

    public boolean hasRedash(long gameTime) {
        return dashesLeft > 0 && gameTime < redashUntil;
    }

    public void openRedash(int dashes, long until) {
        this.dashesLeft = dashes;
        this.redashUntil = until;
    }

    /** True exactly once, on the tick an unused re-dash window lapses. */
    public boolean consumeLapsedRedash(long gameTime) {
        if (dashesLeft > 0 && gameTime >= redashUntil) {
            clearRedash();
            return true;
        }
        return false;
    }

    public void clearRedash() {
        this.dashesLeft = 0;
        this.redashUntil = 0L;
    }

    // --- transient Low Profile state ---

    /** Whether the night vision the player has came from Low Profile, so only ours is taken back. */
    private boolean lowProfileNightVision;
    /** Blocks walked while crouched, banked towards Steel Toe Boot's next point of health. */
    private float crouchDistance;
    private double lastCrouchX;
    private double lastCrouchZ;
    private boolean crouchTracked;

    public boolean lowProfileNightVision() {
        return lowProfileNightVision;
    }

    public void setLowProfileNightVision(boolean granted) {
        this.lowProfileNightVision = granted;
    }

    /** Adds the horizontal distance walked since the last call and returns the running total. */
    public float accumulateCrouchDistance(double x, double z) {
        if (crouchTracked) {
            double dx = x - lastCrouchX;
            double dz = z - lastCrouchZ;
            crouchDistance += (float) Math.sqrt(dx * dx + dz * dz);
        }
        lastCrouchX = x;
        lastCrouchZ = z;
        crouchTracked = true;
        return crouchDistance;
    }

    public void spendCrouchDistance(float amount) {
        crouchDistance -= amount;
    }

    /** Forgets where the player was, so standing up does not bank a teleport's worth of distance. */
    public void resetCrouchTracking() {
        crouchTracked = false;
        crouchDistance = 0.0F;
    }

    /** Game time until which the Sterilizer retry window is open; 0 when there is none. */
    private long sutureRetryUntil;
    /** Whether the player was at full health when they triggered Surgical Suture, for Gel Dressing. */
    private boolean sutureUsedAtFullHealth;

    public boolean sutureUsedAtFullHealth() {
        return sutureUsedAtFullHealth;
    }

    public void setSutureUsedAtFullHealth(boolean full) {
        this.sutureUsedAtFullHealth = full;
    }

    public void openSutureRetry(long until) {
        this.sutureRetryUntil = until;
    }

    public boolean hasSutureRetry(long gameTime) {
        return sutureRetryUntil > 0 && gameTime < sutureRetryUntil;
    }

    /** True exactly once, on the tick the unused retry window lapses. */
    public boolean consumeLapsedSutureRetry(long gameTime) {
        if (sutureRetryUntil > 0 && gameTime >= sutureRetryUntil) {
            sutureRetryUntil = 0;
            return true;
        }
        return false;
    }

    public void clearSutureRetry() {
        this.sutureRetryUntil = 0;
    }

    // --- transient Broken Movement Device state ---
    // Server-side only and deliberately not serialised: a return point never outlives the session
    // it was set in.

    private boolean recallActive;
    private double recallX;
    private double recallY;
    private double recallZ;
    private float recallYRot;
    private float recallXRot;
    /** The health the player had when they set the point, restored exactly on return. */
    private float recallHealth;
    /** The dimension the point was set in; returning from another world is refused. */
    private ResourceLocation recallDimension;
    /** Game time the point vanishes on its own, unused. */
    private long recallExpiresAt;

    public void setRecallPoint(double x, double y, double z, float yRot, float xRot, float health,
            ResourceLocation dimension, long expiresAt) {
        this.recallActive = true;
        this.recallX = x;
        this.recallY = y;
        this.recallZ = z;
        this.recallYRot = yRot;
        this.recallXRot = xRot;
        this.recallHealth = health;
        this.recallDimension = dimension;
        this.recallExpiresAt = expiresAt;
    }

    public boolean hasRecallPoint() {
        return recallActive;
    }

    public double recallX() {
        return recallX;
    }

    public double recallY() {
        return recallY;
    }

    public double recallZ() {
        return recallZ;
    }

    public float recallYRot() {
        return recallYRot;
    }

    public float recallXRot() {
        return recallXRot;
    }

    public float recallHealth() {
        return recallHealth;
    }

    @Nullable
    public ResourceLocation recallDimension() {
        return recallDimension;
    }

    public long recallExpiresAt() {
        return recallExpiresAt;
    }

    public void clearRecallPoint() {
        this.recallActive = false;
        this.recallDimension = null;
        this.recallExpiresAt = 0L;
    }

    /** True exactly once, on the tick an unused return point lapses. */
    public boolean consumeLapsedRecall(long gameTime) {
        if (recallActive && gameTime >= recallExpiresAt) {
            clearRecallPoint();
            return true;
        }
        return false;
    }

    // --- transient Broken Movement Device addon state ---

    /** Where the player stood just before the last return, for Diagnostic Tool C to hand back. */
    private boolean undoActive;
    private double undoX;
    private double undoY;
    private double undoZ;
    private float undoYRot;
    private float undoXRot;
    private float undoHealth;
    private long undoExpiresAt;

    public void setUndoPoint(double x, double y, double z, float yRot, float xRot, float health, long expiresAt) {
        this.undoActive = true;
        this.undoX = x;
        this.undoY = y;
        this.undoZ = z;
        this.undoYRot = yRot;
        this.undoXRot = xRot;
        this.undoHealth = health;
        this.undoExpiresAt = expiresAt;
    }

    public boolean hasUndoPoint(long gameTime) {
        return undoActive && gameTime < undoExpiresAt;
    }

    public double undoX() {
        return undoX;
    }

    public double undoY() {
        return undoY;
    }

    public double undoZ() {
        return undoZ;
    }

    public float undoYRot() {
        return undoYRot;
    }

    public float undoXRot() {
        return undoXRot;
    }

    public float undoHealth() {
        return undoHealth;
    }

    public void clearUndoPoint() {
        this.undoActive = false;
        this.undoExpiresAt = 0L;
    }

    /** The path walked while the point was up, oldest first, for Diagnostic Tool B to play back. */
    private final List<double[]> recallTrail = new ArrayList<>();

    public void recordRecallStep(double x, double y, double z, int limit) {
        if (recallTrail.size() >= limit) {
            recallTrail.remove(0);
        }
        recallTrail.add(new double[] { x, y, z });
    }

    public List<double[]> recallTrail() {
        return recallTrail;
    }

    public void clearRecallTrail() {
        recallTrail.clear();
    }

    /** Ticks of rewind already played back; -1 when no rewind is running. */
    private int rewindTicks = -1;

    public void startRewind() {
        rewindTicks = 0;
    }

    public boolean isRewinding() {
        return rewindTicks >= 0;
    }

    /** @return how many ticks of the rewind have played, after counting this one */
    public int advanceRewind() {
        return ++rewindTicks;
    }

    public void stopRewind() {
        rewindTicks = -1;
    }

    // --- transient Low-Cost Movement Device state ---
    // Not serialised: a fresh session hands the player a full set of charges.

    /** Charges left, or -1 while the device has not been fired at all this session. */
    private int deviceCharges = -1;
    /** Game time the next charge, or the whole set, comes back. */
    private long deviceRechargeAt;
    /** Game time until which a collision still counts as a Box Opener ram. */
    private long deviceImpactUntil;

    public int deviceCharges(int max) {
        return deviceCharges < 0 ? max : Math.min(deviceCharges, max);
    }

    public void setDeviceCharges(int charges) {
        this.deviceCharges = Math.max(0, charges);
    }

    public long deviceRechargeAt() {
        return deviceRechargeAt;
    }

    public void setDeviceRechargeAt(long gameTime) {
        this.deviceRechargeAt = gameTime;
    }

    public boolean isDeviceRamming(long gameTime) {
        return gameTime < deviceImpactUntil;
    }

    public void setDeviceRammingUntil(long gameTime) {
        this.deviceImpactUntil = gameTime;
    }

    // --- transient Bank Shot state ---

    /** Bounces left, or -1 while the perk has not been used at all this session. */
    private int bankCharges = -1;
    /** Game time the next one comes back. */
    private long bankRechargeAt;

    public int bankCharges(int max) {
        return bankCharges < 0 ? max : Math.min(bankCharges, max);
    }

    public void setBankCharges(int charges) {
        this.bankCharges = Math.max(0, charges);
    }

    public long bankRechargeAt() {
        return bankRechargeAt;
    }

    public void setBankRechargeAt(long gameTime) {
        this.bankRechargeAt = gameTime;
    }

    // --- transient Advanced Movement Device state ---
    // Charges are a pool rather than a count, so they are kept as a float. Not serialised: a fresh
    // session hands the player a full reserve.

    /** Charges in the reserve, or -1 while the device has not been used at all this session. */
    private float amdCharges = -1.0F;

    public float amdCharges(float max) {
        return amdCharges < 0.0F ? max : Math.min(amdCharges, max);
    }

    public void setAmdCharges(float charges) {
        this.amdCharges = Math.max(0.0F, charges);
    }

    // --- transient Beware The Power Of An Angel state ---

    /** Charges left, or -1 while the wings have not been used at all this session. */
    private float angelCharges = -1.0F;
    /**
     * Whether the player is off the ground on the perk's account. Set on take-off and only cleared
     * by landing — not by letting go of the key — because the charges and the Mark Of The Banished
     * both run until the feet are down again.
     */
    private boolean angelAirborne;
    /** Whether the flight abilities are granted right now, which the key does control. */
    private boolean angelWinged;

    public float angelCharges(float max) {
        return angelCharges < 0.0F ? max : Math.min(angelCharges, max);
    }

    public void setAngelCharges(float charges) {
        this.angelCharges = Math.max(0.0F, charges);
    }

    public boolean isAngelAirborne() {
        return angelAirborne;
    }

    public void setAngelAirborne(boolean airborne) {
        this.angelAirborne = airborne;
    }

    public boolean isAngelWinged() {
        return angelWinged;
    }

    public void setAngelWinged(boolean winged) {
        this.angelWinged = winged;
    }

    // --- transient generic charge pools ---
    // Every perk that came after the first few keeps its charges here rather than in a field of its
    // own. Not serialised, like the rest of them: a fresh session starts full.

    private final Map<ResourceLocation, Float> perkCharges = new HashMap<>();
    private final Map<ResourceLocation, Long> perkRechargeAt = new HashMap<>();

    /** Charges a perk has left. An untouched pool reads as full. */
    public float perkCharges(ResourceLocation perkId, float max) {
        Float held = perkCharges.get(perkId);
        return held == null ? max : Math.min(held, max);
    }

    public void setPerkCharges(ResourceLocation perkId, float charges) {
        perkCharges.put(perkId, Math.max(0.0F, charges));
    }

    public long perkRechargeAt(ResourceLocation perkId) {
        return perkRechargeAt.getOrDefault(perkId, 0L);
    }

    public void setPerkRechargeAt(ResourceLocation perkId, long gameTime) {
        perkRechargeAt.put(perkId, gameTime);
    }

    /**
     * Fills every charge pool the player has, whichever perk it belongs to. Metal Syringe is the
     * only thing that asks for this: forgetting a pool is how it reads as full again.
     */
    public void refillAllCharges() {
        perkCharges.clear();
        perkRechargeAt.clear();
        deviceCharges = -1;
        deviceRechargeAt = 0L;
        bankCharges = -1;
        bankRechargeAt = 0L;
        amdCharges = -1.0F;
        angelCharges = -1.0F;
    }

    // --- transient Catching Up state ---

    /** Game time the Speed from a trail runs out, whether or not the player is still on one. */
    private long trailSpeedUntil;

    public long trailSpeedUntil() {
        return trailSpeedUntil;
    }

    public void setTrailSpeedUntil(long gameTime) {
        this.trailSpeedUntil = gameTime;
    }

    // --- transient Nasty Blade state ---
    // The streak is deliberately not serialised: it is worth seconds, and starting a session on
    // one the player earned before logging out would be nonsense.

    private int bladeTokens;
    private long bladeExpiresAt;
    /** Game time the blade will take another token, so a flurry of hits banks one, not five. */
    private long bladeNextTokenAt;

    public int bladeTokens() {
        return bladeTokens;
    }

    public boolean canBankBladeToken(long gameTime) {
        return gameTime >= bladeNextTokenAt;
    }

    /** Banks a token, pushes the streak's expiry back out, and shuts the door for a moment. */
    public void addBladeToken(long expiresAt, long nextTokenAt) {
        this.bladeTokens++;
        this.bladeExpiresAt = expiresAt;
        this.bladeNextTokenAt = nextTokenAt;
    }

    /** Keeps a streak alive on a hit that came too soon to be worth a token of its own. */
    public void refreshBladeStreak(long expiresAt) {
        if (bladeTokens > 0) {
            this.bladeExpiresAt = expiresAt;
        }
    }

    /** True exactly once, on the tick a streak runs out. */
    public boolean consumeLapsedBladeStreak(long gameTime) {
        if (bladeTokens > 0 && gameTime >= bladeExpiresAt) {
            bladeTokens = 0;
            bladeExpiresAt = 0L;
            bladeNextTokenAt = 0L;
            return true;
        }
        return false;
    }

    // --- transient Omniscience state ---
    // Where the player last was and how long they have been there. Not serialised: a reveal is
    // rebuilt from scratch the moment they settle again.

    private double omniX;
    private double omniY;
    private double omniZ;
    private boolean omniTracked;
    private long omniStillSince;
    private boolean omniRevealed;
    private long omniRefreshAt;

    /** True when the player has shifted since the last check, which also re-reads their position. */
    public boolean hasOmniscienceMoved(double x, double y, double z, double epsilon) {
        if (!omniTracked) {
            return true;
        }
        return Math.abs(x - omniX) > epsilon || Math.abs(y - omniY) > epsilon || Math.abs(z - omniZ) > epsilon;
    }

    public void markOmniscienceMoved(double x, double y, double z, long gameTime) {
        this.omniX = x;
        this.omniY = y;
        this.omniZ = z;
        this.omniTracked = true;
        this.omniStillSince = gameTime;
    }

    public long omniscienceStillSince() {
        return omniStillSince;
    }

    public boolean isOmniscienceRevealed() {
        return omniRevealed;
    }

    public long omniscienceRefreshAt() {
        return omniRefreshAt;
    }

    public void setOmniscienceRevealed(boolean revealed, long refreshAt) {
        this.omniRevealed = revealed;
        this.omniRefreshAt = refreshAt;
    }

    // --- serialisation ---

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();

        CompoundTag unlocked = new CompoundTag();
        unlockedPerks.forEach((id, tier) -> unlocked.putInt(id.toString(), tier));
        tag.put("unlocked", unlocked);

        CompoundTag loadoutTag = new CompoundTag();
        for (int i = 0; i < LOADOUT_SIZE; i++) {
            if (loadout[i] != null) {
                loadoutTag.putString(String.valueOf(i), loadout[i].toString());
            }
        }
        tag.put("loadout", loadoutTag);

        CompoundTag cooldownTag = new CompoundTag();
        cooldowns.forEach((id, expiry) -> cooldownTag.putLong(id.toString(), expiry));
        tag.put("cooldowns", cooldownTag);

        ListTag addonList = new ListTag();
        unlockedAddons.forEach(id -> addonList.add(StringTag.valueOf(id.toString())));
        tag.put("addons", addonList);
        CompoundTag equippedAddonTag = new CompoundTag();
        equippedAddons.forEach((perkId, addonId) -> equippedAddonTag.putString(perkId.toString(), addonId.toString()));
        tag.put("equippedAddons", equippedAddonTag);
        tag.putFloat("grantedAbsorption", grantedAbsorption);

        tag.putInt("webLevel", webLevel);
        if (soulweb != null) {
            tag.put("soulweb", soulweb.save(provider));
        }
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        unlockedPerks.clear();
        CompoundTag unlocked = tag.getCompound("unlocked");
        for (String key : unlocked.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) {
                unlockedPerks.put(id, unlocked.getInt(key));
            }
        }

        CompoundTag loadoutTag = tag.getCompound("loadout");
        for (int i = 0; i < LOADOUT_SIZE; i++) {
            String key = String.valueOf(i);
            loadout[i] = loadoutTag.contains(key) ? ResourceLocation.tryParse(loadoutTag.getString(key)) : null;
        }

        cooldowns.clear();
        CompoundTag cooldownTag = tag.getCompound("cooldowns");
        for (String key : cooldownTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) {
                cooldowns.put(id, cooldownTag.getLong(key));
            }
        }

        unlockedAddons.clear();
        ListTag addonList = tag.getList("addons", Tag.TAG_STRING);
        for (int i = 0; i < addonList.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(addonList.getString(i));
            if (id != null) {
                unlockedAddons.add(id);
            }
        }
        equippedAddons.clear();
        CompoundTag equippedAddonTag = tag.getCompound("equippedAddons");
        for (String key : equippedAddonTag.getAllKeys()) {
            ResourceLocation perkId = ResourceLocation.tryParse(key);
            ResourceLocation addonId = ResourceLocation.tryParse(equippedAddonTag.getString(key));
            if (perkId != null && addonId != null) {
                equippedAddons.put(perkId, addonId);
            }
        }
        grantedAbsorption = tag.getFloat("grantedAbsorption");

        webLevel = Math.max(1, tag.getInt("webLevel"));
        soulweb = tag.contains("soulweb") ? Soulweb.load(provider, tag.getCompound("soulweb")) : null;
    }
}
