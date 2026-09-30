package net.h3xpy.bloodbound.perk.impl;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.event.PerkEventHandler;
import net.h3xpy.bloodbound.network.PerkChargesPayload;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.registry.ModEffects;
import net.h3xpy.bloodbound.ritual.RitualManager;
import net.h3xpy.bloodbound.ritual.RitualSetup;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Inevitable Death: a long, exposed piece of work that leaves a circle nobody else can survive
 * standing in.
 * <p>
 * The laying is the risk — a dozen seconds of standing still, glowing for everyone to see. What it
 * buys is a ritual with a bank of charges, drained by every trespasser it holds at death's door,
 * and a ritual that hides itself at first and gives more of itself away the longer it burns.
 */
public final class InevitableDeath {

    /** Everyone currently held Exposed by somebody's ritual, so it can be lifted when they leave. */
    private static final Set<UUID> EXPOSED = new HashSet<>();

    private InevitableDeath() {}

    /** The press does nothing: the work is the hold. */
    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        return false;
    }

    public static void setHolding(ServerPlayer player, boolean holding) {
        PlayerPerkData data = PerkDataManager.get(player);
        int tier = data.getActiveTier(ModPerks.INEVITABLE_DEATH);
        if (tier <= 0) {
            RitualSetup.stop(player, ModPerks.INEVITABLE_DEATH.id(), false);
            return;
        }
        // No cooldown at all: a full reserve is what buys a circle, and only a full one.
        float max = maxCharges(tier);
        if (holding && data.perkCharges(ModPerks.INEVITABLE_DEATH.id(), max) < max) {
            RitualSetup.stop(player, ModPerks.INEVITABLE_DEATH.id(), false);
            player.displayClientMessage(Component.translatable("bloodbound.message.inevitable_not_full")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return;
        }
        RitualSetup.setHolding(player, ModPerks.INEVITABLE_DEATH.id(), tier, holding);
    }

    private static float maxCharges(int tier) {
        return (float) ModPerks.INEVITABLE_DEATH.value(ModPerks.INEVITABLE_CHARGES, tier);
    }

    /** Advances the laying, glowing all the while. Called once a tick per player. */
    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        int tier = data.getActiveTier(ModPerks.INEVITABLE_DEATH);
        if (tier <= 0) {
            RitualManager.remove(player.getUUID(), ModPerks.INEVITABLE_DEATH.id());
            return;
        }

        refill(player, data, tier, gameTime);

        int needed = ModPerks.INEVITABLE_DEATH.ticks(ModPerks.INEVITABLE_SETUP, tier);
        int held = RitualSetup.advance(player, ModPerks.INEVITABLE_DEATH.id(), needed);
        if (held < 0) {
            return;
        }

        // Laying it is done in the open, whether the player likes it or not.
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false, false));

        if (held < needed) {
            return;
        }

        RitualSetup.stop(player, ModPerks.INEVITABLE_DEATH.id(), false);
        player.removeEffect(MobEffects.GLOWING);
        RitualManager.place(player, ModPerks.INEVITABLE_DEATH.id(), RitualManager.Mood.HARMFUL, tier,
                ModPerks.INEVITABLE_DEATH.value(ModPerks.INEVITABLE_RADIUS, tier), maxCharges(tier));
        PerkDataManager.sync(player);
        player.displayClientMessage(Component.translatable("bloodbound.message.inevitable_placed")
                .withStyle(ChatFormatting.DARK_RED), true);
    }

    /**
     * The reserve, which is this perk's whole cooldown: it fills only while no circle is drawn, and
     * a circle costs all of it. While one stands, the number on screen is what the circle has left.
     */
    private static void refill(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        float max = maxCharges(tier);
        RitualManager.Ritual ritual = RitualManager.get(player.getUUID(), ModPerks.INEVITABLE_DEATH.id());
        float charges = data.perkCharges(ModPerks.INEVITABLE_DEATH.id(), max);

        if (ritual != null) {
            // Mirror what the circle has left, so the reserve cannot be spent twice — even while the
            // circle sits in an unloaded chunk, where it still stands.
            charges = ritual.charges();
            data.setPerkCharges(ModPerks.INEVITABLE_DEATH.id(), charges);
        } else if (charges < max) {
            double rate = ModPerks.INEVITABLE_DEATH.value(ModPerks.INEVITABLE_REGEN, tier)
                    * (1.0D + PerkEventHandler.lightbringerRate(player));
            charges = Math.min(max, charges + (float) (rate / 20.0D));
            data.setPerkCharges(ModPerks.INEVITABLE_DEATH.id(), charges);
        }

        // Every tick while it moves, once a second otherwise: it is a number the player watches.
        if (charges < max || ritual != null || gameTime % 20L == 0L) {
            PacketDistributor.sendToPlayer(player, new PerkChargesPayload(ModPerks.INEVITABLE_DEATH.id(),
                    Math.round(charges), Math.round(max), 0L));
        }
    }

    /** Works every ritual on the server. Called once a tick. */
    public static void tickRituals(MinecraftServer server, long gameTime) {
        // Every circle on the ground, its owner online or not: a trap does not wait to be watched.
        for (RitualManager.Ritual ritual : RitualManager.all(ModPerks.INEVITABLE_DEATH.id())) {
            if (!ritual.isAlive()) {
                continue;
            }
            ServerLevel level = server.getLevel(ritual.dimension());
            if (level == null) {
                continue;
            }

            // It is buried at first and works its way up: four blocks of warning, one more every
            // four seconds, up to a dozen.
            double seen = Math.min(ModPerks.INEVITABLE_REVEAL_MAX, ModPerks.INEVITABLE_REVEAL_START
                    + (gameTime - ritual.placedAt()) / (double) ModPerks.INEVITABLE_REVEAL_GROW_TICKS);
            double seenSq = seen * seen;

            for (ServerPlayer nearby : level.players()) {
                if (nearby.position().distanceToSqr(ritual.position()) <= seenSq) {
                    RitualManager.revealTo(nearby, ritual, ModPerks.INEVITABLE_REVEAL_TICKS);
                }
            }

            // Everything alive pays, not only players: a mob wandering in is held exactly the same
            // way, and costs the circle exactly as much.
            int trespassers = 0;
            for (LivingEntity nearby : ritual.occupants(level)) {
                if (nearby.getUUID().equals(ritual.ownerId())) {
                    continue;
                }
                trespassers++;
                EXPOSED.add(nearby.getUUID());
                nearby.addEffect(new MobEffectInstance(ModEffects.EXPOSED, ModPerks.INEVITABLE_EXPOSED_TICKS,
                        0, false, true, true));
            }
            // Anyone who has stepped back out is let go the moment their slice of Exposed runs down.
            releaseThoseOutside(level, ritual);

            if (trespassers > 0
                    && !ritual.spend(trespassers * ModPerks.INEVITABLE_DRAIN_PER_SECOND / 20.0F)) {
                // Out of charges: the circle goes, and lets go of everything it was holding.
                for (LivingEntity nearby : ritual.occupants(level)) {
                    release(nearby);
                }
                RitualManager.remove(ritual.ownerId(), ModPerks.INEVITABLE_DEATH.id());
                ServerPlayer owner = server.getPlayerList().getPlayer(ritual.ownerId());
                if (owner != null) {
                    owner.displayClientMessage(Component.translatable("bloodbound.message.inevitable_spent")
                            .withStyle(ChatFormatting.DARK_GRAY), true);
                }
            }
        }
    }

    /** Lifts the hold, but only from something this perk was holding. */
    private static void release(LivingEntity entity) {
        if (EXPOSED.remove(entity.getUUID())) {
            entity.removeEffect(ModEffects.EXPOSED);
        }
    }

    /** Frees whatever has walked out of the circle since the last tick. */
    private static void releaseThoseOutside(ServerLevel level, RitualManager.Ritual ritual) {
        if (EXPOSED.isEmpty()) {
            return;
        }
        for (LivingEntity held : level.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(ritual.position(), ritual.radius() * 4, ritual.radius() * 4,
                        ritual.radius() * 4))) {
            if (EXPOSED.contains(held.getUUID()) && !ritual.covers(held.position())) {
                release(held);
            }
        }
    }

    /** A logout: the laying in hand is dropped, the circle already down stays where it is. */
    public static void logout(ServerPlayer player) {
        RitualSetup.stop(player, ModPerks.INEVITABLE_DEATH.id(), false);
        EXPOSED.remove(player.getUUID());
    }

    public static void clear(ServerPlayer player) {
        RitualSetup.stop(player, ModPerks.INEVITABLE_DEATH.id(), false);
        EXPOSED.remove(player.getUUID());
        RitualManager.remove(player.getUUID(), ModPerks.INEVITABLE_DEATH.id());
    }
}
