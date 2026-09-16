package net.h3xpy.bloodbound.network;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: this projectile is a Bank Shot, and has this many bounces left in it.
 * <p>
 * Sent the moment it is fired rather than when it hits something, because the client simulates its
 * own copy of every projectile in flight. Left to itself that copy runs into the wall, sticks, and
 * jerks backwards while the server drags it out again — so the client has to know about the bounce
 * before the wall, not after it.
 */
public record BankShotMarkPayload(int entityId, int bounces) implements CustomPacketPayload {

    public static final Type<BankShotMarkPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "bank_shot_mark"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BankShotMarkPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BankShotMarkPayload::entityId,
                    ByteBufCodecs.VAR_INT, BankShotMarkPayload::bounces,
                    BankShotMarkPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
