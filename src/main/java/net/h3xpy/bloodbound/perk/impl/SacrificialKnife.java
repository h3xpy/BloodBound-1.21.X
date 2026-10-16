package net.h3xpy.bloodbound.perk.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import net.h3xpy.bloodbound.damage.PerkDamageSource;
import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.entity.KnifeEntity;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.perk.PerkGlow;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Sacrificial Knife: a thrown knife that stays where it lands.
 * <p>
 * The flight is worked out here, like the mod's other thrown things; the knife the players see is a
 * {@link KnifeEntity} that only follows along. Stuck in a target, it leaves two ways out: kill the
 * target and it bursts, mending the thrower and hurting everything round it; or press the key again
 * to tear the knife back, which hauls the target all the way to the thrower, for half the
 * cooldown. Left alone, the knife falls out after a while. The slot glows while a knife is stuck.
 */
public final class SacrificialKnife {

    /** A knife in the air. */
    private static final class Throw {
        private final UUID ownerId;
        private final ResourceKey<Level> dimension;
        private final int tier;
        private final KnifeEntity knife;
        private Vec3 position;
        private Vec3 velocity;
        private int ticksLeft = ModPerks.KNIFE_MAX_FLIGHT_TICKS;

        private Throw(ServerPlayer owner, int tier, KnifeEntity knife) {
            this.ownerId = owner.getUUID();
            this.dimension = owner.level().dimension();
            this.tier = tier;
            this.knife = knife;
            // Leaves from a little in front of the eyes, so the thrower does not see it fill the screen.
            this.position = owner.getEyePosition().add(owner.getLookAngle().scale(0.6D));
            this.velocity = owner.getLookAngle().scale(ModPerks.KNIFE_SPEED);
        }
    }

    /** A knife stuck in something, and when it falls out. */
    private record Stuck(UUID targetId, ResourceKey<Level> dimension, int tier, KnifeEntity knife, long fallsOutAt) {}

    /**
     * A target being hauled in. Steered every tick along an arc to a spot in front of the thrower,
     * by setting its speed to exactly the next step: whatever the distance, it lands there.
     */
    private static final class Pull {
        private final UUID targetId;
        private final UUID ownerId;
        private final ResourceKey<Level> dimension;
        private final Vec3 start;
        private final int ticks;
        private final double arc;
        private int step;

        private Pull(LivingEntity target, ServerPlayer owner, int ticks, double arc) {
            this.targetId = target.getUUID();
            this.ownerId = owner.getUUID();
            this.dimension = target.level().dimension();
            this.start = target.position();
            this.ticks = ticks;
            this.arc = arc;
        }
    }

