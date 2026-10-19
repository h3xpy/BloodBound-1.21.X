package net.h3xpy.bloodbound.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.perk.impl.Tripwires;
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
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A Tripwires wire: two posts a block high, and a wire between their tops.
 * <p>
 * The entity stands on the first post. Until the second is planted it is a lone post, which only
 * waits; once it has its other end it watches the wire for anything walking into it. Whatever
 * crosses it standing up sets it off, whatever crouches through only wears it, and either way it
 * breaks once it has been crossed enough times.
 */
public class TripwireEntity extends Entity implements TrapRoster.Trap {

    public static final String KIND = "tripwires";

    /** Where the second post stands, from the first; and whether it stands at all yet. */
    private static final EntityDataAccessor<Vector3f> DATA_END =
            SynchedEntityData.defineId(TripwireEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Boolean> DATA_HAS_END =
            SynchedEntityData.defineId(TripwireEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_OPACITY =
            SynchedEntityData.defineId(TripwireEntity.class, EntityDataSerializers.INT);

    /** How long a fresh wire takes to arm itself, so its owner's own steps never count. */
    private static final int ARM_TICKS = 10;

    @Nullable
    private UUID ownerId;
    private int tier = 1;
    private long placedAt;
    private int triggersLeft;
    /** Silver Wire: the Bleeding this wire's next cut deals. */
    private int nextBleed = -1;
    /** Read back from a save: a lone post found this way was a laying that never finished. */
    private boolean fromDisk;
    /** What is standing in the wire right now, so walking along it counts once, not every tick. */
    private final Set<Integer> inside = new HashSet<>();

    public TripwireEntity(EntityType<? extends TripwireEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public TripwireEntity(Level level, ServerPlayer owner, int tier) {
        this(ModEntities.TRIPWIRE.get(), level);
        this.ownerId = owner.getUUID();
        this.tier = tier;
        this.placedAt = level.getGameTime();
        this.triggersLeft = ModPerks.TRIPWIRES.intValue(ModPerks.TRIPWIRE_TRIGGERS, tier);
        applyTier();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_END, new Vector3f());
        builder.define(DATA_HAS_END, false);
        builder.define(DATA_OPACITY, 30);
    }

    public float opacity() {
        return Math.clamp(entityData.get(DATA_OPACITY) / 100.0F, 0.01F, 1.0F);
    }

    public boolean hasEnd() {
        return entityData.get(DATA_HAS_END);
    }

    /** The second post's foot, from the first's. */
    public Vec3 endOffset() {
        Vector3f end = entityData.get(DATA_END);
        return new Vec3(end.x(), end.y(), end.z());
    }

    /** Plants the second post here, which arms the wire. */
    public void setEnd(Vec3 foot) {
        Vec3 offset = foot.subtract(position());
        entityData.set(DATA_END, new Vector3f((float) offset.x, (float) offset.y, (float) offset.z));
        entityData.set(DATA_HAS_END, true);
        this.placedAt = level().getGameTime();
        this.tickCount = 0;
    }

    /** Both posts count for the spacing between traps, the second once it is in. */
    @Override
    public java.util.List<Vec3> trapPoints() {
        return hasEnd() ? java.util.List.of(position(), position().add(endOffset())) : java.util.List.of(position());
    }

    /** The top of the first post, and of the second: the two ends of the wire. */
    public Vec3 startTop() {
        return position().add(0.0D, ModPerks.TRIPWIRE_POST_HEIGHT, 0.0D);
    }

    public Vec3 endTop() {
        return position().add(endOffset()).add(0.0D, ModPerks.TRIPWIRE_POST_HEIGHT, 0.0D);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!hasEnd()) {
            // A post nobody finished is pulled up after a while.
            if (tickCount > ModPerks.TRIPWIRE_LONE_POST_TICKS) {
                discard();
            }
            return;
        }
        if (tickCount < ARM_TICKS) {
            return;
        }
        // It reports to its owner, so with the owner away it waits, still armed, for them.
        ServerPlayer owner = ownerId == null ? null : serverLevel.getServer().getPlayerList().getPlayer(ownerId);
        if (owner == null) {
            return;
        }

        Vec3 from = startTop();
        Vec3 to = endTop();
        Set<Integer> now = new HashSet<>();
        for (LivingEntity victim : serverLevel.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(0.5D))) {
            if (!victim.isAlive() || victim.isSpectator() || victim instanceof ArmorStand
                    || victim.getUUID().equals(ownerId)
                    || victim.getBoundingBox().inflate(0.05D).clip(from, to).isEmpty()) {
                continue;
            }
            now.add(victim.getId());
            if (inside.contains(victim.getId())) {
                continue;
            }
            // Crouching through wears the wire without setting it off.
            if (!victim.isCrouching()) {
                Tripwires.onTripped(serverLevel, this, owner, victim);
            }
            if (--triggersLeft <= 0) {
                Tripwires.onBroken(this, owner);
                discard();
                return;
            }
        }
        inside.clear();
        inside.addAll(now);
    }

    /** Silver Wire's cut: what it deals this time, halving (rounded up) what the next one will. */
    public int takeBleed(int first) {
        int bleed = nextBleed < 0 ? first : nextBleed;
        nextBleed = Math.max(1, (bleed + 1) / 2);
        return bleed;
    }

    public int tier() {
        return tier;
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

    // The entity is the first post, but the wire runs off it for up to a dozen blocks.
    @Override
    public AABB getBoundingBoxForCulling() {
        Vec3 end = position().add(endOffset());
        return new AABB(position(), end).inflate(0.5D).expandTowards(0.0D, ModPerks.TRIPWIRE_POST_HEIGHT, 0.0D);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96.0D * 96.0D;
    }

    private void applyTier() {
        this.entityData.set(DATA_OPACITY, 100 - ModPerks.TRIPWIRES.intValue(ModPerks.TRIPWIRE_TRANSPARENCY, tier));
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

    /** Posts are what the perk counts, two to a wire. */
    @Override
    public int trapLimit() {
        return Math.max(1, ModPerks.TRIPWIRES.intValue(ModPerks.TRIPWIRE_POSTS, tier) / 2);
    }

    @Override
    public boolean isSpent() {
        return triggersLeft <= 0 || (fromDisk && !hasEnd());
    }

    // Saved with the world, so a wire outlasts its owner's logout and a restart alike.
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        tier = Math.max(1, tag.getInt("Tier"));
        placedAt = tag.getLong("PlacedAt");
        triggersLeft = tag.getInt("TriggersLeft");
        nextBleed = tag.contains("NextBleed") ? tag.getInt("NextBleed") : -1;
        if (tag.getBoolean("HasEnd")) {
            entityData.set(DATA_END, new Vector3f(tag.getFloat("EndX"), tag.getFloat("EndY"), tag.getFloat("EndZ")));
            entityData.set(DATA_HAS_END, true);
        }
        fromDisk = true;
        applyTier();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) {
            tag.putUUID("Owner", ownerId);
        }
        tag.putInt("Tier", tier);
        tag.putLong("PlacedAt", placedAt);
        tag.putInt("TriggersLeft", triggersLeft);
        tag.putInt("NextBleed", nextBleed);
        tag.putBoolean("HasEnd", hasEnd());
        Vector3f end = entityData.get(DATA_END);
        tag.putFloat("EndX", end.x());
        tag.putFloat("EndY", end.y());
        tag.putFloat("EndZ", end.z());
    }
}
