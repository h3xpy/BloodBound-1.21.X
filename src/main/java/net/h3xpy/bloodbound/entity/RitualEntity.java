package net.h3xpy.bloodbound.entity;

import net.h3xpy.bloodbound.registry.ModEntities;
import net.h3xpy.bloodbound.ritual.RitualManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * What a ritual, or an Eavesdrop trap, looks like on the ground.
 * <p>
 * It holds nothing and does nothing: {@code RitualManager} owns the ritual itself and this is only
 * there to be seen. An entity rather than a block because an aura can be shown on one — the glow is
 * a flag on an entity, and a ritual whose aura can be revealed has to be something that can carry it.
 */
public class RitualEntity extends Entity {

    public RitualEntity(EntityType<? extends RitualEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public RitualEntity(Level level) {
        this(ModEntities.RITUAL.get(), level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // Nothing to sync: every ritual is drawn the same way.
    }

    @Override
    public void tick() {
        // The manager drives everything; standing still is all this has to do.
        setDeltaMovement(0.0D, 0.0D, 0.0D);
    }

    /** Anything at all that hurts it breaks it: a blow, an arrow, a blast, a fire lit under it. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) {
            return false;
        }
        RitualManager.onMarkerHurt(this);
        return true;
    }

    /** Hittable, so a blow or an arrow can find it at all. */
    @Override
    public boolean isPickable() {
        return !isRemoved();
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