    private static final int SUB_STEPS = 4;
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.55F, 0.0F, 0.02F), 1.2F);

    private static final List<Throw> IN_FLIGHT = new ArrayList<>();
    /** Owner to the knife they have stuck in something. */
    private static final Map<UUID, Stuck> STUCK = new HashMap<>();
    private static final List<Pull> PULLS = new ArrayList<>();

    private SacrificialKnife() {}

    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        KnifeEntity knife = new KnifeEntity(player.level(), player.getEyePosition().add(player.getLookAngle().scale(0.6D)),
                player.getLookAngle());
        player.level().addFreshEntity(knife);
        IN_FLIGHT.add(new Throw(player, tier, knife));
        data.setCooldown(ModPerks.SACRIFICIAL_KNIFE.id(), gameTime, ModPerks.SACRIFICIAL_KNIFE.cooldownTicks(tier));
        player.level().playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW.value(),
                SoundSource.PLAYERS, 0.7F, 1.4F);
        return true;
    }

    /**
     * The key while a knife is stuck: tear it back out. Heard before the cooldown gate, since the
     * perk is still cooling down from the throw.
     *
     * @return true when the key was spent pulling the knife back
     */
    public static boolean tryRecall(ServerPlayer player, long gameTime) {
        Stuck stuck = STUCK.remove(player.getUUID());
        if (stuck == null) {
            return false;
        }
        stuck.knife().discard();
        PerkGlow.set(player, ModPerks.SACRIFICIAL_KNIFE, false);
        LivingEntity target = target(player.getServer(), stuck);
        if (target != null && target.level() == player.level()) {
            startPull(player, target);
            ((ServerLevel) target.level()).sendParticles(BLOOD, target.getX(), target.getY() + target.getBbHeight() / 2.0D,
                    target.getZ(), 12, 0.2D, 0.3D, 0.2D, 0.0D);
        }
        shortenCooldown(player, ModPerks.KNIFE_RECALL_COOLDOWN, gameTime);
        player.level().playSound(null, player.blockPosition(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.9F, 1.2F);
        return true;
    }

    /** Leaves only this share of what is left of the cooldown. */
    private static void shortenCooldown(ServerPlayer player, double share, long gameTime) {
        PlayerPerkData data = PerkDataManager.get(player);
        int remaining = data.getCooldownRemaining(ModPerks.SACRIFICIAL_KNIFE.id(), gameTime);
        data.setCooldown(ModPerks.SACRIFICIAL_KNIFE.id(), gameTime, (int) Math.round(remaining * share));
        PerkDataManager.sync(player);
    }

    // --- the pull ---

    private static void startPull(ServerPlayer owner, LivingEntity target) {
        PULLS.removeIf(pull -> pull.targetId.equals(target.getUUID()));
        double distance = target.position().distanceTo(owner.position());
        // As quick as the speed limit allows, and never under half a second, so it reads as a haul.
        int ticks = Math.max(10, (int) Math.ceil(distance / ModPerks.KNIFE_PULL_SPEED) + 4);
        double arc = Math.min(ModPerks.KNIFE_PULL_ARC, 0.5D + distance * 0.1D);
        PULLS.add(new Pull(target, owner, ticks, arc));
    }

    /** Where the pull is meant to be at this share of the way, landing in front of where the thrower is now. */
    private static Vec3 pullPoint(Pull pull, ServerPlayer owner, double share) {
        Vec3 facing = owner.getLookAngle();
        Vec3 flat = new Vec3(facing.x, 0.0D, facing.z);
        flat = flat.lengthSqr() < 1.0E-4D ? Vec3.ZERO : flat.normalize();
        Vec3 goal = owner.position().add(flat.scale(ModPerks.KNIFE_PULL_STOP));
        Vec3 straight = pull.start.lerp(goal, share);
        return straight.add(0.0D, pull.arc * 4.0D * share * (1.0D - share), 0.0D);
    }

    private static void tickPulls(MinecraftServer server) {
        Iterator<Pull> iterator = PULLS.iterator();
        while (iterator.hasNext()) {
            Pull pull = iterator.next();
            ServerLevel level = server.getLevel(pull.dimension);
            Entity entity = level == null ? null : level.getEntity(pull.targetId);
            ServerPlayer owner = server.getPlayerList().getPlayer(pull.ownerId);
            if (!(entity instanceof LivingEntity target) || !target.isAlive() || owner == null || owner.level() != level) {
                iterator.remove();
                continue;
            }
            pull.step++;
            if (pull.step > pull.ticks) {
                // Arrived: stopped dead, or the speed of the last step would carry it on past.
                target.setDeltaMovement(Vec3.ZERO);
                iterator.remove();
            } else {
                // Exactly the step to the next point: a change of speed, never a teleport, so a
                // player's own client carries it out smoothly.
                Vec3 next = pullPoint(pull, owner, pull.step / (double) pull.ticks);
                target.setDeltaMovement(next.subtract(target.position()));
            }
            target.hurtMarked = true;
            target.fallDistance = 0.0F;
        }
    }

    // --- flight ---

    /** Steps the knives in the air, the stuck ones and the pulls. Called once a tick for the server. */
    public static void tick(MinecraftServer server) {
        if (!IN_FLIGHT.isEmpty()) {
            Iterator<Throw> iterator = IN_FLIGHT.iterator();
            while (iterator.hasNext()) {
                Throw knife = iterator.next();
                ServerLevel level = server.getLevel(knife.dimension);
                if (level == null || !advance(level, knife)) {
                    iterator.remove();
                }
            }
        }
        if (!STUCK.isEmpty()) {
            tickStuck(server);
        }
        if (!PULLS.isEmpty()) {
            tickPulls(server);
        }
    }

    private static void tickStuck(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Stuck>> iterator = STUCK.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Stuck> entry = iterator.next();
            Stuck stuck = entry.getValue();
            LivingEntity target = target(server, stuck);
            ServerLevel level = server.getLevel(stuck.dimension());
            // Gone, out of the loaded world, or left in too long: the knife falls out.
            if (target == null || level == null || level.getGameTime() >= stuck.fallsOutAt()) {
                stuck.knife().discard();
                iterator.remove();
                ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
                if (owner != null) {
                    PerkGlow.set(owner, ModPerks.SACRIFICIAL_KNIFE, false);
                }
                continue;
            }
            stuck.knife().follow(target);
            if (level.getGameTime() % 5L == 0L) {
                level.sendParticles(BLOOD, target.getX(), target.getY() + target.getBbHeight() * 0.6D,
                        target.getZ(), 2, 0.15D, 0.15D, 0.15D, 0.0D);
            }
        }
    }

    /** @return false once the knife has landed or given up */
    private static boolean advance(ServerLevel level, Throw knife) {
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(knife.ownerId);
        if (--knife.ticksLeft <= 0 || knife.knife.isRemoved()) {
            knife.knife.discard();
            missed(owner, level);
            return false;
        }
        knife.velocity = knife.velocity.subtract(0.0D, ModPerks.KNIFE_GRAVITY, 0.0D);
        Vec3 step = knife.velocity.scale(1.0D / SUB_STEPS);
        for (int i = 0; i < SUB_STEPS; i++) {
            Vec3 from = knife.position;
            Vec3 to = from.add(step);
            LivingEntity hit = entityAlong(level, owner, from, to);
            if (hit != null) {
                Vec3 point = hit.getBoundingBox().clip(from, to).orElse(hit.getBoundingBox().getCenter());
                stick(level, knife, owner, hit, point);
                return false;
            }
            BlockHitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                    CollisionContext.empty()));
            if (wall.getType() != HitResult.Type.MISS) {
                // Left quivering in the wall for a moment.
                knife.knife.setPos(wall.getLocation().subtract(knife.velocity.normalize().scale(0.1D)));
                knife.knife.point(knife.velocity);
                knife.knife.leaveFor(ModPerks.KNIFE_WALL_TICKS);
                level.playSound(null, wall.getBlockPos(), SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 0.6F, 1.4F);
                missed(owner, level);
                return false;
            }
            knife.position = to;
        }
        knife.knife.setPos(knife.position);
        knife.knife.point(knife.velocity);
        return true;
    }

    private static void missed(@Nullable ServerPlayer owner, ServerLevel level) {
        if (owner != null) {
            shortenCooldown(owner, ModPerks.KNIFE_MISS_COOLDOWN, level.getGameTime());
        }
    }

    private static void stick(ServerLevel level, Throw knife, @Nullable ServerPlayer owner, LivingEntity target, Vec3 point) {
        float damage = (float) ModPerks.SACRIFICIAL_KNIFE.value(ModPerks.KNIFE_DAMAGE, knife.tier);
        // A thrown blade, armour and all: a player's own attack, with its own death message.
        target.hurt(PerkDamageSource.of(owner != null ? level.damageSources().playerAttack(owner)
                : level.damageSources().generic(), "sacrificial_knife"), damage);
        level.playSound(null, target.blockPosition(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.9F, 1.0F);
        level.sendParticles(BLOOD, point.x, point.y, point.z, 10, 0.15D, 0.15D, 0.15D, 0.0D);
        if (owner == null) {
            knife.knife.discard();
            return;
        }
        if (!target.isAlive()) {
            // The throw itself was the kill.
            knife.knife.discard();
            burst(level, owner, target, knife.tier);
            return;
        }
        knife.knife.stickTo(target, point, knife.velocity);
        Stuck previous = STUCK.put(owner.getUUID(), new Stuck(target.getUUID(), level.dimension(), knife.tier,
                knife.knife, level.getGameTime() + ModPerks.KNIFE_STUCK_TICKS));
        if (previous != null) {
            previous.knife().discard();
        }
        PerkGlow.set(owner, ModPerks.SACRIFICIAL_KNIFE, true);
    }

    @Nullable
    private static LivingEntity entityAlong(ServerLevel level, @Nullable ServerPlayer owner, Vec3 from, Vec3 to) {
        AABB swept = new AABB(from, to).inflate(ModPerks.KNIFE_RADIUS);
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, swept)) {
            if (candidate == owner || !candidate.isAlive() || candidate.isSpectator()) {
                continue;
            }
            double distance = candidate.distanceToSqr(from);
            if (distance < best) {
                nearest = candidate;
                best = distance;
            }
        }
        return nearest;
    }

    @Nullable
    private static LivingEntity target(MinecraftServer server, Stuck stuck) {
        ServerLevel level = server.getLevel(stuck.dimension());
        Entity entity = level == null ? null : level.getEntity(stuck.targetId());
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    // --- the burst ---

    /** Whatever has a knife in it dies: it bursts. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide() || STUCK.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Stuck>> iterator = STUCK.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Stuck> entry = iterator.next();
            if (!entry.getValue().targetId().equals(dead.getUUID())) {
                continue;
            }
            iterator.remove();
            entry.getValue().knife().discard();
            ServerPlayer owner = dead.getServer().getPlayerList().getPlayer(entry.getKey());
            if (owner != null) {
                PerkGlow.set(owner, ModPerks.SACRIFICIAL_KNIFE, false);
                burst((ServerLevel) dead.level(), owner, dead, entry.getValue().tier());
            }
        }
    }

    /** A bloody mess: the thrower is mended, and everything else close by is hurt as much. */
    private static void burst(ServerLevel level, ServerPlayer owner, LivingEntity dead, int tier) {
        float amount = (float) ModPerks.SACRIFICIAL_KNIFE.value(ModPerks.KNIFE_BURST, tier);
        double radius = ModPerks.SACRIFICIAL_KNIFE.value(ModPerks.KNIFE_BURST_RADIUS, tier);
        Vec3 centre = dead.position().add(0.0D, dead.getBbHeight() / 2.0D, 0.0D);
        level.sendParticles(BLOOD, centre.x, centre.y, centre.z, 60, 0.6D, 0.6D, 0.6D, 0.0D);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
                centre.x, centre.y, centre.z, 40, 0.5D, 0.5D, 0.5D, 0.15D);
        // The edge of the burst, so how far it reached is plain.
        for (int i = 0; i < 40; i++) {
            double angle = Math.PI * 2.0D * i / 40.0D;
            level.sendParticles(BLOOD, centre.x + Math.cos(angle) * radius, dead.getY() + 0.2D,
                    centre.z + Math.sin(angle) * radius, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        level.playSound(null, dead.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.2F, 0.6F);
        level.playSound(null, dead.blockPosition(), SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, SoundSource.PLAYERS, 1.0F, 0.7F);

        if (owner.isAlive()) {
            owner.heal(amount);
        }
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(centre, radius * 2, radius * 2, radius * 2))) {
            if (nearby == owner || nearby == dead || !nearby.isAlive() || nearby.distanceToSqr(centre) > radius * radius) {
                continue;
            }
            nearby.hurt(PerkDamageSource.of(level.damageSources().playerAttack(owner), "sacrificial_knife"), amount);
        }
    }

    /** Forgets a player's knives, in the air and stuck alike, and any pull of theirs. */
    public static void clear(UUID playerId) {
        IN_FLIGHT.removeIf(knife -> {
            if (knife.ownerId.equals(playerId)) {
                knife.knife.discard();
                return true;
            }
            return false;
        });
        Stuck stuck = STUCK.remove(playerId);
        if (stuck != null) {
            stuck.knife().discard();
        }
        PULLS.removeIf(pull -> pull.ownerId.equals(playerId));
    }
}
