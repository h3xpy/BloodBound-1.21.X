package net.h3xpy.bloodbound.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * The client half of Aura Revealed: outlines the revealed entities through walls on this screen
 * alone.
 * <p>
 * The glow is the vanilla outline, driven by the entity's shared "glowing" flag. That flag normally
 * comes down from the server for everybody at once; setting it here, on one client, is what makes
 * the reveal private to the player who earned it.
 */
public final class ClientAuraReveal {

    /** Entity id to the client tick the outline comes off at. */
    private static final Map<Integer, Long> REVEALED = new HashMap<>();

    private static long clientTicks;

    private ClientAuraReveal() {}

    /** Whether this entity is currently held by an Aura Revealed, rather than by anything else. */
    public static boolean isRevealed(int entityId) {
        return REVEALED.containsKey(entityId);
    }

    public static void reveal(int entityId, int durationTicks) {
        if (durationTicks <= 0) {
            conceal(entityId);
            return;
        }
        REVEALED.put(entityId, clientTicks + durationTicks);
        applyGlow(entityId, true);
    }

    private static void conceal(int entityId) {
        REVEALED.remove(entityId);
        applyGlow(entityId, false);
    }

    /**
     * Keeps the flag on. An entity that leaves view and comes back is a fresh copy with the flag
     * clear, so it has to be set again rather than only once when the reveal lands.
     */
    public static void tick() {
        clientTicks++;
        if (REVEALED.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Integer, Long>> iterator = REVEALED.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Long> entry = iterator.next();
            if (clientTicks >= entry.getValue()) {
                applyGlow(entry.getKey(), false);
                iterator.remove();
                continue;
            }
            applyGlow(entry.getKey(), true);
        }
    }

    /** Clears everything on disconnect, so a second world does not inherit the first one's glows. */
    public static void reset() {
        for (Integer entityId : REVEALED.keySet()) {
            applyGlow(entityId, false);
        }
        REVEALED.clear();
        clientTicks = 0L;
    }

    private static void applyGlow(int entityId, boolean glowing) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Entity entity = minecraft.level.getEntity(entityId);
        // Omniscience may be holding the same entity for its own reasons; leave it alone.
        if (entity != null && (glowing || !ClientOmniscience.isRevealed(entityId))) {
            // Flag 6 is the glowing bit the outline renderer reads.
            entity.setSharedFlag(6, glowing);
        }
    }
}
