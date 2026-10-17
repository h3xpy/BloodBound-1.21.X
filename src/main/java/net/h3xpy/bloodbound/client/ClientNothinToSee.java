package net.h3xpy.bloodbound.client;

import net.h3xpy.bloodbound.network.NothinToSeePayload;
import net.h3xpy.bloodbound.perk.impl.NothinToSee;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/**
 * Nothin' to See, client half: a hidden player is not drawn at all — no body, armour, held item,
 * cape, wings or stuck arrows. Their own third person view included: nothing means nothing. The
 * hidden player's own slot counts down the time they have left.
 */
public final class ClientNothinToSee {

    /** Game time the local player's invisibility runs out, or 0 when they are not hidden. */
    private static long selfEndsAt;

    private ClientNothinToSee() {}

    public static void handle(NothinToSeePayload payload) {
        NothinToSee.setClientHidden(payload.entityId(), payload.hidden());
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.getId() == payload.entityId()) {
            selfEndsAt = payload.hidden() ? payload.endsAt() : 0L;
        }
    }

    /** Ticks the local player stays hidden, or 0. */
    public static int ticksRemaining() {
        long remaining = selfEndsAt - ClientPerkData.gameTime();
        return remaining > 0L ? (int) remaining : 0;
    }

    public static void reset() {
        selfEndsAt = 0L;
        NothinToSee.resetClient();
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (NothinToSee.isClientHidden(event.getEntity().getId())) {
            event.setCanceled(true);
        }
    }

    /** An id is only good while that entity is here: once it leaves, the number may go to another. */
    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            NothinToSee.setClientHidden(event.getEntity().getId(), false);
        }
    }
}
