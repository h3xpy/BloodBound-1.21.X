package net.h3xpy.bloodbound.client;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * A camera shake, applied to the view only: the player's real facing is never touched, so aiming
 * and movement carry on as they were. It fades out over its length rather than stopping dead.
 */
public final class ClientCameraShake {

    private static float strength;
    private static long startedAt;
    private static long endsAt;

    private ClientCameraShake() {}

    /** Starts a shake, or strengthens and lengthens the one already running. */
    public static void shake(float peak, int ticks) {
        long now = gameTime();
        float current = currentStrength(now, 0.0F);
        strength = Math.max(current, peak);
        startedAt = now;
        endsAt = Math.max(endsAt, now + ticks);
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        long now = gameTime();
        if (now >= endsAt) {
            return;
        }
        float partial = (float) event.getPartialTick();
        float amount = currentStrength(now, partial);
        double time = (now + partial) * 0.9D;
        // Three unrelated waves per axis read as a shudder rather than a sway.
        event.setYaw(event.getYaw() + amount * (float) (Math.sin(time * 2.3D) + 0.5D * Math.sin(time * 5.1D)));
        event.setPitch(event.getPitch() + amount * (float) (Math.sin(time * 2.9D + 1.3D) + 0.5D * Math.sin(time * 6.7D)));
        event.setRoll(event.getRoll() + amount * 0.6F * (float) Math.sin(time * 3.7D + 2.1D));
    }

    private static float currentStrength(long now, float partial) {
        if (now >= endsAt || endsAt <= startedAt) {
            return 0.0F;
        }
        float left = (endsAt - now - partial) / (float) (endsAt - startedAt);
        return strength * Math.max(0.0F, left);
    }

    private static long gameTime() {
        return Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
    }
}
