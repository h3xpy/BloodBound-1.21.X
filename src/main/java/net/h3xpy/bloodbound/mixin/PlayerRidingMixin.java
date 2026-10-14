package net.h3xpy.bloodbound.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.h3xpy.bloodbound.perk.impl.Undersized;
import net.minecraft.world.entity.player.Player;

/** Undersized: a crouch still held from before climbing on does not get the rider straight back down. */
@Mixin(Player.class)
public abstract class PlayerRidingMixin {

    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
    private void bloodbound$holdOn(CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        if (!self.level().isClientSide() && Undersized.holdsOn(self)) {
            cir.setReturnValue(false);
        }
    }
}
