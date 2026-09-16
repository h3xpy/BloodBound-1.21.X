package net.h3xpy.bloodbound.client;

import java.util.Arrays;

import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.network.ActivatePerkPayload;
import net.h3xpy.bloodbound.network.HealInputPayload;
import net.h3xpy.bloodbound.network.SlotKeyPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClientEventHandler {

    /** Last reported state of the heal key, so only changes go over the wire. */
    private static boolean healHeld;
    /** Same, per loadout slot, for the perks that run while their key is held. */
    private static final boolean[] SLOT_HELD = new boolean[PlayerPerkData.LOADOUT_SIZE];
    /** Client ticks each slot key has been held for, so the HUD can show a wind-up. */
    private static final int[] SLOT_HELD_TICKS = new int[PlayerPerkData.LOADOUT_SIZE];

    private ClientEventHandler() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientSkillCheck.tick();
        ClientAuraReveal.tick();
        ClientMarks.tick();
        ClientFragPreview.tick(minecraft);
        ClientOmniscience.tick();
        ClientFallHandler.tick(minecraft);

        boolean inGame = minecraft.player != null && minecraft.screen == null;

        for (int slot = 0; slot < ModKeyMappings.ACTIVATE_SLOT.length; slot++) {
            // The key repeats while it is held, and every repeat counts as a click. Only a fresh press
            // is an activation: holding a key must never fire a perk over and over.
            int clicks = 0;
            while (ModKeyMappings.ACTIVATE_SLOT[slot].consumeClick()) {
                clicks++;
            }
            boolean held = inGame && ModKeyMappings.ACTIVATE_SLOT[slot].isDown();
            SLOT_HELD_TICKS[slot] = held ? SLOT_HELD_TICKS[slot] + 1 : 0;
            boolean pressed = held && !SLOT_HELD[slot];
            // A tap that went down and up between two ticks never shows as held, but still counts.
            boolean tapped = inGame && clicks > 0 && !held && !SLOT_HELD[slot];
            if (pressed || tapped) {
                PacketDistributor.sendToServer(new ActivatePerkPayload(slot));
            }
            if (tapped) {
                PacketDistributor.sendToServer(new SlotKeyPayload(slot, true));
                PacketDistributor.sendToServer(new SlotKeyPayload(slot, false));
            }
            if (held != SLOT_HELD[slot]) {
                SLOT_HELD[slot] = held;
                PacketDistributor.sendToServer(new SlotKeyPayload(slot, held));
            }
        }

        while (ModKeyMappings.SKILL_CHECK.consumeClick()) {
            if (inGame) {
                ClientSkillCheck.press();
            }
        }

        // Healing runs while the key is held, so only the transitions are worth sending.
        boolean wantsHeal = inGame && ModKeyMappings.HEAL.isDown();
        if (wantsHeal != healHeld) {
            healHeld = wantsHeal;
            PacketDistributor.sendToServer(new HealInputPayload(wantsHeal));
        }
    }

    /** Ticks a slot key has been held, or zero when it is up. */
    public static int slotHeldTicks(int slot) {
        return slot >= 0 && slot < SLOT_HELD_TICKS.length ? SLOT_HELD_TICKS[slot] : 0;
    }

    /** Omniscience draws its own outlines, since a block cannot carry the vanilla glow. */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            ClientOmniscience.render(event.getPoseStack(), event.getCamera());
            ClientMarks.render(event.getPoseStack(), event.getCamera());
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPerkData.reset();
        ClientSkillCheck.reset();
        ClientAuraReveal.reset();
        ClientOmniscience.reset();
        ClientBankShots.clear();
        ClientMarks.reset();
        ClientEffectCharges.reset();
        ClientUnderTheRadar.reset();
        healHeld = false;
        Arrays.fill(SLOT_HELD, false);
        Arrays.fill(SLOT_HELD_TICKS, 0);
    }
}
