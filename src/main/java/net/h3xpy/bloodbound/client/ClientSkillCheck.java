package net.h3xpy.bloodbound.client;

import net.h3xpy.bloodbound.network.SkillCheckInputPayload;
import net.h3xpy.bloodbound.network.SkillCheckResultPayload;
import net.h3xpy.bloodbound.network.StartSkillCheckPayload;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The client's copy of the skill check currently on screen. The server owns the verdict; this only
 * drives the dial and reports the moment the key was pressed.
 */
public final class ClientSkillCheck {
    /** How long the hit or miss flash lingers after the dial resolves, in ticks. */
    private static final int FLASH_TICKS = 8;

    private static int id = -1;
    private static float zoneStart;
    private static float zoneWidth;
    private static int durationTicks;
    private static int elapsedTicks;
    private static boolean answered;

    private static int flashTicks;
    private static boolean flashSuccess;

    private ClientSkillCheck() {}

    public static void start(StartSkillCheckPayload payload) {
        id = payload.id();
        zoneStart = payload.zoneStart();
        zoneWidth = payload.zoneWidth();
        durationTicks = payload.durationTicks();
        elapsedTicks = 0;
        answered = false;
        flashTicks = 0;
    }

    public static void result(SkillCheckResultPayload payload) {
        if (payload.id() == id) {
            id = -1;
            flashSuccess = payload.success();
            flashTicks = FLASH_TICKS;
        }
    }

    public static void tick() {
        if (isActive()) {
            elapsedTicks++;
            // The server will resolve a timeout; stop the needle at the end meanwhile.
            if (elapsedTicks > durationTicks) {
                elapsedTicks = durationTicks;
            }
        }
        if (flashTicks > 0) {
            flashTicks--;
        }
    }

    /** Sends the press, once per check. */
    public static void press() {
        if (!isActive() || answered) {
            return;
        }
        answered = true;
        PacketDistributor.sendToServer(new SkillCheckInputPayload(id, progress(0.0F)));
    }

    public static boolean isActive() {
        return id >= 0;
    }

    public static boolean isFlashing() {
        return flashTicks > 0;
    }

    public static boolean flashSuccess() {
        return flashSuccess;
    }

    /** Needle position in 0..1, smoothed between ticks. */
    public static float progress(float partialTick) {
        if (durationTicks <= 0) {
            return 1.0F;
        }
        float ticks = Math.min(durationTicks, elapsedTicks + partialTick);
        return ticks / durationTicks;
    }

    public static float zoneStart() {
        return zoneStart;
    }

    public static float zoneWidth() {
        return zoneWidth;
    }

    public static void reset() {
        id = -1;
        flashTicks = 0;
        answered = false;
    }
}
