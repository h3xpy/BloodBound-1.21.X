package net.h3xpy.bloodbound.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.advancement.ModAdvancements;
import net.h3xpy.bloodbound.registry.ModEntities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Chained Up's anchor: the point a chained victim is held to, and the thing that has to be broken to
 * set them loose.
 * <p>
 * The chain never moves the victim by teleporting — that stutters on a player's screen. Instead,
 * while they are further out than its length, the part of their motion carrying them away is taken
 * off and a pull back towards the anchor is added, harder the further they strain: a leash, not a
 * wall. Whatever damage it takes, from any source, counts towards breaking it.
 * <p>
 * It belongs to one fight, not to the world, so it is not saved.
 */
public class ChainAnchorEntity extends Entity {

    /** How long a chain has to hold for Ya Ain't Going Nowhere. */
    private static final int HELD_FOR_ACHIEVEMENT_TICKS = 20 * 20;

    private static final DustParticleOptions LINK = new DustParticleOptions(new Vector3f(0.55F, 0.55F, 0.6F), 0.8F);
    /** How far apart the particles drawing the chain are. */
    private static final double LINK_SPACING = 0.3D;

    @Nullable
    private UUID ownerId;
    @Nullable
    private UUID victimId;
    private double length = 3.0D;
    private float health = 6.0F;
    private double pullPerBlock;
    private double pullMax;

    public ChainAnchorEntity(EntityType<? extends ChainAnchorEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public ChainAnchorEntity(Level level, UUID ownerId, LivingEntity victim, double length, float health,
            double pullPerBlock, double pullMax) {
        this(ModEntities.CHAIN_ANCHOR.get(), level);
        this.ownerId = ownerId;
        this.victimId = victim.getUUID();
        this.length = length;
        this.health = health;
        this.pullPerBlock = pullPerBlock;
        this.pullMax = pullMax;
    }

    @Nullable
    public UUID ownerId() {
        return ownerId;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // Drawn the same whatever it holds.
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        Entity found = victimId == null ? null : level.getEntity(victimId);
        if (!(found instanceof LivingEntity victim) || !victim.isAlive()) {
            // Dead, gone to another world, or unloaded: there is nothing left to hold.
            discard();
            return;
        }

        Vec3 anchor = position();
        Vec3 offset = victim.position().subtract(anchor);
        double distance = offset.length();
        if (distance > length && distance > 1.0E-4D) {
            Vec3 away = offset.scale(1.0D / distance);
            Vec3 motion = victim.getDeltaMovement();
            double outward = motion.dot(away);
            if (outward > 0.0D) {
                motion = motion.subtract(away.scale(outward));
            }
            double pull = Math.min(pullMax, (distance - length) * pullPerBlock);
            victim.setDeltaMovement(motion.subtract(away.scale(pull)));
            victim.hurtMarked = true;
        }

        if (tickCount % 2 == 0) {
            drawChain(level, anchor.add(0.0D, 0.3D, 0.0D), victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D));
        }

        // Ya Ain't Going Nowhere: held twenty seconds.
        if (tickCount == HELD_FOR_ACHIEVEMENT_TICKS) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
            if (owner != null) {
                ModAdvancements.grant(owner, ModAdvancements.YA_AINT_GOING_NOWHERE);
            }
        }
    }

    private static void drawChain(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 span = to.subtract(from);
        int links = Math.min(80, (int) Math.ceil(span.length() / LINK_SPACING));
        for (int i = 0; i <= links; i++) {
            Vec3 point = from.add(span.scale(links == 0 ? 0.0D : i / (double) links));
            level.sendParticles(LINK, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /** Every hit counts, from anything; enough of them and the chain lets go. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!(level() instanceof ServerLevel level) || isRemoved() || amount <= 0.0F) {
            return false;
        }
        health -= amount;
        level.playSound(null, blockPosition(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.8F, 1.0F);
        if (health <= 0.0F) {
            level.playSound(null, blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.0F, 0.8F);
            level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 0.3D, getZ(), 12, 0.2D, 0.2D, 0.2D, 0.1D);
            discard();
        }
        return true;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
