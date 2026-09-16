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
 * Server to client: take these trail marks off the ground now, rather than letting them fade.
 * <p>
 * Final Blow is the only thing that asks: a trail it was showing belongs to someone who has healed
 * back over the line, and those marks stop being its business the moment that happens.
 */
public record RemoveMarksPayload(List<BlockPos> positions) implements CustomPacketPayload {

    public static final Type<RemoveMarksPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "remove_marks"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RemoveMarksPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.collection(ArrayList::new, BlockPos.STREAM_CODEC), RemoveMarksPayload::positions,
                    RemoveMarksPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
