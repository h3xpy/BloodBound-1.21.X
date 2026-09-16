package net.h3xpy.bloodbound.perk.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.event.PerkEventHandler;
import net.h3xpy.bloodbound.network.PerkChargesPayload;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.registry.ModEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Beware The Power Of An Angel: press the key and fly; press it again to stop.
 * <p>
 * The charges drain for as long as the player is off the ground, and only come back once they are
 * on it — so the reserve is not a cooldown but a distance. Folding the wings mid-air stops the
 * flying but not the meter: that only stops at the ground, which is also what puts the wings away
 * for good. Flight is granted through the player's own abilities, which is the only way it can feel
 * like vanilla flight: their client is the one flying them.
 */
public final class BewareThePowerOfAnAngel {

    /** Upward push when the wings are spread on the ground, so there is air to fly in. */
    private static final double GROUND_LIFT = 0.42D;
    /** Ticks after a take-off from the ground in which standing on it does not count as landing. */
    private static final int TAKEOFF_GRACE_TICKS = 6;

    /** Players with their wings spread — a toggle, not a key being held. */
    private static final Set<UUID> ENGAGED = new HashSet<>();
    /** When each player last took off, for the grace above. */
    private static final Map<UUID, Long> TAKEOFF_AT = new HashMap<>();

    private BewareThePowerOfAnAngel() {}

