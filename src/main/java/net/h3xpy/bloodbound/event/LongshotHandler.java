package net.h3xpy.bloodbound.event;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.effect.BleedingHandler;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Longshot and its addons. Everything an arrow needs is stamped onto it the moment it is fired, so
 * the perk keeps working even if the shooter unequips it, dies or logs out mid-flight.
 */
public final class LongshotHandler {

    private static final String KEY_PER_BLOCK = "BloodBoundLongshot";
    private static final String KEY_ORIGIN_X = "BloodBoundOriginX";
    private static final String KEY_ORIGIN_Y = "BloodBoundOriginY";
    private static final String KEY_ORIGIN_Z = "BloodBoundOriginZ";
    private static final String KEY_HEADSHOT = "BloodBoundHeadshot";
    private static final String KEY_LIGHT = "BloodBoundLight";
    private static final String KEY_BOUNTY = "BloodBoundBounty";

    /** Odd Arrow: its heading, the axis it weaves across, its speed and how long it has flown. */
    private static final String KEY_ODD = "BloodBoundOdd";
    private static final String KEY_ODD_DIR_X = "BloodBoundOddDX";
    private static final String KEY_ODD_DIR_Y = "BloodBoundOddDY";
    private static final String KEY_ODD_DIR_Z = "BloodBoundOddDZ";
    private static final String KEY_ODD_SPEED = "BloodBoundOddSpeed";
    private static final String KEY_ODD_AGE = "BloodBoundOddAge";
    private static final String KEY_ODD_HITS = "BloodBoundOddHits";
    /** Cursed Riser: bends towards a target, and opens a wound on it. */
    private static final String KEY_CURSED = "BloodBoundCursed";
    /**
     * Bank Shot's tally of walls an arrow has come off. Read so an Odd Arrow re-takes its heading
     * after a bounce rather than weaving straight back into the wall it just left.
     */
    private static final String KEY_BANK_HITS = "BloodBoundBankHits";

    /**
     * Below this speed a no-gravity arrow gets its gravity back. Without it the arrow coasts to a
     * halt in mid-air and never lands, and vanilla only despawns arrows once they are in the ground.
     */
    private static final double GRAVITY_RESTORE_SPEED_SQR = 0.05D * 0.05D;

    /** Where each lit arrow currently has its light block, so nothing can be orphaned. */
    private record ArrowLight(ResourceKey<Level> dimension, BlockPos pos) {}

    private static final Map<UUID, ArrowLight> LIGHTS = new HashMap<>();

    private LongshotHandler() {}

    // --- stamping the arrow at launch ---

