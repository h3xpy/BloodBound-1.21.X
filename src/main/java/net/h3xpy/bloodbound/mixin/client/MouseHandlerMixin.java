package net.h3xpy.bloodbound.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.h3xpy.bloodbound.client.ClientHangedMan;
import net.minecraft.client.MouseHandler;

/**
 * With the view rolled over by The Hanged Man, the screen is mirrored both ways; the mouse is
 * mirrored with it, so moving it right still turns the view towards the right of the screen.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 0)
    private double bloodbound$flipYaw(double yaw) {
        return ClientHangedMan.isFlipped() ? -yaw : yaw;
    }

    @ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 1)
    private double bloodbound$flipPitch(double pitch) {
        return ClientHangedMan.isFlipped() ? -pitch : pitch;
    }
}
