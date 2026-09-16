package net.h3xpy.bloodbound.network;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: how many charges one of the mod's metered effects has left, out of how many.
 * <p>
 * Bleeding and Exhausted both run on a bar rather than a plain duration, and a bar is not something
 * a status effect can carry — the amplifier is already doing other work. A count of zero with a
 * maximum of zero means the effect is gone and the bar should come off the screen.
 */
public record EffectChargesPayload(ResourceLocation effectId, int charges, int maxCharges)
        implements CustomPacketPayload {

    public static final Type<EffectChargesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "effect_charges"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EffectChargesPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, EffectChargesPayload::effectId,
                    ByteBufCodecs.VAR_INT, EffectChargesPayload::charges,
                    ByteBufCodecs.VAR_INT, EffectChargesPayload::maxCharges,
                    EffectChargesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
