package net.h3xpy.bloodbound.network;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: buy the soulweb node at this index. The server re-checks everything.
 */
public record PurchaseNodePayload(int nodeIndex) implements CustomPacketPayload {
    public static final Type<PurchaseNodePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "purchase_node"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PurchaseNodePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, PurchaseNodePayload::nodeIndex, PurchaseNodePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
