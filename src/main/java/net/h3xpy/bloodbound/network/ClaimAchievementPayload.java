package net.h3xpy.bloodbound.network;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: claim the quest board reward of an earned advancement. */
public record ClaimAchievementPayload(ResourceLocation id) implements CustomPacketPayload {
    public static final Type<ClaimAchievementPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "claim_achievement"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimAchievementPayload> STREAM_CODEC =
            StreamCodec.composite(ResourceLocation.STREAM_CODEC, ClaimAchievementPayload::id,
                    ClaimAchievementPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
