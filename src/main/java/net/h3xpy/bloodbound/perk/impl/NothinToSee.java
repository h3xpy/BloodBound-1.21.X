package net.h3xpy.bloodbound.perk.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.h3xpy.bloodbound.damage.PerkDamageSource;
import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.effect.EffectDurations;
import net.h3xpy.bloodbound.event.AuraRevealHandler;
import net.h3xpy.bloodbound.network.NothinToSeePayload;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.perk.PerkGlow;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.EffectParticleModificationEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Nothin' to See: the key makes the bearer wholly invisible for a while.
 * <p>
 * Vanilla invisibility still shows armour, the held item, effect particles and the dust of running
 * and landing, and mobs still pick out an invisible player who comes close. Here nothing shows:
 * every client is told who is hidden and skips drawing them, the particles are cut at the source
 * (see {@code EntityMixin}, {@code LivingEntityMixin}), and no mob may take the bearer as a target.
 * The vanilla effect is still applied: it is what hides the shadow and the name, and what the
 * player sees in their effect list.
 * <p>
 * The cooldown only starts once the bearer shows again. Pressing the key early ends it and takes
 * the time left off the cooldown, second for second.
 * <p>
 * A hidden player cannot hurt anything: their blows, shots and perks are cancelled before anything
 * else hears of them. With Invisi-Bill each cancelled blow is kept instead, buys more time, and the
 * whole lot comes back on the bearer when they show again.
 */
public final class NothinToSee {

    /**
     * One hidden player: when it ends, at which tier, the longest it may run (Invisi-Bill's cap),
     * and the damage Invisi-Bill has kept back.
     */
    private static final class Cloak {
        private long endsAt;
        private final int tier;
        private final int maxTicks;
        private float storedDamage;
        /** When it began: mobs only lose track of the bearer once it has held a while. */
        private long startedAt;

        private Cloak(long endsAt, int tier, int maxTicks) {
            this.endsAt = endsAt;
            this.tier = tier;
            this.maxTicks = maxTicks;
        }
    }

    /** Leeway when telling the perk's own invisibility from a potion the player drank on top of it. */
    private static final int OWN_EFFECT_SLACK = 5;
    /** How often mobs round a hidden player are made to forget them again, in ticks. */
    private static final int FORGET_INTERVAL = 10;
    /** An Invisi-Bill still owed by a player who logged out with it, saved on the player. */
    private static final String KEY_UNPAID_BILL = "BloodBoundUnpaidInvisiBill";
    /** Vanilla leaves a player who has just joined unhurtable for this long (60 ticks), plus one. */
    private static final int JOIN_GRACE_TICKS = 61;

    /** Server: who is hidden right now. */
    private static final Map<UUID, Cloak> HIDDEN = new HashMap<>();
    /** Client: the entity ids this client has been told are hidden. Only ever touched on the client thread. */
    private static final Set<Integer> CLIENT_HIDDEN = new HashSet<>();

    private NothinToSee() {}

    /** Whether this entity is hidden by the perk, on whichever side it lives. */
    /**
     * Whether mobs have lost track of this player: hidden, and for long enough. Until then a mob
     * already on them keeps coming, and one that spots them can still pick them out.
     */
    private static boolean concealedFromMobs(ServerPlayer player) {
        Cloak cloak = HIDDEN.get(player.getUUID());
        return cloak != null && player.level().getGameTime() - cloak.startedAt >= ModPerks.NOTHIN_FORGET_DELAY_TICKS;
    }

    public static boolean isHidden(Entity entity) {
        return entity.level().isClientSide ? CLIENT_HIDDEN.contains(entity.getId()) : HIDDEN.containsKey(entity.getUUID());
    }

    // --- client side bookkeeping, fed by the payload ---

    public static void setClientHidden(int entityId, boolean hidden) {
        if (hidden) {
            CLIENT_HIDDEN.add(entityId);
        } else {
            CLIENT_HIDDEN.remove(entityId);
        }
    }

    public static boolean isClientHidden(int entityId) {
        return CLIENT_HIDDEN.contains(entityId);
    }

