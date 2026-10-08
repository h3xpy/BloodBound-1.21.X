package net.h3xpy.bloodbound.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * Lets The Hanged Man reset the server's "floating too long" counter: a player falling up into the
 * sky looks, to vanilla, like one hovering, and would be kicked for flying.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerGamePacketListenerImplAccessor {

    @Accessor("aboveGroundTickCount")
    void bloodbound$setAboveGroundTickCount(int ticks);
}
