package net.h3xpy.bloodbound.client;

import java.util.HashMap;
import java.util.Map;

import net.h3xpy.bloodbound.network.EffectChargesPayload;
import net.minecraft.resources.ResourceLocation;

/** What the client knows about the mod's metered effects: how full each bar is. */
public final class ClientEffectCharges {

    /** Charges left and the size of the bar, per effect. */
    public record Bar(int charges, int maxCharges) {}

    private static final Map<ResourceLocation, Bar> BARS = new HashMap<>();

    private ClientEffectCharges() {}

    public static void accept(EffectChargesPayload payload) {
        if (payload.maxCharges() == 0) {
            BARS.remove(payload.effectId());
        } else {
            BARS.put(payload.effectId(), new Bar(payload.charges(), payload.maxCharges()));
        }
    }

    /** The bar for one effect, or null when the player is not under it. */
    public static Bar bar(ResourceLocation effectId) {
        return BARS.get(effectId);
    }

    public static void reset() {
        BARS.clear();
    }
}
