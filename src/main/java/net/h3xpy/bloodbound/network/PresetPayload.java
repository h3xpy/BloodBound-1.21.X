package net.h3xpy.bloodbound.network;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: load, save or empty one of the player's loadout presets at the perk table.
 */
public record PresetPayload(int index, int action) implements CustomPacketPayload {
    public static final int LOAD = 0;
    public static final int SAVE = 1;
    public static final int CLEAR = 2;

    public static final Type<PresetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "preset"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PresetPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PresetPayload::index,
            ByteBufCodecs.VAR_INT, PresetPayload::action,
            PresetPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