    @SubscribeEvent
    public static void onArrowSpawned(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof AbstractArrow arrow)) {
            return;
        }
        if (!(arrow.getOwner() instanceof ServerPlayer shooter) || arrow.tickCount > 0) {
            return;
        }

        PlayerPerkData data = PerkDataManager.get(shooter);
        int tier = data.getActiveTier(ModPerks.LONGSHOT);
        if (tier <= 0) {
            return;
        }

        double perBlock = ModPerks.LONGSHOT.value(ModPerks.LONGSHOT_PER_BLOCK, tier);
        boolean light = data.isAddonActive(ModAddons.TAPED_FLASHLIGHT);
        if (light) {
            perBlock += ModAddons.TAPED_FLASHLIGHT_BONUS_PER_BLOCK;
        }
        if (data.isAddonActive(ModAddons.GUNPOWDER)) {
            arrow.setNoGravity(true);
        }

        CompoundTag tag = arrow.getPersistentData();
        tag.putDouble(KEY_PER_BLOCK, perBlock);
        tag.putDouble(KEY_ORIGIN_X, arrow.getX());
        tag.putDouble(KEY_ORIGIN_Y, arrow.getY());
        tag.putDouble(KEY_ORIGIN_Z, arrow.getZ());
        tag.putBoolean(KEY_HEADSHOT, data.isAddonActive(ModAddons.POINT_BLANK));
        tag.putBoolean(KEY_LIGHT, light);
        tag.putBoolean(KEY_BOUNTY, data.isAddonActive(ModAddons.BOUNTY_POSTER));
        tag.putBoolean(KEY_CURSED, data.isAddonActive(ModAddons.CURSED_RISER));

        if (data.isAddonActive(ModAddons.ODD_ARROW)) {
            stampOdd(arrow, tag);
        }
    }

    /** Odd Arrow: no gravity, a slow start, and the line it is going to weave along. */
    private static void stampOdd(AbstractArrow arrow, CompoundTag tag) {
        Vec3 velocity = arrow.getDeltaMovement();
        double speed = velocity.length() * ModAddons.ODD_ARROW_START_SPEED;
        if (speed < 1.0E-3D) {
            return;
        }
        Vec3 direction = velocity.normalize();
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(direction.scale(speed));

        tag.putBoolean(KEY_ODD, true);
        putDirection(tag, direction);
        tag.putDouble(KEY_ODD_SPEED, speed);
        tag.putInt(KEY_ODD_AGE, 0);
    }

    private static void putDirection(CompoundTag tag, Vec3 direction) {
        tag.putDouble(KEY_ODD_DIR_X, direction.x);
        tag.putDouble(KEY_ODD_DIR_Y, direction.y);
        tag.putDouble(KEY_ODD_DIR_Z, direction.z);
    }

    // --- Odd Arrow: the draw ---

    /** Odd Arrow draws twice as fast: one extra tick taken off the use for every tick held. */
    @SubscribeEvent
    public static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getItem().getItem() instanceof BowItem) && !(event.getItem().getItem() instanceof CrossbowItem)) {
            return;
        }
        PlayerPerkData data = PerkDataManager.get(player);
        if (data.getActiveTier(ModPerks.LONGSHOT) > 0 && data.isAddonActive(ModAddons.ODD_ARROW)) {
            event.setDuration(Math.max(1, event.getDuration() - (int) Math.round(ModAddons.ODD_ARROW_DRAW)));
        }
    }

    // --- damage ---

    /**
     * The distance bonus is flat damage, so it is added here rather than to the arrow's base damage.
     * Base damage is multiplied by the arrow's speed before it lands, which would have tripled the
     * bonus on a full-draw shot.
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof AbstractArrow arrow)) {
            return;
        }
        CompoundTag tag = arrow.getPersistentData();
        if (!tag.contains(KEY_PER_BLOCK)) {
            return;
        }

        double travelled = Math.sqrt(arrow.distanceToSqr(
                tag.getDouble(KEY_ORIGIN_X), tag.getDouble(KEY_ORIGIN_Y), tag.getDouble(KEY_ORIGIN_Z)));
        float amount = event.getAmount() + (float) (travelled * tag.getDouble(KEY_PER_BLOCK));

        // Point Blank multiplies the whole thing, distance bonus included.
        if (tag.getBoolean(KEY_HEADSHOT) && isHeadshot(arrow, event.getEntity())) {
            amount *= ModAddons.POINT_BLANK_MULTIPLIER;
            playHeadshotCue(arrow, event.getEntity());
        }
        event.setAmount(amount);

        // Bounty Poster is settled in the Post event instead: Exposed pins its victim at a single
        // point of health, so applying it here would have the arrow's own damage land on a target
        // already down to one.
        tag.putBoolean(KEY_BOUNTY, tag.getBoolean(KEY_BOUNTY) && travelled >= ModAddons.BOUNTY_POSTER_RANGE);

        // One payout per arrow, so a ricochet cannot be paid twice.
        tag.remove(KEY_PER_BLOCK);
    }

    /** The shot has landed and been paid for; now what sticks to whatever survived it. */
    @SubscribeEvent
    public static void onDamageTaken(LivingDamageEvent.Post event) {
        if (!(event.getSource().getDirectEntity() instanceof AbstractArrow arrow)) {
            return;
        }
        CompoundTag tag = arrow.getPersistentData();
        LivingEntity target = event.getEntity();

        if (tag.getBoolean(KEY_CURSED)) {
            tag.remove(KEY_CURSED);
            if (target.isAlive()) {
                BleedingHandler.apply(target, ModAddons.CURSED_RISER_BLEED);
            }
        }

        if (!tag.getBoolean(KEY_BOUNTY)) {
            return;
        }
        tag.remove(KEY_BOUNTY);
        if (target.isAlive()) {
            applyBounty(arrow, target);
        }
    }

    /**
     * Bounty Poster: a shot from far enough out pins the target at a single point of health and
     * paints them on the shooter's screen for the same length of time.
     */
    private static void applyBounty(AbstractArrow arrow, LivingEntity target) {
        target.addEffect(new MobEffectInstance(ModEffects.EXPOSED,
                ModAddons.BOUNTY_POSTER_DURATION_TICKS, 0, false, true, true));

        if (arrow.getOwner() instanceof ServerPlayer shooter) {
            AuraRevealHandler.reveal(shooter, target, ModAddons.BOUNTY_POSTER_DURATION_TICKS);
            shooter.playNotifySound(SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 1.0F, 0.6F);
        }
    }

    /**
     * A short ping so the shooter knows the shot landed on the head. Played straight to them as
     * well as at the target, since a long shot leaves them out of earshot.
     */
    private static void playHeadshotCue(AbstractArrow arrow, LivingEntity target) {
        target.level().playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
                SoundSource.PLAYERS, 0.8F, 1.7F);
        if (arrow.getOwner() instanceof ServerPlayer shooter) {
            shooter.playNotifySound(SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 1.0F, 1.8F);
        }
    }

    private static boolean isHeadshot(AbstractArrow arrow, LivingEntity target) {
        double headStart = target.getY() + target.getBbHeight() * ModAddons.POINT_BLANK_HEAD_FRACTION;
        return arrow.getY() >= headStart;
    }

    // --- per-tick upkeep ---

    @SubscribeEvent
    public static void onArrowTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow) || arrow.level().isClientSide) {
            return;
        }
        CompoundTag tag = arrow.getPersistentData();
        boolean odd = tag.getBoolean(KEY_ODD);
        boolean flying = !arrow.inGround && !arrow.isRemoved();

        if (odd && flying) {
            weave(arrow, tag);
        } else if (tag.getBoolean(KEY_CURSED) && flying) {
            if (curse(arrow, arrow.getDeltaMovement())) {
                sync(arrow);
            }
        }

        // A no-gravity arrow that has run out of speed would hang in the air for ever, so hand its
        // gravity back and let it land and despawn the way any other arrow would. An Odd Arrow only
        // ever gains speed, so it is left alone.
        if (!odd && arrow.isNoGravity() && arrow.getDeltaMovement().lengthSqr() < GRAVITY_RESTORE_SPEED_SQR) {
            arrow.setNoGravity(false);
        }
        if (odd && arrow.inGround) {
            tag.remove(KEY_ODD);
            arrow.setNoGravity(false);
        }

        if (tag.getBoolean(KEY_LIGHT)) {
            moveLight(arrow);
        }
    }

    /**
     * Odd Arrow: gains speed where every other arrow loses it, and weaves across its own line as it
     * goes. The sideways part is the derivative of a sine, so the path traced is an S rather than a
     * zigzag. Rewritten every tick after vanilla's own move, which is also what cancels its drag.
     */
    private static void weave(AbstractArrow arrow, CompoundTag tag) {
        Vec3 direction = new Vec3(tag.getDouble(KEY_ODD_DIR_X), tag.getDouble(KEY_ODD_DIR_Y), tag.getDouble(KEY_ODD_DIR_Z));

        // Came off a wall: the old heading would steer it straight back in, so it takes the new one.
        int bankHits = tag.getInt(KEY_BANK_HITS);
        if (bankHits != tag.getInt(KEY_ODD_HITS) && arrow.getDeltaMovement().lengthSqr() > 1.0E-6D) {
            tag.putInt(KEY_ODD_HITS, bankHits);
            direction = arrow.getDeltaMovement().normalize();
        }

        if (tag.getBoolean(KEY_CURSED) && curse(arrow, direction)) {
            direction = arrow.getDeltaMovement().normalize();
        }
        putDirection(tag, direction);

        double speed = Math.min(ModAddons.ODD_ARROW_MAX_SPEED, tag.getDouble(KEY_ODD_SPEED) * ModAddons.ODD_ARROW_ACCELERATION);
        tag.putDouble(KEY_ODD_SPEED, speed);
        int age = tag.getInt(KEY_ODD_AGE) + 1;
        tag.putInt(KEY_ODD_AGE, age);

        Vec3 across = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
        across = across.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : across.normalize();
        double sway = Math.cos(Math.PI * 2.0D * age / ModAddons.ODD_ARROW_WAVE_PERIOD)
                * speed * ModAddons.ODD_ARROW_WAVE_AMPLITUDE;

        Vec3 velocity = direction.scale(speed).add(across.scale(sway));
        arrow.setDeltaMovement(velocity);
        double flat = velocity.horizontalDistance();
        arrow.setYRot((float) (Mth.atan2(velocity.x, velocity.z) * (180.0F / Math.PI)));
        arrow.setXRot((float) (Mth.atan2(velocity.y, flat) * (180.0F / Math.PI)));
        sync(arrow);
    }

    /**
     * Cursed Riser: a slight pull onto whatever is closest to the arrow's line and in plain sight.
     *
     * @return true when the arrow was turned
     */
    private static boolean curse(AbstractArrow arrow, Vec3 heading) {
        if (heading.lengthSqr() < 1.0E-6D) {
            return false;
        }
        Vec3 direction = heading.normalize();
        Vec3 position = arrow.position();
        double range = ModAddons.CURSED_RISER_RANGE;
        double minimumDot = Math.cos(Math.toRadians(ModAddons.CURSED_RISER_CONE));
        Entity owner = arrow.getOwner();

        LivingEntity best = null;
        double bestDot = -2.0D;
        for (LivingEntity candidate : arrow.level().getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(position, range * 2, range * 2, range * 2))) {
            if (candidate == owner || !candidate.isAlive() || candidate instanceof ArmorStand || candidate.isSpectator()) {
                continue;
            }
            Vec3 offset = candidate.getBoundingBox().getCenter().subtract(position);
            double distance = offset.length();
            if (distance > range || distance < 1.0E-3D) {
                continue;
            }
            double dot = offset.scale(1.0D / distance).dot(direction);
            if (dot < minimumDot || dot <= bestDot || !inSight(arrow.level(), position, candidate)) {
                continue;
            }
            best = candidate;
            bestDot = dot;
        }
        if (best == null) {
            return false;
        }

        Vec3 velocity = arrow.getDeltaMovement();
        double speed = velocity.length();
        Vec3 wanted = best.getBoundingBox().getCenter().subtract(position).normalize().scale(speed);
        arrow.setDeltaMovement(velocity.add(wanted.subtract(velocity).scale(ModAddons.CURSED_RISER_DRIFT))
                .normalize().scale(speed));
        return true;
    }

    private static boolean inSight(Level level, Vec3 from, LivingEntity target) {
        return level.clip(new ClipContext(from, target.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty())).getType() == HitResult.Type.MISS;
    }

    /**
     * Tells every watching client where a steered arrow really is. Clients fly their own copy by its
     * last known velocity, and a curving arrow is the one thing that copy can never predict.
     */
    private static void sync(AbstractArrow arrow) {
        if (!(arrow.level() instanceof ServerLevel level)) {
            return;
        }
        level.getChunkSource().broadcastAndSend(arrow, new ClientboundSetEntityMotionPacket(arrow));
        if (arrow.tickCount % 10 == 0) {
            level.getChunkSource().broadcastAndSend(arrow, new ClientboundTeleportEntityPacket(arrow));
        }
    }

    // --- Taped Flashlight: the arrow carries a light with it ---

    private static void moveLight(AbstractArrow arrow) {
        BlockPos pos = arrow.blockPosition();
        ArrowLight current = LIGHTS.get(arrow.getUUID());
        if (current != null) {
            if (current.pos().equals(pos)) {
                return;
            }
            clearLight(arrow.level(), current.pos());
            LIGHTS.remove(arrow.getUUID());
        }

        // Only ever replace air, so the light never eats a block the player cared about.
        if (arrow.level().getBlockState(pos).isAir()) {
            arrow.level().setBlock(pos, Blocks.LIGHT.defaultBlockState()
                    .setValue(LightBlock.LEVEL, ModAddons.TAPED_FLASHLIGHT_LIGHT_LEVEL), Block.UPDATE_ALL);
            LIGHTS.put(arrow.getUUID(), new ArrowLight(arrow.level().dimension(), pos));
        }
    }

    /** Hitting anything puts the light out immediately, without waiting for the sweep. */
    @SubscribeEvent
    public static void onImpact(ProjectileImpactEvent event) {
        if (event.getProjectile() instanceof AbstractArrow arrow && !arrow.level().isClientSide) {
            releaseLight(arrow.getUUID(), arrow.level());
        }
    }

    /**
     * Sweeps up after arrows that are gone: picked up, despawned, killed by a command, or unloaded
     * with their chunk. Rebuilding the state from the live entity list every tick is what makes an
     * orphaned light block impossible, rather than relying on any single removal event firing.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (LIGHTS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, ArrowLight>> iterator = LIGHTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ArrowLight> entry = iterator.next();
            ServerLevel level = event.getServer().getLevel(entry.getValue().dimension());
            if (level == null) {
                iterator.remove();
                continue;
            }
            Entity arrow = level.getEntity(entry.getKey());
            if (arrow == null || arrow.isRemoved()) {
                clearLight(level, entry.getValue().pos());
                iterator.remove();
            }
        }
    }

    private static void releaseLight(UUID arrowId, Level level) {
        ArrowLight light = LIGHTS.remove(arrowId);
        if (light != null) {
            clearLight(level, light.pos());
        }
    }

    /** Removes a light block only if it is still one of ours. */
    private static void clearLight(Level level, BlockPos pos) {
        if (level.getBlockState(pos).is(Blocks.LIGHT)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
