package net.h3xpy.bloodbound.network;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the heal key was pressed or released.
 */
public record HealInputPayload(boolean holding) implements CustomPacketPayload {
    public static final Type<HealInputPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "heal_input"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HealInputPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, HealInputPayload::holding, HealInputPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
