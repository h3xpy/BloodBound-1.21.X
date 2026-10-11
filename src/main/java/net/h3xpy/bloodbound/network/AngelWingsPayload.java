package net.h3xpy.bloodbound.network;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to clients: a player's angel wings came out, or went away. */
public record AngelWingsPayload(int entityId, boolean spread) implements CustomPacketPayload {
    public static final Type<AngelWingsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "angel_wings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AngelWingsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, AngelWingsPayload::entityId,
                    ByteBufCodecs.BOOL, AngelWingsPayload::spread,
                    AngelWingsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
