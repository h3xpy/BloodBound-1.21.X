package net.h3xpy.bloodbound.entity;

import org.joml.Vector3f;

import net.h3xpy.bloodbound.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Sacrificial Knife's knife, as the world sees it. It does nothing by itself: the perk flies it,
 * sticks it and takes it away. It only carries what the client needs to draw it — where it points,
 * and, once stuck, what it is stuck in and where, in that thing's own frame so the knife turns with
 * it.
 * <p>
 * Never saved: a knife is a few seconds of a fight, not something to find again after a restart.
 */
public class KnifeEntity extends Entity {

    /** What it is stuck in, as an entity id, or -1 while it flies or sits in a wall. */
    private static final EntityDataAccessor<Integer> DATA_STUCK_TO =
            SynchedEntityData.defineId(KnifeEntity.class, EntityDataSerializers.INT);
    /** Where it went in and which way it points, in the stuck-to thing's frame (turned by its body yaw). */
    private static final EntityDataAccessor<Vector3f> DATA_OFFSET =
            SynchedEntityData.defineId(KnifeEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> DATA_DIRECTION =
            SynchedEntityData.defineId(KnifeEntity.class, EntityDataSerializers.VECTOR3);

    /** Server side: ticks before a knife left in a wall goes, or -1 for never on its own. */
    private int lifeLeft = -1;

    public KnifeEntity(EntityType<? extends KnifeEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public KnifeEntity(Level level, Vec3 position, Vec3 direction) {
        this(ModEntities.KNIFE.get(), level);
        setPos(position);
        point(direction);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_STUCK_TO, -1);
        builder.define(DATA_OFFSET, new Vector3f());
        builder.define(DATA_DIRECTION, new Vector3f(0.0F, 0.0F, 1.0F));
    }

    /** Points the knife along this direction, in the world: its flight, or where it went into a wall. */
    public void point(Vec3 direction) {
        Vec3 d = direction.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : direction.normalize();
        setYRot((float) (Mth.atan2(d.x, d.z) * Mth.RAD_TO_DEG));
        setXRot((float) (Math.asin(Mth.clamp(d.y, -1.0D, 1.0D)) * Mth.RAD_TO_DEG));
        entityData.set(DATA_DIRECTION, new Vector3f((float) d.x, (float) d.y, (float) d.z));
    }

    /** Sticks it in a living thing, at this point of it, pointing this way, both in the world's frame. */
    public void stickTo(LivingEntity target, Vec3 hitPoint, Vec3 direction) {
        float yaw = target.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 offset = hitPoint.subtract(target.position()).yRot(yaw);
        Vec3 pointing = direction.normalize().yRot(yaw);
        entityData.set(DATA_STUCK_TO, target.getId());
        entityData.set(DATA_OFFSET, new Vector3f((float) offset.x, (float) offset.y, (float) offset.z));
        entityData.set(DATA_DIRECTION, new Vector3f((float) pointing.x, (float) pointing.y, (float) pointing.z));
        follow(target);
    }

    /** Leaves it where it is for this long, then takes it away: a knife in a wall. */
    public void leaveFor(int ticks) {
        this.lifeLeft = ticks;
    }

    public int stuckTo() {
        return entityData.get(DATA_STUCK_TO);
    }

    /** Where it went in, in the stuck-to thing's frame. */
    public Vec3 localOffset() {
        Vector3f v = entityData.get(DATA_OFFSET);
        return new Vec3(v.x(), v.y(), v.z());
    }

    /** Which way it points: in the world while it flies, in the stuck-to thing's frame once stuck. */
    public Vec3 direction() {
        Vector3f v = entityData.get(DATA_DIRECTION);
        return new Vec3(v.x(), v.y(), v.z());
    }

    /** Keeps the entity itself on the thing it is stuck in, so it stays tracked and in view. */
    public void follow(LivingEntity target) {
        Vec3 world = localOffset().yRot(-target.yBodyRot * Mth.DEG_TO_RAD);
        setPos(target.position().add(world));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && lifeLeft > 0 && --lifeLeft == 0) {
            discard();
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
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
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
