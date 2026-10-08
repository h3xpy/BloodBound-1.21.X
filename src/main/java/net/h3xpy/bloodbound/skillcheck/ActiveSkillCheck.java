package net.h3xpy.bloodbound.skillcheck;

import java.util.UUID;

import javax.annotation.Nullable;

/**
 * A skill check currently running for one player. The server owns the timing; the client renders a
 * copy of it and reports when the player hit the key.
 */
public class ActiveSkillCheck {
    private final int id;
    private final SkillCheckContext context;
    private final float zoneStart;
    private final float zoneWidth;
    private final int durationTicks;
    /** Steady Hands' great zone, from the start of the success zone; 0 when there is none. */
    private final float greatWidth;
    /** What landing in the great zone multiplies the reward by. */
    private final float greatMultiplier;
    private int elapsedTicks;
    /** Whoever's Panic Attack was on this check, for Aww.. Too Bad. */
    @Nullable
    private UUID panickedBy;

    public ActiveSkillCheck(int id, SkillCheckContext context, float zoneStart, float zoneWidth, int durationTicks,
            float greatWidth, float greatMultiplier) {
        this.id = id;
        this.context = context;
        this.zoneStart = zoneStart;
        this.zoneWidth = zoneWidth;
        this.durationTicks = durationTicks;
        this.greatWidth = greatWidth;
        this.greatMultiplier = greatMultiplier;
    }

    public float greatWidth() {
        return greatWidth;
    }

    public float greatMultiplier() {
        return greatMultiplier;
    }

    public int id() {
        return id;
    }

    public SkillCheckContext context() {
        return context;
    }

    public float zoneStart() {
        return zoneStart;
    }

    public float zoneWidth() {
        return zoneWidth;
    }

    public int durationTicks() {
        return durationTicks;
    }

    /** Needle position, 0 at the start of the sweep and 1 at the end. */
    public float progress() {
        return durationTicks <= 0 ? 1.0F : Math.min(1.0F, elapsedTicks / (float) durationTicks);
    }

    @Nullable
    public UUID panickedBy() {
        return panickedBy;
    }

    public void setPanickedBy(@Nullable UUID panickedBy) {
        this.panickedBy = panickedBy;
    }

    public void tick() {
        elapsedTicks++;
    }

    public boolean isExpired() {
        return elapsedTicks >= durationTicks;
    }

    /** Whether a needle position counts as a hit. */
    public boolean isInZone(float progress) {
        return progress >= zoneStart && progress <= zoneStart + zoneWidth;
    }

    /** Whether a needle position lands in Steady Hands' great zone. */
    public boolean isGreat(float progress) {
        return greatWidth > 0.0F && progress >= zoneStart && progress <= zoneStart + greatWidth;
    }
}
