package net.h3xpy.bloodbound.client;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * One rebindable activation key per loadout slot, all listed under a BloodBound category in the
 * vanilla controls screen.
 */
public final class ModKeyMappings {
    public static final String CATEGORY = "key.categories.bloodbound";

    /** Defaults picked to avoid clashing with anything vanilla binds. */
    private static final int[] DEFAULT_KEYS = {
            GLFW.GLFW_KEY_X,
            GLFW.GLFW_KEY_C,
            GLFW.GLFW_KEY_V,
            GLFW.GLFW_KEY_B
    };

    /** Indexed by loadout slot. */
    public static final KeyMapping[] ACTIVATE_SLOT = new KeyMapping[PlayerPerkData.LOADOUT_SIZE];

    /** Held down to heal a crouched, still teammate. */
    public static final KeyMapping HEAL = new KeyMapping(
            "key.bloodbound.heal", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);

    /**
     * Answers a skill check. Right click by default: it is the fastest button to reach in a hurry,
     * and healing already ignores anything the other hand is doing.
     */
    public static final KeyMapping SKILL_CHECK = new KeyMapping(
            "key.bloodbound.skill_check", InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_RIGHT, CATEGORY);

    static {
        for (int slot = 0; slot < PlayerPerkData.LOADOUT_SIZE; slot++) {
            ACTIVATE_SLOT[slot] = new KeyMapping(
                    "key.bloodbound.activate_slot_" + (slot + 1),
                    InputConstants.Type.KEYSYM,
                    slot < DEFAULT_KEYS.length ? DEFAULT_KEYS[slot] : InputConstants.UNKNOWN.getValue(),
                    CATEGORY);
        }
    }

    private ModKeyMappings() {}

    public static void register(RegisterKeyMappingsEvent event) {
        for (KeyMapping mapping : ACTIVATE_SLOT) {
            event.register(mapping);
        }
        event.register(HEAL);
        event.register(SKILL_CHECK);
    }

    /** The key currently bound to a slot, for display in the UI. Null when the slot is unbound. */
    @Nullable
    public static String boundKeyLabel(int slot) {
        if (slot < 0 || slot >= ACTIVATE_SLOT.length) {
            return null;
        }
        KeyMapping mapping = ACTIVATE_SLOT[slot];
        return mapping.isUnbound() ? null : mapping.getTranslatedKeyMessage().getString();
    }
}
