package net.h3xpy.bloodbound.skillcheck;

/**
 * How forgiving a skill check is. The needle always sweeps the dial once; difficulty changes how
 * wide the success zone is and how long the sweep takes.
 * <p>
 * The window a player actually gets is {@code zoneWidth * durationTicks} ticks, so shrinking the
 * zone and speeding up the sweep compound: NORMAL leaves ~168 ms, HARD only ~40 ms.
 */
public enum SkillCheckDifficulty {
    /** Tinkerer's opening check: wide and slow, since every one after it is tighter. */
    EASY(0.16F, 28),
    /** The everyday check raised while healing someone. */
    NORMAL(0.105F, 24),
    /** Surgical Suture's self-heal check. Deliberately punishing: a 40 ms window. */
    HARD(0.04F, 20);

    private final float zoneWidth;
    private final int durationTicks;

    SkillCheckDifficulty(float zoneWidth, int durationTicks) {
        this.zoneWidth = zoneWidth;
        this.durationTicks = durationTicks;
    }

    /** Width of the success zone as a fraction of the full sweep. */
    public float zoneWidth() {
        return zoneWidth;
    }

    public int durationTicks() {
        return durationTicks;
    }
}
