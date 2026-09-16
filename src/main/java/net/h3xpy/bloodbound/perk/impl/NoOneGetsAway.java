package net.h3xpy.bloodbound.perk.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.event.AuraRevealHandler;
import net.h3xpy.bloodbound.event.BankShotHandler;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * No One Gets Away: a flat, fast shot that drags whatever it catches back to the player.
 * <p>
 * Like the Low-Cost Movement Device's shot this is not an entity — it is invisible, lives for well
 * under a second and only has to answer one question per tick, so it is simulated here and drawn
 * with particles. The addons are read once, as it leaves: what a shot does is settled when it is
 * fired, not re-read halfway down the range.
 */
public final class NoOneGetsAway {

    /** One shot in the air. */
    private static final class Shot {
        private final UUID ownerId;
        private final ResourceKey<Level> dimension;
        private final long firedAt;
        private final int cooldownTicks;
        private final boolean tracking;
        private final boolean heavyHook;
        private final boolean soulChain;
        private final double speed;
        /** Everything Soul Chain has already lit up, so each is only revealed once. */
        private final Set<UUID> revealed = new HashSet<>();
        private Vec3 velocity;
        private Vec3 position;
        private double travelled;
        /** Bounces bought from Bank Shot. */
        private int bankBounces;

        private Shot(ServerPlayer owner, PlayerPerkData data, int tier, long firedAt) {
            this.ownerId = owner.getUUID();
            this.dimension = owner.level().dimension();
            this.firedAt = firedAt;
            this.cooldownTicks = ModPerks.NO_ONE_GETS_AWAY.cooldownTicks(tier);
            this.tracking = data.isAddonActive(ModAddons.TRACKING_HEAD);
            this.heavyHook = data.isAddonActive(ModAddons.HEAVY_HOOK);
            this.soulChain = data.isAddonActive(ModAddons.SOUL_CHAIN);

            double speed = ModPerks.HARPOON_SPEED;
            if (data.isAddonActive(ModAddons.ENHANCED_GAUNTLET)) {
                speed *= ModAddons.ENHANCED_GAUNTLET_SPEED;
            }
            if (soulChain) {
                speed *= ModAddons.SOUL_CHAIN_SPEED;
            }
            this.speed = speed;
            this.position = owner.getEyePosition();
            this.velocity = owner.getLookAngle().scale(speed);
        }
    }

    /** How many collision checks each tick of flight is broken into. */
    private static final int SUB_STEPS = 6;

    private static final List<Shot> IN_FLIGHT = new ArrayList<>();

    private NoOneGetsAway() {}

    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        Shot shot = new Shot(player, data, tier, gameTime);
        // Soul Chain goes straight through walls, so a Bank Shot bounce would never be spent.
        shot.bankBounces = !shot.soulChain && BankShotHandler.spendBounce(player) ? ModPerks.BANK_SHOT_BOUNCES : 0;
        IN_FLIGHT.add(shot);

        // Charged on the shot rather than on the catch: a miss is a miss, and the perk should not
        // be something you can throw at a wall over and over for free.
        data.setCooldown(ModPerks.NO_ONE_GETS_AWAY.id(), gameTime, shot.cooldownTicks);

