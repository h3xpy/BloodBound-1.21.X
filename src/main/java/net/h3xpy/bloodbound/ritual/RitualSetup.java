package net.h3xpy.bloodbound.ritual;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The laying of a ritual: hold the key, stand perfectly still, and do it for long enough.
 * <p>
 * Shared by every perk that lays something this way, since the rule is the same for all of them —
 * a step in any direction, or the key coming up, and the work is lost with nothing to show for it
 * and nothing paid either.
 */
public final class RitualSetup {

    /** One piece of work in progress. */
    private record Work(int tier, Vec3 origin, int ticks) {}

    /** How far the player may drift before it counts as moving. */
    private static final double STILL_EPSILON = 0.08D;

    private static final Map<UUID, Map<ResourceLocation, Work>> WORKING = new HashMap<>();

    private RitualSetup() {}

    /** The key going down starts the work; the key coming up abandons it. */
    public static void setHolding(ServerPlayer player, ResourceLocation perkId, int tier, boolean holding) {
        if (!holding) {
            stop(player, perkId, true);
            return;
        }
        // Started once and never restarted: a repeat report of the key being down must not throw
        // away the seconds already spent.
        WORKING.computeIfAbsent(player.getUUID(), id -> new HashMap<>())
                .putIfAbsent(perkId, new Work(tier, player.position(), 0));
    }

    public static boolean isWorking(ServerPlayer player, ResourceLocation perkId) {
        Map<ResourceLocation, Work> mine = WORKING.get(player.getUUID());
        return mine != null && mine.containsKey(perkId);
    }

    /**
     * Advances the work by a tick.
     *
     * @return how many ticks have been put in, or -1 when there is no work or it has just been lost
     */
    public static int advance(ServerPlayer player, ResourceLocation perkId, int neededTicks) {
        Map<ResourceLocation, Work> mine = WORKING.get(player.getUUID());
        Work work = mine == null ? null : mine.get(perkId);
        if (work == null) {
            return -1;
        }
        if (!player.isAlive()) {
            stop(player, perkId, false);
            return -1;
        }
        if (player.position().distanceToSqr(work.origin()) > STILL_EPSILON * STILL_EPSILON) {
            stop(player, perkId, false);
            player.displayClientMessage(Component.translatable("bloodbound.message.ritual_moved")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return -1;
        }

        int ticks = work.ticks() + 1;
        mine.put(perkId, new Work(work.tier(), work.origin(), ticks));

        if (ticks % 4 == 0) {
            player.serverLevel().sendParticles(ParticleTypes.ENCHANT, player.getX(),
                    player.getY() + 0.4D, player.getZ(), 4, 0.35D, 0.4D, 0.35D, 0.01D);
        }
        if (ticks % 5 == 0) {
            player.displayClientMessage(Component.translatable("bloodbound.message.ritual_setting",
                    Math.max(1, (neededTicks - ticks + 19) / 20)), true);
        }
        return ticks;
    }

    /** Ends the work, saying so only when the player let go of it themselves. */
    public static void stop(ServerPlayer player, ResourceLocation perkId, boolean announce) {
        Map<ResourceLocation, Work> mine = WORKING.get(player.getUUID());
        if (mine == null || mine.remove(perkId) == null) {
            return;
        }
        if (mine.isEmpty()) {
            WORKING.remove(player.getUUID());
        }
        if (announce) {
            player.displayClientMessage(Component.translatable("bloodbound.message.ritual_stopped")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
        }
    }

}
