package net.h3xpy.bloodbound.network;

import java.util.ArrayList;
import java.util.List;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: marks that have just been laid down.
 * <p>
 * Each mark is sent once and nothing follows it. Its whole life — fading in, holding, fading out —
 * is run by the client off the moment it arrived, so a trail costs one small packet per mark and
 * nothing per tick after that.
 */
public record MarksPayload(List<Mark> marks) implements CustomPacketPayload {

    /** A trail left by a player, only seen through a perk. */
    public static final byte KIND_PLAYER = 0;
    /** A trail left by a mob, only seen through a perk. */
    public static final byte KIND_MOB = 1;
    /** Blood from a Bleeding entity, which everyone sees. */
    public static final byte KIND_BLOOD = 2;

    /** One mark: the block it lies on, and what kind it is. */
    public record Mark(BlockPos pos, byte kind) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Mark> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, Mark::pos,
                        ByteBufCodecs.BYTE, Mark::kind,
                        Mark::new);
    }

    public static final Type<MarksPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "marks"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MarksPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.collection(ArrayList::new, Mark.STREAM_CODEC), MarksPayload::marks,
                    MarksPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