        player.level().playSound(null, player.blockPosition(),
                shot.soulChain ? SoundEvents.CHAIN_PLACE : SoundEvents.CROSSBOW_SHOOT,
                SoundSource.PLAYERS, 0.7F, 0.7F);
        return true;
    }

    /** Steps every shot in the air. Called once a tick for the whole server. */
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

    /** @return false once the shot is spent and should be dropped */
    private static boolean advance(ServerLevel level, Shot shot) {
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(shot.ownerId);
        if (shot.tracking) {
            track(level, shot, owner);
        }
        Vec3 step = shot.velocity.scale(1.0D / SUB_STEPS);

        for (int i = 0; i < SUB_STEPS; i++) {
            Vec3 from = shot.position;
            Vec3 to = from.add(step);

            LivingEntity caught = entityAlong(level, owner, from, to);
            if (caught != null) {
                if (owner != null) {
                    reelIn(owner, caught, shot.heavyHook);
                }
                return false;
            }

            // A wall stops it dead — unless it is a Soul Chain, which does not know walls exist.
            if (!shot.soulChain) {
                BlockHitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, CollisionContext.empty()));
                if (wall.getType() != HitResult.Type.MISS) {
                    level.sendParticles(ParticleTypes.CRIT, to.x, to.y, to.z, 6, 0.1D, 0.1D, 0.1D, 0.02D);
                    if (shot.bankBounces <= 0) {
                        return false;
                    }

                    // Bank Shot buys the harpoon one carom, and a look for a mark on the way out.
                    shot.bankBounces--;
                    Direction face = wall.getDirection();
                    shot.velocity = BankShotHandler.reflect(shot.velocity, face)
                            .scale(ModPerks.BANK_SHOT_BOUNCE_SPEED);
                    shot.position = wall.getLocation().add(Vec3.atLowerCornerOf(face.getNormal())
                            .scale(ModPerks.HARPOON_RADIUS + 0.02D));

                    LivingEntity mark = BankShotHandler.seek(level, shot.position, shot.velocity, owner);
                    if (mark != null) {
                        shot.velocity = BankShotHandler.steer(shot.position, shot.velocity, mark);
                    }
                    return true;
                }
            }

            shot.position = to;
            shot.travelled += step.length();
            if (shot.travelled > ModPerks.HARPOON_RANGE) {
                missed(level, shot, owner);
                return false;
            }
        }

        if (shot.soulChain && owner != null) {
            revealAround(level, shot, owner);
        }
        level.sendParticles(shot.soulChain ? ParticleTypes.SOUL : ParticleTypes.CRIT,
                shot.position.x, shot.position.y, shot.position.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        return true;
    }

    /** Tracking Head: a gentle pull onto whatever is closest to the line the shot is flying down. */
    private static void track(ServerLevel level, Shot shot, @Nullable ServerPlayer owner) {
        Vec3 direction = shot.velocity.normalize();
        double minimumDot = Math.cos(Math.toRadians(ModAddons.TRACKING_HEAD_CONE));
        double range = ModAddons.TRACKING_HEAD_RANGE;

        LivingEntity best = null;
        double bestDot = -2.0D;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(shot.position, range * 2, range * 2, range * 2))) {
            if (candidate == owner || !candidate.isAlive() || candidate.isSpectator()) {
                continue;
            }
            Vec3 offset = candidate.getBoundingBox().getCenter().subtract(shot.position);
            double distance = offset.length();
            if (distance > range || distance < 1.0E-3D) {
                continue;
            }
            double dot = offset.scale(1.0D / distance).dot(direction);
            if (dot >= minimumDot && dot > bestDot) {
                best = candidate;
                bestDot = dot;
            }
        }
        if (best == null) {
            return;
        }
        Vec3 wanted = best.getBoundingBox().getCenter().subtract(shot.position).normalize().scale(shot.speed);
        Vec3 turned = shot.velocity.add(wanted.subtract(shot.velocity).scale(ModAddons.TRACKING_HEAD_DRIFT));
        shot.velocity = turned.normalize().scale(shot.speed);
    }

    /** Soul Chain: everything it passes close to shows up for a moment. */
    private static void revealAround(ServerLevel level, Shot shot, ServerPlayer owner) {
        double radius = ModAddons.SOUL_CHAIN_REVEAL_RADIUS;
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(shot.position, radius * 2, radius * 2, radius * 2))) {
            if (nearby == owner || !nearby.isAlive() || nearby.distanceToSqr(shot.position) > radius * radius) {
                continue;
            }
            if (shot.revealed.add(nearby.getUUID())) {
                AuraRevealHandler.reveal(owner, nearby, ModAddons.SOUL_CHAIN_REVEAL_TICKS);
            }
        }
    }

    /** Soul Chain: a shot that catches nothing leaves half the cooldown it was charged. */
    private static void missed(ServerLevel level, Shot shot, @Nullable ServerPlayer owner) {
        if (!shot.soulChain || owner == null) {
            return;
        }
        PlayerPerkData data = PerkDataManager.get(owner);
        long now = level.getGameTime();
        int remaining = (int) Math.max(0L,
                Math.round(shot.cooldownTicks * ModAddons.SOUL_CHAIN_MISS_COOLDOWN) - (now - shot.firedAt));
        data.setCooldown(ModPerks.NO_ONE_GETS_AWAY.id(), now, remaining);
        PerkDataManager.sync(owner);
    }

    /** The first living thing this step passes through, other than whoever fired. */
    private static LivingEntity entityAlong(ServerLevel level, ServerPlayer owner, Vec3 from, Vec3 to) {
        AABB swept = new AABB(from, to).inflate(ModPerks.HARPOON_RADIUS);
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, swept)) {
            if (candidate == owner || !candidate.isAlive()) {
                continue;
            }
            double distance = candidate.distanceToSqr(from);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    /**
     * Brings the two together and leaves the catch barely able to move or fight. Normally the catch
     * is dragged to the player; Heavy Hook drags the player to the catch instead.
     */
    private static void reelIn(ServerPlayer player, LivingEntity caught, boolean heavyHook) {
        if (heavyHook) {
            Vec3 toward = caught.position().subtract(player.position());
            Vec3 flat = new Vec3(toward.x, 0.0D, toward.z);
            Vec3 back = flat.lengthSqr() < 1.0E-4D ? Vec3.ZERO : flat.normalize().scale(ModPerks.HARPOON_DROP_DISTANCE);
            player.teleportTo(caught.getX() - back.x, caught.getY(), caught.getZ() - back.z);
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
            player.hurtMarked = true;
        } else {
            Vec3 facing = player.getLookAngle();
            Vec3 drop = player.position().add(
                    facing.x * ModPerks.HARPOON_DROP_DISTANCE, 0.0D, facing.z * ModPerks.HARPOON_DROP_DISTANCE);
            caught.teleportTo(drop.x, player.getY(), drop.z);
            caught.setDeltaMovement(Vec3.ZERO);
            caught.fallDistance = 0.0F;
            caught.hurtMarked = true;
        }

        caught.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ModPerks.HARPOON_HOLD_TICKS,
                ModPerks.HARPOON_EFFECT_AMPLIFIER, false, true, true));
        caught.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ModPerks.HARPOON_HOLD_TICKS,
                ModPerks.HARPOON_EFFECT_AMPLIFIER, false, true, true));

        player.level().playSound(null, player.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE,
                SoundSource.PLAYERS, 1.0F, 0.6F);
        player.serverLevel().sendParticles(ParticleTypes.CRIT,
                caught.getX(), caught.getY() + caught.getBbHeight() / 2.0D, caught.getZ(),
                20, 0.3D, 0.4D, 0.3D, 0.1D);
    }

    /** Drops anything still in the air for a player who has left. */
    public static void clear(UUID playerId) {
        IN_FLIGHT.removeIf(shot -> shot.ownerId.equals(playerId));
    }
}
