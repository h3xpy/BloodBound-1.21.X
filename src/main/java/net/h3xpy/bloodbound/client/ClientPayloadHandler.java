package net.h3xpy.bloodbound.client;

import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.network.AuraRevealPayload;
import net.h3xpy.bloodbound.network.BankShotMarkPayload;
import net.h3xpy.bloodbound.network.EffectChargesPayload;
import net.h3xpy.bloodbound.network.HiddenPlayersPayload;
import net.h3xpy.bloodbound.network.RemoveMarksPayload;
import net.h3xpy.bloodbound.network.MarksPayload;
import net.h3xpy.bloodbound.network.NastyBladePayload;
import net.h3xpy.bloodbound.network.OmnisciencePayload;
import net.h3xpy.bloodbound.network.PerkChargesPayload;
import net.h3xpy.bloodbound.network.RecallTimerPayload;
import net.h3xpy.bloodbound.network.SkillCheckResultPayload;
import net.h3xpy.bloodbound.network.StartSkillCheckPayload;
import net.h3xpy.bloodbound.network.SyncPerkDataPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side entry point for BloodBound packets. Only ever linked on a physical client.
 */
public final class ClientPayloadHandler {

    private ClientPayloadHandler() {}

    public static void handleSync(SyncPerkDataPayload payload, IPayloadContext context) {
        PlayerPerkData fresh = new PlayerPerkData();
        fresh.deserializeNBT(context.player().registryAccess(), payload.data());
        ClientPerkData.set(fresh);
    }

    public static void handleStartSkillCheck(StartSkillCheckPayload payload) {
        ClientSkillCheck.start(payload);
    }

    public static void handleSkillCheckResult(SkillCheckResultPayload payload) {
        ClientSkillCheck.result(payload);
    }

    public static void handleRecallTimer(RecallTimerPayload payload) {
        ClientPerkData.setRecallExpiry(payload.expiresAt());
    }

    public static void handleAuraReveal(AuraRevealPayload payload) {
        ClientAuraReveal.reveal(payload.entityId(), payload.durationTicks());
    }

    public static void handlePerkCharges(PerkChargesPayload payload) {
        ClientPerkData.setCharges(payload.perkId(), payload.charges(), payload.maxCharges(),
                payload.rechargeAt());
    }

    public static void handleOmniscience(OmnisciencePayload payload) {
        ClientOmniscience.accept(payload);
    }

    public static void handleNastyBlade(NastyBladePayload payload) {
        ClientPerkData.setBladeStreak(payload.tokens(), payload.expiresAt());
    }

    public static void handleBankShotMark(BankShotMarkPayload payload) {
        ClientBankShots.mark(payload.entityId(), payload.bounces());
    }

    public static void handleMarks(MarksPayload payload) {
        ClientMarks.accept(payload);
    }

    public static void handleEffectCharges(EffectChargesPayload payload) {
        ClientEffectCharges.accept(payload);
    }

    public static void handleHiddenPlayers(HiddenPlayersPayload payload) {
        ClientUnderTheRadar.accept(payload);
    }

    public static void handleRemoveMarks(RemoveMarksPayload payload) {
        ClientMarks.remove(payload.positions());
    }
}
