package net.h3xpy.bloodbound.network;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the player's full BloodBound state, so the perk table and HUD can render it.
 */
public record SyncPerkDataPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<SyncPerkDataPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "sync_perk_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPerkDataPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, SyncPerkDataPayload::data, SyncPerkDataPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
