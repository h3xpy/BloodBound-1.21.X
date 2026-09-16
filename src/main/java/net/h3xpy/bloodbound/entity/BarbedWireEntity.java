package net.h3xpy.bloodbound.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.effect.BleedingHandler;
import net.h3xpy.bloodbound.event.AuraRevealHandler;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * A coil of barbed wire on the floor.
 * <p>
 * An entity rather than a block: it has to sit on top of whatever is already there, be shot at, and
 * belong to somebody. It does not move, does not collide, and is not saved with the world — a trap
 * that outlived the session that laid it would belong to nobody.
 */
public class BarbedWireEntity extends Entity {

    /** Opacity as a percentage, which is the only thing the client needs to draw it. */
    private static final EntityDataAccessor<Integer> DATA_OPACITY =
            SynchedEntityData.defineId(BarbedWireEntity.class, EntityDataSerializers.INT);

    /** How long the wire takes to arm itself, so nobody can drop one under their own feet. */
    private static final int ARM_TICKS = 10;
    /** Ticks between two things being caught, so one coil is not a meat grinder. */
    private static final int TRIGGER_COOLDOWN = 20;

    @Nullable
    private UUID ownerId;
    private int tier = 1;
    private int triggerCooldown;

    public BarbedWireEntity(EntityType<? extends BarbedWireEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public BarbedWireEntity(Level level, ServerPlayer owner, int tier) {
        this(ModEntities.BARBED_WIRE.get(), level);
        this.ownerId = owner.getUUID();
        this.tier = tier;
        this.entityData.set(DATA_OPACITY, ModPerks.BARBED_WIRE.intValue(ModPerks.BARBED_OPACITY, tier));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OPACITY, 30);
    }

    /** Opacity the client should draw it at, as a fraction. */
    public float opacity() {
        return Math.clamp(entityData.get(DATA_OPACITY) / 100.0F, 0.01F, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (triggerCooldown > 0) {
            triggerCooldown--;
        }

        AABB reach = getBoundingBox().inflate(ModPerks.BARBED_RADIUS, 0.3D, ModPerks.BARBED_RADIUS);

        // An item thrown into it smothers the wire and is lost doing it, the way lava eats one.
        for (ItemEntity item : serverLevel.getEntitiesOfClass(ItemEntity.class, reach)) {
            item.discard();
            defuse(serverLevel);
            return;
        }

        if (tickCount < ARM_TICKS || triggerCooldown > 0) {
            return;
        }

        for (LivingEntity victim : serverLevel.getEntitiesOfClass(LivingEntity.class, reach)) {
            if (!victim.isAlive() || victim.getUUID().equals(ownerId)) {
                continue;
            }
            trigger(serverLevel, victim);
            return;
        }
    }

    /** Something walked in. A coil only ever goes off once: it is gone the moment it does. */
    private void trigger(ServerLevel level, LivingEntity victim) {
        triggerCooldown = TRIGGER_COOLDOWN;
        discard();

        ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
        float damage = (float) ModPerks.BARBED_WIRE.value(ModPerks.BARBED_DAMAGE, tier);
        DamageSource source = owner != null
                ? level.damageSources().playerAttack(owner)
                : level.damageSources().generic();
        victim.hurt(source, damage);

        BleedingHandler.apply(victim, ModPerks.BARBED_WIRE.intValue(ModPerks.BARBED_BLEED, tier));

        level.playSound(null, blockPosition(), SoundEvents.SWEET_BERRY_BUSH_BREAK,
                SoundSource.PLAYERS, 0.8F, 0.7F);
        level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 0.2D, getZ(), 12, 0.3D, 0.1D, 0.3D, 0.1D);

        // The point of a trap is knowing it went off, and knowing what set it off.
        if (owner != null) {
            owner.displayClientMessage(Component.translatable("bloodbound.message.barbed_wire_triggered",
                    victim.getName()).withStyle(ChatFormatting.RED), true);
            AuraRevealHandler.reveal(owner, victim, ModPerks.BARBED_REVEAL_TICKS);
        }
    }

    /** The wire coming apart, whether it was shot or smothered. */
    private void defuse(ServerLevel level) {
        level.playSound(null, blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.7F, 1.1F);
        level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.2D, getZ(), 10, 0.3D, 0.1D, 0.3D, 0.02D);
        discard();
    }

    /** Shootable: an arrow in the coil takes it apart. */
    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level() instanceof ServerLevel serverLevel && source.getDirectEntity() instanceof AbstractArrow arrow) {
            arrow.discard();
            defuse(serverLevel);
            return true;
        }
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Nullable
    public UUID ownerId() {
        return ownerId;
    }

    // Traps belong to a session, not to a save file: nothing is read or written.
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
