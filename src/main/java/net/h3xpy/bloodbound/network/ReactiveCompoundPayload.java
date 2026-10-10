package net.h3xpy.bloodbound.network;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: where the player's Reactive Compound stands, for the pouring animation and the
 * coated weapon. One of {@link #NONE}, {@link #COATING} or {@link #COATED}.
 */
public record ReactiveCompoundPayload(int phase) implements CustomPacketPayload {
    public static final int NONE = 0;
    public static final int COATING = 1;
    public static final int COATED = 2;

    public static final Type<ReactiveCompoundPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "reactive_compound"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReactiveCompoundPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, ReactiveCompoundPayload::phase, ReactiveCompoundPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