    public static void resetClient() {
        CLIENT_HIDDEN.clear();
    }

    // --- the key ---

    /** Goes invisible, or, already invisible, shows again early. */
    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        if (HIDDEN.containsKey(player.getUUID())) {
            end(player, data, gameTime, true);
            return true;
        }

        int ticks = durationTicks(data, tier);
        // Recorded before the effect goes on: adding it recomputes the effect particles, and the
        // particle event has to already see the player as hidden to cut them.
        Cloak cloak = new Cloak(gameTime + ticks, tier, ticks);
        cloak.startedAt = gameTime;
        HIDDEN.put(player.getUUID(), cloak);
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false, true));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new NothinToSeePayload(player.getId(), true, gameTime + ticks));
        PerkGlow.set(player, ModPerks.NOTHIN_TO_SEE, true);
        // Heard by the bearer alone: a sound in the world would give them away.
        player.playNotifySound(SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8F, 1.2F);
        return true;
    }

    /** How long one go lasts: the perk's own time, longer with Firefly. */
    private static int durationTicks(PlayerPerkData data, int tier) {
        int ticks = ModPerks.NOTHIN_TO_SEE.ticks(ModPerks.NOTHIN_SECONDS, tier);
        if (data.isAddonActive(ModAddons.FIREFLY)) {
            ticks = (int) Math.round(ticks * ModAddons.FIREFLY_DURATION);
        }
        return ticks;
    }

    /** Keeps a hidden player hidden, and ends it when the time is up. Called once a tick. */
    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        collectUnpaidBill(player);
        Cloak cloak = HIDDEN.get(player.getUUID());
        if (cloak == null) {
            return;
        }
        if (gameTime >= cloak.endsAt || data.getActiveTier(ModPerks.NOTHIN_TO_SEE) <= 0) {
            end(player, data, gameTime, false);
            return;
        }
        // Milk, or anything else that clears effects, does not get to end it early.
        if (!player.hasEffect(MobEffects.INVISIBILITY)) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, (int) (cloak.endsAt - gameTime),
                    0, false, false, true));
        }
        if (gameTime % FORGET_INTERVAL == 0L && gameTime - cloak.startedAt >= ModPerks.NOTHIN_FORGET_DELAY_TICKS) {
            forget(player);
        }
    }

    /**
     * Shows the player again and starts the cooldown.
     *
     * @param early the key ended it before its time: the cooldown is cut by the time left, and
     *              Soul In A Jar goes off
     */
    private static void end(ServerPlayer player, PlayerPerkData data, long gameTime, boolean early) {
        Cloak cloak = HIDDEN.remove(player.getUUID());
        if (cloak == null) {
            return;
        }
        long left = Math.max(0L, cloak.endsAt - gameTime);
        removeOwnEffect(player, left);

        int cooldown = ModPerks.NOTHIN_TO_SEE.cooldownTicks(cloak.tier);
        if (early) {
            cooldown = (int) Math.max(0L, cooldown - left * ModPerks.NOTHIN_EARLY_REFUND);
        }
        data.setCooldown(ModPerks.NOTHIN_TO_SEE.id(), gameTime, cooldown);

        if (early && data.isAddonActive(ModAddons.SOUL_IN_A_JAR)) {
            soulInAJar(player);
        }
        if (data.isAddonActive(ModAddons.FIREFLY)) {
            AuraRevealHandler.revealToAll(player, ModAddons.FIREFLY_REVEAL_TICKS);
        }
        if (cloak.storedDamage > 0.0F) {
            payBill(player, cloak.storedDamage);
        }

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new NothinToSeePayload(player.getId(), false, 0L));
        PerkGlow.set(player, ModPerks.NOTHIN_TO_SEE, false);
        player.playNotifySound(SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8F, 0.7F);
        PerkDataManager.sync(player);
    }

    /**
     * Takes the perk's invisibility off, but not a longer one from a potion drunk on top of it: the
     * game merges the two into one effect, and one lasting well past the perk is the potion's.
     */
    private static void removeOwnEffect(ServerPlayer player, long left) {
        MobEffectInstance invisibility = player.getEffect(MobEffects.INVISIBILITY);
        if (invisibility != null && !invisibility.isInfiniteDuration()
                && invisibility.getDuration() <= left + OWN_EFFECT_SLACK) {
            player.removeEffect(MobEffects.INVISIBILITY);
        }
    }

    /**
     * Invisi-Bill: every blow held back while hidden lands on the bearer at once, through armour.
     * Wrapped so it reads as the addon's doing in the death message.
     */
    private static void payBill(ServerPlayer player, float damage) {
        player.invulnerableTime = 0;
        player.hurt(PerkDamageSource.of(player.damageSources().magic(), "invisi_bill"), damage);
    }

    /** Soul In A Jar: a blast round the bearer that throws everything back, lit TNT too, without a scratch. */
    private static void soulInAJar(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        double radius = ModAddons.SOUL_IN_A_JAR_RADIUS;
        AABB box = player.getBoundingBox().inflate(radius);
        for (Entity nearby : level.getEntitiesOfClass(Entity.class, box,
                entity -> entity instanceof LivingEntity || entity instanceof PrimedTnt || entity instanceof MinecartTNT)) {
            if (nearby == player || !nearby.isAlive() || nearby.isSpectator()) {
                continue;
            }
            Vec3 away = nearby.position().subtract(player.position());
            if (away.length() > radius) {
                continue;
            }
            if (away.horizontalDistanceSqr() < 1.0E-4D) {
                away = new Vec3(0.0D, 0.0D, 1.0D);
            }
            Vec3 push = new Vec3(away.x, 0.0D, away.z).normalize().scale(ModAddons.SOUL_IN_A_JAR_PUSH);
            // Thrown, not hurt: whatever the landing costs them is theirs to pay.
            nearby.push(push.x, ModAddons.SOUL_IN_A_JAR_LIFT, push.z);
            nearby.hurtMarked = true;
        }
        level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 1.0D, player.getZ(),
                3, 0.6D, 0.4D, 0.6D, 0.0D);
        level.sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 1.0D, player.getZ(),
                30, 0.8D, 0.6D, 0.8D, 0.1D);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS,
                0.6F, 1.5F);
    }

    /** Every mob round the player lets go of them: its target, its brain's target, its grudge. */
    private static void forget(ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(ModPerks.NOTHIN_FORGET_RADIUS);
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, box)) {
            if (mob.getTarget() == player) {
                mob.setTarget(null);
            }
            Brain<?> brain = mob.getBrain();
            if (brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT)
                    && brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == player) {
                brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }
            if (brain.checkMemory(MemoryModuleType.ANGRY_AT, MemoryStatus.VALUE_PRESENT)
                    && player.getUUID().equals(brain.getMemory(MemoryModuleType.ANGRY_AT).orElse(null))) {
                brain.eraseMemory(MemoryModuleType.ANGRY_AT);
            }
            if (mob instanceof NeutralMob neutral && player.getUUID().equals(neutral.getPersistentAngerTarget())) {
                neutral.stopBeingAngry();
            }
        }
    }

    // --- events ---

    /** No mob may take a hidden player as its target, whatever made it want to. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity wanted = event.getNewAboutToBeSetTarget();
        if (wanted instanceof ServerPlayer player && concealedFromMobs(player)) {
            event.setCanceled(true);
        }
    }

    /** Nothing sees a hidden player, at any distance. */
    @SubscribeEvent
    public static void onVisibility(LivingEvent.LivingVisibilityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && concealedFromMobs(player)) {
            event.modifyVisibility(0.0D);
        }
    }

    /** Not a single effect particle off a hidden player, whichever effects they carry. */
    @SubscribeEvent
    public static void onEffectParticles(EffectParticleModificationEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && HIDDEN.containsKey(player.getUUID())) {
            event.setVisible(false);
        }
    }

    /**
     * A hidden player hurts nothing: whatever they hit, shoot or set off is cancelled here, first,
     * so no other perk hears of it either. Invisi-Bill keeps each blow instead — what it would have
     * dealt, armour aside — and every point of it buys half a second more, up to the full length.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || event.getEntity() == player) {
            return;
        }
        Cloak cloak = HIDDEN.get(player.getUUID());
        if (cloak == null) {
            return;
        }
        event.setCanceled(true);
        float amount = event.getAmount();
        if (amount <= 0.0F || !PerkDataManager.get(player).isAddonActive(ModAddons.INVISI_BILL)) {
            return;
        }

        cloak.storedDamage += amount;
        long now = player.level().getGameTime();
        long bought = Math.round(amount * ModAddons.INVISI_BILL_TICKS_PER_DAMAGE);
        cloak.endsAt = Math.min(cloak.endsAt + bought, now + cloak.maxTicks);
        MobEffectInstance invisibility = player.getEffect(MobEffects.INVISIBILITY);
        if (invisibility != null && invisibility.getDuration() < cloak.endsAt - now) {
            EffectDurations.set(player, invisibility, (int) (cloak.endsAt - now));
        }
        // The countdown on the slot runs off the end time it was sent.
        PacketDistributor.sendToPlayer(player, new NothinToSeePayload(player.getId(), true, cloak.endsAt));
    }

    /** Somebody coming within sight of a hidden player is told, or they would see them. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer hidden && event.getEntity() instanceof ServerPlayer watcher) {
            Cloak cloak = HIDDEN.get(hidden.getUUID());
            if (cloak != null) {
                PacketDistributor.sendToPlayer(watcher, new NothinToSeePayload(hidden.getId(), true, cloak.endsAt));
            }
        }
    }

    /** Their own client forgets them as they leave the old world through a portal: told again. */
    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Cloak cloak = HIDDEN.get(player.getUUID());
            if (cloak != null) {
                PacketDistributor.sendToPlayer(player, new NothinToSeePayload(player.getId(), true, cloak.endsAt));
            }
        }
    }

    // --- leaving ---

    /**
     * Logout: logging out is no way out of an Invisi-Bill. What it held back is written onto the
     * player, which is saved with them, and lands once they are back ({@link #collectUnpaidBill}).
     */
    public static void logout(ServerPlayer player) {
        Cloak cloak = HIDDEN.get(player.getUUID());
        if (cloak != null && cloak.storedDamage > 0.0F) {
            CompoundTag tag = player.getPersistentData();
            tag.putFloat(KEY_UNPAID_BILL, tag.getFloat(KEY_UNPAID_BILL) + cloak.storedDamage);
        }
        release(player);
    }

    /**
     * A bill left by logging out lands once the player is back in, after the few seconds a player
     * who has just joined cannot be hurt in (which would have swallowed it).
     */
    private static void collectUnpaidBill(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData();
        if (!tag.contains(KEY_UNPAID_BILL) || player.tickCount <= JOIN_GRACE_TICKS || !player.isAlive()) {
            return;
        }
        float bill = tag.getFloat(KEY_UNPAID_BILL);
        tag.remove(KEY_UNPAID_BILL);
        if (bill > 0.0F) {
            payBill(player, bill);
        }
    }

    /**
     * Logout or death: shows the player again, on the full cooldown (so logging out is no way round
     * it) and with no addon going off. A death's cooldown is wiped on respawn like any other. On a
     * death, a bill Invisi-Bill was holding is dropped: there is nobody left to pay it.
     */
    public static void release(ServerPlayer player) {
        Cloak cloak = HIDDEN.remove(player.getUUID());
        if (cloak == null) {
            return;
        }
        long gameTime = player.level().getGameTime();
        removeOwnEffect(player, Math.max(0L, cloak.endsAt - gameTime));
        PerkDataManager.get(player).setCooldown(ModPerks.NOTHIN_TO_SEE.id(), gameTime,
                ModPerks.NOTHIN_TO_SEE.cooldownTicks(cloak.tier));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new NothinToSeePayload(player.getId(), false, 0L));
        PerkGlow.set(player, ModPerks.NOTHIN_TO_SEE, false);
    }
}
