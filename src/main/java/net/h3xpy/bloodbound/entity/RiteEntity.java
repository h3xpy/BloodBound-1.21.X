package net.h3xpy.bloodbound.entity;

import org.joml.Vector3f;

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
 * The few seconds of light or fire that consecrate a ritual as it is laid. It does nothing: it is
 * there to be drawn, by {@code RiteRenderer}, and to throw its particles about on the client.
 * <p>
 * A helpful ritual is blessed: a shaft of light comes down onto it, soft rings bloom out to its edge,
 * rays turn across it and a halo hangs over it while motes of light drift up. A harmful one is
 * cursed: the ground splits open from the middle in glowing cracks out to the edge, a ring of fire
 * burns in round it, and smoke and embers boil up.
 * <p>
 * Never saved: it lasts a few seconds.
 */
public class RiteEntity extends Entity {

    /** How long the whole rite lasts, and when it is at its height. */
    public static final int LIFE_TICKS = 70;
    public static final int PEAK_TICKS = 25;

    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(RiteEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_BLESSED =
            SynchedEntityData.defineId(RiteEntity.class, EntityDataSerializers.BOOLEAN);

    private static final DustParticleOptions EMBER = new DustParticleOptions(new Vector3f(0.95F, 0.25F, 0.05F), 1.0F);
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.45F, 0.0F, 0.0F), 1.4F);

    public RiteEntity(EntityType<? extends RiteEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public RiteEntity(Level level, double x, double y, double z, float radius, boolean blessed) {
        this(ModEntities.RITE.get(), level);
        setPos(x, y, z);
        entityData.set(DATA_RADIUS, radius);
        entityData.set(DATA_BLESSED, blessed);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RADIUS, 8.0F);
        builder.define(DATA_BLESSED, true);
    }

    public float radius() {
        return entityData.get(DATA_RADIUS);
    }

    public boolean blessed() {
        return entityData.get(DATA_BLESSED);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            if (tickCount > LIFE_TICKS) {
                discard();
            }
            return;
        }
        if (tickCount > LIFE_TICKS - 15) {
            return;
        }
        float radius = radius();
        if (blessed()) {
            // Motes of light drifting up off the whole circle, and a few soul flames.
            for (int i = 0; i < 4; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double r = radius * Math.sqrt(random.nextDouble());
                level().addParticle(i == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.END_ROD,
                        getX() + Math.cos(angle) * r, getY() + 0.1D, getZ() + Math.sin(angle) * r,
                        0.0D, 0.03D + random.nextDouble() * 0.04D, 0.0D);
            }
            if (tickCount == 14) {
                // The light lands: a burst at the heart.
                for (int i = 0; i < 40; i++) {
                    level().addParticle(ParticleTypes.END_ROD, getX(), getY() + 0.2D, getZ(),
                            (random.nextDouble() - 0.5D) * 0.5D, random.nextDouble() * 0.3D, (random.nextDouble() - 0.5D) * 0.5D);
                }
            }
        } else {
            // Smoke and embers boiling up out of the cracks, the odd drip of blood and lava.
            for (int i = 0; i < 4; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double r = radius * Math.min(1.0D, tickCount / (double) PEAK_TICKS) * Math.sqrt(random.nextDouble());
                double x = getX() + Math.cos(angle) * r;
                double z = getZ() + Math.sin(angle) * r;
                switch (i) {
                    case 0 -> level().addParticle(ParticleTypes.LARGE_SMOKE, x, getY() + 0.1D, z, 0.0D, 0.04D, 0.0D);
                    case 1 -> level().addParticle(ParticleTypes.FLAME, x, getY() + 0.1D, z, 0.0D, 0.05D, 0.0D);
                    case 2 -> level().addParticle(EMBER, x, getY() + 0.1D, z, 0.0D, 0.08D, 0.0D);
                    default -> level().addParticle(random.nextInt(6) == 0 ? ParticleTypes.LAVA : BLOOD,
                            x, getY() + 0.1D, z, 0.0D, 0.05D, 0.0D);
                }
            }
        }
    }

    // Far wider than its own box: drawn as long as any of it could be on screen.
    @Override
    public AABB getBoundingBoxForCulling() {
        float r = radius() * 1.2F;
        return new AABB(getX() - r, getY() - 1.0D, getZ() - r, getX() + r, getY() + 14.0D, getZ() + r);
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
