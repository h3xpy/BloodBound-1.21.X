package net.h3xpy.bloodbound.network;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the player has climbed onto a creature with Undersized, or got off it. Their
 * client drives the creature while it is on, so it has to know.
 */
public record UndersizedRidePayload(boolean riding) implements CustomPacketPayload {

    public static final Type<UndersizedRidePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "undersized_ride"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UndersizedRidePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, UndersizedRidePayload::riding, UndersizedRidePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
