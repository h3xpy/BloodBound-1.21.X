package net.h3xpy.bloodbound.network;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: Vigilance switched on, with its bonus as a fraction, or off with 0. */
public record VigilancePayload(float bonus) implements CustomPacketPayload {
    public static final Type<VigilancePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "vigilance"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VigilancePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.FLOAT, VigilancePayload::bonus, VigilancePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
