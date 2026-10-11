package net.h3xpy.bloodbound.network;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: what the Perk Table's wiki tab needs that only the server knows — how popular
 * every perk and addon is in this world, in stars, and which BloodBound advancements the player has
 * earned. What they have already claimed travels with their perk data.
 */
public record WikiPayload(Map<ResourceLocation, Integer> perkStars, Map<ResourceLocation, Integer> addonStars,
        List<ResourceLocation> earned) implements CustomPacketPayload {

    public static final Type<WikiPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "wiki"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Map<ResourceLocation, Integer>> STARS =
            ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, ByteBufCodecs.VAR_INT);

    public static final StreamCodec<RegistryFriendlyByteBuf, WikiPayload> STREAM_CODEC =
            StreamCodec.composite(
                    STARS, WikiPayload::perkStars,
                    STARS, WikiPayload::addonStars,
                    ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), WikiPayload::earned,
                    WikiPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
