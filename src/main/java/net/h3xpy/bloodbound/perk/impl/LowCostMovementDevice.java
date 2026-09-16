package net.h3xpy.bloodbound.perk.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.event.BankShotHandler;
import net.h3xpy.bloodbound.network.PerkChargesPayload;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Low-Cost Movement Device: fires a falling shot and throws the player at wherever it lands.
 * <p>
 * The shot is not an entity. It is invisible, hits nothing but blocks and lives for a second or
 * two, so it is simulated here and drawn with particles — no entity type, no renderer and nothing
 * to synchronise.
 */
public final class LowCostMovementDevice {

    /** One shot in the air. */
    private static final class Shot {
        private final UUID ownerId;
        private final ResourceKey<Level> dimension;
        private final int tier;
        private final double gravity;
        private Vec3 position;
        private Vec3 velocity;
        private int ticksLeft;
        private int bouncesLeft;
        /** Bounces bought from Bank Shot, spent once Loose Screw's own are gone. */
        private int bankBounces;

        private Shot(UUID ownerId, ResourceKey<Level> dimension, int tier, Vec3 position, Vec3 velocity,
                double gravity, int bouncesLeft) {
            this.ownerId = ownerId;
            this.dimension = dimension;
            this.tier = tier;
            this.position = position;
            this.velocity = velocity;
            this.gravity = gravity;
            this.ticksLeft = ModPerks.DEVICE_MAX_FLIGHT_TICKS;
            this.bouncesLeft = bouncesLeft;
        }
    }

    /** How many collision checks each tick of flight is broken into. */
    private static final int SUB_STEPS = 4;

    private static final List<Shot> IN_FLIGHT = new ArrayList<>();

    private LowCostMovementDevice() {}

    // --- firing ---

    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        int max = maxCharges(data, tier);
        int charges = data.deviceCharges(max);
        if (charges <= 0) {
            return false;
        }

        charges--;
        data.setDeviceCharges(charges);
        // Timers and the key gate are worked out in one place, in the tick, so firing only has to
        // spend the charge and tell the HUD about it.
        sendCharges(player, data, max, charges);

        double speed = ModPerks.LOW_COST_MOVEMENT_DEVICE.value(ModPerks.DEVICE_PROJECTILE_SPEED, tier)
                * (data.isAddonActive(ModAddons.LOOSE_SCREW)
                        ? ModAddons.LOOSE_SCREW_SPEED
                        : ModPerks.DEVICE_SPEED_SCALE);
        double gravity = ModPerks.LOW_COST_MOVEMENT_DEVICE.value(ModPerks.DEVICE_PROJECTILE_GRAVITY, tier)
                * ModPerks.DEVICE_GRAVITY_SCALE;

        // Thrown a touch above where the player is looking, by exactly what the added half of the
        // gravity will take back at the reference distance: an arc to watch, landing where a flat
        // shot would have landed.
        double loft = gravity * ModPerks.DEVICE_LOFT_SHARE * ModPerks.DEVICE_LOFT_REFERENCE
                / (2.0D * speed);
        Vec3 velocity = player.getLookAngle().scale(speed).add(0.0D, loft, 0.0D);

        Shot shot = new Shot(player.getUUID(), player.level().dimension(), tier,
                player.getEyePosition(), velocity, gravity,
                data.isAddonActive(ModAddons.LOOSE_SCREW) ? ModAddons.LOOSE_SCREW_BOUNCES : 0);
        // Bank Shot pays for its own bounce, on top of whatever Loose Screw brings.
        shot.bankBounces = BankShotHandler.spendBounce(player) ? ModPerks.BANK_SHOT_BOUNCES : 0;
        IN_FLIGHT.add(shot);

