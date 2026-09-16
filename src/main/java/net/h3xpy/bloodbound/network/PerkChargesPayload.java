package net.h3xpy.bloodbound.network;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: how many charges one perk has left, out of how many, and when the next one is
 * due. A due time of zero means it is full.
 * <p>
 * Charges are deliberately never serialised, so they cannot ride along on the usual state sync and
 * get a packet of their own. Every perk that holds charges — the Low-Cost Movement Device, Bank
 * Shot — reports through this one.
 */
public record PerkChargesPayload(ResourceLocation perkId, int charges, int maxCharges, long rechargeAt)
        implements CustomPacketPayload {

    public static final Type<PerkChargesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "perk_charges"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PerkChargesPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, PerkChargesPayload::perkId,
                    ByteBufCodecs.VAR_INT, PerkChargesPayload::charges,
                    ByteBufCodecs.VAR_INT, PerkChargesPayload::maxCharges,
                    ByteBufCodecs.VAR_LONG, PerkChargesPayload::rechargeAt,
                    PerkChargesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
