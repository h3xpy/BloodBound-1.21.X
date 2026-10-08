package net.h3xpy.bloodbound.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.h3xpy.bloodbound.perk.impl.HangedMan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * For a player under The Hanged Man the ceiling is the ground, and everything vanilla asks of the
 * ground is asked of it instead:
 * <ul>
 *   <li>standing on it — vanilla only counts a collision while moving down, so without this they
 *   could neither walk nor jump up there, only slide;</li>
 *   <li>the block "under the feet" is the one above the head: friction (ice), speed and jump factors
 *   (soul sand, honey), what is stepped on (magma), footsteps, and what a fall lands on;</li>
 *   <li>falling counts while moving up, so landing on the ceiling hurts like landing on a floor;</li>
 *   <li>stepping up a block is stepping down to a lower stretch of ceiling;</li>
 *   <li>the dust of running comes off the feet, at the top of the hitbox;</li>
 *   <li>the void is above the world too.</li>
 * </ul>
 */
@Mixin(Entity.class)
public abstract class EntityMixin {

    @Shadow
    public boolean verticalCollision;
    @Shadow
    public boolean verticalCollisionBelow;

    @Shadow
    public abstract void setOnGround(boolean onGround);

    @Shadow
    protected abstract void onBelowWorld();

    @Inject(method = "move", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;setOnGroundWithMovement(ZLnet/minecraft/world/phys/Vec3;)V",
            shift = At.Shift.AFTER))
    private void bloodbound$ceilingIsGround(MoverType type, Vec3 movement, CallbackInfo ci) {
        if (HangedMan.isInverted((Entity) (Object) this)) {
            boolean onCeiling = verticalCollision && movement.y > 0.0D;
            verticalCollisionBelow = onCeiling;
            setOnGround(onCeiling);
        }
    }

    /**
     * The block that counts as underfoot is the one over the head. Every "what am I standing on"
     * in vanilla — friction, speed, jumping, stepping on, footsteps, landing — goes through here.
     */
    @Inject(method = "getOnPos(F)Lnet/minecraft/core/BlockPos;", at = @At("HEAD"), cancellable = true)
    private void bloodbound$blockOverHead(float yOffset, CallbackInfoReturnable<BlockPos> result) {
        Entity self = (Entity) (Object) this;
        if (HangedMan.isInverted(self)) {
            result.setReturnValue(new BlockPos(Mth.floor(self.getX()),
                    Mth.floor(self.getBoundingBox().maxY + yOffset), Mth.floor(self.getZ())));
        }
    }

    /** A fall is counted while moving towards the ceiling, which is what falling is up there. */
    @ModifyVariable(method = "checkFallDamage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double bloodbound$fallUpwards(double y) {
        return HangedMan.isInverted((Entity) (Object) this) ? -y : y;
    }

    /**
     * Steps, mirrored. Vanilla tries to lift the hitbox over a block in the way, which up on the
     * ceiling only pushes into it; a stretch of lower ceiling is stepped onto by dropping the
     * hitbox, moving across, and pressing back up against it.
     */
    @Inject(method = "collide", at = @At("RETURN"), cancellable = true)
    private void bloodbound$stepOnCeiling(Vec3 movement, CallbackInfoReturnable<Vec3> result) {
        Entity self = (Entity) (Object) this;
        Vec3 done = result.getReturnValue();
        float step = self.maxUpStep();
        if (step <= 0.0F || !self.onGround() || !HangedMan.isInverted(self)
                || (movement.x == done.x && movement.z == done.z)) {
            return;
        }
        AABB box = self.getBoundingBox();
        List<VoxelShape> nearby = self.level().getEntityCollisions(self,
                box.expandTowards(movement.x, -step, movement.z).expandTowards(0.0D, Math.max(0.0D, movement.y), 0.0D));

        Vec3 down = Entity.collideBoundingBox(self, new Vec3(0.0D, -step, 0.0D), box, self.level(), nearby);
        AABB lowered = box.move(down);
        Vec3 across = Entity.collideBoundingBox(self, new Vec3(movement.x, 0.0D, movement.z), lowered,
                self.level(), nearby);
        if (across.horizontalDistanceSqr() <= done.horizontalDistanceSqr()) {
            return;
        }
        Vec3 back = Entity.collideBoundingBox(self, new Vec3(0.0D, -down.y + Math.max(0.0D, movement.y), 0.0D),
                lowered.move(across), self.level(), nearby);
        result.setReturnValue(down.add(across).add(back));
    }

    /**
     * The dust kicked up by running comes off the feet, which up there are at the top of the
     * hitbox; vanilla puts it at the bottom, right in the player's face.
     */
    @Inject(method = "spawnSprintParticle", at = @At("HEAD"), cancellable = true)
    private void bloodbound$sprintDustAtFeet(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!HangedMan.isInverted(self)) {
            return;
        }
        ci.cancel();
        BlockPos pos = self.getOnPosLegacy();
        BlockState state = self.level().getBlockState(pos);
        if (state.addRunningEffects(self.level(), pos, self) || state.getRenderShape() == RenderShape.INVISIBLE) {
            return;
        }
        Vec3 motion = self.getDeltaMovement();
        double x = Mth.clamp(self.getX() + (self.getRandom().nextDouble() - 0.5D) * self.getBbWidth(),
                pos.getX(), pos.getX() + 1.0D);
        double z = Mth.clamp(self.getZ() + (self.getRandom().nextDouble() - 0.5D) * self.getBbWidth(),
                pos.getZ(), pos.getZ() + 1.0D);
        self.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state).setPos(pos),
                x, self.getBoundingBox().maxY - 0.1D, z, motion.x * -4.0D, -1.5D, motion.z * -4.0D);
    }

    /** Falling up past the top of the world ends the way falling down past the bottom does. */
    @Inject(method = "checkBelowWorld", at = @At("TAIL"))
    private void bloodbound$voidAbove(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (HangedMan.isInverted(self) && self.getY() > self.level().getMaxBuildHeight() + 64) {
            onBelowWorld();
        }
    }

    /** Height before the size was worked out again, for {@link #bloodbound$keepHeadOnCeiling}. */
    @Unique
    private float bloodbound$heightBefore;

    @Inject(method = "refreshDimensions", at = @At("HEAD"))
    private void bloodbound$rememberHeight(CallbackInfo ci) {
        bloodbound$heightBefore = ((Entity) (Object) this).getBbHeight();
    }

    /**
     * A hitbox grows and shrinks from its foot. Hanging from the ceiling, it is the other end that
     * is held, so a crouch or a stand would part the player from the ceiling or push them into it;
     * the foot moves instead.
     */
    @Inject(method = "refreshDimensions", at = @At("TAIL"))
    private void bloodbound$keepHeadOnCeiling(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        float grown = self.getBbHeight() - bloodbound$heightBefore;
        if (grown != 0.0F && bloodbound$heightBefore > 0.0F && HangedMan.isInverted(self)) {
            self.setPos(self.getX(), self.getY() - grown, self.getZ());
        }
    }
}