        player.level().playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_SHOOT,
                SoundSource.PLAYERS, 0.5F, 1.8F);
        return true;
    }

    /** Charges the device holds, which Box Opener cuts to a single one. */
    private static int maxCharges(PlayerPerkData data, int tier) {
        if (data.isAddonActive(ModAddons.BOX_OPENER)) {
            return ModAddons.BOX_OPENER_CHARGES;
        }
        return ModPerks.LOW_COST_MOVEMENT_DEVICE.intValue(ModPerks.DEVICE_CHARGES, tier);
    }

    /**
     * How long the next charge takes. Gear System hands them back one at a time on a short timer,
     * whether the device is empty or not; without it nothing comes back until it has run dry, and
     * then the whole set arrives at once.
     */
    private static int rechargeTicks(PlayerPerkData data, int tier) {
        return data.isAddonActive(ModAddons.GEAR_SYSTEM)
                ? ModAddons.GEAR_SYSTEM_RECHARGE_TICKS
                : cooldownTicks(data, tier);
    }

    private static int cooldownTicks(PlayerPerkData data, int tier) {
        int ticks = ModPerks.LOW_COST_MOVEMENT_DEVICE.cooldownTicks(tier);
        if (data.isAddonActive(ModAddons.BOX_OPENER)) {
            ticks += ModAddons.BOX_OPENER_EXTRA_COOLDOWN_SECONDS * 20;
        }
        return ticks;
    }

    // --- per-player upkeep ---

    /** Hands charges back when they are due, and settles any ram still in progress. */
    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        int tier = data.getActiveTier(ModPerks.LOW_COST_MOVEMENT_DEVICE);
        if (tier <= 0) {
            return;
        }

        int max = maxCharges(data, tier);
        int charges = data.deviceCharges(max);
        boolean geared = data.isAddonActive(ModAddons.GEAR_SYSTEM);
        boolean changed = false;

        // Gear System has a timer running whenever the device is short of a charge. On its own the
        // device waits until it has run dry — there is no cooldown between charges, only after the
        // last one. Starting it here rather than when firing also picks up a device left stranded
        // by a relog or by an addon fitted part way through a set.
        if (charges < max && data.deviceRechargeAt() <= 0 && (geared || charges <= 0)) {
            data.setDeviceRechargeAt(gameTime + rechargeTicks(data, tier));
            changed = true;
        }

        if (charges < max && data.deviceRechargeAt() > 0 && gameTime >= data.deviceRechargeAt()) {
            // Gear System hands one back and queues the next; on its own the device refills whole.
            charges = geared ? charges + 1 : max;
            data.setDeviceCharges(charges);
            data.setDeviceRechargeAt(charges < max && geared ? gameTime + rechargeTicks(data, tier) : 0L);
            changed = true;
        }

        // Rewritten from the charge count every tick rather than only when firing: an empty device
        // holds its key until the next charge lands, a device with anything left never does, and
        // no stale cooldown can outlive the charge that caused it.
        int gate = charges <= 0 ? (int) Math.max(1L, data.deviceRechargeAt() - gameTime) : 0;
        if (data.getCooldownRemaining(ModPerks.LOW_COST_MOVEMENT_DEVICE.id(), gameTime) != gate) {
            data.setCooldown(ModPerks.LOW_COST_MOVEMENT_DEVICE.id(), gameTime, gate);
            changed = true;
        }

        // Sent on every change, and once a second regardless: the HUD has to fill in when the perk
        // is first equipped or after a relog, neither of which changes anything here.
        if (changed || gameTime % 20L == 0L) {
            sendCharges(player, data, max, charges);
        }
        if (changed) {
            PerkDataManager.sync(player);
        }

        if (data.isDeviceRamming(gameTime)) {
            tickRam(player, data, gameTime);
        }
    }

    /** Box Opener: whatever the player crashes into while flying takes the speed on the chin. */
    private static void tickRam(ServerPlayer player, PlayerPerkData data, long gameTime) {
        double speed = player.getDeltaMovement().length();
        AABB reach = player.getBoundingBox().inflate(0.2D);

        for (LivingEntity victim : player.serverLevel().getEntitiesOfClass(LivingEntity.class, reach)) {
            if (victim == player || !victim.isAlive()) {
                continue;
            }
            float damage = (float) Math.min(ModAddons.BOX_OPENER_MAX_DAMAGE,
                    speed * ModAddons.BOX_OPENER_DAMAGE_PER_SPEED);
            if (damage <= 0.0F) {
                continue;
            }
            victim.hurt(player.damageSources().playerAttack(player), damage);
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
                    SoundSource.PLAYERS, 0.9F, 0.8F);
            // One ram per launch: the window closes on the first thing hit.
            data.setDeviceRammingUntil(0L);
            return;
        }
    }

    // --- the shots themselves ---

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
        if (--shot.ticksLeft <= 0) {
            return false;
        }
        shot.velocity = shot.velocity.subtract(0.0D, shot.gravity, 0.0D);
        Vec3 step = shot.velocity.scale(1.0D / SUB_STEPS);

        for (int i = 0; i < SUB_STEPS; i++) {
            Vec3 from = shot.position;
            Vec3 to = from.add(step);

            Impact impact = collision(level, from, to);
            if (impact != null) {
                shot.position = impact.position();
                return onImpact(level, shot, impact.face());
            }

            // A shot flies as far as it likes: nothing but a block, or the flight cap, stops it.
            shot.position = to;
        }

        level.sendParticles(ParticleTypes.END_ROD, shot.position.x, shot.position.y, shot.position.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        return true;
    }

    /** Where a step ran into a block, and which way that block's surface faces. */
    private record Impact(Vec3 position, Direction face) {}

    /**
     * The block this step ran into, or null if it is clear. The centre line is traced first; the
     * radius is then checked against the block shapes, so a shot can clip a corner it would
     * otherwise have slipped past.
     * <p>
     * The position handed back always sits just <em>outside</em> the surface. Leaving it where the
     * step ended would bury the shot inside the block, and the next step would report a second
     * impact in the same spot rather than letting a bounce carry it away.
     */
    private static Impact collision(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 travel = to.subtract(from);
        if (travel.lengthSqr() < 1.0E-12D) {
            return null;
        }

        double radius = ModPerks.DEVICE_PROJECTILE_RADIUS;
        Vec3 unit = travel.normalize();
        // Two axes across the line of travel, to hang the extra rays off at the shot's radius.
        Vec3 side = Math.abs(unit.y) > 0.99D
                ? new Vec3(1.0D, 0.0D, 0.0D)
                : unit.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
        Vec3 lift = unit.cross(side).normalize();
        Vec3[] offsets = {
                Vec3.ZERO,
                side.scale(radius), side.scale(-radius),
                lift.scale(radius), lift.scale(-radius) };

        Impact nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        // Every ray is a real trace, so the face always comes from the block that was actually
        // struck. Guessing it from the direction of travel picked the wrong axis on anything but a
        // head-on hit, and a bounce off the wrong axis drove the shot into the wall instead of away
        // from it.
        for (Vec3 offset : offsets) {
            Vec3 start = from.add(offset);
            BlockHitResult hit = level.clip(new ClipContext(start, to.add(offset),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (hit.getType() == HitResult.Type.MISS) {
                continue;
            }
            double distance = hit.getLocation().distanceToSqr(start);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                Vec3 clearance = Vec3.atLowerCornerOf(hit.getDirection().getNormal())
                        .scale(radius + 0.01D);
                nearest = new Impact(hit.getLocation().subtract(offset).add(clearance), hit.getDirection());
            }
        }
        return nearest;
    }

    /** @return false when the shot is done, true when it bounces on */
    private static boolean onImpact(ServerLevel level, Shot shot, Direction face) {
        level.sendParticles(ParticleTypes.END_ROD, shot.position.x, shot.position.y, shot.position.z,
                12, 0.15D, 0.15D, 0.15D, 0.05D);
        level.playSound(null, shot.position.x, shot.position.y, shot.position.z,
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.4F);

        if (level.getServer().getPlayerList().getPlayer(shot.ownerId) instanceof ServerPlayer owner) {
            applyImpulse(owner, shot.position, shot.tier);
        }

        if (shot.bouncesLeft > 0) {
            shot.bouncesLeft--;
            // A bounce is a short hop, not a second full flight: the shot comes off the block
            // slower and on a short leash, so the second throw lands somewhere the player can
            // still use.
            shot.velocity = reflect(shot.velocity, face).scale(ModAddons.LOOSE_SCREW_BOUNCE_SPEED);
            clearOf(shot, face);
            return true;
        }

        if (shot.bankBounces > 0) {
            // Bank Shot's bounce keeps the shot's own speed and looks for a mark on the way out.
            shot.bankBounces--;
            shot.velocity = BankShotHandler.reflect(shot.velocity, face)
                    .scale(ModPerks.BANK_SHOT_BOUNCE_SPEED);
            clearOf(shot, face);

            LivingEntity mark = BankShotHandler.seek(level, shot.position, shot.velocity,
                    level.getServer().getPlayerList().getPlayer(shot.ownerId));
            if (mark != null) {
                shot.velocity = BankShotHandler.steer(shot.position, shot.velocity, mark);
            }
            return true;
        }

        return false;
    }

    /**
     * Stands the shot a full radius clear of the face it struck. A bounce sheds speed, so a step no
     * longer covers that on its own, and a shot left grazing the block would report a second impact
     * on the spot instead of flying away from it.
     */
    private static void clearOf(Shot shot, Direction face) {
        shot.position = shot.position.add(Vec3.atLowerCornerOf(face.getNormal())
                .scale(ModPerks.DEVICE_PROJECTILE_RADIUS + 0.02D));
    }

    private static Vec3 reflect(Vec3 velocity, Direction face) {
        return switch (face.getAxis()) {
            case X -> new Vec3(-velocity.x, velocity.y, velocity.z);
            case Y -> new Vec3(velocity.x, -velocity.y, velocity.z);
            case Z -> new Vec3(velocity.x, velocity.y, -velocity.z);
        };
    }

    /**
     * Throws the player at the point of impact. The horizontal pull grows with distance up to the
     * tier's ceiling; the vertical part is a flat lift, with more on top when the shot landed
     * overhead. A shot below the player never drags them down — the lift still applies.
     */
    private static void applyImpulse(ServerPlayer player, Vec3 impact, int tier) {
        PlayerPerkData data = PerkDataManager.get(player);
        double multiplier = impulseMultiplier(data);

        // Measured from the eyes rather than the feet: a shot at a wall straight ahead is level,
        // and reading it as a metre and a half of climb is what used to launch the player skyward.
        Vec3 delta = impact.subtract(player.getEyePosition());
        double reach = Math.max(1.0E-4D, delta.length());
        Vec3 direction = delta.scale(1.0D / reach);

        // Thrown at the impact at full strength, whatever the distance. Scaling the pull with how
        // far away the shot landed made a close hit a shove rather than a launch, which is what
        // turned the whole thing into a hop.
        double ceiling = ModPerks.LOW_COST_MOVEMENT_DEVICE.value(ModPerks.DEVICE_HORIZONTAL_MAX, tier)
                * multiplier;
        double vx = direction.x * ceiling;
        double vz = direction.z * ceiling;

        // The lift is earned by aiming up. A flat shot keeps just enough to clear the ground, so
        // the throw carries the player into the target instead of over it; a shot at the ceiling
        // gets the tier's full figure and the overhead bonus on top.
        double upwards = Math.clamp(direction.y, 0.0D, 1.0D);
        double lift = ModPerks.DEVICE_LEVEL_LIFT + (1.0D - ModPerks.DEVICE_LEVEL_LIFT) * upwards;

        double vy = ModPerks.LOW_COST_MOVEMENT_DEVICE.value(ModPerks.DEVICE_VERTICAL_BASE, tier)
                * lift * multiplier;
        if (delta.y > ModPerks.DEVICE_OVERHEAD_MARGIN) {
            vy += Math.min(ModPerks.DEVICE_OVERHEAD_MAX, delta.y * ModPerks.DEVICE_OVERHEAD_PER_BLOCK)
                    * multiplier;
        }
        // Standing on the ground, friction would eat a flat launch before it moved the player, so
        // the throw always lifts enough to get them off it first.
        if (player.onGround()) {
            vy = Math.max(vy, ModPerks.DEVICE_GROUND_CLEARANCE);
        }

        player.setDeltaMovement(vx, vy, vz);
        player.fallDistance = 0.0F;
        // Without this the server keeps the velocity to itself and the player never moves.
        player.hurtMarked = true;

        if (data.isAddonActive(ModAddons.BOX_OPENER)) {
            data.setDeviceRammingUntil(player.level().getGameTime() + ModAddons.BOX_OPENER_IMPACT_TICKS);
        }
    }

    private static double impulseMultiplier(PlayerPerkData data) {
        if (data.isAddonActive(ModAddons.BOX_OPENER)) {
            return ModAddons.BOX_OPENER_IMPULSE;
        }
        if (data.isAddonActive(ModAddons.OVERCLOCKED_MODULE)) {
            return ModAddons.OVERCLOCKED_MODULE_IMPULSE;
        }
        return 1.0D;
    }

    /** Tells the client what the device has left, since charges never travel on the state sync. */
    private static void sendCharges(ServerPlayer player, PlayerPerkData data, int max, int charges) {
        PacketDistributor.sendToPlayer(player, new PerkChargesPayload(ModPerks.LOW_COST_MOVEMENT_DEVICE.id(),
                charges, max, charges < max ? data.deviceRechargeAt() : 0L));
    }

    /** Drops anything still in the air for a player who has left. */
    public static void clear(UUID playerId) {
        IN_FLIGHT.removeIf(shot -> shot.ownerId.equals(playerId));
    }
}
