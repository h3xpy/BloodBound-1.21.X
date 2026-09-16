package net.h3xpy.bloodbound.network;

import java.util.ArrayList;
import java.util.List;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the players in this level who are Under The Radar, and how much of their noise
 * is swallowed. Both halves of the perk are carried out on the listening client — hiding a nametag
 * and quietening a sound are things only the client that would have drawn or played them can do.
 */
public record HiddenPlayersPayload(List<Entry> players) implements CustomPacketPayload {

    /** One hidden player: their entity id, and the share of their sound removed, as a percentage. */
    public record Entry(int entityId, int percent) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, Entry::entityId,
                        ByteBufCodecs.VAR_INT, Entry::percent,
                        Entry::new);
    }

    public static final Type<HiddenPlayersPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "hidden_players"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HiddenPlayersPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.collection(ArrayList::new, Entry.STREAM_CODEC), HiddenPlayersPayload::players,
                    HiddenPlayersPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
