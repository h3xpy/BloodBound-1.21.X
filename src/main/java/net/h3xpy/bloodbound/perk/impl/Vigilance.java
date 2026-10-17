package net.h3xpy.bloodbound.perk.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.event.AuraRevealHandler;
import net.h3xpy.bloodbound.network.VigilancePayload;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Vigilance: being watched sharpens you. While another player has you dead in their sights, or a
 * hostile creature has its eyes on you — looking right at you, or hunting you in plain view — you
 * move faster, hit harder, shake off cooldowns and harmful effects faster, and dig faster. It all
 * outlasts the gaze by a second.
 * <p>
 * Speed is an attribute, which the client follows by itself; digging is worked out on the client
 * too, so the client is told when it switches on and off, which also lights the perk's icon.
 */
public final class Vigilance {

    private static final ResourceLocation SPEED_ID = ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "vigilance");

    /** Players being watched, and until when the bonuses hold. */
    private static final Map<UUID, Long> WATCHED_UNTIL = new HashMap<>();
    /** Players the bonuses are applied to right now, and the client has been told about. */
    private static final Map<UUID, Double> APPLIED = new HashMap<>();
    /** How many gazes were on each player at the last look, for Scary Note. */
    private static final Map<UUID, Integer> GAZES = new HashMap<>();
    /** Mirror Shard: when each player's shard is ready to give a watcher away again. */
    private static final Map<UUID, Long> MIRROR_READY_AT = new HashMap<>();

    /** The client's own copy of the bonus, for digging speed and the icon. Only read on the client side. */
    private static float clientBonus;

    private Vigilance() {}

    /** Whether the bonuses are on for this player, on the server. */
    public static boolean isActive(Player player) {
        Long until = WATCHED_UNTIL.get(player.getUUID());
        return until != null && player.level().getGameTime() < until;
    }

    /** The bonus, as a fraction, while it is on; 0 otherwise. */
    public static double bonus(ServerPlayer player, PlayerPerkData data) {
        return data.getActiveTier(ModPerks.VIGILANCE) > 0 ? APPLIED.getOrDefault(player.getUUID(), 0.0D) : 0.0D;
    }

    /** Looks for gazes now and then, and keeps the speed and the client in step. Called once a tick per player. */
    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        UUID id = player.getUUID();
        int tier = data.getActiveTier(ModPerks.VIGILANCE);
        // What was last applied, not worked out again from the clock: recomputed, "before" was
        // already off on the very tick it ran out, and the switch-off was never seen.
        double before = APPLIED.getOrDefault(id, 0.0D);
        List<LivingEntity> watchers = List.of();
        if (tier <= 0) {
            WATCHED_UNTIL.remove(id);
            GAZES.remove(id);
        } else if (gameTime % ModPerks.VIGILANCE_CHECK_TICKS == 0L) {
            watchers = watchers(player);
            if (!watchers.isEmpty()) {
                int linger = data.isAddonActive(ModAddons.CRACKED_MONOCLE)
                        ? ModAddons.CRACKED_MONOCLE_LINGER_TICKS : ModPerks.VIGILANCE_LINGER_TICKS;
                WATCHED_UNTIL.put(id, gameTime + ModPerks.VIGILANCE_CHECK_TICKS + linger);
                GAZES.put(id, watchers.size());
            }
        }
        boolean on = isActive(player);
        double now = 0.0D;
        if (on) {
            now = ModPerks.VIGILANCE.value(ModPerks.VIGILANCE_BONUS, tier) / 100.0D;
            // Scary Note: every gaze past the first is worth a little more.
            if (data.isAddonActive(ModAddons.SCARY_NOTE)) {
                now += ModAddons.SCARY_NOTE_PER_GAZE * Math.max(0, GAZES.getOrDefault(id, 1) - 1);
            }
        } else {
            GAZES.remove(id);
        }

        // Mirror Shard: switching on gives away whoever did it.
        if (before <= 0.0D && now > 0.0D && data.isAddonActive(ModAddons.MIRROR_SHARD)
                && gameTime >= MIRROR_READY_AT.getOrDefault(id, 0L)) {
            for (LivingEntity watcher : watchers) {
                AuraRevealHandler.reveal(player, watcher, ModAddons.MIRROR_SHARD_REVEAL_TICKS);
            }
            MIRROR_READY_AT.put(id, gameTime + ModAddons.MIRROR_SHARD_COOLDOWN_TICKS);
        }

        if (now != before || (now > 0.0D && !hasSpeed(player))) {
            applySpeed(player, now);
        }
        if (now != before) {
            if (now > 0.0D) {
                APPLIED.put(id, now);
            } else {
                APPLIED.remove(id);
            }
            PacketDistributor.sendToPlayer(player, new VigilancePayload((float) now));
        }
    }

    /** Every player or creature nearby with its eyes on this one. */
    private static List<LivingEntity> watchers(ServerPlayer player) {
        List<LivingEntity> found = new ArrayList<>();
        Vec3 target = player.getBoundingBox().getCenter();
        double playerRange = ModPerks.VIGILANCE_PLAYER_RANGE;
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other != player && !other.isSpectator() && other.isAlive()
                    && other.distanceToSqr(player) <= playerRange * playerRange
                    && looksAt(other, player, target)) {
                found.add(other);
            }
        }
        AABB area = player.getBoundingBox().inflate(ModPerks.VIGILANCE_MOB_RANGE);
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, area, Mob::isAlive)) {
            // Only creatures that mean harm count: animals and villagers turn their heads to any
            // player nearby, and counting that kept Vigilance on all the time.
            boolean hunting = mob.getTarget() == player && mob.hasLineOfSight(player);
            if (hunting || (mob instanceof Enemy && looksAt(mob, player, target))) {
                found.add(mob);
            }
        }
        return found;
    }

    /** A gaze straight at the target, with nothing in the way. */
    private static boolean looksAt(LivingEntity watcher, LivingEntity watched, Vec3 target) {
        Vec3 toTarget = target.subtract(watcher.getEyePosition());
        double distance = toTarget.length();
        if (distance < 1.0E-3D) {
            return false;
        }
        double alignment = watcher.getViewVector(1.0F).dot(toTarget.scale(1.0D / distance));
        return alignment >= ModPerks.VIGILANCE_GAZE_COSINE && watcher.hasLineOfSight(watched);
    }

    private static boolean hasSpeed(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        return speed != null && speed.hasModifier(SPEED_ID);
    }

    private static void applySpeed(ServerPlayer player, double bonus) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        if (bonus <= 0.0D) {
            speed.removeModifier(SPEED_ID);
        } else {
            speed.addOrUpdateTransientModifier(new AttributeModifier(SPEED_ID, bonus,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    // --- damage and digging ---

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            double bonus = bonus(attacker, PerkDataManager.get(attacker));
            if (bonus > 0.0D) {
                event.setAmount((float) (event.getAmount() * (1.0D + bonus)));
            }
        }
    }

    /** Digging is predicted on the client as well, so both sides have to agree on the speed. */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        double bonus = player.level().isClientSide()
                ? clientBonus
                : player instanceof ServerPlayer server ? bonus(server, PerkDataManager.get(server)) : 0.0D;
        if (bonus > 0.0D) {
            event.setNewSpeed(event.getNewSpeed() * (float) (1.0D + bonus));
        }
    }

    // --- the client's copy ---

    /** What the server said the bonus is, as a fraction; 0 while it is off. */
    public static void setClientBonus(float bonus) {
        clientBonus = bonus;
    }

    public static boolean isClientActive() {
        return clientBonus > 0.0F;
    }

    /** Takes the bonuses off a player who is leaving, dying or losing the perk. */
    public static void clear(ServerPlayer player) {
        WATCHED_UNTIL.remove(player.getUUID());
        applySpeed(player, 0.0D);
        // The client keeps its own copy and would keep the icon lit through a death otherwise.
        GAZES.remove(player.getUUID());
        if (APPLIED.remove(player.getUUID()) != null && player.connection != null) {
            PacketDistributor.sendToPlayer(player, new VigilancePayload(0.0F));
        }
    }
}
