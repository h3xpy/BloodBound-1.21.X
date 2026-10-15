package net.h3xpy.bloodbound.perk.impl;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Heart Of Gold: a few more hearts, for as long as the perk is in effect.
 * <p>
 * The modifier is saved with the player rather than transient: health is read back from the save
 * before the first tick, and without the hearts already there it would be cut down to ten.
 */
public final class HeartOfGold {

    private static final ResourceLocation HEALTH_ID = ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "heart_of_gold");

    private HeartOfGold() {}

    /** Puts the hearts on, takes them off, or keeps them at the tier's count. Called once a tick per player. */
    public static void tick(ServerPlayer player, PlayerPerkData data) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) {
            return;
        }
        int tier = data.getActiveTier(ModPerks.HEART_OF_GOLD);
        double amount = tier > 0 ? ModPerks.HEART_OF_GOLD.value(ModPerks.HEART_OF_GOLD_HEARTS, tier) * 2.0D : 0.0D;
        AttributeModifier existing = health.getModifier(HEALTH_ID);
        if (amount <= 0.0D) {
            if (existing != null) {
                health.removeModifier(HEALTH_ID);
                if (player.getHealth() > player.getMaxHealth()) {
                    player.setHealth(player.getMaxHealth());
                }
            }
            return;
        }
        if (existing == null || existing.amount() != amount) {
            health.removeModifier(HEALTH_ID);
            health.addPermanentModifier(new AttributeModifier(HEALTH_ID, amount, AttributeModifier.Operation.ADD_VALUE));
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
        }
    }
}
