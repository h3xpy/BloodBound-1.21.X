package net.h3xpy.bloodbound.perk.impl;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.mark.MarkManager;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Catching Up: reads the ground, and runs down whatever left it.
 * <p>
 * Two things at once. Stepping onto somebody else's trail is worth Speed, and it stays with you for
 * a few seconds after the trail runs out — which is what makes a broken trail followable. And apart
 * from that, plain walking is quicker: only walking, so the perk rewards the patient tracking it is
 * for rather than stacking onto a sprint.
 */
public final class CatchingUp {

    /** Id of the transient speed modifier the walking bonus adds and removes. */
    private static final ResourceLocation WALK_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "catching_up_walk");

    /** Granted in short slices and refreshed while the player is still on a trail. */
    private static final int SPEED_SLICE_TICKS = 40;

    private CatchingUp() {}

    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        int tier = data.getActiveTier(ModPerks.CATCHING_UP);
        if (tier <= 0) {
            clearWalkBonus(player);
            return;
        }

        tickWalkBonus(player, data, tier);
        tickTrail(player, data, tier, gameTime);
    }

    /**
     * The walking bonus, as a transient attribute modifier rather than an effect: it comes off the
     * instant the player breaks into a sprint or drops into a crouch, and it never fights a speed
     * potion for the same slot.
     */
    private static void tickWalkBonus(ServerPlayer player, PlayerPerkData data, int tier) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }

        boolean walking = !player.isSprinting() && !player.isCrouching() && !player.isSwimming();
        AttributeModifier existing = speed.getModifier(WALK_MODIFIER_ID);
        if (!walking) {
            if (existing != null) {
                speed.removeModifier(WALK_MODIFIER_ID);
            }
            return;
        }

        double amount = ModPerks.CATCHING_UP.value(ModPerks.CATCHING_UP_WALK_BONUS, tier) / 100.0D;
        if (existing == null || existing.amount() != amount) {
            speed.removeModifier(WALK_MODIFIER_ID);
            speed.addTransientModifier(new AttributeModifier(WALK_MODIFIER_ID, amount,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void clearWalkBonus(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(WALK_MODIFIER_ID) != null) {
            speed.removeModifier(WALK_MODIFIER_ID);
        }
    }

    /** Speed while on somebody else's trail, and for a few seconds after leaving it. */
    private static void tickTrail(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        BlockPos ground = player.blockPosition().below();
        if (MarkManager.hasForeignMark(player, ground)) {
            // Pushed out afresh on every tick spent on the trail, so the linger is counted from the
            // last mark stepped on rather than from the first.
            data.setTrailSpeedUntil(gameTime + ModPerks.CATCHING_UP.ticks(ModPerks.CATCHING_UP_LINGER, tier));
        }

        long until = data.trailSpeedUntil();
        if (gameTime >= until) {
            return;
        }

        // Granted in slices rather than for the whole remaining window, so letting the trail go
        // cold takes the Speed with it instead of leaving seconds of it hanging around.
        MobEffectInstance current = player.getEffect(MobEffects.MOVEMENT_SPEED);
        int remaining = (int) Math.min(SPEED_SLICE_TICKS, until - gameTime);
        if (current == null || current.getDuration() < remaining) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, remaining,
                    ModPerks.CATCHING_UP_SPEED_LEVEL - 1, false, true, true));
        }
    }
}
