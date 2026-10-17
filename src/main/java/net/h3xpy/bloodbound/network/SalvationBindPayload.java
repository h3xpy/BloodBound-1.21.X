package net.h3xpy.bloodbound.network;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: an entity is bound by Salvation for this many ticks, so its chains are drawn;
 * zero lets it go early.
 */
public record SalvationBindPayload(int entityId, int ticks) implements CustomPacketPayload {

    public static final Type<SalvationBindPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "salvation_bind"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SalvationBindPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SalvationBindPayload::entityId,
                    ByteBufCodecs.VAR_INT, SalvationBindPayload::ticks,
                    SalvationBindPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
