package net.h3xpy.bloodbound.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.registry.ModEntities;
import net.h3xpy.bloodbound.ritual.RitualManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * What a ritual looks like on the ground, and what it is when nobody is holding it in memory.
 * <p>
 * {@code RitualManager} owns the ritual while it runs and this is mostly there to be seen. An entity
 * rather than a block because an aura can be shown on one — the glow is a flag on an entity, and a
 * ritual whose aura can be revealed has to be something that can carry it.
 * <p>
 * It is also how a ritual outlives its owner's session: the marker is saved with its chunk, carrying
 * everything the ritual was, and the manager builds the ritual back from it when the chunk loads.
 */
public class RitualEntity extends Entity {

    @Nullable
    private UUID ownerId;
    @Nullable
    private ResourceLocation perkId;
    private RitualManager.Mood mood = RitualManager.Mood.HARMFUL;
    private int tier = 1;
    private double radius;
    private float charges;
    /** The most charges it can hold, which a ritual that refills fills back up to. */
    private float capacity;
    private long placedAt;
    /** Whether a Bad Omen has already added to this ritual's charges, so it only ever does once. */
    private boolean omenBoosted;

    public RitualEntity(EntityType<? extends RitualEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public RitualEntity(Level level) {
        this(ModEntities.RITUAL.get(), level);
    }

    /** Writes down the ritual this marker stands for, so it can be saved and read back. */
    public void stamp(UUID ownerId, ResourceLocation perkId, RitualManager.Mood mood, int tier, double radius,
            float charges, long placedAt) {
        this.ownerId = ownerId;
        this.perkId = perkId;
        this.mood = mood;
        this.tier = tier;
        this.radius = radius;
        this.charges = charges;
        this.capacity = charges;
        this.placedAt = placedAt;
    }

    @Nullable
    public UUID ownerId() {
        return ownerId;
    }

    @Nullable
    public ResourceLocation perkId() {
        return perkId;
    }

    public RitualManager.Mood mood() {
        return mood;
    }

    public int tier() {
        return tier;
    }

    public double radius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }

    public float charges() {
        return charges;
    }

    public void setCharges(float charges) {
        this.charges = charges;
    }

    public float capacity() {
        return capacity;
    }

    public void setCapacity(float capacity) {
        this.capacity = capacity;
    }

    public long placedAt() {
        return placedAt;
    }

    public boolean omenBoosted() {
        return omenBoosted;
    }

    public void setOmenBoosted(boolean omenBoosted) {
        this.omenBoosted = omenBoosted;
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
        RitualManager.onMarkerHurt(this, source);
        return true;
    }

    /** Hittable, so a blow or an arrow can find it at all. */
    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        perkId = ResourceLocation.tryParse(tag.getString("Perk"));
        mood = tag.getBoolean("Helpful") ? RitualManager.Mood.HELPFUL : RitualManager.Mood.HARMFUL;
        tier = Math.max(1, tag.getInt("Tier"));
        radius = tag.getDouble("Radius");
        charges = tag.getFloat("Charges");
        capacity = tag.contains("Capacity") ? tag.getFloat("Capacity") : charges;
        placedAt = tag.getLong("PlacedAt");
        omenBoosted = tag.getBoolean("OmenBoosted");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) {
            tag.putUUID("Owner", ownerId);
        }
        if (perkId != null) {
            tag.putString("Perk", perkId.toString());
        }
        tag.putBoolean("Helpful", mood == RitualManager.Mood.HELPFUL);
        tag.putInt("Tier", tier);
        tag.putDouble("Radius", radius);
        tag.putFloat("Charges", charges);
        tag.putFloat("Capacity", capacity);
        tag.putLong("PlacedAt", placedAt);
        tag.putBoolean("OmenBoosted", omenBoosted);
    }
}
