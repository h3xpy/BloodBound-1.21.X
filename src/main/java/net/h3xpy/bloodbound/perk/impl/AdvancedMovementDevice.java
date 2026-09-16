package net.h3xpy.bloodbound.perk.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.event.BankShotHandler;
import net.h3xpy.bloodbound.event.PerkEventHandler;
import net.h3xpy.bloodbound.network.PerkChargesPayload;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Advanced Movement Device: a grapple that pays its way by the block.
 * <p>
 * The shot is not an entity — like the mod's other projectiles it is simulated here and drawn with
 * particles. What makes this one different is the meter: every block it covers is charges off a
 * reserve that fills on its own, and the moment either the shot's own budget or the reserve runs
 * out, the player is where the shot is. Missing is what it costs, not a cooldown.
 */
public final class AdvancedMovementDevice {

    /** One shot in the air. */
    private static final class Shot {
        private final UUID ownerId;
        private final ResourceKey<Level> dimension;
        private final double speed;
        /** Charges this shot may spend before it drops the player wherever it got to. */
        private final double budget;
        private Vec3 position;
        private Vec3 velocity;
        private double spent;
        private int ticksLeft = ModPerks.AMD_MAX_FLIGHT_TICKS;
        private int bouncesLeft;
        /** A bounce bought from Bank Shot, spent after the device's own and aimed on the way out. */
        private int bankBounces;

        private Shot(ServerPlayer owner, Vec3 position, Vec3 velocity, double speed, double budget,
                int bouncesLeft) {
            this.ownerId = owner.getUUID();
            this.dimension = owner.level().dimension();
            this.position = position;
            this.velocity = velocity;
            this.speed = speed;
            this.budget = budget;
            this.bouncesLeft = bouncesLeft;
        }
    }

    /** How many collision checks each tick of flight is broken into. */
    private static final int SUB_STEPS = 4;

    private static final List<Shot> IN_FLIGHT = new ArrayList<>();

    private AdvancedMovementDevice() {}

    // --- firing ---

    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        // One shot at a time. The key repeats while it is held, so without this a hold empties the
        // whole reserve into a stream of shots.
        if (hasShot(player.getUUID())) {
            return false;
        }

        float max = maxCharges(data, tier);
        float charges = data.amdCharges(max);
        if (charges < ModPerks.AMD_MIN_CHARGES) {
            player.displayClientMessage(Component.translatable("bloodbound.message.amd_too_low",
                    (int) ModPerks.AMD_MIN_CHARGES).withStyle(ChatFormatting.DARK_GRAY), true);
            return false;
        }

        double speed = shotSpeed(data, tier);
        double budget = shotBudget(data, tier);
        Shot shot = new Shot(player, player.getEyePosition(),
                player.getLookAngle().scale(speed), speed, budget,
                data.isAddonActive(ModAddons.FIELD_RECORDER) ? 0 : ModPerks.AMD_BOUNCES);
        shot.bankBounces = BankShotHandler.spendBounce(player) ? ModPerks.BANK_SHOT_BOUNCES : 0;
        IN_FLIGHT.add(shot);

