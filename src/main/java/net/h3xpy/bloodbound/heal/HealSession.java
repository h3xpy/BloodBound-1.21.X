package net.h3xpy.bloodbound.heal;

import net.minecraft.server.level.ServerPlayer;

/**
 * One in-progress heal between a healer and a target. Both players have to hold still for it to
 * keep running.
 */
public class HealSession {
    private final ServerPlayer healer;
    private final ServerPlayer target;
    /** A Patch Up session: the player is mending themselves, on their own timer. */
    private final boolean selfHeal;

    private double lastHealerX;
    private double lastHealerZ;
    private double lastTargetX;
    private double lastTargetZ;

    /** Ticks of uninterrupted healing accumulated towards the next point of health. */
    private int progressTicks;
    /** Game time until which healing is paused after a botched skill check. */
    private long blockedUntil;
    /** Game time of the last skill check roll, so they are rolled once per second. */
    private long lastCheckRoll;

    /** Game time the session began, so a session can never outlive its usefulness. */
    private final long startedAt;

    public HealSession(ServerPlayer healer, ServerPlayer target, long gameTime, boolean selfHeal) {
        this.healer = healer;
        this.target = target;
        this.selfHeal = selfHeal;
        this.lastCheckRoll = gameTime;
        this.startedAt = gameTime;
        rememberPositions();
    }

    /** True once the session has run longer than any legitimate heal could take. */
    public boolean hasOverrun(long gameTime, int maxTicks) {
        return gameTime - startedAt > maxTicks;
    }

    public ServerPlayer healer() {
        return healer;
    }

    public ServerPlayer target() {
        return target;
    }

    public boolean isSelfHeal() {
        return selfHeal;
    }

    public void rememberPositions() {
        lastHealerX = healer.getX();
        lastHealerZ = healer.getZ();
        lastTargetX = target.getX();
        lastTargetZ = target.getZ();
    }

    /**
     * Whether either player has shifted horizontally since the last tick. Vertical movement is
     * ignored on purpose, so jumping in place to answer a skill check does not break the heal.
     */
    public boolean hasEitherMoved(double epsilon) {
        return horizontalDelta(healer.getX(), healer.getZ(), lastHealerX, lastHealerZ) > epsilon
                || horizontalDelta(target.getX(), target.getZ(), lastTargetX, lastTargetZ) > epsilon;
    }

    private static double horizontalDelta(double x, double z, double lastX, double lastZ) {
        double dx = x - lastX;
        double dz = z - lastZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public boolean isBlocked(long gameTime) {
        return gameTime < blockedUntil;
    }

    public void block(long gameTime, int ticks) {
        this.blockedUntil = gameTime + ticks;
        this.progressTicks = 0;
    }

    /** Advances the heal clock, returning true each time a whole point of health is due. */
    public boolean advance(int ticksPerPoint) {
        progressTicks++;
        if (progressTicks >= ticksPerPoint) {
            progressTicks = 0;
            return true;
        }
        return false;
    }

    /** Its own clock, for the bystanders First Aid Spray Can mends alongside the player. */
    private int bystanderTicks;

    public boolean advanceBystanders(int ticksPerPoint) {
        bystanderTicks++;
        if (bystanderTicks >= ticksPerPoint) {
            bystanderTicks = 0;
            return true;
        }
        return false;
    }

    /** True once a second has passed since the last skill check roll. */
    public boolean shouldRollCheck(long gameTime, int interval) {
        if (gameTime - lastCheckRoll >= interval) {
            lastCheckRoll = gameTime;
            return true;
        }
        return false;
    }
}
