package net.h3xpy.bloodbound.entity;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.perk.impl.SpringPad;
import net.h3xpy.bloodbound.registry.ModEntities;
import net.h3xpy.bloodbound.ritual.TrapRoster;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A Spring Pad on the floor: whatever walks onto it is thrown high into the air and along where it
 * is looking. Its layer is thrown like anybody
 * else.
 * <p>
 * Like Barbed Wire's coil it is an entity, does not collide, and is saved with the world, owner and
 * all; {@link TrapRoster} keeps count of them.
 */
public class SpringPadEntity extends Entity implements TrapRoster.Trap {

    public static final String KIND = "spring_pad";

    @Nullable
    private UUID ownerId;
    private int tier = 1;
    private long placedAt;
    private int launchesLeft = 1;
    private int reload;

    public SpringPadEntity(EntityType<? extends SpringPadEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public SpringPadEntity(Level level, ServerPlayer owner, int tier) {
        this(ModEntities.SPRING_PAD.get(), level);
        this.ownerId = owner.getUUID();
        this.tier = tier;
        this.placedAt = level.getGameTime();
        this.launchesLeft = ModPerks.SPRING_PAD.intValue(ModPerks.SPRING_LAUNCHES, tier);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    /** Whether it has armed yet: a fresh pad waits a moment, so it is never set off by its own laying. */
    public boolean isArmed() {
        return tickCount >= ModPerks.SPRING_ARM_TICKS;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!isArmed()) {
            if (tickCount % 5 == 0) {
                serverLevel.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY() + 0.2D, getZ(),
                        2, 0.3D, 0.05D, 0.3D, 0.0D);
            }
            return;
        }
        if (tickCount == ModPerks.SPRING_ARM_TICKS) {
            serverLevel.playSound(null, blockPosition(), SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS, 0.6F, 1.6F);
        }
        if (reload > 0) {
            reload--;
            return;
        }

        AABB top = getBoundingBox().inflate(ModPerks.SPRING_RADIUS, 0.0D, ModPerks.SPRING_RADIUS).expandTowards(0.0D, 0.5D, 0.0D);
        List<LivingEntity> riders = serverLevel.getEntitiesOfClass(LivingEntity.class, top,
                living -> living.isAlive() && !living.isSpectator() && !living.isPassenger());
        if (riders.isEmpty()) {
            return;
        }
        launch(serverLevel, riders);
    }

    /**
     * Throws everybody on the pad at once, which counts as one use. Always by velocity: players move
     * on their own client, and only a motion sent to them makes them fly.
     */
    private void launch(ServerLevel level, List<LivingEntity> riders) {
        for (LivingEntity rider : riders) {
            // Along where the one thrown is looking: up for height, level for distance. Never flatter
            // than the minimum angle, so looking down still leaves the ground.
            float yaw = rider.getYHeadRot() * Mth.DEG_TO_RAD;
            double angle = Math.toRadians(Mth.clamp(-rider.getXRot(), ModPerks.SPRING_MIN_ANGLE, 90.0D));
            double forward = Math.cos(angle) * ModPerks.SPRING_LAUNCH_SPEED;
            rider.setDeltaMovement(new Vec3(-Mth.sin(yaw) * forward, Math.sin(angle) * ModPerks.SPRING_LAUNCH_SPEED,
                    Mth.cos(yaw) * forward));
            rider.hurtMarked = true;
            rider.hasImpulse = true;
            rider.resetFallDistance();
            if (rider instanceof ServerPlayer player && player.getUUID().equals(ownerId)) {
                SpringPad.onOwnerLaunched(player);
            }
        }
        level.playSound(null, blockPosition(), SoundEvents.SLIME_BLOCK_FALL, SoundSource.PLAYERS, 1.0F, 0.6F);
        level.playSound(null, blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 0.8F, 1.4F);
        level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.2D, getZ(), 8, 0.3D, 0.05D, 0.3D, 0.05D);

        reload = ModPerks.SPRING_RELOAD_TICKS;
        if (--launchesLeft <= 0) {
            level.playSound(null, blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.7F, 0.8F);
            discard();
        }
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
        return ModPerks.SPRING_PAD.intValue(ModPerks.SPRING_TRAPS, tier);
    }

    @Override
    public boolean isSpent() {
        return launchesLeft <= 0;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        tier = Math.max(1, tag.getInt("Tier"));
        placedAt = tag.getLong("PlacedAt");
        launchesLeft = tag.getInt("LaunchesLeft");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) {
            tag.putUUID("Owner", ownerId);
        }
        tag.putInt("Tier", tier);
        tag.putLong("PlacedAt", placedAt);
        tag.putInt("LaunchesLeft", launchesLeft);
    }
}
