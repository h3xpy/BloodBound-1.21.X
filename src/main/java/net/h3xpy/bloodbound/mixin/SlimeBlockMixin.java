package net.h3xpy.bloodbound.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.h3xpy.bloodbound.perk.impl.HangedMan;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.phys.Vec3;

/** A slime block on the ceiling bounces a player under The Hanged Man back down, as a floor one would up. */
@Mixin(SlimeBlock.class)
public abstract class SlimeBlockMixin {

    @Inject(method = "bounceUp", at = @At("HEAD"), cancellable = true)
    private void bloodbound$bounceDown(Entity entity, CallbackInfo ci) {
        if (!HangedMan.isInverted(entity)) {
            return;
        }
        Vec3 motion = entity.getDeltaMovement();
        if (motion.y > 0.0D) {
            double kept = entity instanceof LivingEntity ? 1.0D : 0.8D;
            entity.setDeltaMovement(motion.x, -motion.y * kept, motion.z);
        }
        ci.cancel();
    }
}
