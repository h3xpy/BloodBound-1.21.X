package net.h3xpy.bloodbound.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.h3xpy.bloodbound.perk.impl.Nullification;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The one place every block change in a level ends up, whatever made it — which is the only way
 * Nullification can turn away a block broken by something no event hears about, like an explosion a
 * mod wrote for itself.
 * <p>
 * All the deciding is in {@link Nullification#refusesChange}; with no Nullification standing it
 * returns on its first line.
 */
@Mixin(Level.class)
public abstract class LevelMixin {

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), cancellable = true)
    private void bloodbound$nullify(BlockPos pos, BlockState state, int flags, int recursionLeft,
            CallbackInfoReturnable<Boolean> result) {
        if (Nullification.refusesChange((Level) (Object) this, pos, state, flags)) {
            result.setReturnValue(false);
        }
    }
}
