package net.h3xpy.bloodbound.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.h3xpy.bloodbound.event.DrawSpeed;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

/**
 * The client's half of {@link DrawSpeed}: the same hurried draw, so the bow on screen is full when
 * the server says it is. Gabriel's Bow only counts while the wings are out, which the client reads
 * off its own flight — the perk flies the player through their abilities, and those are synced.
 */
public final class ClientDrawSpeed {

    private static final Map<UUID, Double> DEBT = new HashMap<>();

    private ClientDrawSpeed() {}

    @SubscribeEvent
    public static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || event.getEntity() != player) {
            return;
        }
        boolean winged = player.getAbilities().flying && !player.isCreative() && !player.isSpectator();
        DrawSpeed.apply(event, player.getUUID(), DrawSpeed.bonus(ClientPerkData.get(), winged), DEBT);
    }
}
