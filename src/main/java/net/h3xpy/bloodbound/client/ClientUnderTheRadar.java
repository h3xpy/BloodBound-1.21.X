package net.h3xpy.bloodbound.client;

import java.util.HashSet;
import java.util.Set;

import net.h3xpy.bloodbound.network.HiddenPlayersPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.common.util.TriState;

/**
 * Under The Radar, client half: no nametag over a hidden player, and not a sound out of them.
 * <p>
 * Sounds are caught as this client is about to play them. Cancelling one there also keeps it out of
 * the subtitles, which are only told about a sound once it actually plays. The server cannot tell
 * whose a block sound is — breaking, placing and using blocks are announced by position alone —
 * so a sound counts as a hidden player's when it sits right on them and belongs to the player or
 * block channels. That includes the hidden player's own client: silent means silent.
 */
public final class ClientUnderTheRadar {

    /** How far off a hidden player's body a sound may be and still be theirs. */
    private static final double REACH = 1.25D;

    private static final Set<Integer> HIDDEN = new HashSet<>();

    private ClientUnderTheRadar() {}

    public static void accept(HiddenPlayersPayload payload) {
        HIDDEN.clear();
        for (HiddenPlayersPayload.Entry entry : payload.players()) {
            HIDDEN.add(entry.entityId());
        }
    }

    public static void reset() {
        HIDDEN.clear();
    }

    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        if (HIDDEN.contains(event.getEntity().getId())) {
            event.setCanRender(TriState.FALSE);
        }
    }

    @SubscribeEvent
    public static void onSound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || HIDDEN.isEmpty() || sound.isRelative()) {
            return;
        }
        if (sound.getSource() != SoundSource.PLAYERS && sound.getSource() != SoundSource.BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        for (int id : HIDDEN) {
            Entity entity = minecraft.level.getEntity(id);
            if (entity != null && entity.getBoundingBox().inflate(REACH)
                    .contains(sound.getX(), sound.getY(), sound.getZ())) {
                event.setSound(null);
                return;
            }
        }
    }
}
