package net.h3xpy.bloodbound.client;

import java.util.HashMap;
import java.util.Map;

/**
 * The client's own tally of which projectiles in flight are Bank Shots, and how many bounces they
 * have left.
 * <p>
 * Keyed by entity id, which arrives before the entity itself often enough that looking the entity
 * up here would lose the mark. Entries are dropped as they are spent, and anything left behind by a
 * shot that quietly despawned goes when the world is unloaded.
 */
public final class ClientBankShots {

    private static final Map<Integer, Integer> BOUNCES = new HashMap<>();

    private ClientBankShots() {}

    public static void mark(int entityId, int bounces) {
        if (bounces > 0) {
            BOUNCES.put(entityId, bounces);
        } else {
            BOUNCES.remove(entityId);
        }
    }

    public static int bounces(int entityId) {
        return BOUNCES.getOrDefault(entityId, 0);
    }

    /** Takes one bounce off a shot, forgetting it once it has none left. */
    public static void spend(int entityId) {
        int left = bounces(entityId) - 1;
        if (left > 0) {
            BOUNCES.put(entityId, left);
        } else {
            BOUNCES.remove(entityId);
        }
    }

    public static void clear() {
        BOUNCES.clear();
    }
}
