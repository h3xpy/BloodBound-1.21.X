package net.h3xpy.bloodbound.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.network.HiddenPlayersPayload;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Under The Radar, server half.
 * <p>
 * Keeps every client told who in their level is not to be seen or heard — the hiding itself
 * happens on those clients. And it stands between the player and anything that would reveal their
 * aura: the first attempt is blocked and opens a window in which every other attempt is blocked
 * too, after which the perk goes on cooldown and reveals land as normal.
 */
public final class UnderTheRadarHandler {

    /** How often the list is checked for changes, and how often it is resent regardless. */
    private static final int CHECK_INTERVAL = 10;
    private static final int RESEND_INTERVAL = 100;
    /** Everything is silenced now; the payload still carries a share for the client's sake. */
    private static final int SILENCED = 100;

    /** What each level was last told, so only a change goes over the wire between resends. */
    private static final Map<ResourceKey<Level>, List<HiddenPlayersPayload.Entry>> LAST = new HashMap<>();
    /** Game time each player's reveal-blocking window closes. */
    private static final Map<UUID, Long> WINDOW_UNTIL = new HashMap<>();

    private UnderTheRadarHandler() {}

    public static void tick(MinecraftServer server) {
        int tick = server.getTickCount();
        if (tick % CHECK_INTERVAL != 0) {
            return;
        }
        // Resent every few seconds too, which is what catches a player who has just joined or
        // arrived from another dimension without tracking either.
        boolean resend = tick % RESEND_INTERVAL == 0;

        for (ServerLevel level : server.getAllLevels()) {
            List<HiddenPlayersPayload.Entry> hidden = new ArrayList<>();
            for (ServerPlayer player : level.players()) {
                if (PerkDataManager.get(player).getActiveTier(ModPerks.UNDER_THE_RADAR) > 0) {
                    hidden.add(new HiddenPlayersPayload.Entry(player.getId(), SILENCED));
                }
            }

            List<HiddenPlayersPayload.Entry> previous = LAST.get(level.dimension());
            if (!resend && hidden.equals(previous)) {
                continue;
            }
            LAST.put(level.dimension(), hidden);
            HiddenPlayersPayload payload = new HiddenPlayersPayload(hidden);
            for (ServerPlayer player : level.players()) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    /**
     * Whether an attempt to reveal this player's aura is stopped. The first attempt outside the
     * cooldown opens the window — and puts the perk on cooldown for the window and the cooldown
     * together, so the HUD counts the whole of it down.
     */
    public static boolean blocksReveal(ServerPlayer player) {
        PlayerPerkData data = PerkDataManager.get(player);
        int tier = data.getActiveTier(ModPerks.UNDER_THE_RADAR);
        if (tier <= 0) {
            return false;
        }
        long now = player.level().getGameTime();
        if (now < WINDOW_UNTIL.getOrDefault(player.getUUID(), 0L)) {
            return true;
        }
        if (data.isOnCooldown(ModPerks.UNDER_THE_RADAR.id(), now)) {
            return false;
        }

        int window = ModPerks.UNDER_THE_RADAR.ticks(ModPerks.RADAR_WINDOW, tier);
        WINDOW_UNTIL.put(player.getUUID(), now + window);
        data.setCooldown(ModPerks.UNDER_THE_RADAR.id(), now,
                window + ModPerks.UNDER_THE_RADAR.cooldownTicks(tier));
        PerkDataManager.sync(player);

        player.displayClientMessage(Component.translatable("bloodbound.message.under_the_radar_hidden")
                .withStyle(ChatFormatting.DARK_AQUA), true);
        player.playNotifySound(SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.6F, 1.4F);
        return true;
    }

    public static void clear() {
        LAST.clear();
        WINDOW_UNTIL.clear();
    }

    public static void clear(UUID playerId) {
        WINDOW_UNTIL.remove(playerId);
    }
}
