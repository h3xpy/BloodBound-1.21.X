package net.h3xpy.bloodbound.damage;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * A vanilla damage source that tells its own story when it kills.
 * <p>
 * Only the death message changes: the damage type, the attacker and everything the perks test
 * ({@code is(PLAYER_ATTACK)}, armour bypass, kill credit) stay exactly those of the source it
 * wraps, so no perk behaves differently for it.
 * <p>
 * Keys, under {@code death.attack.bloodbound.<name>}:
 * <ul>
 *   <li>the bare key — nobody to blame ({@code %1$s} the victim);</li>
 *   <li>{@code .player} — someone else did it, or last hurt the victim ({@code %2$s});</li>
 *   <li>{@code .self} — the victim's own perk did it.</li>
 * </ul>
 */
public class PerkDamageSource extends DamageSource {

    private final String key;

    private PerkDamageSource(DamageSource base, String name) {
        super(base.typeHolder(), base.getDirectEntity(), base.getEntity(), base.sourcePositionRaw());
        this.key = "death.attack." + BloodBound.MODID + "." + name;
    }

    /** {@code base}, with the death message {@code death.attack.bloodbound.<name>}. */
    public static DamageSource of(DamageSource base, String name) {
        return new PerkDamageSource(base, name);
    }

    @Override
    public Component getLocalizedDeathMessage(LivingEntity victim) {
        Entity culprit = culprit(victim);
        if (culprit == victim) {
            return Component.translatable(key + ".self", victim.getDisplayName());
        }
        if (culprit != null) {
            return Component.translatable(key + ".player", victim.getDisplayName(), culprit.getDisplayName());
        }
        return Component.translatable(key, victim.getDisplayName());
    }

    /** Whoever the kill goes to: the source's own attacker, else whoever last hurt the victim. */
    @Nullable
    private Entity culprit(LivingEntity victim) {
        Entity attacker = getEntity();
        return attacker != null ? attacker : victim.getKillCredit();
    }
}
