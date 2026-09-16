package net.h3xpy.bloodbound.network;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: outline this entity through walls, for this viewer only. A duration of zero
 * takes the outline away again.
 */
public record AuraRevealPayload(int entityId, int durationTicks) implements CustomPacketPayload {
    public static final Type<AuraRevealPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "aura_reveal"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AuraRevealPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, AuraRevealPayload::entityId,
                    ByteBufCodecs.VAR_INT, AuraRevealPayload::durationTicks,
                    AuraRevealPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
