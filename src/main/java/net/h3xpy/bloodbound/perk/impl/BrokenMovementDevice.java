package net.h3xpy.bloodbound.perk.impl;

import java.util.List;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.network.RecallTimerPayload;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Broken Movement Device: the first press pins where you are and how hurt you are, the second one
 * within the window rewinds you to it. Miss the window and the point is gone.
 * <p>
 * The cooldown only starts once the point is spent or lost, so holding a point open costs nothing
 * but the risk of wasting it.
 * <p>
 * Its five addons all hang off this class: Black Strap and Black Cable only move numbers, while
 * Diagnostic Tool A, B and C each change what the second press does.
 */
public final class BrokenMovementDevice {

    private BrokenMovementDevice() {}

    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        if (data.isRewinding()) {
            // A rewind is already playing; the key is being held to keep it going, not pressed again.
            return false;
        }
        if (data.hasRecallPoint()) {
            return returnToPoint(player, data, tier, gameTime);
        }
        return setPoint(player, data, tier, gameTime);
    }

    // --- setting the point ---

    private static boolean setPoint(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        int window = windowTicks(data, tier);
        data.setRecallPoint(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
                player.getHealth(), player.level().dimension().location(), gameTime + window);
        data.clearRecallTrail();
        sendTimer(player, gameTime + window);

        if (data.isAddonActive(ModAddons.DIAGNOSTIC_TOOL_A)) {
            // A short sprint to get clear, then a steady jog for the rest of the window.
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ModAddons.TOOL_A_BURST_TICKS,
                    ModAddons.TOOL_A_BURST_LEVEL - 1, false, false, true));
        }

        player.level().playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                SoundSource.PLAYERS, 0.6F, 1.8F);
        if (showsParticles(data)) {
            player.serverLevel().sendParticles(ParticleTypes.END_ROD,
                    player.getX(), player.getY() + 0.2D, player.getZ(), 24, 0.35D, 0.1D, 0.35D, 0.02D);
        }
        player.displayClientMessage(
                Component.translatable("bloodbound.message.broken_device_armed", window / 20)
                        .withStyle(ChatFormatting.AQUA),
                true);
        return true;
    }

    // --- returning to it ---

    private static boolean returnToPoint(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        ResourceLocation dimension = data.recallDimension();
        if (dimension == null || !dimension.equals(player.level().dimension().location())) {
            // The point is in another world; drop it rather than pretend it still means anything.
            dropPoint(player, data, tier, gameTime);
            player.displayClientMessage(
                    Component.translatable("bloodbound.message.broken_device_wrong_world")
                            .withStyle(ChatFormatting.DARK_GRAY),
                    true);
            return true;
        }

        // Diagnostic Tool B walks the path back instead of jumping it, one tick at a time.
        if (data.isAddonActive(ModAddons.DIAGNOSTIC_TOOL_B) && !data.recallTrail().isEmpty()) {
            data.startRewind();
            player.level().playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
                    SoundSource.PLAYERS, 0.5F, 2.0F);
            return true;
        }

        // Diagnostic Tool C keeps the door open for a few seconds in case this was a mistake.
        if (data.isAddonActive(ModAddons.DIAGNOSTIC_TOOL_C)) {
            data.setUndoPoint(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
                    player.getHealth(), gameTime + ModAddons.TOOL_C_UNDO_WINDOW_TICKS);
            player.displayClientMessage(
                    Component.translatable("bloodbound.message.broken_device_undo_ready",
                            ModAddons.TOOL_C_UNDO_WINDOW_TICKS / 20).withStyle(ChatFormatting.AQUA),
                    true);
        }

        snapTo(player, data.recallX(), data.recallY(), data.recallZ(), data.recallYRot(), data.recallXRot());
        // setHealth, not heal: the point restores the exact health that was recorded, whether that
        // is more or less than the player has now, and no healing rule gets a say in it.
        player.setHealth(Math.min(data.recallHealth(), player.getMaxHealth()));

        finish(player, data, tier, gameTime);
        return true;
    }

    /**
     * Diagnostic Tool C: takes the last return back. Checked before the cooldown gate, since the
     * perk is already on cooldown by the time this window is open.
     *
     * @return true if a return was actually undone
     */
    public static boolean tryUndo(ServerPlayer player, PlayerPerkData data, long gameTime) {
        if (!data.hasUndoPoint(gameTime)) {
            return false;
        }

        snapTo(player, data.undoX(), data.undoY(), data.undoZ(), data.undoYRot(), data.undoXRot());
        player.setHealth(Math.min(data.undoHealth(), player.getMaxHealth()));
        data.clearUndoPoint();

        player.level().playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.7F, 0.8F);
        player.displayClientMessage(
                Component.translatable("bloodbound.message.broken_device_undone").withStyle(ChatFormatting.AQUA),
                true);
        return true;
    }

    // --- per-tick work ---

    /**
     * Runs every tick: plays back a rewind, lays the trail behind the player, and takes the point
     * away once the window closes.
     */
    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        if (data.isRewinding()) {
            tickRewind(player, data, gameTime);
            return;
        }
        if (!data.hasRecallPoint()) {
            return;
        }

        int tier = data.getActiveTier(ModPerks.BROKEN_MOVEMENT_DEVICE);
        if (tier <= 0) {
            // The perk left the loadout, so the point goes with it. Nothing to charge for either:
            // the player gave the perk up rather than spent it.
            data.clearRecallPoint();
            data.clearRecallTrail();
            sendTimer(player, 0L);
            return;
        }

        // Only Diagnostic Tool B ever plays the path back, so only it pays to record one.
        if (data.isAddonActive(ModAddons.DIAGNOSTIC_TOOL_B)) {
            data.recordRecallStep(player.getX(), player.getY(), player.getZ(), ModAddons.TOOL_B_TRAIL_LIMIT);
        }

        if (data.isAddonActive(ModAddons.DIAGNOSTIC_TOOL_A)) {
            // Once the opening burst has run out, the slower speed carries the rest of the window.
            if (!player.hasEffect(MobEffects.MOVEMENT_SPEED)) {
                int remaining = (int) Math.max(1L, data.recallExpiresAt() - gameTime);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, remaining,
                        ModAddons.TOOL_A_TRAVEL_LEVEL - 1, false, false, true));
            }
        } else if (gameTime % ModPerks.BROKEN_DEVICE_TRAIL_INTERVAL == 0) {
            player.serverLevel().sendParticles(ParticleTypes.END_ROD,
                    player.getX(), player.getY() + 0.15D, player.getZ(), 1, 0.08D, 0.02D, 0.08D, 0.0D);

            // A slow shimmer over the point itself, so the spot you are going back to is somewhere
            // you can see rather than something you have to remember.
            player.serverLevel().sendParticles(ParticleTypes.END_ROD,
                    data.recallX(), data.recallY() + 0.6D, data.recallZ(),
                    2, 0.25D, 0.5D, 0.25D, 0.0D);
        }

        if (!data.consumeLapsedRecall(gameTime)) {
            return;
        }

        data.clearRecallTrail();
        sendTimer(player, 0L);
        startCooldown(data, tier, gameTime);

        if (data.isAddonActive(ModAddons.DIAGNOSTIC_TOOL_A)) {
            // The device dumps everything it had into the failed jump.
            player.removeEffect(MobEffects.MOVEMENT_SPEED);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ModAddons.TOOL_A_PENALTY_TICKS,
                    ModAddons.TOOL_A_PENALTY_LEVEL - 1, false, true, true));
        }

        player.level().playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
                SoundSource.PLAYERS, 0.5F, 1.6F);
        player.displayClientMessage(
                Component.translatable("bloodbound.message.broken_device_lapsed").withStyle(ChatFormatting.DARK_GRAY),
                true);
        PerkDataManager.sync(player);
    }

    /** Diagnostic Tool B: walks the recorded path backwards, one step per tick. */
    private static void tickRewind(ServerPlayer player, PlayerPerkData data, long gameTime) {
        int tier = data.getActiveTier(ModPerks.BROKEN_MOVEMENT_DEVICE);
        List<double[]> trail = data.recallTrail();
        int played = data.advanceRewind();

        if (tier <= 0 || trail.isEmpty()) {
            completeRewind(player, data, Math.max(1, tier), gameTime);
            return;
        }

        float progress = Math.min(1.0F, played / (float) ModAddons.TOOL_B_REWIND_TICKS);
        int index = Math.round((trail.size() - 1) * (1.0F - progress));
        double[] step = trail.get(Math.clamp(index, 0, trail.size() - 1));
        snapTo(player, step[0], step[1], step[2], player.getYRot(), player.getXRot());

        player.serverLevel().sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 0.9D, player.getZ(), 4, 0.2D, 0.4D, 0.2D, 0.01D);

        if (progress >= 1.0F) {
            completeRewind(player, data, tier, gameTime);
        }
    }

    /** The rewind ran all the way home: the point pays out in full. */
    private static void completeRewind(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        snapTo(player, data.recallX(), data.recallY(), data.recallZ(), data.recallYRot(), data.recallXRot());
        player.setHealth(Math.min(data.recallHealth(), player.getMaxHealth()));
        data.stopRewind();
        finish(player, data, tier, gameTime);
        PerkDataManager.sync(player);
    }

    /**
     * The slot key came up. Letting go part way through a rewind leaves the player wherever the
     * playback had reached, with nothing to show for it but the cooldown.
     */
    public static void onKeyReleased(ServerPlayer player, PlayerPerkData data, long gameTime) {
        if (!data.isRewinding()) {
            return;
        }
        data.stopRewind();
        dropPoint(player, data, Math.max(1, data.getActiveTier(ModPerks.BROKEN_MOVEMENT_DEVICE)), gameTime);
        player.displayClientMessage(
                Component.translatable("bloodbound.message.broken_device_rewind_stopped")
                        .withStyle(ChatFormatting.DARK_GRAY),
                true);
        PerkDataManager.sync(player);
    }

    // --- shared bits ---

    /** Moves the player without letting the trip itself hurt them. */
    private static void snapTo(ServerPlayer player, double x, double y, double z, float yRot, float xRot) {
        player.teleportTo(x, y, z);
        player.setYRot(yRot);
        player.setXRot(xRot);
        // The fall the player was in the middle of belongs to the timeline they just left.
        player.fallDistance = 0.0F;
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.hurtMarked = true;
    }

    /** Clears the point and starts the cooldown, with the usual noise. */
    private static void finish(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        data.clearRecallPoint();
        data.clearRecallTrail();
        sendTimer(player, 0L);
        startCooldown(data, tier, gameTime);

        player.level().playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.7F, 1.4F);
        if (showsParticles(data)) {
            player.serverLevel().sendParticles(ParticleTypes.END_ROD,
                    player.getX(), player.getY() + 0.9D, player.getZ(), 30, 0.3D, 0.5D, 0.3D, 0.05D);
        }
    }

    /** Throws the point away and pays the cooldown for it, without moving the player. */
    private static void dropPoint(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        data.clearRecallPoint();
        data.clearRecallTrail();
        sendTimer(player, 0L);
        startCooldown(data, tier, gameTime);
    }

    /** The window, with Black Strap's extra seconds if it is fitted. */
    private static int windowTicks(PlayerPerkData data, int tier) {
        int window = ModPerks.BROKEN_MOVEMENT_DEVICE.ticks(ModPerks.BROKEN_DEVICE_WINDOW, tier);
        return data.isAddonActive(ModAddons.BLACK_STRAP) ? window + ModAddons.BLACK_STRAP_EXTRA_TICKS : window;
    }

    /** Diagnostic Tool A hides the device's whole light show, trail and bursts alike. */
    private static boolean showsParticles(PlayerPerkData data) {
        return !data.isAddonActive(ModAddons.DIAGNOSTIC_TOOL_A);
    }

    private static void startCooldown(PlayerPerkData data, int tier, long gameTime) {
        float ticks = ModPerks.BROKEN_MOVEMENT_DEVICE.cooldownTicks(tier);
        // Only one addon fits a perk at a time, so at most one of these ever applies.
        if (data.isAddonActive(ModAddons.BLACK_CABLE)) {
            ticks *= ModAddons.BLACK_CABLE_COOLDOWN;
        }
        if (data.isAddonActive(ModAddons.DIAGNOSTIC_TOOL_C)) {
            ticks *= ModAddons.TOOL_C_COOLDOWN;
        }
        data.setCooldown(ModPerks.BROKEN_MOVEMENT_DEVICE.id(), gameTime, Math.max(1, Math.round(ticks)));
    }

    /**
     * Tells the client when the point runs out, so the HUD can count it down. A return point is
     * never serialised, so it does not ride along on the usual state sync.
     */
    private static void sendTimer(ServerPlayer player, long expiresAt) {
        PacketDistributor.sendToPlayer(player, new RecallTimerPayload(expiresAt));
    }
}
