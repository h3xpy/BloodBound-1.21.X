package net.h3xpy.bloodbound.event;

import net.h3xpy.bloodbound.advancement.ModAdvancements;
import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.perk.impl.EnhancedPerception;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Call Of Death: a kill of yours lights up everything standing round the body.
 * <p>
 * Measured from the body rather than from the killer, so a shot taken from cover still shows who
 * was keeping the victim company.
 */
public final class CallOfDeathHandler {

    /** I Know Everything: how many one kill has to light up. */
    private static final int I_KNOW_EVERYTHING_REVEALS = 20;

    private CallOfDeathHandler() {}

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide || !(event.getSource().getEntity() instanceof ServerPlayer killer)) {
            return;
        }
        int tier = PerkDataManager.get(killer).getActiveTier(ModPerks.CALL_OF_DEATH);
        if (tier <= 0) {
            return;
        }

        double radius = EnhancedPerception.radius(killer,
                ModPerks.CALL_OF_DEATH.value(ModPerks.CALL_OF_DEATH_RADIUS, tier));
        double radiusSq = radius * radius;
        AABB box = AABB.ofSize(dead.position(), radius * 2, radius * 2, radius * 2);
        int revealed = 0;

        for (LivingEntity nearby : killer.serverLevel().getEntitiesOfClass(LivingEntity.class, box)) {
            if (nearby == dead || nearby == killer || !nearby.isAlive()
                    || nearby.distanceToSqr(dead.position()) > radiusSq) {
                continue;
            }
            if (AuraRevealHandler.reveal(killer, nearby, ModPerks.CALL_OF_DEATH_REVEAL_TICKS)) {
                revealed++;
            }
        }
        if (revealed >= I_KNOW_EVERYTHING_REVEALS) {
            ModAdvancements.grant(killer, ModAdvancements.I_KNOW_EVERYTHING);
        }
    }
}
