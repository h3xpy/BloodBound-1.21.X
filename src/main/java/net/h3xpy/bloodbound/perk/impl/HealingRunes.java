package net.h3xpy.bloodbound.perk.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.h3xpy.bloodbound.advancement.ModAdvancements;
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
 * Healing Runes: a slow mote of light that mends whatever it touches.
 * <p>
 * No gravity and no great speed — a rune has to be led, which is the point: it is a thrown heal, and
 * it costs a charge whether it lands or not. It stops on the first living thing it finds, or on the
 * first wall.
 */
public final class HealingRunes {

    /** One rune in the air. */
    private static final class Rune {
        private final UUID ownerId;
        private final ResourceKey<Level> dimension;
        private final int tier;
        private Vec3 position;
        private Vec3 velocity;
        private int ticksLeft = ModPerks.RUNES_MAX_FLIGHT_TICKS;
        /** A bounce bought from Bank Shot. */
        private int bankBounces;
        /** Whether it has come off a wall yet, for Trick Shot. */
        /** Lucky Charm and Decorated Leaf change what a rune is worth, settled as it leaves. */
        private float healScale = 1.0F;
        /** Decorated Leaf: a rune that breaks at its thrower's feet mends them. */
        private boolean selfHeal;
        private boolean bounced;

        private Rune(ServerPlayer owner, int tier, Vec3 position, Vec3 velocity) {
            this.ownerId = owner.getUUID();
            this.dimension = owner.level().dimension();
            this.tier = tier;
            this.position = position;
            this.velocity = velocity;
        }
    }

    private static final int SUB_STEPS = 2;

    private static final List<Rune> IN_FLIGHT = new ArrayList<>();

    /** Background Player: how far off the patient has to be. */
    private static final double BACKGROUND_PLAYER_RANGE = 30.0D;

    private HealingRunes() {}

    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        float max = ModPerks.HEALING_RUNES.intValue(ModPerks.RUNES_CHARGES, tier);
        float charges = data.perkCharges(ModPerks.HEALING_RUNES.id(), max);
        if (charges < 1.0F) {
            return false;
        }

        data.setPerkCharges(ModPerks.HEALING_RUNES.id(), charges - 1.0F);

        double speed = ModPerks.RUNES_SPEED;
        if (data.isAddonActive(ModAddons.INK_BOTTLE)) {
            speed *= ModAddons.INK_BOTTLE_SPEED;
        }
        Vec3 look = player.getLookAngle();
        boolean lucky = data.isAddonActive(ModAddons.LUCKY_CHARM);
        boolean leaf = data.isAddonActive(ModAddons.DECORATED_LEAF);
        // One Bank Shot charge covers the whole volley.
        int bounces = BankShotHandler.spendBounce(player) ? ModPerks.BANK_SHOT_BOUNCES : 0;

        for (Vec3 offset : lucky ? triangle(look) : List.of(Vec3.ZERO)) {
            Rune rune = new Rune(player, tier, player.getEyePosition().add(offset), look.scale(speed));
            rune.bankBounces = bounces;
            rune.healScale = (lucky ? ModAddons.LUCKY_CHARM_HEAL : 1.0F)
                    * (leaf ? ModAddons.DECORATED_LEAF_OTHER_HEAL : 1.0F);
            rune.selfHeal = leaf;
            IN_FLIGHT.add(rune);
        }

