package net.h3xpy.bloodbound.network;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the activation key bound to {@code slot} was pressed. The server decides
 * whether the perk sitting in that loadout slot is an active one and is ready to fire.
 */
public record ActivatePerkPayload(int slot) implements CustomPacketPayload {
    public static final Type<ActivatePerkPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "activate_perk"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ActivatePerkPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, ActivatePerkPayload::slot, ActivatePerkPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
