package net.h3xpy.bloodbound.perk.impl;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Boundless Soul: blows struck up close hurt more, and everything else hurts far less — arrows,
 * explosions, fire, falls, magic. What nothing can resist (the void, {@code /kill}) is left alone.
 */
public final class BoundlessSoul {

    private BoundlessSoul() {}

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int tier = PerkDataManager.get(player).getActiveTier(ModPerks.BOUNDLESS_SOUL);
        DamageSource source = event.getSource();
        if (tier <= 0 || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        if (isMelee(source)) {
            double more = ModPerks.BOUNDLESS_SOUL.value(ModPerks.BOUNDLESS_MELEE_TAKEN, tier) / 100.0D;
            event.setAmount((float) (event.getAmount() * (1.0D + more)));
        } else {
            double less = ModPerks.BOUNDLESS_SOUL.value(ModPerks.BOUNDLESS_OTHER_REDUCTION, tier) / 100.0D;
            event.setAmount((float) (event.getAmount() * (1.0D - less)));
        }
    }

    /** A blow from a player's or a creature's own hand or weapon, as opposed to anything thrown, shot or cast. */
    private static boolean isMelee(DamageSource source) {
        return source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK)
                || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO);
    }
}
