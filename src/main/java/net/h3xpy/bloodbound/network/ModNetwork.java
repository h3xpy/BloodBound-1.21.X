package net.h3xpy.bloodbound.network;

import net.h3xpy.bloodbound.client.ClientPayloadHandler;
import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.event.PerkActivationHandler;
import net.h3xpy.bloodbound.heal.HealManager;
import net.h3xpy.bloodbound.skillcheck.SkillCheckManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Wires up every BloodBound packet. Handlers run on the main thread.
 */
public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";

    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        // The client handler class is only ever linked when the lambda actually runs, which never
        // happens on a dedicated server.
        registrar.playToClient(SyncPerkDataPayload.TYPE, SyncPerkDataPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleSync(payload, context));

        registrar.playToServer(SetLoadoutPayload.TYPE, SetLoadoutPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                ResourceLocation perkId = payload.perkId().isEmpty()
                        ? null
                        : ResourceLocation.tryParse(payload.perkId());
                PerkDataManager.setLoadoutSlot(player, payload.slot(), perkId);
            }
        });

        registrar.playToServer(PurchaseNodePayload.TYPE, PurchaseNodePayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                PerkDataManager.purchaseNode(player, payload.nodeIndex());
            }
        });

        registrar.playToServer(ActivatePerkPayload.TYPE, ActivatePerkPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                PerkActivationHandler.onActivationKey(player, payload.slot());
            }
        });

        registrar.playToServer(SlotKeyPayload.TYPE, SlotKeyPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                PerkActivationHandler.onSlotKeyHeld(player, payload.slot(), payload.holding());
            }
        });

        registrar.playToServer(SetAddonPayload.TYPE, SetAddonPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                ResourceLocation perkId = ResourceLocation.tryParse(payload.perkId());
                ResourceLocation addonId = payload.addonId().isEmpty()
                        ? null
                        : ResourceLocation.tryParse(payload.addonId());
                if (perkId != null) {
                    PerkDataManager.setEquippedAddon(player, perkId, addonId);
                }
            }
        });

        registrar.playToServer(HealInputPayload.TYPE, HealInputPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                HealManager.setHolding(player, payload.holding());
            }
        });

        registrar.playToServer(SkillCheckInputPayload.TYPE, SkillCheckInputPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        SkillCheckManager.handleInput(player, payload.id(), payload.progress());
                    }
                });

        registrar.playToClient(StartSkillCheckPayload.TYPE, StartSkillCheckPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleStartSkillCheck(payload));

        registrar.playToClient(SkillCheckResultPayload.TYPE, SkillCheckResultPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleSkillCheckResult(payload));

        registrar.playToClient(RecallTimerPayload.TYPE, RecallTimerPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleRecallTimer(payload));

        registrar.playToClient(AuraRevealPayload.TYPE, AuraRevealPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleAuraReveal(payload));

        registrar.playToClient(PerkChargesPayload.TYPE, PerkChargesPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handlePerkCharges(payload));

        registrar.playToClient(OmnisciencePayload.TYPE, OmnisciencePayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleOmniscience(payload));

        registrar.playToClient(NastyBladePayload.TYPE, NastyBladePayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleNastyBlade(payload));

        registrar.playToClient(BankShotMarkPayload.TYPE, BankShotMarkPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleBankShotMark(payload));

        registrar.playToClient(MarksPayload.TYPE, MarksPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleMarks(payload));

        registrar.playToClient(EffectChargesPayload.TYPE, EffectChargesPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleEffectCharges(payload));

        registrar.playToClient(HiddenPlayersPayload.TYPE, HiddenPlayersPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleHiddenPlayers(payload));

        registrar.playToClient(RemoveMarksPayload.TYPE, RemoveMarksPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleRemoveMarks(payload));
    }
}
