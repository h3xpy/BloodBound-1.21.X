package net.h3xpy.bloodbound.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.h3xpy.bloodbound.perk.impl.HangedMan;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Sneaking under The Hanged Man, mirrored: vanilla only ever looks below the feet, which for a
 * player hanging from the ceiling is the open air.
 * <ul>
 *   <li>whether a pose fits is tested from the top of the hitbox, the side against the ceiling —
 *   tested from the bottom, standing up always ran into the ceiling and kept the player crouched;</li>
 *   <li>holding sneak keeps the player off the edge of the ceiling, as it does on a floor.</li>
 * </ul>
 */
@Mixin(Player.class)
public abstract class PlayerMixin {

    /** Set while the edge check runs again on the mirrored movement, so it does not mirror twice. */
    @Unique
    private boolean bloodbound$mirroringEdge;

    @Inject(method = "canPlayerFitWithinBlocksAndEntitiesWhen", at = @At("HEAD"), cancellable = true)
    private void bloodbound$fitFromCeiling(Pose pose, CallbackInfoReturnable<Boolean> result) {
        Player self = (Player) (Object) this;
        if (!HangedMan.isInverted(self)) {
            return;
        }
        EntityDimensions size = self.getDimensions(pose);
        double top = self.getBoundingBox().maxY;
        AABB box = size.makeBoundingBox(self.getX(), top - size.height(), self.getZ()).deflate(1.0E-7);
        result.setReturnValue(self.level().noCollision(self, box));
    }

    @Inject(method = "maybeBackOffFromEdge", at = @At("HEAD"), cancellable = true)
    private void bloodbound$edgeOfCeiling(Vec3 movement, MoverType mover, CallbackInfoReturnable<Vec3> result) {
        Player self = (Player) (Object) this;
        if (bloodbound$mirroringEdge || !HangedMan.isInverted(self)) {
            return;
        }
        // Vanilla gives up on any movement going up, which is every movement pressing into a
        // ceiling. Run it again on the movement turned over, then turn the answer back.
        bloodbound$mirroringEdge = true;
        try {
            Vec3 kept = ((PlayerInvoker) self).bloodbound$maybeBackOffFromEdge(
                    new Vec3(movement.x, -movement.y, movement.z), mover);
            result.setReturnValue(new Vec3(kept.x, movement.y, kept.z));
        } finally {
            bloodbound$mirroringEdge = false;
        }
    }

    /** Whether there is room to fall this far, looked for above the head rather than below the feet. */
    @Inject(method = "canFallAtLeast", at = @At("HEAD"), cancellable = true)
    private void bloodbound$canFallUp(double x, double z, float distance, CallbackInfoReturnable<Boolean> result) {
        Player self = (Player) (Object) this;
        if (!HangedMan.isInverted(self)) {
            return;
        }
        AABB box = self.getBoundingBox();
        result.setReturnValue(self.level().noCollision(self, new AABB(
                box.minX + x, box.maxY, box.minZ + z,
                box.maxX + x, box.maxY + distance + 1.0E-5F, box.maxZ + z)));
    }
}
