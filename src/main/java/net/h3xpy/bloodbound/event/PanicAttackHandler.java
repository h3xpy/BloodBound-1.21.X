package net.h3xpy.bloodbound.event;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Panic Attack: a player you have hit does not get their hands back while you are near them.
 * <p>
 * There is no timer on it. The hit is what starts it and distance is the only thing that ends it,
 * so backing off is the cure and there is nothing to wait out. What it does is narrow and speed up
 * every skill check the victim is asked for, which {@code SkillCheckManager} reads when it raises
 * one.
 */
public final class PanicAttackHandler {

    /** Victim to whoever rattled them. */
    private static final Map<UUID, UUID> PANICKED = new HashMap<>();

    private PanicAttackHandler() {}

    @SubscribeEvent
    public static void onAttack(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker) || attacker == victim) {
            return;
        }
        // A blow landed by hand. A shot from across the field is not what rattles somebody.
        if (!event.getSource().is(DamageTypes.PLAYER_ATTACK)) {
            return;
        }
        if (PerkDataManager.get(attacker).getActiveTier(ModPerks.PANIC_ATTACK) <= 0) {
            return;
        }
        PANICKED.put(victim.getUUID(), attacker.getUUID());
    }

    /**
     * The tier rattling this player right now, or zero. The entry is dropped here rather than on a
     * timer: whoever hit them either kept up or they did not.
     */
    public static int tierAffecting(ServerPlayer victim) {
        UUID attackerId = PANICKED.get(victim.getUUID());
        if (attackerId == null) {
            return 0;
        }

        ServerPlayer attacker = attacker(victim, attackerId);
        if (attacker == null) {
            PANICKED.remove(victim.getUUID());
            return 0;
        }

        int tier = PerkDataManager.get(attacker).getActiveTier(ModPerks.PANIC_ATTACK);
        if (tier <= 0) {
            PANICKED.remove(victim.getUUID());
            return 0;
        }

        double radius = ModPerks.PANIC_ATTACK.value(ModPerks.PANIC_RADIUS, tier);
        if (attacker.distanceToSqr(victim) > radius * radius) {
            // Out of range is out for good: the victim has to be hit again to be rattled again.
            PANICKED.remove(victim.getUUID());
            return 0;
        }
        return tier;
    }

    @Nullable
    private static ServerPlayer attacker(ServerPlayer victim, UUID attackerId) {
        ServerPlayer attacker = victim.serverLevel().getServer().getPlayerList().getPlayer(attackerId);
        return attacker != null && attacker.level() == victim.level() && attacker.isAlive() ? attacker : null;
    }

    public static void clear(UUID playerId) {
        PANICKED.remove(playerId);
        PANICKED.values().removeIf(attackerId -> attackerId.equals(playerId));
    }
}
