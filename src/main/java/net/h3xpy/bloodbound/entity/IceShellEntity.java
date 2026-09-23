package net.h3xpy.bloodbound.entity;

import net.h3xpy.bloodbound.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The block of ice a player seals themselves in.
 * <p>
 * An entity rather than real ice: the perk must not touch the world, and translucent faces drawn
 * without culling mean the player can still see out of it — which a solid block never allowed.
 * It holds no state of its own; {@code IceBlock} moves it and takes it away.
 */
public class IceShellEntity extends Entity {

    public IceShellEntity(EntityType<? extends IceShellEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public IceShellEntity(Level level) {
        this(ModEntities.ICE_SHELL.get(), level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // Every block of ice looks the same.
    }

    @Override
    public void tick() {
        setDeltaMovement(0.0D, 0.0D, 0.0D);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Blows land on the player inside, who is the one that shatters it.
        return false;
    }

    @Override
    public boolean isPickable() {
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
