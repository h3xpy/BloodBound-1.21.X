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
 * Server to client: what a perk has found for this player to see. Three empty lists put that
 * perk's reveal away again.
 * <p>
 * The source says which perk is talking, because more than one can be showing things at once and
 * neither should wipe the other's outlines off the screen.
 */
public record OmnisciencePayload(int source, List<BlockPos> ores, List<BlockPos> containers,
        List<Integer> entities) implements CustomPacketPayload {

    /** Omniscience's own reveal. */
    public static final int SOURCE_OMNISCIENCE = 0;
    /** What an Eavesdrop trap is reporting. */
    public static final int SOURCE_EAVESDROP = 1;

    public static final Type<OmnisciencePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "omniscience"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OmnisciencePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, OmnisciencePayload::source,
                    BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), OmnisciencePayload::ores,
                    BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), OmnisciencePayload::containers,
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), OmnisciencePayload::entities,
                    OmnisciencePayload::new);

    /** Takes the whole reveal away. */
    /** Takes one source's reveal away and leaves the others alone. */
    public static OmnisciencePayload clear(int source) {
        return new OmnisciencePayload(source, List.of(), List.of(), List.of());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
