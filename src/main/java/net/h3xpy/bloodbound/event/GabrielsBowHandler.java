package net.h3xpy.bloodbound.event;

import java.util.UUID;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Gabriel's Bow: a bow drawn on the wing.
 * <p>
 * Two halves, both only while the perk's flight is up. The draw is hurried along in
 * {@link DrawSpeed}, on both sides, with Odd Arrow's. And the arrow leaves far faster than it should — but its damage is reduced by
 * exactly what that speed would have bought, because vanilla prices an arrow by how fast it is
 * travelling, and a free two-and-a-half times damage is not what this is for.
 */
public final class GabrielsBowHandler {

    private GabrielsBowHandler() {}

    /** Speeds the arrow up, and takes the damage that speed would have bought back off it. */
    @SubscribeEvent
    public static void onArrowSpawned(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof AbstractArrow arrow)) {
            return;
        }
        if (arrow.tickCount > 0 || !(arrow.getOwner() instanceof ServerPlayer shooter) || !isArmed(shooter)) {
            return;
        }

        arrow.setDeltaMovement(arrow.getDeltaMovement().scale(ModAddons.GABRIELS_BOW_ARROW_SPEED));
        arrow.setBaseDamage(arrow.getBaseDamage() / ModAddons.GABRIELS_BOW_ARROW_SPEED);
    }

    /** The addon only does anything while the wings are actually out. */
    private static boolean isArmed(ServerPlayer player) {
        PlayerPerkData data = PerkDataManager.get(player);
        return data.isAddonActive(ModAddons.GABRIELS_BOW) && data.isAngelWinged();
    }

    public static void clear(UUID playerId) {
        DrawSpeed.clear(playerId);
    }
}
