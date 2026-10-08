package net.h3xpy.bloodbound.entity;

import org.joml.Vector3f;

import net.h3xpy.bloodbound.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What a Holy Sanctum looks like: a shield bubble everybody can see.
 * <p>
 * It does nothing itself — {@code HolySanctum} keeps the bubble and decides what it stops. This only
 * carries what the client needs to draw it: how big it is, how much of its health is left, and where
 * and when it was last struck, so the hit can light up on that side of the shell.
 */
public class SanctumBubbleEntity extends Entity {

    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(SanctumBubbleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEALTH =
            SynchedEntityData.defineId(SanctumBubbleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Vector3f> DATA_HIT_DIRECTION =
            SynchedEntityData.defineId(SanctumBubbleEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> DATA_HITS =
            SynchedEntityData.defineId(SanctumBubbleEntity.class, EntityDataSerializers.INT);

    /** Client only: the tick the last hit arrived, which the flash fades from. */
    private int lastHitTick = -1000;

    public SanctumBubbleEntity(EntityType<? extends SanctumBubbleEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        // Far bigger than its own hitbox: never let the renderer think it is off screen.
        this.noCulling = true;
    }

    public SanctumBubbleEntity(Level level, Vec3 center, double radius) {
        this(ModEntities.SANCTUM_BUBBLE.get(), level);
        moveTo(center.x, center.y, center.z, 0.0F, 0.0F);
        entityData.set(DATA_RADIUS, (float) radius);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RADIUS, 3.0F);
        builder.define(DATA_HEALTH, 1.0F);
        builder.define(DATA_HIT_DIRECTION, new Vector3f(0.0F, 1.0F, 0.0F));
        builder.define(DATA_HITS, 0);
    }

    /** A hit from this direction, as seen from the centre, leaving this share of its health. */
    public void onHit(Vec3 direction, float healthShare) {
        Vec3 unit = direction.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 1.0D, 0.0D) : direction.normalize();
        entityData.set(DATA_HIT_DIRECTION, new Vector3f((float) unit.x, (float) unit.y, (float) unit.z));
        entityData.set(DATA_HEALTH, Math.clamp(healthShare, 0.0F, 1.0F));
        entityData.set(DATA_HITS, entityData.get(DATA_HITS) + 1);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_HITS.equals(key) && level().isClientSide) {
            lastHitTick = tickCount;
        }
    }

    public float radius() {
        return entityData.get(DATA_RADIUS);
    }

    public float healthShare() {
        return entityData.get(DATA_HEALTH);
    }

    public Vector3f hitDirection() {
        return entityData.get(DATA_HIT_DIRECTION);
    }

    /** Ticks since the last hit landed, as the client saw it. */
    public float ticksSinceHit(float partialTick) {
        return tickCount - lastHitTick + partialTick;
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(radius() + 1.0D);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128.0D * 128.0D;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
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
