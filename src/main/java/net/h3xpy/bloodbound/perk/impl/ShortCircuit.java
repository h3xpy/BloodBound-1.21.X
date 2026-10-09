package net.h3xpy.bloodbound.perk.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.h3xpy.bloodbound.damage.ModDamageTypes;
import net.h3xpy.bloodbound.damage.PerkDamageSource;
import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.network.CameraShakePayload;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

/**
 * Short Circuit: hold the key through a few seconds of being slowed and lit up, and let go a
 * cylinder of energy that runs forward through walls.
 * <p>
 * The cylinder grows from the player's eyes along where they were looking when it went off, holds
 * for a second at full length and fades. Anything inside it is hit for a single point at a time, but
 * often — so often that each hit clears the invulnerability window the one before opened, or the
 * rate would be vanilla's rather than the perk's.
 */
public final class ShortCircuit {

    private static final Vector3f YELLOW = new Vector3f(1.0F, 1.0F, 0.0F);
    private static final Vector3f CYAN = new Vector3f(0.0F, 1.0F, 1.0F);
    private static final DustColorTransitionOptions SPARK = new DustColorTransitionOptions(YELLOW, CYAN, 1.2F);

    /** How long each slice of the charging Slowness lasts; it is topped up while the key stays down. */
    private static final int SLOW_TICKS = 10;
    /** Particles spent on the cylinder per block of its length each tick, and the most in one tick. */
    private static final double PARTICLES_PER_BLOCK = 0.8D;
    private static final int MAX_PARTICLES = 60;
    /** How hard the camera shakes, in degrees, and for how long: the shooter as it goes off, anyone caught while inside. */
    private static final float SHOOTER_SHAKE = 2.5F;
    private static final int SHOOTER_SHAKE_TICKS = 20;
    private static final float VICTIM_SHAKE = 1.5F;
    private static final int VICTIM_SHAKE_TICKS = 8;

    /** One cylinder, from the moment it goes off to the moment it fades. */
    private static final class Beam {
        private final UUID ownerId;
        private final ResourceKey<Level> dimension;
        /**
         * The cylinder's axis, from the eyes to the tip. Straight, it is two points; with Red LED the
         * tip bends after the crosshair as it grows, and every tick's growth adds a point.
         */
        private final List<Vec3> axis = new ArrayList<>();
        /** Which way the tip is growing. */
        private Vec3 direction;
        private final boolean steered;
        private final double radius;
        private final double growthPerTick;
        private final double maxLength;
        private final double interval;
        private double length;
        private int lingerLeft = ModPerks.CIRCUIT_LINGER_TICKS;
        /** When each entity inside is next due a hit, in game ticks with the fraction kept. */
        private final Map<Integer, Double> nextHit = new HashMap<>();

        private Beam(ServerPlayer owner, int tier) {
            this.ownerId = owner.getUUID();
            this.dimension = owner.level().dimension();
            this.axis.add(owner.getEyePosition());
            this.axis.add(owner.getEyePosition());
            this.direction = owner.getLookAngle().normalize();
            this.steered = PerkDataManager.get(owner).isAddonActive(ModAddons.RED_LED);
            this.radius = ModPerks.SHORT_CIRCUIT.value(ModPerks.CIRCUIT_RADIUS, tier);
            this.growthPerTick = ModPerks.SHORT_CIRCUIT.value(ModPerks.CIRCUIT_GROWTH, tier) / 20.0D;
            this.maxLength = ModPerks.SHORT_CIRCUIT.value(ModPerks.CIRCUIT_LENGTH, tier);
            this.interval = ModPerks.SHORT_CIRCUIT.value(ModPerks.CIRCUIT_INTERVAL, tier) * 20.0D;
        }
    }

    /** Players charging, and the tick they started. */
    private static final Map<UUID, Long> CHARGING = new HashMap<>();

    /** Capacitor: half a broken charge, and the tick it runs out. */
    private record Stored(long ticks, long until) {}

    private static final Map<UUID, Stored> STORED = new HashMap<>();
    private static final List<Beam> ACTIVE = new ArrayList<>();

    private ShortCircuit() {}

