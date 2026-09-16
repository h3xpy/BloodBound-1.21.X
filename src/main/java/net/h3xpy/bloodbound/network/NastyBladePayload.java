package net.h3xpy.bloodbound.network;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: how many Nasty Blade tokens the player is holding and when the streak lapses.
 * Zero tokens means the streak is over.
 * <p>
 * A streak is worth seconds and is never serialised, so it cannot ride along on the usual state
 * sync and gets a packet of its own.
 */
public record NastyBladePayload(int tokens, long expiresAt) implements CustomPacketPayload {
    public static final Type<NastyBladePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "nasty_blade"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NastyBladePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, NastyBladePayload::tokens,
                    ByteBufCodecs.VAR_LONG, NastyBladePayload::expiresAt,
                    NastyBladePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
