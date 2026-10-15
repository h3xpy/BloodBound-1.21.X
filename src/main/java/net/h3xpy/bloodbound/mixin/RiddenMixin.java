package net.h3xpy.bloodbound.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.h3xpy.bloodbound.perk.impl.Undersized;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * How a creature ridden through Undersized moves: the rider's keys, at the creature's own pace,
 * facing where the rider looks. Pigs, striders, horses and camels have their own versions of these,
 * which would otherwise win, so they are covered too.
 */
@Mixin({ LivingEntity.class, Pig.class, Strider.class, AbstractHorse.class, Camel.class })
public abstract class RiddenMixin {

    @Inject(method = "getRiddenInput", at = @At("HEAD"), cancellable = true, require = 0)
    private void bloodbound$undersizedInput(Player player, Vec3 travelVector, CallbackInfoReturnable<Vec3> cir) {
        if (Undersized.controls((LivingEntity) (Object) this, player)) {
            cir.setReturnValue(Undersized.riddenInput((LivingEntity) (Object) this, player));
        }
    }

    @Inject(method = "getRiddenSpeed", at = @At("HEAD"), cancellable = true, require = 0)
    private void bloodbound$undersizedSpeed(Player player, CallbackInfoReturnable<Float> cir) {
        if (Undersized.controls((LivingEntity) (Object) this, player)) {
            cir.setReturnValue(Undersized.riddenSpeed((LivingEntity) (Object) this));
        }
    }

    @Inject(method = "tickRidden", at = @At("HEAD"), cancellable = true, require = 0)
    private void bloodbound$undersizedTurn(Player player, Vec3 travelVector, CallbackInfo ci) {
        if (Undersized.controls((LivingEntity) (Object) this, player)) {
            Undersized.tickRidden((LivingEntity) (Object) this, player);
            ci.cancel();
        }
    }
}