        player.level().playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_SHOOT,
                SoundSource.PLAYERS, 0.6F, 1.3F);
        return true;
    }

    /** Tension Spring: the key going up is the order to arrive. */
    public static void onKeyReleased(ServerPlayer player, PlayerPerkData data) {
        if (data.isAddonActive(ModAddons.TENSION_SPRING) && hasShot(player.getUUID())) {
            recall(player.serverLevel(), player.getUUID());
            data.setCooldown(ModPerks.ADVANCED_MOVEMENT_DEVICE.id(), player.level().getGameTime(),
                    ModAddons.TENSION_SPRING_COOLDOWN_TICKS);
            PerkDataManager.sync(player);
        }
    }

    private static boolean hasShot(UUID ownerId) {
        return IN_FLIGHT.stream().anyMatch(shot -> shot.ownerId.equals(ownerId));
    }

    /** Brings a player to wherever their shot has got to, and drops the shot. */
    private static void recall(ServerLevel level, UUID ownerId) {
        Iterator<Shot> iterator = IN_FLIGHT.iterator();
        while (iterator.hasNext()) {
            Shot shot = iterator.next();
            if (shot.ownerId.equals(ownerId)) {
                arrive(level, shot);
                iterator.remove();
            }
        }
    }

    // --- the reserve ---

    /** Fills the reserve back up and keeps the HUD honest. Called once a tick per player. */
    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        int tier = data.getActiveTier(ModPerks.ADVANCED_MOVEMENT_DEVICE);
        if (tier <= 0) {
            return;
        }

        float max = maxCharges(data, tier);
        float charges = data.amdCharges(max);
        if (charges < max) {
            double rate = regenPerSecond(data) * (1.0D + PerkEventHandler.lightbringerRate(player));
            charges = Math.min(max, charges + (float) (rate / 20.0D));
            data.setAmdCharges(charges);
        }

        // Sent every tick while filling, and once a second otherwise: the reserve is a number the
        // player watches, and it moves every tick.
        if (charges < max || gameTime % 20L == 0L) {
            sendCharges(player, max, charges);
        }
    }

    private static float maxCharges(PlayerPerkData data, int tier) {
        double max = ModPerks.ADVANCED_MOVEMENT_DEVICE.value(ModPerks.AMD_MAX_CHARGES, tier);
        if (data.isAddonActive(ModAddons.SCRAPS)) {
            max *= ModAddons.SCRAPS_CHARGES;
        }
        if (data.isAddonActive(ModAddons.PRIMER_BULB)) {
            max *= ModAddons.PRIMER_BULB_CHARGES;
        }
        return (float) max;
    }

    private static double regenPerSecond(PlayerPerkData data) {
        double rate = ModPerks.AMD_REGEN_PER_SECOND;
        if (data.isAddonActive(ModAddons.WIRE_SPOOL)) {
            rate *= ModAddons.WIRE_SPOOL_REGEN;
        }
        if (data.isAddonActive(ModAddons.PRIMER_BULB)) {
            rate *= ModAddons.PRIMER_BULB_REGEN;
        }
        return rate;
    }

    private static double shotSpeed(PlayerPerkData data, int tier) {
        double speed = ModPerks.ADVANCED_MOVEMENT_DEVICE.value(ModPerks.AMD_SPEED, tier);
        return data.isAddonActive(ModAddons.SCRAPS) ? speed * ModAddons.SCRAPS_SPEED : speed;
    }

    private static double shotBudget(PlayerPerkData data, int tier) {
        double budget = ModPerks.ADVANCED_MOVEMENT_DEVICE.value(ModPerks.AMD_SHOT_BUDGET, tier);
        return data.isAddonActive(ModAddons.SCRAPS) ? budget * ModAddons.SCRAPS_CHARGES : budget;
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

    /** @return false once the shot is done and should be dropped */
    private static boolean advance(ServerLevel level, Shot shot) {
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(shot.ownerId);
        if (owner == null || --shot.ticksLeft <= 0) {
            return false;
        }

        PlayerPerkData data = PerkDataManager.get(owner);
        int tier = data.getActiveTier(ModPerks.ADVANCED_MOVEMENT_DEVICE);
        if (tier <= 0) {
            return false;
        }

        // Field Recorder: the shot keeps turning onto the line the player is looking down, which is
        // what lets it be steered round a corner rather than only aimed through one.
        if (data.isAddonActive(ModAddons.FIELD_RECORDER)) {
            Vec3 wanted = owner.getLookAngle().scale(shot.speed);
            shot.velocity = shot.velocity.add(wanted.subtract(shot.velocity)
                    .scale(ModAddons.FIELD_RECORDER_DRIFT));
        }

        Vec3 step = shot.velocity.scale(1.0D / SUB_STEPS);
        for (int i = 0; i < SUB_STEPS; i++) {
            Vec3 from = shot.position;
            Vec3 to = from.add(step);

            if (!spend(owner, data, tier, shot, from.distanceTo(to))) {
                // Out of charges, one way or the other: the shot stops here and takes the player.
                shot.position = to;
                arrive(level, shot);
                return false;
            }

            Impact impact = collision(level, from, to);
            if (impact != null) {
                shot.position = impact.position();
                if (shot.bouncesLeft > 0) {
                    shot.bouncesLeft--;
                    shot.velocity = reflect(shot.velocity, impact.face());
                    clearOf(shot, impact.face());
                    level.playSound(null, shot.position.x, shot.position.y, shot.position.z,
                            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.6F);
                    break;
                }
                if (shot.bankBounces > 0) {
                    // Bank Shot: one more carom, looking for something to fly at on the way out.
                    shot.bankBounces--;
                    shot.velocity = reflect(shot.velocity, impact.face());
                    clearOf(shot, impact.face());
                    LivingEntity mark = BankShotHandler.seek(level, shot.position, shot.velocity, owner);
                    if (mark != null) {
                        shot.velocity = BankShotHandler.steer(shot.position, shot.velocity, mark);
                    }
                    level.playSound(null, shot.position.x, shot.position.y, shot.position.z,
                            SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.6F, 1.6F);
                    break;
                }
                // Second wall, or the first for a Field Recorder: this is the stop.
                arrive(level, shot);
                return false;
            }

            shot.position = to;
        }

        level.sendParticles(ParticleTypes.END_ROD, shot.position.x, shot.position.y, shot.position.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        // Once the tick is done rather than once per sub-step: the reserve is drained four times a
        // tick, but the player only ever reads it once.
        sendCharges(owner, maxCharges(data, tier), data.amdCharges(maxCharges(data, tier)));
        return true;
    }

    /**
     * Bills one step of flight to both meters: the shot's own budget and the player's reserve.
     *
     * @return false when either has run out, which is what ends the flight
     */
    private static boolean spend(ServerPlayer owner, PlayerPerkData data, int tier, Shot shot,
            double distance) {
        double cost = distance * ModPerks.AMD_COST_PER_BLOCK;
        if (data.isAddonActive(ModAddons.FIELD_RECORDER)) {
            cost *= ModAddons.FIELD_RECORDER_COST;
        }
        float max = maxCharges(data, tier);
        float charges = data.amdCharges(max);

        shot.spent += cost;
        data.setAmdCharges((float) (charges - cost));

        return shot.spent < shot.budget && data.amdCharges(max) > 0.0F;
    }

    /** Drops the player at the shot. */
    private static void arrive(ServerLevel level, Shot shot) {
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(shot.ownerId);
        if (owner == null) {
            return;
        }

        level.sendParticles(ParticleTypes.END_ROD, shot.position.x, shot.position.y, shot.position.z,
                30, 0.25D, 0.25D, 0.25D, 0.05D);
        owner.teleportTo(shot.position.x, shot.position.y, shot.position.z);
        owner.setDeltaMovement(Vec3.ZERO);
        owner.resetFallDistance();
        owner.hurtMarked = true;

        level.playSound(null, owner.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.7F, 1.4F);

        // Primer Bulb: arriving patches you up, by however much it happens to have in it.
        PlayerPerkData data = PerkDataManager.get(owner);
        if (data.isAddonActive(ModAddons.PRIMER_BULB)) {
            int heal = owner.getRandom().nextInt(ModAddons.PRIMER_BULB_MAX_HEAL + 1);
            if (heal > 0) {
                owner.heal(heal);
            }
        }
    }

    // --- collision, shared in shape with the other devices ---

    /** Where a step ran into a block, and which way that block's surface faces. */
    private record Impact(Vec3 position, Direction face) {}

    /**
     * The block this step ran into, or null if it is clear. Five real traces — the centre line and
     * four hung off it at the shot's radius — so the face always comes from the block that was
     * actually struck rather than being guessed from the heading.
     */
    private static Impact collision(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 travel = to.subtract(from);
        if (travel.lengthSqr() < 1.0E-12D) {
            return null;
        }

        double radius = ModPerks.AMD_RADIUS;
        Vec3 unit = travel.normalize();
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
                Vec3 clearance = Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(radius + 0.01D);
                nearest = new Impact(hit.getLocation().subtract(offset).add(clearance), hit.getDirection());
            }
        }
        return nearest;
    }

    private static void clearOf(Shot shot, Direction face) {
        shot.position = shot.position.add(Vec3.atLowerCornerOf(face.getNormal())
                .scale(ModPerks.AMD_RADIUS + 0.02D));
    }

    private static Vec3 reflect(Vec3 velocity, Direction face) {
        return switch (face.getAxis()) {
            case X -> new Vec3(-velocity.x, velocity.y, velocity.z);
            case Y -> new Vec3(velocity.x, -velocity.y, velocity.z);
            case Z -> new Vec3(velocity.x, velocity.y, -velocity.z);
        };
    }

    /** Tells the client what is in the reserve. Charges never travel on the usual state sync. */
    private static void sendCharges(ServerPlayer player, float max, float charges) {
        PacketDistributor.sendToPlayer(player, new PerkChargesPayload(
                ModPerks.ADVANCED_MOVEMENT_DEVICE.id(), Math.round(charges), Math.round(max), 0L));
    }

    /** Drops anything still in the air for a player who has left. */
    public static void clear(UUID playerId) {
        IN_FLIGHT.removeIf(shot -> shot.ownerId.equals(playerId));
    }
}
