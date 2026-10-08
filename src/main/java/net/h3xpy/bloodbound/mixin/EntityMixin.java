package net.h3xpy.bloodbound.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.h3xpy.bloodbound.perk.impl.HangedMan;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * For a player under The Hanged Man, the ceiling is the ground: vanilla only counts a collision
 * while moving down, so without this they could neither walk nor jump up there, only slide.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {

    @Shadow
    public boolean verticalCollision;
    @Shadow
    public boolean verticalCollisionBelow;

    @Shadow
    public abstract void setOnGround(boolean onGround);

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
