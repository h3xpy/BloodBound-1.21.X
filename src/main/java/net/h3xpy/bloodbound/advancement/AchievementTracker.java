package net.h3xpy.bloodbound.advancement;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.perk.impl.TeamSpirit;
import net.h3xpy.bloodbound.registry.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * The feats that are not a single moment: surviving for a while after something, a kill that has to
 * follow something else, a count that has to build up without a death in between.
 * <p>
 * Nothing here is saved. A feat half done when the player logs out starts over, which is the fair
 * reading of "without dying" and "within ten seconds" alike.
 */
public final class AchievementTracker {

    /** Save Your Tears: how soon after being hurt the check has to land. */
    private static final int TEARS_WINDOW_TICKS = 60;
    /** Should Have Died Here: the health Close Call has to be used at, and the time to live after. */
    private static final float CLOSE_CALL_HEALTH = 2.0F;
    private static final int CLOSE_CALL_SURVIVE_TICKS = 600;
    /** W Support: how long after Keep Fighting the patient has to land the kill. */
    private static final int SUPPORT_WINDOW_TICKS = 200;
    /** Come On: the window two Adrenalines from the same attacker have to fall in. */
    private static final int ADRENALINE_WINDOW_TICKS = 3600;
    /** Heroes Never Die: Guardian Angel trials to come through in one life. */
    private static final int GUARDIAN_TRIALS = 2;
    /** Get Back Here: how long one trail has to be followed, and the gap it may go cold for. */
    private static final int CHASE_TICKS = 600;
    private static final int CHASE_GAP_TICKS = 100;

    private record Timed(UUID otherId, long at) {}

    private static final class Chase {
        private final UUID targetId;
        private final long since;
        private long lastSeen;

        private Chase(UUID targetId, long since) {
            this.targetId = targetId;
            this.since = since;
            this.lastSeen = since;
        }
    }

    /** When each player last took damage. */
    private static final Map<UUID, Long> LAST_HURT = new HashMap<>();
    /** Close Call used at death's door, and when the player may call it survived. */
    private static final Map<UUID, Long> CLOSE_CALL_UNTIL = new HashMap<>();
    /** Patient to the Keep Fighting user who saved them, and when. */
    private static final Map<UUID, Timed> SUPPORTED = new HashMap<>();
    /** Anything Crime And Punishment blinded, to whoever did it. */
    private static final Map<UUID, UUID> PUNISHED = new HashMap<>();
    /** Each player's last Adrenaline: who drove them to it, and when. */
    private static final Map<UUID, Timed> ADRENALINE = new HashMap<>();
    /** Guardian Angel trials come through since the last death. */
    private static final Map<UUID, Integer> TRIALS_SURVIVED = new HashMap<>();
    /** The trail each Catching Up player is on. */
    private static final Map<UUID, Chase> CHASES = new HashMap<>();

    private AchievementTracker() {}

