package net.h3xpy.bloodbound.network;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the player swung at a Holy Sanctum's shell. The server checks the reach and
 * prices the blow itself.
 */
public record SanctumHitPayload(int entityId) implements CustomPacketPayload {
    public static final Type<SanctumHitPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "sanctum_hit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SanctumHitPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, SanctumHitPayload::entityId, SanctumHitPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
