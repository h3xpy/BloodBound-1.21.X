package net.h3xpy.bloodbound.client;

import java.util.HashMap;
import java.util.Map;

import net.h3xpy.bloodbound.network.PerkGlowPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Which passive perks' slots are lit gold, and until when. */
public final class ClientPerkGlow {

    /** Game time each perk stays lit until; a state that holds is lit until put out. */
    private static final Map<ResourceLocation, Long> LIT_UNTIL = new HashMap<>();

    private ClientPerkGlow() {}

    public static void handle(PerkGlowPayload payload) {
        if (payload.ticks() == PerkGlowPayload.OFF) {
            LIT_UNTIL.remove(payload.perkId());
        } else if (payload.ticks() == PerkGlowPayload.ON) {
            LIT_UNTIL.put(payload.perkId(), Long.MAX_VALUE);
        } else {
            // A flash never cuts short a state that holds.
            LIT_UNTIL.merge(payload.perkId(), gameTime() + payload.ticks(), Math::max);
        }
    }

    public static boolean isLit(ResourceLocation perkId) {
        Long until = LIT_UNTIL.get(perkId);
        return until != null && gameTime() < until;
    }

    public static void reset() {
        LIT_UNTIL.clear();
    }

    private static long gameTime() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0L : minecraft.level.getGameTime();
    }
}