    // --- being hurt ---

    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getNewDamage() > 0.0F) {
            LAST_HURT.put(player.getUUID(), player.level().getGameTime());
        }
    }

    /** Save Your Tears: a Surgical Suture check landed. */
    public static void onSutureLanded(ServerPlayer player) {
        Long hurt = LAST_HURT.get(player.getUUID());
        if (hurt != null && player.level().getGameTime() - hurt <= TEARS_WINDOW_TICKS) {
            ModAdvancements.grant(player, ModAdvancements.SAVE_YOUR_TEARS);
        }
    }

    // --- Close Call ---

    public static void onCloseCall(ServerPlayer player, float healthBefore) {
        if (healthBefore <= CLOSE_CALL_HEALTH) {
            CLOSE_CALL_UNTIL.put(player.getUUID(), player.level().getGameTime() + CLOSE_CALL_SURVIVE_TICKS);
        }
    }

    /** Called once a tick per player. */
    public static void tick(ServerPlayer player, long gameTime) {
        Long until = CLOSE_CALL_UNTIL.get(player.getUUID());
        if (until != null && gameTime >= until) {
            CLOSE_CALL_UNTIL.remove(player.getUUID());
            if (player.isAlive()) {
                ModAdvancements.grant(player, ModAdvancements.SHOULD_HAVE_DIED_HERE);
            }
        }
    }

    // --- Keep Fighting ---

    public static void onKeepFighting(ServerPlayer healer, ServerPlayer patient) {
        SUPPORTED.put(patient.getUUID(), new Timed(healer.getUUID(), patient.level().getGameTime()));
    }

    // --- Crime And Punishment ---

    public static void onPunished(ServerPlayer punisher, LivingEntity victim) {
        PUNISHED.put(victim.getUUID(), punisher.getUUID());
    }

    // --- Adrenaline ---

    /** @param attacker whatever last hurt the player, if anything did */
    public static void onAdrenaline(ServerPlayer player, @Nullable LivingEntity attacker) {
        if (attacker == null) {
            return;
        }
        long now = player.level().getGameTime();
        Timed last = ADRENALINE.put(player.getUUID(), new Timed(attacker.getUUID(), now));
        if (last != null && last.otherId().equals(attacker.getUUID()) && now - last.at() <= ADRENALINE_WINDOW_TICKS) {
            ModAdvancements.grant(player, ModAdvancements.COME_ON);
        }
    }

    // --- Guardian Angel ---

    public static void onGuardianSurvived(ServerPlayer player) {
        int survived = TRIALS_SURVIVED.merge(player.getUUID(), 1, Integer::sum);
        if (survived >= GUARDIAN_TRIALS) {
            ModAdvancements.grant(player, ModAdvancements.HEROES_NEVER_DIE);
        }
    }

    // --- Catching Up ---

    /** The player is standing on a trail somebody else left. */
    public static void onTrail(ServerPlayer player, UUID ownerId, long gameTime) {
        Chase chase = CHASES.get(player.getUUID());
        if (chase == null || !chase.targetId.equals(ownerId) || gameTime - chase.lastSeen > CHASE_GAP_TICKS) {
            CHASES.put(player.getUUID(), new Chase(ownerId, gameTime));
            return;
        }
        chase.lastSeen = gameTime;
        if (gameTime - chase.since >= CHASE_TICKS) {
            ModAdvancements.grant(player, ModAdvancements.GET_BACK_HERE);
        }
    }

    // --- kills and deaths ---

    /**
     * Ahead of everything else that listens for a death: the perks clear their own state up on it,
     * and some of what is asked about here — whether the angel's wings were out — goes with that.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide) {
            return;
        }
        long now = dead.level().getGameTime();

        if (event.getSource().getEntity() instanceof ServerPlayer killer && killer != dead) {
            onKill(killer, dead, now);
        }

        UUID punisher = PUNISHED.remove(dead.getUUID());
        if (punisher != null && event.getSource().getEntity() instanceof ServerPlayer killer
                && killer.getUUID().equals(punisher) && dead.hasEffect(ModEffects.FLASHED)) {
            ModAdvancements.grant(killer, ModAdvancements.INSTANT_KARMA);
        }

        if (dead instanceof ServerPlayer player) {
            if (event.getSource().is(DamageTypeTags.IS_FALL) && PerkDataManager.get(player).isAngelAirborne()) {
                ModAdvancements.grant(player, ModAdvancements.THE_ANGEL_HAS_FALLEN);
            }
            forgetLife(player.getUUID());
        }
    }

    private static void onKill(ServerPlayer killer, LivingEntity dead, long now) {
        Timed support = SUPPORTED.get(killer.getUUID());
        if (support != null && now - support.at() <= SUPPORT_WINDOW_TICKS) {
            SUPPORTED.remove(killer.getUUID());
            ServerPlayer healer = killer.getServer() == null ? null
                    : killer.getServer().getPlayerList().getPlayer(support.otherId());
            if (healer != null) {
                ModAdvancements.grant(healer, ModAdvancements.W_SUPPORT);
            }
        }
        TeamSpirit.onKill(killer);
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            TeamSpirit.onMined(player);
        }
    }

    /** What only counts within one life. */
    private static void forgetLife(UUID playerId) {
        CLOSE_CALL_UNTIL.remove(playerId);
        TRIALS_SURVIVED.remove(playerId);
        CHASES.remove(playerId);
        SUPPORTED.remove(playerId);
    }

    /** Whoever a stale entry names is gone for good; sweeps them up now and then. */
    public static void sweep(long gameTime) {
        SUPPORTED.values().removeIf(timed -> gameTime - timed.at() > SUPPORT_WINDOW_TICKS);
        ADRENALINE.values().removeIf(timed -> gameTime - timed.at() > ADRENALINE_WINDOW_TICKS);
        LAST_HURT.values().removeIf(at -> gameTime - at > TEARS_WINDOW_TICKS);
    }

    /** Flashed ran out on something Crime And Punishment blinded: the karma is no longer instant. */
    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        if (event.getEffectInstance() != null && event.getEffectInstance().is(ModEffects.FLASHED)) {
            PUNISHED.remove(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        if (event.getEffectInstance() != null && event.getEffectInstance().is(ModEffects.FLASHED)) {
            PUNISHED.remove(event.getEntity().getUUID());
        }
    }

    public static void clear(UUID playerId) {
        forgetLife(playerId);
        LAST_HURT.remove(playerId);
        ADRENALINE.remove(playerId);
        PUNISHED.values().removeIf(id -> id.equals(playerId));
        ADRENALINE.values().removeIf(timed -> timed.otherId().equals(playerId));
    }
}
