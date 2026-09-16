package net.h3xpy.bloodbound.network;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: a loadout slot's key was pressed or released. Only the transitions travel.
 * <p>
 * Perks that fire on a press use {@link ActivatePerkPayload} instead; this is for the ones that run
 * for as long as the key is held, such as Patch Up.
 */
public record SlotKeyPayload(int slot, boolean holding) implements CustomPacketPayload {
    public static final Type<SlotKeyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "slot_key"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SlotKeyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SlotKeyPayload::slot,
            ByteBufCodecs.BOOL, SlotKeyPayload::holding,
            SlotKeyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
