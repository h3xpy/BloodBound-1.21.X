package net.h3xpy.bloodbound.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.h3xpy.bloodbound.perk.impl.HangedMan;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * A player under The Hanged Man jumps away from the ceiling they stand on, which is downwards.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "jumpFromGround", at = @At("TAIL"))
    private void bloodbound$jumpDown(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        Vec3 motion = self.getDeltaMovement();
        if (motion.y > 0.0D && HangedMan.isInverted(self)) {
            self.setDeltaMovement(motion.x, -motion.y, motion.z);
        }
    }
}
