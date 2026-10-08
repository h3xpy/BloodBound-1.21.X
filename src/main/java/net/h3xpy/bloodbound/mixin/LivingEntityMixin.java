package net.h3xpy.bloodbound.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.h3xpy.bloodbound.perk.impl.HangedMan;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * What a player under The Hanged Man does with their whole body, turned over:
 * <ul>
 *   <li>a jump pushes away from the ceiling they stand on, which is downwards;</li>
 *   <li>on a ladder or a vine they climb towards their head — down — slide towards their feet at
 *   the usual capped speed, and holding sneak keeps them in place;</li>
 *   <li>a blow knocks them off the ceiling the way it lifts anybody else off the floor.</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Shadow
    private Optional<BlockPos> lastClimbablePos;

    @Inject(method = "jumpFromGround", at = @At("TAIL"))
    private void bloodbound$jumpDown(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        Vec3 motion = self.getDeltaMovement();
        if (motion.y > 0.0D && HangedMan.isInverted(self)) {
            self.setDeltaMovement(motion.x, -motion.y, motion.z);
        }
    }

    /**
     * Whether a ladder is being held is read where the feet are — the top of the hitbox up there.
     * Read at the bottom, as vanilla does, the head left the last rung first and the player could
     * never climb past the end of a ladder onto what it leads to.
     */
    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void bloodbound$climbAtFeet(CallbackInfoReturnable<Boolean> result) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!HangedMan.isInverted(self) || self.isSpectator()) {
            return;
        }
        BlockPos feet = BlockPos.containing(self.getX(), self.getBoundingBox().maxY - 0.1D, self.getZ());
        BlockState state = self.level().getBlockState(feet);
        Optional<BlockPos> ladder = CommonHooks.isLivingOnLadder(state, self.level(), feet, self);
        if (ladder.isPresent()) {
            lastClimbablePos = ladder;
        }
        result.setReturnValue(ladder.isPresent());
    }

    /** The dust of a hard landing rises from where it landed: up at the feet, not down at the head. */
    @Redirect(method = "checkFallDamage", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getY()D"))
    private double bloodbound$landingDustAtFeet(LivingEntity self) {
        return HangedMan.isInverted(self) ? self.getBoundingBox().maxY : self.getY();
    }

    /** Ladders, mirrored: the slide is capped going up, and sneak holds the player still. */
    @Inject(method = "handleOnClimbable", at = @At("HEAD"), cancellable = true)
    private void bloodbound$climbUpsideDown(Vec3 motion, CallbackInfoReturnable<Vec3> result) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!HangedMan.isInverted(self) || !self.onClimbable()) {
            return;
        }
        self.resetFallDistance();
        double x = Mth.clamp(motion.x, -0.15D, 0.15D);
        double z = Mth.clamp(motion.z, -0.15D, 0.15D);
        double y = Math.min(motion.y, 0.15D);
        if (y > 0.0D && !self.getInBlockState().isScaffolding(self) && self.isSuppressingSlidingDownLadder()
                && self instanceof Player) {
            y = 0.0D;
        }
        result.setReturnValue(new Vec3(x, y, z));
    }

    /** Pushing against a ladder climbs it — towards the head, which is down. */
    @Inject(method = "handleRelativeFrictionAndCalculateMovement", at = @At("RETURN"), cancellable = true)
    private void bloodbound$climbDown(Vec3 input, float friction, CallbackInfoReturnable<Vec3> result) {
        LivingEntity self = (LivingEntity) (Object) this;
        Vec3 motion = result.getReturnValue();
        if (motion.y == 0.2D && HangedMan.isInverted(self)) {
            result.setReturnValue(new Vec3(motion.x, -0.2D, motion.z));
        }
    }

    /** Knocked off the ceiling rather than pressed into it. */
    @Inject(method = "knockback", at = @At("TAIL"))
    private void bloodbound$knockedDown(double strength, double x, double z, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        Vec3 motion = self.getDeltaMovement();
        if (motion.y > 0.0D && self.onGround() && HangedMan.isInverted(self)) {
            self.setDeltaMovement(motion.x, -motion.y, motion.z);
        }
    }
}
