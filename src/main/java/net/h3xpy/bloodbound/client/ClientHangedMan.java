package net.h3xpy.bloodbound.client;

import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.perk.impl.HangedMan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * The Hanged Man as the client sees it. Who is upside down comes from the gravity attribute, which
 * the server syncs on its own; this rolls the view over, mirrors the controls to match, and moves the
 * eyes of any player who flipped.
 */
public final class ClientHangedMan {

    /** How far the local view has rolled over, 0 upright to 1 upside down, this tick and last. */
    private static float flip;
    private static float flipOld;

    private ClientHangedMan() {}

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            reset();
            return;
        }

        flipOld = flip;
        float target = HangedMan.isInverted(minecraft.player) ? 1.0F : 0.0F;
        float step = 1.0F / ModPerks.HANGED_MAN_ROLL_TICKS;
        flip = target > flip ? Math.min(target, flip + step) : Math.max(target, flip - step);

        // The eye height is settled when a player's size is worked out, so it has to be worked out
        // again once they flip, here as on the server. Read off the eyes themselves rather than
        // remembered, so a player who comes back into view already flipped is caught too.
        for (AbstractClientPlayer player : minecraft.level.players()) {
            boolean eyesLow = player.getEyeHeight() < player.getBbHeight() / 2.0F;
            if (HangedMan.isInverted(player) != eyesLow) {
                player.refreshDimensions();
            }
        }
    }

    /** Whether the view is far enough over that the controls are mirrored. */
    public static boolean isFlipped() {
        return flip > 0.5F;
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (flip <= 0.0F && flipOld <= 0.0F) {
            return;
        }
        float amount = Mth.lerp((float) event.getPartialTick(), flipOld, flip);
        event.setRoll(event.getRoll() + 180.0F * amount);
    }

    /** Left and right are swapped on a screen turned upside down; so are the strafe keys. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!isFlipped()) {
            return;
        }
        Input input = event.getInput();
        input.leftImpulse = -input.leftImpulse;
        boolean left = input.left;
        input.left = input.right;
        input.right = left;
    }

    public static void reset() {
        flip = 0.0F;
        flipOld = 0.0F;
    }
}
