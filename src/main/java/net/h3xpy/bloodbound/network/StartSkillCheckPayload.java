package net.h3xpy.bloodbound.network;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: draw a skill check dial. The server keeps the authoritative timing.
 */
public record StartSkillCheckPayload(int id, float zoneStart, float zoneWidth, int durationTicks)
        implements CustomPacketPayload {
    public static final Type<StartSkillCheckPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "start_skill_check"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StartSkillCheckPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, StartSkillCheckPayload::id,
                    ByteBufCodecs.FLOAT, StartSkillCheckPayload::zoneStart,
                    ByteBufCodecs.FLOAT, StartSkillCheckPayload::zoneWidth,
                    ByteBufCodecs.VAR_INT, StartSkillCheckPayload::durationTicks,
                    StartSkillCheckPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
