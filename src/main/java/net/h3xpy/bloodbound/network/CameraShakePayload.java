package net.h3xpy.bloodbound.network;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: shake the camera, this hard (in degrees at its peak) for this many ticks. A new
 * shake only ever makes the one running stronger or longer, never weaker.
 */
public record CameraShakePayload(float strength, int ticks) implements CustomPacketPayload {
    public static final Type<CameraShakePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "camera_shake"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CameraShakePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.FLOAT, CameraShakePayload::strength,
                    ByteBufCodecs.VAR_INT, CameraShakePayload::ticks, CameraShakePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
