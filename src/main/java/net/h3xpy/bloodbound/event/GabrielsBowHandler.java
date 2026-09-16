package net.h3xpy.bloodbound.event;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

/**
 * Gabriel's Bow: a bow drawn on the wing.
 * <p>
 * Two halves, both only while the perk's flight is up. The draw is hurried along by taking ticks
 * off what the item has left to be used for, which is the one place both a bow and a crossbow can
 * be reached from. And the arrow leaves far faster than it should — but its damage is reduced by
 * exactly what that speed would have bought, because vanilla prices an arrow by how fast it is
 * travelling, and a free two-and-a-half times damage is not what this is for.
 */
public final class GabrielsBowHandler {

    /** Fractions of a tick of extra draw owed, banked until a whole one is due. */
    private static final Map<UUID, Float> DRAW_DEBT = new HashMap<>();

    private GabrielsBowHandler() {}

    /** Hurries the draw along, a tick at a time. */
    @SubscribeEvent
    public static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isArmed(player)) {
            return;
        }
        ItemStack stack = event.getItem();
        if (!(stack.getItem() instanceof BowItem) && !(stack.getItem() instanceof CrossbowItem)) {
            return;
        }

        // Three ticks in four, since the bonus is three quarters of a tick per tick.
        float owed = DRAW_DEBT.getOrDefault(player.getUUID(), 0.0F) + (float) ModAddons.GABRIELS_BOW_DRAW;
        int whole = (int) owed;
        DRAW_DEBT.put(player.getUUID(), owed - whole);
        if (whole > 0) {
            event.setDuration(Math.max(1, event.getDuration() - whole));
        }
    }

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
        DRAW_DEBT.remove(playerId);
    }
}
