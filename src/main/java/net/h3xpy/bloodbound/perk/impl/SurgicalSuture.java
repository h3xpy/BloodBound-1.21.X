package net.h3xpy.bloodbound.perk.impl;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.skillcheck.SkillCheckContext;
import net.h3xpy.bloodbound.skillcheck.SkillCheckDifficulty;
import net.h3xpy.bloodbound.skillcheck.SkillCheckManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Surgical Suture: a hard skill check that heals you when landed.
 * <p>
 * The perk only goes on cooldown once the check resolves, since the addons change what that
 * cooldown ends up being.
 */
public final class SurgicalSuture {

    /** Id of the transient max_absorption modifier Gel Dressing puts on and takes off. */
    private static final ResourceLocation GEL_CEILING_ID =
            ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "gel_dressing_absorption");

    private SurgicalSuture() {}

    /**
     * Raises the skill check. Called when the player presses the perk's loadout key.
     *
     * @return true if anything changed that the client needs to know about
     */
    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        if (SkillCheckManager.hasActive(player)) {
            return false;
        }

        // Sterilizer's second chance: the same key press takes the retry instead of a fresh attempt.
        if (data.hasSutureRetry(gameTime)) {
            data.clearSutureRetry();
            SkillCheckManager.start(player, SkillCheckContext.SELF_HEAL_RETRY, SkillCheckDifficulty.HARD);
            return true;
        }

        // Gel Dressing cares about the health you had when you triggered the perk, not when the
        // check resolves.
        data.setSutureUsedAtFullHealth(player.getHealth() >= player.getMaxHealth());
        SkillCheckManager.start(player, SkillCheckContext.SELF_HEAL, SkillCheckDifficulty.HARD);
        return false;
    }

    /** Applies the outcome of a self-heal skill check. */
    public static void onSelfHealResult(ServerPlayer player, SkillCheckContext context, boolean success) {
        PlayerPerkData data = PerkDataManager.get(player);
        int tier = data.getActiveTier(ModPerks.SURGICAL_SUTURE);
        if (tier <= 0) {
            return;
        }

        long gameTime = player.level().getGameTime();
        boolean isRetry = context == SkillCheckContext.SELF_HEAL_RETRY;

        if (success) {
            applyReward(player, data, tier);
            // Landing the check outright costs nothing. Only Sterilizer's second chance carries a
            // cooldown, and a halved one at that.
            if (isRetry) {
                applyCooldown(player, data, tier, gameTime, ModAddons.STERILIZER_SUCCESS_COOLDOWN);
            }
        } else if (!isRetry && data.isAddonActive(ModAddons.STERILIZER)) {
            // First miss with Sterilizer on: no cooldown yet, just a short window to try again.
            data.openSutureRetry(gameTime + ModAddons.STERILIZER_RETRY_WINDOW_TICKS);
            player.displayClientMessage(Component.translatable("bloodbound.message.suture_retry")
                    .withStyle(ChatFormatting.YELLOW), true);
        } else {
            float multiplier = isRetry ? ModAddons.STERILIZER_FAILURE_COOLDOWN : 1.0F;
            applyCooldown(player, data, tier, gameTime, multiplier);
        }
        PerkDataManager.sync(player);
    }

    /** The retry window ran out without the player taking it. */
    public static void onRetryLapsed(ServerPlayer player, PlayerPerkData data, long gameTime) {
        int tier = data.getActiveTier(ModPerks.SURGICAL_SUTURE);
        if (tier <= 0) {
            return;
        }
        applyCooldown(player, data, tier, gameTime, ModAddons.STERILIZER_FAILURE_COOLDOWN);
        PerkDataManager.sync(player);
    }

    private static void applyReward(ServerPlayer player, PlayerPerkData data, int tier) {
        // Gel Dressing turns a heal you did not need into absorption hearts.
        if (data.isAddonActive(ModAddons.GEL_DRESSING) && data.sutureUsedAtFullHealth()) {
            // The ceiling has to be raised first: max_absorption defaults to 0 on a player, and
            // setAbsorptionAmount silently clamps to it.
            ensureAbsorptionCeiling(player);

            float next = Math.min(ModAddons.GEL_ABSORPTION_MAX,
                    player.getAbsorptionAmount() + ModAddons.GEL_ABSORPTION_PER_SUCCESS);
            player.setAbsorptionAmount(next);
            data.setGrantedAbsorption(next);
            return;
        }

        float heal = (float) ModPerks.SURGICAL_SUTURE.value(ModPerks.SURGICAL_SELF_HEAL, tier);
        if (data.isAddonActive(ModAddons.NEEDLE_AND_THREAD)) {
            heal *= ModAddons.NEEDLE_HEAL_MULTIPLIER;
        }
        if (heal > 0.0F) {
            player.heal(heal);
        }
    }

    private static void applyCooldown(ServerPlayer player, PlayerPerkData data, int tier, long gameTime,
            float multiplier) {
        int ticks = Math.round(ModPerks.SURGICAL_SUTURE.cooldownTicks(tier) * multiplier);
        data.setCooldown(ModPerks.SURGICAL_SUTURE.id(), gameTime, ticks);
    }

    /**
     * Raises the absorption ceiling so Gel Dressing's hearts can exist at all. Players sit at a
     * max_absorption of 0 by default, which is why nothing sticks without this.
     */
    private static void ensureAbsorptionCeiling(ServerPlayer player) {
        AttributeInstance maxAbsorption = player.getAttribute(Attributes.MAX_ABSORPTION);
        if (maxAbsorption != null && maxAbsorption.getModifier(GEL_CEILING_ID) == null) {
            maxAbsorption.addTransientModifier(new AttributeModifier(GEL_CEILING_ID,
                    ModAddons.GEL_ABSORPTION_MAX, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /**
     * Keeps Gel Dressing's absorption in step with the addon actually being in play. Safe to call
     * every tick.
     */
    public static void enforceAbsorption(ServerPlayer player, PlayerPerkData data) {
        AttributeInstance maxAbsorption = player.getAttribute(Attributes.MAX_ABSORPTION);
        if (maxAbsorption == null) {
            return;
        }
        boolean active = data.isAddonActive(ModAddons.GEL_DRESSING);
        boolean raised = maxAbsorption.getModifier(GEL_CEILING_ID) != null;

        if (!active) {
            if (raised) {
                // Take back exactly what this addon gave, rather than zeroing the bar outright: a
                // potion's absorption hearts are not ours to remove.
                float granted = data.grantedAbsorption();
                maxAbsorption.removeModifier(GEL_CEILING_ID);
                player.setAbsorptionAmount(Math.max(0.0F, player.getAbsorptionAmount() - granted));
                data.setGrantedAbsorption(0.0F);
            }
            return;
        }

        if (!raised) {
            // Transient modifiers do not survive a relog, so put the ceiling back and with it the
            // hearts the player had earned.
            ensureAbsorptionCeiling(player);
            if (data.grantedAbsorption() > 0.0F) {
                player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), data.grantedAbsorption()));
            }
            return;
        }

        // Damage eats absorption behind our back; track it so a later success can top it back up.
        data.setGrantedAbsorption(Math.min(data.grantedAbsorption(), player.getAbsorptionAmount()));
    }
}
