package net.h3xpy.bloodbound.network;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the skill check is over. The client flashes the dial and clears it.
 */
public record SkillCheckResultPayload(int id, boolean success) implements CustomPacketPayload {
    public static final Type<SkillCheckResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "skill_check_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkillCheckResultPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SkillCheckResultPayload::id,
                    ByteBufCodecs.BOOL, SkillCheckResultPayload::success,
                    SkillCheckResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
