package net.h3xpy.bloodbound.network;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the skill check key was pressed. {@code progress} is where the client saw the
 * needle, which the server accepts only if it is close to its own reading.
 */
public record SkillCheckInputPayload(int id, float progress) implements CustomPacketPayload {
    public static final Type<SkillCheckInputPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "skill_check_input"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkillCheckInputPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SkillCheckInputPayload::id,
                    ByteBufCodecs.FLOAT, SkillCheckInputPayload::progress,
                    SkillCheckInputPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
