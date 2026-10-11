package net.h3xpy.bloodbound.perk.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.network.VigilancePayload;
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
    private static final Set<UUID> APPLIED = new HashSet<>();

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
        int tier = data.getActiveTier(ModPerks.VIGILANCE);
        if (tier <= 0 || !isActive(player)) {
            return 0.0D;
        }
        return ModPerks.VIGILANCE.value(ModPerks.VIGILANCE_BONUS, tier) / 100.0D;
    }

    /** Looks for gazes now and then, and keeps the speed and the client in step. Called once a tick per player. */
    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        int tier = data.getActiveTier(ModPerks.VIGILANCE);
        // What was last applied, not worked out again from the clock: recomputed, "before" was
        // already off on the very tick it ran out, and the switch-off was never seen.
        boolean before = APPLIED.contains(player.getUUID());
        if (tier <= 0) {
            WATCHED_UNTIL.remove(player.getUUID());
        } else if (gameTime % ModPerks.VIGILANCE_CHECK_TICKS == 0L && isWatched(player)) {
            WATCHED_UNTIL.put(player.getUUID(), gameTime + ModPerks.VIGILANCE_CHECK_TICKS
                    + ModPerks.VIGILANCE_LINGER_TICKS);
        }
        boolean now = isActive(player);
        if (now != before || (now && !hasSpeed(player))) {
            applySpeed(player, now ? ModPerks.VIGILANCE.value(ModPerks.VIGILANCE_BONUS, tier) / 100.0D : 0.0D);
        }
        if (now != before) {
            if (now) {
                APPLIED.add(player.getUUID());
            } else {
                APPLIED.remove(player.getUUID());
            }
            PacketDistributor.sendToPlayer(player, new VigilancePayload(
                    now ? (float) (ModPerks.VIGILANCE.value(ModPerks.VIGILANCE_BONUS, tier) / 100.0D) : 0.0F));
        }
    }

    /** Whether some player or creature nearby has its eyes on this one. */
    private static boolean isWatched(ServerPlayer player) {
        Vec3 target = player.getBoundingBox().getCenter();
        double playerRange = ModPerks.VIGILANCE_PLAYER_RANGE;
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other != player && !other.isSpectator() && other.isAlive()
                    && other.distanceToSqr(player) <= playerRange * playerRange
                    && looksAt(other, player, target)) {
                return true;
            }
        }
        AABB area = player.getBoundingBox().inflate(ModPerks.VIGILANCE_MOB_RANGE);
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, area, Mob::isAlive)) {
            // Only creatures that mean harm count: animals and villagers turn their heads to any
            // player nearby, and counting that kept Vigilance on all the time.
            boolean hunting = mob.getTarget() == player && mob.hasLineOfSight(player);
            if (hunting || (mob instanceof Enemy && looksAt(mob, player, target))) {
                return true;
            }
        }
        return false;
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
        if (APPLIED.remove(player.getUUID()) && player.connection != null) {
            PacketDistributor.sendToPlayer(player, new VigilancePayload(0.0F));
        }
    }
}
