package net.h3xpy.bloodbound.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.h3xpy.bloodbound.perk.impl.Undersized;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Undersized at Tier III: a creature with the perk's rider on its back names that rider as the one
 * in control, which is what makes vanilla drive it from the rider's client, the way a horse is.
 * Pigs, striders and horses fall back on this one when their own conditions are not met.
 */
@Mixin(Mob.class)
public abstract class MobControlMixin {

    @Inject(method = "getControllingPassenger", at = @At("HEAD"), cancellable = true)
    private void bloodbound$undersizedRider(CallbackInfoReturnable<LivingEntity> cir) {
        Mob self = (Mob) (Object) this;
        if (Undersized.controls(self, self.getFirstPassenger())) {
            cir.setReturnValue((LivingEntity) self.getFirstPassenger());
        }
    }
}
