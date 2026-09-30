package net.h3xpy.bloodbound.perk.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.entity.ChainAnchorEntity;
import net.h3xpy.bloodbound.event.BankShotHandler;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Chained Up: a fast, flat shot that chains whatever it hits to the ground it was standing on.
 * <p>
 * Like the harpoon it is simulated here rather than spawned. What it leaves behind is a real
 * entity, {@link ChainAnchorEntity}, which does the holding and has to be broken to end it. A player
 * has one chain out at a time: a second catch lets the first go.
 * <p>
 * The long cooldown is charged as the shot leaves, so it cannot be fired again while in the air; a
 * shot that finds nothing hands most of it back.
 */
public final class ChainedUp {

    private static final class Shot {
        private final UUID ownerId;
        private final ResourceKey<Level> dimension;
        private final int tier;
        private final long firedAt;
        private Vec3 position;
        private Vec3 velocity;
        private double travelled;
        private int bankBounces;
        private boolean bounced;

        private Shot(ServerPlayer owner, int tier, long firedAt) {
            this.ownerId = owner.getUUID();
            this.dimension = owner.level().dimension();
            this.tier = tier;
            this.firedAt = firedAt;
            this.position = owner.getEyePosition();
            this.velocity = owner.getLookAngle().scale(ModPerks.CHAIN_SHOT_SPEED);
        }
    }

    private static final int SUB_STEPS = 6;

    private static final List<Shot> IN_FLIGHT = new ArrayList<>();
    /** The anchor each player has out. */
    private static final Map<UUID, ChainAnchorEntity> ANCHORS = new HashMap<>();

    private ChainedUp() {}

    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        Shot shot = new Shot(player, tier, gameTime);
        shot.bankBounces = BankShotHandler.spendBounce(player) ? ModPerks.BANK_SHOT_BOUNCES : 0;
        IN_FLIGHT.add(shot);
        data.setCooldown(ModPerks.CHAINED_UP.id(), gameTime, ModPerks.CHAINED_UP.cooldownTicks(tier));
        player.level().playSound(null, player.blockPosition(), SoundEvents.CHAIN_PLACE,
                SoundSource.PLAYERS, 0.9F, 1.4F);
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

    /** @return false once the shot has hit something or given up */
    private static boolean advance(ServerLevel level, Shot shot) {
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(shot.ownerId);
        Vec3 step = shot.velocity.scale(1.0D / SUB_STEPS);

        for (int i = 0; i < SUB_STEPS; i++) {
            Vec3 from = shot.position;
            Vec3 to = from.add(step);

            LivingEntity caught = entityAlong(level, shot, from, to);
            if (caught != null) {
                if (owner != null) {
                    chain(level, owner, shot, caught);
                }
                return false;
            }

            BlockHitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (wall.getType() != HitResult.Type.MISS) {
                level.sendParticles(ParticleTypes.CRIT, to.x, to.y, to.z, 6, 0.1D, 0.1D, 0.1D, 0.02D);
                if (shot.bankBounces <= 0) {
                    missed(level, shot, owner);
                    return false;
                }
                // Bank Shot: one carom, and a look for somebody on the way out.
                shot.bankBounces--;
                shot.bounced = true;
                Direction face = wall.getDirection();
                shot.velocity = BankShotHandler.reflect(shot.velocity, face).scale(ModPerks.BANK_SHOT_BOUNCE_SPEED);
                shot.position = wall.getLocation().add(Vec3.atLowerCornerOf(face.getNormal())
                        .scale(ModPerks.CHAIN_SHOT_RADIUS + 0.02D));
                LivingEntity mark = BankShotHandler.seek(level, shot.position, shot.velocity, owner);
                if (mark != null) {
                    shot.velocity = BankShotHandler.steer(shot.position, shot.velocity, mark);
                }
                return true;
            }

            shot.position = to;
            shot.travelled += step.length();
            if (shot.travelled > ModPerks.CHAIN_SHOT_RANGE) {
                missed(level, shot, owner);
                return false;
            }
        }

        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, shot.position.x, shot.position.y, shot.position.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        return true;
    }

    @Nullable
    private static LivingEntity entityAlong(ServerLevel level, Shot shot, Vec3 from, Vec3 to) {
        AABB swept = new AABB(from, to).inflate(ModPerks.CHAIN_SHOT_RADIUS);
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, swept)) {
            if (candidate.getUUID().equals(shot.ownerId) || !candidate.isAlive()
                    || candidate.isSpectator() || candidate instanceof ArmorStand) {
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

    /** A catch: the anchor goes down where the victim stands, and the chain is on. */
    private static void chain(ServerLevel level, ServerPlayer owner, Shot shot, LivingEntity victim) {
        if (shot.bounced) {
            BankShotHandler.checkTrickShot(owner, victim);
        }
        release(owner.getUUID());

        ChainAnchorEntity anchor = new ChainAnchorEntity(level, owner.getUUID(), victim,
                ModPerks.CHAINED_UP.value(ModPerks.CHAIN_LENGTH, shot.tier),
                (float) ModPerks.CHAINED_UP.value(ModPerks.CHAIN_ANCHOR_HEALTH, shot.tier),
                ModPerks.CHAIN_PULL_PER_BLOCK, ModPerks.CHAIN_PULL_MAX);
        anchor.moveTo(victim.getX(), victim.getY(), victim.getZ(), 0.0F, 0.0F);
        level.addFreshEntity(anchor);
        ANCHORS.put(owner.getUUID(), anchor);

        level.playSound(null, victim.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 0.6F);
        level.playSound(null, victim.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.4F, 1.6F);
    }

    /** A shot that caught nothing leaves only the short cooldown, counted from when it was fired. */
    private static void missed(ServerLevel level, Shot shot, @Nullable ServerPlayer owner) {
        if (owner == null) {
            return;
        }
        PlayerPerkData data = PerkDataManager.get(owner);
        long now = level.getGameTime();
        int miss = ModPerks.CHAINED_UP.ticks(ModPerks.CHAIN_MISS_COOLDOWN, shot.tier);
        data.setCooldown(ModPerks.CHAINED_UP.id(), now, (int) Math.max(0L, miss - (now - shot.firedAt)));
        PerkDataManager.sync(owner);
    }

    /** Lets whatever this player has chained go. */
    private static void release(UUID ownerId) {
        ChainAnchorEntity anchor = ANCHORS.remove(ownerId);
        if (anchor != null && !anchor.isRemoved()) {
            anchor.discard();
        }
    }

    /** Drops the player's shots and chain on logout: nobody is left holding the other end. */
    public static void clear(UUID playerId) {
        IN_FLIGHT.removeIf(shot -> shot.ownerId.equals(playerId));
        release(playerId);
    }
}
