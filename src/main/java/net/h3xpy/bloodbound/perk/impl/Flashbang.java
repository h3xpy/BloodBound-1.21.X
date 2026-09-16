package net.h3xpy.bloodbound.perk.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.event.BankShotHandler;
import net.h3xpy.bloodbound.network.PerkChargesPayload;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.registry.ModEffects;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Unit;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Flashbang: a grenade that blinds everything watching when it goes off.
 * <p>
 * Like the mod's other shots it is simulated here rather than being an entity — it lives two
 * seconds and only has to fall, bounce and beep. Who it blinds is decided by where they were
 * looking: the blast has to be on screen and not behind a wall, so a turned back is cover.
 */
public final class Flashbang {

    /** One grenade in the air. */
    private static final class Grenade {
        private final UUID ownerId;
        private final ResourceKey<Level> dimension;
        private Vec3 position;
        private Vec3 velocity;
        private final int fuseLength;
        private int fuse;
        private int bouncesLeft = ModPerks.FLASHBANG_BOUNCES;
        private int nextBeep;

        private Grenade(UUID ownerId, ResourceKey<Level> dimension, Vec3 position, Vec3 velocity,
                int fuseLength) {
            this.ownerId = ownerId;
            this.dimension = dimension;
            this.position = position;
            this.velocity = velocity;
            this.fuseLength = fuseLength;
            this.fuse = fuseLength;
        }
    }

    private static final int SUB_STEPS = 4;

    private static final List<Grenade> IN_FLIGHT = new ArrayList<>();

    /** Players holding their slot key, and the tick they started. Pulled Pin is the only user. */
    private static final Map<UUID, Long> WINDING = new HashMap<>();

    private Flashbang() {}

