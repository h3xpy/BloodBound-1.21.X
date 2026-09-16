package net.h3xpy.bloodbound.perk;

/**
 * How a perk is triggered.
 */
public enum PerkType {
    /** Runs on its own from game events (falling, sneaking, ...). */
    PASSIVE,
    /** Requires the player to press the activation key. */
    ACTIVE
}
