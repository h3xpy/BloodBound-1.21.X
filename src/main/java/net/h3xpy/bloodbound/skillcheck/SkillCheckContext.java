package net.h3xpy.bloodbound.skillcheck;

/**
 * Why a skill check was raised, so the result can be routed back to whatever asked for it.
 */
public enum SkillCheckContext {
    /** Surgical Suture's self-heal. */
    SELF_HEAL,
    /** The second attempt granted by the Sterilizer addon. */
    SELF_HEAL_RETRY,
    /** Raised at random while healing another player. */
    HEAL,
    /** One step of a Tinkerer repair run. */
    TINKERER,
    /** One of the checks Guardian Angel asks for in exchange for a life. */
    GUARDIAN
}
