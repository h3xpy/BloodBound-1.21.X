package net.h3xpy.bloodbound.network;

import java.util.List;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: what Omniscience has found for this player to see. Three empty lists put the
 * reveal away again.
 */
public record OmnisciencePayload(List<BlockPos> ores, List<BlockPos> containers, List<Integer> entities)
        implements CustomPacketPayload {

    public static final Type<OmnisciencePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "omniscience"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OmnisciencePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), OmnisciencePayload::ores,
                    BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), OmnisciencePayload::containers,
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), OmnisciencePayload::entities,
                    OmnisciencePayload::new);

    /** Takes the whole reveal away. */
    public static OmnisciencePayload clear() {
        return new OmnisciencePayload(List.of(), List.of(), List.of());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