    /**
     * The key going down spreads or folds the wings. It coming up means nothing any more. Only the
     * transition is heard, never the repeat the key sends while it is held down.
     */
    public static void setHolding(ServerPlayer player, boolean holding) {
        if (!holding) {
            return;
        }
        UUID id = player.getUUID();
        if (ENGAGED.remove(id)) {
            player.playNotifySound(SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.5F, 0.7F);
            return;
        }

        PlayerPerkData data = PerkDataManager.get(player);
        int tier = data.getActiveTier(ModPerks.BEWARE_THE_POWER_OF_AN_ANGEL);
        if (tier <= 0 || data.angelCharges(maxCharges(data, tier)) <= 0.0F) {
            return;
        }
        ENGAGED.add(id);

        // From the ground the wings need something to push against first.
        if (player.onGround()) {
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, GROUND_LIFT, motion.z);
            player.hurtMarked = true;
            TAKEOFF_AT.put(id, player.level().getGameTime());
            takeOff(player, data);
        }
    }

    /** Nothing: the toggle is driven by the key going down, which is only ever reported once. */
    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        return false;
    }

    /** Runs the flight, the drain and the refill. Called once a tick per player. */
    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        int tier = data.getActiveTier(ModPerks.BEWARE_THE_POWER_OF_AN_ANGEL);
        if (tier <= 0) {
            // The perk coming out of the loadout has to take the wings with it.
            ENGAGED.remove(player.getUUID());
            if (data.isAngelAirborne() || data.isAngelWinged()) {
                land(player, data);
            }
            return;
        }

        float max = maxCharges(data, tier);
        float charges = data.angelCharges(max);
        boolean wants = ENGAGED.contains(player.getUUID()) && player.isAlive() && !player.isSpectator();
        boolean grounded = player.onGround()
                && gameTime - TAKEOFF_AT.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2) > TAKEOFF_GRACE_TICKS;

        if (grounded) {
            // Touching down is what ends a flight: it stops the drain, takes the Mark Of The
            // Banished off, folds the wings, and is the only state the reserve fills in.
            if (data.isAngelAirborne() || data.isAngelWinged()) {
                land(player, data);
            }
            if (charges < max) {
                double rate = (1.0D + PerkEventHandler.lightbringerRate(player)) / (rechargeSeconds(data) * 20.0D);
                data.setAngelCharges(Math.min(max, charges + (float) rate));
            }
        } else {
            if (!data.isAngelAirborne() && wants && charges > 0.0F) {
                takeOff(player, data);
            }

            if (data.isAngelAirborne()) {
                // Charged for the whole time in the air, wings spread or folded.
                charges -= (float) (drainPerSecond(data) / 20.0D);
                data.setAngelCharges(charges);

                boolean winged = wants && charges > 0.0F;
                setWings(player, data, winged);
                if (winged) {
                    feathers(player);
                }
            }
        }

        if (gameTime % 5L == 0L || data.isAngelAirborne()) {
            sendCharges(player, data, max);
        }
    }

    private static void takeOff(ServerPlayer player, PlayerPerkData data) {
        data.setAngelAirborne(true);
        setWings(player, data, true);

        player.level().playSound(null, player.blockPosition(), SoundEvents.PHANTOM_FLAP,
                SoundSource.PLAYERS, 0.7F, 1.2F);

        // Mark Of The Banished: all that flight, and nothing to hide behind while you use it. It
        // lasts as long as the flight does, which means until the ground.
        if (data.isAddonActive(ModAddons.MARK_OF_THE_BANISHED)) {
            player.addEffect(new MobEffectInstance(ModEffects.EXPOSED, -1, 0, false, true, true));
        }
    }

    /** Grants or revokes the flight abilities. */
    private static void setWings(ServerPlayer player, PlayerPerkData data, boolean winged) {
        if (data.isAngelWinged() == winged) {
            return;
        }
        data.setAngelWinged(winged);

        if (winged) {
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
            player.getAbilities().setFlyingSpeed(flySpeed(data));
        } else if (!player.isCreative() && !player.isSpectator()) {
            // Creative and spectator fly on their own account; the perk must not take that away.
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            // Back to vanilla's own speed, so creative flight later is not left at the perk's.
            player.getAbilities().setFlyingSpeed(0.05F);
        }
        player.onUpdateAbilities();
    }

    /** Feet on the ground: the flight is over and everything it brought with it goes. */
    private static void land(ServerPlayer player, PlayerPerkData data) {
        ENGAGED.remove(player.getUUID());
        data.setAngelAirborne(false);
        setWings(player, data, false);

        if (data.isAddonActive(ModAddons.MARK_OF_THE_BANISHED)) {
            player.removeEffect(ModEffects.EXPOSED);
        }
    }

    /** A trail of feathers, so flight is something the people below can see. */
    private static void feathers(ServerPlayer player) {
        if (player.tickCount % 3 != 0) {
            return;
        }
        player.serverLevel().sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 0.4D, player.getZ(), 2, 0.2D, 0.1D, 0.2D, 0.01D);
    }

    // --- the numbers ---

    private static float maxCharges(PlayerPerkData data, int tier) {
        double max = ModPerks.BEWARE_THE_POWER_OF_AN_ANGEL.value(ModPerks.ANGEL_MAX_CHARGES, tier);
        if (data.isAddonActive(ModAddons.GOLDEN_CROWN)) {
            max *= ModAddons.GOLDEN_CROWN_CHARGES;
        }
        if (data.isAddonActive(ModAddons.MARK_OF_THE_BANISHED)) {
            max *= ModAddons.BANISHED_CHARGES;
        }
        return (float) max;
    }

    private static double drainPerSecond(PlayerPerkData data) {
        return data.isAddonActive(ModAddons.BELT_PENDANT)
                ? ModAddons.BELT_PENDANT_DRAIN
                : ModPerks.ANGEL_DRAIN_PER_SECOND;
    }

    private static double rechargeSeconds(PlayerPerkData data) {
        if (data.isAddonActive(ModAddons.BRIGHT_FEATHER)) {
            return ModAddons.BRIGHT_FEATHER_RECHARGE_SECONDS;
        }
        if (data.isAddonActive(ModAddons.GOLDEN_CROWN)) {
            return ModAddons.GOLDEN_CROWN_RECHARGE_SECONDS;
        }
        return ModPerks.ANGEL_RECHARGE_SECONDS;
    }

    private static float flySpeed(PlayerPerkData data) {
        return data.isAddonActive(ModAddons.BELT_PENDANT)
                ? (float) (ModPerks.ANGEL_FLY_SPEED * ModAddons.BELT_PENDANT_SPEED)
                : ModPerks.ANGEL_FLY_SPEED;
    }

    private static void sendCharges(ServerPlayer player, PlayerPerkData data, float max) {
        float charges = data.angelCharges(max);
        PacketDistributor.sendToPlayer(player, new PerkChargesPayload(
                ModPerks.BEWARE_THE_POWER_OF_AN_ANGEL.id(),
                Math.round(charges), Math.round(max), 0L));
    }

    /**
     * Forgets a player entirely, and takes the wings off them on the way out.
     * <p>
     * The abilities are saved with the player, so a logout in mid-air would otherwise hand them
     * permanent creative flight — the one thing the perk must never leave behind.
     */
    public static void clear(ServerPlayer player) {
        ENGAGED.remove(player.getUUID());
        TAKEOFF_AT.remove(player.getUUID());
        PlayerPerkData data = PerkDataManager.get(player);
        if (data.isAngelAirborne() || data.isAngelWinged()) {
            land(player, data);
        }
    }
}
