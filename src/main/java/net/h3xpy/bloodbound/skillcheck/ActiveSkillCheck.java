package net.h3xpy.bloodbound.skillcheck;

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
    private int elapsedTicks;

    public ActiveSkillCheck(int id, SkillCheckContext context, float zoneStart, float zoneWidth, int durationTicks) {
        this.id = id;
        this.context = context;
        this.zoneStart = zoneStart;
        this.zoneWidth = zoneWidth;
        this.durationTicks = durationTicks;
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
}
