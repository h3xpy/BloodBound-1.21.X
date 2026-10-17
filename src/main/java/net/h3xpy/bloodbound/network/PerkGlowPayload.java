package net.h3xpy.bloodbound.network;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: a passive perk's slot lights up gold, or goes out.
 *
 * @param ticks {@link #ON} for as long as the condition holds, {@link #OFF} to put it out, or how
 *              many ticks a one-off flash lasts
 */
public record PerkGlowPayload(ResourceLocation perkId, int ticks) implements CustomPacketPayload {

    public static final int ON = -1;
    public static final int OFF = 0;

    public static final Type<PerkGlowPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "perk_glow"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PerkGlowPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, PerkGlowPayload::perkId,
                    ByteBufCodecs.VAR_INT, PerkGlowPayload::ticks,
                    PerkGlowPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
