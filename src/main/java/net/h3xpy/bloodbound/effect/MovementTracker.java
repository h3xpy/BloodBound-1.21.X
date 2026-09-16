package net.h3xpy.bloodbound.effect;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * How fast each player is really moving, as the server sees it.
 * <p>
 * A player's own velocity is not something the server can trust — the client moves them — so the
 * speed is read from how far they went since the last tick. That is what lets a leap at a sprint's
 * pace count as running even on the ticks the sprint flag drops out mid-jump, which is exactly when
 * Bleeding and Exhausted used to stop charging.
 */
public final class MovementTracker {

    /** Blocks a tick, sideways, above which a player in the air counts as running. A walk is ~0.22. */
    private static final double AIR_RUN_SPEED = 0.25D;

    private record Sample(double x, double z, double speed) {}

    private static final Map<UUID, Sample> SAMPLES = new HashMap<>();

    private MovementTracker() {}

    /** Records where the player is. Called once a tick per player. */
    public static void update(ServerPlayer player) {
        Sample last = SAMPLES.get(player.getUUID());
        double speed = 0.0D;
        if (last != null) {
            double dx = player.getX() - last.x();
            double dz = player.getZ() - last.z();
            speed = Math.sqrt(dx * dx + dz * dz);
        }
        SAMPLES.put(player.getUUID(), new Sample(player.getX(), player.getZ(), speed));
    }

    /** Sideways speed over the last tick, in blocks. */
    public static double speed(Player player) {
        Sample sample = SAMPLES.get(player.getUUID());
        return sample == null ? 0.0D : sample.speed();
    }

    /**
     * Whether an entity is running: sprinting, or — for a player — off the ground and covering
     * ground faster than a walk, which is what a sprint-jump looks like from the server.
     */
    public static boolean isRunning(LivingEntity entity) {
        if (entity.isSprinting()) {
            return true;
        }
        return entity instanceof Player player && !player.onGround() && speed(player) > AIR_RUN_SPEED;
    }

    public static void clear(UUID playerId) {
        SAMPLES.remove(playerId);
    }
}
