package net.h3xpy.bloodbound.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.h3xpy.bloodbound.perk.impl.HangedMan;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Draws a player under The Hanged Man the way vanilla draws Dinnerbone: upside down within their own
 * hitbox, head turned to match.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Inject(method = "isEntityUpsideDown", at = @At("HEAD"), cancellable = true)
    private static void bloodbound$hangedMan(LivingEntity entity, CallbackInfoReturnable<Boolean> result) {
        if (HangedMan.isInverted(entity)) {
            result.setReturnValue(true);
        }
    }
}
