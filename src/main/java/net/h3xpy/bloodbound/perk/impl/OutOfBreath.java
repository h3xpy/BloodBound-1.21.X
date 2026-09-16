package net.h3xpy.bloodbound.perk.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.event.BankShotHandler;
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

        private Shot(ServerPlayer owner, int tier, Vec3 position, Vec3 velocity) {
            this.ownerId = owner.getUUID();
            this.dimension = owner.level().dimension();
            this.tier = tier;
            this.position = position;
            this.velocity = velocity;
        }
    }

    private static final int SUB_STEPS = 4;

    private static final DustParticleOptions MAGENTA = new DustParticleOptions(new Vector3f(0.85F, 0.2F, 0.8F), 1.0F);
    private static final DustParticleOptions MAGENTA_LARGE = new DustParticleOptions(new Vector3f(0.95F, 0.3F, 0.9F), 2.0F);

    private static final List<Shot> IN_FLIGHT = new ArrayList<>();

    private OutOfBreath() {}

    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        IN_FLIGHT.add(new Shot(player, tier, player.getEyePosition(),
                player.getLookAngle().scale(ModPerks.BREATH_SPEED)));

        data.setCooldown(ModPerks.OUT_OF_BREATH.id(), gameTime,
                ModPerks.OUT_OF_BREATH.cooldownTicks(tier));
        player.level().playSound(null, player.blockPosition(), SoundEvents.SNOWBALL_THROW,
                SoundSource.PLAYERS, 0.8F, 0.7F);
        return true;
    }

    /** Steps every charge in the air. Called once a tick for the whole server. */
    public static void tickShots(MinecraftServer server) {
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
                    .scale(ModPerks.BREATH_BOUNCE_SPEED);
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
            victim.addEffect(new MobEffectInstance(ModEffects.EXHAUSTED, duration, level_ - 1,
                    false, true, true));
        }
    }

    /** Drops anything still in the air for a player who has left. */
    public static void clear(UUID playerId) {
        IN_FLIGHT.removeIf(shot -> shot.ownerId.equals(playerId));
    }
}
