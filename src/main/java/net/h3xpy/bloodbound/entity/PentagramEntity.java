package net.h3xpy.bloodbound.entity;

import org.joml.Vector3f;

import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.registry.ModEntities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Salvation's pentagram, lying on the ground where it was drawn. It only carries its size; the
 * drawing, the flare when it binds and the fading out all run off its age, on the client.
 * <p>
 * Never saved: it lasts a few seconds.
 */
public class PentagramEntity extends Entity {

    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(PentagramEntity.class, EntityDataSerializers.FLOAT);

    public PentagramEntity(EntityType<? extends PentagramEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public PentagramEntity(Level level, double x, double y, double z, float radius) {
        this(ModEntities.PENTAGRAM.get(), level);
        setPos(x, y, z);
        entityData.set(DATA_RADIUS, radius);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RADIUS, 10.0F);
    }

    public float radius() {
        return entityData.get(DATA_RADIUS);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            if (tickCount > ModPerks.SALVATION_DRAW_TICKS + ModPerks.SALVATION_FADE_TICKS) {
                discard();
            }
            return;
        }
        // Embers drift up off the lines while it is drawn, and it bursts when it binds.
        float radius = radius();
        if (tickCount < ModPerks.SALVATION_DRAW_TICKS) {
            for (int i = 0; i < 3; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double r = radius * (random.nextBoolean() ? 1.0D : 0.82D);
                level().addParticle(EMBER, getX() + Math.cos(angle) * r, getY() + 0.1D, getZ() + Math.sin(angle) * r,
                        0.0D, 0.02D + random.nextDouble() * 0.03D, 0.0D);
            }
        } else if (tickCount == ModPerks.SALVATION_DRAW_TICKS) {
            for (int i = 0; i < 120; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double r = radius * Math.sqrt(random.nextDouble());
                level().addParticle(random.nextInt(4) == 0 ? ParticleTypes.SOUL_FIRE_FLAME : EMBER,
                        getX() + Math.cos(angle) * r, getY() + 0.1D, getZ() + Math.sin(angle) * r,
                        0.0D, 0.08D + random.nextDouble() * 0.12D, 0.0D);
            }
        }
    }

    private static final DustParticleOptions EMBER = new DustParticleOptions(new Vector3f(0.9F, 0.08F, 0.05F), 1.1F);

    // Far wider than its own box: drawn as long as any of it could be on screen.
    @Override
    public AABB getBoundingBoxForCulling() {
        float r = radius() * 1.2F;
        return new AABB(getX() - r, getY() - 1.0D, getZ() - r, getX() + r, getY() + 2.0D, getZ() + r);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128.0D * 128.0D;
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
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
