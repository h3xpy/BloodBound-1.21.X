package net.h3xpy.bloodbound.network;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: when the player's Broken Movement Device return point runs out, so the HUD can
 * count it down. Zero means there is no point up.
 * <p>
 * A return point is deliberately never serialised, so it cannot ride along on the usual state sync
 * and gets a packet of its own.
 */
public record RecallTimerPayload(long expiresAt) implements CustomPacketPayload {
    public static final Type<RecallTimerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "recall_timer"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecallTimerPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_LONG, RecallTimerPayload::expiresAt, RecallTimerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