    /**
     * With Pulled Pin the throw happens on the key coming up, so the press only starts the wind-up.
     * Without it the press is the throw.
     */
    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        if (data.isAddonActive(ModAddons.PULLED_PIN)) {
            WINDING.put(player.getUUID(), gameTime);
            return false;
        }
        return throwGrenade(player, data, tier, gameTime, 1.0D);
    }

    /** The key going down or coming up. Only Pulled Pin cares which. */
    public static void setHolding(ServerPlayer player, boolean holding, long gameTime) {
        PlayerPerkData data = PerkDataManager.get(player);
        int tier = data.getActiveTier(ModPerks.FLASHBANG);
        if (tier <= 0 || !data.isAddonActive(ModAddons.PULLED_PIN)) {
            WINDING.remove(player.getUUID());
            return;
        }

        if (holding) {
            if (!data.isOnCooldown(ModPerks.FLASHBANG.id(), gameTime)) {
                WINDING.put(player.getUUID(), gameTime);
            }
            return;
        }

        Long since = WINDING.remove(player.getUUID());
        if (since == null || data.isOnCooldown(ModPerks.FLASHBANG.id(), gameTime)) {
            return;
        }

        // Four seconds of winding is worth three times the throw; anything less is in between.
        double held = Math.clamp((gameTime - since) / (double) ModAddons.PULLED_PIN_FULL_HOLD_TICKS,
                0.0D, 1.0D);
        if (throwGrenade(player, data, tier, gameTime, 1.0D + ModAddons.PULLED_PIN_MAX_BONUS * held)) {
            PerkDataManager.sync(player);
        }
    }

    /** Lets one go, at whatever strength it was thrown. */
    private static boolean throwGrenade(ServerPlayer player, PlayerPerkData data, int tier, long gameTime,
            double power) {
        int fuse = ModPerks.FLASHBANG_FUSE_TICKS;
        if (data.isAddonActive(ModAddons.FAST_FUSE)) {
            fuse = Math.max(5, (int) Math.round(fuse / ModAddons.FAST_FUSE_SPEED));
        }

        IN_FLIGHT.add(new Grenade(player.getUUID(), player.level().dimension(), player.getEyePosition(),
                player.getLookAngle().scale(ModPerks.FLASHBANG_SPEED * power), fuse));

        player.level().playSound(null, player.blockPosition(), SoundEvents.SNOWBALL_THROW,
                SoundSource.PLAYERS, 0.8F, 0.8F);
        chargeCooldown(player, data, tier, gameTime);
        sendSatchel(player, data, gameTime);
        return true;
    }

    /**
     * Puts the perk on cooldown, unless Worn Satchel still has one in the bag. The cooldown that
     * eventually lands is the same one — the satchel buys a second throw, not a cheaper one.
     */
    private static void chargeCooldown(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        int cooldown = ModPerks.FLASHBANG.cooldownTicks(tier);
        if (data.isAddonActive(ModAddons.LIGHT_STEEL)) {
            cooldown = Math.round(cooldown * ModAddons.LIGHT_STEEL_COOLDOWN);
        }

        if (!data.isAddonActive(ModAddons.WORN_SATCHEL)) {
            data.setCooldown(ModPerks.FLASHBANG.id(), gameTime, cooldown);
            return;
        }

        float left = data.perkCharges(ModPerks.FLASHBANG.id(), ModAddons.WORN_SATCHEL_CHARGES) - 1.0F;
        if (left > 0.0F) {
            data.setPerkCharges(ModPerks.FLASHBANG.id(), left);
            return;
        }
        // Bag empty: the cooldown lands, and refills it on the way out.
        data.setPerkCharges(ModPerks.FLASHBANG.id(), ModAddons.WORN_SATCHEL_CHARGES);
        data.setCooldown(ModPerks.FLASHBANG.id(), gameTime, cooldown);
    }

    public static void clearWinding(UUID playerId) {
        WINDING.remove(playerId);
    }

    /**
     * Keeps Worn Satchel's count on the HUD. Sent once a second, and on a slot with no satchel it
     * sends nothing-to-show, so the pips come off the moment the addon does.
     */
    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        if (data.getActiveTier(ModPerks.FLASHBANG) <= 0 || gameTime % 10L != 0L) {
            return;
        }
        sendSatchel(player, data, gameTime);
    }

    private static void sendSatchel(ServerPlayer player, PlayerPerkData data, long gameTime) {
        if (!data.isAddonActive(ModAddons.WORN_SATCHEL)) {
            PacketDistributor.sendToPlayer(player, new PerkChargesPayload(ModPerks.FLASHBANG.id(), -1, 0, 0L));
            return;
        }
        // On cooldown the bag reads empty, even though it is refilled the moment the cooldown starts.
        int left = data.isOnCooldown(ModPerks.FLASHBANG.id(), gameTime)
                ? 0
                : Math.round(data.perkCharges(ModPerks.FLASHBANG.id(), ModAddons.WORN_SATCHEL_CHARGES));
        PacketDistributor.sendToPlayer(player, new PerkChargesPayload(ModPerks.FLASHBANG.id(), left,
                ModAddons.WORN_SATCHEL_CHARGES, 0L));
    }
    /** Steps every grenade in the air. Called once a tick for the whole server. */
    public static void tickGrenades(MinecraftServer server) {
        if (IN_FLIGHT.isEmpty()) {
            return;
        }
        Iterator<Grenade> iterator = IN_FLIGHT.iterator();
        while (iterator.hasNext()) {
            Grenade grenade = iterator.next();
            ServerLevel level = server.getLevel(grenade.dimension);
            if (level == null || !advance(level, grenade)) {
                iterator.remove();
            }
        }
    }

    /** @return false once the grenade has gone off */
    private static boolean advance(ServerLevel level, Grenade grenade) {
        if (--grenade.fuse <= 0) {
            detonate(level, grenade);
            return false;
        }

        beep(level, grenade);

        grenade.velocity = grenade.velocity.subtract(0.0D, ModPerks.FLASHBANG_GRAVITY, 0.0D);
        Vec3 step = grenade.velocity.scale(1.0D / SUB_STEPS);

        for (int i = 0; i < SUB_STEPS; i++) {
            Vec3 from = grenade.position;
            Vec3 to = from.add(step);

            BlockHitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (wall.getType() == HitResult.Type.MISS) {
                grenade.position = to;
                continue;
            }

            Direction face = wall.getDirection();
            grenade.position = wall.getLocation().add(Vec3.atLowerCornerOf(face.getNormal())
                    .scale(ModPerks.FLASHBANG_RADIUS + 0.02D));
            if (grenade.bouncesLeft <= 0) {
                // Out of bounces: it settles where it landed and counts down on the spot.
                grenade.velocity = Vec3.ZERO;
                break;
            }

            grenade.bouncesLeft--;
            grenade.velocity = BankShotHandler.reflect(grenade.velocity, face)
                    .scale(ModPerks.FLASHBANG_BOUNCE_SPEED);
            level.playSound(null, grenade.position.x, grenade.position.y, grenade.position.z,
                    SoundEvents.METAL_HIT, SoundSource.PLAYERS, 0.5F, 1.4F);
            break;
        }

        level.sendParticles(ParticleTypes.SMOKE, grenade.position.x, grenade.position.y, grenade.position.z,
                1, 0.02D, 0.02D, 0.02D, 0.0D);
        return true;
    }

    /** A beep that quickens as the fuse runs down, so the throw can be read by ear. */
    private static void beep(ServerLevel level, Grenade grenade) {
        if (grenade.nextBeep > 0) {
            grenade.nextBeep--;
            return;
        }
        // From about half a second between beeps down to every other tick at the end.
        grenade.nextBeep = Math.max(2, grenade.fuse / 4);
        float pitch = 1.0F + (grenade.fuseLength - grenade.fuse) / (float) grenade.fuseLength;
        level.playSound(null, grenade.position.x, grenade.position.y, grenade.position.z,
                SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.7F, pitch);
    }

    /** Everything that can see the blast is blinded, the further off the shorter. */
    private static void detonate(ServerLevel level, Grenade grenade) {
        Vec3 blast = grenade.position;
        level.sendParticles(ParticleTypes.FLASH, blast.x, blast.y, blast.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.END_ROD, blast.x, blast.y, blast.z, 60, 0.4D, 0.4D, 0.4D, 0.35D);
        level.playSound(null, blast.x, blast.y, blast.z, SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, 2.0F, 1.6F);

        double range = ModPerks.FLASHBANG_RANGE;
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(blast, range * 2, range * 2, range * 2))) {
            if (!victim.isAlive()) {
                continue;
            }
            double distance = victim.getEyePosition().distanceTo(blast);
            if (distance > range || !isLookingAt(victim, blast) || !canSee(level, blast, victim)) {
                continue;
            }

            double seconds = ModPerks.FLASHBANG_BASE_SECONDS - distance * ModPerks.FLASHBANG_FALLOFF_PER_BLOCK;
            int ticks = (int) Math.round(seconds * 20.0D);
            if (ticks > 0) {
                blind(victim, ticks);
            }
        }
    }

    /**
     * Blinds one victim. From The Dark makes it worse: eyes used to the dark take twice as long to
     * come back.
     */
    public static void blind(LivingEntity victim, int ticks) {
        if (victim instanceof ServerPlayer player
                && PerkDataManager.get(player).getActiveTier(ModPerks.FROM_THE_DARK) > 0) {
            ticks *= ModPerks.FROM_THE_DARK_FLASH_MULTIPLIER;
        }
        victim.addEffect(new MobEffectInstance(ModEffects.FLASHED, ticks, 0, false, true, true));
    }

    /**
     * The blast has to be on screen, not merely in the room: a grenade behind you is one you never
     * saw, and turning away in time is the whole skill of dodging it.
     */
    private static boolean isLookingAt(LivingEntity victim, Vec3 blast) {
        Vec3 toBlast = blast.subtract(victim.getEyePosition());
        if (toBlast.lengthSqr() < 1.0E-6D) {
            return true;
        }
        return toBlast.normalize().dot(victim.getLookAngle())
                >= Math.cos(Math.toRadians(ModPerks.FLASHBANG_VIEW_DEGREES));
    }

    /**
     * Nothing you cannot see through between the blast and the entity's eyes.
     * <p>
     * Walked block by block rather than handed to {@code Level.clip}, because a flash is a matter of
     * what reaches the eye, not of what stops an arrow: glass, leaves, fences and the rest are all
     * transparent here, and only a block that truly hides what is behind it is cover. The shape is
     * still consulted, so grazing the corner of a wall is not the same as running into it.
     */
    private static boolean canSee(ServerLevel level, Vec3 blast, LivingEntity victim) {
        Vec3 eyes = victim.getEyePosition();
        Boolean clear = BlockGetter.traverseBlocks(blast, eyes, Unit.INSTANCE, (unit, pos) -> {
            BlockState state = level.getBlockState(pos);
            if (!state.canOcclude()) {
                return null;
            }
            VoxelShape shape = state.getCollisionShape(level, pos);
            return shape.isEmpty() || shape.clip(blast, eyes, pos) == null ? null : Boolean.FALSE;
        }, unit -> Boolean.TRUE);
        return clear == null || clear;
    }

    /** Drops anything still in the air for a player who has left. */
    public static void clear(UUID playerId) {
        IN_FLIGHT.removeIf(grenade -> grenade.ownerId.equals(playerId));
    }
}
