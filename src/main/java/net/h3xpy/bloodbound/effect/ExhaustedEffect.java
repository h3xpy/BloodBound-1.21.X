package net.h3xpy.bloodbound.effect;

import net.minecraft.world.effect.MobEffectCategory;

/**
 * Exhausted: lungs that only hold so much.
 * <p>
 * A flag, like Bleeding. The stamina bar, what running costs, and the Slowness that lands when it
 * empties are all in {@code ExhaustedHandler}; the level the effect carries is what sets how small
 * the bar is.
 */
public class ExhaustedEffect extends BloodBoundEffect {

    public ExhaustedEffect() {
        super(MobEffectCategory.HARMFUL, 0xC23BB5);
    }
}
