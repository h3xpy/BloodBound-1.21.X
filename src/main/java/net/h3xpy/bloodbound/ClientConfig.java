package net.h3xpy.bloodbound;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Per-player display settings, editable in game from Options, Mods, BloodBound, Config.
 */
public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("How BloodBound draws itself on screen.").push("hud");
    }

    public static final ModConfigSpec.DoubleValue HUD_SCALE = BUILDER
            .comment("Size of the perk HUD in the bottom-left corner. 1.0 is the default size.")
            .translation("bloodbound.configuration.hudScale")
            .defineInRange("perkHudScale", 1.0D, 0.5D, 2.5D);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {}

    /** The configured scale, or 1.0 while the config file has yet to load. */
    public static float hudScale() {
        if (!SPEC.isLoaded()) {
            return 1.0F;
        }
        return (float) (double) HUD_SCALE.get();
    }
}
