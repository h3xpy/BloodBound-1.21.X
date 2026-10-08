package net.h3xpy.bloodbound.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.h3xpy.bloodbound.perk.impl.HangedMan;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * The server's own reading of a player's movement assumes "up" is never falling: every move
 * packet that goes up wipes the fall distance, and one that leaves the ground going up counts as a
 * jump. Under The Hanged Man up is exactly where they fall, so both are turned over — which is the
 * whole reason fall damage never landed on the ceiling.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    /** Moving up resets a fall; upside down it is moving down, away from the ceiling, that does. */
    @Redirect(method = "handleMovePlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;resetFallDistance()V"))
    private void bloodbound$fallingUpKeepsFalling(ServerPlayer player) {
        if (!HangedMan.isInverted(player) || player.getKnownMovement().y < 0.0D) {
            player.resetFallDistance();
        }
    }

    /** Leaving the floor going up is not a jump for somebody falling up off it. */
    @Redirect(method = "handleMovePlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;jumpFromGround()V"))
    private void bloodbound$noJumpFallingUp(ServerPlayer player) {
        if (!HangedMan.isInverted(player)) {
            player.jumpFromGround();
        }
    }
}
