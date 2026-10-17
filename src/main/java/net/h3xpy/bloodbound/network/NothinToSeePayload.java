package net.h3xpy.bloodbound.network;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to clients: a player went wholly invisible through Nothin' to See, or showed again.
 *
 * @param endsAt game time it runs out, for the hidden player's own HUD; 0 when shown again
 */
public record NothinToSeePayload(int entityId, boolean hidden, long endsAt) implements CustomPacketPayload {
    public static final Type<NothinToSeePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "nothin_to_see"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NothinToSeePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, NothinToSeePayload::entityId,
                    ByteBufCodecs.BOOL, NothinToSeePayload::hidden,
                    ByteBufCodecs.VAR_LONG, NothinToSeePayload::endsAt,
                    NothinToSeePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
