package net.h3xpy.bloodbound.network;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: fit an addon into the slot of the perk it belongs to, or clear that perk's slot
 * with an empty addon id.
 */
public record SetAddonPayload(String perkId, String addonId) implements CustomPacketPayload {
    public static final Type<SetAddonPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "set_addon"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetAddonPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, SetAddonPayload::perkId,
                    ByteBufCodecs.STRING_UTF8, SetAddonPayload::addonId,
                    SetAddonPayload::new);

    public static SetAddonPayload clear(ResourceLocation perkId) {
        return new SetAddonPayload(perkId.toString(), "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
