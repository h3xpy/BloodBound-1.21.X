package net.h3xpy.bloodbound.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** Whether the local player is up on a creature through Undersized, and so steers it. */
public final class ClientUndersized {

    private static boolean riding;

    private ClientUndersized() {}

    public static void setRiding(boolean value) {
        riding = value;
    }

    public static boolean isRiding(Player player) {
        return riding && player == Minecraft.getInstance().player;
    }
}
