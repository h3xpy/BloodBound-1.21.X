package net.h3xpy.bloodbound.perk.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.mixin.LivingEntityAccessor;
import net.h3xpy.bloodbound.network.UndersizedRidePayload;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.level.ExplosionKnockbackEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Undersized: the bearer is shrunk, model and hitbox alike, through the scale attribute, which the
 * game already applies to both.
 * <p>
 * From Tier II the key climbs onto the shoulders of a crouched player nearby; at Tier III onto any
 * creature, which the bearer then steers like a horse. That steering is vanilla's own: the creature
 * names its rider as the one in control (see the riding mixins), so it is driven from the rider's
 * client, as a horse is. Jumping or crouching gets back down, and the mount cannot be struck from
 * its back.
 */
public final class Undersized {

    private static final ResourceLocation SCALE_ID = ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "undersized");

    /** Players up on someone or something through the perk, on the server. */
    private static final Set<UUID> RIDING = new HashSet<>();
    /**
     * Riders who have let go of jump and crouch since climbing on. Until then neither gets them down:
     * a key still held from before the climb, or a stale jump the server last heard of, would
     * otherwise put them straight back on the ground.
     */
    private static final Set<UUID> ARMED = new HashSet<>();
    /** When each rider climbed on. */
    private static final Map<UUID, Long> MOUNTED_AT = new HashMap<>();
    /**
     * The server only hears a rider's keys once they are on, a tick or two later; until then it still
     * believes whatever it last heard. Letting go only counts after this long.
     */
    private static final int ARM_DELAY_TICKS = 5;
    /** What each rider has told their mount to go after. */
    private static final Map<UUID, UUID> ORDERS = new HashMap<>();
    /** Cruising speed of a flyer that is not a flying mob proper, in the air. */
    private static final float FLYER_SPEED = 0.25F;
    /** How hard a swimmer pushes through water, per point of its own speed. */
    private static final double SWIM_BOOST = 0.12D;

    /** Whether a player is up on a creature through the perk, as their own client knows it. Set by the client. */
    public static Predicate<Player> clientRiding = player -> false;

    private Undersized() {}

    // --- climbing on ---

    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        // Up on a creature, the key points it at what the rider looks at.
        if (RIDING.contains(player.getUUID()) && player.getVehicle() instanceof Mob) {
            command(player);
            return false;
        }
        if (tier < ModPerks.UNDERSIZED_SHOULDER_TIER || player.isPassenger()) {
            return false;
        }
        Entity mount = findMount(player, tier);
        if (mount == null || !player.startRiding(mount, true)) {
            return false;
        }
        RIDING.add(player.getUUID());
        ARMED.remove(player.getUUID());
        MOUNTED_AT.put(player.getUUID(), player.level().getGameTime());
        PacketDistributor.sendToPlayer(player, new UndersizedRidePayload(true));
        if (mount instanceof ServerPlayer carrier) {
            // A player is never told about their own passengers, and would see this one left behind.
            carrier.connection.send(new ClientboundSetPassengersPacket(carrier));
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                SoundSource.PLAYERS, 0.8F, 1.4F);
        return false;
    }

    /** A crouched player first; failing that, at Tier III, the nearest creature. */
    @Nullable
    private static Entity findMount(ServerPlayer player, int tier) {
        double range = ModPerks.UNDERSIZED_RANGE;
        Player shoulders = null;
        double best = range * range;
        for (Player other : player.level().getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(range))) {
            double distance = other.distanceToSqr(player);
            if (other != player && other.isAlive() && !other.isSpectator() && other.isCrouching()
                    && !other.isVehicle() && distance <= best) {
                shoulders = other;
                best = distance;
            }
        }
        if (shoulders != null || tier < ModPerks.UNDERSIZED_MOUNT_TIER) {
            return shoulders;
        }

        Mob creature = null;
        best = range * range;
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(range))) {
            double distance = mob.distanceToSqr(player);
            if (mob.isAlive() && !mob.isVehicle() && !mob.isPassenger() && !(mob instanceof EnderDragon)
                    && distance <= best) {
                creature = mob;
                best = distance;
            }
        }
        return creature;
    }

    // --- riding ---

    /** Whether crouching must not get this rider down yet: they have not let go of it since climbing on. */
    public static boolean holdsOn(Player player) {
        return RIDING.contains(player.getUUID()) && !ARMED.contains(player.getUUID());
    }

    /** Whether this passenger steers this vehicle through the perk, on either side. */
    public static boolean controls(Entity vehicle, @Nullable Entity passenger) {
        if (!(passenger instanceof Player player) || vehicle instanceof Player || player.getVehicle() != vehicle) {
            return false;
        }
        return player.level().isClientSide() ? clientRiding.test(player) : RIDING.contains(player.getUUID());
    }

    /**
     * What the rider's keys ask of the mount: sideways at half speed, backwards at a quarter, as a
     * horse. A flyer, or a swimmer in water, also climbs and dives where the rider looks.
     */
    public static Vec3 riddenInput(LivingEntity mount, Player player) {
        float strafe = player.xxa * 0.5F;
        float forward = player.zza <= 0.0F ? player.zza * 0.25F : player.zza;
        if (isFlyer(mount) || (isSwimmer(mount) && mount.isInWater())) {
            float pitch = player.getXRot() * Mth.DEG_TO_RAD;
            return new Vec3(strafe, -Mth.sin(pitch) * forward, Mth.cos(pitch) * forward);
        }
        return new Vec3(strafe, 0.0D, forward);
    }

    /**
     * The mount's own walking pace. A creature walks with its speed both as its push and as how hard
     * it pushes, so its pace goes with the square of it; the rider's keys push at full, so the square
     * is what keeps a cow a cow and a spider a spider. Flyers that are not flying mobs proper get a
     * steady cruising speed, their own figures being made for a different sum.
     */
    public static float riddenSpeed(LivingEntity mount) {
        if (isFlyer(mount) && !mount.onGround()) {
            return FLYER_SPEED;
        }
        AttributeInstance speed = mount.getAttribute(Attributes.MOVEMENT_SPEED);
        double value = speed == null ? 0.2D : speed.getValue();
        return (float) (value * value);
    }

    /**
     * The mount faces where its rider looks, and keeps what it can do. This runs where the mount is
     * driven, the rider's client, which is where its physics are worked out while it is ridden.
     */
    public static void tickRidden(LivingEntity mount, Player player) {
        mount.setYRot(player.getYRot());
        mount.setXRot(player.getXRot() * 0.5F);
        mount.yRotO = mount.getYRot();
        mount.yBodyRot = mount.getYRot();
        mount.yHeadRot = mount.getYRot();

        if (!mount.level().isClientSide()) {
            return;
        }
        // A spider climbs whatever wall it walks into; the server works that out, but is not moving it now.
        if (mount instanceof Spider spider) {
            spider.setClimbing(spider.horizontalCollision);
        }
        // Flyers stay up: the server keeps them there with its own steering, which is not in play.
        if (isFlyer(mount) && !(mount instanceof FlyingMob)) {
            mount.setNoGravity(true);
        }
        // Swimmers are quick in water, as quick as their own speed.
        if (isSwimmer(mount) && mount.isInWater() && player.zza > 0.0F) {
            AttributeInstance speed = mount.getAttribute(Attributes.MOVEMENT_SPEED);
            double push = (speed == null ? 0.2D : speed.getValue()) * SWIM_BOOST * player.zza;
            mount.setDeltaMovement(mount.getDeltaMovement().add(player.getLookAngle().scale(push)));
        }
    }

    /** Creatures that fly rather than walk. */
    private static boolean isFlyer(LivingEntity mount) {
        return mount instanceof FlyingMob || mount instanceof FlyingAnimal || mount instanceof Bat
                || mount instanceof Vex || mount instanceof Blaze || mount instanceof Allay || mount instanceof WitherBoss;
    }

    /** Creatures at home in water. */
    private static boolean isSwimmer(LivingEntity mount) {
        return mount instanceof WaterAnimal || mount instanceof Drowned || mount instanceof Guardian
                || mount instanceof Axolotl || mount instanceof Turtle || mount instanceof Frog;
    }

    // --- the mount fights for its rider ---

    /** The key, while riding: the mount goes after what the rider looks at, or calms down if nothing. */
    private static void command(ServerPlayer player) {
        Vec3 eyes = player.getEyePosition();
        Vec3 end = eyes.add(player.getLookAngle().scale(ModPerks.UNDERSIZED_COMMAND_RANGE));
        BlockHitResult wall = player.level().clip(new ClipContext(eyes, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        if (wall.getType() != HitResult.Type.MISS) {
            end = wall.getLocation();
        }
        Entity vehicle = player.getVehicle();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eyes, end,
                player.getBoundingBox().expandTowards(end.subtract(eyes)).inflate(1.0D),
                entity -> entity instanceof LivingEntity && entity != vehicle && !entity.isSpectator(),
                eyes.distanceToSqr(end));
        LivingEntity target = hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
        setOrder(player, target);
        player.displayClientMessage(target == null
                ? Component.translatable("bloodbound.message.undersized_calm")
                : Component.translatable("bloodbound.message.undersized_attack", target.getName()), true);
    }

    private static void setOrder(ServerPlayer player, @Nullable LivingEntity target) {
        if (target == null) {
            ORDERS.remove(player.getUUID());
        } else {
            ORDERS.put(player.getUUID(), target.getUUID());
        }
    }

    /**
     * Holds the mount to its rider's order. Every tick, so its own instincts (going for the nearest
     * player, the rider included) never win: it attacks what it was told to, with its own attacks,
     * and nothing else.
     */
    private static void enforceOrder(ServerPlayer player, Mob mount) {
        UUID targetId = ORDERS.get(player.getUUID());
        Entity found = targetId == null ? null : player.serverLevel().getEntity(targetId);
        LivingEntity living = found instanceof LivingEntity alive ? alive : null;
        if (living == null || !living.isAlive() || living == player
                || (living instanceof Player other && (other.isCreative() || other.isSpectator()))) {
            ORDERS.remove(player.getUUID());
            living = null;
        }
        if (mount.getTarget() != living) {
            mount.setTarget(living);
        }
    }

    /** Keeps the size right, and gets the rider down when they jump. Called once a tick per player. */
    public static void tick(ServerPlayer player, PlayerPerkData data) {
        int tier = data.getActiveTier(ModPerks.UNDERSIZED);
        applyScale(player, tier > 0 ? ModPerks.UNDERSIZED.value(ModPerks.UNDERSIZED_SHRINK, tier) / 100.0D : 0.0D);

        if (!RIDING.contains(player.getUUID())) {
            return;
        }
        Entity vehicle = player.getVehicle();
        int needed = vehicle instanceof Player ? ModPerks.UNDERSIZED_SHOULDER_TIER : ModPerks.UNDERSIZED_MOUNT_TIER;
        boolean jumping = ((LivingEntityAccessor) player).bloodbound$isJumping();
        long onFor = player.level().getGameTime() - MOUNTED_AT.getOrDefault(player.getUUID(), 0L);
        if (!ARMED.contains(player.getUUID()) && onFor >= ARM_DELAY_TICKS && !jumping && !player.isShiftKeyDown()) {
            ARMED.add(player.getUUID());
        }
        if (vehicle == null || tier < needed || (jumping && ARMED.contains(player.getUUID()))) {
            player.stopRiding();
            return;
        }
        if (vehicle instanceof Mob mob) {
            enforceOrder(player, mob);
        }
    }

    private static void applyScale(ServerPlayer player, double shrink) {
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        if (scale == null) {
            return;
        }
        AttributeModifier existing = scale.getModifier(SCALE_ID);
        if (shrink <= 0.0D) {
            if (existing != null) {
                scale.removeModifier(SCALE_ID);
            }
            return;
        }
        if (existing == null || existing.amount() != -shrink) {
            scale.removeModifier(SCALE_ID);
            scale.addTransientModifier(new AttributeModifier(SCALE_ID, -shrink, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    // --- getting down, and the mount's safety ---

    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (!event.isDismounting() || event.getLevel().isClientSide()
                || !(event.getEntityMounting() instanceof ServerPlayer player) || !RIDING.remove(player.getUUID())) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new UndersizedRidePayload(false));
        ORDERS.remove(player.getUUID());
        if (event.getEntityBeingMounted() instanceof Mob mob) {
            // Let go, it does not carry on with a fight it was only in for its rider.
            mob.setTarget(null);
        }
        if (event.getEntityBeingMounted() instanceof ServerPlayer carrier) {
            // Sent once the passenger is really gone, which is after this event.
            carrier.getServer().execute(() -> carrier.connection.send(new ClientboundSetPassengersPacket(carrier)));
        }
    }

    /** The mount cannot be struck from its back; anything else the rider hits, the mount goes after too. */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !RIDING.contains(player.getUUID())) {
            return;
        }
        if (event.getTarget() == player.getVehicle()) {
            event.setCanceled(true);
        } else if (player.getVehicle() instanceof Mob && event.getTarget() instanceof LivingEntity target) {
            setOrder(player, target);
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        Entity attacker = event.getSource().getEntity();
        // The rider cannot hurt their mount, by any means.
        if (attacker instanceof ServerPlayer player && RIDING.contains(player.getUUID())
                && event.getEntity() == player.getVehicle()) {
            event.setCanceled(true);
            return;
        }
        if (event.getEntity() instanceof ServerPlayer rider && RIDING.contains(rider.getUUID())) {
            Entity mount = rider.getVehicle();
            // Nor the mount its rider: a creeper's blast, a skeleton's arrow from under them.
            if (attacker != null && attacker == mount) {
                event.setCanceled(true);
                return;
            }
            // Whatever goes for the rider has the mount to deal with.
            if (mount instanceof Mob && attacker instanceof LivingEntity enemy && enemy != mount) {
                setOrder(rider, enemy);
            }
        }
        // Small hands: the bearer's own blows land softer.
        if (attacker instanceof ServerPlayer player && event.getSource().is(DamageTypes.PLAYER_ATTACK)
                && PerkDataManager.get(player).getActiveTier(ModPerks.UNDERSIZED) > 0) {
            event.setAmount(event.getAmount() * ModPerks.UNDERSIZED_MELEE_DEALT);
        }
    }

    /** A creeper going off under its rider does not throw them either: the fall would hurt. */
    @SubscribeEvent
    public static void onExplosionKnockback(ExplosionKnockbackEvent event) {
        if (event.getAffectedEntity() instanceof ServerPlayer rider && RIDING.contains(rider.getUUID())
                && event.getExplosion().getDirectSourceEntity() == rider.getVehicle()) {
            event.setKnockbackVelocity(Vec3.ZERO);
        }
    }

    public static void clear(UUID playerId) {
        ORDERS.remove(playerId);
        RIDING.remove(playerId);
        ARMED.remove(playerId);
        MOUNTED_AT.remove(playerId);
    }
}
