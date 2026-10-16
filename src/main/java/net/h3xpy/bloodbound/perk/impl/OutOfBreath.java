package net.h3xpy.bloodbound.perk.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.h3xpy.bloodbound.advancement.ModAdvancements;
import net.h3xpy.bloodbound.damage.PerkDamageSource;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.event.BankShotHandler;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.registry.ModEffects;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3f;

/**
 * Out Of Breath: a charge that will not go off until it finds the floor.
 * <p>
 * Walls only turn it. That is the whole trick of throwing one: bounce it round a corner, down a
 * stair, into a room, and it goes off where the ground is rather than where the wall was. Anything
 * caught in the blast finds out how little air it has left.
 */
public final class OutOfBreath {

    /** One charge in the air. */
    private static final class Shot {
        private final UUID ownerId;
        private final ResourceKey<Level> dimension;
        private final int tier;
        private Vec3 position;
        private Vec3 velocity;
        private int ticksLeft = ModPerks.BREATH_MAX_FLIGHT_TICKS;
        /** Walls it has come off, for That's Sick. */
        private int bounces;
        /** The thrower's addons, as they were at the throw. */
        private final boolean rubberSheet;
        private final boolean pepperDust;
        private final boolean nailPolish;
        private final boolean sprayCan;

        private Shot(ServerPlayer owner, PlayerPerkData data, int tier, Vec3 position, Vec3 velocity) {
            this.rubberSheet = data.isAddonActive(ModAddons.RUBBER_SHEET);
            this.pepperDust = data.isAddonActive(ModAddons.PEPPER_DUST);
            this.nailPolish = data.isAddonActive(ModAddons.NAIL_POLISH);
            this.sprayCan = data.isAddonActive(ModAddons.SPRAY_CAN);
            this.ownerId = owner.getUUID();
            this.dimension = owner.level().dimension();
            this.tier = tier;
            this.position = position;
            this.velocity = velocity;
        }
    }

    private static final int SUB_STEPS = 4;
    /** That's Sick: walls a charge has to come off before it finds somebody. */
    private static final int THATS_SICK_BOUNCES = 3;

    private static final DustParticleOptions MAGENTA = new DustParticleOptions(new Vector3f(0.85F, 0.2F, 0.8F), 1.0F);
    private static final DustParticleOptions MAGENTA_LARGE = new DustParticleOptions(new Vector3f(0.95F, 0.3F, 0.9F), 2.0F);

    private static final List<Shot> IN_FLIGHT = new ArrayList<>();

    private OutOfBreath() {}

    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        IN_FLIGHT.add(new Shot(player, data, tier, player.getEyePosition(),
                player.getLookAngle().scale(ModPerks.BREATH_SPEED)));

