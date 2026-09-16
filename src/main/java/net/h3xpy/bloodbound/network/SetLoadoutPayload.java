package net.h3xpy.bloodbound.network;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: equip a perk in a loadout slot, or clear the slot with an empty perk id.
 */
public record SetLoadoutPayload(int slot, String perkId) implements CustomPacketPayload {
    public static final Type<SetLoadoutPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "set_loadout"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetLoadoutPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetLoadoutPayload::slot,
            ByteBufCodecs.STRING_UTF8, SetLoadoutPayload::perkId,
            SetLoadoutPayload::new);

    /** Convenience for clearing a slot. */
    public static SetLoadoutPayload clear(int slot) {
        return new SetLoadoutPayload(slot, "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
