package net.h3xpy.bloodbound.event;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

/**
 * Faster bow and crossbow draws: Odd Arrow's, and Gabriel's Bow's while the wings are out.
 * <p>
 * A draw is sped up by taking ticks off what the item has left to be used for. That has to happen on
 * both sides at once. The server decides how strong the shot is, but the client is the one drawing
 * the bow on screen and deciding when to let go: hurried on the server alone, the draw was already
 * full long before it looked it, and the player — watching an animation running at its old pace —
 * never let go any sooner. The client half is {@code ClientDrawSpeed}, working the same sums.
 */
public final class DrawSpeed {

    /** Fractions of a tick of extra draw owed, banked until a whole one is due. */
    private static final Map<UUID, Double> DEBT = new HashMap<>();

    private DrawSpeed() {}

    /** Extra ticks of draw per tick held, from whatever the player has fitted. */
    public static double bonus(PlayerPerkData data, boolean winged) {
        double bonus = 0.0D;
        if (data.getActiveTier(ModPerks.LONGSHOT) > 0 && data.isAddonActive(ModAddons.ODD_ARROW)) {
            bonus += ModAddons.ODD_ARROW_DRAW;
        }
        if (winged && data.isAddonActive(ModAddons.GABRIELS_BOW)) {
            bonus += ModAddons.GABRIELS_BOW_DRAW;
        }
        return bonus;
    }

    /** Takes this tick's share of the bonus off the use, banking the fraction for later ticks. */
    public static void apply(LivingEntityUseItemEvent.Tick event, UUID playerId, double bonus, Map<UUID, Double> debt) {
        if (!(event.getItem().getItem() instanceof BowItem) && !(event.getItem().getItem() instanceof CrossbowItem)) {
            return;
        }
        if (bonus <= 0.0D) {
            debt.remove(playerId);
            return;
        }
        double owed = debt.getOrDefault(playerId, 0.0D) + bonus;
        int whole = (int) owed;
        debt.put(playerId, owed - whole);
        if (whole > 0) {
            event.setDuration(Math.max(1, event.getDuration() - whole));
        }
    }

    @SubscribeEvent
    public static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerPerkData data = PerkDataManager.get(player);
            apply(event, player.getUUID(), bonus(data, data.isAngelWinged()), DEBT);
        }
    }

    public static void clear(UUID playerId) {
        DEBT.remove(playerId);
    }
}
