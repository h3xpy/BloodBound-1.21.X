package net.h3xpy.bloodbound.event;

import java.util.UUID;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * Beyond Vision: almost nothing sticks, and whoever tried is not hidden either.
 * <p>
 * A harmful effect that lands on the bearer is cut down to a fraction of its length, and the thing
 * that cast it is lit up for as long as the effect was meant to last — so an attack that fails to
 * stick still tells you where it came from. The price is four hearts off the top, for good, held
 * there by an attribute modifier rather than by setting health directly.
 */
public final class BeyondVisionHandler {

    /** Id of the modifier that holds the bearer down to eight hearts. */
    private static final ResourceLocation HEALTH_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "beyond_vision_health");

    private BeyondVisionHandler() {}

    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        MobEffectInstance instance = event.getEffectInstance();
        if (instance.getEffect().value().getCategory() != MobEffectCategory.HARMFUL
                || instance.isInfiniteDuration()) {
            return;
        }

        int tier = PerkDataManager.get(player).getActiveTier(ModPerks.BEYOND_VISION);
        if (tier <= 0) {
            return;
        }

        int original = instance.getDuration();
        double reduction = ModPerks.BEYOND_VISION.value(ModPerks.VISION_REDUCTION, tier) / 100.0D;
        int shortened = Math.max(1, (int) Math.round(original * (1.0D - reduction)));

        // Whoever tried is shown for as long as the effect was meant to run, not as long as it
        // actually will: the failed attempt is the tip-off, and its full length is the reward.
        if (event.getEffectSource() instanceof LivingEntity source && source != player) {
            AuraRevealHandler.reveal(player, source, original);
        }

        // Written straight onto the incoming instance. The Added event fires before vanilla stores the
        // effect, so anything taken off and put back here is simply overwritten a line later — which
        // is why the perk used to do nothing at all.
        if (shortened < original) {
            instance.duration = shortened;
        }
    }

    /**
     * Holds the bearer at eight hearts. Run from the tick rather than from the loadout changing, so
     * it is right after a relog and comes off the moment the perk does.
     */
    public static void tick(ServerPlayer player, PlayerPerkData data) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) {
            return;
        }

        boolean active = data.getActiveTier(ModPerks.BEYOND_VISION) > 0;
        AttributeModifier existing = health.getModifier(HEALTH_MODIFIER_ID);

        if (!active) {
            if (existing != null) {
                health.removeModifier(HEALTH_MODIFIER_ID);
            }
            return;
        }

        // Worked out against the base rather than written in, so anything else raising max health
        // still lands the bearer on eight hearts rather than eight minus whatever it added.
        double amount = ModPerks.VISION_MAX_HEALTH - health.getBaseValue();
        if (existing == null || existing.amount() != amount) {
            health.removeModifier(HEALTH_MODIFIER_ID);
            health.addTransientModifier(new AttributeModifier(HEALTH_MODIFIER_ID, amount,
                    AttributeModifier.Operation.ADD_VALUE));
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    public static void clear(UUID playerId) {
        // Nothing is held per player any more; kept so logout cleanup stays in one place.
    }
}