        data.setCooldown(ModPerks.OUT_OF_BREATH.id(), gameTime,
                ModPerks.OUT_OF_BREATH.cooldownTicks(tier));
        player.level().playSound(null, player.blockPosition(), SoundEvents.SNOWBALL_THROW,
                SoundSource.PLAYERS, 0.8F, 0.7F);
        return true;
    }

    /** Steps every charge in the air. Called once a tick for the whole server. */
    public static void tickShots(MinecraftServer server) {
        if (!CLOUDS.isEmpty()) {
            tickClouds(server);
        }
        if (IN_FLIGHT.isEmpty()) {
            return;
        }
        Iterator<Shot> iterator = IN_FLIGHT.iterator();
        while (iterator.hasNext()) {
            Shot shot = iterator.next();
            ServerLevel level = server.getLevel(shot.dimension);
            if (level == null || !advance(level, shot)) {
                iterator.remove();
            }
        }
    }

    /** @return false once the charge has gone off or given up */
    private static boolean advance(ServerLevel level, Shot shot) {
        if (--shot.ticksLeft <= 0) {
            return false;
        }

        shot.velocity = shot.velocity.subtract(0.0D, ModPerks.BREATH_GRAVITY, 0.0D);
        Vec3 step = shot.velocity.scale(1.0D / SUB_STEPS);

        for (int i = 0; i < SUB_STEPS; i++) {
            Vec3 from = shot.position;
            Vec3 to = from.add(step);

            BlockHitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (wall.getType() == HitResult.Type.MISS) {
                shot.position = to;
                continue;
            }

            Direction face = wall.getDirection();
            shot.position = wall.getLocation().add(Vec3.atLowerCornerOf(face.getNormal())
                    .scale(ModPerks.BREATH_PROJECTILE_RADIUS + 0.02D));

            // The floor, and only the floor, sets it off. Everything else is a wall to come off.
            if (face == Direction.UP) {
                detonate(level, shot);
                return false;
            }

            shot.velocity = BankShotHandler.reflect(shot.velocity, face)
                    .scale(ModPerks.BREATH_BOUNCE_SPEED * (shot.rubberSheet ? ModAddons.RUBBER_SHEET_BOUNCE : 1.0D));
            shot.bounces++;
            level.playSound(null, shot.position.x, shot.position.y, shot.position.z,
                    SoundEvents.METAL_HIT, SoundSource.PLAYERS, 0.4F, 0.9F);
            break;
        }

        // Magenta, the colour of the Exhausted it carries, so a charge in the air is unmistakable.
        level.sendParticles(MAGENTA, shot.position.x, shot.position.y, shot.position.z,
                3, 0.04D, 0.04D, 0.04D, 0.0D);
        return true;
    }

    private static void detonate(ServerLevel level, Shot shot) {
        Vec3 blast = shot.position;
        double radius = ModPerks.OUT_OF_BREATH.value(ModPerks.BREATH_RADIUS, shot.tier);
        int level_ = ModPerks.OUT_OF_BREATH.intValue(ModPerks.BREATH_LEVEL, shot.tier);
        int duration = ModPerks.OUT_OF_BREATH.ticks(ModPerks.BREATH_DURATION, shot.tier);
        float damage = (float) ModPerks.OUT_OF_BREATH.value(ModPerks.BREATH_DAMAGE, shot.tier);
        // Spray Can: a charge that came off a wall goes off twice as wide, and harder on the winded.
        if (shot.sprayCan && shot.bounces > 0) {
            radius *= ModAddons.SPRAY_CAN_RADIUS;
            damage *= (float) ModAddons.SPRAY_CAN_DAMAGE;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(shot.ownerId);

        level.sendParticles(MAGENTA_LARGE, blast.x, blast.y, blast.z, 60,
                radius * 0.45D, 0.25D, radius * 0.45D, 0.0D);
        level.sendParticles(ParticleTypes.WITCH, blast.x, blast.y + 0.2D, blast.z, 30,
                radius * 0.4D, 0.3D, radius * 0.4D, 0.02D);
        // A ring at the edge of the blast, so where it reached is as clear as where it went off.
        for (int i = 0; i < 32; i++) {
            double angle = Math.PI * 2.0D * i / 32.0D;
            level.sendParticles(MAGENTA, blast.x + Math.cos(angle) * radius, blast.y + 0.1D,
                    blast.z + Math.sin(angle) * radius, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        level.playSound(null, blast.x, blast.y, blast.z, SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, 1.2F, 0.6F);

        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(blast, radius * 2, radius * 2, radius * 2))) {
            if (!victim.isAlive() || victim.getEyePosition().distanceTo(blast) > radius
                    && victim.position().distanceTo(blast) > radius) {
                continue;
            }
            if (shot.bounces >= THATS_SICK_BOUNCES && !victim.getUUID().equals(shot.ownerId) && owner != null) {
                ModAdvancements.grant(owner, ModAdvancements.THATS_SICK);
            }
            // Read before the new dose goes on, or everybody caught would count as already Exhausted.
            boolean alreadyExhausted = victim.hasEffect(ModEffects.EXHAUSTED);
            victim.addEffect(new MobEffectInstance(ModEffects.EXHAUSTED, duration, level_ - 1,
                    false, true, true));
            if (alreadyExhausted) {
                victim.hurt(PerkDamageSource.of(blastSource(level, shot), "out_of_breath"), damage);
                // Nail Polish: every winded thing caught is a little health back for the thrower.
                if (shot.nailPolish && owner != null && victim != owner && owner.isAlive()) {
                    owner.heal(ModAddons.NAIL_POLISH_HEAL);
                }
            }
        }

        // Pepper Dust: the blast hangs in the air, and whatever walks into it later gets a share.
        if (shot.pepperDust) {
            Cloud cloud = new Cloud(level.dimension(), blast, radius, level.getGameTime() + ModAddons.PEPPER_DUST_TICKS,
                    level_, (int) Math.round(duration * ModAddons.PEPPER_DUST_DURATION));
            for (LivingEntity caught : level.getEntitiesOfClass(LivingEntity.class, cloud.area())) {
                cloud.touched.add(caught.getUUID());
            }
            CLOUDS.add(cloud);
        }
    }

    /** A Pepper Dust cloud hanging where a charge went off. */
    private static final class Cloud {
        private final ResourceKey<Level> dimension;
        private final Vec3 centre;
        private final double radius;
        private final long until;
        private final int level;
        private final int duration;
        /** What has already had its share; each gets one, however long it stands in it. */
        private final Set<UUID> touched = new HashSet<>();

        private Cloud(ResourceKey<Level> dimension, Vec3 centre, double radius, long until, int level, int duration) {
            this.dimension = dimension;
            this.centre = centre;
            this.radius = radius;
            this.until = until;
            this.level = level;
            this.duration = duration;
        }

        private AABB area() {
            return AABB.ofSize(centre, radius * 2.0D, radius * 2.0D, radius * 2.0D);
        }
    }

    private static final List<Cloud> CLOUDS = new ArrayList<>();

    /** Keeps the clouds drifting and catching what walks in. */
    private static void tickClouds(MinecraftServer server) {
        Iterator<Cloud> iterator = CLOUDS.iterator();
        while (iterator.hasNext()) {
            Cloud cloud = iterator.next();
            ServerLevel level = server.getLevel(cloud.dimension);
            if (level == null || level.getGameTime() >= cloud.until) {
                iterator.remove();
                continue;
            }
            if (level.getGameTime() % 4L == 0L) {
                level.sendParticles(MAGENTA, cloud.centre.x, cloud.centre.y + 0.3D, cloud.centre.z, 6,
                        cloud.radius * 0.4D, 0.25D, cloud.radius * 0.4D, 0.0D);
            }
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, cloud.area())) {
                if (victim.isAlive() && victim.position().distanceTo(cloud.centre) <= cloud.radius
                        && cloud.touched.add(victim.getUUID())) {
                    victim.addEffect(new MobEffectInstance(ModEffects.EXHAUSTED, cloud.duration, cloud.level - 1,
                            false, true, true));
                }
            }
        }
    }

    /** Magic damage, credited to the thrower when they are still around. */
    private static DamageSource blastSource(ServerLevel level, Shot shot) {
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(shot.ownerId);
        return owner != null
                ? level.damageSources().indirectMagic(owner, owner)
                : level.damageSources().magic();
    }

    /** Drops anything still in the air for a player who has left. */
    public static void clear(UUID playerId) {
        IN_FLIGHT.removeIf(shot -> shot.ownerId.equals(playerId));
    }
}
