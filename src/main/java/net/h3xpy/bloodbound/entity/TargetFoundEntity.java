package net.h3xpy.bloodbound.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.perk.impl.TargetFound;
import net.h3xpy.bloodbound.registry.ModEntities;
import net.h3xpy.bloodbound.ritual.TrapRoster;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * A Target Found tripwire. It does nothing to what crosses it — it only tells its owner, and gives
 * them a way to get there.
 * <p>
 * Once tripped it goes quiet: the owner's window runs from {@link TargetFound}, which is also what
 * takes the wire away once that window is over.
 */
public class TargetFoundEntity extends Entity implements TrapRoster.Trap {

    public static final String KIND = "target_found";

    private static final EntityDataAccessor<Integer> DATA_OPACITY =
            SynchedEntityData.defineId(TargetFoundEntity.class, EntityDataSerializers.INT);

    /** How long the wire takes to arm itself, so nobody trips it laying it. */
    private static final int ARM_TICKS = 10;

    @Nullable
    private UUID ownerId;
    private boolean tripped;
    private int tier = 1;
    private long placedAt;

    public TargetFoundEntity(EntityType<? extends TargetFoundEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public TargetFoundEntity(Level level, ServerPlayer owner, int tier) {
        this(ModEntities.TARGET_FOUND.get(), level);
        this.ownerId = owner.getUUID();
        this.tier = tier;
        this.placedAt = level.getGameTime();
        applyTier();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OPACITY, 30);
    }

    public float opacity() {
        return Math.clamp(entityData.get(DATA_OPACITY) / 100.0F, 0.01F, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel) || tripped || tickCount < ARM_TICKS) {
            return;
        }

        // The wire is only a way back to its owner, so with the owner away it has nothing to do but
        // wait, still armed, for them to come back.
        if (ownerId == null || serverLevel.getServer().getPlayerList().getPlayer(ownerId) == null) {
            return;
        }
        AABB reach = getBoundingBox().inflate(ModPerks.TARGET_TRAP_RADIUS, 0.3D, ModPerks.TARGET_TRAP_RADIUS);
        for (LivingEntity victim : serverLevel.getEntitiesOfClass(LivingEntity.class, reach)) {
            if (!victim.isAlive() || victim.isSpectator() || victim.getUUID().equals(ownerId)) {
                continue;
            }
            tripped = true;
            TargetFound.onTripped(serverLevel, this, victim);
            return;
        }
    }

    @Nullable
    public UUID ownerId() {
        return ownerId;
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

    private void applyTier() {
        this.entityData.set(DATA_OPACITY, ModPerks.TARGET_FOUND.intValue(ModPerks.TARGET_OPACITY, tier));
    }

    @Override
    public String trapKind() {
        return KIND;
    }

    @Override
    @Nullable
    public UUID trapOwnerId() {
        return ownerId;
    }

    @Override
    public long placedAt() {
        return placedAt;
    }

    @Override
    public int trapLimit() {
        return ModPerks.TARGET_FOUND.intValue(ModPerks.TARGET_TRAPS, tier);
    }

    /** A wire already tripped was only waiting on its owner's window; it does not come back. */
    @Override
    public boolean isSpent() {
        return tripped;
    }

    // Saved with the world, so a wire outlasts its owner's logout and a restart alike.
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        tier = Math.max(1, tag.getInt("Tier"));
        placedAt = tag.getLong("PlacedAt");
        tripped = tag.getBoolean("Tripped");
        applyTier();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) {
            tag.putUUID("Owner", ownerId);
        }
        tag.putInt("Tier", tier);
        tag.putLong("PlacedAt", placedAt);
        tag.putBoolean("Tripped", tripped);
    }
}
