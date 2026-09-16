package net.h3xpy.bloodbound.perk.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.h3xpy.bloodbound.damage.PerkDamageSource;
import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.event.BankShotHandler;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.registry.ModEffects;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import org.joml.Vector3f;

/**
 * Frag' Nade: a grenade thrown as hard as you wound it up, that goes off twice.
 * <p>
 * Walls turn it; the floor stops it. Once it is down it beeps — briefly if it came off a wall on the
 * way, twice as long if it went straight to the ground — and then goes off in two blasts a second
 * apart: a tight one, and a wide one that throws everything clear. Neither cares about armour, and
 * neither cares whose grenade it was — unless the thrower is carrying Pure Topaz, which turns the
 * second blast into a way to be thrown.
 */
public final class FragNade {

    /** Cyan to magenta for the flight and the first blast; yellow to cyan for the second. */
    public static final Vector3f CYAN = new Vector3f(0.0F, 1.0F, 1.0F);
    public static final Vector3f MAGENTA = new Vector3f(1.0F, 0.0F, 1.0F);
    public static final Vector3f YELLOW = new Vector3f(1.0F, 1.0F, 0.0F);
    private static final DustColorTransitionOptions TRAIL = new DustColorTransitionOptions(CYAN, MAGENTA, 1.0F);
    private static final DustColorTransitionOptions FIRST = new DustColorTransitionOptions(CYAN, MAGENTA, 1.6F);
    private static final DustColorTransitionOptions SECOND = new DustColorTransitionOptions(YELLOW, CYAN, 1.8F);

    /** How long Pure Topaz's fall protection may be held at most, landing or not. */
    private static final int TOPAZ_SAFETY_TICKS = 600;

    /** One grenade, from the throw to the second blast. */
    private static final class Grenade {
        private final UUID ownerId;
        private final ResourceKey<Level> dimension;
        private final int tier;
        private final boolean fragstone;
        private final boolean rawOre;
        private final boolean powdered;
        private final boolean quartz;
        private final boolean topaz;
        private Vec3 position;
        private Vec3 velocity;
        private int ticksLeft = ModPerks.FRAG_MAX_FLIGHT_TICKS;
        private boolean bounced;
        private boolean landed;
        private int beepTicks;
        private int beepsLeftUntilNext;
        private int secondIn = -1;

        private Grenade(ServerPlayer owner, PlayerPerkData data, int tier, Vec3 position, Vec3 velocity) {
            this.ownerId = owner.getUUID();
            this.dimension = owner.level().dimension();
            this.tier = tier;
            this.fragstone = data.isAddonActive(ModAddons.FRAGSTONE_SHARD);
            this.rawOre = data.isAddonActive(ModAddons.RAW_ORE);
            this.powdered = data.isAddonActive(ModAddons.POWDERED_CRYSTAL);
            this.quartz = data.isAddonActive(ModAddons.QUARTZ_PENDANT);
            this.topaz = data.isAddonActive(ModAddons.PURE_TOPAZ);
            this.position = position;
            this.velocity = velocity;
        }
    }

    /** Pure Topaz: a thrower being carried by their own blast, and when they came back down. */
    private static final class FallGuard {
        private final long since;
        private boolean leftGround;
        private long landedAt = -1L;

        private FallGuard(long since) {
            this.since = since;
        }
    }

    private static final int SUB_STEPS = 4;

    private static final List<Grenade> ACTIVE = new ArrayList<>();
    /** Players winding a throw up, and the tick they started. */
    private static final Map<UUID, Long> WINDING = new HashMap<>();
    private static final Map<UUID, FallGuard> FALL_GUARDS = new HashMap<>();

    private FragNade() {}

    // --- the throw ---