        sendCharges(player, data, tier, max);
        player.level().playSound(null, player.blockPosition(), SoundEvents.EVOKER_CAST_SPELL,
                SoundSource.PLAYERS, 0.5F, 1.9F);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.8F, 1.7F);
        return true;
    }

    /**
     * Lucky Charm: three starting points in a triangle square to where the player is looking, one
     * above the crosshair and two below either side of it, all flying the same way.
     */
    private static List<Vec3> triangle(Vec3 look) {
        Vec3 side = Math.abs(look.y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = look.cross(side).normalize();
        Vec3 up = right.cross(look).normalize();
        double spread = ModAddons.LUCKY_CHARM_SPREAD;
        List<Vec3> points = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            double angle = Math.PI / 2.0D + i * Math.PI * 2.0D / 3.0D;
            points.add(right.scale(Math.cos(angle) * spread).add(up.scale(Math.sin(angle) * spread)));
        }
        return points;
    }

    /** Hands a rune back when one is due. Called once a tick per player. */
    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        int tier = data.getActiveTier(ModPerks.HEALING_RUNES);
        if (tier <= 0) {
            return;
        }

        float max = ModPerks.HEALING_RUNES.intValue(ModPerks.RUNES_CHARGES, tier);
        float charges = data.perkCharges(ModPerks.HEALING_RUNES.id(), max);
        int recharge = ModPerks.HEALING_RUNES.ticks(ModPerks.RUNES_RECHARGE, tier);
        if (data.isAddonActive(ModAddons.CHEAP_PAPER)) {
            recharge = Math.max(1, (int) Math.round(recharge * ModAddons.CHEAP_PAPER_RECHARGE));
        }
        boolean changed = false;

        // Short of a rune always means a timer running towards the next one, so they come back one
        // at a time whether the player has spent one or all of them.
        if (charges < max && data.perkRechargeAt(ModPerks.HEALING_RUNES.id()) <= 0L) {
            data.setPerkRechargeAt(ModPerks.HEALING_RUNES.id(), gameTime + recharge);
            changed = true;
        }
        long due = data.perkRechargeAt(ModPerks.HEALING_RUNES.id());
        if (charges < max && due > 0L && gameTime >= due) {
            charges += 1.0F;
            data.setPerkCharges(ModPerks.HEALING_RUNES.id(), charges);
            data.setPerkRechargeAt(ModPerks.HEALING_RUNES.id(),
                    charges < max ? gameTime + recharge : 0L);
            changed = true;
        }

        int gate = charges < 1.0F
                ? (int) Math.max(1L, data.perkRechargeAt(ModPerks.HEALING_RUNES.id()) - gameTime)
                : 0;
        if (data.getCooldownRemaining(ModPerks.HEALING_RUNES.id(), gameTime) != gate) {
            data.setCooldown(ModPerks.HEALING_RUNES.id(), gameTime, gate);
            changed = true;
        }

        if (changed || gameTime % 20L == 0L) {
            sendCharges(player, data, tier, max);
        }
        if (changed) {
            PerkDataManager.sync(player);
        }
    }

    /** Steps every rune in the air. Called once a tick for the whole server. */
    public static void tickRunes(MinecraftServer server) {
        if (IN_FLIGHT.isEmpty()) {
            return;
        }
        Iterator<Rune> iterator = IN_FLIGHT.iterator();
        while (iterator.hasNext()) {
            Rune rune = iterator.next();
            ServerLevel level = server.getLevel(rune.dimension);
            if (level == null || !advance(level, rune)) {
                iterator.remove();
            }
        }
    }

    /** @return false once the rune has landed or run out of life */
    private static boolean advance(ServerLevel level, Rune rune) {
        if (--rune.ticksLeft <= 0) {
            return false;
        }
        Vec3 step = rune.velocity.scale(1.0D / SUB_STEPS);

        for (int i = 0; i < SUB_STEPS; i++) {
            Vec3 from = rune.position;
            Vec3 to = from.add(step);

            // Living things first: a rune is for people, and a wall behind them must not get it.
            LivingEntity hit = firstEntity(level, rune, from, to);
            if (hit != null) {
                mend(level, rune, hit);
                return false;
            }

            BlockHitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (wall.getType() != HitResult.Type.MISS) {
                if (rune.bankBounces > 0) {
                    // Bank Shot: off the wall, and onto whoever is in the cone on the way out.
                    rune.bankBounces--;
                    rune.bounced = true;
                    Direction face = wall.getDirection();
                    rune.position = wall.getLocation().add(Vec3.atLowerCornerOf(face.getNormal())
                            .scale(ModPerks.RUNES_RADIUS * 0.5D + 0.05D));
                    rune.velocity = BankShotHandler.reflect(rune.velocity, face);
                    ServerPlayer owner = level.getServer().getPlayerList().getPlayer(rune.ownerId);
                    LivingEntity mark = BankShotHandler.seek(level, rune.position, rune.velocity, owner);
                    if (mark != null) {
                        rune.velocity = BankShotHandler.steer(rune.position, rune.velocity, mark);
                    }
                    level.playSound(null, rune.position.x, rune.position.y, rune.position.z,
                            SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.8F, 1.6F);
                    return true;
                }
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, to.x, to.y, to.z, 6,
                        0.1D, 0.1D, 0.1D, 0.02D);
                if (rune.selfHeal) {
                    mendThrower(level, rune, wall.getLocation());
                }
                level.playSound(null, to.x, to.y, to.z, SoundEvents.AMETHYST_BLOCK_BREAK,
                        SoundSource.PLAYERS, 0.6F, 1.4F);
                return false;
            }
            rune.position = to;
        }

        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, rune.position.x, rune.position.y, rune.position.z,
                1, 0.02D, 0.02D, 0.02D, 0.0D);
        // A soft ring while it flies, so a rune coming at you is heard before it is seen.
        if (rune.ticksLeft % 10 == 0) {
            level.playSound(null, rune.position.x, rune.position.y, rune.position.z,
                    SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.35F, 1.8F);
        }
        return true;
    }

    private static LivingEntity firstEntity(ServerLevel level, Rune rune, Vec3 from, Vec3 to) {
        AABB swept = new AABB(from, to).inflate(ModPerks.RUNES_RADIUS);
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, swept)) {
            if (!candidate.isAlive() || candidate.getUUID().equals(rune.ownerId)) {
                continue;
            }
            double distance = candidate.getBoundingBox().getCenter().distanceToSqr(from);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static void mend(ServerLevel level, Rune rune, LivingEntity target) {
        float heal = (float) ModPerks.HEALING_RUNES.value(ModPerks.RUNES_HEAL, rune.tier) * rune.healScale;
        boolean hurt = target.getHealth() < target.getMaxHealth();
        target.heal(heal);

        // A rune landing on a player is healing them, for the perks that pay out on that.
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(rune.ownerId);
        if (owner != null && rune.bounced) {
            BankShotHandler.checkTrickShot(owner, target);
        }
        if (owner != null && target instanceof ServerPlayer patient) {
            GreenHerbs.recordHealing(owner, patient, heal);
            TeamSpirit.recordHealing(owner, patient, heal);
            if (hurt && owner.distanceTo(patient) > BACKGROUND_PLAYER_RANGE) {
                ModAdvancements.grant(owner, ModAdvancements.BACKGROUND_PLAYER);
            }
        }
        level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK,
                SoundSource.PLAYERS, 0.7F, 1.5F);

        level.sendParticles(ParticleTypes.HEART, target.getX(), target.getEyeY(), target.getZ(),
                5, 0.3D, 0.2D, 0.3D, 0.0D);
        level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.8F, 1.2F);
    }

    /**
     * Decorated Leaf: a rune broken on the ground at its thrower's feet mends them instead, at half
     * what it would have done for anybody else.
     */
    private static void mendThrower(ServerLevel level, Rune rune, Vec3 where) {
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(rune.ownerId);
        if (owner == null || owner.level() != level
                || owner.position().distanceTo(where) > ModAddons.DECORATED_LEAF_FEET_RANGE) {
            return;
        }
        float heal = (float) ModPerks.HEALING_RUNES.value(ModPerks.RUNES_HEAL, rune.tier)
                * rune.healScale / ModAddons.DECORATED_LEAF_OTHER_HEAL * ModAddons.DECORATED_LEAF_SELF_HEAL;
        owner.heal(heal);
        level.sendParticles(ParticleTypes.HEART, owner.getX(), owner.getEyeY(), owner.getZ(),
                3, 0.3D, 0.2D, 0.3D, 0.0D);
    }

    private static void sendCharges(ServerPlayer player, PlayerPerkData data, int tier, float max) {
        float charges = data.perkCharges(ModPerks.HEALING_RUNES.id(), max);
        PacketDistributor.sendToPlayer(player, new PerkChargesPayload(ModPerks.HEALING_RUNES.id(),
                (int) charges, (int) max,
                charges < max ? data.perkRechargeAt(ModPerks.HEALING_RUNES.id()) : 0L));
    }

    /** Drops anything still in the air for a player who has left. */
    public static void clear(UUID playerId) {
        IN_FLIGHT.removeIf(rune -> rune.ownerId.equals(playerId));
    }
}
