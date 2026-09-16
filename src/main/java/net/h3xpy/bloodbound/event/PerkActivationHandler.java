package net.h3xpy.bloodbound.event;

import java.util.Map;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.perk.Perk;
import net.h3xpy.bloodbound.perk.PerkRegistry;
import net.h3xpy.bloodbound.perk.PerkType;
import net.h3xpy.bloodbound.perk.impl.AdvancedMovementDevice;
import net.h3xpy.bloodbound.perk.impl.AntiExhaustionSyringe;
import net.h3xpy.bloodbound.perk.impl.BarbedWire;
import net.h3xpy.bloodbound.perk.impl.BewareThePowerOfAnAngel;
import net.h3xpy.bloodbound.perk.impl.BrokenMovementDevice;
import net.h3xpy.bloodbound.perk.impl.Flashbang;
import net.h3xpy.bloodbound.perk.impl.FragNade;
import net.h3xpy.bloodbound.perk.impl.KeepFighting;
import net.h3xpy.bloodbound.perk.impl.LowCostMovementDevice;
import net.h3xpy.bloodbound.perk.impl.NoOneGetsAway;
import net.h3xpy.bloodbound.perk.impl.HealingRunes;
import net.h3xpy.bloodbound.perk.impl.OutOfBreath;
import net.h3xpy.bloodbound.perk.impl.PatchUp;
import net.h3xpy.bloodbound.perk.impl.SurgicalSuture;
import net.h3xpy.bloodbound.perk.impl.TargetFound;
import net.h3xpy.bloodbound.perk.impl.Tinkerer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Runs the active perks. Each loadout slot has its own key, so pressing a slot's key only ever
 * triggers the perk sitting in that slot.
 * <p>
 * To add another active perk, give it {@link PerkType#ACTIVE} and add one entry to {@link #ACTIONS}.
 * Patch Up is the exception: it runs for as long as its key is held rather than firing on the press,
 * so it lives in {@link #onSlotKeyHeld} instead.
 */
public final class PerkActivationHandler {

    /**
     * What an active perk does when its key is pressed.
     */
    @FunctionalInterface
    public interface PerkAction {
        /**
         * @return true if the perk actually fired, which triggers a state sync to the client
         */
        boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime);
    }

    // Map.ofEntries rather than Map.of: the latter tops out at ten pairs.
    private static final Map<ResourceLocation, PerkAction> ACTIONS = Map.ofEntries(
            Map.entry(ModPerks.CLOSE_CALL.id(), PerkActivationHandler::closeCall),
            Map.entry(ModPerks.SURGICAL_SUTURE.id(), SurgicalSuture::activate),
            Map.entry(ModPerks.KEEP_FIGHTING.id(), KeepFighting::activate),
            Map.entry(ModPerks.BROKEN_MOVEMENT_DEVICE.id(), BrokenMovementDevice::activate),
            Map.entry(ModPerks.ANTI_EXHAUSTION_SYRINGE.id(), AntiExhaustionSyringe::activate),
            Map.entry(ModPerks.TINKERER.id(), Tinkerer::activate),
            Map.entry(ModPerks.LOW_COST_MOVEMENT_DEVICE.id(), LowCostMovementDevice::activate),
            Map.entry(ModPerks.NO_ONE_GETS_AWAY.id(), NoOneGetsAway::activate),
            Map.entry(ModPerks.FLASHBANG.id(), Flashbang::activate),
            Map.entry(ModPerks.ADVANCED_MOVEMENT_DEVICE.id(), AdvancedMovementDevice::activate),
            Map.entry(ModPerks.BEWARE_THE_POWER_OF_AN_ANGEL.id(), BewareThePowerOfAnAngel::activate),
            Map.entry(ModPerks.BARBED_WIRE.id(), BarbedWire::activate),
            Map.entry(ModPerks.OUT_OF_BREATH.id(), OutOfBreath::activate),
            Map.entry(ModPerks.HEALING_RUNES.id(), HealingRunes::activate),
            Map.entry(ModPerks.TARGET_FOUND.id(), TargetFound::activate),
            Map.entry(ModPerks.FRAGNADE.id(), FragNade::activate));

    private PerkActivationHandler() {}

    /**
     * @param slot the loadout slot whose key was pressed
     */
    public static void onActivationKey(ServerPlayer player, int slot) {
        if (slot < 0 || slot >= PlayerPerkData.LOADOUT_SIZE) {
            return;
        }

        PlayerPerkData data = PerkDataManager.get(player);
        Perk perk = PerkRegistry.get(data.getLoadoutSlot(slot));
        if (perk == null || perk.type() != PerkType.ACTIVE) {
            return;
        }

        int tier = data.getActiveTier(perk);
        if (tier <= 0) {
            return;
        }

        long gameTime = player.level().getGameTime();

        // Diagnostic Tool C's undo window opens while the perk is already on cooldown, so it gets
        // its say before the cooldown gate rather than after it.
        if (perk.id().equals(ModPerks.BROKEN_MOVEMENT_DEVICE.id())
                && BrokenMovementDevice.tryUndo(player, data, gameTime)) {
            PerkDataManager.sync(player);
            return;
        }

        // Target Found's window opens while the perk is usually still cooling down from the wire just
        // laid, so the key has to be heard before the cooldown gate.
        if (perk.id().equals(ModPerks.TARGET_FOUND.id()) && TargetFound.tryTeleport(player, gameTime)) {
            PerkDataManager.sync(player);
            return;
        }

        if (data.isOnCooldown(perk.id(), gameTime)) {
            return;
        }

        PerkAction action = ACTIONS.get(perk.id());
        if (action != null && action.activate(player, data, tier, gameTime)) {
            PerkDataManager.sync(player);
        }
    }

    /**
     * A slot's key went down or came up. Only the perks that care what the key does between presses
     * react: Patch Up runs while it is held, and Diagnostic Tool B's rewind stops when it comes up.
     *
     * @param slot the loadout slot whose key changed
     */
    public static void onSlotKeyHeld(ServerPlayer player, int slot, boolean holding) {
        if (slot < 0 || slot >= PlayerPerkData.LOADOUT_SIZE) {
            return;
        }

        PlayerPerkData data = PerkDataManager.get(player);
        Perk perk = PerkRegistry.get(data.getLoadoutSlot(slot));
        if (perk == null || data.getActiveTier(perk) <= 0) {
            return;
        }

        if (perk.id().equals(ModPerks.PATCH_UP.id())) {
            PatchUp.setHolding(player, holding);
        } else if (perk.id().equals(ModPerks.BEWARE_THE_POWER_OF_AN_ANGEL.id())) {
            // The wings are out for exactly as long as the key is down.
            BewareThePowerOfAnAngel.setHolding(player, holding);
        } else if (perk.id().equals(ModPerks.BROKEN_MOVEMENT_DEVICE.id()) && !holding) {
            // Diagnostic Tool B's rewind only runs while the key stays down.
            BrokenMovementDevice.onKeyReleased(player, data, player.level().getGameTime());
        } else if (perk.id().equals(ModPerks.ADVANCED_MOVEMENT_DEVICE.id()) && !holding) {
            // Tension Spring keeps the shot on a leash; without it the key coming up means nothing.
            AdvancedMovementDevice.onKeyReleased(player, data);
        } else if (perk.id().equals(ModPerks.BARBED_WIRE.id())) {
            // Laying a coil is a few seconds of holding still with the key down.
            BarbedWire.setHolding(player, holding);
        } else if (perk.id().equals(ModPerks.FLASHBANG.id())) {
            // Pulled Pin winds the throw up for as long as the key is held.
            Flashbang.setHolding(player, holding, player.level().getGameTime());
        } else if (perk.id().equals(ModPerks.TARGET_FOUND.id())) {
            TargetFound.setHolding(player, holding);
        } else if (perk.id().equals(ModPerks.FRAGNADE.id())) {
            // Wound up while the key is down, thrown when it comes up.
            FragNade.setHolding(player, holding, player.level().getGameTime());
        }
    }

    // --- Close Call ---

    private static boolean closeCall(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        if (player.getHealth() > ModPerks.CLOSE_CALL_HEALTH_THRESHOLD) {
            player.displayClientMessage(
                    Component.translatable("bloodbound.message.close_call_too_healthy")
                            .withStyle(ChatFormatting.DARK_GRAY),
                    true);
            return false;
        }

        // Dash along where the player is facing horizontally, so looking straight up or down still
        // gives a usable direction.
        double yaw = Math.toRadians(player.getYRot());
        double strength = data.isAddonActive(ModAddons.LEATHER_GLOVE)
                ? ModAddons.LEATHER_GLOVE_DISTANCE
                : 1.0D;
        double dirX = -Math.sin(yaw) * strength;
        double dirZ = Math.cos(yaw) * strength;

        data.startDash(ModPerks.CLOSE_CALL_DASH_TICKS, dirX, dirZ, gameTime,
                ModPerks.CLOSE_CALL_INVULNERABILITY_TICKS);
        chargeCloseCall(data, tier, gameTime);

        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.8F, 1.5F);
        player.serverLevel().sendParticles(ParticleTypes.LARGE_SMOKE,
                player.getX(), player.getY() + 0.5D, player.getZ(), 12, 0.3D, 0.3D, 0.3D, 0.02D);
        return true;
    }

    /**
     * Puts Close Call on cooldown, unless Fingerless Glove still owes the player a dash. The
     * cooldown that eventually lands is a long one — three dashes are paid for all at once.
     */
    private static void chargeCloseCall(PlayerPerkData data, int tier, long gameTime) {
        int cooldown = ModPerks.CLOSE_CALL.cooldownTicks(tier);
        if (!data.isAddonActive(ModAddons.FINGERLESS_GLOVE)) {
            data.setCooldown(ModPerks.CLOSE_CALL.id(), gameTime, cooldown);
            return;
        }

        // One dash of the set has just been spent; the rest stay on the table for a moment.
        int left = data.hasRedash(gameTime)
                ? data.dashesLeft() - 1
                : ModAddons.FINGERLESS_GLOVE_DASHES - 1;
        if (left > 0) {
            data.openRedash(left, gameTime + ModAddons.FINGERLESS_GLOVE_WINDOW_TICKS);
            return;
        }

        data.clearRedash();
        data.setCooldown(ModPerks.CLOSE_CALL.id(), gameTime,
                Math.round(cooldown * ModAddons.FINGERLESS_GLOVE_COOLDOWN));
    }
}
