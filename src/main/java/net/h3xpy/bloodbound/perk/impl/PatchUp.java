package net.h3xpy.bloodbound.perk.impl;

import net.h3xpy.bloodbound.heal.HealManager;
import net.minecraft.server.level.ServerPlayer;

/**
 * Patch Up: press the slot key while crouched to start mending yourself, and again to stop.
 * <p>
 * The work is all in {@link HealManager}, which already knows how to run a heal with skill checks —
 * this only starts and stops one pointed at the player themselves. Standing up or moving still ends
 * it, the way it ends a co-op heal.
 */
public final class PatchUp {

    private PatchUp() {}

    /** The slot key holding this perk went down or came up. Only going down does anything. */
    public static void setHolding(ServerPlayer player, boolean holding) {
        if (holding) {
            HealManager.toggleSelfHeal(player);
        }
    }
}
