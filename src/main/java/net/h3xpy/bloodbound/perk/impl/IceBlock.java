package net.h3xpy.bloodbound.perk.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.entity.IceShellEntity;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ice Block: seal yourself in, and for a few seconds nothing can reach you and you can do nothing.
 * <p>
 * The ice is an entity, not blocks: the perk leaves the world exactly as it found it. Being
 * untouchable is a cancelled damage event rather than invulnerability, so the blow still counts —
 * it is what shatters the ice and throws everything nearby off its feet. The key breaks it early
 * instead, which costs the knockback and is what Shattered Mask pays for.
 */
public final class IceBlock {

    /** One player sealed in: the block of ice round them, and when it melts. */
    private record Encasement(IceShellEntity shell, long endsAt, int tier) {}

    /** Absorption won from Gabriel's Ring, and the tick it is taken back on. */
    private record Boon(float amount, long expiresAt) {}

    private static final ResourceLocation RING_CEILING_ID =
            ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "gabriels_ring");

    /** Slowness and jump levels deep enough that a player in the ice cannot shift at all. */
    private static final int FREEZE_SLOWNESS_LEVEL = 8;
    private static final int FREEZE_JUMP_LEVEL = 200;

    private static final Map<UUID, Encasement> SEALED = new HashMap<>();
    private static final Map<UUID, Boon> BOONS = new HashMap<>();

    private IceBlock() {}

    public static boolean isSealed(ServerPlayer player) {
        return SEALED.containsKey(player.getUUID());
    }

    /** The key: seals the player in, or breaks the block they are already in. */
    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        if (SEALED.containsKey(player.getUUID())) {
            shatter(player, data, gameTime, false);
            return true;
        }
        return encase(player, data, tier, gameTime);
    }

    /**
     * Puts the ice round the player. It is an entity standing where they stand: the perk never
     * touches the world, and its faces are drawn from both sides, so being inside it still leaves
     * the player able to watch what is happening to them.
     */
    private static boolean encase(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        ServerLevel level = player.serverLevel();

        IceShellEntity shell = new IceShellEntity(level);
        shell.moveTo(player.getX(), player.getY(), player.getZ(), 0.0F, 0.0F);
        level.addFreshEntity(shell);

        int ticks = ModPerks.ICE_BLOCK.ticks(ModPerks.ICE_BLOCK_SECONDS, tier);
        SEALED.put(player.getUUID(), new Encasement(shell, gameTime + ticks, tier));

        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, ticks,
                ModPerks.ICE_BLOCK_REGEN_LEVEL - 1, false, true, true));
        // Held still by effects rather than by being put back where they were every tick: the client
        // decides movement, and fighting it over the wire is what made it shake.
        freeze(player, ticks);
        if (data.isAddonActive(ModAddons.BLOODIED_BANDAGES)) {
            player.heal(ModAddons.BLOODIED_BANDAGES_HEAL);
        }

        level.playSound(null, player.blockPosition(), SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 1.0F, 0.7F);
        return true;
    }

    /** Slowness deep enough to stop a player dead, and a jump they cannot make. */
    private static void freeze(ServerPlayer player, int ticks) {
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks,
                FREEZE_SLOWNESS_LEVEL - 1, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP, ticks,
                FREEZE_JUMP_LEVEL - 1, false, false, false));
        player.setSprinting(false);
    }

    private static void thaw(ServerPlayer player) {
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.JUMP);
    }

    /** Holds the player still while the ice holds. Called once a tick. */
    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        expireAbsorption(player, gameTime);

        Encasement sealed = SEALED.get(player.getUUID());
        if (sealed == null) {
            return;
        }
        if (gameTime >= sealed.endsAt() || data.getActiveTier(ModPerks.ICE_BLOCK) <= 0) {
            shatter(player, data, gameTime, false);
            return;
        }

        // The ice follows the player: falling is the one thing they can still do in it.
        sealed.shell().moveTo(player.getX(), player.getY(), player.getZ(), 0.0F, 0.0F);
        player.fallDistance = 0.0F;

        if (gameTime % 4L == 0L) {
            player.serverLevel().sendParticles(ParticleTypes.SNOWFLAKE, player.getX(),
                    player.getY() + 1.0D, player.getZ(), 3, 0.3D, 0.5D, 0.3D, 0.01D);
        }
    }

    /**
     * A blow aimed at a sealed player: the ice takes it instead.
     *
     * @return true when the damage was swallowed and the caller should cancel it
     */
    public static boolean absorbDamage(ServerPlayer player, long gameTime) {
        if (!SEALED.containsKey(player.getUUID())) {
            return false;
        }
        shatter(player, PerkDataManager.get(player), gameTime, true);
        return true;
    }

    /**
     * Breaks the ice and starts the cooldown.
     *
     * @param struck whether something broke it rather than the player letting it go
     */
    public static void shatter(ServerPlayer player, PlayerPerkData data, long gameTime, boolean struck) {
        Encasement sealed = SEALED.remove(player.getUUID());
        if (sealed == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        sealed.shell().discard();

        player.removeEffect(MobEffects.REGENERATION);
        thaw(player);
        level.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2F, 0.8F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()),
                player.getX(), player.getY() + 1.0D, player.getZ(), 40, 0.4D, 0.6D, 0.4D, 0.15D);

        if (struck) {
            knockBack(player, level);
            if (data.isAddonActive(ModAddons.GABRIELS_RING)) {
                blindOnlookers(player, level, gameTime);
            }
        } else if (data.isAddonActive(ModAddons.SHATTERED_MASK)) {
            dash(player);
        }

        data.setCooldown(ModPerks.ICE_BLOCK.id(), gameTime,
                ModPerks.ICE_BLOCK.cooldownTicks(sealed.tier()));
        PerkDataManager.sync(player);
    }

    /** The shards throw everything standing round the block off its feet. */
    private static void knockBack(ServerPlayer player, ServerLevel level) {
        double radius = ModPerks.ICE_BLOCK_SHATTER_RADIUS;
        AABB box = player.getBoundingBox().inflate(radius);
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (nearby == player || !nearby.isAlive()) {
                continue;
            }
            Vec3 away = nearby.position().subtract(player.position());
            if (away.lengthSqr() < 1.0E-4D) {
                away = new Vec3(0.0D, 0.0D, 1.0D);
            }
            if (away.length() > radius) {
                continue;
            }
            Vec3 push = away.normalize().scale(ModPerks.ICE_BLOCK_KNOCKBACK);
            nearby.push(push.x, ModPerks.ICE_BLOCK_KNOCKBACK_LIFT, push.z);
            nearby.hurtMarked = true;
        }
    }

    /** Shattered Mask: come out of it moving, and off the ground. */
    private static void dash(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        // Forward and up are worked out apart, so where the player is looking decides the direction
        // of the dash without ever eating into the lift.
        Vec3 forward = new Vec3(look.x, 0.0D, look.z);
        forward = forward.lengthSqr() < 1.0E-6D ? Vec3.ZERO
                : forward.normalize().scale(ModAddons.SHATTERED_MASK_DASH * 0.16D);
        // A push, not a teleport: it should carry, and it should be stopped by a wall.
        player.push(forward.x, ModAddons.SHATTERED_MASK_LIFT, forward.z);
        player.hurtMarked = true;
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.8F, 1.4F);
    }

    /**
     * Gabriel's Ring: the ice going off in somebody's face is a flash of its own, and every pair of
     * eyes it catches is worth a heart.
     */
    private static void blindOnlookers(ServerPlayer player, ServerLevel level, long gameTime) {
        double range = ModAddons.GABRIELS_RING_RANGE;
        Vec3 blast = player.getEyePosition();
        int caught = 0;

        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(blast, range * 2, range * 2, range * 2))) {
            if (nearby == player || !nearby.isAlive()) {
                continue;
            }
            double distance = nearby.getEyePosition().distanceTo(blast);
            if (distance > range || !Flashbang.isLookingAt(nearby, blast)
                    || !Flashbang.canSee(level, blast, nearby)) {
                continue;
            }
            double seconds = ModAddons.GABRIELS_RING_BASE_SECONDS
                    - distance * ModAddons.GABRIELS_RING_FALLOFF_PER_BLOCK;
            int ticks = (int) Math.round(seconds * 20.0D);
            if (ticks <= 0) {
                continue;
            }
            Flashbang.blind(player, nearby, ticks);
            caught++;
        }

        if (caught > 0) {
            grantAbsorption(player, caught * ModAddons.GABRIELS_RING_ABSORPTION, gameTime);
        }
    }

    /** Hearts that are ours to give and ours to take back, on their own ceiling and their own clock. */
    private static void grantAbsorption(ServerPlayer player, float amount, long gameTime) {
        AttributeInstance ceiling = player.getAttribute(Attributes.MAX_ABSORPTION);
        if (ceiling == null) {
            return;
        }
        Boon current = BOONS.get(player.getUUID());
        float total = amount + (current == null ? 0.0F : current.amount());

        ceiling.removeModifier(RING_CEILING_ID);
        ceiling.addTransientModifier(new AttributeModifier(RING_CEILING_ID, total,
                AttributeModifier.Operation.ADD_VALUE));
        player.setAbsorptionAmount(player.getAbsorptionAmount() + amount);
        BOONS.put(player.getUUID(), new Boon(total, gameTime + ModAddons.GABRIELS_RING_ABSORPTION_TICKS));
    }

    private static void expireAbsorption(ServerPlayer player, long gameTime) {
        Boon boon = BOONS.get(player.getUUID());
        if (boon == null || gameTime < boon.expiresAt()) {
            return;
        }
        BOONS.remove(player.getUUID());
        AttributeInstance ceiling = player.getAttribute(Attributes.MAX_ABSORPTION);
        if (ceiling != null) {
            ceiling.removeModifier(RING_CEILING_ID);
        }
        // Only what this gave: absorption from a potion is not ours to take.
        player.setAbsorptionAmount(Math.max(0.0F, player.getAbsorptionAmount() - boon.amount()));
    }

    /**
     * A player leaving or dying in their block: the ice goes with them, since nobody is left to
     * break it. No knockback and no cooldown — there is no one to charge it to.
     */
    public static void release(ServerPlayer player) {
        Encasement sealed = SEALED.remove(player.getUUID());
        if (sealed != null) {
            sealed.shell().discard();
            thaw(player);
        }
        BOONS.remove(player.getUUID());
    }

    /** Forgets a player entirely. The world keeps whatever ice was left, which release avoids. */
    public static void clear(UUID playerId) {
        SEALED.remove(playerId);
        BOONS.remove(playerId);
    }
}
