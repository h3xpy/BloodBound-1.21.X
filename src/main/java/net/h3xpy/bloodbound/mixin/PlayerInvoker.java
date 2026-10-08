package net.h3xpy.bloodbound.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Lets {@link PlayerMixin} run the sneak edge check a second time, on the mirrored movement. */
@Mixin(Player.class)
public interface PlayerInvoker {

    @Invoker("maybeBackOffFromEdge")
    Vec3 bloodbound$maybeBackOffFromEdge(Vec3 movement, MoverType mover);
}