    /** The press alone does nothing: the charge is the hold. */
    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        return false;
    }

    public static void setHolding(ServerPlayer player, boolean holding, long gameTime) {
        PlayerPerkData data = PerkDataManager.get(player);
        if (!holding || data.getActiveTier(ModPerks.SHORT_CIRCUIT) <= 0) {
            interrupt(player, holding ? null : "bloodbound.message.short_circuit_stopped");
            return;
        }
        if (!data.isOnCooldown(ModPerks.SHORT_CIRCUIT.id(), gameTime) && !CHARGING.containsKey(player.getUUID())) {
            // Capacitor: a charge broken off not long ago picks up where it left half of itself.
            long kept = 0L;
            Stored stored = STORED.remove(player.getUUID());
            if (stored != null && gameTime < stored.until && data.isAddonActive(ModAddons.CAPACITOR)) {
                kept = stored.ticks;
            }
            CHARGING.put(player.getUUID(), gameTime - kept);
        }
    }

    /** How long a full charge takes, Copper Coil included. */
    private static int chargeTicks(PlayerPerkData data, int tier) {
        int ticks = ModPerks.SHORT_CIRCUIT.ticks(ModPerks.CIRCUIT_CHARGE, tier);
        if (data.isAddonActive(ModAddons.COPPER_COIL)) {
            ticks = Math.round(ticks * ModAddons.COPPER_COIL_CHARGE);
        }
        return ticks;
    }

    /** A blow landing on a player who is charging breaks the charge. */
    public static void onHurt(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            interrupt(player, "bloodbound.message.short_circuit_interrupted");
        }
    }

    private static void interrupt(ServerPlayer player, String messageKey) {
        Long since = CHARGING.remove(player.getUUID());
        if (since == null) {
            return;
        }
        clearSlow(player);
        PlayerPerkData data = PerkDataManager.get(player);
        if (messageKey != null && data.isAddonActive(ModAddons.CAPACITOR)) {
            long now = player.level().getGameTime();
            STORED.put(player.getUUID(), new Stored(Math.round((now - since) * ModAddons.CAPACITOR_KEPT),
                    now + ModAddons.CAPACITOR_TICKS));
        }
        if (messageKey != null) {
            player.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.DARK_GRAY), true);
        }
    }

    /** Runs the charge, and lets it go once it is full. Called once a tick per player. */
    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        Long since = CHARGING.get(player.getUUID());
        if (since == null) {
            return;
        }
        int tier = data.getActiveTier(ModPerks.SHORT_CIRCUIT);
        if (tier <= 0 || !player.isAlive()) {
            interrupt(player, null);
            return;
        }

        int needed = chargeTicks(data, tier);
        long held = gameTime - since;

        MobEffectInstance slow = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (slow == null || slow.getDuration() < SLOW_TICKS / 2) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, 0, false, false, true));
        }
        player.serverLevel().sendParticles(SPARK, player.getX(), player.getY() + 1.0D, player.getZ(),
                4, 0.45D, 0.6D, 0.45D, 0.0D);
        if (held % 5L == 0L) {
            player.displayClientMessage(Component.translatable("bloodbound.message.short_circuit_charging",
                    String.format("%.1f", Math.max(0L, needed - held) / 20.0D)).withStyle(ChatFormatting.YELLOW), true);
        }
        if (held < needed) {
            return;
        }

        CHARGING.remove(player.getUUID());
        clearSlow(player);
        ACTIVE.add(new Beam(player, tier));
        data.setCooldown(ModPerks.SHORT_CIRCUIT.id(), gameTime, ModPerks.SHORT_CIRCUIT.cooldownTicks(tier));
        PerkDataManager.sync(player);
        PacketDistributor.sendToPlayer(player, new CameraShakePayload(SHOOTER_SHAKE, SHOOTER_SHAKE_TICKS));
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT,
                SoundSource.PLAYERS, 1.0F, 1.6F);
        player.level().playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT,
                SoundSource.PLAYERS, 0.5F, 1.8F);
    }

    /** Only the short slices the charge handed out come off; a potion of Slowness is not ours. */
    private static void clearSlow(ServerPlayer player) {
        MobEffectInstance slow = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (slow != null && slow.getAmplifier() == 0 && slow.getDuration() <= SLOW_TICKS) {
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
    }

    // --- the cylinder ---

    /** Grows, holds and fades every cylinder on the server. Called once a tick. */
    public static void tickBeams(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Beam> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            Beam beam = iterator.next();
            ServerLevel level = server.getLevel(beam.dimension);
            if (level == null || !advance(level, beam)) {
                iterator.remove();
            }
        }
    }

    /** @return false once the cylinder has faded */
    private static boolean advance(ServerLevel level, Beam beam) {
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(beam.ownerId);
        if (beam.length < beam.maxLength) {
            grow(beam, owner, Math.min(beam.growthPerTick, beam.maxLength - beam.length));
        } else if (--beam.lingerLeft < 0) {
            return false;
        }

        Vec3 end = beam.axis.get(beam.axis.size() - 1);
        long now = level.getGameTime();
        DamageSource base = level.damageSources().source(ModDamageTypes.SHORT_CIRCUIT, owner, owner);
        DamageSource source = PerkDamageSource.of(base, "short_circuit");

        AABB box = new AABB(beam.axis.get(0), end);
        for (Vec3 point : beam.axis) {
            box = box.minmax(new AABB(point, point));
        }
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box.inflate(beam.radius))) {
            if (!victim.isAlive() || victim.isSpectator() || victim.getUUID().equals(beam.ownerId)
                    || !inside(beam, victim.getBoundingBox().getCenter())) {
                continue;
            }
            double due = beam.nextHit.getOrDefault(victim.getId(), (double) now);
            if (now < due) {
                continue;
            }
            // Kept fractional so 0.13 seconds really is 2.6 ticks on average, not three.
            beam.nextHit.put(victim.getId(), Math.max(due + beam.interval, now + beam.interval - 1.0D));
            victim.invulnerableTime = 0;
            victim.hurt(source, ModPerks.CIRCUIT_DAMAGE);
            if (victim instanceof ServerPlayer shaken) {
                PacketDistributor.sendToPlayer(shaken, new CameraShakePayload(VICTIM_SHAKE, VICTIM_SHAKE_TICKS));
            }
        }

        sparks(level, beam);
        if (now % 6L == 0L) {
            level.playSound(null, end.x, end.y, end.z, SoundEvents.BEEHIVE_WORK, SoundSource.PLAYERS, 0.6F, 2.0F);
        }
        return true;
    }

    /**
     * Pushes the tip forward. Straight, the tip just moves on; with Red LED it first turns part of
     * the way towards where its owner is now looking, and leaves a bend behind it.
     */
    private static void grow(Beam beam, ServerPlayer owner, double step) {
        Vec3 tip = beam.axis.get(beam.axis.size() - 1);
        if (beam.steered && owner != null && owner.level().dimension() == beam.dimension) {
            Vec3 wanted = owner.getLookAngle().normalize();
            Vec3 turned = beam.direction.add(wanted.subtract(beam.direction).scale(ModAddons.RED_LED_STEER));
            if (turned.lengthSqr() > 1.0E-6D) {
                beam.direction = turned.normalize();
            }
            beam.axis.add(tip.add(beam.direction.scale(step)));
        } else {
            beam.axis.set(beam.axis.size() - 1, tip.add(beam.direction.scale(step)));
        }
        beam.length += step;
    }

    /** Whether a point sits inside the cylinder: within its radius of some stretch of the axis. */
    private static boolean inside(Beam beam, Vec3 point) {
        double radiusSqr = beam.radius * beam.radius;
        for (int i = 1; i < beam.axis.size(); i++) {
            Vec3 from = beam.axis.get(i - 1);
            Vec3 segment = beam.axis.get(i).subtract(from);
            double lengthSqr = segment.lengthSqr();
            Vec3 offset = point.subtract(from);
            if (lengthSqr < 1.0E-8D) {
                continue;
            }
            double along = offset.dot(segment) / lengthSqr;
            if (along >= 0.0D && along <= 1.0D && offset.subtract(segment.scale(along)).lengthSqr() <= radiusSqr) {
                return true;
            }
        }
        return false;
    }

    /** The same yellow-to-cyan sparks as the charge, scattered through the cylinder at random. */
    private static void sparks(ServerLevel level, Beam beam) {
        RandomSource random = level.getRandom();
        int count = Math.min(MAX_PARTICLES, (int) Math.ceil(beam.length * PARTICLES_PER_BLOCK));
        for (int i = 0; i < count; i++) {
            // A random distance along the axis, then the stretch of axis it falls on.
            double along = random.nextDouble() * beam.length;
            Vec3 from = beam.axis.get(0);
            Vec3 direction = beam.direction;
            for (int s = 1; s < beam.axis.size(); s++) {
                Vec3 segment = beam.axis.get(s).subtract(beam.axis.get(s - 1));
                double length = segment.length();
                from = beam.axis.get(s - 1);
                if (length > 1.0E-6D) {
                    direction = segment.scale(1.0D / length);
                }
                if (along <= length || s == beam.axis.size() - 1) {
                    break;
                }
                along -= length;
            }
            Vec3 side = Math.abs(direction.y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
            Vec3 u = direction.cross(side).normalize();
            Vec3 v = direction.cross(u).normalize();
            double angle = random.nextDouble() * Math.PI * 2.0D;
            // Square-rooted so the sparks fill the disc evenly instead of bunching at the axis.
            double out = Math.sqrt(random.nextDouble()) * beam.radius;
            Vec3 point = from.add(direction.scale(along))
                    .add(u.scale(Math.cos(angle) * out)).add(v.scale(Math.sin(angle) * out));
            level.sendParticles(SPARK, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /** Forgets a player's charge, any half of one Capacitor kept, and any cylinder of theirs still standing. */
    public static void clear(ServerPlayer player) {
        interrupt(player, null);
        STORED.remove(player.getUUID());
        ACTIVE.removeIf(beam -> beam.ownerId.equals(player.getUUID()));
    }
}