    /** Nothing: the throw happens when the key comes up. */
    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        return false;
    }

    public static void setHolding(ServerPlayer player, boolean holding, long gameTime) {
        PlayerPerkData data = PerkDataManager.get(player);
        int tier = data.getActiveTier(ModPerks.FRAGNADE);
        if (tier <= 0) {
            WINDING.remove(player.getUUID());
            return;
        }
        if (holding) {
            if (!data.isOnCooldown(ModPerks.FRAGNADE.id(), gameTime)) {
                WINDING.putIfAbsent(player.getUUID(), gameTime);
            }
            return;
        }

        Long since = WINDING.remove(player.getUUID());
        if (since == null || data.isOnCooldown(ModPerks.FRAGNADE.id(), gameTime)) {
            return;
        }
        double share = Math.min(1.0D, (gameTime - since) / (double) ModPerks.FRAG_HOLD_MAX_TICKS);
        ACTIVE.add(new Grenade(player, data, tier, player.getEyePosition(),
                player.getLookAngle().scale(ModPerks.fragSpeed(share))));

        data.setCooldown(ModPerks.FRAGNADE.id(), gameTime, ModPerks.FRAGNADE.cooldownTicks(tier));
        PerkDataManager.sync(player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.SNOWBALL_THROW,
                SoundSource.PLAYERS, 0.9F, 0.6F + (float) share * 0.4F);
    }

    // --- flight, fuse and blasts ---

    public static void tickGrenades(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Grenade> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            Grenade grenade = iterator.next();
            ServerLevel level = server.getLevel(grenade.dimension);
            if (level == null || !advance(level, grenade)) {
                iterator.remove();
            }
        }
    }

    /** @return false once both blasts are done */
    private static boolean advance(ServerLevel level, Grenade grenade) {
        if (grenade.secondIn >= 0) {
            if (--grenade.secondIn <= 0) {
                detonate(level, grenade, false);
                return false;
            }
            return true;
        }
        if (grenade.landed) {
            beep(level, grenade);
            return true;
        }
        if (--grenade.ticksLeft <= 0) {
            return false;
        }

        grenade.velocity = grenade.velocity.subtract(0.0D, ModPerks.FRAG_GRAVITY, 0.0D);
        Vec3 step = grenade.velocity.scale(1.0D / SUB_STEPS);
        for (int i = 0; i < SUB_STEPS; i++) {
            Vec3 from = grenade.position;
            Vec3 to = from.add(step);
            BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (hit.getType() == HitResult.Type.MISS) {
                grenade.position = to;
                continue;
            }

            Direction face = hit.getDirection();
            grenade.position = hit.getLocation().add(Vec3.atLowerCornerOf(face.getNormal())
                    .scale(ModPerks.FRAG_RADIUS + 0.02D));
            if (face == Direction.UP) {
                land(level, grenade);
                return true;
            }
            // A wall, or a ceiling: it comes off, and the fuse it lands with will be the short one.
            grenade.bounced = true;
            grenade.velocity = BankShotHandler.reflect(grenade.velocity, face).scale(ModPerks.FRAG_BOUNCE_SPEED);
            level.playSound(null, grenade.position.x, grenade.position.y, grenade.position.z,
                    SoundEvents.METAL_HIT, SoundSource.PLAYERS, 0.5F, 1.2F);
            break;
        }

        level.sendParticles(TRAIL, grenade.position.x, grenade.position.y, grenade.position.z,
                2, 0.03D, 0.03D, 0.03D, 0.0D);
        return true;
    }

    private static void land(ServerLevel level, Grenade grenade) {
        grenade.landed = true;
        grenade.velocity = Vec3.ZERO;
        int fuse = grenade.bounced ? ModPerks.FRAG_BEEP_BOUNCED_TICKS : ModPerks.FRAG_BEEP_DIRECT_TICKS;
        if (grenade.rawOre) {
            fuse = Math.max(2, Math.round(fuse * ModAddons.RAW_ORE_FUSE));
        }
        grenade.beepTicks = fuse;
        level.playSound(null, grenade.position.x, grenade.position.y, grenade.position.z,
                SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.25F, 1.8F);
    }

    private static void beep(ServerLevel level, Grenade grenade) {
        if (grenade.beepTicks <= 0) {
            detonate(level, grenade, true);
            int interval = ModPerks.FRAG_INTERVAL_TICKS;
            if (grenade.powdered) {
                interval = Math.round(interval * ModAddons.POWDERED_CRYSTAL_INTERVAL);
            }
            grenade.secondIn = interval;
            return;
        }
        if (grenade.beepsLeftUntilNext <= 0) {
            // Quickening as it runs down, like any fuse worth being afraid of.
            grenade.beepsLeftUntilNext = Math.max(2, grenade.beepTicks / 3);
            level.playSound(null, grenade.position.x, grenade.position.y, grenade.position.z,
                    SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.9F, 1.6F);
            level.sendParticles(TRAIL, grenade.position.x, grenade.position.y + 0.1D, grenade.position.z,
                    4, 0.05D, 0.05D, 0.05D, 0.0D);
        }
        grenade.beepsLeftUntilNext--;
        grenade.beepTicks--;
    }

    private static void detonate(ServerLevel level, Grenade grenade, boolean first) {
        Vec3 blast = grenade.position;
        double radius = radius(grenade.tier, first, grenade.fragstone, grenade.powdered, grenade.quartz);
        float damage = (float) ModPerks.FRAGNADE.value(ModPerks.FRAG_DAMAGE, grenade.tier);
        double knockback = ModPerks.FRAG_KNOCKBACK;
        if (grenade.quartz) {
            damage *= first ? ModAddons.QUARTZ_PENDANT_FIRST_DAMAGE : ModAddons.QUARTZ_PENDANT_SECOND_DAMAGE;
        }
        if (!first && grenade.topaz) {
            knockback *= ModAddons.PURE_TOPAZ_KNOCKBACK;
        }

        shell(level, blast, radius, first ? FIRST : SECOND);
        level.sendParticles(ParticleTypes.EXPLOSION, blast.x, blast.y + 0.2D, blast.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(null, blast.x, blast.y, blast.z, SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, first ? 0.9F : 1.4F, first ? 1.5F : 1.0F);

        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(grenade.ownerId);
        DamageSource source = owner != null
                ? level.damageSources().indirectMagic(owner, owner)
                : level.damageSources().magic();

        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(blast, radius * 2, radius * 2, radius * 2))) {
            if (!victim.isAlive() || victim.isSpectator()) {
                continue;
            }
            Vec3 centre = victim.getBoundingBox().getCenter();
            if (centre.distanceTo(blast) > radius && victim.position().distanceTo(blast) > radius) {
                continue;
            }
            boolean isOwner = victim == owner;

            // Magic damage is what vanilla already treats as ignoring armour. The thrower is not
            // spared — except by Pure Topaz, and only from the second blast.
            if (!(isOwner && !first && grenade.topaz)) {
                victim.hurt(PerkDamageSource.of(source, "frag_nade"), damage);
            }

            if (first) {
                if (grenade.quartz) {
                    victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                            ModAddons.QUARTZ_PENDANT_SLOW_TICKS, 0, false, true, true));
                }
                continue;
            }

            Vec3 away = centre.subtract(blast);
            Vec3 direction = away.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 1.0D, 0.0D) : away.normalize();
            double lift = ModPerks.FRAG_KNOCKBACK_LIFT * (grenade.topaz ? ModAddons.PURE_TOPAZ_KNOCKBACK : 1.0D);
            victim.setDeltaMovement(victim.getDeltaMovement().add(direction.scale(knockback)).add(0.0D, lift, 0.0D));
            victim.hurtMarked = true;
            if (grenade.powdered) {
                victim.addEffect(new MobEffectInstance(ModEffects.BROKEN,
                        ModAddons.POWDERED_CRYSTAL_BROKEN_TICKS, 0, false, true, true));
            }
            if (isOwner && grenade.topaz) {
                FALL_GUARDS.put(owner.getUUID(), new FallGuard(level.getGameTime()));
            }
        }
    }

    /** The radius of one blast, once the addons have had their say. Shared with the preview. */
    public static double radius(int tier, boolean first, boolean fragstone, boolean powdered, boolean quartz) {
        double radius = ModPerks.FRAGNADE.value(first ? ModPerks.FRAG_FIRST_RADIUS : ModPerks.FRAG_SECOND_RADIUS, tier);
        if (fragstone) {
            radius *= ModAddons.FRAGSTONE_SHARD_RADIUS;
        }
        if (!first && powdered) {
            radius *= ModAddons.POWDERED_CRYSTAL_SECOND_RADIUS;
        }
        if (first && quartz) {
            radius *= ModAddons.QUARTZ_PENDANT_FIRST_RADIUS;
        }
        return radius;
    }

    /** A shell of colour at the blast's edge, so how far it reached is plain to see. */
    private static void shell(ServerLevel level, Vec3 centre, double radius, DustColorTransitionOptions color) {
        int points = (int) Math.round(30 + radius * 25);
        double golden = Math.PI * (3.0D - Math.sqrt(5.0D));
        for (int i = 0; i < points; i++) {
            double y = 1.0D - (i / (double) (points - 1)) * 2.0D;
            double ring = Math.sqrt(1.0D - y * y);
            double angle = golden * i;
            level.sendParticles(color, centre.x + Math.cos(angle) * ring * radius, centre.y + y * radius,
                    centre.z + Math.sin(angle) * ring * radius, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    // --- Pure Topaz: the landing after the ride ---

    /**
     * Keeps a thrown thrower safe until two seconds after they come back down. Called once a tick
     * per player.
     */
    public static void tickFallGuard(ServerPlayer player, long gameTime) {
        FallGuard guard = FALL_GUARDS.get(player.getUUID());
        if (guard == null) {
            return;
        }
        if (!player.onGround()) {
            guard.leftGround = true;
        } else if (guard.leftGround && guard.landedAt < 0L) {
            guard.landedAt = gameTime;
        }
        boolean graceOver = guard.landedAt >= 0L
                && gameTime - guard.landedAt > ModAddons.PURE_TOPAZ_FALL_GRACE_TICKS;
        if (graceOver || gameTime - guard.since > TOPAZ_SAFETY_TICKS || !player.isAlive()) {
            FALL_GUARDS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && FALL_GUARDS.containsKey(player.getUUID())) {
            event.setCanceled(true);
            player.resetFallDistance();
        }
    }

    public static void clear(UUID playerId) {
        WINDING.remove(playerId);
        FALL_GUARDS.remove(playerId);
        ACTIVE.removeIf(grenade -> grenade.ownerId.equals(playerId) && !grenade.landed);
    }
}
