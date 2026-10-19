package net.h3xpy.bloodbound.entity;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.registry.ModEntities;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;

/**
 * Diagnostic Tool X: a stand-in left where the player was when they jumped back to their return
 * point. It is drawn as that player — their skin, their armour, what they hold, and their name over
 * its head under the same rules as theirs — stands for a few seconds, then goes up in smoke. Any
 * blow, or anything else that would hurt it, gives it away and blows it apart at once.
 * <p>
 * Never saved: a stand-in has no business outliving the moment it was made for.
 */
public class DecoyEntity extends LivingEntity {

    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(DecoyEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> DATA_OWNER_NAME =
            SynchedEntityData.defineId(DecoyEntity.class, EntityDataSerializers.STRING);

    private final NonNullList<ItemStack> hands = NonNullList.withSize(2, ItemStack.EMPTY);
    private final NonNullList<ItemStack> armour = NonNullList.withSize(4, ItemStack.EMPTY);
    private HumanoidArm mainArm = HumanoidArm.RIGHT;

    public DecoyEntity(EntityType<? extends DecoyEntity> type, Level level) {
        super(type, level);
    }

    /** Leaves a stand-in of the player exactly where they stand, as they stand. */
    public static void leaveBehind(ServerPlayer player) {
        DecoyEntity decoy = new DecoyEntity(ModEntities.DECOY.get(), player.level());
        decoy.entityData.set(DATA_OWNER, Optional.of(player.getUUID()));
        decoy.entityData.set(DATA_OWNER_NAME, player.getGameProfile().getName());
        decoy.setCustomName(Component.literal(player.getGameProfile().getName()));
        decoy.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        decoy.setYHeadRot(player.getYHeadRot());
        decoy.setYBodyRot(player.yBodyRot);
        decoy.yHeadRotO = player.getYHeadRot();
        decoy.yBodyRotO = player.yBodyRot;
        decoy.setShiftKeyDown(player.isShiftKeyDown());
        decoy.setPose(player.isShiftKeyDown() ? Pose.CROUCHING : Pose.STANDING);
        decoy.mainArm = player.getMainArm();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HAND || slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                decoy.setItemSlot(slot, player.getItemBySlot(slot).copy());
            }
        }
        player.level().addFreshEntity(decoy);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OWNER, Optional.empty());
        builder.define(DATA_OWNER_NAME, "");
    }

    /** Whose skin it wears. */
    @Nullable
    public UUID ownerId() {
        return entityData.get(DATA_OWNER).orElse(null);
    }

    /**
     * The owner's team, so the name over its head is coloured, prefixed and shown or hidden exactly
     * as the owner's own is.
     */
    @Override
    @Nullable
    public PlayerTeam getTeam() {
        String owner = entityData.get(DATA_OWNER_NAME);
        return owner.isEmpty() ? super.getTeam() : level().getScoreboard().getPlayersTeam(owner);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount >= ModAddons.TOOL_X_DECOY_TICKS) {
            evaporate();
        }
    }

    /** Gone in a puff of smoke, as soon as anything tests it. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && !isRemoved()) {
            evaporate();
        }
        return false;
    }

    private void evaporate() {
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.9D, getZ(), 30, 0.3D, 0.6D, 0.3D, 0.02D);
            level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.9D, getZ(), 20, 0.3D, 0.6D, 0.3D, 0.05D);
            level.playSound(null, blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7F, 1.4F);
        }
        discard();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return armour;
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return switch (slot.getType()) {
            case HAND -> hands.get(slot.getIndex());
            case HUMANOID_ARMOR -> armour.get(slot.getIndex());
            default -> ItemStack.EMPTY;
        };
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        switch (slot.getType()) {
            case HAND -> hands.set(slot.getIndex(), stack);
            case HUMANOID_ARMOR -> armour.set(slot.getIndex(), stack);
            default -> {
            }
        }
    }

    @Override
    public HumanoidArm getMainArm() {
        return mainArm;
    }
}
