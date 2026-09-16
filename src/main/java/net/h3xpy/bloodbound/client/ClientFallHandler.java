package net.h3xpy.bloodbound.client;

import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The way Perfect Landing's addons change a fall.
 * <p>
 * This runs on the client rather than the server on purpose: a player's own movement is simulated
 * by their client and the server takes the result, so a server-side nudge to their velocity would
 * simply be overwritten by the next position packet. What lands on the ground — the Speed, the
 * damage Dead Weight deals — is settled by the server, where it belongs.
 */
public final class ClientFallHandler {

    /** Vanilla's downward pull on a player, per tick. */
    private static final double GRAVITY = 0.08D;
    /** Vanilla's own air acceleration, which Helium adds a share of on top. */
    private static final double AIR_ACCELERATION = 0.02D;

    private ClientFallHandler() {}

    public static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || player.isSpectator() || player.getAbilities().flying) {
            return;
        }

        PlayerPerkData data = ClientPerkData.get();
        if (data.getActiveTier(ModPerks.PERFECT_LANDING) <= 0) {
            return;
        }
        // Only a real fall through open air: swimming, ladders and elytra have their own rules.
        if (player.onGround() || player.isInWater() || player.isInLava() || player.onClimbable()
                || player.isFallFlying()) {
            return;
        }

        Vec3 motion = player.getDeltaMovement();
        double extra = 0.0D;
        if (data.isAddonActive(ModAddons.HELIUM_INFLATED_BALLOON)) {
            extra = GRAVITY * ModAddons.HELIUM_FALL_REDUCTION;
        } else if (data.isAddonActive(ModAddons.DEAD_WEIGHT)) {
            extra = -GRAVITY * ModAddons.DEAD_WEIGHT_EXTRA_FALL;
        } else if (data.isAddonActive(ModAddons.MOMENTUM_FORMULA)
                && player.fallDistance >= ModAddons.MOMENTUM_MIN_FALL) {
            // Nothing for the first few blocks: the formula is about a fall that has already
            // started, not about stepping off a kerb.
            extra = -GRAVITY * (ModAddons.MOMENTUM_FALL_MULTIPLIER - 1.0D);
        }

        // Only on the way down: none of these should change how high a jump goes.
        if (extra != 0.0D && motion.y < 0.0D) {
            motion = motion.add(0.0D, extra, 0.0D);
            player.setDeltaMovement(motion);
        }

        if (data.isAddonActive(ModAddons.HELIUM_INFLATED_BALLOON)) {
            steer(player);
        }
    }

    /**
     * Helium's extra air control: the same acceleration vanilla applies from the movement keys,
     * added again at a fraction of its strength.
     */
    private static void steer(LocalPlayer player) {
        float forward = player.input.forwardImpulse;
        float strafe = player.input.leftImpulse;
        if (forward == 0.0F && strafe == 0.0F) {
            return;
        }

        Vec3 wanted = new Vec3(strafe, 0.0D, forward);
        double lengthSqr = wanted.lengthSqr();
        if (lengthSqr < 1.0E-7D) {
            return;
        }
        if (lengthSqr > 1.0D) {
            wanted = wanted.normalize();
        }
        wanted = wanted.scale(AIR_ACCELERATION * ModAddons.HELIUM_AIR_CONTROL);

        float sin = Mth.sin(player.getYRot() * Mth.DEG_TO_RAD);
        float cos = Mth.cos(player.getYRot() * Mth.DEG_TO_RAD);
        player.setDeltaMovement(player.getDeltaMovement().add(
                wanted.x * cos - wanted.z * sin, 0.0D, wanted.z * cos + wanted.x * sin));
    }
}
