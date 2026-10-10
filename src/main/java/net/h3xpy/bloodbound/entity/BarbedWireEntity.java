package net.h3xpy.bloodbound.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.advancement.QuestTracker;
import net.h3xpy.bloodbound.damage.PerkDamageSource;
import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.effect.BleedingHandler;
import net.h3xpy.bloodbound.event.AuraRevealHandler;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.perk.impl.BarbedWire;
import net.h3xpy.bloodbound.registry.ModEntities;
import net.h3xpy.bloodbound.ritual.TrapRoster;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * A coil of barbed wire on the floor.
 * <p>
 * An entity rather than a block: it has to sit on top of whatever is already there, be shot at, and
 * belong to somebody. It does not move and does not collide. It is saved with the world, owner and
 * all, so a coil stays where it was laid through its owner logging out and the server restarting;
 * {@link TrapRoster} keeps count of them.
 */
public class BarbedWireEntity extends Entity implements TrapRoster.Trap {

    public static final String KIND = "barbed_wire";

    /** Opacity as a percentage, which is the only thing the client needs to draw it. */
    private static final EntityDataAccessor<Integer> DATA_OPACITY =
            SynchedEntityData.defineId(BarbedWireEntity.class, EntityDataSerializers.INT);

    /** How long the wire takes to arm itself, so nobody can drop one under their own feet. */
    private static final int ARM_TICKS = 10;
    /** Ticks between two things being caught, so one coil is not a meat grinder. */
    private static final int TRIGGER_COOLDOWN = 20;

    @Nullable
    private UUID ownerId;
    private int tier = 1;
    private long placedAt;
    private int triggerCooldown;
    /** What the addons fitted when it was laid made of it, kept so they hold while the owner is away. */
    private float bonusDamage;
    private boolean dirtyBlade;
    /** Catches left before the coil is spent: one, or two with Reinforced Wire. */
    private int catchesLeft = 1;
    private int reloadTicks = TRIGGER_COOLDOWN;

    public BarbedWireEntity(EntityType<? extends BarbedWireEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public BarbedWireEntity(Level level, ServerPlayer owner, int tier) {
        this(ModEntities.BARBED_WIRE.get(), level);
        this.ownerId = owner.getUUID();
        this.tier = tier;
        this.placedAt = level.getGameTime();
        PlayerPerkData data = PerkDataManager.get(owner);
        if (data.isAddonActive(ModAddons.RUSTY_NAIL)) {
            this.bonusDamage = ModAddons.RUSTY_NAIL_DAMAGE;
        }
        this.dirtyBlade = data.isAddonActive(ModAddons.DIRTY_BLADE);
        if (data.isAddonActive(ModAddons.REINFORCED_WIRE)) {
            this.catchesLeft = ModAddons.REINFORCED_WIRE_CATCHES;
            this.reloadTicks = ModAddons.REINFORCED_WIRE_RELOAD_TICKS;
        }
        applyTier();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OPACITY, 30);
    }

    /** Opacity the client should draw it at, as a fraction. */
    public float opacity() {
        return Math.clamp(entityData.get(DATA_OPACITY) / 100.0F, 0.01F, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (triggerCooldown > 0) {
            triggerCooldown--;
        }

        AABB reach = getBoundingBox().inflate(ModPerks.BARBED_RADIUS, 0.3D, ModPerks.BARBED_RADIUS);

        // An item thrown into it smothers the wire and is lost doing it, the way lava eats one.
        for (ItemEntity item : serverLevel.getEntitiesOfClass(ItemEntity.class, reach)) {
            item.discard();
            defuse(serverLevel);
            return;
        }

        if (tickCount < ARM_TICKS || triggerCooldown > 0) {
            return;
        }

        for (LivingEntity victim : serverLevel.getEntitiesOfClass(LivingEntity.class, reach)) {
            if (!victim.isAlive() || victim.getUUID().equals(ownerId)) {
                continue;
            }
            trigger(serverLevel, victim);
            return;
        }
    }

    /**
     * Something walked in. A coil goes off once and is gone; with Reinforced Wire, twice,
     * a moment apart.
     */
    private void trigger(ServerLevel level, LivingEntity victim) {
        triggerCooldown = reloadTicks;
        if (--catchesLeft <= 0) {
            discard();
        }

        ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
        float damage = (float) ModPerks.BARBED_WIRE.value(ModPerks.BARBED_DAMAGE, tier) + bonusDamage;
        DamageSource source = owner != null
                ? level.damageSources().playerAttack(owner)
                : level.damageSources().generic();
        victim.hurt(PerkDamageSource.of(source, "barbed_wire"), damage);

        BleedingHandler.apply(victim, ModPerks.BARBED_WIRE.intValue(ModPerks.BARBED_BLEED, tier));
        if (dirtyBlade) {
            BarbedWire.slowWhileBleeding(victim);
        }

        level.playSound(null, blockPosition(), SoundEvents.SWEET_BERRY_BUSH_BREAK,
                SoundSource.PLAYERS, 0.8F, 0.7F);
        level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 0.2D, getZ(), 12, 0.3D, 0.1D, 0.3D, 0.1D);

        // The point of a trap is knowing it went off, and knowing what set it off.
        if (owner != null) {
            owner.displayClientMessage(Component.translatable("bloodbound.message.barbed_wire_triggered",
                    victim.getName()).withStyle(ChatFormatting.RED), true);
            AuraRevealHandler.reveal(owner, victim, ModPerks.BARBED_REVEAL_TICKS);
        }
        QuestTracker.onCoilCaught(owner, ownerId, victim, blockPosition(), level.getGameTime());
    }

    /** The wire coming apart, whether it was shot or smothered. */
    private void defuse(ServerLevel level) {
        level.playSound(null, blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.7F, 1.1F);
        level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.2D, getZ(), 10, 0.3D, 0.1D, 0.3D, 0.02D);
        discard();
    }

    /** Shootable: an arrow in the coil takes it apart. */
    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level() instanceof ServerLevel serverLevel && source.getDirectEntity() instanceof AbstractArrow arrow) {
            arrow.discard();
            defuse(serverLevel);
            return true;
        }
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Nullable
    public UUID ownerId() {
        return ownerId;
    }

    private void applyTier() {
        this.entityData.set(DATA_OPACITY, ModPerks.BARBED_WIRE.intValue(ModPerks.BARBED_OPACITY, tier));
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
        return ModPerks.BARBED_WIRE.intValue(ModPerks.BARBED_TRAPS, tier);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        tier = Math.max(1, tag.getInt("Tier"));
        placedAt = tag.getLong("PlacedAt");
        bonusDamage = tag.getFloat("BonusDamage");
        dirtyBlade = tag.getBoolean("DirtyBlade");
        catchesLeft = tag.contains("CatchesLeft") ? tag.getInt("CatchesLeft") : 1;
        reloadTicks = tag.contains("ReloadTicks") ? tag.getInt("ReloadTicks") : TRIGGER_COOLDOWN;
        applyTier();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) {
            tag.putUUID("Owner", ownerId);
        }
        tag.putInt("Tier", tier);
        tag.putLong("PlacedAt", placedAt);
        tag.putFloat("BonusDamage", bonusDamage);
        tag.putBoolean("DirtyBlade", dirtyBlade);
        tag.putInt("CatchesLeft", catchesLeft);
        tag.putInt("ReloadTicks", reloadTicks);
    }
}
